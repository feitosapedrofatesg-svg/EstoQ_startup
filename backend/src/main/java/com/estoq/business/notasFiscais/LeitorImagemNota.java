package com.estoq.business.notasFiscais;

import com.estoq.core.exceptions.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class LeitorImagemNota {
    private final LeitorTextoNota leitorTexto;
    private final PreparadorImagemNota preparador;
    private final LeitorTsvNota leitorTsv;
    private final String executavel;
    private final int limiteSegundos;
    private final Semaphore vaga=new Semaphore(1);

    public LeitorImagemNota(LeitorTextoNota leitorTexto, PreparadorImagemNota preparador, LeitorTsvNota leitorTsv,
            @Value("${estoq.notas.ocr.executavel:tesseract}") String executavel,
            @Value("${estoq.notas.ocr.limite-segundos:30}") int limiteSegundos) {
        this.leitorTexto=leitorTexto; this.preparador=preparador; this.leitorTsv=leitorTsv;
        this.executavel=executavel; this.limiteSegundos=Math.max(1,Math.min(30,limiteSegundos));
    }

    public DadosNota ler(List<MultipartFile> arquivos) {
        if(!vaga.tryAcquire()) throw new BusinessException("Outra nota está sendo lida. Aguarde alguns segundos e tente novamente.",HttpStatus.TOO_MANY_REQUESTS);
        Path pasta=null;
        Process processo=null;
        long prazo=System.nanoTime()+TimeUnit.SECONDS.toNanos(limiteSegundos);
        try {
            pasta=Files.createTempDirectory("estoq-nota-");
            var texto=new StringBuilder();
            boolean baixaConfianca=false;
            for(int i=0;i<arquivos.size();i++) {
                var imagem=preparador.preparar(arquivos.get(i).getBytes());
                var entrada=pasta.resolve("pagina-"+i+".png");
                try { ImageIO.write(imagem,"png",entrada.toFile()); } finally { imagem.flush(); }
                var comando=new ProcessBuilder(executavel,entrada.toString(),"stdout","-l","por","--psm","3","tsv");
                comando.environment().put("OMP_THREAD_LIMIT","1");
                var saida=pasta.resolve("pagina-"+i+".tsv");
                comando.redirectOutput(saida.toFile()); comando.redirectError(ProcessBuilder.Redirect.DISCARD);
                long restante=prazo-System.nanoTime();
                if(restante<=0) throw tempoEsgotado();
                processo=comando.start();
                if(!processo.waitFor(restante,TimeUnit.NANOSECONDS)) throw tempoEsgotado();
                if(processo.exitValue()!=0) throw new BusinessException("Não foi possível ler a imagem. Tire outra foto ou envie o PDF/XML.",HttpStatus.UNPROCESSABLE_ENTITY);
                if(Files.size(saida)>4*1024*1024) throw new BusinessException("A imagem contém texto demais. Envie o XML da nota.",HttpStatus.UNPROCESSABLE_ENTITY);
                var tsv=Files.readString(saida);
                baixaConfianca |= leitorTsv.temBaixaConfianca(tsv);
                texto.append(leitorTsv.texto(tsv)).append('\n');
            }
            var nota=leitorTexto.ler(texto.toString(),true);
            var avisos=new java.util.ArrayList<>(nota.avisos());
            if(baixaConfianca) avisos.add("Algumas palavras tiveram baixa confiança no OCR. Confira os campos com as fotos.");
            return new DadosNota(nota.chave(),nota.fornecedor(),nota.documentoFornecedor(),nota.numero(),nota.serie(),
                    nota.emissao(),nota.valorTotal(),nota.itens(),avisos);
        } catch(java.io.IOException e) {
            throw new BusinessException("A leitura de imagens está indisponível. Tente PDF/XML ou fale com o administrador.",HttpStatus.SERVICE_UNAVAILABLE);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Leitura interrompida. Envie a nota novamente.",HttpStatus.SERVICE_UNAVAILABLE);
        } finally {
            if(processo!=null && processo.isAlive()) {
                processo.descendants().forEach(ProcessHandle::destroyForcibly);
                processo.destroyForcibly();
                try { processo.waitFor(2,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if(pasta!=null) {
                try(var arquivosTemporarios=Files.walk(pasta)) {
                    for(var arquivo : arquivosTemporarios.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(arquivo);
                } catch(java.io.IOException e) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Não foi possível limpar o diretório temporário do OCR.");
                }
            }
            vaga.release();
        }
    }
    private BusinessException tempoEsgotado() {
        return new BusinessException("A leitura ultrapassou 30 segundos. Reduza a imagem ou envie PDF/XML.",HttpStatus.REQUEST_TIMEOUT);
    }
}

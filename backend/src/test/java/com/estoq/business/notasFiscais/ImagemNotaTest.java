package com.estoq.business.notasFiscais;

import com.estoq.core.exceptions.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ImagemNotaTest {
    @TempDir Path pasta;
    private final PreparadorImagemNota preparador=new PreparadorImagemNota();

    private MockMultipartFile foto() throws Exception {
        var saida=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(100,100,BufferedImage.TYPE_INT_RGB),"png",saida);
        return new MockMultipartFile("arquivos","foto.png","image/png",saida.toByteArray());
    }
    private LeitorImagemNota leitor(String executavel,int segundos) {
        return new LeitorImagemNota(new LeitorTextoNota(),preparador,new LeitorTsvNota(),executavel,segundos);
    }
    @Test void rotacaoDoCelularTrocaDimensoesSemRecortar() {
        var origem=new BufferedImage(30,20,BufferedImage.TYPE_INT_RGB);
        var resultado=preparador.orientar(origem,6);
        assertEquals(20,resultado.getWidth());assertEquals(30,resultado.getHeight());
        assertEquals(BufferedImage.TYPE_BYTE_GRAY,resultado.getType());
    }
    @Test void textoReconstroiColunasMesmoSeOcrLeuBlocosForaDeOrdem() {
        var tsv="5\t1\t2\t1\t1\t1\t400\t20\t60\t20\t90\t10,00\n"
            + "5\t1\t1\t1\t1\t1\t10\t20\t60\t20\t90\tARROZ\n"
            + "5\t1\t1\t1\t2\t1\t10\t60\t60\t20\t90\tFEIJAO\n";
        assertEquals("ARROZ 10,00\nFEIJAO\n",new LeitorTsvNota().texto(tsv));
    }
    @Test void executavelAusenteInformaAlternativaELiberaVaga() throws Exception {
        var leitor=leitor(pasta.resolve("inexistente").toString(),1);
        for(int i=0;i<2;i++) {
            var erro=assertThrows(BaseException.class,()->leitor.ler(List.of(foto())));
            assertEquals(503,erro.getHttpStatus().value());
        }
    }
    @Test void timeoutInterrompeProcessoELimpaArquivosTemporarios() throws Exception {
        var executavel=pasta.resolve("ocr-lento.sh");
        Files.writeString(executavel,"#!/bin/sh\nsleep 5\n");executavel.toFile().setExecutable(true);
        var erro=assertThrows(BaseException.class,()->leitor(executavel.toString(),1).ler(List.of(foto())));
        assertEquals(408,erro.getHttpStatus().value());
        try(var temporarios=Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            assertTrue(temporarios.noneMatch(p -> p.getFileName().toString().startsWith("estoq-nota-")));
        }
    }
    @Test void arquivoFalsoNaoEhTratadoComoFoto() {
        assertThrows(BaseException.class,()->preparador.preparar("<xml/>".getBytes()));
    }
    @Test
    @EnabledIfSystemProperty(named="notas.ocr.executavel",matches=".+")
    void ocrRealLeNotaFicticiaEmPngEJpg() throws Exception {
        // Imagem gerada com dados fictícios; não precisa fotografias privadas no repositório.
        var imagem=new BufferedImage(3000,1000,BufferedImage.TYPE_INT_RGB);
        var g=imagem.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,3000,1000);
        g.setColor(java.awt.Color.BLACK);g.setFont(new java.awt.Font("Monospaced",java.awt.Font.PLAIN,27));
        var linhas=List.of("Recebemos de FORNECEDOR TESTE os produtos", "Emissão: 02/09/2026",
            "5226 0912 3456 7800 0195 5500 1000 0000 0110 0000 0010",
            "VALOR TOTAL DOS PRODUTOS", "20,00", "VALOR TOTAL DA NOTA", "20,00", "DADOS DOS PRODUTOS / SERVIÇOS",
            "AR1 ARROZ 10063011 000 5102 KG 2,00 10,00 0,00 20,00", "DADOS ADICIONAIS");
        int y=70;for(var linha:linhas){g.drawString(linha,40,y);y+=75;}g.dispose();
        for(String formato:List.of("png","jpg")) {
            var bytes=new ByteArrayOutputStream();ImageIO.write(imagem,formato,bytes);
            var nota=leitor(System.getProperty("notas.ocr.executavel"),30).ler(List.of(new MockMultipartFile("arquivos","nota."+formato,"image/"+formato,bytes.toByteArray())));
            assertEquals(1,nota.itens().size());assertEquals("ARROZ",nota.itens().getFirst().descricao());
            assertTrue(nota.itens().getFirst().incerto());
        }
    }
    @Test
    @EnabledIfSystemProperty(named="notas.ocr.executavel",matches=".+")
    @EnabledIfSystemProperty(named="notas.reais",matches=".+")
    void fotosDasNotasFornecidasSaoCompletasOuRecusadas() throws Exception {
        for(var caso : List.of(new Object[]{"JBS 497.pdf",4},new Object[]{"T E T COMERCIO 337.pdf",4},new Object[]{"SENDAS DISTRIBUIDORA 772.pdf",60})) {
            var paginas=new java.util.ArrayList<org.springframework.web.multipart.MultipartFile>();
            try(var documento=org.apache.pdfbox.Loader.loadPDF(Files.readAllBytes(Path.of(System.getProperty("notas.reais"),(String)caso[0])))) {
                var renderizador=new org.apache.pdfbox.rendering.PDFRenderer(documento);
                for(int i=0;i<documento.getNumberOfPages();i++) {
                    var pagina=renderizador.renderImageWithDPI(i,250,org.apache.pdfbox.rendering.ImageType.RGB);
                    var bytes=new ByteArrayOutputStream();ImageIO.write(pagina,"jpg",bytes);pagina.flush();
                    paginas.add(new MockMultipartFile("arquivos","pagina-"+i+".jpg","image/jpeg",bytes.toByteArray()));
                }
            }
            // OCR pode confundir números pequenos do DANFE; jamais aceitar uma leitura parcial.
            try {
                var nota=leitor(System.getProperty("notas.ocr.executavel"),30).ler(paginas);
                assertEquals(caso[1],nota.itens().size(),(String)caso[0]);
            } catch (com.estoq.core.exceptions.FieldValidationException erro) {
                assertTrue(erro.getMessage().contains("imagem mais nítida")
                    || erro.getMessage().contains("total") || erro.getMessage().contains("XML"));
            }
        }
    }

}

package com.estoq.business.notasFiscais;

import com.estoq.core.exceptions.FieldValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class LeitorDocumentoNota {
    private final LeitorPdfNota pdf;
    private final LeitorXmlNota xml;
    private final LeitorImagemNota imagem;
    public DadosNota ler(List<MultipartFile> arquivos) {
        if (arquivos==null || arquivos.isEmpty() || arquivos.size()>10) throw erro("Envie um PDF/XML ou até dez imagens da mesma nota.");
        if (arquivos.stream().anyMatch(MultipartFile::isEmpty) || arquivos.stream().mapToLong(MultipartFile::getSize).sum()>4*1024*1024)
            throw erro("Os arquivos devem somar no máximo 4 MB e não podem estar vazios.");
        boolean somenteImagens = arquivos.stream().allMatch(a -> nome(a).matches(".*\\.(jpg|jpeg|png)$"));
        if (somenteImagens) return imagem.ler(arquivos);
        if (arquivos.size()!=1) throw erro("Não misture formatos. Envie um PDF/XML ou imagens da mesma nota.");
        var arquivo = arquivos.getFirst();
        if (arquivo.isEmpty() || arquivo.getSize()>4*1024*1024) throw erro("O documento deve ter até 4 MB e não pode estar vazio.");
        String nome = arquivo.getOriginalFilename()==null ? "" : arquivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            var bytes = arquivo.getBytes();
            if(nome.endsWith(".pdf")) return pdf.ler(bytes);
            if(nome.endsWith(".xml")) return xml.ler(bytes);
        } catch (IOException e) { throw erro("Não foi possível ler o arquivo enviado."); }
        throw erro("Envie um PDF, XML, JPG ou PNG.");
    }
    private String nome(MultipartFile arquivo) {
        return arquivo.getOriginalFilename()==null ? "" : arquivo.getOriginalFilename().toLowerCase(Locale.ROOT);
    }
    private FieldValidationException erro(String mensagem) { return new FieldValidationException("arquivos",mensagem); }
}

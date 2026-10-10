package com.estoq.business.notasFiscais;

import com.estoq.core.exceptions.FieldValidationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LeitorPdfNota {
    private final LeitorTextoNota leitorTexto;
    public DadosNota ler(byte[] arquivo) {
        try (var documento = Loader.loadPDF(arquivo)) {
            if (documento.isEncrypted() || documento.getNumberOfPages() > 20)
                throw new FieldValidationException("arquivos", "Use um PDF sem senha, com até 20 páginas.");
            var extrator = new PDFTextStripper();
            extrator.setSortByPosition(true);
            return leitorTexto.ler(extrator.getText(documento), false);
        } catch (java.io.IOException e) {
            throw new FieldValidationException("arquivos", "PDF inválido ou protegido. Envie o XML ou outro documento.");
        }
    }
}

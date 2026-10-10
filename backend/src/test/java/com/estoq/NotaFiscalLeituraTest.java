package com.estoq;

import com.estoq.business.notasFiscais.*;
import com.estoq.core.exceptions.FieldValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NotaFiscalLeituraTest {
    private final LeitorTextoNota texto = new LeitorTextoNota();
    private final LeitorPdfNota pdf = new LeitorPdfNota(texto);
    private final LeitorXmlNota xml = new LeitorXmlNota();

    @Test
    @EnabledIfSystemProperty(named="notas.reais", matches=".+")
    void confereDocumentosLocaisSemVersionarDadosPessoais() throws Exception {
        String pasta=System.getProperty("notas.reais");
        for(var caso : List.of(new Object[]{"JBS 497.pdf",4,"4270.75"},new Object[]{"T E T COMERCIO 337.pdf",4,"927.40"},
                new Object[]{"SENDAS DISTRIBUIDORA 772.pdf",60,"1845.36"})) {
            var nota=pdf.ler(Files.readAllBytes(Path.of(pasta,(String)caso[0])));
            assertEquals(caso[1],nota.itens().size(),(String)caso[0]);
            assertEquals(0,new BigDecimal((String)caso[2]).compareTo(nota.valorTotal()),(String)caso[0]);
            new NotaFiscalValidation().validarLeitura(nota);
            assertEquals(0,nota.valorTotal().compareTo(new RateioNota().calcular(nota).stream().reduce(BigDecimal.ZERO,BigDecimal::add)));
        }
    }

    @Test
    void xmlUsaNamespaceERecusaEntidadesExternas() {
        assertThrows(FieldValidationException.class,()->xml.ler("<!DOCTYPE n [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><n>&x;</n>".getBytes()));
        assertThrows(FieldValidationException.class,()->xml.ler("<n/>".getBytes()));
    }

    @Test
    void rateioPreservaCentavosEAplicaDescontoApenasUmaVez() {
        var itens=List.of(new DadosNota.ItemLido(1,"1","A",null,"UN",BigDecimal.ONE,new BigDecimal("1.00"),new BigDecimal("0.10"),null,false),
            new DadosNota.ItemLido(2,"2","B",null,"UN",BigDecimal.ONE,new BigDecimal("2.00"),BigDecimal.ZERO,null,false));
        var nota=new DadosNota("","","","","",null,new BigDecimal("3.00"),itens,List.of());
        assertEquals(List.of(new BigDecimal("0.93"),new BigDecimal("2.07")),new RateioNota().calcular(nota));
    }
}

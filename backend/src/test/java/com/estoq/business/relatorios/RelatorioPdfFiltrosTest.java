package com.estoq.business.relatorios;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelatorioPdfFiltrosTest {
    @Test
    void preservaJanelaDeConsumoEIntervaloInclusivo() throws Exception {
        var relatorios = mock(RelatorioService.class);
        var inicio = LocalDate.of(2026, 8, 10);
        var fim = LocalDate.of(2026, 8, 15);
        var ate = fim.plusDays(1).atStartOfDay();
        when(relatorios.consumoMedio(inicio.atStartOfDay(), ate)).thenReturn(List.of(
                new ConsumoMedioDTO(1L, "Arroz integral", "KG", new BigDecimal("12.375"), 6, new BigDecimal("2.063"))));
        var bytes = new RelatorioPdfService(relatorios).gerar("consumo-medio", inicio, fim, 6, null);
        verify(relatorios).consumoMedio(inicio.atStartOfDay(), ate);
        try (var pdf = Loader.loadPDF(bytes)) {
            String texto = new PDFTextStripper().getText(pdf);
            assertTrue(texto.contains("10/08/2026 a 15/08/2026"));
            assertTrue(texto.contains("12,375 kg"));
            assertTrue(texto.contains("2,063 kg"));
        }
    }

    @Test
    void receitaBaseEValoresDoCmvPermanecemOsDoServico() throws Exception {
        var relatorios = mock(RelatorioService.class);
        var dia = LocalDate.of(2026, 8, 15);
        var inicio = dia.atStartOfDay(); var fim = dia.plusDays(1).atStartOfDay();
        var receita = new BigDecimal("25000");
        when(relatorios.calcularCmv(inicio, fim, receita)).thenReturn(new CmvResumoDTO(inicio, fim,
                new BigDecimal("12345.67"), new BigDecimal("5000"), new BigDecimal("9345.67"), new BigDecimal("8000"), receita,
                new BigDecimal("32"), new BigDecimal("30"), new BigDecimal("2"), new BigDecimal("7500"), new BigDecimal("500"), new BigDecimal("6.25"), BigDecimal.ZERO));
        var bytes = new RelatorioPdfService(relatorios).gerar("cmv", dia, dia, 1, receita);
        verify(relatorios).calcularCmv(inicio, fim, receita);
        try (var pdf = Loader.loadPDF(bytes)) {
            String texto = new PDFTextStripper().getText(pdf);
            assertTrue(texto.contains("R$ 8.000,00"));
            assertTrue(texto.contains("R$ 25.000,00"));
            assertTrue(texto.contains("32%"));
        }
    }
}

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
        var ate = fim.plusDays(1).atTime(3, 0);
        when(relatorios.consumoMedio(inicio.atTime(3, 0), ate)).thenReturn(List.of(
                new ConsumoMedioDTO(1L, "Arroz integral", "KG", new BigDecimal("12.375"), 6, new BigDecimal("2.063"))));
        var bytes = new RelatorioPdfService(new com.estoq.core.helpers.HorarioOperacao(), relatorios).gerar("consumo-medio", inicio, fim, 6, null);
        verify(relatorios).consumoMedio(inicio.atTime(3, 0), ate);
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
        var inicio = dia.atTime(3, 0); var fim = dia.plusDays(1).atTime(3, 0);
        var receita = new BigDecimal("25000");
        when(relatorios.calcularCmv(inicio, fim, receita)).thenReturn(new CmvResumoDTO(inicio, fim,
                new BigDecimal("12345.67"), new BigDecimal("5000"), new BigDecimal("9345.67"), new BigDecimal("8000"), receita,
                new BigDecimal("32"), new BigDecimal("30"), new BigDecimal("2"), new BigDecimal("7500"), new BigDecimal("500"), new BigDecimal("6.25"), BigDecimal.ZERO,
                BigDecimal.ZERO, 1, true));
        var bytes = new RelatorioPdfService(new com.estoq.core.helpers.HorarioOperacao(), relatorios).gerar("cmv", dia, dia, 1, receita);
        verify(relatorios).calcularCmv(inicio, fim, receita);
        try (var pdf = Loader.loadPDF(bytes)) {
            String texto = new PDFTextStripper().getText(pdf);
            assertTrue(texto.contains("R$ 8.000,00"));
            assertTrue(texto.contains("R$ 25.000,00"));
            assertTrue(texto.contains("32%"));
            assertTrue(texto.contains("Faturamento informado como total do período."));
            assertFalse(texto.contains("cadastro anterior"));
        }
    }
    @Test
    void desperdicioSemDescricaoExportaComMotivoETotal() throws Exception {
        var relatorios = mock(RelatorioService.class);
        var dia = LocalDate.of(2026, 8, 15);
        when(relatorios.desperdicio(dia.atTime(3, 0), dia.plusDays(1).atTime(3, 0))).thenReturn(List.of(
                new DesperdicioDTO(dia.atTime(19, 26), com.estoq.business.desperdicios.MotivoDesperdicio.DETERIORACAO, null,
                        1L, "Arroz", "LOTE1", new BigDecimal("2"), new BigDecimal("12.34"), "KG"),
                new DesperdicioDTO(dia.atTime(20, 26), com.estoq.business.desperdicios.MotivoDesperdicio.OUTRO, "Diferença negativa de balanço",
                        1L, "Arroz", "LOTE2", new BigDecimal("1"), new BigDecimal("7.66"), "KG")));
        when(relatorios.estoqueAtual()).thenReturn(List.of());
        var bytes = new RelatorioPdfService(new com.estoq.core.helpers.HorarioOperacao(), relatorios).gerar("desperdicio", dia, dia, 1, null);
        try (var pdf = Loader.loadPDF(bytes)) {
            String texto = new PDFTextStripper().getText(pdf);
            assertTrue(texto.contains("DETERIORACAO"));
            assertTrue(texto.contains("Diferença negativa de balanço"));
            assertTrue(texto.contains("Prejuízo total no período"));
            assertTrue(texto.contains("R$ 20,00"));
            assertTrue(texto.contains("15/08/2026 a 15/08/2026"));
        }
    }

    @Test
    void estoqueEVencidosTotalizamOsValoresExibidos() throws Exception {
        var relatorios = mock(RelatorioService.class);
        var dia = LocalDate.of(2026, 8, 15);
        var unidade = com.estoq.business.produtos.UnidadeMedida.KG;
        when(relatorios.estoqueAtual()).thenReturn(List.of(
                new com.estoq.business.produtos.EstoqueDTO(1L, "Arroz", "Grãos", unidade, new BigDecimal("2"), new BigDecimal("2"), new BigDecimal("12.34"), null, null, null, false, false),
                new com.estoq.business.produtos.EstoqueDTO(2L, "Farinha", "Grãos", unidade, new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("7.66"), null, null, null, false, false)));
        when(relatorios.vencidos()).thenReturn(List.of(
                new com.estoq.business.lotes.LoteDTO(1L, 0L, "VENCIDO", 1L, "Arroz", unidade, new BigDecimal("3"), new BigDecimal("3"), dia.minusDays(10), dia.minusDays(1), new BigDecimal("3.3333"), true, false, -1L)));
        for (String tipo : List.of("estoque-atual", "vencidos")) {
            var bytes = new RelatorioPdfService(new com.estoq.core.helpers.HorarioOperacao(), relatorios).gerar(tipo, dia, dia, 1, null);
            try (var pdf = Loader.loadPDF(bytes)) {
                String texto = new PDFTextStripper().getText(pdf);
                assertTrue(texto.contains(tipo.equals("estoque-atual") ? "Valor total em estoque" : "Valor total dos lotes vencidos"));
                assertTrue(texto.contains(tipo.equals("estoque-atual") ? "R$ 20,00" : "R$ 10,00"));
            }
        }
    }

}

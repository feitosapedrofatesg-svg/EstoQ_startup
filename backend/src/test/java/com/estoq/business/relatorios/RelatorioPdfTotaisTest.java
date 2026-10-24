package com.estoq.business.relatorios;

import com.estoq.business.lotes.LoteDTO;
import com.estoq.business.produtos.UnidadeMedida;
import com.estoq.business.produtosAbertos.ProdutoAbertoDTO;
import com.estoq.core.helpers.HorarioOperacao;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelatorioPdfTotaisTest {
    private final RelatorioService relatorios = mock(RelatorioService.class);
    private final RelatorioPdfService pdfs = new RelatorioPdfService(new PreparadorRelatorioService(new HorarioOperacao(), relatorios));
    private final LocalDate inicio = LocalDate.of(2026, 8, 10);
    private final LocalDate fim = inicio.plusDays(2);

    @Test
    void faturamentoDistingueZeroDePendenciaEPreservaReferenciaHistorica() throws Exception {
        when(relatorios.faturamento(inicio, fim)).thenReturn(new FaturamentoPeriodoDTO(inicio, fim,
                n("120.50"), 2, 1, n("900"), true, List.of(
                new FaturamentoDiarioDTO(inicio, n("120.50"), 1L),
                new FaturamentoDiarioDTO(inicio.plusDays(1), n("0"), 0L),
                new FaturamentoDiarioDTO(fim, null, null))));
        String texto = gerar("faturamento");
        assertTrue(texto.contains("10/08/2026 a 12/08/2026"));
        assertTrue(texto.contains("11/08/2026 R$ 0,00 Informado"));
        assertTrue(texto.contains("12/08/2026 Não informado Pendente"));
        assertTrue(texto.contains("Faturamento informado (parcial) R$ 120,50"));
        assertTrue(texto.contains("Dias informados 2"));
        assertTrue(texto.contains("Dias pendentes 1"));
        assertTrue(texto.contains("Total histórico do intervalo (referência) R$ 900,00"));
        assertFalse(texto.contains("1.020,50"));
        verify(relatorios).faturamento(inicio, fim);
    }

    @Test
    void faturamentoCompletoUsaValorCorrigidoSemDuplicar() throws Exception {
        when(relatorios.faturamento(inicio, fim)).thenReturn(new FaturamentoPeriodoDTO(inicio, fim,
                n("250"), 3, 0, n("250"), false, List.of(
                new FaturamentoDiarioDTO(inicio, n("250"), 2L),
                new FaturamentoDiarioDTO(inicio.plusDays(1), n("0"), 0L),
                new FaturamentoDiarioDTO(fim, n("0"), 0L))));
        String texto = gerar("faturamento");
        assertTrue(texto.contains("Faturamento total do período R$ 250,00"));
        assertTrue(texto.contains("Dias pendentes 0"));
        assertFalse(texto.contains("(parcial)"));
    }

    @Test
    void faturamentoZeradoCompletoNaoSeConfundeComTodosOsDiasPendentes() throws Exception {
        for (boolean preenchido : List.of(true, false)) {
            var dias = inicio.datesUntil(fim.plusDays(1)).map(d -> new FaturamentoDiarioDTO(d,
                    preenchido ? BigDecimal.ZERO : null, preenchido ? 0L : null)).toList();
            when(relatorios.faturamento(inicio, fim)).thenReturn(new FaturamentoPeriodoDTO(inicio, fim,
                    BigDecimal.ZERO, preenchido ? 3 : 0, preenchido ? 0 : 3,
                    preenchido ? BigDecimal.ZERO : null, false, dias));
            String texto = gerar("faturamento");
            assertTrue(texto.contains(preenchido ? "Faturamento total do período R$ 0,00" : "Faturamento informado (parcial) R$ 0,00"));
            assertTrue(texto.contains(preenchido ? "Dias pendentes 0" : "Dias pendentes 3"));
        }
    }

    @Test
    void mensalSomaAsTresColunasPelosCentavosExibidos() throws Exception {
        when(relatorios.cmvPorMes(inicio.atTime(3, 0), fim.plusDays(1).atTime(3, 0))).thenReturn(List.of(
                new CmvMensalDTO(YearMonth.of(2026, 8), n("0.005"), n("10.005"), n("2.005")),
                new CmvMensalDTO(YearMonth.of(2026, 9), n("0.005"), n("20.005"), n("3.005"))));
        String texto = gerar("cmv-mensal");
        assertTrue(texto.contains("CMV total no período R$ 0,02"));
        assertTrue(texto.contains("Compras totais no período R$ 30,02"));
        assertTrue(texto.contains("Desperdício total no período R$ 5,02"));
        assertTrue(texto.contains("períodos parciais"));
    }

    @Test
    void consumoAgrupaQuantidadesPorUnidadeSemSomarMediasOuUnidadeAusente() throws Exception {
        when(relatorios.consumoMedio(inicio.atTime(3, 0), fim.plusDays(1).atTime(3, 0))).thenReturn(List.of(
                new ConsumoMedioDTO(1L, "Arroz", "KG", n("2.125"), 3, n("0.708")),
                new ConsumoMedioDTO(2L, "Farinha", "KG", n("3.250"), 3, n("1.083")),
                new ConsumoMedioDTO(3L, "Óleo", "L", n("7"), 3, n("2.333")),
                new ConsumoMedioDTO(4L, "Sem unidade", null, n("80"), 3, n("26.667"))));
        String texto = gerar("consumo-medio");
        assertTrue(texto.contains("Total consumido (kg) 5,375"));
        assertTrue(texto.contains("Total consumido (l) 7"));
        assertTrue(texto.contains("Produtos com consumo 4"));
        assertFalse(texto.contains("92,375"));
        assertFalse(texto.contains("Total consumido ()"));
    }

    @Test
    void consumoSemanalSomaCustosSemSomarAsMedias() throws Exception {
        when(relatorios.consumoPorDiaSemana(inicio.atTime(3, 0), fim.plusDays(1).atTime(3, 0))).thenReturn(List.of(
                new ConsumoDiaSemanaDTO(DayOfWeek.MONDAY, n("10.005"), n("5")),
                new ConsumoDiaSemanaDTO(DayOfWeek.TUESDAY, n("20.005"), n("4"))));
        String texto = gerar("consumo-dia-semana");
        assertTrue(texto.contains("segunda-feira"));
        assertTrue(texto.contains("terça-feira"));
        assertTrue(texto.contains("Custo total de consumo no período R$ 30,02"));
    }

    @Test
    void proximosVencimentosMostramCustoDoSaldoEUnidadesSeparadas() throws Exception {
        when(relatorios.proximosVencimento()).thenReturn(List.of(
                new LoteDTO(1L, 0L, "A", 1L, "Arroz", UnidadeMedida.KG, n("3"), n("3"), inicio, fim, n("0.335"), false, true, 2L),
                new LoteDTO(2L, 0L, "B", 2L, "Óleo", UnidadeMedida.L, n("3"), n("3"), inicio, fim, n("0.335"), false, true, 2L)));
        String texto = gerar("proximos-vencimento");
        assertTrue(texto.contains("Valor total próximo do vencimento R$ 2,02"));
        assertTrue(texto.contains("Saldo total (kg) 3"));
        assertTrue(texto.contains("Saldo total (l) 3"));
        assertTrue(texto.contains("Posição atual"));
    }

    @Test
    void abertosMostramAberturaLocalERestantesPorUnidadeSemDuplicarEstoque() throws Exception {
        when(relatorios.produtosAbertos()).thenReturn(List.of(
                aberto(1L, "Arroz", "KG", "10", "3"), aberto(2L, "Farinha", "KG", "2", "1"),
                aberto(3L, "Óleo", "L", "4", "2")));
        String texto = gerar("produtos-abertos");
        assertTrue(texto.contains("09/08/2026"));
        assertTrue(texto.contains("Quantidade aberta total (kg) 12"));
        assertTrue(texto.contains("Quantidade restante total (kg) 4"));
        assertTrue(texto.contains("Quantidade restante total (l) 2"));
        assertTrue(texto.contains("Não somar novamente ao estoque"));
        assertFalse(texto.contains("R$"));
    }

    @Test
    void reposicaoMostraQuantidadePorUnidadeSemInventarOrcamento() throws Exception {
        when(relatorios.reposicaoSugerida(null)).thenReturn(List.of(
                new ReposicaoSugeridaDTO(1L, "Arroz", "Grãos", UnidadeMedida.KG, n("0"), n("1"), n("1"), 7, n("7"), "AUTOMATICO", 30, null, null),
                new ReposicaoSugeridaDTO(2L, "Óleo", "Óleos", UnidadeMedida.L, n("0"), n("1"), n("2"), 7, n("14"))));
        String texto = gerar("reposicao-sugerida");
        assertTrue(texto.contains("Automático"));
        assertTrue(texto.contains("sem histórico"));
        assertTrue(texto.contains("Produtos a repor 2"));
        assertTrue(texto.contains("Quantidade sugerida total (kg) 7"));
        assertTrue(texto.contains("Quantidade sugerida total (l) 14"));
        assertFalse(texto.contains("R$"));
    }

    private ProdutoAbertoDTO aberto(long id, String nome, String unidade, String aberta, String restante) {
        return new ProdutoAbertoDTO(id, 0L, id, nome, unidade, id, "Lote " + id, fim, 1L,
                inicio.atTime(1, 0), n(aberta), n(aberta).subtract(n(restante)), n(restante), false, n(restante), n(restante));
    }

    private String gerar(String tipo) throws Exception {
        byte[] bytes = pdfs.gerar(tipo, inicio, fim, 3, null);
        String pasta = System.getProperty("estoq.pdf.validacao");
        if (pasta != null) { Files.createDirectories(Path.of(pasta)); Files.write(Path.of(pasta, tipo + ".pdf"), bytes); }
        try (var pdf = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(pdf).replaceAll("\\s+", " "); }
    }

    private static BigDecimal n(String valor) { return new BigDecimal(valor); }
}

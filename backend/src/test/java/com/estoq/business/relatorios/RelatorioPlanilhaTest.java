package com.estoq.business.relatorios;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.estoq.business.relatorios.DocumentoRelatorioPdf.*;
import static org.mockito.Mockito.*;

class RelatorioPlanilhaTest {
    @Test void csvRecebeAReceitaInformadaAssimComoOPdf() {
        var relatorios = mock(RelatorioService.class);
        var horario = new com.estoq.core.helpers.HorarioOperacao();
        var dia = java.time.LocalDate.of(2026, 10, 20);
        var receita = new java.math.BigDecimal("1500");
        var resumo = mock(CmvResumoDTO.class);
        when(resumo.receitaBase()).thenReturn(receita);
        when(resumo.valorAjustesPositivosBalancos()).thenReturn(new java.math.BigDecimal("25.49"));
        when(relatorios.calcularCmv(horario.inicioUtc(dia), horario.inicioUtc(dia.plusDays(1)), receita)).thenReturn(resumo);
        var preparador = new PreparadorRelatorioService(horario, relatorios);
        var servico = new RelatorioExportacaoService(preparador, new RelatorioPdfService(preparador),
                new RelatorioCsvService(), new RelatorioXlsService(), horario);
        var arquivo = servico.gerar("cmv", "csv", dia, dia, 1, receita);
        String texto = new String(arquivo.conteudo(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(texto.contains("Faturamento do período"));
        assertTrue(texto.contains("1.500,00"));
        assertTrue(texto.contains("Ajustes positivos em balanços"));
        assertTrue(texto.contains("25,49"));
        assertFalse(texto.contains("Perdas não explicadas"));
        String xls = new String(servico.gerar("cmv", "xls", dia, dia, 1, receita).conteudo(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xls.contains("Ajustes positivos em balanços"));
        assertTrue(xls.contains("25,49"));
        assertFalse(xls.contains("Perdas não explicadas"));
        verify(relatorios, times(2)).calcularCmv(horario.inicioUtc(dia), horario.inicioUtc(dia.plusDays(1)), receita);
    }
    @Test void formatosMantemDadosETotaisEProtegemTextos() {
        var dados = new DocumentoRelatorioPdf("Estoque", "Posição atual", List.of(new Coluna("Produto", 1, false)),
                List.of(List.of("Tomate; \"italiano\"\nnovo"), List.of("=1+1"), List.of("<script>")),
                List.of(), null, List.of(new Destaque("Total (kg)", "3")));
        String csv = new RelatorioCsvService().gerar(dados);
        assertTrue(csv.contains("\"Tomate; \"\"italiano\"\"\nnovo\""));
        assertTrue(csv.contains("\"'=1+1\""));
        assertTrue(csv.contains("\"Total (kg)\";\"3\""));
        String xls = new RelatorioXlsService().gerar(dados);
        assertTrue(xls.contains("&lt;script&gt;")); assertFalse(xls.contains("<script>"));
        assertTrue(xls.contains("Total (kg): 3"));
        assertTrue(xls.contains("mso-number-format"));
    }
}

package com.estoq.business.relatorios;

import com.estoq.business.categorias.CategoriaModel;
import com.estoq.business.consumos.ConsumoModel;
import com.estoq.business.movimentacoesEstoque.IMovimentacaoEstoqueRepository;
import com.estoq.business.parametrosEstoque.ParametroEstoqueModel;
import com.estoq.business.produtos.*;
import com.estoq.core.helpers.HorarioOperacao;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReposicaoSugeridaTest {
    // 23h30 no Brasil: o UTC já está no dia seguinte.
    private final HorarioOperacao horario = new HorarioOperacao(Clock.fixed(
            Instant.parse("2026-10-25T02:30:00Z"), ZoneOffset.UTC));
    private final IProdutoRepository produtos = mock(IProdutoRepository.class);
    private final IMovimentacaoEstoqueRepository movimentos = mock(IMovimentacaoEstoqueRepository.class);
    private final EstoqueService estoque = mock(EstoqueService.class);
    private final RelatorioService relatorios = new RelatorioService(horario, estoque, null, null,
            null, produtos, movimentos, null, null);

    private ProdutoModel produto(long id, String manual, int periodo) {
        var categoria = new CategoriaModel(); categoria.setNome("Alimentos");
        var produto = new ProdutoModel(); produto.setId(id); produto.setNome("Produto " + id);
        produto.setCategoria(categoria); produto.setUnidadeMedida(UnidadeMedida.KG);
        var par = new ParametroEstoqueModel(); par.setConsumoMedioDiario(new BigDecimal(manual));
        par.setPeriodoAnaliseDias(periodo); produto.setParametro(par);
        return produto;
    }

    private ConsumoModel consumo(ProdutoModel produto, String quantidade, int diasAtras) {
        var c = new ConsumoModel(); c.setProduto(produto); c.setQuantidade(new BigDecimal(quantidade));
        c.setDataHora(horario.inicioUtc(horario.hoje().minusDays(diasAtras)).plusHours(23));
        return c;
    }

    @Test void automaticoTemPrioridadeIncluiHojeEBuscaTodosProdutosJuntos() {
        var arroz = produto(1, "99", 30);
        var feijao = produto(2, "88", 10);
        when(produtos.findAllByAtivoTrue()).thenReturn(List.of(arroz, feijao));
        var inicio = horario.inicioUtc(LocalDate.of(2026, 9, 25));
        var fim = horario.inicioUtc(LocalDate.of(2026, 10, 25));
        when(movimentos.consumosEntre(inicio, fim)).thenReturn(List.of(
                consumo(arroz, "15", 29), consumo(arroz, "15", 0),
                consumo(feijao, "100", 10), consumo(feijao, "10", 9)));
        var resultados = relatorios.reposicaoSugerida(null);
        assertEquals(2, resultados.size());
        for (var r : resultados) {
            assertEquals("AUTOMATICO", r.origemMedia());
            assertEquals(0, BigDecimal.ONE.compareTo(r.consumoMedioDiario()));
            assertEquals(0, new BigDecimal("7").compareTo(r.quantidadeSugerida()));
            assertEquals(LocalDate.of(2026, 10, 24), r.fimAnalise());
        }
        assertEquals(new BigDecimal("99"), arroz.getParametro().getConsumoMedioDiario());
        verify(movimentos, times(1)).consumosEntre(inicio, fim);
    }

    @Test void semHistoricoUsaManualEMantemPrazoCadastrado() {
        var arroz = produto(1, "2", 30); arroz.getParametro().setTempoReposicaoDias(3);
        var semMedia = produto(2, "0", 30);
        when(produtos.findAllByAtivoTrue()).thenReturn(List.of(arroz, semMedia));
        var resultado = relatorios.reposicaoSugerida(7);
        assertEquals(1, resultado.size());
        assertEquals("MANUAL", resultado.getFirst().origemMedia());
        assertEquals(0, new BigDecimal("6").compareTo(resultado.getFirst().quantidadeSugerida()));
        assertEquals(3, resultado.getFirst().diasReposicao());
    }

    @Test void arredondaTresCasasESemTrocarHistoricoPequenoPelaMediaManual() {
        var arroz = produto(1, "99", 30);
        var minimo = produto(2, "99", 30);
        when(produtos.findAllByAtivoTrue()).thenReturn(List.of(arroz, minimo));
        when(movimentos.consumosEntre(any(), any())).thenReturn(List.of(
                consumo(arroz, "1", 0), consumo(minimo, "0.001", 0)));
        var resultado = relatorios.reposicaoSugerida(null);
        assertEquals(1, resultado.size());
        assertEquals(new BigDecimal("0.033"), resultado.getFirst().consumoMedioDiario());
        assertEquals(new BigDecimal("0.231"), resultado.getFirst().quantidadeSugerida());
    }
}

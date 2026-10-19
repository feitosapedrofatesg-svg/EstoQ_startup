package com.estoq.business.relatorios;

import com.estoq.business.produtos.ProdutoModel;
import com.estoq.core.exceptions.FieldValidationException;

import com.estoq.core.helpers.HorarioOperacao;

import com.estoq.business.consumos.ConsumoModel;
import com.estoq.business.desperdicios.DesperdicioModel;
import com.estoq.business.desperdicios.DesperdicioClassificacao;
import com.estoq.business.entradas.EntradaModel;
import com.estoq.business.lotes.LoteDTO;
import com.estoq.business.lotes.LoteService;
import com.estoq.business.movimentacoesEstoque.IMovimentacaoEstoqueRepository;
import com.estoq.business.movimentacoesEstoque.MovimentacaoEstoqueModel;
import com.estoq.business.parametrosCmv.ParametroCmvService;
import com.estoq.business.produtos.EstoqueDTO;
import com.estoq.business.produtos.EstoqueService;
import com.estoq.business.produtos.IProdutoRepository;
import com.estoq.business.produtosAbertos.ProdutoAbertoDTO;
import com.estoq.business.produtosAbertos.ProdutoAbertoService;

import static com.estoq.core.helpers.NumeroUtil.money;
import static com.estoq.core.helpers.NumeroUtil.s;
import static com.estoq.core.helpers.NumeroUtil.zero;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelatorioService {

    private final HorarioOperacao horario;

    private final EstoqueService estoque;
    private final LoteService lotes;
    private final PosicaoHistoricaEstoqueService posicaoHistorica;
    private final ProdutoAbertoService abertos;
    private final IProdutoRepository produtos;
    private final IMovimentacaoEstoqueRepository movimentos;
    private final ParametroCmvService parametroCmv;
    private final CmvReceitaBaseService receitasCmv;

    public List<EstoqueDTO> estoqueAtual() {
        return estoque.listar(false, false);
    }

    public List<LoteDTO> proximosVencimento() {
        return lotes.proximosVencimento();
    }

    public List<LoteDTO> vencidos() {
        return lotes.vencidos();
    }

    public List<ProdutoAbertoDTO> produtosAbertos() {
        return abertos.listar(null, false);
    }

    public List<DesperdicioDTO> desperdicio(LocalDateTime inicio, LocalDateTime fim) {
        posicaoHistorica.validarPeriodo(inicio, fim);
        return movimentos.desperdiciosEntre(inicio, fim).stream()
                .map(d -> new DesperdicioDTO(d.getDataHora(), DesperdicioClassificacao.motivo(d), DesperdicioClassificacao.descricao(d),
                        d.getProduto().getId(), d.getProduto().getNome(), d.getLote().getCodigo(), DesperdicioClassificacao.quantidade(d),
                        DesperdicioClassificacao.valor(d), d.getProduto().getUnidadeMedida().name()))
                .toList();
    }

    public List<DesperdicioAgregadoDTO> desperdicioAgregado(LocalDateTime inicio, LocalDateTime fim) {
        posicaoHistorica.validarPeriodo(inicio, fim);
        var agregado = new HashMap<String, DesperdicioAgregadoDTO>();
        movimentos.desperdiciosEntre(inicio, fim).stream()
                .forEach(d -> {
                    var chave = d.getProduto().getId() + ":" + DesperdicioClassificacao.motivo(d).name();
                    var atual = agregado.get(chave);
                    if (atual == null) {
                        agregado.put(chave, new DesperdicioAgregadoDTO(d.getProduto().getId(), d.getProduto().getNome(),
                                DesperdicioClassificacao.motivo(d), DesperdicioClassificacao.quantidade(d), DesperdicioClassificacao.valor(d)));
                    } else {
                        agregado.put(chave,
                                new DesperdicioAgregadoDTO(atual.produtoId(), atual.produtoNome(), atual.motivo(),
                                        atual.quantidade().add(DesperdicioClassificacao.quantidade(d)),
                                        atual.valorPrejuizo().add(DesperdicioClassificacao.valor(d))));
                    }
                });
        return agregado.values().stream()
                .sorted(Comparator.comparing(DesperdicioAgregadoDTO::valorPrejuizo).reversed())
                .toList();
    }

    public List<ConsumoMedioDTO> consumoMedio(LocalDateTime inicio, LocalDateTime fim) {
        posicaoHistorica.validarPeriodo(inicio, fim);
        var dias = Math.max(1, ChronoUnit.DAYS.between(horario.diaLocal(inicio), horario.diaLocal(fim)));
        var porProduto = new TreeMap<Long, BigDecimal>();
        var nomes = new HashMap<Long, ProdutoModel>();
        for (var movimento : movimentos.consumosEntre(inicio, fim)) {
            var produto = movimento.getProduto();
            porProduto.merge(produto.getId(), movimento.getQuantidade(), BigDecimal::add);
            nomes.put(produto.getId(), produto);
        }
        return porProduto.entrySet().stream().map(e -> {
            var dto = nomes.get(e.getKey());
            var total = s(e.getValue());
            var nome = dto.getNome();
            var unidade = dto.getUnidadeMedida().name();
            return new ConsumoMedioDTO(e.getKey(), nome, unidade, total, dias,
                    total.divide(BigDecimal.valueOf(dias), 3, RoundingMode.HALF_UP));
        }).toList();
    }

    public List<ConsumoDiaSemanaDTO> consumoPorDiaSemana(LocalDateTime inicio, LocalDateTime fim) {
        posicaoHistorica.validarPeriodo(inicio, fim);
        var totalPorDia = new HashMap<DayOfWeek, BigDecimal>();
        movimentos.consumosEntre(inicio, fim).stream()
                .forEach(m -> totalPorDia.merge(horario.diaLocal(m.getDataHora()).getDayOfWeek(), m.getCustoConsumo(), BigDecimal::add));
        var diasPorDia = new HashMap<DayOfWeek, Long>();
        for (var dia = horario.diaLocal(inicio); dia.isBefore(horario.diaLocal(fim)); dia = dia.plusDays(1)) {
            diasPorDia.merge(dia.getDayOfWeek(), 1L, Long::sum);
        }
        var resultado = new ArrayList<ConsumoDiaSemanaDTO>();
        for (var dia : DayOfWeek.values()) {
            var dias = diasPorDia.getOrDefault(dia, 0L);
            if (dias == 0) {
                continue;
            }
            var total = totalPorDia.getOrDefault(dia, zero());
            resultado.add(new ConsumoDiaSemanaDTO(dia, total,
                    total.divide(BigDecimal.valueOf(dias), 3, RoundingMode.HALF_UP)));
        }
        return resultado;
    }

    /**
     * Entradas e consumos usam consultas por subtipo. A consulta de perdas inclui
     * desperdícios e ajustes negativos de balanço, cada movimentação uma vez.
     */
    private List<MovimentacaoEstoqueModel> movimentacoesEntre(LocalDateTime inicio, LocalDateTime fim) {
        var lista = new ArrayList<MovimentacaoEstoqueModel>();
        lista.addAll(movimentos.entradasEntre(inicio, fim));
        lista.addAll(movimentos.consumosEntre(inicio, fim));
        lista.addAll(movimentos.desperdiciosEntre(inicio, fim));
        return lista;
    }

    public CmvResumoDTO calcularCmv(LocalDateTime inicio, LocalDateTime fim, BigDecimal receitaBase) {
        var posicao = posicaoHistorica.obter(inicio, fim);
        var faturamento = receitasCmv.faturamento(horario.diaLocal(inicio), horario.diaLocal(fim.minusNanos(1)));
        boolean receitaPorPeriodo = receitaBase != null || faturamento.receitaPorPeriodo();
        if (receitaBase == null) receitaBase = faturamento.receitaBase();
        var historico = movimentacoesEntre(inicio, fim);
        var compras = zero();
        var consumido = zero();
        var desperdicio = zero();
        for (var m : historico) {
            if (m instanceof EntradaModel e) {
                compras = compras.add(money(e.getValorTotalPago()));
            } else if (m instanceof ConsumoModel c) {
                consumido = consumido.add(c.getCustoConsumo());
            } else if (m instanceof DesperdicioModel || DesperdicioClassificacao.perdaBalanco(m)) {
                desperdicio = desperdicio.add(DesperdicioClassificacao.valor(m));
            }
        }
        compras = money(compras);
        consumido = money(consumido);
        desperdicio = money(desperdicio);
        var estoqueInicial = posicao.inicial();
        var estoqueFinal = posicao.finalPeriodo();
        var cmv = money(estoqueInicial.add(compras).subtract(estoqueFinal));
        BigDecimal cmvPercentual = receitaBase != null && receitaBase.signum() > 0
                ? cmv.divide(receitaBase, 4, RoundingMode.HALF_UP).movePointRight(2)
                : null;
        var idealDto = parametroCmv.obterAtual();
        BigDecimal ideal = idealDto.percentualIdeal();
        BigDecimal diferenca = cmvPercentual != null && ideal != null ? cmvPercentual.subtract(ideal) : null;
        BigDecimal desperdicioSobre = cmv.signum() > 0
                ? desperdicio.divide(cmv, 4, RoundingMode.HALF_UP).movePointRight(2)
                : BigDecimal.ZERO;
        var perdasNaoExplicadas = money(cmv.subtract(consumido.add(desperdicio)));
        return new CmvResumoDTO(inicio, fim, estoqueInicial, compras, estoqueFinal, cmv, receitaBase,
                cmvPercentual == null ? null : cmvPercentual, ideal, diferenca, consumido, desperdicio,
                desperdicioSobre, perdasNaoExplicadas, faturamento.totalInformado(), faturamento.diasPendentes(), receitaPorPeriodo);
    }

    public List<CmvMensalDTO> cmvPorMes(LocalDateTime inicio, LocalDateTime fim) {
        posicaoHistorica.validarPeriodo(inicio, fim);
        var resultado = new ArrayList<CmvMensalDTO>();
        for (var mes = YearMonth.from(horario.diaLocal(inicio)); !mes.isAfter(YearMonth.from(horario.diaLocal(fim.minusNanos(1)))); mes = mes.plusMonths(1)) {
            var inicioMes = horario.inicioUtc(mes.atDay(1));
            var fimMes = horario.inicioUtc(mes.plusMonths(1).atDay(1));
            var resumo = calcularCmv(inicio.isAfter(inicioMes) ? inicio : inicioMes,
                    fim.isBefore(fimMes) ? fim : fimMes, null);
            resultado.add(new CmvMensalDTO(mes, resumo.cmv(), resumo.valorCompras(), resumo.valorDesperdicio()));
        }
        return resultado;
    }

    public List<ReposicaoSugeridaDTO> reposicaoSugerida(Integer dias) {
        if (dias != null && (dias < 1 || dias > 365))
            throw new FieldValidationException("dias", "Informe de 1 a 365 dias para reposição.");
        var reposDias = dias != null && dias > 0 ? dias : 7;
        var saldos = estoque.posicoesDisponiveis();
        var resultado = new ArrayList<ReposicaoSugeridaDTO>();
        for (var produto : produtos.findAllByAtivoTrue()) {
            var par = produto.getParametro();
            if (par == null) {
                continue;
            }
            var consumo = s(par.getConsumoMedioDiario());
            var lead = par.getTempoReposicaoDias() != null && par.getTempoReposicaoDias() > 0
                    ? par.getTempoReposicaoDias()
                    : reposDias;
            var posicao = saldos.get(produto.getId());
            var saldo = s(posicao == null ? null : posicao.getSaldo());
            var sugestao = consumo.multiply(BigDecimal.valueOf(lead)).subtract(saldo).setScale(3, RoundingMode.HALF_UP);
            if (sugestao.signum() <= 0) {
                continue;
            }
            resultado.add(new ReposicaoSugeridaDTO(produto.getId(), produto.getNome(), produto.getCategoria().getNome(),
                    produto.getUnidadeMedida(), saldo, s(par.getEstoqueMinimo()), consumo, lead, sugestao));
        }
        return resultado.stream().sorted(Comparator.comparing(ReposicaoSugeridaDTO::quantidadeSugerida).reversed())
                .toList();
    }
}

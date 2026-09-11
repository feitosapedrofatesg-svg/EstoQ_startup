package com.estoq.business.relatorios;

import com.estoq.business.consumos.ConsumoModel;
import com.estoq.business.desperdicios.DesperdicioModel;
import com.estoq.business.entradas.EntradaModel;
import com.estoq.business.lotes.ILoteRepository;
import com.estoq.business.lotes.LoteDTO;
import com.estoq.business.lotes.LoteService;
import com.estoq.business.movimentacoesEstoque.IMovimentacaoEstoqueRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelatorioService {

    private final EstoqueService estoque;
    private final LoteService lotes;
    private final ILoteRepository loteRepository;
    private final ProdutoAbertoService abertos;
    private final IProdutoRepository produtos;
    private final IMovimentacaoEstoqueRepository movimentos;
    private final ParametroCmvService parametroCmv;

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
        return movimentos.findByDataHoraLessThanOrderByDataHoraAscIdAsc(fim).stream()
                .filter(m -> m.getDataHora().compareTo(inicio) >= 0)
                .filter(DesperdicioModel.class::isInstance).map(DesperdicioModel.class::cast)
                .map(d -> new DesperdicioDTO(d.getDataHora(), d.getMotivo(), d.getDescricaoMotivo(),
                        d.getProduto().getId(), d.getProduto().getNome(), d.getLote().getCodigo(), d.getQuantidade(), d.getValorPrejuizo()))
                .toList();
    }

    public List<DesperdicioAgregadoDTO> desperdicioAgregado(LocalDateTime inicio, LocalDateTime fim) {
        var agregado = new HashMap<String, DesperdicioAgregadoDTO>();
        movimentos.findByDataHoraLessThanOrderByDataHoraAscIdAsc(fim).stream()
                .filter(m -> m.getDataHora().compareTo(inicio) >= 0 && m instanceof DesperdicioModel)
                .map(DesperdicioModel.class::cast)
                .forEach(d -> {
                    var chave = d.getProduto().getId() + ":" + d.getMotivo().name();
                    var atual = agregado.get(chave);
                    if (atual == null) {
                        agregado.put(chave, new DesperdicioAgregadoDTO(d.getProduto().getId(), d.getProduto().getNome(),
                                d.getMotivo(), d.getQuantidade(), d.getValorPrejuizo()));
                    } else {
                        agregado.put(chave, new DesperdicioAgregadoDTO(atual.produtoId(), atual.produtoNome(), atual.motivo(),
                                atual.quantidade().add(d.getQuantidade()), atual.valorPrejuizo().add(d.getValorPrejuizo())));
                    }
                });
        return agregado.values().stream()
                .sorted(Comparator.comparing(DesperdicioAgregadoDTO::valorPrejuizo).reversed())
                .toList();
    }

    public List<ConsumoMedioDTO> consumoMedio(LocalDateTime inicio, LocalDateTime fim) {
        var dias = Math.max(1, ChronoUnit.DAYS.between(inicio, fim));
        var porProduto = new TreeMap<Long, BigDecimal>();
        movimentos.findByDataHoraLessThanOrderByDataHoraAscIdAsc(fim).stream()
                .filter(m -> m.getDataHora().compareTo(inicio) >= 0 && m instanceof ConsumoModel)
                .forEach(m -> porProduto.merge(m.getProduto().getId(), m.getQuantidade(), BigDecimal::add));
        var nomes = estoque.listar(false, false).stream()
                .collect(Collectors.toMap(EstoqueDTO::produtoId, Function.identity()));
        return porProduto.entrySet().stream().map(e -> {
            var dto = nomes.get(e.getKey());
            var total = money(e.getValue());
            var nome = dto == null ? "?" : dto.produtoNome();
            var unidade = dto == null ? "" : dto.unidadeMedida().name();
            return new ConsumoMedioDTO(e.getKey(), nome, unidade, total, dias, total.divide(BigDecimal.valueOf(dias), 3, RoundingMode.HALF_UP));
        }).toList();
    }

    public List<ConsumoDiaSemanaDTO> consumoPorDiaSemana(LocalDateTime inicio, LocalDateTime fim) {
        var totalPorDia = new HashMap<DayOfWeek, BigDecimal>();
        movimentos.findByDataHoraLessThanOrderByDataHoraAscIdAsc(fim).stream()
                .filter(m -> m.getDataHora().compareTo(inicio) >= 0 && m instanceof ConsumoModel)
                .forEach(m -> totalPorDia.merge(m.getDataHora().getDayOfWeek(), m.getQuantidade(), BigDecimal::add));
        var diasPorDia = new HashMap<DayOfWeek, Long>();
        for (var dia = inicio.toLocalDate(); dia.isBefore(fim.toLocalDate()); dia = dia.plusDays(1)) {
            diasPorDia.merge(dia.getDayOfWeek(), 1L, Long::sum);
        }
        var resultado = new ArrayList<ConsumoDiaSemanaDTO>();
        for (var dia : DayOfWeek.values()) {
            var dias = diasPorDia.getOrDefault(dia, 0L);
            if (dias == 0) {
                continue;
            }
            var total = totalPorDia.getOrDefault(dia, zero());
            resultado.add(new ConsumoDiaSemanaDTO(dia, total, total.divide(BigDecimal.valueOf(dias), 3, RoundingMode.HALF_UP)));
        }
        return resultado;
    }

    public CmvResumoDTO calcularCmv(LocalDateTime inicio, LocalDateTime fim, BigDecimal receitaBase) {
        var historico = movimentos.findByDataHoraLessThanOrderByDataHoraAscIdAsc(fim).stream()
                .filter(m -> m.getDataHora().compareTo(inicio) >= 0).toList();
        var deltaPorLote = new HashMap<Long, BigDecimal>();
        var compras = zero();
        var consumido = zero();
        var desperdicio = zero();
        for (var m : historico) {
            var loteId = m.getLote() == null ? null : m.getLote().getId();
            if (loteId != null) {
                deltaPorLote.merge(loteId, m.getDelta(), BigDecimal::add);
            }
            if (m instanceof EntradaModel e) {
                compras = compras.add(money(e.getValorTotalPago()));
            } else if (m instanceof ConsumoModel c) {
                consumido = consumido.add(c.getCustoConsumo());
            } else if (m instanceof DesperdicioModel d) {
                desperdicio = desperdicio.add(d.getValorPrejuizo());
            }
        }
        compras = money(compras);
        consumido = money(consumido);
        desperdicio = money(desperdicio);
        var estoqueFinal = zero();
        var estoqueInicial = zero();
        var today = LocalDate.now();
        for (var lote : loteRepository.listarEstoque()) {
            var preco = money(lote.getPrecoUnitario());
            var atual = money(lote.getQuantidadeAtual());
            estoqueFinal = estoqueFinal.add(atual.multiply(preco).setScale(2, RoundingMode.HALF_UP));
            var delta = s(deltaPorLote.get(lote.getId()));
            var inicioQtd = atual.subtract(delta).max(BigDecimal.ZERO);
            estoqueInicial = estoqueInicial.add(inicioQtd.multiply(preco).setScale(2, RoundingMode.HALF_UP));
        }
        estoqueInicial = money(estoqueInicial);
        estoqueFinal = money(estoqueFinal);
        var cmv = money(estoqueInicial.add(compras).subtract(estoqueFinal));
        BigDecimal cmvPercentual = receitaBase != null && receitaBase.signum() > 0
                ? cmv.divide(receitaBase, 4, RoundingMode.HALF_UP).movePointRight(2) : null;
        var idealDto = parametroCmv.obterAtual();
        BigDecimal ideal = idealDto.percentualIdeal();
        BigDecimal diferenca = cmvPercentual != null && ideal != null ? cmvPercentual.subtract(ideal) : null;
        BigDecimal desperdicioSobre = cmv.signum() > 0 ? desperdicio.divide(cmv, 4, RoundingMode.HALF_UP).movePointRight(2) : BigDecimal.ZERO;
        var perdasNaoExplicadas = money(cmv.subtract(consumido.add(desperdicio)));
        return new CmvResumoDTO(inicio, fim, estoqueInicial, compras, estoqueFinal, cmv, receitaBase,
                cmvPercentual == null ? null : cmvPercentual, ideal, diferenca, consumido, desperdicio, desperdicioSobre, perdasNaoExplicadas);
    }

    public List<CmvMensalDTO> cmvPorMes(LocalDateTime inicio, LocalDateTime fim) {
        var meses = new ArrayList<YearMonth>();
        for (var ym = YearMonth.from(inicio); !ym.isAfter(YearMonth.from(fim)); ym = ym.plusMonths(1)) {
            meses.add(ym);
        }
        var n = meses.size();
        var indiceMes = new HashMap<YearMonth, Integer>();
        for (int i = 0; i < n; i++) {
            indiceMes.put(meses.get(i), i);
        }
        var deltaPorLote = new HashMap<Long, BigDecimal[]>();
        var comprasMes = new BigDecimal[n];
        var desperdicioMes = new BigDecimal[n];
        Arrays.fill(comprasMes, zero());
        Arrays.fill(desperdicioMes, zero());
        for (var m : movimentos.findByDataHoraLessThanOrderByDataHoraAscIdAsc(fim)) {
            if (m.getDataHora().compareTo(inicio) < 0) {
                continue;
            }
            var k = indiceMes.get(YearMonth.from(m.getDataHora()));
            var loteId = m.getLote() == null ? null : m.getLote().getId();
            if (loteId != null) {
                var deltas = deltaPorLote.computeIfAbsent(loteId, x -> {
                    var arr = new BigDecimal[n];
                    Arrays.fill(arr, zero());
                    return arr;
                });
                deltas[k] = deltas[k].add(m.getDelta());
            }
            if (m instanceof EntradaModel e) {
                comprasMes[k] = comprasMes[k].add(money(e.getValorTotalPago()));
            } else if (m instanceof DesperdicioModel d) {
                desperdicioMes[k] = desperdicioMes[k].add(d.getValorPrejuizo());
            }
        }
        var estoqueInicialMes = new BigDecimal[n];
        var estoqueFinalMes = new BigDecimal[n];
        Arrays.fill(estoqueInicialMes, zero());
        Arrays.fill(estoqueFinalMes, zero());
        for (var lote : loteRepository.listarEstoque()) {
            var deltas = deltaPorLote.get(lote.getId());
            var preco = money(lote.getPrecoUnitario());
            var atual = money(lote.getQuantidadeAtual());
            var devolvido = zero();
            for (int k = n - 1; k >= 0; k--) {
                var delta = deltas == null ? zero() : s(deltas[k]);
                var fimQtd = atual.subtract(devolvido).max(BigDecimal.ZERO);
                var iniQtd = fimQtd.subtract(delta).max(BigDecimal.ZERO);
                estoqueFinalMes[k] = estoqueFinalMes[k].add(fimQtd.multiply(preco).setScale(2, RoundingMode.HALF_UP));
                estoqueInicialMes[k] = estoqueInicialMes[k].add(iniQtd.multiply(preco).setScale(2, RoundingMode.HALF_UP));
                devolvido = devolvido.add(delta);
            }
        }
        var resultado = new ArrayList<CmvMensalDTO>();
        for (int k = 0; k < n; k++) {
            var cmv = money(money(estoqueInicialMes[k]).add(money(comprasMes[k])).subtract(money(estoqueFinalMes[k])));
            resultado.add(new CmvMensalDTO(meses.get(k), cmv, money(comprasMes[k]), money(desperdicioMes[k])));
        }
        return resultado;
    }

    public List<ReposicaoSugeridaDTO> reposicaoSugerida(Integer dias) {
        var reposDias = dias != null && dias > 0 ? dias : 7;
        var saldos = estoque.posicoes();
        var resultado = new ArrayList<ReposicaoSugeridaDTO>();
        for (var produto : produtos.findAllByAtivoTrue()) {
            var par = produto.getParametro();
            if (par == null) {
                continue;
            }
            var consumo = s(par.getConsumoMedioDiario());
            var lead = par.getTempoReposicaoDias() != null && par.getTempoReposicaoDias() > 0 ? par.getTempoReposicaoDias() : reposDias;
            var posicao = saldos.get(produto.getId());
            var saldo = s(posicao == null ? null : posicao.getSaldo());
            var sugestao = consumo.multiply(BigDecimal.valueOf(lead)).subtract(saldo).setScale(3, RoundingMode.HALF_UP);
            if (sugestao.signum() <= 0) {
                continue;
            }
            resultado.add(new ReposicaoSugeridaDTO(produto.getId(), produto.getNome(), produto.getCategoria().getNome(),
                    produto.getUnidadeMedida(), saldo, s(par.getEstoqueMinimo()), consumo, lead, sugestao));
        }
        return resultado.stream().sorted(Comparator.comparing(ReposicaoSugeridaDTO::quantidadeSugerida).reversed()).toList();
    }
}
package com.estoq.business.dashboard;

import com.estoq.business.alertas.IAlertaRepository;
import com.estoq.business.balancos.IBalancoRepository;
import com.estoq.business.balancos.StatusBalanco;
import com.estoq.business.lotes.LoteService;
import com.estoq.business.produtos.EstoqueService;
import com.estoq.business.produtos.IProdutoRepository;
import com.estoq.business.produtosAbertos.IProdutoAbertoRepository;
import com.estoq.business.relatorios.RelatorioService;

import static com.estoq.core.helpers.NumeroUtil.s;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final IProdutoRepository produtos;
    private final IProdutoAbertoRepository abertos;
    private final IBalancoRepository balancos;
    private final EstoqueService estoque;
    private final LoteService lotes;
    private final RelatorioService relatorios;
    private final IAlertaRepository alertas;

    public DashboardResumoDTO resumo(LocalDateTime inicio, LocalDateTime fim, BigDecimal receitaBase) {
        var cmv = relatorios.calcularCmv(inicio, fim, receitaBase);
        return new DashboardResumoDTO(
                produtos.countByAtivoTrue(),
                estoque.listar(false, true).size(),
                lotes.proximosVencimento().size(),
                lotes.vencidos().size(),
                abertos.countByAtivoTrueAndFinalizadoFalse(),
                balancos.countByAtivoTrueAndStatusNot(StatusBalanco.CONCLUIDO),
                s(cmv.valorDesperdicio()), s(cmv.cmv()), cmv.cmvPercentual(), cmv.percentualIdeal(), cmv.diferencaPercentualParaMeta(),
                s(cmv.valorPerdasNaoExplicadas()));
    }
}
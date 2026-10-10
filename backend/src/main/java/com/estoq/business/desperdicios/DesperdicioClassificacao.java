package com.estoq.business.desperdicios;

import com.estoq.business.movimentacoesEstoque.MovimentacaoEstoqueModel;
import com.estoq.business.movimentacoesEstoque.TipoMovimentacao;
import com.estoq.core.helpers.NumeroUtil;

import java.math.BigDecimal;

/** Déficits de balanço já baixados são perdas, mantendo a trilha original de ajuste. */
public final class DesperdicioClassificacao {

    private DesperdicioClassificacao() {
    }

    public static boolean perdaBalanco(MovimentacaoEstoqueModel movimento) {
        return movimento.getTipo() == TipoMovimentacao.AJUSTE
                && movimento.getItemBalanco() != null && movimento.getDelta().signum() < 0;
    }

    public static BigDecimal quantidade(MovimentacaoEstoqueModel movimento) {
        return perdaBalanco(movimento) ? movimento.getDelta().negate() : movimento.getQuantidade();
    }

    public static BigDecimal valor(MovimentacaoEstoqueModel movimento) {
        if (movimento instanceof DesperdicioModel desperdicio) {
            return desperdicio.getValorPrejuizo();
        }
        return NumeroUtil.money(quantidade(movimento).multiply(movimento.getLote().getPrecoUnitario()));
    }

    public static MotivoDesperdicio motivo(MovimentacaoEstoqueModel movimento) {
        return movimento instanceof DesperdicioModel desperdicio ? desperdicio.getMotivo() : MotivoDesperdicio.OUTRO;
    }

    public static String descricao(MovimentacaoEstoqueModel movimento) {
        return movimento instanceof DesperdicioModel desperdicio ? desperdicio.getDescricaoMotivo()
                : "Diferença negativa de balanço";
    }
}

package com.estoq.business.movimentacoesEstoque;

import com.estoq.business.consumos.ConsumoModel;
import com.estoq.business.desperdicios.DesperdicioModel;
import com.estoq.business.entradas.EntradaModel;

import org.springframework.stereotype.Component;

@Component
public class MovimentacaoAdapter {

    public MovimentacaoDTO toDto(MovimentacaoEstoqueModel m) {
        var l = m.getLote();
        return new MovimentacaoDTO(m.getId(), m.getTipo(), m.getDataHora(), m.getProduto().getId(), m.getProduto().getNome(),
                l == null ? null : l.getId(), l == null ? null : l.getCodigo(), l == null ? null : l.getVersion(),
                l == null ? null : l.getQuantidadeAtual(),
                m.getUsuario().getId(), m.getProdutoAberto() == null ? null : m.getProdutoAberto().getId(),
                m.getQuantidade(), m.getQuantidadeAnterior(), m.getQuantidadePosterior(), l == null ? null : l.getPrecoUnitario(),
                m instanceof ConsumoModel c ? c.getCustoConsumo() : null,
                m instanceof DesperdicioModel d ? d.getValorPrejuizo() : null,
                m instanceof EntradaModel e ? e.getValorTotalPago() : null,
                m.getTipo() == TipoMovimentacao.AJUSTE ? m.getDelta() : null,
                m instanceof DesperdicioModel d ? d.getMotivo().name() : null,
                m instanceof DesperdicioModel d ? d.getDescricaoMotivo() : null,
                m.getObservacao());
    }
}
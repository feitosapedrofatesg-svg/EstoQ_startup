package com.estoq.business.lotes;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class LoteAdapter {

    public LoteDTO toDto(LoteModel l) {
        var hoje = LocalDate.now();
        return new LoteDTO(l.getId(), l.getVersion(), l.getCodigo(), l.getProduto().getId(), l.getProduto().getNome(),
                l.getProduto().getUnidadeMedida(), l.getQuantidadeInicial(), l.getQuantidadeAtual(), l.getDataEntrada(),
                l.getDataValidade(), l.getPrecoUnitario(), l.estaVencido(hoje), l.estaDisponivel(hoje), l.diasParaVencimento(hoje));
    }
}
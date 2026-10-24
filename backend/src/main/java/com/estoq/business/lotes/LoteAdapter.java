package com.estoq.business.lotes;

import com.estoq.core.helpers.HorarioOperacao;

import org.springframework.stereotype.Component;


@Component
@lombok.RequiredArgsConstructor
public class LoteAdapter {

    private final HorarioOperacao horario;

    public LoteDTO toDto(LoteModel l) {
        var hoje = horario.hoje();
        return new LoteDTO(l.getId(), l.getVersion(), l.getCodigo(), l.getProduto().getId(), l.getProduto().getNome(),
                l.getProduto().getUnidadeMedida(), l.getQuantidadeInicial(), l.getQuantidadeAtual(), l.getDataEntrada(),
                l.getDataValidade(), l.getPrecoUnitario(), l.estaVencido(hoje), l.estaDisponivel(hoje), l.diasParaVencimento(hoje), l.getLoteFabricante());
    }
}
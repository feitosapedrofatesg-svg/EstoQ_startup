package com.estoq.business.balancos;

import com.estoq.business.itensBalanco.ItemBalancoModel;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class BalancoAdapter {

    public ItemBalancoDTO toItemDto(ItemBalancoModel item) {
        return new ItemBalancoDTO(item.getId(), item.getProduto().getId(), item.getProduto().getNome(),
                item.getProduto().getUnidadeMedida().name(), item.getQuantidadeSistema(), item.getQuantidadeFisica(),
                item.getQuantidadeFisica() == null ? BigDecimal.ZERO : item.getQuantidadeFisica().subtract(item.getQuantidadeSistema()),
                item.isAjusteAplicado());
    }

    public BalancoDTO toDto(BalancoModel b) {
        var itens = b.getItens().isEmpty() ? List.<ItemBalancoDTO>of()
                : b.getItens().stream().map(this::toItemDto).toList();
        return new BalancoDTO(b.getId(), b.getVersion(), b.getDataHora(), b.getTipo(), b.getStatus(),
                b.getUsuario() == null ? null : b.getUsuario().getId(), b.getUsuario() == null ? null : b.getUsuario().getNome(), itens);
    }
}
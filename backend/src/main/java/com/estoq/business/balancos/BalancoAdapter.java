package com.estoq.business.balancos;

import com.estoq.business.itensBalanco.ItemBalancoModel;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class BalancoAdapter {

    public ItemBalancoDTO toItemDto(ItemBalancoModel item) {
        var p = item.getProduto();
        var c = p.getCategoria();
        return new ItemBalancoDTO(item.getId(), p.getId(), p.getNome(),
                p.getUnidadeMedida().name(), c == null ? null : c.getId(), c == null ? null : c.getNome(),
                item.getQuantidadeSistema(), item.getQuantidadeFisica(),
                item.getQuantidadeFisica() == null ? null : item.getQuantidadeFisica().subtract(item.getQuantidadeSistema()),
                item.isAjusteAplicado());
    }

    public BalancoDTO toDto(BalancoModel b) {
        var itens = b.getItens().isEmpty() ? List.<ItemBalancoDTO>of()
                : b.getItens().stream().map(this::toItemDto).toList();
        var categoriaIds = b.getCategorias().stream().map(c -> c.getId()).toList();
        var categoriaNomes = b.getCategorias().stream().map(c -> c.getNome()).toList();
        return new BalancoDTO(b.getId(), b.getVersion(), b.getDataHora(), b.getTipo(), b.getStatus(),
                b.getUsuario() == null ? null : b.getUsuario().getId(), b.getUsuario() == null ? null : b.getUsuario().getNome(),
                categoriaIds, categoriaNomes, itens);
    }
}
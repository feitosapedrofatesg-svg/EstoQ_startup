package com.estoq.business.produtosAbertos;

import com.estoq.business.produtos.EstoqueService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProdutoAbertoAdapter {

    private final EstoqueService estoque;

    public ProdutoAbertoDTO toDto(ProdutoAbertoModel a) {
        var l = a.getLote();
        var p = l.getProduto();
        return new ProdutoAbertoDTO(a.getId(), a.getVersion(), p.getId(), p.getNome(), p.getUnidadeMedida().name(), l.getId(),
                l.getCodigo(), l.getDataValidade(), a.getUsuario() == null ? null : a.getUsuario().getId(), a.getDataAbertura(),
                a.getQuantidadeAberta(), a.getQuantidadeUtilizada(), a.getQuantidadeRestante(), a.isFinalizado(),
                l.getQuantidadeAtual(), estoque.obterSaldo(p.getId()));
    }
}
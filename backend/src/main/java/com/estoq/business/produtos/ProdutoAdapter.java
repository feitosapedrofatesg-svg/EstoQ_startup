package com.estoq.business.produtos;

import com.estoq.core.helpers.IGenericAdapter;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ProdutoAdapter implements IGenericAdapter<ProdutoModel, ProdutoDTO> {

    private final EstoqueService estoque;

    @Override
    public ProdutoModel toEntity(ProdutoDTO d) {
        var p = new ProdutoModel();
        updateEntity(d, p);
        return p;
    }

    @Override
    public void updateEntity(ProdutoDTO d, ProdutoModel p) {
        p.setNome(d.getNome().trim());
        p.setUnidadeMedida(d.getUnidadeMedida());
        p.setCodigoBarras(d.getCodigoBarras());
    }

    @Override
    public ProdutoDTO toDto(ProdutoModel p) {
        return toDto(p, estoque.obterSaldo(p.getId()));
    }

    public ProdutoDTO toDto(ProdutoModel p, BigDecimal saldo) {
        var d = base(p, new ProdutoDTO());
        d.setNome(p.getNome());
        d.setUnidadeMedida(p.getUnidadeMedida());
        d.setCodigoBarras(p.getCodigoBarras());
        d.setCategoriaId(p.getCategoria().getId());
        d.setCategoriaNome(p.getCategoria().getNome());
        d.setParametroEstoqueId(p.getParametro() == null ? null : p.getParametro().getId());
        d.setSaldoAtual(saldo);
        return d;
    }
}
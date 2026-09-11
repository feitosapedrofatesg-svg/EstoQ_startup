package com.estoq.business.categorias;

import com.estoq.core.helpers.IGenericAdapter;

import org.springframework.stereotype.Component;

@Component
public class CategoriaAdapter implements IGenericAdapter<CategoriaModel, CategoriaDTO> {

    @Override
    public CategoriaModel toEntity(CategoriaDTO dto) {
        var c = new CategoriaModel();
        updateEntity(dto, c);
        return c;
    }

    @Override
    public void updateEntity(CategoriaDTO dto, CategoriaModel c) {
        c.setNome(dto.getNome().trim());
        c.setDescricao(dto.getDescricao());
    }

    @Override
    public CategoriaDTO toDto(CategoriaModel c) {
        var dto = base(c, new CategoriaDTO());
        dto.setNome(c.getNome());
        dto.setDescricao(c.getDescricao());
        return dto;
    }
}
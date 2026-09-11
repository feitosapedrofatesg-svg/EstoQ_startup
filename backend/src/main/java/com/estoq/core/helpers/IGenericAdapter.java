package com.estoq.core.helpers;

import com.estoq.core.domains.BaseModel;
import com.estoq.core.dtos.BaseDTO;

public interface IGenericAdapter<E extends BaseModel, D extends BaseDTO> {

    E toEntity(D dto);
    void updateEntity(D dto, E entity);
    D toDto(E entity);

    default D base(E entity, D dto) {
        dto.setId(entity.getId());
        dto.setVersion(entity.getVersion());
        dto.setAtivo(entity.isAtivo());
        dto.setDataHoraCriacao(entity.getDataHoraCriacao());
        return dto;
    }
}
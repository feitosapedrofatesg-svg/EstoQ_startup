package com.estoq.core.services;

import com.estoq.core.dtos.BaseDTO;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IGenericService<D extends BaseDTO> {

    D buscar(Long id);
    Page<D> listar(Pageable pageable);
    default Page<D> listar(Pageable pageable, String q) {
        return listar(pageable);
    }
    D criar(D dto);
    D atualizar(Long id, D dto);
    void excluir(Long id);
}
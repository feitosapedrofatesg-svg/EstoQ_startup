package com.estoq.business.categorias;

import com.estoq.core.repositories.IGenericRepository;

public interface ICategoriaRepository extends IGenericRepository<CategoriaModel> {

    boolean existsByNomeIgnoreCaseAndAtivoTrue(String nome);
    boolean existsByNomeIgnoreCaseAndAtivoTrueAndIdNot(String nome, Long id);
}
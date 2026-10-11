package com.estoq.business.categorias;

import com.estoq.core.repositories.IGenericRepository;

public interface ICategoriaRepository extends IGenericRepository<CategoriaModel> {

    java.util.List<CategoriaModel> findAllByRestauranteIdAndAtivoTrue(Long restauranteId);

    boolean existsByNomeIgnoreCaseAndAtivoTrue(String nome);
    boolean existsByNomeIgnoreCaseAndAtivoTrueAndIdNot(String nome, Long id);
}
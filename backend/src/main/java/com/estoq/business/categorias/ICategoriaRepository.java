package com.estoq.business.categorias;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.estoq.core.repositories.IGenericRepository;

public interface ICategoriaRepository extends IGenericRepository<CategoriaModel> {

    Page<CategoriaModel> findByAtivoTrueAndNomeContainingIgnoreCase(String nome, Pageable pageable);

    java.util.List<CategoriaModel> findAllByRestauranteIdAndAtivoTrue(Long restauranteId);

    boolean existsByNomeIgnoreCaseAndAtivoTrue(String nome);
    boolean existsByNomeIgnoreCaseAndAtivoTrueAndIdNot(String nome, Long id);
}

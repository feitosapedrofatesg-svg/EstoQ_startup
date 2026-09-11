package com.estoq.business.produtos;

import com.estoq.core.repositories.IGenericRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface IProdutoRepository extends IGenericRepository<ProdutoModel> {

    boolean existsByCategoria_IdAndAtivoTrue(Long categoriaId);
    @EntityGraph(attributePaths = {"categoria", "parametro"})
    Optional<ProdutoModel> findByIdAndAtivoTrue(Long id);
    @EntityGraph(attributePaths = {"categoria", "parametro"})
    Page<ProdutoModel> findAllByAtivoTrue(Pageable pageable);
    @EntityGraph(attributePaths = {"categoria", "parametro"})
    List<ProdutoModel> findAllByAtivoTrue();
}
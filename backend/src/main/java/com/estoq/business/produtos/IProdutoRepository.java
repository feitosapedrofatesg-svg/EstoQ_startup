package com.estoq.business.produtos;

import org.springframework.data.jpa.repository.Query;

import com.estoq.core.repositories.IGenericRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface IProdutoRepository extends IGenericRepository<ProdutoModel> {
    @EntityGraph(attributePaths = {"categoria", "parametro"})
    List<ProdutoModel> findAllByAtivoTrueAndIdIn(List<Long> ids);

    @EntityGraph(attributePaths = {"categoria", "parametro"})
    @Query("select p from ProdutoModel p where p.ativo=true and "
            + "(lower(p.nome) like lower(concat('%',:q,'%')) or lower(p.categoria.nome) like lower(concat('%',:q,'%')) "
            + "or p.codigoBarras like concat('%',:q,'%'))")
    Page<ProdutoModel> pesquisar(String q, Pageable pageable);

    boolean existsByCategoria_IdAndAtivoTrue(Long categoriaId);
    @EntityGraph(attributePaths = {"categoria", "parametro"})
    Optional<ProdutoModel> findByIdAndAtivoTrue(Long id);
    @EntityGraph(attributePaths = {"categoria", "parametro"})
    Page<ProdutoModel> findAllByAtivoTrue(Pageable pageable);
    @EntityGraph(attributePaths = {"categoria", "parametro"})
    List<ProdutoModel> findAllByAtivoTrue();
    @EntityGraph(attributePaths = {"categoria", "parametro"})
    List<ProdutoModel> findAllByAtivoTrueAndCategoria_IdIn(List<Long> categoriaIds);
}

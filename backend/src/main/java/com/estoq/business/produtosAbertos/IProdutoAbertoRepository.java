package com.estoq.business.produtosAbertos;

import com.estoq.core.repositories.IGenericRepository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface IProdutoAbertoRepository extends IGenericRepository<ProdutoAbertoModel> {

    @EntityGraph(attributePaths = {"lote", "lote.produto", "usuario"})
    Optional<ProdutoAbertoModel> findByIdAndAtivoTrue(Long id);

    @Query("select a from ProdutoAbertoModel a join fetch a.lote l join fetch l.produto p left join fetch a.usuario "
            + "where a.ativo=true and (:produtoId is null or p.id=:produtoId) and (:finalizado is null or a.finalizado=:finalizado) "
            + "order by a.dataAbertura, a.id")
    List<ProdutoAbertoModel> listar(Long produtoId, Boolean finalizado);

    List<ProdutoAbertoModel> findByLote_IdAndAtivoTrueAndFinalizadoFalseOrderByDataAberturaAscIdAsc(Long loteId);

    @Query("select coalesce(sum(a.quantidadeAberta-a.quantidadeUtilizada),0) from ProdutoAbertoModel a "
            + "where a.lote.id=:loteId and a.ativo=true and a.finalizado=false")
    BigDecimal restanteNoLote(Long loteId);

    @Query("select distinct a.lote.produto.id from ProdutoAbertoModel a where a.ativo=true and a.finalizado=false")
    List<Long> produtosComAbertos();

    long countByAtivoTrueAndFinalizadoFalse();
}
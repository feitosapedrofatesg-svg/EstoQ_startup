package com.estoq.business.produtos;

import org.springframework.data.jpa.repository.Query;

import com.estoq.core.repositories.IGenericRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface IProdutoRepository extends IGenericRepository<ProdutoModel> {
    String FILTRO_ESTOQUE = "from ProdutoModel p left join p.parametro par where p.ativo=true "
        + "and (:q='' or lower(p.nome) like lower(concat('%',:q,'%')) or lower(p.categoria.nome) like lower(concat('%',:q,'%'))) "
        + "and (:abertos=false or exists(select a.id from ProdutoAbertoModel a where a.lote.produto=p and a.ativo=true and a.finalizado=false)) "
        + "and (:baixo=false or (par.estoqueMinimo is not null and coalesce((select sum(l.quantidadeAtual) from LoteModel l where l.produto=p and l.ativo=true and l.quantidadeAtual>0 and (l.dataValidade is null or l.dataValidade>=:hoje)),0)<par.estoqueMinimo)) "
        + "and (:situacao='TODOS' or (:situacao='VENCIDO' and exists(select l.id from LoteModel l where l.produto=p and l.ativo=true and l.quantidadeAtual>0 and l.dataValidade<:hoje)) "
        + "or (:situacao='PROXIMO' and par.ativo=true and exists(select l.id from LoteModel l where l.produto=p and l.ativo=true and l.quantidadeAtual>0 and l.dataValidade>=:hoje and timestampdiff(day,:hoje,l.dataValidade)<=par.diasAlertaVencimento)))";

    @EntityGraph(attributePaths={"categoria","parametro"})
    @Query("select p " + FILTRO_ESTOQUE)
    Page<ProdutoModel> estoqueFiltrado(String q, boolean abertos, boolean baixo, String situacao, java.time.LocalDate hoje, Pageable pageable);

    @Query("select p.id " + FILTRO_ESTOQUE)
    List<Long> idsEstoque(String q, boolean abertos, boolean baixo, String situacao, java.time.LocalDate hoje);

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

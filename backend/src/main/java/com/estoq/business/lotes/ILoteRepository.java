package com.estoq.business.lotes;

import com.estoq.core.repositories.IGenericRepository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ILoteRepository extends IGenericRepository<LoteModel> {

    @EntityGraph(attributePaths = {"produto", "produto.categoria", "produto.parametro"})
    Optional<LoteModel> findByIdAndAtivoTrue(Long id);

    boolean existsByProduto_Id(Long produtoId);

    @Query("select l from LoteModel l join fetch l.produto p left join fetch p.parametro "
            + "where l.ativo=true and p.ativo=true order by l.dataValidade asc nulls last, l.id")
    List<LoteModel> listarEstoque();

    @Query("select l from LoteModel l where l.produto.id=:produtoId and l.ativo=true and l.quantidadeAtual>0 "
            + "and (l.dataValidade is null or l.dataValidade>=:hoje) order by l.dataValidade asc nulls last, l.dataEntrada, l.id")
    List<LoteModel> findDisponiveisBaixa(Long produtoId, LocalDate hoje);

    @Query("select l from LoteModel l where l.produto.id=:produtoId and l.ativo=true and l.quantidadeAtual>0 "
            + "order by l.dataValidade asc nulls last, l.dataEntrada, l.id")
    List<LoteModel> findTodosBaixa(Long produtoId);

    @Query("select coalesce(sum(l.quantidadeAtual),0) from LoteModel l where l.produto.id=:produtoId and l.ativo=true")
    BigDecimal saldo(Long produtoId);

    @Query("select l.produto.id as produtoId, sum(l.quantidadeAtual) as saldo, "
            + "sum(l.quantidadeAtual*l.precoUnitario) as valor from LoteModel l where l.ativo=true group by l.produto.id")
    List<PosicaoProduto> posicoes();

    interface PosicaoProduto {
        Long getProdutoId();
        BigDecimal getSaldo();
        BigDecimal getValor();
    }
}
package com.estoq.business.lotes;

import com.estoq.core.repositories.IGenericRepository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ILoteRepository extends IGenericRepository<LoteModel> {

        // Uma consulta obtém os dois cortes no mesmo retrato do banco, incluindo lotes desativados.
        @Query("select l.quantidadeAtual as atual, l.precoUnitario as preco, "
                        + "coalesce(sum(m.quantidadePosterior-m.quantidadeAnterior),0) as deltaInicio, "
                        + "coalesce(sum(case when m.dataHora>=:fim then m.quantidadePosterior-m.quantidadeAnterior else 0 end),0) as deltaFim "
                        + "from LoteModel l left join MovimentacaoEstoqueModel m on m.lote=l and m.dataHora>=:inicio "
                        + "group by l.id, l.quantidadeAtual, l.precoUnitario")
        List<PosicaoHistorica> posicoesHistoricas(LocalDateTime inicio, LocalDateTime fim);

        interface PosicaoHistorica {
                BigDecimal getAtual();
                BigDecimal getPreco();
                BigDecimal getDeltaInicio();
                BigDecimal getDeltaFim();
        }

        @EntityGraph(attributePaths = { "produto", "produto.categoria", "produto.parametro" })
        Optional<LoteModel> findByIdAndAtivoTrue(Long id);

        boolean existsByProduto_Id(Long produtoId);

        @Query("select l from LoteModel l join fetch l.produto p left join fetch p.parametro "
                        + "where l.ativo=true and p.ativo=true order by l.dataValidade asc nulls last, l.id")
        List<LoteModel> listarEstoque();

        @Query("select l.id as loteId, l.quantidadeAtual as quantidadeAtual, l.precoUnitario as precoUnitario "
                        + "from LoteModel l where l.ativo=true and l.produto.ativo=true")
        List<EstoqueCmv> listarEstoqueCmv();

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

        @Query("select l.produto.id as produtoId, sum(l.quantidadeAtual) as saldo, "
                        + "sum(l.quantidadeAtual*l.precoUnitario) as valor from LoteModel l where l.ativo=true "
                        + "and l.quantidadeAtual>0 and (l.dataValidade is null or l.dataValidade>=:hoje) "
                        + "group by l.produto.id")
        List<PosicaoProduto> posicoesDisponiveis(@Param("hoje") LocalDate hoje);

        interface PosicaoProduto {
                Long getProdutoId();

                BigDecimal getSaldo();

                BigDecimal getValor();
        }

        interface EstoqueCmv {
                Long getLoteId();

                BigDecimal getQuantidadeAtual();

                BigDecimal getPrecoUnitario();
        }
}

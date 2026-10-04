package com.estoq.business.movimentacoesEstoque;

import com.estoq.business.consumos.ConsumoModel;
import com.estoq.business.desperdicios.DesperdicioModel;
import com.estoq.business.entradas.EntradaModel;
import com.estoq.core.repositories.IGenericRepository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface IMovimentacaoEstoqueRepository extends IGenericRepository<MovimentacaoEstoqueModel> {

        @EntityGraph(attributePaths = { "produto", "lote", "usuario", "produtoAberto" })
        @Query("select e from EntradaModel e where e.dataHora < :corte order by e.dataHora, e.id")
        List<EntradaModel> entradasAntesDe(LocalDateTime corte);

        @EntityGraph(attributePaths = { "produto", "lote", "usuario", "produtoAberto" })
        @Query("select c from ConsumoModel c where c.dataHora < :corte order by c.dataHora, c.id")
        List<ConsumoModel> consumosAntesDe(LocalDateTime corte);

        @EntityGraph(attributePaths = { "produto", "lote", "usuario", "produtoAberto" })
        @Query("select d from DesperdicioModel d where d.dataHora < :corte order by d.dataHora, d.id")
        List<DesperdicioModel> desperdiciosAntesDe(LocalDateTime corte);

        @EntityGraph(attributePaths = { "produto", "lote", "usuario", "produtoAberto" })
        @Query("select e from EntradaModel e where e.dataHora >= :inicio and e.dataHora < :fim")
        List<EntradaModel> entradasEntre(LocalDateTime inicio, LocalDateTime fim);

        @EntityGraph(attributePaths = { "produto", "lote", "usuario", "produtoAberto" })
        @Query("select c from ConsumoModel c where c.dataHora >= :inicio and c.dataHora < :fim")
        List<ConsumoModel> consumosEntre(LocalDateTime inicio, LocalDateTime fim);

        @EntityGraph(attributePaths = { "produto", "lote", "usuario", "produtoAberto" })
        @Query("select d from DesperdicioModel d where d.dataHora >= :inicio and d.dataHora < :fim")
        List<DesperdicioModel> desperdiciosEntre(LocalDateTime inicio, LocalDateTime fim);

        @EntityGraph(attributePaths = { "produto", "lote", "usuario", "produtoAberto" })
        @Query("select m from MovimentacaoEstoqueModel m "
                        + "where (:produtoId is null or m.produto.id=:produtoId) "
                        + "and (:loteId is null or m.lote.id=:loteId) "
                        + "and (:usuarioId is null or m.usuario.id=:usuarioId) "
                        + "and (:tipo is null or m.tipo=:tipo) "
                        + "and m.dataHora>=:inicio and m.dataHora<:fim "
                        + "order by m.dataHora,m.id")
        List<MovimentacaoEstoqueModel> consultar(Long produtoId, Long loteId, Long usuarioId, TipoMovimentacao tipo,
                        LocalDateTime inicio, LocalDateTime fim);
}
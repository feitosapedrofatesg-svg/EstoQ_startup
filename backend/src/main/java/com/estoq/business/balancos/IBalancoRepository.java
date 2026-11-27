package com.estoq.business.balancos;

import com.estoq.core.repositories.IGenericRepository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import java.util.List;
import java.util.Optional;

public interface IBalancoRepository extends IGenericRepository<BalancoModel> {
    @Query("select b.id from BalancoModel b where b.ativo=true order by b.dataHora desc,b.id desc")
    org.springframework.data.domain.Page<Long> paginaIds(org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths={"itens.produto","itens.produto.categoria","itens.produto.parametro","usuario"})
    List<BalancoModel> findByIdIn(List<Long> ids);

    @EntityGraph(attributePaths = {"itens.produto", "itens.produto.categoria", "itens.produto.parametro", "usuario"})
    Optional<BalancoModel> findByIdAndAtivoTrue(Long id);
    @EntityGraph(attributePaths = {"itens", "itens.produto", "itens.produto.categoria", "itens.produto.parametro", "usuario"})
    List<BalancoModel> findAllByAtivoTrueOrderByDataHoraDesc();
    /** Sem joins: PostgreSQL bloqueia somente a linha do balanço, antes de ler seus itens. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select b from BalancoModel b where b.id = :id and b.ativo = true")
    Optional<BalancoModel> bloquear(@Param("id") Long id);

    long countByAtivoTrueAndStatusNot(StatusBalanco status);
}

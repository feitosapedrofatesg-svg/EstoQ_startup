package com.estoq.business.balancos;

import com.estoq.core.repositories.IGenericRepository;

import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface IBalancoRepository extends IGenericRepository<BalancoModel> {

    @EntityGraph(attributePaths = {"itens.produto", "usuario"})
    Optional<BalancoModel> findByIdAndAtivoTrue(Long id);
    List<BalancoModel> findAllByAtivoTrueOrderByDataHoraDesc();
    long countByAtivoTrueAndStatusNot(StatusBalanco status);
}
package com.estoq.business.itensBalanco;

import com.estoq.core.repositories.IGenericRepository;

import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface IItemBalancoRepository extends IGenericRepository<ItemBalancoModel> {

    @EntityGraph(attributePaths = { "balanco", "produto", "produto.categoria" })
    Optional<ItemBalancoModel> findByIdAndBalanco_IdAndAtivoTrue(Long id, Long balancoId);
}
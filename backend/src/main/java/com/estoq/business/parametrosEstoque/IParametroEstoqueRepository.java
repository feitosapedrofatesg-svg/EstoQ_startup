package com.estoq.business.parametrosEstoque;

import com.estoq.core.repositories.IGenericRepository;

import java.util.Optional;

public interface IParametroEstoqueRepository extends IGenericRepository<ParametroEstoqueModel> {

    Optional<ParametroEstoqueModel> findByProduto_Id(Long produtoId);
    boolean existsByProduto_IdAndIdNot(Long produtoId, Long id);
    boolean existsByProduto_Id(Long produtoId);
}
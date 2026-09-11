package com.estoq.business.parametrosCmv;

import com.estoq.core.repositories.IGenericRepository;

import java.util.Optional;

public interface IParametroCmvRepository extends IGenericRepository<ParametroCmvModel> {

    Optional<ParametroCmvModel> findFirstByAtivoTrueOrderByIdDesc();
}
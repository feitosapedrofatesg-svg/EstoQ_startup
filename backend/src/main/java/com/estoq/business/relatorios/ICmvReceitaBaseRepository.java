package com.estoq.business.relatorios;

import com.estoq.core.repositories.IGenericRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ICmvReceitaBaseRepository extends IGenericRepository<CmvReceitaBaseModel> {

    Optional<CmvReceitaBaseModel> findByDataInicioAndDataFimAndAtivoTrue(LocalDate inicio, LocalDate fim);
}
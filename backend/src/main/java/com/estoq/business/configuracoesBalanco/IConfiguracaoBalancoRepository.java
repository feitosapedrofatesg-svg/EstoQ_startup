package com.estoq.business.configuracoesBalanco;

import com.estoq.core.repositories.IGenericRepository;

import java.util.List;

public interface IConfiguracaoBalancoRepository extends IGenericRepository<ConfiguracaoBalancoModel> {

    List<ConfiguracaoBalancoModel> findAllByAtivoTrueOrderByIdAsc();
}
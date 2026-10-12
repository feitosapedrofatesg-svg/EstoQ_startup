package com.estoq.business.consumos;

import com.estoq.core.repositories.IGenericRepository;
import java.util.Optional;

public interface IOperacaoConsumoRepository extends IGenericRepository<OperacaoConsumoModel> {
    Optional<OperacaoConsumoModel> findByChave(String chave);
}

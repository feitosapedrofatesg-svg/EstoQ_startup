package com.estoq.business.notasFiscais;

import com.estoq.core.repositories.IGenericRepository;
import java.util.Optional;

public interface IVinculoFornecedorRepository extends IGenericRepository<VinculoFornecedorModel> {
    Optional<VinculoFornecedorModel> findByDocumentoFornecedorAndCodigoAndAtivoTrue(String documentoFornecedor, String codigo);
}

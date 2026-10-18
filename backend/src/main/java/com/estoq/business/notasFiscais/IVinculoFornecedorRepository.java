package com.estoq.business.notasFiscais;

import com.estoq.core.repositories.IGenericRepository;
import java.util.Optional;

public interface IVinculoFornecedorRepository extends IGenericRepository<VinculoFornecedorModel> {
    java.util.List<VinculoFornecedorModel> findByDocumentoFornecedorAndCodigoInAndAtivoTrue(String documentoFornecedor, java.util.Collection<String> codigos);
    Optional<VinculoFornecedorModel> findByDocumentoFornecedorAndCodigoAndAtivoTrue(String documentoFornecedor, String codigo);
}

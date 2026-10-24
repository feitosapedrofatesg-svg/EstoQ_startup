package com.estoq.business.notasFiscais;

import com.estoq.core.repositories.IGenericRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface INotaFiscalRepository extends IGenericRepository<NotaFiscalModel> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotaFiscalModel> findByChaveAndAtivoTrue(String chave);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotaFiscalModel n where n.id=:id and n.ativo=true")
    Optional<NotaFiscalModel> bloquear(Long id);
}

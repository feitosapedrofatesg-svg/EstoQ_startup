package com.estoq.business.restaurantes;

import com.estoq.core.repositories.IGenericRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface IRestauranteRepository extends IGenericRepository<RestauranteModel> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RestauranteModel r where r.id = :id and r.ativo = true")
    Optional<RestauranteModel> bloquearParaImportacao(@Param("id") Long id);
}
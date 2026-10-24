package com.estoq.business.relatorios;

import com.estoq.core.repositories.IGenericRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;

public interface ICmvReceitaBaseRepository extends IGenericRepository<CmvReceitaBaseModel> {

    Optional<CmvReceitaBaseModel> findByDataInicioAndDataFimAndAtivoTrue(LocalDate inicio, LocalDate fim);

    @Query("select r from CmvReceitaBaseModel r where r.ativo=true and r.dataInicio=r.dataFim "
            + "and r.dataInicio between :inicio and :fim order by r.dataInicio")
    List<CmvReceitaBaseModel> listarDiarios(LocalDate inicio, LocalDate fim);
}

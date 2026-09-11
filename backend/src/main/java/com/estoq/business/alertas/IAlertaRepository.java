package com.estoq.business.alertas;

import com.estoq.core.repositories.IGenericRepository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface IAlertaRepository extends IGenericRepository<AlertaModel> {

    @EntityGraph(attributePaths = {"produto", "lote"})
    Optional<AlertaModel> findByIdAndAtivoTrue(Long id);
    @EntityGraph(attributePaths = {"produto", "lote"})
    List<AlertaModel> findAllByAtivoTrueOrderByDataGeracaoDesc();
    long countByAtivoTrueAndVisualizadoFalse();
    @Modifying
    @Query("delete from AlertaModel a where a.ativo=true and a.visualizado=false and a.tipo=:tipo and (:produtoId is null or a.produto.id=:produtoId) and (:loteId is null or a.lote.id=:loteId)")
    void apagarAbertos(TipoAlerta tipo, Long produtoId, Long loteId);
}
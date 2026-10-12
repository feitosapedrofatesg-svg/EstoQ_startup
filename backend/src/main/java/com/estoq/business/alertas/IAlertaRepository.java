package com.estoq.business.alertas;

import com.estoq.core.repositories.IGenericRepository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import com.estoq.business.usuarios.Perfil;

import java.util.List;
import java.util.Optional;

public interface IAlertaRepository extends IGenericRepository<AlertaModel> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            delete from alertas where restaurante_id=:restauranteId and ativo=true and visualizado=false
                and tipo='DIFERENCA_ESTOQUE' and produto_id in (
                    select produto_id from itens_balanco where balanco_id=:balancoId
                        and restaurante_id=:restauranteId and quantidade_fisica<>quantidade_sistema)
            """, nativeQuery = true)
    int apagarDiferencasDoBalanco(Long balancoId, Long restauranteId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into alertas (restaurante_id, ativo, visualizado, data_geracao, data_hora_criacao,
                version, produto_id, perfil_destino, tipo, mensagem)
            select i.restaurante_id, true, false, (current_timestamp at time zone 'UTC'),
                (current_timestamp at time zone 'UTC'), 0, i.produto_id,
                'ADMIN', 'DIFERENCA_ESTOQUE',
                concat('Diferença apurada no balanço #', cast(:balancoId as varchar), ': ', p.nome, '.')
            from itens_balanco i join produtos p on p.id=i.produto_id and p.restaurante_id=i.restaurante_id
            where i.balanco_id=:balancoId and i.restaurante_id=:restauranteId
                and i.quantidade_fisica<>i.quantidade_sistema
            """, nativeQuery = true)
    int criarDiferencasDoBalanco(Long balancoId, Long restauranteId);

    @EntityGraph(attributePaths = { "produto", "produto.parametro", "lote" })
    Optional<AlertaModel> findByIdAndAtivoTrue(Long id);

    @EntityGraph(attributePaths = { "produto", "produto.parametro", "lote" })
    List<AlertaModel> findAllByAtivoTrueOrderByDataGeracaoDesc();

    long countByAtivoTrueAndVisualizadoFalseAndPerfilDestino(Perfil perfil);

    @Modifying
    @Query("delete from AlertaModel a where a.ativo=true and a.visualizado=false and a.tipo=:tipo and (:produtoId is null or a.produto.id=:produtoId) and (:loteId is null or a.lote.id=:loteId)")
    int apagarAbertos(TipoAlerta tipo, Long produtoId, Long loteId);
}

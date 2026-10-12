package com.estoq.business.itensBalanco;

import com.estoq.core.repositories.IGenericRepository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface IItemBalancoRepository extends IGenericRepository<ItemBalancoModel> {

    boolean existsByBalanco_Id(Long balancoId);

    /** Cria o retrato de estoque em uma viagem ao banco, sempre dentro do restaurante bloqueado. */
    @Modifying
    @Query(value = """
            insert into itens_balanco (restaurante_id, data_hora_criacao, version, ativo,
                balanco_id, produto_id, quantidade_sistema, ajuste_aplicado)
            select b.restaurante_id, (current_timestamp at time zone 'UTC'), 0, true, b.id, p.id,
                coalesce(sum(l.quantidade_atual), 0), false
            from balancos b
            join produtos p on p.restaurante_id = b.restaurante_id and p.ativo = true
            left join lotes l on l.produto_id = p.id and l.restaurante_id = b.restaurante_id and l.ativo = true
            where b.id = :balancoId and b.restaurante_id = :restauranteId
                and (b.tipo = 'GERAL' or exists (
                    select 1 from balanco_categorias bc where bc.balanco_id = b.id and bc.categoria_id = p.categoria_id))
            group by b.restaurante_id, b.id, p.id
            """, nativeQuery = true)
    int criarRetrato(Long balancoId, Long restauranteId);

    @EntityGraph(attributePaths = { "balanco", "produto", "produto.categoria", "produto.parametro" })
    Optional<ItemBalancoModel> findByIdAndBalanco_IdAndAtivoTrue(Long id, Long balancoId);
}

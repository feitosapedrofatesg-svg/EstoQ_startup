package com.estoq.core.domains;

import jakarta.persistence.MappedSuperclass;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.TenantId;

/**
 * Base para entidades que pertencem a um restaurante (tenant).
 * <p>
 * O Hibernate isola automaticamente por {@code restaurante_id}: escreve o valor no insert
 * (a partir do usuário autenticado) e filtra todos os selects por ele. Chamadas sem usuário
 * autenticado (registro público, testes) e o perfil PLATAFORMA enxergam a base inteira.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@MappedSuperclass
public abstract class TenantEntity extends BaseModel {

    @TenantId
    private Long restauranteId;
}
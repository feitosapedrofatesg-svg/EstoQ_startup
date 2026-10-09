package com.estoq.business.relatorios;

import com.estoq.core.domains.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "cmv_receitas_periodo", uniqueConstraints = @UniqueConstraint(name = "uk_cmv_receita_tenant_periodo", columnNames = {
        "restaurante_id", "data_inicio", "data_fim" }))
public class CmvReceitaBaseModel extends TenantEntity {

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Column(name = "receita_base", precision = 18, scale = 2)
    private BigDecimal receitaBase;
}
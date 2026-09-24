package com.estoq.business.parametrosCmv;

import com.estoq.core.domains.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "parametros_cmv")
public class ParametroCmvModel extends TenantEntity {

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal percentualIdeal;

    @Column(nullable = false)
    private LocalDateTime dataAtualizacao;
}
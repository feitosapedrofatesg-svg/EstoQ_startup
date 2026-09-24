package com.estoq.business.configuracoesBalanco;

import com.estoq.core.domains.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "configuracoes_balanco")
public class ConfiguracaoBalancoModel extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PeriodicidadeBalanco periodicidade;

    private Integer diaExecucao;

    private LocalDate proximaExecucao;
}
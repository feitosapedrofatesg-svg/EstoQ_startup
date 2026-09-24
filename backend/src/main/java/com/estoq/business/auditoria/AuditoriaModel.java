package com.estoq.business.auditoria;

import com.estoq.core.domains.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "auditoria")
public class AuditoriaModel extends TenantEntity {

    @Column(name = "tipo", length = 40, nullable = false)
    private String tipo;

    @Column(name = "entidade", length = 60)
    private String entidade;

    @Column(name = "entidade_id", length = 60)
    private String entidadeId;

    @Column(name = "descricao", length = 400)
    private String descricao;

    @Column(name = "usuario_nome", length = 120)
    private String usuarioNome;

    @Column(name = "data_hora")
    private LocalDateTime dataHora;
}
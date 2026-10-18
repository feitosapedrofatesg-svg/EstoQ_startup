package com.estoq.business.auditoria;

import com.estoq.core.domains.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Registro global: o operador da plataforma não pertence ao restaurante afetado. */
@Getter
@Setter
@Entity
@Table(name = "auditoria_plataforma")
public class AuditoriaPlataformaModel extends BaseModel {
    @Column(nullable = false)
    private Long usuarioId;
    @Column(nullable = false)
    private Long restauranteId;
    @Column(nullable = false, length = 40)
    private String acao;
    private Long usuarioAlvoId;
}

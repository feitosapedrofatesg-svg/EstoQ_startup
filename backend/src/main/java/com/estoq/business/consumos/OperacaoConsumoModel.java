package com.estoq.business.consumos;

import com.estoq.core.domains.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

/** Guardamos a resposta para que uma conexão perdida não provoque uma segunda baixa. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "operacoes_consumo", uniqueConstraints = @UniqueConstraint(columnNames = {"restaurante_id", "chave"}))
public class OperacaoConsumoModel extends TenantEntity {
    @Column(nullable = false, length = 36)
    private String chave;
    @Column(nullable = false, columnDefinition = "text")
    private String pedido;
    @Column(nullable = false, columnDefinition = "text")
    private String resposta;
}

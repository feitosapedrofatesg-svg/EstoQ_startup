package com.estoq.business.notasFiscais;

import com.estoq.core.domains.TenantEntity;
import com.estoq.business.produtos.UnidadeMedida;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "vinculos_fornecedor", uniqueConstraints = @UniqueConstraint(columnNames = {"restaurante_id", "documento_fornecedor", "codigo"}))
public class VinculoFornecedorModel extends TenantEntity {
    @Column(nullable = false, length = 20) private String documentoFornecedor;
    @Column(nullable = false, length = 60) private String codigo;
    @Column(nullable = false, length = 300) private String descricao;
    @Column(nullable = false, length = 20) private String unidadeNota;
    @Column(nullable = false) private Long produtoId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private UnidadeMedida unidadeEstoque;
    @Column(nullable = false, precision = 18, scale = 6) private BigDecimal fator;
}

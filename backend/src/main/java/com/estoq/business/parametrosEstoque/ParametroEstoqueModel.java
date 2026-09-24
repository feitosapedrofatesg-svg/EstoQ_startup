package com.estoq.business.parametrosEstoque;

import com.estoq.business.produtos.ProdutoModel;
import com.estoq.core.domains.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "parametros_estoque",
        uniqueConstraints = @UniqueConstraint(name = "uk_parametros_estoque_tenant_produto",
                columnNames = {"restaurante_id", "produto_id"}))
public class ParametroEstoqueModel extends TenantEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ProdutoModel produto;

    @Column(nullable = false)
    private Integer tempoReposicaoDias = 0;

    @Column(nullable = false)
    private Integer periodoAnaliseDias = 30;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal consumoMedioDiario = BigDecimal.ZERO;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal estoqueMinimo = BigDecimal.ZERO;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal estoqueMedio = BigDecimal.ZERO;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal estoqueMaximo = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer diasAlertaVencimento = 0;

    private LocalDateTime dataAtualizacao;
}
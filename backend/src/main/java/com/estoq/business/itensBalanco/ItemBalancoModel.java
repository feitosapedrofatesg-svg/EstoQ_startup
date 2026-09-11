package com.estoq.business.itensBalanco;

import com.estoq.business.balancos.BalancoModel;
import com.estoq.business.produtos.ProdutoModel;
import com.estoq.core.domains.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "itens_balanco", indexes = {
        @Index(name = "idx_item_balanco_produto", columnList = "produto_id"),
        @Index(name = "idx_item_balanco_balanco", columnList = "balanco_id")})
public class ItemBalancoModel extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "balanco_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BalancoModel balanco;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ProdutoModel produto;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal quantidadeSistema;

    @Column(precision = 18, scale = 3)
    private BigDecimal quantidadeFisica;

    @Column(nullable = false)
    private boolean ajusteAplicado;

    @Transient
    public BigDecimal getDiferenca() {
        return quantidadeFisica == null ? BigDecimal.ZERO : quantidadeFisica.subtract(quantidadeSistema);
    }

    public void definirContagem(BigDecimal quantidade) {
        if (quantidade == null || quantidade.signum() < 0) {
            throw new IllegalArgumentException("quantidadeFisica");
        }
        quantidadeFisica = quantidade;
    }
}
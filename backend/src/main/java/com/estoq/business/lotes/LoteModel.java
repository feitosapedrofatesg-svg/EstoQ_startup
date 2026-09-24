package com.estoq.business.lotes;

import com.estoq.business.produtos.ProdutoModel;
import com.estoq.core.domains.TenantEntity;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "lotes", indexes = {
        @Index(name = "idx_lote_produto", columnList = "produto_id"),
        @Index(name = "idx_lote_validade", columnList = "data_validade")},
        uniqueConstraints = @UniqueConstraint(name = "uk_lotes_tenant_codigo", columnNames = {"restaurante_id", "codigo"}))
public class LoteModel extends TenantEntity {

    @Column(nullable = false, length = 50)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ProdutoModel produto;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal quantidadeInicial;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal quantidadeAtual;

    private LocalDate dataEntrada;

    // null = sem vencimento (não perecível), disponível enquanto houver quantidade.
    private LocalDate dataValidade;

    @Column(precision = 18, scale = 6, nullable = false)
    private BigDecimal precoUnitario;

    public boolean estaVencido(LocalDate hoje) {
        return dataValidade != null && dataValidade.isBefore(hoje);
    }

    public Long diasParaVencimento(LocalDate hoje) {
        return dataValidade == null ? null : ChronoUnit.DAYS.between(hoje, dataValidade);
    }

    public boolean estaDisponivel(LocalDate hoje) {
        return isAtivo() && produto.isAtivo() && (dataValidade == null || !estaVencido(hoje)) && quantidadeAtual.signum() > 0;
    }

    public void baixar(BigDecimal quantidade) {
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new FieldValidationException("quantidade", "Informe quantidade maior que zero.");
        }
        if (quantidade.compareTo(quantidadeAtual) > 0) {
            throw new ConflictException("Estoque insuficiente no lote " + codigo + ".");
        }
        quantidadeAtual = quantidadeAtual.subtract(quantidade);
    }

    public void somar(BigDecimal quantidade) {
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new FieldValidationException("quantidade", "Informe quantidade maior que zero.");
        }
        quantidadeAtual = quantidadeAtual.add(quantidade);
    }
}
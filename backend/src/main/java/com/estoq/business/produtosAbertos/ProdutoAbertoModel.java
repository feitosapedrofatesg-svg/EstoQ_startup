package com.estoq.business.produtosAbertos;

import com.estoq.business.lotes.LoteModel;
import com.estoq.business.usuarios.UsuarioModel;
import com.estoq.core.domains.BaseModel;
import com.estoq.core.exceptions.ConflictException;

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
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "produtos_abertos", indexes = {
        @Index(name = "idx_aberto_finalizado", columnList = "finalizado"),
        @Index(name = "idx_aberto_lote", columnList = "lote_id")})
public class ProdutoAbertoModel extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoteModel lote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private UsuarioModel usuario;

    @Column(nullable = false)
    private LocalDateTime dataAbertura;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal quantidadeAberta;

    @Column(precision = 18, scale = 3, nullable = false)
    private BigDecimal quantidadeUtilizada = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean finalizado;

    @Transient
    public BigDecimal getQuantidadeRestante() {
        return quantidadeAberta.subtract(quantidadeUtilizada);
    }

    public void utilizar(BigDecimal quantidade) {
        if (finalizado || quantidade.signum() < 0 || quantidade.compareTo(getQuantidadeRestante()) > 0) {
            throw new ConflictException("Quantidade incompatível com o restante do item aberto.");
        }
        quantidadeUtilizada = quantidadeUtilizada.add(quantidade);
        finalizado = getQuantidadeRestante().signum() == 0;
    }
}
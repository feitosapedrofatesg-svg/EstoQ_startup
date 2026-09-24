package com.estoq.business.alertas;

import com.estoq.business.lotes.LoteModel;
import com.estoq.business.produtos.ProdutoModel;
import com.estoq.business.usuarios.Perfil;
import com.estoq.core.domains.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "alertas", indexes = {
        @Index(name = "idx_alerta_aberto", columnList = "tipo,visualizado,perfil_destino")})
public class AlertaModel extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAlerta tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfilDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ProdutoModel produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoteModel lote;

    @Column(nullable = false, length = 300)
    private String mensagem;

    @Column(nullable = false)
    private boolean visualizado;

    @Column(nullable = false)
    private LocalDateTime dataGeracao;
}
package com.estoq.business.alertas;

import com.estoq.business.lotes.LoteModel;
import com.estoq.business.produtos.ProdutoModel;
import com.estoq.business.usuarios.Perfil;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AlertaAdapter {

    public AlertaDTO toDto(AlertaModel a) {
        return new AlertaDTO(a.getId(), a.getTipo(), a.getMensagem(), a.getPerfilDestino().name(), a.getDataGeracao(),
                a.isVisualizado(), a.getProduto() == null ? null : a.getProduto().getId(),
                a.getProduto() == null ? null : a.getProduto().getNome(), a.getLote() == null ? null : a.getLote().getId(),
                a.getLote() == null ? null : a.getLote().getCodigo());
    }

    public static List<Perfil> destinos(TipoAlerta tipo) {
        return switch (tipo) {
            case ESTOQUE_BAIXO, PROXIMO_VENCIMENTO, VENCIDO -> List.of(Perfil.ADMIN, Perfil.COZINHA);
            case BALANCO_PENDENTE, DIFERENCA_ESTOQUE -> List.of(Perfil.ADMIN);
        };
    }

    public static String mensagemDe(TipoAlerta tipo, ProdutoModel produto, LoteModel lote, Object extra) {
        if (produto == null) {
            return switch (tipo) {
                case BALANCO_PENDENTE -> "Configuração de balanço vencida — realize um novo balanço (" + extra + ").";
                default -> "Alerta de estoque.";
            };
        }
        var nome = produto.getNome();
        return switch (tipo) {
            case ESTOQUE_BAIXO -> "Estoque de " + nome + " abaixo do mínimo (" + extra + " " + produto.getUnidadeMedida() + ").";
            case PROXIMO_VENCIMENTO -> "Lote " + lote.getCodigo() + " de " + nome + " vence em " + extra + " dia(s).";
            case VENCIDO -> "Lote " + lote.getCodigo() + " de " + nome + " está vencido.";
            case DIFERENCA_ESTOQUE -> "Diferença apurada no balanço #" + extra + ": " + nome + ".";
            default -> "Alerta de estoque.";
        };
    }
}
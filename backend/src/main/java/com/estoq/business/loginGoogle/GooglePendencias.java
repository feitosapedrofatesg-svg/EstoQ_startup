package com.estoq.business.loginGoogle;

import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.HorarioOperacao;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class GooglePendencias {
    private static final String CHAVE = "ESTOQ_GOOGLE_PENDENTE";
    private final HorarioOperacao horario;

    public enum Etapa { CADASTRO, VINCULAR, CONFIRMAR_EMAIL }
    public record Pendente(GoogleIdentidade identidade, Etapa etapa, LocalDateTime expiraEm) {}
    public record Resumo(Etapa etapa, String nome, String email) {}

    public void guardar(HttpServletRequest pedido, GoogleIdentidade identidade, Etapa etapa) {
        pedido.getSession(true).setAttribute(CHAVE, new Pendente(identidade, etapa, horario.agoraUtc().plusMinutes(10)));
    }

    public Pendente obter(HttpServletRequest pedido) {
        var sessao = pedido.getSession(false);
        if (sessao != null && sessao.getAttribute(CHAVE) instanceof Pendente p
                && horario.agoraUtc().isBefore(p.expiraEm())) return p;
        limpar(pedido);
        throw new BusinessException("O acesso com Google expirou. Clique em Continuar com Google novamente.", HttpStatus.BAD_REQUEST);
    }

    public Resumo resumo(HttpServletRequest pedido) {
        var p = obter(pedido);
        return new Resumo(p.etapa(), p.identidade().nome(), p.identidade().email());
    }

    public void limpar(HttpServletRequest pedido) {
        if (pedido.getSession(false) != null) pedido.getSession(false).removeAttribute(CHAVE);
    }
}

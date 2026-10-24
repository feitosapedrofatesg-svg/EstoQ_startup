package com.estoq.business.confirmacaoEmail;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EnvioConfirmacaoService {
    private static final Logger log = LoggerFactory.getLogger(EnvioConfirmacaoService.class);
    private final GmailClient gmail;
    private final EmailConfig config;
    private final TaskExecutor executor;

    public EnvioConfirmacaoService(GmailClient gmail, EmailConfig config, @Qualifier("enviosEmail") TaskExecutor executor) {
        this.gmail = gmail; this.config = config; this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void agendar(ConfirmacaoSolicitada evento) {
        try {
            executor.execute(() -> enviar(evento));
        } catch (RuntimeException e) {
            log.warn("Fila de e-mail indisponível para usuário {}. Reenvio disponível.", evento.usuarioId());
        }
    }

    private void enviar(ConfirmacaoSolicitada evento) {
        var link = config.getUrlPublica().replaceAll("/$", "") + "/confirmar-email#token=" + evento.token();
        var texto = "Confirme seu e-mail no EstoQ\n\nAbra o link e pressione Confirmar e-mail:\n" + link
                + "\n\nO link vale por 24 horas e pode ser usado uma vez."
                + "\nSe você não fez esse cadastro ou alteração, ignore esta mensagem.";
        try {
            gmail.enviar(evento.email(), texto);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Envio de confirmação interrompido para usuário {}.", evento.usuarioId());
        } catch (Exception e) {
            // Não inclui endereço, token, corpo nem resposta do Google nos logs.
            log.warn("Envio de confirmação falhou para usuário {}. Solicite reenvio.", evento.usuarioId());
        }
    }
}

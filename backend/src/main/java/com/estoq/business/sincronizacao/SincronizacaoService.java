package com.estoq.business.sincronizacao;

import com.estoq.business.auth.UsuarioPrincipal;
import com.estoq.config.security.SessaoValidaService;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Distribuição em uma instância. O polling existente recupera avisos perdidos em reconexões. */
@Service
public class SincronizacaoService implements HttpSessionListener {
    private record Conexao(String id, String sessaoId, HttpSession sessao, UsuarioPrincipal usuario, SseEmitter emitter) {}
    public record Aviso(Set<GrupoDados> grupos) {}
    private final Map<String, Conexao> conexoes = new ConcurrentHashMap<>();
    private final SessaoValidaService sessoes;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256), tarefa -> {
                var thread = new Thread(tarefa, "estoq-sincronizacao"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public SincronizacaoService(SessaoValidaService sessoes) { this.sessoes = sessoes; }

    public SseEmitter conectar(HttpSession sessao, UsuarioPrincipal usuario) {
        var emitter = new SseEmitter(60_000L);
        var c = new Conexao(UUID.randomUUID().toString(), sessao.getId(), sessao, usuario, emitter);
        conexoes.put(c.id(), c);
        emitter.onCompletion(() -> conexoes.remove(c.id()));
        emitter.onTimeout(() -> encerrar(c));
        emitter.onError(erro -> conexoes.remove(c.id()));
        enfileirar(c, () -> enviar(c, "conectado", Map.of()));
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dadosAlterados(DadosAlteradosEvent evento) {
        var aviso = new Aviso(Set.copyOf(evento.grupos()));
        for (var c : conexoes.values()) if (evento.restauranteId().equals(c.usuario().restauranteId()))
            enfileirar(c, () -> enviar(c, "alteracao", aviso));
    }

    private void enfileirar(Conexao c, Runnable tarefa) {
        try { executor.execute(tarefa); }
        catch (java.util.concurrent.RejectedExecutionException erro) { encerrar(c); }
    }

    /** Validar antes de enviar impede uma sessão revogada de continuar recebendo avisos. */
    private void enviar(Conexao c, String nome, Object dados) {
        if (!conexoes.containsKey(c.id())) return;
        try {
            var contexto = (SecurityContext) c.sessao().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (contexto == null || contexto.getAuthentication() == null
                    || !c.usuario().equals(contexto.getAuthentication().getPrincipal()) || !sessoes.validar(c.usuario())) {
                encerrar(c); return;
            }
            synchronized (c) {
                c.emitter().send(SseEmitter.event().name(nome).reconnectTime(3_000).data(dados));
            }
        } catch (Exception erro) { encerrar(c); }
    }

    @Scheduled(fixedDelay = 10_000)
    public void heartbeat() {
        for (var c : conexoes.values()) enfileirar(c, () -> enviar(c, "heartbeat", Map.of()));
    }

    private void encerrar(Conexao c) {
        if (conexoes.remove(c.id()) != null) {
            try { c.emitter().complete(); } catch (IllegalStateException ignorado) { /* Sessão/conexão já encerrada. */ }
        }
    }

    @Override public void sessionDestroyed(HttpSessionEvent evento) {
        String id = evento.getSession().getId();
        for (var c : conexoes.values()) if (c.sessaoId().equals(id)) encerrar(c);
    }

    @PreDestroy public void encerrarServidor() {
        for (var c : conexoes.values()) encerrar(c);
        executor.shutdownNow();
    }
}

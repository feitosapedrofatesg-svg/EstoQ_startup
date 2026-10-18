package com.estoq.business.auth;

import com.estoq.core.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Limite em memória para uma instância, com prazo e tamanho máximo. */
@Component
public class LimiteRequisicoes {
    private record Janela(Duration duracao, ArrayDeque<Instant> pedidos) {}
    private final Map<String, Janela> janelas = new HashMap<>();
    private final Clock relogio;
    private static final int MAX_CHAVES = 10_000;

    public LimiteRequisicoes() { this(Clock.systemUTC()); }
    LimiteRequisicoes(Clock relogio) { this.relogio = relogio; }

    public synchronized void verificar(String chave, int maximo) {
        var janela = janelas.get(chave);
        if (janela != null) {
            expirar(janela);
            if (janela.pedidos().size() >= maximo) bloquear();
        }
    }

    public synchronized void reservar(String chave, int maximo, Duration duracao) {
        verificar(chave, maximo);
        if (!janelas.containsKey(chave) && janelas.size() >= MAX_CHAVES) {
            limparExpiradas();
            if (janelas.size() >= MAX_CHAVES) bloquear();
        }
        var janela = janelas.computeIfAbsent(chave, k -> new Janela(duracao, new ArrayDeque<>()));
        janela.pedidos().addLast(relogio.instant());
    }

    private void expirar(Janela janela) {
        var corte = relogio.instant().minus(janela.duracao());
        while (!janela.pedidos().isEmpty() && !janela.pedidos().getFirst().isAfter(corte))
            janela.pedidos().removeFirst();
    }

    @Scheduled(fixedDelay = 60_000)
    public synchronized void limparExpiradas() {
        janelas.values().forEach(this::expirar);
        janelas.values().removeIf(j -> j.pedidos().isEmpty());
    }

    public synchronized void remover(String chave) { janelas.remove(chave); }
    public synchronized void limpar() { janelas.clear(); }
    synchronized int tamanho() { return janelas.size(); }

    private void bloquear() {
        throw new BusinessException("Muitas tentativas. Aguarde alguns minutos e tente novamente.", HttpStatus.TOO_MANY_REQUESTS);
    }
}

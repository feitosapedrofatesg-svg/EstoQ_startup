package com.estoq.business.auth;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;

import com.estoq.core.exceptions.BusinessException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Component
public class LoginAttemptService {

    private final int maxTentativas;
    private final Duration janela;
    private final Clock clock;
    private final Map<String, Deque<Instant>> falhas = new HashMap<>();

    @Autowired
    public LoginAttemptService(@Value("${estoq.login.max-tentativas:5}") int maxTentativas,
            @Value("${estoq.login.janela-minutos:15}") long janelaMinutos) {
        this(maxTentativas, janelaMinutos, Clock.systemUTC());
    }

    LoginAttemptService(int maxTentativas, long janelaMinutos, Clock clock) {
        this.maxTentativas = maxTentativas;
        this.janela = Duration.ofMinutes(janelaMinutos);
        this.clock = clock;
    }

    synchronized int chavesAtivas() {
        return falhas.size();
    }

    public synchronized void verificar(String email, String ip) {
        var fila = falhas.get(chave(email, ip));
        if (fila == null) {
            return;
        }
        expire(fila);
        if (fila.size() >= maxTentativas) {
            throw new BusinessException("Muitas tentativas de login. Tente novamente em alguns minutos.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /** Reserva antes de autenticar: requisições paralelas também consomem o limite. */
    public synchronized void reservar(String email, String ip) {
        verificar(email, ip);
        registrarFalha(email, ip);
    }

    @Scheduled(fixedDelay = 60_000)
    public synchronized void limparExpiradas() {
        falhas.values().forEach(this::expire);
        falhas.values().removeIf(Deque::isEmpty);
    }

    public synchronized void registrarFalha(String email, String ip) {
        falhas.computeIfAbsent(chave(email, ip), k -> new ArrayDeque<>()).addLast(clock.instant());
    }

    public synchronized void registrarSucesso(String email, String ip) {
        falhas.remove(chave(email, ip));
    }

    private void expire(Deque<Instant> fila) {
        var corte = clock.instant().minus(janela);
        while (!fila.isEmpty() && fila.peekFirst().isBefore(corte)) {
            fila.removeFirst();
        }
    }

    public synchronized void limpar() {
        falhas.clear();
    }

    private String chave(String email, String ip) {
        return (email == null ? "" : email.trim().toLowerCase(Locale.ROOT)) + "|" + (ip == null ? "" : ip);
    }
}

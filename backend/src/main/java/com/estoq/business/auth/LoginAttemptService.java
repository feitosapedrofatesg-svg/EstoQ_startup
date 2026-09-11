package com.estoq.business.auth;

import com.estoq.core.exceptions.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LoginAttemptService {

    private final int maxTentativas;
    private final Duration janela;
    private final Map<String, Deque<Instant>> falhas = new ConcurrentHashMap<>();

    public LoginAttemptService(@Value("${estoq.login.max-tentativas:5}") int maxTentativas,
            @Value("${estoq.login.janela-minutos:15}") long janelaMinutos) {
        this.maxTentativas = maxTentativas;
        this.janela = Duration.ofMinutes(janelaMinutos);
    }

    public void verificar(String email, String ip) {
        var fila = falhas.get(chave(email, ip));
        if (fila == null) {
            return;
        }
        expire(fila);
        if (fila.size() >= maxTentativas) {
            throw new BusinessException("Muitas tentativas de login. Tente novamente em alguns minutos.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    public void registrarFalha(String email, String ip) {
        falhas.computeIfAbsent(chave(email, ip), k -> new ArrayDeque<>()).addLast(Instant.now());
    }

    public void registrarSucesso(String email, String ip) {
        falhas.remove(chave(email, ip));
    }

    private void expire(Deque<Instant> fila) {
        var corte = Instant.now().minus(janela);
        while (!fila.isEmpty() && fila.peekFirst().isBefore(corte)) {
            fila.removeFirst();
        }
    }

    public void limpar() {
        falhas.clear();
    }

    private String chave(String email, String ip) {
        return (email == null ? "" : email.trim().toLowerCase()) + "|" + (ip == null ? "" : ip);
    }
}
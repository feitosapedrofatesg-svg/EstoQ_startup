package com.estoq.business.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

@Component
public class LoginAttemptService {
    private final int maxTentativas;
    private final Duration janela;
    private final LimiteRequisicoes limites;

    @Autowired
    public LoginAttemptService(@Value("${estoq.login.max-tentativas:5}") int maxTentativas,
            @Value("${estoq.login.janela-minutos:15}") long janelaMinutos) {
        this(maxTentativas, janelaMinutos, Clock.systemUTC());
    }

    LoginAttemptService(int maxTentativas, long janelaMinutos, Clock relogio) {
        this.maxTentativas = maxTentativas;
        this.janela = Duration.ofMinutes(janelaMinutos);
        this.limites = new LimiteRequisicoes(relogio);
    }

    synchronized int chavesAtivas() { return limites.tamanho(); }

    public synchronized void verificar(String email, String ip) {
        limites.verificar(chave(email, ip), maxTentativas);
    }

    /** Reservar antes do BCrypt limita pedidos paralelos e e-mails variados. */
    public synchronized void reservar(String email, String ip) {
        verificar(email, ip);
        limites.verificar("conta:" + normalizar(email), Math.max(15, maxTentativas * 3));
        limites.verificar("ip:" + ip, Math.max(100, maxTentativas * 20));
        limites.reservar("ip:" + ip, Math.max(100, maxTentativas * 20), janela);
        limites.reservar("conta:" + normalizar(email), Math.max(15, maxTentativas * 3), janela);
        registrarFalha(email, ip);
    }

    public synchronized void registrarFalha(String email, String ip) {
        limites.reservar(chave(email, ip), maxTentativas, janela);
    }

    public synchronized void registrarSucesso(String email, String ip) {
        limites.remover(chave(email, ip));
        limites.remover("conta:" + normalizar(email));
    }

    @Scheduled(fixedDelay = 60_000)
    public synchronized void limparExpiradas() { limites.limparExpiradas(); }
    public synchronized void limpar() { limites.limpar(); }

    private String chave(String email, String ip) { return "par:" + normalizar(email) + "|" + ip; }
    private String normalizar(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }
}

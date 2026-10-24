package com.estoq.business.confirmacaoEmail;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.net.URI;
import java.util.List;

@Getter
@Configuration
public class EmailConfig {
    @Value("${estoq.email.confirmacao.enabled:false}") private boolean enabled;
    @Value("${estoq.email.remetente:estoq.sistema@gmail.com}") private String remetente;
    @Value("${estoq.email.url-publica:}") private String urlPublica;
    @Value("${estoq.email.google.client-id:}") private String clientId;
    @Value("${estoq.email.google.client-secret:}") private String clientSecret;
    @Value("${estoq.email.google.refresh-token:}") private String refreshToken;

    @PostConstruct
    void validar() {
        if (!enabled) return;
        if (List.of(remetente, urlPublica, clientId, clientSecret, refreshToken).stream().anyMatch(String::isBlank))
            throw new IllegalStateException("Confirmação de e-mail exige remetente, URL pública e autorização OAuth do Gmail.");
        var uri = URI.create(urlPublica);
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || (!"https".equals(uri.getScheme()) && !(local && "http".equals(uri.getScheme()))))
            throw new IllegalStateException("URL pública de confirmação inválida; use HTTPS em produção.");
        if (remetente.contains("\r") || remetente.contains("\n")) throw new IllegalStateException("Remetente inválido.");
    }

    @Bean("enviosEmail")
    ThreadPoolTaskExecutor enviosEmail() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("email-");
        return executor;
    }
}

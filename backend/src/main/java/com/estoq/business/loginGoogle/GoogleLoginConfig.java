package com.estoq.business.loginGoogle;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.List;

@Getter
@Component
public class GoogleLoginConfig {
    @Value("${estoq.login.google.enabled:false}") private boolean enabled;
    @Value("${estoq.login.google.client-id:}") private String clientId;
    @Value("${estoq.login.google.client-secret:}") private String clientSecret;
    @Value("${estoq.login.google.url-publica:}") private String urlPublica;

    @PostConstruct
    void validar() {
        if (!enabled) return;
        if (List.of(clientId, clientSecret, urlPublica).stream().anyMatch(String::isBlank))
            throw new IllegalStateException("Login Google exige cliente Web, segredo e URL pública do ambiente.");
        var uri = URI.create(urlPublica);
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || (!uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                || (!"https".equals(uri.getScheme()) && !(local && "http".equals(uri.getScheme()))))
            throw new IllegalStateException("URL pública do login Google inválida; use a origem HTTPS do frontend.");
        urlPublica = urlPublica.replaceFirst("/+$", "");
    }

    public String retorno() {
        return urlPublica + "/api/login/oauth2/code/google";
    }
}

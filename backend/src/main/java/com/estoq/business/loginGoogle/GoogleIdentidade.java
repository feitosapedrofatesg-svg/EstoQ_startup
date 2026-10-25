package com.estoq.business.loginGoogle;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import java.util.Locale;

/** Somente informações do ID token já validado pelo Spring. */
public record GoogleIdentidade(String sub, String email, String nome, boolean emailConfiavel) {
    public static GoogleIdentidade de(OidcUser usuario) {
        var token = usuario.getIdToken();
        var sub = token.getSubject();
        var email = token.getEmail();
        if (sub == null || sub.isBlank() || sub.length() > 255 || email == null || email.length() > 254
                || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || !Boolean.TRUE.equals(token.getEmailVerified()))
            throw new BadCredentialsException("A conta Google precisa de um e-mail verificado.");
        email = email.trim().toLowerCase(Locale.ROOT);
        var dominio = token.getClaimAsString("hd");
        boolean confiavel = email.endsWith("@gmail.com") || (dominio != null && !dominio.isBlank());
        var nome = token.getFullName();
        if (nome == null || nome.isBlank()) nome = email.substring(0, email.indexOf('@'));
        nome = nome.trim();
        return new GoogleIdentidade(sub, email, nome.substring(0, Math.min(120, nome.length())), confiavel);
    }
}

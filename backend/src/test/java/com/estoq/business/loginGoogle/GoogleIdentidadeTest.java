package com.estoq.business.loginGoogle;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GoogleIdentidadeTest {
    GoogleIdentidade ler(String email, boolean verificado, String dominio) {
        var claims = new HashMap<String, Object>(Map.of("sub", "id-google", "email", email, "email_verified", verificado, "name", "Pessoa"));
        if (dominio != null) claims.put("hd", dominio);
        var token = new OidcIdToken("token-teste", Instant.now(), Instant.now().plusSeconds(300), claims);
        return GoogleIdentidade.de(new DefaultOidcUser(List.of(), token));
    }

    @Test void googleConfirmaGmailEWorkspaceMasEnderecoExternoPrecisaDoLink() {
        assertTrue(ler("Pessoa@GMAIL.COM", true, null).emailConfiavel());
        assertEquals("pessoa@gmail.com", ler("Pessoa@GMAIL.COM", true, null).email());
        assertTrue(ler("pessoa@empresa.com", true, "empresa.com").emailConfiavel());
        assertFalse(ler("pessoa@outlook.com", true, null).emailConfiavel());
        assertThrows(BadCredentialsException.class, () -> ler("pessoa@gmail.com", false, null));
        assertThrows(BadCredentialsException.class, () -> ler("endereco-invalido", true, null));
    }

    @Test void configuracaoDesligadaNaoExigeSegredosEAtivaExigeOrigemSegura() {
        var config = new GoogleLoginConfig();
        config.validar();
        ReflectionTestUtils.setField(config, "enabled", true);
        ReflectionTestUtils.setField(config, "clientId", "");
        ReflectionTestUtils.setField(config, "clientSecret", "");
        ReflectionTestUtils.setField(config, "urlPublica", "");
        assertThrows(IllegalStateException.class, config::validar);
        ReflectionTestUtils.setField(config, "clientId", "cliente");
        ReflectionTestUtils.setField(config, "clientSecret", "segredo");
        for (var url : List.of("http://estoq.test", "https://estoq.test/caminho", "https://usuario@estoq.test", "https://estoq.test?url=outra")) {
            ReflectionTestUtils.setField(config, "urlPublica", url);
            assertThrows(IllegalStateException.class, config::validar);
        }
        ReflectionTestUtils.setField(config, "urlPublica", "https://estoq.test/");
        config.validar(); assertEquals("https://estoq.test/api/login/oauth2/code/google", config.retorno());
        ReflectionTestUtils.setField(config, "urlPublica", "http://localhost:5174");
        config.validar(); assertEquals("http://localhost:5174/api/login/oauth2/code/google", config.retorno());
    }
}

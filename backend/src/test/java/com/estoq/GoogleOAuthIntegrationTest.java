package com.estoq;

import com.estoq.business.auth.UsuarioPrincipal;
import com.estoq.business.loginGoogle.GoogleLoginService;
import com.estoq.business.loginGoogle.GoogleCadastroDTO;
import com.estoq.business.loginGoogle.GoogleIdentidade;
import com.estoq.business.usuarios.IUsuarioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:google_protocolo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "estoq.login.google.enabled=true", "estoq.login.google.client-id=cliente-web-de-teste",
        "estoq.login.google.client-secret=segredo-de-teste", "estoq.login.google.url-publica=https://estoq.test"})
@AutoConfigureMockMvc @ActiveProfiles("test") @DirtiesContext
class GoogleOAuthIntegrationTest {
    static final GoogleProvedorTeste PROVEDOR = new GoogleProvedorTeste();
    @TestConfiguration
    static class ProvedorConfig {
        @Bean @Primary ClientRegistrationRepository clienteTeste() {
            return new InMemoryClientRegistrationRepository(PROVEDOR.cliente());
        }
    }
    @Autowired MockMvc mvc;
    @Autowired GoogleLoginService google;
    @Autowired IUsuarioRepository usuarios;
    @AfterAll static void encerrar() { PROVEDOR.close(); }
    record Inicio(MockHttpSession sessao, Map<String, String> parametros) {}

    Inicio iniciar() throws Exception {
        var resultado = mvc.perform(get("/api/oauth2/authorization/google")).andExpect(status().isFound()).andReturn();
        var parametros = GoogleProvedorTeste.parametros(URI.create(resultado.getResponse().getRedirectedUrl()).getRawQuery());
        assertEquals(GoogleProvedorTeste.ORIGEM + "/api/login/oauth2/code/google", parametros.get("redirect_uri"));
        assertEquals("S256", parametros.get("code_challenge_method"));
        assertTrue(parametros.get("scope").contains("openid"));
        assertFalse(parametros.get("scope").contains("gmail"));
        return new Inicio((MockHttpSession) resultado.getRequest().getSession(), parametros);
    }

    void responder(Inicio inicio, Map<String, Object> claims, String destino) throws Exception {
        var codigo = UUID.randomUUID().toString();
        PROVEDOR.codigos.put(codigo, claims);
        mvc.perform(get("/api/login/oauth2/code/google").session(inicio.sessao())
                .param("code", codigo).param("state", inicio.parametros().get("state")))
                .andExpect(status().isFound()).andExpect(redirectedUrl(GoogleProvedorTeste.ORIGEM + destino));
    }

    @Test void cadastroRecebeSomenteIdentidadeValidadaENaoGuardaTokens() throws Exception {
        var inicio = iniciar();
        responder(inicio, PROVEDOR.claims("novo-" + UUID.randomUUID(), "nova@gmail.com", inicio.parametros().get("nonce")), "/registro/google");
        mvc.perform(get("/api/auth/google/pendente").session(inicio.sessao())).andExpect(status().isOk())
                .andExpect(jsonPath("$.etapa").value("CADASTRO")).andExpect(jsonPath("$.email").value("nova@gmail.com"))
                .andExpect(jsonPath("$.sub").doesNotExist());
        mvc.perform(get("/api/auth/me").session(inicio.sessao())).andExpect(status().isUnauthorized());
        var verificador = PROVEDOR.ultimoPedido.get("code_verifier");
        assertNotNull(verificador);
        assertEquals(inicio.parametros().get("code_challenge"), Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verificador.getBytes(StandardCharsets.US_ASCII))));
        assertNull(inicio.sessao().getAttribute("SPRING_SECURITY_CONTEXT"));
        var atributos = inicio.sessao().getAttributeNames();
        while (atributos.hasMoreElements()) {
            var valor = inicio.sessao().getAttribute(atributos.nextElement()).toString();
            assertFalse(valor.contains("acesso-somente-teste"));
            assertFalse(valor.contains("OidcIdToken"));
        }
    }

    @Test void contaJaVinculadaEntraComSessaoDoEstoqERespeitaTenantELogout() throws Exception {
        var sub = "vinculado-" + UUID.randomUUID();
        var usuario = google.cadastrar(new GoogleIdentidade(sub, sub + "@gmail.com", "Pessoa", true), new GoogleCadastroDTO("Cozinha Google", "Pessoa"));
        var inicio = iniciar();
        var idAnterior = inicio.sessao().getId();
        responder(inicio, PROVEDOR.claims(sub, usuario.getEmail(), inicio.parametros().get("nonce")), "/");
        assertNotEquals(idAnterior, inicio.sessao().getId());
        var contexto = (SecurityContext) inicio.sessao().getAttribute("SPRING_SECURITY_CONTEXT");
        assertInstanceOf(UsuarioPrincipal.class, contexto.getAuthentication().getPrincipal());
        mvc.perform(get("/api/auth/me").session(inicio.sessao())).andExpect(status().isOk())
                .andExpect(jsonPath("$.restauranteId").value(usuario.getRestauranteId())).andExpect(jsonPath("$.perfil").value("ADMIN"));
        mvc.perform(get("/api/categorias?size=100").session(inicio.sessao())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(13));
        mvc.perform(get("/api/plataforma/restaurantes").session(inicio.sessao())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(inicio.sessao()).with(csrf())).andExpect(status().isNoContent());
        assertTrue(inicio.sessao().isInvalid());
    }

    @ParameterizedTest @ValueSource(strings = {"nonce", "aud", "iss", "exp", "assinaturaErrada", "email_verified"})
    void rejeitaIdentidadeInvalida(String campo) throws Exception {
        var inicio = iniciar();
        var claims = PROVEDOR.claims("invalido", "invalido@gmail.com", inicio.parametros().get("nonce"));
        claims.put(campo, switch (campo) {
            case "exp" -> Date.from(Instant.now().minusSeconds(600));
            case "assinaturaErrada" -> true;
            case "email_verified" -> false;
            case "iss" -> "https://outro-provedor.test";
            default -> "outro-valor";
        });
        responder(inicio, claims, "/login?google=erro");
        assertNull(inicio.sessao().getAttribute("SPRING_SECURITY_CONTEXT"));
        mvc.perform(get("/api/auth/google/pendente").session(inicio.sessao())).andExpect(status().isBadRequest());
    }

    @Test void rejeitaStateTrocadoEConsentimentoCancelado() throws Exception {
        var inicio = iniciar();
        mvc.perform(get("/api/login/oauth2/code/google").session(inicio.sessao()).param("code", "sem-troca")
                .param("state", "state-errado")).andExpect(redirectedUrl(GoogleProvedorTeste.ORIGEM + "/login?google=erro"));
        var novo = iniciar();
        mvc.perform(get("/api/login/oauth2/code/google").session(novo.sessao()).param("error", "access_denied")
                .param("state", novo.parametros().get("state")))
                .andExpect(redirectedUrl(GoogleProvedorTeste.ORIGEM + "/login?google=cancelado"));
        assertNull(novo.sessao().getAttribute("SPRING_SECURITY_CONTEXT"));
    }
}

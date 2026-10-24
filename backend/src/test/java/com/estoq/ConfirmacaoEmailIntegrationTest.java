package com.estoq;

import com.estoq.business.confirmacaoEmail.*;
import com.estoq.business.registro.*;
import com.estoq.business.usuarios.*;
import com.estoq.core.helpers.HorarioOperacao;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:confirmacao_email;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "estoq.email.url-publica=https://teste.estoq.local", "estoq.email.confirmacao.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class ConfirmacaoEmailIntegrationTest {
    private static final AtomicReference<Instant> AGORA = new AtomicReference<>(Instant.parse("2026-10-24T15:00:00Z"));
    @TestConfiguration
    static class RelogioTeste {
        @Bean @Primary HorarioOperacao horarioEmail() {
            return new HorarioOperacao(new Clock() {
                public ZoneId getZone() { return ZoneOffset.UTC; }
                public Clock withZone(ZoneId zona) { return Clock.fixed(instant(), zona); }
                public Instant instant() { return AGORA.get(); }
            });
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RegistroService registro;
    @Autowired ConfirmacaoEmailService confirmacao;
    @Autowired EmailConfig config;
    @Autowired IUsuarioRepository usuarios;
    @Autowired IConfirmacaoEmailRepository tokens;
    @Autowired PlatformTransactionManager transacoes;
    @Autowired ApplicationEvents eventos;
    @MockitoBean GmailClient gmail;
    String email;

    @BeforeEach void preparar() {
        AGORA.set(Instant.parse("2026-10-24T15:00:00Z"));
        email = "email-" + UUID.randomUUID() + "@teste.local";
        ReflectionTestUtils.setField(config, "enabled", true);
        SecurityContextHolder.clearContext();
    }
    @AfterEach void limpar() {
        ReflectionTestUtils.setField(config, "enabled", false);
        SecurityContextHolder.clearContext();
    }
    void cadastrar() {
        registro.registrar(new RegistroRequestDTO("Cozinha teste", "Responsável", email, "Teste@12345"));
    }
    String token(String endereco) {
        return eventos.stream(ConfirmacaoSolicitada.class).filter(e -> e.email().equals(endereco))
                .reduce((a, b) -> b).orElseThrow().token();
    }
    MvcResult login(String endereco, String senha, int status) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", endereco, "senha", senha))))
                .andExpect(status().is(status)).andReturn();
    }
    void confirmar(String token, int status) throws Exception {
        mvc.perform(post("/api/auth/email/confirmar").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("token", token)))).andExpect(status().is(status));
    }

    @Test void cadastroPendenteSoEntraDepoisDeConfirmarETokenNaoSeRepete() throws Exception {
        mvc.perform(post("/api/registro").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of(
                "nomeLoja", "Teste", "nomeResponsavel", "Responsável", "email", email, "senha", "Teste@12345"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.confirmacaoEmailPendente").value(true));
        var original = token(email);
        var salvo = tokens.findAll().stream().filter(t -> t.getEmail().equals(email)).findFirst().orElseThrow();
        assertEquals(64, salvo.getTokenHash().length()); assertNotEquals(original, salvo.getTokenHash());
        assertEquals(43, original.length());
        verify(gmail, timeout(2000)).enviar(eq(email), contains("/confirmar-email#token=" + original));
        login(email, "senha errada", 401);
        var negado = login(email, "Teste@12345", 403);
        assertTrue(negado.getResponse().getContentAsString().contains("EMAIL_NAO_CONFIRMADO"));
        mvc.perform(get("/api/auth/email/confirmar").param("token", original)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/auth/email/confirmar").contentType("application/json")
                .content(json.writeValueAsString(Map.of("token", original)))).andExpect(status().isForbidden());
        confirmar(original, 204);
        login(email, "Teste@12345", 200);
        confirmar(original, 400);
        assertNotNull(usuarios.findByEmailIgnoreCaseAndAtivoTrue(email).orElseThrow().getEmailConfirmadoEm());
    }

    @Test void reenvioInvalidaAnteriorERespeitaLimitesPersistidos() throws Exception {
        cadastrar(); var primeiro = token(email);
        confirmacao.reenviar(email); assertEquals(primeiro, token(email));
        for (int i = 1; i <= 5; i++) {
            AGORA.set(AGORA.get().plusSeconds(61));
            mvc.perform(post("/api/auth/email/reenviar").with(csrf()).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("email", email)))).andExpect(status().isAccepted());
        }
        assertEquals(5, tokens.findAll().stream().filter(t -> t.getEmail().equals(email)).count());
        confirmar(primeiro, 400);
        confirmar(token(email), 204);
        mvc.perform(post("/api/auth/email/reenviar").with(csrf()).contentType("application/json")
                .content("{\"email\":\"ausente@teste.local\"}")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Se houver uma conta aguardando confirmação, enviaremos um link. Confira também o spam."));
    }

    @Test void linkExpiraEm24HorasEFuncaoDesligadaNaoLiberaPendente() throws Exception {
        cadastrar(); var original = token(email);
        AGORA.set(AGORA.get().plus(Duration.ofHours(24)));
        confirmar(original, 400);
        ReflectionTestUtils.setField(config, "enabled", false);
        login(email, "Teste@12345", 403);
    }

    @Test void legadoContinuaAcessandoAteTrocarEmailESessaoAntigaEhRevogada() throws Exception {
        ReflectionTestUtils.setField(config, "enabled", false); cadastrar();
        var usuario = usuarios.findByEmailIgnoreCaseAndAtivoTrue(email).orElseThrow();
        assertFalse(usuario.isConfirmacaoEmailObrigatoria()); assertNull(usuario.getEmailConfirmadoEm());
        var sessao = (MockHttpSession) login(email, "Teste@12345", 200).getRequest().getSession(false);
        ReflectionTestUtils.setField(config, "enabled", true);
        var body = Map.of("version", usuario.getVersion(), "nome", "Responsável", "email", email.toUpperCase(), "perfil", "ADMIN", "ativo", true);
        var igual = mvc.perform(put("/api/usuarios/" + usuario.getId()).session(sessao).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmacaoEmailPendente").value(false)).andReturn();
        var novoEmail = "novo-" + email;
        var versao = json.readTree(igual.getResponse().getContentAsString()).get("version").asLong();
        mvc.perform(put("/api/usuarios/" + usuario.getId()).session(sessao).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("version", versao, "nome", "Responsável", "email", novoEmail, "perfil", "ADMIN", "ativo", true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmacaoEmailPendente").value(true));
        mvc.perform(get("/api/auth/me").session(sessao)).andExpect(status().isUnauthorized());
        login(novoEmail, "Teste@12345", 403);
        confirmar(token(novoEmail), 204);
        login(novoEmail, "Teste@12345", 200);
    }

    @Test void usuarioCriadoPelaEquipeTambemPrecisaConfirmarEmail() throws Exception {
        cadastrar(); confirmar(token(email), 204);
        var sessao = (MockHttpSession) login(email, "Teste@12345", 200).getRequest().getSession(false);
        var operacional = "operacional-" + email;
        mvc.perform(post("/api/usuarios").session(sessao).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("nome", "Operador", "email", operacional,
                        "senha", "Teste@12345", "perfil", "COZINHA"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.confirmacaoEmailPendente").value(true));
        login(operacional, "Teste@12345", 403);
        confirmar(token(operacional), 204);
        login(operacional, "Teste@12345", 200);
    }

    @Test void duasConfirmacoesConcorrentesSoUsamOLinkUmaVez() throws Exception {
        cadastrar(); var original = token(email);
        var corpo = json.writeValueAsString(Map.of("token", original));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> pedido = () -> mvc.perform(post("/api/auth/email/confirmar")
                    .with(csrf()).contentType("application/json").content(corpo)).andReturn().getResponse().getStatus();
            var resultados = executor.invokeAll(java.util.List.of(pedido, pedido));
            var status = java.util.List.of(resultados.get(0).get(), resultados.get(1).get()).stream().sorted().toList();
            assertEquals(204, status.getFirst());
            assertTrue(java.util.List.of(400, 409).contains(status.getLast()));
        }
    }

    @Test void rollbackNaoEnviaEmail() throws Exception {
        new TransactionTemplate(transacoes).executeWithoutResult(status -> {
            cadastrar(); status.setRollbackOnly();
        });
        assertTrue(usuarios.findByEmailIgnoreCaseAndAtivoTrue(email).isEmpty());
        verify(gmail, after(300).never()).enviar(eq(email), anyString());
    }

    @Test void falhaDoRemetenteNaoDesfazCadastroEPermiteNovoEnvio() throws Exception {
        doThrow(new IOException("Falha simulada")).when(gmail).enviar(eq(email), anyString());
        cadastrar();
        verify(gmail, timeout(2000)).enviar(eq(email), anyString());
        assertTrue(usuarios.findByEmailIgnoreCaseAndAtivoTrue(email).orElseThrow().emailPendente());
        AGORA.set(AGORA.get().plusSeconds(61));
        doNothing().when(gmail).enviar(eq(email), anyString());
        confirmacao.reenviar(email);
        verify(gmail, timeout(2000).times(2)).enviar(eq(email), anyString());
        confirmar(token(email), 204);
    }
}

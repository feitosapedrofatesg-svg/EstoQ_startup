package com.estoq;

import com.estoq.business.confirmacaoEmail.*;
import com.estoq.business.loginGoogle.*;
import com.estoq.business.registro.*;
import com.estoq.business.restaurantes.IRestauranteRepository;
import com.estoq.business.usuarios.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:google_contas;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "estoq.login.google.enabled=true", "estoq.login.google.client-id=cliente-web-teste",
        "estoq.login.google.client-secret=segredo-teste", "estoq.login.google.url-publica=https://estoq.test",
        "estoq.email.url-publica=https://estoq.test", "estoq.email.confirmacao.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("test") @RecordApplicationEvents
class GoogleLoginIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired GoogleLoginService google;
    @Autowired GooglePendencias pendencias;
    @Autowired GoogleLoginConfig config;
    @Autowired EmailConfig emailConfig;
    @Autowired RegistroService registro;
    @Autowired IUsuarioRepository usuarios;
    @Autowired IRestauranteRepository restaurantes;
    @Autowired PlatformTransactionManager transacoes;
    @Autowired ApplicationEvents eventos;
    @MockitoBean GmailClient gmail;
    GoogleIdentidade identidade;

    @BeforeEach void preparar() {
        String id = UUID.randomUUID().toString();
        identidade = new GoogleIdentidade(id, "pessoa-" + id + "@gmail.com", "Pessoa Google", true);
        ReflectionTestUtils.setField(config, "enabled", true);
        ReflectionTestUtils.setField(emailConfig, "enabled", false);
        SecurityContextHolder.clearContext();
    }
    @AfterEach void limpar() { SecurityContextHolder.clearContext(); ReflectionTestUtils.setField(emailConfig, "enabled", false); }
    MockHttpSession pendente(GooglePendencias.Etapa etapa) {
        var pedido = new MockHttpServletRequest();
        pendencias.guardar(pedido, identidade, etapa);
        return (MockHttpSession) pedido.getSession();
    }
    MvcResult enviar(String url, MockHttpSession sessao, Map<String, ?> corpo, int esperado) throws Exception {
        return mvc.perform(post(url).session(sessao).with(csrf()).contentType("application/json").content(json.writeValueAsString(corpo)))
                .andExpect(status().is(esperado)).andReturn();
    }
    MockHttpSession cadastrar() throws Exception {
        var sessao = pendente(GooglePendencias.Etapa.CADASTRO);
        enviar("/api/registro/google", sessao, Map.of("nomeLoja", "Cozinha Google", "nomeResponsavel", "Responsável"), 201);
        return sessao;
    }
    UsuarioModel usuario() { return usuarios.findByGoogleSub(identidade.sub()).orElseThrow(); }
    void alterar(Runnable acao) { new TransactionTemplate(transacoes).executeWithoutResult(s -> acao.run()); }

    @Test void novoCadastroNaoCriaSenhaERecebeOsMesmosPadroesDoCadastroLocal() throws Exception {
        var sessao = cadastrar(); var usuario = usuario();
        assertNull(usuario.getSenha()); assertTrue(usuario.isConfirmacaoEmailObrigatoria()); assertFalse(usuario.emailPendente());
        mvc.perform(get("/api/auth/me").session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(identidade.email()));
        mvc.perform(get("/api/categorias?size=100").session(sessao)).andExpect(jsonPath("$.totalElements").value(13));
        mvc.perform(get("/api/parametros-cmv").session(sessao)).andExpect(jsonPath("$.percentualIdeal").value(30));
        mvc.perform(get("/api/usuarios/" + usuario.getId()).session(sessao)).andExpect(jsonPath("$.googleVinculado").value(true))
                .andExpect(jsonPath("$.possuiSenha").value(false)).andExpect(jsonPath("$.googleSub").doesNotExist());
        enviar("/api/auth/login", new MockHttpSession(), Map.of("email", identidade.email(), "senha", "qualquer-senha"), 401);
    }

    @Test void csrfPendenteExpiradoECancelamentoNaoCriamContas() throws Exception {
        var sessao = pendente(GooglePendencias.Etapa.CADASTRO); long antes = restaurantes.count();
        mvc.perform(post("/api/registro/google").session(sessao).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        sessao.setAttribute("ESTOQ_GOOGLE_PENDENTE", new GooglePendencias.Pendente(identidade,
                GooglePendencias.Etapa.CADASTRO, LocalDateTime.MIN));
        enviar("/api/registro/google", sessao, Map.of("nomeLoja", "Loja", "nomeResponsavel", "Pessoa"), 400);
        var outra = pendente(GooglePendencias.Etapa.CADASTRO);
        enviar("/api/auth/google/cancelar", outra, Map.of(), 204);
        mvc.perform(get("/api/auth/google/pendente").session(outra)).andExpect(status().isBadRequest());
        assertEquals(antes, restaurantes.count());
    }

    @Test void contasLocaisExigemSenhaParaVincularEPreservamPerfilRestauranteEHash() throws Exception {
        registro.registrar(new RegistroRequestDTO("Loja existente", "Pessoa", identidade.email(), "Senha@12345"));
        var local = usuarios.findByEmailIgnoreCase(identidade.email()).orElseThrow();
        alterar(() -> { var u = usuarios.findById(local.getId()).orElseThrow(); u.setPerfil(Perfil.COZINHA); });
        var anterior = (MockHttpSession) enviar("/api/auth/login", new MockHttpSession(),
                Map.of("email", identidade.email(), "senha", "Senha@12345"), 200).getRequest().getSession();
        assertEquals(GooglePendencias.Etapa.VINCULAR, google.reconhecer(identidade).etapa());
        assertNull(usuarios.findById(local.getId()).orElseThrow().getGoogleSub());
        var sessao = pendente(GooglePendencias.Etapa.VINCULAR);
        enviar("/api/auth/google/vincular", sessao, Map.of("senha", "incorreta"), 401);
        assertNull(usuarios.findById(local.getId()).orElseThrow().getGoogleSub());
        enviar("/api/auth/google/vincular", sessao, Map.of("senha", "Senha@12345"), 200);
        var vinculado = usuario();
        assertEquals(local.getId(), vinculado.getId()); assertEquals(local.getSenha(), vinculado.getSenha());
        assertEquals(local.getRestauranteId(), vinculado.getRestauranteId()); assertEquals(Perfil.COZINHA, vinculado.getPerfil());
        mvc.perform(get("/api/auth/me").session(anterior)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios").session(sessao)).andExpect(status().isForbidden());
        enviar("/api/auth/login", new MockHttpSession(), Map.of("email", identidade.email(), "senha", "Senha@12345"), 200);
    }

    @Test void cadastroDuplicadoConcorrenteNaoDeixaRestauranteOrfao() throws Exception {
        long antes = restaurantes.count();
        var corpo = json.writeValueAsString(Map.of("nomeLoja", "Loja", "nomeResponsavel", "Pessoa"));
        var s1 = pendente(GooglePendencias.Etapa.CADASTRO); var s2 = pendente(GooglePendencias.Etapa.CADASTRO);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var resultados = executor.invokeAll(List.of(
                    (java.util.concurrent.Callable<Integer>) () -> mvc.perform(post("/api/registro/google").session(s1).with(csrf())
                            .contentType("application/json").content(corpo)).andReturn().getResponse().getStatus(),
                    () -> mvc.perform(post("/api/registro/google").session(s2).with(csrf())
                            .contentType("application/json").content(corpo)).andReturn().getResponse().getStatus()));
            assertEquals(List.of(201, 409), resultados.stream().map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } }).sorted().toList());
        }
        assertEquals(antes + 1, restaurantes.count());
    }

    @Test void contasInativasERestaurantesSuspensosNaoEntram() throws Exception {
        cadastrar(); var usuario = usuario();
        alterar(() -> usuarios.findById(usuario.getId()).orElseThrow().setAtivo(false));
        assertThrows(BadCredentialsException.class, () -> google.reconhecer(identidade));
        alterar(() -> { usuarios.findById(usuario.getId()).orElseThrow().setAtivo(true);
            restaurantes.findById(usuario.getRestauranteId()).orElseThrow().setAtivo(false); });
        assertThrows(BadCredentialsException.class, () -> google.reconhecer(identidade));
    }

    @Test void enderecoExternoAoGoogleExigeConfirmacaoPeloLink() throws Exception {
        identidade = new GoogleIdentidade(identidade.sub(), "externo-" + identidade.sub() + "@exemplo.com", "Pessoa", false);
        var sessao = pendente(GooglePendencias.Etapa.CADASTRO); long antes = restaurantes.count();
        enviar("/api/registro/google", sessao, Map.of("nomeLoja", "Loja", "nomeResponsavel", "Pessoa"), 503);
        assertEquals(antes, restaurantes.count());
        ReflectionTestUtils.setField(emailConfig, "enabled", true);
        enviar("/api/registro/google", sessao, Map.of("nomeLoja", "Loja", "nomeResponsavel", "Pessoa"), 201)
                .getResponse();
        mvc.perform(get("/api/auth/me").session(sessao)).andExpect(status().isUnauthorized());
        assertTrue(usuario().emailPendente());
        assertEquals(GooglePendencias.Etapa.CONFIRMAR_EMAIL, google.reconhecer(identidade).etapa());
        String token = eventos.stream(ConfirmacaoSolicitada.class).filter(e -> e.email().equals(identidade.email())).findFirst().orElseThrow().token();
        enviar("/api/auth/email/confirmar", new MockHttpSession(), Map.of("token", token), 204);
        assertNull(google.reconhecer(identidade).etapa());
    }

    @Test void mudarEmailDesvinculaGoogleEExigeSenhaEConfirmacaoSemInvalidarAntesDaMudanca() throws Exception {
        var sessao = cadastrar(); var usuario = usuario();
        var dto = new java.util.HashMap<String, Object>(Map.of("version", usuario.getVersion(), "nome", usuario.getNome(),
                "email", "novo-" + identidade.email(), "perfil", "ADMIN", "ativo", true));
        mvc.perform(put("/api/usuarios/" + usuario.getId()).session(sessao).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(dto))).andExpect(status().isBadRequest());
        dto.put("senha", "NovaSenha@12345");
        mvc.perform(put("/api/usuarios/" + usuario.getId()).session(sessao).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(dto))).andExpect(status().isServiceUnavailable());
        ReflectionTestUtils.setField(emailConfig, "enabled", true);
        mvc.perform(put("/api/usuarios/" + usuario.getId()).session(sessao).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(dto))).andExpect(status().isOk()).andExpect(jsonPath("$.googleVinculado").value(false))
                .andExpect(jsonPath("$.possuiSenha").value(true)).andExpect(jsonPath("$.confirmacaoEmailPendente").value(true));
        mvc.perform(get("/api/auth/me").session(sessao)).andExpect(status().isUnauthorized());
        assertNull(usuarios.findById(usuario.getId()).orElseThrow().getGoogleSub());
    }

    @Test void googleDesligadoMantemLoginLocal() throws Exception {
        ReflectionTestUtils.setField(config, "enabled", false);
        mvc.perform(get("/api/auth/google/configuracao")).andExpect(jsonPath("$.enabled").value(false));
        enviar("/api/registro/google", pendente(GooglePendencias.Etapa.CADASTRO), Map.of("nomeLoja", "Loja", "nomeResponsavel", "Pessoa"), 503);
        registro.registrar(new RegistroRequestDTO("Loja local", "Pessoa", identidade.email(), "Senha@12345"));
        enviar("/api/auth/login", new MockHttpSession(), Map.of("email", identidade.email(), "senha", "Senha@12345"), 200);
    }
}

package com.estoq;

import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.Perfil;
import com.estoq.business.usuarios.UsuarioModel;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    IUsuarioRepository users;
    @Autowired
    PasswordEncoder encoder;

    @BeforeEach
    void usuarios() {
        for (Perfil perfil : Perfil.values()) {
            UsuarioModel u = new UsuarioModel();
            u.setNome(perfil.name());
            u.setEmail(perfil.name().toLowerCase() + "@test.local");
            u.setPerfil(perfil);
            u.setSenha(encoder.encode("Teste@12345"));
            users.saveAndFlush(u);
        }
    }

    @Test
    void configuracaoInvalidaRetorna400EProntidaoNaoCriaSessao() throws Exception {
        var admin = login("admin@test.local", "Teste@12345", 200);
        mvc.perform(post("/api/configuracoes-balanco").session(admin).with(csrf())
                .contentType("application/json").content("{\"periodicidade\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/parametros-cmv").session(admin).with(csrf())
                .contentType("application/json").content("{\"percentualIdeal\":30.001}"))
                .andExpect(status().isBadRequest());
        var saude = mvc.perform(get("/api/saude/pronto")).andExpect(status().isOk()).andReturn();
        org.junit.jupiter.api.Assertions.assertNull(saude.getRequest().getSession(false));
    }

    private MockHttpSession login(String email, String senha, int status) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().is(status)).andReturn().getRequest().getSession(false);
    }

    @Test
    void autenticaTresPerfisEmSessoesIndependentes() throws Exception {
        var admin = login("admin@test.local", "Teste@12345", 200);
        var cozinha = login("cozinha@test.local", "Teste@12345", 200);
        var nutri = login("nutricionista@test.local", "Teste@12345", 200);
        assertNotEquals(admin.getId(), cozinha.getId());
        assertNotEquals(cozinha.getId(), nutri.getId());
        mvc.perform(get("/api/auth/me").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ADMIN"))
                .andExpect(jsonPath("$.senha").doesNotExist());
        mvc.perform(get("/api/auth/me").session(cozinha)).andExpect(jsonPath("$.perfil").value("COZINHA"));
        mvc.perform(get("/api/auth/me").session(nutri)).andExpect(jsonPath("$.perfil").value("NUTRICIONISTA"));
    }

    @Test
    void rejeitaSenhaInvalidaOuUsuarioInativo() throws Exception {
        login("admin@test.local", "errada", 401);
        var u = users.findByEmailIgnoreCaseAndAtivoTrue("cozinha@test.local").orElseThrow();
        u.setAtivo(false);
        users.flush();
        login(u.getEmail(), "Teste@12345", 401);
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void exigeCsrfERevogaSessaoAoDesativar() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Token CSRF inválido ou expirado."));
        var session = login("cozinha@test.local", "Teste@12345", 200);
        var u = users.findByEmailIgnoreCaseAndAtivoTrue("cozinha@test.local").orElseThrow();
        u.setAtivo(false);
        users.flush();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void salvaERecuperaReceitaBaseCmvPeloPeriodo() throws Exception {
        var admin = login("admin@test.local", "Teste@12345", 200);
        var body = "{\"inicio\":\"2026-09-01\",\"fim\":\"2026-09-30\",\"receitaBase\":\"12500.50\"}";

        mvc.perform(put("/api/relatorios/cmv/receita-base").session(admin).with(csrf())
                .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receitaBase").value(12500.50));
        mvc.perform(get("/api/relatorios/cmv/receita-base?inicio=2026-09-01&fim=2026-09-30").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receitaBase").value(12500.50));
    }

    @Test
    void cozinhaENutricionistaNaoFazemGestaoOuEntrada() throws Exception {
        for (String perfil : new String[] { "cozinha", "nutricionista" }) {
            var session = login(perfil + "@test.local", "Teste@12345", 200);
            mvc.perform(
                    post("/api/entradas").session(session).with(csrf()).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Seu perfil não possui permissão para esta operação."));
            mvc.perform(get("/api/usuarios").session(session)).andExpect(status().isForbidden());
        }
        var admin = login("admin@test.local", "Teste@12345", 200);
        mvc.perform(get("/api/usuarios").session(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(admin).with(csrf())).andExpect(status().isNoContent());
        assertTrue(admin.isInvalid());
    }

    @Test
    void apenasAdminENutricionistaRegistramFaturamentoDiario() throws Exception {
        for (String perfil : new String[] { "admin", "nutricionista", "cozinha", "plataforma" }) {
            var session = login(perfil + "@test.local", "Teste@12345", 200);
            boolean permitido = perfil.equals("admin") || perfil.equals("nutricionista");
            for (String tipo : new String[] { "faturamento", "consumo-dia-semana" }) {
                mvc.perform(get("/api/relatorios/pdf/" + tipo + "?inicio=2026-10-10&fim=2026-10-10").session(session))
                        .andExpect(status().is(permitido ? 200 : 403));
            }
            mvc.perform(get("/api/relatorios/faturamento-diario?inicio=2026-10-10&fim=2026-10-10").session(session))
                    .andExpect(status().is(permitido ? 200 : 403));
            mvc.perform(put("/api/relatorios/faturamento-diario").session(session).with(csrf())
                    .contentType("application/json").content("{\"data\":\"2026-10-10\",\"valor\":0,\"version\":"
                            + (perfil.equals("nutricionista") ? "0" : "null") + "}"))
                    .andExpect(status().is(permitido ? 200 : 403));
        }
    }
}

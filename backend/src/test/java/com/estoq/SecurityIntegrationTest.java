package com.estoq;

import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.Perfil;
import com.estoq.business.usuarios.UsuarioModel;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        mvc.perform(get("/api/auth/me").session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.perfil").value("ADMIN"))
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
                .andExpect(status().isForbidden());
        var session = login("cozinha@test.local", "Teste@12345", 200);
        var u = users.findByEmailIgnoreCaseAndAtivoTrue("cozinha@test.local").orElseThrow();
        u.setAtivo(false);
        users.flush();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void cozinhaENutricionistaNaoFazemGestaoOuEntrada() throws Exception {
        for (String perfil : new String[]{"cozinha", "nutricionista"}) {
            var session = login(perfil + "@test.local", "Teste@12345", 200);
            mvc.perform(post("/api/entradas").session(session).with(csrf()).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/usuarios").session(session)).andExpect(status().isForbidden());
        }
        var admin = login("admin@test.local", "Teste@12345", 200);
        mvc.perform(get("/api/usuarios").session(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(admin).with(csrf())).andExpect(status().isNoContent());
        assertTrue(admin.isInvalid());
    }
}
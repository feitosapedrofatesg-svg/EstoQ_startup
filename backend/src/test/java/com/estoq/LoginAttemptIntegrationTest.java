package com.estoq;

import com.estoq.business.auth.LoginAttemptService;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.Perfil;
import com.estoq.business.usuarios.UsuarioModel;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "estoq.login.max-tentativas=2")
@Transactional
class LoginAttemptIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    IUsuarioRepository users;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    LoginAttemptService tentativas;

    @BeforeEach
    void setup() {
        tentativas.limpar();
        var u = new UsuarioModel();
        u.setNome("Alvo");
        u.setEmail("alvo@test.local");
        u.setPerfil(Perfil.ADMIN);
        u.setSenha(encoder.encode("Teste@12345"));
        users.saveAndFlush(u);
    }

    @Test
    void bloqueiaAposLimiteDeFalhasEAceitaDepoisDoSucesso() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"alvo@test.local\",\"senha\":\"errada\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"alvo@test.local\",\"senha\":\"errada\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"alvo@test.local\",\"senha\":\"Teste@12345\"}")).andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"alvo@test.local\",\"senha\":\"Teste@12345\"}")).andExpect(status().isTooManyRequests());
    }

    @Test
    void outroEmailSemFalhasContinuaTendoAcesso() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                    .content("{\"email\":\"alvo@test.local\",\"senha\":\"errada\"}")).andExpect(status().isUnauthorized());
        }
        var outro = new UsuarioModel();
        outro.setNome("Tranquilo");
        outro.setEmail("tranquilo@test.local");
        outro.setPerfil(Perfil.COZINHA);
        outro.setSenha(encoder.encode("Teste@12345"));
        users.saveAndFlush(outro);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"tranquilo@test.local\",\"senha\":\"Teste@12345\"}")).andExpect(status().isOk());
    }
}
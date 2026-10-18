package com.estoq;

import com.estoq.business.auth.LoginAttemptService;
import com.estoq.business.usuarios.IUsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercita o provedor real de autenticação, que encapsula falhas de consulta. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginIndisponibilidadeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired LoginAttemptService tentativas;
    @MockitoBean IUsuarioRepository usuarios;

    @BeforeEach
    void limparLimites() { tentativas.limpar(); }

    @Test
    void bancoForaDoArRetorna503SemExporDetalhes() throws Exception {
        when(usuarios.findByEmailIgnoreCaseAndAtivoTrue("acesso@test.local"))
                .thenThrow(new DataAccessResourceFailureException("host-interno e credencial privados"));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"acesso@test.local\",\"senha\":\"Teste@12345\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "Serviço de acesso temporariamente indisponível. Tente novamente mais tarde."));
    }

    @Test
    void contaInexistenteContinuaRetornando401() throws Exception {
        when(usuarios.findByEmailIgnoreCaseAndAtivoTrue("acesso@test.local"))
                .thenReturn(Optional.empty());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"acesso@test.local\",\"senha\":\"Teste@12345\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos, ou usuário inativo."));
    }
}

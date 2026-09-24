package com.estoq;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Cadastro público de restaurante: cria loja + primeiro ADMIN + dados padrão. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistroIntegrationTest {

    @Autowired
    MockMvc mvc;

    private void registrar(String loja, String responsavel, String email, String senha, int status) throws Exception {
        String body = String.format(
                "{\"nomeLoja\":\"%s\",\"nomeResponsavel\":\"%s\",\"email\":\"%s\",\"senha\":\"%s\"}",
                loja, responsavel, email, senha);
        mvc.perform(post("/api/registro").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status));
    }

    private void login(String email, String senha, int status) throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().is(status));
    }

    @Test
    void cadastraRestauranteComAdminEentraNoSistema() throws Exception {
        registrar("Cantina do Zé", "Zé Ninguém", "ze@cantina.com", "Senha@12345", 201);

        var session = (org.springframework.mock.web.MockHttpSession) mvc
                .perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ze@cantina.com\",\"senha\":\"Senha@12345\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ADMIN"));
        mvc.perform(get("/api/parametros-cmv").session(session)).andExpect(status().isOk());
    }

    @Test
    void rejeitaEmailJaCadastrado() throws Exception {
        registrar("Loja Um", "Dono Um", "dono@loja.com", "Senha@12345", 201);
        registrar("Loja Dois", "Dono Dois", "dono@loja.com", "Senha@12345", 409);
    }

    @Test
    void rejeitaPayloadInvalido() throws Exception {
        registrar("Loja", "Dono", "dono@loja.com", "curta", 400);
        registrar("", "Dono", "dono@loja.com", "Senha@12345", 400);
    }
}
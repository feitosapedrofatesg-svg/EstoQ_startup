package com.estoq;

import com.estoq.business.restaurantes.IRestauranteRepository;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.Perfil;
import com.estoq.business.usuarios.UsuarioModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Dois restaurantes não enxergam os dados um do outro; PLATAFORMA governa os dois.
 * Sem @Transactional: cada requisição tem sua própria transação/sessão, como em produção.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantIsolamentoIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    IUsuarioRepository usuarios;
    @Autowired
    IRestauranteRepository restaurantes;
    @Autowired
    PasswordEncoder encoder;
    final ObjectMapper json = new ObjectMapper();

    /** Estado limpo por teste: o H2 é compartilhado entre classes e o seed cria "EstoQ Padrão" a cada startup. */
    @BeforeEach
    void limparBanco() {
        usuarios.deleteAll();
        restaurantes.deleteAll();
    }

    private void registrar(String loja, String email, String senha) throws Exception {
        String body = String.format(
                "{\"nomeLoja\":\"%s\",\"nomeResponsavel\":\"%s\",\"email\":\"%s\",\"senha\":\"%s\"}",
                loja, "Dono de " + loja, email, senha);
        mvc.perform(post("/api/registro").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private MockHttpSession login(String email, String senha) throws Exception {
        var res = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) res.getRequest().getSession(false);
    }

    private long criarCategoria(MockHttpSession sessao, String nome) throws Exception {
        String body = "{\"nome\":\"" + nome + "\"}";
        var res = mvc.perform(post("/api/categorias").session(sessao).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void lojaBNaoEnxergaNadaDaLojaA() throws Exception {
        registrar("Cantina A", "adm-a@teste.com", "LojaA@12345");
        registrar("Cantina B", "adm-b@teste.com", "LojaB@12345");
        var sessaoA = login("adm-a@teste.com", "LojaA@12345");
        var sessaoB = login("adm-b@teste.com", "LojaB@12345");

        long categoriaA = criarCategoria(sessaoA, "Bebidas");
        mvc.perform(post("/api/produtos").session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Refrigerante\",\"unidadeMedida\":\"UN\",\"categoriaId\":" + categoriaA + "}"))
                .andExpect(status().isCreated());

        // A vê os próprios dados; B não.
        mvc.perform(get("/api/produtos?size=100").session(sessaoA))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/produtos?size=100").session(sessaoB))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/categorias?size=100").session(sessaoB))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/produtos/1").session(sessaoB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/produtos/1").session(sessaoA)).andExpect(status().isOk());

        // Usuário criado pela loja A nasce no tenant dela (preenchimento automático).
        mvc.perform(post("/api/usuarios").session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cozinha A\",\"email\":\"coz-a@teste.com\",\"senha\":\"Senha@12345\",\"perfil\":\"COZINHA\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/usuarios?size=100").session(sessaoA))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/usuarios?size=100").session(sessaoB))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void plataformaGovernaRestaurantes() throws Exception {
        registrar("Cantina A", "adm-a@plataforma.com", "LojaA@12345");
        registrar("Cantina B", "adm-b@plataforma.com", "LojaB@12345");

        var plataforma = new UsuarioModel();
        plataforma.setNome("Dono da Plataforma");
        plataforma.setEmail("plataforma@teste.com");
        plataforma.setSenha(encoder.encode("Plat@12345"));
        plataforma.setPerfil(Perfil.PLATAFORMA);
        usuarios.saveAndFlush(plataforma);
        var sessaoPlat = login("plataforma@teste.com", "Plat@12345");

        // PLATAFORMA enxerga os dois restaurantes, com a contagem real de usuários de cada um.
        var lista = mvc.perform(get("/api/plataforma/restaurantes").session(sessaoPlat))
                .andExpect(status().isOk()).andReturn();
        JsonNode restaurantesJson = json.readTree(lista.getResponse().getContentAsString());
        assertThat(restaurantesJson).hasSize(2);
        long idB = -1;
        for (JsonNode r : restaurantesJson) {
            if ("Cantina B".equals(r.get("nome").asText())) {
                idB = r.get("id").asLong();
                assertThat(r.get("totalUsuarios").asLong()).isEqualTo(1);
            }
            if ("Cantina A".equals(r.get("nome").asText())) {
                assertThat(r.get("totalUsuarios").asLong()).isEqualTo(1);
            }
        }
        assertThat(idB).isPositive();

        // Suspende a loja B: o administrador dela perde o acesso na hora.
        mvc.perform(put("/api/plataforma/restaurantes/" + idB).session(sessaoPlat).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"adm-b@plataforma.com\",\"senha\":\"LojaB@12345\"}"))
                .andExpect(status().isUnauthorized());

        // Redefine a senha do administrador de B.
        mvc.perform(post("/api/plataforma/restaurantes/" + idB + "/redefinir-admin")
                .session(sessaoPlat).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"NovaSenha@12345\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"adm-b@plataforma.com\",\"senha\":\"NovaSenha@12345\"}"))
                .andExpect(status().isUnauthorized()); // loja segue suspensa
        mvc.perform(put("/api/plataforma/restaurantes/" + idB).session(sessaoPlat).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":true}"))
                .andExpect(status().isOk());
        login("adm-b@plataforma.com", "NovaSenha@12345");

        // Loja comum não tem acesso ao painel da plataforma.
        var sessaoA = login("adm-a@plataforma.com", "LojaA@12345");
        mvc.perform(get("/api/plataforma/restaurantes").session(sessaoA))
                .andExpect(status().isForbidden());
    }
}
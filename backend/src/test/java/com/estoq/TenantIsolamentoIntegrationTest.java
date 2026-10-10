package com.estoq;

import com.estoq.business.restaurantes.IRestauranteRepository;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.Perfil;
import com.estoq.business.usuarios.UsuarioModel;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:estoq_tenant_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
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
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate banco;

    /** Estado limpo por teste: o H2 é compartilhado entre classes e o seed cria "EstoQ Padrão" a cada startup. */
    @BeforeEach
    void limparBanco() {
        banco.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            var tabelas = banco.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_TYPE='BASE TABLE'", String.class);
            for (var tabela : tabelas) banco.execute("TRUNCATE TABLE " + tabela + " RESTART IDENTITY");
        } finally { banco.execute("SET REFERENTIAL_INTEGRITY TRUE"); }
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


    private com.estoq.business.notasFiscais.NotaFiscalDTO lerNota(MockHttpSession sessao, String xml) throws Exception {
        var arquivo = new org.springframework.mock.web.MockMultipartFile("arquivos", "nota.xml", "application/xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var resposta = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/notas-fiscais/analisar")
                .file(arquivo).session(sessao).with(csrf())).andExpect(status().isOk()).andReturn();
        return json.readValue(resposta.getResponse().getContentAsString(), com.estoq.business.notasFiscais.NotaFiscalDTO.class);
    }

    private String confirmarNota(com.estoq.business.notasFiscais.NotaFiscalDTO nota, long categoriaId) throws Exception {
        var decisoes = nota.itens().stream().map(i -> new com.estoq.business.notasFiscais.ConfirmarNotaDTO.Item(i.id(), true, null,
                i.nomeProduto(), categoriaId, com.estoq.business.produtos.UnidadeMedida.KG, i.quantidadeNota(), java.math.BigDecimal.ONE,
                i.valorEntrada(), null, true, true)).toList();
        return json.writeValueAsString(new com.estoq.business.notasFiscais.ConfirmarNotaDTO(nota.version(), decisoes, true));
    }

    @Test
    void notasEVinculosFicamIsoladosPorCozinha() throws Exception {
        registrar("Importação A", "nota-a@teste.com", "LojaA@12345");
        registrar("Importação B", "nota-b@teste.com", "LojaB@12345");
        var a = login("nota-a@teste.com", "LojaA@12345");
        var b = login("nota-b@teste.com", "LojaB@12345");
        long categoriaA = criarCategoria(a, "Alimentos");
        var documento = NotaFiscalExemplo.xml(NotaFiscalExemplo.CHAVE, "Arroz", "AR1", "KG", "2", "20.00");
        var notaA = lerNota(a, documento);
        mvc.perform(post("/api/notas-fiscais/" + notaA.id() + "/confirmar").session(a).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(confirmarNota(notaA,categoriaA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmada").value(true));
        mvc.perform(get("/api/notas-fiscais/" + notaA.id()).session(b)).andExpect(status().isNotFound());
        var notaB = lerNota(b, documento);
        org.junit.jupiter.api.Assertions.assertNotEquals(notaA.id(),notaB.id());
        org.junit.jupiter.api.Assertions.assertNull(notaB.itens().getFirst().produtoId());
        mvc.perform(post("/api/notas-fiscais/" + notaB.id() + "/confirmar").session(b).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(confirmarNota(notaB,categoriaA))).andExpect(status().isNotFound());
        mvc.perform(get("/api/produtos?size=100").session(b)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/estoque").session(a)).andExpect(jsonPath("$[0].saldoAtual").value(2));
    }

    @Test
    void confirmacoesConcorrentesNaoDuplicamEntrada() throws Exception {
        registrar("Importação simultânea", "nota-simultanea@teste.com", "LojaA@12345");
        var sessao = login("nota-simultanea@teste.com", "LojaA@12345");
        long categoria = criarCategoria(sessao, "Alimentos");
        var nota = lerNota(sessao, NotaFiscalExemplo.xml(NotaFiscalExemplo.CHAVE,"Arroz","AR1","KG","2","20.00"));
        var corpo = confirmarNota(nota,categoria);
        try(var tarefas = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var partida = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> confirmar = () -> {
                partida.await();
                return mvc.perform(post("/api/notas-fiscais/" + nota.id() + "/confirmar").session(sessao).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn().getResponse().getStatus();
            };
            var primeira = tarefas.submit(confirmar); var segunda = tarefas.submit(confirmar); partida.countDown();
            org.junit.jupiter.api.Assertions.assertEquals(200,primeira.get(20,java.util.concurrent.TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertEquals(200,segunda.get(20,java.util.concurrent.TimeUnit.SECONDS));
        }
        mvc.perform(get("/api/produtos?size=100").session(sessao)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/estoque").session(sessao)).andExpect(jsonPath("$[0].saldoAtual").value(2));
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
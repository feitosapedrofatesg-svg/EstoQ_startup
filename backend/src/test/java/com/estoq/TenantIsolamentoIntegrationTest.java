package com.estoq;

import com.estoq.business.restaurantes.IRestauranteRepository;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.Perfil;
import com.estoq.business.usuarios.UsuarioModel;

import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

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
    void faturamentoDiarioIsolaLojasEExigeVersaoNaCorrecao() throws Exception {
        registrar("Diário A", "diario-a@teste.com", "Teste@12345");
        registrar("Diário B", "diario-b@teste.com", "Teste@12345");
        var a = login("diario-a@teste.com", "Teste@12345");
        var b = login("diario-b@teste.com", "Teste@12345");
        String rota = "/api/relatorios/faturamento-diario";
        mvc.perform(put(rota).session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":\"2026-10-10\",\"valor\":1500}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        mvc.perform(get(rota + "?inicio=2026-10-10&fim=2026-10-11").session(a))
                .andExpect(jsonPath("$.totalInformado").value(1500))
                .andExpect(jsonPath("$.diasPendentes").value(1))
                .andExpect(jsonPath("$.receitaBase").isEmpty());
        mvc.perform(get(rota + "?inicio=2026-10-10&fim=2026-10-10").session(b))
                .andExpect(jsonPath("$.totalInformado").value(0))
                .andExpect(jsonPath("$.diasPendentes").value(1));
        mvc.perform(put(rota).session(b).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":\"2026-10-10\",\"valor\":900}"))
                .andExpect(status().isOk());
        mvc.perform(put(rota).session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":\"2026-10-10\",\"valor\":1600,\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put(rota).session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":\"2026-10-10\",\"valor\":1700,\"version\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(get(rota + "?inicio=2026-10-10&fim=2026-10-10").session(a))
                .andExpect(jsonPath("$.receitaBase").value(1600))
                .andExpect(jsonPath("$.diasInformados").value(1));
        mvc.perform(get(rota + "?inicio=2026-10-10&fim=2026-10-10").session(b))
                .andExpect(jsonPath("$.receitaBase").value(900));
        String pdfRota = "/api/relatorios/pdf/faturamento?inicio=2026-10-10&fim=2026-10-11";
        for (var sessao : java.util.List.of(a, b)) {
            byte[] bytes = mvc.perform(get(pdfRota).session(sessao)).andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn().getResponse().getContentAsByteArray();
            try (var pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) {
                String texto = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
                assertTrue(texto.contains(sessao == a ? "R$ 1.600,00" : "R$ 900,00"));
                assertFalse(texto.contains(sessao == a ? "R$ 900,00" : "R$ 1.600,00"));
                assertTrue(texto.contains("(parcial)"));
            }
        }
        mvc.perform(put(rota).session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":null,\"valor\":100}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(rota).session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":\"2026-10-12\",\"valor\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(rota).session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":\"2026-10-12\",\"valor\":1.123}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lojaBNaoEnxergaNadaDaLojaA() throws Exception {
        registrar("Cantina A", "adm-a@teste.com", "LojaA@12345");
        registrar("Cantina B", "adm-b@teste.com", "LojaB@12345");
        var sessaoA = login("adm-a@teste.com", "LojaA@12345");
        var sessaoB = login("adm-b@teste.com", "LojaB@12345");

        long categoriaA = criarCategoria(sessaoA, "Bebidas exclusivas A");
        mvc.perform(post("/api/produtos").session(sessaoA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Refrigerante\",\"unidadeMedida\":\"UN\",\"categoriaId\":" + categoriaA + "}"))
                .andExpect(status().isCreated());

        // A vê os próprios dados; B não.
        mvc.perform(get("/api/produtos?size=100").session(sessaoA))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/produtos?size=100").session(sessaoB))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/categorias?size=100").session(sessaoB))
                .andExpect(jsonPath("$.totalElements").value(13));
        mvc.perform(get("/api/categorias/"+categoriaA).session(sessaoB)).andExpect(status().isNotFound());
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
    void pesquisaEPaginacaoAlcancamUltimosRegistrosSemCruzarRestaurantes() throws Exception {
        registrar("Loja com catálogo grande", "grande@teste.com", "LojaA@12345");
        registrar("Outra loja", "outra@teste.com", "LojaB@12345");
        var a = login("grande@teste.com", "LojaA@12345");
        var b = login("outra@teste.com", "LojaB@12345");
        long restauranteId = banco.queryForObject("select restaurante_id from usuarios where email=?", Long.class, "grande@teste.com");
        long categoria = criarCategoria(a, "Categoria de teste");
        var senha = encoder.encode("Teste@12345");
        for (int i = 0; i < 125; i++) {
            banco.update("insert into produtos (nome,unidade_medida,categoria_id,codigo_barras,ativo,version,data_hora_criacao,restaurante_id) values (?, 'UN', ?, ?, true, 0, CURRENT_TIMESTAMP, ?)",
                    "Produto " + i, categoria, "COD" + i, restauranteId);
            banco.update("insert into categorias (nome,ativo,version,data_hora_criacao,restaurante_id) values (?,true,0,CURRENT_TIMESTAMP,?)", "Grupo " + i, restauranteId);
            banco.update("insert into usuarios (nome,email,senha,perfil,ativo,version,data_hora_criacao,restaurante_id) values (?,?,?,'COZINHA',true,0,CURRENT_TIMESTAMP,?)",
                    "Pessoa " + i, "pessoa" + i + "@teste.com", senha, restauranteId);
        }
        for (var rota : List.of("produtos", "categorias", "usuarios")) {
            mvc.perform(get("/api/" + rota + "?size=50&page=2&sort=id,asc").session(a))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.number").value(2))
                    .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThan(0)));
            mvc.perform(get("/api/" + rota + "?size=50&q=124").session(a))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
            mvc.perform(get("/api/" + rota + "?q=124").session(b))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
        mvc.perform(get("/api/produtos").param("q", "Categoria de teste").session(a)).andExpect(jsonPath("$.totalElements").value(125));
        mvc.perform(get("/api/produtos?q=COD124").session(a)).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void posicoesHistoricasEParametrosTambemRespeitamRestaurante() throws Exception {
        registrar("Histórico A", "historico-a@teste.com", "LojaA@12345");
        registrar("Histórico B", "historico-b@teste.com", "LojaB@12345");
        var a = login("historico-a@teste.com", "LojaA@12345");
        var b = login("historico-b@teste.com", "LojaB@12345");
        long categoria = criarCategoria(a, "Alimentos");
        var produto = mvc.perform(post("/api/produtos").session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Farinha\",\"unidadeMedida\":\"KG\",\"categoriaId\":" + categoria + "}"))
                .andExpect(status().isCreated()).andReturn();
        var id = json.readTree(produto.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/entradas").session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoId\":" + id + ",\"quantidade\":2,\"valorTotalPago\":20,\"unidadeCompra\":\"KG\",\"semCusto\":false}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/parametros-estoque/por-produto/" + id).session(a)).andExpect(status().isOk());
        mvc.perform(get("/api/parametros-estoque/por-produto/" + id).session(b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/relatorios/cmv").session(a)).andExpect(status().isOk()).andExpect(jsonPath("$.valorCompras").value(20));
        mvc.perform(get("/api/relatorios/cmv").session(b)).andExpect(status().isOk())
                .andExpect(jsonPath("$.valorCompras").value(0)).andExpect(jsonPath("$.valorEstoqueFinal").value(0));
    }

    private com.estoq.business.notasFiscais.NotaFiscalDTO lerNota(MockHttpSession sessao, String xml) throws Exception {
        var arquivo = new org.springframework.mock.web.MockMultipartFile("arquivos", "nota.xml", "application/xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var resposta = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/notas-fiscais/analisar")
                .file(arquivo).session(sessao).with(csrf())).andExpect(status().isOk()).andReturn();
        return json.readValue(resposta.getResponse().getContentAsString(), com.estoq.business.notasFiscais.NotaFiscalDTO.class);
    }

    private String confirmarNota(com.estoq.business.notasFiscais.NotaFiscalDTO nota, Long categoriaId) throws Exception {
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
        org.junit.jupiter.api.Assertions.assertNotNull(notaB.itens().getFirst().categoriaId());
        org.junit.jupiter.api.Assertions.assertEquals("DESCRICAO",notaB.itens().getFirst().origemCategoria());
        org.junit.jupiter.api.Assertions.assertFalse(notaB.itens().getFirst().revisarCategoria());
        org.junit.jupiter.api.Assertions.assertNotEquals(categoriaA,notaB.itens().getFirst().categoriaId());
        mvc.perform(post("/api/notas-fiscais/" + notaB.id() + "/confirmar").session(b).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(confirmarNota(notaB,categoriaA))).andExpect(status().isNotFound());
        mvc.perform(get("/api/produtos?size=100").session(b)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/estoque").session(a)).andExpect(jsonPath("$[0].saldoAtual").value(2));
    }

    @Test
    void reanaliseInvalidaConfirmacaoAbertaEmOutraAba() throws Exception {
        registrar("Revisão de nota", "revisao-nota@teste.com", "LojaA@12345");
        var sessao = login("revisao-nota@teste.com", "LojaA@12345");
        var xml = NotaFiscalExemplo.xml(NotaFiscalExemplo.CHAVE,"BEB GAISE H2O LIMAO","BE1","KG","2","20.00");
        var antiga = lerNota(sessao,xml);
        var atual = lerNota(sessao,xml);
        assertTrue(atual.version() > antiga.version());
        mvc.perform(post("/api/notas-fiscais/" + antiga.id() + "/confirmar").session(sessao).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(confirmarNota(antiga,null)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/produtos").session(sessao)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/notas-fiscais/" + atual.id() + "/confirmar").session(sessao).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(confirmarNota(atual,null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmada").value(true));
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
    void notasDiferentesReutilizamCategoriaPadraoConcorrentemente() throws Exception {
        registrar("Categorias automáticas", "categoria-auto@teste.com", "LojaA@12345");
        var sessao=login("categoria-auto@teste.com", "LojaA@12345");
        var primeira=lerNota(sessao,NotaFiscalExemplo.xml(NotaFiscalExemplo.CHAVE,"ARTIGO UM","A1","KG","2","20.00"));
        var segunda=lerNota(sessao,NotaFiscalExemplo.xml(NotaFiscalExemplo.CHAVE.replace("000000011","000000021"),"ARTIGO DOIS","A2","KG","2","20.00"));
        var corpoPrimeira=confirmarNota(primeira,null);var corpoSegunda=confirmarNota(segunda,null);
        try(var tarefas=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var partida=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> confirmarPrimeira=() -> {
                partida.await();return mvc.perform(post("/api/notas-fiscais/"+primeira.id()+"/confirmar").session(sessao).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(corpoPrimeira)).andReturn().getResponse().getStatus();
            };
            java.util.concurrent.Callable<Integer> confirmarSegunda=() -> {
                partida.await();return mvc.perform(post("/api/notas-fiscais/"+segunda.id()+"/confirmar").session(sessao).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(corpoSegunda)).andReturn().getResponse().getStatus();
            };
            var a=tarefas.submit(confirmarPrimeira);var b=tarefas.submit(confirmarSegunda);partida.countDown();
            org.junit.jupiter.api.Assertions.assertEquals(200,a.get(20,java.util.concurrent.TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertEquals(200,b.get(20,java.util.concurrent.TimeUnit.SECONDS));
        }
        var resposta= mvc.perform(get("/api/categorias?size=100").session(sessao))
            .andExpect(jsonPath("$.totalElements").value(13)).andReturn();
        int semClassificacao=0;
        for(var categoria:json.readTree(resposta.getResponse().getContentAsString()).get("content"))
            if(categoria.get("nome").asText().equals("Sem classificação")) semClassificacao++;
        org.junit.jupiter.api.Assertions.assertEquals(1,semClassificacao);
        mvc.perform(get("/api/produtos?size=100").session(sessao)).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void restauranteVazioImportaPrimeiraNotaComCategoriaSemEscolhaManual() throws Exception {
        registrar("Primeira compra", "primeira-compra@teste.com", "LojaA@12345");
        var sessao=login("primeira-compra@teste.com", "LojaA@12345");
        mvc.perform(get("/api/produtos?size=100").session(sessao)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/estoque").session(sessao)).andExpect(jsonPath("$.length()").value(0));
        var nota=lerNota(sessao,NotaFiscalExemplo.xml(NotaFiscalExemplo.CHAVE,"ARROZ 5KG","A1","KG","2","20.00"));
        org.junit.jupiter.api.Assertions.assertNotNull(nota.itens().getFirst().categoriaId());
        mvc.perform(get("/api/categorias/"+nota.itens().getFirst().categoriaId()).session(sessao))
            .andExpect(jsonPath("$.nome").value("Grãos"));
        mvc.perform(post("/api/notas-fiscais/"+nota.id()+"/confirmar").session(sessao).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(confirmarNota(nota,null)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.confirmada").value(true));
        mvc.perform(get("/api/produtos?size=100").session(sessao)).andExpect(jsonPath("$.content[0].categoriaNome").value("Grãos"));
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

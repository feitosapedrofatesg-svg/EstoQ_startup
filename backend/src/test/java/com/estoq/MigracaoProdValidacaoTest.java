package com.estoq;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import com.estoq.business.registro.RegistroRequestDTO;
import com.estoq.business.registro.RegistroService;
import com.estoq.business.usuarios.IUsuarioRepository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida as migrações V1-V6 num PostgreSQL real embutido (Zonky) em dois cenários:
 * 1) banco legado "limpo" (sem nenhuma coluna de tenant);
 * 2) estado real de produção de 2026-09-24 (ddl-auto=update já criou restaurante_id
 *    em 9 das 17 tabelas e também a tabela restaurantes — que o deploy "drop+recreate"
 *    já resolveu via V1 aplicada; aqui simulamos as 9 tabelas armadas).
 */
class MigracaoProdValidacaoTest {

    static EmbeddedPostgres pg;
    static String jdbcUrl;

    static final String[] TABELAS = {"ajustes", "alertas", "auditoria", "balancos", "categorias",
            "configuracoes_balanco", "consumos", "desperdicios", "entradas", "itens_balanco",
            "lotes", "movimentacoes", "parametros_cmv", "parametros_estoque", "produtos",
            "produtos_abertos", "usuarios"};

    /** As 9 tabelas em que a produção 2026-09-24 tinha a coluna via ddl-auto=update. */
    static final List<String> ARMADAS_COM_COLUNA = List.of(
            "alertas", "auditoria", "balancos", "itens_balanco", "lotes",
            "movimentacoes", "parametros_estoque", "produtos", "produtos_abertos");

    @BeforeAll
    static void subir() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        jdbcUrl = pg.getJdbcUrl("postgres", "postgres");
    }

    @AfterAll
    static void derrubar() throws Exception {
        if (pg != null) {
            pg.close();
        }
    }

    @Test
    void migracoesAplicamNumBancoLegado() throws Exception {
        criarLegado(null, List.of());
        rodarMigracoes(null);
        validarResultado(null);
    }

    @Test
    void migracoesAplicamNoEstadoParcialDeProducao() throws Exception {
        criarLegado("s2", ARMADAS_COM_COLUNA);
        rodarMigracoes("s2");
        validarResultado("s2");
    }

    @Test
    void bancoNovoIniciaEmProducaoECadastraRestaurante() throws Exception {
        try (var context = new SpringApplicationBuilder(EstoqApplication.class).profiles("prod").run(
                "--server.port=0",
                "--spring.datasource.url=" + jdbcUrl + "&currentSchema=novo",
                "--spring.datasource.username=postgres",
                "--spring.datasource.password=postgres",
                "--spring.flyway.schemas=novo",
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.flyway.enabled=true")) {
            // Exercita a consulta histórica também no PostgreSQL real, não só no H2.
            assertEquals(0, context.getBean(com.estoq.business.lotes.ILoteRepository.class)
                    .posicoesHistoricas(java.time.LocalDateTime.now().minusDays(30), java.time.LocalDateTime.now()).size());
            var flyway = context.getBean(Flyway.class);
            assertEquals("8", flyway.info().current().getVersion().toString());
            assertEquals(0, flyway.migrate().migrationsExecuted);
            context.getBean(RegistroService.class).registrar(new RegistroRequestDTO(
                    "Restaurante novo", "Responsável", "novo@teste.com", "Senha@12345"));
            assertTrue(context.getBean(IUsuarioRepository.class).existsByEmailIgnoreCase("novo@teste.com"));
            var admin=context.getBean(IUsuarioRepository.class).findAll().stream()
                .filter(u -> u.getEmail().equals("novo@teste.com")).findFirst().orElseThrow();
            assertEquals(13,context.getBean(com.estoq.business.categorias.ICategoriaRepository.class)
                .findAllByRestauranteIdAndAtivoTrue(admin.getRestauranteId()).size());
        }
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            // O bootstrap da plataforma continua aceitando usuário sem restaurante.
            st.execute("insert into novo.usuarios (ativo, data_hora_criacao, version, nome, email, senha, perfil) "
                    + "values (true, now(), 0, 'Plataforma', 'plataforma@teste.com', 'hash', 'PLATAFORMA')");
        }
    }

    @Test
    void categoriasPadraoApenasParaRestaurantesAtivosSemCategoriasAtivas() throws Exception {
        var configuracao=Flyway.configure().dataSource(jdbcUrl,"postgres","postgres")
            .schemas("categorias_auto").locations("classpath:db/migration");
        configuracao.target("5").load().migrate();
        try(var conn=DriverManager.getConnection(jdbcUrl);var st=conn.createStatement()) {
            st.execute("set search_path to categorias_auto");
            st.execute("insert into restaurantes(id,nome,ativo,data_hora_criacao,version) values "
                + "(1,'Vazio',true,now(),0),(2,'Personalizado',true,now(),0),(3,'Sem classificação',true,now(),0),"
                + "(4,'Categorias inativas',true,now(),0),(5,'Restaurante inativo',false,now(),0)");
            st.execute("insert into categorias(restaurante_id,nome,ativo,data_hora_criacao,version) values "
                + "(2,'Organização própria',true,now(),0),(3,'Sem classificação',true,now(),0),(4,'Antiga',false,now(),0)");
        }
        var atual=Flyway.configure().dataSource(jdbcUrl,"postgres","postgres")
            .schemas("categorias_auto").locations("classpath:db/migration").target("6").load();
        assertEquals(1,atual.migrate().migrationsExecuted);
        assertEquals(0,atual.migrate().migrationsExecuted);
        try(var conn=DriverManager.getConnection(jdbcUrl);var st=conn.createStatement()) {
            st.execute("set search_path to categorias_auto");
            for(long loja:List.of(1L,4L)) {
                try(var rs=st.executeQuery("select nome from categorias where ativo=true and restaurante_id="+loja)) {
                    assertEquals(new java.util.HashSet<>(com.estoq.business.categorias.CategoriaPadraoService.NOMES),
                        new java.util.HashSet<>(coletar(rs)));
                }
            }
            try(var rs=st.executeQuery("select nome from categorias where restaurante_id=2")) {
                assertEquals(List.of("Organização própria"),coletar(rs));
            }
            try(var rs=st.executeQuery("select nome from categorias where restaurante_id=3")) {
                assertEquals(List.of("Sem classificação"),coletar(rs));
            }
            try(var rs=st.executeQuery("select count(*) from categorias where restaurante_id=5")) {
                rs.next();assertEquals(0,rs.getInt(1));
            }
            try(var rs=st.executeQuery("select count(*) from categorias where restaurante_id=4 and ativo=false and nome='Antiga'")) {
                rs.next();assertEquals(1,rs.getInt(1));
            }
        }
    }

    private void criarLegado(String schema, List<String> tabelasArmadas) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            if (schema != null) {
                st.execute("create schema if not exists " + schema);
            }
            String prefixo = schema != null ? schema + "." : "";
            // Usuários legado + admin ativo.
            st.execute("create table " + prefixo + "usuarios (id bigint generated by default as identity primary key, "
                    + "nome varchar(120) not null, email varchar(254) not null unique, senha varchar(120) not null, "
                    + "perfil varchar(30) not null, ativo boolean not null default true, "
                    + "data_hora_criacao timestamp(6) not null, version bigint not null default 0)");
            st.execute("insert into " + prefixo + "usuarios (nome, email, senha, perfil, ativo, data_hora_criacao) "
                    + "values ('Admin EstoQ', 'admin@estoq.com', 'x', 'ADMIN', true, now())");
            // CHECK antigo do perfil (ddl-auto de versões anteriores não conhecia PLATAFORMA).
            st.execute("alter table " + prefixo + "usuarios add constraint usuarios_perfil_check "
                    + "check (perfil in ('ESTAGIARIO', 'ATENDENTE', 'COZINHEIRO', 'ADMIN'))");
            for (String t : TABELAS) {
                if ("usuarios".equals(t)) {
                    continue;
                }
                if ("categorias".equals(t)) {
                    st.execute("create table " + prefixo + "categorias (id bigint generated by default as identity primary key, "
                        + "nome varchar(120) not null, descricao varchar(500), ativo boolean not null, "
                        + "data_hora_criacao timestamp(6) not null, version bigint not null default 0)");
                } else if ("lotes".equals(t)) {
                    st.execute("create table " + prefixo + "lotes (id bigint generated by default as identity primary key, codigo varchar(60) not null)");
                } else if ("parametros_estoque".equals(t)) {
                    st.execute("create table " + prefixo + "parametros_estoque (id bigint generated by default as identity primary key, produto_id bigint)");
                } else {
                    st.execute("create table " + prefixo + t + " (id bigint generated by default as identity primary key)");
                }
                if (tabelasArmadas.contains(t)) {
                    st.execute("alter table " + prefixo + t + " add column restaurante_id bigint");
                }
            }
            // Uniques globais antigos + dados de exemplo.
            st.execute("alter table " + prefixo + "lotes add constraint uk_lotes_codigo unique (codigo)");
            st.execute("insert into " + prefixo + "lotes (codigo) values ('L-0001')");
            st.execute("alter table " + prefixo + "parametros_estoque add constraint uk_parametros_estoque_produto unique (produto_id)");
        }
    }

    private void rodarMigracoes(String schema) {
        var configuracao = Flyway.configure()
                .dataSource(jdbcUrl, "postgres", "postgres")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/migration");
        if (schema != null) {
            configuracao = configuracao.schemas(schema);
        }
        var resultado = configuracao.load().migrate();
        assertEquals(8, resultado.migrationsExecuted, "V1 até V8 devem executar no banco legado");
    }

    private void validarResultado(String schema) throws Exception {
        String prefixo = schema != null ? schema + "." : "";
        String caminho = schema != null ? schema : "public";
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            try (Statement s2 = conn.createStatement()) {
                s2.execute("select set_config('search_path', '" + caminho + "', false)");
            }
            try (ResultSet rs = st.executeQuery("select count(*) from restaurantes where nome = 'EstoQ Padrão'")) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery("select perfil, restaurante_id from usuarios where email = 'admin@estoq.com'")) {
                rs.next();
                assertEquals("PLATAFORMA", rs.getString(1));
                assertNull(rs.getObject(2));
            }
            try (ResultSet rs = st.executeQuery("select restaurante_id from lotes")) {
                rs.next();
                assertEquals(1L, rs.getLong(1));
            }
            try (ResultSet rs = st.executeQuery("select conname from pg_constraint "
                    + "where conrelid = 'lotes'::regclass and contype = 'u' order by conname")) {
                assertEquals(List.of("uk_lotes_tenant_codigo"), coletar(rs));
            }
            try (ResultSet rs = st.executeQuery("select count(*) from pg_constraint "
                    + "where contype = 'f' and confrelid = 'restaurantes'::regclass")) {
                rs.next();
                assertEquals(23, rs.getInt(1)); // V8 acrescenta a referência da auditoria da plataforma.
            }
            try (ResultSet rs = st.executeQuery("select conname from pg_constraint "
                    + "where conrelid = 'operacoes_consumo'::regclass and contype = 'u'")) {
                assertEquals(List.of("uk_operacao_consumo"), coletar(rs));
            }
            try (ResultSet rs = st.executeQuery("insert into restaurantes (nome, ativo, data_hora_criacao, version) "
                    + "values ('Loja Nova', true, now(), 0) returning id")) {
                rs.next();
                assertTrue(rs.getLong(1) > 1);
            }
        }
    }

    private List<String> coletar(ResultSet rs) throws Exception {
        List<String> nomes = new ArrayList<>();
        while (rs.next()) {
            nomes.add(rs.getString(1));
        }
        return nomes;
    }
}

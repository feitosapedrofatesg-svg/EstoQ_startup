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
            assertEquals("12", flyway.info().current().getVersion().toString());
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
                    st.execute("create table " + prefixo + "parametros_estoque (id bigint generated by default as identity primary key, produto_id bigint, "
                            + "estoque_minimo numeric(18,3) not null default 0, estoque_medio numeric(18,3) not null default 0, "
                            + "estoque_maximo numeric(18,3) not null default 0, version bigint not null default 0, data_atualizacao timestamp)");
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

    @Test
    void minimoPadraoAtualizaZerosSemAlterarPersonalizadosENaoReaplicaDepois() throws Exception {
        var antes = Flyway.configure().dataSource(jdbcUrl, "postgres", "postgres")
                .schemas("minimo_padrao").locations("classpath:db/migration").target("8").load();
        antes.migrate();
        try (var conn = DriverManager.getConnection(jdbcUrl); var st = conn.createStatement()) {
            st.execute("set search_path to minimo_padrao");
            st.execute("insert into restaurantes(id,nome,ativo,data_hora_criacao,version) values "
                    + "(1,'Loja A',true,now(),0),(2,'Loja B',true,now(),0)");
            st.execute("insert into categorias(id,restaurante_id,nome,ativo,data_hora_criacao,version) values "
                    + "(1,1,'Categoria A',true,now(),0),(2,2,'Categoria B',true,now(),0)");
            st.execute("insert into produtos(id,restaurante_id,categoria_id,nome,unidade_medida,ativo,data_hora_criacao,version) values "
                    + "(1,1,1,'Zerado A','KG',true,now(),0),(2,1,1,'Personalizado A','KG',true,now(),0),"
                    + "(3,2,2,'Zerado B','L',true,now(),0),(4,2,2,'Personalizado B','UN',true,now(),0)");
            st.execute("insert into parametros_estoque(produto_id,restaurante_id,ativo,data_hora_criacao,version,"
                    + "tempo_reposicao_dias,periodo_analise_dias,consumo_medio_diario,dias_alerta_vencimento,"
                    + "estoque_minimo,estoque_medio,estoque_maximo) values "
                    + "(1,1,true,now(),4,0,30,0,7,0,0,0),(2,1,true,now(),2,0,30,0,7,7,8,9),"
                    + "(3,2,true,now(),1,0,30,0,7,0,5,9),(4,2,true,now(),0,0,30,0,7,0.5,1,2)");
        }
        var depois = Flyway.configure().dataSource(jdbcUrl, "postgres", "postgres")
                .schemas("minimo_padrao").locations("classpath:db/migration").target("9").load();
        assertEquals(1, depois.migrate().migrationsExecuted);
        try (var conn = DriverManager.getConnection(jdbcUrl); var st = conn.createStatement()) {
            st.execute("set search_path to minimo_padrao");
            try (var rs = st.executeQuery("select produto_id,restaurante_id,estoque_minimo,estoque_medio,estoque_maximo,version,data_atualizacao "
                    + "from parametros_estoque order by produto_id")) {
                String[][] esperado = {{"3", "3", "3", "5"}, {"7", "8", "9", "2"},
                        {"3", "5", "9", "2"}, {"0.5", "1", "2", "0"}};
                for (int i = 0; i < esperado.length; i++) {
                    assertTrue(rs.next()); assertEquals(i < 2 ? 1 : 2, rs.getInt("restaurante_id"));
                    for (int coluna = 0; coluna < 3; coluna++) {
                        assertEquals(0, new java.math.BigDecimal(esperado[i][coluna]).compareTo(rs.getBigDecimal(coluna + 3)));
                    }
                    assertEquals(Integer.parseInt(esperado[i][3]), rs.getInt("version"));
                    if (i == 1 || i == 3) assertNull(rs.getObject("data_atualizacao"));
                    else assertTrue(rs.getObject("data_atualizacao") != null);
                }
                assertTrue(!rs.next());
            }
            // Uma escolha explícita de zero, feita depois da atualização, continua permitida.
            st.execute("update parametros_estoque set estoque_minimo=0 where produto_id=1");
            st.execute("insert into produtos(id,restaurante_id,categoria_id,nome,unidade_medida,ativo,data_hora_criacao,version) "
                    + "values(5,1,1,'Criado por SQL','KG',true,now(),0)");
            st.execute("insert into parametros_estoque(produto_id,restaurante_id,ativo,data_hora_criacao,version,"
                    + "tempo_reposicao_dias,periodo_analise_dias,consumo_medio_diario,dias_alerta_vencimento) "
                    + "values(5,1,true,now(),0,0,30,0,7)");
            try (var rs = st.executeQuery("select estoque_minimo,estoque_medio,estoque_maximo from parametros_estoque where produto_id=5")) {
                assertTrue(rs.next());
                for (int i = 1; i <= 3; i++) assertEquals(0, new java.math.BigDecimal("3").compareTo(rs.getBigDecimal(i)));
            }
        }
        assertEquals(0, depois.migrate().migrationsExecuted);
        try (var conn = DriverManager.getConnection(jdbcUrl); var st = conn.createStatement();
             var rs = st.executeQuery("select estoque_minimo from minimo_padrao.parametros_estoque where produto_id=1")) {
            assertTrue(rs.next()); assertEquals(0, rs.getBigDecimal(1).signum());
        }
    }

    @Test
    void explicacaoNovaPreservaCategoriaECustoDeNotaJaConfirmada() throws Exception {
        var antes = Flyway.configure().dataSource(jdbcUrl, "postgres", "postgres")
                .schemas("categoria_nota").locations("classpath:db/migration").target("9").load();
        antes.migrate();
        try (var conn = DriverManager.getConnection(jdbcUrl); var st = conn.createStatement()) {
            st.execute("set search_path to categoria_nota");
            st.execute("insert into restaurantes(id,nome,ativo,data_hora_criacao,version) values(1,'Loja',true,now(),0)");
            st.execute("insert into categorias(id,restaurante_id,nome,ativo,data_hora_criacao,version) "
                    + "values(1,1,'Hortifruti',true,now(),0)");
            st.execute("insert into notas_fiscais(id,restaurante_id,data_hora_criacao,chave,fornecedor,documento_fornecedor,"
                    + "numero,serie,valor_total,confirmada) values(1,1,now(),repeat('1',44),'Fornecedor','123','1','1',20,true)");
            st.execute("insert into itens_nota_fiscal(restaurante_id,nota_id,data_hora_criacao,numero,codigo,descricao,unidade_nota,"
                    + "quantidade_nota,valor_bruto,desconto,valor_rateado,valor_entrada,categoria_id) "
                    + "values(1,1,now(),1,'BE1','BEB GAISE H2O LIMAO','UN',2,20,0,0,20,1)");
        }
        var depois = Flyway.configure().dataSource(jdbcUrl, "postgres", "postgres")
                .schemas("categoria_nota").locations("classpath:db/migration").target("10").load();
        assertEquals(1,depois.migrate().migrationsExecuted);
        try (var conn = DriverManager.getConnection(jdbcUrl); var st = conn.createStatement();
             var rs = st.executeQuery("select i.*,n.confirmada from categoria_nota.itens_nota_fiscal i "
                     + "join categoria_nota.notas_fiscais n on n.id=i.nota_id")) {
            assertTrue(rs.next()); assertEquals(1,rs.getLong("categoria_id"));
            assertTrue(rs.getBoolean("confirmada")); assertEquals(0,rs.getInt("version"));
            assertEquals(0,new java.math.BigDecimal("20").compareTo(rs.getBigDecimal("valor_entrada")));
            assertNull(rs.getString("origem_categoria")); assertNull(rs.getString("motivo_categoria"));
            org.junit.jupiter.api.Assertions.assertFalse(rs.getBoolean("revisar_categoria"));
        }
        assertEquals(0,depois.migrate().migrationsExecuted);
    }

    @Test
    void confirmacaoELoteFabricantePreservamContasESaldosExistentes() throws Exception {
        var antes = Flyway.configure().dataSource(jdbcUrl, "postgres", "postgres")
                .schemas("novos_campos").locations("classpath:db/migration").target("10").load();
        antes.migrate();
        try (var conn = DriverManager.getConnection(jdbcUrl); var st = conn.createStatement()) {
            st.execute("set search_path to novos_campos");
            st.execute("insert into restaurantes(id,nome,ativo,data_hora_criacao,version) values(1,'Loja',true,now(),0)");
            st.execute("insert into usuarios(nome,email,senha,perfil,restaurante_id,ativo,data_hora_criacao,version) "
                    + "values('Admin','legado@teste.local','hash','ADMIN',1,true,now(),0)");
            st.execute("insert into categorias(id,nome,restaurante_id,ativo,data_hora_criacao,version) values(1,'Grãos',1,true,now(),0)");
            st.execute("insert into produtos(id,nome,categoria_id,unidade_medida,restaurante_id,ativo,data_hora_criacao,version) "
                    + "values(1,'Arroz',1,'KG',1,true,now(),0)");
            st.execute("insert into lotes(codigo,produto_id,quantidade_inicial,quantidade_atual,preco_unitario,restaurante_id,ativo,data_hora_criacao,version) "
                    + "values('L-LEGADO',1,10,4,12.5,1,true,now(),0)");
        }
        var depois = Flyway.configure().dataSource(jdbcUrl, "postgres", "postgres")
                .schemas("novos_campos").locations("classpath:db/migration").load();
        assertEquals(2, depois.migrate().migrationsExecuted);
        try (var conn = DriverManager.getConnection(jdbcUrl); var st = conn.createStatement()) {
            try (var rs = st.executeQuery("select confirmacao_email_obrigatoria,email_confirmado_em,senha,version from novos_campos.usuarios")) {
                assertTrue(rs.next()); assertTrue(!rs.getBoolean(1)); assertNull(rs.getObject(2));
                assertEquals("hash", rs.getString(3)); assertEquals(0, rs.getLong(4));
            }
            try (var rs = st.executeQuery("select codigo,lote_fabricante,quantidade_atual,preco_unitario from novos_campos.lotes")) {
                assertTrue(rs.next()); assertEquals("L-LEGADO", rs.getString(1)); assertNull(rs.getString(2));
                assertEquals(0, new java.math.BigDecimal("4").compareTo(rs.getBigDecimal(3)));
                assertEquals(0, new java.math.BigDecimal("12.5").compareTo(rs.getBigDecimal(4)));
            }
        }
        assertEquals(0, depois.migrate().migrationsExecuted);
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
        assertEquals(12, resultado.migrationsExecuted, "V1 até V12 devem executar no banco legado");
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

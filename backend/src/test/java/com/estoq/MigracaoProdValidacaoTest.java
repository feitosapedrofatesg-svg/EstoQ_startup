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
    void bebidasConvertemHistoricoECustosSemAdivinharFatoresAmbiguos() throws Exception {
        Flyway.configure().dataSource(jdbcUrl,"postgres","postgres").schemas("bebidas")
                .locations("classpath:db/migration").target("13").load().migrate();
        try (var c = DriverManager.getConnection(jdbcUrl); var st = c.createStatement()) {
            st.execute("set search_path to bebidas");
            st.execute("""
                    insert into restaurantes(id,nome,ativo,data_hora_criacao,version)
                    values (1,'Cozinha 1',true,now(),0),(2,'Cozinha 2',true,now(),0);
                    insert into categorias(id,restaurante_id,nome,ativo,data_hora_criacao,version)
                    values (1,1,'Bebidas',true,now(),0),(2,2,'Bebidas',true,now(),0),(3,1,'Óleos',true,now(),0);
                    insert into usuarios(id,restaurante_id,nome,email,senha,perfil,ativo,data_hora_criacao,version)
                    values (1,1,'Admin','a@teste.com','hash','ADMIN',true,now(),0);
                    insert into produtos(id,restaurante_id,categoria_id,nome,unidade_medida,ativo,data_hora_criacao,version) values
                    (1,1,1,'H2O 500ML','ML',true,now(),0),
                    (2,1,1,'Refrigerante 2L','L',true,now(),0),
                    (3,1,1,'Bebida com fatores divergentes','ML',true,now(),0),
                    (4,1,1,'Bebida sem embalagem conhecida','ML',true,now(),0),
                    (5,1,1,'Suco 500ML','ML',true,now(),0),
                    (6,2,2,'H2O 500ML','ML',true,now(),0),
                    (7,1,3,'Azeite 500ML','ML',true,now(),0);
                    insert into lotes(id,produto_id,restaurante_id,codigo,quantidade_inicial,quantidade_atual,preco_unitario,ativo,data_hora_criacao,version) values
                    (1,1,1,'L1',6000,5800,0.015,true,now(),0),
                    (2,2,1,'L2',60,58,1.5,true,now(),0),
                    (3,3,1,'L3',6000,6000,0.015,true,now(),0),
                    (4,4,1,'L4',6000,6000,0.015,true,now(),0),
                    (5,5,1,'L5',333.333,333.333,0.015,true,now(),0),
                    (6,6,2,'L6',15000,14500,0.006,true,now(),0),
                    (7,7,1,'L7',15000,15000,0.006,true,now(),0);
                    insert into movimentacoes(id,tipo,produto_id,lote_id,usuario_id,restaurante_id,data_hora,quantidade,quantidade_anterior,quantidade_posterior,ativo,data_hora_criacao,version) values
                    (1,'ENTRADA',1,1,1,1,now(),6000,0,6000,true,now(),0),
                    (2,'CONSUMO',1,1,1,1,now(),200,6000,5800,true,now(),0);
                    insert into entradas(id,restaurante_id,valor_total_pago,unidade_compra) values (1,1,90,'ML');
                    insert into consumos(id,restaurante_id) values (2,1);
                    insert into balancos(id,restaurante_id,usuario_id,data_hora,status,tipo,ativo,data_hora_criacao,version)
                    values (1,1,1,now(),'CONCLUIDO','GERAL',true,now(),0);
                    insert into itens_balanco(id,restaurante_id,balanco_id,produto_id,quantidade_sistema,quantidade_fisica,ajuste_aplicado,ativo,data_hora_criacao,version)
                    values (1,1,1,1,6000,5800,true,true,now(),0);
                    insert into produtos_abertos(id,restaurante_id,lote_id,usuario_id,data_abertura,quantidade_aberta,quantidade_utilizada,finalizado,ativo,data_hora_criacao,version)
                    values (1,1,1,1,now(),1000,200,false,true,now(),0);
                    insert into parametros_estoque(id,restaurante_id,produto_id,tempo_reposicao_dias,periodo_analise_dias,consumo_medio_diario,estoque_minimo,estoque_medio,estoque_maximo,dias_alerta_vencimento,ativo,data_hora_criacao,version)
                    values (1,1,1,0,30,200,3,3,3,0,true,now(),0),(2,1,2,0,30,2,6,8,10,0,true,now(),0);
                    insert into notas_fiscais(id,restaurante_id,chave,fornecedor,documento_fornecedor,numero,serie,valor_total,confirmada,ativo,data_hora_criacao,version)
                    values (1,1,repeat('1',44),'Fornecedor','123','1','1',90,true,true,now(),0);
                    insert into itens_nota_fiscal(id,nota_id,restaurante_id,numero,codigo,descricao,unidade_nota,quantidade_nota,valor_bruto,desconto,valor_rateado,valor_entrada,produto_id,unidade_estoque,fator,quantidade_entrada,ativo,data_hora_criacao,version) values
                    (1,1,1,1,'H2O','H2O 500ML','UN',30,90,0,0,90,1,'ML',200,6000,true,now(),0),
                    (2,1,1,2,'AMB1','Bebida','UN',1,0,0,0,0,3,'ML',100,100,true,now(),0),
                    (3,1,1,3,'AMB2','Bebida','UN',1,0,0,0,0,3,'ML',200,200,true,now(),0);
                    insert into vinculos_fornecedor(id,restaurante_id,documento_fornecedor,codigo,descricao,unidade_nota,produto_id,unidade_estoque,fator,ativo,data_hora_criacao,version)
                    values (1,1,'123','H2O','H2O 500ML','UN',1,'ML',200,true,now(),0),
                    (2,1,'123','CX','H2O 500ML','CX',1,'ML',2400,true,now(),0);
                    """);
        }
        var flyway = Flyway.configure().dataSource(jdbcUrl,"postgres","postgres").schemas("bebidas")
                .locations("classpath:db/migration").target("14").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var c = DriverManager.getConnection(jdbcUrl); var st = c.createStatement()) {
            st.execute("set search_path to bebidas");
            try (var rs = st.executeQuery("select unidade_medida from produtos order by id")) {
                assertEquals(List.of("UN","UN","ML","ML","ML","UN","ML"), coletar(rs));
            }
            try (var rs = st.executeQuery("select quantidade_inicial,quantidade_atual,preco_unitario,quantidade_atual*preco_unitario from lotes where id in (1,2,6) order by id")) {
                while (rs.next()) {
                    assertEquals(0,new java.math.BigDecimal("30").compareTo(rs.getBigDecimal(1)));
                    assertEquals(0,new java.math.BigDecimal("29").compareTo(rs.getBigDecimal(2)));
                    assertEquals(0,new java.math.BigDecimal("3").compareTo(rs.getBigDecimal(3)));
                    assertEquals(0,new java.math.BigDecimal("87").compareTo(rs.getBigDecimal(4)));
                }
            }
            try (var rs = st.executeQuery("select quantidade,quantidade_anterior,quantidade_posterior from movimentacoes where id=2")) {
                rs.next(); assertEquals(1,rs.getInt(1)); assertEquals(30,rs.getInt(2)); assertEquals(29,rs.getInt(3));
            }
            try (var rs = st.executeQuery("select quantidade_sistema,quantidade_fisica from itens_balanco where id=1")) {
                rs.next(); assertEquals(30,rs.getInt(1)); assertEquals(29,rs.getInt(2));
            }
            try (var rs = st.executeQuery("select quantidade_aberta,quantidade_utilizada from produtos_abertos where id=1")) {
                rs.next(); assertEquals(5,rs.getInt(1)); assertEquals(1,rs.getInt(2));
            }
            try (var rs = st.executeQuery("select unidade_estoque,fator,quantidade_entrada,quantidade_nota,valor_entrada from itens_nota_fiscal where id=1")) {
                rs.next(); assertEquals("UN",rs.getString(1)); assertEquals(1,rs.getInt(2)); assertEquals(30,rs.getInt(3));
                assertEquals(30,rs.getInt(4)); assertEquals(90,rs.getInt(5));
            }
            try (var rs = st.executeQuery("select unidade_estoque,fator from vinculos_fornecedor order by id")) {
                rs.next(); assertEquals("UN",rs.getString(1)); assertEquals(1,rs.getInt(2));
                rs.next(); assertEquals("UN",rs.getString(1)); assertEquals(12,rs.getInt(2));
            }
            try (var rs = st.executeQuery("select consumo_medio_diario,estoque_minimo,estoque_medio,estoque_maximo from parametros_estoque order by id")) {
                rs.next(); assertEquals(1,rs.getInt(1)); assertEquals(3,rs.getInt(2)); assertEquals(3,rs.getInt(3)); assertEquals(3,rs.getInt(4));
                rs.next(); assertEquals(1,rs.getInt(1)); assertEquals(3,rs.getInt(2)); assertEquals(4,rs.getInt(3)); assertEquals(5,rs.getInt(4));
            }
            try (var rs = st.executeQuery("select unidade_compra,valor_total_pago from entradas where id=1")) {
                rs.next(); assertEquals("UN",rs.getString(1)); assertEquals(90,rs.getInt(2));
            }
        }
    }

    @Test
    void reclassificaPendentesSemAlterarEscolhasManuaisEstoqueOuOutrasCozinhas() throws Exception {
        Flyway.configure().dataSource(jdbcUrl,"postgres","postgres").schemas("classificacao")
                .locations("classpath:db/migration").target("14").load().migrate();
        try (var c = DriverManager.getConnection(jdbcUrl); var st = c.createStatement()) {
            st.execute("set search_path to classificacao");
            st.execute("""
                    insert into restaurantes(id,nome,ativo,data_hora_criacao,version) values
                    (1,'Loja A',true,now(),0),(2,'Loja B',true,now(),0),(3,'Ambígua',true,now(),0),(4,'Sem destino ativo',true,now(),0);
                    insert into categorias(id,restaurante_id,nome,ativo,data_hora_criacao,version) values
                    (1,1,'Sem classificação',true,now(),0),(2,1,'Carnes',true,now(),0),
                    (3,1,'Mercearia',true,now(),0),(4,1,'Bebidas',true,now(),0),(5,1,'Limpeza',true,now(),0),
                    (21,2,'Sem classificação',true,now(),0),(22,2,'Carnes',true,now(),0),
                    (31,3,'Sem classificação',true,now(),0),(32,3,'Carnes bovinas',true,now(),0),(33,3,'Proteínas',true,now(),0),
                    (41,4,'Sem classificação',true,now(),0),(42,4,'Carnes',false,now(),0);
                    insert into produtos(id,restaurante_id,categoria_id,nome,unidade_medida,ativo,data_hora_criacao,version) values
                    (1,1,1,'COXA/SOB CONG MARCA 20 KG','KG',true,now(),5),
                    (2,1,1,'BISC MAIZENA MARCA 300G','G',true,now(),0),
                    (3,1,1,'LEITE DE COCO MARCA 500ML','ML',true,now(),0),
                    (4,1,1,'ARTIGO SEM TIPO','UN',true,now(),0),
                    (5,1,3,'ACELGA MARCA','UN',true,now(),0),
                    (6,1,1,'BISC MARCA 500G','G',false,now(),0),
                    (7,2,21,'SASSAMI MARCA 1KG','KG',true,now(),0),
                    (8,3,31,'SASSAMI MARCA 1KG','KG',true,now(),0),
                    (9,1,1,'AGUA MIN MARCA 500ML','UN',true,now(),0),
                    (10,1,1,'PO P/REFRESCO MARCA 18G','G',true,now(),0),
                    (11,1,1,'SACO LIXO MARCA 100L','L',true,now(),0),
                    (12,4,41,'SASSAMI MARCA 1KG','KG',true,now(),0);
                    insert into lotes(id,produto_id,restaurante_id,codigo,quantidade_inicial,quantidade_atual,preco_unitario,ativo,data_hora_criacao,version) values
                    (1,1,1,'A',10,7,2.5,true,now(),0),(2,9,1,'B',30,29,3,true,now(),0);
                    """);
        }
        var flyway = Flyway.configure().dataSource(jdbcUrl,"postgres","postgres").schemas("classificacao")
                .locations("classpath:db/migration").target("15").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var c = DriverManager.getConnection(jdbcUrl); var st = c.createStatement()) {
            st.execute("set search_path to classificacao");
            try (var rs = st.executeQuery("select categoria_id from produtos order by id")) {
                assertEquals(List.of("2","3","3","1","3","1","22","31","4","3","5","41"), coletar(rs));
            }
            try (var rs = st.executeQuery("select quantidade_atual,preco_unitario from lotes order by id")) {
                rs.next(); assertEquals(7,rs.getInt(1)); assertEquals(0,new java.math.BigDecimal("2.5").compareTo(rs.getBigDecimal(2)));
                rs.next(); assertEquals(29,rs.getInt(1)); assertEquals(3,rs.getInt(2));
            }
            try (var rs = st.executeQuery("select unidade_medida,version from produtos where id=9")) {
                rs.next(); assertEquals("UN",rs.getString(1)); assertEquals(1,rs.getLong(2));
            }
            try (var rs = st.executeQuery("select unidade_medida,version from produtos where id=10")) {
                rs.next(); assertEquals("G",rs.getString(1)); assertEquals(1,rs.getLong(2));
            }
            try (var rs = st.executeQuery("select version from produtos where id=5")) {
                rs.next(); assertEquals(0,rs.getLong(1));
            }
            try (var rs = st.executeQuery("select version from produtos where id=1")) {
                rs.next(); assertEquals(6,rs.getLong(1));
            }
        }
    }

    @Test
    void limpezaMigraPesoVolumeEPacotesPreservandoCustoHistoricoEMetadados() throws Exception {
        Flyway.configure().dataSource(jdbcUrl,"postgres","postgres").schemas("limpeza").locations("classpath:db/migration").target("16").load().migrate();
        try(var c=DriverManager.getConnection(jdbcUrl); var st=c.createStatement()) {
            st.execute("set search_path to limpeza");
            st.execute("""
                insert into restaurantes(id,nome,ativo,data_hora_criacao,version) values(1,'Loja',true,now(),0),(2,'Outra loja',true,now(),0);
                insert into categorias(id,restaurante_id,nome,ativo,data_hora_criacao,version) values(1,1,'Limpeza',true,now(),0),(2,1,'Mercearia',true,now(),0),(3,2,'Limpeza',true,now(),0);
                insert into usuarios(id,restaurante_id,nome,email,senha,perfil,ativo,data_hora_criacao,version) values(1,1,'Admin','teste@teste.com','hash','ADMIN',true,now(),0);
                insert into produtos(id,restaurante_id,categoria_id,nome,unidade_medida,ativo,data_hora_criacao,version) values
                (1,1,1,'ESPONJA C/6 45G','G',true,now(),0),(2,1,1,'ALCOOL LIQ 1L','L',true,now(),0),
                (3,1,1,'SACO LIXO 100L','L',true,now(),0),(4,1,1,'SEM EMBALAGEM','G',true,now(),0),
                (5,1,2,'ARROZ 5KG','KG',true,now(),0),(6,2,3,'ESPONJA C/6 45G','G',true,now(),0),
                (7,1,1,'ESPONJA C/6 C/8 45G','G',true,now(),0),(8,1,1,'ESPONJA C/6 45G','G',true,now(),0);
                insert into lotes(id,restaurante_id,produto_id,codigo,quantidade_inicial,quantidade_atual,preco_unitario,ativo,data_hora_criacao,version) values
                (1,1,1,'A',90,45,0.06,true,now(),0),(2,1,2,'B',2,1,10,true,now(),0),(3,1,3,'C',500,400,0.02,true,now(),0),
                (4,1,4,'D',90,90,1,true,now(),0),(5,1,5,'E',10,10,1,true,now(),0),(6,2,6,'F',45,45,0.06,true,now(),0),
                (7,1,7,'G',90,90,1,true,now(),0),(8,1,8,'H',45,1,0.06,true,now(),0);
                insert into movimentacoes(id,restaurante_id,tipo,produto_id,lote_id,usuario_id,data_hora,quantidade,quantidade_anterior,quantidade_posterior,ativo,data_hora_criacao,version) values
                (1,1,'ENTRADA',1,1,1,now(),90,0,90,true,now(),0),(2,1,'CONSUMO',1,1,1,now(),45,90,45,true,now(),0);
                insert into entradas(id,restaurante_id,valor_total_pago,unidade_compra,quantidade_compra,conteudo_embalagem,unidade_conteudo) values(1,1,5.40,'UN',2,45,'G');
                insert into consumos(id,restaurante_id) values(2,1);
                insert into notas_fiscais(id,restaurante_id,chave,fornecedor,documento_fornecedor,numero,serie,valor_total,confirmada,ativo,data_hora_criacao,version) values(1,1,repeat('1',44),'Fornecedor','123','1','1',5.40,true,true,now(),0);
                insert into itens_nota_fiscal(id,restaurante_id,nota_id,numero,codigo,descricao,unidade_nota,quantidade_nota,valor_bruto,desconto,valor_rateado,valor_entrada,produto_id,unidade_estoque,fator,quantidade_entrada,ativo,data_hora_criacao,version) values
                (1,1,1,1,'ESP','ESPONJA C/6 45G','UN1',2,5.40,0,0,5.40,1,'G',45,90,true,now(),0);
                insert into vinculos_fornecedor(id,restaurante_id,documento_fornecedor,codigo,descricao,unidade_nota,produto_id,unidade_estoque,fator,ativo,data_hora_criacao,version) values(1,1,'123','ESP','ESPONJA C/6 45G','UN1',1,'G',45,true,now(),0);
                """);
        }
        var flyway=Flyway.configure().dataSource(jdbcUrl,"postgres","postgres").schemas("limpeza").locations("classpath:db/migration").load();
        assertEquals(1,flyway.migrate().migrationsExecuted); assertEquals(0,flyway.migrate().migrationsExecuted);
        try(var c=DriverManager.getConnection(jdbcUrl);var st=c.createStatement()) {
            st.execute("set search_path to limpeza");
            try(var rs=st.executeQuery("select unidade_medida from produtos order by id")) { assertEquals(List.of("UN","UN","UN","G","KG","UN","G","G"),coletar(rs)); }
            try(var rs=st.executeQuery("select quantidade_inicial,quantidade_atual,preco_unitario,quantidade_atual*preco_unitario from lotes where id=1")) {
                rs.next();assertEquals(12,rs.getInt(1));assertEquals(6,rs.getInt(2));assertEquals(0,new java.math.BigDecimal("0.45").compareTo(rs.getBigDecimal(3)));assertEquals(0,new java.math.BigDecimal("2.70").compareTo(rs.getBigDecimal(4)));
            }
            try(var rs=st.executeQuery("select quantidade_compra,conteudo_embalagem,unidade_conteudo,valor_total_pago from entradas where id=1")) {
                rs.next();assertEquals(2,rs.getInt(1));assertEquals(6,rs.getInt(2));assertEquals("UN",rs.getString(3));assertEquals(0,new java.math.BigDecimal("5.40").compareTo(rs.getBigDecimal(4)));
            }
            try(var rs=st.executeQuery("select quantidade,quantidade_anterior,quantidade_posterior from movimentacoes where id=2")) {rs.next();assertEquals(6,rs.getInt(1));assertEquals(12,rs.getInt(2));assertEquals(6,rs.getInt(3));}
            try(var rs=st.executeQuery("select unidade_estoque,fator,quantidade_entrada,quantidade_nota from itens_nota_fiscal where id=1")) {rs.next();assertEquals("UN",rs.getString(1));assertEquals(6,rs.getInt(2));assertEquals(12,rs.getInt(3));assertEquals(2,rs.getInt(4));}
            try(var rs=st.executeQuery("select unidade_estoque,fator from vinculos_fornecedor where id=1")) {rs.next();assertEquals("UN",rs.getString(1));assertEquals(6,rs.getInt(2));}
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
            assertEquals("17", flyway.info().current().getVersion().toString());
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
                } else if ("produtos".equals(t)) {
                    st.execute("create table " + prefixo + "produtos (id bigint generated by default as identity primary key, "
                            + "nome varchar(120), unidade_medida varchar(20), categoria_id bigint, ativo boolean not null default true, version bigint not null default 0)");
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
        assertEquals(7, depois.migrate().migrationsExecuted);
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

    @Test
    void loginGooglePreservaHashEPermiteSomenteContasComUmaCredencial() throws Exception {
        var antes = Flyway.configure().dataSource(jdbcUrl, "postgres", "postgres")
                .schemas("login_google").locations("classpath:db/migration").target("12").load();
        antes.migrate();
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        var hash = encoder.encode("SenhaSomenteDoTeste123");
        try (var conn = DriverManager.getConnection(jdbcUrl);
                var inserir = conn.prepareStatement("insert into login_google.usuarios(nome,email,senha,perfil,ativo,data_hora_criacao,version) "
                        + "values('Legado','legado@teste.com',?,'PLATAFORMA',true,now(),0)")) {
            inserir.setString(1, hash); inserir.executeUpdate();
        }
        var depois = Flyway.configure().dataSource(jdbcUrl, "postgres", "postgres")
                .schemas("login_google").locations("classpath:db/migration").load();
        assertEquals(5, depois.migrate().migrationsExecuted);
        try (var conn = DriverManager.getConnection(jdbcUrl); var st = conn.createStatement()) {
            st.execute("set search_path to login_google");
            try (var rs = st.executeQuery("select senha,google_sub from usuarios where email='legado@teste.com'")) {
                assertTrue(rs.next()); assertEquals(hash, rs.getString(1)); assertNull(rs.getString(2));
                assertTrue(encoder.matches("SenhaSomenteDoTeste123", rs.getString(1)));
            }
            st.execute("insert into usuarios(nome,email,google_sub,perfil,ativo,data_hora_criacao,version) "
                    + "values('Google','google@teste.com','sub-unico','PLATAFORMA',true,now(),0)");
            org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, () -> st.execute(
                    "insert into usuarios(nome,email,google_sub,perfil,ativo,data_hora_criacao,version) "
                    + "values('Duplicado','outro@teste.com','sub-unico','PLATAFORMA',true,now(),0)"));
            org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, () -> st.execute(
                    "insert into usuarios(nome,email,perfil,ativo,data_hora_criacao,version) "
                    + "values('Sem acesso','sem@teste.com','PLATAFORMA',true,now(),0)"));
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
        assertEquals(17, resultado.migrationsExecuted, "V1 até V17 devem executar no banco legado");
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

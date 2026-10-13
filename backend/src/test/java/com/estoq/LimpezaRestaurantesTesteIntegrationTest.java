package com.estoq;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

/** Exercita o mesmo SQL administrativo contra PostgreSQL real e suas FKs. */
class LimpezaRestaurantesTesteIntegrationTest {
    private String script() throws Exception {
        Path raiz = Path.of("").toAbsolutePath();
        if (raiz.getFileName().toString().equals("backend")) raiz = raiz.getParent();
        return Files.readString(raiz.resolve("scripts/limpar-restaurantes-teste.sql"));
    }

    private void preparar(Connection c) throws Exception {
        try (var s = c.createStatement()) {
            s.execute("INSERT INTO restaurantes(nome,ativo,data_hora_criacao,version) VALUES "
                + "('Teste',true,now(),0)");
            s.execute("INSERT INTO usuarios(nome,email,senha,perfil,ativo,data_hora_criacao,version) "
                + "VALUES ('Plataforma','plataforma@teste.com','hash-preservado','PLATAFORMA',true,now(),0)");
            s.execute("INSERT INTO usuarios(restaurante_id,nome,email,senha,perfil,ativo,data_hora_criacao,version) "
                + "VALUES (1,'Admin teste','admin@teste.com','hash','ADMIN',true,now(),0)");
            s.execute("INSERT INTO categorias(restaurante_id,nome,ativo,data_hora_criacao,version) "
                + "VALUES (1,'Teste',true,now(),0)");
            s.execute("INSERT INTO produtos(restaurante_id,categoria_id,nome,unidade_medida,ativo,data_hora_criacao,version) "
                + "VALUES (1,1,'Item','UN',true,now(),0)");
            // O retrato é anterior ao cadastro do restaurante que será preservado.
            s.execute("CREATE TEMP TABLE alvos_limpeza AS SELECT id,nome,data_hora_criacao AS criacao FROM restaurantes");
            s.execute("INSERT INTO restaurantes(nome,ativo,data_hora_criacao,version) VALUES ('Novo',true,now(),0)");
        }
    }

    private long contar(Connection c, String sql) throws Exception {
        try(var s=c.createStatement(); var r=s.executeQuery(sql)) { r.next(); return r.getLong(1); }
    }

    @Test
    void excluiAlvosPreservaPlataformaENovosRestaurantes() throws Exception {
        try(var pg=EmbeddedPostgres.builder().start()) {
            var url=pg.getJdbcUrl("postgres","postgres");
            Flyway.configure().dataSource(url,"postgres","postgres").locations("classpath:db/migration").load().migrate();
            try(var c=DriverManager.getConnection(url)) {
                preparar(c);
                long migracoes=contar(c,"SELECT count(*) FROM flyway_schema_history");
                c.setAutoCommit(false);
                try(var s=c.createStatement()) { s.execute(script()); }
                c.commit();
                assertEquals(0,contar(c,"SELECT count(*) FROM restaurantes WHERE id=1"));
                assertEquals(1,contar(c,"SELECT count(*) FROM restaurantes WHERE id=2 AND ativo"));
                assertEquals(1,contar(c,"SELECT count(*) FROM usuarios WHERE perfil='PLATAFORMA' AND senha='hash-preservado'"));
                assertEquals(0,contar(c,"SELECT count(*) FROM produtos"));
                assertEquals(migracoes,contar(c,"SELECT count(*) FROM flyway_schema_history"));
                assertEquals(3,contar(c,"INSERT INTO restaurantes(nome,ativo,data_hora_criacao,version) VALUES ('Real',true,now(),0) RETURNING id"));
            }
        }
    }

    @Test
    void falhaDeFkReverteAExclusaoInteira() throws Exception {
        try(var pg=EmbeddedPostgres.builder().start()) {
            var url=pg.getJdbcUrl("postgres","postgres");
            Flyway.configure().dataSource(url,"postgres","postgres").locations("classpath:db/migration").load().migrate();
            try(var c=DriverManager.getConnection(url)) {
                preparar(c);
                // Referência indevida de um restaurante preservado força falha após deletes anteriores.
                try(var s=c.createStatement()) { s.execute("INSERT INTO produtos(restaurante_id,categoria_id,nome,unidade_medida,ativo,data_hora_criacao,version) VALUES (2,1,'Externo','UN',true,now(),0)"); }
                c.setAutoCommit(false);
                assertThrows(SQLException.class,()->{try(var s=c.createStatement()){s.execute(script());}});
                c.rollback();
                assertEquals(1,contar(c,"SELECT count(*) FROM restaurantes WHERE id=1 AND ativo"));
                assertEquals(1,contar(c,"SELECT count(*) FROM usuarios WHERE restaurante_id=1"));
                assertEquals(2,contar(c,"SELECT count(*) FROM produtos"));
            }
        }
    }

    @Test
    void bloqueiaPlataformaVinculadaAoAlvoETabelaDesconhecida() throws Exception {
        try(var pg=EmbeddedPostgres.builder().start()) {
            var url=pg.getJdbcUrl("postgres","postgres");
            Flyway.configure().dataSource(url,"postgres","postgres").locations("classpath:db/migration").load().migrate();
            try(var c=DriverManager.getConnection(url)) {
                preparar(c);
                try(var s=c.createStatement()){s.execute("UPDATE usuarios SET restaurante_id=1 WHERE perfil='PLATAFORMA'");}
                c.setAutoCommit(false);
                assertThrows(SQLException.class,()->{try(var s=c.createStatement()){s.execute(script());}});
                c.rollback();
                assertEquals(2,contar(c,"SELECT count(*) FROM restaurantes"));
                try(var s=c.createStatement()) {
                    s.execute("UPDATE usuarios SET restaurante_id=NULL WHERE perfil='PLATAFORMA'");
                    s.execute("CREATE TABLE novo_modulo(restaurante_id bigint)");
                }
                c.commit();
                assertThrows(SQLException.class,()->{try(var s=c.createStatement()){s.execute(script());}});
                c.rollback();
                assertEquals(1,contar(c,"SELECT count(*) FROM produtos"));
            }
        }
    }
}

package com.estoq;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/** Testa os scripts em PostgreSQL descartável; não conecta ao projeto Supabase. */
class SupabasePreparacaoIntegrationTest {
    private String script(String nome) throws Exception {
        Path raiz = Path.of("").toAbsolutePath();
        if (raiz.getFileName().toString().equals("backend")) raiz = raiz.getParent();
        return Files.readString(raiz.resolve("scripts").resolve(nome));
    }

    @Test
    void preparaSchemaPrivadoPreservaPublicECriaSomentePlataforma() throws Exception {
        try (var pg = EmbeddedPostgres.builder().start()) {
            String url = pg.getJdbcUrl("postgres", "postgres");
            try (var conexao = DriverManager.getConnection(url); var sql = conexao.createStatement()) {
                sql.execute("CREATE ROLE anon; CREATE ROLE authenticated; CREATE ROLE service_role");
                sql.execute("CREATE TABLE public.tabela_do_provedor(id int); INSERT INTO public.tabela_do_provedor VALUES(1)");
                sql.execute(script("provision-supabase.sql"));
                sql.execute(script("provision-supabase.sql"));
                var flyway = Flyway.configure().dataSource(url, "postgres", "postgres")
                        .schemas("estoq_v2").locations("classpath:db/migration").load();
                flyway.migrate();
                assertEquals("10", flyway.info().current().getVersion().toString());
                for (String perfil : new String[]{"anon", "authenticated", "service_role"}) {
                    try (var resultado = sql.executeQuery("SELECT has_schema_privilege('" + perfil + "','estoq_v2','USAGE')")) {
                        resultado.next(); assertFalse(resultado.getBoolean(1));
                    }
                }
                try (var resultado = sql.executeQuery("SELECT id FROM public.tabela_do_provedor")) {
                    resultado.next(); assertEquals(1, resultado.getInt(1));
                }
                String template = script("bootstrap-plataforma-sql-editor.sql");
                assertThrows(SQLException.class, () -> sql.execute(template));
                var encoder = new BCryptPasswordEncoder(12);
                String hash = encoder.encode("SenhaSomenteDoTeste123");
                String bootstrap = template.replace("COLE_O_HASH_BCRYPT_GERADO_LOCALMENTE", hash);
                sql.execute(bootstrap);
                assertThrows(SQLException.class, () -> sql.execute(bootstrap));
                try (var resultado = sql.executeQuery("SELECT email,perfil,restaurante_id,senha FROM estoq_v2.usuarios")) {
                    assertTrue(resultado.next());
                    assertEquals("admin@estoq.com", resultado.getString("email"));
                    assertEquals("PLATAFORMA", resultado.getString("perfil"));
                    assertNull(resultado.getObject("restaurante_id"));
                    assertTrue(encoder.matches("SenhaSomenteDoTeste123", resultado.getString("senha")));
                    assertFalse(resultado.next());
                }
                try (var resultado = sql.executeQuery("SELECT count(*) FROM estoq_v2.restaurantes")) {
                    resultado.next(); assertEquals(0, resultado.getLong(1));
                }
            }
        }
    }
}

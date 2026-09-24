package com.estoq.backup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BackupConfig {

    /** jdbc:postgresql://[usuario[:senha]@]host[:porta]/database[?params] */
    private static final Pattern JDBC = Pattern.compile(
            "^jdbc:postgresql://(?:([^:/@]+):([^@]*)@)?([^:/?#]+)(?::(\\d+))?/([^?#]+)(?:\\?(.*))?");
    private static final Pattern PARAM_SCHEMA = Pattern.compile("(?i)(?:^|&)(?:currentSchema|search_path)=([^&]+)");

    private final String diretorio;
    private final String host;
    private final int porta;
    private final String database;
    private final String schema;
    private final String usuario;
    private final String senha;
    private final int manter;

    public BackupConfig(
            @Value("${estoq.backup.diretorio:../dados/backups}") String diretorio,
            @Value("${estoq.backup.host:}") String host,
            @Value("${estoq.backup.porta:0}") int porta,
            @Value("${estoq.backup.database:}") String database,
            @Value("${estoq.backup.schema:}") String schema,
            @Value("${estoq.backup.usuario:}") String usuario,
            @Value("${estoq.backup.senha:}") String senha,
            @Value("${estoq.backup.manter:7}") int manter,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String datasourceUser,
            @Value("${spring.datasource.password:}") String datasourcePassword) {
        var jdbc = parse(datasourceUrl);
        this.diretorio = diretorio;
        this.host = primeiro(host, jdbc != null ? jdbc.host : null, "localhost");
        this.porta = porta > 0 ? porta : (jdbc != null && jdbc.porta > 0 ? jdbc.porta : 5432);
        this.database = primeiro(database, jdbc != null ? jdbc.database : null, "estoq_startup");
        this.schema = primeiro(schema, jdbc != null ? jdbc.schema : null);
        this.usuario = primeiro(usuario, jdbc != null ? jdbc.usuario : null, datasourceUser, "estoq");
        this.senha = primeiro(senha, jdbc != null ? jdbc.senha : null, datasourcePassword, "estoq");
        this.manter = Math.max(1, manter);
    }

    public Path diretorio() {
        return Path.of(diretorio);
    }

    public String host() {
        return host;
    }

    public int porta() {
        return porta;
    }

    public String database() {
        return database;
    }

    public String schema() {
        return schema;
    }

    public String usuario() {
        return usuario;
    }

    public String senha() {
        return senha;
    }

    public int manter() {
        return manter;
    }

    private static Jdbc parse(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher m = JDBC.matcher(url.trim());
        if (!m.matches()) {
            return null;
        }
        String host = m.group(3);
        int porta = m.group(4) != null && !m.group(4).isBlank() ? Integer.parseInt(m.group(4)) : 0;
        String schema = null;
        if (m.group(6) != null) {
            Matcher sc = PARAM_SCHEMA.matcher(m.group(6));
            if (sc.find()) {
                schema = sc.group(1).replace("%20", " ").replace("+", " ");
            }
        }
        return new Jdbc(
                vazio(m.group(1)),
                vazio(m.group(2)),
                host,
                porta,
                m.group(5),
                schema);
    }

    private static record Jdbc(String usuario, String senha, String host, int porta, String database, String schema) {
    }

    private static String vazio(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    private static String primeiro(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
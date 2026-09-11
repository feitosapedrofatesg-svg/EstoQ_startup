package com.estoq.backup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class BackupConfig {

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
            @Value("${estoq.backup.host:localhost}") String host,
            @Value("${estoq.backup.porta:5434}") int porta,
            @Value("${estoq.backup.database:estoq_startup}") String database,
            @Value("${estoq.backup.schema:estoq_v2}") String schema,
            @Value("${estoq.backup.usuario:estoq}") String usuario,
            @Value("${estoq.backup.senha:estoq}") String senha,
            @Value("${estoq.backup.manter:7}") int manter) {
        this.diretorio = diretorio;
        this.host = host;
        this.porta = porta;
        this.database = database;
        this.schema = schema;
        this.usuario = usuario;
        this.senha = senha;
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
}
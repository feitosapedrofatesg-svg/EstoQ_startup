package com.estoq.backup;

import com.estoq.core.exceptions.BusinessException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class BackupService {

    private static final DateTimeFormatter NOME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern ARQUIVO = Pattern.compile("^estoq-\\d{8}-\\d{6}\\.dump$");

    private final BackupConfig config;

    public BackupService(BackupConfig config) {
        this.config = config;
    }

    public BackupDTO gerar() {
        var arquivo = config.diretorio().resolve("estoq-" + LocalDateTime.now().format(NOME) + ".dump");
        try {
            Files.createDirectories(config.diretorio());
        } catch (IOException e) {
            throw new BusinessException("Não foi possível criar o diretório de backups.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        var comando = List.of("pg_dump",
                "--host", config.host(),
                "--port", Integer.toString(config.porta()),
                "--username", config.usuario(),
                "--schema", config.schema(),
                "--format", "custom",
                "--file", arquivo.toString(),
                config.database());
        var processo = new ProcessBuilder(comando);
        processo.environment().put("PGPASSWORD", config.senha());
        try {
            var execucao = processo.start();
            var saida = new String(execucao.getInputStream().readAllBytes());
            var erro = new String(execucao.getErrorStream().readAllBytes());
            int codigo = execucao.waitFor();
            if (codigo != 0) {
                throw new BusinessException("Falha ao gerar backup (pg_dump). " + erro.strip(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
            if (saida != null && !saida.isBlank()) {
                log.debug("pg_dump: {}", saida.strip());
            }
        } catch (IOException e) {
            throw new BusinessException("pg_dump não encontrado ou sem permissão. Verifique a instalação do PostgreSQL.", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Backup interrompido.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        limparAntigos();
        return BackupDTO.of(arquivo);
    }

    public List<BackupDTO> listar() {
        if (Files.notExists(config.diretorio())) {
            return List.of();
        }
        try (var arquivos = Files.list(config.diretorio())) {
            return arquivos.filter(Files::isRegularFile)
                    .filter(a -> ARQUIVO.matcher(a.getFileName().toString()).matches())
                    .map(BackupDTO::of)
                    .sorted(Comparator.comparing(BackupDTO::nome).reversed())
                    .toList();
        } catch (IOException e) {
            throw new BusinessException("Não foi possível listar os backups.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Path baixar(String nome) {
        if (nome == null || !ARQUIVO.matcher(nome).matches()) {
            throw new BusinessException("Backup não encontrado.", HttpStatus.NOT_FOUND);
        }
        var arquivo = config.diretorio().resolve(nome).normalize();
        if (Files.notExists(arquivo)) {
            throw new BusinessException("Backup não encontrado.", HttpStatus.NOT_FOUND);
        }
        return arquivo;
    }

    /** Lê os bytes de um backup recém-gerado para download imediato na resposta do POST. */
    public byte[] ler(String nome) {
        var arquivo = baixar(nome);
        try {
            return Files.readAllBytes(arquivo);
        } catch (IOException e) {
            throw new BusinessException("Não foi possível ler o backup gerado.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void limparAntigos() {
        listar().stream()
                .skip(config.manter())
                .map(b -> config.diretorio().resolve(b.nome()))
                .forEach(a -> {
                    try {
                        Files.deleteIfExists(a);
                    } catch (IOException e) {
                        log.warn("Não foi possível remover backup antigo {}", a.getFileName());
                    }
                });
    }
}
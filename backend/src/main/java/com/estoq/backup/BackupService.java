package com.estoq.backup;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

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
    private static final Pattern ARQUIVO = Pattern.compile("^estoq-\\d{8}-\\d{6}(?:-[a-f0-9-]{36})?\\.dump$");

    private final BackupConfig config;
    private final ReentrantLock geracao = new ReentrantLock();

    public BackupService(BackupConfig config) {
        this.config = config;
    }

    public BackupDTO gerar() {
        if (!geracao.tryLock()) {
            throw new BusinessException("Já existe um backup em geração. Aguarde a conclusão.", HttpStatus.CONFLICT);
        }
        Path parcial = null;
        Process execucao = null;
        try {
            Files.createDirectories(config.diretorio());
            var nome = "estoq-" + LocalDateTime.now().format(NOME) + "-" + UUID.randomUUID() + ".dump";
            var arquivo = config.diretorio().resolve(nome);
            parcial = config.diretorio().resolve(nome + ".partial");
            var comando = new java.util.ArrayList<String>(List.of("pg_dump",
                    "--host", config.host(), "--port", Integer.toString(config.porta()),
                    "--username", config.usuario(), "--format", "custom", "--file", parcial.toString()));
            if (!config.schema().isBlank()) {
                comando.add("--schema");
                comando.add(config.schema());
            }
            comando.add(config.database());
            var processo = new ProcessBuilder(comando);
            processo.environment().put("PGPASSWORD", config.senha());
            // Descartar as saídas evita bloquear o processo ou expor credenciais em erros.
            processo.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            processo.redirectError(ProcessBuilder.Redirect.DISCARD);
            execucao = iniciar(processo);
            if (!aguardar(execucao)) {
                throw new BusinessException("O backup excedeu o tempo permitido. Tente novamente.", HttpStatus.GATEWAY_TIMEOUT);
            }
            if (execucao.exitValue() != 0 || !Files.isRegularFile(parcial) || Files.size(parcial) == 0) {
                throw new BusinessException("Não foi possível gerar o backup. Verifique a configuração do banco.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            try {
                Files.move(parcial, arquivo, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(parcial, arquivo);
            }
            limparAntigos();
            return BackupDTO.of(arquivo);
        } catch (IOException e) {
            throw new BusinessException("Não foi possível gerar o backup. Verifique o diretório e a instalação do PostgreSQL.", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Backup interrompido.", HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (execucao != null && execucao.isAlive()) {
                execucao.destroyForcibly();
                try { execucao.waitFor(10, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if (parcial != null) {
                try { Files.deleteIfExists(parcial); }
                catch (IOException e) { log.warn("Não foi possível remover arquivo parcial de backup."); }
            }
            geracao.unlock();
        }
    }

    protected Process iniciar(ProcessBuilder processo) throws IOException {
        return processo.start();
    }

    protected boolean aguardar(Process processo) throws InterruptedException {
        return processo.waitFor(5, TimeUnit.MINUTES);
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
        if (!Files.isRegularFile(arquivo)) {
            throw new BusinessException("Backup não encontrado.", HttpStatus.NOT_FOUND);
        }
        return arquivo;
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

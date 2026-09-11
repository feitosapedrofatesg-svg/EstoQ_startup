package com.estoq.backup;

import java.nio.file.Path;
import java.time.Instant;

public record BackupDTO(String nome, long tamanhoBytes, Instant criadoEm) {

    public static BackupDTO of(Path arquivo) {
        try {
            return new BackupDTO(arquivo.getFileName().toString(), java.nio.file.Files.size(arquivo),
                    java.nio.file.Files.getLastModifiedTime(arquivo).toInstant());
        } catch (java.io.IOException e) {
            return new BackupDTO(arquivo.getFileName().toString(), 0, Instant.EPOCH);
        }
    }
}
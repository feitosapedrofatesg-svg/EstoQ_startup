package com.estoq.backup;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backups;

    @GetMapping
    public List<BackupDTO> listar() {
        return backups.listar();
    }

    @PostMapping
    public BackupDTO gerar() {
        return backups.gerar();
    }

    @GetMapping("/{nome}")
    public ResponseEntity<FileSystemResource> baixar(@PathVariable String nome) {
        var arquivo = backups.baixar(nome);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(arquivo));
    }
}
package com.estoq.business.notasFiscais;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notas-fiscais")
public class NotaFiscalController {
    private final NotaFiscalService service;
    @PostMapping(value="/analisar", consumes="multipart/form-data")
    public NotaFiscalDTO analisar(@RequestParam("arquivos") List<MultipartFile> arquivos) {
        return service.analisar(arquivos);
    }
    @GetMapping("/{id}")
    public NotaFiscalDTO buscar(@PathVariable Long id) { return service.buscar(id); }
    @PostMapping("/{id}/confirmar")
    public NotaFiscalDTO confirmar(@PathVariable Long id, @Valid @RequestBody ConfirmarNotaDTO dto) {
        return service.confirmar(id, dto);
    }
}

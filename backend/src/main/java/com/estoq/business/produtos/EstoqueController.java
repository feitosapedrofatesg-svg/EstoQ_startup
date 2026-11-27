package com.estoq.business.produtos;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/estoque")
@RequiredArgsConstructor
public class EstoqueController {

    private final EstoqueService service;
    private final com.estoq.business.relatorios.PlanilhaEstoqueService planilha;

    @GetMapping("/pagina")
    public EstoqueService.Pagina pagina(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="false") boolean somenteAbertos, @RequestParam(defaultValue="false") boolean somenteBaixo,
            @RequestParam(defaultValue="TODOS") String situacao, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="25") int size) {
        return service.pagina(q,somenteAbertos,somenteBaixo,situacao,page,size);
    }

    @GetMapping("/planilha")
    public org.springframework.http.ResponseEntity<byte[]> planilha(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="false") boolean somenteAbertos, @RequestParam(defaultValue="false") boolean somenteBaixo,
            @RequestParam(defaultValue="TODOS") String situacao, @RequestParam(defaultValue="pt-BR") String idioma) {
        return org.springframework.http.ResponseEntity.ok()
            .header("Content-Disposition","attachment; filename=estoQ-estoque.xlsx")
            .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(planilha.gerar(service.filtrar(q,somenteAbertos,somenteBaixo,situacao),q,somenteAbertos,somenteBaixo,situacao,idioma));
    }

    @GetMapping
    public List<EstoqueDTO> listar(@RequestParam(defaultValue = "false") boolean somenteAbertos,
            @RequestParam(defaultValue = "false") boolean somenteBaixo) {
        return service.listar(somenteAbertos, somenteBaixo);
    }
}

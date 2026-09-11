package com.estoq.business.movimentacoesEstoque;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/movimentacoes")
@RequiredArgsConstructor
public class MovimentacaoController {

    private final MovimentacaoConsultaService consulta;

    @GetMapping
    public List<MovimentacaoDTO> listar(@RequestParam(required = false) Long produtoId,
            @RequestParam(required = false) Long loteId, @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) TipoMovimentacao tipo, @RequestParam(required = false) LocalDateTime inicio,
            @RequestParam(required = false) LocalDateTime fim) {
        return consulta.listar(produtoId, loteId, usuarioId, tipo, inicio, fim);
    }
}
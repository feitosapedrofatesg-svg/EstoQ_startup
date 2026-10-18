package com.estoq.business.movimentacoesEstoque;

import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.helpers.HorarioOperacao;

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
    private final HorarioOperacao horario;

    @GetMapping
    public List<MovimentacaoDTO> listar(@RequestParam(required = false) Long produtoId,
            @RequestParam(required = false) Long loteId, @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) TipoMovimentacao tipo, @RequestParam(required = false) LocalDateTime inicio,
            @RequestParam(required = false) LocalDateTime fim,
            @RequestParam(required = false) java.time.LocalDate dataInicio,
            @RequestParam(required = false) java.time.LocalDate dataFim) {
        if ((dataInicio != null || dataFim != null) && (inicio != null || fim != null))
            throw new FieldValidationException("periodo", "Use datas ou horários UTC, sem misturar os formatos.");
        if (dataInicio != null) inicio = horario.inicioUtc(dataInicio);
        if (dataFim != null) fim = horario.inicioUtc(dataFim.plusDays(1));
        return consulta.listar(produtoId, loteId, usuarioId, tipo, inicio, fim);
    }
}

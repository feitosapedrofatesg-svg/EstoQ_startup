package com.estoq.business.movimentacoesEstoque;

import com.estoq.core.helpers.HorarioOperacao;

import com.estoq.core.exceptions.FieldValidationException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MovimentacaoConsultaService {

    private final MovimentacaoRegistroService registros;
    private final HorarioOperacao horario;

    public List<MovimentacaoDTO> listar(Long produtoId, Long loteId, Long usuarioId, TipoMovimentacao tipo,
            LocalDateTime inicio, LocalDateTime fim) {
        var ini = inicio == null ? horario.inicioUtc(horario.hoje().withDayOfMonth(1)) : inicio;
        var ate = fim == null ? LocalDateTime.now(ZoneOffset.UTC) : fim;
        if (ini.isAfter(ate)) {
            throw new FieldValidationException("inicio", "Início deve ser anterior ao fim.");
        }
        return registros.consultar(produtoId, loteId, usuarioId, tipo, ini, ate);
    }
}

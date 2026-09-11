package com.estoq.business.alertas;

import java.time.LocalDateTime;

public record AlertaDTO(Long id, TipoAlerta tipo, String mensagem, String perfilDestino, LocalDateTime dataGeracao,
        boolean visualizado, Long produtoId, String produtoNome, Long loteId, String loteCodigo) {
}
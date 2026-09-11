package com.estoq.business.balancos;

import java.time.LocalDateTime;
import java.util.List;

public record BalancoDTO(Long id, Long version, LocalDateTime dataHora, TipoBalanco tipo, StatusBalanco status,
        Long usuarioId, String usuarioNome, List<ItemBalancoDTO> itens) {
}
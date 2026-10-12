package com.estoq.business.sincronizacao;

import java.util.Set;

/** O conjunto é acumulado durante a transação e copiado somente após o commit. */
public record DadosAlteradosEvent(Long restauranteId, Set<GrupoDados> grupos) {}

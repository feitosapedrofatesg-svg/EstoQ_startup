package com.estoq.business.movimentacoesEstoque;

import java.util.List;

public record EstoqueVariosAlteradosEvent(List<Long> produtoIds) {
}

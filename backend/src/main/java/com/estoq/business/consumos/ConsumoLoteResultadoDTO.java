package com.estoq.business.consumos;

import com.estoq.business.produtos.EstoqueDTO;
import com.estoq.business.movimentacoesEstoque.MovimentacaoDTO;
import java.util.List;

public record ConsumoLoteResultadoDTO(List<EstoqueDTO> estoque, List<MovimentacaoDTO> movimentacoes) {
}

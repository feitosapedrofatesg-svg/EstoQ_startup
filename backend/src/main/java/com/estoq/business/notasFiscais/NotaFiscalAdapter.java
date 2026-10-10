package com.estoq.business.notasFiscais;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class NotaFiscalAdapter {
    public NotaFiscalDTO toDto(NotaFiscalModel nota) {
        var itens = nota.getItens().stream().map(i -> new NotaFiscalDTO.Item(i.getId(),i.getNumero(),i.getCodigo(),
                i.getDescricao(),i.getUnidadeNota(),i.getQuantidadeNota(),i.getValorBruto(),i.getDesconto(),
                i.getValorRateado(),i.getValorEntrada(),i.getProdutoId(),i.getCategoriaId(),i.getNomeProduto(),
                i.getUnidadeEstoque(),i.getFator(),i.getQuantidadeEntrada(),i.getValidade(),i.isSelecionado(),
                i.isEmbalagemConfirmada(),i.isIncerto(),i.getMovimentacaoId())).toList();
        return new NotaFiscalDTO(nota.getId(),nota.getVersion(),nota.getChave(),nota.getFornecedor(),
                nota.getDocumentoFornecedor(),nota.getNumero(),nota.getSerie(),nota.getEmissao(),nota.getValorTotal(),
                nota.isConfirmada(),nota.getAvisos()==null || nota.getAvisos().isBlank() ? List.of() : List.of(nota.getAvisos().split("\n")),itens);
    }
}

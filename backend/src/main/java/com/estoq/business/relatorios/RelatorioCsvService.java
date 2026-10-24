package com.estoq.business.relatorios;

import com.estoq.core.helpers.CsvUtil;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
class RelatorioCsvService {
    String gerar(DocumentoRelatorioPdf dados) {
        var texto = new StringBuilder("\ufeff");
        texto.append(CsvUtil.linha(List.of("EstoQ", dados.titulo())));
        texto.append(CsvUtil.linha(List.of(dados.periodo())));
        for (var destaque : dados.resumo()) texto.append(CsvUtil.linha(List.of(destaque.rotulo(), destaque.valor())));
        texto.append(CsvUtil.linha(dados.colunas().stream().map(DocumentoRelatorioPdf.Coluna::titulo).toList()));
        for (var linha : dados.linhas()) texto.append(CsvUtil.linha(linha));
        for (var total : dados.totais()) texto.append(CsvUtil.linha(List.of(total.rotulo(), total.valor())));
        if (dados.observacao() != null) texto.append(CsvUtil.linha(List.of(dados.observacao())));
        return texto.toString();
    }
}

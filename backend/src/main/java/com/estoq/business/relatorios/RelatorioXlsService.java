package com.estoq.business.relatorios;

import org.springframework.stereotype.Service;

/** Mantém o formato HTML compatível com Excel já usado pelo projeto. */
@Service
class RelatorioXlsService {
    String gerar(DocumentoRelatorioPdf dados) {
        var html = new StringBuilder("\ufeff<html xmlns:x=\"urn:schemas-microsoft-com:office:excel\"><head><meta charset=\"UTF-8\"><style>");
        html.append("body{font-family:Arial,sans-serif;font-size:11pt;color:#272932}table{border-collapse:collapse}th{background:#272932;color:white}td,th{padding:8px;border:1px solid #d6d9dc}td{mso-number-format:'\\@'}tr:nth-child(even){background:#fafafa}.total{font-weight:bold;background:#f6f6f7}</style></head><body>");
        html.append("<h1>EstoQ · ").append(escapar(dados.titulo())).append("</h1><p>").append(escapar(dados.periodo())).append("</p>");
        for (var destaque : dados.resumo()) html.append("<p><strong>").append(escapar(destaque.rotulo())).append(": </strong>").append(escapar(destaque.valor())).append("</p>");
        html.append("<table><thead><tr>");
        for (var coluna : dados.colunas()) html.append("<th>").append(escapar(coluna.titulo())).append("</th>");
        html.append("</tr></thead><tbody>");
        for (var linha : dados.linhas()) {
            html.append("<tr>");
            for (var valor : linha) html.append("<td>").append(escapar(valor)).append("</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        for (var total : dados.totais()) html.append("<p class=\"total\">").append(escapar(total.rotulo())).append(": ").append(escapar(total.valor())).append("</p>");
        if (dados.observacao() != null) html.append("<p>").append(escapar(dados.observacao())).append("</p>");
        return html.append("</body></html>").toString();
    }

    private String escapar(String valor) {
        return valor == null ? "" : valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

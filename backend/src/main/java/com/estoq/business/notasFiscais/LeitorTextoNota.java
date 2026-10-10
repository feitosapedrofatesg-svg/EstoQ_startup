package com.estoq.business.notasFiscais;

import com.estoq.core.exceptions.FieldValidationException;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Interpreta o texto do DANFE; cabeçalhos e rodapés não viram produtos. */
@Component
public class LeitorTextoNota {
    private static final String DINHEIRO = "[\\d.]+,\\d{2,6}";
    private static final Pattern ITEM = Pattern.compile("^\\s*(\\S+)\\s+(.+?)\\s+\\d{8}\\s+\\d{3}\\s+\\d{4}\\s+(\\S+)\\s+(" + DINHEIRO + ")\\s+(" + DINHEIRO + ")\\s+(" + DINHEIRO + ")\\s+(" + DINHEIRO + ")(?:\\s+.*)?$");
    private static final Pattern VALOR = Pattern.compile(DINHEIRO);

    public DadosNota ler(String texto, boolean imagem) {
        var chaveEncontrada = Pattern.compile("(?:\\d{4}[ \t]+){10}\\d{4}|\\b\\d{44}\\b").matcher(texto);
        if (!chaveEncontrada.find()) throw erro("Não foi possível ler a chave de acesso. Use o XML ou uma imagem mais nítida.");
        var chave = chaveEncontrada.group().replaceAll("\\s", "");
        while (chaveEncontrada.find()) {
            if (!chave.equals(chaveEncontrada.group().replaceAll("\\s", "")))
                throw erro("Todos os documentos enviados devem pertencer à mesma nota fiscal.");
        }
        if (!chave.substring(20,22).equals("55")) throw erro("Envie uma NF-e de mercadorias (modelo 55).");
        var fornecedorEncontrado = Pattern.compile("Recebemos de (.+?) os produtos", Pattern.CASE_INSENSITIVE).matcher(texto);
        if (!fornecedorEncontrado.find()) throw erro("Não foi possível identificar o fornecedor. Envie o XML da nota.");
        String fornecedor = fornecedorEncontrado.group(1).strip();
        var emissaoEncontrada = Pattern.compile("Emiss[aã]o:\\s*(\\d{2}/\\d{2}/\\d{4})", Pattern.CASE_INSENSITIVE).matcher(texto);
        LocalDate emissao = emissaoEncontrada.find() ? LocalDate.parse(emissaoEncontrada.group(1), DateTimeFormatter.ofPattern("dd/MM/yyyy")) : null;
        var itens = new ArrayList<DadosNota.ItemLido>();
        var linhas = texto.split("\\R");
        BigDecimal total = null;
        BigDecimal totalProdutos = null;
        boolean tabela = false;
        for (int i=0; i<linhas.length; i++) {
            String linha = linhas[i];
            if (linha.contains("VALOR TOTAL DA NOTA")) total = valorAbaixo(linhas,i);
            if (linha.contains("VALOR TOTAL DOS PRODUTOS")) totalProdutos = valorAbaixo(linhas,i);
            if (linha.contains("DADOS DOS PRODUTOS")) tabela = true;
            if (linha.contains("DADOS ADICIONAIS")) tabela = false;
            if (!tabela) continue;
            var item = ITEM.matcher(linha);
            if (item.matches()) {
                itens.add(new DadosNota.ItemLido(itens.size()+1, item.group(1), item.group(2).strip(), null,
                        item.group(3), decimal(item.group(4)), decimal(item.group(7)), decimal(item.group(6)), null, imagem));
            } else if (Pattern.compile("^\\s*\\d+\\s+.+\\d{8}\\s+\\d{3}\\s+\\d{4}").matcher(linha).find()) {
                throw erro("Uma linha de produto ficou incompleta. Envie o XML ou uma imagem mais nítida.");
            }
        }
        if (itens.isEmpty() || total == null) throw erro("Não foi possível ler todos os produtos e o total. Use o XML ou confira a qualidade do documento.");
        var somaProdutos = itens.stream().map(DadosNota.ItemLido::valorBruto).reduce(BigDecimal.ZERO,BigDecimal::add);
        if (totalProdutos==null || somaProdutos.compareTo(totalProdutos)!=0)
            throw erro("Os itens lidos não fecham o total dos produtos. Confira se enviou todas as páginas ou use o XML.");
        var avisos = new ArrayList<String>();
        avisos.add("Confira quantidades e custos: o DANFE pode apresentar valores arredondados.");
        if (imagem) avisos.add("Leitura por imagem: confira cada item com a fotografia antes de confirmar.");
        return new DadosNota(chave, fornecedor, chave.substring(6,20), chave.substring(25,34),
                chave.substring(22,25), emissao, total, itens, avisos);
    }

    private BigDecimal valorAbaixo(String[] linhas, int cabecalho) {
        for (int i=cabecalho+1; i<Math.min(cabecalho+5,linhas.length); i++) {
            var valores=VALOR.matcher(linhas[i]);
            BigDecimal ultimo=null;
            while(valores.find()) ultimo=decimal(valores.group());
            if(ultimo!=null) return ultimo;
        }
        return null;
    }
    private BigDecimal decimal(String valor) { return new BigDecimal(valor.replace(".", "").replace(',', '.')); }
    private FieldValidationException erro(String mensagem) { return new FieldValidationException("arquivos", mensagem); }
}

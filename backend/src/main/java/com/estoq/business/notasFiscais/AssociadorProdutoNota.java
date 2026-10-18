package com.estoq.business.notasFiscais;

import com.estoq.core.exceptions.FieldValidationException;

import com.estoq.business.produtos.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class AssociadorProdutoNota {

    public void sugerir(ItemNotaFiscalModel item, List<ProdutoModel> catalogo, VinculoFornecedorModel vinculo) {
        ProdutoModel produto = null;
        if (vinculo!=null) produto = catalogo.stream().filter(p -> p.getId().equals(vinculo.getProdutoId())).findFirst().orElse(null);
        if (produto==null) {
            var barras = codigoBarrasValido(item.getCodigoBarras());
            var encontrados = catalogo.stream().filter(p -> barras!=null && barras.equals(p.getCodigoBarras())).toList();
            if (encontrados.size()==1) produto = encontrados.getFirst();
            else if (encontrados.isEmpty()) {
                encontrados = catalogo.stream().filter(p -> normalizar(p.getNome()).equals(normalizar(item.getDescricao()))).toList();
                if (encontrados.size()==1) produto = encontrados.getFirst();
            }
        }
        if (produto!=null) {
            item.setProdutoId(produto.getId()); item.setCategoriaId(produto.getCategoria().getId());
            item.setNomeProduto(produto.getNome()); item.setUnidadeEstoque(produto.getUnidadeMedida());
        } else {
            item.setNomeProduto(item.getDescricao().substring(0,Math.min(120,item.getDescricao().length())));
        }
        if (produto!=null && vinculo!=null && produto.getId().equals(vinculo.getProdutoId())
                && normalizar(vinculo.getDescricao()).equals(normalizar(item.getDescricao()))
                && vinculo.getUnidadeNota().equalsIgnoreCase(item.getUnidadeNota())
                && vinculo.getUnidadeEstoque()==produto.getUnidadeMedida()) {
            item.setFator(vinculo.getFator()); item.setEmbalagemConfirmada(true);
            return;
        }
        var unidadeDireta = unidade(item.getUnidadeNota());
        var embalagem = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(KG|ML|G|L)\\b", Pattern.CASE_INSENSITIVE).matcher(item.getDescricao());
        BigDecimal conteudo = BigDecimal.ONE;
        UnidadeMedida origem = unidadeDireta;
        if (origem==null && embalagem.find()) {
            origem = UnidadeMedida.valueOf(embalagem.group(2).toUpperCase());
            conteudo = new BigDecimal(embalagem.group(1).replace(',','.'));
        }
        if (item.getUnidadeEstoque()==null) item.setUnidadeEstoque(origem==null ? UnidadeMedida.UN : origem);
        try {
            item.setFator(origem==null ? BigDecimal.ONE : ConversorUnidade.converter(conteudo,origem,item.getUnidadeEstoque()));
            item.setEmbalagemConfirmada(unidadeDireta!=null);
        } catch (FieldValidationException e) {
            item.setFator(BigDecimal.ONE); item.setEmbalagemConfirmada(false);
        }
    }

    public static UnidadeMedida unidade(String valor) {
        var nome = valor.toUpperCase().strip();
        // KG1 usado no DANFE da Sendas representa peso; UN1 e PC1 são embalagens.
        if (nome.equals("KG1")) return UnidadeMedida.KG;
        try { return UnidadeMedida.valueOf(nome); } catch (IllegalArgumentException e) { return null; }
    }
    public static String normalizar(String valor) {
        return Normalizer.normalize(valor,Normalizer.Form.NFD).replaceAll("\\p{M}","")
                .toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]"," ").replaceAll("\\s+"," ").strip();
    }
    public static String codigoBarrasValido(String codigo) {
        if (codigo==null || !codigo.matches("(?:\\d{8}|\\d{12}|\\d{13}|\\d{14})")) return null;
        int soma=0, peso=3;
        for(int i=codigo.length()-2;i>=0;i--) { soma+=(codigo.charAt(i)-'0')*peso; peso=peso==3?1:3; }
        return (10-soma%10)%10==codigo.charAt(codigo.length()-1)-'0' ? codigo : null;
    }
}

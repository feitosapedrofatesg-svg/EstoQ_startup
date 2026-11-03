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
        item.setProdutoId(null); item.setCategoriaId(null); item.setUnidadeEstoque(null);
        item.setEmbalagemConfirmada(false);
        boolean vinculoCompativel = vinculo!=null
            && normalizar(vinculo.getDescricao()).equals(normalizar(item.getDescricao()))
            && normalizar(vinculo.getUnidadeNota()).equals(normalizar(item.getUnidadeNota()));
        item.setOrigemCategoria(null); item.setMotivoCategoria(null); item.setRevisarCategoria(false);
        if (vinculo!=null && !vinculoCompativel) {
            item.setRevisarCategoria(true);
            item.setMotivoCategoria("O fornecedor mudou a descrição ou a unidade deste código. Confira produto, categoria e embalagem; o fator anterior não foi reutilizado.");
        }
        String origemCategoria = "FORNECEDOR";
        if (vinculoCompativel) produto = catalogo.stream().filter(p -> p.getId().equals(vinculo.getProdutoId())).findFirst().orElse(null);
        if (produto==null) {
            var barras = codigoBarrasValido(item.getCodigoBarras());
            var encontrados = catalogo.stream().filter(p -> barras!=null && barras.equals(p.getCodigoBarras())).toList();
            if (encontrados.size()==1) { produto = encontrados.getFirst(); origemCategoria = "CODIGO_BARRAS"; }
            else if (encontrados.isEmpty()) {
                encontrados = catalogo.stream().filter(p -> normalizar(p.getNome()).equals(normalizar(item.getDescricao()))).toList();
                if (encontrados.size()==1) { produto = encontrados.getFirst(); origemCategoria = "NOME_EXATO"; }
            }
        }
        if (produto!=null) {
            item.setOrigemCategoria(origemCategoria);
            if (item.getMotivoCategoria()==null)
                item.setMotivoCategoria("Produto identificado por " + switch (origemCategoria) {
                    case "FORNECEDOR" -> "histórico compatível do fornecedor";
                    case "CODIGO_BARRAS" -> "código de barras";
                    default -> "nome exato";
                } + ". A categoria é a do produto no catálogo.");
            item.setProdutoId(produto.getId()); item.setCategoriaId(produto.getCategoria().getId());
            item.setNomeProduto(produto.getNome()); item.setUnidadeEstoque(produto.getUnidadeMedida());
        } else {
            item.setNomeProduto(item.getDescricao().substring(0,Math.min(120,item.getDescricao().length())));
        }
        boolean bebida = produto == null ? EstoqueBebida.porUnidade(item.getDescricao(), null)
            : produto.getUnidadeMedida() == UnidadeMedida.UN && EstoqueBebida.porUnidade(produto.getNome(), produto.getCategoria().getNome());
        var direta = unidade(item.getUnidadeNota());
        var embalagem = Pattern.compile("(?i)(\\d+(?:[.,]\\d+)?)\\s*(KG|ML|G|L)\\b").matcher(item.getDescricao());
        BigDecimal conteudo = null; UnidadeMedida origem = null; int medidas = 0;
        while (embalagem.find()) {
            medidas++; conteudo = new BigDecimal(embalagem.group(1).replace(',', '.'));
            origem = UnidadeMedida.valueOf(embalagem.group(2).toUpperCase(java.util.Locale.ROOT));
        }
        boolean multipla = medidas != 1 || Pattern.compile("(?i)\\d\\s*[xX×]\\s*\\d").matcher(item.getDescricao()).find();
        if (item.getUnidadeEstoque() == null)
            item.setUnidadeEstoque(bebida ? UnidadeMedida.UN
                : direta != null && direta != UnidadeMedida.UN ? direta
                : origem != null ? origem : UnidadeMedida.UN);
        BigDecimal fator = null;
        try {
            if (bebida) {
                if (unidadeAvulsa(item.getUnidadeNota())) fator = BigDecimal.ONE;
            } else if (direta != null && direta != UnidadeMedida.UN) {
                fator = fator(BigDecimal.ONE, direta, item.getUnidadeEstoque());
            } else if (unidadeAvulsa(item.getUnidadeNota())) {
                if (item.getUnidadeEstoque() == UnidadeMedida.UN && direta == UnidadeMedida.UN) fator = BigDecimal.ONE;
                else if (!multipla) fator = fator(conteudo, origem, item.getUnidadeEstoque());
            }
        } catch (FieldValidationException | ArithmeticException e) { fator = null; }
        boolean historico = produto != null && vinculoCompativel && produto.getId().equals(vinculo.getProdutoId())
            && vinculo.getUnidadeEstoque() == produto.getUnidadeMedida();
        if (historico && vinculo.getFator() != null && vinculo.getFator().signum() > 0) {
            if (fator == null || fator.compareTo(vinculo.getFator()) == 0) {
                item.setFator(vinculo.getFator()); item.setEmbalagemConfirmada(true); return;
            }
            // Não reaproveita silenciosamente um fator antigo incompatível com a medida atual.
            fator = null;
        }
        item.setFator(fator);
        item.setEmbalagemConfirmada(fator != null && direta != null);
    }

    private BigDecimal fator(BigDecimal conteudo, UnidadeMedida origem, UnidadeMedida destino) {
        if (origem == destino) return conteudo;
        if ((origem == UnidadeMedida.G && destino == UnidadeMedida.KG) || (origem == UnidadeMedida.ML && destino == UnidadeMedida.L))
            return conteudo.divide(new BigDecimal("1000"));
        if ((origem == UnidadeMedida.KG && destino == UnidadeMedida.G) || (origem == UnidadeMedida.L && destino == UnidadeMedida.ML))
            return conteudo.multiply(new BigDecimal("1000"));
        throw new FieldValidationException("unidadeEstoque", "Unidades incompatíveis. Confira o conteúdo da embalagem.");
    }

    public static boolean unidadeAvulsa(String valor) {
        return normalizar(valor).matches("UN|UN1|UND|UNID|UNIDADE|UNIDADES|PC|PC1|PÇ|PCA");
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

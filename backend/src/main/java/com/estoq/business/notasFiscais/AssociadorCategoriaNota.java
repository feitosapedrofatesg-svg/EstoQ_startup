package com.estoq.business.notasFiscais;

import com.estoq.business.categorias.CategoriaModel;
import com.estoq.business.produtos.ProdutoModel;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Sugere categorias da própria cozinha, sem enviar os dados da nota a serviços externos. */
@Component
public class AssociadorCategoriaNota {
    private record Regra(String produtos, String categorias, String categoriaPadrao) { }
    private static final List<Regra> REGRAS = List.of(
        new Regra("DETERGENTE|DESINFETANTE|AGUA SANITARIA|SABAO|ESPONJA|ALVEJANTE|LIMPADOR", "LIMPEZA|HIGIENE", "Limpeza"),
        new Regra("GUARDANAPO|EMBALAGEM|DESCARTAVEL|DESCARTAVEIS|PAPEL ALUMINIO|FILME PVC", "DESCARTAVE|EMBALAGE", "Descartáveis e embalagens"),
        new Regra("BEBIDA|GASEIFICADA|REFRIGERANTE|SUCO|CERVEJA|VINHO|AGUA MINERAL|ENERGETICO", "BEBIDA", "Bebidas"),
        new Regra("BOVINO|BOVINA|SUINO|SUINA|FRANGO|PICANHA|ALCATRA|MAMINHA|CONTRA FILE|COSTELA|CARNE|LINGUICA|BACON", "CARNE|PROTEINA|ACOUGUE", "Carnes"),
        new Regra("PEIXE|TILAPIA|SALMAO|CAMARAO|SARDINHA|ATUM", "PEIXE|PESCADO|FRUTOS DO MAR|PROTEINA", "Peixes e frutos do mar"),
        new Regra("LEITE|QUEIJO|MANTEIGA|IOGURTE|REQUEIJAO|CREME DE LEITE", "LATICINIO|LACTEO", "Laticínios"),
        new Regra("ARROZ|FEIJAO|LENTILHA|GRAO DE BICO|ERVILHA", "GRAO|CEREA|LEGUMINOSA", "Grãos"),
        new Regra("TOMATE|CEBOLA|BATATA|ALFACE|CENOURA|ABOBORA|ABOBRINHA|BROCOLIS|COUVE|PEPINO|BANANA|MACA|LARANJA|LIMAO", "HORTIFRUTI|HORTALICA|VERDURA|LEGUME|FRUTA", "Hortifruti"),
        new Regra("MACARRAO|ESPAGUETE|LASANHA|PENNE|FUSILLI", "MASSA", "Massas"),
        new Regra("SAL|PIMENTA|OREGANO|COMINHO|COLORAU|TEMPERO|CANELA", "TEMPERO|CONDIMENTO|ESPECIARIA", "Temperos e condimentos"),
        new Regra("OLEO|AZEITE|VINAGRE|MOLHO", "OLEO|AZEITE|MOLHO|CONDIMENTO", "Óleos e molhos"),
        new Regra("ACUCAR|FARINHA|CAFE|FUBA|AMIDO", "MERCEARIA|SECO|DESPENSA|CEREA", "Mercearia")
    );
    private static final Set<String> GENERICOS = Set.of("INTEGRAL", "NATURAL", "TRADICIONAL", "REFINADO", "CONGELADO", "RESFRIADO", "PACOTE", "CAIXA", "EXTRA", "TIPO", "BEBIDA", "PRODUTO", "ARTIGO");

    public record Sugestao(Long categoriaId, String origem, String motivo, boolean revisar) { }

    public Long sugerir(String descricao, List<ProdutoModel> produtos, List<CategoriaModel> categorias) {
        return analisar(descricao, produtos, categorias).categoriaId();
    }

    public Sugestao analisar(String descricao, List<ProdutoModel> produtos, List<CategoriaModel> categorias) {
        String nome = normalizarDescricao(descricao);
        // Nestes compostos o ingrediente não identifica o tipo do produto.
        if (nome.matches(".*\\b(?:LEITE DE COCO|CALDO DE CARNE)\\b.*"))
            return revisar("O nome tem mais de um sentido. Escolha a categoria após conferir o produto.");
        String primeiro = nome.split(" ")[0];
        var conhecidas = produtos.stream().filter(p -> primeiro.length() >= 4 && !GENERICOS.contains(primeiro)
                && normalizarDescricao(p.getNome()).split(" ")[0].equals(primeiro))
            .map(p -> p.getCategoria().getId()).distinct().toList();
        if (conhecidas.size() == 1)
            return new Sugestao(conhecidas.getFirst(), "CATALOGO", "Produtos com o mesmo nome principal no catálogo usam esta categoria.", false);
        if (conhecidas.size() > 1) return revisar("O catálogo usa categorias diferentes para este nome. Confira a categoria.");
        var principais = REGRAS.stream().filter(r -> corresponde(nome, r.produtos(), true)).toList();
        // O tipo no começo prevalece sobre o sabor: suco de limão é uma bebida.
        var encontradas = principais.isEmpty()
            ? REGRAS.stream().filter(r -> corresponde(nome, r.produtos(), false)).toList() : principais;
        if (encontradas.size() > 1) return revisar("A descrição combina tipos de produto diferentes. Confira a categoria.");
        if (encontradas.isEmpty()) return revisar("Não foi possível reconhecer o tipo do produto. Sem escolha, entrará em Sem classificação.");
        var regra = encontradas.getFirst();
        var padrao = categorias.stream().filter(c -> AssociadorProdutoNota.normalizar(c.getNome())
            .equals(AssociadorProdutoNota.normalizar(regra.categoriaPadrao()))).toList();
        var compativeis = padrao.isEmpty() ? categorias.stream().filter(c -> Pattern.compile(regra.categorias())
            .matcher(AssociadorProdutoNota.normalizar(c.getNome())).find()).toList() : padrao;
        if (compativeis.size() != 1) return revisar("O tipo foi reconhecido, mas não há uma única categoria compatível neste restaurante.");
        return new Sugestao(compativeis.getFirst().getId(), "DESCRICAO",
            "Tipo reconhecido na descrição: " + regra.categoriaPadrao() + ". Confira a sugestão antes de confirmar.", false);
    }

    private Sugestao revisar(String motivo) {
        return new Sugestao(null, "NAO_IDENTIFICADA", motivo, true);
    }

    private boolean corresponde(String nome, String termos, boolean inicio) {
        return Pattern.compile((inicio ? "^" : "\\b") + "(?:" + termos + ")\\b").matcher(nome).find();
    }

    private String normalizarDescricao(String descricao) {
        // Apenas a busca usa as abreviações expandidas; a descrição fiscal é preservada.
        return AssociadorProdutoNota.normalizar(descricao)
            .replaceAll("\\bBEB\\b", "BEBIDA")
            .replaceAll("\\b(?:GAISE|GASEIF)\\b", "GASEIFICADA")
            .replaceAll("\\b(?:REFRI|REFRIG)\\b", "REFRIGERANTE");
    }
}

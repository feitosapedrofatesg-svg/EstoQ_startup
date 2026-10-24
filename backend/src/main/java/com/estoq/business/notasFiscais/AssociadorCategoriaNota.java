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
    private record Regra(Pattern produtos, Pattern inicio, Pattern categorias, String categoriaPadrao) {
        Regra(String produtos, String categorias, String categoriaPadrao) {
            this(Pattern.compile("\\b(?:" + produtos + ")\\b"),
                Pattern.compile("^(?:" + produtos + ")\\b"), Pattern.compile(categorias), categoriaPadrao);
        }

        int comprimentoInicial(String nome) {
            var matcher = inicio.matcher(nome);
            return matcher.find() ? matcher.end() : 0;
        }
    }
    private static final List<Regra> REGRAS = List.of(
        new Regra("CREME DENTAL|DETERGENTE|DESINFETANTE|AGUA SANITARIA|SABAO|ESPONJA|ALVEJANTE|LIMPADOR", "LIMPEZA|HIGIENE", "Limpeza"),
        new Regra("GUARDANAPO|EMBALAGEM|DESCARTAVEL|DESCARTAVEIS|PAPEL ALUMINIO|FILME PVC", "DESCARTAVE|EMBALAGE", "Descartáveis e embalagens"),
        new Regra("BEBIDA|GASEIFICADA|REFRIGERANTE|SUCO|CERVEJA|VINHO|AGUA MINERAL|ENERGETICO", "BEBIDA", "Bebidas"),
        new Regra("BOVINO|BOVINA|SUINO|SUINA|FRANGO|PICANHA|ALCATRA|MAMINHA|CONTRA FILE|COSTELA|CARNE|LINGUICA|BACON|PATINHO|ACEM|CUPIM", "CARNE|PROTEINA|ACOUGUE", "Carnes"),
        new Regra("PEIXE|TILAPIA|SALMAO|CAMARAO|SARDINHA|ATUM", "PEIXE|PESCADO|FRUTOS DO MAR|PROTEINA", "Peixes e frutos do mar"),
        new Regra("BEBIDA LACTEA|LEITE|QUEIJO|MANTEIGA|IOGURTE|REQUEIJAO|CREME DE LEITE", "LATICINIO|LACTEO", "Laticínios"),
        new Regra("ARROZ|FEIJAO|LENTILHA|GRAO DE BICO|ERVILHA", "GRAO|CEREA|LEGUMINOSA", "Grãos"),
        new Regra("TOMATE|CEBOLA|BATATA|ALFACE|CENOURA|ABOBORA|ABOBRINHA|BROCOLIS|COUVE|PEPINO|BANANA|MACA|LARANJA|LIMAO", "HORTIFRUTI|HORTALICA|VERDURA|LEGUME|FRUTA", "Hortifruti"),
        new Regra("MACARRAO|ESPAGUETE|LASANHA|PENNE|FUSILLI", "MASSA", "Massas"),
        new Regra("SAL|PIMENTA|OREGANO|COMINHO|COLORAU|TEMPERO|CANELA", "TEMPERO|CONDIMENTO|ESPECIARIA", "Temperos e condimentos"),
        new Regra("OLEO|AZEITE|VINAGRE|MOLHO", "OLEO|AZEITE|MOLHO|CONDIMENTO", "Óleos e molhos"),
        new Regra("BATATA PALHA|BATATA CHIPS|ACUCAR|FARINHA|CAFE|FUBA|AMIDO|BISCOITO|BOLACHA|CHOCOLATE EM PO", "MERCEARIA|SECO|DESPENSA|CEREA", "Mercearia")
    );
    private static final Set<String> GENERICOS = Set.of("INTEGRAL", "NATURAL", "TRADICIONAL", "REFINADO", "CONGELADO", "RESFRIADO", "PACOTE", "CAIXA", "EXTRA", "TIPO", "BEBIDA", "PRODUTO", "ARTIGO", "KIT", "MISTURA", "PREPARADO", "FILE", "CREME");
    private static final Pattern EMBALAGEM = Pattern.compile(
        "(?i)(?<![\\p{L}\\d])\\d+(?:[.,]\\d+)?\\s*(?:KG|GR|G|ML|LT|L|UN|UND|UNID)\\b");
    private static final Pattern CREME_LEITE = Pattern.compile("\\bCR (?:DE )?LEITE\\b");
    private static final Pattern ABREVIACOES = Pattern.compile(
        "\\b(?:BEB|GAISE|GASEIF|REFRI|REFRIG|BOV|SUIN|FRG|CONTRAFILE|QJO|QUEIJ|LEIT|FAR|ACUC|MACARR|DETERG|DESINF)\\b");

    public record Sugestao(Long categoriaId, String origem, String motivo, boolean revisar) { }

    public Long sugerir(String descricao, List<ProdutoModel> produtos, List<CategoriaModel> categorias) {
        return analisar(descricao, produtos, categorias).categoriaId();
    }

    public Sugestao analisar(String descricao, List<ProdutoModel> produtos, List<CategoriaModel> categorias) {
        String nome = normalizarDescricao(descricao);
        var disponiveis = categorias.stream().filter(CategoriaModel::isAtivo).toList();
        var ids = disponiveis.stream().filter(c -> !AssociadorProdutoNota.normalizar(c.getNome()).equals("SEM CLASSIFICACAO"))
            .map(CategoriaModel::getId).collect(java.util.stream.Collectors.toSet());
        var catalogo = produtos.stream().filter(p -> p.isAtivo() && p.getCategoria()!=null
            && ids.contains(p.getCategoria().getId())).toList();
        String comparavel = nomeComparavel(descricao);
        // A embalagem não define a categoria; nomes completos prevalecem sobre a primeira palavra.
        var exatas = catalogo.stream().filter(p -> !comparavel.isBlank() && nomeComparavel(p.getNome()).equals(comparavel))
            .map(p -> p.getCategoria().getId()).distinct().toList();
        if (exatas.size() == 1)
            return new Sugestao(exatas.getFirst(), "CATALOGO", "Produto com o mesmo nome normalizado no catálogo, desconsiderando apenas a embalagem informada.", false);
        if (exatas.size() > 1) return revisar("O catálogo usa categorias diferentes para o mesmo nome normalizado. Confira a categoria.");
        // Nestes compostos o ingrediente não identifica o tipo do produto.
        if (nome.matches(".*\\b(?:LEITE DE COCO|CALDO DE CARNE)\\b.*"))
            return revisar("O nome tem mais de um sentido. Escolha a categoria após conferir o produto.");
        var encontradas = tipos(nome);
        if (encontradas.size() > 1) return revisar("A descrição combina tipos de produto diferentes. Confira a categoria.");
        String primeiro = nome.split(" ")[0];
        var conhecidas = catalogo.stream().filter(p -> primeiro.length() >= 4 && !GENERICOS.contains(primeiro)
                && normalizarDescricao(p.getNome()).split(" ")[0].equals(primeiro)
                && tipos(normalizarDescricao(p.getNome())).equals(encontradas))
            .map(p -> p.getCategoria().getId()).distinct().toList();
        if (conhecidas.size() == 1)
            return new Sugestao(conhecidas.getFirst(), "CATALOGO", "Produtos com o mesmo nome principal no catálogo usam esta categoria.", false);
        if (conhecidas.size() > 1) return revisar("O catálogo usa categorias diferentes para este nome. Confira a categoria.");
        if (encontradas.isEmpty()) return revisar("Não foi possível reconhecer o tipo do produto. Sem escolha, entrará em Sem classificação.");
        var regra = encontradas.getFirst();
        var padrao = disponiveis.stream().filter(c -> AssociadorProdutoNota.normalizar(c.getNome())
            .equals(AssociadorProdutoNota.normalizar(regra.categoriaPadrao()))).toList();
        var compativeis = padrao.isEmpty() ? disponiveis.stream().filter(c -> regra.categorias()
            .matcher(AssociadorProdutoNota.normalizar(c.getNome())).find()).toList() : padrao;
        if (compativeis.size() != 1) return revisar("O tipo foi reconhecido, mas não há uma única categoria compatível neste restaurante.");
        return new Sugestao(compativeis.getFirst().getId(), "DESCRICAO",
            "Tipo reconhecido na descrição: " + regra.categoriaPadrao() + ". Confira a sugestão antes de confirmar.", false);
    }

    private Sugestao revisar(String motivo) {
        return new Sugestao(null, "NAO_IDENTIFICADA", motivo, true);
    }

    private List<Regra> tipos(String nome) {
        // O prefixo mais específico prevalece: bebida láctea não é refrigerante;
        // batata palha não é o tubérculo. Sem prefixo, conflitos continuam pedindo revisão.
        int maior = REGRAS.stream().mapToInt(r -> r.comprimentoInicial(nome)).max().orElse(0);
        return REGRAS.stream().filter(r -> maior > 0 ? r.comprimentoInicial(nome) == maior
            : r.produtos().matcher(nome).find()).toList();
    }

    private String nomeComparavel(String descricao) {
        return normalizarDescricao(EMBALAGEM.matcher(descricao == null ? "" : descricao).replaceAll(" "));
    }

    private String normalizarDescricao(String descricao) {
        // Apenas a busca usa as abreviações expandidas; a descrição fiscal é preservada.
        String nome = AssociadorProdutoNota.normalizar(descricao == null ? "" : descricao);
        nome = CREME_LEITE.matcher(nome).replaceAll("CREME DE LEITE");
        return ABREVIACOES.matcher(nome).replaceAll(m -> switch (m.group()) {
            case "BEB" -> "BEBIDA";
            case "GAISE", "GASEIF" -> "GASEIFICADA";
            case "REFRI", "REFRIG" -> "REFRIGERANTE";
            case "BOV" -> "BOVINO";
            case "SUIN" -> "SUINO";
            case "FRG" -> "FRANGO";
            case "CONTRAFILE" -> "CONTRA FILE";
            case "QJO", "QUEIJ" -> "QUEIJO";
            case "LEIT" -> "LEITE";
            case "FAR" -> "FARINHA";
            case "ACUC" -> "ACUCAR";
            case "MACARR" -> "MACARRAO";
            case "DETERG" -> "DETERGENTE";
            case "DESINF" -> "DESINFETANTE";
            default -> m.group();
        });
    }
}

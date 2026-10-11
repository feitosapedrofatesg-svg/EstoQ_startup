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
        new Regra("REFRIGERANTE|SUCO|CERVEJA|VINHO|AGUA MINERAL|ENERGETICO", "BEBIDA", "Bebidas"),
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
    private static final Set<String> GENERICOS = Set.of("INTEGRAL", "NATURAL", "TRADICIONAL", "REFINADO", "CONGELADO", "RESFRIADO", "PACOTE", "CAIXA", "EXTRA", "TIPO");

    public Long sugerir(String descricao, List<ProdutoModel> produtos, List<CategoriaModel> categorias) {
        String nome = AssociadorProdutoNota.normalizar(descricao);
        String primeiro = nome.split(" ")[0];
        // Produtos com o mesmo nome principal ajudam a respeitar a organização do catálogo.
        var conhecidas = produtos.stream().filter(p -> primeiro.length() >= 4 && !GENERICOS.contains(primeiro)
                && AssociadorProdutoNota.normalizar(p.getNome()).split(" ")[0].equals(primeiro))
            .map(p -> p.getCategoria().getId()).distinct().toList();
        if (conhecidas.size() == 1) return conhecidas.getFirst();
        if (conhecidas.size() > 1) return null;
        // O nome principal prevalece sobre ingredientes: molho de tomate não é hortifruti.
        var regras = REGRAS.stream().sorted(java.util.Comparator.comparing(regra ->
            !Pattern.compile("^(?:" + regra.produtos() + ")\\b").matcher(nome).find())).toList();
        for (var regra : regras) {
            if (!Pattern.compile("\\b(?:" + regra.produtos() + ")\\b").matcher(nome).find()) continue;
            // A categoria padrão evita confundir grupos com aliases semelhantes.
            var padrao = categorias.stream().filter(c -> AssociadorProdutoNota.normalizar(c.getNome())
                .equals(AssociadorProdutoNota.normalizar(regra.categoriaPadrao()))).toList();
            if (padrao.size()==1) return padrao.getFirst().getId();
            if (padrao.size()>1) return null;
            var encontradas = categorias.stream().filter(c -> Pattern.compile(regra.categorias())
                .matcher(AssociadorProdutoNota.normalizar(c.getNome())).find()).toList();
            if (encontradas.size() == 1) return encontradas.getFirst().getId();
            // Não escolhe uma categoria arbitrária quando há mais de uma correspondência.
            return null;
        }
        return null;
    }
}

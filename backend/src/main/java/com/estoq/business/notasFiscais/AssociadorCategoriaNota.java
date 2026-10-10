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
    private record Regra(String produtos, String categorias) { }
    private static final List<Regra> REGRAS = List.of(
        new Regra("DETERGENTE|DESINFETANTE|AGUA SANITARIA|SABAO|ESPONJA|ALVEJANTE|LIMPADOR", "LIMPEZA|HIGIENE"),
        new Regra("GUARDANAPO|EMBALAGEM|DESCARTAVEL|DESCARTAVEIS|PAPEL ALUMINIO|FILME PVC", "DESCARTAVE|EMBALAGE"),
        new Regra("REFRIGERANTE|SUCO|CERVEJA|VINHO|AGUA MINERAL|ENERGETICO", "BEBIDA"),
        new Regra("BOVINO|BOVINA|SUINO|SUINA|FRANGO|PICANHA|ALCATRA|MAMINHA|CONTRA FILE|COSTELA|CARNE|LINGUICA|BACON", "CARNE|PROTEINA|ACOUGUE"),
        new Regra("PEIXE|TILAPIA|SALMAO|CAMARAO|SARDINHA|ATUM", "PEIXE|PESCADO|FRUTOS DO MAR|PROTEINA"),
        new Regra("LEITE|QUEIJO|MANTEIGA|IOGURTE|REQUEIJAO|CREME DE LEITE", "LATICINIO|LACTEO"),
        new Regra("ARROZ|FEIJAO|LENTILHA|GRAO DE BICO|ERVILHA", "GRAO|CEREA|LEGUMINOSA"),
        new Regra("TOMATE|CEBOLA|BATATA|ALFACE|CENOURA|ABOBORA|ABOBRINHA|BROCOLIS|COUVE|PEPINO|BANANA|MACA|LARANJA|LIMAO", "HORTIFRUTI|HORTALICA|VERDURA|LEGUME|FRUTA"),
        new Regra("MACARRAO|ESPAGUETE|LASANHA|PENNE|FUSILLI", "MASSA"),
        new Regra("SAL|PIMENTA|OREGANO|COMINHO|COLORAU|TEMPERO|CANELA", "TEMPERO|CONDIMENTO|ESPECIARIA"),
        new Regra("OLEO|AZEITE|VINAGRE|MOLHO", "OLEO|AZEITE|MOLHO|CONDIMENTO"),
        new Regra("ACUCAR|FARINHA|CAFE|FUBA|AMIDO", "MERCEARIA|SECO|DESPENSA|CEREA")
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
        for (var regra : REGRAS) {
            if (!Pattern.compile("\\b(?:" + regra.produtos() + ")\\b").matcher(nome).find()) continue;
            var encontradas = categorias.stream().filter(c -> Pattern.compile(regra.categorias())
                .matcher(AssociadorProdutoNota.normalizar(c.getNome())).find()).toList();
            if (encontradas.size() == 1) return encontradas.getFirst().getId();
            // Não escolhe uma categoria arbitrária quando há mais de uma correspondência.
            return null;
        }
        return null;
    }
}

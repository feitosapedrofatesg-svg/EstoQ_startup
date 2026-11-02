package db.migration;

import com.estoq.business.categorias.CategoriaModel;
import com.estoq.business.notasFiscais.AssociadorCategoriaNota;
import com.estoq.business.notasFiscais.AssociadorProdutoNota;
import com.estoq.business.produtos.ProdutoModel;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reavalia apenas cadastros ativos sem classificação; não altera estoque nem categorias já escolhidas. */
public class V15__classificar_produtos_pendentes extends BaseJavaMigration {
    private record Mudanca(long produto, long restaurante, long anterior, long nova, long versao) { }

    @Override
    public void migrate(Context context) throws Exception {
        var conexao = context.getConnection();
        var porId = new HashMap<Long, CategoriaModel>();
        Map<Long, List<CategoriaModel>> categorias = new HashMap<>();
        Map<Long, List<ProdutoModel>> catalogos = new HashMap<>();
        try (var st = conexao.createStatement(); var rs = st.executeQuery(
                "select id,restaurante_id,nome,ativo from categorias")) {
            while (rs.next()) {
                var c = new CategoriaModel();
                c.setId(rs.getLong(1)); c.setRestauranteId(rs.getLong(2));
                c.setNome(rs.getString(3)); c.setAtivo(rs.getBoolean(4));
                porId.put(c.getId(), c);
                categorias.computeIfAbsent(c.getRestauranteId(), id -> new ArrayList<>()).add(c);
            }
        }
        try (var st = conexao.createStatement(); var rs = st.executeQuery(
                "select id,restaurante_id,nome,categoria_id,version from produtos where ativo=true")) {
            while (rs.next()) {
                var p = new ProdutoModel();
                p.setId(rs.getLong(1)); p.setRestauranteId(rs.getLong(2)); p.setNome(rs.getString(3));
                p.setCategoria(porId.get(rs.getLong(4))); p.setVersion(rs.getLong(5));
                if (p.getCategoria() != null && p.getRestauranteId().equals(p.getCategoria().getRestauranteId()))
                    catalogos.computeIfAbsent(p.getRestauranteId(), id -> new ArrayList<>()).add(p);
            }
        }
        var associador = new AssociadorCategoriaNota();
        var mudancas = new ArrayList<Mudanca>();
        // Todas as decisões usam o mesmo retrato do catálogo: a ordem não propaga novas inferências.
        for (var entrada : catalogos.entrySet()) {
            var disponiveis = categorias.getOrDefault(entrada.getKey(), List.of());
            for (var p : entrada.getValue()) {
                if (!AssociadorProdutoNota.normalizar(p.getCategoria().getNome()).equals("SEM CLASSIFICACAO")) continue;
                var sugestao = associador.analisar(p.getNome(), entrada.getValue(), disponiveis);
                if (sugestao.categoriaId() != null && !sugestao.revisar())
                    mudancas.add(new Mudanca(p.getId(), p.getRestauranteId(), p.getCategoria().getId(), sugestao.categoriaId(), p.getVersion()));
            }
        }
        int total = 0;
        try (var st = conexao.prepareStatement("""
                update produtos set categoria_id=?, version=version+1
                where id=? and restaurante_id=? and categoria_id=? and version=? and ativo=true
                """)) {
            for (var m : mudancas) {
                st.setLong(1, m.nova()); st.setLong(2, m.produto()); st.setLong(3, m.restaurante());
                st.setLong(4, m.anterior()); st.setLong(5, m.versao());
                total += st.executeUpdate();
            }
        }
        System.getLogger(getClass().getName()).log(System.Logger.Level.INFO,
                "Produtos sem classificação reclassificados com resultado único: {0}.", total);
    }
}

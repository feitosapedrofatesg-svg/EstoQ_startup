package db.migration;

import com.estoq.business.produtos.EstoqueLimpeza;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Converte a medida inteira, incluindo histórico, sem criar compras ou consumo fictícios. */
public class V17__limpeza_por_unidade extends BaseJavaMigration {
    private static final System.Logger LOG = System.getLogger(V17__limpeza_por_unidade.class.getName());
    private static final Pattern MEDIDA_EMBALAGEM = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(KG|ML|G|L)\\b", Pattern.CASE_INSENSITIVE);
    private record Produto(long id, long restaurante, String nome, String unidade, String categoria) { }
    private record Medida(String tabela, String coluna, String filtro, int escala) { }
    private static final List<Medida> MEDIDAS = List.of(
            new Medida("lotes", "quantidade_inicial", "produto_id", 3),
            new Medida("lotes", "quantidade_atual", "produto_id", 3),
            new Medida("movimentacoes", "quantidade", "produto_id", 3),
            new Medida("movimentacoes", "quantidade_anterior", "produto_id", 3),
            new Medida("movimentacoes", "quantidade_posterior", "produto_id", 3),
            new Medida("itens_balanco", "quantidade_sistema", "produto_id", 3),
            new Medida("itens_balanco", "quantidade_fisica", "produto_id", 3),
            new Medida("produtos_abertos", "quantidade_aberta", "lote_id in (select id from lotes where produto_id", 3),
            new Medida("produtos_abertos", "quantidade_utilizada", "lote_id in (select id from lotes where produto_id", 3),
            new Medida("itens_nota_fiscal", "quantidade_entrada", "produto_id", 3),
            new Medida("itens_nota_fiscal", "fator", "produto_id", 6),
            new Medida("vinculos_fornecedor", "fator", "produto_id", 6));

    @Override
    public void migrate(Context context) throws Exception {
        var c = context.getConnection();
        // Impede que uma operação iniciada na unidade antiga atravesse a conversão.
        try(var st=c.createStatement()) {
            st.execute("set local lock_timeout='10s'");
            st.execute("lock table produtos,lotes,movimentacoes,itens_balanco,produtos_abertos,itens_nota_fiscal,vinculos_fornecedor,entradas,parametros_estoque in access exclusive mode");
        }
        var produtos = new ArrayList<Produto>();
        try (var st = c.createStatement(); var rs = st.executeQuery("""
                select p.id, p.restaurante_id, p.nome, p.unidade_medida, c.nome
                from produtos p join categorias c on c.id=p.categoria_id and c.restaurante_id=p.restaurante_id
                where p.unidade_medida in ('KG','G','L','ML')
                """)) {
            while (rs.next()) produtos.add(new Produto(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5)));
        }
        for (var p : produtos) {
            if (!EstoqueLimpeza.porUnidade(p.nome(), p.categoria())) continue;
            var fator = fator(c, p);
            if (fator == null || !exata(c, p, fator)) {
                LOG.log(System.Logger.Level.WARNING, "Limpeza {0}, restaurante {1}: conversão para UN requer conferência da embalagem/histórico.", p.id(), p.restaurante());
                continue;
            }
            try (var st = c.prepareStatement("select e.id,e.conteudo_embalagem,e.unidade_conteudo from entradas e join movimentacoes m on m.id=e.id where m.produto_id=? and m.restaurante_id=? and e.conteudo_embalagem is not null")) {
                st.setLong(1,p.id()); st.setLong(2,p.restaurante());
                try (var rs=st.executeQuery()) {
                    while(rs.next()) {
                        var conteudo=converter(rs.getBigDecimal(2),rs.getString(3),p.unidade()).divide(fator,6,RoundingMode.UNNECESSARY);
                        try(var update=c.prepareStatement("update entradas set conteudo_embalagem=?, unidade_conteudo='UN' where id=?")) {
                            update.setBigDecimal(1,conteudo); update.setLong(2,rs.getLong(1)); update.executeUpdate();
                        }
                    }
                }
            }
            for (var m : MEDIDAS) {
                atualizar(c, "update " + m.tabela() + " set " + m.coluna() + "=" + m.coluna()
                        + "/?, version=version+1 where " + filtro(m) + " and restaurante_id=?", p, fator);
            }
            atualizar(c, "update lotes set preco_unitario=preco_unitario*?, version=version+1 where produto_id=? and restaurante_id=?", p, fator);
            // Mantém os níveis padrão em 3 UN; níveis personalizados acompanham a conversão.
            atualizar(c, """
                    update parametros_estoque set
                    consumo_medio_diario=round(consumo_medio_diario/?,3),
                    estoque_minimo=case when estoque_minimo=3 and estoque_medio=3 and estoque_maximo=3 then 3 else ceil(estoque_minimo/?) end,
                    estoque_medio=case when estoque_minimo=3 and estoque_medio=3 and estoque_maximo=3 then 3 else ceil(estoque_medio/?) end,
                    estoque_maximo=case when estoque_minimo=3 and estoque_medio=3 and estoque_maximo=3 then 3 else ceil(estoque_maximo/?) end,
                    version=version+1, data_atualizacao=current_timestamp
                    where produto_id=? and restaurante_id=?
                    """, p, fator, fator, fator, fator);
            atualizar(c, "update entradas set unidade_compra='UN' where id in (select id from movimentacoes where produto_id=? and restaurante_id=?)", p);
            atualizar(c, "update itens_nota_fiscal set unidade_estoque='UN' where produto_id=? and restaurante_id=?", p);
            atualizar(c, "update vinculos_fornecedor set unidade_estoque='UN' where produto_id=? and restaurante_id=?", p);
            atualizar(c, "update produtos set unidade_medida='UN', version=version+1 where id=? and restaurante_id=?", p);
        }
    }

    private BigDecimal fator(Connection c, Produto p) throws SQLException {
        var fatores = new TreeSet<BigDecimal>();
        try (var st = c.prepareStatement("""
                select i.unidade_nota,i.fator,i.descricao,i.unidade_estoque from itens_nota_fiscal i
                join notas_fiscais n on n.id=i.nota_id and n.restaurante_id=i.restaurante_id
                where i.produto_id=? and i.restaurante_id=? and n.confirmada=true and i.selecionado=true
                """)) {
            st.setLong(1, p.id()); st.setLong(2, p.restaurante());
            try (var rs = st.executeQuery()) {
                while (rs.next()) if (EstoqueLimpeza.embalagemAvulsa(rs.getString(1))) {
                    var valor = rs.getBigDecimal(2);
                    if (valor == null || valor.signum() <= 0) return null;
                    var unidades = EstoqueLimpeza.unidadesPorEmbalagem(rs.getString(3));
                    if (unidades == null || !p.unidade().equals(rs.getString(4))) return null;
                    try { fatores.add(valor.divide(unidades)); } catch (ArithmeticException e) { return null; }
                }
            }
        }
        // A quantidade avulsa da nota prevalece: também corrige um volume cadastrado errado.
        if (!fatores.isEmpty()) return fatores.size() == 1 ? fatores.first() : null;
        var matcher = MEDIDA_EMBALAGEM.matcher(p.nome());
        while (matcher.find()) {
            var valor = new BigDecimal(matcher.group(1).replace(',', '.'));
            if (valor.signum() <= 0) return null;
            var unidades = EstoqueLimpeza.unidadesPorEmbalagem(p.nome());
            if (unidades == null) return null;
            try { fatores.add(converter(valor,matcher.group(2).toUpperCase(java.util.Locale.ROOT),p.unidade()).divide(unidades)); }
            catch (ArithmeticException | IllegalArgumentException e) { return null; }
        }
        if (!fatores.isEmpty()) return fatores.size() == 1 ? fatores.first() : null;
        try (var st = c.prepareStatement("select count(*) from lotes where produto_id=? and restaurante_id=?")) {
            st.setLong(1, p.id()); st.setLong(2, p.restaurante());
            try (var rs = st.executeQuery()) { rs.next(); return rs.getLong(1) == 0 ? BigDecimal.ONE : null; }
        }
    }

    private boolean exata(Connection c, Produto p, BigDecimal fator) throws SQLException {
        for (var m : MEDIDAS) {
            try (var st = c.prepareStatement("select " + m.coluna() + " from " + m.tabela() + " where " + filtro(m) + " and restaurante_id=?")) {
                st.setLong(1, p.id()); st.setLong(2, p.restaurante());
                try (var rs = st.executeQuery()) {
                    while (rs.next()) {
                        var quantidade = rs.getBigDecimal(1);
                        if (quantidade != null) try {
                            var convertida = quantidade.divide(fator, m.escala(), RoundingMode.UNNECESSARY);
                            if (convertida.precision() > 18) return false;
                        } catch (ArithmeticException e) { return false; }
                    }
                }
            }
        }
        try (var st = c.prepareStatement("select preco_unitario from lotes where produto_id=? and restaurante_id=?")) {
            st.setLong(1, p.id()); st.setLong(2, p.restaurante());
            try (var rs = st.executeQuery()) {
                while (rs.next()) try {
                    if (rs.getBigDecimal(1).multiply(fator).setScale(6, RoundingMode.UNNECESSARY).precision() > 18) return false;
                } catch (ArithmeticException e) { return false; }
            }
        }
        try(var st=c.prepareStatement("select e.conteudo_embalagem,e.unidade_conteudo from entradas e join movimentacoes m on m.id=e.id where m.produto_id=? and m.restaurante_id=? and e.conteudo_embalagem is not null")) {
            st.setLong(1,p.id()); st.setLong(2,p.restaurante());
            try(var rs=st.executeQuery()) { while(rs.next()) try {
                if(converter(rs.getBigDecimal(1),rs.getString(2),p.unidade()).divide(fator,6,RoundingMode.UNNECESSARY).precision()>18) return false;
            } catch(ArithmeticException | IllegalArgumentException e) { return false; } }
        }
        return true;
    }

    private BigDecimal converter(BigDecimal valor, String origem, String destino) {
        if(origem == null || destino == null) throw new IllegalArgumentException("Unidade ausente");
        if(origem.equals(destino)) return valor;
        if((origem.equals("KG") && destino.equals("G")) || (origem.equals("L") && destino.equals("ML"))) return valor.multiply(new BigDecimal("1000"));
        if((origem.equals("G") && destino.equals("KG")) || (origem.equals("ML") && destino.equals("L"))) return valor.divide(new BigDecimal("1000"));
        throw new IllegalArgumentException("Medidas incompatíveis");
    }

    private String filtro(Medida m) {
        return m.filtro() + "=?" + (m.tabela().equals("produtos_abertos") ? ")" : "");
    }

    private void atualizar(Connection c, String sql, Produto p, BigDecimal... fatores) throws SQLException {
        try (var st = c.prepareStatement(sql)) {
            int i = 1;
            for (var fator : fatores) st.setBigDecimal(i++, fator);
            st.setLong(i++, p.id()); st.setLong(i, p.restaurante());
            st.executeUpdate();
        }
    }
}

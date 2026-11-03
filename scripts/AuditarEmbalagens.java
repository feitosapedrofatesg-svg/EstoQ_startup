import java.sql.*;

/** Auditoria somente em leitura. Credenciais por DATABASE_URL/USER/PASSWORD; saída JSON privada. */
class AuditarEmbalagens {
    static String linhas(Connection c, String sql) throws SQLException {
        var out = new StringBuilder("[");
        try (var st = c.createStatement(); var rs = st.executeQuery(sql)) {
            while (rs.next()) { if (out.length() > 1) out.append(','); out.append(rs.getString(1)); }
        }
        return out.append(']').toString();
    }
    public static void main(String[] args) throws Exception {
        try (var c = DriverManager.getConnection(System.getenv("DATABASE_URL"), System.getenv("DATABASE_USER"), System.getenv("DATABASE_PASSWORD"))) {
            c.setReadOnly(true); c.setAutoCommit(false);
            try (var st = c.createStatement()) { st.execute("set local statement_timeout='30s'"); }
            var notas = linhas(c, """
                select row_to_json(t)::text from (
                  select i.id as item_id,i.restaurante_id,i.nota_id,i.produto_id,p.nome as produto,
                    i.descricao,i.unidade_nota,i.quantidade_nota,i.unidade_estoque,i.fator,i.quantidade_entrada,
                    m.quantidade as quantidade_movimentada,
                    'Conferir fator e quantidade revisada; a quantidade fiscal pode ter sido corrigida na revisão. Não deduzir saldo correto.' as observacao
                  from itens_nota_fiscal i join notas_fiscais n on n.id=i.nota_id and n.restaurante_id=i.restaurante_id
                  join produtos p on p.id=i.produto_id and p.restaurante_id=i.restaurante_id
                  left join movimentacoes m on m.id=i.movimentacao_id and m.restaurante_id=i.restaurante_id
                  where n.confirmada and i.selecionado and
                    (i.descricao ~* '[0-9]+([.,][0-9]+)?[[:space:]]*(KG|G|ML|L)\\y'
                     or i.quantidade_entrada is distinct from m.quantidade)
                  order by i.restaurante_id,i.id
                ) t
                """);
            var manuais = linhas(c, """
                select row_to_json(t)::text from (
                  select m.id as entrada_id,m.restaurante_id,m.produto_id,p.nome as produto,p.unidade_medida,
                    e.unidade_compra,m.quantidade,e.valor_total_pago,
                    'Inconclusivo: sem conteúdo registrado, não se pode distinguir peso direto de embalagem apenas pelo nome.' as observacao
                  from entradas e join movimentacoes m on m.id=e.id join produtos p on p.id=m.produto_id and p.restaurante_id=m.restaurante_id
                  where (to_jsonb(e)->>'conteudo_embalagem') is null
                    and not exists(select 1 from itens_nota_fiscal i where i.movimentacao_id=m.id and i.restaurante_id=m.restaurante_id)
                  order by m.restaurante_id,m.id
                ) t
                """);
            var cadastros = linhas(c, """
                select row_to_json(t)::text from (
                  select id as produto_id,restaurante_id,nome,unidade_medida,
                    'Possível controle por embalagem: descrição contém peso, mas cadastro usa UN. Revisar sem alterar histórico.' as observacao
                  from produtos where ativo and unidade_medida='UN' and nome ~* '[0-9]+([.,][0-9]+)?[[:space:]]*(KG|G)\\y'
                  order by restaurante_id,id
                ) t
                """);
            c.rollback();
            System.out.println("{\"somenteLeitura\":true,\"itensNota\":"+notas+",\"entradasManuaisInconclusivas\":"+manuais+",\"cadastrosEmUNComPeso\":"+cadastros+"}");
        } catch (SQLException e) {
            System.err.println("Auditoria falhou. SQLState="+e.getSQLState()); System.exit(1);
        }
    }
}

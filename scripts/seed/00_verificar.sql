-- =====================================================================
-- ETAPA 0 - verificacao (somente leitura)
-- Gerado por scripts/gerar_seed_sql.py -- nao editar a mao.
-- Gerado em 2026-09-28
-- Destino: estoq_v2
-- Loja: feitosapedrowin@gmail.com (restaurante_id resolvido em tempo de execucao)
-- =====================================================================
-- Arquivo: 00_verificar.sql
--
-- Como rodar: cole no SQL editor do Neon. Para revisar sem gravar, troque o
-- COMMIT final por ROLLBACK (ou use a variante -dryrun).
-- =====================================================================

-- Rode isto ANTES das etapas 1-3. Nao grava nada.
-- 1) O usuario alvo tem loja propria? (a resposta decide se o seed e seguro)
SELECT u.id, u.nome, u.perfil, u.restaurante_id, r.nome AS loja
  FROM estoq_v2.usuarios u
  LEFT JOIN estoq_v2.restaurantes r ON r.id = u.restaurante_id
 WHERE lower(u.email) = 'feitosapedrowin@gmail.com';

-- 2) O que ja existe hoje
SELECT (SELECT count(*) FROM estoq_v2.categorias WHERE restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')) AS categorias,
       (SELECT count(*) FROM estoq_v2.produtos   WHERE restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')) AS produtos,
       (SELECT count(*) FROM estoq_v2.lotes      WHERE restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')) AS lotes,
       (SELECT count(*) FROM estoq_v2.movimentacoes WHERE restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')) AS movimentacoes;

-- 3) O schema de producao tem as colunas que o seed usa?
DO $$
DECLARE
    faltando text;
BEGIN
    SELECT string_agg(format('%I.%I', split_part(t, '.', 2), split_part(t, '.', 3)), ', ')
      INTO faltando
      FROM unnest(ARRAY[
            'estoq_v2.restaurantes.id', 'estoq_v2.restaurantes.nome', 'estoq_v2.restaurantes.ativo'
            'estoq_v2.usuarios.id', 'estoq_v2.usuarios.email', 'estoq_v2.usuarios.perfil', 'estoq_v2.usuarios.restaurante_id'
            'estoq_v2.categorias.id', 'estoq_v2.categorias.nome', 'estoq_v2.categorias.descricao', 'estoq_v2.categorias.restaurante_id', 'estoq_v2.categorias.data_hora_criacao', 'estoq_v2.categorias.ativo', 'estoq_v2.categorias.version'
            'estoq_v2.produtos.id', 'estoq_v2.produtos.nome', 'estoq_v2.produtos.unidade_medida', 'estoq_v2.produtos.categoria_id', 'estoq_v2.produtos.codigo_barras', 'estoq_v2.produtos.restaurante_id', 'estoq_v2.produtos.data_hora_criacao', 'estoq_v2.produtos.ativo', 'estoq_v2.produtos.version'
            'estoq_v2.parametros_estoque.id', 'estoq_v2.parametros_estoque.produto_id', 'estoq_v2.parametros_estoque.tempo_reposicao_dias', 'estoq_v2.parametros_estoque.periodo_analise_dias', 'estoq_v2.parametros_estoque.consumo_medio_diario', 'estoq_v2.parametros_estoque.estoque_minimo', 'estoq_v2.parametros_estoque.estoque_medio', 'estoq_v2.parametros_estoque.estoque_maximo', 'estoq_v2.parametros_estoque.dias_alerta_vencimento', 'estoq_v2.parametros_estoque.data_atualizacao', 'estoq_v2.parametros_estoque.restaurante_id', 'estoq_v2.parametros_estoque.data_hora_criacao', 'estoq_v2.parametros_estoque.ativo', 'estoq_v2.parametros_estoque.version'
            'estoq_v2.lotes.id', 'estoq_v2.lotes.codigo', 'estoq_v2.lotes.produto_id', 'estoq_v2.lotes.quantidade_inicial', 'estoq_v2.lotes.quantidade_atual', 'estoq_v2.lotes.data_entrada', 'estoq_v2.lotes.data_validade', 'estoq_v2.lotes.preco_unitario', 'estoq_v2.lotes.restaurante_id', 'estoq_v2.lotes.data_hora_criacao', 'estoq_v2.lotes.ativo', 'estoq_v2.lotes.version'
            'estoq_v2.movimentacoes.id', 'estoq_v2.movimentacoes.tipo', 'estoq_v2.movimentacoes.data_hora', 'estoq_v2.movimentacoes.produto_id', 'estoq_v2.movimentacoes.lote_id', 'estoq_v2.movimentacoes.usuario_id', 'estoq_v2.movimentacoes.quantidade', 'estoq_v2.movimentacoes.quantidade_anterior', 'estoq_v2.movimentacoes.quantidade_posterior', 'estoq_v2.movimentacoes.observacao', 'estoq_v2.movimentacoes.restaurante_id', 'estoq_v2.movimentacoes.data_hora_criacao', 'estoq_v2.movimentacoes.ativo', 'estoq_v2.movimentacoes.version'
            'estoq_v2.entradas.id', 'estoq_v2.entradas.valor_total_pago', 'estoq_v2.entradas.unidade_compra', 'estoq_v2.entradas.data_validade'
      ]) AS x(t)
     WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns c
         WHERE c.table_schema = split_part(t, '.', 1)
           AND c.table_name = split_part(t, '.', 2)
           AND c.column_name = split_part(t, '.', 3)
    );
    IF faltando IS NOT NULL THEN
        RAISE EXCEPTION 'colunas ausentes -- seed abortado: %', faltando;
    END IF;
    RAISE NOTICE 'schema confere';
END $$;

-- 4) unidade_medida e varchar, nao enum do Postgres (@Enumerated(STRING)).
-- Conferimos o que ja existe gravado, para calibrar o que o seed pode escrever.
SELECT DISTINCT unidade_medida FROM estoq_v2.produtos ORDER BY 1;

-- 5) As tabelas estao no schema esperado?
SELECT table_name FROM information_schema.tables
 WHERE table_schema = 'estoq_v2' ORDER BY table_name;

-- 6) Colunas NOT NULL que o seed precisa preencher (qualquer NOT NULL fora
-- desta lista com valor default quebraria o INSERT).
SELECT table_name, column_name
  FROM information_schema.columns
 WHERE table_schema = 'estoq_v2'
   AND is_nullable = 'NO'
   AND column_default IS NULL
   AND table_name IN ('categorias','produtos','lotes','entradas','movimentacoes',
                      'parametros_estoque')
 ORDER BY table_name, column_name;

-- =====================================================================
-- ETAPA 0 - verificacao (somente leitura)
-- Gerado por scripts/gerar_seed_sql.py -- nao editar a mao.
-- Gerado em 2026-09-28
-- Destino: public
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
  FROM public.usuarios u
  LEFT JOIN public.restaurantes r ON r.id = u.restaurante_id
 WHERE lower(u.email) = 'feitosapedrowin@gmail.com';

-- 2) O que ja existe hoje
SELECT (SELECT count(*) FROM public.categorias WHERE restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')) AS categorias,
       (SELECT count(*) FROM public.produtos   WHERE restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')) AS produtos,
       (SELECT count(*) FROM public.lotes      WHERE restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')) AS lotes,
       (SELECT count(*) FROM public.movimentacoes WHERE restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')) AS movimentacoes;

-- 3) O schema de producao tem as colunas que o seed usa?
DO $$
DECLARE
    faltando text;
BEGIN
    SELECT string_agg(format('%I.%I', split_part(t, '.', 2), split_part(t, '.', 3)), ', ')
      INTO faltando
      FROM unnest(ARRAY[
            'public.restaurantes.id', 'public.restaurantes.nome', 'public.restaurantes.ativo',
                        'public.usuarios.id', 'public.usuarios.email', 'public.usuarios.perfil', 'public.usuarios.restaurante_id',
                        'public.categorias.id', 'public.categorias.nome', 'public.categorias.descricao', 'public.categorias.restaurante_id', 'public.categorias.data_hora_criacao', 'public.categorias.ativo', 'public.categorias.version',
                        'public.produtos.id', 'public.produtos.nome', 'public.produtos.unidade_medida', 'public.produtos.categoria_id', 'public.produtos.codigo_barras', 'public.produtos.restaurante_id', 'public.produtos.data_hora_criacao', 'public.produtos.ativo', 'public.produtos.version',
                        'public.parametros_estoque.id', 'public.parametros_estoque.produto_id', 'public.parametros_estoque.tempo_reposicao_dias', 'public.parametros_estoque.periodo_analise_dias', 'public.parametros_estoque.consumo_medio_diario', 'public.parametros_estoque.estoque_minimo', 'public.parametros_estoque.estoque_medio', 'public.parametros_estoque.estoque_maximo', 'public.parametros_estoque.dias_alerta_vencimento', 'public.parametros_estoque.data_atualizacao', 'public.parametros_estoque.restaurante_id', 'public.parametros_estoque.data_hora_criacao', 'public.parametros_estoque.ativo', 'public.parametros_estoque.version',
                        'public.lotes.id', 'public.lotes.codigo', 'public.lotes.produto_id', 'public.lotes.quantidade_inicial', 'public.lotes.quantidade_atual', 'public.lotes.data_entrada', 'public.lotes.data_validade', 'public.lotes.preco_unitario', 'public.lotes.restaurante_id', 'public.lotes.data_hora_criacao', 'public.lotes.ativo', 'public.lotes.version',
                        'public.movimentacoes.id', 'public.movimentacoes.tipo', 'public.movimentacoes.data_hora', 'public.movimentacoes.produto_id', 'public.movimentacoes.lote_id', 'public.movimentacoes.usuario_id', 'public.movimentacoes.quantidade', 'public.movimentacoes.quantidade_anterior', 'public.movimentacoes.quantidade_posterior', 'public.movimentacoes.observacao', 'public.movimentacoes.restaurante_id', 'public.movimentacoes.data_hora_criacao', 'public.movimentacoes.ativo', 'public.movimentacoes.version',
                        'public.entradas.id', 'public.entradas.valor_total_pago', 'public.entradas.unidade_compra', 'public.entradas.data_validade'
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
SELECT DISTINCT unidade_medida FROM public.produtos ORDER BY 1;

-- 5) As tabelas estao no schema esperado?
SELECT table_name FROM information_schema.tables
 WHERE table_schema = 'public' ORDER BY table_name;

-- 6) Colunas NOT NULL que o seed precisa preencher (qualquer NOT NULL fora
-- desta lista com valor default quebraria o INSERT).
SELECT table_name, column_name
  FROM information_schema.columns
 WHERE table_schema = 'public'
   AND is_nullable = 'NO'
   AND column_default IS NULL
   AND table_name IN ('categorias','produtos','lotes','entradas','movimentacoes',
                      'parametros_estoque')
 ORDER BY table_name, column_name;

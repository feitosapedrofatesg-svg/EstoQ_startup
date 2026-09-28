-- =====================================================================
-- ETAPA 1 - categorias
-- Gerado por scripts/gerar_seed_sql.py -- nao editar a mao.
-- Gerado em 2026-09-28
-- Destino: public
-- Loja: feitosapedrowin@gmail.com (restaurante_id resolvido em tempo de execucao)
-- =====================================================================
-- Arquivo: 01_categorias.sql
---- 15 categorias
-- Como rodar: cole no SQL editor do Neon. Para revisar sem gravar, troque o
-- COMMIT final por ROLLBACK (ou use a variante -dryrun).
-- =====================================================================

BEGIN;

-- Falha fechada: se o schema de producao nao bater com o que o backend
-- valida (DDL_AUTO=validate), aborta antes de gravar qualquer linha.
DO $$
DECLARE
    faltando text;
BEGIN
    SELECT string_agg(format('%I.%I', split_part(t, '.', 2), split_part(t, '.', 3)), ', ')
      INTO faltando
      FROM unnest(ARRAY[
            'public.restaurantes.id', 'public.restaurantes.nome', 'public.restaurantes.ativo',
            'public.usuarios.id', 'public.usuarios.email', 'public.usuarios.perfil',
            'public.usuarios.restaurante_id', 'public.categorias.id', 'public.categorias.nome',
            'public.categorias.descricao', 'public.categorias.restaurante_id', 'public.categorias.data_hora_criacao',
            'public.categorias.ativo', 'public.categorias.version', 'public.produtos.id',
            'public.produtos.nome', 'public.produtos.unidade_medida', 'public.produtos.categoria_id',
            'public.produtos.codigo_barras', 'public.produtos.restaurante_id', 'public.produtos.data_hora_criacao',
            'public.produtos.ativo', 'public.produtos.version', 'public.parametros_estoque.id',
            'public.parametros_estoque.produto_id', 'public.parametros_estoque.tempo_reposicao_dias', 'public.parametros_estoque.periodo_analise_dias',
            'public.parametros_estoque.consumo_medio_diario', 'public.parametros_estoque.estoque_minimo', 'public.parametros_estoque.estoque_medio',
            'public.parametros_estoque.estoque_maximo', 'public.parametros_estoque.dias_alerta_vencimento', 'public.parametros_estoque.data_atualizacao',
            'public.parametros_estoque.restaurante_id', 'public.parametros_estoque.data_hora_criacao', 'public.parametros_estoque.ativo',
            'public.parametros_estoque.version', 'public.lotes.id', 'public.lotes.codigo',
            'public.lotes.produto_id', 'public.lotes.quantidade_inicial', 'public.lotes.quantidade_atual',
            'public.lotes.data_entrada', 'public.lotes.data_validade', 'public.lotes.preco_unitario',
            'public.lotes.restaurante_id', 'public.lotes.data_hora_criacao', 'public.lotes.ativo',
            'public.lotes.version', 'public.movimentacoes.id', 'public.movimentacoes.tipo',
            'public.movimentacoes.data_hora', 'public.movimentacoes.produto_id', 'public.movimentacoes.lote_id',
            'public.movimentacoes.usuario_id', 'public.movimentacoes.quantidade', 'public.movimentacoes.quantidade_anterior',
            'public.movimentacoes.quantidade_posterior', 'public.movimentacoes.observacao', 'public.movimentacoes.restaurante_id',
            'public.movimentacoes.data_hora_criacao', 'public.movimentacoes.ativo', 'public.movimentacoes.version',
            'public.entradas.id', 'public.entradas.valor_total_pago', 'public.entradas.unidade_compra',
            'public.entradas.data_validade'
      ]) AS x(t)
     WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns c
         WHERE c.table_schema = split_part(t, '.', 1)
           AND c.table_name = split_part(t, '.', 2)
           AND c.column_name = split_part(t, '.', 3)
    );
    IF faltando IS NOT NULL THEN
        RAISE EXCEPTION
            'colunas ausentes no schema -- seed abortado sem gravar nada: %', faltando;
    END IF;
END $$;
-- O TenantEntity usa @TenantId: linhas com restaurante_id nulo nao sao
-- filtradas por tenant, ou seja, aparecem para TODOS os restaurantes. Por isso
-- o seed recusa usuario PLATAFORMA ou sem loja propria.
DO $$
DECLARE
    v_restaurante_id bigint;
    v_perfil text;
BEGIN
    SELECT u.restaurante_id, u.perfil
      INTO v_restaurante_id, v_perfil
      FROM public.usuarios u
     WHERE lower(u.email) = 'feitosapedrowin@gmail.com';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'usuario nao encontrado: % -- seed abortado', 'feitosapedrowin@gmail.com';
    END IF;
    IF v_restaurante_id IS NULL THEN
        RAISE EXCEPTION
            'usuario % tem restaurante_id nulo (perfil %) -- seed abortado',
            'feitosapedrowin@gmail.com', v_perfil;
    END IF;
    IF v_perfil = 'PLATAFORMA' THEN
        RAISE EXCEPTION
            'perfil PLATAFORMA enxerga a base inteira; semear aqui vaza dado entre restaurantes';
    END IF;
    RAISE NOTICE 'semeando no restaurante_id % (perfil %)', v_restaurante_id, v_perfil;
END $$;

-- 15 categorias. Idempotente por nome dentro do tenant.

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Bebidas', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Bebidas' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Carnes e Proteínas', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Congelados', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Congelados' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Descartáveis e Limpeza', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Descartáveis e Limpeza' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Enlatados e Conservas', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Grãos e Farinhas', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Hortifruti', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Laticínios', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Massas e Macarrão', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Massas e Macarrão' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Molhos e Temperos', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Não-perecíveis', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Ovos', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Ovos' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Padaria e Confeitaria', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Padaria e Confeitaria' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Peixes e Frutos do Mar', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Peixes e Frutos do Mar' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO public.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Polpas e Doces', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM public.categorias c
     WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

-- Conferindo o que entrou
SELECT c.nome, count(p.id) AS produtos
  FROM public.categorias c
  LEFT JOIN public.produtos p ON p.categoria_id = c.id AND p.ativo = true
 WHERE c.restaurante_id = (SELECT u.restaurante_id FROM public.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 GROUP BY c.nome
 ORDER BY c.nome;

COMMIT;

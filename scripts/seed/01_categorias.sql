-- =====================================================================
-- ETAPA 1 - categorias
-- Gerado por scripts/gerar_seed_sql.py -- nao editar a mao.
-- Gerado em 2026-09-28
-- Destino: estoq_v2
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
            'estoq_v2.restaurantes.id', 'estoq_v2.restaurantes.nome', 'estoq_v2.restaurantes.ativo',
            'estoq_v2.usuarios.id', 'estoq_v2.usuarios.email', 'estoq_v2.usuarios.perfil',
            'estoq_v2.usuarios.restaurante_id', 'estoq_v2.categorias.id', 'estoq_v2.categorias.nome',
            'estoq_v2.categorias.descricao', 'estoq_v2.categorias.restaurante_id', 'estoq_v2.categorias.data_hora_criacao',
            'estoq_v2.categorias.ativo', 'estoq_v2.categorias.version', 'estoq_v2.produtos.id',
            'estoq_v2.produtos.nome', 'estoq_v2.produtos.unidade_medida', 'estoq_v2.produtos.categoria_id',
            'estoq_v2.produtos.codigo_barras', 'estoq_v2.produtos.restaurante_id', 'estoq_v2.produtos.data_hora_criacao',
            'estoq_v2.produtos.ativo', 'estoq_v2.produtos.version', 'estoq_v2.parametros_estoque.id',
            'estoq_v2.parametros_estoque.produto_id', 'estoq_v2.parametros_estoque.tempo_reposicao_dias', 'estoq_v2.parametros_estoque.periodo_analise_dias',
            'estoq_v2.parametros_estoque.consumo_medio_diario', 'estoq_v2.parametros_estoque.estoque_minimo', 'estoq_v2.parametros_estoque.estoque_medio',
            'estoq_v2.parametros_estoque.estoque_maximo', 'estoq_v2.parametros_estoque.dias_alerta_vencimento', 'estoq_v2.parametros_estoque.data_atualizacao',
            'estoq_v2.parametros_estoque.restaurante_id', 'estoq_v2.parametros_estoque.data_hora_criacao', 'estoq_v2.parametros_estoque.ativo',
            'estoq_v2.parametros_estoque.version', 'estoq_v2.lotes.id', 'estoq_v2.lotes.codigo',
            'estoq_v2.lotes.produto_id', 'estoq_v2.lotes.quantidade_inicial', 'estoq_v2.lotes.quantidade_atual',
            'estoq_v2.lotes.data_entrada', 'estoq_v2.lotes.data_validade', 'estoq_v2.lotes.preco_unitario',
            'estoq_v2.lotes.restaurante_id', 'estoq_v2.lotes.data_hora_criacao', 'estoq_v2.lotes.ativo',
            'estoq_v2.lotes.version', 'estoq_v2.movimentacoes.id', 'estoq_v2.movimentacoes.tipo',
            'estoq_v2.movimentacoes.data_hora', 'estoq_v2.movimentacoes.produto_id', 'estoq_v2.movimentacoes.lote_id',
            'estoq_v2.movimentacoes.usuario_id', 'estoq_v2.movimentacoes.quantidade', 'estoq_v2.movimentacoes.quantidade_anterior',
            'estoq_v2.movimentacoes.quantidade_posterior', 'estoq_v2.movimentacoes.observacao', 'estoq_v2.movimentacoes.restaurante_id',
            'estoq_v2.movimentacoes.data_hora_criacao', 'estoq_v2.movimentacoes.ativo', 'estoq_v2.movimentacoes.version',
            'estoq_v2.entradas.id', 'estoq_v2.entradas.valor_total_pago', 'estoq_v2.entradas.unidade_compra',
            'estoq_v2.entradas.data_validade'
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
      FROM estoq_v2.usuarios u
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

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Bebidas', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Bebidas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Carnes e Proteínas', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Congelados', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Congelados' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Descartáveis e Limpeza', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Descartáveis e Limpeza' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Enlatados e Conservas', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Grãos e Farinhas', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Hortifruti', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Laticínios', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Massas e Macarrão', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Massas e Macarrão' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Molhos e Temperos', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Não-perecíveis', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Ovos', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Ovos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Padaria e Confeitaria', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Padaria e Confeitaria' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Peixes e Frutos do Mar', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Peixes e Frutos do Mar' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

INSERT INTO estoq_v2.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT 'Polpas e Doces', 'Catalogo importado da planilha de CMV Real (dez/2023)',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM estoq_v2.categorias c
     WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 );

-- Conferindo o que entrou
SELECT c.nome, count(p.id) AS produtos
  FROM estoq_v2.categorias c
  LEFT JOIN estoq_v2.produtos p ON p.categoria_id = c.id AND p.ativo = true
 WHERE c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
 GROUP BY c.nome
 ORDER BY c.nome;

COMMIT;

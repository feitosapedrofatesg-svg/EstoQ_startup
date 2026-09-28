-- =====================================================================
-- ETAPA 3 - estoque
-- Gerado por scripts/gerar_seed_sql.py -- nao editar a mao.
-- Gerado em 2026-09-28
-- Destino: estoq_v2
-- Loja: feitosapedrowin@gmail.com (restaurante_id resolvido em tempo de execucao)
-- =====================================================================
-- Arquivo: 03_estoque.sql
---- parametros + lotes + entradas + movimentacoes para 130 produtos com saldo
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

-- Repete o que EntradaService.registrarEntrada faz:
--   1. parametros_estoque  (1:1 com produto)
--   2. lotes               (quantidade_inicial = quantidade_atual = saldo)
--   3. movimentacoes       (tabela pai do JOINED, tipo='ENTRADA')
--   4. entradas            (tabela filha, mesmo id do pai)
-- O preco unitario e valor_total_pago / quantidade, como no servico.
-- O codigo do lote sai L-%06d, igual ao String.format do LoteService. O
-- placeholder precisa ser unico por lote: existe UNIQUE(restaurante_id, codigo)
-- em lotes, entao 'PENDENTE-SEED' puro colidiria no segundo produto.
--
-- Idempotente: cada statement so grava se a linha ainda nao existir, e os
-- produtos sao localizados por nome (unico no catalogo).
--
-- ATENCAO: esta etapa cria ENTRADA, nao saida. CMV mede consumo, entao o CMV
-- do app vai aparecer zerado. Para CMV preenchido, seria preciso tambem as
-- semanas de consumo da coluna M da planilha.


-- ================ parametros_estoque (todos os 182 produtos) ================

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.321, 3.375, 9.000, 13.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Café em pó - 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Gelo' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.660, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Acem' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.476, 4.994, 36.376, 54.564, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Alcatra' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.218, 2.291, 5.892, 8.838, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Apresuntado' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.460, 4.827, 5.674, 8.511, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Bacon fatiado' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.107, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Bacon pedaço' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.515, 15.906, 17.338, 26.007, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Capa de contra-filé' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 2.034, 15.134, 15.134, 22.701, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Contra filé' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.371, 14.400, 23.120, 34.680, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Coração de galinha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.209, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Costela Gaucha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.709, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Costela P A' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.832, 1.000, 1.000, 1.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Costela Suina' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.654, 6.868, 10.546, 15.819, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Costelinha de Caranha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.631, 17.122, 39.378, 59.067, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Coxa e sobrecoxa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.849, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Coxao mole' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.993, 10.431, 38.798, 58.197, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Coxinha da asa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 2.099, 13.328, 13.328, 19.992, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Cupim' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.148, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Dobradinha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.080, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Figado' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.682, 17.660, 37.030, 55.545, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Filé de Sassami' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.936, 9.825, 25.326, 37.989, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Filé de peito de frango' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.505, 15.806, 48.532, 72.798, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Fraldinha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.880, 18.326, 18.326, 27.489, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Frango caipira' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.036, 0.375, 2.000, 3.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Hondashi - 500 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.143, 1.500, 9.000, 13.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Kibe' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.103, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Kit Feijoada preparado e pronto' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.093, 11.477, 45.078, 67.617, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Lagarto' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.554, 4.000, 4.000, 6.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Linguiça calabresa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.754, 7.916, 14.110, 21.165, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Linguiça de Frango' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.110, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Linguiça fina' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.010, 10.608, 30.436, 45.654, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Linguiça suina' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.377, 14.234, 14.234, 21.351, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Lombo suino' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 2.519, 17.912, 17.912, 26.868, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Maminha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Maça do Peito' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.510, 15.859, 20.704, 31.056, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Meio da asa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.179, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Moela 1kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.756, 18.437, 27.912, 41.868, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Peito bovino' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.775, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pernil' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 2.636, 22.860, 22.860, 34.290, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Picanha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.604, 1.859, 1.859, 2.789, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Rabada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.435, 15.072, 25.384, 38.076, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Sobrecoxa - file de coxa/sobrecoxa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.469, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Toucinho' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.357, 14.250, 32.412, 48.618, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Batata palito 1 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.934, 4.004, 4.004, 6.006, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Mandioca congelada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.298, 3.128, 3.717, 5.576, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Massa de lasanha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Catupiry - sachê 1,80 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 3.214, 33.750, 133.000, 199.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Sal sache' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, -0.067, 1.797, 3.594, 5.391, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Azeitona Fatiada verde - 3,2kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Azeitona preta 3,2 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, -0.022, 1.000, 2.000, 3.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Azeitona sem caroço verde - 2kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.071, 0.750, 1.000, 1.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Azeitona verde fatiada -2Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.214, 2.250, 6.000, 9.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Ervilha enlatada - 195g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.857, 7.000, 7.000, 10.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Milho enlatado - 195 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.511, 5.363, 47.625, 71.438, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Palmito em conserva 800g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.127, 1.329, 14.150, 21.225, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Amido de milho 5Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.429, 4.500, 14.000, 21.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Arroz 5 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.143, 1.500, 6.000, 9.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Arroz Integral 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.089, 0.938, 5.500, 8.250, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Açúcar Cristalizado 5 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.036, 0.375, 1.000, 1.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Coco Ralado - 200g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.321, 3.375, 7.000, 10.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Farinha de Trigo 5Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.867, 9.107, 45.012, 67.518, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Farinha de mandioca 20 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Farinha de milho 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.036, 0.375, 2.000, 3.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Feijão Branco 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 2.214, 23.250, 25.000, 37.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Feijão Carioca 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.250, 2.625, 34.000, 51.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Feijão preto Cristal 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Flocão de milho - 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.038, 0.398, 1.446, 2.169, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Fuba de milho 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Grao de bico - 500 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.929, 1.000, 1.000, 1.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Milho - Bandeja' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.049, 0.510, 7.526, 11.289, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Trigo para quibe 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.571, 4.000, 4.000, 6.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Abacaxi' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.572, 6.006, 34.132, 51.198, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Abobora Kabutia' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Abobora Moranga' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.332, 13.982, 16.524, 24.786, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Abobrinha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.250, 2.625, 13.500, 20.250, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Alho' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.179, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Alho poró' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.714, 7.500, 21.853, 32.780, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Aneis de cebola' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 2.744, 21.694, 21.694, 32.541, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Banana da terra' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.542, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Banana prata' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.956, 10.035, 20.264, 30.396, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Batata doce' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 2.886, 6.660, 6.660, 9.990, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Batata inglesa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.107, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Batata palha - 800g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.323, 3.392, 9.692, 14.538, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Beringela' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.993, 10.426, 24.876, 37.314, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Beterraba' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 2.061, 6.700, 6.700, 10.050, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Brocolis' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 2.046, 8.464, 8.464, 12.696, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Cebola' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.201, 2.113, 2.976, 4.464, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Cebola Roxa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.284, 6.472, 6.472, 9.708, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Cenoura' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.844, 6.510, 6.510, 9.765, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Chuchu' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.108, 1.131, 5.968, 8.952, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Cogumelo Fatiado - 3,2 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.643, 2.000, 2.000, 3.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Couve flor' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.107, 1.125, 4.000, 6.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Guariroba' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.191, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Jilo' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.446, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Limão taiti' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.113, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Manga' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.044, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Maça fuji' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.179, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Morango' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.543, 5.702, 18.756, 28.134, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pepino' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.592, 6.211, 12.562, 18.843, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pequi 1 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.211, 2.217, 6.554, 9.831, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimentao Verde' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.177, 1.858, 4.834, 7.251, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimentao amarelo' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.214, 2.249, 4.328, 6.492, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimentao vermelho' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.589, 6.184, 8.516, 12.774, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Quiabo' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Repolho roxo' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.973, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Repolho verde' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 3.077, 2.090, 2.090, 3.135, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Tomate' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.071, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Tomate cereja' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.314, 3.299, 13.040, 19.560, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Tomate seco' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.071, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Uva Thompson 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.096, 1.006, 3.004, 4.506, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Uva passa preta 1kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.368, 1.366, 1.366, 2.049, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Vagem' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.643, 6.000, 6.000, 9.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Creme de leite 1,030 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.143, 12.000, 12.000, 18.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Leite Condensado 395g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.786, 18.750, 20.000, 30.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Leite Integral 1L' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.357, 3.750, 9.000, 13.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Leite de coco - 500mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.125, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Margarina 15Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.382, 4.008, 5.808, 8.712, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Mussarela barra' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.730, 7.663, 9.668, 14.502, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Mussarela fatiada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.216, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Queijo Coalho' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Queijo Fresco' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.036, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Queijo Trança' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.161, 1.695, 3.250, 4.875, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Queijo ralado - 760 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.071, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Requeijão cremoso 1,8kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.036, 10.875, 81.000, 121.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Macarrão ESP 8 - pct 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.000, 10.500, 76.000, 114.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Macarrão FUR. 5 - pct 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.179, 1.875, 9.000, 13.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Macarrão NINHO - pct 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.028, 0.292, 1.710, 2.565, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Macarrão PARAFUSO - 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.857, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Massa de pastel 200 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.014, 0.143, 0.580, 0.870, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Achocolatado 750 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Adoçante Zero - 200mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.036, 0.375, 2.000, 3.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Açafrão 0,250 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.001, 0.010, 0.078, 0.117, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Bicarbonato' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 2.500, 5.000, 7.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Caldo de Carne 1kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.036, 0.375, 2.000, 3.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Caldo de galinha 1kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.001, 0.008, 0.438, 0.657, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Canela Moida 500 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Colorau 0,250 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.464, 4.875, 18.000, 27.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Extrato de Tomate 2 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.006, 0.061, 0.324, 0.486, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Folha de Louro 250g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.107, 1.125, 3.000, 4.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Gergelim branco 150 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.036, 0.375, 3.000, 4.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Gergelim preto 150 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.077, 0.806, 2.148, 3.222, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Ketchup' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.464, 4.875, 14.000, 21.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Maionese - 1,02 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Molho Shoyu 3L' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.036, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Molho alho 215ml' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Molho barbecue 3kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.071, 0.750, 6.000, 9.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Molho de pimenta - 900mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.036, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Molho salada hellmanns rose 210ml' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.036, 0.375, 2.000, 3.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Oregano' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimenta Calabresa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.052, 0.541, 3.450, 5.175, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimenta biquinho - 3,02 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.071, 0.750, 1.000, 1.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimenta de bode' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.124, 0.402, 0.402, 0.603, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimenta de cheiro' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimenta do reino' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.214, 2.250, 24.000, 36.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Sal Grosso 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.964, 10.125, 34.000, 51.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Sal refinado 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.071, 0.750, 2.000, 3.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Azeite de Oliva extra virgem- 500mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.071, 0.750, 8.000, 12.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Azeite de dendê - 900mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.214, 2.250, 4.000, 6.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Gelatina sem sabor 24G' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.214, 2.250, 7.000, 10.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Oleo MISTO - 500mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.036, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Vinagre de Álcool - 5L' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 2.357, 24.750, 40.000, 60.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Óleo de Soja - 900mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.893, 7.000, 7.000, 10.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Ovos - cartela de 30un' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.571, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Ovos de codorna' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.006, 0.061, 2.324, 3.486, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Biscoito Maisena' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.015, 0.154, 1.066, 1.599, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Chocolate em barra - 1,01 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.029, 0.305, 0.814, 1.221, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Farinha de Rosca 5Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.036, 0.375, 5.196, 7.794, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pão de forma 450 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Bacalhau' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.050, 0.523, 1.394, 2.091, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Camarão' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Filé de peixe pangasius' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 1.335, 4.811, 4.811, 7.217, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Peixe -Dourada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.500, 1.000, 1.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de Pequi' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.179, 1.875, 10.000, 15.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de abacaxi' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de abacaxi com hortela' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.393, 4.125, 8.000, 12.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de acerola' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.321, 3.375, 9.000, 13.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de cajá' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.321, 3.375, 9.000, 13.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de cajú' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.000, 1.500, 3.000, 4.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de goiaba' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.500, 0.000, 0.000, 0.000, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de maracujá' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.286, 1.000, 1.000, 1.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de morango' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);

INSERT INTO estoq_v2.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, 0.393, 4.125, 5.000, 7.500, 7,
       now(), (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de uva' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);


-- ================ lotes + entradas + movimentacoes (130 produtos com saldo) ================

-- Café em pó - 500g (Bebidas, UN): 9.000 x R$ 16.030769
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 9.000, 9.000, '2026-09-28',
       '2027-03-27', 16.030769, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Café em pó - 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 9.000,
       0, 9.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Café em pó - 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 144.28, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Café em pó - 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Alcatra (Carnes e Proteínas, KG): 36.376 x R$ 32.7
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 36.376, 36.376, '2026-09-28',
       '2026-10-01', 32.700000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Alcatra' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 36.376,
       0, 36.376, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Alcatra' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 1189.50, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Alcatra' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Apresuntado (Carnes e Proteínas, KG): 5.892 x R$ 14.9
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 5.892, 5.892, '2026-09-28',
       '2026-10-01', 14.900000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Apresuntado' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 5.892,
       0, 5.892, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Apresuntado' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 87.79, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Apresuntado' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Bacon fatiado (Carnes e Proteínas, KG): 5.674 x R$ 27.3
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 5.674, 5.674, '2026-09-28',
       '2026-10-01', 27.300000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Bacon fatiado' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 5.674,
       0, 5.674, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Bacon fatiado' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 154.90, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Bacon fatiado' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Capa de contra-filé (Carnes e Proteínas, KG): 17.338 x R$ 24.408272
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 17.338, 17.338, '2026-09-28',
       '2026-10-01', 24.408272, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Capa de contra-filé' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 17.338,
       0, 17.338, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Capa de contra-filé' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 423.19, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Capa de contra-filé' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Contra filé (Carnes e Proteínas, KG): 15.134 x R$ 41.244464
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 15.134, 15.134, '2026-09-28',
       '2026-10-01', 41.244464, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Contra filé' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 15.134,
       0, 15.134, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Contra filé' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 624.19, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Contra filé' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Coração de galinha (Carnes e Proteínas, KG): 23.120 x R$ 31.540654
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 23.120, 23.120, '2026-09-28',
       '2026-10-01', 31.540654, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Coração de galinha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 23.120,
       0, 23.120, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Coração de galinha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 729.22, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Coração de galinha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Costela Suina (Carnes e Proteínas, KG): 1.000 x R$ 24.99
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 1.000, 1.000, '2026-09-28',
       '2026-10-01', 24.990000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Costela Suina' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 1.000,
       0, 1.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Costela Suina' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 24.99, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Costela Suina' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Costelinha de Caranha (Carnes e Proteínas, KG): 10.546 x R$ 19.9
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 10.546, 10.546, '2026-09-28',
       '2026-10-01', 19.900000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Costelinha de Caranha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 10.546,
       0, 10.546, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Costelinha de Caranha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 209.87, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Costelinha de Caranha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Coxa e sobrecoxa (Carnes e Proteínas, KG): 39.378 x R$ 7.255
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 39.378, 39.378, '2026-09-28',
       '2026-10-01', 7.255000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Coxa e sobrecoxa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 39.378,
       0, 39.378, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Coxa e sobrecoxa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 285.69, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Coxa e sobrecoxa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Coxinha da asa (Carnes e Proteínas, KG): 38.798 x R$ 10.86
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 38.798, 38.798, '2026-09-28',
       '2026-10-01', 10.860000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Coxinha da asa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 38.798,
       0, 38.798, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Coxinha da asa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 421.35, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Coxinha da asa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Cupim (Carnes e Proteínas, KG): 13.328 x R$ 41.849631
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 13.328, 13.328, '2026-09-28',
       '2026-10-01', 41.849631, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Cupim' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 13.328,
       0, 13.328, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Cupim' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 557.77, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Cupim' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Filé de Sassami (Carnes e Proteínas, KG): 37.030 x R$ 14.03
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 37.030, 37.030, '2026-09-28',
       '2026-10-01', 14.030000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Filé de Sassami' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 37.030,
       0, 37.030, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Filé de Sassami' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 519.53, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Filé de Sassami' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Filé de peito de frango (Carnes e Proteínas, KG): 25.326 x R$ 14.035
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 25.326, 25.326, '2026-09-28',
       '2026-10-01', 14.035000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Filé de peito de frango' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 25.326,
       0, 25.326, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Filé de peito de frango' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 355.45, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Filé de peito de frango' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Fraldinha (Carnes e Proteínas, KG): 48.532 x R$ 30.013465
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 48.532, 48.532, '2026-09-28',
       '2026-10-01', 30.013465, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Fraldinha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 48.532,
       0, 48.532, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Fraldinha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 1456.61, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Fraldinha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Frango caipira (Carnes e Proteínas, KG): 18.326 x R$ 17.3
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 18.326, 18.326, '2026-09-28',
       '2026-10-01', 17.300000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Frango caipira' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 18.326,
       0, 18.326, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Frango caipira' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 317.04, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Frango caipira' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Hondashi - 500 g (Carnes e Proteínas, UN): 2.000 x R$ 102.0
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 2.000, 2.000, '2026-09-28',
       '2026-10-01', 102.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Hondashi - 500 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 2.000,
       0, 2.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Hondashi - 500 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 204.00, 'UN',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Hondashi - 500 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Kibe (Carnes e Proteínas, KG): 9.000 x R$ 9.8
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 9.000, 9.000, '2026-09-28',
       '2026-10-01', 9.800000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Kibe' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 9.000,
       0, 9.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Kibe' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 88.20, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Kibe' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Lagarto (Carnes e Proteínas, KG): 45.078 x R$ 23.89
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 45.078, 45.078, '2026-09-28',
       '2026-10-01', 23.890000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Lagarto' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 45.078,
       0, 45.078, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Lagarto' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 1076.91, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Lagarto' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Linguiça calabresa (Carnes e Proteínas, KG): 4.000 x R$ 21.042308
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 4.000, 4.000, '2026-09-28',
       '2026-10-01', 21.042308, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Linguiça calabresa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 4.000,
       0, 4.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Linguiça calabresa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 84.17, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Linguiça calabresa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Linguiça de Frango (Carnes e Proteínas, KG): 14.110 x R$ 12.811
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 14.110, 14.110, '2026-09-28',
       '2026-10-01', 12.811000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Linguiça de Frango' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 14.110,
       0, 14.110, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Linguiça de Frango' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 180.76, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Linguiça de Frango' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Linguiça suina (Carnes e Proteínas, KG): 30.436 x R$ 15.1625
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 30.436, 30.436, '2026-09-28',
       '2026-10-01', 15.162500, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Linguiça suina' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 30.436,
       0, 30.436, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Linguiça suina' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 461.49, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Linguiça suina' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Lombo suino (Carnes e Proteínas, KG): 14.234 x R$ 21.344056
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 14.234, 14.234, '2026-09-28',
       '2026-10-01', 21.344056, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Lombo suino' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 14.234,
       0, 14.234, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Lombo suino' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 303.81, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Lombo suino' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Maminha (Carnes e Proteínas, KG): 17.912 x R$ 32.7
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 17.912, 17.912, '2026-09-28',
       '2026-10-01', 32.700000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Maminha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 17.912,
       0, 17.912, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Maminha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 585.72, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Maminha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Meio da asa (Carnes e Proteínas, KG): 20.704 x R$ 18.975
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 20.704, 20.704, '2026-09-28',
       '2026-10-01', 18.975000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Meio da asa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 20.704,
       0, 20.704, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Meio da asa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 392.86, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Meio da asa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Peito bovino (Carnes e Proteínas, KG): 27.912 x R$ 19.515926
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 27.912, 27.912, '2026-09-28',
       '2026-10-01', 19.515926, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Peito bovino' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 27.912,
       0, 27.912, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Peito bovino' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 544.73, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Peito bovino' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Picanha (Carnes e Proteínas, KG): 22.860 x R$ 70.606581
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 22.860, 22.860, '2026-09-28',
       '2026-10-01', 70.606581, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Picanha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 22.860,
       0, 22.860, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Picanha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 1614.07, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Picanha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Rabada (Carnes e Proteínas, KG): 1.859 x R$ 23.79553
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 1.859, 1.859, '2026-09-28',
       '2026-10-01', 23.795530, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Rabada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 1.859,
       0, 1.859, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Rabada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 44.24, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Rabada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Sobrecoxa - file de coxa/sobrecoxa (Carnes e Proteínas, KG): 25.384 x R$ 8.737895
-- validade 2026-10-01 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 25.384, 25.384, '2026-09-28',
       '2026-10-01', 8.737895, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Sobrecoxa - file de coxa/sobrecoxa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 25.384,
       0, 25.384, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Sobrecoxa - file de coxa/sobrecoxa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 221.80, 'KG',
       '2026-10-01'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Sobrecoxa - file de coxa/sobrecoxa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Batata palito 1 kg (Congelados, UN): 32.412 x R$ 12.68
-- validade 2027-01-26 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 32.412, 32.412, '2026-09-28',
       '2027-01-26', 12.680000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Batata palito 1 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 32.412,
       0, 32.412, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Batata palito 1 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 410.98, 'UN',
       '2027-01-26'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Batata palito 1 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Mandioca congelada (Congelados, KG): 4.004 x R$ 4.287169
-- validade 2027-01-26 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 4.004, 4.004, '2026-09-28',
       '2027-01-26', 4.287169, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Mandioca congelada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 4.004,
       0, 4.004, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Mandioca congelada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 17.17, 'KG',
       '2027-01-26'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Mandioca congelada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Massa de lasanha (Congelados, KG): 3.717 x R$ 10.125
-- validade 2027-01-26 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 3.717, 3.717, '2026-09-28',
       '2027-01-26', 10.125000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Massa de lasanha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 3.717,
       0, 3.717, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Massa de lasanha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 37.63, 'KG',
       '2027-01-26'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Massa de lasanha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Sal sache (Descartáveis e Limpeza, UN): 133.000 x R$ 0.96
-- validade 2036-09-25 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 133.000, 133.000, '2026-09-28',
       '2036-09-25', 0.960000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Sal sache' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 133.000,
       0, 133.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Sal sache' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 127.68, 'UN',
       '2036-09-25'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Sal sache' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Azeitona Fatiada verde - 3,2kg (Enlatados e Conservas, UN): 3.594 x R$ 0.0
-- validade 2028-09-17 | SEM CUSTO (sem preco na planilha)

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 3.594, 3.594, '2026-09-28',
       '2028-09-17', 0.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Azeitona Fatiada verde - 3,2kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 3.594,
       0, 3.594, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Azeitona Fatiada verde - 3,2kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 0.00, 'UN',
       '2028-09-17'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Azeitona Fatiada verde - 3,2kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Azeitona sem caroço verde - 2kg (Enlatados e Conservas, UN): 2.000 x R$ 40.6
-- validade 2028-09-17 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 2.000, 2.000, '2026-09-28',
       '2028-09-17', 40.600000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Azeitona sem caroço verde - 2kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 2.000,
       0, 2.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Azeitona sem caroço verde - 2kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 81.20, 'UN',
       '2028-09-17'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Azeitona sem caroço verde - 2kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Azeitona verde fatiada -2Kg (Enlatados e Conservas, UN): 1.000 x R$ 39.25
-- validade 2028-09-17 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 1.000, 1.000, '2026-09-28',
       '2028-09-17', 39.250000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Azeitona verde fatiada -2Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 1.000,
       0, 1.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Azeitona verde fatiada -2Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 39.25, 'UN',
       '2028-09-17'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Azeitona verde fatiada -2Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Ervilha enlatada - 195g (Enlatados e Conservas, UN): 6.000 x R$ 3.69
-- validade 2028-09-17 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 6.000, 6.000, '2026-09-28',
       '2028-09-17', 3.690000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Ervilha enlatada - 195g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 6.000,
       0, 6.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Ervilha enlatada - 195g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 22.14, 'UN',
       '2028-09-17'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Ervilha enlatada - 195g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Milho enlatado - 195 g (Enlatados e Conservas, UN): 7.000 x R$ 2.966129
-- validade 2028-09-17 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 7.000, 7.000, '2026-09-28',
       '2028-09-17', 2.966129, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Milho enlatado - 195 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 7.000,
       0, 7.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Milho enlatado - 195 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 20.76, 'UN',
       '2028-09-17'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Milho enlatado - 195 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Palmito em conserva 800g (Enlatados e Conservas, UN): 47.625 x R$ 42.4
-- validade 2028-09-17 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 47.625, 47.625, '2026-09-28',
       '2028-09-17', 42.400000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Palmito em conserva 800g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 47.625,
       0, 47.625, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Palmito em conserva 800g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 2019.30, 'UN',
       '2028-09-17'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Palmito em conserva 800g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Amido de milho 5Kg (Grãos e Farinhas, UN): 14.150 x R$ 27.556
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 14.150, 14.150, '2026-09-28',
       '2027-09-28', 27.556000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Amido de milho 5Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 14.150,
       0, 14.150, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Amido de milho 5Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 389.92, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Amido de milho 5Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Arroz 5 Kg (Grãos e Farinhas, UN): 14.000 x R$ 29.114286
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 14.000, 14.000, '2026-09-28',
       '2027-09-28', 29.114286, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Arroz 5 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 14.000,
       0, 14.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Arroz 5 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 407.60, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Arroz 5 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Arroz Integral 1 Kg (Grãos e Farinhas, UN): 6.000 x R$ 6.86
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 6.000, 6.000, '2026-09-28',
       '2027-09-28', 6.860000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Arroz Integral 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 6.000,
       0, 6.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Arroz Integral 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 41.16, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Arroz Integral 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Açúcar Cristalizado 5 Kg (Grãos e Farinhas, UN): 5.500 x R$ 17.49
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 5.500, 5.500, '2026-09-28',
       '2027-09-28', 17.490000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Açúcar Cristalizado 5 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 5.500,
       0, 5.500, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Açúcar Cristalizado 5 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 96.20, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Açúcar Cristalizado 5 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Coco Ralado - 200g (Grãos e Farinhas, UN): 1.000 x R$ 3.0
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 1.000, 1.000, '2026-09-28',
       '2027-09-28', 3.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Coco Ralado - 200g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 1.000,
       0, 1.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Coco Ralado - 200g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 3.00, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Coco Ralado - 200g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Farinha de Trigo 5Kg (Grãos e Farinhas, UN): 7.000 x R$ 3.718182
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 7.000, 7.000, '2026-09-28',
       '2027-09-28', 3.718182, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Farinha de Trigo 5Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 7.000,
       0, 7.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Farinha de Trigo 5Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 26.03, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Farinha de Trigo 5Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Farinha de mandioca 20 kg (Grãos e Farinhas, UN): 45.012 x R$ 12.0
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 45.012, 45.012, '2026-09-28',
       '2027-09-28', 12.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Farinha de mandioca 20 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 45.012,
       0, 45.012, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Farinha de mandioca 20 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 540.14, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Farinha de mandioca 20 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Feijão Branco 1 Kg (Grãos e Farinhas, UN): 2.000 x R$ 10.49
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 2.000, 2.000, '2026-09-28',
       '2027-09-28', 10.490000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Feijão Branco 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 2.000,
       0, 2.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Feijão Branco 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 20.98, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Feijão Branco 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Feijão Carioca 1 Kg (Grãos e Farinhas, UN): 25.000 x R$ 7.314627
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 25.000, 25.000, '2026-09-28',
       '2027-09-28', 7.314627, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Feijão Carioca 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 25.000,
       0, 25.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Feijão Carioca 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 182.87, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Feijão Carioca 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Feijão preto Cristal 1 Kg (Grãos e Farinhas, UN): 34.000 x R$ 33.47
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 34.000, 34.000, '2026-09-28',
       '2027-09-28', 33.470000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Feijão preto Cristal 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 34.000,
       0, 34.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Feijão preto Cristal 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 1137.98, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Feijão preto Cristal 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Fuba de milho 500g (Grãos e Farinhas, UN): 1.446 x R$ 10.0
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 1.446, 1.446, '2026-09-28',
       '2027-09-28', 10.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Fuba de milho 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 1.446,
       0, 1.446, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Fuba de milho 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 14.46, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Fuba de milho 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Milho - Bandeja (Grãos e Farinhas, KG): 1.000 x R$ 3.409091
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 1.000, 1.000, '2026-09-28',
       '2027-09-28', 3.409091, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Milho - Bandeja' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 1.000,
       0, 1.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Milho - Bandeja' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 3.41, 'KG',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Milho - Bandeja' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Trigo para quibe 500g (Grãos e Farinhas, UN): 7.526 x R$ 24.0
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 7.526, 7.526, '2026-09-28',
       '2027-09-28', 24.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Trigo para quibe 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 7.526,
       0, 7.526, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Trigo para quibe 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 180.62, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Trigo para quibe 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Abacaxi (Hortifruti, UN): 4.000 x R$ 8.264706
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 4.000, 4.000, '2026-09-28',
       '2026-10-03', 8.264706, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Abacaxi' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 4.000,
       0, 4.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Abacaxi' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 33.06, 'UN',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Abacaxi' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Abobora Kabutia (Hortifruti, KG): 34.132 x R$ 5.063469
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 34.132, 34.132, '2026-09-28',
       '2026-10-03', 5.063469, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Abobora Kabutia' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 34.132,
       0, 34.132, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Abobora Kabutia' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 172.83, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Abobora Kabutia' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Abobrinha (Hortifruti, KG): 16.524 x R$ 3.779424
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 16.524, 16.524, '2026-09-28',
       '2026-10-03', 3.779424, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Abobrinha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 16.524,
       0, 16.524, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Abobrinha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 62.45, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Abobrinha' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Alho (Hortifruti, KG): 13.500 x R$ 19.0
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 13.500, 13.500, '2026-09-28',
       '2026-10-03', 19.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Alho' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 13.500,
       0, 13.500, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Alho' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 256.50, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Alho' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Aneis de cebola (Hortifruti, KG): 21.853 x R$ 22.4
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 21.853, 21.853, '2026-09-28',
       '2026-10-03', 22.400000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Aneis de cebola' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 21.853,
       0, 21.853, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Aneis de cebola' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 489.51, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Aneis de cebola' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Banana da terra (Hortifruti, KG): 21.694 x R$ 6.226579
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 21.694, 21.694, '2026-09-28',
       '2026-10-03', 6.226579, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Banana da terra' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 21.694,
       0, 21.694, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Banana da terra' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 135.08, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Banana da terra' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Batata doce (Hortifruti, KG): 20.264 x R$ 3.112408
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 20.264, 20.264, '2026-09-28',
       '2026-10-03', 3.112408, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Batata doce' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 20.264,
       0, 20.264, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Batata doce' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 63.07, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Batata doce' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Batata inglesa (Hortifruti, KG): 6.660 x R$ 4.712873
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 6.660, 6.660, '2026-09-28',
       '2026-10-03', 4.712873, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Batata inglesa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 6.660,
       0, 6.660, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Batata inglesa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 31.39, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Batata inglesa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Beringela (Hortifruti, KG): 9.692 x R$ 3.502792
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 9.692, 9.692, '2026-09-28',
       '2026-10-03', 3.502792, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Beringela' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 9.692,
       0, 9.692, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Beringela' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 33.95, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Beringela' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Beterraba (Hortifruti, KG): 24.876 x R$ 4.027829
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 24.876, 24.876, '2026-09-28',
       '2026-10-03', 4.027829, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Beterraba' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 24.876,
       0, 24.876, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Beterraba' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 100.20, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Beterraba' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Brocolis (Hortifruti, UN): 6.700 x R$ 5.609375
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 6.700, 6.700, '2026-09-28',
       '2026-10-03', 5.609375, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Brocolis' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 6.700,
       0, 6.700, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Brocolis' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 37.58, 'UN',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Brocolis' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Cebola (Hortifruti, KG): 8.464 x R$ 5.153533
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 8.464, 8.464, '2026-09-28',
       '2026-10-03', 5.153533, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Cebola' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 8.464,
       0, 8.464, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Cebola' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 43.62, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Cebola' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Cebola Roxa (Hortifruti, KG): 2.976 x R$ 7.9
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 2.976, 2.976, '2026-09-28',
       '2026-10-03', 7.900000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Cebola Roxa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 2.976,
       0, 2.976, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Cebola Roxa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 23.51, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Cebola Roxa' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Cenoura (Hortifruti, KG): 6.472 x R$ 3.968859
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 6.472, 6.472, '2026-09-28',
       '2026-10-03', 3.968859, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Cenoura' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 6.472,
       0, 6.472, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Cenoura' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 25.69, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Cenoura' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Chuchu (Hortifruti, KG): 6.510 x R$ 3.080052
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 6.510, 6.510, '2026-09-28',
       '2026-10-03', 3.080052, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Chuchu' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 6.510,
       0, 6.510, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Chuchu' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 20.05, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Chuchu' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Cogumelo Fatiado - 3,2 kg (Hortifruti, UN): 5.968 x R$ 26.84
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 5.968, 5.968, '2026-09-28',
       '2026-10-03', 26.840000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Cogumelo Fatiado - 3,2 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 5.968,
       0, 5.968, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Cogumelo Fatiado - 3,2 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 160.18, 'UN',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Cogumelo Fatiado - 3,2 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Couve flor (Hortifruti, UN): 2.000 x R$ 5.631579
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 2.000, 2.000, '2026-09-28',
       '2026-10-03', 5.631579, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Couve flor' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 2.000,
       0, 2.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Couve flor' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 11.26, 'UN',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Couve flor' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Guariroba (Hortifruti, KG): 4.000 x R$ 32.5
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 4.000, 4.000, '2026-09-28',
       '2026-10-03', 32.500000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Guariroba' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 4.000,
       0, 4.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Guariroba' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 130.00, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Guariroba' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Pepino (Hortifruti, KG): 18.756 x R$ 2.9982
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 18.756, 18.756, '2026-09-28',
       '2026-10-03', 2.998200, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pepino' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 18.756,
       0, 18.756, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Pepino' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 56.23, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Pepino' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Pequi 1 kg (Hortifruti, UN): 12.562 x R$ 8.0
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 12.562, 12.562, '2026-09-28',
       '2026-10-03', 8.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pequi 1 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 12.562,
       0, 12.562, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Pequi 1 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 100.50, 'UN',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Pequi 1 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Pimentao Verde (Hortifruti, KG): 6.554 x R$ 10.979857
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 6.554, 6.554, '2026-09-28',
       '2026-10-03', 10.979857, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimentao Verde' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 6.554,
       0, 6.554, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Pimentao Verde' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 71.96, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Pimentao Verde' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Pimentao amarelo (Hortifruti, KG): 4.834 x R$ 20.819423
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 4.834, 4.834, '2026-09-28',
       '2026-10-03', 20.819423, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimentao amarelo' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 4.834,
       0, 4.834, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Pimentao amarelo' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 100.64, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Pimentao amarelo' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Pimentao vermelho (Hortifruti, KG): 4.328 x R$ 20.92439
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 4.328, 4.328, '2026-09-28',
       '2026-10-03', 20.924390, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimentao vermelho' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 4.328,
       0, 4.328, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Pimentao vermelho' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 90.56, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Pimentao vermelho' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Quiabo (Hortifruti, KG): 8.516 x R$ 6.732383
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 8.516, 8.516, '2026-09-28',
       '2026-10-03', 6.732383, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Quiabo' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 8.516,
       0, 8.516, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Quiabo' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 57.33, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Quiabo' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Tomate (Hortifruti, KG): 2.090 x R$ 3.783865
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 2.090, 2.090, '2026-09-28',
       '2026-10-03', 3.783865, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Tomate' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 2.090,
       0, 2.090, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Tomate' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 7.91, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Tomate' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Tomate seco (Hortifruti, KG): 13.040 x R$ 20.94
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 13.040, 13.040, '2026-09-28',
       '2026-10-03', 20.940000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Tomate seco' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 13.040,
       0, 13.040, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Tomate seco' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 273.06, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Tomate seco' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Uva passa preta 1kg (Hortifruti, UN): 3.004 x R$ 20.0
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 3.004, 3.004, '2026-09-28',
       '2026-10-03', 20.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Uva passa preta 1kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 3.004,
       0, 3.004, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Uva passa preta 1kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 60.08, 'UN',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Uva passa preta 1kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Vagem (Hortifruti, KG): 1.366 x R$ 15.538703
-- validade 2026-10-03 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 1.366, 1.366, '2026-09-28',
       '2026-10-03', 15.538703, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Vagem' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 1.366,
       0, 1.366, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Vagem' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 21.23, 'KG',
       '2026-10-03'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Vagem' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Creme de leite 1,030 Kg (Laticínios, UN): 6.000 x R$ 13.713043
-- validade 2026-10-19 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 6.000, 6.000, '2026-09-28',
       '2026-10-19', 13.713043, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Creme de leite 1,030 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 6.000,
       0, 6.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Creme de leite 1,030 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 82.28, 'UN',
       '2026-10-19'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Creme de leite 1,030 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Leite Condensado 395g (Laticínios, UN): 12.000 x R$ 4.608824
-- validade 2026-10-19 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 12.000, 12.000, '2026-09-28',
       '2026-10-19', 4.608824, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Leite Condensado 395g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 12.000,
       0, 12.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Leite Condensado 395g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 55.31, 'UN',
       '2026-10-19'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Leite Condensado 395g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Leite Integral 1L (Laticínios, UN): 20.000 x R$ 3.47
-- validade 2026-10-19 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 20.000, 20.000, '2026-09-28',
       '2026-10-19', 3.470000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Leite Integral 1L' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 20.000,
       0, 20.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Leite Integral 1L' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 69.40, 'UN',
       '2026-10-19'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Leite Integral 1L' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Leite de coco - 500mL (Laticínios, UN): 9.000 x R$ 7.831818
-- validade 2026-10-19 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 9.000, 9.000, '2026-09-28',
       '2026-10-19', 7.831818, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Leite de coco - 500mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 9.000,
       0, 9.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Leite de coco - 500mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 70.49, 'UN',
       '2026-10-19'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Leite de coco - 500mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Mussarela barra (Laticínios, KG): 5.808 x R$ 26.571508
-- validade 2026-10-19 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 5.808, 5.808, '2026-09-28',
       '2026-10-19', 26.571508, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Mussarela barra' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 5.808,
       0, 5.808, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Mussarela barra' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 154.33, 'KG',
       '2026-10-19'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Mussarela barra' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Mussarela fatiada (Laticínios, KG): 9.668 x R$ 28.907298
-- validade 2026-10-19 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 9.668, 9.668, '2026-09-28',
       '2026-10-19', 28.907298, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Mussarela fatiada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 9.668,
       0, 9.668, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Mussarela fatiada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 279.48, 'KG',
       '2026-10-19'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Mussarela fatiada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Queijo ralado - 760 g (Laticínios, UN): 3.250 x R$ 8.296319
-- validade 2026-10-19 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 3.250, 3.250, '2026-09-28',
       '2026-10-19', 8.296319, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Queijo ralado - 760 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 3.250,
       0, 3.250, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Queijo ralado - 760 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 26.96, 'UN',
       '2026-10-19'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Queijo ralado - 760 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Macarrão ESP 8 - pct 500g (Massas e Macarrão, UN): 81.000 x R$ 2.617
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 81.000, 81.000, '2026-09-28',
       '2027-09-28', 2.617000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Macarrão ESP 8 - pct 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 81.000,
       0, 81.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Macarrão ESP 8 - pct 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 211.98, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Macarrão ESP 8 - pct 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Macarrão FUR. 5 - pct 500g (Massas e Macarrão, UN): 76.000 x R$ 2.815
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 76.000, 76.000, '2026-09-28',
       '2027-09-28', 2.815000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Macarrão FUR. 5 - pct 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 76.000,
       0, 76.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Macarrão FUR. 5 - pct 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 213.94, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Macarrão FUR. 5 - pct 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Macarrão NINHO - pct 500g (Massas e Macarrão, UN): 9.000 x R$ 8.13
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 9.000, 9.000, '2026-09-28',
       '2027-09-28', 8.130000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Macarrão NINHO - pct 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 9.000,
       0, 9.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Macarrão NINHO - pct 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 73.17, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Macarrão NINHO - pct 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Macarrão PARAFUSO - 500g (Massas e Macarrão, UN): 1.710 x R$ 2.65
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 1.710, 1.710, '2026-09-28',
       '2027-09-28', 2.650000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Macarrão PARAFUSO - 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 1.710,
       0, 1.710, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Macarrão PARAFUSO - 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 4.53, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Macarrão PARAFUSO - 500g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Achocolatado 750 g (Molhos e Temperos, UN): 0.580 x R$ 0.0
-- validade 2027-03-27 | SEM CUSTO (sem preco na planilha)

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 0.580, 0.580, '2026-09-28',
       '2027-03-27', 0.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Achocolatado 750 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 0.580,
       0, 0.580, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Achocolatado 750 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 0.00, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Achocolatado 750 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Açafrão 0,250 g (Molhos e Temperos, UN): 2.000 x R$ 24.0
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 2.000, 2.000, '2026-09-28',
       '2027-03-27', 24.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Açafrão 0,250 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 2.000,
       0, 2.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Açafrão 0,250 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 48.00, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Açafrão 0,250 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Bicarbonato (Molhos e Temperos, UN): 0.078 x R$ 0.0
-- validade 2027-03-27 | SEM CUSTO (sem preco na planilha)

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 0.078, 0.078, '2026-09-28',
       '2027-03-27', 0.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Bicarbonato' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 0.078,
       0, 0.078, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Bicarbonato' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 0.00, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Bicarbonato' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Caldo de Carne 1kg (Molhos e Temperos, UN): 5.000 x R$ 10.2
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 5.000, 5.000, '2026-09-28',
       '2027-03-27', 10.200000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Caldo de Carne 1kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 5.000,
       0, 5.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Caldo de Carne 1kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 51.00, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Caldo de Carne 1kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Caldo de galinha 1kg (Molhos e Temperos, UN): 2.000 x R$ 11.2
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 2.000, 2.000, '2026-09-28',
       '2027-03-27', 11.200000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Caldo de galinha 1kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 2.000,
       0, 2.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Caldo de galinha 1kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 22.40, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Caldo de galinha 1kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Canela Moida 500 g (Molhos e Temperos, UN): 0.438 x R$ 19.99
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 0.438, 0.438, '2026-09-28',
       '2027-03-27', 19.990000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Canela Moida 500 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 0.438,
       0, 0.438, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Canela Moida 500 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 8.76, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Canela Moida 500 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Extrato de Tomate 2 Kg (Molhos e Temperos, UN): 18.000 x R$ 22.115385
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 18.000, 18.000, '2026-09-28',
       '2027-03-27', 22.115385, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Extrato de Tomate 2 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 18.000,
       0, 18.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Extrato de Tomate 2 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 398.08, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Extrato de Tomate 2 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Folha de Louro 250g (Molhos e Temperos, UN): 0.324 x R$ 20.0
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 0.324, 0.324, '2026-09-28',
       '2027-03-27', 20.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Folha de Louro 250g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 0.324,
       0, 0.324, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Folha de Louro 250g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 6.48, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Folha de Louro 250g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Gergelim branco 150 g (Molhos e Temperos, UN): 3.000 x R$ 8.0
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 3.000, 3.000, '2026-09-28',
       '2027-03-27', 8.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Gergelim branco 150 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 3.000,
       0, 3.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Gergelim branco 150 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 24.00, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Gergelim branco 150 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Gergelim preto 150 g (Molhos e Temperos, UN): 3.000 x R$ 20.0
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 3.000, 3.000, '2026-09-28',
       '2027-03-27', 20.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Gergelim preto 150 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 3.000,
       0, 3.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Gergelim preto 150 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 60.00, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Gergelim preto 150 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Ketchup (Molhos e Temperos, UN): 2.148 x R$ 84.2
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 2.148, 2.148, '2026-09-28',
       '2027-03-27', 84.200000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Ketchup' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 2.148,
       0, 2.148, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Ketchup' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 180.86, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Ketchup' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Maionese - 1,02 Kg (Molhos e Temperos, UN): 14.000 x R$ 18.447041
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 14.000, 14.000, '2026-09-28',
       '2027-03-27', 18.447041, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Maionese - 1,02 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 14.000,
       0, 14.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Maionese - 1,02 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 258.26, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Maionese - 1,02 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Molho de pimenta - 900mL (Molhos e Temperos, UN): 6.000 x R$ 10.1425
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 6.000, 6.000, '2026-09-28',
       '2027-03-27', 10.142500, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Molho de pimenta - 900mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 6.000,
       0, 6.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Molho de pimenta - 900mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 60.86, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Molho de pimenta - 900mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Oregano (Molhos e Temperos, KG): 2.000 x R$ 24.0
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 2.000, 2.000, '2026-09-28',
       '2027-03-27', 24.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Oregano' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 2.000,
       0, 2.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Oregano' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 48.00, 'KG',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Oregano' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Pimenta biquinho - 3,02 kg (Molhos e Temperos, UN): 3.450 x R$ 21.63
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 3.450, 3.450, '2026-09-28',
       '2027-03-27', 21.630000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimenta biquinho - 3,02 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 3.450,
       0, 3.450, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Pimenta biquinho - 3,02 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 74.62, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Pimenta biquinho - 3,02 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Pimenta de bode (Molhos e Temperos, KG): 1.000 x R$ 10.0
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 1.000, 1.000, '2026-09-28',
       '2027-03-27', 10.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimenta de bode' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 1.000,
       0, 1.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Pimenta de bode' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 10.00, 'KG',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Pimenta de bode' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Pimenta de cheiro (Molhos e Temperos, KG): 0.402 x R$ 15.691468
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 0.402, 0.402, '2026-09-28',
       '2027-03-27', 15.691468, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pimenta de cheiro' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 0.402,
       0, 0.402, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Pimenta de cheiro' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 6.31, 'KG',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Pimenta de cheiro' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Sal Grosso 1 Kg (Molhos e Temperos, UN): 24.000 x R$ 2.89
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 24.000, 24.000, '2026-09-28',
       '2027-03-27', 2.890000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Sal Grosso 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 24.000,
       0, 24.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Sal Grosso 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 69.36, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Sal Grosso 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Sal refinado 1 Kg (Molhos e Temperos, UN): 34.000 x R$ 2.29
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 34.000, 34.000, '2026-09-28',
       '2027-03-27', 2.290000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Sal refinado 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 34.000,
       0, 34.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Sal refinado 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 77.86, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Sal refinado 1 Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Azeite de Oliva extra virgem- 500mL (Não-perecíveis, UN): 2.000 x R$ 34.9
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 2.000, 2.000, '2026-09-28',
       '2027-09-28', 34.900000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Azeite de Oliva extra virgem- 500mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 2.000,
       0, 2.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Azeite de Oliva extra virgem- 500mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 69.80, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Azeite de Oliva extra virgem- 500mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Azeite de dendê - 900mL (Não-perecíveis, UN): 8.000 x R$ 13.65
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 8.000, 8.000, '2026-09-28',
       '2027-09-28', 13.650000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Azeite de dendê - 900mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 8.000,
       0, 8.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Azeite de dendê - 900mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 109.20, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Azeite de dendê - 900mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Gelatina sem sabor 24G (Não-perecíveis, UN): 4.000 x R$ 4.6125
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 4.000, 4.000, '2026-09-28',
       '2027-09-28', 4.612500, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Gelatina sem sabor 24G' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 4.000,
       0, 4.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Gelatina sem sabor 24G' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 18.45, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Gelatina sem sabor 24G' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Oleo MISTO - 500mL (Não-perecíveis, UN): 7.000 x R$ 10.99
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 7.000, 7.000, '2026-09-28',
       '2027-09-28', 10.990000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Oleo MISTO - 500mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 7.000,
       0, 7.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Oleo MISTO - 500mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 76.93, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Oleo MISTO - 500mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Óleo de Soja - 900mL (Não-perecíveis, UN): 40.000 x R$ 5.489521
-- validade 2027-09-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 40.000, 40.000, '2026-09-28',
       '2027-09-28', 5.489521, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Óleo de Soja - 900mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 40.000,
       0, 40.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Óleo de Soja - 900mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 219.58, 'UN',
       '2027-09-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Óleo de Soja - 900mL' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Ovos - cartela de 30un (Ovos, UN): 7.000 x R$ 15.899987
-- validade 2026-10-28 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 7.000, 7.000, '2026-09-28',
       '2026-10-28', 15.899987, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Ovos - cartela de 30un' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 7.000,
       0, 7.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Ovos - cartela de 30un' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 111.30, 'UN',
       '2026-10-28'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Ovos - cartela de 30un' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Biscoito Maisena (Padaria e Confeitaria, KG): 2.324 x R$ 7.1
-- validade 2026-10-05 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 2.324, 2.324, '2026-09-28',
       '2026-10-05', 7.100000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Biscoito Maisena' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 2.324,
       0, 2.324, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Biscoito Maisena' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 16.50, 'KG',
       '2026-10-05'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Biscoito Maisena' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Chocolate em barra - 1,01 kg (Padaria e Confeitaria, UN): 1.066 x R$ 100.0
-- validade 2026-10-05 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 1.066, 1.066, '2026-09-28',
       '2026-10-05', 100.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Chocolate em barra - 1,01 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 1.066,
       0, 1.066, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Chocolate em barra - 1,01 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 106.60, 'UN',
       '2026-10-05'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Chocolate em barra - 1,01 kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Farinha de Rosca 5Kg (Padaria e Confeitaria, UN): 0.814 x R$ 6.785
-- validade 2026-10-05 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 0.814, 0.814, '2026-09-28',
       '2026-10-05', 6.785000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Farinha de Rosca 5Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 0.814,
       0, 0.814, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Farinha de Rosca 5Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 5.52, 'UN',
       '2026-10-05'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Farinha de Rosca 5Kg' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Pão de forma 450 g (Padaria e Confeitaria, UN): 5.196 x R$ 6.15
-- validade 2026-10-05 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 5.196, 5.196, '2026-09-28',
       '2026-10-05', 6.150000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Pão de forma 450 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 5.196,
       0, 5.196, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Pão de forma 450 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 31.96, 'UN',
       '2026-10-05'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Pão de forma 450 g' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Camarão (Peixes e Frutos do Mar, KG): 1.394 x R$ 39.9
-- validade 2026-09-30 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 1.394, 1.394, '2026-09-28',
       '2026-09-30', 39.900000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Camarão' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 1.394,
       0, 1.394, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Camarão' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 55.62, 'KG',
       '2026-09-30'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Camarão' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Peixe -Dourada (Peixes e Frutos do Mar, KG): 4.811 x R$ 26.9
-- validade 2026-09-30 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 4.811, 4.811, '2026-09-28',
       '2026-09-30', 26.900000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Peixe -Dourada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 4.811,
       0, 4.811, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Peixe -Dourada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 129.42, 'KG',
       '2026-09-30'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Peixe -Dourada' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Polpa de Pequi (Polpas e Doces, UN): 1.000 x R$ 7.0
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 1.000, 1.000, '2026-09-28',
       '2027-03-27', 7.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de Pequi' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 1.000,
       0, 1.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Polpa de Pequi' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 7.00, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Polpa de Pequi' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Polpa de abacaxi (Polpas e Doces, UN): 10.000 x R$ 1.89
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 10.000, 10.000, '2026-09-28',
       '2027-03-27', 1.890000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de abacaxi' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 10.000,
       0, 10.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Polpa de abacaxi' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 18.90, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Polpa de abacaxi' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Polpa de acerola (Polpas e Doces, UN): 8.000 x R$ 1.89
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 8.000, 8.000, '2026-09-28',
       '2027-03-27', 1.890000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de acerola' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 8.000,
       0, 8.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Polpa de acerola' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 15.12, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Polpa de acerola' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Polpa de cajá (Polpas e Doces, UN): 9.000 x R$ 1.59
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 9.000, 9.000, '2026-09-28',
       '2027-03-27', 1.590000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de cajá' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 9.000,
       0, 9.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Polpa de cajá' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 14.31, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Polpa de cajá' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Polpa de cajú (Polpas e Doces, UN): 9.000 x R$ 1.59
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 9.000, 9.000, '2026-09-28',
       '2027-03-27', 1.590000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de cajú' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 9.000,
       0, 9.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Polpa de cajú' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 14.31, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Polpa de cajú' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Polpa de goiaba (Polpas e Doces, UN): 3.000 x R$ 0.0
-- validade 2027-03-27 | SEM CUSTO (sem preco na planilha)

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 3.000, 3.000, '2026-09-28',
       '2027-03-27', 0.000000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de goiaba' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 3.000,
       0, 3.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Polpa de goiaba' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 0.00, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Polpa de goiaba' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Polpa de morango (Polpas e Doces, UN): 1.000 x R$ 3.39
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 1.000, 1.000, '2026-09-28',
       '2027-03-27', 3.390000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de morango' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 1.000,
       0, 1.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Polpa de morango' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 3.39, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Polpa de morango' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- Polpa de uva (Polpas e Doces, UN): 5.000 x R$ 3.39
-- validade 2027-03-27 | com custo

INSERT INTO estoq_v2.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, 5.000, 5.000, '2026-09-28',
       '2027-03-27', 3.390000, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
 WHERE pr.nome = 'Polpa de uva' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.lotes l WHERE l.produto_id = pr.id);

INSERT INTO estoq_v2.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, (SELECT u.id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), 5.000,
       0, 5.000, 'Carga inicial - planilha CMV Real dez/2023',
       (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
  FROM estoq_v2.produtos pr
  JOIN estoq_v2.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = 'Polpa de uva' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO estoq_v2.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, 16.95, 'UN',
       '2027-03-27'
  FROM estoq_v2.movimentacoes m
  JOIN estoq_v2.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = 'Polpa de uva' AND pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND NOT EXISTS (SELECT 1 FROM estoq_v2.entradas e WHERE e.id = m.id);

-- ================ codigo do lote, como o LoteService faz ================
UPDATE estoq_v2.lotes
   SET codigo = 'L-' || lpad(id::text, 6, '0')
 WHERE codigo LIKE 'PENDENTE-SEED-%'
   AND restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com');

-- ================ conferindo o que entrou ================
-- Saldo vem de sum(lotes.quantidade_atual); nao existe coluna de saldo em produtos.
SELECT count(*) FILTER (WHERE saldo > 0) AS com_saldo,
       count(*) AS total,
       round(sum(saldo)::numeric, 3) AS saldo_total,
       round(sum(valor)::numeric, 2) AS valor_total_estoque
  FROM (
    SELECT pr.id, coalesce(sum(l.quantidade_atual), 0) AS saldo,
           coalesce(sum(l.quantidade_atual * l.preco_unitario), 0) AS valor
      FROM estoq_v2.produtos pr
      LEFT JOIN estoq_v2.lotes l ON l.produto_id = pr.id AND l.ativo = true
     WHERE pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com') AND pr.ativo = true
     GROUP BY pr.id
  ) s;

-- Deve voltar 0 linhas: produto com lote e sem entrada (oumovemento sem entrada).
SELECT pr.nome AS produto_orfao
  FROM estoq_v2.lotes l
  JOIN estoq_v2.produtos pr ON pr.id = l.produto_id
  LEFT JOIN estoq_v2.entradas e ON e.id = (SELECT m.id FROM estoq_v2.movimentacoes m
                                           WHERE m.tipo = 'ENTRADA'
                                             AND m.lote_id = l.id LIMIT 1)
 WHERE pr.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   AND l.codigo NOT LIKE 'PENDENTE-SEED%'
   AND e.id IS NULL;

COMMIT;

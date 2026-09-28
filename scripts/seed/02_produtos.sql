-- =====================================================================
-- ETAPA 2 - produtos
-- Gerado por scripts/gerar_seed_sql.py -- nao editar a mao.
-- Gerado em 2026-09-28
-- Destino: estoq_v2
-- Loja: feitosapedrowin@gmail.com (restaurante_id resolvido em tempo de execucao)
-- =====================================================================
-- Arquivo: 02_produtos.sql
---- 182 produtos
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

-- Depende da etapa 1: o categoria_id e resolvido pelo nome da categoria.
-- Idempotente por nome dentro do tenant. A unidade ja vem resolvida no CSV --
-- embalagem com peso no nome e UN (1 saco de 5kg), nao KG. Ver
-- documentacao/PLANILHA_CMV_RESTAURANTE.md secao 4.

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Café em pó - 500g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Bebidas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Bebidas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Café em pó - 500g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Gelo', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Bebidas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Bebidas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Gelo' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Acem', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Acem' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Alcatra', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Alcatra' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Apresuntado', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Apresuntado' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Bacon fatiado', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Bacon fatiado' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Bacon pedaço', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Bacon pedaço' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Capa de contra-filé', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Capa de contra-filé' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Contra filé', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Contra filé' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Coração de galinha', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Coração de galinha' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Costela Gaucha', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Costela Gaucha' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Costela P A', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Costela P A' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Costela Suina', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Costela Suina' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Costelinha de Caranha', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Costelinha de Caranha' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Coxa e sobrecoxa', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Coxa e sobrecoxa' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Coxao mole', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Coxao mole' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Coxinha da asa', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Coxinha da asa' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Cupim', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Cupim' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Dobradinha', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Dobradinha' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Figado', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Figado' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Filé de Sassami', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Filé de Sassami' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Filé de peito de frango', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Filé de peito de frango' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Fraldinha', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Fraldinha' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Frango caipira', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Frango caipira' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Hondashi - 500 g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Hondashi - 500 g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Kibe', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Kibe' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Kit Feijoada preparado e pronto', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Kit Feijoada preparado e pronto' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Lagarto', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Lagarto' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Linguiça calabresa', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Linguiça calabresa' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Linguiça de Frango', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Linguiça de Frango' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Linguiça fina', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Linguiça fina' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Linguiça suina', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Linguiça suina' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Lombo suino', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Lombo suino' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Maminha', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Maminha' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Maça do Peito', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Maça do Peito' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Meio da asa', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Meio da asa' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Moela 1kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Moela 1kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Peito bovino', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Peito bovino' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Pernil', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Pernil' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Picanha', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Picanha' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Rabada', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Rabada' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Sobrecoxa - file de coxa/sobrecoxa', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Sobrecoxa - file de coxa/sobrecoxa' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Toucinho', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Carnes e Proteínas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Toucinho' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Batata palito 1 kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Congelados' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Congelados' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Batata palito 1 kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Mandioca congelada', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Congelados' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Congelados' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Mandioca congelada' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Massa de lasanha', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Congelados' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Congelados' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Massa de lasanha' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Catupiry - sachê 1,80 kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Descartáveis e Limpeza' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Descartáveis e Limpeza' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Catupiry - sachê 1,80 kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Sal sache', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Descartáveis e Limpeza' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Descartáveis e Limpeza' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Sal sache' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Azeitona Fatiada verde - 3,2kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Azeitona Fatiada verde - 3,2kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Azeitona preta 3,2 kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Azeitona preta 3,2 kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Azeitona sem caroço verde - 2kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Azeitona sem caroço verde - 2kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Azeitona verde fatiada -2Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Azeitona verde fatiada -2Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Ervilha enlatada - 195g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Ervilha enlatada - 195g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Milho enlatado - 195 g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Milho enlatado - 195 g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Palmito em conserva 800g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Enlatados e Conservas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Palmito em conserva 800g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Amido de milho 5Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Amido de milho 5Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Arroz 5 Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Arroz 5 Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Arroz Integral 1 Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Arroz Integral 1 Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Açúcar Cristalizado 5 Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Açúcar Cristalizado 5 Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Coco Ralado - 200g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Coco Ralado - 200g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Farinha de Trigo 5Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Farinha de Trigo 5Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Farinha de mandioca 20 kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Farinha de mandioca 20 kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Farinha de milho 500g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Farinha de milho 500g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Feijão Branco 1 Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Feijão Branco 1 Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Feijão Carioca 1 Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Feijão Carioca 1 Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Feijão preto Cristal 1 Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Feijão preto Cristal 1 Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Flocão de milho - 500g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Flocão de milho - 500g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Fuba de milho 500g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Fuba de milho 500g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Grao de bico - 500 g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Grao de bico - 500 g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Milho - Bandeja', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Milho - Bandeja' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Trigo para quibe 500g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Grãos e Farinhas' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Trigo para quibe 500g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Abacaxi', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Abacaxi' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Abobora Kabutia', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Abobora Kabutia' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Abobora Moranga', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Abobora Moranga' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Abobrinha', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Abobrinha' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Alho', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Alho' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Alho poró', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Alho poró' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Aneis de cebola', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Aneis de cebola' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Banana da terra', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Banana da terra' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Banana prata', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Banana prata' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Batata doce', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Batata doce' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Batata inglesa', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Batata inglesa' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Batata palha - 800g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Batata palha - 800g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Beringela', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Beringela' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Beterraba', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Beterraba' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Brocolis', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Brocolis' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Cebola', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Cebola' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Cebola Roxa', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Cebola Roxa' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Cenoura', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Cenoura' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Chuchu', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Chuchu' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Cogumelo Fatiado - 3,2 kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Cogumelo Fatiado - 3,2 kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Couve flor', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Couve flor' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Guariroba', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Guariroba' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Jilo', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Jilo' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Limão taiti', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Limão taiti' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Manga', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Manga' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Maça fuji', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Maça fuji' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Morango', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Morango' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Pepino', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Pepino' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Pequi 1 kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Pequi 1 kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Pimentao Verde', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Pimentao Verde' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Pimentao amarelo', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Pimentao amarelo' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Pimentao vermelho', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Pimentao vermelho' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Quiabo', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Quiabo' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Repolho roxo', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Repolho roxo' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Repolho verde', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Repolho verde' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Tomate', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Tomate' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Tomate cereja', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Tomate cereja' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Tomate seco', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Tomate seco' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Uva Thompson 500g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Uva Thompson 500g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Uva passa preta 1kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Uva passa preta 1kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Vagem', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Hortifruti' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Vagem' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Creme de leite 1,030 Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Creme de leite 1,030 Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Leite Condensado 395g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Leite Condensado 395g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Leite Integral 1L', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Leite Integral 1L' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Leite de coco - 500mL', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Leite de coco - 500mL' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Margarina 15Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Margarina 15Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Mussarela barra', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Mussarela barra' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Mussarela fatiada', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Mussarela fatiada' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Queijo Coalho', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Queijo Coalho' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Queijo Fresco', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Queijo Fresco' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Queijo Trança', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Queijo Trança' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Queijo ralado - 760 g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Queijo ralado - 760 g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Requeijão cremoso 1,8kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Laticínios' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Requeijão cremoso 1,8kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Macarrão ESP 8 - pct 500g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Massas e Macarrão' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Massas e Macarrão' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Macarrão ESP 8 - pct 500g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Macarrão FUR. 5 - pct 500g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Massas e Macarrão' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Massas e Macarrão' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Macarrão FUR. 5 - pct 500g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Macarrão NINHO - pct 500g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Massas e Macarrão' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Massas e Macarrão' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Macarrão NINHO - pct 500g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Macarrão PARAFUSO - 500g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Massas e Macarrão' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Massas e Macarrão' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Macarrão PARAFUSO - 500g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Massa de pastel 200 g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Massas e Macarrão' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Massas e Macarrão' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Massa de pastel 200 g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Achocolatado 750 g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Achocolatado 750 g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Adoçante Zero - 200mL', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Adoçante Zero - 200mL' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Açafrão 0,250 g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Açafrão 0,250 g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Bicarbonato', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Bicarbonato' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Caldo de Carne 1kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Caldo de Carne 1kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Caldo de galinha 1kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Caldo de galinha 1kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Canela Moida 500 g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Canela Moida 500 g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Colorau 0,250 g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Colorau 0,250 g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Extrato de Tomate 2 Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Extrato de Tomate 2 Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Folha de Louro 250g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Folha de Louro 250g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Gergelim branco 150 g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Gergelim branco 150 g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Gergelim preto 150 g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Gergelim preto 150 g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Ketchup', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Ketchup' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Maionese - 1,02 Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Maionese - 1,02 Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Molho Shoyu 3L', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Molho Shoyu 3L' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Molho alho 215ml', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Molho alho 215ml' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Molho barbecue 3kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Molho barbecue 3kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Molho de pimenta - 900mL', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Molho de pimenta - 900mL' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Molho salada hellmanns rose 210ml', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Molho salada hellmanns rose 210ml' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Oregano', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Oregano' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Pimenta Calabresa', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Pimenta Calabresa' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Pimenta biquinho - 3,02 kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Pimenta biquinho - 3,02 kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Pimenta de bode', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Pimenta de bode' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Pimenta de cheiro', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Pimenta de cheiro' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Pimenta do reino', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Pimenta do reino' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Sal Grosso 1 Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Sal Grosso 1 Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Sal refinado 1 Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Molhos e Temperos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Sal refinado 1 Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Azeite de Oliva extra virgem- 500mL', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Azeite de Oliva extra virgem- 500mL' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Azeite de dendê - 900mL', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Azeite de dendê - 900mL' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Gelatina sem sabor 24G', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Gelatina sem sabor 24G' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Oleo MISTO - 500mL', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Oleo MISTO - 500mL' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Vinagre de Álcool - 5L', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Vinagre de Álcool - 5L' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Óleo de Soja - 900mL', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Não-perecíveis' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Óleo de Soja - 900mL' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Ovos - cartela de 30un', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Ovos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Ovos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Ovos - cartela de 30un' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Ovos de codorna', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Ovos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Ovos' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Ovos de codorna' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Biscoito Maisena', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Padaria e Confeitaria' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Padaria e Confeitaria' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Biscoito Maisena' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Chocolate em barra - 1,01 kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Padaria e Confeitaria' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Padaria e Confeitaria' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Chocolate em barra - 1,01 kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Farinha de Rosca 5Kg', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Padaria e Confeitaria' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Padaria e Confeitaria' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Farinha de Rosca 5Kg' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Pão de forma 450 g', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Padaria e Confeitaria' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Padaria e Confeitaria' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Pão de forma 450 g' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Bacalhau', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Peixes e Frutos do Mar' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Peixes e Frutos do Mar' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Bacalhau' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Camarão', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Peixes e Frutos do Mar' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Peixes e Frutos do Mar' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Camarão' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Filé de peixe pangasius', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Peixes e Frutos do Mar' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Peixes e Frutos do Mar' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Filé de peixe pangasius' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Peixe -Dourada', 'KG',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Peixes e Frutos do Mar' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Peixes e Frutos do Mar' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Peixe -Dourada' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Polpa de Pequi', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Polpa de Pequi' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Polpa de abacaxi', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Polpa de abacaxi' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Polpa de abacaxi com hortela', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Polpa de abacaxi com hortela' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Polpa de acerola', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Polpa de acerola' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Polpa de cajá', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Polpa de cajá' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Polpa de cajú', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Polpa de cajú' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Polpa de goiaba', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Polpa de goiaba' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Polpa de maracujá', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Polpa de maracujá' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Polpa de morango', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Polpa de morango' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

INSERT INTO estoq_v2.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT 'Polpa de uva', 'UN',
       (SELECT c.id FROM estoq_v2.categorias c
         WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')),
       NULL, (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'), now(), true, 0
 WHERE EXISTS (SELECT 1 FROM estoq_v2.categorias c
                WHERE c.nome = 'Polpas e Doces' AND c.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com'))
   AND NOT EXISTS (
       SELECT 1 FROM estoq_v2.produtos x
        WHERE x.nome = 'Polpa de uva' AND x.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com')
   );

-- Conferindo o que entrou
SELECT count(*) AS produtos,
       count(DISTINCT unidade_medida) AS unidades,
       count(DISTINCT categoria_id) AS categorias
  FROM estoq_v2.produtos
 WHERE restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com') AND ativo = true;

SELECT p.nome, p.unidade_medida, c.nome AS categoria
  FROM estoq_v2.produtos p
  JOIN estoq_v2.categorias c ON c.id = p.categoria_id
 WHERE p.restaurante_id = (SELECT u.restaurante_id FROM estoq_v2.usuarios u WHERE lower(u.email) = 'feitosapedrowin@gmail.com') AND p.categoria_id IS NULL;

COMMIT;

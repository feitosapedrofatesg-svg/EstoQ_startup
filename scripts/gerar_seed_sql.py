#!/usr/bin/env python3
"""Gera os SQL de carga do EstoQ a partir do catalogo extraido da planilha de CMV.

Le `documentacao/catalogo-cmv-dezembro-2023.csv` e escreve arquivos .sql para
serem colados no SQL editor do Neon (ou rodados com psql).

    python scripts/gerar_seed_sql.py                 # gera os arquivos
    python scripts/gerar_seed_sql.py --dry-run       # gera com ROLLBACK

Por que SQL direto e nao a API: a carga e de ~900 linhas em 5 tabelas. Pela API
seriam ~650 chamadas HTTP sequenciais, nao-atomicas (uma falha no meio deixa o
banco pela metade). Em SQL tudo cai numa unica transacao -- ou entra tudo, ou
nada -- e o dry-run e um ROLLBACK, que valida contra o schema real de producao.

Cada arquivo comeca com um guard que ABORTA se o schema nao bater com o que o
backend espera, ou se o usuario alvo nao tiver um restaurante proprio. Falha
fechada: nao grava nada em vez de gravar errado.

Requer: `restaurante_id` resolvido por e-mail em tempo de execucao (ver
EMAIL_ALVO). Nada de senha e nada de ID fixo no arquivo.
"""
import argparse
import csv
import datetime
import os
import re
import sys
from decimal import Decimal, ROUND_HALF_UP

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CSV_CATALOGO = os.path.join(RAIZ, "documentacao", "catalogo-cmv-dezembro-2023.csv")
DIR_SAIDA = os.path.join(RAIZ, "scripts", "seed")
SCHEMA = "estoq_v2"

# E-mail do dono da loja. O SQL resolve o restaurante_id em tempo de execucao,
# entao nao ha ID nem credencial fixos no arquivo.
EMAIL_ALVO = "feitosapedrowin@gmail.com"

# Validade por categoria, em dias. A planilha de CMV nao tem validade (ela
# controla por contagem de compras/consumo), entao isto e um padrao de mercado
# por tipo de mercadoria. Nao-perecivel e descartavel recebem valor alto para
# nao poluir a tela de alertas com vencimento falso.
VALIDADE_DIAS = {
    "Carnes e Proteínas": 3,
    "Peixes e Frutos do Mar": 2,
    "Hortifruti": 5,
    "Ovos": 30,
    "Laticínios": 21,
    "Padaria e Confeitaria": 7,
    "Congelados": 120,
    "Massas e Macarrão": 365,
    "Descartáveis e Limpeza": 3650,
    "Molhos e Temperos": 180,
    "Enlatados e Conservas": 720,
    "Polpas e Doces": 180,
    "Bebidas": 180,
    "Não-perecíveis": 365,
    "Grãos e Farinhas": 365,
}
VALIDIDADE_PADRAO = 180

# Multiplicadores dos limites de estoque, em semanas de consumo.
# min = 1,5 semana de consumo cobre a janela entre compras; max = 1,5x o saldo
# atual. Mantem min <= medio <= max, como ParametroEstoqueValidation exige.
FATOR_MIN = Decimal("1.5")
FATOR_MAX = Decimal("1.5")
DIAS_PERIODO = 28  # 4 semanas da planilha

# Colunas que cada tabela precisa ter para o seed funcionar. O guard aborta se
# alguma faltar -- foi o que evitou descobrir erro de schema no meio da carga.
COLUNAS_ESPERADAS = {
    "restaurantes": ["id", "nome", "ativo"],
    "usuarios": ["id", "email", "perfil", "restaurante_id"],
    "categorias": ["id", "nome", "descricao", "restaurante_id",
                   "data_hora_criacao", "ativo", "version"],
    "produtos": ["id", "nome", "unidade_medida", "categoria_id", "codigo_barras",
                 "restaurante_id", "data_hora_criacao", "ativo", "version"],
    "parametros_estoque": ["id", "produto_id", "tempo_reposicao_dias",
                           "periodo_analise_dias", "consumo_medio_diario",
                           "estoque_minimo", "estoque_medio", "estoque_maximo",
                           "dias_alerta_vencimento", "data_atualizacao",
                           "restaurante_id", "data_hora_criacao", "ativo", "version"],
    "lotes": ["id", "codigo", "produto_id", "quantidade_inicial", "quantidade_atual",
              "data_entrada", "data_validade", "preco_unitario",
              "restaurante_id", "data_hora_criacao", "ativo", "version"],
    "movimentacoes": ["id", "tipo", "data_hora", "produto_id", "lote_id", "usuario_id",
                      "quantidade", "quantidade_anterior", "quantidade_posterior",
                      "observacao", "restaurante_id", "data_hora_criacao",
                      "ativo", "version"],
    # JOINED inheritance: a tabela filha carrega SO os atributos proprios da
    # classe. restaurante_id, data_hora_criacao, ativo e version ficam na tabela
    # pai `movimentacoes`. Confirmado contra o schema gerado pelo Hibernate.
    "entradas": ["id", "valor_total_pago", "unidade_compra", "data_validade"],
}


def d(v, escala=3):
    """Decimal no texto, sem notacao cientifica e com a escala do banco."""
    if v is None or v == "":
        return None
    q = Decimal(1).scaleb(-escala)
    return str(Decimal(str(v)).quantize(q, rounding=ROUND_HALF_UP))


def txt(s):
    """Literal SQL, com aspa simples duplicada."""
    return "'" + str(s).replace("'", "''") + "'"


def data_br(dt):
    return dt.strftime("%Y-%m-%d")


# --------------------------------------------------------------------------
# guard
# --------------------------------------------------------------------------

def guard_schema():
    cols = [f"'{SCHEMA}.{t}.{c}'" for t, cs in COLUNAS_ESPERADAS.items() for c in cs]
    lista = ",\n            ".join(", ".join(cols[i:i + 3]) for i in range(0, len(cols), 3))
    return f"""-- Falha fechada: se o schema de producao nao bater com o que o backend
-- valida (DDL_AUTO=validate), aborta antes de gravar qualquer linha.
DO $$
DECLARE
    faltando text;
BEGIN
    SELECT string_agg(format('%I.%I', split_part(t, '.', 2), split_part(t, '.', 3)), ', ')
      INTO faltando
      FROM unnest(ARRAY[
            {lista}
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
"""


def guard_usuario():
    return f"""-- O TenantEntity usa @TenantId: linhas com restaurante_id nulo nao sao
-- filtradas por tenant, ou seja, aparecem para TODOS os restaurantes. Por isso
-- o seed recusa usuario PLATAFORMA ou sem loja propria.
DO $$
DECLARE
    v_restaurante_id bigint;
    v_perfil text;
BEGIN
    SELECT u.restaurante_id, u.perfil
      INTO v_restaurante_id, v_perfil
      FROM {SCHEMA}.usuarios u
     WHERE lower(u.email) = {txt(EMAIL_ALVO.lower())};

    IF NOT FOUND THEN
        RAISE EXCEPTION 'usuario nao encontrado: % -- seed abortado', {txt(EMAIL_ALVO)};
    END IF;
    IF v_restaurante_id IS NULL THEN
        RAISE EXCEPTION
            'usuario % tem restaurante_id nulo (perfil %) -- seed abortado',
            {txt(EMAIL_ALVO)}, v_perfil;
    END IF;
    IF v_perfil = 'PLATAFORMA' THEN
        RAISE EXCEPTION
            'perfil PLATAFORMA enxerga a base inteira; semear aqui vaza dado entre restaurantes';
    END IF;
    RAISE NOTICE 'semeando no restaurante_id % (perfil %)', v_restaurante_id, v_perfil;
END $$;
"""


# Expressoes que resolvem o tenant e o usuario dentro de cada statement.
RID = (f"(SELECT u.restaurante_id FROM {SCHEMA}.usuarios u"
       f" WHERE lower(u.email) = {txt(EMAIL_ALVO.lower())})")
UID = (f"(SELECT u.id FROM {SCHEMA}.usuarios u"
       f" WHERE lower(u.email) = {txt(EMAIL_ALVO.lower())})")


def cabecalho(titulo, arquivo, hoje, extra=""):
    return f"""-- =====================================================================
-- {titulo}
-- Gerado por scripts/gerar_seed_sql.py -- nao editar a mao.
-- Gerado em {hoje}
-- Destino: {SCHEMA}
-- Loja: {EMAIL_ALVO} (restaurante_id resolvido em tempo de execucao)
-- =====================================================================
-- Arquivo: {arquivo}
--{extra}
-- Como rodar: cole no SQL editor do Neon. Para revisar sem gravar, troque o
-- COMMIT final por ROLLBACK (ou use a variante -dryrun).
-- =====================================================================

"""


def fecha(commit):
    return "\nCOMMIT;\n" if commit else "\nROLLBACK;  -- dry-run: nada foi gravado\n"


# --------------------------------------------------------------------------
# etapas
# --------------------------------------------------------------------------

def etapa_categorias(produtos, hoje, commit):
    nomes = sorted({p["categoria"] for p in produtos})
    out = [cabecalho("ETAPA 1 - categorias", "01_categorias.sql", hoje,
                     f"-- {len(nomes)} categorias")]
    out.append("BEGIN;\n\n")
    out.append(guard_schema())
    out.append(guard_usuario())
    out.append(f"\n-- {len(nomes)} categorias. Idempotente por nome dentro do tenant.\n")
    for nome in nomes:
        out.append(f"""
INSERT INTO {SCHEMA}.categorias (nome, descricao, restaurante_id,
                                data_hora_criacao, ativo, version)
SELECT {txt(nome)}, {txt('Catalogo importado da planilha de CMV Real (dez/2023)')},
       {RID}, now(), true, 0
 WHERE NOT EXISTS (
    SELECT 1 FROM {SCHEMA}.categorias c
     WHERE c.nome = {txt(nome)} AND c.restaurante_id = {RID}
 );
""")
    out.append(f"""
-- Conferindo o que entrou
SELECT c.nome, count(p.id) AS produtos
  FROM {SCHEMA}.categorias c
  LEFT JOIN {SCHEMA}.produtos p ON p.categoria_id = c.id AND p.ativo = true
 WHERE c.restaurante_id = {RID}
 GROUP BY c.nome
 ORDER BY c.nome;
""")
    out.append(fecha(commit))
    return "".join(out), len(nomes)


def etapa_produtos(produtos, hoje, commit):
    out = [cabecalho("ETAPA 2 - produtos", "02_produtos.sql", hoje,
                     f"-- {len(produtos)} produtos")]
    out.append("BEGIN;\n\n")
    out.append(guard_schema())
    out.append(guard_usuario())
    out.append(f"""
-- Depende da etapa 1: o categoria_id e resolvido pelo nome da categoria.
-- Idempotente por nome dentro do tenant. A unidade ja vem resolvida no CSV --
-- embalagem com peso no nome e UN (1 saco de 5kg), nao KG. Ver
-- documentacao/PLANILHA_CMV_RESTAURANTE.md secao 4.
""")
    for p in produtos:
        out.append(f"""
INSERT INTO {SCHEMA}.produtos (nome, unidade_medida, categoria_id, codigo_barras,
                              restaurante_id, data_hora_criacao, ativo, version)
SELECT {txt(p['nome'])}, {txt(p['unidade_estoque'])},
       (SELECT c.id FROM {SCHEMA}.categorias c
         WHERE c.nome = {txt(p['categoria'])} AND c.restaurante_id = {RID}),
       NULL, {RID}, now(), true, 0
 WHERE EXISTS (SELECT 1 FROM {SCHEMA}.categorias c
                WHERE c.nome = {txt(p['categoria'])} AND c.restaurante_id = {RID})
   AND NOT EXISTS (
       SELECT 1 FROM {SCHEMA}.produtos x
        WHERE x.nome = {txt(p['nome'])} AND x.restaurante_id = {RID}
   );
""")
    out.append(f"""
-- Conferindo o que entrou
SELECT count(*) AS produtos,
       count(DISTINCT unidade_medida) AS unidades,
       count(DISTINCT categoria_id) AS categorias
  FROM {SCHEMA}.produtos
 WHERE restaurante_id = {RID} AND ativo = true;

SELECT p.nome, p.unidade_medida, c.nome AS categoria
  FROM {SCHEMA}.produtos p
  JOIN {SCHEMA}.categorias c ON c.id = p.categoria_id
 WHERE p.restaurante_id = {RID} AND p.categoria_id IS NULL;
""")
    out.append(fecha(commit))
    return "".join(out), len(produtos)


def limites(p, estoque):
    """(min, medio, max) respeitando min <= medio <= max, como o backend exige."""
    consumo_sem = Decimal(p["consumo_4_semanas"] or 0) / 4
    if consumo_sem > 0:
        minimo = consumo_sem * FATOR_MIN
    else:
        minimo = estoque * Decimal("0.5")
    minimo = min(minimo, estoque)
    medio = estoque
    maximo = max(medio * FATOR_MAX, medio)
    return (d(minimo), d(medio), d(maximo)), consumo_sem


def etapa_estoque(produtos, hoje, commit):
    com_estoque = [p for p in produtos if Decimal(p["estoque_inicial"] or 0) > 0]
    dt_hoje = datetime.date.fromisoformat(hoje)
    out = [cabecalho("ETAPA 3 - estoque", "03_estoque.sql", hoje,
                     f"-- parametros + lotes + entradas + movimentacoes "
                     f"para {len(com_estoque)} produtos com saldo")]
    out.append("BEGIN;\n\n")
    out.append(guard_schema())
    out.append(guard_usuario())
    out.append(f"""
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
""")

    out.append(f"\n\n-- ================ parametros_estoque (todos os "
               f"{len(produtos)} produtos) ================\n")
    for p in produtos:
        estoque = Decimal(p["estoque_inicial"] or 0)
        (mn, md, mx), _ = limites(p, estoque)
        consumo_diario = (Decimal(p["consumo_4_semanas"] or 0) / DIAS_PERIODO)
        out.append(f"""
INSERT INTO {SCHEMA}.parametros_estoque
    (produto_id, tempo_reposicao_dias, periodo_analise_dias, consumo_medio_diario,
     estoque_minimo, estoque_medio, estoque_maximo, dias_alerta_vencimento,
     data_atualizacao, restaurante_id, data_hora_criacao, ativo, version)
SELECT pr.id, 7, 30, {d(consumo_diario)}, {mn}, {md}, {mx}, 7,
       now(), {RID}, now(), true, 0
  FROM {SCHEMA}.produtos pr
 WHERE pr.nome = {txt(p['nome'])} AND pr.restaurante_id = {RID}
   AND NOT EXISTS (SELECT 1 FROM {SCHEMA}.parametros_estoque pe
                    WHERE pe.produto_id = pr.id);
""")

    out.append(f"\n\n-- ================ lotes + entradas + movimentacoes "
               f"({len(com_estoque)} produtos com saldo) ================\n")
    for p in com_estoque:
        estoque = Decimal(p["estoque_inicial"] or 0)
        qtd = d(estoque)
        preco = p["preco_medio_compra"] or "0"
        tem_custo = Decimal(preco) > 0
        valor_total = d(estoque * Decimal(preco), 2)
        sem_custo = "false" if tem_custo else "true"
        validade = data_br(dt_hoje + datetime.timedelta(
            days=VALIDADE_DIAS.get(p["categoria"], VALIDIDADE_PADRAO)))
        nome = p["nome"]

        out.append(f"""
-- {nome} ({p['categoria']}, {p['unidade_estoque']}): {qtd} x R$ {preco or '0,00'}
-- validade {validade} | {"com custo" if tem_custo else "SEM CUSTO (sem preco na planilha)"}

INSERT INTO {SCHEMA}.lotes
    (codigo, produto_id, quantidade_inicial, quantidade_atual, data_entrada,
     data_validade, preco_unitario, restaurante_id, data_hora_criacao, ativo, version)
SELECT 'PENDENTE-SEED-' || pr.id::text, pr.id, {qtd}, {qtd}, {txt(hoje)},
       {txt(validade)}, {d(preco, 6)}, {RID}, now(), true, 0
  FROM {SCHEMA}.produtos pr
 WHERE pr.nome = {txt(nome)} AND pr.restaurante_id = {RID}
   AND NOT EXISTS (SELECT 1 FROM {SCHEMA}.lotes l WHERE l.produto_id = pr.id);

INSERT INTO {SCHEMA}.movimentacoes
    (tipo, data_hora, produto_id, lote_id, usuario_id, quantidade,
     quantidade_anterior, quantidade_posterior, observacao,
     restaurante_id, data_hora_criacao, ativo, version)
SELECT 'ENTRADA', now(), pr.id, l.id, {UID}, {qtd},
       0, {qtd}, {txt('Carga inicial - planilha CMV Real dez/2023')},
       {RID}, now(), true, 0
  FROM {SCHEMA}.produtos pr
  JOIN {SCHEMA}.lotes l ON l.produto_id = pr.id
 WHERE pr.nome = {txt(nome)} AND pr.restaurante_id = {RID}
   AND NOT EXISTS (SELECT 1 FROM {SCHEMA}.movimentacoes m
                    WHERE m.produto_id = pr.id AND m.tipo = 'ENTRADA');

-- Tabela filha do JOINED: precisa do MESMO id da tabela pai. Nao repete
-- restaurante_id/ativo/version/data_hora_criacao -- herdam de `movimentacoes`.
INSERT INTO {SCHEMA}.entradas (id, valor_total_pago, unidade_compra, data_validade)
SELECT m.id, {'0.00' if not tem_custo else valor_total}, {txt(p['unidade_estoque'])},
       {txt(validade)}
  FROM {SCHEMA}.movimentacoes m
  JOIN {SCHEMA}.produtos pr ON pr.id = m.produto_id
 WHERE m.tipo = 'ENTRADA'
   AND pr.nome = {txt(nome)} AND pr.restaurante_id = {RID}
   AND NOT EXISTS (SELECT 1 FROM {SCHEMA}.entradas e WHERE e.id = m.id);
""")

    out.append(f"""
-- ================ codigo do lote, como o LoteService faz ================
UPDATE {SCHEMA}.lotes
   SET codigo = 'L-' || lpad(id::text, 6, '0')
 WHERE codigo LIKE 'PENDENTE-SEED-%'
   AND restaurante_id = {RID};
""")
    out.append(f"""
-- ================ conferindo o que entrou ================
-- Saldo vem de sum(lotes.quantidade_atual); nao existe coluna de saldo em produtos.
SELECT count(*) FILTER (WHERE saldo > 0) AS com_saldo,
       count(*) AS total,
       round(sum(saldo)::numeric, 3) AS saldo_total,
       round(sum(valor)::numeric, 2) AS valor_total_estoque
  FROM (
    SELECT pr.id, coalesce(sum(l.quantidade_atual), 0) AS saldo,
           coalesce(sum(l.quantidade_atual * l.preco_unitario), 0) AS valor
      FROM {SCHEMA}.produtos pr
      LEFT JOIN {SCHEMA}.lotes l ON l.produto_id = pr.id AND l.ativo = true
     WHERE pr.restaurante_id = {RID} AND pr.ativo = true
     GROUP BY pr.id
  ) s;

-- Deve voltar 0 linhas: produto com lote e sem entrada (oumovemento sem entrada).
SELECT pr.nome AS produto_orfao
  FROM {SCHEMA}.lotes l
  JOIN {SCHEMA}.produtos pr ON pr.id = l.produto_id
  LEFT JOIN {SCHEMA}.entradas e ON e.id = (SELECT m.id FROM {SCHEMA}.movimentacoes m
                                           WHERE m.tipo = 'ENTRADA'
                                             AND m.lote_id = l.id LIMIT 1)
 WHERE pr.restaurante_id = {RID}
   AND l.codigo NOT LIKE 'PENDENTE-SEED%'
   AND e.id IS NULL;
""")
    out.append(fecha(commit))
    return "".join(out), len(com_estoque)


def etapa_verificar(hoje):
    return cabecalho("ETAPA 0 - verificacao (somente leitura)", "00_verificar.sql", hoje) + f"""-- Rode isto ANTES das etapas 1-3. Nao grava nada.
-- 1) O usuario alvo tem loja propria? (a resposta decide se o seed e seguro)
SELECT u.id, u.nome, u.perfil, u.restaurante_id, r.nome AS loja
  FROM {SCHEMA}.usuarios u
  LEFT JOIN {SCHEMA}.restaurantes r ON r.id = u.restaurante_id
 WHERE lower(u.email) = {txt(EMAIL_ALVO.lower())};

-- 2) O que ja existe hoje
SELECT (SELECT count(*) FROM {SCHEMA}.categorias WHERE restaurante_id = {RID}) AS categorias,
       (SELECT count(*) FROM {SCHEMA}.produtos   WHERE restaurante_id = {RID}) AS produtos,
       (SELECT count(*) FROM {SCHEMA}.lotes      WHERE restaurante_id = {RID}) AS lotes,
       (SELECT count(*) FROM {SCHEMA}.movimentacoes WHERE restaurante_id = {RID}) AS movimentacoes;

-- 3) O schema de producao tem as colunas que o seed usa?
DO $$
DECLARE
    faltando text;
BEGIN
    SELECT string_agg(format('%I.%I', split_part(t, '.', 2), split_part(t, '.', 3)), ', ')
      INTO faltando
      FROM unnest(ARRAY[
{chr(10).join("            " + ", ".join(f"'{SCHEMA}.{tb}.{c}'" for c in COLUNAS_ESPERADAS[tb]) for tb in COLUNAS_ESPERADAS)}
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
SELECT DISTINCT unidade_medida FROM {SCHEMA}.produtos ORDER BY 1;

-- 5) As tabelas estao no schema esperado?
SELECT table_name FROM information_schema.tables
 WHERE table_schema = '{SCHEMA}' ORDER BY table_name;

-- 6) Colunas NOT NULL que o seed precisa preencher (qualquer NOT NULL fora
-- desta lista com valor default quebraria o INSERT).
SELECT table_name, column_name
  FROM information_schema.columns
 WHERE table_schema = '{SCHEMA}'
   AND is_nullable = 'NO'
   AND column_default IS NULL
   AND table_name IN ('categorias','produtos','lotes','entradas','movimentacoes',
                      'parametros_estoque')
 ORDER BY table_name, column_name;
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true",
                    help="gera as variantes com ROLLBACK (revisar sem gravar)")
    ap.add_argument("--data", default=datetime.date.today().isoformat(),
                    help="data das entradas (padrao: hoje)")
    args = ap.parse_args()

    if not os.path.exists(CSV_CATALOGO):
        sys.exit(f"CSV nao encontrado: {CSV_CATALOGO}")

    with open(CSV_CATALOGO, encoding="utf-8") as f:
        produtos = list(csv.DictReader(f))
    if not produtos:
        sys.exit("CSV vazio")

    hoje = args.data
    os.makedirs(DIR_SAIDA, exist_ok=True)
    commit = not args.dry_run

    alvos = [
        ("00_verificar.sql", etapa_verificar(hoje), 0),
        ("01_categorias.sql", *etapa_categorias(produtos, hoje, commit)),
        ("02_produtos.sql", *etapa_produtos(produtos, hoje, commit)),
        ("03_estoque.sql", *etapa_estoque(produtos, hoje, commit)),
    ]
    for nome, conteudo, n in alvos:
        caminho = os.path.join(DIR_SAIDA, nome)
        with open(caminho, "w", encoding="utf-8") as f:
            f.write(conteudo)
        print(f"  {os.path.relpath(caminho, RAIZ):42} {len(conteudo):>7} bytes  "
              f"({n} itens)")

    # Variante de revisao: mesmos statements, so o terminador muda. Gerada por
    # padrao junto, para ninguem editar o COMMIT a mao e errar a mao. Os
    # arquivos -dryrun NAO sao versionados: sao 1,3 MB de duplicata que o
    # README ja explica de regenerar com --dry-run.
    if not args.dry_run:
        for nome, _, _ in alvos:
            origem = os.path.join(DIR_SAIDA, nome)
            destino = origem.replace(".sql", "-dryrun.sql")
            with open(origem, encoding="utf-8") as f:
                conteudo = f.read()
            with open(destino, "w", encoding="utf-8") as f:
                f.write(conteudo.replace("\nCOMMIT;\n", fecha(False)))

    com_estoque = sum(1 for p in produtos if Decimal(p["estoque_inicial"] or 0) > 0)
    print(f"\n  data das entradas: {hoje}")
    print(f"  terminador: {'COMMIT' if commit else 'ROLLBACK (dry-run)'}")
    print(f"  produtos: {len(produtos)} | com saldo: {com_estoque} | "
          f"sem saldo: {len(produtos) - com_estoque}")
    if not args.dry_run:
        print("  variantes -dryrun.sql tambem geradas (nao versionadas)")


if __name__ == "__main__":
    main()

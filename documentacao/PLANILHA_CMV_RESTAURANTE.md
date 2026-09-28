# Catálogo de produtos a partir da planilha de CMV Real

> **Status:** análise concluída, implementação pendente.
> Data da análise: 28/09/2026.
> Origem dos dados: `Downloads/Planilha de cálculo de CMV Real DEZEMBRO 22.xls`

Este documento registra o que tem na planilha, o que ela **não** tem, e as
decisões que precisam ser tomadas antes de popular o EstoQ. Os dados já
extraídos e normalizados estão em
[`catalogo-cmv-dezembro-2023.csv`](./catalogo-cmv-dezembro-2023.csv), e a
extração é reproduzível por [`scripts/extrair_catalogo_cmv.py`](../scripts/extrair_catalogo_cmv.py).

---

## 1. O que a planilha é

Arquivo `.xls` legado real (OLE2/BIFF, não é `.xlsx` renomeado), autor
**Renan Almeida Ribeiro**, última edição em **05/01/2024**. É a planilha de
CMV real de um restaurante, não um modelo.

Cinco abas:

| Aba | Conteúdo | Linhas |
|---|---|---|
| `CMV SEMANA 01` | movimento de 04/12/2023 a 11/12/2023 | 176 produtos |
| `CMV SEMANA 02` | movimento de 11/12/2023 a 18/12/2023 | 183 produtos |
| `CMV SEMANA 03` | movimento de 19/12/2023 a 26/12/2023 | 190 produtos |
| `CMV SEMANA 04` | movimento de 26/12/2023 a 02/01/2024 | 184 produtos |
| `CONSUMO` | catálogo canônico + estoque mínimo semanal | 183 produtos |

Estrutura das abas de movimento (colunas B a M):

```
PRODUTO | UNIDADE | ESTOQUE INICIAL (qtd, R$ unid, R$ total)
        | COMPRAS DA SEMANA (qtd, R$ unid, R$ total)
        | ESTOQUE FINAL (qtd, R$ unid, R$ total)
        | CONSUMO DA SEMANA (qtd)
```

A contagem de produtos **cresce e diminui** de uma semana para outra (176 →
183 → 190 → 184): o cardápio foi entrando e saindo ao longo do mês.

---

## 2. Números consolidados

Extraídos das 4 abas de movimento, cruzados com a aba `CONSUMO`:

| Métrica | Valor |
|---|---|
| Produtos no catálogo | **182** (183 linhas, ver §6) |
| Com estoque inicial > 0 | 130 |
| Com compras no período | 138 |
| Sem compra **e** sem estoque | 19 |
| Com preço de referência | 159 |
| Sem preço algum (23 produtos) | 23 |
| **Valor total das compras (4 semanas)** | **R$ 42.411,45** |
| Consumo total do período | 2.854,4 (soma misturada de kg e un) |

Distribuição por unidade de estoque (após o mapeamento do §4): **92 KG** e
**90 UN**.

As 15 categorias geradas:

| Categoria | Qtd | Categoria | Qtd |
|---|---:|---|---:|
| Carnes e Proteínas | 41 | Massas e Macarrão | 5 |
| Hortifruti | 41 | Padaria e Confeitaria | 4 |
| Molhos e Temperos | 27 | Peixes e Frutos do Mar | 4 |
| Grãos e Farinhas | 16 | Congelados | 3 |
| Laticínios | 12 | Bebidas | 2 |
| Polpas e Doces | 10 | Descartáveis e Limpeza | 2 |
| Enlatados e Conservas | 7 | Ovos | 2 |
| Não-perecíveis | 6 | | |

---

## 3. O que a planilha **não** tem

Estes três buracos são o que trava a implementação:

**1. Não há validade em nenhuma coluna.** O EstoQ dá baixa por FIFO ordenando
lotes por vencimento. A planilha de CMV não tem esse conceito — ela controla
por contagem de compras/consumo, não por validade. Sem inventar validade, o
FIFO não roda.

**2. As unidades não existem no enum do backend.** A planilha usa `und`, `kg`,
`cartela`, `pacote`, `um`. O `UnidadeMedida` do EstoQ é só `KG, G, L, ML, UN` —
não tem `cartela` nem `pacote`. Ver §4.

**3. O estoque mínimo é um número genérico.** A coluna
`ESTOQUE MÍNIMO SEMANAL` vale **7 para os 183 produtos** — preenchido em
bloco, não calculado. Serve de ponto de partida, mas não é um número real de
reposição.

---

## 4. O problema das unidades (o mais importante)

**113 dos 182 produtos estão marcados como `kg` na planilha, e 73 dos nomes têm
a medida embutida.** Exemplo:

```
Arroz 5 Kg          unid=und   estoque=2   →  2 sacos
Feijão Carioca 1 Kg unid=und   estoque=3   →  3 sacos
```

O `kg` da planilha é enganoso. Quando o produto se chama "Arroz 5 Kg" e a
quantidade é 2, são **2 sacos de 5 kg**, não 2 kg.

Isso importa porque o EstoQ tem `ConversorUnidade`, que converte na entrada e
**recusa mistura de dimensões**. Se "Arroz 5 Kg" for cadastrado como KG, entra
2 kg em vez de 2 sacos — estoque 25× menor que o real, e todo o CMV errado.

**Decisão aplicada na extração:** embalagem com medida no nome é `UN` (uma
embalagem comprada), e o peso fica no próprio nome, onde já está. Só vira `KG`
ou `L` quando a planilha diz `kg` **e** o nome não tem medida embutida.

O corte sai limpo e verificável: os **92 produtos que viraram `KG` são
exatamente os 92 que não têm medida no nome**, e os 90 que viraram `UN`
incluem os 73 com medida.

Isso é uma decisão de modelagem que vale confirmar — ver §7.

---

## 5. Problemas de qualidade nos dados

Não são erro da extração; estão no arquivo original.

**Produto duplicado no original.** `Acem` aparece 2× na aba `CONSUMO`
(linhas 87 e 146) e 2× na `CMV SEMANA 01` (linhas 92 e 151). Por isso são
**183 linhas mas 182 produtos** — a extração colapsa em uma só. Mas as duas
linhas do movimento foram somadas, então as compras do Acem estão contadas em
dobro (18,475 kg em vez de 9,2375 kg). **Precisa ser tratado antes de usar.**

**Consumo negativo.** 2 produtos com consumo negativo, inconsistência de
digitação — estoque final maior que o inicial:

| Produto | Consumo 4 semanas |
|---|---:|
| Azeitona Fatiada verde - 3,2kg | negativo |
| Azeitona sem caroço verde - 2kg | negativo |

**23 produtos sem preço algum** (sem estoque inicial e sem compra). Sem preço
válido, a entrada tem que ser com `semCusto: true` — e o backend **proíbe**
`valorTotalPago: 0` junto com `semCusto: false`, e **proíbe** `semCusto: true`
junto com valor pago. São as duas únicas combinações aceitas.

**19 produtos sem nenhum dado de movimento** — existem no catálogo, mas não
tiveram compra nem estoque inicial no período.

---

## 6. A coluna `CONSUMO` está quase vazia

A aba `CONSUMO` tem 4 colunas de consumo semanal (04/12, 11/12, 19/12, 26/12)
que seriam o insumo mais direto para o replay do cenário 2. **As 4 colunas
estão vazias para todos os 183 produtos.** O consumo só existe na coluna M das
abas de movimento.

---

## 7. Cenários de carga

A planilha é um histórico de **dezembro/2023**. Popular o EstoQ hoje coloca
dados de 3 anos atrás no sistema. São três formas de usar:

### Cenário 1 — Estoque inicial de hoje
Usa só os produtos com estoque, e as **compras** viram entradas com validade
inventada. Serve para deixar o app populado para demonstrar. O histórico de
dezembro fica de fora; a planilha entra só como "qual produto e quanto custa".

### Cenário 2 — Replay das 4 semanas
Cada compra vira `POST /api/entradas` e cada consumo vira
`POST /api/consumos`, na ordem cronológica. Cria CMV, desperdício e relatórios
realmente coerentes, e é a única forma de reproduzir fielmente a lógica da
planilha. É o mais fiel e o mais trabalhoso — e o que melhor demonstra o
projeto, porque os números batem com a planilha original.

### Cenário 3 — Catálogo só, estoque via balanço
Cadastra os 182 produtos, e o estoque entra por **balanço físico** — que é
como restaurante real faz (abre o mês com contagem). Usa a coluna
`ESTOQUE INICIAL` como quantidade contada. É o mais correto para o domínio,
mas o CMV dos 30 dias seguintes fica zerado até você consumir.

**Recomendação: cenário 2**, porque é o único que faz o EstoQ calcular um CMV
que confere com a planilha — que é exatamente o que o projeto promete.

---

## 8. Decisões pendentes

Nenhuma delas pode ser inventada pelo script — todas mudam o resultado.

**Qual cenário (1, 2 ou 3)?**

**Validade.** Não existe na planilha. Sugestão: inventar por categoria
(grão 6 meses, congelado 4, laticínio 21 dias, hortifruti 5 dias,
não-perecível sem validade). Ou deixar tudo sem validade?

**Unidades.** Confirmar o mapeamento do §4: embalagem-com-peso como `UN`, e
`KG`/`L` só quando a planilha diz `kg` **e** o nome não tem medida?

**Categorias.** As 15 do §2 foram geradas por palavra-chave e revisadas. A
planilha não tem categorias. São boas, ou você tem outra divisão em mente?

**E-mail do ADMIN da loja.** O `admin@estoq.com` do `bootstrap-producao.sql` é
**PLATAFORMA** (`restaurante_id = NULL`, promovido pela V3) — não tem loja, e
o frontend manda PLATAFORMA só para `/plataforma`. `POST /api/entradas`
exige **ADMIN** (`anyRequest() → hasRole("ADMIN")`); COZINHA não popula. O
seed precisa do admin de uma cozinha específica, não do bootstrap.

---

## 9. Restrições do backend que o script vai bater

Validações de `EntradaValidation` que vão travar a primeira tentativa:

- `quantidade` > 0
- `valorTotalPago: 0` **exige** `semCusto: true`
- `semCusto: true` **proíbe** valor pago
- `unidadeCompra` convertido por `ConversorUnidade` (recusa KG + UN)
- `estoqueMinimo <= estoqueMedio <= estoqueMaximo`
- `periodoAnaliseDias > 0`
- `UnidadeMedida` = `KG, G, L, ML, UN`

Fluxo de chamadas, seguindo o padrão de auth de
[`scripts/e2e_validacao.sh`](../scripts/e2e_validacao.sh) (cookie jar com
`-b`/`-c` no `GET /api/auth/csrf`, header `X-CSRF-TOKEN` em todo POST não-GET):

```
POST /api/registro            (se a loja ainda não existir)
POST /api/auth/login
GET  /api/auth/csrf
POST /api/categorias
POST /api/produtos
POST /api/parametros-estoque
POST /api/entradas            (nunca INSERT direto no banco)
```

O script precisa ser **re-executável** (não duplicar dados) e ter `--dry-run`
para revisar antes de gravar em produção.

---

## 10. Como reproduzir a extração

```bash
pip install xlrd
python scripts/extrair_catalogo_cmv.py "caminho/Planilha de cálculo de CMV Real DEZEMBRO 22.xls"
```

Saída: `documentacao/catalogo-cmv-dezembro-2023.csv` (182 linhas, 16 colunas).

Colunas do CSV: `nome`, `categoria`, `unidade_estoque`, `medida_embutida`,
`peso_por_embalagem`, `unidade_medida`, `unidade_planilha`,
`qtd_por_embalagem`, `estoque_inicial`, `preco_unitario_inicial`,
`compras_4_semanas`, `valor_compras_4_semanas`, `preco_medio_compra`,
`consumo_4_semanas`, `semanas_presente`, `estoque_minimo_planilha`.

O `.xls` original **não** é versionado (é documento do cliente, 300 KB, e não
é código). O CSV gerado é, para o catálogo ficar rastreável.

---

## 11. Pendências que não são deste assunto

Anotadas aqui para não se perderem:

- `V4__schema_completo.sql` não existe — rodar V1→V3 em Postgres vazio falha
  com `relation "ajustes" does not exist`. O app **não sobe em banco novo**.
  É bloqueador de deploy.
- `README.md:30` afirma "O `core` nunca importa `business`", mas
  `core/conf/seed/SeedDataConfig.java:3-12` tem 10 imports de `business/`.
- `documentacao/COMMITS_E_FLUXO_DE_TRABALHO.md` ainda descreve a branch
  `master`, que foi deletada.

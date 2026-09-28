# Carga de estoque — planilha de CMV Real (dez/2023)

SQL gerado a partir de `documentacao/catalogo-cmv-dezembro-2023.csv`, extraído de
`Planilha de cálculo de CMV Real DEZEMBRO 22.xls`.

Gerar de novo:

```bash
python scripts/gerar_seed_sql.py             # com COMMIT
python scripts/gerar_seed_sql.py --dry-run   # com ROLLBACK
```

Não edite os `.sql` à mão — eles são sobrescritos. Ajuste o gerador.

## Como rodar

No SQL editor do Neon, um arquivo por vez, **nessa ordem**. Não junte: cada um
abre e fecha sua própria transação.

| Ordem | Arquivo | O que faz | Registros |
|---|---|---|---|
| 1 | `00_verificar.sql` | só lê, não grava | — |
| 2 | `01_categorias.sql` | categorias | 15 |
| 3 | `02_produtos.sql` | produtos | 182 |
| 4 | `03_estoque.sql` | parâmetros + lotes + entradas + movimentações | 130 |

Cada arquivo termina com as queries de conferência. Leia a saída antes de
passar para o próximo.

## Por que SQL e não a API

A carga são ~900 linhas em 5 tabelas. Pela API seriam ~650 chamadas HTTP
sequenciais e não-atômicas: uma falha no meio deixaria o banco pela metade. Em
SQL tudo cai numa transação — ou entra tudo, ou nada.

O dry-run é um `ROLLBACK`, o que é melhor que o da API: valida contra o schema
real de produção, e não só simula a requisição.

## Os guards

Todo arquivo começa com dois blocos `DO $$` que abortam **antes de gravar**:

1. **Schema** — confere se as colunas que o seed usa existem. O schema de
   produção não veio das migrations (V1–V3 estão quebradas e a V4 não existe),
   então as migrations não servem de referência; só a introspecção do banco
   serve.
2. **Tenant** — resolve o `restaurante_id` pelo e-mail de `EMAIL_ALVO` e aborta
   se o usuário não existir, não tiver loja, ou for `PLATAFORMA`.

O guard de tenant não é paranoia. O `TenantEntity` usa `@TenantId`, e linha com
`restaurante_id` nulo **não é filtrada por tenant** — ela aparece para todos os
restaurantes. O `admin@estoq.com` do `bootstrap-producao.sql` é exatamente
esse caso. Se o seed rodasse com ele, os 182 produtos ficariam visíveis para a
plataforma inteira.

## Idempotência

Rodar duas vezes não duplica nada. Cada `INSERT` só grava se a linha ainda não
existir, e produtos são localizados por nome (único no catálogo). Verificado:
1ª e 2ª execução dão 15 / 182 / 182 / 130 / 130 / 130.

## O que foi replicado do código

| Comportamento | Origem |
|---|---|
| `preco_unitario = valor_total_pago / quantidade` | `EntradaService:35` |
| `lotes.codigo` = `L-%06d` | `LoteService:64-72` |
| `quantidade_anterior = 0`, `quantidade_posterior = quantidade` | `EntradaService:41` |
| `unidade_compra` = `unidade_medida` do produto | evita `ConversorUnidade` recusar |
| `restaurante_id`, `ativo`, `version` em toda linha | `BaseModel` — todos `not null` |
| `entradas` só com 4 colunas | `@Inheritance(JOINED)`: o resto herda de `movimentacoes` |
| estoque mínimo = 1,5 semana de consumo | `ParametroEstoqueValidation` exige `min <= medio <= max` |
| validade por categoria | a planilha não tem validade; ver `VALIDADE_DIAS` no gerador |

O placeholder `PENDENTE-SEED-<id>` do `lotes.codigo` é único por lote porque
existe `UNIQUE(restaurante_id, codigo)`.

## Limitações conhecidas

**52 dos 182 produtos entram com saldo 0.** A planilha não tem estoque inicial
nem compra para eles — 19 nunca tiveram movimento nenhum no período. Entram no
catálogo, aparecem zerados. É dado real, não placeholder; para dar saldo a eles
seria preciso inventar quantidade.

**CMV aparece praticamente zerado.** O app mede CMV por consumo, e esta carga
cria só entrada. Como a entrada é de hoje, o relatório dos últimos 30 dias
mostra a compra (R$ 25.481) e consumo 0.

**Perdas não explicadas de R$ 0,60.** `cmvPeriodo` e `perdasNaoExplicadasPeriodo`
não saem zerados: os R$ 1,99 de estoque inicial sem consumo caem na conta de
perda. Vem do acúmulo de quantidades fracionárias em `lotes.quantidade_atual`.
Ruído de arredondamento, não indica dado errado.

**Validade é inventada.** Padrão de mercado por tipo de mercadoria. Só a
hortifruti e as carnes ficam dentro da janela de alerta (62 lotes); os
não-perecíveis ficam com validade longa e não disparam alerta.

**Preço é de dez/2023.** Serve para manipular o app, não para decisão de compra.

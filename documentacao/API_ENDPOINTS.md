# EstoQ_Startup — Contrato de API (referência do frontend)

Documento gerado a partir do código (controllers + `SecurityConfig.java`). Base URL local: **`http://localhost:8083`**.

---

## 0. Como a autenticação funciona (obrigatório ler antes)

1. `GET /api/auth/csrf` (público) → devolve `{ "token": "...", "headerName": "X-CSRF-TOKEN" }` e cria a sessão (cookie `ESTOQSESSION`, HttpOnly).
2. `POST /api/auth/login` com `{ "email", "senha" }` **+ header `X-CSRF-TOKEN`** → responde `{ id, nome, email, perfil }`. Login rotaciona o token CSRF: **reconsultar `GET /api/auth/csrf` após o login**.
3. Todas as requisições autenticadas enviam o cookie de sessão automaticamente. Toda requisição **POST/PUT/DELETE** exige o header `X-CSRF-TOKEN` (com o token atual).
4. `GET /api/auth/me` → usuário da sessão (usar ao recarregar a página para restaurar o estado de login).
5. `POST /api/auth/logout` → encerra a sessão (204).

**Papéis (role):** `ADMIN`, `COZINHA`, `NUTRICIONISTA`.

**Regras de erro:**
- 401 sem sessão · 403 sem permissão **ou CSRF inválido** · 409 `Conflict` em corrida (conflito de `version`) · 429 após **5 falhas de login em 15 min** (por e-mail/IP)
- Erros de validação → 400/422 com corpo em `ErrorResponse`
- CORS: configurado por `estoq.cors.origins` (envioronment) — em dev, liberar a origem do front (ex.: `http://localhost:5173`).

---

## 1. Auth

| Método | Rotas | Papel | Corpo / notas |
|--------|-------|-------|---------------|
| GET | `/api/auth/csrf` | público | sem corpo → `{ token, headerName }` |
| POST | `/api/auth/login` | público | `{ email, senha }` |
| GET | `/api/auth/me` | autenticado | → `{ id, nome, email, perfil }` |
| POST | `/api/auth/logout` | autenticado | → 204 |

---

## 2. Usuários (somente ADMIN)

`/api/usuarios` fundo: `Pagina<UsuarioResponseDTO>` usa parâmetros `page`, `size`, `sort` (Spring Data).

| Método | Rota | Corpo / notas |
|--------|------|---------------|
| GET | `/api/usuarios` | lista paginada |
| GET | `/api/usuarios/{id}` | — |
| POST | `/api/usuarios` | `{ nome, email, senha, perfil }` (senha ≥ 8) → 201 |
| PUT | `/api/usuarios/{id}` | `{ version, nome, email, senha?, perfil, ativo }` |
| DELETE | `/api/usuarios/{id}` | desativa (revoga sessão) → 204 |

---

## 3. Catálogo

### Categorias — `ADMIN/COZINHA` lê, `ADMIN` escreve
| Método | Rota | Corpo |
|--------|------|-------|
| GET | `/api/categorias` | lista paginada (`?page=&size=&sort=`) |
| GET | `/api/categorias/{id}` | — |
| POST | `/api/categorias` | `{ nome, descricao? }` → 201 |
| PUT | `/api/categorias/{id}` | `{ id?, version?, nome, descricao? }` |
| DELETE | `/api/categorias/{id}` | → 204 |

### Parâmetros de estoque — **só ADMIN** (rota fora dos matchers de COZINHA)
| Método | Rota | Corpo |
|--------|------|------|
| GET | `/api/parametros-estoque` | paginada |
| GET | `/api/parametros-estoque/{id}` | — |
| POST | `/api/parametros-estoque` | `{ produtoId, tempoReposicaoDias, periodoAnaliseDias, consumoMedioDiario, estoqueMinimo, estoqueMedio, estoqueMaximo, diasAlertaVencimento }` → 201 |
| PUT | `/api/parametros-estoque/{id}` | mesmo corpo + `version` |
| DELETE | `/api/parametros-estoque/{id}` | → 204 |

### Produtos — `ADMIN/COZINHA` lê, `ADMIN` escreve
| Método | Rota | Corpo / notas |
|--------|------|---------------|
| GET | `/api/produtos` | paginada (`?page=&size=&sort=`) |
| GET | `/api/produtos/{id}` | — |
| POST | `/api/produtos` | `{ nome, unidadeMedida, categoriaId, codigoBarras?, parametroEstoqueId? }` → 201 |
| PUT | `/api/produtos/{id}` | mesmo corpo + `version` |
| DELETE | `/api/produtos/{id}` | → 204 |
| GET | `/api/produtos/{id}/saldo` | → `BigDecimal` |
| GET | `/api/produtos/{id}/lotes-disponiveis` | → lista de `LoteDTO` |
| GET | `/api/produtos/estoque-baixo` | → lista `EstoqueDTO` abaixo do mínimo |

> ⚠️ Ordem das rotas: `/api/produtos/estoque-baixo` e `/api/produtos/{id}/saldo` são dois GETs específicos; `{id}` nunca é string "estoque-baixo", então não há conflito.

---

## 4. Estoque e lotes

| Método | Rota | Papel | Corpo / notas |
|--------|------|-------|---------------|
| GET | `/api/estoque` | ADM/COZ | `?somenteAbertos=false&somenteBaixo=false` → lista `EstoqueDTO` |
| GET | `/api/lotes` | ADM/COZ | `?produtoId=` → lista `LoteDTO` |
| GET | `/api/lotes/{id}` | ADM/COZ | — |
| GET | `/api/lotes/vencidos` | ADM/COZ | → lista `LoteDTO` |
| GET | `/api/lotes/proximos-vencimento` | ADM/COZ | → lista `LoteDTO` |

`EstoqueDTO`: `{ produtoId, produtoNome, categoriaNome, unidadeMedida, saldoAtual, valorEstoque, estoqueMinimo, estoqueMedio, estoqueMaximo, abaixoDoMinimo, possuiItensAbertos }`
`LoteDTO`: `{ id, version, codigo, produtoId, produtoNome, unidadeMedida, quantidadeInicial, quantidadeAtual, dataEntrada, dataValidade, precoUnitario, vencido, disponivel, diasParaVencimento }` — **`version` é obrigatório para consumo/desperdício concorrente.**

---

## 5. Movimentações

| Método | Rota | Papel | Corpo / notas |
|--------|------|-------|---------------|
| GET | `/api/movimentacoes` | ADM/COZ | `?produtoId=&loteId=&usuarioId=&tipo=ENTRADA|CONSUMO|DESPERDICIO|AJUSTE_...|ABERTURA&inicio=&fim=` (datas ISO) |
| POST | `/api/entradas` | **ADMIN** | `{ produtoId, quantidade, valorTotalPago, unidadeCompra, dataValidade?, observacao?, semCusto? }` → 201 |
| POST | `/api/consumos` | ADM/COZ | `{ produtoId, loteId?, quantidade, versionLote?, observacao? }` |
| POST | `/api/desperdicios` | ADM/COZ | `{ produtoId, loteId?, quantidade, motivo, descricaoMotivo?, versionLote?, observacao? }` |

- `unidadeCompra`: enum `UnidadeMedida` (ex.: `KG`, `G`, `L`, ...). A entrada converte para a unidade do produto.
- `valorTotalPago = 0` exige `semCusto = true`.
- **`motivo` (desperdício):** enum `MotivoDesperdicio` (ex.: `VENCIDO`, `QUEBRADO`, `SOBRA_NAO_APROVEITADA`...).
- Resposta das movimentações: `MovimentacaoResultadoDTO { produtoId, saldoAtual, movimentacoes: [...] }` (cada item com `quantidadeAnterior/posterior`, `delta`, `dataHora`).

---

## 6. Produtos abertos

| Método | Rota | Papel | Corpo / notas |
|--------|------|-------|---------------|
| GET | `/api/produtos-abertos` | ADM/COZ | `?produtoId=&finalizado=false` |
| GET | `/api/produtos-abertos/{id}` | ADM/COZ | — |
| POST | `/api/produtos-abertos/abrir` | ADM/COZ | `{ produtoId, loteId, quantidadeDaEmbalagem, quantoUsouAgora, versionLote? }` → 201 |
| POST | `/api/produtos-abertos/{id}/consumir` | ADM/COZ | `{ quantidade, version?, versionLote? }` |
| POST | `/api/produtos-abertos/{id}/desperdicar` | ADM/COZ | `{ quantidade, version?, versionLote? }` |

> PEP abertos `version` (do produto aberto) e `versionLote` (do lote origem) vão juntos e atualizam no mesmo request.

---

## 7. Balanço e alertas

### Balanço (`ADM/COZ`)
| Método | Rota | Corpo / notas |
|--------|------|---------------|
| GET | `/api/balancos` | lista `BalancoDTO` |
| GET | `/api/balancos/{id}` | → inclui `itens[]` |
| POST | `/api/balancos` | `{ tipo?: GERAL|PARCIAL }` (sem corpo = GERAL) → 201 |
| POST | `/api/balancos/{id}/iniciar` | gera itens de contagem |
| PUT | `/api/balancos/{id}/itens/{itemId}/contagem` | `{ quantidadeFisica }` |
| POST | `/api/balancos/{id}/confirmar` | **ADMIN** — exige contagem completa |
| POST | `/api/balancos/{id}/gerar-ajustes` | **ADMIN** — aplica FIFO (déficit/superávit) |

> ⚠️ `confirmar` e `gerar-ajustes` caem em `anyRequest().hasRole("ADMIN")` no `SecurityConfig`: **somente ADMIN**, assim como `/api/configuracoes-balanco`.

### Configuração de balanço (só ADMIN)
| Método | Rota | Corpo |
|--------|------|-------|
| GET | `/api/configuracoes-balanco` | — |
| POST | `/api/configuracoes-balanco` | `{ periodicidade, diaExecucao, proximaExecucao }` |
| PUT | `/api/configuracoes-balanco/{id}` | mesmo corpo + `version` |
| DELETE | `/api/configuracoes-balanco/{id}` | → 204 |

### Alertas (`ADM/COZ`)
| Método | Rota | Corpo / notas |
|--------|------|---------------|
| GET | `/api/alertas` | `?visualizado=` (ou usa perfil do usuário p/ filtrar) |
| GET | `/api/alertas/abertos` | → `long` (contador para badge) |
| PUT | `/api/alertas/{id}/visualizado` | → 200 |

---

## 8. Relatórios, dashboard e CMV — `ADMIN`/`NUTRICIONISTA`

Datas como `?inicio=2026-09-01&fim=2026-09-30` (ISO `LocalDate`). Sem `inicio` → últimos 30 dias.

| Método | Rota | Notas |
|--------|------|-------|
| GET | `/api/relatorios/estoque-atual` | → lista `EstoqueDTO` |
| GET | `/api/relatorios/proximos-vencimento` | → `LoteDTO[]` |
| GET | `/api/relatorios/vencidos` | → `LoteDTO[]` |
| GET | `/api/relatorios/produtos-abertos` | → `ProdutoAbertoDTO[]` |
| GET | `/api/relatorios/desperdicio` | → `DesperdicioDTO[]` (dataHora, motivo, produto, lote, quantidade, valorPrejuizo) |
| GET | `/api/relatorios/desperdicio/agregado` | → `DesperdicioAgregadoDTO[]` (por motivo) |
| GET | `/api/relatorios/consumo-medio` | `?dias=30&fim=` → `ConsumoMedioDTO[]` |
| GET | `/api/relatorios/consumo/dia-semana` | → `ConsumoDiaSemanaDTO[]` |
| GET | `/api/relatorios/cmv` | `?inicio=&fim=&receitaBase=` → `CmvResumoDTO` |
| GET | `/api/relatorios/cmv/mensal` | → `CmvMensalDTO[]` (mês, compras, desperdício, cmv...) |
| GET | `/api/relatorios/reposicao-sugerida` | `?dias=` → `ReposicaoSugeridaDTO[]` |
| GET | `/api/relatorios/pdf/{tipo}` | PDF (content-type `application/pdf`). `tipo` ∈ {`estoque`, `cmv`, `desperdicio`, `consumo-medio`, ...} conforme `RelatorioPdfService` |
| GET | `/api/dashboard/resumo` | → `DashboardResumoDTO` (contadores + CMV + percentuais + perdas não explicadas) |
| GET | `/api/parametros-cmv` | → `{ id, version, percentualIdeal }` |
| PUT | `/api/parametros-cmv` | `{ id?, version?, percentualIdeal }` |

---

## 9. Backup — **só ADMIN**

| Método | Rota | Notas |
|--------|------|-------|
| GET | `/api/backups` | → `BackupDTO[]` (`nome`, `tamanhoBytes`, `criadoEm`) |
| POST | `/api/backups` | gera dump (`pg_dump` custom, schema `estoq_v2`) → 201 |
| GET | `/api/backups/{nome}` | baixa `.dump` (valida nome; sem path traversal) |

---

## 10. Swagger/OpenAPI (público)

- `GET /v3/api-docs`
- `GET /swagger-ui.html`, `/swagger-ui/**`

---

## 11. Vejamos os campos de resposta mais usados

- **`CmvResumoDTO`**: `dataInicio, dataFim, valorEstoqueInicial, valorCompras, valorEstoqueFinal, cmv, receitaBase, cmvPercentual, percentualIdeal, diferencaPercentualParaMeta, valorConsumoRegistrado, valorDesperdicio, percentualDesperdicioSobreCmv, valorPerdasNaoExplicadas`
- **`DashboardResumoDTO`**: `totalProdutosAtivos, produtosEstoqueBaixo, lotesProximosVencimento, lotesVencidos, produtosAbertosAtivos, balancosPendentes, valorDesperdicioPeriodo, cmvPeriodo, cmvPercentual, cmvIdeal, diferencaCmvParaMeta, perdasNaoExplicadasPeriodo`
- **`ReposicaoSugeridaDTO`**: `produtoId, produtoNome, categoriaNome, unidadeMedida, saldoAtual, estoqueMinimo, consumoMedioDiario, diasReposicao, quantidadeSugerida`
- **`BalancoDTO`**: `id, version, dataHora, tipo, status, usuarioId, usuarioNome, itens[]`
- Generic (categorias/produtos/parametros-estoque) herdam `BaseDTO`: `id, version, ativo, dataHoraCriacao`.
# EstoQ_Startup — Resumo da sessão (backend)

Local: `/home/pedro/EstoQ_Startup` · Backend em `backend/` · Porta **8083** · PostgreSQL Docker porta **5434** (banco `estoq_startup`, user `estoq`/`estoq`).

---

## 1. Objetivo e escopo
Refatorar o backend do gerenciador de estoque/CMV definido na Constituição (`documentacao/EstoQ_Constituicao_do_Projeto_v1.0.docx`), agora seguindo o contrato `EstoQ_Prompt_Backend_Modular_Camadas_v1.0.docx`: padrão **PIAds3** (`core` genérico + módulos `business` autocontidos e modulares), dados novos no schema **`estoq_v2`** (a base antiga no schema `public` fica preservada, com backup em `dados/`).

Nesta rodada: refatoração de estilo concluída (anotações linha a linha, imports explícitos) e **Fases A/B de correção e gestão** implementadas (superávit, gap de perdas, ciclo de alertas, agregados, reposição, entrada sem custo e não perecível). **Vendas/PDV ficaram fora do escopo** (decisão do produto: o app cobre controle de estoque e desperdício).

## 2. Decisões-chave (confirmadas nesta rodada)
- **Auth por e-mail + senha** (BCrypt) com **sessão HTTP**: cookie HttpOnly `ESTOQSESSION` + proteção **CSRF** (`X-CSRF-TOKEN` via `GET /api/auth/csrf`; login rotaciona o token). Elimina o PIN e o token bearer antigos.
- **Saldo sempre derivado** (RN02): `saldoAtual = soma(lote.quantidadeAtual)`. O restante de produto aberto **não** é somado de novo (já está dentro do lote); abertura baixa do lote apenas o usado.
- Consumo: primeiro itens abertos (mais antigos), depois lotes **FIFO por validade** (vencidos ficam de fora; dataValidade nula vai por último).
- **Lote sem validade = não perecível**: disponível enquanto houver quantidade, ordenado por último no FIFO, nunca "vence".
- **Ajuste de balanço — opção B** (confirmada): ItemBalanco por produto com distribuição **FIFO** — déficit baixa lotes em FIFO; superávit soma no lote disponível mais antigo (ou cria **lote de correção consumível** com validade nula se não houver). Nunca zera lote abaixo de zero.
- Desperdício: FIFO **incluindo vencidos**; sobra de item aberto finaliza com motivo `SOBRA_NAO_APROVEITADA`.
- **Entrada com `valorTotalPago = 0` exige flag `semCusto`** (doação) e, com a flag, custo/preço são zerados (não distorce CMV). A validade da entrada passou a ser **opcional** (lote não perecível).
- **Gap de perdas não explicadas** no CMV: `cmv − (consumo registrado + desperdício)` exposto no `CmvResumoDTO` e no dashboard.
- **Ciclo de alertas de balanço**: scheduler diário (06h, `@EnableScheduling`) avalia a config vencida → gera `BALANCO_PENDENTE` e avança `proximaExecucao`; concluir balanço limpa `BALANCO_PENDENTE`; `gerar-ajustes` limpa `DIFERENCA_ESTOQUE`.
- **%CMV fica secundário**: sem módulo de vendas, `receitaBase` é parâmetro manual e opcional; indicadores principais passam a ser CMV em R$, desperdício (valor e % sobre CMV) e o gap.
- Concorrência: `@Version` (RN24) — lock otimista vira `409 Conflict` amigável.
- Entrada gera lote com código sequencial `L-000001, ...`; `precoUnitario = valorTotalPago / (quantidade * fatorConversao)`; unidade de compra é convertida para a unidade do produto se diferente.
- Trilha de movimentações: base abstrata `MovimentacaoEstoqueModel` com `@Inheritance(JOINED)` + `@DiscriminatorColumn("tipo")`; cada movimento registra `quantidadeAnterior/Posterior` e `getDelta()`.
- `@TransactionalEventListener(AFTER_COMMIT)` exige `Propagation.REQUIRES_NEW` (reavaliação de alertas pós-movimento).
- JAR de build: `target/estoq-startup.jar` (Boot 4 compacta).

## 3. Estrutura criada
```
backend/src/main/java/com/estoq/
├── EstoqApplication.java
├── core/                  # genérico PIAds3 (sem import p/ business)
│   ├── domains/ dtos/ repositories/ services/ validations/
│   ├── controllers/ helpers/ exceptions/   # BaseException, Conflict 409, GlobalExceptionHandler
│   └── conf/seed/SeedDataConfig            # seed dev (3 usuários + CMV 30% + balanço MENSAL)
├── config/security/       # SecurityConfig (matriz rotas), CustomUserDetailsService,
│                          # UsuarioAtivoFilter, exceptions de segurança
└── business/              # módulos numerados conforme o contrato
    ├── usuarios/  auth/  auditoria/
    ├── categorias/  produtos/  parametrosEstoque/  lotes/
    ├── entradas/  consumos/  desperdicios/  ajustes/  movimentacoesEstoque/
    ├── produtosAbertos/  balancos/  itensBalanco/  configuracoesBalanco/
    └── alertas/  parametrosCmv/  relatorios/  dashboard/
```

## 4. Regras de negócio implementadas
- **RN02** saldo derivado (nunca digitado).
- **RN05** entrada com conversão de unidade gera lote (ConversorUnidade KG/G/L/ML/UN).
- **RN07** consumo não usa lotes vencidos (lotes sem validade são consumíveis).
- **RN10/RN11** aberto: restante = embalagem − utilizada; só o utilizado vira consumo; abrir sem uso não cria movimento.
- **RN12** sobra vira movimentação (Desperdicio `SOBRA_NAO_APROVEITADA` ao descartar aberto).
- **RN15/16** balanço: diferença = física − sistema; gerar-ajustes aplica FIFO e é idempotente (`ajusteAplicado`).
- **RN24** controle de concorrência em lote.
- Lote de correção de superávit nasce consumível (validade nula) — não é estoque morto.
- Entrada sem custo só com `semCusto=true` (doação); custo zero sem flag é rejeitado.
- CMV mensal: cálculo por lote com deltas por mês (a soma dos meses fecha com o CMV total do período).
- Reposição sugerida: `(consumoMedioDiario × diasReposição) − saldoAtual`, exibida quando positiva.
- Matriz de permissões (`SecurityConfig`):
  - COZINHA: GETs (consultas de estoque/catálogo/movimentações/alertas), POST consumo/desperdício, abrir/consumir/desperdicar aberto, contagem de balanço, `PUT /api/alertas/*/visualizado`.
  - NUTRICIONISTA: GET/PUT `/api/parametros-cmv` (meta de CMV), relatórios e dashboard.
  - ADMIN: todo o resto (cadastros, entradas, balanço, ajustes, configurações).

## 5. Seed (dev)
- Usuários: `admin@estoq.com`/`Admin@12345` (ADMIN), `cozinha@estoq.com`/`Cozinha@12345` (COZINHA), `nutricionista@estoq.com`/`Nutricao@12345` (NUTRICIONISTA).
- Meta CMV padrão 30% (`parametro_cmv`) e `ConfiguracaoBalanco` MENSAL (dia 1).
- Ativo só no profile `dev` com `estoq.seed.enabled=true` (padrão).

## 6. Endpoints principais
- Auth: `GET /api/auth/csrf` · `POST /api/auth/login` · `POST /api/auth/logout` · `GET /api/auth/me`
- Catálogo: `GET/POST /api/categorias` · `GET/POST /api/produtos` · `GET /api/produtos/{id}/saldo|lotes-disponiveis` · `GET /api/produtos/estoque-baixo` · `GET/POST /api/lotes` · `GET /api/estoque` · `GET /api/movimentacoes?produtoId&inicio&fim`
- Movimentações: `POST /api/entradas|consumos|desperdicios` · `POST /api/produtos-abertos/abrir|/{id}/consumir|/{id}/desperdicar` (entrada aceita `dataValidade` nula e `semCusto`)
- Balanço: `POST /api/balancos` · `/{id}/iniciar` · `PUT /{id}/itens/{itemId}/contagem` · `/{id}/confirmar` · `/{id}/gerar-ajustes` · `GET/POST/PUT/DELETE /api/configuracoes-balanco`
- Alertas: `GET /api/alertas` · `GET /api/alertas/abertos` · `PUT /api/alertas/{id}/visualizado` (scheduler 06h gere `BALANCO_PENDENTE`)
- CMV/Relatórios/Dashboard: `GET/PUT /api/parametros-cmv` · `GET /api/relatorios/estoque-atual|proximos-vencimento|vencidos|produtos-abertos|desperdicio|consumo-medio|cmv` · `GET /api/relatorios/desperdicio/agregado` · `GET /api/relatorios/consumo/dia-semana` · `GET /api/relatorios/cmv/mensal` · `GET /api/relatorios/reposicao-sugerida?dias=N` · `GET /api/dashboard/resumo`

## 7. Como rodar
```bash
docker compose up -d postgres
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev   # porta 8083
# pacote: mvn package -DskipTests → target/estoq-startup.jar
```

## 8. Testes automatizados (mvn test — 33 verdes)
- `SecurityIntegrationTest` (4): login formato inválido, login OK + me, logout invalida sessão.
- `DominioTest` (4): conversor de unidades, baixa negativa rejeitada, validade hoje/mais antiga, níveis de estoque invertidos.
- `EstoqueIntegrationTest` (8): abrir embalagem → consumir → desperdiçar (saldo/prejuízo/custos), abertura sem uso não cria movimento e conflita 2ª abertura, consumo FIFO cede primeiro o lote, vencido rejeitado, desperdício acima do saldo rejeitado, versão divergente → 409, **entrada sem validade é consumível e nunca vence**, **custo zero exige `semCusto`**.
- `BalancoCmvIntegrationTest` (10): débito de balanço em FIFO, superávit soma no lote, **superávit cria lote novo consumível**, confirmar exige contagem completa, CMV/consumo médio/percentuais + **gap = 0**, **gap mensura perdas não registradas**, meta CMV restritiva, **ciclo de alertas (gera/limpa/avança)**, **agregados (top desperdício, dia da semana, CMV mensal = total)**, **reposição sugerida (consumo×lead−saldo)**.
- `ConcorrenciaIntegrationTest` (1, perfil `concorrencia`, H2 isolado): 2 threads no `RegistradorConsumoSingleton` → 1 sucesso + 1 `409` (lock otimista real), saldo 4/5. Ver `documentacao/THREADS_E_SINGLETON.md`.
- `BackupPdfIntegrationTest` (4): PDF `estoque-atual` e `cmv` começam com `%PDF`, tipo inválido → `400`, listar backups vazio (dir temp) e `baixar` com nome fora do padrão → erro.
- `LoginAttemptIntegrationTest` (2, `estoq.login.max-tentativas=2`): bloqueio após o limite → `429` (inclusive com senha correta), e outro e-mail sem falhas mantém acesso (limite é por e-mail+IP).
- Smoke E2E via curl validado (app dev no Postgres): login → categoria/produto → entrada (L-000001, preço 20) → saldo 10 → abrir (lote 9) → consumir aberto (7) → consumo geral (5) → balanço iniciar (snapshot 5) → contagem 4 → confirmar → gerar-ajustes (saldo 4; AJUSTE delta −1; obs "Ajuste de déficit do balanço #1") → CMV (compras 200, estoque final 80, CMV 120, %60 vs meta 30 → dif 30) → dashboard → relatório estoque → movimentações (histórico com deltas) → alerta `DIFERENCA_ESTOQUE` → 401 sem sessão.

## 9. Backup e PDF de relatórios (novidades desta rodada)
- **Backup real via `pg_dump`** (PostgreSQL): novo módulo `com.estoq.backup` (paralelo a `patterns`, respeitando a Constituição — não toca no `core`). Config em `application.properties` (`estoq.backup.diretorio=${ESTOQ_BACKUP_DIR:../dados/backups}`). Endpoints: `POST /api/backups` (gera dump custom `.dump`, mantém as N últimas), `GET /api/backups` (lista) e `GET /api/backups/{nome}` (download). Nome validado por regex (`estoq-\d{8}-\d{6}\.dump`) contra path traversal; rota exige ADMIN. **Testado de ponta a ponta no Postgres real** (geração, listagem, download e restauração).
- **PDF de relatórios**: `RelatorioPdfService` (`business/relatorios`) gera PDF via **PDFBox 3.0.4** por tipo: `estoque-atual`, `proximos-vencimento`, `vencidos`, `produtos-abertos`, `desperdicio`, `consumo-medio`, `cmv`, `cmv-mensal`, `reposicao-sugerida` (com quebra de página e truncamento de linha). Endpoint `GET /api/relatorios/pdf/{tipo}?inicio&fim&dias&receitaBase` (produces `application/pdf`). **PDF de `cmv` validado com dados reais.**

## 10. Segurança de produção (reforçada)
- **Cookie de sessão**: `ESTOQSESSION` `HttpOnly` + `Secure` (env `SESSION_SECURE`, default `true`; `false` apenas em dev/test) + `SameSite` parametrizado por env `SESSION_SAMESITE` (default `lax`).
- **Limite de tentativas de login** (`LoginAttemptService`, em `business/auth`): janela deslizante in-memory por **e-mail+IP**; ao exceder `estoq.login.max-tentativas` (default 5) na janela `estoq.login.janela-minutos` (default 15) → `429 Too Many Requests` (mesmo com senha correta). Sucesso limpa o registro; email/IP distintos não são afetados. Valores via env `LOGIN_MAX_TENTATIVAS`/`LOGIN_JANELA_MINUTOS`.
- Proteção de fixação de sessão (`changeSessionId`) e rotação de CSRF pós-login já existiam.

## 11. Restauração de backup (validada)
O dump cobre apenas o schema `estoq_v2` (17 tabelas). Para restaurar num banco novo:

```bash
createdb -h localhost -p 5434 -U estoq estoq_restore_test
psql -h localhost -p 5434 -U estoq -d estoq_restore_test -c 'CREATE SCHEMA estoq_v2 AUTHORIZATION estoq;'
pg_restore --host localhost --port 5434 --username estoq --dbname estoq_restore_test \
  --schema estoq_v2 --no-owner --no-privileges dados/backups/estoq-*.dump
```

**Pegadinha:** como o dump usa `--schema estoq_v2`, o `pg_restore --schema` **não inclui o `CREATE SCHEMA`** (o filtro remove a entrada de schema) — sem pré-criar o schema, o restore falha com ~112 erros. Já pré-criando o schema, o restore volta com 0 erros (dados conferidos: 3 usuários, produto, lotes, movimentações, alertas). Validação real feita com dump gerado pelo `POST /api/backups` e banco temporário depois removido.

## 12. Pendências / próximos passos sugeridos
- **Fora do escopo:** módulo de vendas/PDV e receita real para a meta de %CMV (decisão do produto; `receitaBase` segue manual e opcional).
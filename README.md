# EstoQ — Controle de Estoque e CMV para Restaurantes

Backend do **EstoQ**, um sistema para gestão de estoque, lotes/validade, consumo,
desperdício, balanço físico e custo da mercadoria vendida (CMV) em restaurantes.

Projeto Integrador · SENAI FATESG · ADS · 2026

## O que resolve

O app controla o ciclo de vida completo de ingredientes: entrada no estoque,
consumo em preparações, abertura de embalagens, desperdício, balanço físico e
o custo de cada item que sai do cardápio. É voltado para restaurantes pequenos
que precisam de CMV real — sem módulo de vendas, sem approximação.

Três perfis operam simultaneamente: ADMIN (gestão), COZINHA (movimentações) e
NUTRICIONISTA (CMV e relatórios).

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.0.6, Spring Security, Spring Data JPA |
| Banco | PostgreSQL 16 (Docker) |
| Padrão arquitetônico | PIAds3 (core genérico + módulos business autocontidos) |
| PDF | PDFBox 3.0.4 |
| Build | Maven (offline-capable) |

O padrão **PIAds3** separa a camada genérica (`core/`) da camada de negócio
(`business/`). O `core` nunca importa `business`. Cada módulo de negócio é
autocontido com seu controller → service → repository.

## Estrutura

```
EstoQ_Startup/
├── docker-compose.yml
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/estoq/
│       ├── core/                  # genérico (sem import de business)
│       ├── config/security/       # SecurityConfig, filtros, handlers
│       ├── business/              # módulos de negócio
│       │   ├── auth/ usuarios/ categorias/ produtos/ lotes/
│       │   ├── entradas/ consumos/ desperdicios/ ajustes/
│       │   ├── produtosAbertos/ balancos/ itensBalanco/
│       │   ├── configuracoesBalanco/ alertas/
│       │   ├── parametrosCmv/ relatorios/ dashboard/
│       │   └── backup/            # pg_dump integrado
│       └── patterns/              # singleton para concorrência
├── dados/                         # backups locais (gitignored)
└── documentacao/                  # constituição, contrato, resumo
```

## Como rodar

```bash
# 1. Subir o PostgreSQL
docker compose up -d postgres

# 2. Rodar o backend (dev, porta 8083)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Ou compilar o JAR
mvn clean package -DskipTests
java -jar target/estoq-startup.jar --spring.profiles.active=dev
```

**Swagger UI:** `http://localhost:8083/swagger-ui.html` (somente dev)

## Usuários iniciais (seed, dev apenas)

| Perfil | E-mail | Senha |
|---|---|---|
| ADMIN | admin@estoq.com | Admin@12345 |
| COZINHA | cozinha@estoq.com | Cozinha@12345 |
| NUTRICIONISTA | nutricionista@estoq.com | Nutricao@12345 |

## Autenticação e segurança

- Sessão HTTP via cookie `ESTOQSESSION` (HttpOnly, Secure, SameSite=Lax)
- CSRF obrigatório via header `X-CSRF-TOKEN` (obtido em `GET /api/auth/csrf`)
- Limite de tentativas de login: 5 falhas em 15 minutos → `429 Too Many Requests`
  (configurável via env `LOGIN_MAX_TENTATIVAS` e `LOGIN_JANELA_MINUTOS`)
- Lock otimista em `@Version` para concorrência em lote
- Trilha de movimentações com coluna discriminadora (JOINED)

### Fluxo de login

```
GET  /api/auth/csrf       → { token, headerName }
POST /api/auth/login      → { email, senha } + header X-CSRF-TOKEN → 200 + sessão
GET  /api/auth/me          → dados do usuário logado
POST /api/auth/logout      → 204, sessão invalidada
```

## Endpoints principais

| Recurso | Rotas |
|---|---|
| Categorias e produtos | `GET/POST /api/categorias` · `GET/POST /api/produtos` · `GET /api/produtos/{id}/saldo` |
| Lotes | `GET/POST /api/lotes` · `GET /api/lotes/vencidos` · `GET /api/lotes/proximos-vencimento` |
| Estoque e movimentações | `GET /api/estoque` · `GET /api/movimentacoes` · `GET/POST /api/entradas` |
| Consumo e desperdício | `POST /api/consumos` · `POST /api/desperdicios` · `POST /api/produtos-abertos/abrir` |
| Balanço | `POST /api/balancos` · `/{id}/iniciar` · `/{id}/itens/{itemId}/contagem` · `/{id}/confirmar` · `/{id}/gerar-ajustes` |
| Alertas | `GET /api/alertas` · `PUT /api/alertas/{id}/visualizado` |
| CMV e relatórios | `GET/PUT /api/parametros-cmv` · `GET /api/relatorios/*` · `GET /api/relatorios/pdf/{tipo}` |
| Dashboard | `GET /api/dashboard/resumo` |
| Backup | `GET/POST /api/backups` · `GET /api/backups/{nome}` |

## Backup

O módulo de backup gera dumps do PostgreSQL via `pg_dump` (formato custom).

```bash
# Via API (requer perfil ADMIN)
POST /api/backups          → gera dump, mantém os N últimos
GET  /api/backups          → lista backups existentes
GET  /api/backups/{nome}   → baixa o arquivo .dump
```

**Restauração:**

```bash
createdb estoq_restore
psql estoq_restore -c "CREATE SCHEMA estoq_v2 AUTHORIZATION estoq;"
pg_restore --dbname estoq_restore --schema estoq_v2 --no-owner --no-privileges \
  dados/backups/estoq-YYYYMMDD-HHMMSS.dump
```

> **Nota:** o schema `estoq_v2` precisa existir antes do `pg_restore`. O dump
> gerado via `--schema` não inclui o `CREATE SCHEMA`.

## Variáveis de ambiente

| Variável | Default | Descrição |
|---|---|---|
| `PORT` | 8083 | Porta do servidor |
| `DATABASE_URL` | jdbc:postgresql://localhost:5434/estoq_startup?currentSchema=estoq_v2 | JDBC URL |
| `DATABASE_USER` | estoq | Usuário PostgreSQL |
| `DATABASE_PASSWORD` | estoq | Senha PostgreSQL |
| `DDL_AUTO` | validate | Modo Hibernate (dev: `update`) |
| `SESSION_SECURE` | true | Cookie Secure (dev: `false`) |
| `SESSION_SAMESITE` | lax | Cookie SameSite |
| `LOGIN_MAX_TENTATIVAS` | 5 | Limite de falhas antes de 429 |
| `LOGIN_JANELA_MINUTOS` | 15 | Janela deslizante para contagem |
| `ESTOQ_BACKUP_DIR` (opcional) | ../dados/backups | Pasta de destino dos dumps |
| `ESTOQ_BACKUP_HOST` (opcional) | herda `DATABASE_URL` | Host PostgreSQL para backup |
| `ESTOQ_BACKUP_PORTA` (opcional) | herda `DATABASE_URL` | Porta PostgreSQL para backup |
| `SEED_ENABLED` | false | Habilitar seed (dev: true) |

As variáveis `ESTOQ_BACKUP_*` são opcionais: quando ausentes, o backup usa os valores de
`DATABASE_URL` (e `localhost`/`estoq`/`estoq_startup` como último recurso).

Nunca committar o arquivo `.env`. Use `.env.example` como referência.

## Testes

```bash
cd backend
mvn test          # 38 testes verdes
mvn -o test       # offline (dependências já no .m2)
```

| Suite | O que valida |
|---|---|
| SecurityIntegrationTest | Login, sessão, CSRF, logout, desativação de usuário |
| DominioTest | Conversores, validações de domínio |
| EstoqueIntegrationTest | FIFO, abertura, consumo, desperdício, 409 por lock |
| BalancoCmvIntegrationTest | Balanço, CMV, gap, alertas, agregados |
| ConcorrenciaIntegrationTest | Duas threads no mesmo lote (Singleton + @Version) |
| LoginAttemptIntegrationTest | Limite de tentativas (429) |
| BackupPdfIntegrationTest | PDF válido, backup listável, path traversal |
| RegistroIntegrationTest | Auto-cadastro: cria loja + admin + dados padrão, e-mail duplicado 409 |
| TenantIsolamentoIntegrationTest | Isolamento entre lojas, auto-fill do tenant, suspensão, redefinição de admin, painel PLATAFORMA |

## Decisões de projeto

O projeto segue a **Constituição do Projeto** (`documentacao/EstoQ_Constituicao_do_Projeto_v1.0.docx`),
que define a arquitetura, regras de negócio e escopo. As principais decisões:

- Saldo sempre derivado do lote (nunca digitado)
- Consumo em FIFO por validade (lotes vencidos ficam de fora)
- Abertura de embalagem: o restante fica no lote, apenas o usado vira consumo
- Balanço com ajuste automático em FIFO (déficit ou superávit)
- CMV calculado por movimentações, não por fórmula estática
- Sem módulo de vendas — receita base é manual e opcional
- Multi-tenant: cada restaurante é uma loja isolada (`@TenantId` + `restaurante_id`),
  com auto-cadastro aberto e perfil PLATAFORMA para governar as lojas (suspender,
  reativar, redefinir senha do admin)

Detalhes completos em `documentacao/RESUMO_SESSAO_BACKEND.md`.

## Licença

Projeto acadêmico · SENAI FATESG · ADS 2026

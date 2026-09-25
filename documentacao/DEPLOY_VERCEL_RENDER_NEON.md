# Plano de Deploy — EstoQ (Vercel + Render + Neon)

> Custo estimado: **R$ 0/mês** (todos os serviços no plano grátis).

## Topologia

```
[Browser]
   │
   ▼
[Vercel — Frontend]  React/Vite (grátis)
   │  /api/* → rewrite para o backend ✓ (cookie fica no MESMO domínio)
   ▼
[Render — Backend]  Spring Boot (free tier, "espia" ~15min de inatividade)
   │  JDBC (SSL)
   ▼
[Neon — PostgreSQL 16]  (free, 0.5 GB)
```

**Por que proxy e não chamada direta ao backend?**
O backend usa sessão por cookie `ESTOQSESSION`. Se o frontend chamasse o
backend direto (domínios diferentes), o `SameSite=Lax` bloquearia o cookie em
fetch cross-site. Com o **rewrite `/api/*` na Vercel**, o navegador só fala com
o domínio da Vercel — cookie e CSRF funcionam exatamente como no `npm run dev`.

---

## 0. Pré-requisitos

- Conta no **Vercel** (github.com)
- Conta no **Render** (render.com — entra com GitHub)
- Conta no **Neon** (neon.tech — entra com GitHub)
- Repositório já no GitHub: `feitosapedrofatesg-svg/EstoQ_startup` ✅
- Node 20+ e Java 21 (para build local, opcional)

---

## 1. Banco — Neon (10 min)

1. Novo projeto → nome `estoq-startup`, região `South America (São Paulo)`.
2. Crie o banco `estoq_startup`.
3. Abra o **SQL Editor** e rode: `scripts/provision-neon.sql`
   (cria o schema `estoq_v2` — sem ele o app nem conecta, pois a JDBC usa
   `currentSchema=estoq_v2`).
4. Em **Connection Details → Prisma / JDBC**, copie a **JDBC URL**. Ela vem
   tipo:
   `jdbc:postgresql://ep-xyz-1.us-east-2.aws.neon.tech/estoq_startup?sslmode=require`
   Ajuste para:
   `jdbc:postgresql://ep-xyz-1.us-east-2.aws.neon.tech/estoq_startup?sslmode=require&currentSchema=estoq_v2`

> **Alternativa equivalente:** Vercel Postgres (é o mesmo Neon por baixo, 256 MB).

> **Nota — prod atual (2026-09-24):** a `DATABASE_URL` do serviço Render aponta para o
> banco `neondb` **sem** `currentSchema` — as tabelas de negócio vivem no schema
> **`public`**. O app conecta normalmente sem `currentSchema`; as migrações Flyway
> (V1–V3) aplicam no **schema default da conexão** (`public` nesse caso). O
> `currentSchema=estoq_v2` é recomendação para setups novos que queiram schema
> dedicado — as migrações funcionam em qualquer um dos dois.

---

## 2. Backend — Render (15 min)

1. **New → Web Service → "Build and deploy from a Git repository"** e selecione
   o repo `EstoQ_Startup`.
2. Como o `pom.xml` está em `backend/`, escolha **Dockerfile** nos
   configurações (já existe `backend/Dockerfile`), ou use o `render.yaml` via
   **New → Blueprint** (importa o `render.yaml` da raiz).
3. Seu serviço será criado em `https://estoq-backend-jv04.onrender.com`
   (o nome final depende do slug gerado pelo Render).
4. Defina as **env vars** do serviço (copie de `.env.example`):

   | Variável | Valor inicial | Observação |
   |---|---|---|
   | `DATABASE_URL` | JDBC do Neon (com `currentSchema=estoq_v2`) | obrigatória |
   | `DATABASE_USER` | usuário da string do Neon | obrigatória |
   | `DATABASE_PASSWORD` | senha do Neon | obrigatória |
   | `PORT` | `8080` | Render já injeta; deixe default |
   | `SESSION_SECURE` | `true` | cookie Secure |
   | `SESSION_SAMESITE` | `lax` | funciona com proxy da Vercel |
   | `DDL_AUTO` | `validate` | o schema é gerenciado pelo Flyway, não pelo Hibernate |
   | `FLYWAY_ENABLED` | `true` | migrações versionadas do schema (ver §2b) |
   | `SEED_ENABLED` | `true` pela 1ª vez, depois `false` | ver §5 |
   | `CORS_ORIGINS` | vazio | não precisa com proxy |
   | `API_DOCS_ENABLED` | `false` | |
   | `ESTOQ_BACKUP_DIR` | `/tmp/estoq-backups` | efêmero (limitação, ver §6) |
   | `ESTOQ_BACKUP_HOST/PORTA/DB/USER/PASS` | *(opcional)* | se vazios, o backup deriva host/porta/banco/usuário/senha da própria `DATABASE_URL` — não precisa configurar |

   > O Flyway roda **antes** do boot concluir e versiona o schema (`flyway_schema_history`).
   > Não use mais `DDL_AUTO=update` — isso já era só para o 1º deploy e agora é responsabilidade
   > das migrações.
   >
   > **Importante (Boot 4):** no Spring Boot 4 a auto-configuração do Flyway saiu de
   > `spring-boot-autoconfigure` para o módulo `spring-boot-flyway`. Ter só
   > `flyway-core` no pom NÃO faz as migrações rodarem. É preciso o starter
   > `org.springframework.boot:spring-boot-starter-flyway` (o bean `flyway` passa a existir
   > e o `entityManagerFactory` passa a depender dele). Foi exatamente isso que quebrou o
   > deploy de 2026-09-24: sem o starter, as colunas `restaurante_id` nunca eram criadas e
   > login/registro falhavam com `column "restaurante_id" does not exist`.

5. Aguarde o deploy. Para validar rápido, chame de um navegador:
   `GET https://estoq-backend-jv04.onrender.com/api/auth/csrf` → deve responder JSON
   `{ "token": "...", "headerName": "X-CSRF-TOKEN" }`.

## 2b. Migrações do schema (Flyway)

As migrações ficam em `backend/src/main/resources/db/migration/` e rodam em ordem na
primeira subida do serviço (e uma única vez a partir dali):

| Migração | O que faz |
|---|---|
| `V1__criar_restaurantes.sql` | Cria a tabela `restaurantes` (os tenants) |
| `V2__coluna_tenant.sql` | Adiciona `restaurante_id` + FK em **17 tabelas** de negócio e transforma as unicidades de `lotes.codigo` e `parametros_estoque.produto_id` em `unique(restaurante_id, coluna)` |
| `V3__backfill_restaurante_padrao.sql` | Dados existentes → restaurante **"EstoQ Padrão"** (id 1) e promove o **ADMIN ativo mais antigo** a **PLATAFORMA** (`restaurante_id = NULL`) |

**No banco atual de produ\u00E7ão** (que tem só o `admin@estoq.com`), o próximo deploy:
1. baselineia o histórico (o banco já existe) e aplica `V1→V3`;
2. **promove o `admin@estoq.com` a PLATAFORMA** — ele passa a governar as lojas pelo painel
   `https://…/plataforma`, e a loja dele vira "EstoQ Padrão";
3. qualquer cozinha nova passa a se **auto-cadastrar** pela tela pública de registro.

> O `baseline-on-migrate=true` (baseline em `0`) é o que permite aplicar o schema em
> bancos que já existiam antes do Flyway. Num banco **novo** (ex.: deploys futuros),
> `V3` não encontra ADMIN para promover e segue sem usuário PLATAFORMA — o que é
> correto para um app de auto-cadastro aberto.

> **Resiliência (incidente 2026-09-24):** as migrações são propositalmente idempotentes
> contra estados "sujos" que `DDL_AUTO=update` pode deixar para trás num deploy
> intermediário. A `V2` usa `add column if not exists` (o Hibernate já havia criado
> `restaurante_id` em 9 das 17 tabelas) e cria as FKs só quando não existem (PostgreSQL
> não tem `add constraint if not exists`). A `V3` derruba qualquer CHECK antigo da
> coluna `perfil` antes do promote — o de versões anteriores não incluía `PLATAFORMA`.
> Não altere `V1` (já aplicada em produção: checksum do Flyway).

**Sobre "espia" do free tier:** o serviço dorme após ~15 min sem tráfego; o
primeiro acesso depois disso demora ~30–60 s (cold start). Aceitável para
projeto acadêmico — para always-on use Railway (~US$5/mês).

---

## 3. Frontend — Vercel (10 min)

1. **Add New → Project** → importe o repo.
2. Configure:
   - **Framework Preset:** Vite
   - **Build Command:** `npm run build`
   - **Output Directory:** `dist`
   - **Root Directory:** `frontend`
4. Deploy. O site sai em `https://estoq-ph-feit0sa.vercel.app`.

O `vercel.json` em `frontend/` já contém (a URL do backend é **fixa** no arquivo,
pois rewrites da Vercel não aceitam `${VARIÁVEL}`):
- rewrite `/api/*` → `https://estoq-backend-jv04.onrender.com/api/*`
- fallback de todas as rotas → `index.html` (navegação client-side do React Router)
- headers de segurança básicos.

> **Importante:** se a URL do backend mudar, edite o `vercel.json` e reimplante
> (o `vercel.json` só é lido no build).

---

## 4. Testando em produção

> Para a validação automatizada do multi-tenant (login PLATAFORMA, auto-cadastro de
> duas cozinhas, isolamento, suspensão, redefinição de admin, backup e 403 na loja
> comum), rode `SENHA_ADMIN='...' bash scripts/e2e_validacao.sh` — as lojas de teste
> são deixadas suspensas no final.

1. Abra `https://estoq-oficial.vercel.app` → deve mostrar a tela de login, com link
   **"Crie a conta agora"** (auto-cadastro público).
2. **Multi-tenant (validação principal):**
   - Cadastre duas cozinhas (ex.: "Cantina A" e "Cantina B") pela tela de registro.
   - Entre em cada uma e confirme que **não** enxerga os dados da outra (produtos,
     categorias, usuários) — cada loja acessa uma base isolada (`restaurante_id`).
   - As duas lojas nascem com o painel completo (CMV 30%, balanço mensal) e backup.
3. Entre com o usuário **PLATAFORMA** (no banco atual, o `admin@estoq.com` — veja §2b):
   - `https://estoq-oficial.vercel.app` → login → cai direto no painel **Restaurantes**.
   - **Suspenda a "Cantina B"** e confirme que o login dela passa a dar **401**.
   - **Redefina a senha do admin** de B pela plataforma e entre com a nova senha.
   - Confirme que uma loja comum **não** vê `/api/plataforma/**` (403).
4. Verifique navegação, abrir/fechar modais e relatórios PDF (o PDF é gerado
   no backend e baixado via blob — funciona com o proxy).

---

## 5. Seed de usuários em produção

O seed (`SeedDataConfig`) só roda no perfil `dev`. No primeiro boot em prod,
defina `SEED_ENABLED=true` para criar o restaurante **"EstoQ Padrão"** + os 3
usuários (ADMIN/COZINHA/NUTRICIONISTA, senhas padrão do README). **Altere as
senhas imediatamente** na tela Usuários e depois desligue `SEED_ENABLED=false`.

> Não existe seed de usuário **PLATAFORMA**: ele vem da migração `V3` (promoção do
> ADMIN ativo mais antigo) num banco que já tinha dados — ou não existe, se o banco
> nasceu depois do multi-tenant. Isso é de propósito: plataforma é autoridade que
> nasce da evolução, não de seed.

Se preferir sem seed: crie o admin direto no banco com senha BCrypt, ex.:
```sql
INSERT INTO estoq_v2.tb_usuario (nome, email, senha, perfil, ativo, restaurante_id, data_hora_criacao, versao)
VALUES ('Admin', 'admin@estoq.com', '<hash_bcrypt>', 'ADMIN', true, 1, now(), 0);
```

---

## 6. Limitações conhecidas do plano "barato" (importante)

1. **Filesystem efêmero (backups).** O módulo `backup/` roda `pg_dump` contra a
   própria `DATABASE_URL` (sem configuração extra) e salva o `.dump` no disco
   local, que no Render free é **efêmero** — some a cada reinício/deploy.
   → Por isso, desde 2026-09 o botão **"Gerar backup agora" baixa o dump
   imediatamente** (o POST devolve o arquivo); o arquivo no servidor é só um
   extra temporário. Mantenha também o **backup nativo do Neon** (painel →
   Backups) como camada extra de segurança.
2. **Sessão em memória:** com 1 instância (caso atual) funciona. Escalar para
   2+ instâncias exige Spring Session (JDBC/Redis) + sessão sticky — fora do
   escopo atual.
3. **Rate-limit de login em memória:** idem — reinicia a cada cold start.
4. **Cold start** no free do Render (~30–60 s no primeiro acesso).
5. **Vercel free:** limits de banda/build mensais (generosos para o projeto).

---

## 7. Rollback e observabilidade

- **Vercel:** Redeploy de um deployment anterior (Production Deployments).
- **Render:** Deploy Automático ON; reimplante manual revertendo para commit
  anterior no painel.
- **Logs:** Render → seu serviço → **Logs** (stdout do Spring Boot);

---

## 8. Melhorias futuras de produção (fora do escopo "barato")

- Backup real: dump agendado do Neon para S3/R2, ou Postgres no Supabase com
  PITR.
- Domínio próprio + `SameSite` relaxado, ou Spring Session para multi-instância.
- CI/CD: GitHub Actions com `mvn test` antes de merge (o `.github/` já existe
  para modernização do Java — estender para build+test no push).

> ✅ Implementado em 2026-09: **Flyway** com migrações versionadas (V1–V3),
> auto-cadastro de cozinhas e painel PLATAFORMA (suspender/reativar/redefinir
> senha do admin).
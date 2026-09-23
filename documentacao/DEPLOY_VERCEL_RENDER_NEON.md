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
   | `DDL_AUTO` | `update` | **1º deploy só**: cria as tabelas |
   | `SEED_ENABLED` | `true` pela 1ª vez, depois `false` | ver "Seed no prod" |
   | `CORS_ORIGINS` | vazio | não precisa com proxy |
   | `API_DOCS_ENABLED` | `false` | |
   | `ESTOQ_BACKUP_DIR` | `/tmp/estoq-backups` | efêmero (limitação, ver §6) |

   > Após o primeiro boot OK, troque `DDL_AUTO` para `validate` e reimplante.
   > Deixe `SEED_ENABLED=true` no primeiro boot para criar o usuário admin
   > inicial, depois mude para `false`.

5. Aguarde o deploy. Para validar rápido, chame de um navegador:
   `GET https://estoq-backend-jv04.onrender.com/api/auth/csrf` → deve responder JSON
   `{ "token": "...", "headerName": "X-CSRF-TOKEN" }`.

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

1. Abra `https://estoq-ph-feit0sa.vercel.app` → deve mostrar a tela de login.
2. Primeiro login com usuário criado pelo seed (ver §5).
3. Verifique navegação, abrir/fechar modais e relatórios PDF (o PDF é gerado
   no backend e baixado via blob — funciona com o proxy).

---

## 5. Seed de usuários em produção

O seed (`SeedDataConfig`) só roda no perfil `dev`. No primeiro boot em prod,
defina `SEED_ENABLED=true` para criar os 3 usuários (com as senhas padrão do
README). **Altere as senhas imediatamente** na tela Usuários e depois desligue
`SEED_ENABLED=false`.

Se preferir sem seed: crie o admin direto no banco com senha BCrypt, ex.:
```sql
INSERT INTO estoq_v2.tb_usuario (nome, email, senha, perfil, ativo, data_hora_criacao, versao)
VALUES ('Admin', 'admin@estoq.com', '<hash_bcrypt>', 'ADMIN', true, now(), 0);
```

---

## 6. Limitações conhecidas do plano "barato" (importante)

1. **Backup `pg_dump` não funciona em PaaS.** O módulo `backup/` chama o
   binário `pg_dump` + filesystem local → no Render free não existe/é efêmero.
   → **Use o backup nativo do Neon** (painel → Backups; o free tier tem
   backup de dados básicos). Deixe a página **Backup** do estoQ desabilitada
   no menu (ou ignore-a em prod).
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

- Flyway/Liquibase para migrações de schema (trocar `ddl-auto` de vez).
- Backup real: dump agendado do Neon para S3/R2, ou Postgres no Supabase com
  PITR.
- Domínio próprio + `SameSite` relaxado, ou Spring Session para multi-instância.
- CI/CD: GitHub Actions com `mvn test` antes de merge (o `.github/` já existe
  para modernização do Java — estender para build+test no push).
# Migração do backend para a região Virgínia (junto ao banco)

**Data:** 18/10/2026 (à noite) · **Status:** concluída e testada

## Resumo

O backend saiu da região **Oregon (costa oeste dos EUA)** do Render e passou para
**Virgínia (costa leste)** — a mesma região onde está o banco Supabase. Isso
elimina a viagem transcontinental que acontecia a **cada consulta ao banco**.

| | Antes | Depois |
|---|---|---|
| Serviço no Render | `estoq-backend` (`srv-daoqm0mgekts73emglrg`) | `estoq-backend-va` (`srv-dbanrbc9v7es7398j3qg`) |
| Região | Oregon (us-west) | **Virgínia (us-east)** |
| URL do backend | `https://estoq-backend-jv04.onrender.com` | **`https://estoq-backend-va.onrender.com`** |
| Banco | Supabase `us-east-1` (Virgínia) | **igual — nada mudou** |
| Plano / custo | Free / R$ 0 | Free / R$ 0 |

## Por que

- Cada consulta backend↔banco custava ~0,2–0,4s de distância (Oregon ⇄ Virgínia).
- Telas do sistema fazem **várias** consultas por carga; o atraso se acumulava.
- O Render **não tem** região no Brasil, então Virgínia (do lado do banco no
  Supabase `us-east-1`) é a melhor combinação possível nos provedores atuais.

## O que NÃO mudou

- **Banco e dados:** nenhuma migração; o Supabase ficou exatamente onde estava.
- **Site (Vercel):** mesmos endereços (`estoq-oficial.vercel.app` etc.).
- **Custo:** continua no plano Free.
- Único efeito visível: todos precisam **logar de novo uma vez** (a sessão fica
  em memória no servidor e mudou de máquina).

## Como foi feito

1. Serviço novo criado via API do Render em `virginia`, copiando do antigo o
   repositório, branch, Dockerfile (`backend/Dockerfile`), plano e health check.
2. As **14 variáveis de ambiente** foram copiadas (Supabase, `DDL_AUTO`,
   `FLYWAY_ENABLED`, etc.).
3. Deploy acompanhado até `live`; testado (healthchecks + login `admin@estoq.com`
   → HTTP 200, perfil PLATAFORMA).
4. Ponteiros trocados para a URL nova:
   - `frontend/vercel.json` (regra de rewrite `/api/*`) — este commit;
   - `keepalive/src/index.js` (worker do Cloudflare);
   - `.env.example` e `render.yaml` atualizados por consistência.
5. Serviço antigo **suspenso** (não apagado ainda) como rede de segurança.

## Medições (rede do usuário → serviço, direto)

| | `/api/saude/vivo` (sem banco) | `/api/saude/pronto` (com banco) | custo do banco por consulta |
|---|---|---|---|
| Antes (Oregon) | 0,41–0,44s | 0,63–0,80s | **+0,19–0,37s** |
| Depois (Virgínia) | 0,48–0,67s | 0,48–0,79s | **~0,00–0,12s** |

Leitura: o tempo absoluto ainda é dominado pela distância do usuário até o
servidor (parecida nas duas regiões) — o ganho estrutural é **backend↔banco**,
que é o que multiplica dentro de cada tela: antes **cada** ida ao banco custava
~0,2–0,4s; agora custa ~0. Telas que fazem 5–15 consultas sentem a diferença
direto.

## Keep-alive (Cloudflare) — agora com papel duplo

`keepalive/src/index.js` passou a chamar **`/api/saude/pronto`** (em vez de
`/api/saude/vivo`) a cada 10 minutos:

- mantém o **Render free** acordado (ele dorme após ~15 min sem tráfego);
- e mantém o **Supabase free ativo** — projetos free do Supabase **pausam após
  7 dias sem atividade**, e `/pronto` faz uma consulta real ao banco.

O worker continua sendo publicado manualmente: `cd keepalive && wrangler deploy`.

## Serviço antigo (Oregon)

- **Suspenso** (não apagado) para permitir rollback rápido se algo aparecer.
- Reativar: dashboard do Render → serviço `estoq-backend` → *Resume*, ou
  `POST /v1/services/srv-daoqm0mgekts73emglrg/resume` na API.
- **Apagar definitivamente** depois de alguns dias de observação sem problemas
  (evita consumir horas de instância do plano Free).

## Rollback (como voltar tudo)

1. `git revert` deste commit (devolve `vercel.json` e o keepalive para a URL antiga).
2. Resumir o serviço `estoq-backend` (Oregon) no Render.
3. Rerodar `wrangler deploy` na pasta `keepalive`.
4. Funciona **enquanto o serviço antigo existir**.

## Pendências relacionadas

- Apagar o serviço antigo após o período de observação.
- Itens ainda abertos da migração de banco (`documentacao/DEPLOY_SUPABASE.md`):
  desativar Data API do Supabase, validar backup/restauração.
- Opcional: plano Starter (US$ 7/mês) por mais CPU — a hibernação já está
  resolvida pelo keep-alive.

## Referências

- `documentacao/DEPLOY_SUPABASE.md` — migração de banco (autoritativa).
- Docs antigos que citam a URL antiga ficam como **registro histórico**:
  `RESUMO_SESSAO_BACKEND.md`, `DEPLOY_VERCEL_RENDER_NEON.md`.

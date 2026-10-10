# Commits e fluxo de trabalho Git

Repositório local: `/home/pedro/EstoQ`. Remote: `https://github.com/feitosapedrofatesg-svg/EstoQ_startup.git`.

## Branches

O projeto usa somente `dev` e `main`:

| Branch | Papel |
| --- | --- |
| `dev` | Desenvolvimento e validação das mudanças. |
| `main` | Versão publicada; o push dispara o deploy automático. |

## Fluxo

```bash
git fetch origin
git switch dev
git merge origin/main
# Implementar e validar antes de publicar.
git add <arquivos>
git commit -m "fix(<escopo>): <resumo>"
git push origin dev
git switch main
git merge --ff-only dev
git push origin main
```

Se houver commits distintos nas duas branches, integrar os históricos e resolver os conflitos antes de publicar. Nunca usar force push para substituir trabalho remoto.

## Mensagens

Usar Conventional Commits com resumo concreto: `fix`, `feat`, `test`, `docs` ou `chore`, com escopo quando ajudar. Cada commit deve representar uma mudança coesa.

## Validação

- Backend: `cd backend && mvn test` (Java 21; os testes de migração abrem PostgreSQL temporário).
- Frontend: `npm --prefix frontend ci` e `npm --prefix frontend run build`.
- Verificar `git diff --check` e revisar os arquivos antes do push.
- O modelo `documentacao/ci.yml.example` executa os testes do backend e o build do frontend nas duas branches e em pull requests. Para ativar, publicar como `.github/workflows/ci.yml` usando uma credencial com permissão `workflow`; a credencial atual não permite publicar workflows.
- Após publicar `main`, acompanhar o deploy e verificar o site e a rota `/api/auth/csrf`.

## Arquivos locais

Não versionar credenciais, `.env`, dumps, `dados/`, `backend/target/` ou `frontend/dist/`. O `.gitignore` mantém esses arquivos fora do histórico. Scripts de manutenção e carga ficam em `scripts/`.

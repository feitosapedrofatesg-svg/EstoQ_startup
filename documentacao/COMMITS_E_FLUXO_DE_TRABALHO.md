# EstoQ_Startup — Commits e fluxo de trabalho Git

Documenta como o histórico foi organizado no repositório, as convenções de mensagem e o fluxo de branches. Local do repo: `/home/pedro/EstoQ_Startup` · remote: `origin` (`https://github.com/feitosapedrofatesg-svg/EstoQ_startup.git`).

---

## 1. Visão geral

O repositório usa **três branches** com papéis distintos:

| Branch   | Papel                                         | Estado atual (HEAD)                        |
|----------|-----------------------------------------------|--------------------------------------------|
| `master` | Produção / ponto de entrega                    | `8a284e8` merge do backend em main        |
| `main`   | Branch estável padrão (default)                | `8a284e8` merge do backend em main        |
| `dev`    | Desenvolvimento (commits atômicos e contínuos) | `1d882ce` suíte de testes                 |

Fluxo adotado: **o trabalho acontece em `dev`, commit por commit; quando um conjunto fecha, `dev` é integrado em `main` por merge; `master` é criada/apontada para o estado entregue.** Assim `main` e `master` ficam sempre em estado estável e `dev` carrega o histórico detalhado do desenvolvimento.

---

## 2. Convenção de mensagens

Mensagens em português, no padrão **Conventional Commits** (mesmo modelo do commit inicial do professor):

- `<tipo>(<escopo>): <resumo em uma linha>`
- Tipos usados: `feat` (funcionalidade nova), `test` (suíte de testes), `docs` (documentação), `chore` (bootstrap/configuração), `fix` (correção — ainda não usado), `merge` (integração).
- **Escopo** = tema/domínio do commit, ex.: `feat(catalogo)`, `feat(backup)`, `test`.
- O corpo (opcional) detalha o que foi feito e por quê. Sem clichês, sem emojis.

Exemplos reais:

```
feat(usuarios): autenticação por sessão, perfis e auditoria

Login e-mail+senha (BCrypt), sessão HTTP com cookie HttpOnly + CSRF, usuários
ADMIN/COZINHA/NUTRICIONISTA, desativação revoga sessão e trilha de auditoria.
```

```
feat(movimentacoes): entradas, consumo, desperdício, ajustes e abertos

Trilha de movimentações (JOINED + coluna tipo) com quantidade anterior/posterior
e delta. FIFO por validade no consumo, desperdício inclui vencidos, ajuste de
balanço em FIFO e produtos abertos com sobra reaproveitada.
```

### Regras seguidas
- **Tudo que não deve ir para o GitHub fica fora do commit**: `.env`, `.env.*` (exceto `.env.example`), `*.dump`, `*.jar`, `dados/` e `backend/target/` estão no `.gitignore`. O JAR de build e o backup real **não** entram no histórico.
- **Um commit = um tema coeso**: nunca se mistura por exemplo autenticação com relatórios.
- Mensagens erradas são corrigidas com `git commit --amend` **antes** do push (como no commit de testes, que tinha "dirty de 29 testes" e foi corrigido para "29 testes").

---

## 3. Histórico de commits (`dev` → `main`)

Cronologia real do repo (do mais antigo para o mais novo):

| Commit | Branch | Resumo |
|--------|--------|--------|
| `ec967d8` `chore: scaffold do projeto` | `main` | Primeiro upload (`.gitignore`, `docker-compose.yml`, `README.md`). |
| `49cbcb8` `docs: constituição do projeto, contrato de backend e resumo da sessão` | `main` | Documentos em `documentacao/` (constituição, prompt/contrato, resumo). |
| `7819aa4` `feat(core): núcleo PIAds3, configuração, segurança e seed` | `dev` | `pom.xml`, `EstoqApplication`, `core/` genérico, `config/security`, resources e seed dev. |
| `a42805a` `feat(usuarios): autenticação por sessão, perfis e auditoria` | `dev` | `business/usuarios`, `business/auth` (+ `LoginAttemptService`), `business/auditoria`. |
| `a12eff2` `feat(catalogo): categorias, produtos, parâmetros de estoque e lotes` | `dev` | Catálogo completo com FIFO por validade e conversor de unidades. |
| `341724f` `feat(movimentacoes): entradas, consumo, desperdício, ajustes e abertos` | `dev` | Trilha de movimentações e operações de baixa/sobra. |
| `dc7ba2a` `feat(balanco): balanço físico, ajustes automáticos e ciclo de alertas` | `dev` | Balanço, itens, configuração periódica e alertas. |
| `1091fb8` `feat(cmv): relatórios de CMV, consumo médio e dashboard` | `dev` | Indicadores, reposição sugerida e PDF (PDFBox). |
| `cb7f5b1` `feat(backup): dumps via pg_dump e padrão de concorrência` | `dev` | Backup/restauração real e singleton de concorrência. |
| `1d882ce` `test: suíte de integração e domínio (29 testes, perfil concorrencia)` | `dev` | 9 arquivos de teste cobrindo segurança, domínio, estoque, balanço/CMV, concorrência e backup/PDF. |
| `8a284e8` `merge: integra o desenvolvimento do backend em main` | `main` | Merge `dev` em `main` (com commit de merge explícito, `--no-ff`). |

`master` aponta para o mesmo commit de merge (`8a284e8`), representando o estado entregue.

### Números do histórico
- 11 commits no total (2 em `main` + 9 no fluxo `dev` + 1 merge).
- 183 arquivos rastreados, ~7.000 linhas de código entregues no merge.

---

## 4. Fluxo de trabalho (como reproduzir)

```bash
# Nova feature começa sempre em dev
git checkout dev

# Modifica, testa e commita em partes coesas
git add backend/src/main/java/com/estoq/business/<modulo>/
git commit -m "feat(<escopo>): <resumo>"

# Ao fechar um conjunto, integra em main (merge com commit explícito)
git checkout main
git merge dev --no-ff -m "merge: integra o desenvolvimento em main"

# Atualiza a branch de produção se a entrega for fechada
git branch -f master main

# Publica tudo
git push origin main dev master
```

Ferramentas/explicações que já foram aplicadas nesta etapa:

- `GIT_TERMINAL_PROMPT=0 GIT_ASKPASS=/bin/echo` no push: evita que o terminal **trave** esperando credencial quando ela não existe (o push destravou porque o credential helper `store` já tinha o token).
- `git commit --amend` para corrigir mensagem do último commit ainda não enviado.
- O **merge com `--no-ff`** preserva o commit de merge e deixa o histórico de `dev` visível no grafo, mesmo quando a integração é fast-forwardável.

### Diagrama do grafo final

```
*   8a284e8  merge: integra o desenvolvimento do backend em main   <- main = master
|\
| *  1d882ce  test: suíte de integração e domínio (29 testes...)   |
| *  cb7f5b1  feat(backup): dumps via pg_dump e padrão de concorrência   |
| *  1091fb8  feat(cmv): relatórios de CMV, consumo médio e dashboard     | dev
| *  dc7ba2a  feat(balanco): balanço físico, ajustes automáticos...        |
| *  341724f  feat(movimentacoes): entradas, consumo, desperdício...       |
| *  a12eff2  feat(catalogo): categorias, produtos, parâmetros...          |
| *  a42805a  feat(usuarios): autenticação por sessão, perfis...           |
| *  7819aa4  feat(core): núcleo PIAds3, configuração, segurança e seed    |
|/
*  49cbcb8  docs: constituição do projeto, contrato e resumo da sessão
*  ec967d8  chore: scaffold do projeto
```

---

## 5. O que fica de fora do Git (`.gitignore`)

O `.gitignore` novo protege itens sensíveis e artefatos gerados:

```
.env
.env.*
!.env.example
.~lock.*
~$*
*.jar
*.dump
dados/
backend/target/
```

- `dados/` guarda o dump antigo e o tar.gz da base pré-refatoração — são referência local, não entram no repo.
- `scripts/` existe mas está vazio; quando ganhar conteúdo entra normalmente (não está no `.gitignore`).
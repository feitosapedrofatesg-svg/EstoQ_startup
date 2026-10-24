# Commits, branches e validação

Revisão: **20/10/2026**. Repositório local: `/home/pedro/estoq-cmv`.
Remote: `https://github.com/feitosapedrofatesg-svg/EstoQ_startup.git`.

## Branches e entrega

| Branch | Função |
| --- | --- |
| `dev` | Desenvolvimento das mudanças e validação inicial. |
| `homologacao` | Versão candidata, testes e aprovação antes de publicar. |
| `main` | Versão publicada; push dispara os deploys automáticos. |

Não há fluxo com master. Atualize referências, integre trabalho remoto e revise
as alterações antes de publicar. Não substitua histórico remoto com force push.

```bash
git fetch origin
git switch dev
git merge --ff-only origin/dev
git merge --ff-only origin/main
# Implementar, revisar o diff e executar as verificações necessárias.
git add <arquivos>
git commit -m "fix(<escopo>): <resultado concreto>"
git push origin dev
# Aguardar a CI da dev; então preparar a candidata.
git switch homologacao
git merge --ff-only origin/homologacao
git merge --ff-only dev
git push origin homologacao
git rev-parse HEAD
# Aguardar a CI, testar e registrar o hash aprovado em homologacao.
git switch main
git merge --ff-only origin/main
git merge --ff-only HASH_APROVADO
git push origin main
```

Substitua `HASH_APROVADO` pelo hash completo testado em homologacao. Não integre
dev diretamente em main: a dev pode ter recebido mudanças depois dos testes.
Após o push, verifique a publicação.
Veja o [guia de homologação](HOMOLOGACAO.md) para os comandos completos e as
diferenças entre a branch e um ambiente de testes separado.

Se fast-forward não for possível, conferir a divergência, integrar os commits e
resolver conflitos antes de continuar. Os comandos acima não descartam alterações.
Use Conventional Commits: feat, fix, docs, test ou chore, com escopo útil.

## CI ativa

O workflow real é [.github/workflows/validacao.yml](../.github/workflows/validacao.yml),
ativo em pushes e pull requests para dev/homologacao/main. `ci.yml.example` é uma cópia de
referência; não precisa ser instalado como outro workflow.

- Backend: Java 21, Tesseract em português e `mvn test`.
- Frontend: Node 24, `npm ci`, build, instalação do Chromium e Playwright.
- Verificação local de arquivos: `git diff --check` e revisão do diff.

```bash
mvn -f backend/pom.xml test
npm --prefix frontend ci
npm --prefix frontend run build
npm --prefix frontend test
```

Localmente, instale o navegador Playwright conforme o ambiente de testes.
`PLAYWRIGHT_CHROMIUM_EXECUTABLE` pode apontar para um Chrome já disponível.
Não confundir execução de teste com build de produção nem pular testes de
concorrência quando mudar os contratos de balanço.

## Commit enviado e site publicado são estados diferentes

`git push` atualiza o GitHub. GitHub Actions valida; Vercel e Render publicam.
Dois checks bem-sucedidos e um check Vercel pendente podem significar que a
publicação ainda está em andamento. Confira o SHA do deployment, não publique um
novo commit apenas porque o anterior ainda está sendo processado.

Após publicar main, confira CI, deployment, login/navegação e `/api/auth/csrf`.
Para backend/frontend com contrato incompatível, publique o backend compatível
primeiro e verifique disponibilidade antes de liberar o frontend dependente.
O header `X-Estoq-Sincronizacao: 3` identifica o protocolo, não o último commit.

## Documentação e arquivos locais

Atualize [HISTORICO_DE_MUDANCAS.md](HISTORICO_DE_MUDANCAS.md) e os contratos
correspondentes. Mudança apenas documental não requer nova suíte de negócio;
verifique links, comandos, referências e integridade dos arquivos Word.

Não versionar credenciais, `.env`, dumps, fotos/notas fiscais reais, `dados/`,
`backend/target/`, `frontend/dist/` ou resultados de testes. Scripts ficam em
`scripts/`; arquivos SQL gerados pela carga devem ser refeitos pelo gerador.

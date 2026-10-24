# Frontend — atalho de nota fiscal e permissões da cozinha

Revisão: **21/10/2026**. Commits: `66e434e` e `414175d`.

Este documento registra duas mudanças de interface feitas depois da aplicação do
tema Nutrigo. Nenhuma alteração de API, banco ou regra de negócio foi necessária.
O tema visual continua documentado em [TEMA_NUTRIGO.md](TEMA_NUTRIGO.md).

## 1. Importar nota fiscal no Catálogo

O cabeçalho de **Catálogo** passou a exibir o botão **Importar nota fiscal** ao
lado de **Novo produto**. O botão usa o mesmo `LinkButton` já presente em
Movimentações e navega para `/notas-fiscais`, sem nova lógica de importação.

- Visível apenas para **ADMIN**, assim como **Novo produto**.
- A rota `/notas-fiscais` já era protegida por `Guarded allow={isAdmin}`.
- Ordem no desktop: **Importar nota fiscal** (contorno) e **Novo produto**
  (primário), com 10 px entre os dois.
- Em 390 px e 320 px, os botões ocupam a largura disponível e quebram em duas
  linhas, preservando área de toque de 44 px e ausência de rolagem horizontal.
- Não há mudança no fluxo existente de leitura, revisão ou confirmação da nota.

Arquivo alterado: `frontend/src/pages/Catalogo.tsx`.

## 2. Movimentações fora do perfil Cozinha

O item **Movimentações** foi removido do menu do perfil **COZINHA**. O acesso
direto pela URL também foi bloqueado: a rota `/movimentacoes` passou de
`allow={canMove}` para `allow={isAdmin}`.

- **ADMIN:** mantém o item no menu e o acesso à tela.
- **COZINHA:** perde o item e, ao abrir `/movimentacoes`, vê a mensagem de área
  indisponível para o perfil.
- **NUTRICIONISTA:** já não tinha acesso e continua sem acesso.
- A cozinha mantém **Estoque**, **Consumo**, **Desperdício**, **Balanço físico**
  e **Catálogo**.

A API `GET /api/movimentacoes` não foi restringida. Ela também alimenta as telas
de Consumo e Desperdício, que continuam disponíveis para a cozinha. A restrição
desta entrega é de navegação e acesso à tela, não do endpoint de consulta.

Arquivos alterados: `frontend/src/components/Layout.tsx` e
`frontend/src/App.tsx`.

## Validação

- Teste novo em `frontend/tests/multitenant.spec.ts`: confirma que a cozinha não
  encontra o link no menu e recebe a tela de acesso negado na URL direta.
- Build de produção aprovado.
- Suíte completa com **89 testes Playwright** aprovada no CI.
- CI aprovada em `dev` (`39650387998`) e `homologacao` (`39650748323`).
- Homologação conferida com login real:
  - ADMIN continua vendo e abrindo Movimentações.
  - Catálogo a 1440 px mostra o atalho ao lado de Novo produto; a 390 px e
    320 px não há overflow.
  - Clique no atalho chega a `/notas-fiscais`.
  - Cozinha simulada no navegador não vê o link e é bloqueada na URL direta.
  - Zero erros JavaScript e zero requisições aos hosts de produção.

O roteiro completo, incluindo a ressalva sobre a simulação do perfil, está em
[HOMOLOGACAO_DETALHADA_2026_10_21.md](HOMOLOGACAO_DETALHADA_2026_10_21.md).

## Fora de escopo e reversão

Não houve alteração de layout além do cabeçalho do Catálogo e da remoção do
item de menu da cozinha, nem mudança nas regras de permissão do backend, nos
dados ou em migrações. As duas mudanças podem ser
revertidas de forma independente pelos commits `66e434e` e `414175d`, sem
restauração de banco.

Produção e branch `main` não foram alteradas por esta entrega.

## Atualização de 22/10/2026 — nome visível Operacional

O perfil `COZINHA` agora é exibido como **Operacional**. Os rótulos do menu e
de Usuários são compartilhados em `frontend/src/lib/perfis.ts`. O nome aparece
também nas explicações de acesso, de Balanço e do Dashboard. A descrição do
formulário indica **estoque e operação diária**, sem sugerir acesso à tela de
Movimentações.

Não foram alterados os valores dos formulários (`COZINHA`), os payloads de
criação/edição, o enum do backend, as permissões nem o banco. Menções a cozinha
como estabelecimento ou ambiente de trabalho permanecem como estavam.

Validação local: build aprovado, 5 testes de configuração e **97 testes
Playwright** aprovados. A cobertura inclui o rótulo em 320 e 1440 px, os acessos
do perfil e a preservação de `COZINHA` ao criar e editar usuários existentes.

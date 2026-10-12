# Responsividade do EstoQ

Revisão: **12/10/2026**.

## Objetivo

Este documento explica as decisões de responsividade implementadas no estoQ e as heurísticas usadas para adaptar a interface a desktop, tablet, celular e dispositivos híbridos com tela touch. A meta é manter as tarefas operacionais legíveis e diretas, sem rolagem horizontal da página e sem esconder ações importantes.

## Princípios de adaptação

- **Priorizar a tarefa:** campos, quantidades e ações frequentes continuam visíveis; no balanço, o botão de confirmação fica junto ao produto correspondente.
- **Usar o espaço disponível:** larguras fixas são reduzidas ou substituídas por `min-width: 0`, colunas fluidas e quebra de linha quando a tela estreita.
- **Reduzir a densidade gradualmente:** painéis e colunas passam a ocupar uma coluna ou cartões menores em vez de simplesmente encolher todo o conteúdo.
- **Conter a rolagem:** a página não deve rolar horizontalmente. Até 1024 px, as tabelas gerais usam cartões com rótulos; até 1280 px, o conteúdo quebra dentro das colunas. A contagem do balanço conserva seus cartões próprios.
- **Facilitar o toque:** controles importantes têm áreas de interação próximas de 44 px ou maiores em dispositivos que oferecem ponteiro touch.
- **Preservar contexto:** nomes, rótulos e valores continuam identificáveis quando uma tabela muda para apresentação em cartões.

## Faixas e pontos de adaptação

Os valores são limites CSS, não modelos rígidos de aparelho. Um tablet pode usar o layout de celular se sua janela estiver abaixo do limite, e um notebook touch pode combinar mouse e toque.

| Limite | O que muda | Motivo |
| --- | --- | --- |
| `1280 px` | A sidebar fixa se transforma em navegação recolhida; aparece o botão de menu, o conteúdo deixa de reservar espaço para a sidebar e o fundo escurecido permite fechar o menu. | Dar prioridade ao conteúdo principal em tablets, inclusive alguns em paisagem, sem depender do tipo de ponteiro. |
| `1024 px` | O espaçamento lateral diminui e tabelas gerais passam a cartões com rótulos. | Recuperar área útil e manter todas as colunas acessíveis sem rolagem lateral. |
| `960 px` | Blocos de dashboard que dividiam duas colunas passam a uma coluna. | Manter gráficos e indicadores legíveis sem comprimir cada painel. |
| `860 px` | A tela de login e comparações de CMV empilham seus painéis. | Evitar duas colunas estreitas e manter o formulário utilizável. |
| `720 px` | Filtros ocupam a largura disponível, resumos reduzem colunas, espaçamentos de cartões e modais diminuem e a contagem do balanço vira uma lista de cartões. | Adequar fluxos densos ao celular e reduzir bordas e margens acumuladas. |
| `600 px` | A abertura da tela de login fica mais baixa; vídeo e texto da marca ficam em áreas separadas. | Evitar que a marca fique sobreposta à mídia e liberar altura para o formulário. |
| `any-pointer: coarse` | Alvos de toque são ampliados, inclusive quando o aparelho também oferece mouse ou trackpad. | Notebooks híbridos frequentemente informam mouse como ponteiro principal, embora aceitem toque. |

Os pontos também aparecem em regras específicas: por exemplo, indicadores resumidos mudam de seis para três colunas em `1180 px` e para duas em `720 px`; formulários de duas ou três colunas passam a uma coluna em `720 px`.

## Heurísticas por interface

### Navegação

Até `1280 px`, a sidebar deixa de ocupar permanentemente 264 px da tela. A navegação é aberta pelo botão do menu e fechada ao escolher uma rota ou tocar no fundo escurecido. O conteúdo principal passa a usar a largura da janela. Essa decisão atende tanto tablets quanto telas menores sem exigir que o dispositivo seja touch.

### Login

Até `860 px`, os painéis deixam de ficar lado a lado. Em até `600 px`, a faixa de marca é compactada: a mídia tem altura limitada e a mensagem fica abaixo dela, em fluxo normal, em vez de sobreposta. Isso reduz o cabeçalho e evita conflito visual em celulares estreitos.

### Busca, filtros e datas

Em até `720 px`, campos de busca usam a largura disponível. Filtros podem quebrar linha; datas dividem o espaço em partes iguais, sem manter largura mínima de desktop. Os valores e controles não devem empurrar o documento para além da viewport.

### Resumos, painéis e modais

Resumos que tinham três colunas passam a duas em celular, com conteúdo longo podendo quebrar linha. Cabeçalhos e ações de cartões podem empilhar. Modais recebem margens menores, altura limitada à viewport e rodapé que pode quebrar linha; o corpo continua rolável verticalmente.

### Tabelas

Tabelas gerais preservam sua estrutura no DOM e apresentam linhas em cartões até
1024 px. Cada célula recebe um rótulo correspondente ao cabeçalho; nomes, ações
e células com múltiplas colunas podem ocupar a linha inteira. Os cabeçalhos ficam
disponíveis para leitores de tela. Entre 1025 e 1280 px, colunas distribuem a
largura e quebram texto. A contagem do balanço mantém seu tratamento específico.

## Organização e validação da revisão de 12/10/2026

O módulo `frontend/src/responsividade/` contém as pastas `desktop`, `celular` e
`tablet`, com um ponto de entrada `index.css`. Layout e dimensões foram extraídos
de `styles.css`; fontes, cores e aparência permanecem nesse arquivo. Os estilos
anteriores de cada faixa estão em `legado.css`, e as correções em `ajustes.css`.
Não há páginas duplicadas nem estados separados por tamanho de tela.

O conteúdo usa toda a largura disponível até 1280 px. Nomes longos, filtros,
abas, botões e valores quebram sem empurrar a página. Modais respeitam `100dvh`
e áreas seguras; o cabeçalho e os atalhos de nota fiscal também consideram os
recortes do aparelho. A mídia e a mensagem do login ficam separadas no tablet.

A skill `mobile-converter` foi aplicada para investigar largura, toque e altura
dinâmica. Sugestões de trocar navegação, fontes e cores não foram adotadas,
conforme a restrição desta revisão. A auditoria estática passou com nota 85:
restam três avisos de tabelas que a análise isolada não relaciona aos estilos
externos (tabela geral, HTML de impressão e tabela simples de CMV). O teste
renderizado verifica o encaixe real. A verificação visual também sinaliza o
contraste dos links laranja já existentes; cores foram preservadas. Três alertas
automáticos do banner de login atribuíram o fundo claro da página ao texto
branco; a captura inspecionada mostra esse texto sobre o painel escuro existente.

A suíte frontend tem 40 testes aprovados. A matriz responsiva cobre 320, 390,
720, 768, 1024, 1180 e 1440 px, incluindo nomes e números extensos, modal de
lotes, contagem de balanço, revisão de nota, login, cadastro e plataforma.
A comparação do formulário desktop em 1440 px confirmou as mesmas dimensões,
fontes e cores antes e depois da reorganização. O build TypeScript/Vite é
verificado antes da entrega.

### Balanço físico

No desktop, cada linha apresenta produto, quantidade no sistema, contagem física, resultado e confirmação em colunas. Em até `720 px`, o conteúdo vira um cartão por produto: o nome fica no topo, sistema e contagem ocupam duas colunas, o resultado é identificado por rótulo e o botão **OK** ocupa a largura do cartão. O alvo tem pelo menos 44 px de altura.

Essa organização remove a necessidade de rolar a tabela para achar a última coluna. Também reduz os espaçamentos externos da tela de balanço e permite que nomes de produtos e metadados quebrem linha.

## Como a decisão foi investigada

O problema do botão **OK** foi localizado na estrutura da tabela de contagem: a confirmação era a última coluna, depois de produto, sistema, campo de entrada e resultado. Em telas estreitas, essa estrutura deixava a ação fora da área visível. A solução foi substituir essa tabela específica por uma grade responsiva, mantendo uma apresentação em colunas no desktop e cartões no celular.

Para telas touch híbridas, foi considerada a diferença entre `pointer: coarse` e `any-pointer: coarse`. O primeiro só identifica o tipo de ponteiro principal; o segundo também reconhece touch quando há mouse ou trackpad. Os alvos maiores usam `any-pointer: coarse` para contemplar essa combinação.

## Listas recolhidas por padrão

| Tela/bloco | Estado inicial e controles |
| --- | --- |
| Desperdício — Itens para excluir do estoque | Recolhido, apenas ADMIN, com Maximizar/Minimizar lista. |
| Desperdício — Histórico geral e Embalagens abertas | Visíveis, sem controle de recolhimento. |
| Relatórios — Reposição sugerida | Recolhida, com Maximizar/Minimizar lista. |
| Relatórios — demais blocos | Visíveis, sem recolhimento. |
| Balanços — relação disponível | Visível, sem botão geral para esconder a relação. |
| Balanços — itens de cada balanço | Recolhidos, com Maximizar/Minimizar balanço individual. |
| Plataforma — cozinhas | Recolhidas com controle da lista. |
| Estoque, consumo, catálogo, movimentações, usuários, backup e visão geral | Visíveis, sem controle geral de recolhimento. |

Filtros são preservados ao recolher a lista. Botões têm texto, ícone, contraste
e altura mínima de 44 px; no celular, adaptam-se ao cabeçalho.

## Listas extensas e interação

Nos relatórios, Desperdício e Consumo médio usam rolagem interna quando há mais
de oito itens, preservando as oito primeiras linhas visíveis. O limite é visual,
não descarte ou truncamento dos resultados da API.

A revisão de nota fiscal possui atalhos para ir ao final e voltar ao início da
lista. A conferência final libera a confirmação global, respeitando as validações
de dados. Atalhos de rolagem respeitam a preferência por movimento reduzido.

Históricos apresentam as movimentações recentes no topo; embalagens abertas usam
a última movimentação/abertura. Isso reduz rolagem sem alterar estoque/catálogo
ou a regra de seleção dos lotes para consumo.

Na central do cabeçalho, Visto remove o alerta imediatamente e bloqueia cliques
repetidos durante a confirmação. Falha devolve a linha e mostra o erro. O estado
visual usa confirmação otimista apenas para esse reconhecimento de alerta;
as gravações de contagem continuam dependendo da resposta do backend.

## Validação

- Recolhimento e expansão exercitados no Chrome com API simulada nas telas operacionais, dashboard, relatórios e plataforma; filtros preservados e modal de lotes verificado.
- Botões de balanço e ausência de overflow da página verificados em 320, 390 e 900 px.
- Build de produção do frontend executado com `npm --prefix frontend run build`.
- Layout mobile exercitado em viewports de `320 px` e `390 px`.
- Contagem do balanço exercitada também em `900 px`, para conferir o formato em colunas.
- Verificado que, nos cenários testados, o documento não excede a largura útil da viewport e que o botão **OK** permanece inteiramente visível.
- A validação das linhas de contagem foi feita com conteúdo representativo no navegador; não substitui teste completo com uma sessão autenticada e dados reais.

## Arquivos relacionados

- `frontend/src/styles.css`: breakpoints, espaçamentos, navegação, filtros, calendário, modais e regras mobile da contagem.
- `frontend/src/pages/Balanco.tsx`: estrutura responsiva da lista de itens e ação de confirmação do balanço.
- `frontend/src/components/Layout.tsx`: estrutura da sidebar, botão de menu e fechamento da navegação.

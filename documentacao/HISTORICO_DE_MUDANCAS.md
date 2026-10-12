# Histórico de mudanças do EstoQ

Revisão: **12/10/2026**. Registro das entregas recentes, conferido no Git.
O commit `6bdddad` registra a correção de horários; a revisão atual acrescenta
a reorganização e as correções de responsividade descritas abaixo.
As decisões atuais prevalecem sobre descrições históricas de etapas anteriores.

## 12/10/2026 — data única nos relatórios

- Um único campo de data no topo, iniciado no dia local atual, com atalho Hoje.
- CMV, receita base, desperdício e consumo usam apenas o dia selecionado.
- Histórico mensal cobre o ano até a data selecionada, com o último mês parcial.
- PDFs de CMV, desperdício e consumo recebem os mesmos filtros da tela; CMV inclui a receita base salva.
- Estoque, vencimentos e reposição continuam operacionais e atuais, identificados na tela; não representam um estoque histórico.
- Mantidos estilos, rolagem após oito itens e recolhimento apenas da reposição.

## 12/10/2026 — consumo rápido e impressão

- Lista de seleção por categoria, controles −/+, atalhos e painel numérico próprio.
- Digitação da busca liberada somente após dois toques; quantidades preservadas nos filtros.
- Conferência com impressão antes de gravar e lista diária consolidada por produto/unidade.
- Novo registro conjunto transacional, com chave persistida pela migração V7 contra duplicação.
- Resposta atualiza saldos/histórico sem esperar recarga; consultas e avaliação de alertas agrupadas.
- Endpoint individual preservado e unidade acrescentada ao DTO de movimentação.

## 12/10/2026 — responsividade de celular e tablet

- Criado `frontend/src/responsividade/`, dividido em `desktop`, `celular` e `tablet`.
- Centralizados layout e regras de adaptação, mantendo fontes, cores e estilo.
- Tabelas gerais em cartões com rótulos até 1024 px; textos e valores longos quebram dentro da tela.
- Ajustados largura útil, filtros, ações, modais, áreas seguras e atalhos da nota fiscal.
- Separados mídia e mensagem do login no tablet; ampliados controles de toque.
- Preservada a rolagem dos relatórios após oito itens, medindo as linhas adaptadas.
- Aplicada a skill `mobile-converter`; validação frontend com 40 testes e matriz de 320 a 1440 px.

## 10/10/2026 — entrada, agendamento e interface

| Commit | Entrega |
| --- | --- |
| `ad28434` | Recolhimento limitado às listas solicitadas, com controles aparentes. |
| `5658720` | Próximo balanço no dashboard/tela de balanço e aviso na data agendada. |
| `40ccfe2` | Leitura NF-e PDF/XML, revisão, vínculos e confirmação de entrada. |
| `1889aff` | JPG/PNG com OCR, câmera, limites de leitura e conferência humana. |
| `864c653` | Atalhos para ir ao início/fim da lista da nota. |
| `abfb3d7`, `d7c75a5` | Conferência final libera confirmação global; pendências inválidas são explicadas. |
| `fa0ca6c` | Associação automática de categorias para itens importados. |

A confirmação acrescenta quantidade via lote/entrada para produto existente ou
cria produto novo. A leitura não altera estoque, e repetir confirmação não duplica.
Custos, descontos e fatores continuam sendo validados mesmo com conferência global.

## 11/10/2026 — restaurantes novos e permissões

| Commit | Entrega |
| --- | --- |
| `55e14d8` | 13 categorias padrão no cadastro e migração V6 para lojas elegíveis. |
| `4dbfdff` | Bloco Itens para excluir do estoque reservado ao ADMIN. |
| `e20641e` | Histórico de desperdício e embalagens abertas sempre expandidos. |
| `3095a6d` | Desperdício e Consumo médio dos relatórios mostram até oito linhas, com rolagem quando há mais. |

COZINHA mantém consumo e desperdício operacional; não recebeu exclusão ou ajuste
administrativo. Diferença negativa de balanço aplicada integra a perda geral
sem gerar segunda movimentação de descarte.

## 12/10/2026 — base, cache e sincronização

| Commit | Entrega |
| --- | --- |
| `ff7a70e`, `c802395` | Isolamento de restaurante, revisão de backup/CMV/paginação, baseline de banco novo e imports da pesquisa paginada. |
| `30173c3` | Reutilização das listas com cache por sessão: 15 segundos operacional, um minuto demais consultas, revalidação em segundo plano. |
| `2c5337a` | Proteção concorrente de balanço e eventos pós-commit por restaurante. |
| `a9f85a1` | SSE no navegador, atualização entre sessões, preservação de rascunhos e conflitos explícitos. |

O banco é compartilhado com isolamento por restaurante. O produto passou de
proposta de execução local/PWA para aplicação web multitenant. O backup completo
permanece, mas é exclusivo da PLATAFORMA; backup individual não foi implementado.

## 12/10/2026 — correção da regressão de desempenho

| Commit | Entrega |
| --- | --- |
| `fe03a9f` | Remove consultas por produto, cria retrato/alertas em lote e evita leituras duplicadas durante eventos. |
| `33c4733` | Carrega parâmetros junto dos produtos ao consultar alertas de balanço. |

A primeira implementação de proteção/sincronização causou lentidão. A correção
manteve as garantias de concorrência e reduziu consultas repetidas. Uma leitura
de balanços com 186 produtos caiu de aproximadamente 55 s para 1,8 s no cenário
medido em produção. Testes de PostgreSQL verificam limites de consultas, versões,
rollback, permissões e isolamento; não há promessa de latência fixa.

## 12/10/2026 — ordem, alertas e horário

| Commit | Entrega |
| --- | --- |
| `a8a8460` | Históricos mais recentes no topo; embalagens ordenadas pela última movimentação; Visto remove imediatamente e bloqueia cliques repetidos. |
| `6bdddad` | Horários UTC exibidos no fuso do navegador; gravação UTC explícita em Java e SQL em lote. |

A ordem visual não altera a seleção dos lotes para consumo. Estoque e catálogo
mantêm suas próprias ordenações. Na central de alertas, erro ao gravar Visto
recoloca a linha para nova tentativa; uma resposta antiga não desfaz a confirmação.

Exemplo do horário corrigido: 19:26 UTC → 16:26 na Bahia. Teste também cobre a
virada do dia, offset explícito e preservação da validade sem horário. Não foi
feita uma migração para subtrair três horas dos registros existentes.

## Revisão documental de 12/10/2026

Atualizados os documentos de requisitos, camadas, API, resumo, deploy, Git/CI,
notas, planilha, responsividade e concorrência, incluindo Word. Acrescentados
índice, cache/sincronização, backup/restauração e este histórico. O exemplo de CI
passa a espelhar o workflow ativo. O CSV de referência mantém seus dados originais.

Última validação funcional: 99 casos backend, sem falhas (skips de OCR conforme
ambiente), 29 casos Playwright e build. CI da dev/main passou; a publicação da
correção de horário foi confirmada no site com consulta de um balanço real.

## Limites atuais

Ainda não há backup por restaurante, escrita offline, reabertura de balanço,
infraestrutura de SSE/sessão compartilhada entre instâncias, PDV, emissão fiscal
ou integração automática de faturamento. Essas possibilidades são evoluções,
não recursos já entregues.

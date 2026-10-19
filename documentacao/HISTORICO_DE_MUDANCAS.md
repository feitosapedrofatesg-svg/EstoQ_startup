# Histórico de mudanças do EstoQ

## 19/10/2026 — botão compacto de recalcular CMV

Botão Recalcular com 132 × 44 px, mantendo cores, tipografia, ícone, sombra e
estrutura dos filtros. No celular permanece abaixo das datas e à direita.
Tamanho estável durante o carregamento e área de toque de 44 px.
Sem alteração nos cálculos ou na API.

## 19/10/2026 — faturamento e totais nos PDFs

Exportações de faturamento diário e consumo por dia da semana; CMV mensal e
embalagens abertas disponibilizados na interface. Todos os relatórios possuem
fechamento com valores monetários, contagens ou quantidades por unidade.
Faturamento parcial preserva pendências e separa a referência histórica, sem
dupla contagem. Layout permite vários totais com paginação.
Regras em [Relatórios em PDF](RELATORIOS_PDF.md).

## 19/10/2026 — base diária de faturamento para CMV

Faturamento salvo por restaurante/data com correção por versão, distinção entre
zero e pendência e soma automática para os relatórios, dashboard e PDF. Totais
antigos por período ficam preservados, sem rateio nem dupla contagem. APIs e
publicação descritas em [Faturamento diário](FATURAMENTO_DIARIO.md).

## 18/10/2026 — confirmação na criação e alteração de senha

Cadastro público, criação/edição de usuários e redefinição pela PLATAFORMA exigem
senhas iguais antes de enviar. Na edição, os dois campos vazios mantêm a senha.
A confirmação é apenas local ao formulário, não é gravada nem enviada na API.
Testes de interface cobrem divergência, confirmação ausente, preservação da senha
existente e envio correto. O login permanece com um campo de senha.

## 18/10/2026 — preparação de banco vazio no Supabase

Referência de ambiente para Session pooler, schema privado `estoq_v2`, scripts de
provisionamento e criação inicial de PLATAFORMA sem senha fixa. Gerador local de
BCrypt com entrada oculta; PostgreSQL descartável valida scripts e migração até V8.
O Neon não é apagado. A troca efetiva depende da configuração do projeto e do
Render, conforme [procedimento Supabase](DEPLOY_SUPABASE.md).

## 18/10/2026 — ociosidade do Neon e mensagem de login

Healthcheck automático consulta apenas o processo, pool de produção libera conexões
ociosas sem pings e falhas internas de autenticação retornam 503 com o contrato de
erro do core. Login distingue indisponibilidade de credenciais recusadas.
Procedimento e limitações em [Neon e consumo](NEON_E_CONSUMO.md).

## 18/10/2026 — revisão de segurança e estoque

Correções de fuso, impressão do balanço, saldo disponível na reposição, reconciliação de alertas, proteção do último ADMIN, validações, auditoria administrativa e consultas de notas/relatórios. Dependências atualizadas e scanner no CI. API, migração V8 e operação descritas no [anexo da revisão](REVISAO_SEGURANCA_2026_10_18.md).

Revisão: **12/10/2026**. Registro das entregas recentes, conferido no Git.
O commit `6bdddad` registra a correção de horários; a revisão atual acrescenta
a reorganização e as correções de responsividade descritas abaixo.
As decisões atuais prevalecem sobre descrições históricas de etapas anteriores.

## 12/10/2026 — exportação de desperdício e totais dos PDFs

- Corrigida a exportação de desperdício quando a descrição opcional do motivo está vazia ou nula.
- Campos textuais opcionais não interrompem a montagem das linhas do PDF.
- Estoque atual, vencidos e desperdício apresentam total monetário no fechamento.
- O fechamento permanece junto aos últimos itens; totais usam os valores exibidos em centavos.
- Testes cobrem descrição ausente, perdas de balanço, totais e fechamento em várias páginas.

## 12/10/2026 — layout dos relatórios impressos

- Tabelas reais no PDFBox, com larguras medidas e quebra de texto sem truncamento.
- A4 retrato/paisagem, cabeçalhos repetidos, emissão, página X de Y e números alinhados.
- Nove tipos de PDF com formatação brasileira, unidades e resumo de CMV já calculado.
- Impressão de consumo com cabeçalho, metadados, legenda de conferência e fechamento.
- Estilos exclusivos da saída impressa; cálculos, filtros e telas normais preservados.
- Testes de limites de página, conteúdo extenso, filtros e geração de todos os tipos.

## 12/10/2026 — período único nos relatórios

- Campos De e Até compartilhados, iniciados no dia local atual, e receita base do período.
- CMV, desperdício, consumo médio e gráfico semanal usam o mesmo intervalo inclusivo.
- Histórico mensal usa os limites escolhidos; meses nas bordas podem ser parciais.
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

## 12/10/2026 — preparação para iniciar com dados reais

- Utilitário administrativo em `scripts/limpar_restaurantes_teste.py`, com inspeção,
  ensaio de restauração e execução explícita; nenhum endpoint ou migração automática.
- Manifesto fixa os restaurantes de teste; contas PLATAFORMA, novos restaurantes,
  sequências e histórico Flyway são preservados. Exclusão respeita FKs e transação.
- Execução exige duas cópias do dump e comprovante do ensaio, vinculados por SHA-256.
- Testes PostgreSQL reais cobrem preservação, rollback e bloqueios de segurança.
- Procedimento e configuração inicial registrados em `INICIO_PRODUCAO.md`.
- A implementação da ferramenta não significa que a limpeza remota foi executada;
  produção depende do acesso ao Neon, backup e ensaio efetivamente verificados.

### Execução da preparação no Neon

- Backup completo salvo fora do Render, com segunda cópia local e hashes iguais.
- Restauração do schema da aplicação ensaiada em PostgreSQL 18 isolado; conteúdo
  das 26 tabelas conferido contra a origem antes da exclusão.
- 17 restaurantes de teste e 22 usuários operacionais removidos em transação.
  A conta PLATAFORMA, o histórico Flyway, as sequências e a tabela de exemplo
  própria do Neon foram preservados e conferidos contra o backup.
- Registros antigos das tabelas filhas de movimentação sem restaurante_id foram
  contemplados pelo vínculo com a movimentação principal e testados em PostgreSQL.
- A extensão gerenciada pg_session_jwt permanece no dump completo e na produção;
  o ensaio local do schema da aplicação não homologa sua restauração no Neon.
- Backups e registro detalhado ficam em dados/, ignorado pelo Git. O cadastro
  do restaurante real e a troca das credenciais permanecem a cargo do responsável.

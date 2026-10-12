# Consumo rápido por toque

Revisão: **12/10/2026**.

## Uso da tela

Em **Novo consumo**, escolha uma categoria ou percorra os produtos. Cada cartão
mostra saldo consumível e unidade. Preencher uma quantidade seleciona o produto;
zerar ou remover retira da seleção. Filtros conservam as quantidades escolhidas.
O filtro **Somente selecionados** permite revisar a preparação.

Os botões −/+ e atalhos acrescentam ou retiram quantidade:

| Unidade | Passo −/+ | Atalhos de acréscimo |
| --- | --- | --- |
| UN | 1 | 1, 2 e 5 |
| KG/L | 0,1 | 0,1, 0,5 e 1 |
| G/ML | 10 | 50, 100 e 500 |

Tocar no valor abre o painel numérico da própria aplicação. Ele possui vírgula,
apagar, limpar e confirmar, com até três casas decimais; não abre o teclado do
aparelho. Na busca, um toque mantém a digitação bloqueada, e dois toques em até
400 ms liberam o teclado. Ao sair, o campo volta a bloquear. Enter com o campo
focado oferece a ativação equivalente para quem navega por teclado.

O lote automático mantém a regra existente: lotes válidos com vencimento mais
próximo primeiro; embalagens abertas são baixadas antes da parcela fechada do
lote. **Lote e observação** permite uma escolha explícita por produto. Não há
repetição automática do último consumo nem estimativa das quantidades usadas.

## Conferência e impressão

**Registrar consumo de X itens** abre a conferência. É possível voltar para
editar, imprimir ou confirmar. A impressão anterior à confirmação é identificada
como **consumo ainda não registrado** e nunca altera o estoque.

Em **Consumo de hoje**, **Imprimir lista do dia** consolida os registros confirmados
por produto e unidade. Registros de diferentes lotes e embalagens abertas são
somados. Os documentos contêm restaurante, data, emissão, produto, quantidade e
unidade; não contêm valores financeiros. O diálogo do navegador permite imprimir
ou salvar em PDF. Bloqueio de abertura de janela é comunicado na tela.

O documento usa texto no DOM, sem interpretar nomes como HTML. Cabeçalhos se
repetem entre páginas e linhas não são divididas pela paginação de impressão.
Imprimir ou cancelar o diálogo não registra consumo nem limpa a seleção.

## Registro, falhas e sincronização

A tela envia uma confirmação única para `POST /api/consumos/lote`, com até 500
produtos. Não envia uma sequência de consumos individuais. Uma falha desfaz
todo o conjunto; a seleção permanece para correção.

A conferência conserva um retrato dos itens, independente de atualizações de
estoque recebidas enquanto o envio ocorre. Cliques repetidos ficam bloqueados.
Quando a conexão falha ou a resposta é inconclusiva, **Tentar novamente** usa a
mesma chave e o mesmo pedido. Nesse estado, a edição fica bloqueada até recuperar
uma resposta conclusiva. A chave e a resposta confirmada são persistidas no banco
por restaurante; os dados da tentativa permanecem na tela enquanto ela está aberta.

Após confirmação do servidor, saldos e histórico recebem a resposta imediatamente.
As recargas seguintes ocorrem em segundo plano; falhar ao atualizar uma lista
não significa que o consumo confirmado falhou. Atualizações continuam seguindo
as regras existentes de cache e SSE.

O backend compartilha as regras de baixa entre registro individual e conjunto,
carrega produtos, lotes e embalagens em grupo e faz uma única conclusão. Alertas
dos produtos afetados também reutilizam as leituras agrupadas após o commit.
O bloqueio da chave é coordenado pelo restaurante e a baixa mantém as versões
otimistas de lotes e embalagens. A migração V7 acrescenta `operacoes_consumo`.

## Validação

Playwright cobre seleção por toque, categorias, números fracionados, busca por
dois toques, impressão sem gravação, consolidação diária, reenvio idempotente,
recarga lenta, lista longa e impressão de várias páginas com nomes seguros.
Há verificações de largura em celular, tablet e desktop.

Os testes com PostgreSQL real verificam rollback, confirmação concorrente,
permissão da cozinha pelo HTTP, reenvio, chave incompatível, lote vencido,
coerência das embalagens e isolamento de restaurante. Testes usam dados fictícios.

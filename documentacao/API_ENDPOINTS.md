# Contrato de API do EstoQ

Revisão: **12/10/2026**, conferida nos controllers, DTOs e `SecurityConfig` até
as entregas de consumo conjunto desta revisão. Base local: `http://localhost:8083`; no site, use caminhos relativos
`/api` pelo proxy da Vercel. A autorização efetiva está no backend.

## Autenticação e formatos

1. `GET /api/auth/csrf` retorna `{token, headerName}` e disponibiliza cookie de sessão.
2. `POST /api/auth/login` recebe `{email, senha}` e o header CSRF. Retorna `{id, nome, email, perfil, restauranteNome, restauranteId}`.
3. Consulte CSRF novamente após login, pois o token é rotacionado.
4. Envie cookie e token atual nas operações POST/PUT/DELETE. Login e registro também exigem CSRF.
5. `GET /api/auth/me` recupera a identidade; `POST /api/auth/logout` encerra a sessão e retorna 204.

Perfis: ADMIN, COZINHA, NUTRICIONISTA e PLATAFORMA. O restaurante vem da sessão;
não há parâmetro público para consultar arbitrariamente outra loja. PLATAFORMA
não recebe acesso irrestrito às rotas operacionais do restaurante.

Datas sem horário: `YYYY-MM-DD`. Horários de eventos: ISO `LocalDateTime` em UTC,
atualmente **sem offset** no JSON. O cliente deve interpretar esse contrato como
UTC e converter para exibição local. Decimais seguem os DTOs `BigDecimal`.
As consultas de movimentações recebem `LocalDateTime`; as de relatórios recebem
`LocalDate`. Não confundir os dois contratos de filtro.

Erros: 400 para payload inválido, 401 para ausência/sessão inválida, 403 para
perfil/CSRF, 404 para recurso não encontrado, 409 para conflito de versão/estado
e 429 para excesso de tentativas de login. Validações de negócio podem retornar
422, conforme a exceção. O corpo padronizado é `ErrorResponse`.

## Registro e plataforma

| Método | Rota | Permissão / dados |
| --- | --- | --- |
| POST | `/api/registro` | Público com CSRF; `{nomeLoja, nomeResponsavel, email, senha}`. Cria restaurante, ADMIN, categorias e configurações; não cria estoque. |
| GET | `/api/plataforma/restaurantes` | PLATAFORMA; lista restaurantes. |
| PUT | `/api/plataforma/restaurantes/{id}` | PLATAFORMA; `{ativo}` suspende/reativa. |
| POST | `/api/plataforma/restaurantes/{id}/redefinir-admin` | PLATAFORMA; `{senha}`. |

## Cadastros e paginação

Categorias, produtos, usuários e parâmetros de estoque usam CRUD conforme seus
controllers. Rotas genéricas: GET/POST na coleção, GET/PUT/DELETE em `/{id}`.
DELETE cadastral é desativação lógica. Atualização envia a versão consultada.

| Coleção | Leitura | Escrita | Campos principais |
| --- | --- | --- | --- |
| `/api/categorias` | ADMIN/COZINHA | ADMIN | `nome, descricao`. |
| `/api/produtos` | ADMIN/COZINHA | ADMIN | `nome, unidadeMedida, categoriaId, codigoBarras`. |
| `/api/usuarios` | ADMIN | ADMIN | `nome, email, perfil, senha` na criação; `ativo, version` na atualização. Senha nunca é retornada. |
| `/api/parametros-estoque` | ADMIN | ADMIN | `produtoId, tempoReposicaoDias, periodoAnaliseDias, consumoMedioDiario, estoqueMinimo, estoqueMedio, estoqueMaximo, diasAlertaVencimento`. |

Listas paginadas aceitam `page`, `size`, `sort` e busca `q`; limite de página
configurado: 100. Resposta inclui `content`, `number`, `size`, `totalPages` e
`totalElements`. Quando precisa de todos os registros, o frontend percorre as
páginas; não resolve o limite pedindo uma página arbitrariamente enorme.

Unidades: `KG`, `G`, `L`, `ML`, `UN`. Produtos podem ter múltiplos lotes.
Consulta auxiliar ADMIN: `GET /api/parametros-estoque/por-produto/{produtoId}`.

Cada produto novo recebe parâmetros com mínimo/médio/máximo em 3 na unidade de
cadastro. Os níveis continuam editáveis, respeitando mínimo ≤ médio ≤ máximo;
mínimo zero desativa o alerta de estoque baixo. A V9 atualiza uma única vez os
mínimos antigos zerados, preservando os personalizados e elevando os demais
níveis somente quando necessário. Um zero escolhido após a V9 é preservado.

## Estoque e lotes

| Método | Rota | Permissão / notas |
| --- | --- | --- |
| GET | `/api/estoque` | ADMIN/COZINHA; `somenteAbertos`, `somenteBaixo`. |
| GET | `/api/produtos/{id}/saldo` | ADMIN/COZINHA; saldo derivado. |
| GET | `/api/produtos/{id}/lotes-disponiveis` | ADMIN/COZINHA; lotes para consumo. |
| GET | `/api/produtos/estoque-baixo` | ADMIN/COZINHA. |
| GET | `/api/lotes` | ADMIN/COZINHA; filtro `produtoId`. |
| GET | `/api/lotes/{id}` | ADMIN/COZINHA. |
| GET | `/api/lotes/vencidos` | ADMIN/COZINHA. |
| GET | `/api/lotes/proximos-vencimento` | ADMIN/COZINHA. |

`EstoqueDTO` expõe `produtoId`, nome/categoria/unidade, `saldoAtual`,
`saldoDisponivelConsumo`, `valorEstoque`, mínimo/médio/máximo,
`abaixoDoMinimo` e `possuiItensAbertos`. Saldo físico inclui vencidos presentes;
saldo disponível para consumo não inclui. Não somar o restante aberto novamente.

`LoteDTO` inclui ID, versão, código, produto, quantidades, datas, preço,
`vencido`, `disponivel` e dias para vencimento. Envie `versionLote` quando
operar sobre o lote selecionado para detectar alterações concorrentes.

## Movimentações e embalagens abertas

| Método | Rota | Permissão / corpo |
| --- | --- | --- |
| GET | `/api/movimentacoes` | ADMIN/COZINHA; `produtoId, loteId, usuarioId, tipo, inicio, fim`. Mais recentes primeiro. |
| POST | `/api/entradas` | ADMIN; `{produtoId, quantidade, valorTotalPago, unidadeCompra, dataValidade?, observacao?, semCusto?}`. |
| POST | `/api/consumos` | ADMIN/COZINHA; `{produtoId, loteId?, quantidade, versionLote?, observacao?}`. |
| POST | `/api/consumos/lote` | ADMIN/COZINHA; `{chave: UUID, itens: [{produtoId, loteId?, quantidade, versionLote?, observacao?}]}`. |
| POST | `/api/desperdicios` | ADMIN/COZINHA; `{produtoId, loteId?, quantidade, motivo, descricaoMotivo?, versionLote?, observacao?}`. |
| GET | `/api/produtos-abertos` | ADMIN/COZINHA; `produtoId, finalizado`. Última movimentação/abertura mais recente primeiro. |
| GET | `/api/produtos-abertos/{id}` | ADMIN/COZINHA. |
| POST | `/api/produtos-abertos/abrir` | ADMIN/COZINHA; `{produtoId, loteId, quantidadeDaEmbalagem, quantoUsouAgora, versionLote?, observacao?}`. |
| POST | `/api/produtos-abertos/{id}/consumir` | ADMIN/COZINHA; `{quantidade, version?, versionLote?, observacao?}`. |
| POST | `/api/produtos-abertos/{id}/desperdicar` | ADMIN/COZINHA; mesmos campos de quantidade/versões/observação. |

Tipos de movimento: ENTRADA, CONSUMO, DESPERDICIO e AJUSTE. A abertura sem uso
não cria um tipo ABERTURA no histórico. Motivos: VENCIMENTO, DETERIORACAO,
PREPARO_INCORRETO, SOBRA_NAO_APROVEITADA e OUTRO.

Respostas de registro usam `MovimentacaoResultadoDTO`: produto, saldo e movimentos
com quantidades anterior/posterior, delta e horário. Entrada gratuita exige
`semCusto=true`; essa flag não aceita valor pago positivo. Validade é opcional.
Déficit de balanço aplicado aparece como desperdício sem duplicar o movimento.

### Confirmação conjunta de consumo

O novo endpoint recebe de 1 a 500 produtos, sem produto repetido. Quantidades
positivas aceitam até três casas decimais. Todos os itens são gravados na mesma
transação: saldo insuficiente, lote inválido ou conflito desfazem a confirmação
inteira. A mensagem identifica o produto que precisa de revisão.

A resposta é `{estoque: EstoqueDTO[], movimentacoes: MovimentacaoDTO[]}`. Cada
movimentação também expõe `unidadeMedida`, inclusive na consulta do histórico;
essa adição permite imprimir quantidades sem presumir uma unidade.

A chave UUID pertence ao restaurante da sessão e fica persistida pela migração
V7. Reenviar a mesma chave e os mesmos itens recupera a resposta original, sem
uma nova baixa. Reutilizar a chave com conteúdo diferente retorna 409. O cliente
mantém a chave e o retrato dos itens quando não recebe uma resposta conclusiva.
Cada nova confirmação independente recebe uma chave nova.

O endpoint individual continua disponível. A impressão é feita no navegador,
sem outro endpoint de PDF e sem gravar consumo.

## Balanços e versões obrigatórias

| Método | Rota | Permissão / corpo |
| --- | --- | --- |
| GET | `/api/balancos` | ADMIN/COZINHA; lista com itens. |
| GET | `/api/balancos/{id}` | ADMIN/COZINHA; estado atual. |
| GET | `/api/balancos/proximo` | ADMIN/COZINHA/NUTRICIONISTA; lista de configurações de agendamento. |
| POST | `/api/balancos` | ADMIN/COZINHA; `{tipo: "GERAL"}` ou `{tipo: "PARCIAL", categorias: [1,2]}`. Corpo ausente cria GERAL. |
| POST | `/api/balancos/{id}/iniciar` | ADMIN/COZINHA; `{version}`. |
| PUT | `/api/balancos/{id}/itens/{itemId}/contagem` | ADMIN/COZINHA; `{quantidadeFisica, version}` do item. |
| POST | `/api/balancos/{id}/confirmar` | ADMIN; versão do balanço e versões de todos os itens. |
| POST | `/api/balancos/{id}/gerar-ajustes` | ADMIN; `{version}` do balanço. |

Exemplo de conclusão (IDs/versões fictícios, sempre obtidos da leitura atual):

```json
{"version": 1, "itens": [{"id": 101, "version": 2}, {"id": 102, "version": 1}]}
```

O conjunto precisa corresponder aos itens completos do balanço, todos contados.
`BalancoDTO` e cada `ItemBalancoDTO` incluem `version`. Gravações bloqueiam o
balanço pai e validam estado. Versão antiga: 409. Campo obrigatório ausente: 400.
Não gravar contagem em CONCLUIDO; não há rota de reabertura. Ajustes são idempotentes.

Configuração, apenas ADMIN: GET/POST `/api/configuracoes-balanco` e
PUT/DELETE `/api/configuracoes-balanco/{id}`. Campos: `periodicidade`,
`diaExecucao`, `proximaExecucao`, ID/versão na atualização. Periodicidades:
DIARIA, SEMANAL, MENSAL. Confirmar balanço avança o agendamento vencido.

## Alertas e SSE

| Método | Rota | Permissão / resposta |
| --- | --- | --- |
| GET | `/api/alertas` | ADMIN/COZINHA; filtro `visualizado`, destino do perfil. |
| GET | `/api/alertas/abertos` | ADMIN/COZINHA; quantidade não vista. |
| PUT | `/api/alertas/{id}/visualizado` | ADMIN/COZINHA; marca visto. |
| GET | `/api/sincronizacao/eventos` | ADMIN/COZINHA/NUTRICIONISTA; SSE autenticado. |

O agendamento vencido é avaliado nas consultas autenticadas de alertas,
sem duplicar o aviso da data/perfil ou reabrir um aviso visto.
SSE: `conectado`, `heartbeat` e `alteracao` com `{grupos:[...]}` após commit.
A conexão tem duração de um minuto; heartbeat a cada dez segundos. Não transmite
registros de estoque nem dados de outro restaurante. O header
`X-Estoq-Sincronizacao: 3` identifica o protocolo disponível; não é o SHA do commit.
Ver [CACHE_E_SINCRONIZACAO.md](CACHE_E_SINCRONIZACAO.md).

## Notas fiscais — somente ADMIN

| Método | Rota | Dados |
| --- | --- | --- |
| POST | `/api/notas-fiscais/analisar` | Multipart `arquivos`; XML/PDF ou até dez JPG/PNG, máximo total 4 MB. |
| GET | `/api/notas-fiscais/{id}` | Revisão/resultado. |
| POST | `/api/notas-fiscais/{id}/confirmar` | `{version, itens, custosConferidos}`. |

Item de confirmação: `id`, `selecionado`, `produtoId`, `nomeProduto`,
`categoriaId`, `unidadeEstoque`, `quantidadeNota`, `fator`, `valorEntrada`,
`validade`, `embalagemConfirmada`, `leituraConferida`, conforme a decisão.
O item retornado na análise/consulta inclui `origemCategoria`, `motivoCategoria`
(opcionais em dados antigos) e `revisarCategoria`. Origens: `FORNECEDOR`,
`CODIGO_BARRAS`, `NOME_EXATO`, `CATALOGO`, `DESCRICAO`, `NAO_IDENTIFICADA`, `REVISAO`.
São explicações, não novos campos obrigatórios de confirmação. Reanalisar um
rascunho recalcula as sugestões e incrementa `version`; confirmação antiga recebe
409. Notas confirmadas não são reanalisadas nem duplicadas.

A conferência final vale globalmente; inconsistências continuam sendo validadas.
O serviço bloqueia a nota e confirma tudo em uma transação; repetição não duplica
estoque. A chave fiscal é única por restaurante. Ver o documento de importação.

## Relatórios, CMV e dashboard

Permissão: ADMIN/NUTRICIONISTA. Filtros de período usam `inicio` e `fim` no formato
`YYYY-MM-DD`; a camada de consulta transforma o fim em corte do dia seguinte.
Sem datas, as rotas de período usam os padrões definidos no controller.

| Método | Rota | Notas |
| --- | --- | --- |
| GET | `/api/relatorios/estoque-atual` | EstoqueDTO. |
| GET | `/api/relatorios/proximos-vencimento` | Lotes próximos. |
| GET | `/api/relatorios/vencidos` | Lotes vencidos. |
| GET | `/api/relatorios/produtos-abertos` | Embalagens. |
| GET | `/api/relatorios/desperdicio` | Perdas, incluindo ajustes negativos já aplicados. |
| GET | `/api/relatorios/desperdicio/agregado` | Agrupamento por motivo. |
| GET | `/api/relatorios/consumo-medio` | `dias` (padrão 30) e `fim`. |
| GET | `/api/relatorios/consumo/dia-semana` | Distribuição por dia. |
| GET | `/api/relatorios/cmv` | Período e `receitaBase` opcional. |
| GET | `/api/relatorios/cmv/mensal` | CMV por mês. |
| GET | `/api/relatorios/cmv/receita-base` | Período obrigatório. |
| PUT | `/api/relatorios/cmv/receita-base` | Salva receita informada por período. |
| GET | `/api/relatorios/faturamento-diario` | Lista dias e total com início/fim inclusivos. |
| PUT | `/api/relatorios/faturamento-diario` | Salva ou corrige data/valor usando version. |
| GET | `/api/relatorios/reposicao-sugerida` | `dias` opcional. |
| GET | `/api/relatorios/pdf/{tipo}` | PDF com os filtros correspondentes. |
| GET | `/api/dashboard/resumo` | Indicadores e CMV. |
| GET/PUT | `/api/parametros-cmv` | Meta: ID/versão/`percentualIdeal`. |

Tipos PDF: `estoque-atual`, `proximos-vencimento`, `vencidos`, `produtos-abertos`,
`desperdicio`, `consumo-medio`, `consumo-dia-semana`, `faturamento`, `cmv`,
`cmv-mensal`, `reposicao-sugerida`. Todos estão disponíveis no bloco Exportar em PDF.
Os fechamentos somam valores monetários exibidos e quantidades por unidade, sem
somar médias ou percentuais. Faturamento identifica dias pendentes e total parcial.
Detalhes em [Relatórios em PDF](RELATORIOS_PDF.md).

CMV = estoque inicial + compras − estoque final. Sem receita válida não calcula
percentual. Perdas não explicadas = CMV − (consumo registrado + desperdício).
A receita base por período não significa integração com vendas/PDV.

O CMV resolve automaticamente o faturamento diário completo ou o total antigo do
intervalo exato, sem somar as duas bases. Dias pendentes não são dias com R$ 0.
Regras, campos e compatibilidade em [Faturamento diário](FATURAMENTO_DIARIO.md).

## Backup e documentação interativa

| Método | Rota | Permissão / resposta |
| --- | --- | --- |
| GET | `/api/backups` | PLATAFORMA; lista `BackupDTO {nome, tamanhoBytes, criadoEm}`. |
| POST | `/api/backups` | PLATAFORMA; **200 com arquivo binário e Content-Disposition**, não JSON/201. |
| GET | `/api/backups/{nome}` | PLATAFORMA; download do dump validado. |

Backup completo abrange os restaurantes do banco/schema configurado. Não é backup
individual e não está disponível aos perfis dos restaurantes. A geração tem
limite de cinco minutos e impede duas execuções simultâneas na instância.

`/v3/api-docs` e `/swagger-ui/**` dependem da habilitação por configuração;
dev habilita, produção mantém desabilitado por padrão.

## Ajustes de 18/10/2026

Consulte o [anexo de correções](REVISAO_SEGURANCA_2026_10_18.md#api-ajustada): filtros locais `dataInicio`/`dataFim`, custos semanais `valorTotal`/`valorMedio`, unidade no desperdício e endpoints públicos de saúde.

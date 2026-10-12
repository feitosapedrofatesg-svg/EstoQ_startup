# Cache, sincronização e concorrência de balanços

Revisão: **12/10/2026**. Fontes: `frontend/src/lib/{cache,hooks,sessao,sincronizacao}.ts`
e `backend/src/main/java/com/estoq/business/{balancos,sincronizacao}`.

## Reutilização das listas

O cache é mantido em memória da aba. Consultas usam a identidade da sessão mais
o caminho completo, incluindo filtros e paginação. Login, logout, expiração e
mudança de sessão limpam as entradas; respostas antigas não podem publicar dados
em uma nova sessão. Não há persistência em localStorage nem escrita offline.

| Prazo | Consultas |
| --- | --- |
| 15 segundos | Estoque, lotes, consumos, desperdícios, abertos, movimentações, balanços, alertas e saldo/lotes disponíveis por produto. |
| 1 minuto | Demais consultas: catálogo, categorias, usuários, configurações, relatórios e dashboard. |

A tela mostra imediatamente os dados já disponíveis e revalida em segundo plano
quando vencidos. Atualização periódica depende de aba visível e conexão; foco,
retorno à aba e reconexão também permitem recuperação. A expiração é contada a
partir da resposta, evitando adiar a atualização em cada troca de página.

Limites: 200 entradas e aproximadamente 20 MiB de JSON serializado, com descarte
por uso recente (LRU). Esse valor limita o conteúdo armazenado, não toda a memória
JavaScript. Respostas maiores podem alimentar o componente sem permanecer no cache.
Uma mesma chave compartilha uma consulta pendente.

Gravações locais invalidam os grupos afetados; avisos remotos também invalidam.
Portanto 15 segundos/um minuto são prazos de revalidação, não atraso obrigatório.
Se chegar um aviso remoto durante uma leitura, ela não publica um resultado
ultrapassado e ocorre uma única leitura posterior. Não abre várias requisições
iguais para a mesma tela a cada evento.

## SSE entre aparelhos

`GET /api/sincronizacao/eventos`, com cookie autenticado, retorna `text/event-stream`.
Permitido a ADMIN, COZINHA e NUTRICIONISTA. O restaurante é obtido da sessão.

| Evento | Payload | Função |
| --- | --- | --- |
| `conectado` | `{}` | Confirma conexão. A primeira abertura evita duplicar as consultas iniciais. |
| `heartbeat` | `{}` | Mantém/verifica o canal, enviado a cada dez segundos. |
| `alteracao` | `{ "grupos": ["BALANCOS"] }` | Solicita nova leitura dos grupos afetados após commit. |

Grupos: BALANCOS, ESTOQUE, CATALOGO, USUARIOS, CONFIGURACOES_BALANCO, ALERTAS e CMV.
Avisos são agrupados por 250 ms na aba. A conexão é renovada após um minuto;
reconexão usa espera de três segundos. Aba oculta/offline fecha o canal; ao
retornar, reabre e recupera o estado. Após erro, `/api/auth/me` permite reconhecer
sessão expirada, limitado a uma tentativa por 15 segundos.

O backend usa duas threads de envio e fila limitada a 256 tarefas. Não envia
produtos/quantidades no evento, apenas grupos. Valida identidade, usuário ativo,
credenciais/perfil e restaurante antes de enviar. Logout e revogação encerram
acesso. Não há notificação de transação revertida.

Eventos não possuem replay durável. Reconexão e polling recuperam perdas.
SSE, sessões e rate limit pressupõem uma instância; múltiplas instâncias exigem
infraestrutura compartilhada que ainda não foi implementada.

## Proteção das gravações de balanço

O backend adquire lock pessimista no balanço pai antes de consultar os itens;
a consulta de lock não usa joins. Contagem, conclusão e ajustes passam pelo
mesmo ponto de serialização. O estado é conferido dentro da transação.

| Operação | Versão exigida |
| --- | --- |
| Iniciar | `version` do balanço. |
| Contar | `version` do item, mais `quantidadeFisica`. |
| Confirmar | `version` do balanço e lista completa de `{id, version}` dos itens. |
| Gerar ajustes | `version` do balanço. |

Versões antigas recebem 409. Payload obrigatório ausente/inválido recebe 400.
Balanço concluído não admite contagem, mesmo antes de o aviso chegar ao celular.
A confirmação não altera automaticamente os lotes; os ajustes são outra ação do ADMIN.

A tela mantém o valor e a versão do item desde o começo do rascunho. Atualizações
remotas alteram campos livres, preservam os editados e destacam conflitos.
**Recarregar contagem** permite descartar o rascunho e usar o servidor. Se o
balanço foi concluído remotamente, a edição fica indisponível e o rascunho não salvo
é sinalizado. Não existe reabertura automática.

## Correção de desempenho

A regressão de outubro tinha consultas extras por produto e repetição de leituras
na abertura do SSE. Foram aplicados graphs para produto/categoria/parâmetro,
carregamento das categorias em lote, criação do retrato por INSERT SELECT,
criação/limpeza dos alertas em lote e coalescência de invalidações pendentes.

Na amostra de produção com 186 produtos, a consulta de balanços caiu de cerca de
55 segundos para cerca de 1,8 segundo. É uma medição daquele cenário, não uma
promessa de latência. O teste PostgreSQL de referência verifica limites de
consultas, além da correção do estado e do tenant.

## Central de alertas e horário

Na central do cabeçalho, **Marcar como visto** remove imediatamente o alerta da
lista local. Durante a gravação, bloqueia cliques repetidos e os botões restantes;
após a resposta, mantém uma proteção de 500 ms contra o segundo clique atingir
a próxima linha. Resposta de leitura antiga não recoloca o alerta confirmado.
Se a gravação falhar, o alerta reaparece e a tela mostra o erro.

Horários sem offset vindos do backend são UTC. `fmtDateTime` acrescenta a indicação
UTC somente quando ela está ausente e formata no fuso do navegador. 19:26 UTC
aparece como 16:26 na Bahia; a virada do dia também é convertida. `fmtDate`
preserva datas sem horário. Não é uma subtração fixa de três horas.

## Verificação

Cobertura: cache por sessão, leituras concorrentes, respostas antigas, troca de
restaurante, rollback, SSE/reconexão/heartbeat, revogação de usuário, conflitos
reais de balanço, limite de consultas, cliques em Visto e conversão de horário.
Ver `SincronizacaoBalancoIntegrationTest`, `HorariosTest` e os testes Playwright
`cache.spec.ts`, `sincronizacao.spec.ts`, `alertas.spec.ts` e `horarios.spec.ts`.

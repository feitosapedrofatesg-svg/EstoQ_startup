# Faturamento diário e CMV

Revisão: 19/10/2026. Evolução da receita base manual por período.

## Uma base para as duas telas

Cada restaurante informa seu faturamento por data. Corrigir uma data atualiza o
registro existente, sem somar outra vez. R$ 0 é um dia informado sem vendas;
valor vazio/null é um dia pendente. ADMIN e NUTRICIONISTA podem consultar e salvar.

A tabela existente `cmv_receitas_periodo` armazena o dia com `data_inicio = data_fim`.
A chave única de restaurante/início/fim e `@Version` continuam protegendo os registros.
Não é necessária alteração de schema nem reexecução de migrações antigas.

Relatórios, dashboard e PDF usam o mesmo serviço para resolver o faturamento.
Com todos os dias informados, a base é a soma dos valores diários. Havendo dia
pendente, não calcular percentual com receita parcial. Se o total for zero,
também não há divisão; isso é diferente de informação ausente. CMV em reais
continua sendo estoque inicial + compras − estoque final.

Na Visão geral, os últimos 30 dias incluem hoje (início = hoje − 29 dias).
Relatórios continuam iniciando em hoje e podem abrir o mesmo intervalo pelo
atalho da Visão geral. Datas operacionais usam o horário de negócio já definido.

## Dados anteriores e compatibilidade

Receitas antigas de um só dia já são lançamentos diários e ficam disponíveis
para edição com a versão atual. Um total antigo de vários dias não é dividido,
copiado para dias nem somado aos lançamentos diários.

Enquanto faltarem dias, um total antigo salvo para o intervalo **exato** ainda
pode servir de base, identificado como `receitaPorPeriodo=true`. Ao completar os
dias, a soma diária passa a ser usada. O registro antigo permanece preservado.
Intervalos diferentes nunca recebem rateio ou estimativa de receitas antigas.

O parâmetro opcional `receitaBase` das APIs de CMV/PDF continua disponível para
clientes antigos e representa uma base explícita por período. A nova interface
deixa o servidor resolver a base e não envia esse parâmetro.

## API

- `GET /api/relatorios/faturamento-diario?inicio=YYYY-MM-DD&fim=YYYY-MM-DD`:
  retorna início/fim, `totalInformado`, `diasInformados`, `diasPendentes`,
  `receitaBase` resolvida, `receitaPorPeriodo` e `dias`.
- Cada dia contém `data`, `valor` (null quando pendente) e `version`.
- `PUT /api/relatorios/faturamento-diario`: recebe `{data, valor, version}`.
  Criação usa versão null; correção exige a versão retornada na consulta.
  Versão divergente ou criação simultânea recebe 409. Não repetir sem conferir.
- Valor não negativo, no máximo 16 dígitos inteiros e duas casas decimais.
  Consulta limitada a 3660 dias inclusivos. O restaurante vem da sessão.
- Resumos de CMV/dashboard acrescentam `faturamentoInformado`,
  `diasFaturamentoPendentes` e `receitaPorPeriodo`; dashboard também informa
  `receitaBase`. Campos anteriores permanecem disponíveis.

## Publicação

Publicar o backend compatível antes da nova interface. `/api/saude/vivo` agora
inclui `versao`, obtida de `RENDER_GIT_COMMIT`, para conferir o commit publicado
sem consultar o banco nem criar sessão. O valor local é `local`.

O gerador de PDF identifica dias pendentes, total zero e base histórica por período.
Registros financeiros reais e senhas não são necessários para os testes.

## Uso nas telas

Em Visão geral ou Relatórios, selecionar o dia, informar seu faturamento e clicar
em **Salvar faturamento**. O botão só envia quando há alteração. Reabrir a tela
carrega o valor salvo. A seção **Ver dias do período** em Relatórios mostra cada
data, seu valor ou a indicação Não informado, com atalho para editar.

Atualizações automáticas preservam o rascunho e a versão original. Em conflito,
o usuário confere a mensagem e usa **Recarregar faturamento** antes de corrigir.
O campo vazio salvo torna o dia pendente; não equivale a registrar R$ 0.

O destaque da Visão geral é o percentual; o custo em reais continua em linha
separada. O atalho **Ver relatório destes 30 dias** mantém o intervalo. Abrir
Relatórios pelo menu continua usando hoje. O indicador mostra 0% quando o custo
for zero e houver faturamento positivo, em vez de ocultar esse percentual.

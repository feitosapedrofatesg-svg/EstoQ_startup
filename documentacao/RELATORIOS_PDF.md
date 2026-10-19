# Relatórios em PDF

Revisão: 19/10/2026. Disponíveis para ADMIN e NUTRICIONISTA, no restaurante da sessão.

O bloco **Exportar em PDF**, em Relatórios, disponibiliza onze documentos.
Faturamento, CMV, CMV mensal, desperdício e consumo usam o intervalo inclusivo
selecionado na tela. Estoque atual, vencimentos, embalagens abertas e reposição
mostram a posição atual, identificada no cabeçalho, sem sugerir estoque histórico.

| Tipo da API | Fechamento |
| --- | --- |
| `faturamento` | Soma diária informada e contagens de dias informados/pendentes |
| `cmv` | CMV total do período, mantendo a composição e os indicadores separados |
| `cmv-mensal` | Totais de CMV, compras e desperdício |
| `estoque-atual` | Valor em estoque e saldos por unidade |
| `proximos-vencimento` | Valor dos saldos e quantidades por unidade |
| `vencidos` | Valor dos saldos e quantidades por unidade |
| `produtos-abertos` | Contagem de embalagens, quantidades abertas e restantes por unidade |
| `desperdicio` | Prejuízo e quantidades perdidas por unidade |
| `consumo-medio` | Contagem de produtos e quantidade consumida por unidade |
| `consumo-dia-semana` | Custo total no período, separado das médias por ocorrência |
| `reposicao-sugerida` | Contagem de produtos e quantidades sugeridas por unidade |

`GET /api/relatorios/pdf/{tipo}` mantém os parâmetros `inicio`, `fim`, `dias` e
`receitaBase` opcional para clientes antigos. A interface informa `dias` como o
tamanho inclusivo do intervalo para o consumo médio. Não há migração de banco.

## Como interpretar os totais

- R$ 0 no faturamento significa dia sem vendas; Não informado é pendência. Com
  pendências, o fechamento é **Faturamento informado (parcial)**, nunca um total
  completo presumido. Valores corrigidos usam o registro atual, sem duplicar.
- Um total antigo do intervalo exato aparece apenas como referência histórica;
  não é distribuído pelos dias nem somado ao faturamento diário.
- Totais em dinheiro somam os centavos exibidos em cada linha, usando HALF_UP.
  Quantidades mantêm a precisão informada e são agrupadas por unidade. Valores
  sem unidade identificada permanecem somente na linha do produto.
- Médias, prazos e percentuais não são somados. Reposição não é orçamento.
  Restante de embalagens já faz parte dos lotes e não aumenta o estoque total.
- Listas vazias informam a ausência de registros e exibem total monetário ou
  contagem zero quando aplicável; não inventam unidades para quantidades vazias.

O layout compartilhado admite vários totais e continua o fechamento em outra
página quando necessário, preservando cabeçalhos, margens e rodapés.

## Validação e publicação

Testes conferem conteúdo dos PDFs, valores, unidades, pendências, permissões e
isolamento entre restaurantes. O parâmetro Maven `estoq.pdf.validacao` salva
exemplos fictícios fora do repositório para renderizar com `pdftoppm` e revisar.
Publicar backend compatível primeiro e conferir seu commit em `/api/saude/vivo`;
só então liberar a interface com os novos botões, seguindo dev → CI → main.

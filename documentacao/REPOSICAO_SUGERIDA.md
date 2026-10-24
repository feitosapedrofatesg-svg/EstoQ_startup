# Reposição sugerida

A média diária vem dos consumos registrados no período de análise do produto
(padrão: 30 dias, incluindo hoje, no fuso do Brasil). Uma única consulta busca
os consumos de todos os produtos; o relatório de consumo usa o mesmo cálculo
da média, com três casas decimais e arredondamento HALF_UP.

Sem consumo no período, vale a média manual cadastrada. Sem as duas fontes,
não aparece sugestão. O cálculo automático não altera o valor manual salvo.

Comprar = média diária × prazo − saldo disponível. O prazo é o cadastrado ou
7 dias; o parâmetro `dias` da API continua sendo a alternativa ao prazo não
cadastrado. Só valores positivos aparecem. Lotes vencidos não entram no saldo.
O estoque mínimo é informativo e não altera essa fórmula.

A tela, PDF, CSV e Excel mostram a origem e o período da média. A API também
retorna `origemMedia` (AUTOMATICO/MANUAL), `periodoAnaliseDias`, `inicioAnalise`
e `fimAnalise`. A posição é atual, independente do filtro histórico da tela.
Nenhuma migração é necessária para essa regra.

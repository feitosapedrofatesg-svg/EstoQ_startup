# Relatórios impressos e PDFs

Revisão: 12/10/2026.

## Mecanismos existentes

- Backend Java 21 / Spring Boot 4, com PDFBox já presente no projeto. `RelatorioPdfService` consulta os serviços de negócio e monta dados de apresentação; `DocumentoRelatorioPdf` descreve colunas, linhas e destaques; `LayoutRelatorioPdf` desenha e pagina o documento.
- Frontend React / TypeScript: `ImprimirConsumo` abre uma janela de documento e chama a impressão nativa. Estilos exclusivos dessa janela ficam em `frontend/src/responsividade/impressao.css`.
- Nenhuma dependência nova. Telas de operação, URLs, permissões e cálculos permanecem no fluxo existente.

## Padrão visual

Usa a paleta do repositório: tinta `#1d2733` e acento `#ef7320`. Fundo branco, texto escuro, regras horizontais e alertas escritos tornam a informação compreensível em preto e branco. A cor não define o estado de um item.

Cabeçalho com EstoQ, título específico, período ou posição atual e emissão. Moeda em real, datas brasileiras e quantidades com unidades quando disponíveis. Os números ficam alinhados à direita. O CMV destaca valores já calculados pelo serviço; os relatórios de estoque, vencidos e desperdício fecham com a soma dos valores monetários exibidos nas linhas, arredondados para centavos. Nenhuma quantidade de unidades diferentes é somada.

## PDFs do backend

Abrange estoque, lotes próximos do vencimento, vencidos, embalagens abertas, desperdício, consumo médio, CMV, CMV mensal e reposição.

- A4 com margem de 40 pontos, aproximadamente 14 mm.
- Retrato para tabelas de até quatro colunas; paisagem para as tabelas de cinco colunas.
- Corpo de 10 pontos, quebra de textos por largura real da fonte, sem truncamento por quantidade de caracteres.
- Título, período, colunas e página X de Y repetidos em cada página.
- Linhas comuns não se dividem entre páginas. Uma linha excepcionalmente maior que a área inteira da página continua na próxima, sem perder conteúdo.
- Estoque e reposição continuam mostrando a posição atual. O consumo médio usa a janela `dias`, encerrada em `fim`, conforme o cálculo existente. Não se apresenta um período diferente do efetivamente consultado.
- A descrição complementar do desperdício é opcional. Se estiver ausente, o PDF mostra o motivo cadastrado, sem impedir a exportação.
- Os totais monetários são mantidos junto à última linha da tabela, evitando uma página apenas para o fechamento.
- Desperdício usa a unidade de cadastro quando o produto aparece no estoque consultado; se ela não estiver disponível, uma legenda informa esse limite. Não há soma de quantidades com unidades diferentes.

As fontes Helvetica existentes no PDFBox foram mantidas. Acentos portugueses são suportados; caracteres fora da codificação dessa fonte, como emojis, recebem `?` para não impedir a emissão inteira. A impressão de cores depende da impressora, mas o texto não depende delas.

## Consumo pelo navegador

Inclui conferência antes da baixa e lista diária consolidada. Preserva a janela nativa, os dados, a segurança de inserir nomes como texto e a espera limitada por estilos/fontes.

A4 retrato, margem de 14 mm, cabeçalho de tabela repetido, unidades em coluna própria, aviso explícito para a lista ainda não registrada e fechamento com a contagem já existente. Os estilos não alcançam as telas normais.

A paginação e as quebras dependem do navegador e das opções escolhidas na impressão. Não se usa um contador CSS de páginas, que não funciona de forma consistente entre navegadores. O usuário pode habilitar os cabeçalhos/rodapés nativos para a numeração. Uma linha maior que uma página pode ser dividida pelo navegador.

## Verificação

Testes do renderer verificam retrato/paisagem, lista vazia, nomes extensos, moeda, repetição de cabeçalhos, página final e coordenadas dentro da área imprimível. Testes de filtros verificam intervalo inclusivo, janela de consumo e receita base. A integração gera todos os nove tipos.

Revisão visual com PDFs fictícios: estoque com poucas linhas; desperdício com 85 linhas em várias páginas; CMV com resumo; consumo do navegador com 90 produtos, incluindo páginas inicial, intermediária e final. Nenhum dado real de restaurante foi alterado nesses testes.

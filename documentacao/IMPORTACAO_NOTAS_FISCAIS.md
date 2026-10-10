# Entrada de mercadorias por nota fiscal

A opção **Importar nota fiscal** fica em **Movimentações**, para o administrador.
Enviar o documento gera uma revisão; somente **Confirmar entrada no estoque**
cria produtos, lotes e movimentações.

## PDF e XML

Envie uma NF-e de mercadorias em PDF com texto ou XML. O limite é 4 MB.
A leitura de PDF considera os modelos DANFE fornecidos (JBS, T&T e Sendas,
inclusive a segunda página). Modelos que não puderem ser lidos devem usar XML
ou entrada manual. O XML não carrega entidades ou recursos externos.

Na revisão, associe o produto ao catálogo ou escolha criar um novo produto com
nome, categoria e unidade. É possível aplicar uma categoria a todos os novos
produtos e desmarcar linhas que não devem entrar no estoque.

As próximas compras reconhecem o código do fornecedor e o fator de embalagem
confirmado. Mudança de descrição ou unidade pede nova conferência. Pacotes e
garrafas não são convertidos para peso/volume sem confirmação.

## Custos e quantidades

A quantidade de entrada é **quantidade na nota × conteúdo da embalagem**, na
unidade do produto. O estoque admite até três casas decimais; o sistema recusa
conversão que exigiria arredondamento silencioso.

O custo parte do valor total da linha menos seu desconto. A diferença para o
total da nota é rateada proporcionalmente para contemplar frete, descontos e
despesas. O administrador deve conferir o ajuste e pode corrigir os custos,
mantendo a soma da nota. Desmarcar uma linha não redistribui seu custo.

A validade não é inferida do vencimento de boleto. A movimentação usa a data da
confirmação; a data de emissão é preservada na nota.

## Camadas e consistência

O módulo `business/notasFiscais` separa leitores, validação, serviço, adaptação,
controller e persistência. O serviço reaproveita `ProdutoService` e
`EntradaService`, incluindo histórico, auditoria, custos e eventos de alertas.

A revisão, os itens e os vínculos são dados da cozinha autenticada. A migração
V5 acrescenta unicidade da chave por restaurante e vínculos que não permitem
referenciar produtos ou movimentações de outra cozinha.

A confirmação usa uma transação e bloqueio da nota: se uma linha falhar, nada é
confirmado; repetir a confirmação retorna o resultado sem duplicar estoque.
Notas confirmadas não podem ser importadas de novo, mesmo em outro formato.
As linhas desmarcadas também são consideradas processadas.

## API

- `POST /api/notas-fiscais/analisar`: multipart com campo `arquivos`.
- `GET /api/notas-fiscais/{id}`: recupera revisão ou resultado.
- `POST /api/notas-fiscais/{id}/confirmar`: versão, decisões de todos os itens e
  confirmação dos custos. Somente ADMIN, com sessão e CSRF.

Os documentos originais não são armazenados. Os dados extraídos e as decisões
permanecem para consulta. Dados fiscais reais não devem ir para o Git.

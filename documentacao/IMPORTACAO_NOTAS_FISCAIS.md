# Entrada de mercadorias por nota fiscal

Revisão: **12/10/2026**.

A opção **Importar nota fiscal** fica em **Movimentações**, para o administrador.
Enviar o documento gera uma revisão; somente **Confirmar entrada no estoque**
cria produtos, lotes e movimentações.

## Categorias desde o cadastro

Todo restaurante novo recebe 13 categorias na mesma transação do cadastro:
Carnes, Peixes e frutos do mar, Laticínios, Grãos, Hortifruti, Massas, Temperos e
condimentos, Óleos e molhos, Mercearia, Bebidas, Limpeza, Descartáveis e embalagens
e Sem classificação. Nenhum produto ou estoque é criado nessa etapa.

A primeira nota já pode associar os itens a essas categorias. O catálogo existente
tem prioridade; depois são usadas as categorias padrão e equivalentes pelo nome.
Você pode alterar a sugestão e escolher uma categoria manual antes de confirmar.

A migração V6 adiciona a lista aos restaurantes ativos sem nenhuma categoria
ativa. Restaurantes com categorias ativas, inclusive somente Sem classificação,
não são alterados. Categorias inativas permanecem no histórico.

## PDF e XML

Envie uma NF-e de mercadorias em PDF com texto ou XML. O limite é 4 MB.
A leitura de PDF considera os modelos DANFE fornecidos (JBS, T&T e Sendas,
inclusive a segunda página). Modelos que não puderem ser lidos devem usar XML
ou entrada manual. O XML não carrega entidades ou recursos externos.

Na revisão, associe o produto ao catálogo ou escolha criar um novo produto com
nome e unidade. A categoria é sugerida a partir dos produtos já classificados e
regras pelo nome do item (carnes, grãos, bebidas, limpeza e outros grupos).
A sugestão usa somente categorias ativas da cozinha autenticada. Correspondências
ambíguas ou desconhecidas entram em **Sem classificação**, criada somente na
confirmação se ainda não existir. Você pode substituir a sugestão por uma categoria
manual. Rascunhos antigos também aceitam confirmação sem categoria preenchida.
É possível aplicar uma categoria a todos os novos
produtos e desmarcar linhas que não devem entrar no estoque.

As próximas compras reconhecem o código do fornecedor e o fator de embalagem
confirmado. Mudança de descrição ou unidade pede nova conferência. Pacotes e
garrafas não são convertidos para peso/volume sem a conferência final da nota.

## JPG, PNG e câmera

Envie até dez fotos da mesma nota, somando no máximo 4 MB. A câmera do celular
permite acrescentar páginas. Use os botões Antes/Depois para ordenar e clique na
foto para abri-la em outra aba durante a revisão. Trocar ou reordenar arquivos
exige uma nova leitura, para não confirmar uma revisão de outro documento.

O backend executa Tesseract em português, com posições e confiança das palavras.
A orientação EXIF é aplicada e a imagem é convertida para tons de cinza.
Cada foto admite até 20 megapixels. A nota tem prazo de 30 segundos e apenas uma
leitura de imagem roda por instância; outra tentativa recebe aviso para aguardar.

A caixa final de conferência vale para todos os itens, incluindo o OCR, as unidades
e o conteúdo das embalagens; não é necessário marcar cada item individualmente. A soma das linhas precisa
fechar o total dos produtos; texto incompleto e páginas ausentes não geram uma
entrada parcial. Fotos de notas diferentes não podem ser combinadas.

Os arquivos temporários são removidos em sucesso, erro ou timeout. Não há serviço
externo de OCR nem armazenamento permanente de fotografias. Ao retomar um
rascunho pelo endereço, reabra as fotos locais para conferir os dados.

Para desenvolvimento local, instale Tesseract com idioma `por` ou configure
`estoq.notas.ocr.executavel`. A imagem Docker já instala os pacotes necessários.

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

## Revisão, navegação e associação

A lista possui botões para ir ao final e voltar ao início. A caixa final
**Conferi os itens, os descontos e os custos rateados** vale para a nota inteira.
Não é necessário conferir cada linha por um botão individual. A confirmação
continua bloqueada quando quantidade, custo ou associação necessária é inválida;
a tela apresenta as pendências correspondentes.

Produto já existente recebe nova entrada/lote, acrescentando quantidade sem
recriar o produto. Produto novo é criado com a categoria sugerida ou escolhida.
Um restaurante recém-cadastrado já possui as categorias padrão, mas não produtos
ou estoque. Sugestões desconhecidas usam Sem classificação, sem misturar lojas.

Horários de confirmação são UTC e exibidos no fuso do navegador; a emissão e
a validade são datas sem horário. A atualização invalida as listas afetadas e
avisa as outras sessões do mesmo restaurante via SSE após o commit.

Veja [API_ENDPOINTS.md](API_ENDPOINTS.md) para o payload de confirmação e
[CACHE_E_SINCRONIZACAO.md](CACHE_E_SINCRONIZACAO.md) para consistência entre aparelhos.

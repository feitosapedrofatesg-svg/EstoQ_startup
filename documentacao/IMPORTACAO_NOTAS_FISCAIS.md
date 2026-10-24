# Entrada de mercadorias por nota fiscal

Revisão: **22/10/2026**.

A opção **Importar nota fiscal** fica em **Catálogo** e **Movimentações**, para o administrador.
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

## Sugestões e conferência de categorias

`AssociadorProdutoNota` identifica o produto pelo histórico compatível do fornecedor,
código de barras válido ou nome exato. O histórico só é reutilizado com a mesma
descrição e unidade da nota normalizadas. Se mudarem, o produto é buscado novamente,
o fator antigo é descartado e a revisão mostra um aviso.

`AssociadorCategoriaNota` sugere categorias para produtos novos. Expande BEB,
GAISE, GASEIF, REFRI, REFRIG, BOV, SUIN, FRG, QJO, QUEIJ, LEIT, FAR, ACUC,
MACARR, DETERG e DESINF apenas para a busca. Também reconhece CONTRAFILE e
CR LEITE/CR DE LEITE. A descrição fiscal original é preservada. O tipo
no começo do nome prevalece sobre o sabor: **BEB GAISE H2O LIMAO → Bebidas**,
**LIMÃO → Hortifruti**, **DETERGENTE LIMÃO → Limpeza**. A palavra genérica
BEBIDA não é suficiente para herdar a categoria de outro produto.

Na sugestão de categoria, o **nome completo normalizado** tem prioridade sobre a
primeira palavra. Conteúdos como `500g`, `1 KG` e `1,5L` são desconsiderados nessa
comparação: CAFÉ TORRADO 1KG pode herdar a categoria de Café torrado 500g sem
confundir com Café solúvel. Isso sugere somente a categoria, não associa um
produto nem reaproveita seu fator de embalagem.

Sem correspondência completa, o catálogo pode fornecer uma categoria
personalizada pelo nome principal e tipo compatível, desde que não haja conflito.
O prefixo mais específico prevalece: **BEBIDA LÁCTEA → Laticínios** e
**BATATA PALHA → Mercearia**, sem herdar a categoria de batata inglesa. A palavra
genérica FILÉ não permite que frango classifique tilápia. Produtos inativos,
categorias indisponíveis e Sem classificação não servem de referência para novas
sugestões de categoria.

Compostos como LEITE DE COCO e CALDO DE CARNE ficam para conferência, salvo
correspondência confiável com o catálogo. Mais de uma categoria para o mesmo
nome completo ou tipo conflitante não escolhe o primeiro resultado. Uma
correspondência completa pode resolver um conflito que existia só na primeira
palavra; se o nome completo não resolver, a revisão continua necessária.

Categorias escolhidas ao confirmar uma nota ficam no catálogo e podem ajudar
as próximas sugestões. O histórico do fornecedor continua exigindo descrição
e unidade compatíveis para reutilizar produto e fator. A nova normalização não
relaxa essa proteção, não reclassifica produtos existentes e não altera custos.
A classificação usa regras locais determinísticas, sem modelo treinado, pacote
NPM, serviço externo ou envio de dados da nota a terceiros.

Cada item explica a origem e o motivo da sugestão. **Conferir categoria** é um
aviso, sem uma caixa obrigatória por item. A conferência final da nota permanece
obrigatória. Sem escolha e sem reconhecimento seguro, usa Sem classificação.
Alterar nome/produto/categoria remove a explicação anterior. Alterar somente o
nome também limpa a categoria sugerida, para não confirmar uma sugestão desatualizada.

Enviar novamente um rascunho recalcula suas associações e incrementa a versão da
nota; uma aba com a versão anterior recebe 409 ao confirmar. Apenas abrir o
endereço da nota não recalcula. Notas confirmadas mantêm os dados históricos.
A V10 adiciona explicações opcionais e um indicador de revisão, sem reclassificar
produtos existentes ou mexer nos custos. Um H2O já cadastrado com categoria errada
precisa ser corrigido em **Catálogo**; as próximas associações usam essa categoria.

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

## Lote do fabricante

A revisão de cada item e a entrada manual aceitam `loteFabricante` opcional,
com até 50 caracteres. O sistema mantém seu código interno `L-xxxxxx`.
O número do fabricante pode se repetir: cada entrada continua com seu próprio
custo, quantidade e validade. Não há leitura automática desse campo no OCR.
O número informado acompanha o lote, o histórico e as exportações.

A migração V12 adiciona somente colunas opcionais; os lotes existentes
continuam válidos e seus saldos não são alterados. Aplicar junto da V11.

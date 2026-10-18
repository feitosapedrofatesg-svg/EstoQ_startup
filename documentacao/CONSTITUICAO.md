# Constituição do projeto EstoQ

Atualização posterior: [correções de 18/10/2026](REVISAO_SEGURANCA_2026_10_18.md). O conteúdo abaixo preserva a revisão histórica correspondente ao Word.

Revisão **1.1 — 12/10/2026**. Correspondente ao Word; requisitos originais preservados e consolidação atual na seção 17.

EstoQ

Constituição do Projeto

| REVISÃO 1.1 · REQUISITOS CONSOLIDADOS · 12/10/2026 |
| --- |

Faculdade SENAI FATESG

Curso Superior de Tecnologia em Análise e Desenvolvimento de Sistemas

Projeto Integrador · 2026

Sistema EstoQ — estoque, consumo, desperdício e custos para restaurantes

| Objetivo deste documento — Definir o problema, o escopo, as regras, os módulos e a base técnica do EstoQ antes da implementação. Esta versão consolida as decisões de escopo já validadas, incluindo os três perfis de acesso, uso simultâneo em aparelhos diferentes, aplicação web multitenant, fluxo de itens abertos, alerta de validade configurável e regra de CMV. |
| --- |

## 1. Identificação do projeto

| Item | Definição |
| --- | --- |
| Nome | EstoQ |
| Tipo | Aplicação web multitenant para apoio à gestão de estoque de restaurante |
| Curso | Tecnologia em Análise e Desenvolvimento de Sistemas — SENAI FATESG |
| Contexto acadêmico | Projeto do 4º período / ADS 4, com foco em aplicação dos conteúdos já estudados |
| Problema central | Baixa visibilidade sobre estoque, validade, consumo, desperdício e custo real dos insumos |
| Perfis previstos | ADMIN, COZINHA e NUTRICIONISTA no restaurante; PLATAFORMA na administração global. |
| Backend de referência | PIAds3 — Java/Spring, com core genérico e módulos de negócio |
| Banco previsto | PostgreSQL centralizado, acessado pelo backend da aplicação |
| Escopo operacional | Aplicação web multitenant: vários restaurantes na mesma plataforma, com isolamento por restaurante. |
| Uso simultâneo | Suportar pelo menos 3 sessões ativas ao mesmo tempo, em aparelhos diferentes, uma para cada perfil operacional |

## 2. Contexto e problema

Restaurantes trabalham com insumos de diferentes unidades, lotes e prazos de validade. Quando entradas, consumos, itens abertos, sobras e contagens físicas não são registrados de forma consistente, o gestor perde a capacidade de saber o que realmente possui, o que precisa comprar, o que está próximo do vencimento e quanto dinheiro foi perdido com desperdício.

O EstoQ será desenvolvido para concentrar esses controles em uma única aplicação centralizada, mantendo rastreabilidade das movimentações e oferecendo indicadores objetivos para tomada de decisão. O sistema deverá atender simultaneamente aos perfis ADMIN, COZINHA e NUTRICIONISTA em aparelhos distintos, todos conectados ao mesmo backend e à mesma base de dados do restaurante. Cada perfil visualizará e executará apenas as funcionalidades compatíveis com sua responsabilidade.

## 3. Objetivos

### 3.1 Objetivo geral

Desenvolver uma aplicação web multitenant para controle de estoque de um restaurante, capaz de operar simultaneamente em pelo menos três aparelhos, registrar entradas, consumo, abertura de embalagens, desperdícios e ajustes de balanço, controlar validade por lote, calcular o CMV do período e aplicar permissões distintas para ADMIN, COZINHA e NUTRICIONISTA.

### 3.2 Objetivos específicos

Exibir a posição atual do estoque e a quantidade de produtos cadastrados/ativos.

Identificar produtos abaixo do estoque mínimo.

Identificar lotes vencidos e lotes próximos do vencimento.

Permitir abrir uma embalagem, informar sua quantidade e quanto foi utilizado, mantendo automaticamente o restante no estoque como item aberto.

Permitir registrar sobras e outros desperdícios, com motivo e valor estimado do prejuízo.

Registrar entradas de compra e gerar lotes com quantidade, validade e preço unitário.

Registrar consumo e manter histórico de movimentações com saldo anterior e posterior.

Executar balanços físicos, comparar quantidade do sistema com quantidade contada e gerar ajustes.

Gerar relatórios por período.

Permitir uso simultâneo por ADMIN, COZINHA e NUTRICIONISTA, com dados centralizados e atualizados entre os aparelhos.

Calcular o CMV absoluto e percentual, comparar o resultado com o percentual ideal definido por ADMIN ou NUTRICIONISTA e apresentar separadamente indicadores de desperdício e consumo.

## 4. Escopo funcional

### 4.1 Incluído na primeira versão

Dashboard: estoque atual, itens com estoque baixo, itens próximos do vencimento, itens vencidos, produtos abertos, balanço pendente, indicadores de CMV/desperdício e comparação entre CMV atual e CMV ideal.

Cadastros: usuários, categorias, produtos, parâmetros de estoque, configuração de balanço e parâmetro de CMV ideal.

Estoque por lote: entrada, quantidade atual, validade, preço unitário e disponibilidade.

Movimentações: entrada, consumo, desperdício/sobra e ajuste.

Produtos abertos: selecionar o produto/lote, informar a quantidade da embalagem e quanto foi usado; o restante permanece no estoque como item aberto até seu consumo, desperdício ou finalização.

Alertas: estoque baixo, próximo vencimento com antecedência configurável pelo ADMIN, vencido, balanço pendente e diferença de estoque.

Balanço: criação, contagem física, apuração de diferenças, confirmação e geração de ajustes.

Relatórios: estoque atual, próximos vencimentos, vencidos, produtos abertos, desperdício, consumo médio e CMV por período.

CMV: cálculo por período pela fórmula Estoque Inicial + Compras - Estoque Final; cálculo percentual quando houver receita base informada e comparação com o percentual ideal definido por ADMIN ou NUTRICIONISTA.

### 4.2 Fora do escopo desta primeira versão

PDV, emissão fiscal, comandas, mesas e fechamento de caixa.

Compras com cotação e pedido completo a fornecedores.

Ficha técnica de receitas e baixa automática por prato vendido.

Integração com ERPs, iFood ou sistemas contábeis.

Aplicativo executável, PWA e operação offline ficam fora do escopo. O sistema é uma aplicação web multitenant.

Previsão por inteligência artificial.

Observação: os itens acima podem virar evolução futura, mas não são exigidos pelo diagrama entregue e aumentariam muito o escopo acadêmico.

## 5. Atores e perfis

O restaurante possui ADMIN, COZINHA e NUTRICIONISTA, com sessões simultâneas e permissões distintas. PLATAFORMA administra os restaurantes e o backup completo, sem operar como ADMIN global de estoque.

| Funcionalidade | ADMIN | COZINHA | NUTRICIONISTA |
| --- | --- | --- | --- |
| Gerenciar usuários | Sim | Não | Não |
| Gerenciar categorias/produtos | Sim | Consultar | Não |
| Configurar estoque e alertas de validade | Sim | Não | Não |
| Registrar entrada de estoque | Sim | Não | Não |
| Registrar saída manual/ajuste administrativo | Sim | Não | Não |
| Registrar consumo diário | Sim | Sim | Não |
| Abrir embalagem e consumir item aberto | Sim | Sim | Não |
| Registrar sobra/desperdício operacional | Sim | Sim | Não |
| Consultar estoque | Sim | Sim | Não |
| Executar contagem de balanço | Sim | Sim | Não |
| Confirmar/aplicar ajuste do balanço | Sim | Não | Não |
| Definir percentual ideal de CMV | Sim | Não | Sim |
| Consultar relatórios e CMV | Sim | Não | Sim |

## 6. Requisitos funcionais

| ID | Requisito |
| --- | --- |
| RF01 | Autenticar usuário e identificar seu perfil de acesso, permitindo sessões simultâneas em aparelhos diferentes. |
| RF02 | Cadastrar, consultar, editar e desativar usuários. |
| RF03 | Cadastrar, consultar, editar e desativar categorias. |
| RF04 | Cadastrar, consultar, editar e desativar produtos, informando unidade de medida e categoria. |
| RF05 | Definir parâmetros de estoque por produto: tempo de reposição, período de análise, consumo médio, níveis mínimo/médio/máximo e quantidade de dias para alerta de próximo vencimento. |
| RF06 | Registrar entrada de estoque com quantidade, valor pago, unidade de compra e validade, gerando o lote correspondente. |
| RF07 | Consultar lotes disponíveis de um produto. |
| RF08 | Calcular o saldo atual do produto a partir de seus lotes/movimentações, sem depender de um saldo digitado manualmente. |
| RF09 | Listar produtos abaixo do estoque mínimo. |
| RF10 | Listar lotes vencidos e próximos do vencimento conforme a antecedência configurada. |
| RF11 | Registrar abertura de embalagem vinculada ao produto e ao lote de origem, informando quantidade da embalagem e quantidade utilizada no momento da abertura. |
| RF12 | Calcular automaticamente o restante da embalagem aberta e mantê-lo no estoque com status de item aberto até seu consumo, desperdício ou finalização. |
| RF13 | Registrar consumo diário, inclusive consumo proveniente de produto aberto, e calcular seu custo com base no custo unitário do lote. |
| RF14 | Registrar desperdício, motivo, observação e valor do prejuízo. |
| RF15 | Registrar sobra não aproveitada de produto aberto como desperdício quando o restante deixar de retornar ao estoque. |
| RF16 | Gerar e consultar alertas de estoque baixo, vencimento, balanço pendente e diferença de estoque. |
| RF17 | Criar balanço periódico e carregar os produtos que deverão ser conferidos. |
| RF18 | Permitir que ADMIN ou COZINHA informem a quantidade física e apurem a diferença contra o sistema; a confirmação que gera ajuste de estoque é responsabilidade do ADMIN. |
| RF19 | Gerar ajustes de estoque a partir das diferenças confirmadas no balanço. |
| RF20 | Gerar relatórios por tipo e período. |
| RF21 | Calcular e consultar o CMV por período pela fórmula Estoque Inicial + Compras - Estoque Final. |
| RF22 | Exibir um resumo para dashboard com contadores e indicadores principais, respeitando as permissões do perfil autenticado. |
| RF23 | Permitir filtrar a consulta de estoque para exibir somente itens abertos. |
| RF24 | Permitir a ADMIN e NUTRICIONISTA definir e atualizar o percentual ideal de CMV do restaurante. |
| RF25 | Permitir informar a receita base do período para calcular CMV% = (CMV / receita do período) x 100 e comparar o resultado com o CMV ideal. |
| RF26 | Permitir que o ADMIN faça movimentações administrativas de entrada, saída manual e ajuste; a COZINHA realiza somente os fluxos operacionais permitidos, como consumo, abertura de embalagem, desperdício e balanço. |
| RF27 | Após cada operação que altere o estoque, disponibilizar o novo estado central para consulta pelos demais aparelhos conectados. |
| RF28 | Disponibilizar a aplicação como site multitenant, sem executável, instalação obrigatória ou modo offline. |

## 7. Regras de negócio

| ID | Regra |
| --- | --- |
| RN01 | A validade pertence ao lote. Um mesmo produto pode possuir vários lotes com validades e preços diferentes. |
| RN02 | O saldo atual do produto é derivado; não deve ser digitado manualmente como fonte principal de verdade. |
| RN03 | Uma movimentação deve registrar quantidade, data/hora, tipo, quantidade anterior, quantidade posterior e usuário responsável. |
| RN04 | Nenhuma saída pode deixar a quantidade do lote negativa. |
| RN05 | Entrada deve gerar um lote com quantidade inicial igual à quantidade convertida para a unidade de estoque. |
| RN06 | Preço unitário do lote deve ser calculado a partir do valor total pago e da quantidade efetivamente incorporada ao estoque. |
| RN07 | Lote vencido não pode ser considerado disponível para consumo normal. |
| RN08 | Produto é considerado abaixo do mínimo quando saldoAtual < estoqueMinimo. A antecedência de próximo vencimento deve ser configurável pelo ADMIN no parâmetro de estoque do produto. |
| RN09 | Produto aberto deve estar vinculado ao produto e ao lote de origem, preservando rastreabilidade de validade e custo. |
| RN10 | Ao abrir uma embalagem, quantidadeRestante = quantidadeEmbalagem - quantidadeUtilizada. A quantidade utilizada não pode ser maior que a quantidade da embalagem nem gerar saldo negativo. |
| RN11 | A abertura da embalagem registra como consumo apenas a quantidade efetivamente utilizada. O restante não é descartado: continua compondo o estoque como ProdutoAberto. |
| RN12 | O restante de uma embalagem aberta somente se transforma em desperdício quando for explicitamente registrado como sobra não aproveitada ou outro motivo de perda. |
| RN13 | Motivos de desperdício: VENCIMENTO, DETERIORACAO, PREPARO_INCORRETO, SOBRA_NAO_APROVEITADA e OUTRO. |
| RN14 | Balanço deve possuir ao menos um ItemBalanco. |
| RN15 | Diferença do ItemBalanco = quantidadeFisica - quantidadeSistema. |
| RN16 | Balanço confirmado não pode ter suas contagens alteradas sem procedimento explícito de reabertura/correção. A COZINHA pode executar a contagem; o ADMIN confirma os ajustes de estoque. |
| RN17 | Ajuste gerado por balanço deve guardar justificativa e rastreabilidade da diferença que o originou. |
| RN18 | Exclusões cadastrais devem ser preferencialmente lógicas (ativo = false), seguindo a base de estudos. |
| RN19 | Cálculos monetários e de quantidade devem usar BigDecimal. |
| RN20 | Regras específicas ficam no módulo de negócio; o core não pode depender de classes do pacote business e deve conter somente código genérico reutilizável. |
| RN21 | O ADMIN é o único perfil autorizado a registrar compras/entradas, saídas manuais e ajustes administrativos de estoque. |
| RN22 | A COZINHA pode gerar saídas apenas por fluxos operacionais autorizados: consumo diário, consumo de item aberto e desperdício/sobra; não pode criar entrada ou ajuste manual. |
| RN23 | O estoque é único para o restaurante. Todas as sessões autenticadas consultam e alteram a mesma base central, respeitando as permissões de cada perfil. |
| RN24 | Operações concorrentes sobre o mesmo lote não podem sobrescrever alterações já confirmadas por outro aparelho; o backend deve detectar conflito e exigir nova leitura quando necessário. |
| RN25 | O percentual ideal de CMV pode ser definido por ADMIN e NUTRICIONISTA, deve ser maior que zero e compatível com percentual. |
| RN26 | CMV% = (CMV / receita base do período) x 100. Se não houver receita válida para o período, o sistema apresenta o CMV absoluto sem calcular o percentual. |
| RN27 | A aplicação não deve permitir movimentações offline na primeira versão, evitando divergências entre aparelhos. Operações de estoque exigem conexão com o backend central. |

## 8. Cálculo do CMV

| Definição consolidada — CMV do período = Estoque Inicial + Compras/Entradas do período - Estoque Final. Quando a receita base do período for informada, CMV% = (CMV / Receita do período) x 100. ADMIN e NUTRICIONISTA podem definir o percentual ideal de CMV, e o relatório apresenta o valor realizado, a meta e a diferença entre eles. |
| --- |

Critérios técnicos adotados:

Valorizar cada lote pelo seu preço unitário registrado na entrada.

Reconstruir o estoque inicial/final pelos lotes e deltas das movimentações nas datas de corte; ajustes aplicados de balanço integram o histórico.

A reconstrução histórica não modifica o saldo armazenado e preserva o custo unitário dos lotes.

Apresentar, no relatório do período, pelo menos: estoque inicial, total de entradas, estoque final, CMV absoluto, receita base informada, CMV percentual, CMV ideal, desvio em relação à meta, consumo registrado, desperdício registrado e percentual do desperdício sobre o CMV.

A fórmula de CMV desta constituição está fechada para a primeira versão. Integração automática com PDV/faturamento fica fora do escopo; quando necessário ao CMV percentual, a receita base do período será informada no sistema.

## 9. Entidades e relacionamentos do domínio

| Entidade | Responsabilidade |
| --- | --- |
| Usuario | Autenticação, perfil e autoria das operações. |
| Categoria | Classifica produtos. |
| Produto | Item controlado; unidade de medida; saldo atual derivado. |
| ParametroEstoque | Parâmetros de reposição, consumo médio, níveis mínimo/médio/máximo e dias de antecedência para alerta de vencimento. |
| Lote | Quantidade, entrada, validade e preço unitário por lote. |
| MovimentacaoEstoque | Classe abstrata para movimentações e trilha de saldo. |
| Entrada | Movimentação de entrada; valor pago; gera lote. |
| Consumo | Movimentação de saída por uso; possui custo calculado. |
| Desperdicio | Movimentação de perda; motivo e valor do prejuízo. |
| Ajuste | Correção decorrente de diferença apurada. |
| ProdutoAberto | Controle de embalagem já aberta, quantidade da embalagem, quantidade utilizada, quantidade restante, lote de origem e finalização. |
| Alerta | Avisos operacionais e gerenciais. |
| ConfiguracaoBalanco | Periodicidade e próxima execução. |
| Balanco | Conferência física periódica. |
| ItemBalanco | Quantidade do sistema, quantidade física e diferença por produto. |
| Relatorio | Solicitação/registro de relatório por tipo e período. |
| ParametroCmv | Armazena o percentual ideal de CMV definido por ADMIN ou NUTRICIONISTA e a data/usuário da última atualização. Extensão necessária ao UML original para atender ao requisito consolidado. |

Relações principais previstas pelo UML: Categoria classifica Produto; Produto possui Lotes e define seu ParametroEstoque; Lote origina ProdutosAbertos; MovimentacaoEstoque pode referir-se a um Lote; Entrada, Consumo, Desperdicio e Ajuste herdam de MovimentacaoEstoque; Balanco é composto por ItemBalanco; ItemBalanco confere um Produto e pode gerar Ajuste; usuários conduzem/solicitam/registram operações do sistema. Como extensão desta constituição, o domínio também deverá persistir o ParametroCmv, vinculado ao restaurante da sessão e atualizado pelo perfil NUTRICIONISTA.

## 10. Relatórios e indicadores

| Relatório/indicador | Conteúdo mínimo |
| --- | --- |
| Estoque atual | Produto, unidade, saldo, nível mínimo/médio/máximo, situação e filtro “somente itens abertos”. |
| Próximo vencimento | Produto, lote, validade, dias restantes e quantidade. |
| Vencidos | Produto, lote, validade, quantidade e valor estimado. |
| Produtos abertos | Produto/lote, data de abertura, quantidade da embalagem, quantidade utilizada, quantidade restante e status. |
| Desperdício | Período, produto, lote, motivo, quantidade e valor do prejuízo. |
| Consumo médio | Produto, período analisado, consumo total e média diária. |
| CMV | Estoque inicial, compras/entradas, estoque final, CMV absoluto, receita base, CMV%, percentual ideal e desvio da meta. |
| Dashboard | Contadores de itens em estoque, baixos, vencendo, vencidos, abertos e balanços pendentes. |

## 11. Arquitetura e base tecnológica

A arquitetura seguirá a mesma linha didática do repositório PIAds3: uma área core com componentes genéricos reutilizáveis e uma área business com os módulos de domínio. O core deverá conter somente código que possa ser reutilizado genericamente por vários módulos, sem importar classes de business. O backend será a fonte única de verdade do estoque e atenderá simultaneamente aos aparelhos conectados.

| Camada/elemento | Papel no EstoQ |
| --- | --- |
| core/domains | BaseModel e abstrações realmente comuns. |
| core/dtos | BaseDTO. |
| core/repositories | IGenericRepository. |
| core/services | IGenericService e GenericService. |
| core/validations | IGenericValidation e GenericValidation. |
| core/controllers | GenericController para CRUD comum. |
| core/helpers | IGenericAdapter e utilitários reutilizados por vários módulos. |
| core/exceptions | Exceções padronizadas e GlobalExceptionHandler. |
| business/<modulo> | Model, DTO, Adapter, Repository, Validation, Service e Controller específicos. |

### 11.1 Distribuição da aplicação e uso simultâneo

O EstoQ é disponibilizado como site multitenant. Os usuários acessam pelo navegador e as operações dependem do backend central e da conexão com a internet.

Os aparelhos de um restaurante utilizam o mesmo backend e o banco compartilhado da plataforma. Todas as consultas e gravações operacionais são isoladas pelo restaurante autenticado. Não existem bancos locais independentes.

A primeira versão deverá exigir conexão com a internet para qualquer alteração de estoque. Não haverá sincronização de movimentações offline, pois esse cenário aumenta o risco de conflitos e foge do escopo acadêmico inicial.

Cada aparelho terá sua própria sessão autenticada. O backend aplicará as permissões do perfil em todas as rotas, independentemente do que o front exibir ou ocultar.

Para evitar perda de atualização quando dois aparelhos alterarem dados próximos no tempo, as operações de estoque serão transacionais e as entidades críticas de saldo/lote deverão usar controle de concorrência otimista do JPA, por exemplo com @Version. Após uma alteração, o front deverá consultar novamente o estado retornado pelo backend.

Stack do backend alinhada ao repositório de referência: Java 21, Spring Boot, Maven, Spring Web MVC, Spring Data JPA, Bean Validation, PostgreSQL e Lombok. O front deverá ser uma aplicação web multitenant; o framework de interface poderá ser definido separadamente. Novas bibliotecas só devem ser adicionadas quando houver necessidade funcional clara e compatibilidade com o conteúdo do curso.

## 12. Requisitos não funcionais

RNF01 — Manutenibilidade: código modular, nomes claros, métodos curtos e regras de negócio fora de controllers.

RNF02 — Consistência: operações que alteram estoque devem ser transacionais e realizadas exclusivamente no backend central.

RNF03 — Integridade: não permitir quantidades negativas nem relacionamentos inválidos.

RNF04 — Rastreabilidade: movimentações e balanços devem registrar usuário e data/hora.

RNF05 — Desempenho: consultas de dashboard e relatórios devem usar paginação quando aplicável e evitar carregar relações LAZY desnecessariamente.

RNF06 — Segurança: senhas nunca em texto puro; autenticação e autorização por perfil são obrigatórias na primeira versão.

RNF07 — Qualidade: validações de campos e regras de negócio padronizadas, com tratamento global de erros.

RNF08 — Testabilidade: services e validações críticas devem possuir testes unitários.

RNF09 — Concorrência: suportar pelo menos três sessões simultâneas sem perda de atualização de estoque.

RNF10 — Centralização: todos os aparelhos devem utilizar o mesmo backend e o mesmo banco compartilhado com isolamento por restaurante.

RNF11 — Distribuição: aplicação web multitenant acessível pelo navegador; operações dependem de conexão com o backend.

RNF12 — Conectividade: movimentações de estoque exigem conexão; não haverá escrita offline na primeira versão.

RNF13 — Controle de concorrência: alterações simultâneas sobre registros críticos devem ser protegidas por transação e locking otimista, evitando sobrescrita silenciosa.

RNF14 — Atualização de interface: após qualquer movimentação, a tela deve refletir os dados confirmados pelo backend; telas de acompanhamento podem realizar atualização periódica leve.

## 13. Critérios de aceite da primeira versão

Cadastrar produto e categoria e consultar o produto ativo.

Registrar uma compra e visualizar o lote gerado com validade e preço unitário.

Visualizar o saldo atual calculado do produto.

Receber/listar alerta quando o produto estiver abaixo do mínimo.

Receber/listar alerta para lote próximo do vencimento e lote vencido.

Abrir uma embalagem, informar quantidade da embalagem e quantidade utilizada, verificar o consumo gerado e visualizar o restante como item aberto.

Registrar consumo pela COZINHA e verificar a baixa e o custo correspondente no estoque central.

Registrar sobra/desperdício e verificar a baixa e o valor do prejuízo.

Executar balanço pela COZINHA, informar contagem física, apurar diferença e permitir que o ADMIN confirme/efetive o ajuste.

Gerar os relatórios previstos no diagrama.

Calcular CMV de um período com valores reproduzíveis, calcular CMV% quando houver receita base e comparar com o percentual ideal definido por ADMIN ou NUTRICIONISTA.

Garantir que o core não contenha importações/regras específicas dos módulos business.

Validar que ADMIN, COZINHA e NUTRICIONISTA consigam permanecer autenticados simultaneamente em três aparelhos.

Validar permissões: ADMIN gerencia entradas/ajustes; COZINHA registra consumo e balanço; NUTRICIONISTA define CMV ideal e consulta relatórios.

Validar que duas operações próximas no tempo não gerem saldo negativo nem sobrescrevam silenciosamente alterações do mesmo lote.

Acessar o site em aparelhos de teste e confirmar o estado compartilhado dentro do restaurante e o isolamento entre restaurantes.

Filtrar o estoque para exibir somente itens abertos.

## 14. Plano de implementação sugerido

| Fase | Entrega |
| --- | --- |
| 1 | Core genérico + configuração do projeto + PostgreSQL + tratamento de erros + autenticação/autorização dos três perfis. |
| 2 | Categoria, Produto, ParametroEstoque (incluindo dias de alerta de vencimento), Lote e consultas de estoque. |
| 3 | MovimentacaoEstoque + Entrada + saldo derivado + controle transacional/concorrência. |
| 4 | Consumo diário, abertura de embalagem, ProdutoAberto, desperdício/sobra, filtro de itens abertos e alertas. |
| 5 | Balanço, ItemBalanco, Ajuste e ConfiguracaoBalanco, separando contagem da COZINHA e confirmação do ADMIN. |
| 6 | ParametroCmv, receita base do período, CMV absoluto/percentual, meta definida pelo NUTRICIONISTA, relatórios e dashboard. |
| 7 | Integração com o frontend web multitenant, atualização de estado entre aparelhos e testes de três sessões simultâneas. |
| 8 | Testes unitários/integrados, documentação da API, revisão de permissões e preparação da apresentação acadêmica. |

## 15. Decisões de escopo consolidadas

| Decisões validadas pelo responsável pelo projetoOs pontos de escopo anteriormente pendentes foram definidos e passam a orientar a versão 1.0 da constituição e a implementação do sistema. |
| --- |

O EstoQ atende vários restaurantes no mesmo ambiente. Cada usuário opera exclusivamente no restaurante vinculado à sua sessão; PLATAFORMA administra os restaurantes.

A antecedência do alerta de próximo vencimento será configurável pelo ADMIN, preferencialmente no ParametroEstoque de cada produto.

Fluxo de abertura: selecionar produto/lote, informar a quantidade da embalagem e quanto foi utilizado. A parcela utilizada gera consumo; o restante volta ao estoque como ProdutoAberto e pode ser localizado pelo filtro de itens abertos. Somente quando esse restante não for aproveitado será registrado como desperdício.

O CMV seguirá Estoque Inicial + Compras - Estoque Final. ADMIN e NUTRICIONISTA poderão definir o percentual ideal de CMV; quando houver receita base informada, o sistema calculará também o CMV percentual para comparação.

Autenticação e autorização dos perfis ADMIN, COZINHA e NUTRICIONISTA fazem parte da primeira versão e devem ser aplicadas no backend.

A distribuição ocorre pelo site, sem instalação de executável ou requisito de PWA. Após cada operação, as telas consultam o servidor e o cache permanece restrito à sessão do restaurante.

## 16. Glossário

| Termo | Definição |
| --- | --- |
| CMV | Custo da Mercadoria Vendida/consumida no período, calculado a partir da movimentação do estoque. |
| Lote | Conjunto de unidades de um produto com entrada, validade e custo próprios. |
| Saldo atual | Quantidade física calculada pela soma dos lotes. O saldo disponível para consumo é separado e exclui vencidos. |
| Desperdício | Perda de insumo por vencimento, deterioração, preparo incorreto, sobra não aproveitada ou outro motivo. |
| Balanço | Conferência entre a quantidade física contada e a quantidade registrada no sistema. |
| Ajuste | Movimentação de correção que alinha o sistema à diferença validada no balanço. |
| Aplicação web multitenant | Aplicação acessada pelo navegador, com vários restaurantes isolados por restaurante_id em um backend/banco compartilhado. Não exige executável ou instalação PWA. |
| CMV ideal | Percentual de referência definido por ADMIN ou NUTRICIONISTA para comparação com o CMV percentual realizado. |
| Item aberto | Parcela restante de uma embalagem já aberta que continua fisicamente disponível no estoque e mantém vínculo com seu lote de origem. |

Referências estruturais utilizadas

Diagrama de domínio: “DIAGRAMA DE CLASSES — SISTEMA EstoQ”, Faculdade SENAI FATESG, ADS, 2026, fornecido pelo aluno. O diagrama permanece como base das entidades e relacionamentos; os requisitos consolidados desta versão acrescentam a persistência do percentual ideal de CMV e a parametrização do alerta de validade.

Base de estudos do backend: https://github.com/feitosapedrofatesg-svg/PIAds3.git — consultado para manter o padrão de core genérico + módulos de negócio, DTOs, adapters, validações, services, repositories e controllers.

## 17. Consolidação da implementação — 12/10/2026

Esta revisão preserva RF01–RF28, RN01–RN27 e os requisitos não funcionais, e consolida as mudanças implementadas até o commit 6bdddad. Os nomes históricos dos arquivos Word foram preservados. A versão equivalente em Markdown está em CONSTITUICAO.md.

### 17.1 Multitenancy e backup

O sistema é web multitenant. Cada sessão operacional pertence a um restaurante e não acessa dados de outro. PLATAFORMA administra restaurantes e possui acesso exclusivo ao backup completo do banco/schema configurado. ADMIN, COZINHA e NUTRICIONISTA não geram, listam ou baixam esse dump. Não há backup individual por restaurante nesta etapa. A geração é manual, baixada imediatamente e temporária no servidor; guardar o arquivo fora dele é necessário para preservá-lo.

### 17.2 Permissões e desperdício

A COZINHA continua autorizada a registrar consumo, abertura e desperdício operacional, criar/iniciar/contar balanço e consultar dados permitidos. Não acessa o bloco Itens para excluir do estoque, não registra entradas nem executa conclusão/ajustes administrativos. ADMIN e NUTRICIONISTA podem configurar a meta de CMV e informar receita base. Toda diferença negativa de balanço já aplicada integra o desperdício geral, sem gerar segunda baixa.

### 17.3 Entrada automatizada com revisão

ADMIN pode analisar XML, PDF com texto ou fotos JPG/PNG de nota fiscal. A análise não muda estoque. A confirmação global exige a conferência final dos itens, descontos e custos rateados, mantendo as validações de quantidade, unidade, fator e custos. Produto existente recebe entrada/lote; produto novo é criado. Vínculos e sugestões de categoria são isolados por restaurante. A confirmação é transacional e idempotente.

Restaurante novo recebe 13 categorias padrão, meta de CMV 30% e configuração mensal, sem produtos ou estoque. A associação de categorias já está disponível na primeira nota; casos desconhecidos usam Sem classificação. A migração V6 prepara restaurantes ativos elegíveis sem categorias ativas.

### 17.4 Balanço concorrente e atualização

O backend bloqueia a raiz do balanço antes de consultar os itens e valida estado e versões. Contagem envia versão do item; início/ajustes enviam versão do balanço; conclusão envia versão do balanço e versões de todos os itens. Conflitos retornam 409 e exigem nova leitura. Balanço concluído rejeita contagens antigas. Não há reabertura implementada.

Cache em memória da aba reutiliza consultas por sessão: 15 segundos para dados operacionais e um minuto para os demais. Gravações e eventos SSE invalidam o cache antes desses prazos. SSE envia avisos após commit apenas para o restaurante correspondente; polling/reconexão recuperam perdas. Rascunhos locais são preservados e divergências são sinalizadas. Cache não oferece escrita offline.

O próximo balanço aparece no dashboard e na tela de balanço. O aviso de agendamento vencido é avaliado nas consultas autenticadas dos alertas, sem duplicar ou reabrir o já visto; a data avança ao concluir. Não há avaliação global por scheduler de balanços às 06h.

### 17.5 Interface, desempenho e horários

Históricos mostram movimentos recentes primeiro; embalagens abertas seguem a última movimentação/abertura. Isso não altera seleção de lotes para consumo nem a ordenação de catálogo/estoque. Visto na central do cabeçalho remove a linha imediatamente, bloqueia cliques repetidos e devolve o alerta se a gravação falhar.

Somente os blocos definidos em RESPONSIVIDADE.md começam recolhidos. Histórico de desperdício e embalagens abertas ficam visíveis. Nos relatórios, Desperdício e Consumo médio têm rolagem interna quando há mais de oito itens; Reposição sugerida mantém Maximizar/Minimizar.

Consultas de balanço carregam os relacionamentos necessários e criam retrato/alertas em lote para evitar consultas por produto. A proteção de concorrência não depende da velocidade de atualização do navegador.

Horários de eventos/criação/atualização são persistidos em UTC. A API usa LocalDateTime sem offset, interpretado como UTC pela interface e exibido no fuso do navegador: 19:26 UTC aparece como 16:26 na Bahia. Datas sem horário, como validade e agendamento, preservam o dia. Não houve subtração de três horas no banco histórico.

### 17.6 Validação e limites

A CI ativa em .github/workflows/validacao.yml executa backend Java 21 com OCR e frontend Node 24 com build/Playwright nas branches dev/main. A implementação recente foi validada por testes de isolamento, transação, versões, SSE, limite de consultas, cache, alertas e horário.

Sessão, limite de login e SSE estão em memória de uma instância. Backup individual, escrita offline, reabertura de balanço, PDV, emissão fiscal e integração automática de faturamento permanecem fora dos recursos implementados. Consulte HISTORICO_DE_MUDANCAS.md e os contratos de API/desenvolvimento para as entregas e os detalhes atuais.

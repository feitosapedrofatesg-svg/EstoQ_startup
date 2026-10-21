# Contrato de desenvolvimento — backend em camadas

Atualização posterior: [correções de 18/10/2026](REVISAO_SEGURANCA_2026_10_18.md). O conteúdo abaixo preserva a revisão histórica correspondente ao Word.

Revisão: **12/10/2026**, consolidando o código até `6bdddad`.
Correspondente ao documento Word `EstoQ_Prompt_Backend_Modular_Camadas_v1.0.docx`.

## Orientação para novas alterações

Desenvolva o EstoQ como uma aplicação acadêmica de ADS do 4º período: código
limpo, nomes claros, dependências explícitas, métodos pequenos e comentários em
português quando explicarem decisões. Preserve o padrão PIAds3: núcleo genérico
mais módulos de negócio. Não introduza microserviços, CQRS ou outras arquiteturas
sem uma necessidade aprovada. Novas dependências precisam resolver uma necessidade real.

O produto é web multitenant. Restaurante é parte do domínio, e entidades
operacionais devem usar `restaurante_id`. Não tratar ADMIN como administrador
global; PLATAFORMA possui um conjunto separado de capacidades.

## Camadas e responsabilidades

| Camada | Responsabilidade |
| --- | --- |
| `core` | BaseModel/BaseDTO, CRUD genérico, adapters e validações comuns, tratamento de erros e contratos reutilizáveis. Sem imports de business. |
| `business/<modulo>` | Model, DTO, Adapter, Repository, Validation, Service e Controller do domínio. |
| `config` | Segurança, resolução do tenant, seed de desenvolvimento e configuração da aplicação. |
| `backup` | Geração/listagem/download do dump completo, fora do núcleo genérico. |
| `patterns` | Exemplo didático de Singleton para registrar consumo concorrente. |

Controller recebe, valida o formato e delega. Service orquestra regras e transações.
Repository consulta/persiste. Adapter converte entidade/DTO. Evite serializar
entidades com relações LAZY. Use DTOs, graphs e consultas em lote para os dados
necessários; `open-in-view=false` permanece habilitado por configuração.

A notificação do CRUD genérico depende de um contrato em `core`; a implementação
específica de restaurante/grupos fica em `business/sincronizacao`.

## Base técnica e contratos

Java 21, Spring Boot 4.0.6, Maven, Web MVC, JPA, Bean Validation,
Spring Security, PostgreSQL e Lombok. `BigDecimal` para valores/quantidades.
`LocalDate` para datas sem horário; `LocalDateTime` UTC para eventos, com
conversão de exibição no navegador. Não aplicar conversão UTC às datas de validade.

Sessão HTTP com BCrypt, cookie HttpOnly/Secure em produção e CSRF. O tenant é
resolvido pela sessão, nunca aceito livremente do request. SQL nativo deve
incluir predicado de restaurante; o isolamento do ORM não corrige SQL sem filtro.

A autorização segue `SecurityConfig`: COZINHA pode desperdício operacional e
contagem, mas não entrada/exclusão administrativa/conclusão/ajustes. Relatórios
são ADMIN/NUTRICIONISTA. PLATAFORMA administra restaurantes e acessa exclusivamente
o backup completo. Não implementar backup individual implicitamente.

## Integridade e concorrência

Saldo é derivado dos lotes. Saldo de embalagem aberta já faz parte do lote.
Lote vencido existe fisicamente, mas é indisponível para consumo.
Operações de estoque são transacionais, auditáveis e impedem quantidade negativa.

Preserve `@Version`. No balanço, bloqueie a raiz sem joins antes de carregar os
itens; valide estado e as versões obrigatórias. Não deixe uma sessão antiga
alterar balanço concluído. HTTP 409 exige leitura atual e decisão do usuário;
não repetir silenciosamente uma gravação com versões novas.

Ajustes negativos já aplicados contam como desperdício, sem gerar segunda baixa.
Déficit distribui baixa pelos lotes; superávit soma ao lote adequado ou cria lote
de correção consumível com validade nula. Ajustes são idempotentes.

Importação de nota precisa de revisão humana, transação e proteção contra
confirmação/entrada duplicada. Reutilize ProdutoService e EntradaService.
Sugestões de categoria não autorizam assumir embalagem, custo ou quantidade inválidos.

## Atualização e desempenho

Eventos SSE são emitidos somente após commit e ao restaurante correspondente.
O backend valida sessão antes de cada envio; evento não carrega dados completos.
Cache de 15 segundos/um minuto é restrito à sessão, com invalidação após gravação
ou aviso remoto. Rascunhos não podem desaparecer em atualização automática.

Evite consultas por produto no início/conclusão do balanço. Retrato e alertas
usam SQL em lote; graphs carregam produto, categoria e parâmetro necessários.
Não bloquear a requisição de estoque esperando envio SSE ou conclusão de OCR
em outra requisição. OCR e backup possuem seus próprios limites de execução.

## Migrações, testes e entrega

Não reescreva V1–V6 ou B4 já aplicadas. Banco novo usa baseline B4; legado recebe
migrações incrementais. Mudança de schema exige nova migração e teste de integridade.
Não usar `ddl-auto=update` em produção. Seed fica em `config/seed`, só em dev.

Teste as invariantes afetadas: saldo, custo, permissões, tenant, transação,
concorrência, idempotência e comportamento da interface. Use dados fictícios nos
testes; não grave documentos fiscais reais no repositório. A CI ativa é
`.github/workflows/validacao.yml`.

Atualize a API, os documentos do módulo e o histórico da mudança. Preserve
coerência entre Markdown e Word. O fluxo atual é dev → homologacao → main:
confira a CI, valide o commit em homologacao e integre somente a versão aprovada
na main com fast-forward. Veja [HOMOLOGACAO.md](HOMOLOGACAO.md).
Publicação só está concluída após verificar deploy e site.

Consulte [API_ENDPOINTS.md](API_ENDPOINTS.md),
[CACHE_E_SINCRONIZACAO.md](CACHE_E_SINCRONIZACAO.md) e
[CONSTITUICAO.md](CONSTITUICAO.md) para os contratos completos.

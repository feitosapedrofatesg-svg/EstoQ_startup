# EstoQ — resumo do sistema implementado

Revisão: **12/10/2026** · Código de referência: `6bdddad`.
Projeto acadêmico de ADS, 4º período, SENAI FATESG. Repositório local:
`/home/pedro/EstoQ`. A arquitetura segue o padrão PIAds3 em camadas.

## Produto e arquitetura

Aplicação **web multitenant**, acessada pelo navegador, sem executável,
instalação obrigatória, PWA ou escrita offline. O backend é a fonte de verdade;
cache no navegador melhora a navegação e não autoriza alterações de estoque.

```text
Navegador React → Vercel (/api por proxy) → Spring Boot no Render → PostgreSQL no Neon
```

| Parte | Implementação |
| --- | --- |
| Backend | Java 21, Spring Boot 4.0.6, Maven, Spring MVC, JPA, Validation, Security e Lombok. |
| Frontend | React 18, TypeScript, Vite e React Router. |
| Banco | PostgreSQL; desenvolvimento local com Docker na porta 5434. |
| Migrações | Flyway; baseline B4 e evolução V1–V6 conforme o estado da base. |
| Documentos | PDFBox para PDF; leitores NF-e e Tesseract para fotos. |
| Testes | JUnit/integrados, H2, PostgreSQL temporário e Playwright. |

`core` contém CRUD, entidades base, adapters e validações genéricas, exceções e
contratos reutilizáveis. Não importa `business`. `business/<modulo>` contém
Model, DTO, Adapter, Repository, Validation, Service e Controller conforme a
necessidade. `config` concentra segurança, resolução de tenant e seed de dev.
`backup` e `patterns` ficam fora do núcleo genérico.

O controller recebe e delega; o service executa regras e transações; o repository
acessa dados; o adapter converte entidades e DTOs. Consultas SQL nativas precisam
filtrar explicitamente o restaurante, além de respeitar o isolamento do JPA.

## Restaurantes, sessões e permissões

O cadastro público cria restaurante, ADMIN, 13 categorias padrão, meta de CMV
30% e agendamento mensal. Não cria produtos ou saldo de estoque. A categoria
sugerida na primeira nota já pode ser utilizada por um restaurante novo.

Entidades operacionais usam `restaurante_id`; o tenant vem da identidade
autenticada. IDs de outro restaurante não concedem acesso. Desativação de usuário,
suspensão de restaurante ou alteração de credenciais revogam o acesso correspondente.

| Perfil | Responsabilidade |
| --- | --- |
| ADMIN | Cadastros, entradas, notas fiscais, operações de estoque, conclusão e ajustes de balanço, relatórios e configurações. |
| COZINHA | Consultas operacionais, consumo, abertura de embalagem, desperdício operacional, criação/início/contagem de balanço e leitura de alertas. |
| NUTRICIONISTA | Dashboard, relatórios, meta de CMV, receita base e próximo agendamento. |
| PLATAFORMA | Administração de restaurantes, suspensão/reativação, redefinição do administrador e backup completo. |

A COZINHA não possui o bloco **Itens para excluir do estoque**, não registra
entrada administrativa e não conclui nem aplica ajustes de balanço. Desperdício
operacional permanece autorizado. Não há backup individual por restaurante;
ADMIN, COZINHA e NUTRICIONISTA não acessam o dump completo da plataforma.

Login usa e-mail/senha BCrypt, sessão HTTP, cookie HttpOnly `ESTOQSESSION` e
CSRF. Token rotaciona após login. Sessão tem prazo configurado de 24 horas.
Tentativas de login: limite padrão de cinco falhas em 15 minutos por e-mail/IP.
As permissões são aplicadas no backend, independentemente dos botões da tela.

## Estoque, consumo e desperdício

- Saldo físico é a soma das quantidades dos lotes, incluindo vencidos ainda presentes.
- `saldoDisponivelConsumo` exclui lotes vencidos; saldo aberto não é somado novamente.
- Entrada converte KG/G ou L/ML, calcula custo unitário e cria lote na mesma transação.
- Lote sem validade é consumível enquanto tiver saldo; não recebe validade inventada na entrada manual.
- Entrada gratuita exige `semCusto=true`; valor positivo e essa flag são incompatíveis.
- Consumo prioriza embalagens abertas e depois os lotes disponíveis ordenados por validade.
- Abrir embalagem registra consumo apenas do utilizado; o restante permanece no lote e no controle de abertos.
- Saídas não podem deixar saldo negativo. Estoque usa `@Version` para detectar concorrência.
- Desperdício admite vencidos e mantém motivo, usuário, lote, quantidade e custo.
- Ajuste negativo de balanço já aplicado integra o desperdício geral, preservando o movimento AJUSTE e evitando dupla baixa.

Históricos aparecem do mais recente para o mais antigo, com desempate por ID.
Embalagens abertas são ordenadas pela última movimentação ou abertura. A ordem
operacional do consumo continua seguindo sua regra de lotes, separadamente da
ordem visual. Estoque e catálogo não receberam ordenação por último movimento.

## Notas fiscais

ADMIN envia XML, PDF com texto ou JPG/PNG/fotos. A leitura cria uma revisão;
a entrada só ocorre após a conferência final. O catálogo e os vínculos do fornecedor
orientam a associação; nomes e categorias padrão permitem sugestão automática.
Se o produto existe, a confirmação acrescenta um lote e sua quantidade. Caso
contrário, cria produto e entrada. Não sobrescreve o histórico existente.

A caixa **Conferi os itens, os descontos e os custos rateados** confirma a revisão
como um todo; não exige marcar cada linha individualmente. Dados inválidos,
quantidades incompatíveis ou custos inconsistentes continuam bloqueando a entrada.
A confirmação é transacional e idempotente. Detalhes em
[IMPORTACAO_NOTAS_FISCAIS.md](IMPORTACAO_NOTAS_FISCAIS.md).

## Balanços e atualização entre aparelhos

Fluxo: criar → iniciar → contar → ADMIN confirmar → ADMIN gerar ajustes.
O balanço parcial restringe os produtos às categorias escolhidas.

Cada gravação bloqueia primeiro o balanço pai e valida estado e versões.
Contagem informa a versão do item; início/ajustes informam a versão do balanço;
conclusão informa a versão do balanço e de todos os itens apresentados.
Conflitos devolvem HTTP 409; balanço concluído rejeita novas contagens, inclusive
quando a tela da COZINHA ainda estiver antiga. Não há reabertura implementada.

SSE avisa somente as sessões do mesmo restaurante após o commit. A tela reconsulta
os dados, preserva rascunhos locais e sinaliza divergências antes de salvar.
Cache usa 15 segundos para dados operacionais e um minuto para os demais.
Não é preciso aguardar esse prazo quando uma gravação invalida o cache ou chega um
aviso remoto. Ver [CACHE_E_SINCRONIZACAO.md](CACHE_E_SINCRONIZACAO.md).

O próximo balanço aparece no dashboard e na tela de balanço. No dia marcado ou
em atraso, consultas autenticadas aos alertas avaliam o agendamento e criam o
aviso sem duplicar/reabrir o já visto. A data avança quando o balanço é concluído;
não há scheduler global de balanços às 06h.

## CMV, relatórios e horários

CMV = estoque inicial + compras − estoque final. A posição histórica é reconstruída
pelos lotes e deltas das movimentações nos cortes do período. Consumo registrado e
desperdício são apresentados separadamente. Perdas não explicadas =
CMV − (consumo registrado + desperdício). Receita base é manual e persistida por
período; permite CMV percentual. ADMIN e NUTRICIONISTA configuram a meta.

Relatórios incluem estoque, vencimentos, embalagens abertas, desperdício,
consumo médio/dia da semana, CMV/mensal e reposição sugerida, com exportação PDF.
Não há vendas/PDV, emissão fiscal, receitas por prato nem cálculo automático de faturamento.

Eventos e criação/atualização são gravados em UTC, inclusive inserções em lote.
A API retorna `LocalDateTime` sem offset; o formatter identifica esse contrato e
exibe no fuso do navegador. Exemplo: 19:26 UTC → 16:26 na Bahia. Offsets explícitos
são preservados. Datas de validade/emissão/agendamento não recebem essa conversão.
O cache não altera o horário gravado. Não houve reescrita de timestamps históricos.

## Execução e validação

Na raiz, inicie PostgreSQL com `docker compose up -d postgres`. No backend,
execute `mvn spring-boot:run -Dspring-boot.run.profiles=dev` (porta 8083).
No frontend, `npm ci` e `npm run dev` (porta do Vite informada no terminal).
O proxy de desenvolvimento envia `/api` ao backend local. Dev habilita seed;
produção usa Flyway e `ddl-auto=validate`.

Validação de referência da última alteração funcional: **99 casos backend**,
sem falhas; três ignorados localmente conforme disponibilidade do OCR.
Frontend: **29 testes Playwright**, incluindo horário da Bahia e virada do dia.
Os skips dependem do ambiente; consulte o resultado da execução, não apenas a contagem.
A CI ativa também instala OCR e executa build e Playwright.

```bash
mvn -f backend/pom.xml test
npm --prefix frontend ci
npm --prefix frontend run build
npm --prefix frontend test
git diff --check
```

Testes de PostgreSQL temporário precisam poder abrir portas locais. Navegador e
OCR devem estar disponíveis para seus testes. As suítes cobrem isolamento,
revogação de sessão, backup, notas, CMV, concorrência, SSE, cache, permissões e interface.

## Publicação e limites atuais

Site: https://estoq-oficial.vercel.app. Backend:
https://estoq-backend-jv04.onrender.com. Banco de produção usa `public`; local
pode usar `estoq_v2`. Não trocar schema de produção sem migração planejada.

Git usa somente `dev` e `main`; main dispara publicação automática.
Sessão, rate limit e distribuição SSE estão na memória de uma instância.
Escalar exige armazenamento/distribuição compartilhados. Arquivos de backup
no servidor são temporários; o download precisa ser guardado fora dele.
Não foram implementados backup por restaurante, escrita offline ou reabertura de balanço.

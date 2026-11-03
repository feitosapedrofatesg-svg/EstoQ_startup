# EstoQ — Controle de Estoque e CMV para Restaurantes

Aplicação web **EstoQ**, um sistema para gestão de estoque, lotes/validade, consumo,
desperdício, balanço físico e custo da mercadoria vendida (CMV) em restaurantes.

Projeto Integrador · SENAI FATESG · ADS · 2026

Produção está configurada no Render, na Virgínia, com PostgreSQL gerenciado no
Supabase. A homologação usa serviços separados. O banco local de desenvolvimento
é PostgreSQL 16 via Docker. O backend conecta ao PostgreSQL por JDBC; não depende
da API REST do Supabase. Consulte [infraestrutura de produção](documentacao/MIGRACAO_REGIAO_VIRGINIA.md),
[configuração do banco](documentacao/DEPLOY_SUPABASE.md) e
[guia de homologação](documentacao/HOMOLOGACAO.md).

O Neon aparece em registros históricos de incidentes e operação; não é o destino
de produção descrito atualmente.

## Ambientes

| Ambiente | Branch | Frontend | Backend |
| --- | --- | --- | --- |
| **Produção** | `main` | **https://estoq.site** | https://estoq-backend-va.onrender.com |
| Homologação | `homologacao` | https://estoq-homologacao.vercel.app | https://estoq-backend-homologacao-va.onrender.com |

Domínios antigos que não são os endereços oficiais atuais:
`https://estoq-oficial.vercel.app`, `https://estoq-ph-feit0sa.vercel.app`,
`https://estoq-backend-jv04.onrender.com`.

## O que resolve

O app registra entradas, lotes e validade, consumo, abertura de embalagens,
desperdício e balanços. Calcula o CMV a partir das posições históricas do estoque
e das compras; o faturamento pode ser informado por período ou por dia para
calcular o percentual. Não é um PDV e não importa vendas automaticamente.

Também permite importar notas fiscais em XML, PDF ou imagem, sugere categorias
para conferência, acompanha reposição sugerida e exporta relatórios. A sugestão
de categoria deve ser revisada antes de confirmar a entrada.

Cada restaurante tem os perfis ADMIN (gestão), OPERACIONAL (operações diárias;
identificador interno `COZINHA`) e NUTRICIONISTA (CMV e relatórios). O perfil
PLATAFORMA é separado: administra restaurantes, não pertence a uma loja e tem
acesso exclusivo ao backup completo.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.0.8, Spring Security, Spring Data JPA |
| Banco local | PostgreSQL 16 no Docker |
| Banco hospedado | PostgreSQL gerenciado no Supabase |
| Padrão arquitetônico | PIAds3 (core genérico + módulos business autocontidos) |
| Frontend | React 18, TypeScript, Vite 7 e React Router 7 |
| PDF | PDFBox 3.0.4 |
| Build | Maven (offline-capable) |

O padrão **PIAds3** separa a camada genérica (`core/`) da camada de negócio
(`business/`). O `core` nunca importa `business`. Cada módulo de negócio é
autocontido com seu controller → service → repository.

Os horários dos registros são gravados em UTC, independentemente do fuso do
servidor. A API usa `LocalDateTime` sem offset; a interface interpreta esses
horários como UTC e exibe no fuso do navegador. Datas sem horário, como validade
e agendamento de balanço, preservam o dia informado. Os filtros por dia usam o fuso de operação `America/Sao_Paulo`, convertendo os limites para UTC.

## Estrutura

```
EstoQ_Startup/
├── docker-compose.yml
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/estoq/
│       ├── core/                  # genérico (sem import de business)
│       ├── config/                # segurança e seed de desenvolvimento
│       ├── business/              # módulos de negócio
│       │   ├── auth/ usuarios/ categorias/ produtos/ lotes/
│       │   ├── entradas/ consumos/ desperdicios/ ajustes/
│       │   ├── produtosAbertos/ balancos/ itensBalanco/
│       │   ├── configuracoesBalanco/ alertas/
│       │   ├── parametrosCmv/ relatorios/ dashboard/
│       ├── patterns/              # componentes compartilhados
│       └── backup/                # backup completo via pg_dump
├── frontend/                      # React + Vite, publicado no Vercel
├── apis/                          # configuração e ferramentas Google/Gmail
├── keepalive/                     # Worker Cloudflare; cron vazio no código atual
├── scripts/                       # carga, medição e utilitários
├── dados/                         # backups locais (gitignored)
└── documentacao/                  # constituição, contrato, resumo
```

## Como rodar

```bash
# 1. Subir o PostgreSQL
docker compose up -d postgres

# 2. Rodar o backend (dev, porta 8083)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Ou compilar o JAR
mvn clean package -DskipTests
java -jar target/estoq-startup.jar --spring.profiles.active=dev
```

**Swagger UI:** `http://localhost:8083/swagger-ui.html` (somente dev)

## Contas de desenvolvimento e conta PLATAFORMA

O seed opcional do perfil local `dev` cria contas de demonstração para um
restaurante local: ADMIN, OPERACIONAL (identificador interno `COZINHA`) e
NUTRICIONISTA. Também cria `plataforma.dev@estoq.com`, uma conta PLATAFORMA sem
restaurante, para testar o painel global localmente. As credenciais ficam em
`backend/src/main/java/com/estoq/config/seed/SeedDataConfig.java`; são somente
para desenvolvimento e não devem ser usadas em nenhum serviço publicado.

A conta de produção é provisionada separadamente, tem senha própria e não é
criada pelo seed local; consulte [configuração do Supabase e bootstrap da
PLATAFORMA](documentacao/DEPLOY_SUPABASE.md). O ADMIN `admin@estoq.com` do seed
pertence ao restaurante local; ele não é a conta PLATAFORMA de produção.

## Autenticação e segurança

- Sessão HTTP via cookie `ESTOQSESSION` (HttpOnly, Secure, SameSite=Lax)
- CSRF obrigatório via header `X-CSRF-TOKEN` (obtido em `GET /api/auth/csrf`)
- Limite de tentativas de login: por padrão, 5 falhas em 15 minutos → `429 Too Many Requests`
  (configurável via `LOGIN_MAX_TENTATIVAS` e `LOGIN_JANELA_MINUTOS`). A contagem
  fica em memória por instância e não é compartilhada entre réplicas.
- Lock otimista em `@Version` para concorrência em lote
- Trilha de movimentações com coluna discriminadora (JOINED)
- Login e cadastro com Google opcionais, preservando o acesso com senha:
  [configuração dos clientes Web](apis/google/login.md).
- Confirmação por link usa autorização separada do remetente:
  [Gmail e confirmação de e-mail](apis/google/README.md).

### Fluxo de login

```
GET  /api/auth/csrf       → { token, headerName }
POST /api/auth/login      → { email, senha } + header X-CSRF-TOKEN → 200 + sessão
GET  /api/auth/me          → dados do usuário logado
POST /api/auth/logout      → 204, sessão invalidada
```

## Endpoints principais

| Recurso | Rotas |
|---|---|
| Categorias e produtos | `GET/POST /api/categorias` · `GET/POST /api/produtos` · `GET /api/produtos/{id}/saldo` |
| Lotes | `GET/POST /api/lotes` · `GET /api/lotes/vencidos` · `GET /api/lotes/proximos-vencimento` |
| Estoque e movimentações | `GET /api/estoque` · `GET /api/movimentacoes` · `GET/POST /api/entradas` |
| Consumo e desperdício | `POST /api/consumos` · `POST /api/desperdicios` · `POST /api/produtos-abertos/abrir` |
| Notas fiscais | `/api/notas-fiscais/analisar` · `/api/notas-fiscais/{id}/confirmar` |
| Balanço | `POST /api/balancos` · `/{id}/iniciar` · `/{id}/itens/{itemId}/contagem` · `/{id}/confirmar` · `/{id}/gerar-ajustes` |
| Alertas | `GET /api/alertas` · `PUT /api/alertas/{id}/visualizado` |
| CMV, faturamento e relatórios | `GET/PUT /api/parametros-cmv` · `/api/relatorios/*` · `/api/relatorios/pdf/{tipo}` · `/api/relatorios/exportar` |
| Dashboard | `GET /api/dashboard/resumo` |
| Backup | `GET/POST /api/backups` · `GET /api/backups/{nome}` |

Os endpoints de notas e relatórios incluem rotas auxiliares; em desenvolvimento,
consulte os controllers ou a documentação OpenAPI.

## Backup

O módulo gera um dump completo de todos os restaurantes via `pg_dump` (formato custom), exclusivo de PLATAFORMA. Administradores de restaurantes não podem acessar nenhuma operação de backup. Não existe backup individual por restaurante nesta etapa.

A geração é manual, limitada a uma por vez e a cinco minutos. Somente arquivos concluídos ficam disponíveis; falhas removem arquivos parciais. O download é transmitido sem carregar o dump inteiro na memória. O armazenamento do Render em `/tmp/estoq-backups` é temporário: preserve o arquivo baixado fora do servidor. Não são criados backups automáticos no computador.

```bash
# Via API (requer perfil PLATAFORMA)
POST /api/backups          → gera dump completo, baixa imediatamente, mantém os N últimos
GET  /api/backups          → lista backups existentes
GET  /api/backups/{nome}   → baixa o arquivo .dump
```

**Restauração:**

```bash
createdb estoq_restore
psql estoq_restore -c "CREATE SCHEMA estoq_v2;"
pg_restore --dbname estoq_restore --schema estoq_v2 --no-owner --no-privileges \
  dados/backups/estoq-YYYYMMDD-HHMMSS.dump
```

> **Nota:** restauração é uma operação da administração da plataforma, em banco separado; o dump contém todos os restaurantes e dados de autenticação. Nunca restaure sobre produção sem um procedimento próprio. O schema `estoq_v2` precisa existir antes do `pg_restore`. O dump
> gerado via `--schema` não inclui o `CREATE SCHEMA`.

## Variáveis de ambiente

| Variável | Default | Descrição |
|---|---|---|
| `PORT` | 8083 | Porta do servidor |
| `DATABASE_URL` | jdbc:postgresql://localhost:5434/estoq_startup?currentSchema=estoq_v2 | JDBC URL |
| `DATABASE_USER` | estoq | Usuário PostgreSQL |
| `DATABASE_PASSWORD` | estoq | Senha PostgreSQL |
| `DDL_AUTO` | validate | Modo Hibernate (dev: `update`) |
| `SESSION_SECURE` | true | Cookie Secure (dev: `false`) |
| `SESSION_SAMESITE` | lax | Cookie SameSite |
| `LOGIN_MAX_TENTATIVAS` | 5 | Limite de falhas antes de 429 |
| `LOGIN_JANELA_MINUTOS` | 15 | Janela deslizante para contagem |
| `ESTOQ_BACKUP_DIR` (opcional) | ../dados/backups | Pasta de destino dos dumps |
| `ESTOQ_BACKUP_HOST` (opcional) | herda `DATABASE_URL` | Host PostgreSQL para backup |
| `ESTOQ_BACKUP_PORTA` (opcional) | herda `DATABASE_URL` | Porta PostgreSQL para backup |
| `SEED_ENABLED` | false | Habilitar seed (dev: true) |

As integrações externas são opcionais e ficam desligadas por padrão. As variáveis
`GOOGLE_LOGIN_ENABLED`, `GOOGLE_LOGIN_CLIENT_ID` e `GOOGLE_LOGIN_CLIENT_SECRET`
configuram o login Google. `EMAIL_CONFIRMACAO_ENABLED`, `EMAIL_FROM`,
`APP_PUBLIC_URL`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` e
`GOOGLE_REFRESH_TOKEN` configuram o envio de confirmação pelo Gmail; esse cliente
de envio é distinto do cliente OAuth de login. Consulte `.env.example` e
`apis/google/README.md`. Não coloque credenciais reais no Git.

O cadastro público permite dez tentativas por origem a cada hora (`estoq.cadastro.max-tentativas`). Login e cadastro aplicam limites em memória, com expiração e teto de chaves por instância; os dados não são compartilhados entre réplicas e reiniciam com o processo.

As variáveis `ESTOQ_BACKUP_*` são opcionais: quando ausentes, o backup usa os valores de
`DATABASE_URL` (e `localhost`/`estoq`/`estoq_startup` como último recurso).

Nunca committar o arquivo `.env`. Use `.env.example` como referência.

## Banco novo e atualização do banco legado

O Flyway aplica `B4__schema_completo.sql` em um schema vazio. Esse baseline cria
as tabelas, índices e relacionamentos sem dados de demonstração; em seguida são
aplicadas as migrações incrementais existentes (atualmente V1–V13).
O cadastro público cria o restaurante e seu ADMIN. O perfil dev pode criar os
usuários de demonstração pelo seed.

Em bancos existentes, o Flyway preserva o histórico e aplica as migrações
pendentes. Não editar migrações já aplicadas nem limpar o banco de produção. A
versão B4 é ignorada quando já há histórico de migrações. Novas alterações de
schema devem usar uma versão V acima da última já aplicada.

O estoque informa saldo físico (inclui lotes vencidos) e saldo disponível para
consumo (lotes ativos com quantidade positiva, sem vencimento ou ainda válidos).
Desperdícios podem baixar lotes vencidos. Na tela de Desperdício, a lista de lotes
vencidos permite registrar a exclusão total ou parcial; outros lotes podem ser
selecionados para perdas por deterioração ou outros motivos. O histórico geral
inclui ajustes negativos de balanço já aplicados como desperdício, sem nova baixa
e preservando a movimentação original de ajuste. Esses déficits também compõem
os relatórios de desperdício, o CMV e o dashboard.

## Frontend

Requer Node.js 24 e npm. O sistema é um site, sem requisito de PWA, executável ou operação offline.

```bash
cd frontend
npm ci
npm run dev
npm run build
```

## Testes

```bash
cd backend
mvn test          # testes de domínio, integração e migrações PostgreSQL
mvn -o test       # offline (dependências já no .m2)
```

| Suite | O que valida |
|---|---|
| SecurityIntegrationTest | Login, sessão, CSRF, logout, desativação de usuário |
| DominioTest | Conversores, validações de domínio |
| EstoqueIntegrationTest | FIFO, abertura, consumo, desperdício, 409 por lock |
| BalancoCmvIntegrationTest | Balanço, CMV, gap, alertas, agregados |
| ConcorrenciaIntegrationTest | Duas threads no mesmo lote (Singleton + @Version) |
| LoginAttemptIntegrationTest | Limite de tentativas (429) |
| BackupPdfIntegrationTest | PDF válido, backup listável, path traversal |
| RegistroIntegrationTest | Auto-cadastro: cria loja + admin + dados padrão, e-mail duplicado 409 |
| TenantIsolamentoIntegrationTest | Isolamento entre lojas, auto-fill do tenant, suspensão, redefinição de admin, painel PLATAFORMA |

## Decisões de projeto

O projeto segue a **Constituição do Projeto** (`documentacao/EstoQ_Constituicao_do_Projeto_v1.0.docx`),
que define a arquitetura, regras de negócio e escopo. As principais decisões:

- Saldo sempre derivado do lote (nunca digitado)
- Consumo em FIFO por validade (lotes vencidos ficam de fora)
- Abertura de embalagem: o restante fica no lote, apenas o usado vira consumo
- Balanço com ajuste automático em FIFO (déficit ou superávit)
- CMV calculado por movimentações, não por fórmula estática
- Sem módulo de vendas — o faturamento é informado por dia; o CMV vem das movimentações de estoque e o percentual usa a receita do período
- Multi-tenant: cada restaurante é uma loja isolada (`@TenantId` + `restaurante_id`),
  com auto-cadastro aberto e perfil PLATAFORMA para governar as lojas (suspender,
  reativar, redefinir senha do admin)

O cache do navegador é separado por restaurante, usuário e perfil e invalidado nas mudanças de sessão. Respostas antigas não atualizam uma nova sessão. Catálogo, categorias e usuários têm paginação no servidor de 50 registros e busca por `q`; seletores percorrem todas as páginas de categorias. Parâmetros de estoque são consultados por `GET /api/parametros-estoque/por-produto/{produtoId}` (ADMIN).

Novos produtos começam com estoque mínimo **3**, na unidade de cadastro. Médio
e máximo também iniciam em 3 para manter a ordem dos níveis e podem ser editados.
A migração V9 aplica esse mínimo uma única vez aos registros existentes zerados,
preservando mínimos personalizados. Depois disso, o administrador pode escolher
outro mínimo, inclusive zero para desativar o alerta de estoque baixo.

O CMV reconstrói os saldos nos limites do período desfazendo todas as movimentações posteriores, inclusive ajustes e registros de lotes/produtos desativados. Os intervalos têm início inclusivo e término exclusivo; a data final informada inclui o dia inteiro. Quantidade e preço conservam sua precisão até os resultados monetários. ADMIN e NUTRICIONISTA configuram a meta de CMV.

O fluxo de entrega é `dev → homologacao → main`. O GitHub Actions valida pushes
e pull requests nas três branches. Veja o [guia de homologação](documentacao/HOMOLOGACAO.md)
para aprovar uma versão antes de publicar.

Para o ambiente separado de testes e medição, consulte
[homologação online](documentacao/DEPLOY_HOMOLOGACAO.md). Use somente dados de
teste e confirme que frontend, backend e banco são os de homologação antes de
validar uma versão.

Para testar a interface com APIs fictícias:

```bash
cd frontend
npx playwright install chromium
npm test
```

Para usar um Chromium já instalado, defina `PLAYWRIGHT_CHROMIUM_EXECUTABLE`. Os testes de frontend não acessam produção. Testes opcionais com notas reais exigem `-Dnotas.reais=/caminho/das/notas`; OCR real exige também `-Dnotas.ocr.executavel=/caminho/do/tesseract`. Testes ignorados não representam validação desses arquivos.

Detalhes completos em `documentacao/RESUMO_SESSAO_BACKEND.md`.

## Licença

Projeto acadêmico · SENAI FATESG · ADS 2026

## Cache das listas no navegador

As listas já visitadas aparecem imediatamente e são atualizadas em segundo plano:
estoque, lotes, consumo, desperdício, embalagens abertas, movimentações, balanços
e alertas a cada 15 segundos; cadastros, parâmetros, dashboard, relatórios, backups
e administração da plataforma a cada minuto. As buscas periódicas pausam quando
a aba está oculta ou sem conexão. Ao retornar, consultas vencidas são atualizadas.

Gravações bem-sucedidas invalidam as consultas relacionadas imediatamente, sem
esconder dados já carregados. Em falhas de atualização, a lista permanece visível
e o aviso permite tentar novamente. Requisições simultâneas são compartilhadas.

O cache é separado por sessão, restaurante, usuário e perfil, e é limpo na saída
ou troca de conta. Não usa armazenamento persistente e termina ao recarregar ou
fechar o app. A retenção é limitada a 200 consultas e aproximadamente 20 MiB de
conteúdo JSON; respostas maiores são exibidas sem retenção. Esse limite mede o
conteúdo serializado, não a memória total usada pelo navegador.

## Sincronização entre usuários e proteção do balanço

Cada aba visível e autenticada de um restaurante abre uma conexão SSE em
`GET /api/sincronizacao/eventos`. Após confirmar uma transação, o servidor avisa
quais grupos de dados mudaram; as telas atualizam as consultas relacionadas,
agrupando avisos próximos durante 250 ms. Nenhum conteúdo de estoque, usuário ou
restaurante é enviado pelo canal. O restaurante é obtido da sessão autenticada.

A conexão recebe heartbeat a cada dez segundos, renova após um minuto e tenta
reconectar após três segundos. Logout, revogação da conta, aba oculta e ausência
de conexão encerram o canal. Uma conexão nova revalida os dados; as consultas
periódicas de 15 segundos e um minuto continuam como recuperação. A distribuição
SSE foi projetada para uma única instância do backend. Mais instâncias exigem um
canal compartilhado de eventos antes de escalar o serviço.

As gravações do balanço bloqueiam o balanço no banco antes de conferir seu estado.
Contagens enviam a versão do item; início e ajustes enviam a versão do balanço;
a conclusão envia também as versões de todos os itens apresentados. Uma versão
desatualizada retorna HTTP 409, sem sobrescrever outra contagem ou editar um
balanço concluído. Clientes antigos sem versão devem recarregar a aplicação.
Não foi necessária migração do banco: as versões existentes foram reaproveitadas.

Ao receber alterações, campos sem edição acompanham a contagem atual. Rascunhos
em conflito são preservados e oferecem **Recarregar contagem** antes de gravar.
Uma conclusão remota remove a edição e avisa se havia rascunhos não salvos.
Os testes cobrem concorrência com PostgreSQL, sessões HTTP com SSE, isolamento
entre restaurantes, rollback, revogação e os comportamentos do navegador.

O balanço carrega os parâmetros de produto junto dos itens, e as categorias são
lidas em lotes. O início cria o retrato de quantidades com uma única inserção
por consulta, limitada explicitamente ao restaurante do balanço bloqueado.
Atualizações dos itens usam lotes de 50 registros. Avisos remotos recebidos
durante uma consulta aguardam seu término e pedem uma única leitura posterior,
sem acumular consultas simultâneas. A primeira conexão SSE aproveita as leituras
já abertas pela tela. O teste de desempenho usa 186 produtos e limita o número
de consultas de listagem, início, conclusão e ajustes sem diferenças.

Históricos de movimentação, consumo e desperdício exibem primeiro os registros
mais recentes. Embalagens abertas usam a última movimentação, ou a abertura se
não houver movimentação, como ordem de exibição. A central retira imediatamente
o alerta marcado como visto, restaura-o se houver falha e bloqueia os botões
durante a gravação e por 500 ms após a resposta para proteger cliques repetidos.

## Verificação de dependências

```bash
mvn -B -f backend/pom.xml dependency:list -DincludeScope=runtime -DoutputFile=target/dependencias.txt
python3 scripts/verificar_dependencias.py backend/target/dependencias.txt
npm audit --prefix frontend --audit-level=high
```

A consulta ao OSV exige internet e usa somente nomes e versões dos pacotes. A CI executa essas verificações. Ausência de avisos conhecidos não substitui os testes e a revisão de segurança.

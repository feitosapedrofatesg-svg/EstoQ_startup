# Homologação online do EstoQ

Esta configuração prepara um site completo separado da produção. Criar os
arquivos ou a branch não provisiona contas, serviços nem banco automaticamente.
Antes de considerar o ambiente disponível, registre as URLs reais e valide os
checks abaixo. A publicação exige acesso autorizado às contas dos provedores.

## Recursos previstos

| Recurso | Configuração |
| --- | --- |
| Banco | Projeto Supabase exclusivo `kklnvrihortzogvdpbfu`, região us-west-2 (Oregon). |
| Backend | Serviço Render `estoq-backend-homologacao`, região Oregon, branch homologacao. |
| Frontend | Novo projeto Vercel `estoq-homologacao`, raiz frontend, branch principal homologacao. |
| Dados | Restaurante e contas exclusivos de teste; nenhuma cópia de dados reais. |

Não reutilizar o projeto Supabase de produção nem copiar suas variáveis de
conexão para o novo backend. O schema pode continuar se chamando `estoq_v2`,
pois fica em outra instância de banco. Os recursos de produção seguem main.

## Publicação realizada em 21/10/2026

- Site: **https://estoq-homologacao.vercel.app**.
- Backend: https://estoq-backend-homologacao.onrender.com.
- Render: `srv-dbc8668n74is73fmt13g`, dentro do projeto `homologacao_estoQ`
  (`prj-dbc2egtg1s2s73bji4hg`), plano **free**, região **Oregon**.
- Vercel: `estoq-homologacao` (`prj_2e1RTTg0xblYT98RWH3o8UJItsks`),
  raiz `frontend`, branch principal **homologacao**, sem contratação de plano pago.
- Supabase exclusivo: `kklnvrihortzogvdpbfu`, região **us-west-2**;
  Session Pooler `aws-1-us-west-2.pooler.supabase.com:5432`.
- Versão inicial validada: `c7e03618e21d57d9e05ba7d36b9c32c3681704df`.
- Candidata atual: `414175d2557a9fbda1d6fb0cf43b1b7d2c65178f`; detalhes em
  [HOMOLOGACAO_DETALHADA_2026_10_21.md](HOMOLOGACAO_DETALHADA_2026_10_21.md).
- Produção e branch `main` não foram alteradas.

No projeto Vercel original, foi configurada somente a variável `ESTOQ_API_ORIGIN`
no escopo **Preview**, restrita à branch **homologacao**, apontando ao backend de
testes. Isso evita falhas dos previews dessa branch sem mudar as variáveis nem o
destino da produção.

O schema privado foi provisionado pelo script e as migrações foram aplicadas no
primeiro deploy. Os endpoints `/api/saude/vivo` e `/api/saude/pronto` retornaram
HTTP 200 tanto pelo backend quanto pelo domínio Vercel. O primeiro confirmou
`ambiente: homologacao` e a versão esperada.

Foi criada uma conta ADMIN exclusiva, com senha aleatória, restaurante fictício
e **100 produtos fictícios**. As credenciais ficam somente no arquivo local
protegido `/home/pedro/.estoq-secrets/hml-test.env`, nunca no repositório.
Também é possível cadastrar outra conta de teste pelo site; ela terá seu próprio
restaurante, separado do catálogo de carga.

Conferência em Chromium: banner de homologação, login e produtos no catálogo
confirmados; nenhum erro JavaScript e nenhuma requisição aos hosts de produção.
Cadastro e logout também foram validados pela API. Os cinco testes locais de
configuração Vercel passaram.

### Medições pelo frontend publicado

Dez consultas por rota, após aquecimento; três simultâneas usam a mesma sessão.
As 80 consultas medidas terminaram com HTTP 200 e JSON válido.

| Rota | p95 sequencial | p95 com 3 simultâneas |
| --- | --- | --- |
| Catálogo | 819,8 ms | 943,3 ms |
| Estoque | 921,7 ms | 1.531,7 ms |
| Dashboard | 1.332,8 ms | 1.739,6 ms |
| CMV | 921,8 ms | 920,5 ms |

Todos ficaram abaixo do limite inicial de 2.000 ms. Evidências sem dados de sessão:
[sequencial](evidencias/homologacao-2026-10-21-sequencial.json) e
[três simultâneas](evidencias/homologacao-2026-10-21-tres-requisicoes.json).
Os testes usaram catálogo mínimo, sem histórico representativo de movimentações,
e serviço já aquecido. Não validam cold start, capacidade máxima ou todos os
fluxos necessários para promover uma candidata à produção.

### Pendências e uso gratuito

- Confirmar/desativar a **Data API** nas configurações do Supabase. Sem acesso à
  API administrativa do Supabase, o desligamento não foi confirmado. As permissões
  de `anon`, `authenticated` e `service_role` sobre `estoq_v2` já foram revogadas
  pelo script de provisionamento.
- Conferir manualmente entrada, consumo, notas, balanço, faturamento, PDFs e
  sincronização entre abas, com movimentações fictícias representativas.
- Não há keepalive na homologação: o Render pode dormir quando ocioso e o
  Supabase Free pode pausar após inatividade. As horas gratuitas do Render são
  compartilhadas com a produção; acompanhar a cota antes de testes prolongados.
- Credenciais enviadas pelo chat devem ser trocadas; ao trocar a senha do banco,
  atualizar também `DATABASE_PASSWORD` no serviço de homologação do Render.

## Banco e backend

1. Use o projeto Supabase exclusivo em us-west-2 (Oregon). Guarde a senha no gerenciador de
   senhas e preencha-a somente nas variáveis privadas do novo serviço.
2. No projeto novo, execute `scripts/provision-supabase.sql`. Esse script prepara
   o schema privado; Flyway executará as migrações ao iniciar a aplicação.
3. Mantenha a Data API desativada para o projeto de testes; o EstoQ usa JDBC.
4. Importe `render.homologacao.yaml` pelo Render, selecionando homologacao.
   O Blueprint fixa Docker, região, plano free e perfil `prod,homologacao`.
5. Preencha DATABASE_URL, DATABASE_USER e DATABASE_PASSWORD do **banco novo**.
   Use o Session Pooler, SSL e `currentSchema=estoq_v2`, como descrito em
   [DEPLOY_SUPABASE.md](DEPLOY_SUPABASE.md). Não envie senhas ao GitHub.
6. Aguarde o deploy. `/api/saude/vivo` deve retornar `ambiente: homologacao`
   e o hash implantado. `/api/saude/pronto` deve retornar status ok.

O perfil prod mantém Flyway e validação do schema, configurações de pool e
segurança equivalentes às da produção. O perfil homologacao muda a identificação
do ambiente e o nome do cookie, sem trocar a implementação de negócio.

## Frontend

Crie o projeto Vercel com o mesmo repositório, raiz `frontend`, framework Vite e
branch principal `homologacao`. Configure estas variáveis nesse projeto:

```text
ESTOQ_AMBIENTE=homologacao
ESTOQ_API_ORIGIN=https://URL_REAL_DO_BACKEND_DE_HOMOLOGACAO
```

O `frontend/vercel.mjs` monta os rewrites usando essas variáveis e mantém os
cabeçalhos de segurança de `vercel.base.json`. O build mostra o aviso
**Homologação · use apenas dados de teste** em todas as telas.

O proxy mantém as chamadas em `/api` no domínio do frontend, incluindo cookies,
CSRF e SSE. Não é necessário colocar chaves Supabase no navegador. A variável
ESTOQ_API_ORIGIN não contém senha; é somente a origem HTTPS do backend.

Para Preview de outras branches, configure ESTOQ_API_ORIGIN no escopo Preview
do projeto correspondente. Um Preview sem backend de teste configurado falha
no build; não usa mais a API de produção por padrão. Na main em produção,
continua sendo usado o backend atual de produção.

## Validação funcional

Abra o endereço do novo frontend e confirme o aviso de homologação. Cadastre um
restaurante fictício e um e-mail de teste, com uma senha exclusiva deste ambiente.
Essa conta será ADMIN do restaurante; uma conta PLATAFORMA não é necessária para
testar os fluxos de estoque.

Confira login/logout, catálogo, entrada, consumo, nota fiscal, balanço, CMV,
faturamento, PDFs e sincronização entre duas abas. Confira saldo, custo e totais
antes e depois, e a ausência desses dados no site de produção.

## Como avaliar a lentidão

Use o mesmo tipo de instância da produção para comparar. Neste ambiente, banco e
backend ficam juntos em Oregon; a produção continua na Virgínia. Essa diferença
de região deve ser considerada ao comparar a latência percebida pelo usuário.
Carregue dados fictícios com volume parecido ao uso previsto: produtos, lotes,
movimentações e notas. Um banco vazio não representa o desempenho real.
O medidor exige pelo menos 100 produtos; esse é um mínimo inicial, não uma
garantia de que o volume já representa seu restaurante.

Preencha as credenciais no seu terminal sem gravar a senha no histórico:

```bash
read -r -p 'E-mail de teste: ' ESTOQ_TEST_EMAIL
read -r -s -p 'Senha de teste: ' ESTOQ_TEST_SENHA
export ESTOQ_TEST_EMAIL ESTOQ_TEST_SENHA
python3 scripts/medir_homologacao.py \
  --url https://URL_REAL_DO_FRONTEND_DE_HOMOLOGACAO \
  --versao HASH_COMPLETO_DO_BACKEND \
  --repeticoes 10 --simultaneos 1 \
  --saida /tmp/estoq-homologacao-sequencial.json
python3 scripts/medir_homologacao.py \
  --url https://URL_REAL_DO_FRONTEND_DE_HOMOLOGACAO \
  --versao HASH_COMPLETO_DO_BACKEND \
  --repeticoes 10 --simultaneos 3 \
  --saida /tmp/estoq-homologacao-tres-requisicoes.json
unset ESTOQ_TEST_EMAIL ESTOQ_TEST_SENHA
```

O teste faz login de verdade e consulta catálogo, estoque, dashboard e CMV,
passando pelo Vercel, Render e banco. Não modifica os produtos nem o estoque.
Relata a primeira resposta do processo e a prontidão do banco separadamente.
Depois aquece cada rota e mede mediana, p95, máximo e erros com concorrência
controlada. Três requisições simultâneas usam a mesma sessão; não simulam três
usuários independentes. O script limita concorrência a cinco e repetições a vinte.

O limite inicial é p95 de 2 segundos, com zero erros, sem contar aquecimento.
É ajustável por `--p95-maximo` e não é uma promessa de capacidade ou SLA.
A saída é JSON, sem senhas, cookies ou conteúdo dos relatórios. Guarde os dois
resultados com o hash aprovado. O teste recusa hosts de produção, ambiente sem
marcador de homologação, commit diferente, respostas HTML ou conta não ADMIN.

Para medir o despertar após inatividade, faça uma execução sem abrir o ambiente
antes. O primeiro acesso só mede esse despertar se o provedor realmente suspendeu
o serviço. Teste também o tempo percebido de navegação e de operações com escrita
no navegador; o medidor de consultas não substitui esses testes.

## Critério para promover a candidata

- CI de homologacao aprovada e hash do backend conferido.
- Frontend usando o backend e o banco exclusivos de homologação.
- Fluxos funcionais conferidos com dados fictícios em volume representativo.
- Medições anexadas à revisão, sem erros e dentro do limite combinado.
- Aprovação da candidata antes de promover seu hash para main.

Homologação reduz o risco; diferenças de dados, carga, recursos e serviços externos
ainda podem afetar a produção. Faça uma conferência breve após publicar.

## Planos e custos

O Blueprint propõe free e não contrata um plano pago. Confira a disponibilidade
na conta antes de criar recursos. O Render gratuito pode suspender por inatividade
e possui cotas compartilhadas; não configure keepalive para a homologação.
Se a produção usar plano pago, a comparação de capacidade precisa de recursos
equivalentes, com orçamento aprovado antes de contratar.

Referências: [Render gratuito](https://render.com/docs/free),
[Blueprints](https://render.com/docs/blueprint-spec),
[configuração Vercel](https://vercel.com/docs/project-configuration/vercel-ts),
[limites de projetos Supabase](https://supabase.com/docs/guides/platform/billing-faq).


## Atualização 24/10/2026 — Backend migrado para Virgínia

O backend de homologação foi migrado para a mesma região da produção (**Virgínia, us-east**).

- Novo serviço: `estoq-backend-homologacao-va` (`srv-dbe20d7avr4c739qfk4g`)
- URL: `https://estoq-backend-homologacao-va.onrender.com`
- Região: Virgínia (free)
- Branch: `homologacao`
- Perfil: `prod,homologacao`
- Health check: `/api/saude/vivo`
- Banco: continua no mesmo projeto Supabase (Oregon, us-west-2) — apenas o backend mudou de região

O `ESTOQ_API_ORIGIN` do projeto Vercel `estoq-homologacao` foi atualizado para apontar
ao novo backend. O serviço antigo `estoq-backend-homologacao` (Oregon) foi suspenso
como rede de segurança (pode ser reativado rapidamente se necessário).

Testado: login, `/api/saude/vivo` e `/api/saude/pronto`, exportação CSV funcionando.

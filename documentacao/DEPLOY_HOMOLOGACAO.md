# Homologação online do EstoQ

Esta configuração prepara um site completo separado da produção. Criar os
arquivos ou a branch não provisiona contas, serviços nem banco automaticamente.
Antes de considerar o ambiente disponível, registre as URLs reais e valide os
checks abaixo. A publicação exige acesso autorizado às contas dos provedores.

## Recursos previstos

| Recurso | Configuração |
| --- | --- |
| Banco | Novo projeto Supabase `estoq-homologacao`, região us-east-1. |
| Backend | Novo serviço Render `estoq-backend-homologacao`, região Virginia, branch homologacao. |
| Frontend | Novo projeto Vercel `estoq-homologacao`, raiz frontend, branch principal homologacao. |
| Dados | Restaurante e contas exclusivos de teste; nenhuma cópia de dados reais. |

Não reutilizar o projeto Supabase de produção nem copiar suas variáveis de
conexão para o novo backend. O schema pode continuar se chamando `estoq_v2`,
pois fica em outra instância de banco. Os recursos de produção seguem main.

## Banco e backend

1. Crie o novo projeto Supabase em us-east-1. Guarde a senha no gerenciador de
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

Use a mesma região e o mesmo tipo de instância da produção para comparar.
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

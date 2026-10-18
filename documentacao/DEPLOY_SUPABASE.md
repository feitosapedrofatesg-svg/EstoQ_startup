# Supabase: início com banco vazio

Revisão: 18/10/2026. Destino aprovado para recomeçar sem importar dados do Neon.
O projeto Neon fica intacto. Não reutilizar sua senha de banco nem a senha exposta
da PLATAFORMA. Nenhuma mudança no frontend ou no domínio é necessária.

## Preparar o projeto

1. Criar um projeto Free no Supabase e guardar a senha do banco fora do Git.
2. Desativar **Enable Data API**, pois o EstoQ usa JDBC pelo backend e seu próprio
   login. Não acrescentar `estoq_v2` aos schemas expostos por REST/GraphQL.
3. No SQL Editor, como `postgres`, executar
   [`provision-supabase.sql`](../scripts/provision-supabase.sql). Ele cria somente o
   schema privado `estoq_v2` e retira acesso das roles de API. Não executa migrações.
4. Em **Connect → Session pooler**, copiar host, porta, database e username.
   Não usar Transaction pooler/6543: o backend é persistente e utiliza estado de
   sessão e prepared statements. Não usar chave `anon` ou `service_role` como senha.

## Trocar a conexão no Render

Guardar privadamente os valores anteriores para identificar o ambiente antigo;
não restaurá-los como rollback depois de registrar dados reais no destino novo.
Configurar no serviço backend, não no frontend:

```dotenv
DATABASE_URL=jdbc:postgresql://HOST_DO_SESSION_POOLER:5432/postgres?sslmode=require&currentSchema=estoq_v2
DATABASE_USER=postgres.REFERENCIA_DO_PROJETO
DATABASE_PASSWORD=PREENCHER_SOMENTE_NO_RENDER
SPRING_PROFILES_ACTIVE=prod
ESTOQ_BACKUP_SCHEMA=estoq_v2
```

Usar os dados exatos do Connect; não construir o host a partir da região. Senha
fica separada da URL para não exigir escape de caracteres. `sslmode=require`
exige criptografia. Para verificar também o servidor, baixar o certificado no
Supabase, disponibilizar ao backend e usar `sslmode=verify-full&sslrootcert=CAMINHO`.

Remover overrides antigos `ESTOQ_BACKUP_HOST`, `ESTOQ_BACKUP_PORTA`,
`ESTOQ_BACKUP_DB`, `ESTOQ_BACKUP_USER` e `ESTOQ_BACKUP_PASS` que ainda apontem para
o Neon. O backup passa a herdar a conexão nova; manter apenas o schema explícito.
Conferir `SEED_ENABLED=false`, Flyway habilitado e Hibernate `validate`.

Salvar e publicar o backend da main. O Flyway usa B4 e as migrações posteriores
no schema vazio; não desligar validação nem usar `ddl-auto=update`. As tabelas
internas já existentes do Supabase ficam fora do schema da aplicação.
Conferir nos logs a versão 8, `GET /api/saude/pronto` retornando 200 e o healthcheck
automático configurado como `/api/saude/vivo`.

## Criar o acesso PLATAFORMA

Não existe senha padrão no banco novo. No seu terminal com Java 21 e Maven:

```bash
mvn -f backend/pom.xml dependency:build-classpath -Dmdep.outputFile=target/bootstrap-classpath.txt
java --class-path "$(cat backend/target/bootstrap-classpath.txt)" scripts/GerarHashSenha.java
```

O utilitário pede e confirma a senha sem mostrá-la, gera BCrypt com custo 12 e
imprime apenas o hash para uso privado. Não recebe senha por argumento nem gera
arquivo com senha. Não enviar o hash à conversa nem publicá-lo.

No SQL Editor, abrir [`bootstrap-plataforma-sql-editor.sql`](../scripts/bootstrap-plataforma-sql-editor.sql),
substituir o marcador pelo hash e executar. O template cria `admin@estoq.com` como
PLATAFORMA ativa sem restaurante. Recusa marcador inválido e conta já existente;
não redefine senha nem promove ADMIN. O SQL original com prompts `psql` continua
disponível para operadores que já utilizam esse cliente.

Entrar no domínio atual com o e-mail e a senha escolhida. O painel deve abrir sem
restaurantes. Criar o restaurante do piloto pelo cadastro público, com outro
e-mail, e conferir o perfil ADMIN e a loja correta. Supabase Auth não é utilizado.

## Conferência e limites

Verificar primeiro migrações, acesso PLATAFORMA e cadastro; depois registrar dados
do piloto. Gerar um backup pela conta PLATAFORMA e validar a restauração do schema
`estoq_v2` em banco separado. Não tentar restaurar os schemas gerenciados `auth`
e `storage`; o backup da aplicação filtra somente o schema de negócio.

No Free, manter cópia fora do servidor, acompanhar tamanho e transferência e
conferir as regras de pausa por inatividade. Não prometer disponibilidade de
produção com plano gratuito. O procedimento está preparado, mas só a conferência
do serviço e do login confirma a migração efetiva.

Referências: [conexões](https://supabase.com/docs/guides/database/connecting-to-postgres),
[desativar Data API](https://supabase.com/docs/guides/api/securing-your-api#disable-the-data-api)
e [limites](https://supabase.com/pricing).

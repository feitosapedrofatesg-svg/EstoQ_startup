# Backup e restauração

Revisão: **12/10/2026**. Fontes: `backup/BackupController`, `BackupService`,
`BackupConfig`, `SecurityConfig` e `frontend/src/pages/Backup.tsx`.

## Escopo e acesso

O recurso do EstoQ gera um backup PostgreSQL completo do banco ou do schema
configurado, incluindo dados de todos os restaurantes desse escopo. Por isso,
somente **PLATAFORMA** gera, lista e baixa os arquivos. Não há backup individual
por restaurante nesta etapa. ADMIN, COZINHA e NUTRICIONISTA recebem 403 nas rotas.
O recurso do EstoQ não é um sistema de snapshots do computador pessoal.

O backup é manual. `POST /api/backups` executa `pg_dump --format custom` e devolve
o arquivo para download imediato. A cópia do servidor é temporária e não
substitui o arquivo guardado pelo responsável fora dele.

## Geração e configuração

- Nome novo: `estoq-AAAAMMDD-HHMMSS-<uuid>.dump`, com horário UTC.
- Nomes legados sem UUID também são aceitos pela validação de download.
- Uma geração por instância; tentativa paralela recebe 409.
- Prazo de cinco minutos; em timeout, o processo é encerrado e o parcial removido.
- Escreve `.partial`, valida resultado e tamanho, depois publica o arquivo final.
- Retenção padrão: sete arquivos. Configuração: `ESTOQ_BACKUP_MANTER`.
- Senha é passada ao subprocesso por ambiente; não compõe a linha de comando.

A conexão é derivada de `spring.datasource.*` quando os overrides estão vazios.
Overrides disponíveis: `ESTOQ_BACKUP_HOST`, `ESTOQ_BACKUP_PORTA`,
`ESTOQ_BACKUP_DB`, `ESTOQ_BACKUP_SCHEMA`, `ESTOQ_BACKUP_USER`, `ESTOQ_BACKUP_PASS`.
Diretório: `ESTOQ_BACKUP_DIR` (no Render configurado como `/tmp/estoq-backups`).
`pg_dump` precisa ser compatível com a versão do servidor PostgreSQL.

Sem filtro de schema, o dump cobre o banco configurado. Com schema explícito,
abrange apenas esse schema. A configuração não aplica filtro `restaurante_id`.
O banco de produção conhecido usa `public`; dev pode usar `estoq_v2`.
Confira o escopo real antes de escolher como restaurar.

## Download

A interface inicia o download do retorno binário do POST. Guarde o arquivo em
armazenamento externo ao servidor e identifique a data/ambiente. GET `/api/backups`
lista as cópias ainda existentes; GET `/api/backups/{nome}` baixa novamente.
Nome/caminho são validados para impedir acesso a arquivos fora do diretório.

No ambiente atual, reinício ou deploy pode apagar arquivos do disco efêmero.
Não há agendamento automático, armazenamento externo ou restauração pela interface.

## Restauração em ambiente separado

Use um banco vazio de teste e as credenciais dele. O exemplo abaixo assume que o
dump foi filtrado para `estoq_v2`; substitua pelo escopo conferido do seu arquivo.
Nunca apontar estes comandos para a base em uso sem planejar a recuperação.

```bash
createdb --host localhost --port 5434 --username estoq estoq_restore_test
psql --host localhost --port 5434 --username estoq --dbname estoq_restore_test \
  --command 'CREATE SCHEMA IF NOT EXISTS estoq_v2 AUTHORIZATION estoq;'
pg_restore --host localhost --port 5434 --username estoq \
  --dbname estoq_restore_test --no-owner --no-privileges --exit-on-error \
  caminho/backup.dump
```

Dump com `--schema` pode exigir criar previamente o schema de destino. Para dump
completo, confira os schemas internos e não aplique um filtro que omita dados.
Use `pg_restore --list caminho/backup.dump` para inspecionar antes de restaurar.

Após restauração, confira versão do Flyway, restaurante_id, usuários, categorias,
produtos, lotes, movimentos, notas e balanços. Teste acesso separado de duas
lojas e a consistência de saldo/custo em ambiente de validação. Backup sem teste
de restauração não demonstra recuperação bem-sucedida.

## Evoluções ainda não implementadas

Exportação/restauração individual por restaurante, agendamento externo, cópia
persistente remota e recuperação por interface exigem projeto próprio. Um dump
completo nunca deve ser entregue como backup individual de uma loja.

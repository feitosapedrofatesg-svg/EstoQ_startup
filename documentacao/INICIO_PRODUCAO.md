# Início de produção: remoção dos restaurantes de teste

Revisão: 12/10/2026.

O responsável confirmou que todos os restaurantes atuais são testes e escolheu
exclusão definitiva. A limpeza não roda no deploy, não é uma migração Flyway e
não cria uma função de exclusão no aplicativo. O cadastro de restaurantes reais
continua pelo fluxo normal. Execute durante uma janela sem uso do sistema.

## Ferramenta e proteções

Use `scripts/limpar_restaurantes_teste.py`, com Python 3 e os clientes PostgreSQL
`psql` e `pg_restore` compatíveis com a versão do dump. Não há dependência Python
adicional. A conexão é configurada por `PGSERVICE`/`.pg_service.conf` e `.pgpass`,
com permissão 0600. Nunca coloque senha no comando, no Git ou na conversa.

Alternativamente, salve somente a URL PostgreSQL fornecida pelo Neon em
`dados/conexao-neon.txt`, com permissão 0600. Acrescente
`--conexao-arquivo dados/conexao-neon.txt` nos comandos do utilitário. O arquivo
fica ignorado pelo Git; o script transfere a senha ao ambiente do cliente, sem
exibi-la. A URL deve começar com `postgresql://`, sem o comando `psql` nem aspas.
A URL HTTPS da Data API (`/rest/v1`) não serve para este procedimento.

A inspeção grava um manifesto com banco, schema, nomes, datas de criação e IDs.
Somente esses IDs são excluídos: restaurantes cadastrados depois são preservados.
O manifesto não deve ser editado; repita a inspeção se os alvos mudarem.

A execução exige duas cópias idênticas do backup e o comprovante do ensaio. Este
comprovante vincula os hashes do dump e do manifesto. O ensaio restaura o dump em
um banco vazio com outro nome e testa a limpeza com ROLLBACK. A restauração de ensaio seleciona
o schema da aplicação (`public`), incluindo suas tabelas, dados, sequências e FKs.
Extensões gerenciadas do Neon, como `pg_session_jwt`, permanecem no dump completo,
mas precisam de um ambiente Neon compatível para uma restauração integral do
serviço de autenticação. O ensaio local não homologa essa extensão. Um arquivo válido
não substitui a necessidade de armazenar uma cópia em outro dispositivo/local.

A limpeza bloqueia escritas brevemente, suspende os alvos e remove as relações em
uma única transação. Uma FK inválida ou qualquer outro erro desfaz a operação.
Tabelas filhas de movimentação usam o ID da movimentação principal para incluir
registros legados sem tenant na filha; vínculos contraditórios interrompem a limpeza.
Contas PLATAFORMA vinculadas aos alvos, ausência de PLATAFORMA ativa e tabelas
novas com restaurante_id interrompem a limpeza. Referências cruzadas não são
corrigidas automaticamente. Schema, Flyway e sequências são preservados.

## Procedimento

1. Entre como PLATAFORMA e baixe um backup completo pela tela Backup. Armazene
   fora do Render e faça uma segunda cópia, preferencialmente em outro dispositivo.
   Confira que o dump contém o schema configurado no banco de produção.
2. Configure serviços PostgreSQL separados: `estoq_producao` (origem) e
   `estoq_ensaio` (banco vazio, com outro nome). Use o schema real; no deploy
   documentado é `public`. O nome do serviço não contém senha.
3. Na raiz do projeto, crie uma pasta ignorada pelo Git e inspecione:

```bash
mkdir -p dados/preparacao-producao
export PGSERVICE=estoq_producao
python3 scripts/limpar_restaurantes_teste.py inspecionar \
  --schema public --manifesto dados/preparacao-producao/manifesto.json
```

4. Confira os nomes e IDs no manifesto. Não execute se houver dados reais.
   Restaure e ensaie usando o dump baixado:

```bash
python3 scripts/limpar_restaurantes_teste.py ensaiar \
  --manifesto dados/preparacao-producao/manifesto.json \
  --backup dados/preparacao-producao/antes.dump \
  --servico-restauracao estoq_ensaio \
  --comprovante dados/preparacao-producao/ensaio.json
```

5. Execute na origem somente depois do ensaio bem-sucedido:

```bash
python3 scripts/limpar_restaurantes_teste.py executar \
  --manifesto dados/preparacao-producao/manifesto.json \
  --backup dados/preparacao-producao/antes.dump \
  --copia-backup /caminho/da/segunda-copia/antes.dump \
  --comprovante dados/preparacao-producao/ensaio.json
```

Não utilize TRUNCATE CASCADE, não desative constraints e não reinicie sequências.
Se o cliente perder conexão durante o COMMIT, confira os IDs na origem antes de
repetir: uma falha de rede pode acontecer depois de a transação ser confirmada.
Mantenha o manifesto, backup e comprovante como registro privado da operação.

## Depois da limpeza

- Confirme o login PLATAFORMA e que os IDs do manifesto desapareceram. Sessões
  operacionais de usuários removidos devem perder acesso. Atualize/reabra as abas.
- Troque senhas padrão ainda existentes por segredos pessoais, em canal seguro.
  A ferramenta preserva as contas e hashes PLATAFORMA; não troca suas senhas.
- Cadastre o restaurante real pelo formulário normal, com seus dados verdadeiros.
  O fluxo já cria categorias padrão e configurações, sem produtos ou histórico.
- Cadastre a equipe, configure a data do balanço e informe o estoque inicial com
  quantidades, custos e validades conferidos. Verifique o isolamento entre perfis.
- Confira consumo, balanço, relatórios/exportação e faça um novo backup após a
  configuração inicial. Guarde-o fora do Render.

O ensaio testa restauração e integridade SQL. A homologação do restaurante real
exige os dados e credenciais do responsável; não é criada uma conta fictícia para
substituir essa etapa. Uma conexão de produção indisponível não impede preparar e
testar o utilitário, mas impede afirmar que os dados remotos foram removidos.

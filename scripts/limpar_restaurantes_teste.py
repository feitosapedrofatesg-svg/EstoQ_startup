#!/usr/bin/env python3
"""Limpeza administrativa explícita. Conexão por PGSERVICE/PGHOST e .pgpass."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlsplit, parse_qs, unquote

SQL = Path(__file__).with_name('limpar-restaurantes-teste.sql')


def consultar(sql, ambiente=None):
    # -X ignora comandos locais; erros SQL encerram a sessão e revertem a transação.
    resultado = subprocess.run(['psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1'],
        input=sql, text=True, capture_output=True, env=ambiente, check=True)
    return resultado.stdout.strip()


def literal(valor):
    return "'" + str(valor).replace("'", "''") + "'"


def salvar(caminho, dados):
    # Manifestos e comprovantes são arquivos locais, nunca versionados.
    with open(caminho, 'x', encoding='utf-8') as arquivo:
        os.chmod(caminho, 0o600)
        json.dump(dados, arquivo, ensure_ascii=False, indent=2)


def hash_arquivo(caminho):
    resumo = hashlib.sha256()
    with open(caminho, 'rb') as arquivo:
        for bloco in iter(lambda: arquivo.read(1024 * 1024), b''):
            resumo.update(bloco)
    return resumo.hexdigest()


def identidade(ambiente=None):
    return json.loads(consultar("SELECT json_build_object('banco',current_database(),"
        "'usuario',current_user);", ambiente))


def limpeza(manifesto, confirmar, ambiente=None):
    if not manifesto['restaurantes']:
        raise ValueError('Manifesto sem restaurantes selecionados.')
    valores = []
    for restaurante in manifesto['restaurantes']:
        identificador = restaurante['id']
        if type(identificador) is not int or identificador <= 0:
            raise ValueError('ID inválido no manifesto.')
        valores.append(f"({identificador},{literal(restaurante['nome'])},"
            f"{literal(restaurante['criacao'])}::timestamp)")
    consulta = f'''BEGIN;
SET LOCAL lock_timeout='10s';
SET LOCAL statement_timeout='120s';
SET LOCAL search_path TO "{manifesto['schema']}";
CREATE TEMP TABLE alvos_limpeza(id bigint PRIMARY KEY,nome text,criacao timestamp) ON COMMIT DROP;
INSERT INTO alvos_limpeza VALUES {','.join(valores)};
'''+SQL.read_text(encoding='utf-8')+('\nCOMMIT;' if confirmar else '\nROLLBACK;')
    consultar(consulta, ambiente)


def carregar_conexao(caminho):
    # A URL privada fica em arquivo local; a senha não entra na linha de comando.
    url = urlsplit(Path(caminho).read_text(encoding='utf-8').strip())
    if url.scheme not in ('postgres', 'postgresql') or not url.hostname or not url.username or not url.path.strip('/'):
        raise ValueError('O arquivo precisa conter uma URL PostgreSQL válida, sem o comando psql.')
    opcoes = parse_qs(url.query)
    permitidas = {'sslmode': 'PGSSLMODE', 'channel_binding': 'PGCHANNELBINDING', 'connect_timeout': 'PGCONNECT_TIMEOUT'}
    if set(opcoes) - set(permitidas):
        raise ValueError('Parâmetros de conexão desconhecidos. Use a URL padrão do Neon.')
    os.environ.pop('PGSERVICE', None)
    os.environ.update(PGHOST=url.hostname, PGPORT=str(url.port or 5432),
        PGUSER=unquote(url.username), PGDATABASE=unquote(url.path.strip('/')),
        PGPASSWORD=unquote(url.password or ''), PGSSLMODE='require', PGCONNECT_TIMEOUT='20')
    for chave, valores in opcoes.items():
        os.environ[permitidas[chave]] = valores[-1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('modo', choices=['inspecionar', 'ensaiar', 'executar'])
    parser.add_argument('--manifesto', required=True)
    parser.add_argument('--schema', default='public')
    parser.add_argument('--conexao-arquivo', help='Arquivo privado com URL PostgreSQL, nunca uma URL da Data API.')
    parser.add_argument('--backup')
    parser.add_argument('--copia-backup')
    parser.add_argument('--comprovante')
    parser.add_argument('--servico-restauracao')
    args = parser.parse_args()
    if args.conexao_arquivo:
        carregar_conexao(args.conexao_arquivo)
    if not re.fullmatch(r'[a-z_][a-z0-9_]*', args.schema):
        raise ValueError('Schema inválido.')
    if args.modo == 'inspecionar':
        # A inspeção não altera o banco. Novos restaurantes não entram neste manifesto.
        dados = identidade()
        dados['schema'] = args.schema
        dados['servico'] = os.environ.get('PGSERVICE', '')
        dados['host'] = os.environ.get('PGHOST', '')
        dados['porta'] = os.environ.get('PGPORT', '')
        dados['restaurantes'] = json.loads(consultar(f'''SELECT coalesce(json_agg(r ORDER BY id),'[]'::json)
            FROM (SELECT id,nome,data_hora_criacao AS criacao,ativo,
                (SELECT count(*) FROM "{args.schema}".usuarios u WHERE u.restaurante_id=r.id) AS usuarios
                FROM "{args.schema}".restaurantes r) r;'''))
        tabelas = re.findall(r'DELETE FROM ([a-z_]+) WHERE restaurante_id', SQL.read_text(encoding='utf-8'))
        contagens = ' UNION ALL '.join(
            f"SELECT '{tabela}' AS tabela, count(*) AS registros FROM \"{args.schema}\".{tabela} "
            f"WHERE restaurante_id IN (SELECT id FROM \"{args.schema}\".restaurantes)"
            for tabela in tabelas)
        dados['contagens'] = json.loads(consultar(
            f"SELECT json_object_agg(tabela,registros) FROM ({contagens}) contagens;"))
        salvar(args.manifesto, dados)
        print(json.dumps(dados, ensure_ascii=False, indent=2))
        return
    manifesto = json.loads(Path(args.manifesto).read_text(encoding='utf-8'))
    if not re.fullmatch(r'[a-z_][a-z0-9_]*', manifesto['schema']):
        raise ValueError('Schema inválido no manifesto.')
    if not all([args.backup, args.comprovante]):
        raise ValueError('Informe --backup e --comprovante.')
    assinatura = {'backup': hash_arquivo(args.backup), 'manifesto': hash_arquivo(args.manifesto)}
    if args.modo == 'ensaiar':
        if not args.servico_restauracao or args.servico_restauracao == manifesto['servico']:
            raise ValueError('Informe um PGSERVICE separado para a restauração de ensaio.')
        ambiente = {k: v for k, v in os.environ.items() if k not in ('PGHOST','PGPORT','PGUSER','PGDATABASE','PGPASSWORD','PGSSLMODE','PGCHANNELBINDING')}
        ambiente['PGSERVICE'] = args.servico_restauracao
        destino = identidade(ambiente)
        if destino['banco'] == manifesto['banco']:
            raise ValueError('Use outro nome de banco para o ensaio; nunca restaure na origem.')
        tabelas = consultar("SELECT count(*) FROM information_schema.tables WHERE table_schema NOT IN "
            "('pg_catalog','information_schema') AND table_type='BASE TABLE';", ambiente)
        if tabelas != '0':
            raise ValueError('O banco de ensaio precisa estar vazio.')
        subprocess.run(['pg_restore', '--exit-on-error', '--no-owner', '--no-privileges',
            '--dbname', 'service='+args.servico_restauracao, args.backup], env=ambiente, check=True)
        limpeza(manifesto, False, ambiente)
        salvar(args.comprovante, assinatura)
        print('Backup restaurado e limpeza ensaiada com ROLLBACK. Origem preservada.')
        return
    if not args.copia_backup or Path(args.backup).resolve() == Path(args.copia_backup).resolve():
        raise ValueError('Informe uma segunda cópia do backup em outro caminho.')
    if hash_arquivo(args.copia_backup) != assinatura['backup']:
        raise ValueError('As cópias do backup são diferentes.')
    if json.loads(Path(args.comprovante).read_text(encoding='utf-8')) != assinatura:
        raise ValueError('Backup/manifesto divergem do ensaio validado.')
    atual = identidade()
    for chave in ('banco', 'usuario'):
        if atual[chave] != manifesto[chave]:
            raise ValueError('A conexão não corresponde ao manifesto.')
    for chave, variavel in [('servico','PGSERVICE'), ('host','PGHOST'), ('porta','PGPORT')]:
        if os.environ.get(variavel, '') != manifesto[chave]:
            raise ValueError('A configuração da conexão mudou desde a inspeção.')
    limpeza(manifesto, True)
    print('Limpeza concluída. Restaurantes posteriores ao manifesto e PLATAFORMA preservados.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as erro:
        # Não imprime comando, ambiente ou stderr do cliente: podem conter segredos.
        mensagem = 'Falha no cliente PostgreSQL. Confira o estado do banco antes de repetir a operação.' if isinstance(erro, subprocess.CalledProcessError) else str(erro)
        print(mensagem, file=sys.stderr)
        sys.exit(1)

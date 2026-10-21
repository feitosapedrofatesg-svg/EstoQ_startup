#!/usr/bin/env python3
"""Mede a API real da homologação, com sessão e sem alterar cadastros ou estoque."""
import argparse
import concurrent.futures
import http.cookiejar
import json
import math
import os
from pathlib import Path
import statistics
import time
import urllib.error
import urllib.parse
import urllib.request

PRODUCAO = {'estoq-oficial.vercel.app', 'estoq-backend-va.onrender.com'}


def validar_destino(url):
    destino = urllib.parse.urlsplit(url)
    if (destino.scheme != 'https' or not destino.hostname or destino.hostname in PRODUCAO
            or destino.username or destino.password or destino.path not in ('', '/')
            or destino.query or destino.fragment):
        raise ValueError('Use somente a origem HTTPS do site de homologação, sem caminhos ou credenciais.')
    return url.rstrip('/')


def validar_ambiente(dados, versao):
    if dados.get('ambiente') != 'homologacao' or dados.get('versao') != versao:
        raise ValueError('O destino não confirmou homologacao e o commit esperado. Teste cancelado.')


def resumir(amostras):
    tempos = sorted(a['ms'] for a in amostras)
    return {'requisicoes': len(amostras), 'erros': sum(a['status'] != 200 or not a.get('json_valido', True) for a in amostras),
            'mediana_ms': round(statistics.median(tempos), 1),
            'p95_ms': tempos[math.ceil(len(tempos) * .95) - 1], 'max_ms': tempos[-1]}


class SemRedirecionamento(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Evita seguir um login externo ou levar cabeçalhos da sessão para outro site.
        return None


class Cliente:
    def __init__(self, origem):
        self.origem = origem
        self.http = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), SemRedirecionamento())

    def chamar(self, caminho, corpo=None, cabecalhos=None):
        headers = {'Accept': 'application/json', 'Cache-Control': 'no-cache', **(cabecalhos or {})}
        dados = None
        if corpo is not None:
            dados = json.dumps(corpo).encode()
            headers['Content-Type'] = 'application/json'
        inicio = time.perf_counter()
        try:
            req = urllib.request.Request(self.origem + caminho, data=dados, headers=headers)
            with self.http.open(req, timeout=90) as resposta:
                conteudo = resposta.read()
                status = resposta.status
        except urllib.error.HTTPError as erro:
            conteudo, status = b'{}', erro.code
        except (urllib.error.URLError, TimeoutError):
            conteudo, status = b'{}', 0
        try:
            payload = json.loads(conteudo)
        except (ValueError, UnicodeDecodeError):
            payload = None
        amostra = {'status': status, 'json_valido': isinstance(payload, (dict, list)),
                   'ms': round((time.perf_counter() - inicio) * 1000, 1)}
        return payload, amostra

    def autenticar(self, email, senha):
        csrf, medida = self.chamar('/api/auth/csrf')
        if medida['status'] != 200 or not isinstance(csrf, dict) or not csrf.get('token'):
            raise ValueError('Não foi possível obter o CSRF da homologação.')
        _, medida = self.chamar('/api/auth/login', {'email': email, 'senha': senha},
                                {csrf['headerName']: csrf['token']})
        if medida['status'] != 200:
            raise ValueError('Login de teste recusado. Confira a conta exclusiva da homologação.')
        usuario, medida = self.chamar('/api/auth/me')
        if medida['status'] != 200 or not isinstance(usuario, dict) or usuario.get('perfil') != 'ADMIN':
            raise ValueError('Use uma conta ADMIN de restaurante na homologação.')


def executar(args):
    origem = validar_destino(args.url)
    email, senha = os.environ.get('ESTOQ_TEST_EMAIL'), os.environ.get('ESTOQ_TEST_SENHA')
    if not email or not senha:
        raise ValueError('Defina ESTOQ_TEST_EMAIL e ESTOQ_TEST_SENHA localmente, sem colocar a senha no comando.')
    cliente = Cliente(origem)
    vivo, primeiro = cliente.chamar('/api/saude/vivo')
    if primeiro['status'] != 200 or not isinstance(vivo, dict):
        raise ValueError('O processo não respondeu. Confira o deploy; nenhuma sessão de teste foi enviada.')
    validar_ambiente(vivo, args.versao)
    _, pronto = cliente.chamar('/api/saude/pronto')
    if pronto['status'] != 200:
        raise ValueError('A aplicação não está pronta para acessar o banco.')
    cliente.autenticar(email, senha)
    produtos, medida = cliente.chamar('/api/produtos?size=1')
    if (medida['status'] != 200 or not isinstance(produtos, dict)
            or produtos.get('totalElements', 0) < args.minimo_produtos):
        raise ValueError(f'Cadastre pelo menos {args.minimo_produtos} produtos fictícios antes de avaliar desempenho.')
    rotas = ['/api/produtos?size=100', '/api/estoque', '/api/dashboard/resumo', '/api/relatorios/cmv']
    relatorio = {'origem': origem, 'versao': args.versao, 'simultaneos': args.simultaneos,
                 'produtos_cadastrados': produtos['totalElements'],
                 'primeiro_acesso': primeiro, 'banco_pronto': pronto, 'rotas': {}}
    for rota in rotas:
        _, aquecimento = cliente.chamar(rota)
        with concurrent.futures.ThreadPoolExecutor(max_workers=args.simultaneos) as executor:
            resultados = list(executor.map(lambda _: cliente.chamar(rota), range(args.repeticoes)))
        amostras = [medida for _, medida in resultados]
        resumo = resumir(amostras)
        resumo['aquecimento'] = aquecimento
        resumo['aprovado'] = (resumo['erros'] == 0 and aquecimento['status'] == 200
                              and aquecimento['json_valido'] and resumo['p95_ms'] <= args.p95_maximo)
        relatorio['rotas'][rota] = resumo
    relatorio['aprovado'] = all(r['aprovado'] for r in relatorio['rotas'].values())
    Path(args.saida).write_text(json.dumps(relatorio, indent=2, ensure_ascii=False) + '\n')
    print(json.dumps(relatorio, indent=2, ensure_ascii=False))
    return 0 if relatorio['aprovado'] else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', required=True, help='URL do frontend de homologação, para incluir o proxy Vercel.')
    parser.add_argument('--versao', required=True, help='Hash completo do commit implantado no backend.')
    parser.add_argument('--repeticoes', type=int, choices=range(1, 21), default=10)
    parser.add_argument('--simultaneos', type=int, choices=range(1, 6), default=1)
    parser.add_argument('--p95-maximo', type=float, default=2000, help='Limite em ms, sem o aquecimento.')
    parser.add_argument('--minimo-produtos', type=int, choices=(100, 250, 500, 1000, 5000, 10000), default=100)
    parser.add_argument('--saida', default='/tmp/estoq-desempenho-homologacao.json')
    argumentos = parser.parse_args()
    try:
        raise SystemExit(executar(argumentos))
    except ValueError as erro:
        parser.exit(2, str(erro) + '\n')

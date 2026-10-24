#!/usr/bin/env python3
"""Autorização local do remetente. Só usa a biblioteca padrão do Python."""
import argparse
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import shlex
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import webbrowser

SCOPE = "https://www.googleapis.com/auth/gmail.send"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--credenciais", required=True, type=Path, help="JSON do cliente OAuth do tipo aplicativo de computador")
    parser.add_argument("--saida", type=Path, default=Path.home() / ".config/estoq/gmail.env")
    parser.add_argument("--nao-abrir", action="store_true")
    args = parser.parse_args()
    if args.saida.exists():
        raise ValueError("O arquivo de saída já existe. Escolha outro caminho para preservar a autorização anterior.")
    cliente = json.loads(args.credenciais.read_text()).get("installed")
    if not cliente:
        raise ValueError("Use um cliente OAuth do tipo aplicativo de computador (campo installed no JSON).")
    estado = secrets.token_urlsafe(32)
    verifier = secrets.token_urlsafe(64)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    resposta = {}

    class Callback(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # A URL de callback contém o código OAuth e não deve aparecer em logs.

        def do_GET(self):
            url = urlsplit(self.path)
            if url.path != "/":
                self.send_error(404)
                return
            campos = parse_qs(url.query)
            valido = hmac.compare_digest(campos.get("state", [""])[0], estado)
            if valido and "code" in campos:
                resposta["code"] = campos["code"][0]
            else:
                resposta["erro"] = True
            self.send_response(200 if "code" in resposta else 400)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write("Autorização recebida. Volte ao terminal.".encode())

    with HTTPServer(("127.0.0.1", 0), Callback) as servidor:
        servidor.timeout = 180
        redirect = f"http://127.0.0.1:{servidor.server_port}/"
        url = "https://accounts.google.com/o/oauth2/v2/auth?" + urlencode({
            "client_id": cliente["client_id"], "redirect_uri": redirect, "response_type": "code",
            "scope": SCOPE, "access_type": "offline", "prompt": "consent", "state": estado,
            "code_challenge": challenge, "code_challenge_method": "S256",
            "login_hint": "estoq.sistema@gmail.com",
        })
        print("Autorize somente a conta estoQ remetente; não envie tokens na conversa.")
        print(url)
        if not args.nao_abrir:
            webbrowser.open(url)
        while not resposta:
            servidor.handle_request()
            if not resposta:  # Timeout ou caminho que não é callback.
                raise ValueError("Callback não recebido. Rode novamente e conclua a autorização em até 3 minutos.")
    if "code" not in resposta:
        raise ValueError("Autorização recusada ou state inválido. Rode novamente.")
    corpo = urlencode({
        "client_id": cliente["client_id"], "client_secret": cliente["client_secret"],
        "code": resposta["code"], "redirect_uri": redirect, "grant_type": "authorization_code",
        "code_verifier": verifier,
    }).encode()
    pedido = Request("https://oauth2.googleapis.com/token", data=corpo,
                     headers={"Content-Type": "application/x-www-form-urlencoded"})
    with urlopen(pedido, timeout=20) as result:
        tokens = json.load(result)
    if not tokens.get("refresh_token"):
        raise ValueError("Google não retornou refresh token. Revise o consentimento e autorize novamente.")
    valores = {"EMAIL_CONFIRMACAO_ENABLED": "false", "EMAIL_FROM": "estoq.sistema@gmail.com",
               "APP_PUBLIC_URL": "https://estoq-oficial.vercel.app", "GOOGLE_CLIENT_ID": cliente["client_id"],
               "GOOGLE_CLIENT_SECRET": cliente["client_secret"], "GOOGLE_REFRESH_TOKEN": tokens["refresh_token"]}
    if any("\n" in v or "\r" in v for v in valores.values()):
        raise ValueError("Valor inesperado na autorização; nenhum arquivo foi gravado.")
    args.saida.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(args.saida, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as arquivo:
        arquivo.write("\n".join(f"{k}={shlex.quote(v)}" for k, v in valores.items()) + "\n")
    print(f"Autorização salva com permissão 600 em {args.saida}. Não publique esse arquivo.")
    print("Mantenha a confirmação desligada até testar um envio real em homologação.")


if __name__ == "__main__":
    try:
        main()
    except HTTPError as erro:
        raise SystemExit(f"Google recusou a autorização (HTTP {erro.code}). Revise o cliente OAuth e tente novamente.")
    except (ValueError, KeyError, OSError) as erro:
        # Não imprime respostas, códigos ou conteúdo do JSON de credenciais.
        raise SystemExit("Não foi possível concluir. Confira o tipo do cliente, os caminhos e a conexão. Nenhum segredo é exibido.") from None

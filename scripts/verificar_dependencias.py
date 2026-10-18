#!/usr/bin/env python3
"""Consulta o OSV usando somente nomes e versões das dependências Maven resolvidas."""
import json
import re
import sys
import urllib.request
from pathlib import Path


def ler_dependencias(texto):
    pacotes = set()
    for linha in texto.splitlines():
        item = linha.strip().split(" ")[0]
        partes = item.split(":")
        if len(partes) >= 5 and partes[-1] in {"compile", "runtime", "provided"}:
            if re.fullmatch(r"[\w.-]+", partes[0]) and re.fullmatch(r"[\w.-]+", partes[1]):
                pacotes.add((partes[0] + ":" + partes[1], partes[-2]))
    if not pacotes:
        raise ValueError("Lista vazia. Gere o arquivo com mvn dependency:list antes de verificar.")
    return sorted(pacotes)


def consultar(consultas):
    pedido = urllib.request.Request("https://api.osv.dev/v1/querybatch",
        data=json.dumps({"queries": consultas}).encode(), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(pedido, timeout=30) as resposta:
        resultados = json.load(resposta)["results"]
    if len(resultados) != len(consultas):
        raise ValueError("O OSV retornou uma resposta incompleta.")
    return resultados


def verificar(pacotes):
    encontrados = set()
    for inicio in range(0, len(pacotes), 100):
        consultas = [{"package": {"ecosystem": "Maven", "name": nome}, "version": versao}
                     for nome, versao in pacotes[inicio:inicio + 100]]
        while consultas:
            proximas = []
            for consulta, resultado in zip(consultas, consultar(consultas)):
                if "error" in resultado:
                    raise ValueError("Falha na consulta de uma dependência no OSV.")
                for aviso in resultado.get("vulns", []):
                    encontrados.add((consulta["package"]["name"], consulta["version"], aviso["id"]))
                if resultado.get("next_page_token"):
                    proximas.append({**consulta, "page_token": resultado["next_page_token"]})
            consultas = proximas
    return sorted(encontrados)


def main():
    try:
        pacotes = ler_dependencias(Path(sys.argv[1]).read_text())
        avisos = verificar(pacotes)
        print(f"OSV: {len(pacotes)} dependências verificadas; {len(avisos)} avisos.")
        for nome, versao, aviso in avisos:
            print(f"{nome}:{versao} — https://osv.dev/vulnerability/{aviso}")
        return 1 if avisos else 0
    except Exception as erro:
        print(f"Verificação de dependências não concluída: {erro}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env bash
# Roda a carga de estoque no Neon, na ordem, num processo so.
#
# A URL de conexao vem de fora (console do Neon ou painel do Render) e nunca
# fica no repo. Uso:
#
#   export NEON_URL='postgresql://neondb_owner:SENHA@HOST/neondb?sslmode=require'
#   ./scripts/seed/rodar.sh            # so o 00, nao grava nada
#   ./scripts/seed/rodar.sh --gravar   # os 4
#
# Antes de gravar, ele imprime o host para voce conferir se e a branch certa.

set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"

if [ -z "${NEON_URL:-}" ]; then
    echo "ERRO: NEON_URL vazia." >&2
    echo "Pegue a connection string no console do Neon (Connection Details) e" >&2
    echo "exporte aqui. Nao coloque a senha em arquivo do repo." >&2
    exit 1
fi

GRAVAR=0
[ "${1:-}" = "--gravar" ] && GRAVAR=1

# Preferimos psql nativo; se nao houver, docker resolve sem instalar nada.
if command -v psql >/dev/null 2>&1; then
    rodar() { psql "$NEON_URL" -v ON_ERROR_STOP=1 -q -f "$1"; }
    echo "usando: psql nativo ($(psql --version))"
else
    if ! docker info >/dev/null 2>&1; then
        echo "ERRO: nem psql nem docker disponiveis." >&2
        exit 1
    fi
    rodar() { docker run --rm -v "$AQUI:/sql:ro" postgres:16-alpine \
                    psql "$NEON_URL" -v ON_ERROR_STOP=1 -q -f "/sql/$(basename "$1")"; }
    echo "usando: docker + postgres:16-alpine"
fi

# Mostra o host sem a senha, so para voce conferir a branch antes de gravar.
host_echo="$(printf '%s' "$NEON_URL" | sed -E 's#^[^@]*@([^/]*)/.*#\1#')"
echo "alvo: $host_echo"
echo

rodar "$AQUI/00_verificar.sql"
echo
echo "=== 00 ok. Nenhum dado gravado ainda. ==="
echo

if [ "$GRAVAR" = "0" ]; then
    echo "Rode de novo com --gravar para escrever."
    exit 0
fi

for etapa in 01_categorias 02_produtos 03_estoque; do
    echo "########## $etapa"
    rodar "$AQUI/$etapa.sql"
    echo
done

echo "=== carga completa. Confira os numeros acima: ==="
echo "  15 categorias | 182 produtos | 130 com saldo | 0 orfaos"

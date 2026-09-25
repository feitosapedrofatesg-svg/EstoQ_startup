#!/usr/bin/env bash
# Validação E2E do multi-tenant em produção (Fase 4).
# Uso: SENHA_ADMIN='...' bash scripts/e2e_validacao.sh
# A senha do admin PLATAFORMA vem do ambiente (não versionada no repo).
set -u
BASE="https://estoq-backend-jv04.onrender.com"
[ -n "${SENHA_ADMIN:-}" ] || { echo "Defina SENHA_ADMIN (senha do admin@estoq.com, perfil PLATAFORMA)."; exit 2; }
IP=$(dig +short @1.1.1.1 estoq-backend-jv04.onrender.com 2>/dev/null | tail -1 | grep -oE '^[0-9.]+$')
RES="--resolve estoq-backend-jv04.onrender.com:443:$IP"
SELO=$(date +%s | tail -c 6)
PASSO=0
ok() { PASSO=$((PASSO+1)); echo "✔ $1"; }
falha() { echo "✘ FALHA no passo $PASSO: $1"; exit 1; }

# IMPORTANTE: o GET /api/auth/csrf precisa ENVIAR (-b) e salvar (-c) o cookie de
# sessão. Sem -b, o servidor cria uma sessão anônima nova e o jar perde a sessão
# autenticada -> todo POST seguinte responde 401 (incidente na 1ª versão do script).
csrf() {
  curl -skS $RES --max-time 30 -b "$1" -c "$1" "$BASE/api/auth/csrf" 2>/dev/null | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])"
}
req() { # $1=jar $2=metodo $3=caminho $4=[dados] -> grava em /tmp/opencode/last.json e imprime http_code
  local jar="$1" m="$2" p="$3" d="${4:-}"
  local args=(-skS $RES --max-time 40 -X "$m" -b "$jar" -c "$jar")
  [ -n "$d" ] && args+=(-H "Content-Type: application/json" -H "X-CSRF-TOKEN: $(csrf "$jar")" -d "$d")
  curl "${args[@]}" -o /tmp/opencode/last.json -w "%{http_code}" "$BASE$p"
}

# ---------- A. Plataforma (admin promovido por V3) ----------
JAR=/tmp/opencode/jarplat.txt; rm -f "$JAR"
CODE=$(req "$JAR" POST /api/auth/login "{\"email\":\"admin@estoq.com\",\"senha\":\"$SENHA_ADMIN\"}")
[ "$CODE" = "200" ] || falha "login plataforma admin@estoq.com ($CODE)"
ME=$(curl -skS $RES --max-time 30 -b "$JAR" "$BASE/api/auth/me")
echo "$ME" | grep -q '"perfil":"PLATAFORMA"' || falha "admin não virou PLATAFORMA: $ME"
ok "admin@estoq.com logado como PLATAFORMA (promoção V3)"
CODE=$(req "$JAR" GET /api/plataforma/restaurantes)
[ "$CODE" = "200" ] || falha "GET /api/plataforma/restaurantes ($CODE)"
grep -q '"nome":"EstoQ Padrão"' /tmp/opencode/last.json || falha "painel sem 'EstoQ Padrão'"
ok "painel listou restaurantes (EstoQ Padrão presente)"

# ---------- B. Auto-cadastro de 2 cozinhas ----------
EMA=adm-a-$SELO@teste.com; EMB=adm-b-$SELO@teste.com; SEN=$RANDOM$RANDOM'Aa1!'
JARA=/tmp/opencode/jara.txt; JARB=/tmp/opencode/jarb.txt; rm -f "$JARA" "$JARB"
CODE=$(req "$JARA" POST /api/registro "{\"nomeLoja\":\"Cantina Validação A $SELO\",\"nomeResponsavel\":\"Dona A\",\"email\":\"$EMA\",\"senha\":\"$SEN\"}")
[ "$CODE" = "201" ] || falha "registro loja A ($CODE)"
CODE=$(req "$JARB" POST /api/registro "{\"nomeLoja\":\"Cantina Validação B $SELO\",\"nomeResponsavel\":\"Dono B\",\"email\":\"$EMB\",\"senha\":\"$SEN\"}")
[ "$CODE" = "201" ] || falha "registro loja B ($CODE)"
ok "duas cozinhas auto-cadastradas (201)"

# ---------- C. Isolamento ----------
CODE=$(req "$JARA" POST /api/auth/login "{\"email\":\"$EMA\",\"senha\":\"$SEN\"}")
[ "$CODE" = "200" ] || falha "login A ($CODE)"
CODE=$(req "$JARB" POST /api/auth/login "{\"email\":\"$EMB\",\"senha\":\"$SEN\"}")
[ "$CODE" = "200" ] || falha "login B ($CODE)"
CODE=$(req "$JARA" POST /api/categorias "{\"nome\":\"Bebidas Validação\"}")
[ "$CODE" = "201" ] || falha "categoria em A ($CODE)"
CODE=$(req "$JARA" GET "/api/categorias?size=100")
[ "$CODE" = "200" ] || falha "listar categorias A ($CODE)"
CODE=$(req "$JARB" GET "/api/categorias?size=100")
[ "$CODE" = "200" ] || falha "listar categorias B ($CODE)"
echo "$(cat /tmp/opencode/last.json)" | grep -q '"totalElements":0' || falha "B enxergou categoria de A"
ok "isolamento: B não enxerga categoria de A"

# ---------- D. Suspensão + redefinição de admin ----------
# Casamento pelo nome COM o $SELO do run (runs anteriores deixam lojas no painel).
PIDS=$(curl -skS $RES --max-time 30 -b "$JAR" "$BASE/api/plataforma/restaurantes")
IDB=$(echo "$PIDS" | python3 -c "
import sys,json
for r in json.load(sys.stdin):
    if r['nome'].startswith('Cantina Validação B $SELO'): print(r['id'])
" )
[ -n "$IDB" ] || falha "id da loja B não encontrado no painel"
CODE=$(req "$JAR" PUT "/api/plataforma/restaurantes/$IDB" '{"ativo":false}')
[ "$CODE" = "200" ] || falha "suspender B ($CODE)"
CODE=$(req "$JARB" POST /api/auth/login "{\"email\":\"$EMB\",\"senha\":\"$SEN\"}")
[ "$CODE" = "401" ] || falha "login B deveria dar 401 após suspensão (foi $CODE)"
ok "suspensão: login do admin de B bloqueado (401)"
NOVASEN="NovaSenha$RANDOM$RANDOM!"
CODE=$(req "$JAR" POST "/api/plataforma/restaurantes/$IDB/redefinir-admin" "{\"senha\":\"$NOVASEN\"}")
[ "$CODE" = "200" ] || falha "redefinir admin B ($CODE)"
req "$JAR" PUT "/api/plataforma/restaurantes/$IDB" '{"ativo":true}' >/dev/null
CODE=$(req "$JARB" POST /api/auth/login "{\"email\":\"$EMB\",\"senha\":\"$NOVASEN\"}")
[ "$CODE" = "200" ] || falha "login B com nova senha ($CODE)"
CODE=$(req "$JARB" POST /api/auth/login "{\"email\":\"$EMB\",\"senha\":\"$SEN\"}")
[ "$CODE" = "401" ] || falha "senha antiga de B ainda aceita ($CODE)"
ok "redefinição de admin: nova senha entra, antiga não"

# ---------- E. Backup segue funcionando ----------
mkdir -p /tmp/opencode/backuptest && curl -skS $RES --max-time 120 -b "$JARA" \
  -H "X-CSRF-TOKEN: $(csrf "$JARA")" -o /tmp/opencode/backuptest/backup.dump \
  -w "backup HTTP %{http_code}\n" -X POST "$BASE/api/backups"
file /tmp/opencode/backuptest/backup.dump
ok "backup gerado (módulo intacto)"

# ---------- E2. Loja comum não acessa painel plataforma ----------
CODE=$(req "$JARA" GET /api/plataforma/restaurantes)
[ "$CODE" = "403" ] || { [ "$CODE" = "401" ] || falha "loja comum deveria levar 403/401 no painel (foi $CODE)"; }
ok "loja comum bloqueada no painel plataforma ($CODE)"

# ---------- F. Deixa as lojas de teste suspensas (painel limpo) ----------
CODE=$(req "$JAR" PUT "/api/plataforma/restaurantes/$IDB" '{"ativo":false}')
IDAS=$(echo "$PIDS" | python3 -c "
import sys,json
for r in json.load(sys.stdin):
    if r['nome'].startswith('Cantina Validação A $SELO'): print(r['id'])
" )
[ -n "$IDAS" ] || falha "id da loja A não encontrado"
CODE=$(req "$JAR" PUT "/api/plataforma/restaurantes/$IDAS" '{"ativo":false}')
ok "lojas de teste suspensas no painel (A=$IDAS, B=$IDB)"

echo ""
echo "=== VALIDAÇÃO E2E CONCLUÍDA ==="
echo "Plataforma: admin@estoq.com (PLATAFORMA)"
echo "Lojas de teste: $EMA / $EMB (senhas geradas, agora suspensas)"
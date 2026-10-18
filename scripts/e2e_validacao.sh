#!/usr/bin/env bash
# Integração local: sessões HTTP, isolamento e concorrência com dados fictícios.
# A suíte inicia seu próprio backend e PostgreSQL descartável. Não acessa produção.
set -euo pipefail
PASTA_PROJETO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
mvn -B -f "$PASTA_PROJETO/backend/pom.xml" \
  -Dtest=TenantIsolamentoIntegrationTest,SincronizacaoBalancoIntegrationTest test

#!/usr/bin/env bash
# Restaura um dump gerado por backup.sh. ATENÇÃO: sobrescreve os dados atuais.
# Uso: bash scripts/restore.sh backups/familianca-AAAAMMDD-HHMMSS.dump
set -euo pipefail
if [ $# -ne 1 ]; then
  echo "Uso: $0 backups/arquivo.dump"
  exit 1
fi
PGPASSWORD="${DB_PASSWORD:-familianca}" pg_restore \
  -h "${DB_HOST:-postgres}" -U "${DB_USER:-familianca}" -d "${DB_NAME:-familianca}" \
  --clean --if-exists --no-owner "$1"
echo "Restaurado: $1"

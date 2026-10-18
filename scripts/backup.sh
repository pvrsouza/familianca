#!/usr/bin/env bash
# Gera dump do banco em ./backups.
# Uso dentro do devcontainer: bash scripts/backup.sh
# Uso pelo host:             docker compose run --rm dev bash scripts/backup.sh
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p backups
arquivo="backups/familianca-$(date +%Y%m%d-%H%M%S).dump"
PGPASSWORD="${DB_PASSWORD:-familianca}" pg_dump \
  -h "${DB_HOST:-postgres}" -U "${DB_USER:-familianca}" -d "${DB_NAME:-familianca}" \
  -Fc -f "$arquivo"
echo "Backup gerado: $arquivo"

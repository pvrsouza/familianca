#!/usr/bin/env bash
# Backup do banco de produção, pelo cron do VPS (deploy_vps.md, passo 7):
#   1. pg_dump (formato custom, comprimido) para BACKUP_DIR;
#   2. confere que o dump é legível antes de aceitá-lo;
#   3. envia a cópia para fora do VPS (remoto do rclone, criptografado);
#   4. mantém só os BACKUP_MANTER mais recentes no VPS.
# Falha em qualquer etapa = sai com erro e não apaga backups antigos.
set -euo pipefail

DIR_DEPLOY="$(cd "$(dirname "$0")" && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
BACKUP_MANTER="${BACKUP_MANTER:-3}"
BACKUP_REMOTO="${BACKUP_REMOTO:-backup-cripto:familianca}"
# Opcional: URL de um monitor (ex.: healthchecks.io) avisado só quando o backup termina bem.
BACKUP_PING_URL="${BACKUP_PING_URL:-}"

compose() { docker compose -f "$DIR_DEPLOY/compose.prod.yaml" "$@"; }

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
arquivo="$BACKUP_DIR/familianca-$(date +%Y%m%d-%H%M%S).dump"
trap 'rm -f "$arquivo.parcial"' EXIT

compose exec -T postgres pg_dump -U postgres -d familianca -Fc > "$arquivo.parcial"
compose exec -T postgres pg_restore --list < "$arquivo.parcial" > /dev/null
mv "$arquivo.parcial" "$arquivo"
echo "dump ok: $arquivo ($(du -h "$arquivo" | cut -f1))"

rclone copy "$arquivo" "$BACKUP_REMOTO"
echo "cópia externa ok: $BACKUP_REMOTO"

ls -1t "$BACKUP_DIR"/familianca-*.dump | tail -n +"$((BACKUP_MANTER + 1))" | xargs -r rm -f --
echo "no VPS: $(ls -1 "$BACKUP_DIR"/familianca-*.dump | wc -l) backup(s)"

if [ -n "$BACKUP_PING_URL" ]; then curl -fsS -m 10 --retry 3 "$BACKUP_PING_URL" > /dev/null; fi

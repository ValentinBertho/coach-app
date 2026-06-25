#!/usr/bin/env bash
# ============================================================================
# Darilab — sauvegarde PostgreSQL (pg_dump compressé + rotation).
#
# Usage :
#   DATABASE_URL="postgresql://user:pass@host:5432/darilab" \
#   BACKUP_DIR=/var/backups/darilab RETENTION_DAYS=14 ./ops/backup-db.sh
#
# Variables (toutes optionnelles sauf la connexion) :
#   DATABASE_URL        URL de connexion (ou PGHOST/PGUSER/PGPASSWORD/PGDATABASE)
#   BACKUP_DIR          dossier cible (def: ./backups)
#   RETENTION_DAYS      jours de rétention (def: 14)
#   PG_DUMP             binaire pg_dump (def: pg_dump)
#
# Format : dump "custom" (-Fc) → restauration sélective via pg_restore.
# À planifier en cron (voir docs/OPERATIONS.md).
# ============================================================================
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
PG_DUMP="${PG_DUMP:-pg_dump}"
DB="${PGDATABASE:-darilab}"
TS="$(date -u +%Y%m%d-%H%M%S)"
OUT="${BACKUP_DIR}/darilab-${DB}-${TS}.dump"

mkdir -p "${BACKUP_DIR}"

echo "[backup] $(date -u +%FT%TZ) → ${OUT}"

if [[ -n "${DATABASE_URL:-}" ]]; then
  "${PG_DUMP}" --format=custom --no-owner --no-privileges --file="${OUT}" "${DATABASE_URL}"
else
  # Utilise PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE de l'environnement.
  "${PG_DUMP}" --format=custom --no-owner --no-privileges --file="${OUT}" "${DB}"
fi

# Intégrité : pg_restore --list doit pouvoir lire l'archive.
pg_restore --list "${OUT}" >/dev/null
SIZE="$(du -h "${OUT}" | cut -f1)"
echo "[backup] OK (${SIZE})"

# Rotation : purge des dumps plus vieux que RETENTION_DAYS.
find "${BACKUP_DIR}" -name 'darilab-*.dump' -type f -mtime "+${RETENTION_DAYS}" -print -delete \
  | sed 's/^/[backup] purge /' || true

echo "[backup] terminé."

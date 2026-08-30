#!/usr/bin/env sh
set -eu

ENV_FILE="${ENV_FILE:-.env}"
DB_BACKUP_PATH="${DB_BACKUP_PATH:-}"
UPLOAD_BACKUP_PATH="${UPLOAD_BACKUP_PATH:-}"
CONFIRM_DATABASE="${CONFIRM_DATABASE:-}"
SKIP_SAFETY_BACKUP="${SKIP_SAFETY_BACKUP:-no}"
SKIP_UPLOAD_RESTORE="${SKIP_UPLOAD_RESTORE:-no}"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck source=compose-common.sh
. "$SCRIPT_DIR/compose-common.sh"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

DATABASE="$(config_value MYSQL_DATABASE heihu_workorder)"
BACKUP_DIR="$(config_value BACKUP_DIR backups)"
UPLOAD_STORAGE_TYPE="$(config_value APP_UPLOAD_STORAGE_TYPE local)"

if [ "$CONFIRM_DATABASE" != "$DATABASE" ]; then
  echo "Destructive restore requires CONFIRM_DATABASE=$DATABASE" >&2
  echo "The confirmation must exactly match the configured database name." >&2
  exit 1
fi
if [ -z "$DB_BACKUP_PATH" ] || [ ! -f "$DB_BACKUP_PATH" ]; then
  echo "Missing DB_BACKUP_PATH or file not found: $DB_BACKUP_PATH" >&2
  exit 1
fi
verify_checksum "$DB_BACKUP_PATH"

if [ "$UPLOAD_STORAGE_TYPE" = "local" ] && [ "$SKIP_UPLOAD_RESTORE" != "yes" ]; then
  if [ -z "$UPLOAD_BACKUP_PATH" ] || [ ! -f "$UPLOAD_BACKUP_PATH" ]; then
    echo "Local attachment storage requires UPLOAD_BACKUP_PATH or explicit SKIP_UPLOAD_RESTORE=yes" >&2
    exit 1
  fi
  verify_checksum "$UPLOAD_BACKUP_PATH"
elif [ -n "$UPLOAD_BACKUP_PATH" ]; then
  if [ ! -f "$UPLOAD_BACKUP_PATH" ]; then
    echo "UPLOAD_BACKUP_PATH file not found: $UPLOAD_BACKUP_PATH" >&2
    exit 1
  fi
  verify_checksum "$UPLOAD_BACKUP_PATH"
fi

safety_backup=""
if [ "$SKIP_SAFETY_BACKUP" != "yes" ]; then
  echo "Creating a local safety backup before destructive restore"
  BACKUP_REQUIRE_OFFSITE=false BACKUP_REMOTE_URI= BACKUP_SECONDARY_DIR= \
    ENV_FILE="$ENV_FILE" sh "$SCRIPT_DIR/backup-compose.sh"
  safety_backup="$(latest_file "$BACKUP_DIR" 'mysql_*.sql')"
else
  echo "WARNING: pre-restore safety backup was explicitly skipped" >&2
fi

echo "Stopping application services before restore"
docker compose --env-file "$ENV_FILE" stop frontend backend

restore_failed=true
handle_exit() {
  if [ "$restore_failed" = true ]; then
    echo "Restore failed; frontend and backend remain stopped." >&2
    if [ -n "$safety_backup" ]; then echo "Safety backup: $safety_backup" >&2; fi
  fi
}
trap handle_exit EXIT INT TERM

echo "Recreating MySQL database $DATABASE"
docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'db="$1"; case "$db" in *[!a-zA-Z0-9_]*) exit 64 ;; esac; exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS \`$db\`; CREATE DATABASE \`$db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"' \
  sh "$DATABASE"

echo "Restoring MySQL from $DB_BACKUP_PATH"
docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$1"' \
  sh "$DATABASE" < "$DB_BACKUP_PATH"

if [ "$UPLOAD_STORAGE_TYPE" = "s3" ]; then
  echo "APP_UPLOAD_STORAGE_TYPE=s3; restore and reconcile object versions before opening traffic."
elif [ "$SKIP_UPLOAD_RESTORE" = "yes" ]; then
  echo "WARNING: local attachment restore was explicitly skipped" >&2
else
  echo "Restoring report images from $UPLOAD_BACKUP_PATH"
  docker compose --env-file "$ENV_FILE" run --rm --no-deps -T backend sh -c \
    'mkdir -p /var/lib/heihu-workorder/report-images && find /var/lib/heihu-workorder/report-images -mindepth 1 -delete'
  docker compose --env-file "$ENV_FILE" run --rm --no-deps -T backend sh -c \
    'tar -C /var/lib/heihu-workorder -xzf -' \
    < "$UPLOAD_BACKUP_PATH"
fi

echo "Starting application services"
docker compose --env-file "$ENV_FILE" up -d backend frontend

echo "Refreshing the binlog recovery chain"
ENV_FILE="$ENV_FILE" sh "$SCRIPT_DIR/archive-binlogs-compose.sh"

echo "Waiting for restored services and production controls to become healthy"
attempt=1
while [ "$attempt" -le 30 ]; do
  if ENV_FILE="$ENV_FILE" sh "$SCRIPT_DIR/monitor-compose.sh"; then
    restore_failed=false
    trap - EXIT INT TERM
    echo "Restore complete and health checks passed."
    if [ -n "$safety_backup" ]; then echo "Safety backup: $safety_backup"; fi
    exit 0
  fi
  sleep 2
  attempt=$((attempt + 1))
done

echo "Restore completed but health checks did not pass within 60 seconds." >&2
docker compose --env-file "$ENV_FILE" ps >&2
exit 1

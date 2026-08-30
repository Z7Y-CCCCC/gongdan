#!/usr/bin/env sh
set -eu

ENV_FILE="${ENV_FILE:-.env}"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck source=compose-common.sh
. "$SCRIPT_DIR/compose-common.sh"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

BACKUP_DIR="$(config_value BACKUP_DIR backups)"
DB_BACKUP_PATH="${DB_BACKUP_PATH:-}"
if [ -z "$DB_BACKUP_PATH" ]; then
  DB_BACKUP_PATH="$(latest_file "$BACKUP_DIR" 'mysql_*.sql')"
fi
if [ -z "$DB_BACKUP_PATH" ] || [ ! -f "$DB_BACKUP_PATH" ]; then
  echo "No database backup was found in $BACKUP_DIR" >&2
  exit 1
fi
verify_checksum "$DB_BACKUP_PATH" >/dev/null

TEMP_DATABASE="gongdan_restore_drill_$(date +%Y%m%d%H%M%S)_$$"
case "$TEMP_DATABASE" in *[!a-zA-Z0-9_]*) echo "Unsafe temporary database name" >&2; exit 1 ;; esac
START_EPOCH="$(date +%s)"
CREATED=false

cleanup() {
  if [ "$CREATED" = true ]; then
    docker compose --env-file "$ENV_FILE" exec -T db sh -c \
      'db="$1"; mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS \`$db\`"' \
      sh "$TEMP_DATABASE" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

echo "Creating isolated restore database: $TEMP_DATABASE"
docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'db="$1"; exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE \`$db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"' \
  sh "$TEMP_DATABASE"
CREATED=true

echo "Restoring $DB_BACKUP_PATH into $TEMP_DATABASE"
docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$1"' \
  sh "$TEMP_DATABASE" < "$DB_BACKUP_PATH"

table_count="$(docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$1" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"' \
  sh "$TEMP_DATABASE")"
critical_count="$(docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$1" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN (\"factories\", \"factory_users\", \"work_orders\")"' \
  sh "$TEMP_DATABASE")"
flyway_count="$(docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$1" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"' \
  sh "$TEMP_DATABASE")"

case "$table_count" in ''|*[!0-9]*) echo "Invalid restored table count: $table_count" >&2; exit 1 ;; esac
case "$critical_count" in ''|*[!0-9]*) echo "Invalid critical table count: $critical_count" >&2; exit 1 ;; esac
case "$flyway_count" in ''|*[!0-9]*) echo "Invalid Flyway migration count: $flyway_count" >&2; exit 1 ;; esac
if [ "$table_count" -lt 3 ] || [ "$critical_count" -ne 3 ] || [ "$flyway_count" -lt 1 ]; then
  echo "Restore validation failed: tables=$table_count critical=$critical_count flyway=$flyway_count" >&2
  exit 1
fi

duration="$(( $(date +%s) - START_EPOCH ))"
printf '%s restore_drill=passed backup=%s database=%s tables=%s critical=%s flyway=%s duration_seconds=%s\n' \
  "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$DB_BACKUP_PATH" "$TEMP_DATABASE" "$table_count" "$critical_count" "$flyway_count" "$duration" \
  >> "$BACKUP_DIR/restore-drills.log"
echo "Restore drill passed: tables=$table_count critical=$critical_count flyway=$flyway_count duration=${duration}s"

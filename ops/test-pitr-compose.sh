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
ARCHIVE_DIR="$(config_value BINLOG_ARCHIVE_DIR backups/binlogs)"
DATABASE="$(config_value MYSQL_DATABASE heihu_workorder)"
DB_BACKUP_PATH="${DB_BACKUP_PATH:-}"
STOP_DATETIME="${PITR_TEST_STOP_DATETIME:-}"
if [ -z "$DB_BACKUP_PATH" ]; then DB_BACKUP_PATH="$(latest_file "$BACKUP_DIR" 'mysql_*.sql')"; fi
if [ -z "$DB_BACKUP_PATH" ] || [ ! -f "$DB_BACKUP_PATH" ]; then
  echo "No database backup was found in $BACKUP_DIR" >&2
  exit 1
fi
verify_checksum "$DB_BACKUP_PATH" >/dev/null

coordinate_line="$(grep -m 1 -E '^-- CHANGE (REPLICATION SOURCE|MASTER) TO ' "$DB_BACKUP_PATH" || true)"
if [ -z "$coordinate_line" ]; then
  echo "The backup does not contain binary log coordinates. Create a new format_version=2 backup." >&2
  exit 1
fi
start_file="$(printf '%s' "$coordinate_line" | sed -n "s/.*\(SOURCE_LOG_FILE\|MASTER_LOG_FILE\)='\([^']*\)'.*/\2/p")"
start_position="$(printf '%s' "$coordinate_line" | sed -n 's/.*\(SOURCE_LOG_POS\|MASTER_LOG_POS\)=\([0-9][0-9]*\).*/\2/p')"
case "$start_file" in binlog.*) ;; *) echo "Unexpected start binlog: $start_file" >&2; exit 1 ;; esac
case "$start_position" in ''|*[!0-9]*) echo "Unexpected start position: $start_position" >&2; exit 1 ;; esac

TEMP_DATABASE="gongdan_pitr_drill_$(date +%Y%m%d%H%M%S)_$$"
case "$TEMP_DATABASE" in *[!a-zA-Z0-9_]*) echo "Unsafe temporary database name" >&2; exit 1 ;; esac
START_EPOCH="$(date +%s)"
CREATED=false
COPIED_FILES=""

cleanup() {
  for copied in $COPIED_FILES; do
    docker compose --env-file "$ENV_FILE" exec -T db rm -f "/tmp/$copied" >/dev/null 2>&1 || true
  done
  if [ "$CREATED" = true ]; then
    docker compose --env-file "$ENV_FILE" exec -T db sh -c \
      'db="$1"; mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS \`$db\`"' \
      sh "$TEMP_DATABASE" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'db="$1"; exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE \`$db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"' \
  sh "$TEMP_DATABASE"
CREATED=true
docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$1"' \
  sh "$TEMP_DATABASE" < "$DB_BACKUP_PATH"

found_start=false
replayed=0
for binlog_path in "$ARCHIVE_DIR"/binlog.[0-9]*; do
  [ -f "$binlog_path" ] || continue
  binlog_file="$(basename "$binlog_path")"
  suffix="${binlog_file#binlog.}"
  case "$suffix" in ''|*[!0-9]*) continue ;; esac
  if [ "$found_start" = false ]; then
    if [ "$binlog_file" != "$start_file" ]; then continue; fi
    found_start=true
  fi
  verify_checksum "$binlog_path" >/dev/null

  container_file="gongdan-pitr-$$-$binlog_file"
  docker compose --env-file "$ENV_FILE" cp "$binlog_path" "db:/tmp/$container_file" >/dev/null
  COPIED_FILES="$COPIED_FILES $container_file"

  position_argument=""
  if [ "$replayed" -eq 0 ]; then position_argument="--start-position=$start_position"; fi
  docker compose --env-file "$ENV_FILE" exec -T db sh -c \
    'file="$1"; source_db="$2"; target_db="$3"; start_arg="$4"; stop_value="$5"; set -- mysqlbinlog --skip-gtids=true --database="$source_db" --rewrite-db="$source_db->$target_db"; if [ -n "$start_arg" ]; then set -- "$@" "$start_arg"; fi; if [ -n "$stop_value" ]; then set -- "$@" "--stop-datetime=$stop_value"; fi; "$@" "/tmp/$file" | mysql --init-command="SET SESSION sql_log_bin=0" -uroot -p"$MYSQL_ROOT_PASSWORD" "$target_db"' \
    sh "$container_file" "$DATABASE" "$TEMP_DATABASE" "$position_argument" "$STOP_DATETIME"
  replayed=$((replayed + 1))
done

if [ "$found_start" != true ] || [ "$replayed" -lt 1 ]; then
  echo "Required start binlog was not found in $ARCHIVE_DIR: $start_file" >&2
  exit 1
fi

critical_count="$(docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$1" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN (\"factories\", \"factory_users\", \"work_orders\")"' \
  sh "$TEMP_DATABASE")"
case "$critical_count" in ''|*[!0-9]*) echo "Invalid critical table count: $critical_count" >&2; exit 1 ;; esac
if [ "$critical_count" -ne 3 ]; then
  echo "PITR validation failed: critical tables=$critical_count" >&2
  exit 1
fi

duration="$(( $(date +%s) - START_EPOCH ))"
printf '%s pitr_drill=passed backup=%s start_file=%s start_position=%s replayed=%s stop_datetime=%s duration_seconds=%s\n' \
  "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$DB_BACKUP_PATH" "$start_file" "$start_position" "$replayed" "${STOP_DATETIME:-latest}" "$duration" \
  >> "$BACKUP_DIR/restore-drills.log"
echo "PITR drill passed: start=$start_file:$start_position replayed=$replayed duration=${duration}s"

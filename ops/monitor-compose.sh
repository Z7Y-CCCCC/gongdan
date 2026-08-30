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

TIMEOUT_SECONDS="$(config_value MONITOR_TIMEOUT_SECONDS 8)"
ALERT_WEBHOOK_URL="$(config_value ALERT_WEBHOOK_URL "")"
FRONTEND_URL="$(config_value MONITOR_FRONTEND_URL "http://127.0.0.1:$(config_value FRONTEND_PORT 8080)/")"
BACKEND_HEALTH_URL="$(config_value MONITOR_HEALTH_URL "http://127.0.0.1:$(config_value FRONTEND_PORT 8080)/api/actuator/health/readiness")"
BACKUP_DIR="$(config_value BACKUP_DIR backups)"
BACKUP_MAX_AGE_HOURS="$(config_value BACKUP_MAX_AGE_HOURS 26)"
BACKUP_MIN_FREE_GB="$(config_value BACKUP_MIN_FREE_GB 10)"
BINLOG_ARCHIVE_DIR="$(config_value BINLOG_ARCHIVE_DIR backups/binlogs)"
BINLOG_MAX_AGE_MINUTES="$(config_value BINLOG_ARCHIVE_MAX_AGE_MINUTES 10)"
REQUIRE_PITR="$(config_value BACKUP_REQUIRE_PITR true)"

failures=""

record_failure() {
  name="$1"
  detail="$2"
  echo "FAIL $name $detail" >&2
  failures="${failures}${name} "
}

record_ok() {
  echo "OK $1 $2"
}

alert() {
  message="$1"
  if [ -n "$ALERT_WEBHOOK_URL" ]; then
    escaped="$(printf '%s' "$message" | sed 's/\\/\\\\/g; s/"/\\"/g')"
    curl -fsS -m "$TIMEOUT_SECONDS" \
      -H "Content-Type: application/json" \
      -d "{\"text\":\"$escaped\"}" \
      "$ALERT_WEBHOOK_URL" >/dev/null || true
  fi
}

check_url() {
  name="$1"
  url="$2"
  if curl -fsS -m "$TIMEOUT_SECONDS" "$url" >/dev/null; then
    record_ok "$name" "$url"
  else
    record_failure "$name" "$url"
  fi
}

is_integer() {
  value="$1"
  case "$value" in
    ''|*[!0-9]*) return 1 ;;
    *) return 0 ;;
  esac
}

for config_item in \
  "BACKUP_MAX_AGE_HOURS=$BACKUP_MAX_AGE_HOURS" \
  "BACKUP_MIN_FREE_GB=$BACKUP_MIN_FREE_GB" \
  "BINLOG_ARCHIVE_MAX_AGE_MINUTES=$BINLOG_MAX_AGE_MINUTES"
do
  config_name="${config_item%%=*}"
  config_number="${config_item#*=}"
  if ! is_integer "$config_number"; then
    echo "$config_name must be a non-negative integer: $config_number" >&2
    exit 1
  fi
done

check_url frontend "$FRONTEND_URL"
check_url backend-readiness "$BACKEND_HEALTH_URL"

if docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' >/dev/null 2>&1; then
  record_ok mysql "server responded"
else
  record_failure mysql "server did not respond"
fi

if docker compose --env-file "$ENV_FILE" exec -T redis redis-cli ping 2>/dev/null | grep -q '^PONG'; then
  record_ok redis "server responded"
else
  record_failure redis "server did not respond"
fi

now_epoch="$(date +%s)"
status_file="$BACKUP_DIR/backup-status.env"
if [ ! -f "$status_file" ]; then
  record_failure database-backup "status file missing: $status_file"
elif ! verify_checksum "$status_file" >/dev/null 2>&1; then
  record_failure database-backup "status checksum failed"
else
  last_success_epoch="$(grep -E '^last_success_epoch=' "$status_file" | tail -n 1 | cut -d= -f2 || true)"
  database_file="$(grep -E '^database_file=' "$status_file" | tail -n 1 | cut -d= -f2- || true)"
  if ! is_integer "$last_success_epoch"; then
    record_failure database-backup "invalid last_success_epoch"
  else
    age_seconds="$((now_epoch - last_success_epoch))"
    max_age_seconds="$((BACKUP_MAX_AGE_HOURS * 3600))"
  fi
  if ! is_integer "$last_success_epoch"; then
    :
  elif [ "$age_seconds" -gt "$max_age_seconds" ]; then
    record_failure database-backup "age_seconds=$age_seconds max=$max_age_seconds"
  elif [ -z "$database_file" ] || [ ! -f "$BACKUP_DIR/$database_file" ]; then
    record_failure database-backup "recorded database file is missing: $database_file"
  elif ! verify_checksum "$BACKUP_DIR/$database_file" >/dev/null 2>&1; then
    record_failure database-backup "database checksum failed: $database_file"
  else
    record_ok database-backup "file=$database_file age_seconds=$age_seconds"
  fi
fi

if is_true "$REQUIRE_PITR"; then
  binlog_status="$BINLOG_ARCHIVE_DIR/binlog-archive-status.env"
  if [ ! -f "$binlog_status" ]; then
    record_failure binlog-archive "status file missing: $binlog_status"
  elif ! verify_checksum "$binlog_status" >/dev/null 2>&1; then
    record_failure binlog-archive "status checksum failed"
  else
    archived_epoch="$(grep -E '^archived_epoch=' "$binlog_status" | tail -n 1 | cut -d= -f2 || true)"
    latest_archived="$(grep -E '^latest_archived_file=' "$binlog_status" | tail -n 1 | cut -d= -f2- || true)"
    if ! is_integer "$archived_epoch"; then
      record_failure binlog-archive "invalid archived_epoch"
    else
      age_seconds="$((now_epoch - archived_epoch))"
      max_age_seconds="$((BINLOG_MAX_AGE_MINUTES * 60))"
    fi
    if ! is_integer "$archived_epoch"; then
      :
    elif [ "$age_seconds" -gt "$max_age_seconds" ]; then
      record_failure binlog-archive "age_seconds=$age_seconds max=$max_age_seconds"
    elif [ -z "$latest_archived" ] || [ ! -f "$BINLOG_ARCHIVE_DIR/$latest_archived" ]; then
      record_failure binlog-archive "latest archived file is missing: $latest_archived"
    elif ! verify_checksum "$BINLOG_ARCHIVE_DIR/$latest_archived" >/dev/null 2>&1; then
      record_failure binlog-archive "checksum failed: $latest_archived"
    else
      record_ok binlog-archive "file=$latest_archived age_seconds=$age_seconds"
    fi
  fi
fi

if [ -d "$BACKUP_DIR" ]; then
  available_kb="$(df -Pk "$BACKUP_DIR" | awk 'NR == 2 {print $4}')"
  if ! is_integer "$available_kb"; then
    record_failure backup-disk "unable to read available space"
  else
    minimum_kb="$((BACKUP_MIN_FREE_GB * 1024 * 1024))"
  fi
  if ! is_integer "$available_kb"; then
    :
  elif [ "$available_kb" -lt "$minimum_kb" ]; then
    record_failure backup-disk "available_kb=$available_kb minimum_kb=$minimum_kb"
  else
    record_ok backup-disk "available_kb=$available_kb"
  fi
else
  record_failure backup-disk "directory missing: $BACKUP_DIR"
fi

if [ -n "$failures" ]; then
  alert "gongdan production monitor failed: $failures"
  exit 1
fi

echo "Monitor passed"

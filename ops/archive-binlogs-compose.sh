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

ARCHIVE_DIR="$(config_value BINLOG_ARCHIVE_DIR backups/binlogs)"
SECONDARY_ROOT="$(config_value BACKUP_SECONDARY_DIR "")"
REMOTE_URI="$(config_value BACKUP_REMOTE_URI "")"
RETENTION_DAYS="$(config_value BINLOG_ARCHIVE_RETENTION_DAYS 35)"
STATUS_FILE="$ARCHIVE_DIR/binlog-archive-status.env"
LIST_FILE="$ARCHIVE_DIR/.binary-logs.$$"
LOCK_DIR="$ARCHIVE_DIR/.archive.lock"

require_safe_directory "$ARCHIVE_DIR" BINLOG_ARCHIVE_DIR
case "$RETENTION_DAYS" in
  ''|*[!0-9]*) echo "BINLOG_ARCHIVE_RETENTION_DAYS must be a positive integer" >&2; exit 1 ;;
esac
if [ "$RETENTION_DAYS" -lt 1 ]; then
  echo "BINLOG_ARCHIVE_RETENTION_DAYS must be at least 1" >&2
  exit 1
fi
if is_true "$(config_value BACKUP_REQUIRE_OFFSITE true)" && [ -z "$SECONDARY_ROOT" ] && [ -z "$REMOTE_URI" ]; then
  echo "Binlog archive requires BACKUP_SECONDARY_DIR or BACKUP_REMOTE_URI" >&2
  exit 1
fi

mkdir -p "$ARCHIVE_DIR"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  echo "Another binlog archive is already running: $LOCK_DIR" >&2
  exit 1
fi
cleanup() {
  rm -f "$LIST_FILE"
  rmdir "$LOCK_DIR" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

if [ -n "$REMOTE_URI" ] && is_true "$(config_value BACKUP_VERIFY_REMOTE_POLICY true)"; then
  verify_remote_policy
fi

server_uuid="$(docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT @@server_uuid"')"
case "$server_uuid" in
  ''|*[!a-zA-Z0-9-]*) echo "Unexpected MySQL server UUID: $server_uuid" >&2; exit 1 ;;
esac

# Closing the active log creates a stable file that can be copied without racing writes.
docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -e "FLUSH BINARY LOGS; SHOW BINARY LOGS"' \
  > "$LIST_FILE"
test -s "$LIST_FILE"

current_file="$(tail -n 1 "$LIST_FILE" | awk '{print $1}')"
latest_archived=""
secondary_dir=""
if [ -n "$SECONDARY_ROOT" ]; then secondary_dir="${SECONDARY_ROOT%/}/binlogs/$server_uuid"; fi

while IFS="$(printf '\t')" read -r file expected_size remainder; do
  [ -n "$file" ] || continue
  case "$file" in
    binlog.*)
      suffix="${file#binlog.}"
      case "$suffix" in ''|*[!0-9]*) echo "Unexpected binary log name: $file" >&2; exit 1 ;; esac
      ;;
    *) echo "Unexpected binary log name: $file" >&2; exit 1 ;;
  esac
  if [ "$file" = "$current_file" ]; then continue; fi

  destination="$ARCHIVE_DIR/$file"
  temporary="${destination}.tmp.$$"
  if [ ! -f "$destination" ]; then
    docker compose --env-file "$ENV_FILE" exec -T db sh -c \
      'file="$1"; suffix="${file#binlog.}"; case "$file:$suffix" in binlog.*:*[!0-9]*|binlog.:*) exit 64 ;; binlog.*:*) cat "/var/lib/mysql/$file" ;; *) exit 64 ;; esac' \
      sh "$file" > "$temporary"
    actual_size="$(wc -c < "$temporary" | tr -d ' ')"
    if [ "$actual_size" != "$expected_size" ]; then
      rm -f "$temporary"
      echo "Binary log size mismatch for $file: expected=$expected_size actual=$actual_size" >&2
      exit 1
    fi
    mv "$temporary" "$destination"
    write_checksum "$destination"
  else
    verify_checksum "$destination" >/dev/null
  fi

  if [ -n "$secondary_dir" ]; then
    atomic_copy_to_directory "$destination" "$secondary_dir"
    atomic_copy_to_directory "${destination}.sha256" "$secondary_dir"
  fi
  if [ -n "$REMOTE_URI" ] && [ ! -f "${destination}.remote-ok" ]; then
    remote_upload_file "$destination" "binlog/$server_uuid/$file"
    remote_upload_file "${destination}.sha256" "binlog/$server_uuid/${file}.sha256"
    : > "${destination}.remote-ok"
  fi
  latest_archived="$file"
done < "$LIST_FILE"

if [ -z "$latest_archived" ]; then
  echo "No closed MySQL binary log was available after rotation" >&2
  exit 1
fi

{
  echo "server_uuid=$server_uuid"
  echo "archived_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "archived_epoch=$(date +%s)"
  echo "current_file=$current_file"
  echo "latest_archived_file=$latest_archived"
} > "${STATUS_FILE}.tmp"
mv "${STATUS_FILE}.tmp" "$STATUS_FILE"
write_checksum "$STATUS_FILE"

if [ -n "$secondary_dir" ]; then
  atomic_copy_to_directory "$STATUS_FILE" "$secondary_dir"
  atomic_copy_to_directory "${STATUS_FILE}.sha256" "$secondary_dir"
fi
if [ -n "$REMOTE_URI" ]; then
  remote_upload_file "$STATUS_FILE" "binlog/$server_uuid/$(basename "$STATUS_FILE")"
  remote_upload_file "${STATUS_FILE}.sha256" "binlog/$server_uuid/$(basename "$STATUS_FILE").sha256"
fi

find "$ARCHIVE_DIR" -maxdepth 1 -type f \
  \( -name 'binlog.[0-9]*' -o -name 'binlog.[0-9]*.sha256' -o -name 'binlog.[0-9]*.remote-ok' \) \
  -mtime "+$RETENTION_DAYS" -delete

echo "Binlog archive passed: server=$server_uuid latest=$latest_archived current=$current_file"

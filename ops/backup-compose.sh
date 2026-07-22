#!/usr/bin/env sh
set -eu

ENV_FILE="${ENV_FILE:-.env}"
BACKUP_DIR="${BACKUP_DIR:-backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
STAMP="$(date +%Y%m%d_%H%M%S)"

if [ -z "$BACKUP_DIR" ] || [ "$BACKUP_DIR" = "/" ]; then
  echo "Unsafe BACKUP_DIR: $BACKUP_DIR" >&2
  exit 1
fi

case "$RETENTION_DAYS" in
  ''|*[!0-9]*)
    echo "BACKUP_RETENTION_DAYS must be a positive integer" >&2
    exit 1
    ;;
esac
if [ "$RETENTION_DAYS" -lt 1 ]; then
  echo "BACKUP_RETENTION_DAYS must be at least 1" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

DB_BACKUP="$BACKUP_DIR/mysql_${STAMP}.sql"
UPLOAD_BACKUP="$BACKUP_DIR/report-images_${STAMP}.tar.gz"
LOG_BACKUP="$BACKUP_DIR/runtime-logs_${STAMP}.tar.gz"
MANIFEST="$BACKUP_DIR/manifest_${STAMP}.txt"
DB_TEMP="${DB_BACKUP}.tmp"
UPLOAD_TEMP="${UPLOAD_BACKUP}.tmp"
LOG_TEMP="${LOG_BACKUP}.tmp"
UPLOAD_STORAGE_TYPE="$(grep -E '^APP_UPLOAD_STORAGE_TYPE=' "$ENV_FILE" | tail -n 1 | sed 's/^APP_UPLOAD_STORAGE_TYPE=//' || true)"

cleanup_temp() {
  rm -f "$DB_TEMP" "$UPLOAD_TEMP" "$LOG_TEMP"
}
trap cleanup_temp EXIT INT TERM

write_checksum() {
  file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "$(dirname "$file")" && sha256sum "$(basename "$file")" > "$(basename "$file").sha256")
  elif command -v shasum >/dev/null 2>&1; then
    (cd "$(dirname "$file")" && shasum -a 256 "$(basename "$file")" > "$(basename "$file").sha256")
  else
    echo "Warning: no SHA-256 utility found; checksum not written for $file" >&2
  fi
}

echo "Backing up MySQL to $DB_BACKUP"
docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'exec mysqldump --single-transaction --routines --triggers -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  > "$DB_TEMP"
test -s "$DB_TEMP"
mv "$DB_TEMP" "$DB_BACKUP"
write_checksum "$DB_BACKUP"

if [ "$UPLOAD_STORAGE_TYPE" = "s3" ]; then
  echo "APP_UPLOAD_STORAGE_TYPE=s3; skip local report image volume backup."
  echo "Back up the configured object storage bucket with provider snapshots, replication, or lifecycle tooling."
else
  echo "Backing up report images to $UPLOAD_BACKUP"
  docker compose --env-file "$ENV_FILE" exec -T backend sh -c \
    'tar -C /var/lib/heihu-workorder -czf - report-images' \
    > "$UPLOAD_TEMP"
  test -s "$UPLOAD_TEMP"
  mv "$UPLOAD_TEMP" "$UPLOAD_BACKUP"
  write_checksum "$UPLOAD_BACKUP"
fi

echo "Backing up runtime logs to $LOG_BACKUP"
docker compose --env-file "$ENV_FILE" exec -T backend sh -c \
  'tar -C /var/log/heihu-workorder -czf - .' \
  > "$LOG_TEMP"
test -s "$LOG_TEMP"
mv "$LOG_TEMP" "$LOG_BACKUP"
write_checksum "$LOG_BACKUP"

{
  echo "created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "database=$DB_BACKUP"
  if [ "$UPLOAD_STORAGE_TYPE" != "s3" ]; then
    echo "uploads=$UPLOAD_BACKUP"
  fi
  echo "runtime_logs=$LOG_BACKUP"
  echo "retention_days=$RETENTION_DAYS"
} > "$MANIFEST"

find "$BACKUP_DIR" -maxdepth 1 -type f \
  \( -name 'mysql_*.sql' -o -name 'mysql_*.sql.sha256' -o \
     -name 'report-images_*.tar.gz' -o -name 'report-images_*.tar.gz.sha256' -o \
     -name 'runtime-logs_*.tar.gz' -o -name 'runtime-logs_*.tar.gz.sha256' -o \
     -name 'manifest_*.txt' \) \
  -mtime "+$RETENTION_DAYS" -delete

echo "Backup complete:"
echo "  $DB_BACKUP"
if [ "$UPLOAD_STORAGE_TYPE" != "s3" ]; then
  echo "  $UPLOAD_BACKUP"
fi
echo "  $LOG_BACKUP"
echo "  $MANIFEST"

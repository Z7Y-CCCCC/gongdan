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
SECONDARY_DIR="$(config_value BACKUP_SECONDARY_DIR "")"
REMOTE_URI="$(config_value BACKUP_REMOTE_URI "")"
RETENTION_DAYS="$(config_value BACKUP_RETENTION_DAYS 35)"
STAMP="$(date +%Y%m%d_%H%M%S)"
REMOTE_DATE_PATH="full/$(date +%Y/%m/%d)"

require_safe_directory "$BACKUP_DIR" BACKUP_DIR
case "$RETENTION_DAYS" in
  ''|*[!0-9]*) echo "BACKUP_RETENTION_DAYS must be a positive integer" >&2; exit 1 ;;
esac
if [ "$RETENTION_DAYS" -lt 1 ]; then
  echo "BACKUP_RETENTION_DAYS must be at least 1" >&2
  exit 1
fi
if [ -n "$SECONDARY_DIR" ]; then
  require_safe_directory "$SECONDARY_DIR" BACKUP_SECONDARY_DIR
  if [ "$SECONDARY_DIR" = "$BACKUP_DIR" ]; then
    echo "BACKUP_SECONDARY_DIR must differ from BACKUP_DIR" >&2
    exit 1
  fi
fi
if is_true "$(config_value BACKUP_REQUIRE_OFFSITE true)" && [ -z "$SECONDARY_DIR" ] && [ -z "$REMOTE_URI" ]; then
  echo "Production backup requires BACKUP_SECONDARY_DIR or BACKUP_REMOTE_URI" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"

DB_BACKUP="$BACKUP_DIR/mysql_${STAMP}.sql"
DB_METADATA="$BACKUP_DIR/mysql_${STAMP}.metadata.txt"
UPLOAD_BACKUP="$BACKUP_DIR/report-images_${STAMP}.tar.gz"
LOG_BACKUP="$BACKUP_DIR/runtime-logs_${STAMP}.tar.gz"
MANIFEST="$BACKUP_DIR/manifest_${STAMP}.txt"
STATUS_FILE="$BACKUP_DIR/backup-status.env"
DB_TEMP="${DB_BACKUP}.tmp"
DB_METADATA_TEMP="${DB_METADATA}.tmp"
UPLOAD_TEMP="${UPLOAD_BACKUP}.tmp"
LOG_TEMP="${LOG_BACKUP}.tmp"
UPLOAD_STORAGE_TYPE="$(config_value APP_UPLOAD_STORAGE_TYPE local)"

cleanup_temp() {
  rm -f "$DB_TEMP" "$DB_METADATA_TEMP" "$UPLOAD_TEMP" "$LOG_TEMP"
}
trap cleanup_temp EXIT INT TERM

echo "Backing up MySQL to $DB_BACKUP"
docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'exec mysqldump --single-transaction --source-data=2 --routines --triggers --events --hex-blob --set-gtid-purged=OFF -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  > "$DB_TEMP"
test -s "$DB_TEMP"
mv "$DB_TEMP" "$DB_BACKUP"
write_checksum "$DB_BACKUP"

docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT CONCAT(\"server_uuid=\", @@server_uuid); SELECT CONCAT(\"gtid_executed=\", @@global.gtid_executed); SHOW BINARY LOG STATUS;"' \
  > "$DB_METADATA_TEMP"
test -s "$DB_METADATA_TEMP"
mv "$DB_METADATA_TEMP" "$DB_METADATA"
write_checksum "$DB_METADATA"

if [ "$UPLOAD_STORAGE_TYPE" = "s3" ]; then
  echo "APP_UPLOAD_STORAGE_TYPE=s3; local report image volume is not part of this archive."
  echo "The configured object bucket must be protected and reconciled separately."
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
  echo "format_version=2"
  echo "created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "database=$DB_BACKUP"
  echo "database_sha256=${DB_BACKUP}.sha256"
  echo "database_metadata=$DB_METADATA"
  echo "database_metadata_sha256=${DB_METADATA}.sha256"
  if [ "$UPLOAD_STORAGE_TYPE" != "s3" ]; then
    echo "uploads=$UPLOAD_BACKUP"
    echo "uploads_sha256=${UPLOAD_BACKUP}.sha256"
  fi
  echo "runtime_logs=$LOG_BACKUP"
  echo "runtime_logs_sha256=${LOG_BACKUP}.sha256"
  echo "upload_storage_type=$UPLOAD_STORAGE_TYPE"
  echo "retention_days=$RETENTION_DAYS"
  echo "git_commit=$(git rev-parse --verify HEAD 2>/dev/null || echo unknown)"
} > "$MANIFEST"
write_checksum "$MANIFEST"

if [ -n "$REMOTE_URI" ] && is_true "$(config_value BACKUP_VERIFY_REMOTE_POLICY true)"; then
  echo "Verifying remote backup bucket policy"
  verify_remote_policy
fi

replicate_artifact() {
  artifact="$1"
  if [ -n "$SECONDARY_DIR" ]; then
    atomic_copy_to_directory "$artifact" "$SECONDARY_DIR"
  fi
  if [ -n "$REMOTE_URI" ]; then
    remote_upload_file "$artifact" "$REMOTE_DATE_PATH/$(basename "$artifact")"
  fi
}

replicate_artifact "$DB_BACKUP"
replicate_artifact "${DB_BACKUP}.sha256"
replicate_artifact "$DB_METADATA"
replicate_artifact "${DB_METADATA}.sha256"
if [ "$UPLOAD_STORAGE_TYPE" != "s3" ]; then
  replicate_artifact "$UPLOAD_BACKUP"
  replicate_artifact "${UPLOAD_BACKUP}.sha256"
fi
replicate_artifact "$LOG_BACKUP"
replicate_artifact "${LOG_BACKUP}.sha256"
replicate_artifact "$MANIFEST"
replicate_artifact "${MANIFEST}.sha256"

{
  echo "last_success_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "last_success_epoch=$(date +%s)"
  echo "database_file=$(basename "$DB_BACKUP")"
  echo "manifest_file=$(basename "$MANIFEST")"
  echo "secondary_configured=$(if [ -n "$SECONDARY_DIR" ]; then echo true; else echo false; fi)"
  echo "remote_configured=$(if [ -n "$REMOTE_URI" ]; then echo true; else echo false; fi)"
} > "${STATUS_FILE}.tmp"
mv "${STATUS_FILE}.tmp" "$STATUS_FILE"
write_checksum "$STATUS_FILE"
if [ -n "$SECONDARY_DIR" ]; then
  atomic_copy_to_directory "$STATUS_FILE" "$SECONDARY_DIR"
  atomic_copy_to_directory "${STATUS_FILE}.sha256" "$SECONDARY_DIR"
fi
if [ -n "$REMOTE_URI" ]; then
  remote_upload_file "$STATUS_FILE" "status/$(basename "$STATUS_FILE")"
  remote_upload_file "${STATUS_FILE}.sha256" "status/$(basename "$STATUS_FILE").sha256"
fi

find "$BACKUP_DIR" -maxdepth 1 -type f \
  \( -name 'mysql_*.sql' -o -name 'mysql_*.sql.sha256' -o \
     -name 'mysql_*.metadata.txt' -o -name 'mysql_*.metadata.txt.sha256' -o \
     -name 'report-images_*.tar.gz' -o -name 'report-images_*.tar.gz.sha256' -o \
     -name 'runtime-logs_*.tar.gz' -o -name 'runtime-logs_*.tar.gz.sha256' -o \
     -name 'manifest_*.txt' -o -name 'manifest_*.txt.sha256' \) \
  -mtime "+$RETENTION_DAYS" -delete

echo "Backup complete:"
echo "  $DB_BACKUP"
echo "  $DB_METADATA"
if [ "$UPLOAD_STORAGE_TYPE" != "s3" ]; then echo "  $UPLOAD_BACKUP"; fi
echo "  $LOG_BACKUP"
echo "  $MANIFEST"
if [ -n "$SECONDARY_DIR" ]; then echo "  secondary=$SECONDARY_DIR"; fi
if [ -n "$REMOTE_URI" ]; then echo "  remote=$REMOTE_URI/$REMOTE_DATE_PATH"; fi

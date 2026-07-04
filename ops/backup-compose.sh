#!/usr/bin/env sh
set -eu

ENV_FILE="${ENV_FILE:-.env}"
BACKUP_DIR="${BACKUP_DIR:-backups}"
STAMP="$(date +%Y%m%d_%H%M%S)"

mkdir -p "$BACKUP_DIR"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

DB_BACKUP="$BACKUP_DIR/mysql_${STAMP}.sql"
UPLOAD_BACKUP="$BACKUP_DIR/report-images_${STAMP}.tar.gz"
UPLOAD_STORAGE_TYPE="$(grep -E '^APP_UPLOAD_STORAGE_TYPE=' "$ENV_FILE" | tail -n 1 | sed 's/^APP_UPLOAD_STORAGE_TYPE=//' || true)"

echo "Backing up MySQL to $DB_BACKUP"
docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'exec mysqldump --single-transaction --routines --triggers -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  > "$DB_BACKUP"

if [ "$UPLOAD_STORAGE_TYPE" = "s3" ]; then
  echo "APP_UPLOAD_STORAGE_TYPE=s3; skip local report image volume backup."
  echo "Back up the configured object storage bucket with provider snapshots, replication, or lifecycle tooling."
else
  echo "Backing up report images to $UPLOAD_BACKUP"
  docker compose --env-file "$ENV_FILE" exec -T backend sh -c \
    'tar -C /var/lib/heihu-workorder -czf - report-images' \
    > "$UPLOAD_BACKUP"
fi

echo "Backup complete:"
echo "  $DB_BACKUP"
if [ "$UPLOAD_STORAGE_TYPE" != "s3" ]; then
  echo "  $UPLOAD_BACKUP"
fi

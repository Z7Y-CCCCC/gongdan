#!/usr/bin/env sh
set -eu

ENV_FILE="${ENV_FILE:-.env}"
DB_BACKUP_PATH="${DB_BACKUP_PATH:-}"
UPLOAD_BACKUP_PATH="${UPLOAD_BACKUP_PATH:-}"
CONFIRM_RESTORE="${CONFIRM_RESTORE:-no}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

if [ "$CONFIRM_RESTORE" != "yes" ]; then
  echo "Restore is destructive. Re-run with CONFIRM_RESTORE=yes after verifying backups." >&2
  echo "Example: CONFIRM_RESTORE=yes DB_BACKUP_PATH=backups/mysql_YYYYMMDD_HHMMSS.sql UPLOAD_BACKUP_PATH=backups/report-images_YYYYMMDD_HHMMSS.tar.gz sh ops/restore-compose.sh" >&2
  exit 1
fi

if [ -z "$DB_BACKUP_PATH" ] || [ ! -f "$DB_BACKUP_PATH" ]; then
  echo "Missing DB_BACKUP_PATH or file not found: $DB_BACKUP_PATH" >&2
  exit 1
fi

if [ -n "$UPLOAD_BACKUP_PATH" ] && [ ! -f "$UPLOAD_BACKUP_PATH" ]; then
  echo "UPLOAD_BACKUP_PATH file not found: $UPLOAD_BACKUP_PATH" >&2
  exit 1
fi

UPLOAD_STORAGE_TYPE="$(grep -E '^APP_UPLOAD_STORAGE_TYPE=' "$ENV_FILE" | tail -n 1 | sed 's/^APP_UPLOAD_STORAGE_TYPE=//' || true)"

echo "Stopping application services before restore"
docker compose --env-file "$ENV_FILE" stop frontend backend

echo "Restoring MySQL from $DB_BACKUP_PATH"
docker compose --env-file "$ENV_FILE" exec -T db sh -c \
  'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  < "$DB_BACKUP_PATH"

if [ "$UPLOAD_STORAGE_TYPE" = "s3" ]; then
  echo "APP_UPLOAD_STORAGE_TYPE=s3; skip local report image volume restore."
  echo "Restore the configured object storage bucket with provider tooling before opening traffic."
elif [ -n "$UPLOAD_BACKUP_PATH" ]; then
  echo "Restoring report images from $UPLOAD_BACKUP_PATH"
  docker compose --env-file "$ENV_FILE" run --rm --no-deps -T backend sh -c \
    'mkdir -p /var/lib/heihu-workorder/report-images && find /var/lib/heihu-workorder/report-images -mindepth 1 -delete'
  docker compose --env-file "$ENV_FILE" run --rm --no-deps -T backend sh -c \
    'tar -C /var/lib/heihu-workorder -xzf -' \
    < "$UPLOAD_BACKUP_PATH"
fi

echo "Starting application services"
docker compose --env-file "$ENV_FILE" up -d backend frontend

echo "Restore complete. Check health before opening traffic:"
echo "  docker compose --env-file $ENV_FILE ps"
echo "  sh ops/monitor-compose.sh"

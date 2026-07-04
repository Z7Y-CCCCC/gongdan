#!/usr/bin/env sh
set -eu

ENV_FILE="${ENV_FILE:-.env}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

require_var() {
  name="$1"
  value="$(grep -E "^${name}=" "$ENV_FILE" | tail -n 1 | sed "s/^${name}=//")"
  if [ -z "$value" ]; then
    echo "Missing required env: $name" >&2
    exit 1
  fi
}

reject_placeholder() {
  name="$1"
  value="$(grep -E "^${name}=" "$ENV_FILE" | tail -n 1 | sed "s/^${name}=//")"
  case "$value" in
    change-*|*example.com*|wx...|*your*|*Your*)
      echo "Placeholder value must be replaced: $name=$value" >&2
      exit 1
      ;;
  esac
}

for name in \
  MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD \
  APP_SEED_FACTORY_NAME APP_SEED_ADMIN_PASSWORD APP_SEED_SUPER_ADMIN_PASSWORD \
  APP_WECHAT_APP_ID APP_WECHAT_SECRET APP_SMS_HTTP_URL APP_CORS_ALLOWED_ORIGIN_PATTERNS
do
  require_var "$name"
  reject_placeholder "$name"
done

case "$(grep -E '^APP_CORS_ALLOWED_ORIGIN_PATTERNS=' "$ENV_FILE" | tail -n 1 | sed 's/^APP_CORS_ALLOWED_ORIGIN_PATTERNS=//')" in
  *'*'*)
    echo "Wildcard CORS is not allowed for production" >&2
    exit 1
    ;;
  http://*)
    echo "Production CORS origin should use HTTPS" >&2
    exit 1
    ;;
esac

storage_type="$(grep -E '^APP_UPLOAD_STORAGE_TYPE=' "$ENV_FILE" | tail -n 1 | sed 's/^APP_UPLOAD_STORAGE_TYPE=//' || true)"
if [ "$storage_type" = "s3" ]; then
  for name in APP_UPLOAD_S3_BUCKET APP_UPLOAD_S3_ACCESS_KEY APP_UPLOAD_S3_SECRET_KEY
  do
    require_var "$name"
    reject_placeholder "$name"
  done
fi

docker compose --env-file "$ENV_FILE" config >/dev/null

echo "Preflight passed for $ENV_FILE"

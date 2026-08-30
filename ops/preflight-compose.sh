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

require_var() {
  name="$1"
  value="$(config_value "$name" "")"
  if [ -z "$value" ]; then
    echo "Missing required env: $name" >&2
    exit 1
  fi
}

reject_placeholder() {
  name="$1"
  value="$(config_value "$name" "")"
  lower="$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')"
  case "$lower" in
    *change-*|*change_me*|*example.com*|wx...|*your*)
      echo "Placeholder value must be replaced: $name" >&2
      exit 1
      ;;
  esac
}

require_positive_integer() {
  name="$1"
  minimum="$2"
  value="$(config_value "$name" "")"
  case "$value" in
    ''|*[!0-9]*) echo "$name must be an integer" >&2; exit 1 ;;
  esac
  if [ "$value" -lt "$minimum" ]; then
    echo "$name must be at least $minimum" >&2
    exit 1
  fi
}

for name in \
  MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD \
  APP_SEED_FACTORY_NAME APP_SEED_ADMIN_PASSWORD APP_SEED_SUPER_ADMIN_PASSWORD \
  APP_WECHAT_APP_ID APP_WECHAT_SECRET APP_SMS_HTTP_URL APP_CORS_ALLOWED_ORIGIN_PATTERNS \
  APP_PLUGIN_GATEWAY_SECRET APP_PLUGIN_ALLOWED_BACKEND_HOSTS APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS \
  BACKUP_DIR BINLOG_ARCHIVE_DIR
do
  require_var "$name"
  reject_placeholder "$name"
done

if [ "$(config_value MYSQL_USER "")" = "root" ]; then
  echo "MYSQL_USER must not be root in production" >&2
  exit 1
fi
if [ "$(config_value APP_SEED_ADMIN_PASSWORD "")" = "$(config_value APP_SEED_SUPER_ADMIN_PASSWORD "")" ]; then
  echo "Factory admin and super admin passwords must differ" >&2
  exit 1
fi

case "$(config_value APP_CORS_ALLOWED_ORIGIN_PATTERNS "")" in
  *'*'*) echo "Wildcard CORS is not allowed for production" >&2; exit 1 ;;
  http://*) echo "Production CORS origin must use HTTPS" >&2; exit 1 ;;
esac

case "$(config_value APP_PLUGIN_ALLOWED_BACKEND_HOSTS "")" in
  *'*'*|*://*|*/*) echo "Plugin backend allow-list must contain host names only" >&2; exit 1 ;;
esac
case "$(config_value APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS "")" in
  *'*'*) echo "Wildcard plugin frontend origins are not allowed" >&2; exit 1 ;;
  http://*) echo "Production plugin frontend origins must use HTTPS" >&2; exit 1 ;;
esac

storage_type="$(config_value APP_UPLOAD_STORAGE_TYPE local)"
case "$storage_type" in
  local) ;;
  s3)
    for name in APP_UPLOAD_S3_BUCKET APP_UPLOAD_S3_ACCESS_KEY APP_UPLOAD_S3_SECRET_KEY; do
      require_var "$name"
      reject_placeholder "$name"
    done
    ;;
  *) echo "APP_UPLOAD_STORAGE_TYPE must be local or s3" >&2; exit 1 ;;
esac

require_positive_integer BACKUP_RETENTION_DAYS 7
require_positive_integer BACKUP_MAX_AGE_HOURS 1
require_positive_integer BACKUP_MIN_FREE_GB 1
require_positive_integer BINLOG_ARCHIVE_RETENTION_DAYS 7
require_positive_integer BINLOG_ARCHIVE_MAX_AGE_MINUTES 1
require_positive_integer MYSQL_BINLOG_EXPIRE_LOGS_SECONDS 604800
require_positive_integer BACKUP_REMOTE_MIN_RETENTION_DAYS 1
require_positive_integer APP_PLUGIN_PROXY_MAX_REQUEST_BYTES 1024

backup_dir="$(config_value BACKUP_DIR "")"
secondary_dir="$(config_value BACKUP_SECONDARY_DIR "")"
remote_uri="$(config_value BACKUP_REMOTE_URI "")"
require_safe_directory "$backup_dir" BACKUP_DIR
if [ -n "$secondary_dir" ]; then
  require_safe_directory "$secondary_dir" BACKUP_SECONDARY_DIR
  if [ "$secondary_dir" = "$backup_dir" ]; then
    echo "BACKUP_SECONDARY_DIR must differ from BACKUP_DIR" >&2
    exit 1
  fi
fi
if is_true "$(config_value BACKUP_REQUIRE_OFFSITE true)" && [ -z "$secondary_dir" ] && [ -z "$remote_uri" ]; then
  echo "Production requires BACKUP_SECONDARY_DIR or BACKUP_REMOTE_URI" >&2
  exit 1
fi

if is_true "$(config_value BACKUP_REQUIRE_IMMUTABLE_REMOTE true)" && [ -z "$remote_uri" ]; then
  echo "BACKUP_REMOTE_URI is required for the immutable production copy" >&2
  exit 1
fi

if [ -n "$remote_uri" ]; then
  reject_placeholder BACKUP_REMOTE_URI
  remote_bucket="$(remote_bucket_name "$remote_uri")"
  if [ "$storage_type" = "s3" ] && [ "$remote_bucket" = "$(config_value APP_UPLOAD_S3_BUCKET "")" ]; then
    echo "Backup bucket must differ from the live attachment bucket" >&2
    exit 1
  fi
  require_command aws
  if ! is_true "$(config_value BACKUP_REQUIRE_IMMUTABLE_REMOTE true)"; then
    echo "BACKUP_REQUIRE_IMMUTABLE_REMOTE must be true for production" >&2
    exit 1
  fi
  case "$(config_value BACKUP_REMOTE_REQUIRED_LOCK_MODE COMPLIANCE | tr '[:lower:]' '[:upper:]')" in
    COMPLIANCE|GOVERNANCE) ;;
    *) echo "BACKUP_REMOTE_REQUIRED_LOCK_MODE must be COMPLIANCE or GOVERNANCE" >&2; exit 1 ;;
  esac
  if is_true "$(config_value BACKUP_VERIFY_REMOTE_POLICY true)"; then
    verify_remote_policy
  fi
fi

case "$(uname -s 2>/dev/null || echo unknown)" in
  MINGW*|MSYS*|CYGWIN*) ;;
  *)
    if mode="$(stat -c %a "$ENV_FILE" 2>/dev/null)"; then
      case "$mode" in
        400|440|600|640) ;;
        *) echo "Environment file permissions must be 600/640 or stricter, current=$mode file=$ENV_FILE" >&2; exit 1 ;;
      esac
    fi
    ;;
esac

require_command docker
require_command curl
if ! command -v sha256sum >/dev/null 2>&1 && ! command -v shasum >/dev/null 2>&1; then
  echo "A SHA-256 command is required" >&2
  exit 1
fi

docker compose --env-file "$ENV_FILE" config >/dev/null
echo "Preflight passed for $ENV_FILE"

#!/usr/bin/env sh

# Shared helpers for Compose maintenance scripts. Callers must set ENV_FILE first.

env_file_value() {
  name="$1"
  default_value="${2:-}"
  line=""
  if [ -f "$ENV_FILE" ]; then
    line="$(grep -E "^${name}=" "$ENV_FILE" | tail -n 1 | tr -d '\r' || true)"
  fi
  if [ -z "$line" ]; then
    printf '%s' "$default_value"
    return
  fi

  value="${line#*=}"
  case "$value" in
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
  esac
  printf '%s' "$value"
}

config_value() {
  name="$1"
  default_value="${2:-}"
  if value="$(printenv "$name" 2>/dev/null)"; then
    printf '%s' "$value"
  else
    env_file_value "$name" "$default_value"
  fi
}

is_true() {
  case "$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')" in
    1|true|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    return 1
  }
}

require_safe_directory() {
  path="$1"
  label="$2"
  if [ -z "$path" ] || [ "$path" = "/" ]; then
    echo "Unsafe $label: $path" >&2
    return 1
  fi
}

write_checksum() {
  file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "$(dirname "$file")" && sha256sum "$(basename "$file")" > "$(basename "$file").sha256")
  elif command -v shasum >/dev/null 2>&1; then
    (cd "$(dirname "$file")" && shasum -a 256 "$(basename "$file")" > "$(basename "$file").sha256")
  else
    echo "No SHA-256 utility found; cannot protect $file" >&2
    return 1
  fi
}

verify_checksum() {
  file="$1"
  sidecar="${file}.sha256"
  if [ ! -f "$sidecar" ]; then
    echo "Checksum file not found: $sidecar" >&2
    return 1
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "$(dirname "$file")" && sha256sum -c "$(basename "$sidecar")")
  elif command -v shasum >/dev/null 2>&1; then
    expected="$(awk '{print $1}' "$sidecar")"
    actual="$(shasum -a 256 "$file" | awk '{print $1}')"
    [ "$expected" = "$actual" ] || {
      echo "Checksum mismatch: $file" >&2
      return 1
    }
  else
    echo "No SHA-256 utility found; cannot verify $file" >&2
    return 1
  fi
}

atomic_copy_to_directory() {
  source_file="$1"
  destination_dir="$2"
  mkdir -p "$destination_dir"
  destination="$destination_dir/$(basename "$source_file")"
  temporary="${destination}.tmp.$$"
  if ! cp "$source_file" "$temporary"; then
    rm -f "$temporary"
    return 1
  fi
  if ! mv -f "$temporary" "$destination"; then
    rm -f "$temporary"
    return 1
  fi
}

configure_aws_environment() {
  access_key="$(config_value BACKUP_REMOTE_ACCESS_KEY "${AWS_ACCESS_KEY_ID:-}")"
  secret_key="$(config_value BACKUP_REMOTE_SECRET_KEY "${AWS_SECRET_ACCESS_KEY:-}")"
  session_token="$(config_value BACKUP_REMOTE_SESSION_TOKEN "${AWS_SESSION_TOKEN:-}")"
  region="$(config_value BACKUP_REMOTE_REGION "${AWS_DEFAULT_REGION:-us-east-1}")"

  if [ -n "$access_key" ]; then export AWS_ACCESS_KEY_ID="$access_key"; fi
  if [ -n "$secret_key" ]; then export AWS_SECRET_ACCESS_KEY="$secret_key"; fi
  if [ -n "$session_token" ]; then export AWS_SESSION_TOKEN="$session_token"; fi
  export AWS_DEFAULT_REGION="$region"
  export AWS_PAGER=""
}

run_backup_aws() {
  endpoint="$(config_value BACKUP_REMOTE_ENDPOINT_URL "")"
  configure_aws_environment
  if [ -n "$endpoint" ]; then
    aws --endpoint-url "$endpoint" "$@"
  else
    aws "$@"
  fi
}

remote_bucket_name() {
  remote_uri="$1"
  case "$remote_uri" in
    s3://*) ;;
    *) echo "BACKUP_REMOTE_URI must use s3://bucket/prefix syntax" >&2; return 1 ;;
  esac
  without_scheme="${remote_uri#s3://}"
  bucket="${without_scheme%%/*}"
  if [ -z "$bucket" ]; then
    echo "BACKUP_REMOTE_URI does not contain a bucket name" >&2
    return 1
  fi
  printf '%s' "$bucket"
}

verify_remote_policy() {
  remote_uri="$(config_value BACKUP_REMOTE_URI "")"
  [ -n "$remote_uri" ] || return 0
  require_command aws
  bucket="$(remote_bucket_name "$remote_uri")"

  versioning="$(run_backup_aws s3api get-bucket-versioning --bucket "$bucket" --query Status --output text)"
  if [ "$versioning" != "Enabled" ]; then
    echo "Backup bucket versioning is not enabled: $bucket" >&2
    return 1
  fi

  if is_true "$(config_value BACKUP_REQUIRE_IMMUTABLE_REMOTE true)"; then
    lock_configuration="$(run_backup_aws s3api get-object-lock-configuration --bucket "$bucket" --query 'ObjectLockConfiguration.[ObjectLockEnabled,Rule.DefaultRetention.Mode,Rule.DefaultRetention.Days,Rule.DefaultRetention.Years]' --output text)"
    set -- $lock_configuration
    lock_status="${1:-None}"
    lock_mode="${2:-None}"
    lock_days="${3:-None}"
    lock_years="${4:-None}"
    if [ "$lock_status" != "Enabled" ]; then
      echo "Backup bucket Object Lock is not enabled: $bucket" >&2
      return 1
    fi
    required_mode="$(config_value BACKUP_REMOTE_REQUIRED_LOCK_MODE COMPLIANCE | tr '[:lower:]' '[:upper:]')"
    if [ "$lock_mode" != "$required_mode" ]; then
      echo "Backup bucket default Object Lock mode must be $required_mode: bucket=$bucket actual=$lock_mode" >&2
      return 1
    fi
    case "$lock_days" in ''|None) lock_days=0 ;; *[!0-9]*) echo "Unexpected Object Lock retention days: $lock_days" >&2; return 1 ;; esac
    case "$lock_years" in ''|None) lock_years=0 ;; *[!0-9]*) echo "Unexpected Object Lock retention years: $lock_years" >&2; return 1 ;; esac
    effective_lock_days=$((lock_days + lock_years * 365))
    minimum_lock_days="$(config_value BACKUP_REMOTE_MIN_RETENTION_DAYS 35)"
    case "$minimum_lock_days" in ''|*[!0-9]*) echo "BACKUP_REMOTE_MIN_RETENTION_DAYS must be an integer" >&2; return 1 ;; esac
    if [ "$effective_lock_days" -lt "$minimum_lock_days" ]; then
      echo "Backup bucket default Object Lock retention is too short: bucket=$bucket actualDays=$effective_lock_days minimumDays=$minimum_lock_days" >&2
      return 1
    fi
  fi
}

remote_upload_file() {
  source_file="$1"
  remote_relative_path="$2"
  remote_uri="$(config_value BACKUP_REMOTE_URI "")"
  [ -n "$remote_uri" ] || return 0

  destination="${remote_uri%/}/${remote_relative_path}"
  sse="$(config_value BACKUP_REMOTE_SSE "")"
  kms_key_id="$(config_value BACKUP_REMOTE_KMS_KEY_ID "")"
  set -- s3 cp "$source_file" "$destination" --only-show-errors
  if [ -n "$sse" ]; then set -- "$@" --sse "$sse"; fi
  if [ -n "$kms_key_id" ]; then set -- "$@" --sse-kms-key-id "$kms_key_id"; fi
  run_backup_aws "$@"
}

latest_file() {
  directory="$1"
  pattern="$2"
  find "$directory" -maxdepth 1 -type f -name "$pattern" -printf '%T@ %p\n' 2>/dev/null \
    | sort -nr \
    | sed -n '1{s/^[^ ]* //;p;}'
}

file_epoch() {
  file="$1"
  if stat -c %Y "$file" >/dev/null 2>&1; then
    stat -c %Y "$file"
  else
    stat -f %m "$file"
  fi
}

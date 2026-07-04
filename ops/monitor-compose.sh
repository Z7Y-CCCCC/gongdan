#!/usr/bin/env sh
set -eu

ENV_FILE="${ENV_FILE:-.env}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-8}"
ALERT_WEBHOOK_URL="${ALERT_WEBHOOK_URL:-}"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:${FRONTEND_PORT:-8080}/}"
BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://127.0.0.1:${FRONTEND_PORT:-8080}/api/actuator/health/readiness}"

failures=""

alert() {
  message="$1"
  if [ -n "$ALERT_WEBHOOK_URL" ]; then
    curl -fsS -m "$TIMEOUT_SECONDS" \
      -H "Content-Type: application/json" \
      -d "{\"text\":\"$message\"}" \
      "$ALERT_WEBHOOK_URL" >/dev/null || true
  fi
}

check_url() {
  name="$1"
  url="$2"
  if curl -fsS -m "$TIMEOUT_SECONDS" "$url" >/dev/null; then
    echo "OK $name $url"
  else
    echo "FAIL $name $url" >&2
    failures="${failures}${name} "
  fi
}

check_url "frontend" "$FRONTEND_URL"
check_url "backend-readiness" "$BACKEND_HEALTH_URL"

if [ -n "$failures" ]; then
  alert "gongdan monitor failed: $failures"
  exit 1
fi

echo "Monitor passed"

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
if [ -z "$(config_value BACKUP_REMOTE_URI "")" ]; then
  echo "BACKUP_REMOTE_URI is not configured" >&2
  exit 1
fi

verify_remote_policy
echo "Remote backup policy passed: $(config_value BACKUP_REMOTE_URI "")"

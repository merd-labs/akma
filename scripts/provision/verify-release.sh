#!/usr/bin/env bash
set -euo pipefail
script_directory=$(CDPATH='' cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
java_command=java
if [[ -n ${JAVA_HOME:-} ]]; then java_command="$JAVA_HOME/bin/java"; fi
if ! command -v "$java_command" >/dev/null 2>&1; then
  printf '%s\n' 'FAIL: JDK 17 not found' >&2
  exit 1
fi
exec "$java_command" --source 17 "$script_directory/VerifyReleaseArtifact.java" "$@"

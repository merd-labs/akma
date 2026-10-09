#!/usr/bin/env bash
# Runs the Akma merged-manifest security guard on a built variant.
# Usage: scripts/security/check-merged-manifest.sh [variant] [--release]
#   variant defaults to "debug". Build first, e.g. ./gradlew :app:processDebugManifest
# Exit: 0 pass, 1 policy violation, 2 usage/input error.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
variant=debug
extra=()
for arg in "$@"; do
  case "$arg" in
    --release) extra+=(--release) ;;
    -*) echo "unknown option: $arg" >&2; exit 2 ;;
    *) variant=$arg ;;
  esac
done
cap="$(printf '%s' "${variant:0:1}" | tr '[:lower:]' '[:upper:]')${variant:1}"
manifest="$root/app/build/intermediates/merged_manifests/$variant/process${cap}Manifest/AndroidManifest.xml"
if [ ! -f "$manifest" ]; then
  manifest=$(find "$root/app/build/intermediates/merged_manifests/$variant" -name AndroidManifest.xml 2>/dev/null | head -n 1 || true)
fi
if [ -z "${manifest:-}" ] || [ ! -f "$manifest" ]; then
  echo "Merged manifest for variant '$variant' not found. Run: ./gradlew :app:process${cap}Manifest" >&2
  exit 2
fi
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then java_bin="$JAVA_HOME/bin/java"; else java_bin=java; fi
exec "$java_bin" "$here/ManifestGuard.java" --policy "$here/manifest-policy.txt" "${extra[@]+"${extra[@]}"}" "$manifest"

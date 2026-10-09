#!/usr/bin/env bash
set -u
printf 'MERD Akma Ubuntu setup audit — read-only\n'
for cmd in git gh java javac adb sdkmanager python3 uv specify codex claude gemini gradle; do
  printf '%-12s ' "$cmd"
  if command -v "$cmd" >/dev/null 2>&1; then command -v "$cmd"; else echo MISSING; fi
done
printf '\nVersions (errors acceptable when tool is absent):\n'
for cmd in 'git --version' 'gh --version' 'java -version' 'adb version' 'python3 --version'; do
  echo "== $cmd =="; bash -lc "$cmd" 2>&1 | head -n 3 || true
done
printf '\nANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "${ANDROID_HOME:-<unset>}" "${ANDROID_SDK_ROOT:-<unset>}"
printf '\nADB devices (no app data read):\n'
if command -v adb >/dev/null; then adb devices -l; fi
printf '\nNo packages installed or configuration changed.\n'

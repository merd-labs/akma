#!/usr/bin/env bash
# Local verification only. No download, ADB operations, or path disclosure.
set -euo pipefail
if [[ $# == 1 && ( $1 == --help || $1 == -h ) ]]; then
  printf '%s\n' 'Usage: verify-artifact.sh FILE EXPECTED_BYTES EXPECTED_SHA256' 'Use trusted owner metadata. Output contains status only; never enable shell tracing.'
  exit 0
fi
if [[ $# != 3 ]]; then
  printf '%s\n' 'FAIL: expected file, byte size and SHA-256 arguments' >&2
  exit 2
fi
artifact=$1
expected_bytes=$2
expected_sha=$3
if [[ ! $expected_bytes =~ ^[1-9][0-9]*$ || ! $expected_sha =~ ^[a-f0-9]{64}$ ]]; then
  printf '%s\n' 'FAIL: invalid trusted metadata' >&2
  exit 2
fi
if [[ ! -f $artifact || ! -r $artifact ]]; then
  printf '%s\n' 'FAIL: missing or unreadable artifact' >&2
  exit 1
fi
actual_bytes=$(stat -c %s -- "$artifact" 2>/dev/null) || { printf '%s\n' 'FAIL: size read' >&2; exit 1; }
if [[ $actual_bytes != "$expected_bytes" ]]; then
  printf '%s\n' 'FAIL: size mismatch' >&2
  exit 1
fi
signature=$(head -c 8 -- "$artifact" 2>/dev/null) || { printf '%s\n' 'FAIL: header read' >&2; exit 1; }
if [[ $signature != LITERTLM ]]; then
  printf '%s\n' 'FAIL: LiteRT-LM container signature mismatch' >&2
  exit 1
fi
digest=$(sha256sum 2>/dev/null < "$artifact") || { printf '%s\n' 'FAIL: hash read' >&2; exit 1; }
actual_sha=${digest:0:64}
if [[ $actual_sha != "$expected_sha" ]]; then
  printf '%s\n' 'FAIL: SHA-256 mismatch' >&2
  exit 1
fi
printf '%s\n' 'PASS: local size, container signature and SHA-256 verified; native inference NOT TESTED'

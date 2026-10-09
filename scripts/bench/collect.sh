#!/usr/bin/env bash
# Read-only, selected-device snapshots. Never retain raw ADB output.
set -eu
umask 077

help() {
    cat <<'EOF'
Usage: bash collect.sh --serial SERIAL --device pova2|zero5g|camon30
  --phase LABEL --output NEW_FILE --slot-confirmed [--package PACKAGE]

Read-only snapshot for a physical Android device. SERIAL is never saved.
Default package: ph.merd.akma. Phase: 1-32 letters, digits, underscores or hyphens.
--slot-confirmed attests Elijah handed this device exclusively to you.
Output: UTF-8 TSV; existing files are never overwritten. Parent must exist.
Requires adb and GNU timeout (Linux/WSL). Each ADB call has a 10-second limit.
No launch, benchmark, model load, settings change, clipboard read or log capture.
Do not use this collector during another operator's benchmark or Gradle build.
EOF
}
fail() { printf '%s\n' "$1" >&2; exit 1; }
SERIAL='' DEVICE='' PHASE='' OUTPUT='' PACKAGE='ph.merd.akma' SLOT=false
while (($#)); do
    case "$1" in
        --help|-h) help; exit 0 ;;
        --slot-confirmed) SLOT=true; shift ;;
        --serial|--device|--phase|--output|--package)
            (($# >= 2)) || fail 'Missing option value.'
            case "$1" in
                --serial) SERIAL=$2 ;; --device) DEVICE=$2 ;; --phase) PHASE=$2 ;;
                --output) OUTPUT=$2 ;; --package) PACKAGE=$2 ;;
            esac
            shift 2 ;;
        *) fail 'Unknown option. Use --help.' ;;
    esac
done
[[ $SERIAL =~ ^[A-Za-z0-9_.:-]+$ ]] || fail 'Valid explicit serial required.'
case "$DEVICE" in pova2|zero5g|camon30) ;; *) fail 'Supported device alias required.' ;; esac
[[ $PHASE =~ ^[A-Za-z0-9_-]{1,32}$ ]] || fail 'Valid phase label required.'
[[ $PACKAGE =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]] || fail 'Invalid package.'
[[ -n $OUTPUT && ! -e $OUTPUT && ! -L $OUTPUT && -d $(dirname -- "$OUTPUT") ]] || fail 'New output file in existing directory required.'
$SLOT || fail 'Exclusive device handoff required; pass --slot-confirmed after agreement.'
[[ $SERIAL != emulator-* ]] || fail 'Emulator rejected; physical device required.'
command -v adb >/dev/null || fail 'adb unavailable.'
command -v timeout >/dev/null || fail 'GNU timeout unavailable.'

RAW='' STATUS=''
query() {
    local code=0
    RAW=$(timeout --kill-after=1s 10s adb -s "$SERIAL" "$@" 2>/dev/null) || code=$?
    RAW=${RAW//$'\r'/}
    case $code in
        0) STATUS=ok ;; 124|137) STATUS=timeout; RAW='' ;; *) STATUS=query_failed; RAW='' ;;
    esac
}
query get-state
[[ $STATUS == ok && $RAW == device ]] || fail 'Selected device unavailable or unauthorized.'
query shell getprop ro.kernel.qemu
[[ $STATUS == ok && ( $RAW == '' || $RAW == 0 ) ]] || fail 'Physical device verification failed.'
query shell getprop ro.boot.qemu
[[ $STATUS == ok && ( $RAW == '' || $RAW == 0 ) ]] || fail 'Physical device verification failed.'

TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)
ROWS=''
row() {
    local metric=$1 value=$2 unit=$3 status=$STATUS
    [[ -n $value ]] || { [[ $status != ok ]] || status=unavailable; value=''; }
    ROWS+=$(printf '%s\t%s\t%s\t%s\t%s\t%s\t%s' "$TIMESTAMP" "$DEVICE" "$PHASE" "$metric" "$value" "$unit" "$status")$'\n'
}
integer_row() {
    local value=$2
    [[ $value =~ ^[0-9]+$ ]] || value=''
    row "$1" "$value" "$3"
}
query shell getprop ro.build.version.sdk; integer_row android_api "$RAW" api
query shell getprop ro.build.version.release
[[ $RAW =~ ^[0-9]+(\.[0-9]+)*$ ]] || RAW=''
row android_release "$RAW" version
query shell getprop ro.product.cpu.abi
case "$RAW" in arm64-v8a|armeabi-v7a|armeabi|x86|x86_64|riscv64) ;; *) RAW='' ;; esac
row abi "$RAW" name
query shell getprop ro.product.model
# Model names are useful, but arbitrary property content must not enter evidence.
RAW=${RAW//"$SERIAL"/[REDACTED]}
MODEL_PATTERN='^[A-Za-z0-9][][A-Za-z0-9 ._()-]{0,79}$'
[[ $RAW =~ $MODEL_PATTERN ]] || RAW=''
row model "$RAW" name
query shell cat /proc/meminfo
integer_row mem_total "$(awk '$1 == "MemTotal:" && $3 == "kB" {print $2; exit}' <<< "$RAW")" KiB
integer_row mem_available "$(awk '$1 == "MemAvailable:" && $3 == "kB" {print $2; exit}' <<< "$RAW")" KiB
query shell df -k /data
integer_row data_available "$(awk '$NF == "/data" {print $(NF-2); exit}' <<< "$RAW")" KiB
query shell dumpsys battery
integer_row battery_temperature "$(awk '$1 == "temperature:" {print $2; exit}' <<< "$RAW")" deciC
query shell dumpsys thermalservice
integer_row thermal_status "$(awk '$1 == "Thermal" && $2 == "Status:" {print $3; exit}' <<< "$RAW")" android_status
query shell dumpsys meminfo "$PACKAGE"
PSS=$(awk '/TOTAL PSS:/ {for (i=1;i<=NF-2;i++) if ($i == "TOTAL" && $(i+1) == "PSS:") {print $(i+2); found=1; exit}} $1 == "TOTAL" && $2 ~ /^[0-9]+$/ {fallback=$2} END {if (!found && fallback != "") print fallback}' <<< "$RAW")
# Prefer the TOTAL PSS summary; older Android versions only have the table row.
PSS=${PSS%%$'\n'*}
integer_row app_total_pss "$PSS" KiB

# noclobber also protects against a file appearing after argument validation.
set -o noclobber
if ! { exec 3> "$OUTPUT"; } 2>/dev/null; then fail 'Cannot create new output file.'; fi
printf 'timestamp_utc\tdevice_alias\tphase\tmetric\tvalue\tunit\tstatus\n%s' "$ROWS" >&3
exec 3>&-
printf 'Snapshot saved. Review evidence before sharing.\n'

#!/usr/bin/env bash
# Read-only; never print the serial, subscription ID, raw service dump or identifiers.
set -euo pipefail
if [[ $# == 1 && ( $1 == --help || $1 == -h ) ]]; then
  printf '%s\n' 'Usage: check-offline.sh SERIAL' 'Read-only airplane/Wi-Fi/default-subscription data check. Keep selector private; never enable tracing.'
  exit 0
fi
if [[ $# != 1 || -z $1 ]]; then printf '%s\n' 'FAIL: explicit private serial required'; exit 2; fi
device_selector=$1
query() { adb -s "$device_selector" "$@" 2>/dev/null || printf '%s' 'UNKNOWN'; }
if [[ $(query get-state) != device ]]; then printf '%s\n' 'NOT TESTED: authorized device unavailable'; exit 1; fi
airplane=$(query shell settings get global airplane_mode_on)
wifi=$(query shell settings get global wifi_on)
legacy_data=$(query shell settings get global mobile_data)
subscription=$(query shell settings get global multi_sim_data_call)
data=$legacy_data
data_source=legacy_global
if [[ $subscription =~ ^[0-9]{1,10}$ ]]; then
  data=$(query shell settings get global "mobile_data$subscription")
  data_source=default_subscription
fi
service=$(query shell dumpsys wifi)
wifi_disabled=false
while IFS= read -r line; do
  line=${line%$'\r'}
  if [[ $line =~ ^[[:space:]]*Wi-Fi\ is\ disabled[[:space:]]*$ ]]; then wifi_disabled=true; break; fi
done <<< "$service"
# Print only allowlisted states. Unexpected values must not become log content.
case $airplane in 0|1) ;; *) airplane=UNKNOWN;; esac
case $wifi in 0|1|2|3) ;; *) wifi=UNKNOWN;; esac
case $data in 0|1) ;; *) data=UNKNOWN;; esac
printf '%s\n' "airplane=$airplane" "wifi_setting=$wifi" "wifi_service_disabled=$wifi_disabled" "mobile_data=$data" "mobile_data_source=$data_source"
if [[ $airplane == 1 && ( $wifi == 0 || $wifi == 3 ) && $wifi_disabled == true && $data == 0 ]]; then
  printf '%s\n' 'PASS: airplane enabled, Wi-Fi disabled, mobile data disabled; inference NOT TESTED'
else
  printf '%s\n' 'FAIL: offline radio state is not fully verified'
  exit 1
fi

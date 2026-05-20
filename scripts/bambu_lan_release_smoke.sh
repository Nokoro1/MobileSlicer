#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <bambu-lan-smoke-evidence.json>" >&2
  exit 64
fi

EVIDENCE_PATH="$1"
command -v jq >/dev/null || { echo "jq is required" >&2; exit 69; }
[[ -f "$EVIDENCE_PATH" ]] || { echo "evidence file not found: $EVIDENCE_PATH" >&2; exit 66; }

required_cases=(
  connect
  upload_only
  upload_and_start
  wrong_access_code
  offline_printer
)

for case_name in "${required_cases[@]}"; do
  status="$(jq -r --arg case "$case_name" '.cases[$case].status // empty' "$EVIDENCE_PATH")"
  [[ "$status" == "pass" ]] || {
    echo "Bambu LAN smoke case '$case_name' is not pass: ${status:-missing}" >&2
    exit 1
  }
done

printer_model="$(jq -r '.printer.model // empty' "$EVIDENCE_PATH")"
printer_serial_hash="$(jq -r '.printer.serial_hash // empty' "$EVIDENCE_PATH")"
app_version="$(jq -r '.app.versionName // empty' "$EVIDENCE_PATH")"
app_version_code="$(jq -r '.app.versionCode // empty' "$EVIDENCE_PATH")"
apk_sha256="$(jq -r '.app.apk_sha256 // empty' "$EVIDENCE_PATH")"
operator="$(jq -r '.operator // empty' "$EVIDENCE_PATH")"
completed_at="$(jq -r '.completed_at // empty' "$EVIDENCE_PATH")"

[[ -n "$printer_model" ]] || { echo "printer.model is required" >&2; exit 1; }
[[ -n "$printer_serial_hash" ]] || { echo "printer.serial_hash is required; record a hash, not the raw serial" >&2; exit 1; }
[[ -n "$app_version" ]] || { echo "app.versionName is required" >&2; exit 1; }
[[ -n "$app_version_code" ]] || { echo "app.versionCode is required" >&2; exit 1; }
[[ -n "$apk_sha256" ]] || { echo "app.apk_sha256 is required" >&2; exit 1; }
[[ -n "$operator" ]] || { echo "operator is required" >&2; exit 1; }
[[ -n "$completed_at" ]] || { echo "completed_at is required" >&2; exit 1; }

echo "Bambu LAN smoke evidence verified: $printer_model app=$app_version/$app_version_code apk=$apk_sha256"

#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="${PACKAGE_NAME:-com.mobileslicer}"
OUTPUT_ROOT="${1:-scanner-audits}"

mkdir -p "${OUTPUT_ROOT}"

latest_workspace="$(
  adb shell "run-as ${PACKAGE_NAME} sh -c 'ls -td cache/scanner-workspaces/scan_* 2>/dev/null | head -n 1'" \
    | tr -d '\r'
)"

if [[ -z "${latest_workspace}" ]]; then
  echo "No scanner workspace found for ${PACKAGE_NAME}." >&2
  exit 1
fi

scan_id="$(basename "${latest_workspace}")"
output_dir="${OUTPUT_ROOT}/${scan_id}"
archive_name="${scan_id}.tar"

rm -rf "${output_dir}"
mkdir -p "${output_dir}"

adb shell "run-as ${PACKAGE_NAME} sh -c 'cd cache/scanner-workspaces && tar -cf /data/data/${PACKAGE_NAME}/cache/${archive_name} ${scan_id}'"
adb exec-out "run-as ${PACKAGE_NAME} cat cache/${archive_name}" > "${output_dir}/${archive_name}"
tar -xf "${output_dir}/${archive_name}" -C "${output_dir}"
adb shell "run-as ${PACKAGE_NAME} rm -f cache/${archive_name}" >/dev/null

echo "Pulled scanner workspace: ${output_dir}/${scan_id}"
echo "Summary: ${output_dir}/${scan_id}/reconstruction_summary.json"
echo "Audit: ${output_dir}/${scan_id}/reconstruction_audit.json"

#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="${PACKAGE_NAME:-com.mobileslicer}"
ANDROID_DIR="${ANDROID_DIR:-android-app}"
OUTPUT_ROOT="${1:-scanner-audits}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required." >&2
  exit 1
fi

if [[ ! -d "${ANDROID_DIR}" ]]; then
  echo "Android project directory not found: ${ANDROID_DIR}" >&2
  exit 1
fi

echo "Running scanner JVM tests and building debug APK..."
(
  cd "${ANDROID_DIR}"
  ./gradlew :app:testDebugUnitTest --tests 'com.mobileslicer.scanner.*' :app:assembleDebug
)

echo "Installing debug APK..."
adb install -r "${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"

echo "Clearing logcat and launching ${PACKAGE_NAME}..."
adb logcat -c
adb shell monkey -p "${PACKAGE_NAME}" 1 >/dev/null

cat <<EOF

Manual camera step still required:
  1. Open scanner.
  2. Tap object.
  3. Capture until complete.
  4. Export.
  5. Run local reconstruction checks.

Press Enter here after reconstruction finishes.
EOF
read -r _

mkdir -p "${OUTPUT_ROOT}"
log_file="${OUTPUT_ROOT}/scanner_device_smoke_$(date +%Y%m%d_%H%M%S).log"
adb logcat -d > "${log_file}"

latest_scan_id="$(
  rg 'MobileSlicerScanner: (export_done|pipeline_done)' "${log_file}" \
    | sed -E 's/.*scanId=([^ ]+).*/\1/' \
    | tail -n 1
)"

if [[ -z "${latest_scan_id}" ]]; then
  echo "No scanner export/pipeline completion found in logs."
  echo "Full log: ${log_file}"
  exit 1
fi

echo
echo "Latest scan: ${latest_scan_id}"
echo "Full log: ${log_file}"
echo
echo "Capture counts:"
rg "capture_analyzed scanId=${latest_scan_id}" "${log_file}" \
  | awk '
      {
        pass="unknown"; accepted="unknown"; retained="unknown";
        for (i=1;i<=NF;i++) {
          if ($i ~ /^pass=/) pass=substr($i,6);
          if ($i ~ /^accepted=/) accepted=substr($i,10);
          if ($i ~ /^retained=/) retained=substr($i,10);
        }
        count[pass ":accepted=" accepted ":retained=" retained]++;
      }
      END {
        for (key in count) print "  " key, count[key];
      }
    ' \
  | sort || true

echo
echo "Export/pipeline summary:"
rg "MobileSlicerScanner: (export_done|pipeline_done).*scanId=${latest_scan_id}" "${log_file}" || true

if adb shell "run-as ${PACKAGE_NAME} test -d cache/scanner-workspaces/${latest_scan_id}" >/dev/null 2>&1; then
  echo
  echo "Pulling latest scanner audit..."
  scripts/scanner_pull_latest_audit.sh "${OUTPUT_ROOT}"
else
  echo
  echo "No workspace found for ${latest_scan_id}; reconstruction may not have started."
fi

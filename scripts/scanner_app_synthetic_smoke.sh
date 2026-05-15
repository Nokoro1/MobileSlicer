#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="${PACKAGE_NAME:-com.mobileslicer}"
ANDROID_DIR="${ANDROID_DIR:-android-app}"
OUTPUT_ROOT="${1:-scanner-audits/app-synthetic}"
STATUS_PATH="${STATUS_PATH:-files/automation/scanner-status.json}"
SUMMARY_PATH="${SUMMARY_PATH:-files/automation/scanner-summary.json}"
ACTION="com.mobileslicer.action.AUTOMATE_SCANNER_SYNTHETIC"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required." >&2
  exit 1
fi

if [[ ! -d "${ANDROID_DIR}" ]]; then
  echo "Android project directory not found: ${ANDROID_DIR}" >&2
  exit 1
fi

mkdir -p "${OUTPUT_ROOT}"

echo "Running scanner JVM regression and building debug APK..."
(
  cd "${ANDROID_DIR}"
  ./gradlew \
    :app:testDebugUnitTest \
    --tests 'com.mobileslicer.scanner.ScannerSyntheticCaptureTest' \
    --tests 'com.mobileslicer.scanner.ScannerSyntheticMetricPoseFixtureTest' \
    --tests 'com.mobileslicer.scanner.ScannerExportPipelineHandoffTest' \
    --tests 'com.mobileslicer.scanner.ScannerCapturePlanTest' \
    :app:assembleDebug
)

echo "Installing debug APK..."
adb install -r "${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"

run_scanner_automation() {
  local fixture_kind="$1"
  local scan_id="scan_app_${fixture_kind}_$(date +%Y%m%d_%H%M%S)"
  local status_path="files/automation/scanner-${fixture_kind}-status.json"
  local summary_path="files/automation/scanner-${fixture_kind}-summary.json"

  echo
  echo "Launching scanner app automation (${fixture_kind}): ${scan_id}"
  adb shell "run-as ${PACKAGE_NAME} rm -f '${status_path}' '${summary_path}'" >/dev/null 2>&1 || true
  adb logcat -c
  adb shell am force-stop "${PACKAGE_NAME}" >/dev/null 2>&1 || true
  adb shell am start -W \
    -n "${PACKAGE_NAME}/.MainActivity" \
    -a "${ACTION}" \
    --es scanner_automation_scan_id "${scan_id}" \
    --es scanner_automation_fixture_kind "${fixture_kind}" \
    --es scanner_automation_status_path "${status_path}" \
    --es scanner_automation_summary_path "${summary_path}" >/dev/null

  local status_json=""
  for _ in $(seq 1 120); do
    if adb shell "run-as ${PACKAGE_NAME} test -f '${status_path}'" >/dev/null 2>&1; then
      status_json="$(adb exec-out run-as "${PACKAGE_NAME}" cat "${status_path}" | tr -d '\r')"
      status="$(printf '%s' "${status_json}" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("status", ""))' 2>/dev/null || true)"
      if [[ "${status}" == "passed" || "${status}" == "failed" ]]; then
        break
      fi
    fi
    sleep 1
  done

  if [[ -z "${status_json}" ]]; then
    local log_file="${OUTPUT_ROOT}/${scan_id}_logcat.txt"
    adb logcat -d -v time > "${log_file}" 2>&1 || true
    echo "Scanner app automation did not write status: ${status_path}" >&2
    echo "Logcat: ${log_file}" >&2
    exit 1
  fi

  local status_file="${OUTPUT_ROOT}/${scan_id}_status.json"
  local summary_file="${OUTPUT_ROOT}/${scan_id}_summary.json"
  local log_file="${OUTPUT_ROOT}/${scan_id}_logcat.txt"

  printf '%s\n' "${status_json}" > "${status_file}"
  adb exec-out run-as "${PACKAGE_NAME}" cat "${summary_path}" > "${summary_file}" 2>/dev/null || true
  adb logcat -d -v time > "${log_file}" 2>&1 || true

  python3 - "${status_file}" "${fixture_kind}" <<'PY'
import json
import sys

path = sys.argv[1]
expected_fixture = sys.argv[2]
with open(path, "r", encoding="utf-8") as handle:
    status = json.load(handle)

details = status.get("details", {})
errors = []
if status.get("status") != "passed":
    errors.append(f"status={status.get('status')}")
if status.get("mode") != "synthetic_app_scanner_smoke":
    errors.append(f"mode={status.get('mode')}")
if status.get("fixture_kind") != expected_fixture:
    errors.append(f"fixture_kind={status.get('fixture_kind')}")
if details.get("fixture_kind") != expected_fixture:
    errors.append(f"details.fixture_kind={details.get('fixture_kind')}")
if not details.get("automation_success"):
    errors.append("automation_success=false")

if expected_fixture == "capture_package":
    if details.get("accepted_frame_count") != 44:
        errors.append(f"accepted_frame_count={details.get('accepted_frame_count')}")
    if not details.get("package_valid"):
        errors.append("package_valid=false")
    if not details.get("pipeline_completed"):
        errors.append("pipeline_completed=false")
    if any("package_validation_failed" in str(item) for item in details.get("blocking_errors", [])):
        errors.append("package_validation_failed present")
    if not str(details.get("reconstruction_summary", "")).endswith("reconstruction_summary.json"):
        errors.append("missing reconstruction_summary path")
    if not str(details.get("reconstruction_audit", "")).endswith("reconstruction_audit.json"):
        errors.append("missing reconstruction_audit path")

if expected_fixture == "metric_reconstruction":
    required_true = [
        "optimizer_allowed",
        "optimizer_metric",
        "sparse_metric",
        "dense_admitted",
        "debug_point_cloud_ready",
        "surface_ready",
    ]
    for key in required_true:
        if not details.get(key):
            errors.append(f"{key}=false")
    if details.get("sparse_measured_point_count", 0) < 48:
        errors.append(f"sparse_measured_point_count={details.get('sparse_measured_point_count')}")
    forbidden_prefixes = (
        "feature_match_graph:",
        "feature_tracks:",
        "pose_initialization:",
        "pose_refinement:",
        "metric_pose_graph:",
        "metric_pose_solve:",
        "metric_pose_optimizer:",
        "sparse_triangulation:",
        "dense_reconstruction:",
        "dense_point_cloud:",
        "surface_reconstruction:",
    )
    for item in details.get("blocking_errors", []):
        if str(item).startswith(forbidden_prefixes):
            errors.append(f"unexpected blocker={item}")
            break

if errors:
    raise SystemExit("Scanner app synthetic smoke failed: " + ", ".join(errors))

print("Scanner app synthetic status validated.")
print("Scan:", status.get("scan_id"))
print("Fixture:", expected_fixture)
print("Workspace:", details.get("workspace_dir"))
print("Blocking gates:", ", ".join(details.get("blocking_errors", [])))
PY

  if [[ "${fixture_kind}" == "capture_package" ]]; then
    echo
    echo "Pulling scanner audit workspace..."
    scripts/scanner_pull_latest_audit.sh "${OUTPUT_ROOT}" || true
  fi

  echo "Status: ${status_file}"
  echo "Summary: ${summary_file}"
  echo "Logcat: ${log_file}"
}

run_scanner_automation "capture_package"
run_scanner_automation "metric_reconstruction"

echo
echo "Scanner app synthetic smoke passed."

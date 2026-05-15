#!/usr/bin/env bash
set -euo pipefail

ANDROID_DIR="${ANDROID_DIR:-android-app}"

if [[ ! -d "${ANDROID_DIR}" ]]; then
  echo "Android project directory not found: ${ANDROID_DIR}" >&2
  exit 1
fi

(
  cd "${ANDROID_DIR}"
  ./gradlew \
    :app:testDebugUnitTest \
    --tests 'com.mobileslicer.scanner.ScannerSyntheticCaptureTest' \
    --tests 'com.mobileslicer.scanner.ScannerExportPipelineHandoffTest' \
    --tests 'com.mobileslicer.scanner.ScannerCapturePlanTest'
)

echo
echo "Synthetic scanner regression passed."
echo "This covers scanner capture/package/reconstruction handoff without a physical camera scan."

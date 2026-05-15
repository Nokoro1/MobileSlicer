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
    --tests 'com.mobileslicer.scanner.ScannerRealDeviceReplayTest' \
    --tests 'com.mobileslicer.scanner.ScannerFeatureTracksTest'
)

echo
echo "Real-device scanner replay passed."
echo "This replays the pulled phone scan through feature tracks, pose scaffolding,"
echo "sparse input gates, and metric pose diagnostics without requiring a rescan."

#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 6 || $# -gt 7 ]]; then
  echo "Usage: $0 <repo> <tag> <asset-name> <expected-sha256> <version-name> <version-code> [expected-commit]" >&2
  exit 64
fi

REPO="$1"
TAG="$2"
ASSET_NAME="$3"
EXPECTED_SHA256="$4"
VERSION_NAME="$5"
VERSION_CODE="$6"
EXPECTED_COMMIT="${7:-}"
ANDROID_HOME="${ANDROID_HOME:-$(pwd)/.android-sdk}"
AAPT="${AAPT:-$ANDROID_HOME/build-tools/35.0.0/aapt}"

command -v gh >/dev/null || { echo "gh is required" >&2; exit 69; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 69; }
[[ -x "$AAPT" ]] || { echo "aapt not found or not executable: $AAPT" >&2; exit 69; }

release_json="$(gh release view "$TAG" --repo "$REPO" --json tagName,targetCommitish,isDraft,isPrerelease,assets)"
actual_tag="$(jq -r '.tagName' <<<"$release_json")"
is_draft="$(jq -r '.isDraft' <<<"$release_json")"
is_prerelease="$(jq -r '.isPrerelease' <<<"$release_json")"
asset_digest="$(jq -r --arg name "$ASSET_NAME" '.assets[] | select(.name == $name) | .digest' <<<"$release_json")"

[[ "$actual_tag" == "$TAG" ]] || { echo "release tag mismatch: $actual_tag" >&2; exit 1; }
[[ "$is_draft" == "false" ]] || { echo "release is still draft" >&2; exit 1; }
[[ "$is_prerelease" == "true" ]] || { echo "release is not marked prerelease" >&2; exit 1; }
[[ "$asset_digest" == "sha256:$EXPECTED_SHA256" ]] || {
  echo "asset digest mismatch: $asset_digest" >&2
  exit 1
}
if [[ -n "$EXPECTED_COMMIT" ]]; then
  actual_commit="$(git ls-remote "https://github.com/$REPO.git" "refs/tags/$TAG^{}" | awk '{print $1}')"
  if [[ -z "$actual_commit" ]]; then
    actual_commit="$(git ls-remote "https://github.com/$REPO.git" "refs/tags/$TAG" | awk '{print $1}')"
  fi
  [[ "$actual_commit" == "$EXPECTED_COMMIT" ]] || {
    echo "release tag commit mismatch: $actual_commit" >&2
    exit 1
  }
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
gh release download "$TAG" --repo "$REPO" --pattern "$ASSET_NAME" --dir "$tmp_dir" --clobber
apk="$tmp_dir/$ASSET_NAME"
actual_sha="$(sha256sum "$apk" | awk '{print $1}')"
[[ "$actual_sha" == "$EXPECTED_SHA256" ]] || { echo "downloaded APK SHA mismatch: $actual_sha" >&2; exit 1; }

badging="$("$AAPT" dump badging "$apk")"
grep -q "versionCode='$VERSION_CODE'" <<<"$badging" || { echo "APK versionCode mismatch" >&2; exit 1; }
grep -q "versionName='$VERSION_NAME'" <<<"$badging" || { echo "APK versionName mismatch" >&2; exit 1; }

echo "GitHub release asset verified: $TAG $ASSET_NAME $EXPECTED_SHA256"

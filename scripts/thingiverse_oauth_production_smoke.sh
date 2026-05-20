#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${MOBILE_SLICER_THINGIVERSE_BASE_URL:-https://mobileslicer.com}"
CLIENT_ID="${MOBILE_SLICER_THINGIVERSE_CLIENT_ID:-}"
REDIRECT_URI="${MOBILE_SLICER_THINGIVERSE_REDIRECT_URI:-mobileslicer://thingiverse-auth}"

[[ -n "$CLIENT_ID" ]] || {
  echo "MOBILE_SLICER_THINGIVERSE_CLIENT_ID is required for production OAuth smoke." >&2
  exit 64
}
command -v curl >/dev/null || { echo "curl is required" >&2; exit 69; }

base="${BASE_URL%/}"
encoded_redirect="$(
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$REDIRECT_URI"
)"

start_url="$base/v1/thingiverse/oauth/start?client_id=$CLIENT_ID&redirect_uri=$encoded_redirect"
start_headers="$(mktemp)"
callback_headers="$(mktemp)"
redeem_body="$(mktemp)"
redeem_headers="$(mktemp)"
trap 'rm -f "$start_headers" "$callback_headers" "$redeem_body" "$redeem_headers"' EXIT

start_status="$(curl -sS -o /dev/null -D "$start_headers" -w "%{http_code}" "$start_url")"
start_location="$(awk 'BEGIN{IGNORECASE=1} /^location:/ {sub(/\r$/, ""); print substr($0, index($0,$2)); exit}' "$start_headers")"
[[ "$start_status" =~ ^30[12378]$ ]] || {
  echo "OAuth start route returned HTTP $start_status, expected redirect" >&2
  exit 1
}
[[ "$start_location" == https://www.thingiverse.com/* ]] || {
  echo "OAuth start redirect did not target Thingiverse: $start_location" >&2
  exit 1
}

callback_status="$(curl -sS -o /dev/null -D "$callback_headers" -w "%{http_code}" "$base/v1/thingiverse/oauth/callback")"
[[ "$callback_status" != "404" ]] || {
  echo "OAuth callback route returned 404" >&2
  exit 1
}
[[ "$callback_status" != "500" ]] || {
  echo "OAuth callback route returned 500 for missing parameters" >&2
  exit 1
}

redeem_status="$(
  curl -sS \
    -o "$redeem_body" \
    -D "$redeem_headers" \
    -w "%{http_code}" \
    -H "content-type: application/json" \
    --data '{"code":"mobileslicer-production-smoke-invalid-code"}' \
    "$base/v1/thingiverse/oauth/redeem"
)"
[[ "$redeem_status" != "404" ]] || {
  echo "OAuth redeem route returned 404" >&2
  exit 1
}
[[ "$redeem_status" != "500" ]] || {
  echo "OAuth redeem route returned 500 for invalid smoke code" >&2
  exit 1
}

echo "Thingiverse production OAuth routes verified at $base"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEBSITE_DIR="$ROOT_DIR/Website"
ANDROID_DIR="$ROOT_DIR/android-app"

fail() {
  printf '[verify_thingiverse_release_ready] ERROR: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '[verify_thingiverse_release_ready] %s\n' "$*"
}

require_file() {
  local path="$1"
  [[ -f "$ROOT_DIR/$path" ]] || fail "Missing required file: $path"
}

require_env_or_local_property() {
  local env_name="$1"
  local property_name="$2"
  local local_properties="$ANDROID_DIR/local.properties"
  if [[ -n "${!env_name:-}" ]]; then
    return 0
  fi
  if [[ -f "$local_properties" ]] && grep -Eq "^${property_name}=" "$local_properties"; then
    return 0
  fi
  fail "Set $env_name or $property_name in android-app/local.properties for release-style OAuth testing."
}

require_toml_value() {
  local name="$1"
  local pattern="$2"
  if ! grep -Eq "$pattern" "$WEBSITE_DIR/wrangler.toml"; then
    fail "Website/wrangler.toml is missing required Thingiverse OAuth config: $name"
  fi
}

require_file "Website/functions/v1/thingiverse/oauth/start.js"
require_file "Website/functions/v1/thingiverse/oauth/callback.js"
require_file "Website/functions/v1/thingiverse/oauth/redeem.js"
require_file "Website/lib/thingiverse-oauth.mjs"
require_file "Website/test/thingiverse-oauth.test.mjs"
require_file "docs/modelsearch/THINGIVERSE_DIRECT_IMPORT_PLAN.md"
require_file "docs/modelsearch/IMPLEMENTATION.md"

log "Running backend OAuth tests"
(cd "$ROOT_DIR" && node --test Website/test/thingiverse-oauth.test.mjs)

log "Checking Android release-style OAuth config"
require_env_or_local_property "THINGIVERSE_CLIENT_ID" "thingiverse.clientId"
require_env_or_local_property "THINGIVERSE_AUTH_BACKEND_URL" "thingiverse.authBackendUrl"

log "Checking Website OAuth deployment config"
require_toml_value "THINGIVERSE_CLIENT_ID" '^THINGIVERSE_CLIENT_ID = ".+"$'
require_toml_value "THINGIVERSE_ALLOWED_APP_REDIRECTS" '^THINGIVERSE_ALLOWED_APP_REDIRECTS = "mobileslicer://thingiverse-auth"$'
require_toml_value "THINGIVERSE_OAUTH_RATE_LIMIT_KV binding" '^binding = "THINGIVERSE_OAUTH_RATE_LIMIT_KV"$'
require_toml_value "THINGIVERSE_OAUTH_RATE_LIMIT_KV id" '^id = "[a-f0-9]{32}"$'

log "Checking Android model-search contract tests"
(cd "$ANDROID_DIR" && ./gradlew :app:testDebugUnitTest --tests com.mobileslicer.modelsearch.ModelSearchContractsTest)

if command -v wrangler >/dev/null 2>&1; then
  log "Found wrangler: $(wrangler --version)"
elif command -v npx >/dev/null 2>&1; then
  log "wrangler is not installed globally; npx is available. Use: npx wrangler@latest pages deploy Website --project-name mobileslicer --branch main"
else
  fail "wrangler is not installed and npx is unavailable."
fi

cat <<'NEXT_STEPS'
[verify_thingiverse_release_ready] Account-backed release checks:
  1. Thingiverse developer app callback must be:
     https://mobileslicer.com/v1/thingiverse/oauth/callback
  2. Cloudflare Pages production secrets must include:
     THINGIVERSE_CLIENT_SECRET
     THINGIVERSE_OAUTH_STATE_SECRET
  3. Cloudflare Pages production config must bind:
     THINGIVERSE_OAUTH_RATE_LIMIT_KV
  4. Public start route should redirect with valid app params:
     https://mobileslicer.com/v1/thingiverse/oauth/start
  5. Test sign-in and import on a physical Android device.
NEXT_STEPS

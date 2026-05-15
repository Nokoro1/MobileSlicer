# Thingiverse Direct Import Plan

Date: 2026-05-08

This document is the tracked implementation guide for adding Thingiverse API
search and direct import to MobileSlicer. It complements the broader local plan
at `README/plans/MODEL_SEARCH_IMPORT_PLAN.md`.

## Goal

Add a native Thingiverse path that lets a user search Thingiverse, inspect a
model, choose a printable file, and import it directly into MobileSlicer's
existing local workspace importer.

The integration must not turn MobileSlicer into a Thingiverse mirror. Files are
user-selected local imports, not MobileSlicer-hosted catalog content.

## Current Source Status

| Source | Current app behavior | Direct import status |
| --- | --- | --- |
| Thingiverse | Native API search/import panel | Implemented for debug app-token and release OAuth paths |
| Printables | Opens website in Custom Tab | Partner/API permission required |
| MakerWorld | Opens website in Custom Tab | Partner/API permission required |

## Official Thingiverse Requirements Found

References checked on 2026-05-08:

* <https://www.thingiverse.com/developers>
* <https://www.thingiverse.com/developers/getting-started>
* <https://www.thingiverse.com/legal/api>
* <https://www.thingiverse.com/developers/faq>
* <https://www.thingiverse.com/developers/swagger>

Requirements and constraints to design around:

* API requests use `https://api.thingiverse.com`.
* API access requires authentication.
* Authentication uses OAuth2 bearer tokens.
* Apps must be registered.
* Mobile Android/iOS apps are a supported app type.
* Private/unapproved apps are limited to 10 users.
* Rate limiting is 300 requests per 5 minute window.
* OAuth tokens should be sent as `Authorization: Bearer`.
* Basic authentication is unavailable.
* Non-web application flow is not documented as supported, so expect a browser
  authorization flow.
* The app receives a Client ID and Client Secret.
* Client Secret must not be shared or shipped in the Android APK.
* Apps must not sell or commercialize Thingiverse user content without
  permission from both parties.
* Apps must not redistribute Thingiverse content on other sites or platforms.
* Apps must handle usability based on Thing licenses.
* API data must not be supplemented with scraped site data.

## Approval Posture

Register MobileSlicer with a narrow app description:

> MobileSlicer is a local Android 3D slicer. The Thingiverse integration lets
> users search Thingiverse, view model details, open the original Thingiverse
> page, and import user-selected printable files into their local slicing
> workspace. MobileSlicer does not rehost, redistribute, resell, or mirror
> Thingiverse content.

Screenshots submitted for approval should show:

* Thingiverse search
* result rows with creator/source context
* model detail with original page link
* user-selected file import
* no platform logo unless permitted
* no partnership language unless permitted

## Architecture Decision

Do not ship a private Thingiverse Client Secret in Android.

Use the backend code exchange path for release:

1. Android launches the MobileSlicer backend OAuth start URL.
2. Backend starts Thingiverse browser authorization.
3. Thingiverse redirects to the backend callback with an auth code.
4. Backend exchanges that code using the Client Secret.
5. Backend stores the user-scoped token behind a short-lived one-time handoff
   code in KV.
6. Backend redirects back to `mobileslicer://thingiverse-auth` with the
   original `state` and only the one-time handoff code.
7. Android validates the `state`, redeems the handoff code over HTTPS, stores
   the returned token with Android Keystore-backed encryption, and uses it for
   Thingiverse API calls.
8. Backend rate-limits OAuth start/callback/redeem requests and any future proxied
   endpoints.

The public-token path can be revisited only if Thingiverse explicitly allows it
for native apps. The app-token debug path must remain development-only.

## Implemented Backend

The release OAuth bridge is implemented as Cloudflare Pages Functions:

```text
Website/functions/v1/thingiverse/oauth/start.js
Website/functions/v1/thingiverse/oauth/callback.js
Website/functions/v1/thingiverse/oauth/redeem.js
Website/lib/thingiverse-oauth.mjs
Website/test/thingiverse-oauth.test.mjs
```

Thingiverse developer app callback URL:

```text
https://mobileslicer.com/v1/thingiverse/oauth/callback
```

Android release configuration:

```properties
thingiverse.clientId=...
thingiverse.authBackendUrl=https://mobileslicer.com
```

Cloudflare Pages configuration:

```bash
wrangler pages secret put THINGIVERSE_CLIENT_SECRET --project-name mobileslicer
wrangler pages secret put THINGIVERSE_OAUTH_STATE_SECRET --project-name mobileslicer
wrangler kv namespace create THINGIVERSE_OAUTH_RATE_LIMIT_KV
```

Put the public `THINGIVERSE_CLIENT_ID` and the exact app redirect allowlist in
`Website/wrangler.toml`. Bind the created KV namespace to the Pages project as
`THINGIVERSE_OAUTH_RATE_LIMIT_KV`.

```toml
THINGIVERSE_CLIENT_ID = "..."
THINGIVERSE_ALLOWED_APP_REDIRECTS = "mobileslicer://thingiverse-auth"
```

Runtime behavior:

* `/start` validates MobileSlicer's Client ID, exact app callback URI, and the
  app-provided state.
* `/start` creates a short-lived signed OAuth state, stores the same state in a
  short-lived HTTP-only SameSite cookie, and redirects to Thingiverse's
  documented browser OAuth endpoint.
* `/callback` validates the signed state, exchanges the code with Thingiverse
  using the server-side Client Secret, validates the returned access token with
  `https://api.thingiverse.com/users/me`, stores the token behind a short-lived
  one-time handoff code, and redirects back to `mobileslicer://thingiverse-auth`
  with only `state` and `handoff_code`.
* `/redeem` validates and consumes the one-time handoff code, then returns the
  token over HTTPS to the Android app. The code cannot be reused.
* The backend does not persist Thingiverse access tokens beyond the short-lived
  handoff TTL. It does not persist search queries, thumbnails, model metadata,
  or model files.
* OAuth start/callback/redeem requests are fixed-window rate-limited by a hashed
  client identity in Cloudflare KV.

Backend rules:

* keep Client Secret server-side
* rate-limit by user/device/IP as appropriate
* do not persist Thingiverse model files
* do not create a MobileSlicer-hosted model catalog
* log only operational metadata needed to debug/rate-limit
* avoid storing search queries longer than necessary
* do not cache thumbnails unless Thingiverse terms explicitly allow it

Implemented endpoints:

```text
GET /v1/thingiverse/oauth/start?client_id={clientId}&redirect_uri={appCallback}&state={state}
GET /v1/thingiverse/oauth/callback?code={code}&state={signedBackendState}
GET /v1/thingiverse/oauth/redeem?code={oneTimeHandoffCode}
```

Possible future backend proxy endpoints, only if they become necessary for
approval, rate limiting, or API key handling:

```text
GET /v1/thingiverse/search?q={query}&page={page}
GET /v1/thingiverse/things/{thingId}
GET /v1/thingiverse/things/{thingId}/files
GET /v1/thingiverse/files/{fileId}/download
```

The current Android implementation expects the OAuth start endpoint to complete
by redirecting to:

```text
mobileslicer://thingiverse-auth?state={state}&handoff_code={oneTimeHandoffCode}
```

Android rejects callbacks with the wrong state. It does not accept raw
Thingiverse access tokens from the custom-scheme callback in the release flow.
The token is retrieved only by redeeming the one-time handoff code against the
configured HTTPS backend.

## API Spike Checklist

Before coding UI:

1. Create/register a Thingiverse developer app.
2. Record app type, callback URL, Client ID handling, and secret handling.
3. Verify OAuth flow in debug.
4. Verify `/users/me` or equivalent authenticated sanity call.
5. Verify search endpoint and pagination.
6. Verify thing detail endpoint.
7. Verify files-list endpoint.
8. Verify whether a documented direct download URL or endpoint exists.
9. Record response fields used by MobileSlicer.
10. Record caching and thumbnail rules.
11. Record rate-limit behavior and headers.
12. Record error states for 400, 401, 403, 404, and rate limit.

Do not implement native direct import until item 8 is answered.

## UI Plan

The native Thingiverse UI should be compact and work-focused.

Screen structure:

* top bar: `Find and import model`
* segmented source control:
  * `Thingiverse`
  * `Web`
  * `Local`
* Thingiverse search field
* result list

Result row:

* model title
* creator name
* license badge if API provides it
* file count if API provides it
* `Open page`

Model detail:

* title
* creator
* license if API provides it
* original Thingiverse page action
* supported file list
* each supported file row has `Import`

Import behavior:

* no review screen
* user taps `Import`
* file is fetched through the approved API path
* imported file is handed to the existing MobileSlicer importer
* failure returns to model detail with a clear error

Do not use:

* Thingiverse logo unless branding permission allows it
* `official`, `powered by`, or `partnered with` copy unless approval allows it
* marketplace-style checkout, cart, or collection UI
* large marketing cards

## Implementation Phases

T0: Registration, backend, and endpoint spike

Status: implemented for the app-token debug path and Cloudflare Pages release
OAuth bridge.

Acceptance:

* developer app registered
* app-token authenticated API requests work in debug
* endpoint list documented
* direct file download support confirmed through `/files/{file_id}/download`
* Client Secret remains out of Android source and committed config
* Android accepts `mobileslicer://thingiverse-auth` callbacks and rejects
  mismatched OAuth state
* backend exchanges code server-side and validates the returned user token
* backend custom-scheme callbacks carry only one-time handoff codes, not raw
  Thingiverse tokens
* backend OAuth requests are rate-limited through a Cloudflare KV binding

T1: Read-only authenticated search

Status: implemented behind debug token configuration and user OAuth token.

Acceptance:

* search screen calls documented API
* first page of results works
* results show source, title, creator, and license when present
* original Thingiverse website remains available from search results and file
  list headers through validated HTTPS Thingiverse URLs

T2: Model detail and file list

Status: implemented as a compact file-list panel rather than a separate detail
screen.

Acceptance:

* file list loads from documented endpoint
* unsupported files are hidden or disabled
* original Thingiverse page stays available while inspecting files
* original page stays available

T3: Direct import

Status: implemented for user-selected STL/3MF/STEP/STP files through the documented
tracked download endpoint.

Acceptance:

* user selects one supported file
* file import is user-initiated
* file is not persisted on backend
* file enters existing workspace importer
* auth/rate-limit/download errors are visible and recoverable
* 401 responses clear the local Thingiverse session and ask the user to sign in
  again
* direct download starts from `/files/{file_id}/download`, ignores untrusted
  API-provided arbitrary download URLs, sends bearer auth only to the API host,
  validates HTTPS redirect hosts, and enforces a 250 MB stream limit

T4: Approval and beta

Acceptance:

* private beta stays within Thingiverse's unapproved-app user limit
* screenshots and data-flow summary are ready
* privacy policy updates are prepared if backend/auth/search logging exists
* approval request is submitted

## Test Plan

Unit tests:

* OAuth redirect parsing
* backend signed-state validation
* backend redirect allowlist validation
* backend token exchange response parsing
* token audience validation if token flow is used
* search response parsing
* thing detail response parsing
* file list parsing
* unsupported file filtering
* rate-limit response mapping
* auth-expired response mapping

Integration/manual tests:

* sign in through browser
* search common query
* open original page
* import one STL
* import one 3MF
* cancel download/import
* expired token recovery
* rate-limited request recovery
* no review screen appears
* imported model enters workspace like local `Open model`

## Open Questions

Resolved for current implementation:

* File download endpoint: `/files/{file_id}/download`.
* Native secret handling: backend code exchange with Cloudflare secrets.
* Mobile callback: `mobileslicer://thingiverse-auth`.
* Thingiverse developer callback: `https://mobileslicer.com/v1/thingiverse/oauth/callback`.

Remaining before public approval:

* Does Thingiverse require download accounting or a browser handoff for files?
* Are thumbnail URLs allowed in native result rows?
* How long may API metadata be cached, if at all?
* What exact branding language is permitted after app approval?

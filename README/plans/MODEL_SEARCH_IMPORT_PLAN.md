# Model Search And Import Plan

Date: 2026-05-08

This plan defines MobileSlicer's `Find and import model` feature. The current
objective is a clean, touch-first, local Android slicer. Model search is an
assisted path into the existing local model import flow, not a marketplace,
mirror, scraper, or backend-first product.

This is a product and engineering plan, not legal advice. Before enabling any
source API, direct download, thumbnails, logos, cached catalog data, cloud
workflow, or commercial workflow, re-check the current platform terms and record
the accepted behavior in a release-facing document.

## Current Decision

V1 has no model review screen.

Users who downloaded a file, created their own model, or open/share a model into
MobileSlicer should not have to fill out source, rights, license, or attribution
metadata before opening it locally.

The active V1 local/web-source behavior is:

1. User taps `Find and import model`.
2. MobileSlicer shows source choices.
3. User opens a source website in an Android Custom Tab.
4. User downloads a model on the source website.
5. User returns to MobileSlicer.
6. User taps `Import file`.
7. Android's file picker opens.
8. The selected file goes directly into the existing MobileSlicer importer.

Android `Open with MobileSlicer` and share-sheet entries also route directly to
the existing importer.

## Product Goal

The feature promise is:

MobileSlicer helps users find printable models and import user-selected files
into the local slicing workspace without pretending to own, mirror, or replace
the source platforms.

The home import card order is:

1. `Open model`
2. `Find and import model`
3. `Scan Object`

This keeps the hierarchy clear:

* `Open model` is the fastest path for files already on the device.
* `Find and import model` helps users search official APIs or visit source
  sites and return with a file.
* `Scan Object` remains a separate creation path.

## V1 Scope

Allowed in V1:

* show a native Thingiverse API search/import panel
* show source buttons for Printables and MakerWorld
* open external source websites in Android Custom Tabs
* fall back to a normal browser intent if Custom Tabs fail
* let the user import Thingiverse STL/3MF files through the official API
* let the user import all printable files from a multi-part Thingiverse model
* let the user long-press file rows, select a subset, and import selected
  printable files
* show API-provided Thingiverse preview images in search results
* show API-provided Thingiverse file render thumbnails in file lists
* let the user select a downloaded web-source model through Android's file picker
* accept supported files through Android share sheet
* accept supported files through `Open with MobileSlicer`
* keep the selected file in the existing local import pipeline
* use plain text source names

Not allowed in V1:

* scrape source websites
* use undocumented APIs
* bypass login, cookies, anti-bot systems, or download tracking
* silently scan Downloads
* request broad storage permissions only to watch downloads
* persist source-site thumbnails or cache public model files on a MobileSlicer backend
* rehost downloaded model files
* use platform logos without permission
* claim partnership, endorsement, or official status without written permission
* show a source/license/rights import review screen before local import

## Android Flow

The active state machine is intentionally small:

```kotlin
sealed interface FindImportState {
    data object SourcePicker : FindImportState

    data class ExternalSiteOpened(
        val sourceId: String,
        val openedAtEpochMs: Long
    ) : FindImportState

    data class AwaitingUserFile(
        val sourceId: String?,
        val openedAtEpochMs: Long?
    ) : FindImportState

    data class ImportBlocked(
        val reason: ImportFailureReason
    ) : FindImportState
}
```

Flow rules:

* `SourcePicker` is the normal entry state.
* `ExternalSiteOpened` is set before launching a Custom Tab.
* `AwaitingUserFile` is shown after the source site has been opened.
* File picker selection imports directly through the existing importer.
* Share sheet and `Open with MobileSlicer` imports directly through the same
  existing importer.
* Never assume a Custom Tab download completed.
* Never scan Downloads to guess which file the user downloaded.

MIME rules:

* Accept STL and 3MF when Android reports specific MIME types.
* Accept STL and 3MF when Android reports generic `application/octet-stream`.
* The manifest may use broad open/share filters because many Android file
  providers are generic. The existing importer remains responsible for actual
  parse success or user-facing import failure.

## Source Integration Modes

Source modes remain explicit so future behavior can be enabled per source
without scattering policy checks through UI code.

```kotlin
enum class SourceIntegrationMode {
    EXTERNAL_LINK_ONLY,
    OFFICIAL_API,
    PARTNER_API,
    USER_IMPORT_ONLY,
    DISABLED
}
```

Current source policy:

```kotlin
val sourcePolicies = listOf(
    ModelSourcePolicy(
        id = "printables",
        displayName = "Printables",
        mode = SourceIntegrationMode.EXTERNAL_LINK_ONLY,
        externalUrl = "https://www.printables.com",
        directDownloadAllowed = false,
        thumbnailCachingAllowed = false
    ),
    ModelSourcePolicy(
        id = "makerworld",
        displayName = "MakerWorld",
        mode = SourceIntegrationMode.EXTERNAL_LINK_ONLY,
        externalUrl = "https://makerworld.com",
        directDownloadAllowed = false,
        thumbnailCachingAllowed = false
    )
)
```

Thingiverse is no longer listed as a web-source row in the active UI. It has its
own API search/import panel.

## Direct Download Strategy

Direct download is the intended higher-value path. It should be implemented only
where the source platform allows it through an official API, partner API, or
written permission.

Direct download must mean:

* user-initiated import into MobileSlicer's local workspace
* no rehosting
* no backend mirror
* no redistribution on another public platform
* no scraping
* no bypassing source download gates or accounting
* no private API secret inside the Android APK

Source status:

| Source | Current mode | Direct-download path |
| --- | --- | --- |
| Thingiverse | Official API search/import panel | Official tracked download endpoint for user-selected STL/3MF files |
| Printables | External link | Partner/public API or written permission |
| MakerWorld | External link | Partner/public API or written permission |

## Thingiverse API Research

Thingiverse is the first direct-download candidate because it has an official
developer program and API documentation.

References checked on 2026-05-08:

* Thingiverse Developers: <https://www.thingiverse.com/developers>
* Thingiverse Getting Started:
  <https://www.thingiverse.com/developers/getting-started>
* Thingiverse API Terms: <https://www.thingiverse.com/legal/api>
* Thingiverse API FAQ: <https://www.thingiverse.com/developers/faq>
* Thingiverse Swagger/OpenAPI: <https://www.thingiverse.com/developers/swagger>

Known requirements from the documentation:

* API calls use `https://api.thingiverse.com`.
* API calls require authentication.
* Authentication uses OAuth2 bearer tokens.
* Apps must be registered with Thingiverse.
* Supported app types include mobile Android/iOS apps.
* Private/unapproved apps are limited to 10 users.
* Rate limiting is 300 requests per 5 minute window.
* Basic authentication is not available.
* OAuth tokens use the `Authorization: Bearer` header.
* Non-web application flow is not supported in the documented flow; users are
  directed through a browser.
* Each registered application receives a Client ID and Client Secret.
* The Client Secret must not be shared or sent to the browser. For MobileSlicer,
  it also must not be shipped in the APK.
* Apps must not sell or otherwise commercialize Thingiverse users' content
  without permission from both parties.
* Apps must not redistribute Thingiverse content on other sites or platforms.
* Apps must recognize and handle usability based on Thing licenses.
* Apps must not supplement API data with scraped Thingiverse site data.

## Thingiverse Approval Plan

Register the app with a narrow, accurate description:

> MobileSlicer is a local Android 3D slicer. The Thingiverse integration lets
> users search Thingiverse, view model details, open the original Thingiverse
> page, and import user-selected printable files into their local slicing
> workspace. MobileSlicer does not rehost, redistribute, resell, or mirror
> Thingiverse content.

Approval posture:

* present MobileSlicer as a local slicer, not a marketplace
* preserve the original Thingiverse page as the canonical source
* show Thingiverse creator/title/license when provided by the API
* use OAuth exactly as documented
* keep any private Client Secret server-side
* rate limit backend/API calls
* do not cache thumbnails or metadata unless the terms allow it
* do not store model files on the backend except transiently if technically
  required for a permitted stream
* do not use Thingiverse logos/marks unless branding terms allow it
* do not imply partnership unless approval explicitly permits that language

Pre-approval checklist:

1. Create a Thingiverse developer app.
2. Record Client ID handling.
3. Decide OAuth flow:
   * if using code exchange, perform it on a backend
   * if using token flow, validate the token audience against MobileSlicer's
     Client ID before trusting it
4. Confirm exact API endpoints for:
   * search
   * thing details
   * files list
   * file download or download URL
5. Confirm terms for:
   * thumbnails
   * file download
   * caching
   * attribution/source display
   * branding
   * rate limits
6. Implement a private beta within the 10-user unapproved limit.
7. Submit for review/approval with screenshots and a short data-flow summary.

## Thingiverse API Implementation Plan

Phase T0: Terms and endpoint spike

Status: implemented for app-token debug builds and the release OAuth backend.

* registered application exists
* app-token authenticated requests are supported
* search endpoint access proved through `/search/{term}/?type=things`
* file listing endpoint access proved through `/things/{thing_id}/files`
* direct file download endpoint identified as `/files/{file_id}/download`
* Cloudflare Pages OAuth bridge exists at
  `/v1/thingiverse/oauth/start`, `/v1/thingiverse/oauth/callback`, and
  `/v1/thingiverse/oauth/redeem`
* backend exchanges authorization codes with the Client Secret server-side
* backend validates returned Thingiverse tokens with `/users/me`
* backend redirects only one-time handoff codes through the app callback
* Android redeems handoff codes over HTTPS and stores returned tokens with
  Android Keystore-backed encryption
* backend OAuth requests are rate-limited through a Cloudflare KV binding
* exact endpoints and response fields are documented in
  `docs/modelsearch/THINGIVERSE_DIRECT_IMPORT_PLAN.md`

Phase T1: Read-only native search

Status: implemented as an embedded Thingiverse API panel.

* keep Printables and MakerWorld as website rows
* show search box
* show result rows with title, creator, source, and license if present
* do not cache thumbnails unless allowed

Phase T2: User-selected API import

Status: implemented as a compact file-list view.

* show source, creator, license, and file list
* expose `Import` on individual supported files
* expose `Import all` when a model has multiple supported printable files
* long-press file rows to enter selection mode and expose `Import selected`
* import into the existing MobileSlicer importer
* do not show a separate review screen
* store files only as local user imports
* start downloads from the documented Thingiverse API endpoint only
* validate HTTPS redirect hosts and cap downloaded model bytes
* never attach bearer auth to arbitrary API-provided URLs

Phase T3: Polish and approval

Status: implemented locally; pending broader private beta and Thingiverse review.

* deploy Cloudflare Pages OAuth bridge with secrets and KV binding
* add API error states
* add empty states
* add auth expired/reconnect state
* keep search input fixed-height and one-line while the Android keyboard is open
* sort printable files with 3MF before STL and show size/type metadata
* hide unsupported files from direct import while reporting how many were hidden
* show compact import progress instead of raw long filenames
* import multi-part queues sequentially and append after the first imported file
* start auto-arrange after a successful multi-file import
* preserve the selected Thingiverse file name through staging, plate labels,
  saved-project names, and suggested G-code export names
* add private beta testing under the unapproved-app user limit
* submit for Thingiverse approval

## Clean UI Direction

The source screen should feel like a quiet import tool, not a policy page.

Current V1 screen:

* title: `Find and import model`
* subtitle: `Browse libraries, then open the file in MobileSlicer.`
* section title: `Thingiverse`
* Thingiverse API panel:
  * compact search field
  * result rows with file count/license/creator when available
  * file list for supported STL/3MF files, sorted 3MF before STL
  * unsupported Thingiverse files hidden from direct import and counted in the
    file-list header
  * `Import all` for multi-file models
  * long-press selection mode with `Import selected`
  * `Import` button on each supported file
* collapsed `Other sources` section
* source rows:
  * Printables: `Website import path.`
  * MakerWorld: `Website import path.`
* row action: `Browse`
* bottom action: `Import file`

Future Thingiverse API UI:

* segmented source control: `Thingiverse`, `Web`, `Local`
* search field at top for Thingiverse
* compact result rows, not large marketing cards
* result row fields:
  * model title
  * creator
  * license badge if API provides it
  * file count if API provides it
* detail actions:
  * `Import file`
  * `Open page`
* no platform logo unless permitted
* no `official`, `powered by`, or `partnered with` language unless permitted

## Printables And MakerWorld

Printables and MakerWorld remain external-link-only because no clean official
public model API was identified during this research pass.

Do not:

* scrape their search pages
* call undocumented endpoints
* direct-download files through reverse-engineered URLs
* bypass login or memberships
* cache their thumbnails
* imply partnership

Future partner request should ask for:

* search API
* model detail API
* file list API
* direct file download API
* thumbnail permission
* license metadata fields
* creator attribution requirements
* rate limits
* commercial/exclusive model handling
* takedown process
* logo and trademark rules

## Backend Position

V1 local/web-source import does not require a backend.

Thingiverse release OAuth does require one because the Thingiverse Client Secret
must not ship in Android.

Implemented backend:

```text
Website/functions/v1/thingiverse/oauth/start.js
Website/functions/v1/thingiverse/oauth/callback.js
Website/functions/v1/thingiverse/oauth/redeem.js
Website/lib/thingiverse-oauth.mjs
```

Implemented endpoints:

```text
GET /v1/thingiverse/oauth/start?client_id={clientId}&redirect_uri=mobileslicer://thingiverse-auth&state={state}
GET /v1/thingiverse/oauth/callback
GET /v1/thingiverse/oauth/redeem?code={oneTimeHandoffCode}
```

Rules:

* do not put private secrets in Android
* store Thingiverse Client Secret only as a Cloudflare Pages secret
* use `https://mobileslicer.com/v1/thingiverse/oauth/callback` as the
  Thingiverse developer callback URL
* bind `THINGIVERSE_OAUTH_RATE_LIMIT_KV` before enabling the endpoint
* redirect only one-time handoff codes through the custom app callback, not raw
  Thingiverse access tokens
* encrypt stored Thingiverse tokens on Android
* do not mirror model files
* do not persist tokens beyond the short handoff window, search queries,
  thumbnails, model metadata, or model files in the OAuth bridge
* keep logs minimal and privacy-aware

Possible future backend proxy endpoints should be added only if needed for
approval, rate limiting, or API-key handling.

## Privacy And App Store Notes

If the app only opens websites and imports local user-selected files, the
privacy story stays simple.

If Thingiverse API or backend proxy is added, update:

* privacy policy
* Play Data Safety form
* in-app disclosure if login is required
* backend retention policy

Data categories to mention if applicable:

* source account authentication token handling
* model search queries
* selected model metadata
* downloaded/imported model files
* source URLs
* error logs

## Milestones

M1: V1 source browser and direct local import

Acceptance:

* `Find and import model` appears under `Open model`
* source rows open Custom Tabs
* `Open downloaded file` opens Android file picker
* selected file imports directly with no review screen
* Android share/open-with imports directly
* no scraping
* no direct source download
* no platform logos

M2: Thingiverse API spike

Acceptance:

* developer app registered
* OAuth flow documented
* endpoint list documented
* rate limit documented
* file download permission documented
* no private secret in APK

M3: Thingiverse read-only search

Acceptance:

* authenticated search works
* result rows show source and original page link
* no direct download yet
* no thumbnail caching unless approved
* private beta stays inside Thingiverse unapproved-app limits

M4: Thingiverse direct import

Acceptance:

* file list comes from documented API
* user selects a file
* file imports directly into existing workspace flow
* no backend mirror
* source page remains available
* API errors and auth expiry are handled

M5: Partner integrations

Acceptance:

* written permission or documented API exists
* download and metadata rules documented
* creator/license/commercial handling documented
* implementation avoids scraping

## Testing Plan

V1 tests:

* source registry defaults to external-link-only
* Thingiverse/Printables/MakerWorld source URLs normalize correctly
* Android generic MIME handling accepts likely STL/3MF files
* Android generic MIME handling rejects obvious non-model files where possible
* source state transitions to awaiting user file after source open
* source screen has no review flow
* local file picker import still works
* share-sheet import works
* open-with import works

Thingiverse API tests:

* OAuth redirect handling
* backend signed-state validation
* backend app callback allowlist validation
* backend one-time handoff redemption
* backend token exchange parsing
* token exchange or token validation
* token audience validation if implicit token flow is used
* expired token state
* rate-limit response state
* search result parsing
* paginated search result loading
* thing detail parsing
* file list parsing
* unsupported file filtering
* printable file display names, size labels, and 3MF-before-STL sort order
* multi-import progress state
* original page link presence
* direct import success
* multi-file direct import appends parts sequentially
* direct import failure cleanup

Backend verification:

```bash
node --test Website/test/thingiverse-oauth.test.mjs
scripts/verify_thingiverse_release_ready.sh
```

## Definition Of Done For Current V1

V1 is done when:

* users can open source sites from MobileSlicer
* users can return and open downloaded files directly
* users can open/share STL or 3MF files into MobileSlicer
* no model review screen appears
* no source scraping exists
* no direct download exists outside approved API/permission
* Thingiverse release OAuth keeps the private Client Secret out of the APK
* Thingiverse search, file selection, clear search, original page, and direct
  STL/3MF import work on device
* Thingiverse search does not duplicate search controls and exposes pagination
  with a clear load-more action
* Thingiverse multi-part `Import all` and `Import selected` work on device
* Thingiverse imports keep the imported STL/3MF file name instead of surfacing
  internal staging names such as `selected_model` or `selected-model-*`
* multi-part imported plates can be sliced without blank shell-thickness config
  failures
* slice preflight blocks generated brim/skirt footprints that would exceed the
  printable volume before calling the native slicer
* G-code preview seam markers are hidden by default to avoid seam-point artifacts
  appearing as broken mesh in preview
* the native slice config normalizes the legacy `seam_gap=10%` default to `0%`
  so imported circular/multi-part models do not preview with a visible vertical
  perimeter split
* implementation notes document the current scope
* tests and debug build pass

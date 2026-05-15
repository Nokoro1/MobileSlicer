# Model Search Implementation Notes

Date: 2026-05-08

This directory tracks implementation-facing notes for MobileSlicer's
`Find and import model` feature. The current product decision is to keep this
as a lightweight source browser and direct importer, without a model review
step.

## Current Implemented Scope

Implemented behavior:

* `Find and import model` appears under `Open model` on the home screen
* Thingiverse is the native direct-import source through the official API
* release-oriented Thingiverse sign-in exists: the app can open a
  backend OAuth start URL, receive a `mobileslicer://thingiverse-auth` callback,
  store the returned user token locally, and use that token for API search,
  file listing, and direct import
* debug builds can still use a local `thingiverse.appToken` for isolated API
  testing, but release builds rely on OAuth and do not ship a Thingiverse app
  token
* the Cloudflare Pages backend OAuth bridge exists under `Website/functions`:
  it starts Thingiverse's browser OAuth flow, exchanges authorization codes
  server-side, validates the returned user token with `/users/me`, redirects a
  one-time handoff code back to Android, and exposes a HTTPS redeem endpoint
  for that code
* Printables and MakerWorld open as external-link-only sources
* external web sources open in Android Custom Tabs with a normal browser intent
  fallback
* Thingiverse API panel can search documented `/search/{term}/?type=things`
  results
* Thingiverse result rows display API-provided preview images when available
* Thingiverse result rows use a compact thumbnail/title/creator/file action and
  keep the original Thingiverse page available from the file-selection view
* Thingiverse result rows can load documented `/things/{thing_id}/files`
* Thingiverse file rows display API-provided file render thumbnails when
  available
* Thingiverse file rows show only printable STL/3MF/STEP/STP candidates, sort
  3MF before STL before STEP/STP, show file type and size when available, and report how many unsupported
  files were hidden
* Thingiverse multi-part models can be imported with `Import all`; long-pressing
  a file row enters selection mode, where users can pick specific printable
  files and use `Import selected`
* Thingiverse file-list headers keep the original Thingiverse page available
  while users choose a file
* Thingiverse imports show compact progress, including `Importing N of M` for
  multi-file queues, instead of long raw filenames
* user-selected Thingiverse STL/3MF/STEP/STP files can import through documented
  `/files/{file_id}/download`
* Thingiverse direct downloads always start from the documented API download
  endpoint, attach bearer auth only to the API request, validate HTTPS redirect
  hosts, and enforce a 250 MB streaming limit
* MobileSlicer does not scrape source pages
* MobileSlicer does not direct-download models from source sites except the
  user-selected Thingiverse API path above
* MobileSlicer does not persist source-site thumbnails or third-party model
  files; Thingiverse previews are held in memory only for the current UI session
* after browsing a web source, the user selects the downloaded file manually
* selected files go straight into the existing MobileSlicer model importer
* Android `Open with MobileSlicer` and share-sheet files also import directly
* `singleTop` activity handling accepts new external file intents while the app
  is already running
* source names are plain text only; no platform logos or partnership language
* auto-orient and auto-arrange reject duplicate taps while native planning is
  running
* repeated plate-planning actions reuse the prepared native config when the
  plate, printer, filament, process, flush, and object signature is unchanged
* workspace mesh preparation schedules a native planning-model prewarm so the
  first user-tapped auto-orient or auto-arrange can reuse already parsed source
  models when prewarm finishes in time
* single-object auto-arrange without rotation and without a prime tower uses a
  deterministic center-on-bed fast path after verifying the transformed object
  fits the selected bed

## Boundaries

The implementation keeps source policy and import-flow contracts isolated under:

```text
android-app/app/src/main/java/com/mobileslicer/modelsearch/
android-app/app/src/test/java/com/mobileslicer/modelsearch/
```

The source browser is intentionally not a marketplace clone. Thingiverse is a
compact official-API importer; Printables and MakerWorld are assisted entry
points into the same local import path the app already uses for `Open model`.

Broad Android open/share filters are registered because many file managers and
downloads providers report STL, 3MF, STEP, and STP files as generic content. Those filters
only make MobileSlicer available as a target. Actual loading still happens in
the existing importer, which owns format parsing and user-facing import errors.

There is no import review screen in the active app flow. Users who own a model
or have just downloaded one do not need to re-enter source, creator, license, or
rights metadata before opening it locally.

Direct download is the intended higher-value path for sources that permit it.
For Thingiverse, MobileSlicer uses the official API to search, list files, and
import a user-selected STL, 3MF, STEP, or STP. Debug builds may use a local app token for
developer testing. The release path uses the Cloudflare Pages OAuth bridge
described in
`THINGIVERSE_DIRECT_IMPORT_PLAN.md`: Android opens
`/v1/thingiverse/oauth/start`, Thingiverse redirects to the backend callback,
the backend exchanges the code with the private Client Secret, validates the
user token, then redirects back to Android with only a one-time handoff code.
Android redeems that code over HTTPS and stores the returned token with
Android Keystore-backed encryption. The Client Secret belongs only in
Cloudflare secrets, never in the Android APK.
Printables and MakerWorld remain external-link-only until they provide written
permission or a partner/public API that allows search, metadata, and file
download from a third-party mobile app.

The detailed Thingiverse path is tracked in
`docs/modelsearch/THINGIVERSE_DIRECT_IMPORT_PLAN.md`.

## Thingiverse UI Contract

The Thingiverse panel should remain compact and task-focused:

* one primary Thingiverse panel, not a duplicate web-source row
* signed-in state shown as a compact pill with a separate `Sign out` action
* fixed-height one-line search field so the keyboard never causes label or
  placeholder clipping
* search helper text below the field, not inside the field
* result rows show thumbnail, model name, creator, license/file metadata, and a
  single `Files` action
* file-selection rows show thumbnail, cleaned file name, extension, size, and a
  single `Import` action
* unsupported files are hidden from the import list and counted in the file
  header
* when more than one printable file is present, the file view shows `Import all`
* long-pressing a file row enters selection mode; selected rows show checkboxes,
  the primary action changes to `Import selected`, and `Cancel` exits selection
* `Open original page`, `Results`, and `Clear search` stay available from the
  file-selection view
* the screen uses IME padding so the Android keyboard does not cover the search
  and import controls

## Multipart Thingiverse Import

Multi-file Thingiverse imports are intentionally sequential. The app downloads
and imports one selected STL/3MF/STEP/STP at a time through the same importer used by
single-file imports.

Rules:

* `Import all` includes only printable STL/3MF/STEP/STP files already visible in the file
  list
* `Import selected` imports only the selected printable files
* files are imported in the visible sorted order: 3MF first, then STL, then STEP/STP
* if the workspace already has objects, the first queued file appends to the
  active plate
* if the workspace is empty, the first queued file starts the plate and later
  queued files append
* if a later file fails, already imported files remain on the plate and the
  status reports how many completed
* after a successful multi-file import, MobileSlicer starts auto-arrange without
  rotation so the added parts are placed cleanly
* if Orca auto-arrange places imported parts on multiple beds, the app prompts
  before mutating the workspace: users can keep everything on the current plate
  for manual cleanup, or apply Orca's returned multi-plate layout

## Local Thingiverse Token Setup

For debug API testing, provide a Thingiverse app token outside Git:

```properties
# android-app/local.properties
thingiverse.appToken=...
```

or:

```bash
THINGIVERSE_APP_TOKEN=... ./gradlew :app:assembleDebug
```

`android-app/local.properties` is ignored by Git. The Client Secret must not be
stored in Android source, Gradle files, docs, or committed config. Release
builds intentionally set `BuildConfig.THINGIVERSE_APP_TOKEN` to an empty string.

For release-style OAuth testing, configure the public Client ID and backend URL
outside Git:

```properties
# android-app/local.properties
thingiverse.clientId=...
thingiverse.authBackendUrl=https://your-mobile-slicer-api.example
```

The app callback URI is:

```text
mobileslicer://thingiverse-auth
```

For the current Cloudflare Pages backend, set the Thingiverse developer app
callback URL to:

```text
https://mobileslicer.com/v1/thingiverse/oauth/callback
```

Configure Cloudflare Pages secrets and bindings:

```bash
cd Website
wrangler pages secret put THINGIVERSE_CLIENT_SECRET --project-name mobileslicer
wrangler pages secret put THINGIVERSE_OAUTH_STATE_SECRET --project-name mobileslicer
wrangler kv namespace create THINGIVERSE_OAUTH_RATE_LIMIT_KV
```

Then set the public `THINGIVERSE_CLIENT_ID`, keep
`THINGIVERSE_ALLOWED_APP_REDIRECTS = "mobileslicer://thingiverse-auth"`, and
bind the created KV namespace to `THINGIVERSE_OAUTH_RATE_LIMIT_KV` in
`Website/wrangler.toml` or the Cloudflare Pages project settings.

The backend finishes browser authentication by redirecting to the app callback
with `state` and `handoff_code`. Android rejects callbacks with the wrong state,
then redeems the one-time code against `/v1/thingiverse/oauth/redeem` over
HTTPS. The redeem response contains the user-scoped `access_token` plus
optional `display_name`, `user_id`, `token_type`, and `expires_in` values.
Expired or unauthorized sessions are cleared locally.

Backend verification:

```bash
node --test Website/test/thingiverse-oauth.test.mjs
```

Full Thingiverse release-readiness preflight:

```bash
scripts/verify_thingiverse_release_ready.sh
```

That preflight verifies backend tests, Android model-search contract tests,
required OAuth files, release-style Android configuration, and Cloudflare deploy
tooling. It intentionally does not deploy account secrets or publish the
Cloudflare Pages project.

## Native Planning Performance Notes

Auto-orient and auto-arrange use the native Orca planning path. The current
implementation keeps behavior correct first:

* no Kotlin fallback orientation is used
* auto-orient applies the native orientation result while preserving the
  object's current plate center
* auto-arrange applies native placement results
* if native auto-arrange returns multiple Orca bed indices, the UI offers that
  exact multi-plate arrangement instead of silently failing or scattering
  objects
* selected-object auto-orient remains the preferred fast path
* single-object auto-arrange can skip native packing only when it is an exact
  center-on-bed operation, rotations are disabled, and no prime tower space must
  be reserved
* duplicate planning requests are blocked while the native planner is active

The UI keeps the planning buttons disabled while a request is running so the app
does not enqueue duplicate native jobs. For repeated actions against the same
plate/profile state, MobileSlicer reuses the prepared native config JSON instead
of rebuilding it.

The native engine also keeps a small planning source-model cache. Cache entries
are keyed by model path, file size, and file modified time. Prewarm fills that
cache after workspace mesh preparation without changing the plate, orientation,
or UI state. Auto-orient and auto-arrange still build a fresh mutable planning
copy for each request, but they can clone from the cached source model instead
of parsing the STL again.

Timing logs are intentionally available in debug builds:

```text
MobileSlicerPerf: plate_planning_config_cache hit|miss ...
MobileSlicerPerf: plate_planning_prewarm success=... objects=... totalMs=...
MobileSlicerPerf: plate_planning_prewarm_request reason=... success=... objects=...
MobileSlicerPerf: autoOrient ui totalMs=...
MobileSlicerPerf: autoOrient nativePipeline totalMs=... requestMs=... nativeMs=... validationMs=...
MobileSlicerNative: orca_plan_auto_orientation: timing configMs=... loadMs=... groupMs=... meshMs=... orientMs=... applyMs=... totalMs=...
MobileSlicerPerf: autoArrange ui totalMs=...
MobileSlicerPerf: autoArrange fastPath=singleObjectCenter totalMs=... fastPathMs=...
MobileSlicerPerf: autoArrange nativePipeline totalMs=... requestMs=... nativeMs=... validationMs=...
MobileSlicerNative: orca_plan_plate_arrangement: timing configMs=... loadMs=... itemBuildMs=... paramsMs=... arrangeMs=... resultMs=... totalMs=...
```

Next optimization should be chosen from those logs. If native `loadMs` still
dominates after prewarm, the next improvement is reducing model clone cost or
sharing a deeper immutable Orca representation. If `orientMs` or `arrangeMs`
dominates, the next improvement belongs in Orca-side geometry simplification or
selected-object/all-object workflow separation. If arrange `itemBuildMs` or
`paramsMs` dominates, optimize footprint construction, grouped-object hull
creation, or arrange parameter preparation before touching the packing
algorithm.

Verbose per-object native arrange logs are gated behind
`kVerboseNativeTimingLogs`; summary timings remain on so debug builds still show
the performance shape without flooding logcat during large plates.

Planning config must be valid before the native call starts. Blank or invalid
`top_shell_thickness` and `bottom_shell_thickness` values are normalized to
numeric zero during native config assembly, plate material-slot application, and
post-repair config merging. The native override layer also coerces blank string
overrides for those numeric fields to `0`. This prevents Orca config
deserialization from rejecting auto-arrange, auto-orient, or slicing before it
can plan the plate.

Slice requests run the same generated-footprint preflight used by the workspace
before calling the native slicer. The check derives brim and skirt clearance from
the final native config JSON, validates plate-object bounds against the selected
bed, and logs a compact `slice_preflight` summary. If the generated skirt, brim,
or purge footprint would exceed the printable volume, MobileSlicer now returns a
clear printable-volume message instead of passing impossible coordinates to Orca.

## Verification

Focused verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest --tests com.mobileslicer.modelsearch.ModelSearchContractsTest
```

The focused contract suite covers external-link-only source policy, source URL
normalization, generic Android model MIME handling, supported Thingiverse file
classification, and the source-browser state transition from external source to
awaiting a user-selected file.

Related focused verification:

```bash
cd android-app
./gradlew :app:testDebugUnitTest \
  --tests com.mobileslicer.ModelLoaderNavigationTest \
  --tests com.mobileslicer.ModelLoaderImportFlowTest \
  --tests com.mobileslicer.storage.SavedProjectRepositoryTest \
  --tests com.mobileslicer.modelsearch.ModelSearchContractsTest
```

Manual Android regression checklist:

* tap `Find and import model` under `Open model`
* confirm Thingiverse appears once as the API panel, not as a separate web row
* open each external web source and return without downloading
* import an STL from the Android file picker
* import a 3MF from the Android file picker
* sign in to Thingiverse through the release OAuth flow, or configure a debug
  app token for local API testing
* search Thingiverse for a model
* confirm the search field remains one line while focused and while typing
* clear search results and confirm the panel returns to its idle state
* use `Load more results` when a Thingiverse search returns a full API page
* open a Thingiverse result's file list
* confirm printable files are sorted with 3MF before STL and unsupported files
  are counted but not offered for direct import
* open the original Thingiverse page from the file-selection view
* import one Thingiverse STL or 3MF file
* import all files for a multi-part Thingiverse model and confirm all parts land
  on one plate
* long-press a file row, select a subset, import selected, and confirm only
  those files are added
* confirm imported Thingiverse file names remain visible on the plate and in
  suggested G-code export names after staging
* confirm import progress is compact and clears after success/failure/back
* slice a multi-part import after arrange and confirm no blank shell-thickness
  config errors are logged
* move an object close enough to the bed edge that brim/skirt output would
  exceed the printable area and confirm preflight blocks the slice with a
  printable-volume message
* confirm G-code preview seam markers are hidden by default so seam points do
  not render as broken-looking mesh artifacts; users can still inspect line
  types from the preview info sheet
* confirm the active native slice config normalizes the legacy `seam_gap=10%`
  default to `0%` so imported models do not preview with a vertical perimeter
  split
* try an unsupported file and confirm the existing importer reports failure
* open an STL from the system Files app with MobileSlicer
* share an STL to MobileSlicer from a file manager
* confirm none of those flows show a model review screen

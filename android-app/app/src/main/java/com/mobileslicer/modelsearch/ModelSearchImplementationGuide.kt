package com.mobileslicer.modelsearch

/**
 * Implementation boundary for the "Find and import model" feature.
 *
 * This package implements the M0 contract from
 * README/plans/MODEL_SEARCH_IMPORT_PLAN.md:
 *
 * - keep V1 local-first for local files and external-link-only sources
 * - use the official Thingiverse API for direct import
 * - keep Thingiverse release OAuth behind the MobileSlicer backend bridge so
 *   the private Client Secret never ships in Android
 * - open Printables and MakerWorld as external-link-only sources
 * - do not scrape, cache thumbnails, intercept downloads, or direct-download
 *   files from sources without an approved API path
 * - use Android's file picker or open/share intents for explicit user-selected files
 * - hand selected model files directly to the existing MobileSlicer importer
 * - keep source browsing separate from model loading so the app never assumes a
 *   website download happened automatically
 *
 * UI wiring should call into these contracts instead of embedding source policy
 * or Android lifecycle assumptions in Compose screens.
 */
object ModelSearchImplementationGuide {
    const val HOME_ENTRY_LABEL = "Find and import model"
    const val IMPORT_DOWNLOADED_FILE_LABEL = "Import file"
}

package com.mobileslicer.modelsearch.sources

/**
 * Contract-level source mode. UI and repositories must derive source behavior
 * from this value instead of scattering platform/legal policy checks.
 */
enum class SourceIntegrationMode {
    EXTERNAL_LINK_ONLY,
    OFFICIAL_API,
    PARTNER_API,
    USER_IMPORT_ONLY,
    DISABLED
}

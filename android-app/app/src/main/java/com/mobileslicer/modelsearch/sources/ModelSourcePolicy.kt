package com.mobileslicer.modelsearch.sources

data class ModelSourcePolicy(
    val id: String,
    val displayName: String,
    val mode: SourceIntegrationMode,
    val externalUrl: String?,
    val directDownloadAllowed: Boolean,
    val thumbnailCachingAllowed: Boolean,
    val plainTextUiOnly: Boolean = true
) {
    val canOpenExternally: Boolean
        get() = mode == SourceIntegrationMode.EXTERNAL_LINK_ONLY && !externalUrl.isNullOrBlank()
}

package com.mobileslicer.modelsearch.sources

object SourceRegistry {
    const val THINGIVERSE = "thingiverse"
    const val PRINTABLES = "printables"
    const val MAKERWORLD = "makerworld"

    val defaultSources: List<ModelSourcePolicy> = listOf(
        ModelSourcePolicy(
            id = PRINTABLES,
            displayName = "Printables",
            mode = SourceIntegrationMode.EXTERNAL_LINK_ONLY,
            externalUrl = "https://www.printables.com",
            directDownloadAllowed = false,
            thumbnailCachingAllowed = false
        ),
        ModelSourcePolicy(
            id = MAKERWORLD,
            displayName = "MakerWorld",
            mode = SourceIntegrationMode.EXTERNAL_LINK_ONLY,
            externalUrl = "https://makerworld.com",
            directDownloadAllowed = false,
            thumbnailCachingAllowed = false
        )
    )

    fun byId(sourceId: String): ModelSourcePolicy? =
        defaultSources.firstOrNull { it.id == sourceId }
}

package com.mobileslicer.modelsearch.sources

import java.net.URI

object SourceUrlNormalizer {
    fun normalize(rawUrl: String?): NormalizedSourceUrl {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return NormalizedSourceUrl(rawUrl = rawUrl, normalizedUrl = null, sourceId = null, valid = false)
        }

        val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val uri = runCatching { URI(candidate) }.getOrNull()
        val host = uri?.host?.lowercase()?.removePrefix("www.")
        val sourceId = when {
            host == null -> null
            host.endsWith("thingiverse.com") -> SourceRegistry.THINGIVERSE
            host.endsWith("printables.com") -> SourceRegistry.PRINTABLES
            host.endsWith("makerworld.com") -> SourceRegistry.MAKERWORLD
            else -> null
        }
        val valid = uri?.scheme in setOf("http", "https") && !host.isNullOrBlank()

        return NormalizedSourceUrl(
            rawUrl = rawUrl,
            normalizedUrl = if (valid) uri.toString() else null,
            sourceId = sourceId,
            valid = valid
        )
    }
}

data class NormalizedSourceUrl(
    val rawUrl: String?,
    val normalizedUrl: String?,
    val sourceId: String?,
    val valid: Boolean
)

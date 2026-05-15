package com.mobileslicer.nativebridge

import org.json.JSONObject

internal data class NativeThumbnailRequest(
    val width: Int,
    val height: Int,
    val format: String
)

internal data class NativeThumbnailRequestErrors(
    val invalidValue: Boolean = false,
    val outOfRange: Boolean = false,
    val invalidExtension: Boolean = false
) {
    val hasAny: Boolean
        get() = invalidValue || outOfRange || invalidExtension
}

internal data class NativeThumbnailRequestSummary(
    val source: String,
    val thumbnails: String,
    val thumbnailsFormat: String,
    val errors: NativeThumbnailRequestErrors,
    val errorText: String,
    val requests: List<NativeThumbnailRequest>
) {
    val hasErrors: Boolean
        get() = errors.hasAny
}

internal fun parseNativeThumbnailRequestsJson(rawJson: String?): NativeThumbnailRequestSummary? {
    val raw = rawJson?.takeIf { it.isNotBlank() } ?: return null
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val errorsJson = root.optJSONObject("errors")
    val errors = NativeThumbnailRequestErrors(
        invalidValue = errorsJson?.optBoolean("invalidValue", false) ?: root.optBoolean("invalidValue", false),
        outOfRange = errorsJson?.optBoolean("outOfRange", false) ?: root.optBoolean("outOfRange", false),
        invalidExtension = errorsJson?.optBoolean("invalidExtension", false) ?: root.optBoolean("invalidExtension", false)
    )
    val requestsJson = root.optJSONArray("requests")
    val requests = buildList {
        if (requestsJson != null) {
            for (index in 0 until requestsJson.length()) {
                val item = requestsJson.optJSONObject(index) ?: continue
                val width = item.optInt("width", 0)
                val height = item.optInt("height", 0)
                val format = item.optString("format", "").trim()
                if (width > 0 && height > 0 && format.isNotBlank()) {
                    add(
                        NativeThumbnailRequest(
                            width = width,
                            height = height,
                            format = format
                        )
                    )
                }
            }
        }
    }
    return NativeThumbnailRequestSummary(
        source = root.optString("source", "orca"),
        thumbnails = root.optString("thumbnails", ""),
        thumbnailsFormat = root.optString("thumbnailsFormat", ""),
        errors = errors,
        errorText = root.optString("errorText", ""),
        requests = requests
    )
}

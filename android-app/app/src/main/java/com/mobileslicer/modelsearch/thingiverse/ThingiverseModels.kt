package com.mobileslicer.modelsearch.thingiverse

import com.mobileslicer.workspace.detectSourceModelFileFormat

data class ThingiverseSearchResult(
    val thingId: Long,
    val name: String,
    val creatorName: String?,
    val publicUrl: String?,
    val thumbnailUrl: String?,
    val license: String?,
    val fileCount: Int?,
    val likeCount: Int?,
    val downloadCount: Int?
)

data class ThingiverseFileResult(
    val fileId: Long,
    val name: String,
    val sizeBytes: Long?,
    val formattedSize: String?,
    val thumbnailUrl: String?,
    val downloadUrl: String?,
    val publicUrl: String?,
    val directUrl: String?
) {
    val isSupportedModelFile: Boolean
        get() {
            return detectSourceModelFileFormat(name) != null
        }

    val extensionLabel: String
        get() = name.substringAfterLast('.', missingDelimiterValue = "file").uppercase()

    val displayName: String
        get() = name
            .substringBeforeLast('.', missingDelimiterValue = name)
            .replace("_-_", " ")
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { name }

    val displaySize: String?
        get() = formattedSize ?: sizeBytes?.let { bytes ->
            when {
                bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
                bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
                else -> "$bytes B"
            }
        }

    val printableFileSortKey: Int
        get() = when (extensionLabel) {
            "3MF" -> 0
            "STL" -> 1
            "STEP", "STP" -> 2
            else -> 3
        }
}

data class ThingiverseThingDetail(
    val thingId: Long,
    val name: String,
    val creatorName: String?,
    val publicUrl: String?,
    val license: String?,
    val files: List<ThingiverseFileResult>
)

sealed interface ThingiverseSearchUiState {
    data object Idle : ThingiverseSearchUiState
    data object MissingToken : ThingiverseSearchUiState
    data object Searching : ThingiverseSearchUiState
    data class SearchResults(
        val query: String,
        val results: List<ThingiverseSearchResult>,
        val page: Int = 1,
        val canLoadMore: Boolean = false,
        val isLoadingMore: Boolean = false
    ) : ThingiverseSearchUiState
    data class LoadingFiles(
        val thing: ThingiverseSearchResult
    ) : ThingiverseSearchUiState
    data class FileResults(
        val thing: ThingiverseSearchResult,
        val files: List<ThingiverseFileResult>
    ) : ThingiverseSearchUiState
    data class ImportingFile(
        val thing: ThingiverseSearchResult,
        val file: ThingiverseFileResult
    ) : ThingiverseSearchUiState
    data class ImportingFiles(
        val thing: ThingiverseSearchResult,
        val files: List<ThingiverseFileResult>,
        val currentIndex: Int
    ) : ThingiverseSearchUiState {
        val currentFile: ThingiverseFileResult?
            get() = files.getOrNull(currentIndex)

        val completedCount: Int
            get() = currentIndex.coerceIn(0, files.size)

        val totalCount: Int
            get() = files.size
    }
    data class Error(
        val message: String,
        val canRetry: Boolean
    ) : ThingiverseSearchUiState
}

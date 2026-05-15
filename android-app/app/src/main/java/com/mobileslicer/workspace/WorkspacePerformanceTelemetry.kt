package com.mobileslicer.workspace

internal data class WorkspacePerformanceSnapshot(
    val prepareDurationMs: Long?,
    val sliceDurationMs: Long?,
    val warnings: List<String>
) {
    fun compactSummary(): String = buildList {
        prepareDurationMs?.let { add("Prepare: ${formatDurationSecondsTenths(it)}") }
        sliceDurationMs?.let { add("Slice: ${formatDurationSecondsTenths(it)}") }
        warnings.forEach { add(it) }
    }.joinToString(" • ")
}

internal fun workspaceResponsivenessLogLine(
    eventName: String,
    importTiming: ModelImportTiming?,
    workspacePreparationTiming: WorkspacePreparationTiming?,
    firstVisibleWorkspaceFrameMs: Long?,
    firstVisiblePreviewFrameMs: Long?,
    sliceTiming: SlicePipelineTiming?
): String {
    val snapshot = workspacePerformanceSnapshot(
        importTiming = importTiming,
        workspacePreparationTiming = workspacePreparationTiming,
        firstVisibleWorkspaceFrameMs = firstVisibleWorkspaceFrameMs,
        sliceTiming = sliceTiming
    )
    return buildList {
        add("event=${eventName.logValue()}")
        importTiming?.let {
            add("importStagingMs=${it.stagingMs}")
            add("importNativeLoadMs=${it.nativeLoadMs}")
            add("importTotalMs=${it.stagingMs + it.nativeLoadMs}")
        }
        workspacePreparationTiming?.let {
            add("workspaceMeshPrepMs=${it.viewerMeshPrepMs}")
            add("workspaceMeshCacheHit=${it.cacheHit}")
            it.sourceTriangleCount?.let { triangleCount -> add("sourceTriangles=$triangleCount") }
            it.displayTriangleCount?.let { triangleCount -> add("displayTriangles=$triangleCount") }
            if (it.sourceTriangleCount != null && it.displayTriangleCount != null) {
                add("displayExact=${it.sourceTriangleCount == it.displayTriangleCount}")
            }
            it.renderArrayBytes?.let { bytes -> add("renderArrayBytes=$bytes") }
            it.cacheRetainedBytes?.let { bytes -> add("meshCacheRetainedBytes=$bytes") }
            it.exactPreviewBudgetBytes?.let { bytes -> add("exactPreviewBudgetBytes=$bytes") }
        }
        firstVisibleWorkspaceFrameMs?.let { add("firstWorkspaceFrameMs=$it") }
        firstVisiblePreviewFrameMs?.let { add("firstPreviewFrameMs=$it") }
        snapshot.prepareDurationMs?.let { add("prepareMs=$it") }
        sliceTiming?.let {
            add("sliceModelReloadMs=${it.modelReloadMs}")
            add("sliceConfigMs=${it.configMs}")
            add("sliceNativeMs=${it.nativeSliceMs}")
            add("sliceWriteGcodeMs=${it.writeGcodeMs}")
            add("sliceSummaryMs=${it.summaryMs}")
            if (it.thumbnailMs > 0L) add("sliceThumbnailMs=${it.thumbnailMs}")
            add("sliceTotalMs=${it.totalMs}")
        }
        snapshot.warnings.forEachIndexed { index, warning ->
            add("warning$index=${warning.logValue()}")
        }
    }.joinToString(separator = " ", prefix = "workspace_responsiveness ")
}

internal fun workspacePerformanceSnapshot(
    importTiming: ModelImportTiming?,
    workspacePreparationTiming: WorkspacePreparationTiming?,
    firstVisibleWorkspaceFrameMs: Long?,
    sliceTiming: SlicePipelineTiming?
): WorkspacePerformanceSnapshot {
    val prepareDurationMs = firstVisibleWorkspaceFrameMs ?: run {
        var totalMs = 0L
        var hasTiming = false
        importTiming?.let {
            totalMs += it.stagingMs + it.nativeLoadMs
            hasTiming = true
        }
        workspacePreparationTiming?.let {
            totalMs += it.viewerMeshPrepMs
            hasTiming = true
        }
        totalMs.takeIf { hasTiming }
    }
    val sliceDurationMs = sliceTiming?.totalMs
    return WorkspacePerformanceSnapshot(
        prepareDurationMs = prepareDurationMs,
        sliceDurationMs = sliceDurationMs,
        warnings = workspacePerformanceWarnings(
            prepareDurationMs = prepareDurationMs,
            sliceDurationMs = sliceDurationMs
        )
    )
}

private fun workspacePerformanceWarnings(
    prepareDurationMs: Long?,
    sliceDurationMs: Long?
): List<String> = buildList {
    if (prepareDurationMs != null && prepareDurationMs >= 15_000L) {
        add("Prepare slow")
    }
    if (sliceDurationMs != null && sliceDurationMs >= 120_000L) {
        add("Slice slow")
    }
}

private fun String.logValue(): String =
    trim()
        .ifBlank { "unknown" }
        .replace(Regex("[^A-Za-z0-9_.-]"), "_")

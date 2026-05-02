package com.mobileslicer.workspace

internal data class WorkspacePerformanceSnapshot(
    val prepareDurationMs: Long?,
    val sliceDurationMs: Long?,
    val displayLodSummary: String,
    val warnings: List<String>
) {
    fun compactSummary(): String = buildList {
        prepareDurationMs?.let { add("Prepare: ${formatDurationSecondsTenths(it)}") }
        if (displayLodSummary.isNotBlank()) add(displayLodSummary)
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
            add("displayReduced=${it.reducedForDisplay}")
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
    val displayLodSummary = workspacePreparationTiming
        ?.takeIf { it.reducedForDisplay && it.sourceTriangleCount != null && it.displayTriangleCount != null }
        ?.let { "Display LOD: ${it.displayTriangleCount}/${it.sourceTriangleCount} tris" }
        .orEmpty()
    val sliceDurationMs = sliceTiming?.totalMs
    return WorkspacePerformanceSnapshot(
        prepareDurationMs = prepareDurationMs,
        sliceDurationMs = sliceDurationMs,
        displayLodSummary = displayLodSummary,
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

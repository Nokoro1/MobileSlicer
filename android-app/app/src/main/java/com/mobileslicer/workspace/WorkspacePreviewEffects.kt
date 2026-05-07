package com.mobileslicer.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.mobileslicer.nativebridge.NativeEngineCalls
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.TouchModelViewerView
import com.mobileslicer.viewer.ViewerFailure
import com.mobileslicer.viewer.parsePreviewRangePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun WorkspacePreviewEffects(
    workspaceMode: WorkspaceMode,
    previewSliceKey: Long,
    previewLayerCount: Int,
    previewEngineHandle: Long,
    previewVertexBudget: Long,
    sliceSummary: SliceResultSummary?,
    sliceInProgress: Boolean,
    showPreviewInfoSheet: Boolean,
    loadedMesh: StlMesh?,
    runtimeViewerReady: Boolean,
    runtimeViewerFailure: ViewerFailure?,
    previewPathVisibility: Map<String, Boolean>,
    viewerView: TouchModelViewerView?,
    previewRuntime: WorkspacePreviewRuntime,
    onRuntimeViewerFailureChanged: (ViewerFailure?) -> Unit,
    onSliceSummaryChanged: (SliceResultSummary) -> Unit,
    onFirstVisibleWorkspaceFrame: () -> Unit,
    onFirstVisiblePreviewFrame: () -> Unit
) {
    LaunchedEffect(workspaceMode, previewSliceKey, previewLayerCount, previewEngineHandle, sliceSummary?.byteCount, previewVertexBudget) {
        if (workspaceMode != WorkspaceMode.Preview || previewSliceKey <= 0L || previewEngineHandle == 0L) {
            previewRuntime.exactPlanReady = workspaceMode != WorkspaceMode.Preview
            return@LaunchedEffect
        }
        if (
            previewVertexBudget >= com.mobileslicer.viewer.GcodePreviewPerformanceMode.HARD_VERTEX_CEILING &&
            (sliceSummary?.byteCount ?: 0) in 1 until PreviewRangePlanningByteThreshold
        ) {
            previewRuntime.resetToFullRange(previewLayerCount)
            previewRuntime.exactPlanReady = true
            return@LaunchedEffect
        }
        previewRuntime.exactPlanReady = false
        onRuntimeViewerFailureChanged(null)
        val plannedRanges = withContext(Dispatchers.Default) {
            val handle = NativeEngineHandle.fromRaw(previewEngineHandle) ?: return@withContext emptyList()
            val rawPlan = NativeEngineCalls.planLatestSlicePreviewRanges(
                handle = handle,
                minLayer = 0L,
                maxLayer = (previewLayerCount - 1).toLong(),
                vertexBudget = previewVertexBudget
            )
            parsePreviewRangePlan(rawPlan)
        }
        if (plannedRanges.size > 1) {
            previewRuntime.autoRanges = plannedRanges
            previewRuntime.autoRangeIndex = 0
            previewRuntime.layerSelection = plannedRanges.first().toPreviewLayerSelection()
        } else {
            previewRuntime.resetToFullRange(previewLayerCount)
        }
        previewRuntime.exactPlanReady = true
    }
    LaunchedEffect(showPreviewInfoSheet, workspaceMode, previewSliceKey, previewEngineHandle, sliceSummary?.previewInfo?.hasRichData, sliceInProgress) {
        if (sliceInProgress || !showPreviewInfoSheet || workspaceMode != WorkspaceMode.Preview || previewSliceKey <= 0L || previewEngineHandle == 0L) return@LaunchedEffect
        if (sliceSummary?.previewInfo?.hasRichData == true) return@LaunchedEffect
        val enrichedSummary = withContext(Dispatchers.Default) {
            val handle = NativeEngineHandle.fromRaw(previewEngineHandle) ?: return@withContext null
            GcodeSummaryParser.fromNativeSummary(NativeEngineCalls.getEnrichedGcodeSummary(handle))
        }
        if (enrichedSummary != null) {
            onSliceSummaryChanged(enrichedSummary)
        }
    }
    LaunchedEffect(runtimeViewerReady, loadedMesh) {
        if (runtimeViewerReady && loadedMesh != null) {
            onFirstVisibleWorkspaceFrame()
        }
    }
    LaunchedEffect(runtimeViewerReady, workspaceMode, previewSliceKey) {
        if (runtimeViewerReady && workspaceMode == WorkspaceMode.Preview && previewSliceKey > 0L) {
            onFirstVisiblePreviewFrame()
        }
    }
    LaunchedEffect(runtimeViewerFailure, workspaceMode, previewSliceKey) {
        val suggestions = runtimeViewerFailure?.previewRangeSuggestions.orEmpty()
        if (workspaceMode != WorkspaceMode.Preview || suggestions.isEmpty()) {
            return@LaunchedEffect
        }
        val chunkRanges = suggestions.filter { it.label.startsWith("Range ") }.ifEmpty { suggestions }
        val firstRange = chunkRanges.firstOrNull() ?: return@LaunchedEffect
        previewRuntime.autoRanges = chunkRanges
        previewRuntime.autoRangeIndex = 0
        previewRuntime.layerSelection = firstRange.toPreviewLayerSelection()
        previewRuntime.layerReloadToken++
        onRuntimeViewerFailureChanged(null)
    }
    LaunchedEffect(viewerView, previewPathVisibility, workspaceMode, previewSliceKey, sliceSummary) {
        val view = viewerView ?: return@LaunchedEffect
        if (workspaceMode != WorkspaceMode.Preview || previewSliceKey <= 0L) return@LaunchedEffect
        sliceSummary?.previewInfo?.lineTypes.orEmpty().forEach { row ->
            val key = previewLineVisibilityKey(row)
            previewPathVisibility[key]?.let { visible ->
                view.setGcodePathVisibility(row.kind.nativeKind, row.nativeId, visible)
            }
        }
    }
}

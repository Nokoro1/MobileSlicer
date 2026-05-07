package com.mobileslicer.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mobileslicer.viewer.PreviewRangeSuggestion

internal class WorkspacePreviewRuntime(
    previewLayerCount: Int
) {
    var layerSelection by mutableStateOf(defaultPreviewLayerSelection(previewLayerCount))
    var layerReloadToken by mutableLongStateOf(0L)
    var exactPlanReady by mutableStateOf(false)
    var autoRanges by mutableStateOf<List<PreviewRangeSuggestion>>(emptyList())
    var autoRangeIndex by mutableIntStateOf(0)

    fun resetToFullRange(previewLayerCount: Int) {
        autoRanges = emptyList()
        autoRangeIndex = 0
        layerSelection = defaultPreviewLayerSelection(previewLayerCount)
    }
}

@Composable
internal fun rememberWorkspacePreviewRuntime(
    previewSliceKey: Long,
    previewLayerCount: Int
): WorkspacePreviewRuntime =
    remember(previewSliceKey, previewLayerCount) {
        WorkspacePreviewRuntime(previewLayerCount)
    }

internal fun defaultPreviewLayerSelection(previewLayerCount: Int): PreviewLayerSelection =
    PreviewLayerSelection(
        mode = PreviewLayerMode.Range,
        singleLayer = previewLayerCount,
        rangeStartLayer = 1,
        rangeEndLayer = previewLayerCount
    )

internal fun selectedPreviewLayerRange(
    selection: PreviewLayerSelection,
    previewLayerCount: Int,
    maxRangeLayerSpan: Int
): Pair<Long, Long> =
    when (selection.mode) {
        PreviewLayerMode.Single -> {
            val layer = selection.singleLayer.coerceIn(1, previewLayerCount)
            (layer - 1).toLong() to (layer - 1).toLong()
        }
        PreviewLayerMode.Range -> {
            val start = selection.rangeStartLayer.coerceIn(1, previewLayerCount)
            val unclampedEnd = selection.rangeEndLayer.coerceIn(start, previewLayerCount)
            val end = if (maxRangeLayerSpan >= previewLayerCount) {
                unclampedEnd
            } else {
                unclampedEnd.coerceAtMost(start + maxRangeLayerSpan - 1)
            }
            (start - 1).toLong() to (end - 1).toLong()
        }
    }

package com.mobileslicer.workspace

import com.mobileslicer.viewer.PreviewRangeSuggestion
import kotlin.math.abs

internal fun previewSelectionLayerSpan(
    selection: PreviewLayerSelection,
    layerCount: Int
): IntRange {
    val count = layerCount.coerceAtLeast(1)
    return when (selection.mode) {
        PreviewLayerMode.Single -> {
            val layer = selection.singleLayer.coerceIn(1, count)
            layer..layer
        }
        PreviewLayerMode.Range -> {
            val start = selection.rangeStartLayer.coerceIn(1, count)
            val end = selection.rangeEndLayer.coerceIn(start, count)
            start..end
        }
    }
}

internal fun previewRangeIndexForSelection(
    selection: PreviewLayerSelection,
    ranges: List<PreviewRangeSuggestion>,
    currentIndex: Int,
    layerCount: Int
): Int {
    if (ranges.isEmpty()) {
        return 0
    }
    val current = currentIndex.coerceIn(0, ranges.lastIndex)
    val requested = previewSelectionLayerSpan(selection, layerCount)
    if (ranges[current].contains(requested)) {
        return current
    }
    ranges.indexOfFirst { it.contains(requested) }
        .takeIf { it >= 0 }
        ?.let { return it }

    val requestedAnchor = requested.first
    ranges.indexOfFirst { requestedAnchor in it.startLayer..it.endLayer }
        .takeIf { it >= 0 }
        ?.let { return it }

    return ranges.indices.minBy { index ->
        val range = ranges[index]
        minOf(abs(requestedAnchor - range.startLayer), abs(requestedAnchor - range.endLayer))
    }
}

private fun PreviewRangeSuggestion.contains(layers: IntRange): Boolean =
    layers.first >= startLayer && layers.last <= endLayer

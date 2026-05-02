package com.mobileslicer.workspace

import com.mobileslicer.viewer.PreviewRangeSuggestion
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspacePreviewRangePlanningTest {
    @Test
    fun keepsCurrentChunkWhenSelectionIsAlreadyLoaded() {
        val ranges = listOf(
            PreviewRangeSuggestion(label = "Range 1", startLayer = 1, endLayer = 100),
            PreviewRangeSuggestion(label = "Range 2", startLayer = 101, endLayer = 200)
        )

        val index = previewRangeIndexForSelection(
            selection = PreviewLayerSelection(
                mode = PreviewLayerMode.Single,
                singleLayer = 55,
                rangeStartLayer = 1,
                rangeEndLayer = 100
            ),
            ranges = ranges,
            currentIndex = 0,
            layerCount = 200
        )

        assertEquals(0, index)
    }

    @Test
    fun switchesChunksWhenSingleLayerSelectionLeavesLoadedRange() {
        val ranges = listOf(
            PreviewRangeSuggestion(label = "Range 1", startLayer = 1, endLayer = 100),
            PreviewRangeSuggestion(label = "Range 2", startLayer = 101, endLayer = 200)
        )

        val index = previewRangeIndexForSelection(
            selection = PreviewLayerSelection(
                mode = PreviewLayerMode.Single,
                singleLayer = 175,
                rangeStartLayer = 1,
                rangeEndLayer = 100
            ),
            ranges = ranges,
            currentIndex = 0,
            layerCount = 200
        )

        assertEquals(1, index)
    }

    @Test
    fun choosesRangeContainingWholeRequestedSpanBeforeAnchorFallback() {
        val ranges = listOf(
            PreviewRangeSuggestion(label = "Range 1", startLayer = 1, endLayer = 100),
            PreviewRangeSuggestion(label = "Range 2", startLayer = 90, endLayer = 180)
        )

        val index = previewRangeIndexForSelection(
            selection = PreviewLayerSelection(
                mode = PreviewLayerMode.Range,
                singleLayer = 1,
                rangeStartLayer = 95,
                rangeEndLayer = 150
            ),
            ranges = ranges,
            currentIndex = 0,
            layerCount = 180
        )

        assertEquals(1, index)
    }
}

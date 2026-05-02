package com.mobileslicer.viewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GcodePreviewLoadFreshnessTest {
    @Test
    fun previewLoadFreshnessIgnoresDisplayAndVisibilityStateVersions() {
        val expected = GcodePreviewStateVersions(
            previewVersion = 8L,
            layerRangeVersion = 10L,
            pathVisibilityVersion = 12L,
            displayModeVersion = 14L
        )
        val displayModeChanged = expected.copy(displayModeVersion = 15L)
        val pathVisibilityChanged = expected.copy(pathVisibilityVersion = 13L)
        val layerRangeChanged = expected.copy(layerRangeVersion = 11L)

        assertFalse(ViewerUpdateDecisions.isGcodePreviewStateCurrent(expected, displayModeChanged))
        assertFalse(ViewerUpdateDecisions.isGcodePreviewStateCurrent(expected, pathVisibilityChanged))
        assertFalse(ViewerUpdateDecisions.isGcodePreviewStateCurrent(expected, layerRangeChanged))
        assertTrue(
            ViewerUpdateDecisions.isGcodePreviewLoadCurrent(
                expectedPreviewVersion = expected.previewVersion,
                currentPreviewVersion = displayModeChanged.previewVersion
            )
        )
        assertTrue(
            ViewerUpdateDecisions.isGcodePreviewLoadCurrent(
                expectedPreviewVersion = expected.previewVersion,
                currentPreviewVersion = pathVisibilityChanged.previewVersion
            )
        )
        assertTrue(
            ViewerUpdateDecisions.isGcodePreviewLoadCurrent(
                expectedPreviewVersion = expected.previewVersion,
                currentPreviewVersion = layerRangeChanged.previewVersion
            )
        )
    }

    @Test
    fun previewLoadFreshnessRejectsNewPreviewGeneration() {
        assertFalse(
            ViewerUpdateDecisions.isGcodePreviewLoadCurrent(
                expectedPreviewVersion = 8L,
                currentPreviewVersion = 9L
            )
        )
    }
}

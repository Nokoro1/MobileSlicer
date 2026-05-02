package com.mobileslicer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewInteractionAutomationTest {
    @Test
    fun parsesPreviewProfileIntent() {
        val request = PreviewInteractionAutomationRequest.pathsFromValues(
            action = PreviewInteractionAutomationRequest.ACTION_PROFILE_PREVIEW,
            modelPath = "/tmp/model.stl",
            statusPath = "/tmp/status.txt"
        )

        assertNotNull(request)
        assertEquals("/tmp/model.stl", request?.first)
        assertEquals("/tmp/status.txt", request?.second)
    }

    @Test
    fun rejectsMissingPreviewProfileModelPath() {
        assertNull(
            PreviewInteractionAutomationRequest.pathsFromValues(
                action = PreviewInteractionAutomationRequest.ACTION_PROFILE_PREVIEW,
                modelPath = null,
                statusPath = null
            )
        )
    }
}

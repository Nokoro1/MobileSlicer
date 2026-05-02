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

    @Test
    fun clampsPreviewChurnRequestCount() {
        assertEquals(0, PreviewInteractionAutomationRequest.normalizeChurnRequests(-1))
        assertEquals(12, PreviewInteractionAutomationRequest.normalizeChurnRequests(12))
        assertEquals(40, PreviewInteractionAutomationRequest.normalizeChurnRequests(10_000))
    }

    @Test
    fun clampsPreviewLifecycleCycleCount() {
        assertEquals(0, PreviewInteractionAutomationRequest.normalizeLifecycleCycles(-1))
        assertEquals(6, PreviewInteractionAutomationRequest.normalizeLifecycleCycles(6))
        assertEquals(12, PreviewInteractionAutomationRequest.normalizeLifecycleCycles(10_000))
    }
}

package com.mobileslicer

import com.mobileslicer.automation.OffscreenEglSmokeRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OffscreenEglSmokeRequestTest {
    @Test
    fun parsesAndClampsSmokeRequest() {
        val request = OffscreenEglSmokeRequest.fromValues(
            action = OffscreenEglSmokeRequest.ACTION_EGL_THUMBNAIL_SMOKE,
            statusPath = "/tmp/egl.status.txt",
            width = 4,
            height = 4000,
            repeats = 100
        )

        requireNotNull(request)
        assertEquals("/tmp/egl.status.txt", request.statusPath)
        assertEquals(16, request.width)
        assertEquals(1024, request.height)
        assertEquals(20, request.repeats)
        assertEquals(270f, request.bedWidthMm)
        assertEquals(270f, request.bedDepthMm)
        assertEquals(256f, request.bedHeightMm)
        assertEquals(OffscreenEglSmokeRequest.SOURCE_LAYOUT_SINGLE, request.sourceLayout)
        assertEquals(emptyList<Int>(), request.sourceColors)
    }

    @Test
    fun rejectsNonSmokeActionAndMissingStatusPath() {
        assertNull(
            OffscreenEglSmokeRequest.fromValues(
                action = "android.intent.action.VIEW",
                statusPath = "/tmp/egl.status.txt"
            )
        )
        assertNull(
            OffscreenEglSmokeRequest.fromValues(
                action = OffscreenEglSmokeRequest.ACTION_EGL_THUMBNAIL_SMOKE,
                statusPath = ""
            )
        )
    }

    @Test
    fun acceptsRealSliceThumbnailSmokeAction() {
        val request = OffscreenEglSmokeRequest.fromValues(
            action = OffscreenEglSmokeRequest.ACTION_EGL_SLICE_THUMBNAIL_SMOKE,
            statusPath = "/tmp/slice-thumbnail.status.txt"
        )

        requireNotNull(request)
        assertEquals(OffscreenEglSmokeRequest.ACTION_EGL_SLICE_THUMBNAIL_SMOKE, request.action)
        assertEquals(128, request.width)
        assertEquals(128, request.height)
        assertEquals(3, request.repeats)
    }

    @Test
    fun acceptsThumbnailCompareActionWithArtifactInputs() {
        val request = OffscreenEglSmokeRequest.fromValues(
            action = OffscreenEglSmokeRequest.ACTION_EGL_THUMBNAIL_COMPARE,
            statusPath = "/tmp/compare.status.txt",
            modelPath = "/tmp/model.stl",
            artifactDirPath = "/tmp/artifacts",
            bedWidthMm = 220f,
            bedDepthMm = 221f,
            bedHeightMm = 250f,
            sourceLayout = OffscreenEglSmokeRequest.SOURCE_LAYOUT_TWO_FILAMENT_OBJECTS,
            sourceColors = listOf(0xF2754E, 0x4EA3F2)
        )

        requireNotNull(request)
        assertEquals(OffscreenEglSmokeRequest.ACTION_EGL_THUMBNAIL_COMPARE, request.action)
        assertEquals("/tmp/model.stl", request.modelPath)
        assertEquals("/tmp/artifacts", request.artifactDirPath)
        assertEquals(220f, request.bedWidthMm)
        assertEquals(221f, request.bedDepthMm)
        assertEquals(250f, request.bedHeightMm)
        assertEquals(OffscreenEglSmokeRequest.SOURCE_LAYOUT_TWO_FILAMENT_OBJECTS, request.sourceLayout)
        assertEquals(listOf(0xF2754E, 0x4EA3F2), request.sourceColors)
    }

    @Test
    fun clampsUnknownThumbnailCompareSourceLayoutToSingle() {
        val request = OffscreenEglSmokeRequest.fromValues(
            action = OffscreenEglSmokeRequest.ACTION_EGL_THUMBNAIL_COMPARE,
            statusPath = "/tmp/compare.status.txt",
            sourceLayout = "unknown"
        )

        requireNotNull(request)
        assertEquals(OffscreenEglSmokeRequest.SOURCE_LAYOUT_SINGLE, request.sourceLayout)
    }
}

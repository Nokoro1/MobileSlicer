package com.mobileslicer.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerImageQualityTest {
    @Test
    fun flatImageHasNoSharpness() {
        val luma = IntArray(9 * 9) { 128 }

        val analysis = analyzeScannerLumaQuality(width = 9, height = 9, luma = luma)

        assertEquals(0f, analysis.blurScore, 0.0001f)
        assertEquals(1f, analysis.exposureScore, 0.01f)
        assertEquals(0f, analysis.clippedHighlightRatio, 0.0001f)
    }

    @Test
    fun checkerboardImageHasHighSharpness() {
        val width = 9
        val height = 9
        val luma = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if ((x + y) % 2 == 0) 0 else 255
        }

        val analysis = analyzeScannerLumaQuality(width = width, height = height, luma = luma)

        assertTrue("Expected high blur score, got ${analysis.blurScore}", analysis.blurScore > 100000f)
        assertTrue(analysis.clippedHighlightRatio > 0.9f)
    }

    @Test
    fun darkImageHasLowExposureScore() {
        val luma = IntArray(10 * 10) { 10 }

        val analysis = analyzeScannerLumaQuality(width = 10, height = 10, luma = luma)

        assertTrue(analysis.exposureScore < 0.1f)
    }

    @Test
    fun readinessHelpersClampValues() {
        assertEquals(1f, scannerImageSharpnessReadiness(999f), 0.0001f)
        assertEquals(0f, scannerImageSharpnessReadiness(-10f), 0.0001f)
        assertEquals(0.45f, scannerLightingReadiness(0.5f, 0.1f), 0.0001f)
    }

    @Test
    fun admissionRejectsRapidDuplicateAcceptedFrame() {
        val first = qualityFrame(
            id = "000001",
            timestampNs = 1_000_000_000L,
            pose = poseWithTranslationMeters(0f)
        )
        val duplicate = qualityFrame(
            id = "000002",
            timestampNs = 1_120_000_000L,
            pose = poseWithTranslationMeters(0.005f)
        )

        val admitted = applyScannerFrameAdmissionQuality(duplicate, listOf(first))

        assertTrue(admitted.quality.rejectionReasons.contains("capture_too_fast"))
        assertTrue(admitted.quality.rejectionReasons.contains("insufficient_view_change"))
    }

    @Test
    fun admissionKeepsSeparatedView() {
        val first = qualityFrame(
            id = "000001",
            timestampNs = 1_000_000_000L,
            pose = poseWithTranslationMeters(0f)
        )
        val next = qualityFrame(
            id = "000002",
            timestampNs = 2_000_000_000L,
            pose = poseWithTranslationMeters(0.08f)
        )

        val admitted = applyScannerFrameAdmissionQuality(next, listOf(first))

        assertTrue(admitted.quality.accepted)
        assertTrue(admitted.quality.rejectionReasons.isEmpty())
    }

    @Test
    fun admissionRejectsWeakDepthWhenDepthExists() {
        val frame = qualityFrame(
            id = "000001",
            timestampNs = 1_000_000_000L,
            pose = poseWithTranslationMeters(0f),
            depthCoverage = 0.03f
        )

        val admitted = applyScannerFrameAdmissionQuality(frame, emptyList())

        assertTrue(admitted.quality.rejectionReasons.contains("depth_coverage_weak"))
    }

    private fun qualityFrame(
        id: String,
        timestampNs: Long,
        pose: FloatArray?,
        depthCoverage: Float? = 0.50f
    ): ScanFrame =
        ScanFrame(
            id = id,
            timestampNs = timestampNs,
            rgbPath = "frames/$id.jpg",
            rgbSha256 = "hash-$id",
            maskPath = null,
            maskSha256 = null,
            depth16Path = depthCoverage?.let { "frames/${id}_depth.png" },
            depthSha256 = depthCoverage?.let { "depth-$id" },
            depthConfidencePath = depthCoverage?.let { "frames/${id}_confidence.png" },
            poseWorldFromCamera = pose,
            intrinsics = null,
            distortion = null,
            lensFacing = "back",
            focalLengthMm = null,
            exposureTimeNs = null,
            iso = null,
            focusDistance = null,
            whiteBalanceMode = null,
            width = 100,
            height = 100,
            capturePass = ScannerCapturePass.MidRing,
            quality = FrameQuality(
                blurScore = 300f,
                exposureScore = 0.8f,
                overlapScore = 0.5f,
                trackingGood = true,
                depthCoverage = depthCoverage,
                markerVisibility = 0f,
                scaleConfidence = 0f,
                materialRisk = 0f,
                accepted = true,
                rejectionReasons = emptyList()
            ),
            forcedCapture = false
        )

    private fun poseWithTranslationMeters(x: Float): FloatArray =
        floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            x, 0f, 0f, 1f
        )
}

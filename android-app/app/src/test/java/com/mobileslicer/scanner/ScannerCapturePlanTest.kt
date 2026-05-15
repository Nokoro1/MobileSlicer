package com.mobileslicer.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScannerCapturePlanTest {
    @Test
    fun emptyScanStartsAtMidRing() {
        assertEquals(ScannerCapturePass.MidRing, nextPrintableCapturePass(emptyList()))
    }

    @Test
    fun rejectedFramesDoNotSatisfyCaptureTargets() {
        val rejectedMidFrames = List(20) { index ->
            frame(
                id = "mid_rejected_$index",
                pass = ScannerCapturePass.MidRing,
                accepted = false
            )
        }

        assertEquals(ScannerCapturePass.MidRing, nextPrintableCapturePass(rejectedMidFrames))
    }

    @Test
    fun advancesThroughPrintablePassesUsingAcceptedFramesOnly() {
        val midComplete = List(12) { index ->
            frame(id = "mid_$index", pass = ScannerCapturePass.MidRing)
        }
        assertEquals(ScannerCapturePass.HighRing, nextPrintableCapturePass(midComplete))

        val highComplete = midComplete + List(12) { index ->
            frame(id = "high_$index", pass = ScannerCapturePass.HighRing)
        }
        assertEquals(ScannerCapturePass.LowRing, nextPrintableCapturePass(highComplete))

        val lowComplete = highComplete + List(12) { index ->
            frame(id = "low_$index", pass = ScannerCapturePass.LowRing)
        }
        assertEquals(ScannerCapturePass.TopDetail, nextPrintableCapturePass(lowComplete))
    }

    @Test
    fun returnsNullWhenPrintableCapturePlanIsComplete() {
        val completePlan =
            List(12) { index -> frame(id = "mid_$index", pass = ScannerCapturePass.MidRing) } +
                List(12) { index -> frame(id = "high_$index", pass = ScannerCapturePass.HighRing) } +
                List(12) { index -> frame(id = "low_$index", pass = ScannerCapturePass.LowRing) } +
                List(8) { index -> frame(id = "detail_$index", pass = ScannerCapturePass.TopDetail) }

        assertNull(nextPrintableCapturePass(completePlan))
    }

    private fun frame(
        id: String,
        pass: ScannerCapturePass,
        accepted: Boolean = true
    ): ScanFrame =
        ScanFrame(
            id = id,
            timestampNs = 1_000_000_000L,
            rgbPath = "frames/$id.jpg",
            rgbSha256 = "hash-$id",
            maskPath = null,
            maskSha256 = null,
            depth16Path = null,
            depthSha256 = null,
            depthConfidencePath = null,
            poseWorldFromCamera = null,
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
            capturePass = pass,
            quality = FrameQuality(
                blurScore = 300f,
                exposureScore = 0.8f,
                overlapScore = 0.5f,
                trackingGood = true,
                depthCoverage = null,
                markerVisibility = 0f,
                scaleConfidence = 0f,
                materialRisk = 0f,
                accepted = accepted,
                rejectionReasons = if (accepted) emptyList() else listOf("insufficient_view_change")
            ),
            forcedCapture = false
        )
}

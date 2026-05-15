package com.mobileslicer.scanner

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerSoftMaskTest {
    @Test
    fun heuristicSoftMaskMarksContrastingCenterAsForeground() {
        val width = 20
        val height = 20
        val luma = IntArray(width * height) { 40 }
        for (y in 7 until 13) {
            for (x in 7 until 13) {
                luma[y * width + x] = 220
            }
        }

        val mask = heuristicSoftMaskAlpha(width, height, luma)

        assertEquals(width * height, mask.alpha.size)
        assertTrue(mask.coverageRatio > 0.05f)
        assertTrue(mask.coverageRatio < 0.75f)
        assertTrue(mask.centerSupport > 0.40f)
        assertTrue(mask.warnings.contains("heuristic_soft_mask_not_ai"))
        assertTrue(mask.warnings.contains("mask_is_soft_evidence_only"))
    }

    @Test
    fun heuristicSoftMaskWarnsWhenCoverageIsTooHigh() {
        val width = 30
        val height = 30
        val luma = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (x == 0 || y == 0 || x == width - 1 || y == height - 1) 0 else 255
        }

        val mask = heuristicSoftMaskAlpha(width, height, luma)

        assertTrue(mask.coverageRatio > 0.65f)
        assertTrue(mask.warnings.contains("mask_coverage_high"))
    }

    @Test
    fun scannerAnalyzeSoftMaskAlphaReportsCoverageWithoutHeuristicWarnings() {
        val width = 10
        val height = 10
        val alpha = ByteArray(width * height) { index ->
            if (index % 3 == 0) 255.toByte() else 0.toByte()
        }

        val mask = scannerAnalyzeSoftMaskAlpha(width, height, alpha)

        assertTrue(mask.coverageRatio > 0.30f)
        assertTrue(mask.coverageRatio < 0.40f)
        assertTrue(mask.warnings.none { it == "heuristic_soft_mask_not_ai" })
    }

    @Test
    fun objectRoiClampsCoordinatesAndRecordsSource() {
        val roi = ScannerObjectRoi(
            xNormalized = -0.25f,
            yNormalized = 1.25f,
            source = ScannerObjectRoiSource.UserTap
        ).clamped()

        assertEquals(0f, roi.xNormalized)
        assertEquals(1f, roi.yNormalized)
        assertEquals(ScannerObjectRoiSource.UserTap, roi.source)
        assertTrue(scannerObjectRoiWarnings(roi).contains("user_object_tap_recorded"))
    }

    @Test
    fun defaultObjectRoiIsLabeledWeakEvidence() {
        val roi = defaultScannerObjectRoi()

        assertEquals(0.5f, roi.xNormalized)
        assertEquals(0.5f, roi.yNormalized)
        assertEquals(ScannerObjectRoiSource.DefaultCenter, roi.source)
        assertTrue(scannerObjectRoiWarnings(roi).contains("default_center_roi_weak_evidence"))
    }

    @Test
    fun mediaPipeStatusRequiresApprovedMagicTouchHashAndSize() {
        val missing = scannerMediaPipeInteractiveSegmenterStatus(
            ScannerModelAssetInspection(exists = false)
        )
        assertFalse(missing.productionReady)
        assertEquals("model_asset_missing", missing.status)

        val wrongSize = scannerMediaPipeInteractiveSegmenterStatus(
            ScannerModelAssetInspection(
                exists = true,
                byteSize = 4,
                sha256 = SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_EXPECTED_SHA256
            )
        )
        assertFalse(wrongSize.productionReady)
        assertEquals("model_asset_size_mismatch", wrongSize.status)

        val verified = scannerMediaPipeInteractiveSegmenterStatus(
            ScannerModelAssetInspection(
                exists = true,
                byteSize = SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_EXPECTED_BYTES,
                sha256 = SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_EXPECTED_SHA256
            )
        )
        assertTrue(verified.available)
        assertTrue(verified.productionReady)
        assertEquals("model_asset_verified", verified.status)
    }

    @Test
    fun modelAssetInspectionComputesShaAndSize() {
        val inspection = inspectScannerModelAsset(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)))

        assertTrue(inspection.exists)
        assertEquals(4L, inspection.byteSize)
        assertEquals(sha256Hex(byteArrayOf(1, 2, 3, 4)), inspection.sha256)
    }
}

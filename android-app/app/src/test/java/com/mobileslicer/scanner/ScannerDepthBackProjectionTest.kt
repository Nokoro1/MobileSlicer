package com.mobileslicer.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerDepthBackProjectionTest {
    @Test
    fun backProjectionUsesIntrinsicsAtCameraCenter() {
        val points = backProjectScannerDepthSamples(
            frameId = "000001",
            samples = listOf(ScannerDepthPixelSample(xPx = 50, yPx = 40, depthMm = 1_000, confidence = 255)),
            intrinsics = CameraIntrinsicsData(
                fx = 100f,
                fy = 100f,
                cx = 50f,
                cy = 40f,
                imageWidth = 100,
                imageHeight = 80
            ),
            poseWorldFromCamera = null
        )

        assertEquals(1, points.size)
        assertEquals(0.0, points.single().xMm, 0.0001)
        assertEquals(0.0, points.single().yMm, 0.0001)
        assertEquals(1_000.0, points.single().zMm, 0.0001)
        assertEquals("measured_high", points.single().provenanceClass)
    }

    @Test
    fun backProjectionAppliesWorldPoseTranslation() {
        val poseWorldFromCamera = floatArrayOf(
            1f, 0f, 0f, 10f,
            0f, 1f, 0f, 20f,
            0f, 0f, 1f, 30f,
            0f, 0f, 0f, 1f
        )

        val point = backProjectScannerDepthSamples(
            frameId = "000001",
            samples = listOf(ScannerDepthPixelSample(xPx = 60, yPx = 50, depthMm = 1_000, confidence = 128)),
            intrinsics = CameraIntrinsicsData(
                fx = 100f,
                fy = 100f,
                cx = 50f,
                cy = 40f,
                imageWidth = 100,
                imageHeight = 80
            ),
            poseWorldFromCamera = poseWorldFromCamera
        ).single()

        assertEquals(110.0, point.xMm, 0.0001)
        assertEquals(120.0, point.yMm, 0.0001)
        assertEquals(1_030.0, point.zMm, 0.0001)
        assertEquals("measured_low", point.provenanceClass)
    }

    @Test
    fun backProjectionRejectsWeakOrInvalidDepthSamples() {
        val points = backProjectScannerDepthSamples(
            frameId = "000001",
            samples = listOf(
                ScannerDepthPixelSample(xPx = 50, yPx = 40, depthMm = 0, confidence = 255),
                ScannerDepthPixelSample(xPx = 50, yPx = 40, depthMm = 1_000, confidence = 12),
                ScannerDepthPixelSample(xPx = 50, yPx = 40, depthMm = 1_000, confidence = 255)
            ),
            intrinsics = CameraIntrinsicsData(
                fx = 100f,
                fy = 100f,
                cx = 50f,
                cy = 40f,
                imageWidth = 100,
                imageHeight = 80
            ),
            poseWorldFromCamera = null
        )

        assertEquals(1, points.size)
        assertEquals(255, points.single().confidence)
    }

    @Test
    fun uniformDepthSamplerRespectsStrideAndUnsignedConfidence() {
        val samples = scannerUniformDepthSamples(
            depthMm = intArrayOf(
                100, 200, 300, 400,
                500, 600, 700, 800
            ),
            confidence = byteArrayOf(0, 64, 127, (-1).toByte(), 1, 2, 3, 4),
            width = 4,
            height = 2,
            stride = 2
        )

        assertEquals(2, samples.size)
        assertEquals(100, samples[0].depthMm)
        assertEquals(0, samples[0].confidence)
        assertEquals(300, samples[1].depthMm)
        assertEquals(127, samples[1].confidence)
    }

    @Test
    fun depthCoverageUsesValidDepthAndConfidenceInsideMask() {
        val coverage = scannerDepthCoverage(
            samples = listOf(
                ScannerDepthPixelSample(0, 0, depthMm = 100, confidence = 100),
                ScannerDepthPixelSample(0, 1, depthMm = 0, confidence = 100),
                ScannerDepthPixelSample(0, 2, depthMm = 100, confidence = 0)
            ),
            maskPixelCount = 4
        )

        assertEquals(0.25f, coverage, 0.0001f)
    }

    @Test
    fun quantizedKeyIsStableForVoxelDeduplication() {
        val point = ScannerBackProjectedDepthPoint(
            pointId = "p",
            frameId = "f",
            xMm = 1.49,
            yMm = 2.51,
            zMm = 10.0,
            sourceXPx = 1,
            sourceYPx = 2,
            depthMm = 10,
            confidence = 255,
            provenanceClass = "measured_high"
        )

        assertTrue(point.quantizedKey(1f).isNotBlank())
        assertEquals("1:3:10", point.quantizedKey(1f))
    }

    @Test
    fun scaledIntrinsicsMatchDepthImageResolution() {
        val scaled = scaledScannerIntrinsicsForDepth(
            intrinsics = CameraIntrinsicsData(
                fx = 1000f,
                fy = 800f,
                cx = 500f,
                cy = 400f,
                imageWidth = 1000,
                imageHeight = 800
            ),
            depthWidth = 250,
            depthHeight = 200
        )

        assertEquals(250f, scaled.fx, 0.0001f)
        assertEquals(200f, scaled.fy, 0.0001f)
        assertEquals(125f, scaled.cx, 0.0001f)
        assertEquals(100f, scaled.cy, 0.0001f)
        assertEquals(250, scaled.imageWidth)
        assertEquals(200, scaled.imageHeight)
    }
}

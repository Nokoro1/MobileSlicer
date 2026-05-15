package com.mobileslicer.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerCalibrationTest {
    @Test
    fun printedScaleVerificationAcceptsWithinOnePercent() {
        val result = verifyPrintedScale(
            PrintedScaleVerificationInput(
                expectedMm = 100f,
                measuredMm = 99.2f
            )
        )

        assertTrue(result.valid)
        assertEquals(1.0080645f, result.scaleCorrection, 0.0001f)
        assertEquals(0.008f, result.scaleErrorRatio, 0.0001f)
    }

    @Test
    fun printedScaleVerificationRejectsPrinterScalingError() {
        val result = verifyPrintedScale(
            PrintedScaleVerificationInput(
                expectedMm = 100f,
                measuredMm = 96.5f
            )
        )

        assertFalse(result.valid)
        assertEquals(0.035f, result.scaleErrorRatio, 0.0001f)
    }

    @Test
    fun printableCalibrationGateAcceptsVerifiedMarkerMat() {
        val markerEvidence = summarizeMarkerEvidence(
            observations = markerObservations(),
            acceptedFrameCount = 2,
            detectorName = "test_detector",
            detectorStatus = "ready"
        )
        val result = evaluatePrintableCalibrationGate(
            scaleSource = ScannerScaleSource.VerifiedMarkerMat,
            calibration = ScannerCalibration(
                markerType = ScannerMarkerSystem.AprilTag.manifestValue,
                markerSizeMm = 40f,
                printedScaleBarExpectedMm = 100f,
                printedScaleBarMeasuredMm = 99.2f,
                scaleCorrection = 1.0080645f,
                scaleConfidence = 0.91f,
                markerReprojectionErrorPx = 1.6f
            ),
            alignment = ScannerTwoSidedAlignment(
                objectMovedDuringSession = false,
                alignmentMethod = ScannerAlignmentMethod.NotMoved,
                alignmentConfidence = null,
                passCount = 1
            ),
            markerEvidence = markerEvidence
        )

        assertTrue(result.reasons.joinToString(), result.allowed)
    }

    @Test
    fun printableCalibrationGateRejectsDepthOnlyScale() {
        val result = evaluatePrintableCalibrationGate(
            scaleSource = ScannerScaleSource.ArCoreDepthAssist,
            calibration = null,
            alignment = ScannerTwoSidedAlignment(
                objectMovedDuringSession = false,
                alignmentMethod = ScannerAlignmentMethod.NotMoved,
                alignmentConfidence = null,
                passCount = 1
            )
        )

        assertFalse(result.allowed)
        assertTrue(result.reasons.contains("scale_source_not_printable"))
        assertTrue(result.reasons.contains("calibration_missing"))
        assertTrue(result.reasons.contains("marker_detector_not_ready"))
    }

    @Test
    fun printableCalibrationGateRejectsUnknownTwoSidedAlignment() {
        val result = evaluatePrintableCalibrationGate(
            scaleSource = ScannerScaleSource.VerifiedMarkerMat,
            calibration = ScannerCalibration(
                markerType = ScannerMarkerSystem.AprilTag.manifestValue,
                markerSizeMm = 40f,
                printedScaleBarExpectedMm = 100f,
                printedScaleBarMeasuredMm = 100f,
                scaleCorrection = 1f,
                scaleConfidence = 0.9f,
                markerReprojectionErrorPx = 1.2f
            ),
            alignment = ScannerTwoSidedAlignment(
                objectMovedDuringSession = true,
                alignmentMethod = ScannerAlignmentMethod.Unknown,
                alignmentConfidence = null,
                passCount = 2
            ),
            markerEvidence = summarizeMarkerEvidence(
                observations = markerObservations(),
                acceptedFrameCount = 2,
                detectorName = "test_detector",
                detectorStatus = "ready"
            )
        )

        assertFalse(result.allowed)
        assertEquals(listOf("two_sided_alignment_unknown"), result.reasons)
    }

    @Test
    fun markerEvidenceSummarizesObservationCoverage() {
        val evidence = summarizeMarkerEvidence(
            observations = markerObservations(),
            acceptedFrameCount = 2,
            detectorName = "test_detector",
            detectorStatus = "ready"
        )

        assertEquals(4, evidence.observationCount)
        assertEquals(4, evidence.calibratedObservationCount)
        assertEquals(2, evidence.observedFrameCount)
        assertEquals(4, evidence.uniqueMarkerCount)
        assertEquals(1.25f, evidence.averageReprojectionErrorPx ?: 0f, 0.0001f)
        assertTrue(evidence.markerScaleConfidence > 0f)
        assertTrue(evidence.reasons.isEmpty())
    }

    @Test
    fun markerEvidenceFailsClosedWhenDetectorIsNotReady() {
        val evidence = summarizeMarkerEvidence(
            observations = emptyList(),
            acceptedFrameCount = 3,
            detectorName = "apriltag_36h11_jni",
            detectorStatus = "not_wired_fail_closed"
        )

        assertEquals(0f, evidence.markerScaleConfidence, 0.0001f)
        assertEquals(
            listOf("marker_detector_not_ready", "markers_not_detected", "marker_reprojection_missing"),
            evidence.reasons
        )
    }

    @Test
    fun markerPoseEstimatesLowReprojectionForCalibratedFrame() {
        val observation = markerObservationFromProjection(
            markerId = "12",
            frameId = "000010",
            intrinsics = testIntrinsics(),
            markerSizeMm = 40f,
            zMm = 500f
        )

        val pose = estimateMarkerPose(observation, testIntrinsics())

        assertTrue(pose != null)
        pose ?: return
        assertTrue("Expected low reprojection error, got ${pose.reprojectionErrorPx}", pose.reprojectionErrorPx < 0.05f)
        assertEquals(500f, pose.distanceMm, 8f)
    }

    @Test
    fun markerCalibrationAddsReprojectionOnlyWhenIntrinsicsExist() {
        val withIntrinsics = testFrame("000001", intrinsics = testIntrinsics())
        val withoutIntrinsics = testFrame("000002", intrinsics = null)
        val calibrated = calibrateMarkerObservations(
            observations = listOf(
                markerObservationFromProjection("0", "000001", testIntrinsics(), 40f, 500f),
                markerObservationFromProjection("1", "000002", testIntrinsics(), 40f, 500f)
            ),
            frames = listOf(withIntrinsics, withoutIntrinsics)
        )
        val evidence = summarizeMarkerEvidence(
            observations = calibrated,
            acceptedFrameCount = 2,
            detectorName = "test_detector",
            detectorStatus = "ready"
        )

        assertEquals(2, evidence.observationCount)
        assertEquals(1, evidence.calibratedObservationCount)
        assertTrue(evidence.reasons.contains("marker_pose_intrinsics_missing"))
        assertFalse(evidence.reasons.contains("marker_reprojection_missing"))
    }

    private fun markerObservations(): List<MarkerObservation> =
        listOf(
            markerObservation("0", "000001", 1.0f),
            markerObservation("1", "000001", 1.2f),
            markerObservation("2", "000002", 1.3f),
            markerObservation("3", "000002", 1.5f)
        )

    private fun markerObservation(markerId: String, frameId: String, reprojectionErrorPx: Float): MarkerObservation =
        MarkerObservation(
            markerSystem = ScannerMarkerSystem.AprilTag,
            markerId = markerId,
            frameId = frameId,
            framePath = "frames/$frameId.jpg",
            markerSizeMm = 40f,
            cornersPx = listOf(
                0f to 0f,
                40f to 0f,
                40f to 40f,
                0f to 40f
            ),
            hamming = 0,
            decisionMargin = 80f,
            reprojectionErrorPx = reprojectionErrorPx
        )

    private fun markerObservationFromProjection(
        markerId: String,
        frameId: String,
        intrinsics: CameraIntrinsicsData,
        markerSizeMm: Float,
        zMm: Float
    ): MarkerObservation {
        val size = markerSizeMm
        val cornersMm = listOf(
            0f to 0f,
            size to 0f,
            size to size,
            0f to size
        )
        return MarkerObservation(
            markerSystem = ScannerMarkerSystem.AprilTag,
            markerId = markerId,
            frameId = frameId,
            framePath = "frames/$frameId.jpg",
            markerSizeMm = markerSizeMm,
            cornersPx = cornersMm.map { corner ->
                intrinsics.fx * (corner.first / zMm) + intrinsics.cx to
                    intrinsics.fy * (corner.second / zMm) + intrinsics.cy
            },
            hamming = 0,
            decisionMargin = 80f
        )
    }

    private fun testIntrinsics(): CameraIntrinsicsData =
        CameraIntrinsicsData(
            fx = 1000f,
            fy = 1000f,
            cx = 640f,
            cy = 360f,
            imageWidth = 1280,
            imageHeight = 720
        )

    private fun testFrame(frameId: String, intrinsics: CameraIntrinsicsData?): ScanFrame =
        ScanFrame(
            id = frameId,
            timestampNs = 1L,
            rgbPath = "frames/$frameId.jpg",
            rgbSha256 = "hash-$frameId",
            maskPath = null,
            maskSha256 = null,
            depth16Path = null,
            depthSha256 = null,
            depthConfidencePath = null,
            poseWorldFromCamera = null,
            intrinsics = intrinsics,
            distortion = null,
            lensFacing = "back",
            focalLengthMm = null,
            exposureTimeNs = null,
            iso = null,
            focusDistance = null,
            whiteBalanceMode = null,
            width = intrinsics?.imageWidth ?: 1280,
            height = intrinsics?.imageHeight ?: 720,
            quality = FrameQuality(
                blurScore = 180f,
                exposureScore = 0.9f,
                overlapScore = 0f,
                trackingGood = null,
                depthCoverage = null,
                markerVisibility = 0f,
                scaleConfidence = 0f,
                materialRisk = 0f,
                accepted = true
            ),
            forcedCapture = false
        )
}

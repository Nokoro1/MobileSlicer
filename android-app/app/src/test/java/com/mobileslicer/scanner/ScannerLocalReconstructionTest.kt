package com.mobileslicer.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerLocalReconstructionTest {
    @Test
    fun preflightBlocksUnderCapturedPackageBeforeLocalReconstruction() {
        val manifest = reconstructionManifest(
            frames = listOf(reconstructionFrame("000001", ScannerCapturePass.MidRing)),
            hasMasks = false,
            scaleSource = ScannerScaleSource.None,
            calibration = null
        )

        val result = evaluateLocalReconstructionPreflight(manifest)

        assertFalse(result.allowed)
        assertTrue(result.reasons.contains("not_enough_accepted_frames"))
        assertTrue(result.reasons.contains("scale_unverified"))
        assertTrue(result.reasons.contains("calibration_missing"))
        assertTrue(result.reasons.contains("object_masks_missing"))
    }

    @Test
    fun preflightAllowsCompleteCalibratedMaskedEvidencePackage() {
        val frames = (0 until 36).map { index ->
            val pass = when (index % 3) {
                0 -> ScannerCapturePass.LowRing
                1 -> ScannerCapturePass.MidRing
                else -> ScannerCapturePass.HighRing
            }
            reconstructionFrame(index.toString().padStart(6, '0'), pass, hasMask = true)
        }
        val manifest = reconstructionManifest(frames = frames, hasMasks = true)

        val result = evaluateLocalReconstructionPreflight(manifest)

        assertTrue(result.reasons.joinToString(), result.allowed)
        assertTrue(result.acceptedPasses.containsAll(ScannerLocalReconstructionLimits().requiredPasses))
    }

    @Test
    fun preflightCanStageUncalibratedEvidenceWhenCalibrationIsDeferred() {
        val frames = (0 until 36).map { index ->
            val pass = when (index % 3) {
                0 -> ScannerCapturePass.LowRing
                1 -> ScannerCapturePass.MidRing
                else -> ScannerCapturePass.HighRing
            }
            reconstructionFrame(index.toString().padStart(6, '0'), pass, hasMask = true)
        }
        val manifest = reconstructionManifest(
            frames = frames,
            hasMasks = true,
            scaleSource = ScannerScaleSource.None,
            calibration = null
        )

        val result = evaluateLocalReconstructionPreflight(
            manifest,
            ScannerLocalReconstructionLimits(
                requireVerifiedScale = false,
                minimumScaleConfidence = 0f
            )
        )

        assertTrue(result.reasons.joinToString(), result.allowed)
        assertEquals(ScannerScaleSource.None, result.scaleSource)
    }

    @Test
    fun preflightBlocksUnknownUndersideAlignment() {
        val frames = (0 until 36).map { index ->
            reconstructionFrame(
                id = index.toString().padStart(6, '0'),
                pass = when (index % 3) {
                    0 -> ScannerCapturePass.LowRing
                    1 -> ScannerCapturePass.MidRing
                    else -> ScannerCapturePass.HighRing
                },
                hasMask = true
            )
        }
        val manifest = reconstructionManifest(
            frames = frames,
            hasMasks = true,
            alignment = ScannerTwoSidedAlignment(
                objectMovedDuringSession = true,
                alignmentMethod = ScannerAlignmentMethod.Unknown,
                alignmentConfidence = null,
                passCount = 4
            )
        )

        val result = evaluateLocalReconstructionPreflight(manifest)

        assertFalse(result.allowed)
        assertTrue(result.reasons.contains("two_sided_alignment_unknown"))
    }

    private fun reconstructionManifest(
        frames: List<ScanFrame>,
        hasMasks: Boolean,
        scaleSource: ScannerScaleSource = ScannerScaleSource.VerifiedMarkerMat,
        calibration: ScannerCalibration? = ScannerCalibration(
            markerType = "apriltag",
            markerSizeMm = 40f,
            printedScaleBarExpectedMm = 100f,
            printedScaleBarMeasuredMm = 100f,
            scaleCorrection = 1f,
            scaleConfidence = 0.90f,
            markerReprojectionErrorPx = 1.4f
        ),
        alignment: ScannerTwoSidedAlignment = ScannerTwoSidedAlignment(
            objectMovedDuringSession = false,
            alignmentMethod = ScannerAlignmentMethod.NotMoved,
            alignmentConfidence = null,
            passCount = 3
        )
    ): ScanPackageManifest =
        ScanPackageManifest(
            scanId = "scan_reconstruction",
            createdAtIso8601 = "2026-05-08T12:00:00Z",
            mode = ScannerMode.LocalPrintable,
            captureProfile = ScannerCaptureProfile.HighResPhoto,
            frameCount = frames.size,
            acceptedFrameCount = frames.count { it.quality.accepted },
            forcedFrameCount = frames.count { it.forcedCapture },
            rejectedFrameCount = frames.count { !it.quality.accepted },
            hasArCorePoses = false,
            hasDepth = false,
            hasMasks = hasMasks,
            scaleSource = scaleSource,
            calibration = calibration,
            twoSidedAlignment = alignment,
            requestedOutputs = listOf("stl", "3mf"),
            frames = frames
        )

    private fun reconstructionFrame(
        id: String,
        pass: ScannerCapturePass,
        hasMask: Boolean = false
    ): ScanFrame =
        ScanFrame(
            id = id,
            timestampNs = 123456789L,
            rgbPath = "frames/$id.jpg",
            rgbSha256 = "sha-$id",
            maskPath = if (hasMask) "frames/${id}_mask.png" else null,
            maskSha256 = if (hasMask) "mask-sha-$id" else null,
            depth16Path = null,
            depthSha256 = null,
            depthConfidencePath = null,
            poseWorldFromCamera = null,
            intrinsics = CameraIntrinsicsData(
                fx = 1430f,
                fy = 1430f,
                cx = 960f,
                cy = 540f,
                imageWidth = 1920,
                imageHeight = 1080
            ),
            distortion = null,
            lensFacing = "back",
            focalLengthMm = 5.4f,
            exposureTimeNs = 8333333L,
            iso = 160,
            focusDistance = 0.7f,
            whiteBalanceMode = "auto",
            width = 1920,
            height = 1080,
            capturePass = pass,
            quality = FrameQuality(
                blurScore = 180f,
                exposureScore = 0.92f,
                overlapScore = 0.7f,
                trackingGood = null,
                depthCoverage = null,
                markerVisibility = 0.7f,
                scaleConfidence = 0.90f,
                materialRisk = 0.1f,
                accepted = true
            ),
            forcedCapture = false
        )
}

package com.mobileslicer.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerDiagnosticsTest {
    @Test
    fun readinessSummaryTracksCoverageAndScaleGate() {
        val manifest = diagnosticManifest(
            frames = listOf(
                diagnosticFrame("000001", ScannerCapturePass.MidRing, accepted = true),
                diagnosticFrame("000002", ScannerCapturePass.LowRing, accepted = true),
                diagnosticFrame("000003", ScannerCapturePass.HighRing, accepted = false)
            )
        )

        val readiness = scannerReadinessSummary(
            frames = manifest.frames,
            calibrationGate = PrintableCalibrationGateResult(allowed = true, reasons = emptyList())
        )

        assertEquals(0.4f, readiness.coverageReadiness, 0.0001f)
        assertEquals(1f, readiness.scaleReadiness, 0.0001f)
        assertEquals(0f, readiness.maskReadiness, 0.0001f)
        assertTrue(readiness.captureReadiness > 0f)
    }

    @Test
    fun depthPoseReadinessScoresAcceptedPoseAndDepthEvidence() {
        val frameWithPoseAndDepth = diagnosticFrame("000001", ScannerCapturePass.MidRing, accepted = true).copy(
            poseWorldFromCamera = floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
            ),
            quality = diagnosticFrame("000001", ScannerCapturePass.MidRing, accepted = true).quality.copy(
                depthCoverage = 0.80f
            )
        )
        val frameWithoutPose = diagnosticFrame("000002", ScannerCapturePass.LowRing, accepted = true)

        val readiness = scannerDepthPoseReadiness(listOf(frameWithPoseAndDepth, frameWithoutPose))

        assertTrue(readiness > 0.40f)
        assertTrue(readiness < 1.0f)
    }

    @Test
    fun maskQualitySummaryRewardsConsistentAiMasksAndReportsFallbackRisk() {
        val summary = scannerMaskQualitySummary(
            listOf(
                softMask("000001", generator = "mediapipe_interactive_segmenter_v1", coverage = 0.24f),
                softMask("000002", generator = "mediapipe_interactive_segmenter_v1", coverage = 0.26f),
                softMask(
                    "000003",
                    generator = "heuristic_center_background_soft_mask_v1",
                    coverage = 0.25f,
                    warnings = listOf("heuristic_soft_mask_not_ai")
                )
            )
        )

        assertEquals(3, summary.maskCount)
        assertEquals(2, summary.aiMaskCount)
        assertEquals(1, summary.heuristicMaskCount)
        assertTrue(summary.consistency > 0.65f)
        assertTrue(summary.warnings.contains("heuristic_masks_present"))
    }

    @Test
    fun captureGuidanceReportsActionableMessagesAndBlockingReasons() {
        val manifest = diagnosticManifest(
            frames = listOf(
                diagnosticFrame("000001", ScannerCapturePass.MidRing, accepted = true),
                diagnosticFrame("000002", ScannerCapturePass.LowRing, accepted = false)
            )
        )
        val maskSummary = scannerMaskQualitySummary(emptyList())
        val readiness = scannerReadinessSummary(
            frames = manifest.frames,
            calibrationGate = PrintableCalibrationGateResult(allowed = false, reasons = listOf("scale_unverified")),
            maskSummary = maskSummary
        )

        val guidance = scannerCaptureGuidanceReport(
            manifest = manifest,
            readiness = readiness,
            maskSummary = maskSummary,
            depthPriorStatus = scannerDepthAnythingV2SmallStatus { false }
        )

        assertTrue(guidance.messages.contains("Move slower"))
        assertTrue(guidance.messages.contains("Show the verified marker mat clearly"))
        assertTrue(guidance.messages.contains("Depth prior unavailable; capture extra angles"))
        assertTrue(guidance.blockingReasons.contains("not_enough_accepted_frames"))
        assertTrue(guidance.blockingReasons.contains("scale_not_ready"))
    }

    @Test
    fun localAiStatusJsonRecordsMaskDepthAndGuidanceContracts() {
        val maskSummary = scannerMaskQualitySummary(listOf(softMask("000001")))
        val depth = scannerDepthAnythingV2SmallStatus { it == SCANNER_DEPTH_ANYTHING_V2_SMALL_MODEL }
        val guidance = ScannerCaptureGuidanceReport(
            messages = listOf("Need high ring views"),
            blockingReasons = listOf("coverage_incomplete"),
            warnings = listOf("mask_is_soft_evidence_only")
        )

        val json = localAiStatusJson(maskSummary, depth, guidance)

        assertEquals("blocked", json.getJSONObject("stack").getString("overall_state"))
        assertFalse(json.getJSONObject("stack").getBoolean("metric_truth"))
        assertTrue(json.getJSONObject("depth_prior").getBoolean("available"))
        assertFalse(json.getJSONObject("depth_prior").getBoolean("metric_truth"))
        assertEquals("Need high ring views", json.getJSONObject("guidance").getJSONArray("messages").getString(0))
        assertTrue(json.getJSONObject("mask_quality").getDouble("consistency") > 0.0)
    }

    @Test
    fun localAiStackSeparatesVerifiedSegmentationFromDepthPrior() {
        val stack = scannerLocalAiStackStatus(
            maskSummary = scannerMaskQualitySummary(
                listOf(
                    softMask("000001", generator = "mediapipe_interactive_segmenter_v1"),
                    softMask("000002", generator = "mediapipe_interactive_segmenter_v1")
                )
            ),
            mediaPipeStatus = ScannerMediaPipeSoftMaskStatus(
                available = true,
                status = "model_asset_verified",
                detail = "verified",
                productionReady = true
            ),
            depthPriorStatus = scannerDepthAnythingV2SmallStatus { false },
            guidance = ScannerCaptureGuidanceReport(
                messages = listOf("Need high ring views"),
                blockingReasons = emptyList(),
                warnings = emptyList()
            )
        )

        assertEquals(ScannerLocalAiComponentState.Ready, stack.overallState)
        assertTrue(stack.productionReady)
        assertFalse(stack.metricTruth)
        assertTrue(stack.components.any { it.name == "monocular_depth_prior" && it.state == ScannerLocalAiComponentState.Disabled })
    }

    @Test
    fun deviceCapabilitiesJsonReportsUnavailableCameraDepthWhenContextIsMissing() {
        val json = deviceCapabilitiesJson(context = null)
        val camera2Depth = json.getJSONObject("camera2_depth_output")

        assertFalse(camera2Depth.getBoolean("checked"))
        assertFalse(camera2Depth.getBoolean("depth_output_supported"))
        assertFalse(camera2Depth.getBoolean("back_camera_checked"))
        assertEquals("context_unavailable", camera2Depth.getString("reason"))
        assertEquals("context_unavailable", json.getJSONObject("arcore").getString("status"))
    }

    @Test
    fun deviceDepthCapabilityJsonRecordsCheckedDepthSupport() {
        val json = scannerDeviceDepthCapabilityJson(
            ScannerDeviceDepthCapability(
                checked = true,
                depthOutputSupported = true,
                backCameraChecked = true,
                reason = "depth_output_supported"
            )
        )

        assertTrue(json.getBoolean("checked"))
        assertTrue(json.getBoolean("depth_output_supported"))
        assertTrue(json.getBoolean("back_camera_checked"))
        assertEquals("depth_output_supported", json.getString("reason"))
    }

    @Test
    fun arCoreAvailabilityJsonRecordsOptionalSupportState() {
        val json = scannerArCoreAvailabilityJson(
            ScannerArCoreAvailability(
                checked = true,
                installed = true,
                supported = true,
                transient = false,
                status = "SUPPORTED_INSTALLED"
            )
        )

        assertTrue(json.getBoolean("checked"))
        assertTrue(json.getBoolean("installed"))
        assertTrue(json.getBoolean("supported"))
        assertFalse(json.getBoolean("transient"))
        assertEquals("SUPPORTED_INSTALLED", json.getString("status"))
    }

    @Test
    fun rejectedFramesDiagnosticsIncludeReasons() {
        val manifest = diagnosticManifest(
            frames = listOf(
                diagnosticFrame("000001", ScannerCapturePass.MidRing, accepted = true),
                diagnosticFrame("000002", ScannerCapturePass.LowRing, accepted = false)
            )
        )

        val json = rejectedFramesJson(manifest)
        val rejected = json.getJSONArray("frames").getJSONObject(0)

        assertEquals(1, json.getInt("rejected_frame_count"))
        assertEquals("000002", rejected.getString("frame_id"))
        assertEquals("low_ring", rejected.getString("capture_pass"))
        assertEquals("too_blurry", rejected.getJSONArray("reasons").getString(0))
    }

    @Test
    fun rejectedFramesDiagnosticsCanUseCompactRejectedListOutsideManifest() {
        val acceptedOnlyManifest = diagnosticManifest(
            frames = listOf(diagnosticFrame("000001", ScannerCapturePass.MidRing, accepted = true))
        )
        val rejectedFrame = diagnosticFrame("000099", ScannerCapturePass.HighRing, accepted = false)

        val manifestJson = rejectedFramesJson(acceptedOnlyManifest)
        val compactJson = rejectedFramesJson(listOf(rejectedFrame))

        assertEquals(0, manifestJson.getInt("rejected_frame_count"))
        assertEquals(1, compactJson.getInt("rejected_frame_count"))
        assertEquals("000099", compactJson.getJSONArray("frames").getJSONObject(0).getString("frame_id"))
        assertEquals("high_ring", compactJson.getJSONArray("frames").getJSONObject(0).getString("capture_pass"))
    }

    @Test
    fun markerObservationDiagnosticsIncludeDetectorStatusAndCorners() {
        val observations = listOf(
            MarkerObservation(
                markerSystem = ScannerMarkerSystem.AprilTag,
                markerId = "12",
                frameId = "000001",
                framePath = "frames/000001.jpg",
                markerSizeMm = 40f,
                cornersPx = listOf(1f to 2f, 3f to 4f, 5f to 6f, 7f to 8f),
                hamming = 0,
                decisionMargin = 90f,
                reprojectionErrorPx = 1.4f
            )
        )
        val evidence = summarizeMarkerEvidence(
            observations = observations,
            acceptedFrameCount = 1,
            detectorName = "test_detector",
            detectorStatus = "ready"
        )

        val json = markerObservationsJson(observations, evidence)
        val observation = json.getJSONArray("observations").getJSONObject(0)

        assertEquals("test_detector", json.getString("detector_name"))
        assertEquals("ready", json.getString("detector_status"))
        assertEquals(1, json.getJSONObject("summary").getInt("observation_count"))
        assertEquals("12", observation.getString("marker_id"))
        assertEquals(4, observation.getJSONArray("corners_px").length())
    }

    @Test
    fun markerMatLayoutWritesMetricLayoutAndPreview() {
        val directory = java.nio.file.Files.createTempDirectory("marker-mat").toFile()

        writeMarkerMatAssets(directory)

        assertTrue(directory.resolve("mobile_slicer_marker_mat_layout.json").isFile)
        assertTrue(directory.resolve("mobile_slicer_marker_mat_preview.svg").isFile)
        assertTrue(directory.resolve("README.txt").readText().contains("Printable scale still requires detector observations"))
    }

    @Test
    fun markerMatPreviewEmbedsProvidedTagImages() {
        val directory = java.nio.file.Files.createTempDirectory("marker-mat-tags").toFile()

        writeMarkerMatAssets(directory) { markerId ->
            "marker-$markerId".encodeToByteArray()
        }

        val preview = directory.resolve("mobile_slicer_marker_mat_preview.svg").readText()
        assertTrue(preview.contains("data:image/png;base64,"))
        assertTrue(preview.contains("image-rendering=\"pixelated\""))
    }

    @Test
    fun standardMarkerMatAssetsWriteA4AndUsLetterFiles() {
        val directory = java.nio.file.Files.createTempDirectory("marker-mat-standard").toFile()

        val files = writeStandardMarkerMatAssets(directory) { markerId ->
            "marker-$markerId".encodeToByteArray()
        }

        assertTrue(directory.resolve("mobile_slicer_marker_mat_a4.svg").isFile)
        assertTrue(directory.resolve("mobile_slicer_marker_mat_a4_layout.json").isFile)
        assertTrue(directory.resolve("mobile_slicer_marker_mat_us_letter.svg").isFile)
        assertTrue(directory.resolve("mobile_slicer_marker_mat_us_letter_layout.json").isFile)
        assertTrue(directory.resolve("README.txt").readText().contains("Choose the SVG that matches your printer paper"))
        assertEquals(5, files.size)
    }

    @Test
    fun softMaskDiagnosticsRecordGeneratorAndWarnings() {
        val json = softMasksJson(
            listOf(
                ScannerSoftMaskResult(
                    maskPath = "frames/000001_mask.png",
                    maskSha256 = "abc123",
                    generatorName = "heuristic_center_background_soft_mask_v1",
                    generatorStatus = "ready_non_ai_fallback",
                    objectRoi = ScannerObjectRoi(
                        xNormalized = 0.25f,
                        yNormalized = 0.75f,
                        source = ScannerObjectRoiSource.UserTap
                    ),
                    coverageRatio = 0.22f,
                    edgeUncertainty = 0.14f,
                    centerSupport = 0.77f,
                    warnings = listOf("heuristic_soft_mask_not_ai", "mask_is_soft_evidence_only")
                )
            )
        )

        val mask = json.getJSONArray("masks").getJSONObject(0)
        assertEquals(1, json.getInt("mask_count"))
        assertTrue(json.getBoolean("has_masks"))
        assertEquals("frames/000001_mask.png", mask.getString("mask"))
        assertEquals("heuristic_center_background_soft_mask_v1", mask.getString("generator_name"))
        assertEquals("user_tap", mask.getJSONObject("object_roi").getString("source"))
        assertEquals(0.25, mask.getJSONObject("object_roi").getDouble("tap_x_normalized"), 0.0001)
        assertEquals(0.75, mask.getJSONObject("object_roi").getDouble("tap_y_normalized"), 0.0001)
        assertEquals(2, mask.getJSONArray("warnings").length())
    }

    @Test
    fun coverageDiagnosticsReportEveryRequiredPass() {
        val manifest = diagnosticManifest(
            frames = listOf(diagnosticFrame("000001", ScannerCapturePass.MidRing, accepted = true))
        )
        val readiness = scannerReadinessSummary(
            frames = manifest.frames,
            calibrationGate = PrintableCalibrationGateResult(allowed = false, reasons = listOf("marker_reprojection_missing"))
        )

        val json = coverageMapJson(manifest, readiness)

        assertEquals(ScannerCapturePass.entries.size, json.getJSONArray("passes").length())
        assertFalse(json.getJSONObject("readiness").getDouble("printable") > 0.0)
    }

    @Test
    fun localReconstructionPreflightDiagnosticsRecordBlockedReasons() {
        val json = localReconstructionPreflightJson(
            ScannerLocalReconstructionPreflightResult(
                allowed = false,
                reasons = listOf("not_enough_accepted_frames", "scale_unverified"),
                acceptedFrameCount = 4,
                frameCount = 5,
                acceptedPasses = setOf(ScannerCapturePass.MidRing),
                scaleSource = ScannerScaleSource.None,
                scaleConfidence = 0.0f,
                hasMasks = false,
                averageMaterialRisk = 0.2f,
                forcedFrameRatio = 0.0f,
                alignmentMethod = ScannerAlignmentMethod.NotMoved
            )
        )

        assertFalse(json.getBoolean("allowed"))
        assertEquals(2, json.getJSONArray("reasons").length())
        assertEquals("none", json.getString("scale_source"))
        assertEquals("mid_ring", json.getJSONArray("accepted_passes").getString(0))
    }

    private fun diagnosticManifest(frames: List<ScanFrame>): ScanPackageManifest =
        ScanPackageManifest(
            scanId = "scan_diag",
            createdAtIso8601 = "2026-05-08T12:00:00Z",
            mode = ScannerMode.LocalPrintable,
            captureProfile = ScannerCaptureProfile.HighResPhoto,
            frameCount = frames.size,
            acceptedFrameCount = frames.count { it.quality.accepted },
            forcedFrameCount = frames.count { it.forcedCapture },
            rejectedFrameCount = frames.count { !it.quality.accepted },
            hasArCorePoses = false,
            hasDepth = false,
            hasMasks = false,
            scaleSource = ScannerScaleSource.None,
            calibration = null,
            twoSidedAlignment = ScannerTwoSidedAlignment(
                objectMovedDuringSession = false,
                alignmentMethod = ScannerAlignmentMethod.NotMoved,
                alignmentConfidence = null,
                passCount = 1
            ),
            requestedOutputs = emptyList(),
            frames = frames
        )

    private fun diagnosticFrame(
        id: String,
        capturePass: ScannerCapturePass,
        accepted: Boolean
    ): ScanFrame =
        ScanFrame(
            id = id,
            timestampNs = 1L,
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
            capturePass = capturePass,
            quality = FrameQuality(
                blurScore = if (accepted) 120f else 10f,
                exposureScore = if (accepted) 0.8f else 0.2f,
                overlapScore = 0.5f,
                trackingGood = null,
                depthCoverage = null,
                markerVisibility = 0f,
                scaleConfidence = 0f,
                materialRisk = 0.1f,
                accepted = accepted,
                rejectionReasons = if (accepted) emptyList() else listOf("too_blurry")
            ),
            forcedCapture = false
        )

    private fun softMask(
        id: String,
        generator: String = "mediapipe_interactive_segmenter_v1",
        coverage: Float = 0.24f,
        edgeUncertainty: Float = 0.12f,
        centerSupport: Float = 0.82f,
        warnings: List<String> = listOf("mask_is_soft_evidence_only")
    ): ScannerSoftMaskResult =
        ScannerSoftMaskResult(
            maskPath = "frames/${id}_mask.png",
            maskSha256 = "mask-$id",
            generatorName = generator,
            generatorStatus = "ready",
            objectRoi = ScannerObjectRoi(0.5f, 0.5f, ScannerObjectRoiSource.UserTap),
            coverageRatio = coverage,
            edgeUncertainty = edgeUncertainty,
            centerSupport = centerSupport,
            warnings = warnings
        )
}

package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerReconstructionPipelineTest {
    @Test
    fun pipelineRunsEveryCurrentStageAndFailsClosedBeforeDenseReconstruction() {
        val packageDir = tempDir("scanner-pipeline-package")
        val workspaceDir = tempDir("scanner-pipeline-workspace")
        writePipelinePackage(packageDir)

        val result = runScannerLocalReconstructionPipeline(
            packageDir = packageDir,
            workspaceDir = workspaceDir,
            limits = ScannerReconstructionPipelineLimits(
                features = ScannerFeatureExtractionLimits(
                    minFeaturesPerFrame = 8,
                    maxFeaturesPerFrame = 80,
                    minPairMatches = 8
                ),
                depthFusion = ScannerDepthFusionLimits(minDepthFrameCount = 3),
                tracks = ScannerFeatureTrackLimits(
                    minTrackCount = 4,
                    minLongTrackCount = 2,
                    minSpatialCells = 1
                ),
                sparseReconstruction = ScannerSparseReconstructionLimits(minPreparedTracks = 4),
                bundleAdjustment = ScannerBundleAdjustmentLimits(minPreparedTrackCount = 4),
                poseOptimizer = ScannerPoseOptimizerLimits(minPreparedTrackCount = 4),
                sparseTriangulation = ScannerSparseTriangulationLimits(minPreparedTracks = 4),
                experimentalSparsePreview = ScannerExperimentalSparsePreviewLimits(minFeatureTracks = 4),
                denseReconstruction = ScannerDenseReconstructionLimits(minMetricSparsePoints = 4),
                densePointCloud = ScannerDensePointCloudLimits(minPointCountForSurface = 20),
                surfaceReconstruction = ScannerSurfaceReconstructionLimits(minSurfacePointCount = 20),
                meshTopology = ScannerMeshTopologyLimits(minSourcePointCount = 20),
                metricMeshCandidate = ScannerMetricMeshCandidateLimits(minVertexCount = 20, minTriangleCount = 6),
                meshValidation = ScannerMeshValidationLimits(minVertexCount = 20, minTriangleCount = 6),
                repairReport = ScannerRepairReportLimits(),
                printabilityReport = ScannerPrintabilityReportLimits(minVertexCount = 20, minTriangleCount = 6),
                slicerLoadValidation = ScannerSlicerLoadValidationLimits(minVertexCount = 20, minTriangleCount = 6),
                metricExport = ScannerMetricExportLimits(minVertexCount = 20, minTriangleCount = 6),
                workspaceHandoff = ScannerWorkspaceHandoffLimits(minVertexCount = 20, minTriangleCount = 6)
            )
        )

        assertTrue(result.completed)
        assertFalse(result.metric)
        assertFalse(result.denseReconstructionReady)
        assertEquals(SCANNER_RECONSTRUCTION_SUMMARY_PATH, result.summaryPath)
        assertEquals(
            listOf(
                "workspace",
                "depth_fusion",
                "feature_match_graph",
                "feature_tracks",
                "pose_initialization",
                "pose_refinement",
                "sparse_reconstruction_inputs",
                "metric_pose_graph",
                "bundle_adjustment_diagnostics",
                "metric_pose_solve",
                "metric_pose_optimizer",
                "sparse_triangulation",
                "experimental_sparse_preview",
                "dense_reconstruction",
                "dense_point_cloud",
                "surface_reconstruction",
                "mesh_topology",
                "metric_mesh_candidate",
                "mesh_validation",
                "repair_report",
                "printability_report",
                "slicer_load_validation",
                "metric_export",
                "workspace_handoff"
            ),
            result.stages.map { it.name }
        )
        assertTrue(workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_DEPTH_FUSION_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_FEATURES_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_FEATURE_TRACKS_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_POSE_INITIALIZATION_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_POSE_REFINEMENT_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_SPARSE_RECONSTRUCTION_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_METRIC_POSE_GRAPH_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_BUNDLE_ADJUSTMENT_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_METRIC_POSE_SOLVE_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_OPTIMIZED_METRIC_POSES_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_OPTIMIZED_SPARSE_POINTS_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_SPARSE_TRIANGULATION_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_EXPERIMENTAL_SPARSE_PREVIEW_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_DENSE_RECONSTRUCTION_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_DENSE_POINT_CLOUD_PATH).isFile)
        assertFalse(workspaceDir.resolve(SCANNER_DEBUG_POINT_CLOUD_PLY_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_SURFACE_RECONSTRUCTION_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_MESH_TOPOLOGY_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_METRIC_MESH_CANDIDATE_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_MESH_VALIDATION_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_REPAIR_REPORT_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_PRINTABILITY_REPORT_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_SLICER_LOAD_VALIDATION_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_EXPORT_MANIFEST_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_WORKSPACE_HANDOFF_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_RECONSTRUCTION_SUMMARY_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_RECONSTRUCTION_AUDIT_PATH).isFile)
        assertTrue(result.blockingErrors.any { it.startsWith("metric_pose_optimizer:native_pose_optimizer_failed:") })
        assertTrue(result.blockingErrors.contains("depth_fusion:depth_frame_count_low"))
        assertTrue(result.blockingErrors.contains("depth_fusion:dense_depth_assist_blocked_until_depth_fusion"))
        assertTrue(result.blockingErrors.contains("metric_pose_optimizer:optimized_pose_output_missing"))
        assertTrue(result.blockingErrors.contains("metric_pose_optimizer:optimized_sparse_point_output_missing"))
        assertTrue(result.blockingErrors.contains("sparse_triangulation:dense_reconstruction_blocked_until_sparse_points_are_metric"))
        assertTrue(result.blockingErrors.contains("experimental_sparse_preview:calibration_deferred_preview_is_not_metric"))
        assertTrue(result.blockingErrors.contains("experimental_sparse_preview:printable_handoff_blocked_for_experimental_preview"))
        assertTrue(result.blockingErrors.contains("dense_reconstruction:sparse_triangulation_blocked"))
        assertTrue(result.blockingErrors.contains("dense_reconstruction:mesh_generation_blocked_until_dense_reconstruction"))
        assertTrue(result.blockingErrors.contains("dense_point_cloud:dense_reconstruction_blocked"))
        assertTrue(result.blockingErrors.contains("dense_point_cloud:surface_reconstruction_blocked_until_dense_point_cloud"))
        assertTrue(result.blockingErrors.contains("surface_reconstruction:dense_point_cloud_blocked"))
        assertTrue(result.blockingErrors.contains("surface_reconstruction:mesh_generation_blocked_until_surface_reconstruction"))
        assertTrue(result.blockingErrors.contains("mesh_topology:surface_reconstruction_blocked"))
        assertTrue(result.blockingErrors.contains("mesh_topology:topology_generation_blocked_until_surface_reconstruction"))
        assertTrue(result.blockingErrors.contains("metric_mesh_candidate:mesh_topology_blocked"))
        assertTrue(result.blockingErrors.contains("metric_mesh_candidate:metric_mesh_blocked_until_triangle_candidate"))
        assertTrue(result.blockingErrors.contains("mesh_validation:metric_mesh_candidate_blocked"))
        assertTrue(result.blockingErrors.contains("mesh_validation:printability_blocked_until_mesh_validation"))
        assertTrue(result.blockingErrors.contains("repair_report:mesh_validation_blocked"))
        assertTrue(result.blockingErrors.contains("repair_report:printability_blocked_until_repair_report"))
        assertTrue(result.blockingErrors.contains("printability_report:mesh_validation_blocked"))
        assertTrue(result.blockingErrors.contains("printability_report:export_blocked_until_printability_report"))
        assertTrue(result.blockingErrors.contains("slicer_load_validation:printability_report_blocked"))
        assertTrue(result.blockingErrors.contains("slicer_load_validation:export_blocked_until_slicer_load_validation"))
        assertTrue(result.blockingErrors.contains("metric_export:slicer_load_validation_blocked"))
        assertTrue(result.blockingErrors.contains("metric_export:workspace_handoff_blocked_until_metric_export"))
        assertTrue(result.blockingErrors.contains("workspace_handoff:metric_export_blocked"))

        val summary = JSONObject(workspaceDir.resolve(SCANNER_RECONSTRUCTION_SUMMARY_PATH).readText())
        assertFalse(summary.getBoolean("metric"))
        assertFalse(summary.getBoolean("dense_reconstruction_ready"))
        assertEquals(24, summary.getJSONArray("stages").length())
        assertTrue(
            summary.getJSONArray("stages")
                .getJSONObject(9)
                .getString("name") == "metric_pose_solve"
        )
        assertTrue(
            JSONObject(workspaceDir.resolve(SCANNER_OPTIMIZED_METRIC_POSES_PATH).readText())
                .has("optimizer_request")
        )
        assertFalse(
            JSONObject(workspaceDir.resolve(SCANNER_OPTIMIZED_METRIC_POSES_PATH).readText())
                .getJSONObject("native_optimizer_response")
                .getBoolean("success")
        )

        val audit = JSONObject(workspaceDir.resolve(SCANNER_RECONSTRUCTION_AUDIT_PATH).readText())
        assertEquals("scan_pipeline", audit.getString("scan_id"))
        assertEquals(workspaceDir.absolutePath, audit.getString("workspace_path"))
        assertEquals("depth_fusion", audit.getJSONObject("first_failing_stage").getString("name"))
        assertTrue(audit.getJSONArray("next_actions").length() > 1)
        assertEquals(result.blockingErrors.size, audit.getInt("raw_blocker_count"))
        assertEquals(24, audit.getJSONArray("stages").length())
        assertTrue(audit.getJSONArray("stages").getJSONObject(0).getBoolean("artifact_exists"))
    }

    @Test
    fun pipelineWritesSummaryWhenWorkspacePreflightBlocksPackage() {
        val packageDir = tempDir("scanner-pipeline-bad-package")
        val workspaceDir = tempDir("scanner-pipeline-bad-workspace")
        writePipelinePackage(packageDir, frameCount = 1, scaleSource = ScannerScaleSource.None, calibration = null)

        val result = runScannerLocalReconstructionPipeline(
            packageDir = packageDir,
            workspaceDir = workspaceDir
        )

        assertTrue(result.completed)
        assertFalse(result.metric)
        assertEquals(listOf("workspace"), result.stages.map { it.name })
        assertTrue(result.blockingErrors.contains("workspace:local_reconstruction_preflight_failed"))
        assertTrue(result.blockingErrors.contains("workspace:not_enough_accepted_frames"))
        assertFalse(result.blockingErrors.contains("workspace:scale_unverified"))
        assertTrue(workspaceDir.resolve(SCANNER_RECONSTRUCTION_SUMMARY_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_RECONSTRUCTION_AUDIT_PATH).isFile)
        val audit = JSONObject(workspaceDir.resolve(SCANNER_RECONSTRUCTION_AUDIT_PATH).readText())
        assertEquals("workspace", audit.getJSONObject("first_failing_stage").getString("name"))
        assertTrue(
            audit.getJSONArray("next_actions")
                .toString()
                .contains("Capture more accepted frames")
        )
    }

    private fun writePipelinePackage(
        directory: File,
        frameCount: Int = 36,
        scaleSource: ScannerScaleSource = ScannerScaleSource.VerifiedMarkerMat,
        calibration: ScannerCalibration? = ScannerCalibration(
            markerType = "apriltag",
            markerSizeMm = 40f,
            printedScaleBarExpectedMm = 100f,
            printedScaleBarMeasuredMm = 100f,
            scaleCorrection = 1f,
            scaleConfidence = 0.90f,
            markerReprojectionErrorPx = 1.4f
        )
    ) {
        directory.mkdirs()
        val frames = (0 until frameCount).map { index ->
            val id = (index + 1).toString().padStart(6, '0')
            val pass = when (index % 3) {
                0 -> ScannerCapturePass.LowRing
                1 -> ScannerCapturePass.MidRing
                else -> ScannerCapturePass.HighRing
            }
            val imagePath = "frames/$id.jpg"
            val maskPath = "frames/${id}_mask.png"
            directory.resolve(imagePath).also {
                requireNotNull(it.parentFile).mkdirs()
                it.writeText(plainGrayMap(checkerLumaImage(72, 72)))
            }
            directory.resolve(maskPath).writeText(plainGrayMap(maskLumaImage(72, 72)))
            pipelineFrame(id, imagePath, maskPath, pass, directory)
        }
        writeScannerPackageDirectory(
            directory = directory,
            manifest = ScanPackageManifest(
                scanId = "scan_pipeline",
                createdAtIso8601 = "2026-05-08T12:00:00Z",
                mode = ScannerMode.LocalPrintable,
                captureProfile = ScannerCaptureProfile.HighResPhoto,
                frameCount = frames.size,
                acceptedFrameCount = frames.count { it.quality.accepted },
                forcedFrameCount = 0,
                rejectedFrameCount = 0,
                hasArCorePoses = false,
                hasDepth = false,
                hasMasks = true,
                scaleSource = scaleSource,
                calibration = calibration,
                twoSidedAlignment = ScannerTwoSidedAlignment(
                    objectMovedDuringSession = false,
                    alignmentMethod = ScannerAlignmentMethod.NotMoved,
                    alignmentConfidence = null,
                    passCount = frames.map { it.capturePass }.distinct().size
                ),
                requestedOutputs = listOf("stl", "3mf"),
                frames = frames
            )
        )
    }

    private fun pipelineFrame(
        id: String,
        imagePath: String,
        maskPath: String,
        pass: ScannerCapturePass,
        directory: File
    ): ScanFrame =
        ScanFrame(
            id = id,
            timestampNs = 123456789L,
            rgbPath = imagePath,
            rgbSha256 = sha256Hex(directory.resolve(imagePath)),
            maskPath = maskPath,
            maskSha256 = sha256Hex(directory.resolve(maskPath)),
            depth16Path = null,
            depthSha256 = null,
            depthConfidencePath = null,
            poseWorldFromCamera = null,
            intrinsics = CameraIntrinsicsData(
                fx = 1000f,
                fy = 1000f,
                cx = 36f,
                cy = 36f,
                imageWidth = 72,
                imageHeight = 72
            ),
            distortion = null,
            lensFacing = "back",
            focalLengthMm = 5.4f,
            exposureTimeNs = 8333333L,
            iso = 160,
            focusDistance = 0.7f,
            whiteBalanceMode = "auto",
            width = 72,
            height = 72,
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

    private fun checkerLumaImage(width: Int, height: Int): ScannerLumaImage =
        ScannerLumaImage(
            width = width,
            height = height,
            luma = IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                if (((x / 8) + (y / 8)) % 2 == 0) 35 else 220
            }
        )

    private fun maskLumaImage(width: Int, height: Int): ScannerLumaImage =
        ScannerLumaImage(width = width, height = height, luma = IntArray(width * height) { 255 })

    private fun plainGrayMap(image: ScannerLumaImage): String =
        buildString {
            appendLine("P2")
            appendLine("${image.width} ${image.height}")
            appendLine("255")
            image.luma.forEachIndexed { index, value ->
                append(value)
                append(if ((index + 1) % image.width == 0) '\n' else ' ')
            }
        }

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}

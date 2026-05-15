package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerMetricPoseGraphTest {
    @Test
    fun poseGraphBuildsRelativeConstraintsAndScaleEvidence() {
        val workspaceDir = tempDir("scanner-metric-pose-graph-ready")
        writeReconstructionJob(workspaceDir)
        writePoseRefinement(workspaceDir, allowed = true)
        writeMatchGraph(
            workspaceDir,
            pairs = listOf(
                matchPair("low", "mid", 24),
                matchPair("mid", "high", 22),
                matchPair("low", "high", 20)
            )
        )

        val result = buildScannerMetricPoseGraph(
            workspaceDir = workspaceDir,
            limits = ScannerMetricPoseGraphLimits(
                minRelativePoseConstraints = 2,
                minRelativePoseInliers = 16,
                minRelativePoseInlierRatio = 0.55
            ),
            openCvSolverStatus = readyOpenCvStatus(),
            relativePairSolver = ::successfulRelativePairSolve
        )

        assertTrue(result.errors.joinToString(), result.allowed)
        assertFalse(result.metric)
        assertTrue(result.bundleAdjustmentReady)
        assertEquals(1, result.connectedComponentCount)
        assertEquals(3, result.constraints.count { it.relativePoseAvailable })
        val json = JSONObject(workspaceDir.resolve(SCANNER_METRIC_POSE_GRAPH_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getBoolean("bundle_adjustment_ready"))
        assertEquals(3, json.getJSONArray("relative_pose_constraints").length())
        assertEquals(0, json.getJSONArray("errors").length())
    }

    @Test
    fun poseGraphBlocksMissingScaleAndUnavailableRelativeSolver() {
        val workspaceDir = tempDir("scanner-metric-pose-graph-blocked")
        writeReconstructionJob(
            workspaceDir = workspaceDir,
            calibration = null,
            scaleSource = ScannerScaleSource.None.manifestValue
        )
        writePoseRefinement(workspaceDir, allowed = true)
        writeMatchGraph(workspaceDir, pairs = listOf(matchPair("low", "mid", 24)))

        val result = buildScannerMetricPoseGraph(
            workspaceDir = workspaceDir,
            openCvSolverStatus = ScannerOpenCvPoseSolverStatus(
                available = false,
                status = "opencv_unavailable",
                detail = "test unavailable",
                solverName = ScannerAndroidOpenCvPoseSolver.SolverName
            ),
            relativePairSolver = ::successfulRelativePairSolve
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("android_opencv_relative_pose_solver_unavailable:opencv_unavailable"))
        assertTrue(result.errors.contains("relative_pose_constraint_failed"))
        assertTrue(result.errors.contains("verified_marker_mat_required"))
        assertTrue(result.errors.contains("calibration_missing"))
        assertTrue(result.errors.contains("scale_confidence_low"))
        assertTrue(result.errors.contains("marker_reprojection_missing"))
        assertTrue(result.errors.contains("not_enough_relative_pose_constraints"))
    }

    private fun writeReconstructionJob(
        workspaceDir: File,
        calibration: JSONObject? = calibrationJson(),
        scaleSource: String = ScannerScaleSource.VerifiedMarkerMat.manifestValue
    ) {
        workspaceDir.mkdirs()
        workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("scale_source", scaleSource)
                .put("calibration", calibration ?: JSONObject.NULL)
                .put(
                    "frames",
                    JSONArray().apply {
                        put(frame("low", ScannerCapturePass.LowRing))
                        put(frame("mid", ScannerCapturePass.MidRing))
                        put(frame("high", ScannerCapturePass.HighRing))
                    }
                )
                .toString(2)
        )
    }

    private fun writePoseRefinement(workspaceDir: File, allowed: Boolean) {
        workspaceDir.resolve("poses").mkdirs()
        workspaceDir.resolve(SCANNER_POSE_REFINEMENT_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("errors", JSONArray())
                .toString(2)
        )
    }

    private fun writeMatchGraph(workspaceDir: File, pairs: List<JSONObject>) {
        workspaceDir.resolve("matches").mkdirs()
        workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("errors", JSONArray())
                .put("pairs", JSONArray().apply { pairs.forEach(::put) })
                .toString(2)
        )
    }

    private fun frame(frameId: String, capturePass: ScannerCapturePass): JSONObject =
        JSONObject()
            .put("frame_id", frameId)
            .put("capture_pass", capturePass.manifestValue)
            .put(
                "intrinsics",
                JSONObject()
                    .put("fx", 920.0)
                    .put("fy", 918.0)
                    .put("cx", 320.0)
                    .put("cy", 240.0)
                    .put("width", 640)
                    .put("height", 480)
            )

    private fun matchPair(frameA: String, frameB: String, count: Int): JSONObject =
        JSONObject()
            .put("frame_a", frameA)
            .put("frame_b", frameB)
            .put("match_count", count)
            .put("accepted", true)
            .put(
                "matches",
                JSONArray().apply {
                    repeat(count) { index ->
                        val x = 80 + (index % 8) * 34
                        val y = 70 + (index / 8) * 42
                        put(
                            JSONObject()
                                .put("ax", x)
                                .put("ay", y)
                                .put("bx", x + 4)
                                .put("by", y + 2)
                        )
                    }
                }
            )

    private fun calibrationJson(): JSONObject =
        JSONObject()
            .put("marker_type", "apriltag")
            .put("marker_size_mm", 40.0)
            .put("printed_scale_bar_expected_mm", 100.0)
            .put("printed_scale_bar_measured_mm", 100.0)
            .put("scale_confidence", 0.92)
            .put("marker_reprojection_error_px", 1.4)

    private fun readyOpenCvStatus(): ScannerOpenCvPoseSolverStatus =
        ScannerOpenCvPoseSolverStatus(
            available = true,
            status = "ready",
            detail = "test solver",
            solverName = ScannerAndroidOpenCvPoseSolver.SolverName
        )

    private fun successfulRelativePairSolve(
        pointsA: DoubleArray,
        pointsB: DoubleArray,
        intrinsicsA: CameraIntrinsicsData,
        intrinsicsB: CameraIntrinsicsData,
        minInliers: Int,
        maxReprojectionErrorPx: Double
    ): ScannerNativeRelativePairSolveResult =
        ScannerNativeRelativePairSolveResult(
            success = true,
            status = "relative_pose_recovered",
            detail = "test relative pose",
            inlierCount = pointsA.size / 2,
            inlierRatio = 0.86,
            rotationRowMajor = listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
            translationUnit = listOf(1.0, 0.0, 0.0)
        )

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}

package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerMetricPoseSolveTest {
    @Test
    fun metricSolveBlocksWhenReconstructionJobIsMissing() {
        val workspaceDir = tempDir("scanner-metric-missing-job")

        val result = solveScannerMetricPoses(workspaceDir)

        assertFalse(result.allowed)
        assertEquals(ScannerMetricPoseSolverStatus.Blocked, result.solverStatus)
        assertTrue(result.errors.contains("reconstruction_job_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_METRIC_POSE_SOLVE_PATH).isFile)
    }

    @Test
    fun metricSolveBlocksWhenRefinementIsMissing() {
        val workspaceDir = tempDir("scanner-metric-missing-refinement")
        writeReconstructionJob(workspaceDir)

        val result = solveScannerMetricPoses(workspaceDir)

        assertFalse(result.allowed)
        assertEquals(ScannerMetricPoseSolverStatus.Blocked, result.solverStatus)
        assertTrue(result.errors.contains("pose_refinement_missing"))
    }

    @Test
    fun metricSolveRequiresCalibrationAndMarkerReprojectionEvidence() {
        val workspaceDir = tempDir("scanner-metric-no-calibration")
        writeReconstructionJob(workspaceDir, calibration = null, scaleSource = ScannerScaleSource.None.manifestValue)
        writePoseRefinement(workspaceDir, allowed = true, confidence = 0.9f)

        val result = solveScannerMetricPoses(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("verified_marker_mat_required"))
        assertTrue(result.errors.contains("calibration_missing"))
        assertTrue(result.errors.contains("scale_confidence_low"))
        assertTrue(result.errors.contains("marker_reprojection_missing"))
    }

    @Test
    fun metricSolveBlocksWhenUpstreamEvidenceIsMissing() {
        val workspaceDir = tempDir("scanner-metric-strong-evidence")
        writeReconstructionJob(workspaceDir)
        writePoseRefinement(workspaceDir, allowed = true, confidence = 0.92f)

        val result = solveScannerMetricPoses(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertFalse(result.denseReconstructionAllowed)
        assertEquals(ScannerMetricPoseSolverStatus.Blocked, result.solverStatus)
        assertTrue(result.errors.contains("feature_tracks_missing"))
        assertTrue(result.errors.contains("feature_tracks_blocked"))
        assertTrue(result.errors.contains("sparse_reconstruction_missing"))
        assertTrue(result.errors.contains("sparse_reconstruction_blocked"))
        assertTrue(result.errors.contains("metric_pose_graph_missing"))
        assertTrue(result.errors.contains("metric_pose_graph_blocked"))
        assertTrue(result.errors.contains("relative_pose_constraint_count_low"))
        assertEquals(0.91f, result.scaleConfidence, 0.0001f)
        assertEquals(1.6f, result.markerReprojectionErrorPx ?: -1f, 0.0001f)
        val json = JSONObject(workspaceDir.resolve(SCANNER_METRIC_POSE_SOLVE_PATH).readText())
        assertFalse(json.getBoolean("allowed"))
        assertFalse(json.getBoolean("dense_reconstruction_allowed"))
        assertEquals("blocked", json.getString("solver_status"))
        assertTrue(json.getJSONObject("feature_tracks").getJSONArray("blocking_errors").length() > 0)
        assertTrue(json.getJSONObject("sparse_reconstruction").getJSONArray("blocking_errors").length() > 0)
        assertTrue(json.getJSONObject("metric_pose_graph").getJSONArray("blocking_errors").length() > 0)
    }

    @Test
    fun metricSolveBlocksHighMarkerReprojectionError() {
        val workspaceDir = tempDir("scanner-metric-reprojection-high")
        writeReconstructionJob(
            workspaceDir = workspaceDir,
            calibration = calibrationJson(scaleConfidence = 0.91f, reprojectionErrorPx = 8.0f)
        )
        writePoseRefinement(workspaceDir, allowed = true, confidence = 0.92f)

        val result = solveScannerMetricPoses(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("marker_reprojection_error_high"))
    }

    @Test
    fun metricSolveEmitsPoseCandidatesButBlocksUntilBundleAdjustment() {
        val workspaceDir = tempDir("scanner-metric-pose-candidates")
        writeReconstructionJob(workspaceDir)
        writePoseRefinement(workspaceDir, allowed = true, confidence = 0.92f)
        writeAllowedMetricPoseGraph(workspaceDir)

        val result = solveScannerMetricPoses(workspaceDir)

        assertFalse(result.allowed)
        assertFalse(result.metric)
        assertEquals(3, result.poseCandidates.size)
        assertTrue(result.errors.contains("feature_tracks_missing"))
        assertTrue(result.errors.contains("sparse_reconstruction_missing"))
        assertTrue(result.errors.contains("bundle_adjustment_missing"))
        val json = JSONObject(workspaceDir.resolve(SCANNER_METRIC_POSE_SOLVE_PATH).readText())
        assertEquals(3, json.getJSONArray("camera_pose_candidates").length())
        assertEquals("not_implemented", json.getJSONObject("bundle_adjustment").getString("status"))
        assertFalse(json.getJSONObject("bundle_adjustment").getBoolean("ready"))
    }

    @Test
    fun metricSolveAcceptsConnectedCalibratedPoseGraphAndBundleGate() {
        val workspaceDir = tempDir("scanner-metric-accepted")
        writeReconstructionJob(workspaceDir)
        writePoseRefinement(workspaceDir, allowed = true, confidence = 0.92f)
        writeFeatureTracks(workspaceDir, trackCount = 48, longTrackCount = 16)
        writeSparseReconstruction(workspaceDir, preparedTracks = 48)
        writeAllowedMetricPoseGraph(workspaceDir)
        writeBundleAdjustment(workspaceDir)

        val result = solveScannerMetricPoses(workspaceDir)

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.denseReconstructionAllowed)
        assertEquals(ScannerMetricPoseSolverStatus.Accepted, result.solverStatus)
        assertEquals(3, result.poseCandidates.size)
        assertTrue(result.errors.isEmpty())

        val json = JSONObject(workspaceDir.resolve(SCANNER_METRIC_POSE_SOLVE_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getBoolean("metric"))
        assertEquals("accepted", json.getString("solver_status"))
        assertTrue(json.getJSONArray("camera_pose_candidates").getJSONObject(0).getBoolean("metric_validated"))
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
                .toString(2)
        )
    }

    private fun writePoseRefinement(
        workspaceDir: File,
        allowed: Boolean,
        confidence: Float,
        errors: List<String> = emptyList()
    ) {
        workspaceDir.resolve("poses").mkdirs()
        workspaceDir.resolve(SCANNER_POSE_REFINEMENT_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("refinement_confidence", confidence.toDouble())
                .put("errors", JSONArray(errors))
                .toString(2)
        )
    }

    private fun writeAllowedMetricPoseGraph(workspaceDir: File) {
        workspaceDir.resolve("poses").mkdirs()
        workspaceDir.resolve(SCANNER_METRIC_POSE_GRAPH_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("metric", false)
                .put("bundle_adjustment_ready", true)
                .put("errors", JSONArray())
                .put("connected_component_count", 1)
                .put("scale_confidence", 0.91)
                .put("marker_reprojection_error_px", 1.6)
                .put(
                    "nodes",
                    JSONArray().apply {
                        put(metricPoseGraphNode("low"))
                        put(metricPoseGraphNode("mid"))
                        put(metricPoseGraphNode("high"))
                    }
                )
                .put(
                    "relative_pose_constraints",
                    JSONArray().apply {
                        put(metricPoseGraphConstraint("low", "mid"))
                        put(metricPoseGraphConstraint("mid", "high"))
                    }
                )
                .toString(2)
        )
    }

    private fun writeFeatureTracks(
        workspaceDir: File,
        trackCount: Int,
        longTrackCount: Int
    ) {
        workspaceDir.resolve("features").mkdirs()
        workspaceDir.resolve(SCANNER_FEATURE_TRACKS_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("errors", JSONArray())
                .put("track_count", trackCount)
                .put("long_track_count", longTrackCount)
                .toString(2)
        )
    }

    private fun writeSparseReconstruction(
        workspaceDir: File,
        preparedTracks: Int
    ) {
        workspaceDir.resolve("sparse").mkdirs()
        workspaceDir.resolve(SCANNER_SPARSE_RECONSTRUCTION_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("metric", false)
                .put("prepared_track_count", preparedTracks)
                .put("errors", JSONArray())
                .toString(2)
        )
    }

    private fun writeBundleAdjustment(workspaceDir: File) {
        workspaceDir.resolve("poses").mkdirs()
        workspaceDir.resolve(SCANNER_BUNDLE_ADJUSTMENT_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", true)
                .put("metric", true)
                .put("status", "optimizer_admission_ready")
                .put("frame_residual_max_px", 1.0)
                .put("track_residual_max_px", 1.0)
                .put("marker_residual_px", 1.0)
                .put("scale_residual_percent", 0.0)
                .put("rejected_constraint_count", 0)
                .put("errors", JSONArray())
                .toString(2)
        )
    }

    private fun metricPoseGraphNode(frameId: String): JSONObject =
        JSONObject()
            .put("frame_id", frameId)
            .put("capture_pass", ScannerCapturePass.MidRing.manifestValue)

    private fun metricPoseGraphConstraint(frameA: String, frameB: String): JSONObject =
        JSONObject()
            .put("frame_a", frameA)
            .put("frame_b", frameB)
            .put("match_count", 24)
            .put("status", "relative_pose_recovered")
            .put("relative_pose_available", true)
            .put("inlier_count", 21)
            .put("inlier_ratio", 0.86)
            .put("rotation_row_major", JSONArray(listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)))
            .put("translation_unit", JSONArray(listOf(1.0, 0.0, 0.0)))

    private fun calibrationJson(
        scaleConfidence: Float = 0.91f,
        reprojectionErrorPx: Float? = 1.6f
    ): JSONObject =
        JSONObject()
            .put("marker_type", "apriltag")
            .put("marker_size_mm", 40.0)
            .put("printed_scale_bar_expected_mm", 100.0)
            .put("printed_scale_bar_measured_mm", 100.0)
            .put("scale_correction", 1.0)
            .put("scale_confidence", scaleConfidence.toDouble())
            .put("marker_reprojection_error_px", reprojectionErrorPx ?: JSONObject.NULL)

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}

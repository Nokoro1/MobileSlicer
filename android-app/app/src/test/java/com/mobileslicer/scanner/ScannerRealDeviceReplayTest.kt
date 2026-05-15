package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerRealDeviceReplayTest {
    @Test
    fun realDeviceScanReplaysPastOldFeatureTrackBlockerAndReportsNextMetricGates() {
        val workspaceDir = Files.createTempDirectory("scanner-real-device-replay").toFile()

        val replay = runRealDeviceScannerReplay(workspaceDir)

        assertTrue(replay.matchGraphAllowed)
        assertTrue(replay.featureTracks.errors.joinToString(), replay.featureTracks.allowed)
        assertFalse(replay.featureTracks.errors.contains("not_enough_long_feature_tracks"))
        assertTrue(replay.featureTracks.trackCount >= 40)
        assertTrue(replay.featureTracks.longTrackCount >= 12)
        assertTrue(replay.featureTracks.descriptorSeededTrackCount > 0)
        assertTrue(
            replay.featureTracks.warnings.contains("descriptor_seeded_tracks_require_metric_pose_validation")
        )

        assertTrue(replay.poseInitialization.errors.joinToString(), replay.poseInitialization.allowed)
        assertTrue(replay.poseInitialization.errors.contains("pose_initialization_not_metric"))
        assertTrue(replay.poseRefinement.errors.joinToString(), replay.poseRefinement.allowed)
        assertTrue(replay.poseRefinement.errors.contains("pose_refinement_not_metric"))
        assertFalse(replay.sparseReconstruction.allowed)
        assertTrue(replay.sparseReconstruction.errors.contains("verified_marker_mat_required"))
        assertTrue(replay.sparseReconstruction.errors.contains("calibration_missing"))
        assertTrue(replay.sparseReconstruction.errors.contains("scale_confidence_low"))
        assertTrue(replay.sparseReconstruction.errors.contains("marker_reprojection_missing"))
        assertFalse(replay.metricPoseGraph.allowed)
        assertTrue(replay.metricPoseGraph.errors.contains("verified_marker_mat_required"))
        assertTrue(replay.metricPoseGraph.errors.contains("calibration_missing"))
        assertTrue(replay.metricPoseSolve.errors.contains("verified_marker_mat_required"))

        val summary = JSONObject(workspaceDir.resolve(REAL_DEVICE_REPLAY_SUMMARY_PATH).readText())
        assertEquals(REAL_SCAN_ID, summary.getString("scan_id"))
        assertEquals("feature_tracks", summary.getJSONArray("stage_order").getString(1))
        assertFalse(summary.getJSONArray("blocking_errors").toString().contains("not_enough_long_feature_tracks"))
        assertTrue(summary.getJSONArray("blocking_errors").toString().contains("verified_marker_mat_required"))
        assertTrue(summary.getJSONArray("blocking_errors").toString().contains("calibration_missing"))
        assertEquals("sparse_reconstruction_inputs", summary.getJSONObject("first_failing_stage").getString("name"))
    }
}

private const val REAL_SCAN_ID = "scan_c2a4f330-2251-4686-8076-d1729b571cd2"
private const val REAL_DEVICE_REPLAY_SUMMARY_PATH = "real_device_replay_summary.json"

private data class RealDeviceScannerReplayResult(
    val matchGraphAllowed: Boolean,
    val featureTracks: ScannerFeatureTrackResult,
    val poseInitialization: ScannerPoseInitializationResult,
    val poseRefinement: ScannerPoseRefinementResult,
    val sparseReconstruction: ScannerSparseReconstructionResult,
    val metricPoseGraph: ScannerMetricPoseGraphResult,
    val bundleAdjustment: ScannerBundleAdjustmentResult,
    val metricPoseSolve: ScannerMetricPoseSolveResult
)

private fun runRealDeviceScannerReplay(workspaceDir: File): RealDeviceScannerReplayResult {
    if (workspaceDir.exists()) workspaceDir.deleteRecursively()
    workspaceDir.mkdirs()
    copyRealDeviceReplayArtifacts(workspaceDir)
    writeRealDeviceReplayManifest(workspaceDir)

    val matchGraph = JSONObject(workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH).readText())
    val featureTracks = buildScannerFeatureTracks(workspaceDir)
    val poseInitialization = initializeScannerPoseGraph(workspaceDir)
    val poseRefinement = refineScannerPoseGraph(workspaceDir)
    val sparseReconstruction = prepareScannerSparseReconstruction(workspaceDir)
    val metricPoseGraph = buildScannerMetricPoseGraph(workspaceDir)
    val bundleAdjustment = analyzeScannerBundleAdjustment(workspaceDir)
    val metricPoseSolve = solveScannerMetricPoses(workspaceDir)

    val result = RealDeviceScannerReplayResult(
        matchGraphAllowed = matchGraph.optBoolean("allowed", false),
        featureTracks = featureTracks,
        poseInitialization = poseInitialization,
        poseRefinement = poseRefinement,
        sparseReconstruction = sparseReconstruction,
        metricPoseGraph = metricPoseGraph,
        bundleAdjustment = bundleAdjustment,
        metricPoseSolve = metricPoseSolve
    )
    workspaceDir.resolve(REAL_DEVICE_REPLAY_SUMMARY_PATH).writeText(realDeviceReplaySummaryJson(result).toString(2))
    return result
}

private fun copyRealDeviceReplayArtifacts(workspaceDir: File) {
    val fixtureRoot = File("src/test/resources/scanner/real_scan_c2a4f330/$REAL_SCAN_ID")
    require(fixtureRoot.isDirectory) { "Real scan fixture missing: ${fixtureRoot.absolutePath}" }
    listOf(SCANNER_FEATURES_PATH, SCANNER_MATCH_GRAPH_PATH).forEach { relativePath ->
        val source = fixtureRoot.resolve(relativePath)
        val target = workspaceDir.resolve(relativePath)
        target.parentFile?.mkdirs()
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

private fun writeRealDeviceReplayManifest(workspaceDir: File) {
    val features = JSONObject(workspaceDir.resolve(SCANNER_FEATURES_PATH).readText())
    val frames = features.getJSONArray("frames")
    workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).writeText(
        JSONObject()
            .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
            .put("scan_id", REAL_SCAN_ID)
            .put("scale_source", ScannerScaleSource.None.manifestValue)
            .put(
                "frames",
                JSONArray().apply {
                    for (index in 0 until frames.length()) {
                        val frame = frames.getJSONObject(index)
                        val width = frame.getInt("width")
                        val height = frame.getInt("height")
                        put(
                            JSONObject()
                                .put("frame_id", frame.getString("frame_id"))
                                .put("capture_pass", frame.getString("capture_pass"))
                                .put(
                                    "intrinsics",
                                    JSONObject()
                                        .put("fx", width.toDouble())
                                        .put("fy", width.toDouble())
                                        .put("cx", width.toDouble() / 2.0)
                                        .put("cy", height.toDouble() / 2.0)
                                        .put("width", width)
                                        .put("height", height)
                                )
                        )
                    }
                }
            )
            .toString(2)
    )
}

private fun realDeviceReplaySummaryJson(result: RealDeviceScannerReplayResult): JSONObject {
    val stages = listOf(
        realReplayStage("feature_match_graph", result.matchGraphAllowed, false, emptyList(), emptyList()),
        realReplayStage(
            "feature_tracks",
            result.featureTracks.allowed,
            false,
            result.featureTracks.errors,
            result.featureTracks.warnings
        ).put("track_count", result.featureTracks.trackCount)
            .put("long_track_count", result.featureTracks.longTrackCount)
            .put("descriptor_seeded_track_count", result.featureTracks.descriptorSeededTrackCount),
        realReplayStage(
            "pose_initialization",
            result.poseInitialization.allowed,
            result.poseInitialization.metric,
            result.poseInitialization.errors,
            result.poseInitialization.warnings
        ),
        realReplayStage(
            "pose_refinement",
            result.poseRefinement.allowed,
            result.poseRefinement.metric,
            result.poseRefinement.errors,
            result.poseRefinement.warnings
        ).put("refinement_confidence", result.poseRefinement.refinementConfidence.toDouble()),
        realReplayStage(
            "sparse_reconstruction_inputs",
            result.sparseReconstruction.allowed,
            result.sparseReconstruction.metric,
            result.sparseReconstruction.errors,
            result.sparseReconstruction.warnings
        ).put("prepared_track_count", result.sparseReconstruction.preparedTrackCount),
        realReplayStage(
            "metric_pose_graph",
            result.metricPoseGraph.allowed,
            result.metricPoseGraph.metric,
            result.metricPoseGraph.errors,
            result.metricPoseGraph.warnings
        ).put("constraint_count", result.metricPoseGraph.constraints.size),
        realReplayStage(
            "bundle_adjustment_diagnostics",
            result.bundleAdjustment.allowed,
            result.bundleAdjustment.metric,
            result.bundleAdjustment.errors,
            result.bundleAdjustment.warnings
        ),
        realReplayStage(
            "metric_pose_solve",
            result.metricPoseSolve.allowed,
            result.metricPoseSolve.metric,
            result.metricPoseSolve.errors,
            result.metricPoseSolve.warnings
        )
    )
    val blocking = stages.flatMap { stage ->
        val name = stage.getString("name")
        val errors = stage.getJSONArray("errors")
        List(errors.length()) { index -> "$name:${errors.getString(index)}" }
    }.filterNot { it == "feature_tracks:not_enough_long_feature_tracks" }
    val firstFailing = stages.firstOrNull { !it.getBoolean("allowed") }
    return JSONObject()
        .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
        .put("scan_id", REAL_SCAN_ID)
        .put("fixture", "real_device_replay")
        .put("completed", true)
        .put("metric", false)
        .put("stage_order", JSONArray(stages.map { it.getString("name") }))
        .put("stages", JSONArray(stages))
        .put("blocking_errors", JSONArray(blocking))
        .put(
            "first_failing_stage",
            firstFailing ?: JSONObject.NULL
        )
        .put(
            "next_expected_gate",
            "verified calibration/scale and metric pose validation"
        )
}

private fun realReplayStage(
    name: String,
    allowed: Boolean,
    metric: Boolean,
    errors: List<String>,
    warnings: List<String>
): JSONObject =
    JSONObject()
        .put("name", name)
        .put("allowed", allowed)
        .put("metric", metric)
        .put("errors", JSONArray(errors))
        .put("warnings", JSONArray(warnings))

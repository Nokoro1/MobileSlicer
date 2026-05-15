package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerPoseRefinementTest {
    @Test
    fun refinementBlocksWhenPoseInitializationIsMissing() {
        val workspaceDir = tempDir("scanner-refinement-missing")

        val result = refineScannerPoseGraph(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("pose_initialization_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_POSE_REFINEMENT_PATH).isFile)
    }

    @Test
    fun refinementBlocksWhenPoseInitializationIsBlocked() {
        val workspaceDir = tempDir("scanner-refinement-blocked")
        writePoseInitialization(
            workspaceDir = workspaceDir,
            allowed = false,
            metric = false,
            errors = listOf("not_enough_anchor_passes:high_ring"),
            nodes = emptyList(),
            edges = emptyList()
        )

        val result = refineScannerPoseGraph(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("pose_initialization_blocked"))
        assertTrue(result.errors.contains("not_enough_anchor_passes:high_ring"))
    }

    @Test
    fun refinementAllowsScaffoldButBlocksDenseReconstructionForNonMetricInitialization() {
        val workspaceDir = tempDir("scanner-refinement-nonmetric")
        writeStrongPoseInitialization(workspaceDir, metric = false)

        val result = refineScannerPoseGraph(
            workspaceDir,
            ScannerPoseRefinementLimits(
                minAverageSupportEdges = 2f,
                minAverageMatchCount = 16f,
                minRefinementConfidence = 0.70f,
                requireMetricInitialization = true
            )
        )

        assertTrue(result.errors.joinToString(), result.allowed)
        assertFalse(result.metric)
        assertFalse(result.denseReconstructionAllowed)
        assertTrue(result.errors.contains("pose_refinement_not_metric"))
        assertTrue(result.errors.contains("dense_reconstruction_blocked_until_metric_pose_solve"))
        assertTrue(result.refinementConfidence >= 0.70f)
        val json = JSONObject(workspaceDir.resolve(SCANNER_POSE_REFINEMENT_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertFalse(json.getBoolean("dense_reconstruction_allowed"))
        assertEquals(3, json.getJSONArray("nodes").length())
    }

    @Test
    fun refinementBlocksLowSupportPoseGraph() {
        val workspaceDir = tempDir("scanner-refinement-low-support")
        writePoseInitialization(
            workspaceDir = workspaceDir,
            allowed = true,
            metric = false,
            errors = listOf("pose_initialization_not_metric"),
            nodes = listOf(
                "low_1" to ScannerCapturePass.LowRing,
                "mid_1" to ScannerCapturePass.MidRing,
                "high_1" to ScannerCapturePass.HighRing
            ),
            edges = listOf(Triple("low_1", "mid_1", 8))
        )

        val result = refineScannerPoseGraph(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("pose_support_low"))
        assertTrue(result.errors.contains("pose_match_count_low"))
    }

    private fun writeStrongPoseInitialization(workspaceDir: File, metric: Boolean) {
        writePoseInitialization(
            workspaceDir = workspaceDir,
            allowed = true,
            metric = metric,
            errors = if (metric) emptyList() else listOf("pose_initialization_not_metric"),
            nodes = listOf(
                "low_1" to ScannerCapturePass.LowRing,
                "mid_1" to ScannerCapturePass.MidRing,
                "high_1" to ScannerCapturePass.HighRing
            ),
            edges = listOf(
                Triple("low_1", "mid_1", 24),
                Triple("mid_1", "high_1", 22),
                Triple("low_1", "high_1", 18)
            )
        )
    }

    private fun writePoseInitialization(
        workspaceDir: File,
        allowed: Boolean,
        metric: Boolean,
        errors: List<String>,
        nodes: List<Pair<String, ScannerCapturePass>>,
        edges: List<Triple<String, String, Int>>
    ) {
        workspaceDir.resolve("poses").mkdirs()
        workspaceDir.resolve(SCANNER_POSE_INITIALIZATION_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("metric", metric)
                .put("errors", JSONArray(errors))
                .put(
                    "nodes",
                    JSONArray().apply {
                        nodes.forEach { (frameId, pass) ->
                            put(
                                JSONObject()
                                    .put("frame_id", frameId)
                                    .put("capture_pass", pass.manifestValue)
                                    .put("support_edges", edges.count { it.first == frameId || it.second == frameId })
                                    .put("status", "initial_unmetric")
                            )
                        }
                    }
                )
                .put(
                    "edges",
                    JSONArray().apply {
                        edges.forEach { (frameA, frameB, matchCount) ->
                            put(
                                JSONObject()
                                    .put("frame_a", frameA)
                                    .put("frame_b", frameB)
                                    .put("match_count", matchCount)
                            )
                        }
                    }
                )
                .toString(2)
        )
    }

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}

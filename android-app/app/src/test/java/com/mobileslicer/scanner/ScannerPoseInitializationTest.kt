package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerPoseInitializationTest {
    @Test
    fun poseInitializationBlocksWhenMatchGraphIsMissing() {
        val workspaceDir = tempDir("scanner-pose-missing-graph")

        val result = initializeScannerPoseGraph(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("match_graph_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_POSE_INITIALIZATION_PATH).isFile)
    }

    @Test
    fun poseInitializationBlocksWhenMatchGraphIsBlocked() {
        val workspaceDir = tempDir("scanner-pose-blocked-graph")
        writeReconstructionJob(workspaceDir)
        writeMatchGraph(
            workspaceDir = workspaceDir,
            allowed = false,
            errors = listOf("match_graph_disconnected"),
            acceptedPairs = emptyList()
        )

        val result = initializeScannerPoseGraph(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("match_graph_blocked"))
        assertTrue(result.errors.contains("match_graph_disconnected"))
    }

    @Test
    fun poseInitializationChoosesAnchorsForConnectedLowMidHighGraph() {
        val workspaceDir = tempDir("scanner-pose-connected")
        writeReconstructionJob(workspaceDir)
        writeMatchGraph(
            workspaceDir = workspaceDir,
            allowed = true,
            errors = emptyList(),
            acceptedPairs = listOf(
                Triple("low_1", "mid_1", 24),
                Triple("mid_1", "high_1", 22),
                Triple("low_1", "high_1", 18)
            )
        )

        val result = initializeScannerPoseGraph(workspaceDir)

        assertTrue(result.errors.joinToString(), result.allowed)
        assertFalse(result.metric)
        assertTrue(result.errors.contains("pose_initialization_not_metric"))
        assertEquals(3, result.anchors.size)
        assertEquals(
            setOf(ScannerCapturePass.LowRing, ScannerCapturePass.MidRing, ScannerCapturePass.HighRing),
            result.anchors.map { it.capturePass }.toSet()
        )
        val json = JSONObject(workspaceDir.resolve(SCANNER_POSE_INITIALIZATION_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertFalse(json.getBoolean("metric"))
        assertEquals(3, json.getJSONArray("anchors").length())
        assertEquals("initial_unmetric", json.getJSONArray("nodes").getJSONObject(0).getString("status"))
    }

    @Test
    fun poseInitializationBlocksWhenRequiredAnchorPassIsMissing() {
        val workspaceDir = tempDir("scanner-pose-missing-anchor")
        writeReconstructionJob(
            workspaceDir = workspaceDir,
            frames = listOf(
                "low_1" to ScannerCapturePass.LowRing,
                "mid_1" to ScannerCapturePass.MidRing
            )
        )
        writeMatchGraph(
            workspaceDir = workspaceDir,
            allowed = true,
            errors = emptyList(),
            acceptedPairs = listOf(Triple("low_1", "mid_1", 30))
        )

        val result = initializeScannerPoseGraph(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("not_enough_anchor_passes:high_ring"))
    }

    private fun writeReconstructionJob(
        workspaceDir: File,
        frames: List<Pair<String, ScannerCapturePass>> = listOf(
            "low_1" to ScannerCapturePass.LowRing,
            "mid_1" to ScannerCapturePass.MidRing,
            "high_1" to ScannerCapturePass.HighRing
        )
    ) {
        workspaceDir.mkdirs()
        workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put(
                    "frames",
                    JSONArray().apply {
                        frames.forEach { (frameId, pass) ->
                            put(
                                JSONObject()
                                    .put("frame_id", frameId)
                                    .put("capture_pass", pass.manifestValue)
                            )
                        }
                    }
                )
                .toString(2)
        )
    }

    private fun writeMatchGraph(
        workspaceDir: File,
        allowed: Boolean,
        errors: List<String>,
        acceptedPairs: List<Triple<String, String, Int>>
    ) {
        workspaceDir.resolve("matches").mkdirs()
        workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("allowed", allowed)
                .put("errors", JSONArray(errors))
                .put(
                    "pairs",
                    JSONArray().apply {
                        acceptedPairs.forEach { (frameA, frameB, matchCount) ->
                            put(
                                JSONObject()
                                    .put("frame_a", frameA)
                                    .put("frame_b", frameB)
                                    .put("match_count", matchCount)
                                    .put("accepted", true)
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

package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerDepthFusionTest {
    @Test
    fun depthFusionBlocksWhenWorkspaceIsMissing() {
        val workspaceDir = tempDir("scanner-depth-missing")

        val result = buildScannerDepthFusion(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("reconstruction_workspace_missing"))
        assertTrue(workspaceDir.resolve(SCANNER_DEPTH_FUSION_PATH).isFile)
    }

    @Test
    fun depthFusionBlocksWhenNoDepthFramesExist() {
        val workspaceDir = tempDir("scanner-depth-none")
        writeWorkspace(workspaceDir, depthFrameCount = 0)

        val result = buildScannerDepthFusion(workspaceDir)

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("depth_frame_count_low"))
        assertTrue(result.errors.contains("dense_depth_assist_blocked_until_depth_fusion"))
    }

    @Test
    fun depthFusionAcceptsMeasuredDepthFrames() {
        val workspaceDir = tempDir("scanner-depth-ready")
        writeWorkspace(workspaceDir, depthFrameCount = 4)

        val result = buildScannerDepthFusion(
            workspaceDir,
            ScannerDepthFusionLimits(minDepthFrameCount = 3, minAverageDepthCoverage = 0.50f)
        )

        assertTrue(result.allowed)
        assertTrue(result.metric)
        assertTrue(result.depthFusionReady)
        assertTrue(result.denseReconstructionAssistReady)
        assertEquals(4, result.depthFrameCount)
        assertTrue(result.averageDepthCoverage >= 0.50f)

        val json = JSONObject(workspaceDir.resolve(SCANNER_DEPTH_FUSION_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getBoolean("depth_fusion_ready"))
        assertEquals(4, json.getJSONArray("frames").length())
        assertEquals(16, json.getJSONArray("frames").getJSONObject(0).getJSONArray("pose_world_from_camera").length())
    }

    private fun writeWorkspace(workspaceDir: File, depthFrameCount: Int) {
        workspaceDir.mkdirs()
        val frames = JSONArray()
        repeat(4) { index ->
            val frameId = "frame_$index"
            val depthPath = if (index < depthFrameCount) "depth/${index.toString().padStart(6, '0')}_depth16.png" else null
            val confidencePath =
                if (index < depthFrameCount) "depth/${index.toString().padStart(6, '0')}_confidence.png" else null
            if (depthPath != null) {
                workspaceDir.resolve(depthPath).also {
                    it.parentFile?.mkdirs()
                    it.writeBytes(byteArrayOf(1, 2, 3, 4))
                }
            }
            if (confidencePath != null) {
                workspaceDir.resolve(confidencePath).also {
                    it.parentFile?.mkdirs()
                    it.writeBytes(byteArrayOf(4, 3, 2, 1))
                }
            }
            frames.put(
                JSONObject()
                    .put("frame_id", frameId)
                    .put("staged_depth16", depthPath ?: JSONObject.NULL)
                    .put("staged_depth16_sha256", if (depthPath != null) "hash_$index" else JSONObject.NULL)
                    .put("staged_depth_confidence", confidencePath ?: JSONObject.NULL)
                    .put("staged_depth_confidence_sha256", if (confidencePath != null) "conf_hash_$index" else JSONObject.NULL)
                    .put(
                        "pose_world_from_camera",
                        if (depthPath != null) {
                            JSONArray(
                                listOf(
                                    1.0, 0.0, 0.0, index.toDouble(),
                                    0.0, 1.0, 0.0, 0.0,
                                    0.0, 0.0, 1.0, 0.0,
                                    0.0, 0.0, 0.0, 1.0
                                )
                            )
                        } else {
                            JSONObject.NULL
                        }
                    )
                    .put("quality", JSONObject().put("depth_coverage", if (depthPath != null) 0.70 else JSONObject.NULL))
            )
        }
        workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("frames", frames)
                .toString(2)
        )
    }

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}

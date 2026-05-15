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

class ScannerFeatureTracksTest {
    @Test
    fun featureTracksConnectPairCorrespondencesAcrossFrames() {
        val workspaceDir = tempDir("scanner-tracks-connected")
        writeFeatureFrames(workspaceDir)
        writeMatchGraph(
            workspaceDir,
            pairs = listOf(
                matchPair("low", "mid", 0 until 8),
                matchPair("mid", "high", 0 until 8),
                matchPair("low", "high", 0 until 8)
            )
        )

        val result = buildScannerFeatureTracks(
            workspaceDir,
            ScannerFeatureTrackLimits(
                minTrackCount = 8,
                minLongTrackCount = 8,
                minSpatialCells = 2
            )
        )

        assertTrue(result.errors.joinToString(), result.allowed)
        assertEquals(8, result.trackCount)
        assertEquals(8, result.longTrackCount)
        assertTrue(result.maxTrackLength >= 3)
        assertEquals(
            setOf(ScannerCapturePass.LowRing, ScannerCapturePass.MidRing, ScannerCapturePass.HighRing),
            result.observedPasses
        )
        val json = JSONObject(workspaceDir.resolve(SCANNER_FEATURE_TRACKS_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertEquals(8, json.getJSONArray("tracks").length())
    }

    @Test
    fun featureTracksBlockWeakPairwiseOnlyCoverage() {
        val workspaceDir = tempDir("scanner-tracks-weak")
        writeFeatureFrames(workspaceDir)
        writeMatchGraph(
            workspaceDir,
            pairs = listOf(matchPair("low", "mid", 0 until 4))
        )

        val result = buildScannerFeatureTracks(
            workspaceDir,
            ScannerFeatureTrackLimits(
                minTrackCount = 8,
                minLongTrackCount = 4,
                minSpatialCells = 2
            )
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.contains("not_enough_feature_tracks"))
        assertTrue(result.errors.contains("not_enough_long_feature_tracks"))
        assertTrue(result.errors.contains("track_pass_missing:high_ring"))
    }

    @Test
    fun realDeviceScanUsesDescriptorSeededTracksWithoutRelaxingMetricGates() {
        val workspaceDir = tempDir("scanner-tracks-real-device")
        copyRealScanFeatureArtifacts(workspaceDir)

        val result = buildScannerFeatureTracks(workspaceDir)

        assertTrue(result.errors.joinToString(), result.allowed)
        assertTrue("expected real scan to produce enough tracks", result.trackCount >= 40)
        assertTrue("expected descriptor seeded long tracks", result.longTrackCount >= 12)
        assertTrue(result.descriptorSeededTrackCount > 0)
        assertTrue(result.warnings.contains("descriptor_seeded_tracks_require_metric_pose_validation"))

        val json = JSONObject(workspaceDir.resolve(SCANNER_FEATURE_TRACKS_PATH).readText())
        assertTrue(json.getBoolean("allowed"))
        assertTrue(json.getInt("descriptor_seeded_track_count") > 0)
        assertTrue(json.getJSONObject("limits").getBoolean("enable_descriptor_seeded_tracks"))
    }

    private fun writeFeatureFrames(workspaceDir: File) {
        workspaceDir.resolve("features").mkdirs()
        workspaceDir.resolve(SCANNER_FEATURES_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put(
                    "frames",
                    JSONArray().apply {
                        put(featureFrame("low", ScannerCapturePass.LowRing))
                        put(featureFrame("mid", ScannerCapturePass.MidRing))
                        put(featureFrame("high", ScannerCapturePass.HighRing))
                    }
                )
                .toString(2)
        )
    }

    private fun featureFrame(frameId: String, pass: ScannerCapturePass): JSONObject =
        JSONObject()
            .put("frame_id", frameId)
            .put("capture_pass", pass.manifestValue)
            .put("width", 120)
            .put("height", 120)

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

    private fun matchPair(frameA: String, frameB: String, range: IntRange): JSONObject =
        JSONObject()
            .put("frame_a", frameA)
            .put("frame_b", frameB)
            .put("match_count", range.count())
            .put("accepted", true)
            .put(
                "matches",
                JSONArray().apply {
                    range.forEach { index ->
                        val x = 12 + index * 9
                        val y = 16 + (index % 4) * 18
                        put(
                            JSONObject()
                                .put("ax", x)
                                .put("ay", y)
                                .put("bx", x + 2)
                                .put("by", y + 1)
                                .put("descriptor_distance", 4)
                        )
                    }
                }
            )

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()

    private fun copyRealScanFeatureArtifacts(workspaceDir: File) {
        val fixtureRoot = File(
            "src/test/resources/scanner/real_scan_c2a4f330/" +
                "scan_c2a4f330-2251-4686-8076-d1729b571cd2"
        )
        require(fixtureRoot.isDirectory) { "Real scan fixture missing: ${fixtureRoot.absolutePath}" }
        listOf(SCANNER_FEATURES_PATH, SCANNER_MATCH_GRAPH_PATH).forEach { relativePath ->
            val source = fixtureRoot.resolve(relativePath)
            val target = workspaceDir.resolve(relativePath)
            target.parentFile?.mkdirs()
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

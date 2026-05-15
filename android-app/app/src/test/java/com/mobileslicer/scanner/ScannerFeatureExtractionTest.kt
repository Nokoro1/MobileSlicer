package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerFeatureExtractionTest {
    @Test
    fun featureExtractorFindsRepeatableGradientCorners() {
        val image = checkerLumaImage(width = 72, height = 72)

        val features = extractScannerImageFeatures(
            image,
            ScannerFeatureExtractionLimits(minFeaturesPerFrame = 12, maxFeaturesPerFrame = 80)
        )

        assertTrue(features.size >= 12)
        assertEquals(features, extractScannerImageFeatures(image, ScannerFeatureExtractionLimits(maxFeaturesPerFrame = 80)))
        assertTrue(features.all { it.score > 40 })
    }

    @Test
    fun pairMatcherAcceptsSharedDescriptorsAndRejectsBlankFrames() {
        val limits = ScannerFeatureExtractionLimits(minFeaturesPerFrame = 8, minPairMatches = 8)
        val featureA = extractScannerImageFeatures(checkerLumaImage(72, 72), limits)
        val featureB = extractScannerImageFeatures(checkerLumaImage(72, 72), limits)
        val blank = extractScannerImageFeatures(blankLumaImage(72, 72), limits)
        val reports = listOf(
            featureReport("a", ScannerCapturePass.LowRing, featureA),
            featureReport("b", ScannerCapturePass.MidRing, featureB),
            featureReport("c", ScannerCapturePass.HighRing, blank)
        )

        val pairs = buildScannerPairMatchReports(reports, limits)

        assertTrue(pairs.single { it.frameA == "a" && it.frameB == "b" }.accepted)
        assertTrue(pairs.single { it.frameA == "a" && it.frameB == "b" }.matches.isNotEmpty())
        assertFalse(pairs.single { it.frameA == "a" && it.frameB == "c" }.accepted)
    }

    @Test
    fun featureExtractorRejectsBorderFeatures() {
        val image = borderOnlyLumaImage(width = 96, height = 96)
        val limits = ScannerFeatureExtractionLimits(
            minFeaturesPerFrame = 1,
            maxFeaturesPerFrame = 80,
            borderRejectRatio = 0.12f
        )

        val features = extractScannerImageFeatures(image, limits)

        assertTrue(features.isEmpty())
    }

    @Test
    fun featureExtractorUsesMaskAsSoftSupportWithoutHardDeletingEdges() {
        val image = checkerLumaImage(width = 96, height = 96)
        val mask = ScannerLumaImage(
            width = 96,
            height = 96,
            luma = IntArray(96 * 96) { index ->
                val x = index % 96
                when {
                    x >= 48 -> 255
                    x >= 40 -> 24
                    else -> 0
                }
            }
        )
        val limits = ScannerFeatureExtractionLimits(
            minFeaturesPerFrame = 8,
            maxFeaturesPerFrame = 80,
            borderRejectRatio = 0.04f,
            maskHardRejectThreshold = 4,
            maskSupportThreshold = 128
        )

        val features = extractScannerImageFeatures(image, limits, mask)

        assertTrue(features.isNotEmpty())
        assertTrue(features.all { it.x >= 40 })
        assertTrue(features.any { it.x in 40 until 48 && it.maskSupport == 24 })
        assertTrue(features.any { it.maskSupport == 255 })
    }

    @Test
    fun workspaceFeatureGraphWritesDiagnosticsForConnectedFrames() {
        val workspaceDir = tempDir("scanner-feature-workspace")
        writeFeatureWorkspace(workspaceDir, frameCount = 6, blankLastFrame = false)

        val result = buildScannerFeatureMatchGraph(
            workspaceDir,
            ScannerFeatureExtractionLimits(
                minFeaturesPerFrame = 8,
                maxFeaturesPerFrame = 80,
                minPairMatches = 8
            )
        )

        assertTrue(result.errors.joinToString(), result.allowed)
        assertEquals(1, result.connectedComponentCount)
        assertTrue(workspaceDir.resolve(SCANNER_FEATURES_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH).isFile)
        val graph = JSONObject(workspaceDir.resolve(SCANNER_MATCH_GRAPH_PATH).readText())
        assertTrue(graph.getBoolean("allowed"))
        assertEquals(15, graph.getJSONArray("pairs").length())
        val firstPair = graph.getJSONArray("pairs").getJSONObject(0)
        assertTrue(firstPair.getJSONArray("matches").length() > 0)
        assertTrue(firstPair.getInt("spatial_cell_count") > 0)
        assertFalse(firstPair.isNull("average_descriptor_distance"))
    }

    @Test
    fun workspaceFeatureGraphBlocksDisconnectedOrWeakFrame() {
        val workspaceDir = tempDir("scanner-feature-workspace-weak")
        writeFeatureWorkspace(workspaceDir, frameCount = 3, blankLastFrame = true)

        val result = buildScannerFeatureMatchGraph(
            workspaceDir,
            ScannerFeatureExtractionLimits(
                minFeaturesPerFrame = 8,
                maxFeaturesPerFrame = 80,
                minPairMatches = 8
            )
        )

        assertFalse(result.allowed)
        assertTrue(result.errors.any { it.startsWith("not_enough_features") })
        assertTrue(result.errors.contains("match_graph_disconnected"))
    }

    private fun featureReport(
        frameId: String,
        pass: ScannerCapturePass,
        features: List<ScannerImageFeature>
    ): ScannerFrameFeatureReport =
        ScannerFrameFeatureReport(
            frameId = frameId,
            stagedImagePath = "images/$frameId.jpg",
            stagedMaskPath = null,
            capturePass = pass,
            imageWidth = 72,
            imageHeight = 72,
            features = features,
            warnings = emptyList()
        )

    private fun writeFeatureWorkspace(
        workspaceDir: File,
        frameCount: Int,
        blankLastFrame: Boolean
    ) {
        workspaceDir.mkdirs()
        workspaceDir.resolve("images").mkdirs()
        val frames = org.json.JSONArray()
        repeat(frameCount) { index ->
            val id = (index + 1).toString().padStart(6, '0')
            val imagePath = "images/$id.jpg"
            val blank = blankLastFrame && index == frameCount - 1
            workspaceDir.resolve(imagePath).writeText(
                if (blank) {
                    plainGrayMap(blankLumaImage(72, 72))
                } else {
                    plainGrayMap(checkerLumaImage(72, 72))
                }
            )
            frames.put(
                JSONObject()
                    .put("frame_id", id)
                    .put("staged_image", imagePath)
                    .put("staged_mask", JSONObject.NULL)
                    .put(
                        "capture_pass",
                        when (index % 3) {
                            0 -> ScannerCapturePass.LowRing.manifestValue
                            1 -> ScannerCapturePass.MidRing.manifestValue
                            else -> ScannerCapturePass.HighRing.manifestValue
                        }
                    )
            )
        }
        workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).writeText(
            JSONObject()
                .put("schema_version", SCANNER_RECONSTRUCTION_WORKSPACE_SCHEMA_VERSION)
                .put("frames", frames)
                .toString(2)
        )
    }

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

    private fun blankLumaImage(width: Int, height: Int): ScannerLumaImage =
        ScannerLumaImage(width = width, height = height, luma = IntArray(width * height) { 127 })

    private fun borderOnlyLumaImage(width: Int, height: Int): ScannerLumaImage =
        ScannerLumaImage(
            width = width,
            height = height,
            luma = IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                if (x < 8 || y < 8 || x >= width - 8 || y >= height - 8) {
                    if (((x / 4) + (y / 4)) % 2 == 0) 20 else 235
                } else {
                    127
                }
            }
        )

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

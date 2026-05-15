package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerExportPipelineHandoffTest {
    @Test
    fun compactExportPackageDirectoryFeedsPipelineWithoutReadingRawSessionFolder() {
        val sessionDir = tempDir("scanner-export-session")
        val workspaceDir = tempDir("scanner-export-workspace")
        val accepted = (0 until 44).map { index ->
            val id = (index + 1).toString().padStart(6, '0')
            val pass = when {
                index < 12 -> ScannerCapturePass.MidRing
                index < 24 -> ScannerCapturePass.HighRing
                index < 36 -> ScannerCapturePass.LowRing
                else -> ScannerCapturePass.TopDetail
            }
            writeFrame(sessionDir, id)
            frame(id = id, pass = pass, accepted = true, sessionDir = sessionDir)
        }
        val rejected = (0 until 12).map { index ->
            val id = "rejected_${(index + 1).toString().padStart(3, '0')}"
            writeFrame(sessionDir, id)
            frame(id = id, pass = ScannerCapturePass.MidRing, accepted = false, sessionDir = sessionDir)
        }

        val export = exportCapturePackage(
            sessionDir = sessionDir,
            scanId = "scan_export_handoff",
            frames = accepted + rejected,
            measuredScaleBarMm = null,
            context = null,
            markerDetector = NoMarkerDetector,
            softMaskGenerator = NoOpSoftMaskGenerator
        )
        val pipeline = runScannerLocalReconstructionPipeline(
            packageDir = export.packageDir,
            workspaceDir = workspaceDir
        )

        assertTrue(export.validation.errors.joinToString(), export.validation.valid)
        assertEquals(44, export.validation.manifest?.frameCount)
        assertEquals(44, export.validation.manifest?.acceptedFrameCount)
        assertEquals(0, export.validation.manifest?.rejectedFrameCount)
        assertTrue(export.zipFile.name.contains("_44_"))
        assertTrue(export.packageDir.resolve(SCANNER_MANIFEST_PATH).isFile)
        assertFalse(sessionDir.resolve(SCANNER_MANIFEST_PATH).exists())

        val rejectionDiagnostics = JSONObject(
            export.packageDir.resolve("diagnostics/capture_rejections_compact.json").readText()
        )
        assertEquals(56, rejectionDiagnostics.getInt("captured_frame_count"))
        assertEquals(44, rejectionDiagnostics.getInt("accepted_frame_count"))
        assertEquals(12, rejectionDiagnostics.getInt("rejected_frame_count"))
        assertFalse(rejectionDiagnostics.getBoolean("rejected_image_files_exported"))

        assertTrue(pipeline.completed)
        assertFalse(pipeline.blockingErrors.any { it.contains("package_validation_failed") })
        assertFalse(pipeline.blockingErrors.any { it.contains("manifest.json") })
        assertTrue(workspaceDir.resolve(SCANNER_RECONSTRUCTION_SUMMARY_PATH).isFile)
    }

    private object NoMarkerDetector : ScannerMarkerDetector {
        override val detectorName: String = "test_no_marker_detector"
        override val detectorStatus: String = "ready"
        override fun detectMarkers(frame: ScanFrame, absoluteFramePath: String): List<MarkerObservation> = emptyList()
    }

    private object NoOpSoftMaskGenerator : ScannerSoftMaskGenerator {
        override val generatorName: String = "test_noop_soft_mask"
        override val generatorStatus: String = "disabled_for_handoff_test"
        override fun generateMask(
            sessionDir: File,
            frame: ScanFrame,
            absoluteFramePath: String,
            objectRoi: ScannerObjectRoi
        ): ScannerSoftMaskResult? = null
    }

    private fun writeFrame(sessionDir: File, id: String) {
        sessionDir.resolve("frames/$id.jpg").also {
            requireNotNull(it.parentFile).mkdirs()
            it.writeText(plainGrayMap(checkerLumaImage(72, 72)))
        }
    }

    private fun frame(
        id: String,
        pass: ScannerCapturePass,
        accepted: Boolean,
        sessionDir: File
    ): ScanFrame =
        ScanFrame(
            id = id,
            timestampNs = 1_000_000_000L,
            rgbPath = "frames/$id.jpg",
            rgbSha256 = sha256Hex(sessionDir.resolve("frames/$id.jpg")),
            maskPath = null,
            maskSha256 = null,
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
            exposureTimeNs = 8_333_333L,
            iso = 160,
            focusDistance = 0.7f,
            whiteBalanceMode = "auto",
            width = 72,
            height = 72,
            capturePass = pass,
            quality = FrameQuality(
                blurScore = 220f,
                exposureScore = 0.9f,
                overlapScore = 0.7f,
                trackingGood = null,
                depthCoverage = null,
                markerVisibility = 0f,
                scaleConfidence = 0f,
                materialRisk = 0.1f,
                accepted = accepted,
                rejectionReasons = if (accepted) emptyList() else listOf("insufficient_view_change")
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

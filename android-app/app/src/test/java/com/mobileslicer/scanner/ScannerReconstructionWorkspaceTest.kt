package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerReconstructionWorkspaceTest {
    @Test
    fun workspaceBuilderRejectsInvalidPackageBeforePreflight() {
        val packageDir = tempDir("scanner-reconstruction-invalid-package")
        val workspaceDir = tempDir("scanner-reconstruction-invalid-workspace")

        val result = buildLocalReconstructionWorkspace(packageDir, workspaceDir)

        assertFalse(result.created)
        assertTrue(result.errors.contains("package_validation_failed"))
        assertTrue(result.errors.any { it.startsWith("Invalid manifest") })
    }

    @Test
    fun workspaceBuilderRejectsPackageThatFailsPreflight() {
        val packageDir = tempDir("scanner-reconstruction-preflight-fail")
        writePackage(
            directory = packageDir,
            frames = listOf(reconstructionFrame("000001", ScannerCapturePass.MidRing, hasMask = false)),
            hasMasks = false,
            scaleSource = ScannerScaleSource.None,
            calibration = null
        )
        val workspaceDir = tempDir("scanner-reconstruction-preflight-workspace")

        val result = buildLocalReconstructionWorkspace(packageDir, workspaceDir)

        assertFalse(result.created)
        assertTrue(result.errors.contains("local_reconstruction_preflight_failed"))
        assertTrue(result.errors.contains("not_enough_accepted_frames"))
        assertTrue(result.errors.contains("scale_unverified"))
        assertTrue(result.stagedFrames.isEmpty())
    }

    @Test
    fun workspaceBuilderStagesApprovedFramesDeterministically() {
        val packageDir = tempDir("scanner-reconstruction-package")
        val frames = (0 until 36).map { index ->
            val pass = when (index % 3) {
                0 -> ScannerCapturePass.LowRing
                1 -> ScannerCapturePass.MidRing
                else -> ScannerCapturePass.HighRing
            }
            reconstructionFrame(index.toString().padStart(6, '0'), pass, hasMask = true)
        }
        writePackage(directory = packageDir, frames = frames, hasMasks = true)
        val workspaceDir = tempDir("scanner-reconstruction-workspace")

        val result = buildLocalReconstructionWorkspace(packageDir, workspaceDir)

        assertTrue(result.errors.joinToString(), result.created)
        assertEquals(36, result.stagedFrames.size)
        assertEquals(SCANNER_RECONSTRUCTION_MANIFEST_PATH, result.manifestPath)
        assertTrue(workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).isFile)
        assertTrue(workspaceDir.resolve(SCANNER_RECONSTRUCTION_INPUT_HASHES_PATH).isFile)
        assertTrue(workspaceDir.resolve("images/000001.jpg").isFile)
        assertTrue(workspaceDir.resolve("masks/000001_mask.png").isFile)

        val job = JSONObject(workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).readText())
        assertEquals("scan_reconstruction_workspace", job.getString("scan_id"))
        assertEquals(36, job.getInt("frame_count"))
        assertTrue(job.getJSONObject("preflight").getBoolean("allowed"))
        assertEquals("low_ring", job.getJSONArray("frames").getJSONObject(0).getString("capture_pass"))
        assertEquals("images/000001.jpg", job.getJSONArray("frames").getJSONObject(0).getString("staged_image"))
        assertEquals(16, job.getJSONArray("marker_corner_observations").length())
        assertEquals(0.0, job.getJSONArray("marker_corner_observations").getJSONObject(0).getJSONArray("world_xyz_mm").getDouble(2), 0.0)
    }

    @Test
    fun workspaceBuilderStagesDepthEvidenceWhenPresent() {
        val packageDir = tempDir("scanner-reconstruction-depth-package")
        val frames = (0 until 36).map { index ->
            val pass = when (index % 3) {
                0 -> ScannerCapturePass.LowRing
                1 -> ScannerCapturePass.MidRing
                else -> ScannerCapturePass.HighRing
            }
            reconstructionFrame(
                id = index.toString().padStart(6, '0'),
                pass = pass,
                hasMask = true,
                hasDepth = index < 4,
                hasPose = index < 4
            )
        }
        writePackage(directory = packageDir, frames = frames, hasMasks = true, hasDepth = true, hasArCorePoses = true)
        val workspaceDir = tempDir("scanner-reconstruction-depth-workspace")

        val result = buildLocalReconstructionWorkspace(packageDir, workspaceDir)

        assertTrue(result.errors.joinToString(), result.created)
        assertTrue(workspaceDir.resolve("depth/000001_depth16.png").isFile)
        assertTrue(workspaceDir.resolve("depth/000001_confidence.png").isFile)

        val firstFrame = JSONObject(workspaceDir.resolve(SCANNER_RECONSTRUCTION_MANIFEST_PATH).readText())
            .getJSONArray("frames")
            .getJSONObject(0)
        assertEquals("frames/000000_depth16.png", firstFrame.getString("source_depth16"))
        assertEquals("depth/000001_depth16.png", firstFrame.getString("staged_depth16"))
        assertTrue(firstFrame.getString("staged_depth16_sha256").isNotBlank())
        assertEquals(16, firstFrame.getJSONArray("pose_world_from_camera").length())
        assertEquals(1.0, firstFrame.getJSONArray("pose_world_from_camera").getDouble(0), 0.0)
        assertEquals(0.72, firstFrame.getJSONObject("quality").getDouble("depth_coverage"), 0.0)
    }

    private fun writePackage(
        directory: File,
        frames: List<ScanFrame>,
        hasMasks: Boolean,
        hasDepth: Boolean = false,
        hasArCorePoses: Boolean = false,
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
        frames.forEach { frame ->
            directory.resolve(frame.rgbPath).also {
                requireNotNull(it.parentFile).mkdirs()
                it.writeText("image-${frame.id}")
            }
            frame.maskPath?.let { maskPath ->
                directory.resolve(maskPath).also {
                    requireNotNull(it.parentFile).mkdirs()
                    it.writeText("mask-${frame.id}")
                }
            }
            frame.depth16Path?.let { depthPath ->
                directory.resolve(depthPath).also {
                    requireNotNull(it.parentFile).mkdirs()
                    it.writeBytes(byteArrayOf(1, 2, 3, 4))
                }
            }
            frame.depthConfidencePath?.let { confidencePath ->
                directory.resolve(confidencePath).also {
                    requireNotNull(it.parentFile).mkdirs()
                    it.writeBytes(byteArrayOf(4, 3, 2, 1))
                }
            }
        }
        val framesWithHashes = frames.map { frame ->
            frame.copy(
                rgbSha256 = sha256Hex(directory.resolve(frame.rgbPath)),
                maskSha256 = frame.maskPath?.let { sha256Hex(directory.resolve(it)) },
                depthSha256 = frame.depth16Path?.let { sha256Hex(directory.resolve(it)) }
            )
        }
        writeScannerPackageDirectory(
            directory = directory,
            manifest = reconstructionManifest(
                frames = framesWithHashes,
                hasMasks = hasMasks,
                hasDepth = hasDepth,
                hasArCorePoses = hasArCorePoses,
                scaleSource = scaleSource,
                calibration = calibration
            ),
            writeDiagnostics = {
                writeWorkspaceMarkerObservations(directory, framesWithHashes.take(4))
            }
        )
    }

    private fun writeWorkspaceMarkerObservations(directory: File, frames: List<ScanFrame>) {
        val observations = JSONArray()
        frames.forEachIndexed { index, frame ->
            val marker = defaultMobileSlicerMarkerMatLayout().markers[index]
            observations.put(
                JSONObject()
                    .put("marker_system", ScannerMarkerSystem.AprilTag.manifestValue)
                    .put("marker_id", marker.id.toString())
                    .put("frame_id", frame.id)
                    .put("frame_path", frame.rgbPath)
                    .put("marker_size_mm", marker.sizeMm.toDouble())
                    .put("reprojection_error_px", 1.0)
                    .put(
                        "corners_px",
                        JSONArray().apply {
                            put(JSONArray(listOf(960.0, 540.0)))
                            put(JSONArray(listOf(1040.0, 540.0)))
                            put(JSONArray(listOf(1040.0, 620.0)))
                            put(JSONArray(listOf(960.0, 620.0)))
                        }
                    )
            )
        }
        directory.resolve("diagnostics").mkdirs()
        directory.resolve("diagnostics/marker_observations.json").writeText(
            JSONObject().put("observations", observations).toString(2)
        )
    }

    private fun reconstructionManifest(
        frames: List<ScanFrame>,
        hasMasks: Boolean,
        hasDepth: Boolean,
        hasArCorePoses: Boolean,
        scaleSource: ScannerScaleSource,
        calibration: ScannerCalibration?
    ): ScanPackageManifest =
        ScanPackageManifest(
            scanId = "scan_reconstruction_workspace",
            createdAtIso8601 = "2026-05-08T12:00:00Z",
            mode = ScannerMode.LocalPrintable,
            captureProfile = ScannerCaptureProfile.HighResPhoto,
            frameCount = frames.size,
            acceptedFrameCount = frames.count { it.quality.accepted },
            forcedFrameCount = frames.count { it.forcedCapture },
            rejectedFrameCount = frames.count { !it.quality.accepted },
            hasArCorePoses = hasArCorePoses,
            hasDepth = hasDepth,
            hasMasks = hasMasks,
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

    private fun reconstructionFrame(
        id: String,
        pass: ScannerCapturePass,
        hasMask: Boolean,
        hasDepth: Boolean = false,
        hasPose: Boolean = false
    ): ScanFrame =
        ScanFrame(
            id = id,
            timestampNs = 123456789L,
            rgbPath = "frames/$id.jpg",
            rgbSha256 = "",
            maskPath = if (hasMask) "frames/${id}_mask.png" else null,
            maskSha256 = null,
            depth16Path = if (hasDepth) "frames/${id}_depth16.png" else null,
            depthSha256 = null,
            depthConfidencePath = if (hasDepth) "frames/${id}_confidence.png" else null,
            poseWorldFromCamera = if (hasPose) {
                floatArrayOf(
                    1f, 0f, 0f, 10f,
                    0f, 1f, 0f, 20f,
                    0f, 0f, 1f, 30f,
                    0f, 0f, 0f, 1f
                )
            } else {
                null
            },
            intrinsics = CameraIntrinsicsData(
                fx = 1430f,
                fy = 1430f,
                cx = 960f,
                cy = 540f,
                imageWidth = 1920,
                imageHeight = 1080
            ),
            distortion = null,
            lensFacing = "back",
            focalLengthMm = 5.4f,
            exposureTimeNs = 8333333L,
            iso = 160,
            focusDistance = 0.7f,
            whiteBalanceMode = "auto",
            width = 1920,
            height = 1080,
            capturePass = pass,
            quality = FrameQuality(
                blurScore = 180f,
                exposureScore = 0.92f,
                overlapScore = 0.7f,
                trackingGood = null,
                depthCoverage = if (hasDepth) 0.72f else null,
                markerVisibility = 0.7f,
                scaleConfidence = 0.90f,
                materialRisk = 0.1f,
                accepted = true
            ),
            forcedCapture = false
        )

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}

package com.mobileslicer.scanner

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerPackageTest {
    @Test
    fun manifestRoundTripsWithCalibrationAndFrameMetadata() {
        val manifest = sampleManifest()

        val parsed = parseScanPackageManifest(manifest.toJson())

        assertEquals(manifest, parsed)
        assertEquals(SCANNER_PACKAGE_UNITS, manifest.toJson().getString("units"))
        assertEquals("verified_marker_mat", manifest.toJson().getString("scale_source"))
    }

    @Test
    fun packageDirectoryValidatesHashesAndReferencedFiles() {
        val directory = tempDir("scanner-package-dir")
        val frameFile = directory.resolve("frames/000001.jpg")
        requireNotNull(frameFile.parentFile).mkdirs()
        frameFile.writeText("not actually jpeg, but deterministic test bytes")
        val manifest = sampleManifest(
            rgbPath = "frames/000001.jpg",
            rgbSha256 = sha256Hex(frameFile)
        )
        writeScannerPackageDirectory(directory, manifest)

        val result = validateScannerPackageDirectory(directory)

        assertTrue(result.errors.joinToString(), result.valid)
        assertNotNull(result.manifest)
        assertEquals(3, result.fileCount)
    }

    @Test
    fun packageDirectoryRejectsTamperedFrameBytes() {
        val directory = tempDir("scanner-package-tamper")
        val frameFile = directory.resolve("frames/000001.jpg")
        requireNotNull(frameFile.parentFile).mkdirs()
        frameFile.writeText("original frame bytes")
        val manifest = sampleManifest(
            rgbPath = "frames/000001.jpg",
            rgbSha256 = sha256Hex(frameFile)
        )
        writeScannerPackageDirectory(directory, manifest)
        frameFile.writeText("tampered frame bytes")

        val result = validateScannerPackageDirectory(directory)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it == "Hash mismatch: frames/000001.jpg" })
    }

    @Test
    fun packageZipRoundTripValidates() {
        val directory = tempDir("scanner-package-zip-source")
        val zipFile = File(tempDir("scanner-package-zip-out"), "scan_package.zip")
        val frameFile = directory.resolve("frames/000001.jpg")
        requireNotNull(frameFile.parentFile).mkdirs()
        frameFile.writeText("frame bytes")
        val manifest = sampleManifest(
            rgbPath = "frames/000001.jpg",
            rgbSha256 = sha256Hex(frameFile)
        )
        writeScannerPackageDirectory(directory, manifest)

        writeScannerPackageZip(directory, zipFile)
        val result = validateScannerPackageZip(zipFile)

        assertTrue(result.errors.joinToString(), result.valid)
        assertEquals(manifest, result.manifest)
        assertEquals(3, result.fileCount)
    }

    @Test
    fun packageDirectoryValidatesSoftMaskReferenceAndHash() {
        val directory = tempDir("scanner-package-mask")
        val frameFile = directory.resolve("frames/000001.jpg")
        val maskFile = directory.resolve("frames/000001_mask.png")
        requireNotNull(frameFile.parentFile).mkdirs()
        frameFile.writeText("frame bytes")
        maskFile.writeText("mask bytes")
        val manifest = sampleManifest(
            rgbPath = "frames/000001.jpg",
            rgbSha256 = sha256Hex(frameFile),
            maskPath = "frames/000001_mask.png",
            maskSha256 = sha256Hex(maskFile),
            hasMasks = true
        )
        writeScannerPackageDirectory(directory, manifest)

        val result = validateScannerPackageDirectory(directory)

        assertTrue(result.errors.joinToString(), result.valid)
        assertTrue(result.manifest?.hasMasks == true)
        assertEquals(4, result.fileCount)
    }

    @Test
    fun packageDirectoryRejectsCapabilityFlagsThatDoNotMatchFrames() {
        val directory = tempDir("scanner-package-capabilities")
        val frameFile = directory.resolve("frames/000001.jpg")
        requireNotNull(frameFile.parentFile).mkdirs()
        frameFile.writeText("frame bytes")
        val manifest = sampleManifest(
            rgbPath = "frames/000001.jpg",
            rgbSha256 = sha256Hex(frameFile),
            poseWorldFromCamera = FloatArray(16) { index -> if (index % 5 == 0) 1f else 0f },
            hasArCorePoses = false
        )
        writeScannerPackageDirectory(directory, manifest)

        val result = validateScannerPackageDirectory(directory)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it == "Manifest has_arcore_poses does not match frame pose metadata" })
    }

    @Test
    fun packageDirectoryHashesDiagnosticsWrittenBeforeHashFile() {
        val directory = tempDir("scanner-package-diagnostics")
        val frameFile = directory.resolve("frames/000001.jpg")
        requireNotNull(frameFile.parentFile).mkdirs()
        frameFile.writeText("frame bytes")
        val manifest = sampleManifest(
            rgbPath = "frames/000001.jpg",
            rgbSha256 = sha256Hex(frameFile)
        )

        writeScannerPackageDirectory(
            directory = directory,
            manifest = manifest,
            writeDiagnostics = {
                directory.resolve("diagnostics/rejected_frames.json").also {
                    requireNotNull(it.parentFile).mkdirs()
                    it.writeText("""{"schema_version":1,"frames":[]}""")
                }
            }
        )

        val result = validateScannerPackageDirectory(directory)

        assertTrue(result.errors.joinToString(), result.valid)
        assertEquals(4, result.fileCount)
    }

    @Test
    fun packageZipRejectsPathTraversal() {
        val zipFile = File(tempDir("scanner-unsafe-zip"), "scan_package.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../manifest.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }

        val result = validateScannerPackageZip(zipFile)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Unsafe package path") })
    }

    @Test
    fun frameQualityRejectsUnprintableEvidence() {
        val quality = evaluateScannerFrameQuality(
            ScannerFrameQualityInput(
                blurScore = 20f,
                exposureScore = 0.9f,
                overlapScore = 0.5f,
                coverageGain = 0.1f,
                objectMaskArea = 0.2f,
                clippedHighlightRatio = 0.01f,
                trackingGood = true,
                depthCoverage = 0.4f,
                markerVisibility = 0.0f,
                scaleConfidence = 0.4f,
                materialRisk = 0.1f,
                depthRequired = false,
                markersRequired = true,
                scaleRequired = true,
                lateCapture = false
            )
        )

        assertFalse(quality.accepted)
        assertEquals(listOf("too_blurry", "markers_not_visible", "scale_unverified"), quality.rejectionReasons)
    }

    @Test
    fun readinessScoreUsesWeightedCategories() {
        val score = scannerReadinessScore(
            ScannerReadinessInput(
                angleCoverage = 1f,
                imageSharpness = 1f,
                scaleConfidence = 0f,
                maskConsistency = 0f,
                lightingScore = 1f,
                depthPoseSupport = 0f
            )
        )

        assertEquals(0.6f, score, 0.0001f)
    }

    private fun sampleManifest(
        rgbPath: String = "frames/000001.jpg",
        rgbSha256: String = "abc123",
        maskPath: String? = null,
        maskSha256: String? = null,
        hasMasks: Boolean = false,
        hasArCorePoses: Boolean = false,
        poseWorldFromCamera: FloatArray? = null
    ): ScanPackageManifest =
        ScanPackageManifest(
            scanId = "scan_123",
            createdAtIso8601 = "2026-05-08T12:00:00Z",
            mode = ScannerMode.LocalPrintable,
            captureProfile = ScannerCaptureProfile.HighResPhoto,
            frameCount = 1,
            acceptedFrameCount = 1,
            forcedFrameCount = 0,
            rejectedFrameCount = 0,
            hasArCorePoses = hasArCorePoses,
            hasDepth = false,
            hasMasks = hasMasks,
            scaleSource = ScannerScaleSource.VerifiedMarkerMat,
            calibration = ScannerCalibration(
                markerType = "apriltag",
                markerSizeMm = 40f,
                printedScaleBarExpectedMm = 100f,
                printedScaleBarMeasuredMm = 99.2f,
                scaleCorrection = 1.0080645f,
                scaleConfidence = 0.91f,
                markerReprojectionErrorPx = 1.6f
            ),
            twoSidedAlignment = ScannerTwoSidedAlignment(
                objectMovedDuringSession = false,
                alignmentMethod = ScannerAlignmentMethod.NotMoved,
                alignmentConfidence = null,
                passCount = 1
            ),
            requestedOutputs = listOf("stl", "3mf"),
            frames = listOf(
                ScanFrame(
                    id = "000001",
                    timestampNs = 123456789L,
                    rgbPath = rgbPath,
                    rgbSha256 = rgbSha256,
                    maskPath = maskPath,
                    maskSha256 = maskSha256,
                    depth16Path = null,
                    depthSha256 = null,
                    depthConfidencePath = null,
                    poseWorldFromCamera = poseWorldFromCamera,
                    intrinsics = CameraIntrinsicsData(
                        fx = 1430.2f,
                        fy = 1431.1f,
                        cx = 960f,
                        cy = 540f,
                        imageWidth = 1920,
                        imageHeight = 1080
                    ),
                    distortion = CameraDistortionData(
                        model = "brown_conrady",
                        coefficients = floatArrayOf(0f, 0f, 0f, 0f, 0f)
                    ),
                    lensFacing = "back",
                    focalLengthMm = 5.43f,
                    exposureTimeNs = 8333333L,
                    iso = 160,
                    focusDistance = 0.72f,
                    whiteBalanceMode = "auto",
                    width = 1920,
                    height = 1080,
                    quality = FrameQuality(
                        blurScore = 182.4f,
                        exposureScore = 0.93f,
                        overlapScore = 0.7f,
                        trackingGood = null,
                        depthCoverage = null,
                        markerVisibility = 0.4f,
                        scaleConfidence = 0.91f,
                        materialRisk = 0.1f,
                        accepted = true
                    ),
                    forcedCapture = false
                )
            )
        )

    private fun tempDir(prefix: String): File =
        Files.createTempDirectory(prefix).toFile()
}

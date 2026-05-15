package com.mobileslicer.scanner

import java.io.File
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class ScannerSyntheticRunConfig(
    val scanId: String = "scan_synthetic_${UUID.randomUUID()}",
    val rejectedWarmupFrames: Int = 6,
    val rejectedDuplicateFramesPerPass: Int = 3,
    val measuredScaleBarMm: Float? = null,
    val includeSyntheticMarkers: Boolean = false
)

internal data class ScannerSyntheticRunResult(
    val scanId: String,
    val sessionDir: File,
    val workspaceDir: File,
    val capturedFrameCount: Int,
    val retainedFrameCount: Int,
    val acceptedFrameCount: Int,
    val retainedRejectedFrameCount: Int,
    val export: ScannerPackageExportResult,
    val pipeline: ScannerReconstructionPipelineResult
)

internal fun runScannerSyntheticCaptureRegression(
    rootDir: File,
    config: ScannerSyntheticRunConfig = ScannerSyntheticRunConfig()
): ScannerSyntheticRunResult {
    val sessionDir = rootDir.resolve("scanner-sessions/${config.scanId}").also {
        if (it.exists()) it.deleteRecursively()
        it.mkdirs()
    }
    val workspaceDir = rootDir.resolve("scanner-workspaces/${config.scanId}").also {
        if (it.exists()) it.deleteRecursively()
    }
    val retainedFrames = mutableListOf<ScanFrame>()
    val capturedFrames = buildSyntheticCaptureFrames(
        sessionDir = sessionDir,
        config = config
    ).map { candidate ->
        val admitted = applyScannerFrameAdmissionQuality(candidate, retainedFrames)
        if (shouldRetainScannerDiagnosticFrame(admitted, retainedFrames)) {
            retainedFrames += admitted
        } else {
            deleteSyntheticFrameAssets(sessionDir, admitted)
        }
        admitted
    }
    val export = exportCapturePackage(
        sessionDir = sessionDir,
        scanId = config.scanId,
        frames = retainedFrames,
        measuredScaleBarMm = config.measuredScaleBarMm,
        context = null,
        markerDetector = if (config.includeSyntheticMarkers) {
            SyntheticMarkerDetector
        } else {
            SyntheticNoMarkerDetector
        },
        softMaskGenerator = SyntheticSoftMaskGenerator
    )
    val pipeline = runScannerLocalReconstructionPipeline(
        packageDir = export.packageDir,
        workspaceDir = workspaceDir
    )
    return ScannerSyntheticRunResult(
        scanId = config.scanId,
        sessionDir = sessionDir,
        workspaceDir = workspaceDir,
        capturedFrameCount = capturedFrames.size,
        retainedFrameCount = retainedFrames.size,
        acceptedFrameCount = retainedFrames.count { it.quality.accepted },
        retainedRejectedFrameCount = retainedFrames.count { !it.quality.accepted },
        export = export,
        pipeline = pipeline
    )
}

internal fun shouldRetainScannerDiagnosticFrame(
    frame: ScanFrame,
    previousFrames: List<ScanFrame>
): Boolean =
    frame.quality.accepted ||
        previousFrames.count { it.capturePass == frame.capturePass && !it.quality.accepted } <
        SCANNER_MAX_RETAINED_REJECTED_FRAMES_PER_PASS

private fun buildSyntheticCaptureFrames(
    sessionDir: File,
    config: ScannerSyntheticRunConfig
): List<ScanFrame> {
    val frames = mutableListOf<ScanFrame>()
    repeat(config.rejectedWarmupFrames) { index ->
        frames += syntheticFrame(
            sessionDir = sessionDir,
            id = "warmup_${(index + 1).toString().padStart(3, '0')}",
            pass = ScannerCapturePass.MidRing,
            accepted = true,
            timestampNs = index * 900_000_000L,
            poseXMeters = index * 0.03f,
            depthCoverage = 0f
        )
    }
    var sequence = config.rejectedWarmupFrames
    syntheticCaptureTargets().forEach { target ->
        repeat(target.target) { index ->
            sequence += 1
            frames += syntheticFrame(
                sessionDir = sessionDir,
                id = "${target.pass.manifestValue}_${(index + 1).toString().padStart(3, '0')}",
                pass = target.pass,
                accepted = true,
                timestampNs = sequence * 900_000_000L,
                poseXMeters = sequence * 0.025f,
                depthCoverage = 0.62f
            )
            if (index < config.rejectedDuplicateFramesPerPass) {
                sequence += 1
                frames += syntheticFrame(
                    sessionDir = sessionDir,
                    id = "${target.pass.manifestValue}_duplicate_${(index + 1).toString().padStart(3, '0')}",
                    pass = target.pass,
                    accepted = true,
                    timestampNs = sequence * 900_000_000L,
                    poseXMeters = (sequence - 1) * 0.025f + 0.001f,
                    depthCoverage = 0.62f
                )
            }
        }
    }
    return frames
}

private data class SyntheticCaptureTarget(
    val pass: ScannerCapturePass,
    val target: Int
)

private fun syntheticCaptureTargets(): List<SyntheticCaptureTarget> =
    listOf(
        SyntheticCaptureTarget(ScannerCapturePass.MidRing, 12),
        SyntheticCaptureTarget(ScannerCapturePass.HighRing, 12),
        SyntheticCaptureTarget(ScannerCapturePass.LowRing, 12),
        SyntheticCaptureTarget(ScannerCapturePass.TopDetail, 8)
    )

private fun syntheticFrame(
    sessionDir: File,
    id: String,
    pass: ScannerCapturePass,
    accepted: Boolean,
    timestampNs: Long,
    poseXMeters: Float,
    depthCoverage: Float?
): ScanFrame {
    val imagePath = "frames/$id.jpg"
    sessionDir.resolve(imagePath).also {
        requireNotNull(it.parentFile).mkdirs()
        it.writeText(syntheticPlainGrayMap(syntheticCheckerLumaImage(72, 72, poseXMeters)))
    }
    return ScanFrame(
        id = id,
        timestampNs = timestampNs,
        rgbPath = imagePath,
        rgbSha256 = sha256Hex(sessionDir.resolve(imagePath)),
        maskPath = null,
        maskSha256 = null,
        depth16Path = null,
        depthSha256 = null,
        depthConfidencePath = null,
        poseWorldFromCamera = syntheticPose(poseXMeters),
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
            blurScore = 260f,
            exposureScore = 0.9f,
            overlapScore = 0.7f,
            trackingGood = true,
            depthCoverage = depthCoverage,
            markerVisibility = 0f,
            scaleConfidence = 0f,
            materialRisk = 0.1f,
            accepted = accepted,
            rejectionReasons = if (accepted) emptyList() else listOf("synthetic_rejected")
        ),
        forcedCapture = false
    )
}

private fun syntheticPose(xMeters: Float): FloatArray =
    floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        xMeters, 0f, 0f, 1f
    )

private fun syntheticCheckerLumaImage(width: Int, height: Int, offset: Float): ScannerLumaImage =
    ScannerLumaImage(
        width = width,
        height = height,
        luma = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val shift = (offset * 100f).toInt()
            if ((((x + shift) / 8) + (y / 8)) % 2 == 0) 35 else 220
        }
    )

private fun syntheticPlainGrayMap(image: ScannerLumaImage): String =
    buildString {
        appendLine("P2")
        appendLine("${image.width} ${image.height}")
        appendLine("255")
        image.luma.forEachIndexed { index, value ->
            append(value)
            append(if ((index + 1) % image.width == 0) '\n' else ' ')
        }
    }

private fun deleteSyntheticFrameAssets(sessionDir: File, frame: ScanFrame) {
    listOfNotNull(
        frame.rgbPath,
        frame.maskPath,
        frame.depth16Path,
        frame.depthConfidencePath
    ).forEach { runCatching { sessionDir.resolve(it).delete() } }
}

private object SyntheticNoMarkerDetector : ScannerMarkerDetector {
    override val detectorName: String = "synthetic_no_marker_detector"
    override val detectorStatus: String = "ready"
    override fun detectMarkers(frame: ScanFrame, absoluteFramePath: String): List<MarkerObservation> = emptyList()
}

private object SyntheticMarkerDetector : ScannerMarkerDetector {
    override val detectorName: String = "synthetic_apriltag_detector"
    override val detectorStatus: String = "ready"

    override fun detectMarkers(frame: ScanFrame, absoluteFramePath: String): List<MarkerObservation> {
        val markerId = frame.id.filter(Char::isDigit).takeLast(2).ifBlank { "0" }
        return listOf(
            MarkerObservation(
                markerSystem = ScannerMarkerSystem.AprilTag,
                markerId = markerId,
                frameId = frame.id,
                framePath = frame.rgbPath,
                markerSizeMm = 40f,
                cornersPx = listOf(
                    20f to 20f,
                    52f to 20f,
                    52f to 52f,
                    20f to 52f
                ),
                hamming = 0,
                decisionMargin = 80f,
                reprojectionErrorPx = 1.0f
            )
        )
    }
}

private object SyntheticSoftMaskGenerator : ScannerSoftMaskGenerator {
    override val generatorName: String = "synthetic_local_ai_soft_mask_v1"
    override val generatorStatus: String = "ready_synthetic_contract"
    override fun generateMask(
        sessionDir: File,
        frame: ScanFrame,
        absoluteFramePath: String,
        objectRoi: ScannerObjectRoi
    ): ScannerSoftMaskResult {
        val width = frame.width.coerceAtLeast(1)
        val height = frame.height.coerceAtLeast(1)
        val centerX = (width - 1) * 0.5f
        val centerY = (height - 1) * 0.5f
        val radiusX = width * 0.34f
        val radiusY = height * 0.34f
        val alpha = ByteArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val normalizedDistance = ((x - centerX) * (x - centerX)) / (radiusX * radiusX) +
                ((y - centerY) * (y - centerY)) / (radiusY * radiusY)
            when {
                normalizedDistance <= 0.72f -> 255.toByte()
                normalizedDistance <= 1.0f -> 160.toByte()
                else -> 0.toByte()
            }
        }
        val analysis = scannerAnalyzeSoftMaskAlpha(width, height, alpha)
        val relativePath = "frames/${frame.id}_mask.png"
        val output = sessionDir.resolve(relativePath)
        output.parentFile?.mkdirs()
        output.writeText(
            syntheticPlainGrayMap(
                ScannerLumaImage(
                    width = width,
                    height = height,
                    luma = IntArray(alpha.size) { index -> alpha[index].toInt() and 0xFF }
                )
            )
        )
        return ScannerSoftMaskResult(
            maskPath = relativePath,
            maskSha256 = sha256Hex(output),
            generatorName = generatorName,
            generatorStatus = generatorStatus,
            objectRoi = objectRoi.clamped(),
            coverageRatio = analysis.coverageRatio,
            edgeUncertainty = analysis.edgeUncertainty,
            centerSupport = analysis.centerSupport,
            warnings = (
                listOf(
                    "synthetic_contract_mask",
                    "mask_is_soft_evidence_only"
                ) + scannerObjectRoiWarnings(objectRoi) + analysis.warnings
                ).distinct()
        )
    }
}

internal fun scannerSyntheticRunSummaryJson(result: ScannerSyntheticRunResult): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_PACKAGE_SCHEMA_VERSION)
        .put("scan_id", result.scanId)
        .put("created_at", Instant.now().toString())
        .put("session_dir", result.sessionDir.absolutePath)
        .put("package_dir", result.export.packageDir.absolutePath)
        .put("zip", result.export.zipFile.absolutePath)
        .put("workspace_dir", result.workspaceDir.absolutePath)
        .put("captured_frame_count", result.capturedFrameCount)
        .put("retained_frame_count", result.retainedFrameCount)
        .put("accepted_frame_count", result.acceptedFrameCount)
        .put("retained_rejected_frame_count", result.retainedRejectedFrameCount)
        .put("package_valid", result.export.validation.valid)
        .put("package_errors", JSONArray(result.export.validation.errors))
        .put("pipeline_completed", result.pipeline.completed)
        .put("dense_ready", result.pipeline.denseReconstructionReady)
        .put("blocking_errors", JSONArray(result.pipeline.blockingErrors))
        .put(
            "artifact_paths",
            JSONObject()
                .put("summary", result.workspaceDir.resolve(result.pipeline.summaryPath).absolutePath)
                .put("audit", result.workspaceDir.resolve(SCANNER_RECONSTRUCTION_AUDIT_PATH).absolutePath)
        )

internal fun writeScannerSyntheticRunSummary(result: ScannerSyntheticRunResult, outputFile: File) {
    outputFile.parentFile?.mkdirs()
    outputFile.writeText(scannerSyntheticRunSummaryJson(result).toString(2))
}

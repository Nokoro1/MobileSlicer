package com.mobileslicer.scanner

import android.content.Context
import android.graphics.BitmapFactory
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal const val SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_MODEL =
    "scanner/mediapipe/magic_touch.tflite"
internal const val SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_SOURCE_URL =
    "https://storage.googleapis.com/mediapipe-models/interactive_segmenter/magic_touch/float32/1/magic_touch.tflite"
internal const val SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_EXPECTED_SHA256 =
    "e24338a717c1b7ad8d159666677ef400babb7f33b8ad60c4d96db4ecf694cd25"
internal const val SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_EXPECTED_BYTES = 6_227_884L

internal data class ScannerModelAssetInspection(
    val exists: Boolean,
    val byteSize: Long? = null,
    val sha256: String? = null,
    val error: String? = null
)

internal data class ScannerMediaPipeSoftMaskStatus(
    val available: Boolean,
    val status: String,
    val detail: String,
    val modelAssetPath: String = SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_MODEL,
    val sourceUrl: String = SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_SOURCE_URL,
    val expectedSha256: String = SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_EXPECTED_SHA256,
    val actualSha256: String? = null,
    val expectedBytes: Long = SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_EXPECTED_BYTES,
    val actualBytes: Long? = null,
    val productionReady: Boolean = false
)

internal fun scannerMediaPipeInteractiveSegmenterStatus(context: Context): ScannerMediaPipeSoftMaskStatus {
    val inspection = runCatching {
        context.assets.open(SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_MODEL).use { input ->
            inspectScannerModelAsset(input)
        }
    }.getOrElse {
        ScannerModelAssetInspection(
            exists = false,
            error = it.message
        )
    }
    return scannerMediaPipeInteractiveSegmenterStatus(inspection)
}

internal fun scannerMediaPipeInteractiveSegmenterStatus(
    inspection: ScannerModelAssetInspection
): ScannerMediaPipeSoftMaskStatus =
    when {
        !inspection.exists -> ScannerMediaPipeSoftMaskStatus(
            available = false,
            status = "model_asset_missing",
            detail = "Expected model asset $SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_MODEL is not bundled.",
            actualSha256 = inspection.sha256,
            actualBytes = inspection.byteSize,
            productionReady = false
        )
        inspection.byteSize != SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_EXPECTED_BYTES -> ScannerMediaPipeSoftMaskStatus(
            available = false,
            status = "model_asset_size_mismatch",
            detail = "MediaPipe Interactive Segmenter model asset size does not match the approved MagicTouch asset.",
            actualSha256 = inspection.sha256,
            actualBytes = inspection.byteSize,
            productionReady = false
        )
        inspection.sha256 != SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_EXPECTED_SHA256 -> ScannerMediaPipeSoftMaskStatus(
            available = false,
            status = "model_asset_hash_mismatch",
            detail = "MediaPipe Interactive Segmenter model asset hash does not match the approved MagicTouch asset.",
            actualSha256 = inspection.sha256,
            actualBytes = inspection.byteSize,
            productionReady = false
        )
        else -> ScannerMediaPipeSoftMaskStatus(
            available = true,
            status = "model_asset_verified",
            detail = "Verified MediaPipe MagicTouch Interactive Segmenter model asset is bundled.",
            actualSha256 = inspection.sha256,
            actualBytes = inspection.byteSize,
            productionReady = true
        )
    }

internal fun inspectScannerModelAsset(input: InputStream): ScannerModelAssetInspection {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var byteSize = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read > 0) {
            digest.update(buffer, 0, read)
            byteSize += read.toLong()
        }
    }
    return ScannerModelAssetInspection(
        exists = true,
        byteSize = byteSize,
        sha256 = digest.digest().joinToString("") { "%02x".format(it) }
    )
}

internal class ScannerCompositeSoftMaskGenerator(
    private val context: Context,
    private val mediaPipeGenerator: ScannerSoftMaskGenerator = MediaPipeInteractiveScannerSoftMaskGenerator(context),
    private val fallbackGenerator: ScannerSoftMaskGenerator = HeuristicScannerSoftMaskGenerator
) : ScannerSoftMaskGenerator, AutoCloseable {
    override val generatorName: String = "mediapipe_interactive_segmenter_then_heuristic_v1"
    override val generatorStatus: String
        get() = scannerMediaPipeInteractiveSegmenterStatus(context).status

    override fun generateMask(
        sessionDir: File,
        frame: ScanFrame,
        absoluteFramePath: String,
        objectRoi: ScannerObjectRoi
    ): ScannerSoftMaskResult? {
        val status = scannerMediaPipeInteractiveSegmenterStatus(context)
        if (status.productionReady) {
            val mediaPipeResult = synchronized(SCANNER_MEDIAPIPE_SEGMENTER_LOCK) {
                mediaPipeGenerator.generateMask(sessionDir, frame, absoluteFramePath, objectRoi)
            }
            if (mediaPipeResult != null) return mediaPipeResult
        }
        val fallbackResult = fallbackGenerator.generateMask(sessionDir, frame, absoluteFramePath, objectRoi) ?: return null
        return fallbackResult.copy(
            generatorName = generatorName,
            generatorStatus = status.status,
            warnings = listOf(
                "mediapipe_interactive_segmenter_unavailable",
                status.status,
                "heuristic_fallback_used"
            ) + fallbackResult.warnings
        )
    }

    override fun close() {
        (mediaPipeGenerator as? AutoCloseable)?.close()
        (fallbackGenerator as? AutoCloseable)?.close()
    }
}

internal class MediaPipeInteractiveScannerSoftMaskGenerator(
    private val context: Context,
    private val modelAssetPath: String = SCANNER_MEDIAPIPE_INTERACTIVE_SEGMENTER_MODEL
) : ScannerSoftMaskGenerator, AutoCloseable {
    private var segmenter: InteractiveSegmenter? = null

    override val generatorName: String = "mediapipe_interactive_segmenter_v1"
    override val generatorStatus: String
        get() = scannerMediaPipeInteractiveSegmenterStatus(context).status

    override fun generateMask(
        sessionDir: File,
        frame: ScanFrame,
        absoluteFramePath: String,
        objectRoi: ScannerObjectRoi
    ): ScannerSoftMaskResult? {
        val status = scannerMediaPipeInteractiveSegmenterStatus(context)
        if (!status.productionReady) return null
        val bitmap = BitmapFactory.decodeFile(absoluteFramePath) ?: return null
        return try {
            val activeSegmenter = activeSegmenter() ?: return null
            val image = BitmapImageBuilder(bitmap).build()
            val clampedRoi = objectRoi.clamped()
            val roi = InteractiveSegmenter.RegionOfInterest.create(
                NormalizedKeypoint.create(
                    clampedRoi.xNormalized,
                    clampedRoi.yNormalized
                )
            )
            val result = activeSegmenter.segment(image, roi)
            val categoryMask = result.categoryMask().orElse(null) ?: return null
            val maskBuffer = ByteBufferExtractor.extract(categoryMask)
            val alpha = ByteArray(maskBuffer.remaining())
            maskBuffer.get(alpha)
            val maskWidth = categoryMask.width
            val maskHeight = categoryMask.height
            if (maskWidth <= 0 || maskHeight <= 0 || alpha.size != maskWidth * maskHeight) return null
            val analysis = scannerAnalyzeSoftMaskAlpha(maskWidth, maskHeight, alpha)
            writeScannerSoftMaskPng(
                sessionDir = sessionDir,
                frame = frame,
                width = maskWidth,
                height = maskHeight,
                alpha = alpha,
                result = analysis,
                generatorName = generatorName,
                generatorStatus = "ready",
                objectRoi = clampedRoi,
                warnings = listOf("mediapipe_interactive_segmenter", "mask_is_soft_evidence_only") +
                    scannerObjectRoiWarnings(clampedRoi) +
                    analysis.warnings
            )
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() {
        segmenter?.close()
        segmenter = null
    }

    private fun activeSegmenter(): InteractiveSegmenter? {
        segmenter?.let { return it }
        return createSegmenter()?.also { segmenter = it }
    }

    private fun createSegmenter(): InteractiveSegmenter? =
        runCatching {
            val options = InteractiveSegmenter.InteractiveSegmenterOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(modelAssetPath)
                        .build()
                )
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(false)
                .build()
            InteractiveSegmenter.createFromOptions(context, options)
        }.getOrNull()
}

private val SCANNER_MEDIAPIPE_SEGMENTER_LOCK = Any()

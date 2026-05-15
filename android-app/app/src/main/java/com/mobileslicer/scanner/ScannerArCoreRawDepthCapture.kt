package com.mobileslicer.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraIntrinsics
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class ScannerArCoreAvailability(
    val checked: Boolean,
    val installed: Boolean,
    val supported: Boolean,
    val transient: Boolean,
    val status: String
)

internal data class ScannerArCoreDepthSessionConfig(
    val configured: Boolean,
    val depthMode: String?,
    val rawDepthSupported: Boolean,
    val automaticDepthSupported: Boolean,
    val reason: String
)

internal data class ScannerArCoreRawDepthCaptureResult(
    val frame: ScanFrame?,
    val status: String,
    val errors: List<String>,
    val warnings: List<String>
)

internal fun scannerArCoreAvailability(context: Context): ScannerArCoreAvailability =
    runCatching {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        ScannerArCoreAvailability(
            checked = true,
            installed = availability == ArCoreApk.Availability.SUPPORTED_INSTALLED,
            supported = availability.isSupported,
            transient = availability.isTransient,
            status = availability.name
        )
    }.getOrElse {
        ScannerArCoreAvailability(
            checked = false,
            installed = false,
            supported = false,
            transient = false,
            status = "arcore_availability_failed:${it.javaClass.simpleName}"
        )
    }

internal fun configureScannerArCoreRawDepthSession(session: Session): ScannerArCoreDepthSessionConfig =
    runCatching {
        val rawDepthSupported = session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)
        val automaticDepthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        val depthMode = when {
            rawDepthSupported -> Config.DepthMode.RAW_DEPTH_ONLY
            automaticDepthSupported -> Config.DepthMode.AUTOMATIC
            else -> null
        }
        if (depthMode == null) {
            ScannerArCoreDepthSessionConfig(
                configured = false,
                depthMode = null,
                rawDepthSupported = false,
                automaticDepthSupported = false,
                reason = "arcore_depth_mode_not_supported"
            )
        } else {
            val config = Config(session)
            config.setDepthMode(depthMode)
            config.setFocusMode(Config.FocusMode.AUTO)
            session.configure(config)
            ScannerArCoreDepthSessionConfig(
                configured = true,
                depthMode = depthMode.name,
                rawDepthSupported = rawDepthSupported,
                automaticDepthSupported = automaticDepthSupported,
                reason = "arcore_depth_configured"
            )
        }
    }.getOrElse {
        ScannerArCoreDepthSessionConfig(
            configured = false,
            depthMode = null,
            rawDepthSupported = false,
            automaticDepthSupported = false,
            reason = "arcore_depth_config_failed:${it.javaClass.simpleName}"
        )
    }

internal class ScannerArCoreRawDepthCaptureManager(
    private val session: Session,
    private val sessionDir: File
) {
    fun captureKeyframe(
        index: Int,
        capturePass: ScannerCapturePass
    ): ScannerArCoreRawDepthCaptureResult =
        runCatching {
            val frame = session.update()
            captureFrame(index = index, capturePass = capturePass, frame = frame)
        }.getOrElse {
            ScannerArCoreRawDepthCaptureResult(
                frame = null,
                status = "arcore_frame_unavailable",
                errors = listOf("arcore_update_failed:${it.javaClass.simpleName}"),
                warnings = emptyList()
            )
        }

    fun captureFrame(
        index: Int,
        capturePass: ScannerCapturePass,
        frame: Frame
    ): ScannerArCoreRawDepthCaptureResult {
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) {
            return ScannerArCoreRawDepthCaptureResult(
                frame = null,
                status = "arcore_tracking_not_ready",
                errors = listOf("arcore_tracking_not_ready"),
                warnings = emptyList()
            )
        }
        val frameId = index.toString().padStart(6, '0')
        val rgbPath = "frames/$frameId.jpg"
        val depthPath = "frames/${frameId}_depth_rgba.png"
        val confidencePath = "frames/${frameId}_confidence.png"
        val rgbFile = sessionDir.resolve(rgbPath)
        val depthFile = sessionDir.resolve(depthPath)
        val confidenceFile = sessionDir.resolve(confidencePath)

        val cameraImage = try {
            frame.acquireCameraImage()
        } catch (error: NotYetAvailableException) {
            return ScannerArCoreRawDepthCaptureResult(
                frame = null,
                status = "arcore_camera_image_not_ready",
                errors = listOf("arcore_camera_image_not_ready"),
                warnings = emptyList()
            )
        }

        val rawDepthImage = try {
            frame.acquireRawDepthImage16Bits()
        } catch (error: NotYetAvailableException) {
            null
        }

        val confidenceImage = if (rawDepthImage != null) {
            try {
                frame.acquireRawDepthConfidenceImage()
            } catch (error: NotYetAvailableException) {
                null
            }
        } else {
            null
        }

        cameraImage.use { image ->
            writeYuv420ImageAsJpeg(image, rgbFile)
        }
        rawDepthImage?.use { image ->
            if (confidenceImage != null) {
                writeDepth16ImageAsLosslessRgbaPng(image, depthFile)
            }
        }
        confidenceImage?.use { image ->
            writeConfidenceImageAsPng(image, confidenceFile)
        }

        val imageQuality = analyzeScannerJpegQuality(rgbFile)
        val depthCoverage = confidenceFile
            .takeIf { it.isFile }
            ?.let(::readConfidenceCoverage)
        val quality = evaluateScannerFrameQuality(
            ScannerFrameQualityInput(
                blurScore = imageQuality?.blurScore ?: 0f,
                exposureScore = imageQuality?.exposureScore ?: 0f,
                overlapScore = 0.5f,
                coverageGain = 0.1f,
                objectMaskArea = 0.1f,
                clippedHighlightRatio = imageQuality?.clippedHighlightRatio ?: 1f,
                trackingGood = true,
                depthCoverage = depthCoverage,
                markerVisibility = 0f,
                scaleConfidence = 0f,
                materialRisk = ((imageQuality?.clippedHighlightRatio ?: 0f) * 2f).coerceIn(0f, 1f),
                depthRequired = false,
                markersRequired = false,
                scaleRequired = false,
                lateCapture = false
            )
        )
        val scanFrame = ScanFrame(
            id = frameId,
            timestampNs = frame.timestamp,
            rgbPath = rgbPath,
            rgbSha256 = sha256Hex(rgbFile),
            maskPath = null,
            maskSha256 = null,
            depth16Path = depthPath.takeIf { depthFile.isFile },
            depthSha256 = depthFile.takeIf { it.isFile }?.let(::sha256Hex),
            depthConfidencePath = confidencePath.takeIf { confidenceFile.isFile },
            poseWorldFromCamera = camera.pose.toScannerMatrixArray(),
            intrinsics = camera.imageIntrinsics.toScannerCameraIntrinsicsData(
                imageWidth = imageQuality?.width ?: 0,
                imageHeight = imageQuality?.height ?: 0
            ),
            distortion = null,
            lensFacing = "back",
            focalLengthMm = null,
            exposureTimeNs = null,
            iso = null,
            focusDistance = null,
            whiteBalanceMode = null,
            width = imageQuality?.width ?: 0,
            height = imageQuality?.height ?: 0,
            capturePass = capturePass,
            quality = quality,
            forcedCapture = false
        )
        return ScannerArCoreRawDepthCaptureResult(
            frame = scanFrame,
            status = when {
                quality.accepted && depthFile.isFile && confidenceFile.isFile -> "arcore_rgb_pose_depth_frame_accepted"
                quality.accepted -> "arcore_rgb_pose_frame_accepted_depth_unavailable"
                else -> "arcore_rgb_pose_frame_rejected"
            },
            errors = if (quality.accepted) emptyList() else quality.rejectionReasons,
            warnings = buildList {
                add("arcore_depth_is_measured_assist_not_standalone_geometry")
                if (!depthFile.isFile || !confidenceFile.isFile) {
                    add("raw_depth_unavailable_for_frame_rgb_pose_kept")
                }
            }
        )
    }
}

private fun writeYuv420ImageAsJpeg(image: Image, target: File) {
    require(image.format == ImageFormat.YUV_420_888) {
        "Expected YUV_420_888 camera image, got ${image.format}"
    }
    val nv21 = yuv420888ToNv21(image)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    target.parentFile?.mkdirs()
    target.outputStream().use { output ->
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 95, output)
    }
}

private fun yuv420888ToNv21(image: Image): ByteArray {
    val width = image.width
    val height = image.height
    val ySize = width * height
    val uvSize = width * height / 2
    val output = ByteArray(ySize + uvSize)
    copyPlaneToBuffer(image.planes[0].buffer, image.planes[0].rowStride, image.planes[0].pixelStride, width, height, output, 0)
    val chromaHeight = height / 2
    val chromaWidth = width / 2
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]
    var outputOffset = ySize
    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            output[outputOffset++] = vPlane.buffer.get(row * vPlane.rowStride + col * vPlane.pixelStride)
            output[outputOffset++] = uPlane.buffer.get(row * uPlane.rowStride + col * uPlane.pixelStride)
        }
    }
    return output
}

private fun copyPlaneToBuffer(
    source: ByteBuffer,
    rowStride: Int,
    pixelStride: Int,
    width: Int,
    height: Int,
    output: ByteArray,
    outputOffset: Int
) {
    var offset = outputOffset
    for (row in 0 until height) {
        for (col in 0 until width) {
            output[offset++] = source.get(row * rowStride + col * pixelStride)
        }
    }
}

private fun writeDepth16ImageAsLosslessRgbaPng(image: Image, target: File) {
    val plane = image.planes[0]
    val buffer = plane.buffer.order(ByteOrder.LITTLE_ENDIAN)
    val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            val byteIndex = y * plane.rowStride + x * plane.pixelStride
            val depth = buffer.getShort(byteIndex).toInt() and 0xFFFF
            val low = depth and 0xFF
            val high = (depth ushr 8) and 0xFF
            bitmap.setPixel(x, y, Color.argb(255, low, high, 0))
        }
    }
    target.parentFile?.mkdirs()
    target.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
}

private fun writeConfidenceImageAsPng(image: Image, target: File) {
    val plane = image.planes[0]
    val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            val confidence = plane.buffer.get(y * plane.rowStride + x * plane.pixelStride).toInt() and 0xFF
            bitmap.setPixel(x, y, Color.argb(255, confidence, confidence, confidence))
        }
    }
    target.parentFile?.mkdirs()
    target.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
}

private fun readConfidenceCoverage(confidencePng: File): Float {
    val bitmap = android.graphics.BitmapFactory.decodeFile(confidencePng.absolutePath) ?: return 0f
    val total = (bitmap.width * bitmap.height).coerceAtLeast(1)
    var valid = 0
    for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
            if (Color.red(bitmap.getPixel(x, y)) > 0) valid += 1
        }
    }
    return (valid.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

private fun com.google.ar.core.Pose.toScannerMatrixArray(): FloatArray =
    FloatArray(16).also { toMatrix(it, 0) }

private fun CameraIntrinsics.toScannerCameraIntrinsicsData(
    imageWidth: Int,
    imageHeight: Int
): CameraIntrinsicsData {
    val focalLength = focalLength
    val principalPoint = principalPoint
    val dimensions = imageDimensions
    return CameraIntrinsicsData(
        fx = focalLength.getOrNull(0) ?: 0f,
        fy = focalLength.getOrNull(1) ?: 0f,
        cx = principalPoint.getOrNull(0) ?: 0f,
        cy = principalPoint.getOrNull(1) ?: 0f,
        imageWidth = dimensions.getOrNull(0)?.takeIf { it > 0 } ?: imageWidth,
        imageHeight = dimensions.getOrNull(1)?.takeIf { it > 0 } ?: imageHeight
    )
}

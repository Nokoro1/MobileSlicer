package com.mobileslicer.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject

internal object ScannerAprilTagNativeBridge {
    const val DetectorName = "apriltag_36h11_jni"

    fun status(): ScannerAprilTagNativeStatus =
        runCatching {
            parseStatus(nativeStatusJson())
        }.getOrElse {
            ScannerAprilTagNativeStatus(
                available = false,
                status = "native_bridge_unavailable",
                detail = it.message ?: "Native scanner_apriltag bridge is unavailable"
            )
        }

    fun detect(frame: ScanFrame, absoluteFramePath: String): List<MarkerObservation> =
        runCatching {
            val gray = decodeGrayscale(absoluteFramePath) ?: return@runCatching emptyList()
            parseDetections(
                frame,
                nativeDetectTag36h11Json(
                    gray.pixels,
                    gray.width,
                    gray.height,
                    frame.id,
                    frame.rgbPath
                )
            )
        }.getOrElse { emptyList() }

    private external fun nativeStatusJson(): String
    private external fun nativeDetectTag36h11Json(
        grayscale: ByteArray,
        width: Int,
        height: Int,
        frameId: String,
        framePath: String
    ): String

    init {
        runCatching { System.loadLibrary("scanner_apriltag") }
    }
}

private data class GrayscaleImage(
    val pixels: ByteArray,
    val width: Int,
    val height: Int
)

internal data class ScannerAprilTagNativeStatus(
    val available: Boolean,
    val status: String,
    val detail: String
)

internal class ScannerAprilTagNativeDetector : ScannerMarkerDetector {
    private val status = ScannerAprilTagNativeBridge.status()

    override val detectorName: String = ScannerAprilTagNativeBridge.DetectorName
    override val detectorStatus: String = status.status

    override fun detectMarkers(frame: ScanFrame, absoluteFramePath: String): List<MarkerObservation> =
        if (status.available && status.status == "ready") {
            ScannerAprilTagNativeBridge.detect(frame, absoluteFramePath)
        } else {
            emptyList()
        }
}

private fun parseStatus(jsonText: String): ScannerAprilTagNativeStatus {
    val json = JSONObject(jsonText)
    return ScannerAprilTagNativeStatus(
        available = json.optBoolean("available", false),
        status = json.optString("status", "native_bridge_unavailable"),
        detail = json.optString("detail", "")
    )
}

private fun decodeGrayscale(path: String): GrayscaleImage? {
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val row = IntArray(width)
        val gray = ByteArray(width * height)
        for (y in 0 until height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val pixel = row[x]
                val red = pixel shr 16 and 0xFF
                val green = pixel shr 8 and 0xFF
                val blue = pixel and 0xFF
                gray[y * width + x] = ((red * 299 + green * 587 + blue * 114) / 1000).toByte()
            }
        }
        GrayscaleImage(gray, width, height)
    } finally {
        bitmap.recycle()
    }
}

private fun parseDetections(frame: ScanFrame, jsonText: String): List<MarkerObservation> {
    val json = JSONObject(jsonText)
    val detections = json.optJSONArray("detections") ?: JSONArray()
    return List(detections.length()) { index ->
        val detection = detections.getJSONObject(index)
        MarkerObservation(
            markerSystem = ScannerMarkerSystem.AprilTag,
            markerId = detection.getString("id"),
            frameId = detection.optString("frame_id", frame.id),
            framePath = detection.optString("frame_path", frame.rgbPath),
            markerSizeMm = detection.optDouble("marker_size_mm", 40.0).toFloat(),
            cornersPx = detection.getJSONArray("corners_px").toCornerList(),
            hamming = detection.nullableInt("hamming"),
            decisionMargin = detection.nullableFloat("decision_margin"),
            reprojectionErrorPx = detection.nullableFloat("reprojection_error_px")
        )
    }
}

private fun JSONArray.toCornerList(): List<Pair<Float, Float>> =
    List(length()) { index ->
        val corner = getJSONArray(index)
        corner.getDouble(0).toFloat() to corner.getDouble(1).toFloat()
    }

private fun JSONObject.nullableInt(name: String): Int? =
    if (!has(name) || isNull(name)) null else getInt(name)

private fun JSONObject.nullableFloat(name: String): Float? =
    if (!has(name) || isNull(name)) null else getDouble(name).toFloat()

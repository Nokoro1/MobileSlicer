package com.mobileslicer.workspace

import kotlin.math.abs
import com.mobileslicer.viewer.ViewerPaintOverlay
import com.mobileslicer.viewer.ViewerPaintOverlayLayer
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.ViewerPaintMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

internal fun parseNativePaintOverlay(
    json: String?,
    slotColors: Map<Int, Int> = emptyMap(),
    baseColorSlotIndex: Int? = null
): ViewerPaintOverlay {
    if (json.isNullOrBlank()) return ViewerPaintOverlay.Empty
    return runCatching {
        val root = JSONObject(json)
        val layers = root.optJSONArray("layers") ?: JSONArray()
        val mode = root.optString("mode").toViewerPaintModeOrNull()
        ViewerPaintOverlay(
            layers = List(layers.length()) { index ->
                layers.getJSONObject(index).toPaintOverlayLayer(mode, slotColors)
            }.filter { layer ->
                layer.vertices.isNotEmpty() &&
                    layer.normals.size == layer.vertices.size &&
                    !isHiddenBaseColorLayer(mode, layer.state, baseColorSlotIndex)
            }
        )
    }.getOrDefault(ViewerPaintOverlay.Empty)
}

internal fun parseNativePaintOverlayDeltaInterleaved(
    values: FloatArray?,
    mode: ViewerPaintMode?,
    slotColors: Map<Int, Int>,
    baseColorSlotIndex: Int? = null
): ViewerPaintOverlay {
    if (values == null || values.size <= 1) return ViewerPaintOverlay.Empty
    var cursor = 0
    val rawLayerCount = values[cursor++].toInt()
    val replaceDelta = rawLayerCount < 0
    val layerCount = abs(rawLayerCount)
    val appendGeneration = if (replaceDelta) -1L else nextOverlayDeltaGeneration()
    val layers = ArrayList<ViewerPaintOverlayLayer>(layerCount)
    repeat(layerCount) { layerIndex ->
        val headerFloatCount = if (replaceDelta) 10 else 9
        if (cursor + headerFloatCount > values.size) return@repeat
        val state = values[cursor++].toInt()
        val volumeIndex = values[cursor++].toInt()
        val sourceTriangle = if (replaceDelta) values[cursor++].toInt() else null
        val bounds = MeshBounds(
            minX = values[cursor++],
            minY = values[cursor++],
            minZ = values[cursor++],
            maxX = values[cursor++],
            maxY = values[cursor++],
            maxZ = values[cursor++]
        )
        val vertexCount = values[cursor++].toInt().coerceAtLeast(0)
        val floatCount = vertexCount * 6
        if (vertexCount > 0 && isHiddenBaseColorLayer(mode, state, baseColorSlotIndex)) {
            cursor = (cursor + floatCount).coerceAtMost(values.size)
            return@repeat
        }
        if (vertexCount == 0 || cursor + floatCount > values.size) {
            cursor = (cursor + floatCount).coerceAtMost(values.size)
            if (replaceDelta && sourceTriangle != null && !isHiddenBaseColorLayer(mode, state, baseColorSlotIndex)) {
                layers += ViewerPaintOverlayLayer(
                    id = "delta-v$volumeIndex-s$state-t$sourceTriangle",
                    colorInt = colorForNativeOverlayState(mode, state, slotColors),
                    state = state,
                    vertices = FloatArray(0),
                    normals = FloatArray(0),
                    sourceBounds = bounds,
                    deleteOnly = true
                )
            }
            return@repeat
        }
        val vertices = FloatArray(vertexCount * 3)
        val normals = FloatArray(vertexCount * 3)
        for (vertex in 0 until vertexCount) {
            val target = vertex * 3
            vertices[target] = values[cursor++]
            vertices[target + 1] = values[cursor++]
            vertices[target + 2] = values[cursor++]
            normals[target] = values[cursor++]
            normals[target + 1] = values[cursor++]
            normals[target + 2] = values[cursor++]
        }
        layers += ViewerPaintOverlayLayer(
            id = if (replaceDelta && sourceTriangle != null) {
                "delta-v$volumeIndex-s$state-t$sourceTriangle"
            } else {
                "delta-v$volumeIndex-s$state-g$appendGeneration-$layerIndex"
            },
            colorInt = colorForNativeOverlayState(mode, state, slotColors),
            state = state,
            vertices = vertices,
            normals = normals,
            sourceBounds = bounds
        )
    }
    return ViewerPaintOverlay(layers)
}

internal fun parseNativePaintOverlayDeltaInterleaved(
    values: ByteBuffer?,
    mode: ViewerPaintMode?,
    slotColors: Map<Int, Int>,
    baseColorSlotIndex: Int? = null
): ViewerPaintOverlay {
    if (values == null || values.capacity() <= Float.SIZE_BYTES) return ViewerPaintOverlay.Empty
    val buffer = values.duplicate().order(ByteOrder.nativeOrder())
    buffer.position(0)
    val floatCount = buffer.capacity() / Float.SIZE_BYTES
    if (floatCount <= 1) return ViewerPaintOverlay.Empty

    var cursor = 0
    fun readFloat(): Float {
        val value = buffer.getFloat(cursor * Float.SIZE_BYTES)
        cursor++
        return value
    }
    fun skipFloats(count: Int) {
        cursor = (cursor + count).coerceAtMost(floatCount)
    }

    val rawLayerCount = readFloat().toInt()
    val replaceDelta = rawLayerCount < 0
    val layerCount = abs(rawLayerCount)
    val appendGeneration = if (replaceDelta) -1L else nextOverlayDeltaGeneration()
    val layers = ArrayList<ViewerPaintOverlayLayer>(layerCount)
    repeat(layerCount) { layerIndex ->
        val headerFloatCount = if (replaceDelta) 10 else 9
        if (cursor + headerFloatCount > floatCount) return@repeat
        val state = readFloat().toInt()
        val volumeIndex = readFloat().toInt()
        val sourceTriangle = if (replaceDelta) readFloat().toInt() else null
        val bounds = MeshBounds(
            minX = readFloat(),
            minY = readFloat(),
            minZ = readFloat(),
            maxX = readFloat(),
            maxY = readFloat(),
            maxZ = readFloat()
        )
        val vertexCount = readFloat().toInt().coerceAtLeast(0)
        val payloadFloatCount = vertexCount * 6
        if (vertexCount > 0 && isHiddenBaseColorLayer(mode, state, baseColorSlotIndex)) {
            skipFloats(payloadFloatCount)
            return@repeat
        }
        if (vertexCount == 0 || cursor + payloadFloatCount > floatCount) {
            skipFloats(payloadFloatCount)
            if (replaceDelta && sourceTriangle != null && !isHiddenBaseColorLayer(mode, state, baseColorSlotIndex)) {
                layers += ViewerPaintOverlayLayer(
                    id = "delta-v$volumeIndex-s$state-t$sourceTriangle",
                    colorInt = colorForNativeOverlayState(mode, state, slotColors),
                    state = state,
                    vertices = FloatArray(0),
                    normals = FloatArray(0),
                    sourceBounds = bounds,
                    deleteOnly = true
                )
            }
            return@repeat
        }
        val vertices = FloatArray(vertexCount * 3)
        val normals = FloatArray(vertexCount * 3)
        for (vertex in 0 until vertexCount) {
            val target = vertex * 3
            vertices[target] = readFloat()
            vertices[target + 1] = readFloat()
            vertices[target + 2] = readFloat()
            normals[target] = readFloat()
            normals[target + 1] = readFloat()
            normals[target + 2] = readFloat()
        }
        layers += ViewerPaintOverlayLayer(
            id = if (replaceDelta && sourceTriangle != null) {
                "delta-v$volumeIndex-s$state-t$sourceTriangle"
            } else {
                "delta-v$volumeIndex-s$state-g$appendGeneration-$layerIndex"
            },
            colorInt = colorForNativeOverlayState(mode, state, slotColors),
            state = state,
            vertices = vertices,
            normals = normals,
            sourceBounds = bounds
        )
    }
    return ViewerPaintOverlay(layers)
}

private fun JSONObject.toPaintOverlayLayer(
    mode: ViewerPaintMode?,
    slotColors: Map<Int, Int>
): ViewerPaintOverlayLayer {
    val rawVertices = optJSONArray("vertices").toFloatArray()
    val rawNormals = optJSONArray("normals").toFloatArray()
    val split = if (rawNormals.isEmpty() && rawVertices.size % 6 == 0) {
        splitInterleavedPositionNormals(rawVertices)
    } else {
        rawVertices to rawNormals
    }
    return ViewerPaintOverlayLayer(
        id = optString("id", "native-overlay"),
        colorInt = if (has("colorInt") || has("color")) {
            optInt("colorInt", optInt("color"))
        } else {
            colorForNativeOverlayState(mode, optInt("state", 2), slotColors)
        },
        state = optInt("state", 0),
        vertices = split.first,
        normals = split.second,
        sourceBounds = optJSONObject("sourceBounds")?.toMeshBounds(),
        modelMatrix = optJSONArray("modelMatrix")
            ?.takeIf { it.length() == 16 }
            ?.toFloatArray()
    )
}

private fun JSONObject.toMeshBounds(): MeshBounds =
    MeshBounds(
        minX = optDouble("minX", 0.0).toFloat(),
        minY = optDouble("minY", 0.0).toFloat(),
        minZ = optDouble("minZ", 0.0).toFloat(),
        maxX = optDouble("maxX", 0.0).toFloat(),
        maxY = optDouble("maxY", 0.0).toFloat(),
        maxZ = optDouble("maxZ", 0.0).toFloat()
    )

private fun JSONArray?.toFloatArray(): FloatArray {
    if (this == null || length() == 0) return FloatArray(0)
    return FloatArray(length()) { index -> optDouble(index, 0.0).toFloat() }
}

private fun splitInterleavedPositionNormals(values: FloatArray): Pair<FloatArray, FloatArray> {
    val vertexCount = values.size / 6
    val vertices = FloatArray(vertexCount * 3)
    val normals = FloatArray(vertexCount * 3)
    for (index in 0 until vertexCount) {
        val source = index * 6
        val target = index * 3
        vertices[target] = values[source]
        vertices[target + 1] = values[source + 1]
        vertices[target + 2] = values[source + 2]
        normals[target] = values[source + 3]
        normals[target + 1] = values[source + 4]
        normals[target + 2] = values[source + 5]
    }
    return vertices to normals
}

private fun colorForNativeOverlayState(
    mode: ViewerPaintMode?,
    state: Int,
    slotColors: Map<Int, Int>
): Int =
    when (mode) {
        ViewerPaintMode.Color -> slotColors[state] ?: nativeColorOverlayPalette[(state - 1).coerceIn(0, nativeColorOverlayPalette.lastIndex)]
        ViewerPaintMode.Seam -> if (state == NativePaintBlockerState) 0xFFE94B5F.toInt() else 0xFF2F80ED.toInt()
        ViewerPaintMode.Support -> if (state == NativePaintBlockerState) 0xFFE94B5F.toInt() else 0xFF20B455.toInt()
        ViewerPaintMode.FuzzySkin -> 0xFF9C4DCC.toInt()
        null -> nativeColorOverlayPalette[(state - 1).coerceIn(0, nativeColorOverlayPalette.lastIndex)]
    }

private fun isHiddenBaseColorLayer(
    mode: ViewerPaintMode?,
    state: Int,
    baseColorSlotIndex: Int?
): Boolean =
    mode == ViewerPaintMode.Color &&
        baseColorSlotIndex != null &&
        state == baseColorSlotIndex

private fun String.toViewerPaintModeOrNull(): ViewerPaintMode? =
    when (lowercase()) {
        "color" -> ViewerPaintMode.Color
        "seam" -> ViewerPaintMode.Seam
        "support" -> ViewerPaintMode.Support
        "fuzzy", "fuzzyskin", "fuzzy_skin" -> ViewerPaintMode.FuzzySkin
        else -> null
    }

private const val NativePaintBlockerState = 2

private val overlayDeltaGeneration = AtomicLong(0L)

private fun nextOverlayDeltaGeneration(): Long =
    overlayDeltaGeneration.incrementAndGet()

private val nativeColorOverlayPalette = intArrayOf(
    0x6688A2FF, 0x66FF7043, 0x66FFD166, 0x6606D6A0,
    0x66EF476F, 0x66118AB2, 0x667833FF, 0x66F15BB5,
    0x6600BBF9, 0x669B5DE5, 0x66FEE440, 0x6600F5D4,
    0x66FF9F1C, 0x662EC4B6, 0x66E71D36, 0x66A7C957
)

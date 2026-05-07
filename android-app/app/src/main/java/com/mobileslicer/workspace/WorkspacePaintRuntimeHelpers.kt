package com.mobileslicer.workspace

import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.nativebridge.NativePaintCalls
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.ViewerPaintBrushShape
import com.mobileslicer.viewer.ViewerPaintMode
import com.mobileslicer.viewer.ViewerPaintOverlay
import com.mobileslicer.viewer.ViewerPaintOverlayLayer
import com.mobileslicer.viewer.ViewerPaintStrokePoint
import kotlinx.coroutines.Job
import org.json.JSONObject
import kotlin.math.sqrt

internal class PaintRuntimeState {
    var pendingMove: ViewerPaintStrokePoint? = null
    var strokeBeginJob: Job? = null
    var movePump: Job? = null
    var overlayRefreshJob: Job? = null
    var overlayConsolidationJob: Job? = null
    var overlayDeltaDrainJob: Job? = null
    var overlayRefreshPending: Boolean = false
    var lastOverlayRefreshMs: Long = 0L
    var overlayDeltaLayersSinceSnapshot: Int = 0
    var overlayDeltaVerticesSinceSnapshot: Int = 0
    var strokeDeltaLayers: Int = 0
    var strokeDeltaVertices: Int = 0
    var strokeDeltaDrainCalls: Int = 0
    var strokeStartedAtMs: Long = 0L
    var lastLiveOverlayDrainMs: Long = 0L
    var lastLiveOverlayCompactionMs: Long = 0L
    var lastLiveOverlayCompactionSkipLogMs: Long = 0L
    var strokeLargestDeltaVertices: Int = 0
    var strokeLargestDeltaLayers: Int = 0
    var payloadDirty: Boolean = false
    var lastStrokePoint: ViewerPaintStrokePoint? = null
    var strokeInProgress: Boolean = false
    var strokeBeginAccepted: Boolean = false
    var deferredNativeStrokeBegin: Boolean = false
    var strokeReleaseSequence: Long = 0L
}

internal fun ViewerPaintOverlay.plus(other: ViewerPaintOverlay): ViewerPaintOverlay =
    when {
        layers.isEmpty() -> other
        other.layers.isEmpty() -> this
        else -> ViewerPaintOverlay(layers + other.layers)
    }

internal fun ViewerPaintOverlay.plusReplacing(other: ViewerPaintOverlay): ViewerPaintOverlay =
    when {
        other.layers.isEmpty() -> this
        layers.isEmpty() -> ViewerPaintOverlay(other.layers.filterNot { it.deleteOnly })
        else -> {
            val replacementSourceKeys = other.layers.mapNotNullTo(mutableSetOf()) { it.id.overlaySourceKey() }
            val byId = LinkedHashMap<String, ViewerPaintOverlayLayer>(layers.size + other.layers.size)
            layers.forEach { layer ->
                if (!layer.deleteOnly && layer.id.overlaySourceKey() !in replacementSourceKeys) {
                    byId[layer.id] = layer
                }
            }
            other.layers.forEach { layer ->
                if (layer.deleteOnly) {
                    byId.remove(layer.id)
                    layer.id.overlaySourceKey()?.let { sourceKey ->
                        byId.keys
                            .filter { it.overlaySourceKey() == sourceKey }
                            .forEach { byId.remove(it) }
                    }
                } else {
                    byId[layer.id] = layer
                }
            }
            ViewerPaintOverlay(byId.values.toList())
        }
    }

private fun String.overlaySourceKey(): String? {
    if (!startsWith("delta-v")) return null
    val stateIndex = indexOf("-s")
    if (stateIndex <= 0) return null
    val sourceIndex = indexOf("-t", startIndex = stateIndex + 2)
    if (sourceIndex <= stateIndex) return null
    return substring(0, stateIndex) + substring(sourceIndex)
}

internal fun ViewerPaintMode.paintMenuLabel(): String = when (this) {
    ViewerPaintMode.Color -> "Color"
    ViewerPaintMode.Seam -> "Seam"
    ViewerPaintMode.Support -> "Support"
    ViewerPaintMode.FuzzySkin -> "Fuzzy"
}

internal fun ViewerPaintBrushShape.paintMenuLabel(): String = when (this) {
    ViewerPaintBrushShape.Circle -> "Circle"
    ViewerPaintBrushShape.Sphere -> "Sphere"
    ViewerPaintBrushShape.Triangle -> "Triangle"
    ViewerPaintBrushShape.HeightRange -> "Height"
    ViewerPaintBrushShape.Fill -> "Fill"
    ViewerPaintBrushShape.GapFill -> "Gap fill"
}

internal fun ViewerPaintBrushShape.isTapPaintTool(): Boolean =
    this == ViewerPaintBrushShape.Triangle ||
        this == ViewerPaintBrushShape.HeightRange ||
        this == ViewerPaintBrushShape.Fill ||
        this == ViewerPaintBrushShape.GapFill

internal fun nativePaintSourceBoundsFor(handle: NativeEngineHandle, mobileObjectId: Long): MeshBounds? =
    parseNativePaintSourceBoundsJson(NativePaintCalls.objectBoundsJson(handle, mobileObjectId), mobileObjectId)

internal fun parseNativePaintSourceBoundsJson(boundsJson: String?, mobileObjectId: Long): MeshBounds? =
    runCatching {
        val root = JSONObject(boundsJson.orEmpty())
        if (root.optLong("mobileObjectId", mobileObjectId) != mobileObjectId) {
            return@runCatching null
        }
        val boundsArray = root.optJSONArray("volumeBounds") ?: return@runCatching null
        val firstBounds = boundsArray.optJSONObject(0) ?: return@runCatching null
        firstBounds.toMeshBounds()
    }.getOrNull()

private fun JSONObject.toMeshBounds(): MeshBounds =
    MeshBounds(
        minX = optDouble("minX", 0.0).toFloat(),
        minY = optDouble("minY", 0.0).toFloat(),
        minZ = optDouble("minZ", 0.0).toFloat(),
        maxX = optDouble("maxX", 0.0).toFloat(),
        maxY = optDouble("maxY", 0.0).toFloat(),
        maxZ = optDouble("maxZ", 0.0).toFloat()
    )

internal fun remapPaintRayBetweenSourceBounds(
    ray: FloatArray,
    fromBounds: MeshBounds,
    toBounds: MeshBounds
): FloatArray {
    if (ray.size < 6) return ray
    val scaleX = sourceBoundsScale(toBounds.sizeX, fromBounds.sizeX)
    val scaleY = sourceBoundsScale(toBounds.sizeY, fromBounds.sizeY)
    val scaleZ = sourceBoundsScale(toBounds.sizeZ, fromBounds.sizeZ)
    val originX = (ray[0] - fromBounds.centerX) * scaleX + toBounds.centerX
    val originY = (ray[1] - fromBounds.centerY) * scaleY + toBounds.centerY
    val originZ = (ray[2] - fromBounds.centerZ) * scaleZ + toBounds.centerZ
    var dirX = ray[3] * scaleX
    var dirY = ray[4] * scaleY
    var dirZ = ray[5] * scaleZ
    val length = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
    if (length > 0.0001f) {
        dirX /= length
        dirY /= length
        dirZ /= length
    }
    return floatArrayOf(originX, originY, originZ, dirX, dirY, dirZ)
}

private fun sourceBoundsScale(targetSize: Float, sourceSize: Float): Float =
    if (targetSize > 0.0001f && sourceSize > 0.0001f) {
        (targetSize / sourceSize).coerceIn(0.01f, 100f)
    } else {
        1f
    }

internal fun ViewerPaintOverlay.remapSourceBoundsToViewerBounds(viewerBounds: MeshBounds?): ViewerPaintOverlay {
    if (viewerBounds == null || layers.isEmpty()) return this
    var changed = false
    val remappedLayers = layers.map { layer ->
        val sourceBounds = layer.sourceBounds
        if (sourceBounds == null || sourceBounds.nearlySameBounds(viewerBounds)) {
            layer
        } else {
            changed = true
            layer.remapSourceBoundsToViewerBounds(sourceBounds, viewerBounds)
        }
    }
    return if (changed) ViewerPaintOverlay(remappedLayers) else this
}

private fun ViewerPaintOverlayLayer.remapSourceBoundsToViewerBounds(
    sourceBounds: MeshBounds,
    viewerBounds: MeshBounds
): ViewerPaintOverlayLayer {
    val scaleX = sourceBoundsScale(viewerBounds.sizeX, sourceBounds.sizeX)
    val scaleY = sourceBoundsScale(viewerBounds.sizeY, sourceBounds.sizeY)
    val scaleZ = sourceBoundsScale(viewerBounds.sizeZ, sourceBounds.sizeZ)
    val remappedVertices = vertices.copyOf()
    var index = 0
    while (index + 2 < remappedVertices.size) {
        remappedVertices[index] = (remappedVertices[index] - sourceBounds.centerX) * scaleX + viewerBounds.centerX
        remappedVertices[index + 1] = (remappedVertices[index + 1] - sourceBounds.centerY) * scaleY + viewerBounds.centerY
        remappedVertices[index + 2] = (remappedVertices[index + 2] - sourceBounds.centerZ) * scaleZ + viewerBounds.centerZ
        index += 3
    }
    val remappedNormals = normals.copyOf()
    index = 0
    while (index + 2 < remappedNormals.size) {
        var nx = remappedNormals[index] / scaleX
        var ny = remappedNormals[index + 1] / scaleY
        var nz = remappedNormals[index + 2] / scaleZ
        val length = sqrt(nx * nx + ny * ny + nz * nz)
        if (length > 0.0001f) {
            nx /= length
            ny /= length
            nz /= length
        }
        remappedNormals[index] = nx
        remappedNormals[index + 1] = ny
        remappedNormals[index + 2] = nz
        index += 3
    }
    return copy(vertices = remappedVertices, normals = remappedNormals, sourceBounds = viewerBounds)
}

private fun MeshBounds.nearlySameBounds(other: MeshBounds): Boolean =
    nearlySame(minX, other.minX) &&
        nearlySame(minY, other.minY) &&
        nearlySame(minZ, other.minZ) &&
        nearlySame(maxX, other.maxX) &&
        nearlySame(maxY, other.maxY) &&
        nearlySame(maxZ, other.maxZ)

private fun nearlySame(a: Float, b: Float): Boolean =
    kotlin.math.abs(a - b) <= 0.001f

internal fun MeshBounds.paintSizingSummary(): String =
    "size=${String.format(java.util.Locale.US, "%.3f", sizeX)}x" +
        "${String.format(java.util.Locale.US, "%.3f", sizeY)}x" +
        String.format(java.util.Locale.US, "%.3f", sizeZ)

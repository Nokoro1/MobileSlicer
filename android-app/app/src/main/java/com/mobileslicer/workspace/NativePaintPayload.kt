package com.mobileslicer.workspace

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal fun nativePaintPayloadJson(objects: List<PlateObject>): String {
    val paintedObjects = objects.filter { it.paint.hasActivePaint }
    if (paintedObjects.isEmpty()) return ""
    return JSONObject()
        .put("schemaVersion", 1)
        .put("objects", JSONArray().apply {
            paintedObjects.forEach { objectOnPlate ->
                put(
                    JSONObject()
                        .put("mobileObjectId", objectOnPlate.id)
                        .put("sourceKey", objectOnPlate.nativeSourceKey.ifBlank { objectOnPlate.filePath })
                        .put("paintHash", objectOnPlate.paint.paintHash())
                        .put("layers", JSONArray().apply {
                            objectOnPlate.paint.layers()
                                .filter { !it.isEmpty && !it.isStale }
                                .forEach { put(it.toNativeJson()) }
                        })
                )
            }
        })
        .toString()
}

internal data class NativePaintPayloadCommit(
    val mobileObjectId: Long,
    val layers: List<SerializedPaintLayer>
) {
    val isEmpty: Boolean get() = layers.isEmpty()
}

internal data class NativePaintPlateObjectCommitResult(
    val objects: List<PlateObject>,
    val committedObject: PlateObject?
) {
    val changed: Boolean get() = committedObject != null
}

internal fun parseNativePaintPayloadCommit(
    payloadJson: String,
    fallbackObject: PlateObject,
    fallbackMode: PaintMode
): NativePaintPayloadCommit? =
    runCatching {
        val root = JSONObject(payloadJson)
        val sourceKey = fallbackObject.nativeSourceKey.ifBlank { fallbackObject.filePath }
        val sourceFingerprint = computeSourceMeshFingerprint(
            file = File(fallbackObject.filePath),
            bounds = fallbackObject.bounds,
            triangleCount = null
        )
        if (root.has("objects")) {
            root.toReplayPayloadCommit(
                fallbackObject = fallbackObject,
                fallbackMode = fallbackMode,
                sourceKey = sourceKey,
                sourceFingerprint = sourceFingerprint
            )
        } else {
            root.toSessionPayloadCommit(
                fallbackObject = fallbackObject,
                fallbackMode = fallbackMode,
                sourceKey = sourceKey,
                sourceFingerprint = sourceFingerprint
            )
        }
    }.getOrNull()

internal fun commitNativePaintPayloadToPlateObjects(
    objects: List<PlateObject>,
    objectId: Long,
    mode: PaintMode,
    payloadJson: String
): NativePaintPlateObjectCommitResult {
    val index = objects.indexOfFirst { it.id == objectId }
    if (index < 0) {
        return NativePaintPlateObjectCommitResult(objects = objects, committedObject = null)
    }
    val objectOnPlate = objects[index]
    val commit = parseNativePaintPayloadCommit(
        payloadJson = payloadJson,
        fallbackObject = objectOnPlate,
        fallbackMode = mode
    ) ?: return NativePaintPlateObjectCommitResult(objects = objects, committedObject = null)
    if (commit.mobileObjectId != objectId || commit.isEmpty) {
        return NativePaintPlateObjectCommitResult(objects = objects, committedObject = null)
    }
    val updatedObject = objectOnPlate.copy(
        paint = objectOnPlate.paint.withCommittedNativeLayers(commit.layers)
    )
    return NativePaintPlateObjectCommitResult(
        objects = objects.toMutableList().also { it[index] = updatedObject },
        committedObject = updatedObject
    )
}

internal fun PlateObjectPaint.withCommittedNativeLayers(layers: List<SerializedPaintLayer>): PlateObjectPaint {
    var next = this
    layers.forEach { layer ->
        next = when (layer.mode) {
            PaintMode.Support -> next.copy(support = layer.takeUnless { it.isEmpty })
            PaintMode.Seam -> next.copy(seam = layer.takeUnless { it.isEmpty })
            PaintMode.Color -> next.copy(color = layer.takeUnless { it.isEmpty })
            PaintMode.FuzzySkin -> next.copy(fuzzySkin = layer.takeUnless { it.isEmpty })
        }
    }
    return next
}

private fun SerializedPaintLayer.toNativeJson(): JSONObject =
    JSONObject()
        .put("mode", mode.name)
        .put("schemaVersion", schemaVersion)
        .put("objectSourceKey", objectSourceKey)
        .put("sourceFingerprint", meshFingerprint?.stableSignature().orEmpty())
        .put("volumes", JSONArray().apply {
            volumeLayers
                .filter { !it.isEmpty }
                .sortedBy { it.volumeIndex }
                .forEach { put(it.toNativeJson()) }
        })
        .also { json ->
            if (mode == PaintMode.Color) {
                json.put("colorSlots", JSONArray().apply {
                    referencedSlotIndexes.sorted().forEach { put(it) }
                })
            }
        }

private fun SerializedPaintVolumeLayer.toNativeJson(): JSONObject = JSONObject()
    .put("volumeIndex", volumeIndex)
    .put("triangleCount", triangleCount)
    .put("meshFingerprint", nativeMeshFingerprint.orEmpty())
    .put("triangles", JSONArray().apply {
        serializedTriangles
            .filter { it.hexBits.isNotBlank() }
            .sortedBy { it.triangleIndex }
            .forEach { triangle ->
                put(
                    JSONObject()
                        .put("triangleIndex", triangle.triangleIndex)
                        .put("hexBits", triangle.hexBits)
                )
            }
    })

private fun JSONObject.toSessionPayloadCommit(
    fallbackObject: PlateObject,
    fallbackMode: PaintMode,
    sourceKey: String,
    sourceFingerprint: SourceMeshFingerprint?
): NativePaintPayloadCommit? {
    val mobileObjectId = optLong("mobileObjectId", fallbackObject.id)
    if (mobileObjectId != fallbackObject.id) return null
    val mode = optString("mode", fallbackMode.name).toPaintModeOrNull() ?: fallbackMode
    val layer = SerializedPaintLayer(
        mode = mode,
        objectSourceKey = sourceKey,
        meshFingerprint = sourceFingerprint,
        referencedSlotIndexes = paintSlotIndexes(),
        volumeLayers = optJSONArray("volumes").toVolumeLayers()
    )
    return NativePaintPayloadCommit(mobileObjectId = mobileObjectId, layers = listOf(layer))
}

private fun JSONObject.toReplayPayloadCommit(
    fallbackObject: PlateObject,
    fallbackMode: PaintMode,
    sourceKey: String,
    sourceFingerprint: SourceMeshFingerprint?
): NativePaintPayloadCommit? {
    val objects = optJSONArray("objects") ?: return null
    for (index in 0 until objects.length()) {
        val objectJson = objects.optJSONObject(index) ?: continue
        val mobileObjectId = objectJson.optLong("mobileObjectId", -1L)
        if (mobileObjectId != fallbackObject.id) continue
        return NativePaintPayloadCommit(
            mobileObjectId = mobileObjectId,
            layers = objectJson.optJSONArray("layers").toSerializedPaintLayers(
                fallbackMode = fallbackMode,
                sourceKey = sourceKey,
                sourceFingerprint = sourceFingerprint
            )
        )
    }
    return null
}

private fun JSONArray?.toSerializedPaintLayers(
    fallbackMode: PaintMode,
    sourceKey: String,
    sourceFingerprint: SourceMeshFingerprint?
): List<SerializedPaintLayer> {
    if (this == null || length() == 0) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val layerJson = optJSONObject(index) ?: continue
            val mode = layerJson.optString("mode", fallbackMode.name).toPaintModeOrNull() ?: continue
            add(
                SerializedPaintLayer(
                    mode = mode,
                    objectSourceKey = layerJson.optString("objectSourceKey", sourceKey).ifBlank { sourceKey },
                    meshFingerprint = sourceFingerprint,
                    referencedSlotIndexes = layerJson.paintSlotIndexes(),
                    volumeLayers = layerJson.optJSONArray("volumes").toVolumeLayers()
                )
            )
        }
    }
}

private fun JSONArray?.toVolumeLayers(): List<SerializedPaintVolumeLayer> {
    if (this == null || length() == 0) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val volumeJson = optJSONObject(index) ?: continue
            val triangles = volumeJson.optJSONArray("triangles").toSerializedPaintTriangles()
            if (triangles.isEmpty()) continue
            val volumeIndex = volumeJson.optInt("volumeIndex", -1)
            if (volumeIndex < 0) continue
            add(
                SerializedPaintVolumeLayer(
                    volumeIndex = volumeIndex,
                    triangleCount = volumeJson.optInt("triangleCount", 0),
                    serializedTriangles = triangles,
                    nativeMeshFingerprint = volumeJson.optString("meshFingerprint", "").takeIf { it.isNotBlank() }
                )
            )
        }
    }
}

private fun JSONArray?.toSerializedPaintTriangles(): List<SerializedPaintTriangle> {
    if (this == null || length() == 0) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val triangleJson = optJSONObject(index) ?: continue
            val triangleIndex = triangleJson.optInt("triangleIndex", -1)
            val hexBits = triangleJson.optString("hexBits", "").trim().uppercase()
            if (triangleIndex >= 0 && hexBits.isNotBlank() && hexBits.all { it in '0'..'9' || it in 'A'..'F' }) {
                add(SerializedPaintTriangle(triangleIndex = triangleIndex, hexBits = hexBits))
            }
        }
    }.sortedBy { it.triangleIndex }
}

private fun JSONArray?.toIntSet(): Set<Int> {
    if (this == null || length() == 0) return emptySet()
    return buildSet {
        for (index in 0 until length()) {
            val value = optInt(index, 0)
            if (value > 0) add(value)
        }
    }
}

private fun JSONObject.paintSlotIndexes(): Set<Int> =
    (optJSONArray("colorSlots") ?: optJSONArray("referencedSlotIndexes")).toIntSet()

private fun String.toPaintModeOrNull(): PaintMode? =
    PaintMode.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }

package com.mobileslicer.viewer

import java.util.LinkedHashMap

internal const val PaintLiveOverlayBatchVertexLimit = 18_000

internal fun replacementLayerIdsForUploads(uploads: List<PaintOverlayUpload>): Set<String> {
    val layerIds = mutableSetOf<String>()
    uploads.forEach { upload ->
        layerIds += upload.id.overlayLayerId()
        layerIds += upload.layerIds
    }
    return layerIds
}

internal fun replacementKeysForUploads(uploads: List<PaintOverlayUpload>): Set<String> {
    val keys = mutableSetOf<String>()
    uploads.forEach { keys += replacementKeysForUpload(it) }
    return keys
}

internal fun replacementKeysForUpload(upload: PaintOverlayUpload): Set<String> =
    buildSet {
        add(upload.id.overlayLayerId())
        addAll(upload.layerIds)
        addAll(upload.sourceKeys)
    }

internal fun PaintOverlayUpload.primaryPageKey(): String =
    sourceKeys.firstOrNull()
        ?: layerIds.firstOrNull()
        ?: id.overlayLayerId()

internal fun MutableSet<String>.rebuildFromPaintOverlayUploads(uploads: List<PaintOverlayUpload>) {
    clear()
    addAll(replacementKeysForUploads(uploads))
}

internal fun String.overlayLayerId(): String =
    substringBeforeLast(":", this)

internal fun String.overlaySourceKey(): String? {
    if (!startsWith("delta-v")) return null
    val stateIndex = indexOf("-s")
    if (stateIndex <= 0) return null
    val sourceIndex = indexOf("-t", startIndex = stateIndex + 2)
    if (sourceIndex <= stateIndex) return null
    return substring(0, stateIndex) + substring(sourceIndex)
}

internal fun String.matchesOverlayReplacement(layerIds: Set<String>, sourceKeys: Set<String>): Boolean {
    if (this in layerIds) return true
    val key = overlaySourceKey() ?: return false
    return key in sourceKeys
}

internal fun PaintOverlayUpload.matchesOverlayReplacement(layerIds: Set<String>, sourceKeys: Set<String>): Boolean =
    id.overlayLayerId().matchesOverlayReplacement(layerIds, sourceKeys) ||
        this.layerIds.any { it in layerIds } ||
        this.sourceKeys.any { it in sourceKeys }

internal fun ViewerPaintOverlay.plusReplacing(other: ViewerPaintOverlay): ViewerPaintOverlay {
    if (other.layers.isEmpty()) return this
    if (layers.isEmpty()) return ViewerPaintOverlay(other.layers.filterNot { it.deleteOnly })
    val byId = LinkedHashMap<String, ViewerPaintOverlayLayer>(layers.size + other.layers.size)
    val replacementSourceKeys = other.layers.mapNotNullTo(mutableSetOf()) { it.id.overlaySourceKey() }
    layers.forEach { layer ->
        if (!layer.deleteOnly && !layer.id.matchesOverlayReplacement(emptySet(), replacementSourceKeys)) {
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
    return ViewerPaintOverlay(byId.values.toList())
}

internal fun ViewerPaintOverlay.coalescedForLiveUpload(): ViewerPaintOverlay {
    if (layers.size <= 1) return this
    val batched = mutableListOf<ViewerPaintOverlayLayer>()
    var batchColor = 0
    var batchState = 0
    var batchSourceBounds: MeshBounds? = null
    var batchModelMatrix: FloatArray? = null
    val batchVertices = mutableListOf<FloatArray>()
    val batchNormals = mutableListOf<FloatArray>()
    var batchVertexCount = 0

    fun flushBatch() {
        if (batchVertexCount <= 0) return
        val vertices = FloatArray(batchVertexCount * 3)
        val normals = FloatArray(batchVertexCount * 3)
        var vertexOffset = 0
        batchVertices.forEach { source ->
            source.copyInto(vertices, destinationOffset = vertexOffset)
            vertexOffset += source.size
        }
        var normalOffset = 0
        batchNormals.forEach { source ->
            source.copyInto(normals, destinationOffset = normalOffset)
            normalOffset += source.size
        }
        batched += ViewerPaintOverlayLayer(
            id = "live-batch-${batched.size}",
            colorInt = batchColor,
            state = batchState,
            vertices = vertices,
            normals = normals,
            sourceBounds = batchSourceBounds,
            modelMatrix = batchModelMatrix
        )
        batchVertices.clear()
        batchNormals.clear()
        batchVertexCount = 0
    }

    fun canJoin(layer: ViewerPaintOverlayLayer, vertexCount: Int): Boolean =
        batchVertexCount > 0 &&
            batchColor == layer.colorInt &&
            batchState == layer.state &&
            batchSourceBounds == layer.sourceBounds &&
            batchModelMatrix.contentEqualsNullable(layer.modelMatrix) &&
            batchVertexCount + vertexCount <= PaintLiveOverlayBatchVertexLimit

    layers.forEach { layer ->
        if (layer.deleteOnly || layer.vertices.isEmpty() || layer.normals.isEmpty()) return@forEach
        val vertexCount = minOf(layer.vertices.size, layer.normals.size) / 3
        if (vertexCount <= 0) return@forEach
        if (!canJoin(layer, vertexCount)) {
            flushBatch()
            batchColor = layer.colorInt
            batchState = layer.state
            batchSourceBounds = layer.sourceBounds
            batchModelMatrix = layer.modelMatrix
        }
        batchVertices += layer.vertices
        batchNormals += layer.normals
        batchVertexCount += vertexCount
    }
    flushBatch()
    return ViewerPaintOverlay(batched)
}

internal fun FloatArray?.contentEqualsNullable(other: FloatArray?): Boolean =
    when {
        this === other -> true
        this == null || other == null -> false
        else -> contentEquals(other)
    }

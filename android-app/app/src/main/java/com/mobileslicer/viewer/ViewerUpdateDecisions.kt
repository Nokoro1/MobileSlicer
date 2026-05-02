package com.mobileslicer.viewer

internal data class PlateObjectUpdateDecision(
    val geometryChanged: Boolean,
    val transformChanged: Boolean,
    val selectionChanged: Boolean
)

internal data class GcodePreviewSource(
    val engineHandle: Long,
    val previewKey: Long
)

internal data class GcodeLayerRangeUpdateDecision(
    val rangeChanged: Boolean,
    val reloadChanged: Boolean,
    val shouldReloadPreview: Boolean
)

internal data class GcodePreviewStateVersions(
    val previewVersion: Long,
    val layerRangeVersion: Long,
    val pathVisibilityVersion: Long,
    val displayModeVersion: Long
)

internal object ViewerUpdateDecisions {
    fun plateObjectsSignature(objects: List<ViewerPlateObject>): Long =
        objects.fold(FnvOffsetBasis) { acc, objectOnPlate ->
            mix64(acc, objectOnPlate.renderSignature())
        }

    fun samePlateObjectUploadSet(
        previous: List<ViewerPlateObject>,
        next: List<ViewerPlateObject>
    ): Boolean = samePlateObjectGeometry(previous, next)

    fun plateObjectUpdateDecision(
        previous: List<ViewerPlateObject>,
        next: List<ViewerPlateObject>
    ): PlateObjectUpdateDecision =
        PlateObjectUpdateDecision(
            geometryChanged = !samePlateObjectGeometry(previous, next),
            transformChanged = !samePlateObjectTransforms(previous, next),
            selectionChanged = !samePlateObjectSelection(previous, next)
        )

    fun normalizeGcodePreviewSource(engineHandle: Long, previewKey: Long): GcodePreviewSource {
        val safePreviewKey = if (engineHandle != 0L && previewKey > 0L) previewKey else 0L
        val safeEngineHandle = if (safePreviewKey > 0L) engineHandle else 0L
        return GcodePreviewSource(
            engineHandle = safeEngineHandle,
            previewKey = safePreviewKey
        )
    }

    fun gcodeLayerRangeUpdateDecision(
        previousMinLayer: Long,
        previousMaxLayer: Long,
        previousReloadToken: Long,
        nextMinLayer: Long,
        nextMaxLayer: Long,
        nextReloadToken: Long,
        activeEngineHandle: Long,
        activePreviewKey: Long
    ): GcodeLayerRangeUpdateDecision {
        val rangeChanged = previousMinLayer != nextMinLayer || previousMaxLayer != nextMaxLayer
        val reloadChanged = previousReloadToken != nextReloadToken
        return GcodeLayerRangeUpdateDecision(
            rangeChanged = rangeChanged,
            reloadChanged = reloadChanged,
            shouldReloadPreview = reloadChanged && activeEngineHandle != 0L && activePreviewKey > 0L
        )
    }

    fun isGcodePreviewStateCurrent(
        expected: GcodePreviewStateVersions,
        current: GcodePreviewStateVersions
    ): Boolean = expected == current

    fun gcodePreviewReloadCoalesceDelayMs(
        rendererActive: Boolean,
        queuedAtMs: Long,
        nowMs: Long,
        coalesceWindowMs: Long
    ): Long {
        if (!rendererActive || queuedAtMs <= 0L || coalesceWindowMs <= 0L) {
            return 0L
        }
        return (coalesceWindowMs - (nowMs - queuedAtMs)).coerceAtLeast(0L)
    }

    private fun samePlateObjectGeometry(
        previous: List<ViewerPlateObject>,
        next: List<ViewerPlateObject>
    ): Boolean {
        if (previous.size != next.size) return false
        return previous.zip(next).all { (left, right) ->
            left.id == right.id && left.mesh === right.mesh
        }
    }

    private fun samePlateObjectTransforms(
        previous: List<ViewerPlateObject>,
        next: List<ViewerPlateObject>
    ): Boolean {
        if (previous.size != next.size) return false
        return previous.zip(next).all { (left, right) ->
            left.id == right.id && left.transform == right.transform
        }
    }

    private fun samePlateObjectSelection(
        previous: List<ViewerPlateObject>,
        next: List<ViewerPlateObject>
    ): Boolean {
        if (previous.size != next.size) return false
        return previous.zip(next).all { (left, right) ->
            left.id == right.id &&
                left.selected == right.selected &&
                left.colorInt == right.colorInt
        }
    }

    private fun ViewerPlateObject.renderSignature(): Long {
        var result = mix64(FnvOffsetBasis, id)
        result = mix64(result, mesh.geometrySignature())
        result = mix64(result, transform.hashCode().toLong())
        result = mix64(result, colorInt?.toLong() ?: 0L)
        result = mix64(result, if (selected) 1L else 0L)
        return result
    }

    private fun StlMesh.geometrySignature(): Long {
        var result = mix64(FnvOffsetBasis, System.identityHashCode(this).toLong())
        result = mix64(result, System.identityHashCode(vertices).toLong())
        result = mix64(result, System.identityHashCode(normals).toLong())
        result = mix64(result, vertices.size.toLong())
        result = mix64(result, normals.size.toLong())
        result = mix64(result, triangleCount.toLong())
        result = mix64(result, bounds.hashCode().toLong())
        return result
    }

    private fun mix64(seed: Long, value: Long): Long =
        (seed xor value) * 1_099_511_628_211L

    private const val FnvOffsetBasis = -3_750_763_034_362_895_579L
}

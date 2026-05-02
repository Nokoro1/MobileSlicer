package com.mobileslicer.viewer

import com.mobileslicer.nativebridge.NativeEngineCallResult
import com.mobileslicer.nativebridge.NativeEngineError
import com.mobileslicer.nativebridge.NativeEngineErrorCode
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.nativebridge.NativeGcodeViewerCalls
import com.mobileslicer.nativebridge.NativeGcodeViewerHandle

internal val DefaultPreviewVertexBudget: Long = GcodePreviewPerformanceMode.Default.vertexBudget

internal data class GcodePreviewLoadState(
    val loadedLayerStart: Long,
    val loadedLayerEnd: Long,
    val localLayerMin: Long,
    val localLayerMax: Long
)

internal data class GcodePreviewNativeLoadMetrics(
    val selectedParseMs: Long = 0L,
    val libvgcodeLoadMs: Long = 0L,
    val totalMs: Long = 0L,
    val vertices: Long = 0L,
    val cachedVertices: Long = 0L,
    val cachedLayers: Long = 0L,
    val cacheHit: Long = 0L,
    val cacheBuilt: Long = 0L
)

internal class GcodePreviewRenderer {
    private var viewerHandle: NativeGcodeViewerHandle? = null
    private var loadedLayerStart: Long = 0L
    private var loadedLayerEnd: Long = Long.MAX_VALUE
    private var latestNativeLoadMetrics = GcodePreviewNativeLoadMetrics()

    val isActive: Boolean
        get() = viewerHandle != null

    val lastNativeLoadMetrics: GcodePreviewNativeLoadMetrics
        get() = latestNativeLoadMetrics

    fun loadLatestSlice(
        engineRawHandle: Long,
        requestedLayerMin: Long,
        requestedLayerMax: Long,
        vertexBudget: Long = DefaultPreviewVertexBudget,
        generation: Long = 0L
    ): NativeEngineCallResult {
        val activeHandle = viewerHandle
        val createdHandle = activeHandle ?: NativeGcodeViewerCalls.create()
            ?: return failure("nativeCreateGcodeViewer", "G-code preview renderer could not be created.")
        val engineHandle = NativeEngineHandle.fromRaw(engineRawHandle)
        if (engineHandle == null) {
            if (activeHandle == null) {
                NativeGcodeViewerCalls.destroy(createdHandle)
            }
            return failure("nativeLoadLatestSliceIntoGcodeViewer", "G-code preview source engine was unavailable.")
        }

        viewerHandle = createdHandle
        val loadResult = NativeGcodeViewerCalls.loadLatestSlice(
            handle = createdHandle,
            engineHandle = engineHandle,
            minLayer = requestedLayerMin,
            maxLayer = requestedLayerMax,
            lodHint = vertexBudget.coerceIn(1L, GcodePreviewPerformanceMode.HARD_VERTEX_CEILING).toInt(),
            generation = generation
        )
        if (loadResult is NativeEngineCallResult.Success) {
            loadedLayerStart = requestedLayerMin
            loadedLayerEnd = requestedLayerMax
            latestNativeLoadMetrics = parseNativeLoadMetrics(
                NativeGcodeViewerCalls.getLastLoadMetrics(createdHandle)
            )
        } else {
            latestNativeLoadMetrics = GcodePreviewNativeLoadMetrics()
            release()
        }
        return loadResult
    }

    fun currentLoadState(requestedLayerMin: Long, requestedLayerMax: Long): GcodePreviewLoadState =
        GcodePreviewLoadState(
            loadedLayerStart = requestedLayerMin,
            loadedLayerEnd = requestedLayerMax,
            localLayerMin = 0L,
            localLayerMax = (requestedLayerMax - requestedLayerMin).coerceAtLeast(0L)
        )

    fun setVisibleLayerRange(globalLayerMin: Long, globalLayerMax: Long): NativeEngineCallResult {
        val handle = viewerHandle ?: return NativeEngineCallResult.Success
        val loadedLayerSpan = (loadedLayerEnd - loadedLayerStart).coerceAtLeast(0L)
        val localLayerMin = (globalLayerMin - loadedLayerStart).coerceIn(0L, loadedLayerSpan)
        val localLayerMax = (globalLayerMax - loadedLayerStart)
            .coerceAtLeast(localLayerMin)
            .coerceAtMost(loadedLayerSpan)
        return NativeGcodeViewerCalls.setLayerRange(handle, localLayerMin, localLayerMax)
    }

    fun setPathVisibility(kind: Int, id: Int, visible: Boolean): NativeEngineCallResult {
        val handle = viewerHandle ?: return NativeEngineCallResult.Success
        return NativeGcodeViewerCalls.setPathVisibility(handle, kind, id, visible)
    }

    fun setDisplayMode(mode: GcodePreviewDisplayMode): NativeEngineCallResult {
        val handle = viewerHandle ?: return NativeEngineCallResult.Success
        return NativeGcodeViewerCalls.setViewType(handle, mode.nativeId)
    }

    fun render(viewMatrix: FloatArray, projectionMatrix: FloatArray): NativeEngineCallResult {
        val handle = viewerHandle
            ?: return failure("nativeRenderGcodeViewer", "G-code preview renderer is not active.")
        return NativeGcodeViewerCalls.render(handle, viewMatrix, projectionMatrix)
    }

    fun release() {
        val handle = viewerHandle ?: return
        NativeGcodeViewerCalls.shutdown(handle)
        NativeGcodeViewerCalls.destroy(handle)
        viewerHandle = null
        loadedLayerStart = 0L
        loadedLayerEnd = Long.MAX_VALUE
        latestNativeLoadMetrics = GcodePreviewNativeLoadMetrics()
    }

    private fun failure(operation: String, message: String): NativeEngineCallResult.Failure =
        NativeEngineCallResult.Failure(
            operation = operation,
            error = NativeEngineError(
                code = NativeEngineErrorCode.Unknown,
                message = message
            )
        )
}

private fun parseNativeLoadMetrics(raw: String): GcodePreviewNativeLoadMetrics {
    if (raw.isBlank()) return GcodePreviewNativeLoadMetrics()
    val fields = raw.split('|')
        .mapNotNull { field ->
            val separator = field.indexOf('=')
            if (separator <= 0) {
                null
            } else {
                field.substring(0, separator) to field.substring(separator + 1)
            }
        }
        .toMap()
    fun longField(name: String): Long = fields[name]?.toLongOrNull() ?: 0L
    val cache = fields["cache"].orEmpty()
    return GcodePreviewNativeLoadMetrics(
        selectedParseMs = longField("selectedParseMs"),
        libvgcodeLoadMs = longField("libvgcodeLoadMs"),
        totalMs = longField("totalMs"),
        vertices = longField("vertices"),
        cachedVertices = longField("cachedVertices"),
        cachedLayers = longField("cachedLayers"),
        cacheHit = if (cache == "range") 1L else 0L,
        cacheBuilt = longField("cacheBuilt")
    )
}

internal fun parsePreviewRangePlan(plan: String?): List<PreviewRangeSuggestion> {
    if (plan.isNullOrBlank()) {
        return emptyList()
    }
    return plan.split(';')
        .mapNotNull { rawRange ->
            val parts = rawRange.trim().split('-', limit = 2)
            if (parts.size != 2) {
                return@mapNotNull null
            }
            val startZeroBased = parts[0].trim().toLongOrNull() ?: return@mapNotNull null
            val endZeroBased = parts[1].trim().toLongOrNull() ?: return@mapNotNull null
            if (startZeroBased < 0L || endZeroBased < startZeroBased) {
                return@mapNotNull null
            }
            PreviewRangeSuggestion(
                startLayer = (startZeroBased + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                endLayer = (endZeroBased + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                label = ""
            )
        }
        .mapIndexed { index, range ->
            range.copy(label = "Range ${index + 1}")
        }
}

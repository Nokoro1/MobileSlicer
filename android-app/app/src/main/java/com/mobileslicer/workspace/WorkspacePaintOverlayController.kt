package com.mobileslicer.workspace

import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.mobileslicer.nativebridge.NativePaintCalls
import com.mobileslicer.viewer.ViewerPaintMode
import com.mobileslicer.viewer.ViewerPaintOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal fun WorkspacePaintRuntimeController.refreshNativePaintOverlay(
    force: Boolean = false,
    preserveCachedOnEmpty: Boolean = false,
    initialDelayMs: Long = 0L,
    releaseStrokeId: Long = 0L,
    releaseStartedAtMs: Long = 0L
) {
    val handle = paintNativeHandle ?: return
    val overlayMode = activePaintMode
    val overlaySlotColors = filamentSlots.associate { slot ->
        slot.index to slotColor(slot.colorHex).toArgb()
    }
    val baseColorSlotIndex = selectedPlateObject?.filamentSlotIndex
    if (force) {
        paintRuntime.overlayRefreshJob?.cancel()
        paintRuntime.overlayRefreshJob = null
        paintRuntime.overlayRefreshPending = false
    } else if (paintRuntime.overlayRefreshJob?.isActive == true) {
        paintRuntime.overlayRefreshPending = true
        return
    }
    paintRuntime.overlayRefreshJob = paintCoroutineScope.launch {
        try {
            if (initialDelayMs > 0L) {
                delay(initialDelayMs)
                if (
                    paintRuntime.strokeInProgress ||
                    paintRuntime.pendingMove != null ||
                    paintRuntime.movePump?.isActive == true ||
                    paintRuntime.strokeBeginJob?.isActive == true
                ) {
                    Log.i("MobileSlicerPaintPerf", "overlay_snapshot delayed_cancelled reason=active_stroke")
                    return@launch
                }
            }
            do {
                val now = android.os.SystemClock.elapsedRealtime()
                val waitMs = if (force) {
                    0L
                } else {
                    (PaintOverlayRefreshMinIntervalMs - (now - paintRuntime.lastOverlayRefreshMs))
                        .coerceAtLeast(0L)
                }
                if (waitMs > 0L) delay(waitMs)
                paintRuntime.overlayRefreshPending = false
                val overlay = withContext(Dispatchers.Default) {
                    val nativeStartedAt = android.os.SystemClock.elapsedRealtime()
                    val binary = NativePaintCalls.overlayInterleavedBuffer(handle)
                    val nativeMs = android.os.SystemClock.elapsedRealtime() - nativeStartedAt
                    val parseStartedAt = android.os.SystemClock.elapsedRealtime()
                    val binaryParsed = if (binary != null) {
                        parseNativePaintOverlayDeltaInterleaved(binary, overlayMode, overlaySlotColors, baseColorSlotIndex)
                    } else {
                        parseNativePaintOverlayDeltaInterleaved(
                            NativePaintCalls.overlayInterleaved(handle),
                            overlayMode,
                            overlaySlotColors,
                            baseColorSlotIndex
                        )
                    }
                    val binaryParseMs = android.os.SystemClock.elapsedRealtime() - parseStartedAt
                    if (binaryParsed.layers.isNotEmpty()) {
                        val vertexCount = binaryParsed.layers.sumOf { it.vertices.size / 3 }
                        Log.i(
                            "MobileSlicerPaintPerf",
                            "overlay_snapshot_binary force=$force nativeMs=$nativeMs parseMs=$binaryParseMs " +
                                "layers=${binaryParsed.layers.size} vertices=$vertexCount floats=${(binary?.capacity() ?: 0) / Float.SIZE_BYTES}"
                        )
                        return@withContext binaryParsed
                    }
                    val jsonStartedAt = android.os.SystemClock.elapsedRealtime()
                    val json = NativePaintCalls.overlay(handle)
                    val jsonNativeMs = android.os.SystemClock.elapsedRealtime() - jsonStartedAt
                    if ((json?.length ?: 0) > PaintOverlaySnapshotByteLimit) {
                        Log.w(
                            "MobileSlicerPaintPerf",
                            "overlay_snapshot skipped reason=large_json nativeMs=$jsonNativeMs " +
                                "bytes=${json?.length ?: 0} limit=$PaintOverlaySnapshotByteLimit"
                        )
                        return@withContext ViewerPaintOverlay.Empty
                    }
                    val jsonParseStartedAt = android.os.SystemClock.elapsedRealtime()
                    val parsed = parseNativePaintOverlay(
                        json,
                        slotColors = overlaySlotColors,
                        baseColorSlotIndex = baseColorSlotIndex
                    )
                    val parseMs = android.os.SystemClock.elapsedRealtime() - jsonParseStartedAt
                    val vertexCount = parsed.layers.sumOf { it.vertices.size / 3 }
                    Log.i(
                        "MobileSlicerPaintPerf",
                        "overlay_snapshot force=$force nativeMs=$jsonNativeMs parseMs=$parseMs " +
                            "layers=${parsed.layers.size} vertices=$vertexCount bytes=${json?.length ?: 0}"
                    )
                    parsed
                }
                val cachedOverlay = overlayMode?.let { paintModeOverlayCache[it] }
                val useCachedEmptyFallback = force &&
                    preserveCachedOnEmpty &&
                    overlay.layers.isEmpty() &&
                    cachedOverlay != null &&
                    cachedOverlay.layers.isNotEmpty()
                val effectiveOverlay = if (useCachedEmptyFallback) {
                    Log.i(
                        "MobileSlicerPaintPerf",
                        "overlay_snapshot_empty_preserved mode=$overlayMode cachedLayers=${cachedOverlay.layers.size}"
                    )
                    cachedOverlay
                } else {
                    overlay
                }
                if (
                    force &&
                    (
                        paintRuntime.strokeInProgress ||
                            paintRuntime.pendingMove != null ||
                            paintRuntime.movePump?.isActive == true ||
                            paintRuntime.strokeBeginJob?.isActive == true
                        )
                ) {
                    Log.i(
                        "MobileSlicerPaintPerf",
                        "overlay_snapshot_apply skipped reason=active_stroke " +
                            "layers=${effectiveOverlay.layers.size} " +
                            "vertices=${effectiveOverlay.layers.sumOf { it.vertices.size / 3 }}"
                    )
                    return@launch
                }
                onNativePaintOverlayChanged(effectiveOverlay)
                if (force) {
                    paintRuntime.overlayDeltaLayersSinceSnapshot = 0
                    paintRuntime.overlayDeltaVerticesSinceSnapshot = 0
                }
                overlayMode?.let { mode ->
                    if (effectiveOverlay.layers.isNotEmpty()) {
                        paintModeOverlayCache[mode] = effectiveOverlay
                    } else if (force) {
                        paintModeOverlayCache.remove(mode)
                    }
                }
                val displayOverlay = if (force || effectiveOverlay.layers.isNotEmpty()) {
                    livePaintOverlayRef[0] = ViewerPaintOverlay.Empty
                    effectiveOverlay
                } else {
                    livePaintOverlayRef[0]
                }
                currentViewerView()?.setPaintOverlay(displayOverlay)
                paintRuntime.lastOverlayRefreshMs = android.os.SystemClock.elapsedRealtime()
                if (releaseStrokeId > 0L && releaseStartedAtMs > 0L) {
                    Log.i(
                        "MobileSlicerPaintPerf",
                        "stroke_release_complete strokeId=$releaseStrokeId totalMs=" +
                            "${paintRuntime.lastOverlayRefreshMs - releaseStartedAtMs} " +
                            "force=$force layers=${displayOverlay.layers.size} " +
                            "vertices=${displayOverlay.layers.sumOf { it.vertices.size / 3 }}"
                    )
                }
            } while (paintRuntime.overlayRefreshPending)
        } finally {
            if (paintRuntime.overlayRefreshJob == coroutineContext[Job]) {
                paintRuntime.overlayRefreshJob = null
            }
        }
    }
}

internal fun WorkspacePaintRuntimeController.clearLivePaintOverlay() {
    livePaintOverlayRef[0] = ViewerPaintOverlay.Empty
    currentViewerView()?.clearLivePaintOverlay()
}

internal fun WorkspacePaintRuntimeController.promoteLivePaintOverlay(mode: ViewerPaintMode?) {
    val liveOverlay = livePaintOverlayRef[0]
    if (liveOverlay.layers.isEmpty()) return
    val promotedOverlay = currentNativePaintOverlay().plusReplacing(liveOverlay)
    onNativePaintOverlayChanged(promotedOverlay)
    mode?.let { paintModeOverlayCache[it] = promotedOverlay }
    livePaintOverlayRef[0] = ViewerPaintOverlay.Empty
    paintRuntime.overlayDeltaLayersSinceSnapshot = 0
    paintRuntime.overlayDeltaVerticesSinceSnapshot = 0
    currentViewerView()?.promoteLivePaintOverlay()
}

internal fun WorkspacePaintRuntimeController.maybeCompactLivePaintOverlay() {
    if (!paintRuntime.strokeInProgress) return
    val now = android.os.SystemClock.elapsedRealtime()
    if (
        paintRuntime.overlayDeltaLayersSinceSnapshot < PaintLiveOverlayCompactionLayerThreshold &&
        paintRuntime.overlayDeltaVerticesSinceSnapshot < PaintLiveOverlayCompactionVertexThreshold
    ) return
    val baseVertices = currentNativePaintOverlay().layers.sumOf { it.vertices.size / 3 }
    if (
        paintRuntime.overlayDeltaLayersSinceSnapshot >= PaintLiveOverlayCompactionMaxPromotionLayerThreshold ||
        paintRuntime.overlayDeltaVerticesSinceSnapshot >= PaintLiveOverlayCompactionMaxPromotionVertexThreshold
    ) {
        if (now - paintRuntime.lastLiveOverlayCompactionMs >= PaintLiveOverlayCompactionSkipLogMinIntervalMs) {
            paintRuntime.lastLiveOverlayCompactionMs = now
            Log.i(
                "MobileSlicerPaintPerf",
                "overlay_live_promote skipped reason=large_live " +
                    "layers=${paintRuntime.overlayDeltaLayersSinceSnapshot} " +
                    "vertices=${paintRuntime.overlayDeltaVerticesSinceSnapshot} baseVertices=$baseVertices"
            )
        }
        return
    }
    if (baseVertices >= PaintLiveOverlayActiveCompactionBaseVertexLimit) {
        if (now - paintRuntime.lastLiveOverlayCompactionMs >= PaintLiveOverlayCompactionSkipLogMinIntervalMs) {
            paintRuntime.lastLiveOverlayCompactionMs = now
            Log.i(
                "MobileSlicerPaintPerf",
                "overlay_live_promote skipped reason=large_base " +
                    "layers=${paintRuntime.overlayDeltaLayersSinceSnapshot} " +
                    "vertices=${paintRuntime.overlayDeltaVerticesSinceSnapshot} baseVertices=$baseVertices"
            )
        }
        return
    }
    if (now - paintRuntime.lastLiveOverlayCompactionMs < PaintLiveOverlayCompactionMinIntervalMs) return
    paintRuntime.lastLiveOverlayCompactionMs = now
    Log.i(
        "MobileSlicerPaintPerf",
        "overlay_live_promote layers=${paintRuntime.overlayDeltaLayersSinceSnapshot} " +
            "vertices=${paintRuntime.overlayDeltaVerticesSinceSnapshot} baseVertices=$baseVertices"
    )
    promoteLivePaintOverlay(activePaintMode)
}

internal fun WorkspacePaintRuntimeController.appendExactPaintOverlayDelta(delta: ViewerPaintOverlay) {
    if (delta.layers.isEmpty()) return
    val vertices = delta.layers.sumOf { it.vertices.size / 3 }
    paintRuntime.strokeLargestDeltaVertices = maxOf(paintRuntime.strokeLargestDeltaVertices, vertices)
    paintRuntime.strokeLargestDeltaLayers = maxOf(paintRuntime.strokeLargestDeltaLayers, delta.layers.size)
    paintRuntime.strokeDeltaLayers += delta.layers.size
    paintRuntime.strokeDeltaVertices += vertices
    paintRuntime.strokeDeltaDrainCalls++
    livePaintOverlayRef[0] = livePaintOverlayRef[0].plusReplacing(delta)
    currentViewerView()?.appendPaintOverlay(delta)
    paintRuntime.overlayDeltaLayersSinceSnapshot += delta.layers.size
    paintRuntime.overlayDeltaVerticesSinceSnapshot += vertices
    if (vertices >= PaintOverlayPerfLogVertexThreshold) {
        Log.i("MobileSlicerPaintPerf", "overlay_delta_append layers=${delta.layers.size} vertices=$vertices")
    }
    maybeCompactLivePaintOverlay()
}

internal suspend fun WorkspacePaintRuntimeController.pullNativePaintOverlayDelta(reason: String, pass: Int): ViewerPaintOverlay? {
    val handle = paintNativeHandle ?: return null
    val overlayMode = activePaintMode
    val overlaySlotColors = filamentSlots.associate { slot ->
        slot.index to slotColor(slot.colorHex).toArgb()
    }
    val baseColorSlotIndex = selectedPlateObject?.filamentSlotIndex
    val startedAt = android.os.SystemClock.elapsedRealtime()
    val result = withContext(Dispatchers.Default) {
        val nativeStartedAt = android.os.SystemClock.elapsedRealtime()
        val binary = NativePaintCalls.overlayDeltaInterleavedBuffer(handle)
        val nativeMs = android.os.SystemClock.elapsedRealtime() - nativeStartedAt
        val parseStartedAt = android.os.SystemClock.elapsedRealtime()
        val delta = if (binary != null) {
            parseNativePaintOverlayDeltaInterleaved(binary, overlayMode, overlaySlotColors, baseColorSlotIndex)
        } else {
            parseNativePaintOverlayDeltaInterleaved(
                NativePaintCalls.overlayDeltaInterleaved(handle),
                overlayMode,
                overlaySlotColors,
                baseColorSlotIndex
            )
        }
        val parseMs = android.os.SystemClock.elapsedRealtime() - parseStartedAt
        Triple(delta, nativeMs, parseMs)
    }
    val delta = result.first
    val vertices = delta.layers.sumOf { it.vertices.size / 3 }
    if (delta.layers.isNotEmpty()) {
        Log.i(
            "MobileSlicerPaintPerf",
            "overlay_delta_drain reason=$reason pass=$pass ms=${android.os.SystemClock.elapsedRealtime() - startedAt} " +
                "nativeMs=${result.second} parseMs=${result.third} layers=${delta.layers.size} vertices=$vertices"
        )
        return delta
    }
    return null
}

internal suspend fun WorkspacePaintRuntimeController.pullAndAppendNativePaintOverlayDelta(reason: String, pass: Int): Boolean {
    val delta = pullNativePaintOverlayDelta(reason, pass) ?: return false
    appendExactPaintOverlayDelta(delta)
    return true
}

internal fun WorkspacePaintRuntimeController.drainNativePaintOverlayDeltas(reason: String, maxPasses: Int = PaintOverlayDeltaDrainPasses) {
    paintRuntime.overlayDeltaDrainJob?.cancel()
    paintRuntime.overlayDeltaDrainJob = paintCoroutineScope.launch {
        repeat(maxPasses) { pass ->
            val appended = pullAndAppendNativePaintOverlayDelta(reason, pass)
            if (!appended) return@launch
            delay(PaintOverlayDeltaDrainFrameDelayMs)
        }
    }
}

internal fun WorkspacePaintRuntimeController.drainNativePaintOverlayDeltasIfIdle(reason: String, maxPasses: Int = PaintOverlayDeltaDrainPasses) {
    if (paintRuntime.overlayDeltaDrainJob?.isActive == true) return
    if (reason.startsWith("stroke_")) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - paintRuntime.lastLiveOverlayDrainMs < PaintLiveOverlayDrainMinIntervalMs) return
        paintRuntime.lastLiveOverlayDrainMs = now
    }
    paintRuntime.overlayDeltaDrainJob = paintCoroutineScope.launch {
        repeat(maxPasses) { pass ->
            val appended = pullAndAppendNativePaintOverlayDelta(reason, pass)
            if (!appended) return@launch
            delay(PaintOverlayDeltaDrainFrameDelayMs)
        }
    }
}

internal suspend fun WorkspacePaintRuntimeController.flushNativePaintOverlayDeltasForStrokeEnd(): Pair<Int, Int> {
    val maxPasses = PaintTapOverlayDeltaDrainPasses
    var flushedLayers = 0
    var flushedVertices = 0
    var consecutiveSmall = 0
    for (pass in 0 until maxPasses) {
        val delta = pullNativePaintOverlayDelta("stroke_end_flush", pass) ?: break
        val layers = delta.layers.size
        val vertices = delta.layers.sumOf { it.vertices.size / 3 }
        appendExactPaintOverlayDelta(delta)
        flushedLayers += layers
        flushedVertices += vertices
        if (
            pass + 1 >= PaintTapFlushMinimumPasses &&
            layers <= PaintTapFlushSmallLayerThreshold &&
            vertices <= PaintTapFlushSmallVertexThreshold
        ) {
            consecutiveSmall++
            if (consecutiveSmall >= PaintTapFlushConsecutiveSmallLimit) {
                Log.i(
                    "MobileSlicerPaintPerf",
                    "stroke_end_flush stop reason=small_tail pass=$pass " +
                        "consecutiveSmall=$consecutiveSmall layers=$flushedLayers vertices=$flushedVertices"
                )
                break
            }
        } else {
            consecutiveSmall = 0
        }
    }
    return flushedLayers to flushedVertices
}

internal fun WorkspacePaintRuntimeController.schedulePaintOverlayConsolidation(mode: ViewerPaintMode?) {
    if (mode == null) return
    paintRuntime.overlayConsolidationJob?.cancel()
    paintRuntime.overlayConsolidationJob = paintCoroutineScope.launch {
        delay(PaintOverlayConsolidationDelayMs)
        if (
            workspaceMode == WorkspaceMode.Paint &&
            activePaintMode == mode &&
            currentNativePaintSessionActive() &&
            !paintRuntime.strokeInProgress &&
            paintRuntime.pendingMove == null &&
            paintRuntime.movePump?.isActive != true &&
            paintRuntime.strokeBeginJob?.isActive != true
        ) {
            val retainedOverlay = currentNativePaintOverlay()
            val retainedLayers = retainedOverlay.layers.size
            val retainedVertices = retainedOverlay.layers.sumOf { it.vertices.size / 3 }
            val recentSnapshot =
                android.os.SystemClock.elapsedRealtime() - paintRuntime.lastOverlayRefreshMs < PaintOverlayRecentSnapshotSkipMs
            val shouldConsolidate =
                paintRuntime.overlayDeltaLayersSinceSnapshot >= PaintOverlayConsolidationLayerThreshold ||
                    paintRuntime.overlayDeltaVerticesSinceSnapshot >= PaintOverlayConsolidationVertexThreshold ||
                    (
                        !recentSnapshot &&
                            retainedLayers >= PaintOverlayRetainedConsolidationLayerThreshold &&
                            retainedLayers > PaintOverlayCompactLayerThreshold
                        )
            if (shouldConsolidate) {
                Log.i(
                    "MobileSlicerPaintPerf",
                    "overlay_consolidate force=true layers=${paintRuntime.overlayDeltaLayersSinceSnapshot} " +
                        "vertices=${paintRuntime.overlayDeltaVerticesSinceSnapshot} " +
                        "retainedLayers=$retainedLayers retainedVertices=$retainedVertices"
                )
                refreshNativePaintOverlay(force = true)
            } else {
                Log.i(
                    "MobileSlicerPaintPerf",
                    "overlay_consolidate skipped layers=${paintRuntime.overlayDeltaLayersSinceSnapshot} " +
                        "vertices=${paintRuntime.overlayDeltaVerticesSinceSnapshot} " +
                        "retainedLayers=$retainedLayers retainedVertices=$retainedVertices recentSnapshot=$recentSnapshot"
                )
            }
        }
    }
}

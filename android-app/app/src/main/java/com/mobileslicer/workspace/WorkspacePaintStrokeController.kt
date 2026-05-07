package com.mobileslicer.workspace

import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.nativebridge.NativePaintCallResult
import com.mobileslicer.nativebridge.NativePaintCalls
import com.mobileslicer.viewer.ViewerPaintOverlay
import com.mobileslicer.viewer.ViewerPaintStrokePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun WorkspacePaintRuntimeController.enqueuePaintMove(point: ViewerPaintStrokePoint) {
    paintRuntime.overlayConsolidationJob?.cancel()
    paintRuntime.pendingMove = point
    if (paintRuntime.movePump?.isActive == true) return
    paintRuntime.movePump = paintCoroutineScope.launch {
        while (true) {
            val nextPoint = paintRuntime.pendingMove ?: break
            paintRuntime.pendingMove = null
            paintRuntime.lastStrokePoint = nextPoint
            val handle = paintNativeHandle ?: break
            paintRuntime.strokeBeginJob?.join()
            if (!paintRuntime.strokeBeginAccepted) break
            if (!currentNativePaintSessionActive()) break
            val ray = nativePaintRayFor(nextPoint.x, nextPoint.y) ?: continue
            val moveStartedAt = android.os.SystemClock.elapsedRealtime()
            val nativeResult = withContext(Dispatchers.Default) {
                val paintStartedAt = android.os.SystemClock.elapsedRealtime()
                val nativeBrushShape = activeNativeBrushShape()
                val result = if (paintRuntime.deferredNativeStrokeBegin) {
                    NativePaintCalls.strokeBeginRay(
                        handle = handle,
                        ray = ray,
                        brushShape = nativeBrushShape,
                        action = activeNativePaintAction(),
                        brushRadiusMm = activeNativeBrushRadiusMm()
                    )
                } else {
                    NativePaintCalls.strokeMoveRay(
                        handle = handle,
                        ray = ray,
                        brushShape = nativeBrushShape,
                        action = activeNativePaintAction(),
                        brushRadiusMm = activeNativeBrushRadiusMm()
                    )
                }
                val paintMs = android.os.SystemClock.elapsedRealtime() - paintStartedAt
                result to "paintMs=$paintMs"
            }
            val result = nativeResult.first
            val moveMs = android.os.SystemClock.elapsedRealtime() - moveStartedAt
            if (moveMs >= 16L || result !is NativePaintCallResult.Success) {
                Log.i("MobileSlicerPaintPerf", "stroke_move result=${result.paintPerfLabel()} ms=$moveMs ${nativeResult.second}")
            }
            when (result) {
                NativePaintCallResult.Success -> {
                    paintRuntime.deferredNativeStrokeBegin = false
                    onNativePaintAvailableChanged(true)
                }
                NativePaintCallResult.Unavailable -> {
                    if (!paintRuntime.deferredNativeStrokeBegin) markNativePaintResult(result)
                    paintRuntime.deferredNativeStrokeBegin = false
                    break
                }
                is NativePaintCallResult.Failure -> {
                    if (paintRuntime.deferredNativeStrokeBegin) {
                        Log.i("MobileSlicerPaintPerf", "stroke_deferred_begin_waiting reason=${result.message}")
                        continue
                    } else {
                        markNativePaintResult(result)
                    }
                    break
                }
            }
            if (PaintMovePumpIntervalMs > 0L) delay(PaintMovePumpIntervalMs)
        }
    }
}

internal fun WorkspacePaintRuntimeController.beginPaintStroke(stroke: ViewerPaintStrokePoint) {
    val handle = paintNativeHandle
    val session = paintSession
    val ray = nativePaintRayFor(stroke.x, stroke.y)
    if (!currentNativePaintSessionActive() || handle == null || session == null) return
    paintRuntime.overlayConsolidationJob?.cancel()
    paintRuntime.overlayRefreshJob?.cancel()
    paintRuntime.overlayRefreshJob = null
    paintRuntime.overlayRefreshPending = false
    paintRuntime.strokeInProgress = true
    paintRuntime.strokeBeginAccepted = false
    paintRuntime.deferredNativeStrokeBegin = false
    paintRuntime.strokeStartedAtMs = android.os.SystemClock.elapsedRealtime()
    paintRuntime.strokeDeltaLayers = 0
    paintRuntime.strokeDeltaVertices = 0
    paintRuntime.strokeDeltaDrainCalls = 0
    paintRuntime.strokeLargestDeltaVertices = 0
    paintRuntime.strokeLargestDeltaLayers = 0
    clearLivePaintOverlay()
    paintRuntime.lastStrokePoint = stroke
    val clippingPlane = activeNativeClippingPlane()
    val colorSlot = paintColorSlotIndex()
    val overlayMode = activePaintMode
    val overlaySlotColors = filamentSlots.associate { slot ->
        slot.index to slotColor(slot.colorHex).toArgb()
    }
    val baseColorSlotIndex = selectedPlateObject?.filamentSlotIndex
    paintRuntime.strokeBeginJob?.cancel()
    paintRuntime.strokeBeginJob = paintCoroutineScope.launch {
        val beginStartedAt = android.os.SystemClock.elapsedRealtime()
        val firstTouchLatencyMs = if (stroke.gestureDownUptimeMs > 0L) {
            android.os.SystemClock.uptimeMillis() - stroke.gestureDownUptimeMs
        } else {
            -1L
        }
        val nativeResult = withContext(Dispatchers.Default) {
            val optionsResult = NativePaintCalls.setToolOptions(
                handle = handle,
                smartFillAngleDeg = paintSmartFillAngleDeg,
                overhangAngleDeg = activeNativeOverhangAngleDeg(session),
                clippingPlane = clippingPlane
            )
            if (optionsResult !is NativePaintCallResult.Success) {
                Triple(optionsResult, ViewerPaintOverlay.Empty, "paintMs=0 deltaNativeMs=0 deltaParseMs=0")
            } else {
                val nativeBrushShape = session.brushShape.toNativePaintBrushShape(session.mode)
                val nativeRadius = nativeBrushRadiusMmFor(session.brushRadiusMm)
                logNativePaintBrushSizing("stroke_begin", session.brushRadiusMm, nativeRadius)
                val toolResult = NativePaintCalls.setTool(
                    handle = handle,
                    brushShape = nativeBrushShape,
                    action = session.action.toNativePaintAction(),
                    brushRadiusMm = nativeRadius,
                    brushHeightMm = nativeBrushHeightMmFor(session.brushHeightMm),
                    colorSlot = colorSlot
                )
                if (toolResult !is NativePaintCallResult.Success) {
                    Triple(toolResult, ViewerPaintOverlay.Empty, "paintMs=0 deltaNativeMs=0 deltaParseMs=0")
                } else if (ray == null && activeStylusPaintOnly) {
                    Triple(NativePaintCallResult.Success, ViewerPaintOverlay.Empty, "paintMs=0 deferredOffModelStylus=true deltaNativeMs=0 deltaParseMs=0")
                } else if (ray == null) {
                    Triple(NativePaintCallResult.Failure("paint stroke ray unavailable"), ViewerPaintOverlay.Empty, "paintMs=0 deltaNativeMs=0 deltaParseMs=0")
                } else {
                    val paintStartedAt = android.os.SystemClock.elapsedRealtime()
                    val result = NativePaintCalls.strokeBeginRay(
                        handle = handle,
                        ray = ray,
                        brushShape = nativeBrushShape,
                        action = session.action.toNativePaintAction(),
                        brushRadiusMm = nativeRadius
                    )
                    val paintMs = android.os.SystemClock.elapsedRealtime() - paintStartedAt
                    var deltaNativeMs = 0L
                    var deltaParseMs = 0L
                    val delta = if (result is NativePaintCallResult.Success && session.brushShape.isTapPaintTool()) {
                        val deltaStartedAt = android.os.SystemClock.elapsedRealtime()
                        val binary = NativePaintCalls.overlayDeltaInterleavedBuffer(handle)
                        deltaNativeMs = android.os.SystemClock.elapsedRealtime() - deltaStartedAt
                        val parseStartedAt = android.os.SystemClock.elapsedRealtime()
                        (if (binary != null) {
                            parseNativePaintOverlayDeltaInterleaved(binary, overlayMode, overlaySlotColors, baseColorSlotIndex)
                        } else {
                            parseNativePaintOverlayDeltaInterleaved(
                                NativePaintCalls.overlayDeltaInterleaved(handle),
                                overlayMode,
                                overlaySlotColors,
                                baseColorSlotIndex
                            )
                        }).also {
                            deltaParseMs = android.os.SystemClock.elapsedRealtime() - parseStartedAt
                        }
                    } else {
                        ViewerPaintOverlay.Empty
                    }
                    Triple(result, delta, "paintMs=$paintMs deltaNativeMs=$deltaNativeMs deltaParseMs=$deltaParseMs")
                }
            }
        }
        val result = nativeResult.first
        val beginVertices = nativeResult.second.layers.sumOf { it.vertices.size / 3 }
        Log.i(
            "MobileSlicerPaintPerf",
            "stroke_begin result=${result.paintPerfLabel()} ms=${android.os.SystemClock.elapsedRealtime() - beginStartedAt} " +
                "firstTouchLatencyMs=$firstTouchLatencyMs vertices=$beginVertices ${nativeResult.third}"
        )
        if (markNativePaintResult(result)) {
            paintRuntime.strokeBeginAccepted = true
            paintRuntime.deferredNativeStrokeBegin = nativeResult.third.contains("deferredOffModelStylus=true")
            onNativePaintAvailableChanged(true)
            appendExactPaintOverlayDelta(nativeResult.second)
            if (session.brushShape.isTapPaintTool()) {
                paintRuntime.overlayDeltaDrainJob?.cancel()
                drainNativePaintOverlayDeltas("tap_begin", PaintTapLiveOverlayDeltaDrainPasses)
            }
        } else {
            paintRuntime.strokeInProgress = false
            paintRuntime.strokeBeginAccepted = false
            paintRuntime.deferredNativeStrokeBegin = false
        }
        paintRuntime.strokeBeginJob = null
    }
}

internal fun WorkspacePaintRuntimeController.commitNativePaintStroke(committed: Boolean) {
    val handle = paintNativeHandle ?: return
    val session = paintSession ?: return
    val finalPoint = paintRuntime.pendingMove ?: paintRuntime.lastStrokePoint
    paintRuntime.pendingMove = null
    val beginJob = paintRuntime.strokeBeginJob
    val activeMovePump = paintRuntime.movePump
    paintCoroutineScope.launch {
        val releaseStartedAtMs = android.os.SystemClock.elapsedRealtime()
        val releaseStrokeId = ++paintRuntime.strokeReleaseSequence
        beginJob?.join()
        activeMovePump?.join()
        if (!paintRuntime.strokeBeginAccepted) {
            paintRuntime.strokeInProgress = false
            paintRuntime.pendingMove = null
            paintRuntime.lastStrokePoint = null
            paintRuntime.deferredNativeStrokeBegin = false
            Log.i("MobileSlicerPaintPerf", "stroke_release_skipped strokeId=$releaseStrokeId reason=begin_not_accepted")
            return@launch
        }
        if (paintRuntime.deferredNativeStrokeBegin) {
            paintRuntime.strokeInProgress = false
            paintRuntime.strokeBeginAccepted = false
            paintRuntime.deferredNativeStrokeBegin = false
            paintRuntime.pendingMove = null
            paintRuntime.lastStrokePoint = null
            clearLivePaintOverlay()
            Log.i("MobileSlicerPaintPerf", "stroke_release_skipped strokeId=$releaseStrokeId reason=deferred_begin_never_hit_model")
            return@launch
        }
        if (finalPoint != null && !session.brushShape.isTapPaintTool()) {
            val finalRay = nativePaintRayFor(finalPoint.x, finalPoint.y)
            if (finalRay != null) {
                val finalMoveStartedAt = android.os.SystemClock.elapsedRealtime()
                val moveResult = withContext(Dispatchers.Default) {
                    NativePaintCalls.strokeMoveRay(
                        handle = handle,
                        ray = finalRay,
                        brushShape = activeNativeBrushShape(),
                        action = activeNativePaintAction(),
                        brushRadiusMm = activeNativeBrushRadiusMm()
                    )
                }
                Log.i("MobileSlicerPaintPerf", "stroke_final_move result=${moveResult.paintPerfLabel()} ms=${android.os.SystemClock.elapsedRealtime() - finalMoveStartedAt}")
                markNativePaintResult(moveResult)
            }
        }
        if (committed) {
            val existingDrainJob = paintRuntime.overlayDeltaDrainJob
            paintRuntime.overlayDeltaDrainJob = null
            existingDrainJob?.cancel()
            existingDrainJob?.join()
            if (session.brushShape.isTapPaintTool()) {
                flushNativePaintOverlayDeltasForStrokeEnd()
                promoteLivePaintOverlay(session.mode)
            }
        }
        val strokeEndStartedAt = android.os.SystemClock.elapsedRealtime()
        val strokeEndResult = withContext(Dispatchers.Default) {
            NativePaintCalls.strokeEnd(handle, commit = committed)
        }
        Log.i("MobileSlicerPaintPerf", "stroke_end committed=$committed result=${strokeEndResult.paintPerfLabel()} ms=${android.os.SystemClock.elapsedRealtime() - strokeEndStartedAt}")
        paintRuntime.strokeInProgress = false
        paintRuntime.strokeBeginAccepted = false
        Log.i(
            "MobileSlicerPaintPerf",
            "stroke_summary committed=$committed mode=${session.mode} shape=${session.brushShape} strokeId=$releaseStrokeId " +
                "durationMs=${android.os.SystemClock.elapsedRealtime() - paintRuntime.strokeStartedAtMs} " +
                "deltaCalls=${paintRuntime.strokeDeltaDrainCalls} deltaLayers=${paintRuntime.strokeDeltaLayers} " +
                "deltaVertices=${paintRuntime.strokeDeltaVertices} largestDeltaLayers=${paintRuntime.strokeLargestDeltaLayers} " +
                "largestDeltaVertices=${paintRuntime.strokeLargestDeltaVertices} retainedLayers=${currentNativePaintOverlay().layers.size} " +
                "retainedVertices=${currentNativePaintOverlay().layers.sumOf { it.vertices.size / 3 }}"
        )
        if (markNativePaintResult(strokeEndResult)) {
            if (!committed) clearLivePaintOverlay()
            refreshNativePaintHistoryState()
            if (committed) {
                paintRuntime.overlayDeltaDrainJob?.cancel()
                paintRuntime.overlayDeltaDrainJob = null
                paintRuntime.payloadDirty = true
                refreshNativePaintOverlay(
                    force = true,
                    preserveCachedOnEmpty = true,
                    initialDelayMs = PaintTapOverlaySnapshotIdleDelayMs,
                    releaseStrokeId = releaseStrokeId,
                    releaseStartedAtMs = releaseStartedAtMs
                )
                schedulePaintOverlayConsolidation(session.mode)
            }
        }
    }
}

internal fun WorkspacePaintRuntimeController.publishNativePaintPayload() {
    val handle = paintNativeHandle ?: return
    val session = paintSession ?: return
    paintCoroutineScope.launch {
        val serializeStartedAt = android.os.SystemClock.elapsedRealtime()
        val payload = withContext(Dispatchers.Default) { NativePaintCalls.serialize(handle) }
        Log.i(
            "MobileSlicerPaintPerf",
            "serialize_payload ms=${android.os.SystemClock.elapsedRealtime() - serializeStartedAt} bytes=${payload?.length ?: 0}"
        )
        if (!payload.isNullOrBlank()) {
            onNativePaintPayloadCommitted(session.selectedObjectId, session.mode, payload)
        }
    }
}

internal fun WorkspacePaintRuntimeController.runNativePaintHistoryCommand(command: (NativeEngineHandle) -> NativePaintCallResult) {
    val handle = paintNativeHandle ?: return
    paintCoroutineScope.launch {
        val result = withContext(Dispatchers.Default) { command(handle) }
        if (markNativePaintResult(result)) {
            clearLivePaintOverlay()
            refreshNativePaintOverlay(force = true)
            refreshNativePaintHistoryState()
            paintRuntime.payloadDirty = true
        }
    }
}

internal fun WorkspacePaintRuntimeController.hitTest(screenX: Float, screenY: Float): Boolean {
    if (!currentNativePaintSessionActive() || paintNativeHandle == null) return false
    val ray = nativePaintRayFor(screenX, screenY)
    return if (ray == null) {
        onNativePaintAvailableChanged(false)
        onNativePaintStatusChanged("Native paint ray unavailable")
        false
    } else {
        onNativePaintAvailableChanged(true)
        true
    }
}

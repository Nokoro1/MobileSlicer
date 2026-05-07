package com.mobileslicer.workspace

import android.util.Log
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.nativebridge.NativePaintCallResult
import com.mobileslicer.nativebridge.NativePaintCalls
import com.mobileslicer.viewer.ViewerPaintMode
import com.mobileslicer.viewer.ViewerPaintOverlay
import com.mobileslicer.viewer.ViewerPaintSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun WorkspacePaintRuntimeController.refreshNativePaintHistoryState() {
    val handle = paintNativeHandle
    if (handle == null || !currentNativePaintSessionActive()) {
        onNativePaintUndoAvailableChanged(false)
        onNativePaintRedoAvailableChanged(false)
        return
    }
    onNativePaintUndoAvailableChanged(NativePaintCalls.canUndo(handle))
    onNativePaintRedoAvailableChanged(NativePaintCalls.canRedo(handle))
}

internal fun WorkspacePaintRuntimeController.nativePaintRayFor(screenX: Float, screenY: Float): FloatArray? {
    val selectedId = selectedPlateObject?.id ?: return null
    val viewerRay = currentViewerView()?.paintRayForObject(screenX, screenY, selectedId)?.values ?: return null
    val viewerBounds = selectedPlateObject.mesh?.bounds
    val nativeBounds = currentNativePaintSourceBounds()
    return if (viewerBounds != null && nativeBounds != null) {
        remapPaintRayBetweenSourceBounds(viewerRay, viewerBounds, nativeBounds)
    } else {
        viewerRay
    }
}

internal fun WorkspacePaintRuntimeController.activeNativeBrushShape(): com.mobileslicer.nativebridge.NativePaintBrushShape {
    val session = paintSession
    val mode = session?.mode ?: activePaintMode ?: ViewerPaintMode.Support
    val shape = session?.brushShape ?: paintBrushShape
    return shape.toNativePaintBrushShape(mode)
}

internal fun WorkspacePaintRuntimeController.activeNativePaintAction(): com.mobileslicer.nativebridge.NativePaintAction =
    (paintSession?.action ?: paintAction).toNativePaintAction()

internal fun WorkspacePaintRuntimeController.activeNativeModelUniformScale(): Float =
    selectedPlateObject?.transform?.uniformScale ?: modelTransform?.uniformScale ?: 1f

internal fun WorkspacePaintRuntimeController.nativeBrushRadiusMmFor(displayRadiusMm: Float): Float =
    nativePaintBrushRadiusMm(displayRadiusMm, activeNativeModelUniformScale())

internal fun WorkspacePaintRuntimeController.nativeBrushHeightMmFor(displayHeightMm: Float): Float =
    nativePaintBrushHeightMm(displayHeightMm)

internal fun WorkspacePaintRuntimeController.activeNativeBrushRadiusMm(): Float =
    nativeBrushRadiusMmFor(paintSession?.brushRadiusMm ?: paintBrushRadiusMm)

internal fun WorkspacePaintRuntimeController.logNativePaintBrushSizing(context: String, displayRadiusMm: Float, nativeRadiusMm: Float) {
    val viewerBounds = selectedPlateObject?.mesh?.bounds
    val sourceBounds = currentNativePaintSourceBounds()
    val scale = activeNativeModelUniformScale()
    Log.i(
        "MobileSlicerPaintPerf",
        "brush_sizing context=$context uiRadiusMm=${effectivePaintBrushRadiusMm(displayRadiusMm)} " +
            "nativeRadiusMm=$nativeRadiusMm modelScale=$scale " +
            "viewerBounds=${viewerBounds?.paintSizingSummary().orEmpty()} " +
            "nativeBounds=${sourceBounds?.paintSizingSummary().orEmpty()}"
    )
}

internal fun WorkspacePaintRuntimeController.activeNativeOverhangAngleDeg(session: ViewerPaintSession?): Float =
    if (session?.mode == ViewerPaintMode.Support) paintOverhangAngleDeg else 0f

internal fun WorkspacePaintRuntimeController.activeNativeClippingPlane(): FloatArray? {
    if (!paintClippingEnabled) return null
    val bounds = currentNativePaintSourceBounds() ?: selectedPlateObject?.bounds
        ?: return floatArrayOf(0f, 0f, 1f, Float.MAX_VALUE)
    val z = bounds.minZ + (bounds.maxZ - bounds.minZ) * paintSectionViewPosition.coerceIn(0f, 1f)
    return floatArrayOf(0f, 0f, 1f, z + 0.001f)
}

internal fun WorkspacePaintRuntimeController.configureNativePaintTool(session: ViewerPaintSession? = paintSession): Boolean {
    val activeSession = session ?: return false
    val handle = paintNativeHandle ?: return false
    val optionsResult = NativePaintCalls.setToolOptions(
        handle = handle,
        smartFillAngleDeg = paintSmartFillAngleDeg,
        overhangAngleDeg = activeNativeOverhangAngleDeg(activeSession),
        clippingPlane = activeNativeClippingPlane()
    )
    if (optionsResult !is NativePaintCallResult.Success) {
        return markNativePaintResult(optionsResult)
    }
    val nativeBrushShape = activeSession.brushShape.toNativePaintBrushShape(activeSession.mode)
    val nativeRadius = nativeBrushRadiusMmFor(activeSession.brushRadiusMm)
    logNativePaintBrushSizing("configure_tool", activeSession.brushRadiusMm, nativeRadius)
    val result = NativePaintCalls.setTool(
        handle = handle,
        brushShape = nativeBrushShape,
        action = activeSession.action.toNativePaintAction(),
        brushRadiusMm = nativeRadius,
        brushHeightMm = nativeBrushHeightMmFor(activeSession.brushHeightMm),
        colorSlot = paintColorSlotIndex()
    )
    return markNativePaintResult(result)
}

internal fun WorkspacePaintRuntimeController.markNativePaintResult(result: NativePaintCallResult): Boolean =
    when (result) {
        NativePaintCallResult.Success -> {
            onNativePaintAvailableChanged(true)
            onNativePaintStatusChanged(null)
            true
        }
        NativePaintCallResult.Unavailable -> {
            onNativePaintAvailableChanged(false)
            onNativePaintStatusChanged("Native paint API unavailable")
            false
        }
        is NativePaintCallResult.Failure -> {
            onNativePaintStatusChanged(result.message)
            false
        }
    }

internal suspend fun WorkspacePaintRuntimeController.prepareNativeSessionForCurrentSelection() {
    val requestedMode = activePaintMode
    onNativePaintSessionActiveChanged(false)
    onNativePaintUndoAvailableChanged(false)
    onNativePaintRedoAvailableChanged(false)
    val resetOverlay = if (workspaceMode == WorkspaceMode.Paint) {
        requestedMode?.let { paintModeOverlayCache[it] } ?: ViewerPaintOverlay.Empty
    } else {
        ViewerPaintOverlay.Empty
    }
    onNativePaintOverlayChanged(resetOverlay)
    onNativePaintSourceBoundsChanged(null)
    preparedNativePaintSourceBounds = null
    livePaintOverlayRef[0] = ViewerPaintOverlay.Empty
    currentViewerView()?.setPaintOverlay(resetOverlay)
    paintRuntime.overlayDeltaLayersSinceSnapshot = 0
    paintRuntime.overlayDeltaVerticesSinceSnapshot = 0
    paintRuntime.payloadDirty = false
    paintRuntime.pendingMove = null
    paintRuntime.strokeBeginJob?.cancel()
    paintRuntime.strokeBeginJob = null
    paintRuntime.strokeInProgress = false
    paintRuntime.strokeBeginAccepted = false
    paintRuntime.overlayRefreshJob?.cancel()
    paintRuntime.overlayRefreshJob = null
    paintRuntime.overlayRefreshPending = false
    paintRuntime.lastStrokePoint = null
    if (workspaceMode != WorkspaceMode.Paint) {
        onNativePaintStatusChanged(null)
        onNativePaintAvailableChanged(null)
        return
    }
    val selected = selectedPlateObject ?: return
    val mode = requestedMode ?: return
    val handle = paintNativeHandle ?: return
    onNativePaintStatusChanged("Preparing native paint session")
    val prepared = withContext(Dispatchers.Default) {
        onPrepareNativePaintSessionRequested(plateObjects.toList(), selectedPrinter.toBedSpec())
    }
    if (!prepared) {
        onNativePaintAvailableChanged(false)
        onNativePaintStatusChanged("Native paint setup failed: selected object is not bound in the native plate model")
        return
    }
    val beginResult = withContext(Dispatchers.Default) {
        NativePaintCalls.beginSession(handle, selected.id, mode.toNativePaintMode())
    }
    val active = markNativePaintResult(beginResult)
    onNativePaintSessionActiveChanged(active)
    if (active) {
        preparedNativePaintSourceBounds = nativePaintSourceBoundsFor(handle, selected.id)
        onNativePaintSourceBoundsChanged(preparedNativePaintSourceBounds)
        configureNativePaintTool()
        refreshNativePaintOverlay(force = true, preserveCachedOnEmpty = true)
        refreshNativePaintHistoryState()
    }
}

internal fun WorkspacePaintRuntimeController.enterPaintMode(mode: ViewerPaintMode, paintModePrepareInProgress: Boolean) {
    val selected = selectedPlateObject ?: plateObjects.firstOrNull() ?: run {
        onNativePaintStatusChanged("Select an object before painting")
        Log.w("MobileSlicerPaintPerf", "paint_entry rejected reason=no_selected_object mode=$mode")
        return
    }
    if (paintModePrepareInProgress) return
    val objectsForBinding = plateObjects.toList()
    val bedForBinding = selectedPrinter.toBedSpec()
    val previousHandle = paintNativeHandle
    val previousSession = paintSession
    val previousPendingPoint = paintRuntime.pendingMove ?: paintRuntime.lastStrokePoint
    val previousBeginJob = paintRuntime.strokeBeginJob
    val previousMovePump = paintRuntime.movePump
    onPaintModePrepareInProgressChanged(true)
    onNativePaintStatusChanged("Preparing native paint session")
    paintCoroutineScope.launch {
        try {
            Log.i("MobileSlicerPaintPerf", "paint_entry_start mode=$mode selectedObjectId=${selected.id}")
            if (
                workspaceMode == WorkspaceMode.Paint &&
                previousHandle != null &&
                previousSession != null &&
                currentNativePaintSessionActive()
            ) {
                previousBeginJob?.join()
                previousMovePump?.join()
                paintRuntime.overlayConsolidationJob?.cancel()
                previousPendingPoint?.let { point ->
                    nativePaintRayFor(point.x, point.y)?.let { ray ->
                        withContext(Dispatchers.Default) {
                            NativePaintCalls.strokeMoveRay(
                                handle = previousHandle,
                                ray = ray,
                                brushShape = previousSession.brushShape.toNativePaintBrushShape(previousSession.mode),
                                action = previousSession.action.toNativePaintAction(),
                                brushRadiusMm = nativeBrushRadiusMmFor(previousSession.brushRadiusMm)
                            )
                        }
                    }
                }
                promoteLivePaintOverlay(previousSession.mode)
                val payload = withContext(Dispatchers.Default) {
                    NativePaintCalls.strokeEnd(previousHandle, commit = true)
                    NativePaintCalls.serialize(previousHandle)
                }
                if (!payload.isNullOrBlank()) {
                    onNativePaintPayloadCommitted(previousSession.selectedObjectId, previousSession.mode, payload)
                }
                withContext(Dispatchers.Default) {
                    NativePaintCalls.endSession(previousHandle, commit = true)
                }
                paintRuntime.pendingMove = null
                paintRuntime.movePump = null
                paintRuntime.strokeInProgress = false
                paintRuntime.payloadDirty = false
            }
            val prepared = withContext(Dispatchers.Default) {
                onPrepareNativePaintSessionRequested(objectsForBinding, bedForBinding)
            }
            if (!prepared) {
                onNativePaintAvailableChanged(false)
                onNativePaintStatusChanged("Native paint setup failed: selected object is not bound in the native plate model")
                Log.w("MobileSlicerPaintPerf", "paint_entry_failed mode=$mode reason=prepare_failed selectedObjectId=${selected.id}")
                return@launch
            }
            onPlateObjectSelected(selected.id)
            onActivePaintModeChanged(mode)
            val defaultTool = defaultPaintToolStateForMode(mode)
            onPaintBrushShapeChanged(defaultTool.brushShape)
            onPaintActionChanged(defaultTool.action)
            if (mode == ViewerPaintMode.Color && activePaintColorSlotIndex == null) {
                onActivePaintColorSlotIndexChanged(filamentSlots.firstOrNull()?.index)
            }
            Log.i("MobileSlicerPaintPerf", "paint_entry_ready mode=$mode selectedObjectId=${selected.id}")
            onWorkspaceModeChanged(WorkspaceMode.Paint)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            onNativePaintAvailableChanged(false)
            onNativePaintStatusChanged("Native paint setup failed: ${throwable.message ?: throwable.javaClass.simpleName}")
            Log.e("MobileSlicerPaintPerf", "paint_entry_failed mode=$mode selectedObjectId=${selected.id}", throwable)
        } finally {
            onPaintModePrepareInProgressChanged(false)
        }
    }
}

internal fun WorkspacePaintRuntimeController.exitPaintMode() {
    paintRuntime.overlayConsolidationJob?.cancel()
    paintRuntime.overlayDeltaDrainJob?.cancel()
    paintRuntime.strokeInProgress = false
    paintRuntime.strokeBeginAccepted = false
    val handle = paintNativeHandle
    val session = paintSession
    if (handle != null && session != null) {
        val finalPoint = paintRuntime.pendingMove ?: paintRuntime.lastStrokePoint
        val finalRay = if (session.brushShape.isTapPaintTool()) {
            null
        } else {
            finalPoint?.let { nativePaintRayFor(it.x, it.y) }
        }
        paintRuntime.pendingMove = null
        val activeMovePump = paintRuntime.movePump
        paintCoroutineScope.launch {
            activeMovePump?.join()
            if (finalRay != null) {
                withContext(Dispatchers.Default) {
                    NativePaintCalls.strokeMoveRay(
                        handle = handle,
                        ray = finalRay,
                        brushShape = session.brushShape.toNativePaintBrushShape(session.mode),
                        action = session.action.toNativePaintAction(),
                        brushRadiusMm = nativeBrushRadiusMmFor(session.brushRadiusMm)
                    )
                }
            }
            promoteLivePaintOverlay(session.mode)
            val payload = withContext(Dispatchers.Default) {
                NativePaintCalls.strokeEnd(handle, commit = true)
                NativePaintCalls.serialize(handle)
            }
            if (!payload.isNullOrBlank()) {
                onNativePaintPayloadCommitted(session.selectedObjectId, session.mode, payload)
            }
            withContext(Dispatchers.Default) {
                NativePaintCalls.endSession(handle, commit = true)
            }
            if (paintRuntime.movePump == activeMovePump) {
                paintRuntime.movePump = null
            }
            paintRuntime.strokeBeginAccepted = false
        }
    }
    onActivePaintModeChanged(null)
    onWorkspaceModeChanged(WorkspaceMode.Prepare)
}

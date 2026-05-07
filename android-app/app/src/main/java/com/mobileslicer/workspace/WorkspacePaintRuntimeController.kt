package com.mobileslicer.workspace

import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.nativebridge.NativePaintCallResult
import com.mobileslicer.nativebridge.NativePaintCalls
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.TouchModelViewerView
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.ViewerPaintAction
import com.mobileslicer.viewer.ViewerPaintBrushShape
import com.mobileslicer.viewer.ViewerPaintMode
import com.mobileslicer.viewer.ViewerPaintOverlay
import com.mobileslicer.viewer.ViewerPaintSession
import com.mobileslicer.viewer.ViewerPaintStrokePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal class WorkspacePaintRuntimeController(
    internal val paintRuntime: PaintRuntimeState,
    internal val paintCoroutineScope: CoroutineScope,
    internal val paintNativeHandle: NativeEngineHandle?,
    internal val paintSession: ViewerPaintSession?,
    internal val activePaintMode: ViewerPaintMode?,
    internal val paintBrushShape: ViewerPaintBrushShape,
    internal val paintBrushRadiusMm: Float,
    internal val paintSmartFillAngleDeg: Float,
    internal val paintOverhangAngleDeg: Float,
    internal val paintClippingEnabled: Boolean,
    internal val paintSectionViewPosition: Float,
    internal val paintAction: ViewerPaintAction,
    internal val activePaintColorSlotIndex: Int?,
    internal val nativePaintSessionActive: Boolean,
    internal val nativePaintOverlay: ViewerPaintOverlay,
    internal val nativePaintSourceBounds: MeshBounds?,
    internal val nativePaintOverlayProvider: () -> ViewerPaintOverlay,
    internal val nativePaintSourceBoundsProvider: () -> MeshBounds?,
    internal val nativePaintSessionActiveProvider: () -> Boolean,
    internal val paintModeOverlayCache: MutableMap<ViewerPaintMode, ViewerPaintOverlay>,
    internal val livePaintOverlayRef: Array<ViewerPaintOverlay>,
    internal val selectedPlateObject: PlateObject?,
    internal val plateObjects: List<PlateObject>,
    internal val filamentSlots: List<PlateFilamentSlot>,
    internal val selectedPrinter: PrinterProfile,
    internal val modelTransform: ViewerModelTransform?,
    internal val workspaceMode: WorkspaceMode,
    internal val activeStylusPaintOnly: Boolean,
    internal val viewerView: TouchModelViewerView?,
    internal val viewerViewProvider: () -> TouchModelViewerView?,
    internal val onNativePaintSessionActiveChanged: (Boolean) -> Unit,
    internal val onNativePaintAvailableChanged: (Boolean?) -> Unit,
    internal val onNativePaintStatusChanged: (String?) -> Unit,
    internal val onNativePaintOverlayChanged: (ViewerPaintOverlay) -> Unit,
    internal val onNativePaintSourceBoundsChanged: (MeshBounds?) -> Unit,
    internal val onNativePaintUndoAvailableChanged: (Boolean) -> Unit,
    internal val onNativePaintRedoAvailableChanged: (Boolean) -> Unit,
    internal val onPaintModePrepareInProgressChanged: (Boolean) -> Unit,
    internal val onActivePaintModeChanged: (ViewerPaintMode?) -> Unit,
    internal val onPaintBrushShapeChanged: (ViewerPaintBrushShape) -> Unit,
    internal val onPaintActionChanged: (ViewerPaintAction) -> Unit,
    internal val onActivePaintColorSlotIndexChanged: (Int?) -> Unit,
    internal val onPrepareNativePaintSessionRequested: (List<PlateObject>, com.mobileslicer.viewer.PrinterBedSpec) -> Boolean,
    internal val onNativePaintPayloadCommitted: (Long, ViewerPaintMode, String) -> Unit,
    internal val onPlateObjectSelected: (Long?) -> Unit,
    internal val onWorkspaceModeChanged: (WorkspaceMode) -> Unit
) {
    internal var preparedNativePaintSourceBounds: MeshBounds? = nativePaintSourceBounds

    internal fun currentNativePaintOverlay(): ViewerPaintOverlay =
        nativePaintOverlayProvider()

    internal fun currentNativePaintSourceBounds(): MeshBounds? =
        preparedNativePaintSourceBounds ?: nativePaintSourceBoundsProvider()

    internal fun currentNativePaintSessionActive(): Boolean =
        nativePaintSessionActiveProvider()

    internal fun currentViewerView(): TouchModelViewerView? =
        viewerViewProvider() ?: viewerView

    fun paintColorSlotIndex(): Int =
        activePaintColorSlotIndex?.takeIf { slotIndex ->
            filamentSlots.any { it.index == slotIndex }
        } ?: 0

    fun nativePaintDragOverlaySnapshotAllowed(): Boolean {
        val triangleCount = selectedPlateObject?.mesh?.triangleCount ?: return true
        return triangleCount <= PaintDragOverlaySnapshotTriangleLimit
    }

    fun currentPaintDisplayOverlay(): ViewerPaintOverlay =
        currentNativePaintOverlay().plusReplacing(livePaintOverlayRef[0])

}

internal fun NativePaintCallResult.paintPerfLabel(): String =
    when (this) {
        NativePaintCallResult.Success -> "success"
        NativePaintCallResult.Unavailable -> "unavailable"
        is NativePaintCallResult.Failure -> "failure:${message}"
    }

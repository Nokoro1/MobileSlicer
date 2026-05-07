package com.mobileslicer.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.viewer.ViewerPaintSession

@Composable
internal fun WorkspaceHeaderChrome(
    paintModeActive: Boolean,
    paintSession: ViewerPaintSession?,
    paintObjectLabel: String,
    nativePaintSessionActive: Boolean,
    nativePaintAvailable: Boolean?,
    nativePaintUndoAvailable: Boolean,
    nativePaintRedoAvailable: Boolean,
    selectedPrinter: PrinterProfile,
    workspaceMode: WorkspaceMode,
    hasGeneratedGcode: Boolean,
    transformReady: Boolean,
    sliceInProgress: Boolean,
    selectedPlateObject: PlateObject?,
    canSendToPrinter: Boolean,
    sendToPrinterInProgress: Boolean,
    printerStatusMessage: String?,
    sliceSummary: SliceResultSummary?,
    onBack: () -> Unit,
    onPaintBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTransformClick: () -> Unit,
    onDeleteObject: () -> Unit,
    onCloneObject: () -> Unit,
    onAddObject: () -> Unit,
    onOpenSendMenu: () -> Unit,
    onOpenPrinter: () -> Unit,
    onOpenPreviewInfo: () -> Unit,
    onWorkspaceModeChanged: (WorkspaceMode) -> Unit,
    modifier: Modifier = Modifier
) {
    if (paintModeActive && paintSession != null) {
        WorkspacePaintTopBar(
            mode = paintSession.mode,
            objectLabel = paintObjectLabel,
            undoEnabled = nativePaintSessionActive && nativePaintAvailable == true && nativePaintUndoAvailable,
            redoEnabled = nativePaintSessionActive && nativePaintAvailable == true && nativePaintRedoAvailable,
            onBack = onPaintBack,
            onUndo = onUndo,
            onRedo = onRedo,
            modifier = modifier
        )
    } else {
        WorkspaceTopBar(
            onBack = onBack,
            printerTitle = selectedPrinter.name,
            printerBed = selectedPrinter.toBedSpec(),
            workspaceMode = workspaceMode,
            previewEnabled = hasGeneratedGcode,
            transformEnabled = workspaceMode == WorkspaceMode.Prepare && transformReady && !sliceInProgress,
            deleteEnabled = workspaceMode == WorkspaceMode.Prepare && selectedPlateObject != null && !sliceInProgress,
            cloneEnabled = workspaceMode == WorkspaceMode.Prepare && selectedPlateObject != null && !sliceInProgress,
            canSendToPrinter = canSendToPrinter,
            sendToPrinterInProgress = sendToPrinterInProgress,
            printerStatusLabel = compactPrinterStatusLabel(printerStatusMessage),
            showPreviewInfo = workspaceMode == WorkspaceMode.Preview && sliceSummary != null && !sliceInProgress,
            onTransformClick = onTransformClick,
            onDeleteObject = onDeleteObject,
            onCloneObject = onCloneObject,
            onAddObject = onAddObject,
            onOpenSendMenu = onOpenSendMenu,
            onOpenPrinter = onOpenPrinter,
            onOpenPreviewInfo = onOpenPreviewInfo,
            onModeClick = {
                val nextMode = if (workspaceMode == WorkspaceMode.Prepare) {
                    WorkspaceMode.Preview
                } else {
                    WorkspaceMode.Prepare
                }
                onWorkspaceModeChanged(nextMode)
            },
            modifier = modifier
        )
    }
}

internal fun paintHeaderLabel(nativePaintStatus: String?, objectLabel: String): String =
    when {
        nativePaintStatus.isNullOrBlank() -> objectLabel
        nativePaintStatus == "no active paint session" -> objectLabel
        else -> nativePaintStatus
    }

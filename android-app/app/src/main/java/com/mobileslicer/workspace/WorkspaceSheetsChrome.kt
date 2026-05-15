package com.mobileslicer.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobileslicer.profiles.FilamentProfile
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.printerconnection.BambuLanPrintOptions
import com.mobileslicer.printerconnection.PrinterUploadAction
import com.mobileslicer.viewer.GcodePreviewDisplayMode
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.ViewerPaintMode

@Composable
internal fun BoxScope.WorkspaceSheetsChrome(
    workspaceMode: WorkspaceMode,
    showTransformSheet: Boolean,
    activeTransformTab: TransformToolTab,
    showPrinterSendSheet: Boolean,
    showPreviewInfoSheet: Boolean,
    showObjectListSheet: Boolean,
    showPlateSettingsSheet: Boolean,
    activePlateLabel: String,
    workspacePlates: List<WorkspacePlate>,
    activePlateId: Long,
    selectedFilamentSlotSheetIndex: Int?,
    missingPrinterConnectionDialog: Boolean,
    isLandscape: Boolean,
    selectedPrinter: PrinterProfile,
    effectiveModelTransform: ViewerModelTransform,
    sendToPrinterInProgress: Boolean,
    currentGcodeFileName: String,
    sliceSummary: SliceResultSummary?,
    sliceInProgress: Boolean,
    platePlanningInProgress: Boolean,
    previewPathVisibility: Map<String, Boolean>,
    previewDisplayMode: GcodePreviewDisplayMode,
    plateObjects: List<PlateObject>,
    filamentSlots: List<PlateFilamentSlot>,
    selectedPlateObject: PlateObject?,
    availableFilaments: List<FilamentProfile>,
    onTransformSheetDismiss: () -> Unit,
    onTransformTabChanged: (TransformToolTab) -> Unit,
    onTransformChanged: (ViewerModelTransform?) -> Unit,
    onAutoOrientObjects: () -> Unit,
    onAutoArrangeObjects: (Boolean) -> Unit,
    onLayFaceRequested: () -> Unit,
    onPaintModeSelected: (ViewerPaintMode) -> Unit,
    cutOffsetOverride: Float?,
    onCutPreviewChanged: (WorkspaceCutRequest?) -> Unit,
    onCutRequested: (WorkspaceCutRequest) -> Unit,
    onSendToPrinter: (PrinterUploadAction, String, BambuLanPrintOptions?) -> Unit,
    onPrinterSendDismiss: () -> Unit,
    onPreviewDisplayModeChanged: (GcodePreviewDisplayMode) -> Unit,
    onPreviewLineVisibilityChanged: (PreviewLineTypeRow, Boolean) -> Unit,
    onPreviewInfoDismiss: () -> Unit,
    onObjectSelected: (Long?) -> Unit,
    onObjectProcessSelected: (Long) -> Unit,
    onAddModifierToObject: (Long) -> Unit,
    onModifierProcessSelected: (Long, Long) -> Unit,
    onToggleModifier: (Long, Long, Boolean) -> Unit,
    onDeleteModifier: (Long, Long) -> Unit,
    onCenterModifierOnObject: (Long, Long) -> Unit,
    onRotateModifier: (Long, Long) -> Unit,
    onModifierTransformChanged: (Long, Long, ViewerModelTransform) -> Unit,
    onObjectListDismiss: () -> Unit,
    onPlateSettingsDismiss: () -> Unit,
    onActivePlateChanged: (Long) -> Unit,
    onAddPlate: () -> Unit,
    onDuplicateActivePlate: () -> Unit,
    onDeleteActivePlate: () -> Unit,
    onRenameActivePlate: (String) -> Unit,
    onMoveObjectToPlate: (Long, Long) -> Unit,
    onMoveObjectToNewPlate: (Long) -> Unit,
    onFilamentSheetDismiss: () -> Unit,
    onAssignFilamentSlotToSelected: (Int) -> Unit,
    onUpdateFilamentSlotColor: (Int, String) -> Unit,
    onUpdateFilamentSlotProfile: (Int, FilamentProfile) -> Unit,
    onUpdateFilamentSlotNozzle: (Int, Int?) -> Unit,
    onRemoveFilamentSlot: (Int) -> Unit,
    onMissingPrinterConnectionDismiss: () -> Unit
) {
    if (showTransformSheet && workspaceMode == WorkspaceMode.Prepare) {
        TransformPopoverContent(
            printerBed = selectedPrinter.toBedSpec(),
            transform = effectiveModelTransform,
            selectedTab = activeTransformTab,
            onSelectedTabChanged = onTransformTabChanged,
            onTransformChanged = onTransformChanged,
            onAutoOrientObjects = onAutoOrientObjects,
            onAutoArrangeObjects = onAutoArrangeObjects,
            platePlanningInProgress = platePlanningInProgress,
            onLayFaceRequested = onLayFaceRequested,
            onPaintModeSelected = onPaintModeSelected,
            cutBounds = selectedPlateObject?.mesh?.bounds ?: selectedPlateObject?.bounds,
            cutOffsetOverride = cutOffsetOverride,
            onCutPreviewChanged = {},
            onCutRequested = {},
            showControls = false,
            compactLayout = isLandscape,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    top = if (isLandscape) 58.dp else 64.dp,
                    start = if (isLandscape) 42.dp else 24.dp,
                    end = if (isLandscape) 42.dp else 24.dp
                )
                .widthIn(max = if (isLandscape) 760.dp else 840.dp)
        )
    }
    if (showPrinterSendSheet && workspaceMode == WorkspaceMode.Preview) {
        PrinterSendSheet(
            sending = sendToPrinterInProgress,
            suggestedFileName = currentGcodeFileName,
            printerProfile = selectedPrinter,
            onUpload = { remoteFileName, bambuOptions ->
                onSendToPrinter(PrinterUploadAction.UploadOnly, remoteFileName, bambuOptions)
            },
            onUploadAndStart = { remoteFileName, bambuOptions ->
                onSendToPrinter(PrinterUploadAction.UploadAndStart, remoteFileName, bambuOptions)
            },
            onQueue = { remoteFileName, bambuOptions ->
                onSendToPrinter(PrinterUploadAction.Queue, remoteFileName, bambuOptions)
            },
            onDismiss = onPrinterSendDismiss
        )
    }
    if (showPreviewInfoSheet && workspaceMode == WorkspaceMode.Preview && sliceSummary != null && !sliceInProgress) {
        PreviewInfoSheet(
            summary = sliceSummary,
            lineVisibility = previewPathVisibility,
            displayMode = previewDisplayMode,
            onDisplayModeChanged = onPreviewDisplayModeChanged,
            onLineVisibilityChanged = onPreviewLineVisibilityChanged,
            onDismiss = onPreviewInfoDismiss
        )
    }
    if (showObjectListSheet && workspaceMode == WorkspaceMode.Prepare && plateObjects.isNotEmpty()) {
        PlateObjectListSheet(
            plateObjects = plateObjects,
            filamentSlots = filamentSlots,
            workspacePlates = workspacePlates,
            activePlateId = activePlateId,
            selectedPlateObjectId = selectedPlateObject?.id,
            onObjectSelected = onObjectSelected,
            onObjectProcessSelected = onObjectProcessSelected,
            onAddModifierToObject = onAddModifierToObject,
            onModifierProcessSelected = onModifierProcessSelected,
            onToggleModifier = onToggleModifier,
            onDeleteModifier = onDeleteModifier,
            onCenterModifierOnObject = onCenterModifierOnObject,
            onRotateModifier = onRotateModifier,
            onModifierTransformChanged = onModifierTransformChanged,
            onMoveObjectToPlate = onMoveObjectToPlate,
            onMoveObjectToNewPlate = onMoveObjectToNewPlate,
            onDismiss = onObjectListDismiss
        )
    }
    if (showPlateSettingsSheet && workspaceMode == WorkspaceMode.Prepare) {
        WorkspacePlateSheet(
            plateLabel = activePlateLabel,
            plates = workspacePlates,
            activePlateId = activePlateId,
            objectCount = plateObjects.size,
            selectedObjectId = selectedPlateObject?.id,
            selectedObjectLabel = selectedPlateObject?.label,
            onDismiss = onPlateSettingsDismiss,
            onPlateSelected = onActivePlateChanged,
            onAddPlate = onAddPlate,
            onDuplicateActivePlate = onDuplicateActivePlate,
            onDeleteActivePlate = onDeleteActivePlate,
            onRenameActivePlate = onRenameActivePlate,
            onMoveObjectToPlate = onMoveObjectToPlate,
            onMoveObjectToNewPlate = onMoveObjectToNewPlate
        )
    }
    if (selectedFilamentSlotSheetIndex != null && isLandscape) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .noRippleTap { onFilamentSheetDismiss() }
        )
    }
    selectedFilamentSlotSheetIndex?.let { slotIndex ->
        val slot = filamentSlots.firstOrNull { it.index == slotIndex }
        if (slot != null && workspaceMode == WorkspaceMode.Prepare) {
            FilamentSlotSheet(
                slot = slot,
                selectedObjectLabel = selectedPlateObject?.label,
                availableFilaments = availableFilaments,
                physicalNozzleCount = selectedPrinter.physicalNozzleCount(),
                landscapeLayout = isLandscape,
                modifier = if (isLandscape) {
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                } else {
                    Modifier
                },
                onAssignToSelected = {
                    onAssignFilamentSlotToSelected(slot.index)
                },
                onColorSelected = { colorHex ->
                    onUpdateFilamentSlotColor(slot.index, colorHex)
                },
                onFilamentSelected = { filament ->
                    onUpdateFilamentSlotProfile(slot.index, filament)
                },
                onNozzleSelected = { physicalNozzleIndex ->
                    onUpdateFilamentSlotNozzle(slot.index, physicalNozzleIndex)
                },
                onRemoveSlot = {
                    onRemoveFilamentSlot(slot.index)
                },
                onDismiss = onFilamentSheetDismiss
            )
        }
    }
    if (missingPrinterConnectionDialog) {
        AlertDialog(
            onDismissRequest = onMissingPrinterConnectionDismiss,
            confirmButton = {
                TextButton(onClick = onMissingPrinterConnectionDismiss) {
                    Text("OK")
                }
            },
            title = { Text("Printer connection") },
            text = {
                Text("Set up a printer connection in Profiles > Printer > Connection first.")
            }
        )
    }
}

package com.mobileslicer.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.mobileslicer.appBodyColor
import com.mobileslicer.profiles.ActiveSlicerConfiguration
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.viewer.GcodePreviewPerformanceMode
import com.mobileslicer.viewer.PreviewRangeSuggestion
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.ViewerPaintAction
import com.mobileslicer.viewer.ViewerPaintBrushShape
import com.mobileslicer.viewer.ViewerPaintMode
import com.mobileslicer.viewer.ViewerPaintSession
import java.util.Locale

@Composable
internal fun WorkspaceBottomChrome(
    paintModeActive: Boolean,
    paintSession: ViewerPaintSession?,
    isLandscape: Boolean,
    activePaintMode: ViewerPaintMode?,
    paintControlsExpanded: Boolean,
    paintBrushShape: ViewerPaintBrushShape,
    paintBrushRadiusMm: Float,
    paintSmartFillAngleDeg: Float,
    paintOverhangAngleDeg: Float,
    paintClippingEnabled: Boolean,
    paintSectionViewPosition: Float,
    paintAction: ViewerPaintAction,
    filamentSlots: List<PlateFilamentSlot>,
    activePaintColorSlotIndex: Int?,
    nativePaintSessionActive: Boolean,
    nativePaintAvailable: Boolean?,
    workspaceMode: WorkspaceMode,
    selectedPlateObject: PlateObject?,
    modelLabel: String,
    selectedPrinter: PrinterProfile,
    modelFormat: ImportedModelFormat?,
    effectiveViewerState: WorkspaceViewerState,
    meshSummary: String?,
    importTiming: ModelImportTiming?,
    workspacePreparationTiming: WorkspacePreparationTiming?,
    firstVisibleWorkspaceFrameMs: Long?,
    firstVisiblePreviewFrameMs: Long?,
    activeConfiguration: ActiveSlicerConfiguration,
    workspaceStatus: String,
    sliceInProgress: Boolean,
    sendToPrinterInProgress: Boolean,
    sliceReady: Boolean,
    hasGeneratedGcode: Boolean,
    canSendToPrinter: Boolean,
    sliceSummary: SliceResultSummary?,
    sliceTiming: SlicePipelineTiming?,
    previewLayerCount: Int,
    previewLayerSelection: PreviewLayerSelection,
    maxRangeLayerSpan: Int,
    activePreviewChunkBounds: IntRange?,
    autoPreviewRanges: List<PreviewRangeSuggestion>,
    autoPreviewRangeIndex: Int,
    printerStatusMessage: String?,
    workspaceControlsExpanded: Boolean,
    effectiveModelTransform: ViewerModelTransform,
    objectCount: Int,
    activePlateLabel: String,
    onPaintControlsExpandedChange: (Boolean) -> Unit,
    onBrushShapeChanged: (ViewerPaintBrushShape) -> Unit,
    onBrushRadiusChanged: (Float) -> Unit,
    onSmartFillAngleChanged: (Float) -> Unit,
    onOverhangAngleChanged: (Float) -> Unit,
    onClippingEnabledChanged: (Boolean) -> Unit,
    onSectionViewPositionChanged: (Float) -> Unit,
    onActionChanged: (ViewerPaintAction) -> Unit,
    onColorSlotSelected: (Int?) -> Unit,
    onUnsupportedOption: (String) -> Unit,
    onClear: () -> Unit,
    onFilamentSlotClick: (Int) -> Unit,
    onAddFilamentSlot: () -> Unit,
    onPreviewLayerSelectionChanged: (PreviewLayerSelection) -> Unit,
    onPreviousPreviewRangeChunk: () -> Unit,
    onNextPreviewRangeChunk: () -> Unit,
    onWorkspaceControlsExpandedChange: (Boolean) -> Unit,
    onModelTransformChanged: (ViewerModelTransform?) -> Unit,
    onOpenObjectList: () -> Unit,
    onOpenPlateSettings: () -> Unit,
    onOpenProfiles: () -> Unit,
    onSavePlate: () -> Unit,
    onSlice: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (paintModeActive && paintSession != null) {
            WorkspacePaintControlsChrome(
                paintSession = paintSession,
                isCollapsed = !paintControlsExpanded,
                activePaintMode = activePaintMode,
                paintBrushShape = paintBrushShape,
                paintBrushRadiusMm = paintBrushRadiusMm,
                paintSmartFillAngleDeg = paintSmartFillAngleDeg,
                paintOverhangAngleDeg = paintOverhangAngleDeg,
                paintClippingEnabled = paintClippingEnabled,
                paintSectionViewPosition = paintSectionViewPosition,
                paintAction = paintAction,
                filamentSlots = filamentSlots,
                activePaintColorSlotIndex = activePaintColorSlotIndex,
                nativePaintSessionActive = nativePaintSessionActive,
                nativePaintAvailable = nativePaintAvailable,
                onPaintControlsExpandedChange = onPaintControlsExpandedChange,
                onBrushShapeChanged = onBrushShapeChanged,
                onBrushRadiusChanged = onBrushRadiusChanged,
                onSmartFillAngleChanged = onSmartFillAngleChanged,
                onOverhangAngleChanged = onOverhangAngleChanged,
                onClippingEnabledChanged = onClippingEnabledChanged,
                onSectionViewPositionChanged = onSectionViewPositionChanged,
                onActionChanged = onActionChanged,
                onColorSlotSelected = onColorSlotSelected,
                onUnsupportedOption = onUnsupportedOption,
                onClear = onClear
            )
        }
        if (workspaceMode == WorkspaceMode.Prepare && filamentSlots.isNotEmpty()) {
            WorkspaceFilamentStrip(
                slots = filamentSlots,
                selectedSlotIndex = selectedPlateObject?.filamentSlotIndex ?: 1,
                onSlotClick = onFilamentSlotClick,
                onAddSlot = onAddFilamentSlot
            )
        }
        if (!paintModeActive) {
            WorkspaceControlPanel(
                modelLabel = modelLabel,
                printerTitle = selectedPrinter.name,
                printerBed = selectedPrinter.toBedSpec(),
                modelFormat = modelFormat,
                viewerState = effectiveViewerState,
                meshSummary = meshSummary,
                importTiming = importTiming,
                workspacePreparationTiming = workspacePreparationTiming,
                firstVisibleWorkspaceFrameMs = firstVisibleWorkspaceFrameMs,
                firstVisiblePreviewFrameMs = firstVisiblePreviewFrameMs,
                activeConfiguration = activeConfiguration,
                workspaceStatus = workspaceStatus,
                workspaceMode = workspaceMode,
                sliceInProgress = sliceInProgress,
                sendToPrinterInProgress = sendToPrinterInProgress,
                sliceReady = sliceReady,
                hasGeneratedGcode = hasGeneratedGcode,
                canSendToPrinter = canSendToPrinter,
                sliceSummary = sliceSummary,
                sliceTiming = sliceTiming,
                previewLayerCount = previewLayerCount,
                previewLayerSelection = previewLayerSelection,
                maxRangeLayerSpan = maxRangeLayerSpan,
                previewRangeSliderBounds = activePreviewChunkBounds,
                previewRangeChunks = autoPreviewRanges,
                previewRangeChunkIndex = autoPreviewRangeIndex,
                printerStatusLabel = compactPrinterStatusLabel(printerStatusMessage),
                controlsExpanded = workspaceControlsExpanded,
                compactControlsEnabled = isLandscape,
                modelTransform = effectiveModelTransform,
                objectCount = objectCount,
                activePlateLabel = activePlateLabel,
                selectedObjectLabel = selectedPlateObject?.label,
                onPreviewLayerSelectionChanged = onPreviewLayerSelectionChanged,
                onPreviewLayerSelectionCommitted = onPreviewLayerSelectionChanged,
                onPreviousPreviewRangeChunk = onPreviousPreviewRangeChunk,
                onNextPreviewRangeChunk = onNextPreviewRangeChunk,
                onControlsExpandedChange = onWorkspaceControlsExpandedChange,
                onModelTransformChanged = onModelTransformChanged,
                onOpenObjectList = onOpenObjectList,
                onOpenPlateSettings = onOpenPlateSettings,
                onOpenProfiles = onOpenProfiles,
                onSavePlate = onSavePlate,
                onSlice = onSlice,
                onExport = onExport,
                onShare = onShare
            )
        }
    }
}

@Composable
private fun WorkspacePaintControlsChrome(
    paintSession: ViewerPaintSession,
    isCollapsed: Boolean,
    activePaintMode: ViewerPaintMode?,
    paintBrushShape: ViewerPaintBrushShape,
    paintBrushRadiusMm: Float,
    paintSmartFillAngleDeg: Float,
    paintOverhangAngleDeg: Float,
    paintClippingEnabled: Boolean,
    paintSectionViewPosition: Float,
    paintAction: ViewerPaintAction,
    filamentSlots: List<PlateFilamentSlot>,
    activePaintColorSlotIndex: Int?,
    nativePaintSessionActive: Boolean,
    nativePaintAvailable: Boolean?,
    onPaintControlsExpandedChange: (Boolean) -> Unit,
    onBrushShapeChanged: (ViewerPaintBrushShape) -> Unit,
    onBrushRadiusChanged: (Float) -> Unit,
    onSmartFillAngleChanged: (Float) -> Unit,
    onOverhangAngleChanged: (Float) -> Unit,
    onClippingEnabledChanged: (Boolean) -> Unit,
    onSectionViewPositionChanged: (Float) -> Unit,
    onActionChanged: (ViewerPaintAction) -> Unit,
    onColorSlotSelected: (Int?) -> Unit,
    onUnsupportedOption: (String) -> Unit,
    onClear: () -> Unit
) {
    if (isCollapsed) {
        Surface(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val compactLabelStyle = MaterialTheme.typography.labelLarge
                Text(
                    text = "${activePaintMode?.paintMenuLabel().orEmpty()} paint",
                    color = appBodyColor(),
                    style = compactLabelStyle
                )
                Text(
                    text = paintBrushShape.paintMenuLabel(),
                    color = appBodyColor(),
                    style = compactLabelStyle
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", effectivePaintBrushRadiusMm(paintBrushRadiusMm))} mm",
                    color = appBodyColor(),
                    style = compactLabelStyle
                )
                TextButton(
                    onClick = { onPaintControlsExpandedChange(true) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Menu",
                        style = compactLabelStyle
                    )
                }
            }
        }
    } else {
        WorkspacePaintToolbar(
            mode = paintSession.mode,
            brushShape = paintBrushShape,
            brushRadiusMm = effectivePaintBrushRadiusMm(paintBrushRadiusMm),
            smartFillAngleDeg = paintSmartFillAngleDeg,
            overhangAngleDeg = paintOverhangAngleDeg,
            clippingEnabled = paintClippingEnabled,
            sectionViewPosition = paintSectionViewPosition,
            action = paintAction,
            filamentSlots = filamentSlots,
            activeColorSlotIndex = activePaintColorSlotIndex,
            onBrushShapeChanged = onBrushShapeChanged,
            onBrushRadiusChanged = onBrushRadiusChanged,
            onSmartFillAngleChanged = onSmartFillAngleChanged,
            onOverhangAngleChanged = onOverhangAngleChanged,
            onClippingEnabledChanged = onClippingEnabledChanged,
            onSectionViewPositionChanged = onSectionViewPositionChanged,
            onActionChanged = onActionChanged,
            onColorSlotSelected = onColorSlotSelected,
            onUnsupportedOption = onUnsupportedOption,
            onCollapse = { onPaintControlsExpandedChange(false) },
            clearEnabled = nativePaintSessionActive && nativePaintAvailable == true,
            onClear = onClear
        )
    }
}

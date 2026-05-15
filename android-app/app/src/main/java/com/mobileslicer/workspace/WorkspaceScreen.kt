package com.mobileslicer.workspace

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import com.mobileslicer.profiles.ActiveSlicerConfiguration
import com.mobileslicer.profiles.FilamentProfile
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.printerconnection.BambuLanPrintOptions
import com.mobileslicer.printerconnection.PrinterUploadAction
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.mobileslicer.appBodyColor
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.nativebridge.NativeEngineBridge
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.nativebridge.NativePaintCalls
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.GcodePreviewDisplayMode
import com.mobileslicer.viewer.GcodePreviewPerformanceMode
import com.mobileslicer.viewer.TouchModelViewerView
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.ViewerPaintAction
import com.mobileslicer.viewer.ViewerPaintBrushShape
import com.mobileslicer.viewer.ViewerPaintMode
import com.mobileslicer.viewer.ViewerPaintOverlay
import com.mobileslicer.viewer.ViewerPaintSession
import com.mobileslicer.ui.theme.LocalAppDarkTheme
import com.mobileslicer.ui.theme.WorldViewColorOption
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("SuspiciousIndentation")
internal fun WorkspaceScreen(
    modelLabel: String,
    modelFilePath: String?,
    modelFormat: ImportedModelFormat?,
    modelLoaded: Boolean,
    preparedMesh: StlMesh?,
    modelBounds: MeshBounds?,
    viewerPreparationError: String?,
    importTiming: ModelImportTiming?,
    workspacePreparationTiming: WorkspacePreparationTiming?,
    firstVisibleWorkspaceFrameMs: Long?,
    firstVisiblePreviewFrameMs: Long?,
    selectedPrinter: PrinterProfile,
    activeConfiguration: ActiveSlicerConfiguration,
    workspaceStatus: String,
    showModelImportOverlay: Boolean,
    workspaceMode: WorkspaceMode,
    sliceInProgress: Boolean,
    platePlanningInProgress: Boolean,
    sendToPrinterInProgress: Boolean,
    hasGeneratedGcode: Boolean,
    canSendToPrinter: Boolean,
    currentGcodeFileName: String,
    sliceSummary: SliceResultSummary?,
    sliceTiming: SlicePipelineTiming?,
    previewEngineHandle: Long,
    previewSliceKey: Long,
    nativeEngineHandle: Long,
    gcodePreviewPerformanceMode: GcodePreviewPerformanceMode,
    activeStylusPaintOnly: Boolean,
    worldViewColor: WorldViewColorOption,
    modelTransform: ViewerModelTransform?,
    workspacePlates: List<WorkspacePlate>,
    activePlateId: Long,
    plateObjects: List<PlateObject>,
    filamentSlots: List<PlateFilamentSlot>,
    availableFilaments: List<FilamentProfile>,
    selectedPlateObjectId: Long?,
    primeTowerPlacementOverride: PrimeTowerPlacementOverride?,
    onWorkspaceModeChanged: (WorkspaceMode) -> Unit,
    onModelTransformChanged: (ViewerModelTransform?) -> Unit,
    onActivePlateChanged: (Long) -> Unit,
    onAddPlate: () -> Unit,
    onDuplicateActivePlate: () -> Unit,
    onDeleteActivePlate: () -> Unit,
    onRenameActivePlate: (String) -> Unit,
    onMoveObjectToPlate: (Long, Long) -> Unit,
    onMoveObjectToNewPlate: (Long) -> Unit,
    onPlateObjectSelected: (Long?) -> Unit,
    onPrimeTowerPlacementChanged: (PrimeTowerPlacementOverride?, Boolean) -> Unit,
    onAddFilamentSlot: () -> Unit,
    onAssignFilamentSlotToSelected: (Int) -> Unit,
    onUpdateFilamentSlotColor: (Int, String) -> Unit,
    onUpdateFilamentSlotProfile: (Int, FilamentProfile) -> Unit,
    onUpdateFilamentSlotNozzle: (Int, Int?) -> Unit,
    onSliceSummaryChanged: (SliceResultSummary) -> Unit,
    onRemoveFilamentSlot: (Int) -> Unit,
    onDeleteSelectedObject: () -> Unit,
    onCloneSelectedObject: () -> Unit,
    onAutoOrientObjects: () -> Unit,
    onAutoArrangeObjects: (Boolean) -> Unit,
    onAddObject: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenObjectProcess: (Long?) -> Unit,
    onAddModifierToObject: (Long) -> Unit,
    onOpenModifierProcess: (Long, Long) -> Unit,
    onToggleModifier: (Long, Long, Boolean) -> Unit,
    onDeleteModifier: (Long, Long) -> Unit,
    onCenterModifierOnObject: (Long, Long) -> Unit,
    onRotateModifier: (Long, Long) -> Unit,
    onModifierTransformChanged: (Long, Long, ViewerModelTransform) -> Unit,
    onSavePlate: (Bitmap?) -> Unit,
    onBack: () -> Unit,
    onFirstVisibleWorkspaceFrame: () -> Unit,
    onFirstVisiblePreviewFrame: () -> Unit,
    onSlice: () -> Unit,
    onExport: () -> Unit,
    onSendToPrinter: (PrinterUploadAction, String, BambuLanPrintOptions?) -> Unit,
    onOpenPrinter: () -> Unit,
    onPrinterStatus: suspend (PrinterProfile) -> String,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    onPrepareNativePaintSessionRequested: (List<PlateObject>, PrinterBedSpec) -> Boolean = { _, _ -> true },
    onNativePaintPayloadCommitted: (Long, ViewerPaintMode, String) -> Unit = { _, _, _ -> },
    onCutRequested: (PlateObject, WorkspaceCutRequest) -> Unit = { _, _ -> },
    onSplitToObjectsRequested: (PlateObject) -> Unit = {},
    onSplitToPartsRequested: (PlateObject) -> Unit = {}
) {
    val darkTheme = LocalAppDarkTheme.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val accentColor = MaterialTheme.colorScheme.primary.toArgb()
    val worldColor = selectedWorkspaceWorldColor(worldViewColor).toArgb()
    var showTransformSheet by remember { mutableStateOf(false) }
    var activeTransformTab by rememberSaveable { mutableStateOf(TransformToolTab.Move) }
    var directMoveUnlocked by rememberSaveable { mutableStateOf(false) }
    var primeTowerSelected by rememberSaveable { mutableStateOf(false) }
    var layFacePickPending by remember { mutableStateOf(false) }
    var cutPreviewRequest by remember { mutableStateOf<WorkspaceCutRequest?>(null) }
    var cutOffsetOverride by remember { mutableStateOf<Float?>(null) }
    var showPrinterSendSheet by remember { mutableStateOf(false) }
    var showPreviewInfoSheet by remember { mutableStateOf(false) }
    var showObjectListSheet by remember { mutableStateOf(false) }
    var showPlateSettingsSheet by remember { mutableStateOf(false) }
    var selectedFilamentSlotSheetIndex by remember { mutableStateOf<Int?>(null) }
    var missingPrinterConnectionDialog by remember { mutableStateOf(false) }
    var printerStatusMessage by remember(selectedPrinter.id) { mutableStateOf<String?>(null) }
    var workspaceControlsExpanded by rememberSaveable(workspaceMode, previewSliceKey, isLandscape) { mutableStateOf(!isLandscape) }
    var activePaintMode by rememberSaveable { mutableStateOf<ViewerPaintMode?>(null) }
    var paintControlsExpanded by rememberSaveable(activePaintMode, isLandscape) { mutableStateOf(true) }
    var paintBrushShape by rememberSaveable { mutableStateOf(ViewerPaintBrushShape.Circle) }
    var paintBrushRadiusMm by rememberSaveable { mutableFloatStateOf(PaintBrushDefaultRadiusMm) }
    var paintBrushRadiusMigrated by remember { mutableStateOf(false) }
    var paintSmartFillAngleDeg by rememberSaveable { mutableFloatStateOf(30f) }
    var paintOverhangAngleDeg by rememberSaveable { mutableFloatStateOf(0f) }
    var paintClippingEnabled by rememberSaveable { mutableStateOf(false) }
    var paintSectionViewPosition by rememberSaveable { mutableFloatStateOf(1f) }
    var paintAction by rememberSaveable { mutableStateOf(ViewerPaintAction.Paint) }
    var activePaintColorSlotIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var paintModePrepareInProgress by remember { mutableStateOf(false) }
    var nativePaintSessionActive by remember { mutableStateOf(false) }
    var nativePaintAvailable by remember { mutableStateOf<Boolean?>(null) }
    var nativePaintStatus by remember { mutableStateOf<String?>(null) }
    var nativePaintOverlay by remember { mutableStateOf(ViewerPaintOverlay.Empty) }
    var nativePaintSourceBounds by remember { mutableStateOf<MeshBounds?>(null) }
    val paintModeOverlayCache = remember { mutableStateMapOf<ViewerPaintMode, ViewerPaintOverlay>() }
    val livePaintOverlayRef = remember { arrayOf(ViewerPaintOverlay.Empty) }
    var nativePaintUndoAvailable by remember { mutableStateOf(false) }
    var nativePaintRedoAvailable by remember { mutableStateOf(false) }
    val paintRuntime = remember { PaintRuntimeState() }
    val paintCoroutineScope = rememberCoroutineScope()
    val paintNativeHandle = NativeEngineHandle.fromRaw(nativeEngineHandle)
    LaunchedEffect(Unit) {
        if (!paintBrushRadiusMigrated) {
            paintBrushRadiusMigrated = true
            if (paintBrushRadiusMm !in PaintBrushMinRadiusMm..PaintBrushMaxRadiusMm) {
                paintBrushRadiusMm = PaintBrushDefaultRadiusMm
            }
        }
    }
    val sceneState = rememberWorkspaceSceneState(
        modelFilePath = modelFilePath,
        modelFormat = modelFormat,
        modelLoaded = modelLoaded,
        preparedMesh = preparedMesh,
        modelBounds = modelBounds,
        viewerPreparationError = viewerPreparationError,
        selectedPrinter = selectedPrinter,
        activeConfiguration = activeConfiguration,
        workspaceMode = workspaceMode,
        sliceInProgress = sliceInProgress,
        modelTransform = modelTransform,
        workspacePlates = workspacePlates,
        activePlateId = activePlateId,
        plateObjects = plateObjects,
        filamentSlots = filamentSlots,
        selectedPlateObjectId = selectedPlateObjectId,
        primeTowerPlacementOverride = primeTowerPlacementOverride,
        showTransformSheet = showTransformSheet,
        activeTransformTab = activeTransformTab,
        directMoveUnlocked = directMoveUnlocked,
        primeTowerSelected = primeTowerSelected,
        activePaintMode = activePaintMode,
        paintBrushShape = paintBrushShape,
        paintBrushRadiusMm = paintBrushRadiusMm,
        paintAction = paintAction,
        activePaintColorSlotIndex = activePaintColorSlotIndex,
        nativePaintOverlay = nativePaintOverlay,
        livePaintOverlay = livePaintOverlayRef[0]
    )
    val selectedPlateObject = sceneState.selectedPlateObject
    val activePlateLabel = sceneState.activePlateLabel
    val filamentSlotsByIndex = sceneState.filamentSlotsByIndex
    val paintModeActive = sceneState.paintModeActive
    val paintSession = sceneState.paintSession
    val viewerPlateObjects = sceneState.viewerPlateObjects
    val viewerState = sceneState.viewerState
    val canDragSelectedPlateObject = sceneState.canDragSelectedPlateObject
    val loadedMesh = sceneState.loadedMesh
    val effectiveModelTransform = sceneState.effectiveModelTransform
    val meshSummary = sceneState.meshSummary
    val sliceReady = sceneState.sliceReady
    val transformReady = sceneState.transformReady
    val cutPreviewBounds = sceneState.cutPreviewBounds
    val primeTowerViewerState = sceneState.primeTowerViewerState
    val primeTowerPlacement = primeTowerViewerState.placement
    val canDragPrimeTower = primeTowerViewerState.canDrag
    val viewerSceneKey = when (workspaceMode) {
        WorkspaceMode.Preview -> "preview:$previewSliceKey:$previewEngineHandle"
        WorkspaceMode.Prepare -> "prepare:${selectedPrinter.id}:${modelFilePath.orEmpty()}"
        WorkspaceMode.Paint -> "paint:${selectedPlateObject?.id}:${activePaintMode?.name}:${selectedPrinter.id}"
    }
    var runtimeViewerFailure by remember(viewerSceneKey) { mutableStateOf<com.mobileslicer.viewer.ViewerFailure?>(null) }
    var runtimeViewerReady by remember(viewerSceneKey) { mutableStateOf(false) }
    var runtimeViewerEverReady by remember(viewerSceneKey) { mutableStateOf(false) }
    var viewerView by remember { mutableStateOf<TouchModelViewerView?>(null) }
    var previewPathVisibility by remember(previewSliceKey) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var previewDisplayMode by remember(previewSliceKey) { mutableStateOf(GcodePreviewDisplayMode.Auto) }
    val cutSession = workspaceCutSession(
        workspaceMode = workspaceMode,
        showTransformSheet = showTransformSheet,
        activeTransformTab = activeTransformTab,
        selectedPlateObject = selectedPlateObject,
        cutPreviewBounds = cutPreviewBounds,
        cutPreviewRequest = cutPreviewRequest
    )
    val cutToolActive = cutSession != null
    val toolsActive = showTransformSheet && workspaceMode == WorkspaceMode.Prepare
    val previewLayerCount = (sliceSummary?.layerChangeCount ?: 0).coerceAtLeast(1)
    val exactPreviewVertexBudget = gcodePreviewPerformanceMode.vertexBudget
    val maxRangeLayerSpan = Int.MAX_VALUE
    val previewRuntime = rememberWorkspacePreviewRuntime(previewSliceKey, previewLayerCount)
    val suppressPreviewRangeFailure = workspaceMode == WorkspaceMode.Preview &&
        previewRuntime.autoRanges.size > 1 &&
        runtimeViewerFailure?.isGcodePreviewRangeTooLarge() == true
    val effectiveRuntimeViewerFailure = runtimeViewerFailure.takeUnless { suppressPreviewRangeFailure }
    val effectiveViewerState = effectiveRuntimeViewerFailure?.let { failure ->
        WorkspaceViewerState.Error(failure.title, failure.detail)
    } ?: when {
        loadedMesh != null && !runtimeViewerReady && !runtimeViewerEverReady -> WorkspaceViewerState.Preparing
        else -> viewerState
    }
    val selectedPreviewLayerRange = selectedPreviewLayerRange(
        selection = previewRuntime.layerSelection,
        previewLayerCount = previewLayerCount,
        maxRangeLayerSpan = maxRangeLayerSpan
    )
    val activePreviewChunkBounds = previewRuntime.autoRanges
        .getOrNull(previewRuntime.autoRangeIndex)
        ?.let { it.startLayer..it.endLayer }
    fun applyPreviewLayerSelection(selection: PreviewLayerSelection) {
        if (previewRuntime.autoRanges.isNotEmpty()) {
            val nextIndex = previewRangeIndexForSelection(
                selection = selection,
                ranges = previewRuntime.autoRanges,
                currentIndex = previewRuntime.autoRangeIndex,
                layerCount = previewLayerCount
            )
            if (nextIndex != previewRuntime.autoRangeIndex) {
                previewRuntime.autoRangeIndex = nextIndex
                previewRuntime.layerReloadToken++
            }
        }
        previewRuntime.layerSelection = selection
    }
    WorkspacePreviewEffects(
        workspaceMode = workspaceMode,
        previewSliceKey = previewSliceKey,
        previewLayerCount = previewLayerCount,
        previewEngineHandle = previewEngineHandle,
        previewVertexBudget = exactPreviewVertexBudget,
        sliceSummary = sliceSummary,
        sliceInProgress = sliceInProgress,
        showPreviewInfoSheet = showPreviewInfoSheet,
        loadedMesh = loadedMesh,
        runtimeViewerReady = runtimeViewerReady,
        runtimeViewerFailure = runtimeViewerFailure,
        previewPathVisibility = previewPathVisibility,
        viewerView = viewerView,
        previewRuntime = previewRuntime,
        onRuntimeViewerFailureChanged = { runtimeViewerFailure = it },
        onSliceSummaryChanged = onSliceSummaryChanged,
        onFirstVisibleWorkspaceFrame = onFirstVisibleWorkspaceFrame,
        onFirstVisiblePreviewFrame = onFirstVisiblePreviewFrame
    )
    LaunchedEffect(loadedMesh, selectedPrinter.id, modelTransform, plateObjects.size) {
        if (plateObjects.isEmpty() && loadedMesh != null && modelTransform == null) {
            onModelTransformChanged(defaultViewerModelTransform(selectedPrinter.toBedSpec()))
        }
    }
    LaunchedEffect(workspaceMode) {
        if (workspaceMode != WorkspaceMode.Prepare) {
            showTransformSheet = false
            directMoveUnlocked = false
        }
        if (workspaceMode != WorkspaceMode.Preview) {
            showPrinterSendSheet = false
            showPreviewInfoSheet = false
        }
        if (workspaceMode != WorkspaceMode.Paint) {
            activePaintMode = null
        } else {
            showTransformSheet = false
            showObjectListSheet = false
            selectedFilamentSlotSheetIndex = null
        }
    }
    LaunchedEffect(plateObjects.size, selectedPlateObjectId) {
        if (plateObjects.size == 1 && selectedPlateObjectId != plateObjects.first().id) {
            onPlateObjectSelected(plateObjects.first().id)
        }
    }
    LaunchedEffect(primeTowerPlacement) {
        if (primeTowerPlacement == null) {
            primeTowerSelected = false
        }
    }
    LaunchedEffect(filamentSlots, activePaintColorSlotIndex) {
        val currentSlot = activePaintColorSlotIndex
        if (currentSlot == null || filamentSlots.none { it.index == currentSlot }) {
            activePaintColorSlotIndex = filamentSlots.firstOrNull()?.index
        }
    }
    LaunchedEffect(activePaintMode, paintBrushShape, paintAction) {
        val mode = activePaintMode ?: return@LaunchedEffect
        val normalizedTool = normalizedPaintToolState(mode, paintBrushShape, paintAction)
        paintBrushShape = normalizedTool.brushShape
        paintAction = normalizedTool.action
    }
    val paintController = WorkspacePaintRuntimeController(
        paintRuntime = paintRuntime,
        paintCoroutineScope = paintCoroutineScope,
        paintNativeHandle = paintNativeHandle,
        paintSession = paintSession,
        activePaintMode = activePaintMode,
        paintBrushShape = paintBrushShape,
        paintBrushRadiusMm = paintBrushRadiusMm,
        paintSmartFillAngleDeg = paintSmartFillAngleDeg,
        paintOverhangAngleDeg = paintOverhangAngleDeg,
        paintClippingEnabled = paintClippingEnabled,
        paintSectionViewPosition = paintSectionViewPosition,
        paintAction = paintAction,
        activePaintColorSlotIndex = activePaintColorSlotIndex,
        nativePaintSessionActive = nativePaintSessionActive,
        nativePaintOverlay = nativePaintOverlay,
        nativePaintSourceBounds = nativePaintSourceBounds,
        nativePaintOverlayProvider = { nativePaintOverlay },
        nativePaintSourceBoundsProvider = { nativePaintSourceBounds },
        nativePaintSessionActiveProvider = { nativePaintSessionActive },
        paintModeOverlayCache = paintModeOverlayCache,
        livePaintOverlayRef = livePaintOverlayRef,
        selectedPlateObject = selectedPlateObject,
        plateObjects = plateObjects,
        filamentSlots = filamentSlots,
        selectedPrinter = selectedPrinter,
        modelTransform = modelTransform,
        workspaceMode = workspaceMode,
        activeStylusPaintOnly = activeStylusPaintOnly,
        viewerView = viewerView,
        viewerViewProvider = { viewerView },
        onNativePaintSessionActiveChanged = { nativePaintSessionActive = it },
        onNativePaintAvailableChanged = { nativePaintAvailable = it },
        onNativePaintStatusChanged = { nativePaintStatus = it },
        onNativePaintOverlayChanged = { nativePaintOverlay = it },
        onNativePaintSourceBoundsChanged = { nativePaintSourceBounds = it },
        onNativePaintUndoAvailableChanged = { nativePaintUndoAvailable = it },
        onNativePaintRedoAvailableChanged = { nativePaintRedoAvailable = it },
        onPaintModePrepareInProgressChanged = { paintModePrepareInProgress = it },
        onActivePaintModeChanged = { activePaintMode = it },
        onPaintBrushShapeChanged = { paintBrushShape = it },
        onPaintActionChanged = { paintAction = it },
        onActivePaintColorSlotIndexChanged = { activePaintColorSlotIndex = it },
        onPrepareNativePaintSessionRequested = onPrepareNativePaintSessionRequested,
        onNativePaintPayloadCommitted = onNativePaintPayloadCommitted,
        onPlateObjectSelected = onPlateObjectSelected,
        onWorkspaceModeChanged = onWorkspaceModeChanged
    )
    LaunchedEffect(paintModeActive, selectedPlateObject?.id, activePaintMode, nativeEngineHandle) {
        paintController.prepareNativeSessionForCurrentSelection()
    }
    LaunchedEffect(
        paintBrushShape,
        paintBrushRadiusMm,
        paintSmartFillAngleDeg,
        paintOverhangAngleDeg,
        paintClippingEnabled,
        paintAction,
        activePaintColorSlotIndex,
        nativePaintSessionActive
    ) {
        if (!nativePaintSessionActive) return@LaunchedEffect
        paintController.configureNativePaintTool()
    }
    BackHandler(enabled = paintModeActive) {
        paintController.exitPaintMode()
    }
    LaunchedEffect(sliceInProgress) {
        if (sliceInProgress) {
            showPreviewInfoSheet = false
        }
    }
    LaunchedEffect(workspaceMode, canSendToPrinter, selectedPrinter.id, selectedPrinter.printHost, selectedPrinter.printHostWebUi) {
        if (workspaceMode != WorkspaceMode.Preview || !canSendToPrinter) {
            printerStatusMessage = null
            return@LaunchedEffect
        }
        while (true) {
            printerStatusMessage = onPrinterStatus(selectedPrinter)
            delay(3_000)
        }
    }
    WorkspaceScreenFrame(modifier = modifier) {
        WorkspaceViewerSurface(
            modelFormat = modelFormat,
            preparedMesh = if (viewerPlateObjects.isEmpty()) loadedMesh else null,
            printerBed = selectedPrinter.toBedSpec(),
            viewerState = effectiveViewerState,
            darkTheme = darkTheme,
            accentColor = accentColor,
            worldColor = worldColor,
            modelTransform = if (viewerPlateObjects.isEmpty()) effectiveModelTransform else null,
            plateObjects = viewerPlateObjects,
            previewEngineHandle = if (workspaceMode == WorkspaceMode.Preview && previewRuntime.exactPlanReady) previewEngineHandle else 0L,
            previewSliceKey = if (workspaceMode == WorkspaceMode.Preview && previewRuntime.exactPlanReady) previewSliceKey else 0L,
            gcodePreviewVertexBudget = exactPreviewVertexBudget,
            gcodeLayerMin = selectedPreviewLayerRange.first,
            gcodeLayerMax = selectedPreviewLayerRange.second,
            gcodeLayerReloadToken = previewRuntime.layerReloadToken,
            gcodeDisplayMode = if (sliceInProgress) null else previewDisplayMode,
            paintSession = paintSession,
            activeStylusPaintOnly = activeStylusPaintOnly,
            cutSession = cutSession,
            onCutOffsetChanged = { nextOffset ->
                cutOffsetOverride = nextOffset
                cutPreviewRequest = cutPreviewRequest?.copy(heightMm = nextOffset)
            },
            onCutConnectorPointAdded = { point ->
                val currentRequest = cutPreviewRequest
                if (currentRequest != null && currentRequest.connectorKind != WorkspaceCutConnectorKind.None) {
                    cutPreviewRequest = currentRequest.copy(
                        connectorPositions = (currentRequest.connectorPositions + point).takeLast(64)
                    )
                }
            },
            onRuntimeFailureChanged = { failure ->
                runtimeViewerFailure = if (
                    workspaceMode == WorkspaceMode.Preview &&
                    previewRuntime.autoRanges.size > 1 &&
                    failure?.isGcodePreviewRangeTooLarge() == true
                ) {
                    null
                } else {
                    failure
                }
            },
            onViewerReadyChanged = { ready ->
                runtimeViewerReady = ready
                if (ready) {
                    runtimeViewerEverReady = true
                }
            },
            onPreviewRuntimeMetrics = { metrics ->
                Log.i(
                    "MobileSlicerPerf",
                    "workspace_preview_runtime " +
                        "previewKey=${metrics.previewKey} " +
                        "layerStart=${metrics.layerStart} " +
                        "layerEnd=${metrics.layerEnd} " +
                        "vertexBudget=${metrics.vertexBudget} " +
                        "nativeLoadMs=${metrics.nativeLoadMs} " +
                        "nativeSelectedParseMs=${metrics.nativeSelectedParseMs} " +
                        "nativeLibvgcodeLoadMs=${metrics.nativeLibvgcodeLoadMs} " +
                        "nativeTotalLoadMs=${metrics.nativeTotalLoadMs} " +
                        "nativeLoadedVertices=${metrics.nativeLoadedVertices} " +
                        "nativeCachedVertices=${metrics.nativeCachedVertices} " +
                        "nativeCachedLayers=${metrics.nativeCachedLayers} " +
                        "nativeCacheHit=${metrics.nativeCacheHit} " +
                        "nativeCacheBuilt=${metrics.nativeCacheBuilt} " +
                        "firstFrameMs=${metrics.firstFrameMs} " +
                        "lastFrameMs=${metrics.lastFrameMs} " +
                        "slowFrames=${metrics.slowFrameCount} " +
                        "frames=${metrics.renderedFrameCount}"
                )
            },
            onObjectSelected = { objectId ->
                if (!paintModeActive) {
                    if (objectId == PrimeTowerVirtualObjectId) {
                        primeTowerSelected = true
                        if (selectedPlateObject == null) {
                            plateObjects.firstOrNull()?.id?.let(onPlateObjectSelected)
                        }
                    } else if (objectId != null && objectId < 0L) {
                        val parentObject = plateObjects.firstOrNull { objectOnPlate ->
                            objectOnPlate.modifiers.any { modifier ->
                                modifierViewerObjectId(objectOnPlate.id, modifier.id) == objectId
                            }
                        }
                        primeTowerSelected = false
                        onPlateObjectSelected(parentObject?.id)
                    } else {
                        primeTowerSelected = false
                        onPlateObjectSelected(objectId)
                    }
                }
            },
            onObjectDrag = { objectId, deltaXmm, deltaYmm, finished ->
                if (objectId == PrimeTowerVirtualObjectId && primeTowerPlacement != null) {
                    if (finished) {
                        onPrimeTowerPlacementChanged(primeTowerPlacementOverride, true)
                    } else if (canDragPrimeTower) {
                        val margin = 5f + primeTowerPlacement.brimWidthMm
                        val nextX = (primeTowerPlacement.xMm + deltaXmm)
                            .coerceIn(margin, selectedPrinter.toBedSpec().widthMm - primeTowerPlacement.widthMm - margin)
                        val nextY = (primeTowerPlacement.yMm + deltaYmm)
                            .coerceIn(margin, selectedPrinter.toBedSpec().depthMm - primeTowerPlacement.depthMm - margin)
                        onPrimeTowerPlacementChanged(PrimeTowerPlacementOverride(nextX, nextY), false)
                    }
                } else if (!finished && canDragSelectedPlateObject && objectId == selectedPlateObject?.id) {
                    selectedPlateObject?.transform?.let { currentTransform ->
                        onModelTransformChanged(
                            currentTransform.copy(
                                centerXmm = (currentTransform.centerXmm + deltaXmm)
                                    .coerceIn(0f, selectedPrinter.toBedSpec().widthMm),
                                centerYmm = (currentTransform.centerYmm + deltaYmm)
                                    .coerceIn(0f, selectedPrinter.toBedSpec().depthMm)
                            )
                        )
                    }
                } else if (!finished && canDragSelectedPlateObject && objectId < 0L) {
                    plateObjects.firstNotNullOfOrNull { objectOnPlate ->
                        objectOnPlate.modifiers.firstOrNull { modifier ->
                            modifierViewerObjectId(objectOnPlate.id, modifier.id) == objectId
                        }?.let { modifier -> objectOnPlate to modifier }
                    }?.let { (objectOnPlate, modifier) ->
                        onModifierTransformChanged(
                            objectOnPlate.id,
                            modifier.id,
                            modifier.transform.copy(
                                centerXmm = (modifier.transform.centerXmm + deltaXmm)
                                    .coerceIn(0f, selectedPrinter.toBedSpec().widthMm),
                                centerYmm = (modifier.transform.centerYmm + deltaYmm)
                                    .coerceIn(0f, selectedPrinter.toBedSpec().depthMm)
                            )
                        )
                    }
                }
            },
            onObjectHitSelected = { hit ->
                if (!layFacePickPending) {
                    false
                } else {
                    layFacePickPending = false
                    val targetObject = selectedPlateObject
                    if (hit != null && targetObject != null && hit.objectId == targetObject.id) {
                        val layFaceNormal = targetObject.mesh?.let { mesh ->
                            clusteredLayFaceNormal(
                                mesh = mesh,
                                triangleIndex = hit.triangleIndex,
                                transform = targetObject.transform,
                                fallbackWorldNormal = hit.normal
                            )
                        } ?: hit.normal
                        layFaceOnBedTransform(
                            transform = targetObject.transform,
                            worldNormalX = layFaceNormal.xMm,
                            worldNormalY = layFaceNormal.yMm,
                            worldNormalZ = layFaceNormal.zMm,
                            bounds = targetObject.mesh?.bounds ?: targetObject.bounds
                        )?.let { next ->
                            onModelTransformChanged(next)
                        }
                    }
                    true
                }
            },
            onPaintHitTest = paintController::hitTest,
            onPaintStrokeBegin = paintController::beginPaintStroke,
            onPaintStrokeMove = paintController::enqueuePaintMove,
            onPaintStrokeEnd = { committed ->
                paintController.commitNativePaintStroke(committed)
            },
            onViewerViewChanged = { view ->
                val previousView = viewerView
                viewerView = view
                if (view != null && view !== previousView && workspaceMode == WorkspaceMode.Paint) {
                    view.setPaintOverlay(paintController.currentPaintDisplayOverlay())
                }
            }
        )
        if (showModelImportOverlay) {
            ImportingModelOverlay(
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (paintModeActive && isLandscape && paintControlsExpanded) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .noRippleTap { paintControlsExpanded = false }
            )
        }
        WorkspaceHeaderChrome(
            paintModeActive = paintModeActive,
            paintSession = paintSession,
            paintObjectLabel = paintHeaderLabel(nativePaintStatus, selectedPlateObject?.label.orEmpty()),
            nativePaintSessionActive = nativePaintSessionActive,
            nativePaintAvailable = nativePaintAvailable,
            nativePaintUndoAvailable = nativePaintUndoAvailable,
            nativePaintRedoAvailable = nativePaintRedoAvailable,
            selectedPrinter = selectedPrinter,
            workspaceMode = workspaceMode,
            hasGeneratedGcode = hasGeneratedGcode,
            transformReady = transformReady,
            sliceInProgress = sliceInProgress,
            selectedPlateObject = selectedPlateObject,
            canSendToPrinter = canSendToPrinter,
            sendToPrinterInProgress = sendToPrinterInProgress,
            printerStatusMessage = printerStatusMessage,
            sliceSummary = sliceSummary,
            onBack = onBack,
            onPaintBack = paintController::exitPaintMode,
            onUndo = { paintController.runNativePaintHistoryCommand(NativePaintCalls::undo) },
            onRedo = { paintController.runNativePaintHistoryCommand(NativePaintCalls::redo) },
            onTransformClick = { showTransformSheet = !showTransformSheet },
            onDeleteObject = onDeleteSelectedObject,
            onCloneObject = onCloneSelectedObject,
            onAddObject = onAddObject,
            onOpenSendMenu = {
                if (canSendToPrinter) {
                    showPrinterSendSheet = true
                } else {
                    missingPrinterConnectionDialog = true
                }
            },
            onOpenPrinter = {
                if (canSendToPrinter) {
                    onOpenPrinter()
                } else {
                    missingPrinterConnectionDialog = true
                }
            },
            onOpenPreviewInfo = { showPreviewInfoSheet = true },
            onWorkspaceModeChanged = onWorkspaceModeChanged,
            modifier = Modifier.align(Alignment.TopStart)
        )
        if (layFacePickPending) {
            LayFacePickHint(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (toolsActive) 150.dp else 62.dp)
            )
        }
        if (workspaceMode == WorkspaceMode.Prepare && selectedPlateObject != null && !paintModeActive && !showTransformSheet) {
            DirectMoveLockButton(
                unlocked = directMoveUnlocked,
                enabled = !sliceInProgress,
                onToggle = { directMoveUnlocked = !directMoveUnlocked },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 62.dp, end = 12.dp)
            )
        }
        if (toolsActive) {
            TransformPopoverContent(
                printerBed = selectedPrinter.toBedSpec(),
                transform = effectiveModelTransform ?: defaultViewerModelTransform(selectedPrinter.toBedSpec()),
                selectedTab = activeTransformTab,
                onSelectedTabChanged = { activeTransformTab = it },
                onTransformChanged = onModelTransformChanged,
                onAutoOrientObjects = onAutoOrientObjects,
                onAutoArrangeObjects = onAutoArrangeObjects,
                platePlanningInProgress = platePlanningInProgress,
                onLayFaceRequested = {
                    layFacePickPending = true
                },
                onPaintModeSelected = { mode ->
                    showTransformSheet = false
                    paintController.enterPaintMode(mode, paintModePrepareInProgress)
                },
                onSplitToObjectsRequested = {
                    selectedPlateObject?.let { objectOnPlate ->
                        showTransformSheet = false
                        onSplitToObjectsRequested(objectOnPlate)
                    }
                },
                onSplitToPartsRequested = {
                    selectedPlateObject?.let { objectOnPlate ->
                        showTransformSheet = false
                        onSplitToPartsRequested(objectOnPlate)
                    }
                },
                cutBounds = selectedPlateObject?.mesh?.bounds ?: selectedPlateObject?.bounds,
                cutOffsetOverride = cutOffsetOverride,
                onCutPreviewChanged = { request ->
                    cutPreviewRequest = mergeCutPreviewRequest(cutPreviewRequest, request)
                    if (request == null) {
                        cutOffsetOverride = null
                    }
                },
                onCutRequested = { request ->
                    selectedPlateObject?.let { objectOnPlate ->
                        val mergedRequest = mergeSubmittedCutRequest(cutPreviewRequest, request)
                        showTransformSheet = false
                        cutPreviewRequest = null
                        cutOffsetOverride = null
                        onCutRequested(objectOnPlate, mergedRequest)
                    }
                },
                showTabRow = false,
                compactLayout = isLandscape,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = if (isLandscape) 760.dp else 620.dp)
                    .navigationBarsPadding()
                    .padding(
                        start = if (isLandscape) 42.dp else 24.dp,
                        end = if (isLandscape) 42.dp else 24.dp,
                        bottom = if (isLandscape) 8.dp else 12.dp
                    )
            )
        } else {
            WorkspaceBottomChrome(
            paintModeActive = paintModeActive,
            paintSession = paintSession,
            isLandscape = isLandscape,
            activePaintMode = activePaintMode,
            paintControlsExpanded = paintControlsExpanded,
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
            workspaceMode = workspaceMode,
            selectedPlateObject = selectedPlateObject,
            modelLabel = modelLabel,
            selectedPrinter = selectedPrinter,
            modelFormat = modelFormat,
            effectiveViewerState = effectiveViewerState,
            meshSummary = meshSummary,
            importTiming = importTiming,
            workspacePreparationTiming = workspacePreparationTiming,
            firstVisibleWorkspaceFrameMs = firstVisibleWorkspaceFrameMs,
            firstVisiblePreviewFrameMs = firstVisiblePreviewFrameMs,
            activeConfiguration = activeConfiguration,
            workspaceStatus = workspaceStatus,
            sliceInProgress = sliceInProgress,
            sendToPrinterInProgress = sendToPrinterInProgress,
            sliceReady = sliceReady,
            hasGeneratedGcode = hasGeneratedGcode,
            canSendToPrinter = canSendToPrinter,
            sliceSummary = sliceSummary,
            sliceTiming = sliceTiming,
            previewLayerCount = previewLayerCount,
            previewLayerSelection = previewRuntime.layerSelection,
            maxRangeLayerSpan = maxRangeLayerSpan,
            activePreviewChunkBounds = activePreviewChunkBounds,
            autoPreviewRanges = previewRuntime.autoRanges,
            autoPreviewRangeIndex = previewRuntime.autoRangeIndex,
            printerStatusMessage = printerStatusMessage,
            workspaceControlsExpanded = workspaceControlsExpanded,
            effectiveModelTransform = effectiveModelTransform ?: defaultViewerModelTransform(selectedPrinter.toBedSpec()),
            objectCount = plateObjects.size,
            activePlateLabel = activePlateLabel,
            onPaintControlsExpandedChange = { paintControlsExpanded = it },
            onBrushShapeChanged = { paintBrushShape = it },
            onBrushRadiusChanged = { paintBrushRadiusMm = effectivePaintBrushRadiusMm(it) },
            onSmartFillAngleChanged = { paintSmartFillAngleDeg = it },
            onOverhangAngleChanged = { paintOverhangAngleDeg = it },
            onClippingEnabledChanged = { paintClippingEnabled = it },
            onSectionViewPositionChanged = { paintSectionViewPosition = it.coerceIn(0f, 1f) },
            onActionChanged = { paintAction = it },
            onColorSlotSelected = { activePaintColorSlotIndex = it },
            onUnsupportedOption = { message -> nativePaintStatus = message },
            onClear = { paintController.runNativePaintHistoryCommand(NativePaintCalls::clear) },
            onFilamentSlotClick = { selectedFilamentSlotSheetIndex = it },
            onAddFilamentSlot = onAddFilamentSlot,
            onPreviewLayerSelectionChanged = { applyPreviewLayerSelection(it) },
            onPreviousPreviewRangeChunk = {
                if (previewRuntime.autoRanges.isNotEmpty()) {
                    val nextIndex = if (previewRuntime.autoRangeIndex <= 0) {
                        previewRuntime.autoRanges.lastIndex
                    } else {
                        previewRuntime.autoRangeIndex - 1
                    }
                    val nextRange = previewRuntime.autoRanges[nextIndex]
                    previewRuntime.autoRangeIndex = nextIndex
                    previewRuntime.layerSelection = nextRange.toPreviewLayerSelection()
                    previewRuntime.layerReloadToken++
                }
            },
            onNextPreviewRangeChunk = {
                if (previewRuntime.autoRanges.isNotEmpty()) {
                    val nextIndex = (previewRuntime.autoRangeIndex + 1) % previewRuntime.autoRanges.size
                    val nextRange = previewRuntime.autoRanges[nextIndex]
                    previewRuntime.autoRangeIndex = nextIndex
                    previewRuntime.layerSelection = nextRange.toPreviewLayerSelection()
                    previewRuntime.layerReloadToken++
                }
            },
            onWorkspaceControlsExpandedChange = { workspaceControlsExpanded = it },
            onModelTransformChanged = onModelTransformChanged,
            onOpenObjectList = { showObjectListSheet = true },
            onOpenPlateSettings = { showPlateSettingsSheet = true },
            onOpenProfiles = onOpenProfiles,
            onSavePlate = {
                val view = viewerView
                if (view == null) {
                    onSavePlate(null)
                } else {
                    view.captureCurrentFrame { bitmap -> onSavePlate(bitmap) }
                }
            },
            onSlice = onSlice,
            onExport = onExport,
            onShare = onShare,
            modifier = if (paintModeActive && isLandscape) {
                Modifier
                    .align(Alignment.BottomStart)
                    .widthIn(max = 680.dp)
                    .navigationBarsPadding()
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            }
        )
        }
        WorkspaceSheetsChrome(
            workspaceMode = workspaceMode,
            showTransformSheet = showTransformSheet,
            activeTransformTab = activeTransformTab,
            showPrinterSendSheet = showPrinterSendSheet,
            showPreviewInfoSheet = showPreviewInfoSheet,
            showObjectListSheet = showObjectListSheet,
            showPlateSettingsSheet = showPlateSettingsSheet,
            activePlateLabel = activePlateLabel,
            workspacePlates = workspacePlates,
            activePlateId = activePlateId,
            selectedFilamentSlotSheetIndex = selectedFilamentSlotSheetIndex,
            missingPrinterConnectionDialog = missingPrinterConnectionDialog,
            isLandscape = isLandscape,
            selectedPrinter = selectedPrinter,
            effectiveModelTransform = effectiveModelTransform,
            sendToPrinterInProgress = sendToPrinterInProgress,
            currentGcodeFileName = currentGcodeFileName,
            sliceSummary = sliceSummary,
            sliceInProgress = sliceInProgress,
            platePlanningInProgress = platePlanningInProgress,
            previewPathVisibility = previewPathVisibility,
            previewDisplayMode = previewDisplayMode,
            plateObjects = plateObjects,
            filamentSlots = filamentSlots,
            selectedPlateObject = selectedPlateObject,
            availableFilaments = availableFilaments,
            onTransformSheetDismiss = { showTransformSheet = false },
            onTransformTabChanged = { activeTransformTab = it },
            onTransformChanged = onModelTransformChanged,
            onAutoOrientObjects = onAutoOrientObjects,
            onAutoArrangeObjects = onAutoArrangeObjects,
            onLayFaceRequested = {
                layFacePickPending = true
            },
            onPaintModeSelected = { mode ->
                showTransformSheet = false
                paintController.enterPaintMode(mode, paintModePrepareInProgress)
            },
            cutOffsetOverride = cutOffsetOverride,
            onCutPreviewChanged = { request ->
                cutPreviewRequest = mergeCutPreviewRequest(cutPreviewRequest, request)
                if (request == null) {
                    cutOffsetOverride = null
                }
            },
            onCutRequested = { request ->
                selectedPlateObject?.let { objectOnPlate ->
                    val mergedRequest = mergeSubmittedCutRequest(cutPreviewRequest, request)
                    showTransformSheet = false
                    cutPreviewRequest = null
                    cutOffsetOverride = null
                    onCutRequested(objectOnPlate, mergedRequest)
                }
            },
            onSendToPrinter = { action, remoteFileName, bambuOptions ->
                showPrinterSendSheet = false
                onSendToPrinter(action, remoteFileName, bambuOptions)
            },
            onPrinterSendDismiss = { showPrinterSendSheet = false },
            onPreviewDisplayModeChanged = { mode ->
                previewDisplayMode = mode
                viewerView?.setGcodeDisplayMode(mode)
            },
            onPreviewLineVisibilityChanged = { row: PreviewLineTypeRow, visible ->
                val key = previewLineVisibilityKey(row)
                previewPathVisibility = previewPathVisibility.toMutableMap().apply { put(key, visible) }
                viewerView?.setGcodePathVisibility(row.kind.nativeKind, row.nativeId, visible)
            },
            onPreviewInfoDismiss = { showPreviewInfoSheet = false },
            onObjectSelected = { objectId ->
                onPlateObjectSelected(objectId)
                showObjectListSheet = false
            },
            onObjectProcessSelected = { objectId ->
                onPlateObjectSelected(objectId)
                showObjectListSheet = false
                onOpenObjectProcess(objectId)
            },
            onAddModifierToObject = { objectId ->
                onPlateObjectSelected(objectId)
                showObjectListSheet = false
                onAddModifierToObject(objectId)
            },
            onModifierProcessSelected = { objectId, modifierId ->
                onPlateObjectSelected(objectId)
                showObjectListSheet = false
                onOpenModifierProcess(objectId, modifierId)
            },
            onToggleModifier = onToggleModifier,
            onDeleteModifier = onDeleteModifier,
            onCenterModifierOnObject = onCenterModifierOnObject,
            onRotateModifier = onRotateModifier,
            onModifierTransformChanged = onModifierTransformChanged,
            onObjectListDismiss = { showObjectListSheet = false },
            onPlateSettingsDismiss = { showPlateSettingsSheet = false },
            onActivePlateChanged = { plateId ->
                showPlateSettingsSheet = false
                onActivePlateChanged(plateId)
            },
            onAddPlate = {
                showPlateSettingsSheet = false
                onAddPlate()
            },
            onDuplicateActivePlate = {
                showPlateSettingsSheet = false
                onDuplicateActivePlate()
            },
            onDeleteActivePlate = {
                showPlateSettingsSheet = false
                onDeleteActivePlate()
            },
            onRenameActivePlate = onRenameActivePlate,
            onMoveObjectToPlate = onMoveObjectToPlate,
            onMoveObjectToNewPlate = onMoveObjectToNewPlate,
            onFilamentSheetDismiss = { selectedFilamentSlotSheetIndex = null },
            onAssignFilamentSlotToSelected = { slotIndex ->
                onAssignFilamentSlotToSelected(slotIndex)
                selectedFilamentSlotSheetIndex = null
            },
            onUpdateFilamentSlotColor = onUpdateFilamentSlotColor,
            onUpdateFilamentSlotProfile = { slotIndex, filament ->
                onUpdateFilamentSlotProfile(slotIndex, filament)
                selectedFilamentSlotSheetIndex = null
            },
            onUpdateFilamentSlotNozzle = onUpdateFilamentSlotNozzle,
            onRemoveFilamentSlot = { slotIndex ->
                onRemoveFilamentSlot(slotIndex)
                selectedFilamentSlotSheetIndex = null
            },
            onMissingPrinterConnectionDismiss = { missingPrinterConnectionDialog = false }
        )
    }
}

@Composable
private fun ImportingModelOverlay(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(min = 180.dp, max = 280.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.size(14.dp))
            Text(
                text = "Importing model",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

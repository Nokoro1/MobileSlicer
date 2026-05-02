package com.mobileslicer.workspace

import android.graphics.Bitmap
import android.util.Log
import com.mobileslicer.appBackgroundGradient
import com.mobileslicer.profiles.ActiveSlicerConfiguration
import com.mobileslicer.profiles.FilamentProfile
import com.mobileslicer.profiles.PrintHostType
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.printerconnection.PrinterUploadAction
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.nativebridge.NativeEngineBridge
import com.mobileslicer.nativebridge.NativeEngineCalls
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.GcodePreviewDisplayMode
import com.mobileslicer.viewer.GcodePreviewPerformanceMode
import com.mobileslicer.viewer.PreviewRangeSuggestion
import com.mobileslicer.viewer.TouchModelViewerView
import com.mobileslicer.viewer.ViewerFailure
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.ViewerPlateObject
import com.mobileslicer.viewer.parsePreviewRangePlan
import com.mobileslicer.ui.theme.LocalAppDarkTheme
import com.mobileslicer.ui.theme.WorldViewColorOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val PreviewRangePlanningByteThreshold = 8_000_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
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
    workspaceMode: WorkspaceMode,
    sliceInProgress: Boolean,
    sendToPrinterInProgress: Boolean,
    hasGeneratedGcode: Boolean,
    canSendToPrinter: Boolean,
    currentGcodeFileName: String,
    sliceSummary: SliceResultSummary?,
    sliceTiming: SlicePipelineTiming?,
    previewEngineHandle: Long,
    previewSliceKey: Long,
    gcodePreviewPerformanceMode: GcodePreviewPerformanceMode,
    worldViewColor: WorldViewColorOption,
    modelTransform: ViewerModelTransform?,
    plateObjects: List<PlateObject>,
    filamentSlots: List<PlateFilamentSlot>,
    availableFilaments: List<FilamentProfile>,
    selectedPlateObjectId: Long?,
    onWorkspaceModeChanged: (WorkspaceMode) -> Unit,
    onModelTransformChanged: (ViewerModelTransform?) -> Unit,
    onPlateObjectSelected: (Long?) -> Unit,
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
    onAutoArrangeObjects: () -> Unit,
    onAddObject: () -> Unit,
    onOpenProfiles: () -> Unit,
    onSavePlate: (Bitmap?) -> Unit,
    onBack: () -> Unit,
    onFirstVisibleWorkspaceFrame: () -> Unit,
    onFirstVisiblePreviewFrame: () -> Unit,
    onSlice: () -> Unit,
    onExport: () -> Unit,
    onSendToPrinter: (PrinterUploadAction, String) -> Unit,
    onOpenPrinter: () -> Unit,
    onPrinterStatus: suspend (PrinterProfile) -> String,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalAppDarkTheme.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val accentColor = MaterialTheme.colorScheme.primary.toArgb()
    val worldColor = selectedWorkspaceWorldColor(worldViewColor).toArgb()
    var showTransformSheet by remember { mutableStateOf(false) }
    var showPrinterSendSheet by remember { mutableStateOf(false) }
    var showPreviewInfoSheet by remember { mutableStateOf(false) }
    var showObjectListSheet by remember { mutableStateOf(false) }
    var selectedFilamentSlotSheetIndex by remember { mutableStateOf<Int?>(null) }
    var missingPrinterConnectionDialog by remember { mutableStateOf(false) }
    var printerStatusMessage by remember(selectedPrinter.id) { mutableStateOf<String?>(null) }
    var workspaceControlsExpanded by rememberSaveable(workspaceMode, previewSliceKey, isLandscape) { mutableStateOf(!isLandscape) }
    val selectedPlateObject = selectedPlateObjectId?.let { selectedId ->
        plateObjects.firstOrNull { it.id == selectedId }
    } ?: plateObjects.singleOrNull()
    val filamentSlotsByIndex = filamentSlots.associateBy { it.index }
    val viewerPlateObjects = plateObjects.mapNotNull { objectOnPlate ->
        objectOnPlate.mesh?.let { mesh ->
            ViewerPlateObject(
                id = objectOnPlate.id,
                label = objectOnPlate.label,
                mesh = mesh,
                transform = objectOnPlate.transform,
                colorInt = filamentSlotsByIndex[objectOnPlate.filamentSlotIndex]?.colorHex?.let { slotColor(it).toArgb() },
                selected = objectOnPlate.id == selectedPlateObject?.id
            )
        }
    }
    val viewerSceneKey = when (workspaceMode) {
        WorkspaceMode.Preview -> "preview:$previewSliceKey:$previewEngineHandle"
        WorkspaceMode.Prepare -> "prepare:${selectedPrinter.id}:${modelFilePath.orEmpty()}"
    }
    var runtimeViewerFailure by remember(viewerSceneKey) { mutableStateOf<com.mobileslicer.viewer.ViewerFailure?>(null) }
    var runtimeViewerReady by remember(viewerSceneKey) { mutableStateOf(false) }
    var viewerView by remember { mutableStateOf<TouchModelViewerView?>(null) }
    var previewPathVisibility by remember(previewSliceKey) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var previewDisplayMode by remember(previewSliceKey) { mutableStateOf(GcodePreviewDisplayMode.Auto) }
    val viewerState = when {
        modelFormat == ImportedModelFormat.ThreeMf -> WorkspaceViewerState.Unsupported
        !modelLoaded || modelFilePath == null -> WorkspaceViewerState.Empty
        modelFormat != ImportedModelFormat.Stl -> WorkspaceViewerState.Error(
            title = "Unsupported viewer format",
            message = "Viewer format not supported in this build."
        )
        selectedPlateObject?.mesh != null -> WorkspaceViewerState.Loaded(selectedPlateObject.mesh)
        preparedMesh != null -> WorkspaceViewerState.Loaded(preparedMesh)
        !viewerPreparationError.isNullOrBlank() -> WorkspaceViewerState.Error(
            title = "STL parse failed",
            message = viewerPreparationError
        )
        else -> WorkspaceViewerState.Preparing
    }
    val loadedMesh = (viewerState as? WorkspaceViewerState.Loaded)?.mesh
    val effectiveModelTransform = selectedPlateObject?.transform ?: modelTransform ?: defaultViewerModelTransform(selectedPrinter.toBedSpec())
    val meshSummary = loadedMesh?.let { formatMeshSummary(it, modelFormat) }
    val sliceReady = loadedMesh != null || modelBounds != null || selectedPlateObject?.bounds != null
    val transformReady = selectedPlateObject != null || loadedMesh != null || preparedMesh != null || modelBounds != null
    val previewLayerCount = (sliceSummary?.layerChangeCount ?: 0).coerceAtLeast(1)
    val exactPreviewVertexBudget = gcodePreviewPerformanceMode.vertexBudget
    val maxRangeLayerSpan = Int.MAX_VALUE
    var previewLayerSelection by remember(previewSliceKey, previewLayerCount) {
        mutableStateOf(
            PreviewLayerSelection(
                mode = PreviewLayerMode.Range,
                singleLayer = previewLayerCount,
                rangeStartLayer = 1,
                rangeEndLayer = previewLayerCount
            )
        )
    }
    var previewLayerReloadToken by remember(previewSliceKey, previewLayerCount) { mutableLongStateOf(0L) }
    var exactPreviewPlanReady by remember(previewSliceKey, previewLayerCount) { mutableStateOf(false) }
    var autoPreviewRanges by remember(previewSliceKey, previewLayerCount) {
        mutableStateOf<List<PreviewRangeSuggestion>>(emptyList())
    }
    var autoPreviewRangeIndex by remember(previewSliceKey, previewLayerCount) { mutableIntStateOf(0) }
    val suppressPreviewRangeFailure = workspaceMode == WorkspaceMode.Preview &&
        autoPreviewRanges.size > 1 &&
        runtimeViewerFailure?.isGcodePreviewRangeTooLarge() == true
    val effectiveRuntimeViewerFailure = runtimeViewerFailure.takeUnless { suppressPreviewRangeFailure }
    val effectiveViewerState = effectiveRuntimeViewerFailure?.let { failure ->
        WorkspaceViewerState.Error(failure.title, failure.detail)
    } ?: when {
        loadedMesh != null && !runtimeViewerReady -> WorkspaceViewerState.Preparing
        else -> viewerState
    }
    val selectedPreviewLayerRange = when (previewLayerSelection.mode) {
        PreviewLayerMode.Single -> {
            val layer = previewLayerSelection.singleLayer.coerceIn(1, previewLayerCount)
            (layer - 1).toLong() to (layer - 1).toLong()
        }
        PreviewLayerMode.Range -> {
            val start = previewLayerSelection.rangeStartLayer.coerceIn(1, previewLayerCount)
            val unclampedEnd = previewLayerSelection.rangeEndLayer.coerceIn(start, previewLayerCount)
            val end = if (maxRangeLayerSpan >= previewLayerCount) {
                unclampedEnd
            } else {
                unclampedEnd.coerceAtMost(start + maxRangeLayerSpan - 1)
            }
            (start - 1).toLong() to (end - 1).toLong()
        }
    }
    val activePreviewChunkBounds = autoPreviewRanges
        .getOrNull(autoPreviewRangeIndex)
        ?.let { it.startLayer..it.endLayer }
    fun applyPreviewLayerSelection(selection: PreviewLayerSelection) {
        if (autoPreviewRanges.isNotEmpty()) {
            val nextIndex = previewRangeIndexForSelection(
                selection = selection,
                ranges = autoPreviewRanges,
                currentIndex = autoPreviewRangeIndex,
                layerCount = previewLayerCount
            )
            if (nextIndex != autoPreviewRangeIndex) {
                autoPreviewRangeIndex = nextIndex
                previewLayerReloadToken++
            }
        }
        previewLayerSelection = selection
    }
    LaunchedEffect(workspaceMode, previewSliceKey, previewLayerCount, previewEngineHandle, sliceSummary?.byteCount, exactPreviewVertexBudget) {
        if (workspaceMode != WorkspaceMode.Preview || previewSliceKey <= 0L || previewEngineHandle == 0L) {
            exactPreviewPlanReady = workspaceMode != WorkspaceMode.Preview
            return@LaunchedEffect
        }
        if (
            exactPreviewVertexBudget >= GcodePreviewPerformanceMode.HARD_VERTEX_CEILING &&
            (sliceSummary?.byteCount ?: 0) in 1 until PreviewRangePlanningByteThreshold
        ) {
            autoPreviewRanges = emptyList()
            autoPreviewRangeIndex = 0
            previewLayerSelection = PreviewLayerSelection(
                mode = PreviewLayerMode.Range,
                singleLayer = previewLayerCount,
                rangeStartLayer = 1,
                rangeEndLayer = previewLayerCount
            )
            exactPreviewPlanReady = true
            return@LaunchedEffect
        }
        exactPreviewPlanReady = false
        runtimeViewerFailure = null
        val plannedRanges = withContext(Dispatchers.Default) {
            val handle = NativeEngineHandle.fromRaw(previewEngineHandle) ?: return@withContext emptyList()
            val rawPlan = NativeEngineCalls.planLatestSlicePreviewRanges(
                handle = handle,
                minLayer = 0L,
                maxLayer = (previewLayerCount - 1).toLong(),
                vertexBudget = exactPreviewVertexBudget
            )
            parsePreviewRangePlan(rawPlan)
        }
        if (plannedRanges.size > 1) {
            autoPreviewRanges = plannedRanges
            autoPreviewRangeIndex = 0
            previewLayerSelection = plannedRanges.first().toPreviewLayerSelection()
        } else {
            autoPreviewRanges = emptyList()
            autoPreviewRangeIndex = 0
            previewLayerSelection = PreviewLayerSelection(
                mode = PreviewLayerMode.Range,
                singleLayer = previewLayerCount,
                rangeStartLayer = 1,
                rangeEndLayer = previewLayerCount
            )
        }
        exactPreviewPlanReady = true
    }
    LaunchedEffect(showPreviewInfoSheet, workspaceMode, previewSliceKey, previewEngineHandle, sliceSummary?.previewInfo?.hasRichData, sliceInProgress) {
        if (sliceInProgress || !showPreviewInfoSheet || workspaceMode != WorkspaceMode.Preview || previewSliceKey <= 0L || previewEngineHandle == 0L) return@LaunchedEffect
        if (sliceSummary?.previewInfo?.hasRichData == true) return@LaunchedEffect
        val enrichedSummary = withContext(Dispatchers.Default) {
            val handle = NativeEngineHandle.fromRaw(previewEngineHandle) ?: return@withContext null
            GcodeSummaryParser.fromNativeSummary(NativeEngineCalls.getEnrichedGcodeSummary(handle))
        }
        if (enrichedSummary != null) {
            onSliceSummaryChanged(enrichedSummary)
        }
    }
    LaunchedEffect(runtimeViewerReady, loadedMesh) {
        if (runtimeViewerReady && loadedMesh != null) {
            onFirstVisibleWorkspaceFrame()
        }
    }
    LaunchedEffect(loadedMesh, selectedPrinter.id, modelTransform, plateObjects.size) {
        if (plateObjects.isEmpty() && loadedMesh != null && modelTransform == null) {
            onModelTransformChanged(defaultViewerModelTransform(selectedPrinter.toBedSpec()))
        }
    }
    LaunchedEffect(runtimeViewerReady, workspaceMode, previewSliceKey) {
        if (runtimeViewerReady && workspaceMode == WorkspaceMode.Preview && previewSliceKey > 0L) {
            onFirstVisiblePreviewFrame()
        }
    }
    LaunchedEffect(runtimeViewerFailure, workspaceMode, previewSliceKey) {
        val suggestions = runtimeViewerFailure?.previewRangeSuggestions.orEmpty()
        if (workspaceMode != WorkspaceMode.Preview || suggestions.isEmpty()) {
            return@LaunchedEffect
        }
        val chunkRanges = suggestions.filter { it.label.startsWith("Range ") }.ifEmpty { suggestions }
        val firstRange = chunkRanges.firstOrNull() ?: return@LaunchedEffect
        autoPreviewRanges = chunkRanges
        autoPreviewRangeIndex = 0
        previewLayerSelection = firstRange.toPreviewLayerSelection()
        previewLayerReloadToken++
        runtimeViewerFailure = null
    }
    LaunchedEffect(viewerView, previewPathVisibility, workspaceMode, previewSliceKey, sliceSummary) {
        val view = viewerView ?: return@LaunchedEffect
        if (workspaceMode != WorkspaceMode.Preview || previewSliceKey <= 0L) return@LaunchedEffect
        sliceSummary?.previewInfo?.lineTypes.orEmpty().forEach { row ->
            val key = previewLineVisibilityKey(row)
            previewPathVisibility[key]?.let { visible ->
                view.setGcodePathVisibility(row.kind.nativeKind, row.nativeId, visible)
            }
        }
    }
    LaunchedEffect(workspaceMode) {
        if (workspaceMode != WorkspaceMode.Prepare) {
            showTransformSheet = false
        }
        if (workspaceMode != WorkspaceMode.Preview) {
            showPrinterSendSheet = false
            showPreviewInfoSheet = false
        }
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = appBackgroundGradient()
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
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
            previewEngineHandle = if (workspaceMode == WorkspaceMode.Preview && exactPreviewPlanReady) previewEngineHandle else 0L,
            previewSliceKey = if (workspaceMode == WorkspaceMode.Preview && exactPreviewPlanReady) previewSliceKey else 0L,
            gcodePreviewVertexBudget = exactPreviewVertexBudget,
            gcodeLayerMin = selectedPreviewLayerRange.first,
            gcodeLayerMax = selectedPreviewLayerRange.second,
            gcodeLayerReloadToken = previewLayerReloadToken,
            gcodeDisplayMode = if (sliceInProgress) null else previewDisplayMode,
            onRuntimeFailureChanged = { failure ->
                runtimeViewerFailure = if (
                    workspaceMode == WorkspaceMode.Preview &&
                    autoPreviewRanges.size > 1 &&
                    failure?.isGcodePreviewRangeTooLarge() == true
                ) {
                    null
                } else {
                    failure
                }
            },
            onViewerReadyChanged = { runtimeViewerReady = it },
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
                onPlateObjectSelected(objectId)
            },
            onViewerViewChanged = { view -> viewerView = view }
        )
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
            onModeClick = {
                val nextMode = if (workspaceMode == WorkspaceMode.Prepare) {
                    WorkspaceMode.Preview
                } else {
                    WorkspaceMode.Prepare
                }
                onWorkspaceModeChanged(nextMode)
            },
            modifier = Modifier.align(Alignment.TopStart)
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (workspaceMode == WorkspaceMode.Prepare && filamentSlots.isNotEmpty()) {
                WorkspaceFilamentStrip(
                    slots = filamentSlots,
                    selectedSlotIndex = selectedPlateObject?.filamentSlotIndex ?: 1,
                    onSlotClick = { selectedFilamentSlotSheetIndex = it },
                    onAddSlot = onAddFilamentSlot
                )
            }
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
                objectCount = plateObjects.size,
                selectedObjectLabel = selectedPlateObject?.label,
                onPreviewLayerSelectionChanged = { applyPreviewLayerSelection(it) },
                onPreviewLayerSelectionCommitted = {
                    // Range scrubbing must remain live. Commit records the final
                    // value only; rebuilding the native preview here makes
                    // bottom-to-top scrubbing visibly stall on large G-code.
                    applyPreviewLayerSelection(it)
                },
                onPreviousPreviewRangeChunk = {
                    if (autoPreviewRanges.isNotEmpty()) {
                        val nextIndex = if (autoPreviewRangeIndex <= 0) {
                            autoPreviewRanges.lastIndex
                        } else {
                            autoPreviewRangeIndex - 1
                        }
                        val nextRange = autoPreviewRanges[nextIndex]
                        autoPreviewRangeIndex = nextIndex
                        previewLayerSelection = nextRange.toPreviewLayerSelection()
                        previewLayerReloadToken++
                    }
                },
                onNextPreviewRangeChunk = {
                    if (autoPreviewRanges.isNotEmpty()) {
                        val nextIndex = (autoPreviewRangeIndex + 1) % autoPreviewRanges.size
                        val nextRange = autoPreviewRanges[nextIndex]
                        autoPreviewRangeIndex = nextIndex
                        previewLayerSelection = nextRange.toPreviewLayerSelection()
                        previewLayerReloadToken++
                    }
                },
                onControlsExpandedChange = { workspaceControlsExpanded = it },
                onModelTransformChanged = onModelTransformChanged,
                onOpenObjectList = { showObjectListSheet = true },
                onOpenProfiles = onOpenProfiles,
                onSavePlate = {
                    val view = viewerView
                    if (view == null) {
                        onSavePlate(null)
                    } else {
                        view.captureCurrentFrame { bitmap ->
                            onSavePlate(bitmap)
                        }
                    }
                },
                onSlice = onSlice,
                onExport = onExport,
                onShare = onShare
            )
        }
        if (showTransformSheet && workspaceMode == WorkspaceMode.Prepare) {
            TransformPopoverContent(
                printerBed = selectedPrinter.toBedSpec(),
                transform = effectiveModelTransform,
                onTransformChanged = onModelTransformChanged,
                onAutoOrientObjects = onAutoOrientObjects,
                onAutoArrangeObjects = onAutoArrangeObjects,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, start = 24.dp, end = 24.dp)
            )
        }
        if (showPrinterSendSheet && workspaceMode == WorkspaceMode.Preview) {
            PrinterSendSheet(
                sending = sendToPrinterInProgress,
                suggestedFileName = currentGcodeFileName,
                supportsUploadAndStart = selectedPrinter.printHostType != PrintHostType.SimplyPrint,
                supportsQueue = selectedPrinter.printHostType == PrintHostType.PrusaConnect ||
                    selectedPrinter.printHostType == PrintHostType.SimplyPrint,
                onUpload = { remoteFileName ->
                    showPrinterSendSheet = false
                    onSendToPrinter(PrinterUploadAction.UploadOnly, remoteFileName)
                },
                onUploadAndStart = { remoteFileName ->
                    showPrinterSendSheet = false
                    onSendToPrinter(PrinterUploadAction.UploadAndStart, remoteFileName)
                },
                onQueue = { remoteFileName ->
                    showPrinterSendSheet = false
                    onSendToPrinter(PrinterUploadAction.Queue, remoteFileName)
                },
                onDismiss = { showPrinterSendSheet = false }
            )
        }
        if (showPreviewInfoSheet && workspaceMode == WorkspaceMode.Preview && sliceSummary != null && !sliceInProgress) {
            PreviewInfoSheet(
                summary = sliceSummary,
                lineVisibility = previewPathVisibility,
                displayMode = previewDisplayMode,
                onDisplayModeChanged = { mode ->
                    previewDisplayMode = mode
                    viewerView?.setGcodeDisplayMode(mode)
                },
                onLineVisibilityChanged = { row, visible ->
                    val key = previewLineVisibilityKey(row)
                    previewPathVisibility = previewPathVisibility.toMutableMap().apply { put(key, visible) }
                    viewerView?.setGcodePathVisibility(row.kind.nativeKind, row.nativeId, visible)
                },
                onDismiss = { showPreviewInfoSheet = false }
            )
        }
        if (showObjectListSheet && workspaceMode == WorkspaceMode.Prepare && plateObjects.size > 1) {
            PlateObjectListSheet(
                plateObjects = plateObjects,
                filamentSlots = filamentSlots,
                selectedPlateObjectId = selectedPlateObject?.id,
                onObjectSelected = { objectId ->
                    onPlateObjectSelected(objectId)
                    showObjectListSheet = false
                },
                onDismiss = { showObjectListSheet = false }
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
                    onAssignToSelected = {
                        onAssignFilamentSlotToSelected(slot.index)
                        selectedFilamentSlotSheetIndex = null
                    },
                    onColorSelected = { colorHex ->
                        onUpdateFilamentSlotColor(slot.index, colorHex)
                    },
                    onFilamentSelected = { filament ->
                        onUpdateFilamentSlotProfile(slot.index, filament)
                        selectedFilamentSlotSheetIndex = null
                    },
                    onNozzleSelected = { physicalNozzleIndex ->
                        onUpdateFilamentSlotNozzle(slot.index, physicalNozzleIndex)
                    },
                    onRemoveSlot = {
                        onRemoveFilamentSlot(slot.index)
                        selectedFilamentSlotSheetIndex = null
                    },
                    onDismiss = { selectedFilamentSlotSheetIndex = null }
                )
            }
        }
        if (missingPrinterConnectionDialog) {
            AlertDialog(
                onDismissRequest = { missingPrinterConnectionDialog = false },
                confirmButton = {
                    TextButton(onClick = { missingPrinterConnectionDialog = false }) {
                        Text("OK")
                    }
                },
                title = { Text("Printer Connection") },
                text = {
                    Text("No printer connection established, go to Profiles, Printer, Connection to establish a connection.")
                }
            )
        }
    }
}

private fun PreviewRangeSuggestion.toPreviewLayerSelection(): PreviewLayerSelection =
    PreviewLayerSelection(
        mode = PreviewLayerMode.Range,
        singleLayer = startLayer,
        rangeStartLayer = startLayer,
        rangeEndLayer = endLayer
    )

private fun ViewerFailure.isGcodePreviewRangeTooLarge(): Boolean =
    title.contains("G-code preview range too large", ignoreCase = true) ||
        detail.contains("G-code preview is too large", ignoreCase = true) ||
        detail.contains("Vertex limit:", ignoreCase = true)

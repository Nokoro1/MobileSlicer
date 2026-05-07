package com.mobileslicer

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.PrintHostType
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.profiles.ProfilesScreen
import com.mobileslicer.profiles.activeConfiguration
import com.mobileslicer.profiles.toNativeSliceConfigBuildResult
import com.mobileslicer.printerconnection.PrinterConnectionChoicesResult
import com.mobileslicer.printerconnection.PrinterConnectionResult
import com.mobileslicer.printerconnection.BambuLanPrintOptions
import com.mobileslicer.printerconnection.PrinterUploadAction
import com.mobileslicer.printerconnection.SimplyPrintOAuthResult
import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.calibration.PrinterCalibrationsScreen
import com.mobileslicer.calibration.unsupportedFirmwareMessage
import com.mobileslicer.storage.GCODE_MIME_TYPE
import com.mobileslicer.storage.SavedProject
import com.mobileslicer.workspace.AppScreen
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.ModelLoadResult
import com.mobileslicer.workspace.PaintMode
import com.mobileslicer.workspace.PlateFilamentSlot
import com.mobileslicer.workspace.PlateFlushVolumes
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.PlateObjectGeometrySource
import com.mobileslicer.workspace.PrimeTowerPlacementOverride
import com.mobileslicer.workspace.SliceResult
import com.mobileslicer.workspace.WorkspaceMode
import com.mobileslicer.workspace.WorkspacePreparationResult
import com.mobileslicer.workspace.WorkspaceCutAxis
import com.mobileslicer.workspace.WorkspaceCutConnectorKind
import com.mobileslicer.workspace.WorkspaceCutConnectorShape
import com.mobileslicer.workspace.WorkspaceCutConnectorStyle
import com.mobileslicer.workspace.WorkspaceCutMode
import com.mobileslicer.workspace.WorkspaceCutRequest
import com.mobileslicer.workspace.WorkspacePlate
import com.mobileslicer.workspace.WorkspaceScreen
import com.mobileslicer.workspace.WorkspaceSessionViewModel
import com.mobileslicer.workspace.defaultViewerModelTransform
import com.mobileslicer.workspace.defaultWorkspacePlateLabel
import com.mobileslicer.workspace.toNativeCutMode
import com.mobileslicer.workspace.workspaceResponsivenessLogLine
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.GcodePreviewPerformanceMode
import com.mobileslicer.ui.theme.AccentPaletteOption
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.ViewerPaintMode
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.ui.theme.ThemeModeOption
import com.mobileslicer.ui.theme.WorldViewColorOption
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.nativebridge.NativeCutAttributes
import com.mobileslicer.nativebridge.NativeCutCalls
import com.mobileslicer.nativebridge.NativeCutRequest
import com.mobileslicer.nativebridge.NativeEngineCalls
import com.mobileslicer.nativebridge.NativePaintCalls
import com.mobileslicer.nativebridge.NativeSplitCallResult
import com.mobileslicer.nativebridge.NativeSplitCalls
import com.mobileslicer.nativebridge.NativeSplitMode
import com.mobileslicer.viewer.StlMeshParser
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val VerboseWorkspacePlanningLogs = false

private data class NativeCutPlane(
    val matrixRowMajor: DoubleArray,
    val rotationRowMajor: DoubleArray,
    val center: DoubleArray
)

private fun ViewerPaintMode.toWorkspacePaintMode(): PaintMode = when (this) {
    ViewerPaintMode.Support -> PaintMode.Support
    ViewerPaintMode.Seam -> PaintMode.Seam
    ViewerPaintMode.Color -> PaintMode.Color
    ViewerPaintMode.FuzzySkin -> PaintMode.FuzzySkin
}

private fun PlateObject.splitSourcePath(): String =
    when (val source = geometrySource) {
        is PlateObjectGeometrySource.ThreeMfMeshExtract -> source.originalPath
        else -> filePath
    }

private fun nativeCutPlane(
    offsetMm: Float,
    rotationXDegrees: Float,
    rotationYDegrees: Float,
    bounds: MeshBounds?
): NativeCutPlane {
    val rx = Math.toRadians(rotationXDegrees.toDouble())
    val ry = Math.toRadians(rotationYDegrees.toDouble())
    val cx = cos(rx)
    val sx = sin(rx)
    val cy = cos(ry)
    val sy = sin(ry)
    val normalX = sy * cx
    val normalY = -sx
    val normalZ = cy * cx
    val centerX = bounds?.let { (it.minX + it.maxX).toDouble() * 0.5 } ?: 0.0
    val centerY = bounds?.let { (it.minY + it.maxY).toDouble() * 0.5 } ?: 0.0
    val centerZ = bounds?.let { (it.minZ + it.maxZ).toDouble() * 0.5 } ?: 0.0
    val normalTravel = offsetMm.toDouble() - centerZ
    val planeX = centerX + normalX * normalTravel
    val planeY = centerY + normalY * normalTravel
    val planeZ = centerZ + normalZ * normalTravel
    val matrix = doubleArrayOf(
        cy, sy * sx, sy * cx, planeX,
        0.0, cx, -sx, planeY,
        -sy, cy * sx, cy * cx, planeZ,
        0.0, 0.0, 0.0, 1.0
    )
    val rotation = doubleArrayOf(
        cy, sy * sx, sy * cx, 0.0,
        0.0, cx, -sx, 0.0,
        -sy, cy * sx, cy * cx, 0.0,
        0.0, 0.0, 0.0, 1.0
    )
    return NativeCutPlane(
        matrixRowMajor = matrix,
        rotationRowMajor = rotation,
        center = doubleArrayOf(planeX, planeY, planeZ)
    )
}

private fun nativeCutConnectorsJson(request: WorkspaceCutRequest, plane: NativeCutPlane?): JSONArray {
    if (request.connectorKind == WorkspaceCutConnectorKind.None || plane == null) return JSONArray()
    val type = when (request.connectorKind) {
        WorkspaceCutConnectorKind.None -> "plug"
        WorkspaceCutConnectorKind.Plug -> "plug"
        WorkspaceCutConnectorKind.Dowel -> "dowel"
        WorkspaceCutConnectorKind.Snap -> "snap"
    }
    val style = when (request.connectorStyle) {
        WorkspaceCutConnectorStyle.Prism -> "prism"
        WorkspaceCutConnectorStyle.Frustum -> "frustum"
    }
    val shape = when (request.connectorShape) {
        WorkspaceCutConnectorShape.Triangle -> "triangle"
        WorkspaceCutConnectorShape.Square -> "square"
        WorkspaceCutConnectorShape.Hexagon -> "hexagon"
        WorkspaceCutConnectorShape.Circle -> "circle"
    }
    val connectorPositions = request.connectorPositions
    return JSONArray().apply {
        connectorPositions.forEachIndexed { index, position ->
            put(
                JSONObject()
                    .put("id", "connector-$index-$type")
                    .put("type", type)
                    .put("style", style)
                    .put("shape", shape)
                    .put(
                        "position",
                        JSONArray()
                            .put(position.xMm.toDouble())
                            .put(position.yMm.toDouble())
                            .put(position.zMm.toDouble())
                    )
                    .put("rotationMatrix", JSONArray().apply { plane.rotationRowMajor.forEach { put(it) } })
                    .put("radius", (request.connectorSizeMm * 0.5f).toDouble())
                    .put("height", request.connectorDepthMm.toDouble())
                    .put("radiusTolerance", (request.connectorSizeToleranceMm * 0.5f).toDouble())
                    .put("heightTolerance", request.connectorDepthToleranceMm.toDouble())
                    .put("zAngle", Math.toRadians(request.connectorRotationDegrees.toDouble()))
                    .put("snapBulgeProportion", (request.connectorSnapBulgePercent / 100f).toDouble())
                    .put("snapSpaceProportion", (request.connectorSnapSpacePercent / 100f).toDouble())
            )
        }
    }
}

private fun nativeCutGrooveJson(request: WorkspaceCutRequest): JSONObject? =
    if (request.mode != WorkspaceCutMode.Groove) null else JSONObject()
        .put("depth", request.grooveDepthMm.toDouble())
        .put("width", request.grooveWidthMm.toDouble())
        .put("flapsAngleRadians", Math.toRadians(request.grooveFlapAngleDegrees.toDouble()))
        .put("angleRadians", Math.toRadians(request.grooveAngleDegrees.toDouble()))
        .put("depthTolerance", request.grooveDepthToleranceMm.toDouble())
        .put("widthTolerance", request.grooveWidthToleranceMm.toDouble())

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ModelLoaderScreen(
    initialOutput: String,
    initialProfileStore: ProfileStore,
    initialSavedProjects: List<SavedProject>,
    savedProjectRootDir: File,
    appVersion: String,
    appPackageName: String,
    nativeEngineHandle: Long,
    themeMode: ThemeModeOption,
    accentPalette: AccentPaletteOption,
    worldViewColor: WorldViewColorOption,
    showAdvancedProfileSettings: Boolean,
    activeStylusPaintOnly: Boolean,
    gcodePreviewPerformanceMode: GcodePreviewPerformanceMode,
    onThemeModeSelected: (ThemeModeOption) -> Unit,
    onAccentPaletteSelected: (AccentPaletteOption) -> Unit,
    onWorldViewColorSelected: (WorldViewColorOption) -> Unit,
    onShowAdvancedProfileSettingsChanged: (Boolean) -> Unit,
    onActiveStylusPaintOnlyChanged: (Boolean) -> Unit,
    onGcodePreviewPerformanceModeSelected: (GcodePreviewPerformanceMode) -> Unit,
    onProfileStoreChanged: (ProfileStore) -> Unit,
    onSavedProjectsChanged: (List<SavedProject>) -> Unit,
    workspaceSession: WorkspaceSessionViewModel,
    onModelSelected: (Uri) -> ModelLoadResult,
    onWorkspaceMeshPreparationRequested: (String) -> WorkspacePreparationResult,
    onSliceRequested: (String, List<PlateObject>, String?, StlMesh?, MeshBounds?, PrinterBedSpec, ViewerModelTransform?, String?) -> SliceResult,
    onNativeAutoArrangeRequested: suspend (String, List<PlateObject>, PrinterBedSpec, Boolean) -> PlatePlanningOutcome<PlateAutoArrangeResult>,
    onNativeAutoOrientRequested: suspend (String, List<PlateObject>, Long?, PrinterBedSpec) -> PlatePlanningOutcome<PlateAutoOrientResult>,
    onNativePlatePlanningCancelRequested: () -> Unit,
    onExportRequested: (Uri, String) -> String,
    onSavedProjectNativeLoadRequested: (SavedProject?, List<PlateObject>, PrinterBedSpec) -> Boolean,
    onNativePlateCacheCurrent: (List<PlateObject>, PrinterBedSpec) -> Unit,
    onSendToPrinterRequested: suspend (String, String, PrinterProfile, PrinterUploadAction, BambuLanPrintOptions?, (Int) -> Unit) -> PrinterConnectionResult,
    onTestPrinterConnectionRequested: suspend (PrinterProfile) -> String,
    onPrinterStatusRequested: suspend (PrinterProfile) -> String,
    onDiscoverPrinterHostsRequested: suspend () -> PrinterConnectionChoicesResult,
    onBrowsePrinterConnectionTargetsRequested: suspend (PrinterProfile) -> PrinterConnectionChoicesResult,
    onBrowsePrinterConnectionGroupsRequested: suspend (PrinterProfile) -> PrinterConnectionChoicesResult,
    onSimplyPrintLoginRequested: suspend (PrinterProfile) -> SimplyPrintOAuthResult,
    onShareRequested: (String, String) -> String
) {
    val context = LocalContext.current
    var modelLoaded by workspaceSession.modelLoaded
    var currentModelLabel by workspaceSession.currentModelLabel
    var currentModelFilePath by workspaceSession.currentModelFilePath
    var currentPreparedMesh by workspaceSession.currentPreparedMesh
    var currentModelBounds by workspaceSession.currentModelBounds
    var currentViewerPreparationError by workspaceSession.currentViewerPreparationError
    var currentImportTiming by workspaceSession.currentImportTiming
    var currentWorkspacePreparationTiming by workspaceSession.currentWorkspacePreparationTiming
    var importStartedAtMs by workspaceSession.importStartedAtMs
    var firstVisibleWorkspaceFrameMs by workspaceSession.firstVisibleWorkspaceFrameMs
    var workspacePreparationTargetKey by remember { mutableStateOf<String?>(null) }
    var currentModelFormatName by workspaceSession.currentModelFormatName
    var workspaceStatus by workspaceSession.workspaceStatus
    var currentGcodeFilePath by workspaceSession.currentGcodeFilePath
    var currentSliceSummary by workspaceSession.currentSliceSummary
    var currentSliceTiming by workspaceSession.currentSliceTiming
    var currentGcodeFileName by workspaceSession.currentGcodeFileName
    var currentSlicePreviewKey by workspaceSession.currentSlicePreviewKey
    var currentModelTransform by workspaceSession.currentModelTransform
    val workspacePlates = workspaceSession.workspacePlates
    var activePlateId by workspaceSession.activePlateId
    var nextPlateId by workspaceSession.nextPlateId
    val plateObjects = workspaceSession.plateObjects
    val plateFilamentSlots = workspaceSession.plateFilamentSlots
    var plateFlushVolumes by workspaceSession.plateFlushVolumes
    var selectedPlateObjectId by workspaceSession.selectedPlateObjectId
    var nextPlateObjectId by workspaceSession.nextPlateObjectId
    var primeTowerPlacementOverride by remember { mutableStateOf<PrimeTowerPlacementOverride?>(null) }
    var currentCalibrationJob by remember { mutableStateOf<CalibrationJob?>(null) }
    var sliceInProgress by rememberSaveable { mutableStateOf(false) }
    var sendToPrinterInProgress by rememberSaveable { mutableStateOf(false) }
    var platePlanningInProgress by rememberSaveable { mutableStateOf(false) }
    var sliceCompletionResult by remember { mutableStateOf<SliceResult?>(null) }
    var workspaceEventBanner by remember { mutableStateOf<String?>(null) }
    var savePlateNamePrompt by remember { mutableStateOf<String?>(null) }
    var savePlateThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var workspaceMode by workspaceSession.workspaceMode
    var previewStartedAtMs by remember { mutableStateOf<Long?>(null) }
    var firstVisiblePreviewFrameMs by remember { mutableStateOf<Long?>(null) }
    var pendingExportGcodeFilePath by remember { mutableStateOf<String?>(null) }
    var profileStore by remember { mutableStateOf(initialProfileStore) }
    var savedProjects by remember { mutableStateOf(initialSavedProjects) }
    var currentScreen by workspaceSession.currentScreen
    var profilesReturnScreenName by workspaceSession.profilesReturnScreenName
    var currentSavedProjectId by workspaceSession.currentSavedProjectId
    var importInProgress by rememberSaveable { mutableStateOf(false) }
    var appendNextImportToPlate by remember { mutableStateOf(false) }
    var missingProfileDialogMessage by remember { mutableStateOf<String?>(null) }
    var printerBrowserUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var printerBrowserReturnScreenName by rememberSaveable { mutableStateOf(AppScreen.Workspace.name) }
    var printerUploadDialogMessage by remember { mutableStateOf<String?>(null) }
    var printerUploadDialogCanRetry by remember { mutableStateOf(false) }
    var printerUploadProgressPercent by remember { mutableStateOf<Int?>(null) }
    var printerUploadJob by remember { mutableStateOf<Job?>(null) }
    var lastPrinterUploadRequest by remember { mutableStateOf<PrinterUploadRequest?>(null) }
    var transformInvalidationKey by remember { mutableLongStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()

    if (workspaceStatus.isBlank()) {
        workspaceStatus = initialOutput
    }

    LaunchedEffect(transformInvalidationKey) {
        if (transformInvalidationKey == 0L) return@LaunchedEffect
        delay(350)
        workspaceSession.clearGeneratedPreviewState()
    }

    fun handleBackNavigation() {
        val plan = planModelLoaderBackNavigation(
            currentScreen = currentScreen,
            workspaceMode = workspaceMode,
            profilesReturnScreenName = profilesReturnScreenName,
            printerBrowserReturnScreenName = printerBrowserReturnScreenName
        )
        currentScreen = plan.screen
        workspaceMode = plan.workspaceMode
    }

    fun openPrinterBrowserFromWorkspace(url: String) {
        printerBrowserUrl = url
        printerBrowserReturnScreenName = AppScreen.Workspace.name
        currentScreen = AppScreen.PrinterBrowser
    }

    fun clearPrinterUploadDialog() {
        printerUploadDialogMessage = null
        printerUploadDialogCanRetry = false
    }

    fun startModelLoaderPrinterUpload(request: PrinterUploadRequest) {
        startPrinterUploadRequest(
            request = request,
            coroutineScope = coroutineScope,
            context = context,
            onSendToPrinterRequested = onSendToPrinterRequested,
            setSendInProgress = { sendToPrinterInProgress = it },
            isSendInProgress = { sendToPrinterInProgress },
            setProgress = { printerUploadProgressPercent = it },
            setJob = { printerUploadJob = it },
            setWorkspaceStatus = { workspaceStatus = it },
            setDialogMessage = { text, canRetry ->
                printerUploadDialogMessage = text
                printerUploadDialogCanRetry = canRetry
            },
            setBrowser = ::openPrinterBrowserFromWorkspace
        )
    }

    missingProfileDialogMessage?.let { message ->
        ModelLoaderMissingProfileDialog(
            message = message,
            onDismiss = { missingProfileDialogMessage = null }
        )
    }

    printerUploadDialogMessage?.let { message ->
        ModelLoaderPrinterUploadDialog(
            message = message,
            canRetry = canRetryPrinterUploadDialog(printerUploadDialogCanRetry, lastPrinterUploadRequest),
            onDismiss = ::clearPrinterUploadDialog,
            onRetry = {
                val request = lastPrinterUploadRequest ?: return@ModelLoaderPrinterUploadDialog
                clearPrinterUploadDialog()
                startModelLoaderPrinterUpload(request)
            }
        )
    }

    if (sendToPrinterInProgress) {
        ModelLoaderPrinterUploadProgressDialog(
            progressPercent = printerUploadProgressPercent,
            onCancel = {
                printerUploadJob?.cancel()
                sendToPrinterInProgress = false
                printerUploadProgressPercent = null
                workspaceStatus = uploadCancelledStatus()
            }
        )
    }

    fun updateProfileStore(transform: (ProfileStore) -> ProfileStore) {
        val next = transform(profileStore)
        profileStore = next
        onProfileStoreChanged(next)
    }

    val hasCompleteProfileSelection = profileStore.hasCompleteProfileSelection()

    if (!hasCompleteProfileSelection) {
        ModelLoaderIncompleteProfileGate(
            currentScreen = currentScreen,
            profileStore = profileStore,
            currentModelLabel = currentModelLabel,
            savedProjects = savedProjects,
            importInProgress = importInProgress,
            currentSavedProjectId = currentSavedProjectId,
            appVersion = appVersion,
            appPackageName = appPackageName,
            themeMode = themeMode,
            accentPalette = accentPalette,
            worldViewColor = worldViewColor,
            showAdvancedProfileSettings = showAdvancedProfileSettings,
            activeStylusPaintOnly = activeStylusPaintOnly,
            gcodePreviewPerformanceMode = gcodePreviewPerformanceMode,
            onThemeModeSelected = onThemeModeSelected,
            onAccentPaletteSelected = onAccentPaletteSelected,
            onWorldViewColorSelected = onWorldViewColorSelected,
            onShowAdvancedProfileSettingsChanged = onShowAdvancedProfileSettingsChanged,
            onActiveStylusPaintOnlyChanged = onActiveStylusPaintOnlyChanged,
            onGcodePreviewPerformanceModeSelected = onGcodePreviewPerformanceModeSelected,
            onProfileStoreChanged = { updated -> updateProfileStore { updated } },
            onSavedProjectsChanged = { next ->
                savedProjects = next
                onSavedProjectsChanged(next)
            },
            onCurrentSavedProjectIdChanged = { currentSavedProjectId = it },
            onCurrentScreenChanged = { currentScreen = it },
            onProfilesReturnScreenNameChanged = { profilesReturnScreenName = it },
            onPrinterBrowserRequested = { url, returnScreen ->
                printerBrowserUrl = url
                printerBrowserReturnScreenName = returnScreen.name
                currentScreen = AppScreen.PrinterBrowser
            },
            onMissingProfileMessage = { missingProfileDialogMessage = it },
            onBackNavigation = { handleBackNavigation() },
            onTestPrinterConnectionRequested = onTestPrinterConnectionRequested,
            onPrinterStatusRequested = onPrinterStatusRequested,
            onDiscoverPrinterHostsRequested = onDiscoverPrinterHostsRequested,
            onBrowsePrinterConnectionTargetsRequested = onBrowsePrinterConnectionTargetsRequested,
            onBrowsePrinterConnectionGroupsRequested = onBrowsePrinterConnectionGroupsRequested,
            onSimplyPrintLoginRequested = onSimplyPrintLoginRequested
        )
        return
    }

    val sliceReadyProfileStore = profileStore
    val activeConfiguration = sliceReadyProfileStore.activeConfiguration()
    val selectedPrinter = activeConfiguration.printer
    val selectedFilament = activeConfiguration.filament
    val selectedProcess = activeConfiguration.process
    LaunchedEffect(
        selectedPrinter.id,
        selectedPrinter.hashCode(),
        selectedFilament.id,
        selectedFilament.hashCode(),
        selectedProcess.id,
        selectedProcess.contentHash()
    ) {
        withContext(Dispatchers.Default) {
            activeConfiguration.toNativeSliceConfigBuildResult(context.applicationContext)
        }
    }
    LaunchedEffect(selectedPrinter.id, currentSavedProjectId) {
        if (currentSavedProjectId == null) {
            val printerMaterialSlotState = loadPrinterMaterialSlotState(
                context = context,
                printerId = selectedPrinter.id,
                availableFilaments = sliceReadyProfileStore.filaments,
                fallbackFilament = selectedFilament
            )
            val printerSlots = printerMaterialSlotState.slots
            plateFilamentSlots.clear()
            plateFilamentSlots.addAll(printerSlots)
            plateFlushVolumes = ensureFlushVolumesForSlots(
                slots = printerSlots,
                existing = printerMaterialSlotState.flushVolumes,
                regenerateFromColors = printerMaterialSlotState.flushVolumes == null
            )
            plateObjects.replaceAll { objectOnPlate ->
                objectOnPlate.copy(filamentSlotIndex = objectOnPlate.filamentSlotIndex.coerceIn(1, printerSlots.size.coerceAtLeast(1)))
            }
        }
    }
    LaunchedEffect(
        selectedPrinter.id,
        currentSavedProjectId,
        sliceReadyProfileStore.filaments
            .filter { it.printerProfileId == selectedPrinter.id || it.printerProfileId.isBlank() }
            .map { it.id to it.hashCode() }
            .hashCode()
    ) {
        if (plateFilamentSlots.isNotEmpty()) {
            val syncedSlots = syncPlateFilamentSlotsWithProfiles(
                slots = plateFilamentSlots.toList(),
                availableFilaments = sliceReadyProfileStore.filaments,
                fallbackFilament = selectedFilament
            )
            if (syncedSlots != plateFilamentSlots.toList()) {
                plateFilamentSlots.clear()
                plateFilamentSlots.addAll(syncedSlots)
                plateFlushVolumes = ensureFlushVolumesForSlots(
                    slots = syncedSlots,
                    existing = plateFlushVolumes,
                    regenerateFromColors = true
                )
                if (currentSavedProjectId == null) {
                    persistPrinterMaterialSlotState(context, selectedPrinter.id, syncedSlots, plateFlushVolumes)
                }
                workspaceSession.clearGeneratedPreviewState()
            }
        }
    }

    fun updateSavedProjects(next: List<SavedProject>) {
        savedProjects = normalizedSavedProjects(next)
        onSavedProjectsChanged(savedProjects)
        pruneInactiveSavedProjectDirectories(savedProjectRootDir, savedProjects)
    }

    fun activePlateIndex(): Int = workspacePlates.indexOfFirst { it.id == activePlateId }

    fun currentWorkspacePlatesSnapshot(): List<WorkspacePlate> {
        if (workspacePlates.isEmpty()) {
            return listOf(
                WorkspacePlate(
                    id = activePlateId,
                    label = defaultWorkspacePlateLabel(1),
                    objects = plateObjects.toList(),
                    selectedObjectId = selectedPlateObjectId
                )
            )
        }
        val activeIndex = activePlateIndex()
        return workspacePlates.mapIndexed { index, plate ->
            if (index == activeIndex) {
                plate.copy(
                    objects = plateObjects.toList(),
                    selectedObjectId = selectedPlateObjectId,
                    gcodeFilePath = null,
                    sliceSummary = null,
                    sliceTiming = null,
                    gcodeFileName = "mobile_slicer_output.gcode",
                    slicePreviewKey = 0L
                )
            } else {
                plate
            }
        }
    }

    fun saveActivePlateSnapshot() {
        if (workspacePlates.isEmpty()) {
            workspacePlates.add(
                WorkspacePlate(
                    id = activePlateId,
                    label = defaultWorkspacePlateLabel(1),
                    objects = plateObjects.toList(),
                    selectedObjectId = selectedPlateObjectId
                )
            )
            nextPlateId = (activePlateId + 1L).coerceAtLeast(nextPlateId)
            return
        }
        val index = activePlateIndex().takeIf { it >= 0 } ?: 0
        activePlateId = workspacePlates[index].id
        workspacePlates[index] = workspacePlates[index].copy(
            objects = plateObjects.toList(),
            selectedObjectId = selectedPlateObjectId,
            gcodeFilePath = null,
            sliceSummary = null,
            sliceTiming = null,
            gcodeFileName = "mobile_slicer_output.gcode",
            slicePreviewKey = 0L
        )
    }

    fun syncSelectedObjectToLegacyState(objectOnPlate: PlateObject?) {
        val syncPlan = planSelectedObjectSync(objectOnPlate)
        val legacyState = syncPlan.legacyState
        selectedPlateObjectId = syncPlan.selectedPlateObjectId
        modelLoaded = legacyState.modelLoaded
        currentModelLabel = legacyState.modelLabel
        currentModelFilePath = legacyState.modelFilePath
        currentPreparedMesh = syncPlan.preparedMesh
        currentModelBounds = legacyState.modelBounds
        currentViewerPreparationError = syncPlan.viewerPreparationError
        currentImportTiming = legacyState.importTiming
        currentWorkspacePreparationTiming = syncPlan.workspacePreparationTiming
        currentModelFormatName = legacyState.modelFormatName
        currentModelTransform = syncPlan.modelTransform
    }

    fun applyWorkspacePlateMutation(mutation: WorkspacePlateMutation) {
        if (mutation.clearGeneratedPreviewState) {
            workspaceSession.clearGeneratedPreviewState()
        }
        workspacePlates.clear()
        workspacePlates.addAll(mutation.plates)
        activePlateId = mutation.activePlateId
        plateObjects.clear()
        plateObjects.addAll(mutation.activeObjects)
        nextPlateId = mutation.nextPlateId
        syncSelectedObjectToLegacyState(
            mutation.selectedObjectId?.let { id -> mutation.activeObjects.firstOrNull { it.id == id } }
                ?: mutation.activeObjects.firstOrNull()
        )
        workspaceStatus = mutation.statusMessage
    }

    fun loadWorkspacePlate(plate: WorkspacePlate) {
        workspaceSession.clearGeneratedPreviewState()
        plateObjects.clear()
        plateObjects.addAll(plate.objects)
        syncSelectedObjectToLegacyState(
            plate.selectedObjectId?.let { selectedId -> plate.objects.firstOrNull { it.id == selectedId } }
                ?: plate.objects.firstOrNull()
        )
        workspaceMode = WorkspaceMode.Prepare
        workspaceStatus = "${plate.label} active"
    }

    fun switchWorkspacePlate(plateId: Long) {
        if (plateId == activePlateId) return
        saveActivePlateSnapshot()
        val target = workspacePlates.firstOrNull { it.id == plateId } ?: return
        activePlateId = target.id
        loadWorkspacePlate(target)
    }

    fun addWorkspacePlate() {
        saveActivePlateSnapshot()
        applyWorkspacePlateMutation(
            addWorkspacePlateMutation(
                plates = workspacePlates.toList(),
                activePlateId = activePlateId,
                activeObjects = plateObjects.toList(),
                selectedObjectId = selectedPlateObjectId,
                nextPlateId = nextPlateId
            )
        )
    }

    fun duplicateActiveWorkspacePlate() {
        saveActivePlateSnapshot()
        val firstObjectId = nextPlateObjectId
        val mutation = duplicateActiveWorkspacePlateMutation(
            plates = workspacePlates.toList(),
            activePlateId = activePlateId,
            activeObjects = plateObjects.toList(),
            selectedObjectId = selectedPlateObjectId,
            nextPlateId = nextPlateId,
            firstObjectId = firstObjectId
        ) ?: return
        nextPlateObjectId = firstObjectId + (mutation.plates.lastOrNull()?.objects?.size ?: 0)
        applyWorkspacePlateMutation(mutation)
    }

    fun moveWorkspaceObjectToPlate(objectId: Long, targetPlateId: Long) {
        moveWorkspaceObjectToPlateMutation(
            plates = workspacePlates.toList(),
            activePlateId = activePlateId,
            activeObjects = plateObjects.toList(),
            selectedObjectId = selectedPlateObjectId,
            objectId = objectId,
            targetPlateId = targetPlateId,
            nextPlateId = nextPlateId
        )?.let(::applyWorkspacePlateMutation)
    }

    fun moveWorkspaceObjectToNewPlate(objectId: Long) {
        val mutation = moveWorkspaceObjectToNewPlateMutation(
            plates = workspacePlates.toList(),
            activePlateId = activePlateId,
            activeObjects = plateObjects.toList(),
            selectedObjectId = selectedPlateObjectId,
            objectId = objectId,
            nextPlateId = nextPlateId
        ) ?: return
        applyWorkspacePlateMutation(mutation)
    }

    fun deleteActiveWorkspacePlate() {
        if (workspacePlates.size <= 1) {
            workspaceStatus = "Cannot delete the only plate"
            return
        }
        val index = activePlateIndex()
        if (index < 0) return
        workspacePlates.removeAt(index)
        val nextIndex = index.coerceAtMost(workspacePlates.lastIndex)
        val nextPlate = workspacePlates[nextIndex]
        activePlateId = nextPlate.id
        loadWorkspacePlate(nextPlate)
    }

    fun renameActiveWorkspacePlate(name: String) {
        val index = activePlateIndex()
        if (index < 0) return
        val fallback = defaultWorkspacePlateLabel(index + 1)
        val nextName = name.trim().ifBlank { fallback }
        if (workspacePlates[index].label == nextName) return
        workspacePlates[index] = workspacePlates[index].copy(label = nextName)
    }

    fun saveCurrentPlate(projectName: String, thumbnailBitmap: Bitmap?) {
        val platesForSave = currentWorkspacePlatesSnapshot()
        if (platesForSave.all { it.objects.isEmpty() }) {
            workspaceStatus = noPlateToSaveStatus()
            return
        }
        saveActivePlateSnapshot()
        val projectIdForSave = currentSavedProjectId ?: "project_${java.util.UUID.randomUUID()}"
        val nativeProjectFile = File(File(savedProjectRootDir, projectIdForSave), "native-project.3mf")
        val nativeProjectFilePath = NativeEngineHandle.fromRaw(nativeEngineHandle)?.let { handle ->
            nativeProjectFile.parentFile?.mkdirs()
            when (NativeEngineCalls.writeProject3mfToFile(handle, nativeProjectFile.absolutePath)) {
                is com.mobileslicer.nativebridge.NativeEngineCallResult.Success ->
                    nativeProjectFile.absolutePath.takeIf { nativeProjectFile.exists() }
                is com.mobileslicer.nativebridge.NativeEngineCallResult.Failure -> null
            }
        }
        val project = buildSavedProject(
            currentSavedProjectId = currentSavedProjectId,
            projectIdOverride = projectIdForSave,
            projectName = projectName,
            projectNameFallback = suggestedSavedProjectName(plateObjects = plateObjects, currentModelLabel = currentModelLabel),
            savedProjectRootDir = savedProjectRootDir,
            profileStore = profileStore,
            plateObjects = plateObjects.toList(),
            workspacePlates = platesForSave,
            plateFilamentSlots = plateFilamentSlots.toList(),
            plateFlushVolumes = plateFlushVolumes,
            nativeProjectFilePath = nativeProjectFilePath,
            thumbnailBitmap = thumbnailBitmap
        )
        val update = savedProjectsAfterSave(project, savedProjects)
        updateSavedProjects(update.projects)
        currentSavedProjectId = update.currentSavedProjectId
        workspaceStatus = plateSavedStatus(project)
        workspaceEventBanner = "Save Successful"
    }
    fun openSavedProject(project: SavedProject) {
        coroutineScope.launch {
            val startPlan = planSavedProjectOpenStart(project)
            importInProgress = startPlan.importInProgress
            importStartedAtMs = SystemClock.elapsedRealtime()
            firstVisibleWorkspaceFrameMs = startPlan.firstVisibleWorkspaceFrameMs
            firstVisiblePreviewFrameMs = startPlan.firstVisiblePreviewFrameMs
            workspaceStatus = startPlan.statusMessage
            val openedProject = withContext(Dispatchers.Default) {
                openSavedProjectState(project)
            }
            if (openedProject == null) {
                val failurePlan = planSavedProjectOpenMissingFiles()
                importInProgress = failurePlan.importInProgress
                workspaceStatus = failurePlan.statusMessage
                return@launch
            }
            profileStore = project.profileStore
            onProfileStoreChanged(project.profileStore)
            currentCalibrationJob = null
            workspacePlates.clear()
            workspacePlates.addAll(openedProject.plates)
            activePlateId = openedProject.plates.firstOrNull()?.id ?: 1L
            nextPlateId = ((openedProject.plates.maxOfOrNull { it.id } ?: 0L) + 1L).coerceAtLeast(2L)
            plateObjects.clear()
            plateObjects.addAll(openedProject.plateObjects)
            plateFilamentSlots.clear()
            plateFilamentSlots.addAll(openedProject.filamentSlots)
            plateFlushVolumes = openedProject.flushVolumes
            nextPlateObjectId = openedProject.nextPlateObjectId
            currentSavedProjectId = project.id
            workspaceSession.clearGeneratedPreviewState()
            syncSelectedObjectToLegacyState(openedProject.plateObjects.first())
            val nativeWarmLoadSucceeded = withContext(Dispatchers.IO) {
                onSavedProjectNativeLoadRequested(project, openedProject.plateObjects, openedProject.printerBed)
            }
            workspaceStatus = savedProjectLoadedStatus(project, nativeWarmLoadSucceeded)
            importInProgress = false
            currentScreen = AppScreen.Workspace
        }
    }

    fun deleteSavedProject(project: SavedProject) {
        deleteSavedProjectDirectory(savedProjectRootDir, project)
        val update = savedProjectsAfterDelete(project, savedProjects, currentSavedProjectId)
        updateSavedProjects(update.projects)
        currentSavedProjectId = update.currentSavedProjectId
    }

    fun defaultPlateObjectTransform(existingObjectCount: Int): ViewerModelTransform {
        return defaultImportedPlateObjectTransform(
            bed = selectedPrinter.toBedSpec(),
            existingObjectCount = existingObjectCount
        )
    }

    fun effectiveGcodeFileName(): String {
        return effectiveGcodeFileName(
            calibrationJob = currentCalibrationJob,
            plateObjects = plateObjects.toList(),
            summary = currentSliceSummary,
            filamentMaterial = selectedFilament.materialType,
            fallbackName = currentGcodeFileName
        )
    }

    fun slicerFootprintClearanceMm(): Float = generatedFootprintClearanceMm(activeConfiguration.process)

    fun normalizeGeneratedFootprintBeforeSlice(): Boolean {
        val bed = selectedPrinter.toBedSpec()
        val clearance = slicerFootprintClearanceMm()
        if (clearance <= 0f) return true

        val rects = plateObjects.mapNotNull { objectOnPlate ->
            val bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds ?: return@mapNotNull null
            generatedFootprintRect(bounds, objectOnPlate.transform, clearance)
        }
        val union = if (rects.isNotEmpty()) {
            unionGeneratedFootprint(rects)
        } else {
            val bounds = currentPreparedMesh?.bounds ?: currentModelBounds
            val transform = currentModelTransform ?: defaultViewerModelTransform(bed)
            bounds?.let { generatedFootprintRect(it, transform, clearance) }
        } ?: return true

        val shift = shiftForGeneratedFootprint(union, bed) ?: return false
        val (dx, dy) = shift
        if (abs(dx) <= 0.05f && abs(dy) <= 0.05f) return true

        if (plateObjects.isNotEmpty()) {
            plateObjects.replaceAll { objectOnPlate ->
                objectOnPlate.copy(
                    transform = objectOnPlate.transform.copy(
                        centerXmm = (objectOnPlate.transform.centerXmm + dx).coerceIn(0f, bed.widthMm),
                        centerYmm = (objectOnPlate.transform.centerYmm + dy).coerceIn(0f, bed.depthMm)
                    )
                )
            }
            syncSelectedObjectToLegacyState(plateObjects.firstOrNull { it.id == selectedPlateObjectId } ?: plateObjects.firstOrNull())
            saveActivePlateSnapshot()
        } else {
            val transform = currentModelTransform ?: defaultViewerModelTransform(bed)
            currentModelTransform = transform.copy(
                centerXmm = (transform.centerXmm + dx).coerceIn(0f, bed.widthMm),
                centerYmm = (transform.centerYmm + dy).coerceIn(0f, bed.depthMm)
            )
        }
        transformInvalidationKey += 1
        workspaceSession.clearGeneratedPreviewState()
        if (VerboseWorkspacePlanningLogs) {
            Log.i(
                "MobileSlicer",
                "generated footprint shifted before slice dx=${String.format(Locale.US, "%.2f", dx)} dy=${String.format(Locale.US, "%.2f", dy)} " +
                    "clearance=${String.format(Locale.US, "%.2f", clearance)} union=${union.minX},${union.maxX},${union.minY},${union.maxY}"
            )
        }
        return true
    }

    fun printableVolumePreflightFailure(): String? = printableVolumePreflightFailure(
        plateObjects = plateObjects,
        fallbackBounds = currentPreparedMesh?.bounds ?: currentModelBounds,
        fallbackTransform = currentModelTransform,
        fallbackModelPath = currentModelFilePath,
        bed = selectedPrinter.toBedSpec(),
        clearance = slicerFootprintClearanceMm(),
        defaultTransform = defaultViewerModelTransform(selectedPrinter.toBedSpec())
    )

    fun applyAutoOrientResult(result: PlateAutoOrientResult, source: String) {
        plateObjects.clear()
        plateObjects.addAll(result.objects)
        workspaceSession.clearGeneratedPreviewState()
        transformInvalidationKey += 1
        syncSelectedObjectToLegacyState(plateObjects.firstOrNull { it.id == selectedPlateObjectId } ?: plateObjects.firstOrNull())
        saveActivePlateSnapshot()
        if (VerboseWorkspacePlanningLogs) {
            Log.i("MobileSlicer", "autoOrient $source applied: targets=${result.targetCount} changed=${result.changedCount}")
        }
        workspaceStatus = autoOrientPlateObjectsStatus(result)
    }

    fun currentPlatePlanningSignature(selectedObjectIdForPlanning: Long?): String {
        val currentConfiguration = profileStore.activeConfiguration()
        val currentPrinter = currentConfiguration.printer
        val currentFilament = currentConfiguration.filament
        val currentProcess = currentConfiguration.process
        val currentBed = currentPrinter.toBedSpec()
        val currentSlots = plateFilamentSlots.toList().ifEmpty {
            listOf(currentFilament.toPlateFilamentSlot(index = 1))
        }
        return buildString {
            append("selected=").append(selectedObjectIdForPlanning).append('|')
            append("printer=").append(currentPrinter.id).append(':').append(currentPrinter.hashCode()).append('|')
            append("filament=").append(currentFilament.id).append(':').append(currentFilament.hashCode()).append('|')
            append("process=").append(currentProcess.id).append(':').append(currentProcess.contentHash()).append('|')
            append("bed=").append(currentBed.originXmm).append(',')
                .append(currentBed.originYmm).append(',')
                .append(currentBed.widthMm).append(',')
                .append(currentBed.depthMm).append(',')
                .append(currentBed.maxHeightMm).append('|')
            append("slots=").append(currentSlots.joinToString(";")).append('|')
            append("flush=").append(plateFlushVolumes).append('|')
            append("objects=").append(
                plateObjects.joinToString(";") { objectOnPlate ->
                    listOf(
                        objectOnPlate.id,
                        objectOnPlate.filePath,
                        objectOnPlate.format,
                        objectOnPlate.filamentSlotIndex,
                        objectOnPlate.transform,
                        objectOnPlate.bounds
                    ).joinToString(":")
                }
            )
        }
    }

    fun abandonStalePlanningResult(capturedSignature: String, selectedObjectIdForPlanning: Long?, previousStatus: String, progressStatus: String): Boolean {
        if (currentPlatePlanningSignature(selectedObjectIdForPlanning) == capturedSignature) {
            return false
        }
        if (workspaceStatus == progressStatus) {
            workspaceStatus = previousStatus
        }
        if (VerboseWorkspacePlanningLogs) {
            Log.i("MobileSlicer", "plate planning result ignored because request inputs changed")
        }
        return true
    }

    fun autoOrientPlateObjects() {
        if (platePlanningInProgress) {
            workspaceStatus = "Planning already in progress\nWaiting for native planner"
            return
        }
        if (plateObjects.isEmpty()) {
            workspaceStatus = autoOrientPlateObjectsUnavailableStatus()
            return
        }
        val capturedObjects = plateObjects.toList()
        val capturedSelectedId = selectedPlateObjectId
        val bed = selectedPrinter.toBedSpec()
        val progressStatus = "Auto-orienting\nChecking model surfaces"
        val previousStatus = workspaceStatus
        val capturedSignature = currentPlatePlanningSignature(capturedSelectedId)
        val capturedSlots = plateFilamentSlots.toList().ifEmpty {
            listOf(selectedFilament.toPlateFilamentSlot(index = 1))
        }
        val capturedFilaments = sliceReadyProfileStore.filaments.toList()
        val capturedFlushVolumes = plateFlushVolumes
        coroutineScope.launch {
            platePlanningInProgress = true
            workspaceStatus = progressStatus
            try {
                val nativeOutcome = try {
                    val configJson = withContext(Dispatchers.Default) {
                        prepareNativeConfigForPlatePlanning(
                            context = context,
                            configuration = activeConfiguration,
                            plateObjects = capturedObjects,
                            profileFilaments = capturedFilaments,
                            activePlateSlots = capturedSlots,
                            flushVolumes = capturedFlushVolumes,
                            primeTowerPlacementOverride = primeTowerPlacementOverride,
                            printer = selectedPrinter
                        )
                    }
                    onNativeAutoOrientRequested(configJson, capturedObjects, capturedSelectedId, bed)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    Log.w("MobileSlicer", "autoOrient native planning failed", error)
                    PlatePlanningOutcome.Failure(nativeAutoOrientPlateObjectsFailureStatus(error.message))
                }
                if (abandonStalePlanningResult(capturedSignature, capturedSelectedId, previousStatus, progressStatus)) return@launch
                when (nativeOutcome) {
                    is PlatePlanningOutcome.Success -> applyAutoOrientResult(nativeOutcome.result, source = "native")
                    is PlatePlanningOutcome.Failure -> workspaceStatus = nativeOutcome.statusMessage
                }
            } finally {
                platePlanningInProgress = false
            }
        }
    }

    fun applyAutoArrangeResult(result: PlateAutoArrangeResult, source: String) {
        plateObjects.clear()
        plateObjects.addAll(result.objects)
        workspaceSession.clearGeneratedPreviewState()
        transformInvalidationKey += 1
        syncSelectedObjectToLegacyState(plateObjects.firstOrNull { it.id == selectedPlateObjectId } ?: plateObjects.firstOrNull())
        saveActivePlateSnapshot()
        if (VerboseWorkspacePlanningLogs) {
            Log.i(
                "MobileSlicer",
                "autoArrange $source applied: objects=${plateObjects.size} changed=${result.changedCount} centers=${result.centersSummary}"
            )
        }
        workspaceStatus = autoArrangePlateObjectsStatus(plateObjects.size, result)
    }

    fun arrangePlateObjects(allowRotation: Boolean) {
        val bed = selectedPrinter.toBedSpec()
        if (platePlanningInProgress) {
            workspaceStatus = "Planning already in progress\nWaiting for native planner"
            return
        }
        if (plateObjects.isEmpty()) {
            workspaceStatus = autoArrangePlateObjectsFailureStatus(plateObjects.size, bed)
            return
        }
        val capturedObjects = plateObjects.toList()
        val progressStatus = "Arranging objects\nChecking plate fit"
        val previousStatus = workspaceStatus
        val capturedSignature = currentPlatePlanningSignature(selectedObjectIdForPlanning = null)
        val capturedSlots = plateFilamentSlots.toList().ifEmpty {
            listOf(selectedFilament.toPlateFilamentSlot(index = 1))
        }
        val capturedFilaments = sliceReadyProfileStore.filaments.toList()
        val capturedFlushVolumes = plateFlushVolumes
        coroutineScope.launch {
            platePlanningInProgress = true
            workspaceStatus = progressStatus
            try {
                val nativeOutcome = try {
                    val configJson = withContext(Dispatchers.Default) {
                        prepareNativeConfigForPlatePlanning(
                            context = context,
                            configuration = activeConfiguration,
                            plateObjects = capturedObjects,
                            profileFilaments = capturedFilaments,
                            activePlateSlots = capturedSlots,
                            flushVolumes = capturedFlushVolumes,
                            primeTowerPlacementOverride = primeTowerPlacementOverride,
                            printer = selectedPrinter
                        )
                    }
                    onNativeAutoArrangeRequested(configJson, capturedObjects, bed, allowRotation)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    Log.w("MobileSlicer", "autoArrange native planning failed", error)
                    PlatePlanningOutcome.Failure(nativeAutoArrangePlateObjectsFailureStatus(error.message))
                }
                if (abandonStalePlanningResult(capturedSignature, selectedObjectIdForPlanning = null, previousStatus, progressStatus)) return@launch
                when (nativeOutcome) {
                    is PlatePlanningOutcome.Success -> applyAutoArrangeResult(nativeOutcome.result, source = "native")
                    is PlatePlanningOutcome.Failure -> workspaceStatus = nativeOutcome.statusMessage
                }
            } finally {
                platePlanningInProgress = false
            }
        }
    }

    fun cloneSelectedPlateObject() {
        val (clone, nextId) = cloneSelectedPlateObject(
            objects = plateObjects.toList(),
            selectedPlateObjectId = selectedPlateObjectId,
            nextPlateObjectId = nextPlateObjectId,
            bed = selectedPrinter.toBedSpec()
        )
        if (clone == null) return
        nextPlateObjectId = nextId
        plateObjects.add(clone)
        workspaceSession.clearGeneratedPreviewState()
        syncSelectedObjectToLegacyState(clone)
        saveActivePlateSnapshot()
        workspaceStatus = clonedPlateObjectStatus(plateObjects.size)
    }

    fun splitSelectedPlateObject(objectOnPlate: PlateObject, mode: NativeSplitMode) {
        val handle = NativeEngineHandle.fromRaw(nativeEngineHandle)
        if (handle == null) {
            workspaceEventBanner = "Split unavailable: the slicer is not ready."
            return
        }
        val sourcePath = objectOnPlate.splitSourcePath()
        if (!File(sourcePath).exists()) {
            workspaceEventBanner = "Split failed: source mesh is no longer available."
            return
        }
        val outputDir = File(
            context.filesDir,
            "split-results/${SystemClock.elapsedRealtime()}-${objectOnPlate.id}-${mode.label}"
        )
        workspaceEventBanner = if (mode == NativeSplitMode.Objects) "Splitting to objects..." else "Splitting to parts..."
        coroutineScope.launch {
            val result = withContext(Dispatchers.Default) {
                NativeSplitCalls.splitModelMesh(
                    handle = handle,
                    inputPath = sourcePath,
                    outputDirectory = outputDir.absolutePath,
                    mode = mode
                )
            }
            when (result) {
                is NativeSplitCallResult.Success -> {
                    val preparedObjects = withContext(Dispatchers.Default) {
                        result.result.objects.mapIndexedNotNull { index, splitObject ->
                            val file = File(splitObject.filePath).takeIf { it.exists() } ?: return@mapIndexedNotNull null
                            val prepared = runCatching { StlMeshParser.parseForDisplay(file) }.getOrNull()
                            val mesh = prepared?.mesh
                            val nextObjectId = nextPlateObjectId + index
                            PlateObject(
                                id = nextObjectId,
                                label = splitObject.label.ifBlank {
                                    if (mode == NativeSplitMode.Objects) "Split object ${index + 1}" else "Split part ${index + 1}"
                                },
                                filePath = file.absolutePath,
                                nativeSourceKey = "${sourcePath}:${mode.label}:${index}:${file.name}",
                                filamentSlotIndex = objectOnPlate.filamentSlotIndex,
                                format = ImportedModelFormat.Stl,
                                importTiming = null,
                                bounds = mesh?.bounds,
                                mesh = mesh,
                                viewerPreparationError = if (mesh == null) "Could not prepare split STL mesh." else null,
                                workspacePreparationTiming = prepared?.let {
                                    com.mobileslicer.workspace.WorkspacePreparationTiming(
                                        viewerMeshPrepMs = 0L,
                                        sourceTriangleCount = it.sourceTriangleCount,
                                        displayTriangleCount = it.displayTriangleCount,
                                        reducedForDisplay = it.reducedForDisplay
                                    )
                                },
                                transform = objectOnPlate.transform,
                                geometrySource = PlateObjectGeometrySource.StagedFile
                            )
                        }
                    }
                    if (preparedObjects.size <= 1) {
                        workspaceEventBanner = "Split failed: native split returned no multiple reloadable items."
                    } else {
                        val nextObjects = plateObjects.filterNot { it.id == objectOnPlate.id } + preparedObjects
                        plateObjects.clear()
                        plateObjects.addAll(nextObjects)
                        selectedPlateObjectId = preparedObjects.first().id
                        nextPlateObjectId = (plateObjects.maxOfOrNull { it.id } ?: 0L) + 1L
                        workspaceSession.clearGeneratedPreviewState()
                        workspaceMode = WorkspaceMode.Prepare
                        currentGcodeFilePath = null
                        currentSliceSummary = null
                        currentSliceTiming = null
                        syncSelectedObjectToLegacyState(preparedObjects.first())
                        saveActivePlateSnapshot()
                        workspaceEventBanner = if (mode == NativeSplitMode.Objects) {
                            "Split successful: ${preparedObjects.size} objects"
                        } else {
                            "Split successful: ${preparedObjects.size} parts"
                        }
                    }
                }
                is NativeSplitCallResult.Failure -> {
                    workspaceEventBanner = "Split failed: ${result.message}"
                }
                NativeSplitCallResult.Unavailable -> {
                    workspaceEventBanner = "Split unavailable in this build."
                }
            }
        }
    }

    fun startCalibrationJob(job: CalibrationJob) {
        workspaceSession.clearGeneratedPreviewState()
        currentGcodeFilePath = null
        currentSliceSummary = null
        currentSliceTiming = null
        currentSlicePreviewKey = 0L
        currentSavedProjectId = null
        currentCalibrationJob = job

        val calibrationDir = File(savedProjectRootDir.parentFile ?: savedProjectRootDir, "calibration-models")
        val bed = selectedPrinter.toBedSpec()
        val calibrationPlateResult = buildModelLoaderCalibrationPlate(
            context = context,
            calibrationDir = calibrationDir,
            job = job,
            bed = bed,
            firstObjectId = nextPlateObjectId,
            defaultTransform = ::defaultPlateObjectTransform
        ).getOrElse { error ->
            workspaceStatus = calibrationPlateCreationFailureStatus(error)
            return
        }
        nextPlateObjectId = calibrationPlateResult.nextObjectId

        plateObjects.clear()
        plateObjects.addAll(calibrationPlateResult.objects)
        if (plateFilamentSlots.isEmpty()) {
            plateFilamentSlots.add(selectedFilament.toPlateFilamentSlot(index = 1))
        }
        plateFlushVolumes = ensureFlushVolumesForSlots(
            slots = plateFilamentSlots.toList(),
            existing = plateFlushVolumes,
            regenerateFromColors = plateFlushVolumes == null
        )
        syncSelectedObjectToLegacyState(calibrationPlateResult.objects.first())
        saveActivePlateSnapshot()
        currentGcodeFileName = job.gcodeFileName()
        workspaceMode = WorkspaceMode.Prepare
        workspaceStatus = job.workspaceStatus()
        currentScreen = AppScreen.Workspace
    }

    fun updatePlateObject(objectId: Long, transform: (PlateObject) -> PlateObject) {
        val index = plateObjects.indexOfFirst { it.id == objectId }
        if (index >= 0) {
            val updated = transform(plateObjects[index])
            plateObjects[index] = updated
            if (selectedPlateObjectId == objectId) {
                syncSelectedObjectToLegacyState(plateObjects[index])
            }
            saveActivePlateSnapshot()
        }
    }

    fun applyNativePaintPayloadCommitResult(
        objectId: Long,
        result: ModelLoaderNativePaintCommitMutation
    ): Boolean {
        if (!result.changed) return false
        plateObjects.clear()
        plateObjects.addAll(result.objects)
        result.committedObject?.let { committed ->
            if (selectedPlateObjectId == objectId) {
                syncSelectedObjectToLegacyState(committed)
            }
        }
        currentSavedProjectId = result.currentSavedProjectId
        workspaceSession.clearGeneratedPreviewState(resetWorkspaceMode = false)
        saveActivePlateSnapshot()
        return true
    }

    fun applyPlateMutation(mutation: ModelLoaderPlateMutation, persistSlots: Boolean = true) {
        if (!mutation.changed) return
        plateFilamentSlots.clear()
        plateFilamentSlots.addAll(mutation.slots)
        plateObjects.clear()
        plateObjects.addAll(mutation.objects)
        plateFlushVolumes = mutation.flushVolumes
        if (persistSlots) {
            persistPrinterMaterialSlotState(context, selectedPrinter.id, mutation.slots, mutation.flushVolumes)
        }
        workspaceSession.clearGeneratedPreviewState()
        plateObjects.firstOrNull { it.id == selectedPlateObjectId }?.let { syncSelectedObjectToLegacyState(it) }
        saveActivePlateSnapshot()
    }

    fun logResponsivenessEvent(eventName: String) {
        Log.i(
            "MobileSlicerPerf",
            workspaceResponsivenessLogLine(
                eventName = eventName,
                importTiming = currentImportTiming,
                workspacePreparationTiming = currentWorkspacePreparationTiming,
                firstVisibleWorkspaceFrameMs = firstVisibleWorkspaceFrameMs,
                firstVisiblePreviewFrameMs = firstVisiblePreviewFrameMs,
                sliceTiming = currentSliceTiming
            )
        )
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val importStartState = planModelImportStart()
            importStartedAtMs = SystemClock.elapsedRealtime()
            importInProgress = importStartState.importInProgress
            if (importStartState.currentCalibrationJobCleared) currentCalibrationJob = null
            workspaceStatus = importStartState.statusMessage
            if (importStartState.clearGeneratedPreviewState) workspaceSession.clearGeneratedPreviewState()
            if (importStartState.clearPreparedMeshState) {
                currentPreparedMesh = null
                currentModelBounds = null
                currentViewerPreparationError = null
                currentImportTiming = null
                currentWorkspacePreparationTiming = null
                currentModelTransform = null
            }
            if (importStartState.clearFirstFrameTimings) firstVisibleWorkspaceFrameMs = null
            if (importStartState.clearWorkspacePreparationTarget) workspacePreparationTargetKey = null
            coroutineScope.launch {
                val result = withContext(Dispatchers.Default) {
                    onModelSelected(uri)
                }
                val importApplication = planModelImportApplication(
                    result = result,
                    currentScreen = currentScreen,
                    existingPlateObjects = plateObjects.toList(),
                    appendRequested = appendNextImportToPlate,
                    nextPlateObjectId = nextPlateObjectId,
                    defaultTransform = ::defaultPlateObjectTransform
                )
                if (importApplication.replacePlate) {
                    plateObjects.clear()
                }
                if (importApplication.clearSavedProject) {
                    currentSavedProjectId = null
                }
                nextPlateObjectId = importApplication.nextPlateObjectId
                importApplication.importedPlateObject?.let { objectOnPlate ->
                    plateObjects.add(objectOnPlate)
                    syncSelectedObjectToLegacyState(objectOnPlate)
                }
                importApplication.legacyState?.let { legacyState ->
                    modelLoaded = legacyState.modelLoaded
                    currentModelLabel = legacyState.modelLabel
                    currentModelFilePath = legacyState.modelFilePath
                    currentImportTiming = legacyState.importTiming
                    currentModelBounds = legacyState.modelBounds
                    currentModelFormatName = legacyState.modelFormatName
                }
                saveActivePlateSnapshot()
                logResponsivenessEvent("model_import_completed")
                workspaceStatus = importApplication.statusMessage
                val completionPlan = planModelImportCompletionUi(importApplication)
                importInProgress = completionPlan.importInProgress
                appendNextImportToPlate = completionPlan.appendNextImportToPlate
                if (completionPlan.clearGeneratedPreviewState) {
                    workspaceSession.clearGeneratedPreviewState()
                }
                completionPlan.screen?.let { screen ->
                    currentScreen = screen
                }
            }
        }
    }

    fun launchModelPickerOrPromptForProfiles(appendToPlate: Boolean) {
        profileStore.profileRequirementMessage()?.let { message ->
            missingProfileDialogMessage = message
            return
        }
        appendNextImportToPlate = appendToPlate
        modelPicker.launch(arrayOf("*/*"))
    }

    fun openCalibrationsOrPromptForProfiles() {
        profileStore.profileRequirementMessage()?.let { message ->
            missingProfileDialogMessage = message
            return
        }
        currentScreen = AppScreen.Calibrations
    }

    val workspacePreparationObject = selectedPlateObjectId?.let { objectId ->
        plateObjects.firstOrNull { it.id == objectId }
    }
    val workspacePreparationKey = workspacePreparationTargetKey(workspacePreparationObject, currentModelFilePath)

    LaunchedEffect(currentScreen, modelLoaded, currentModelFilePath, currentModelFormatName, workspacePreparationKey) {
        val request = resolveWorkspacePreparationRequest(
            currentScreen = currentScreen,
            modelLoaded = modelLoaded,
            currentModelFilePath = currentModelFilePath,
            currentModelFormatName = currentModelFormatName,
            currentImportTiming = currentImportTiming,
            selectedObject = workspacePreparationObject,
            currentPreparedMeshPresent = currentPreparedMesh != null,
            currentViewerPreparationError = currentViewerPreparationError,
            inProgressTargetKey = workspacePreparationTargetKey
        ) ?: return@LaunchedEffect

        workspacePreparationTargetKey = request.targetKey
        workspaceStatus = workspaceMeshPreparingStatus(request.modelFilePath, request.importTiming)

        try {
            val result = withContext(Dispatchers.Default) {
                onWorkspaceMeshPreparationRequested(request.modelFilePath)
            }
            val application = planWorkspacePreparationApplication(
                request = request,
                result = result,
                selectedPlateObjectId = selectedPlateObjectId,
                currentModelFilePath = currentModelFilePath,
                currentModelBounds = currentModelBounds
            )
            application.legacyState?.let { preparedState ->
                currentPreparedMesh = preparedState.preparedMesh
                currentModelBounds = preparedState.modelBounds
                currentViewerPreparationError = preparedState.viewerPreparationError
                currentWorkspacePreparationTiming = preparedState.workspacePreparationTiming
            }
            request.selectedObjectId?.let { objectId ->
                updatePlateObject(objectId) { objectOnPlate ->
                    applyWorkspacePreparationToPlateObject(objectOnPlate, request.modelFilePath, result)
                }
            }
            application.statusMessage?.let { statusMessage ->
                workspaceStatus = statusMessage
            }
            logResponsivenessEvent("workspace_mesh_prepared")
        } finally {
            workspacePreparationTargetKey = clearedWorkspacePreparationTarget(
                currentTargetKey = workspacePreparationTargetKey,
                completedTargetKey = request.targetKey
            )
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GCODE_MIME_TYPE)) { uri ->
        val gcodeFilePath = pendingExportGcodeFilePath
        workspaceStatus = if (uri == null || gcodeFilePath == null) {
            exportCancelledStatus()
        } else {
            onExportRequested(uri, gcodeFilePath)
        }
        pendingExportGcodeFilePath = null
    }

    LaunchedEffect(workspaceEventBanner) {
        if (workspaceEventBanner == null) return@LaunchedEffect
        delay(1100)
        workspaceEventBanner = null
    }

    sliceCompletionResult?.let { result ->
        if (result.sliced) {
            workspaceEventBanner = result.message.lineSequence().firstOrNull { it.isNotBlank() } ?: "Slice successful"
            sliceCompletionResult = null
        } else {
            ModelLoaderSliceCompletionDialog(
                result = result,
                activeConfiguration = activeConfiguration,
                onDismiss = { sliceCompletionResult = null }
            )
        }
    }

    savePlateNamePrompt?.let { initialName ->
        ModelLoaderSavePlateDialog(
            initialName = initialName,
            onDismiss = {
                savePlateNamePrompt = null
                savePlateThumbnail = null
            },
            onSave = { projectName ->
                savePlateNamePrompt = null
                val thumbnail = savePlateThumbnail
                savePlateThumbnail = null
                saveCurrentPlate(projectName, thumbnail)
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
            AppScreen.Home -> ModelLoaderHomeScreen(
                importedModel = currentModelLabel,
                printerTitle = selectedPrinter.name,
                filamentTitle = selectedFilament.name,
                processTitle = selectedProcess.name,
                projects = savedProjects,
                importInProgress = importInProgress,
                onOpenSettings = { currentScreen = AppScreen.Settings },
                onSelectModel = {
                    launchModelPickerOrPromptForProfiles(appendToPlate = false)
                },
                onCalibrationsClick = ::openCalibrationsOrPromptForProfiles,
                onProfilesClick = {
                    profilesReturnScreenName = AppScreen.Home.name
                    currentScreen = AppScreen.Profiles
                },
                onOpenProject = ::openSavedProject,
                onDeleteProject = ::deleteSavedProject,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            AppScreen.Workspace -> WorkspaceScreen(
                modelLabel = currentModelLabel,
                modelFilePath = currentModelFilePath,
                modelFormat = currentModelFormatName?.let(ImportedModelFormat::valueOf),
                modelLoaded = modelLoaded,
                preparedMesh = currentPreparedMesh,
                modelBounds = currentModelBounds,
                viewerPreparationError = currentViewerPreparationError,
                importTiming = currentImportTiming,
                workspacePreparationTiming = currentWorkspacePreparationTiming,
                firstVisibleWorkspaceFrameMs = firstVisibleWorkspaceFrameMs,
                firstVisiblePreviewFrameMs = firstVisiblePreviewFrameMs,
                selectedPrinter = selectedPrinter,
                activeConfiguration = activeConfiguration,
                workspaceStatus = workspaceStatus,
                workspaceMode = workspaceMode,
                sliceInProgress = sliceInProgress,
                sendToPrinterInProgress = sendToPrinterInProgress,
                hasGeneratedGcode = currentGcodeFilePath != null,
                canSendToPrinter = selectedPrinter.printHost.isNotBlank() ||
                    selectedPrinter.printHostType == PrintHostType.PrusaConnect ||
                    selectedPrinter.printHostType == PrintHostType.Obico ||
                    selectedPrinter.printHostType == PrintHostType.SimplyPrint,
                currentGcodeFileName = effectiveGcodeFileName(),
                sliceSummary = currentSliceSummary,
                sliceTiming = currentSliceTiming,
                previewEngineHandle = nativeEngineHandle,
                previewSliceKey = currentSlicePreviewKey,
                nativeEngineHandle = nativeEngineHandle,
                onPrepareNativePaintSessionRequested = { plateObjects, printerBed ->
                    onSavedProjectNativeLoadRequested(null, plateObjects, printerBed)
                },
                gcodePreviewPerformanceMode = gcodePreviewPerformanceMode,
                activeStylusPaintOnly = activeStylusPaintOnly,
                worldViewColor = worldViewColor,
                modelTransform = currentModelTransform,
                workspacePlates = currentWorkspacePlatesSnapshot(),
                activePlateId = activePlateId,
                plateObjects = plateObjects.toList(),
                filamentSlots = plateFilamentSlots.toList().ifEmpty {
                    listOf(selectedFilament.toPlateFilamentSlot(index = 1))
                },
                availableFilaments = sliceReadyProfileStore.filaments
                    .filter { it.printerProfileId == selectedPrinter.id || it.printerProfileId.isBlank() },
                selectedPlateObjectId = selectedPlateObjectId,
                primeTowerPlacementOverride = primeTowerPlacementOverride,
                onModelTransformChanged = { transform ->
                    val objectId = selectedPlateObjectId
                    if (objectId != null && transform != null) {
                        updatePlateObject(objectId) { it.copy(transform = transform) }
                    } else {
                        currentModelTransform = transform
                    }
                    transformInvalidationKey += 1
                    saveActivePlateSnapshot()
                },
                onActivePlateChanged = { plateId ->
                    switchWorkspacePlate(plateId)
                },
                onAddPlate = {
                    addWorkspacePlate()
                },
                onDuplicateActivePlate = {
                    duplicateActiveWorkspacePlate()
                },
                onDeleteActivePlate = {
                    deleteActiveWorkspacePlate()
                },
                onRenameActivePlate = { name ->
                    renameActiveWorkspacePlate(name)
                },
                onMoveObjectToPlate = { objectId, targetPlateId ->
                    moveWorkspaceObjectToPlate(objectId, targetPlateId)
                },
                onMoveObjectToNewPlate = { objectId ->
                    moveWorkspaceObjectToNewPlate(objectId)
                },
                onPlateObjectSelected = { objectId ->
                    if (objectId == null) {
                        if (plateObjects.size == 1) {
                            syncSelectedObjectToLegacyState(plateObjects.first())
                        } else {
                            selectedPlateObjectId = null
                        }
                    } else {
                        val selected = plateObjects.firstOrNull { objectOnPlate -> objectOnPlate.id == objectId }
                        syncSelectedObjectToLegacyState(selected)
                    }
                    saveActivePlateSnapshot()
                },
                onPrimeTowerPlacementChanged = { placement, committed ->
                    primeTowerPlacementOverride = placement
                    if (committed && currentGcodeFilePath != null) {
                        workspaceSession.clearGeneratedPreviewState(resetWorkspaceMode = false)
                    }
                },
                onAddFilamentSlot = {
                    applyPlateMutation(
                        addPlateFilamentSlot(
                            slots = plateFilamentSlots.toList(),
                            objects = plateObjects.toList(),
                            selectedFilament = selectedFilament,
                            flushVolumes = plateFlushVolumes
                        )
                    )
                },
                onAssignFilamentSlotToSelected = { slotIndex ->
                    selectedPlateObjectId?.let { objectId ->
                        updatePlateObject(objectId) { it.copy(filamentSlotIndex = slotIndex) }
                        workspaceSession.clearGeneratedPreviewState()
                    }
                },
                onUpdateFilamentSlotColor = { slotIndex, colorHex ->
                    applyPlateMutation(
                        updatePlateFilamentSlotColor(
                            slots = plateFilamentSlots.toList(),
                            objects = plateObjects.toList(),
                            flushVolumes = plateFlushVolumes,
                            slotIndex = slotIndex,
                            colorHex = colorHex
                        )
                    )
                },
                onUpdateFilamentSlotProfile = { slotIndex, filament ->
                    applyPlateMutation(
                        updatePlateFilamentSlotProfile(
                            slots = plateFilamentSlots.toList(),
                            objects = plateObjects.toList(),
                            flushVolumes = plateFlushVolumes,
                            slotIndex = slotIndex,
                            filament = filament
                        )
                    )
                },
                onUpdateFilamentSlotNozzle = { slotIndex, physicalNozzleIndex ->
                    applyPlateMutation(
                        updatePlateFilamentSlotNozzle(
                            slots = plateFilamentSlots.toList(),
                            objects = plateObjects.toList(),
                            flushVolumes = plateFlushVolumes,
                            slotIndex = slotIndex,
                            physicalNozzleIndex = physicalNozzleIndex
                        )
                    )
                },
                onSliceSummaryChanged = { currentSliceSummary = it },
                onRemoveFilamentSlot = { slotIndex ->
                    val paintRemapHandle = NativeEngineHandle.fromRaw(nativeEngineHandle)
                    applyPlateMutation(
                        removePlateFilamentSlot(
                            slots = plateFilamentSlots.toList(),
                            objects = plateObjects.toList(),
                            flushVolumes = plateFlushVolumes,
                            slotIndex = slotIndex,
                            colorSlotPayloadRemapper = paintRemapHandle?.let { handle ->
                                { payloadJson, oldSlotToNewSlot ->
                                    NativePaintCalls.remapColorSlots(
                                        handle = handle,
                                        payloadJson = payloadJson,
                                        oldSlotToNewSlot = oldSlotToNewSlot
                                    )
                                }
                            }
                        )
                    )
                },
                onDeleteSelectedObject = {
                    val result = deleteSelectedPlateObject(
                        objects = plateObjects.toList(),
                        selectedPlateObjectId = selectedPlateObjectId
                    )
                    if (result.changed) {
                        plateObjects.clear()
                        plateObjects.addAll(result.objects)
                        workspaceSession.clearGeneratedPreviewState()
                        currentGcodeFilePath = null
                        currentSliceSummary = null
                        currentSliceTiming = null
                        syncSelectedObjectToLegacyState(result.nextSelection)
                        saveActivePlateSnapshot()
                        workspaceStatus = result.statusMessage
                    }
                },
                onCloneSelectedObject = {
                    cloneSelectedPlateObject()
                },
                onSplitToObjectsRequested = { objectOnPlate ->
                    splitSelectedPlateObject(objectOnPlate, NativeSplitMode.Objects)
                },
                onSplitToPartsRequested = { objectOnPlate ->
                    splitSelectedPlateObject(objectOnPlate, NativeSplitMode.Parts)
                },
                onAutoOrientObjects = {
                    autoOrientPlateObjects()
                },
                onAutoArrangeObjects = { allowRotation ->
                    arrangePlateObjects(allowRotation)
                },
                onCutRequested = { objectOnPlate, cutRequest ->
                    val handle = NativeEngineHandle.fromRaw(nativeEngineHandle)
                    if (handle == null) {
                        workspaceStatus = "Cut unavailable: the slicer is not ready."
                    } else if (
                        cutRequest.connectorKind != WorkspaceCutConnectorKind.None &&
                        cutRequest.connectorPositions.isEmpty()
                    ) {
                        workspaceStatus = "Cut failed: place at least one connector on the cut face before cutting."
                    } else {
                        workspaceStatus = "Cutting ${objectOnPlate.label}..."
                        coroutineScope.launch {
                            val printerBed = selectedPrinter.toBedSpec()
                            val loaded = withContext(Dispatchers.Default) {
                                onSavedProjectNativeLoadRequested(null, plateObjects.toList(), printerBed)
                            }
                            if (!loaded) {
                                workspaceStatus = "Cut failed: native plate load rejected the current objects."
                                return@launch
                            }
                            val outputDir = File(context.filesDir, "cut-results/${SystemClock.elapsedRealtime()}-${objectOnPlate.id}")
                            val bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds
                            val cutPlane = nativeCutPlane(
                                cutRequest.heightMm,
                                cutRequest.rotationXDegrees,
                                cutRequest.rotationYDegrees,
                                bounds
                            )
                            val result = withContext(Dispatchers.Default) {
                                NativeCutCalls.cutObject(
                                    handle,
                                    NativeCutRequest(
                                        mobileObjectId = objectOnPlate.id,
                                        cutMatrixRowMajor = cutPlane.matrixRowMajor,
                                        mode = cutRequest.mode.toNativeCutMode(),
                                        attributes = NativeCutAttributes(
                                            keepUpper = cutRequest.keepUpper,
                                            keepLower = cutRequest.keepLower,
                                            keepAsParts = cutRequest.keepAsParts,
                                            flipUpper = cutRequest.flipUpper,
                                            flipLower = cutRequest.flipLower,
                                            placeOnCutUpper = cutRequest.placeOnCutUpper,
                                            placeOnCutLower = cutRequest.placeOnCutLower
                                        ),
                                        outputDirectory = outputDir.absolutePath,
                                        connectors = nativeCutConnectorsJson(cutRequest, cutPlane),
                                        groove = nativeCutGrooveJson(cutRequest)
                                    )
                                )
                            }
                            when (result) {
                                is com.mobileslicer.nativebridge.NativeCutCallResult.Success -> {
                                    val preparedObjects = withContext(Dispatchers.Default) {
                                        result.result.objects.mapNotNull { nativeObject ->
                                            val file = nativeObject.filePath?.let(::File)?.takeIf { it.exists() } ?: return@mapNotNull null
                                            val prepared = runCatching { StlMeshParser.parseForDisplay(file) }.getOrNull()
                                            val mesh = prepared?.mesh
                                            val cutTransform = mesh?.bounds?.let { cutBounds ->
                                                ViewerModelTransform(
                                                    centerXmm = cutBounds.centerX,
                                                    centerYmm = cutBounds.centerY,
                                                    rotationXDegrees = 0f,
                                                    rotationYDegrees = 0f,
                                                    rotationZDegrees = 0f,
                                                    uniformScale = 1f
                                                )
                                            } ?: objectOnPlate.transform.copy(
                                                rotationXDegrees = 0f,
                                                rotationYDegrees = 0f,
                                                rotationZDegrees = 0f,
                                                uniformScale = 1f
                                            )
                                            PlateObject(
                                                id = nativeObject.mobileObjectId,
                                                label = nativeObject.label,
                                                filePath = file.absolutePath,
                                                nativeSourceKey = "${result.result.cutGroupId}:${nativeObject.role}:${nativeObject.mobileObjectId}",
                                                filamentSlotIndex = objectOnPlate.filamentSlotIndex,
                                                format = ImportedModelFormat.Stl,
                                                importTiming = null,
                                                bounds = mesh?.bounds,
                                                mesh = mesh,
                                                viewerPreparationError = if (mesh == null) "Could not prepare cut STL mesh." else null,
                                                workspacePreparationTiming = prepared?.let {
                                                    com.mobileslicer.workspace.WorkspacePreparationTiming(
                                                        viewerMeshPrepMs = 0L,
                                                        sourceTriangleCount = it.sourceTriangleCount,
                                                        displayTriangleCount = it.displayTriangleCount,
                                                        reducedForDisplay = it.reducedForDisplay
                                                    )
                                                },
                                                transform = cutTransform,
                                                geometrySource = PlateObjectGeometrySource.NativeCutResult(
                                                    cutGroupId = result.result.cutGroupId,
                                                    sourceMobileObjectId = result.result.sourceMobileObjectId,
                                                    role = nativeObject.role,
                                                    resultJson = result.result.rawJson
                                                )
                                            )
                                        }
                                    }
                                    if (preparedObjects.isEmpty()) {
                                        workspaceStatus = "Cut failed: native cut returned no reloadable objects."
                                    } else {
                                        val nextObjects = plateObjects.filterNot { it.id == objectOnPlate.id } + preparedObjects
                                        plateObjects.clear()
                                        plateObjects.addAll(nextObjects)
                                        selectedPlateObjectId = preparedObjects.first().id
                                        nextPlateObjectId = (plateObjects.maxOfOrNull { it.id } ?: 0L) + 1L
                                        workspaceSession.clearGeneratedPreviewState()
                                        workspaceMode = WorkspaceMode.Prepare
                                        currentGcodeFilePath = null
                                        currentSliceSummary = null
                                        currentSliceTiming = null
                                        syncSelectedObjectToLegacyState(preparedObjects.first())
                                        onNativePlateCacheCurrent(plateObjects.toList(), printerBed)
                                        saveActivePlateSnapshot()
                                        workspaceStatus = "Cut complete: ${preparedObjects.size} object(s) created."
                                    }
                                }
                                is com.mobileslicer.nativebridge.NativeCutCallResult.Failure -> {
                                    workspaceStatus = "Cut failed: ${result.message}"
                                }
                                com.mobileslicer.nativebridge.NativeCutCallResult.Unavailable -> {
                                    workspaceStatus = "Cut unavailable in this build."
                                }
                            }
                        }
                    }
                },
                onAddObject = {
                    launchModelPickerOrPromptForProfiles(appendToPlate = true)
                },
                onWorkspaceModeChanged = { mode ->
                    if (mode == WorkspaceMode.Preview) {
                        previewStartedAtMs = SystemClock.elapsedRealtime()
                        firstVisiblePreviewFrameMs = null
                    }
                    workspaceMode = mode
                },
                onOpenProfiles = {
                    profilesReturnScreenName = AppScreen.Workspace.name
                    currentScreen = AppScreen.Profiles
                },
                onSavePlate = {
                    val projectObjects = currentWorkspacePlatesSnapshot().flatMap { it.objects }
                    when (val savePlan = planSavePlatePrompt(projectObjects, currentModelLabel)) {
                        is ModelLoaderSavePlatePromptPlan.Fail -> workspaceStatus = savePlan.statusMessage
                        is ModelLoaderSavePlatePromptPlan.Prompt -> {
                            savePlateThumbnail = it
                            savePlateNamePrompt = savePlan.suggestedName
                        }
                    }
                },
                onBack = { handleBackNavigation() },
                onFirstVisibleWorkspaceFrame = {
                    if (firstVisibleWorkspaceFrameMs == null) {
                        val startedAt = importStartedAtMs
                        if (startedAt != null) {
                            val firstFrameMs = SystemClock.elapsedRealtime() - startedAt
                            firstVisibleWorkspaceFrameMs = firstFrameMs
                            workspaceStatus = firstVisibleWorkspaceFrameStatus(workspaceStatus, firstFrameMs)
                            logResponsivenessEvent("workspace_first_visible_frame")
                        }
                    }
                },
                onFirstVisiblePreviewFrame = {
                    if (firstVisiblePreviewFrameMs == null) {
                        previewStartedAtMs?.let { startedAt ->
                            firstVisiblePreviewFrameMs = SystemClock.elapsedRealtime() - startedAt
                            logResponsivenessEvent("preview_first_visible_frame")
                        }
                    }
                },
                onSlice = {
                    val sliceStartPlan =
                        if (!canRequestModelLoaderSlice(modelLoaded, sliceInProgress, sendToPrinterInProgress)) {
                            ModelLoaderSliceStartPlan.Ignore
                        } else {
                            val generatedFootprintFits = normalizeGeneratedFootprintBeforeSlice()
                            val calibrationFirmwareFailure =
                                currentCalibrationJob?.unsupportedFirmwareMessage(selectedPrinter.gcodeFlavor)
                            planModelLoaderSliceStart(
                                modelLoaded = modelLoaded,
                                sliceInProgress = sliceInProgress,
                                sendToPrinterInProgress = sendToPrinterInProgress,
                                generatedFootprintFits = generatedFootprintFits,
                                printableVolumePreflightFailure = if (generatedFootprintFits) {
                                    calibrationFirmwareFailure ?: printableVolumePreflightFailure()
                                } else {
                                    null
                                },
                                nativeSliceTitle = activeConfiguration.nativeSliceTitle()
                            )
                        }
                    when (sliceStartPlan) {
                        ModelLoaderSliceStartPlan.Ignore -> Unit
                        is ModelLoaderSliceStartPlan.Fail -> {
                            sliceCompletionResult = sliceStartPlan.result
                        }
                        is ModelLoaderSliceStartPlan.Start -> {
                            val uiStartPlan = planModelLoaderSliceUiStart(sliceStartPlan.statusMessage)
                            sliceInProgress = uiStartPlan.sliceInProgress
                            currentGcodeFilePath = uiStartPlan.gcodeFilePath
                            currentSliceSummary = uiStartPlan.summary
                            currentSliceTiming = uiStartPlan.timing
                            currentSlicePreviewKey = uiStartPlan.previewKey
                            previewStartedAtMs = uiStartPlan.previewStartedAtMs
                            firstVisiblePreviewFrameMs = uiStartPlan.firstVisiblePreviewFrameMs
                            workspaceMode = uiStartPlan.workspaceMode
                            workspaceStatus = uiStartPlan.statusMessage
                            val sliceInputs = captureModelLoaderSliceRunInputs(
                                configuration = activeConfiguration,
                                calibrationJob = currentCalibrationJob,
                                plateObjects = plateObjects.toList(),
                                profileFilaments = sliceReadyProfileStore.filaments,
                                plateFilamentSlots = plateFilamentSlots.toList(),
                                fallbackFilament = selectedFilament,
                                flushVolumes = plateFlushVolumes,
                                primeTowerPlacementOverride = primeTowerPlacementOverride,
                                printer = selectedPrinter,
                                modelFilePath = currentModelFilePath,
                                preparedMesh = currentPreparedMesh,
                                modelBounds = currentModelBounds,
                                modelTransform = currentModelTransform,
                                gcodeFileName = effectiveGcodeFileName()
                            )
                            coroutineScope.launch {
                                val preparedResult = withContext(Dispatchers.Default) {
                                    runModelLoaderSlice(
                                        context = context.applicationContext,
                                        configuration = sliceInputs.configuration,
                                        calibrationJob = sliceInputs.calibrationJob,
                                        plateObjects = sliceInputs.plateObjects,
                                        profileFilaments = sliceInputs.profileFilaments,
                                        activePlateSlots = sliceInputs.activePlateSlots,
                                        flushVolumes = sliceInputs.flushVolumes,
                                        primeTowerPlacementOverride = sliceInputs.primeTowerPlacementOverride,
                                        printer = sliceInputs.printer,
                                        modelFilePath = sliceInputs.modelFilePath,
                                        preparedMesh = sliceInputs.preparedMesh,
                                        modelBounds = sliceInputs.modelBounds,
                                        modelTransform = sliceInputs.modelTransform,
                                        gcodeFileName = sliceInputs.gcodeFileName,
                                        onSliceRequested = onSliceRequested
                                    )
                                }
                                val completionPlan = planModelLoaderSliceCompletion(
                                    result = preparedResult,
                                    calibrationJob = sliceInputs.calibrationJob,
                                    fallbackFileName = sliceInputs.gcodeFileName,
                                    previousPreviewKey = currentSlicePreviewKey
                                )
                                currentGcodeFilePath = completionPlan.gcodeFilePath
                                currentSliceSummary = completionPlan.summary
                                currentSliceTiming = completionPlan.timing
                                currentGcodeFileName = completionPlan.gcodeFileName
                                currentSlicePreviewKey = completionPlan.previewKey
                                workspaceStatus = completionPlan.statusMessage
                                sliceInProgress = false
                                sliceCompletionResult = completionPlan.completionResult
                                logResponsivenessEvent("slice_completed")
                            }
                        }
                    }
                },
                onExport = {
                    planGcodeFileAction(
                        gcodeFilePath = currentGcodeFilePath,
                        calibrationJob = currentCalibrationJob,
                        plateObjects = plateObjects.toList(),
                        summary = currentSliceSummary,
                        filamentMaterial = selectedFilament.materialType,
                        fallbackName = currentGcodeFileName
                    )?.let { action ->
                        currentGcodeFileName = action.fileName
                        pendingExportGcodeFilePath = action.gcodeFilePath
                        exportLauncher.launch(action.fileName)
                    }
                },
                onSendToPrinter = { uploadAction, remoteFileName, bambuOptions ->
                    planPrinterUploadRequest(
                        gcodeFilePath = currentGcodeFilePath,
                        sendToPrinterInProgress = sendToPrinterInProgress,
                        calibrationJob = currentCalibrationJob,
                        remoteFileName = remoteFileName,
                        printerProfile = selectedPrinter,
                        uploadAction = uploadAction,
                        bambuOptions = bambuOptions
                    )?.let { request ->
                        lastPrinterUploadRequest = request
                        printerUploadDialogCanRetry = false
                        startModelLoaderPrinterUpload(request)
                    }
                },
                onOpenPrinter = {
                    printerOpenPlan(selectedPrinter)
                        .onSuccess { url ->
                            printerBrowserUrl = url
                            printerBrowserReturnScreenName = AppScreen.Workspace.name
                            currentScreen = AppScreen.PrinterBrowser
                        }
                        .onFailure { error ->
                            workspaceStatus = error.message.orEmpty()
                        }
                },
                onPrinterStatus = onPrinterStatusRequested,
                onShare = {
                    planGcodeFileAction(
                        gcodeFilePath = currentGcodeFilePath,
                        calibrationJob = currentCalibrationJob,
                        plateObjects = plateObjects.toList(),
                        summary = currentSliceSummary,
                        filamentMaterial = selectedFilament.materialType,
                        fallbackName = currentGcodeFileName
                    )?.let { action ->
                        currentGcodeFileName = action.fileName
                        workspaceStatus = onShareRequested(action.gcodeFilePath, action.fileName)
                    }
                },
                onNativePaintPayloadCommitted = { objectId, mode, payloadJson ->
                    Log.i(
                        "MobileSlicerPaint",
                        "native paint payload committed object=$objectId bytes=${payloadJson.length}"
                    )
                    val objectsSnapshot = plateObjects.toList()
                    val savedProjectIdSnapshot = currentSavedProjectId
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.Default) {
                            applyNativePaintPayloadCommit(
                                objects = objectsSnapshot,
                                currentSavedProjectId = savedProjectIdSnapshot,
                                objectId = objectId,
                                mode = mode.toWorkspacePaintMode(),
                                payloadJson = payloadJson
                            )
                        }
                        if (!applyNativePaintPayloadCommitResult(objectId, result)) {
                            workspaceStatus = "Paint update failed\nNative payload was not usable"
                        }
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )
            AppScreen.Profiles -> ProfilesScreen(
                store = profileStore,
                showAdvancedProfileSettings = showAdvancedProfileSettings,
                onStoreChanged = { updated -> updateProfileStore { updated } },
                onTestPrinterConnection = onTestPrinterConnectionRequested,
                onPrinterStatus = onPrinterStatusRequested,
                onDiscoverPrinterHosts = onDiscoverPrinterHostsRequested,
                onBrowsePrinterConnectionTargets = onBrowsePrinterConnectionTargetsRequested,
                onBrowsePrinterConnectionGroups = onBrowsePrinterConnectionGroupsRequested,
                onSimplyPrintLogin = onSimplyPrintLoginRequested,
                onOpenPrinterUi = { printer ->
                    printerOpenPlan(printer).onSuccess { url ->
                        printerBrowserUrl = url
                        printerBrowserReturnScreenName = AppScreen.Profiles.name
                        currentScreen = AppScreen.PrinterBrowser
                    }
                },
                onBack = { handleBackNavigation() },
                modifier = Modifier.padding(innerPadding)
            )
            AppScreen.Calibrations -> PrinterCalibrationsScreen(
                store = profileStore,
                onBack = { handleBackNavigation() },
                onStartCalibration = { job ->
                    startCalibrationJob(job)
                },
                modifier = Modifier.padding(innerPadding)
            )
            AppScreen.PrinterBrowser -> PrinterBrowserScreen(
                url = printerBrowserUrl.orEmpty(),
                printerName = selectedPrinter.name,
                onBack = { handleBackNavigation() },
                modifier = Modifier.padding(innerPadding)
            )
            AppScreen.Settings -> SettingsScreen(
                appVersion = appVersion,
                appPackageName = appPackageName,
                themeMode = themeMode,
                accentPalette = accentPalette,
                worldViewColor = worldViewColor,
                showAdvancedProfileSettings = showAdvancedProfileSettings,
                activeStylusPaintOnly = activeStylusPaintOnly,
                gcodePreviewPerformanceMode = gcodePreviewPerformanceMode,
                onThemeModeSelected = onThemeModeSelected,
                onAccentPaletteSelected = onAccentPaletteSelected,
                onWorldViewColorSelected = onWorldViewColorSelected,
                onShowAdvancedProfileSettingsChanged = onShowAdvancedProfileSettingsChanged,
                onActiveStylusPaintOnlyChanged = onActiveStylusPaintOnlyChanged,
                onGcodePreviewPerformanceModeSelected = onGcodePreviewPerformanceModeSelected,
                onBack = { handleBackNavigation() },
                modifier = Modifier.padding(innerPadding)
            )
            }

            workspaceEventBanner?.let { message ->
                ModelLoaderSuccessBanner(message)
            }
        }
    }

    BackHandler(enabled = currentScreen != AppScreen.Home) {
        handleBackNavigation()
    }
}

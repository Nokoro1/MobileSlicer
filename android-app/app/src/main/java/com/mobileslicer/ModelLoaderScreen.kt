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
import com.mobileslicer.printerconnection.PrinterUploadAction
import com.mobileslicer.printerconnection.SimplyPrintOAuthResult
import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.calibration.PrinterCalibrationsScreen
import com.mobileslicer.storage.GCODE_MIME_TYPE
import com.mobileslicer.storage.SavedProject
import com.mobileslicer.workspace.AppScreen
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.ModelLoadResult
import com.mobileslicer.workspace.PlateFilamentSlot
import com.mobileslicer.workspace.PlateFlushVolumes
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.SliceResult
import com.mobileslicer.workspace.WorkspaceMode
import com.mobileslicer.workspace.WorkspacePreparationResult
import com.mobileslicer.workspace.WorkspaceScreen
import com.mobileslicer.workspace.WorkspaceSessionViewModel
import com.mobileslicer.workspace.defaultViewerModelTransform
import com.mobileslicer.workspace.formatDurationMs
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
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.ui.theme.ThemeModeOption
import com.mobileslicer.ui.theme.WorldViewColorOption
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val VerboseWorkspacePlanningLogs = false

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
    gcodePreviewPerformanceMode: GcodePreviewPerformanceMode,
    onThemeModeSelected: (ThemeModeOption) -> Unit,
    onAccentPaletteSelected: (AccentPaletteOption) -> Unit,
    onWorldViewColorSelected: (WorldViewColorOption) -> Unit,
    onShowAdvancedProfileSettingsChanged: (Boolean) -> Unit,
    onGcodePreviewPerformanceModeSelected: (GcodePreviewPerformanceMode) -> Unit,
    onProfileStoreChanged: (ProfileStore) -> Unit,
    onSavedProjectsChanged: (List<SavedProject>) -> Unit,
    workspaceSession: WorkspaceSessionViewModel,
    onModelSelected: (Uri) -> ModelLoadResult,
    onWorkspaceMeshPreparationRequested: (String) -> WorkspacePreparationResult,
    onSliceRequested: (String, List<PlateObject>, String?, StlMesh?, MeshBounds?, PrinterBedSpec, ViewerModelTransform?, String?) -> SliceResult,
    onExportRequested: (Uri, String) -> String,
    onSavedProjectNativeLoadRequested: (List<PlateObject>, PrinterBedSpec) -> Boolean,
    onSendToPrinterRequested: suspend (String, String, PrinterProfile, PrinterUploadAction, (Int) -> Unit) -> PrinterConnectionResult,
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
    val plateObjects = workspaceSession.plateObjects
    val plateFilamentSlots = workspaceSession.plateFilamentSlots
    var plateFlushVolumes by workspaceSession.plateFlushVolumes
    var selectedPlateObjectId by workspaceSession.selectedPlateObjectId
    var nextPlateObjectId by workspaceSession.nextPlateObjectId
    var currentCalibrationJob by remember { mutableStateOf<CalibrationJob?>(null) }
    var sliceInProgress by rememberSaveable { mutableStateOf(false) }
    var sendToPrinterInProgress by rememberSaveable { mutableStateOf(false) }
    var sliceCompletionResult by remember { mutableStateOf<SliceResult?>(null) }
    var sliceSuccessBanner by remember { mutableStateOf<String?>(null) }
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
        when (currentScreen) {
            AppScreen.Home -> Unit
            AppScreen.Workspace -> {
                if (workspaceMode == WorkspaceMode.Preview) {
                    workspaceMode = WorkspaceMode.Prepare
                } else {
                    currentScreen = AppScreen.Home
                }
            }
            AppScreen.Profiles -> currentScreen = appScreenFromName(profilesReturnScreenName, AppScreen.Home)
            AppScreen.Calibrations -> currentScreen = AppScreen.Home
            AppScreen.PrinterBrowser -> currentScreen = appScreenFromName(printerBrowserReturnScreenName, AppScreen.Workspace)
            AppScreen.Settings -> currentScreen = AppScreen.Home
        }
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
            canRetry = printerUploadDialogCanRetry && lastPrinterUploadRequest != null,
            onDismiss = {
                printerUploadDialogMessage = null
                printerUploadDialogCanRetry = false
            },
            onRetry = {
                val request = lastPrinterUploadRequest ?: return@ModelLoaderPrinterUploadDialog
                printerUploadDialogMessage = null
                printerUploadDialogCanRetry = false
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
                    setBrowser = { url ->
                        printerBrowserUrl = url
                        printerBrowserReturnScreenName = AppScreen.Workspace.name
                        currentScreen = AppScreen.PrinterBrowser
                    }
                )
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
                workspaceStatus = "Upload cancelled"
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
            gcodePreviewPerformanceMode = gcodePreviewPerformanceMode,
            onThemeModeSelected = onThemeModeSelected,
            onAccentPaletteSelected = onAccentPaletteSelected,
            onWorldViewColorSelected = onWorldViewColorSelected,
            onShowAdvancedProfileSettingsChanged = onShowAdvancedProfileSettingsChanged,
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

    fun syncSelectedObjectToLegacyState(objectOnPlate: PlateObject?) {
        val legacyState = legacyStateForPlateObject(objectOnPlate)
        selectedPlateObjectId = objectOnPlate?.id
        modelLoaded = legacyState.modelLoaded
        currentModelLabel = legacyState.modelLabel
        currentModelFilePath = legacyState.modelFilePath
        currentPreparedMesh = objectOnPlate?.mesh
        currentModelBounds = legacyState.modelBounds
        currentViewerPreparationError = objectOnPlate?.viewerPreparationError
        currentImportTiming = legacyState.importTiming
        currentWorkspacePreparationTiming = objectOnPlate?.workspacePreparationTiming
        currentModelFormatName = legacyState.modelFormatName
        currentModelTransform = objectOnPlate?.transform
    }

    fun saveCurrentPlate(projectName: String, thumbnailBitmap: Bitmap?) {
        if (plateObjects.isEmpty()) {
            workspaceStatus = "No plate to save"
            return
        }
        val project = buildSavedProject(
            currentSavedProjectId = currentSavedProjectId,
            projectName = projectName,
            projectNameFallback = suggestedSavedProjectName(plateObjects = plateObjects, currentModelLabel = currentModelLabel),
            savedProjectRootDir = savedProjectRootDir,
            profileStore = profileStore,
            plateObjects = plateObjects.toList(),
            plateFilamentSlots = plateFilamentSlots.toList(),
            plateFlushVolumes = plateFlushVolumes,
            thumbnailBitmap = thumbnailBitmap
        )
        updateSavedProjects(listOf(project) + savedProjects.filterNot { it.id == project.id })
        currentSavedProjectId = project.id
        workspaceStatus = "Plate saved\n${project.name}"
        sliceSuccessBanner = "Save Successful"
    }

    fun openSavedProject(project: SavedProject) {
        coroutineScope.launch {
            importInProgress = true
            importStartedAtMs = SystemClock.elapsedRealtime()
            firstVisibleWorkspaceFrameMs = null
            firstVisiblePreviewFrameMs = null
            workspaceStatus = "Opening saved project\n${project.name}"
            val openedProject = withContext(Dispatchers.Default) {
                openSavedProjectState(project)
            }
            if (openedProject == null) {
                importInProgress = false
                workspaceStatus = "Saved project could not be opened\nModel files are missing."
                return@launch
            }
            profileStore = project.profileStore
            onProfileStoreChanged(project.profileStore)
            currentCalibrationJob = null
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
                onSavedProjectNativeLoadRequested(openedProject.plateObjects, openedProject.printerBed)
            }
            workspaceStatus = savedProjectLoadedStatus(project, nativeWarmLoadSucceeded)
            importInProgress = false
            currentScreen = AppScreen.Workspace
        }
    }

    fun deleteSavedProject(project: SavedProject) {
        deleteSavedProjectDirectory(savedProjectRootDir, project)
        updateSavedProjects(savedProjects.filterNot { it.id == project.id })
        if (currentSavedProjectId == project.id) {
            currentSavedProjectId = null
        }
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

    fun autoOrientPlateObjects() {
        val result = planAutoOrientPlateObjects(
            plateObjects = plateObjects,
            selectedPlateObjectId = selectedPlateObjectId
        )
        if (result == null) {
            workspaceStatus = "Auto-orient unavailable\nNo objects on plate"
            return
        }
        plateObjects.clear()
        plateObjects.addAll(result.objects)
        workspaceSession.clearGeneratedPreviewState()
        transformInvalidationKey += 1
        syncSelectedObjectToLegacyState(plateObjects.firstOrNull { it.id == selectedPlateObjectId } ?: plateObjects.firstOrNull())
        if (VerboseWorkspacePlanningLogs) {
            Log.i("MobileSlicer", "autoOrient local applied: targets=${result.targetCount} changed=${result.changedCount}")
        }
        workspaceStatus = if (result.changedCount == 0) {
            if (result.selectedOnly) "Object already oriented\nSnapped to nearest 90 degrees" else "Objects already oriented\n${result.targetCount} checked"
        } else {
            if (result.selectedOnly) "Object auto-oriented\nSnapped to nearest 90 degrees" else "Objects auto-oriented\n${result.changedCount} snapped to nearest 90 degrees"
        }
    }

    fun arrangePlateObjects() {
        val bed = selectedPrinter.toBedSpec()
        val result = planAutoArrangePlateObjects(
            plateObjects = plateObjects,
            bed = bed,
            clearance = slicerFootprintClearanceMm(),
            materialSlotCount = plateFilamentSlots.size,
            singleExtruderMultiMaterial = selectedPrinter.singleExtruderMultiMaterial,
            primeTowerWidthMm = activeConfiguration.process.primeTowerWidthMm,
            primeTowerBrimWidthMm = activeConfiguration.process.primeTowerBrimWidthMm
        )
        if (result == null) {
            workspaceStatus = "Objects do not fit\n${plateObjects.size} on ${bed.widthMm.toInt()} x ${bed.depthMm.toInt()} mm plate"
            return
        }
        plateObjects.clear()
        plateObjects.addAll(result.objects)
        workspaceSession.clearGeneratedPreviewState()
        transformInvalidationKey += 1
        syncSelectedObjectToLegacyState(plateObjects.firstOrNull { it.id == selectedPlateObjectId } ?: plateObjects.firstOrNull())
        if (VerboseWorkspacePlanningLogs) {
            Log.i(
                "MobileSlicer",
                "autoArrange local applied: objects=${plateObjects.size} changed=${result.changedCount} centers=${result.centersSummary}"
            )
        }
        workspaceStatus = if (result.reservedPrimeTowerSpace) {
            "Objects arranged\n${plateObjects.size} on plate; prime tower space reserved"
        } else {
            "Objects arranged\n${plateObjects.size} on plate"
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
        workspaceStatus = "Object duplicated\n${plateObjects.size} on plate"
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
            workspaceStatus = "Calibration could not be created\n${error.localizedMessage ?: "Unable to write Orca calibration model."}"
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
        }
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
                workspaceStatus = importApplication.statusMessage
                importInProgress = false
                appendNextImportToPlate = false
                if (importApplication.shouldOpenWorkspace) {
                    workspaceSession.clearGeneratedPreviewState()
                    currentScreen = AppScreen.Workspace
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
            val targetStillCurrent = workspacePreparationTargetStillCurrent(
                targetObjectId = request.selectedObjectId,
                selectedPlateObjectId = selectedPlateObjectId,
                currentModelFilePath = currentModelFilePath,
                modelFilePath = request.modelFilePath
            )
            if (targetStillCurrent && currentModelFilePath == request.modelFilePath) {
                val preparedState = preparedLegacyState(result, currentModelBounds)
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
            if (targetStillCurrent) {
                workspaceStatus = workspaceMeshPreparedStatus(request.modelFilePath, request.importTiming, result)
            }
        } finally {
            if (workspacePreparationTargetKey == request.targetKey) {
                workspacePreparationTargetKey = null
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GCODE_MIME_TYPE)) { uri ->
        val gcodeFilePath = pendingExportGcodeFilePath
        workspaceStatus = if (uri == null || gcodeFilePath == null) {
            "Export cancelled"
        } else {
            onExportRequested(uri, gcodeFilePath)
        }
        pendingExportGcodeFilePath = null
    }

    LaunchedEffect(sliceSuccessBanner) {
        if (sliceSuccessBanner == null) return@LaunchedEffect
        delay(1100)
        sliceSuccessBanner = null
    }

    sliceCompletionResult?.let { result ->
        if (result.sliced) {
            sliceSuccessBanner = result.message.lineSequence().firstOrNull { it.isNotBlank() } ?: "Slice successful"
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
                gcodePreviewPerformanceMode = gcodePreviewPerformanceMode,
                worldViewColor = worldViewColor,
                modelTransform = currentModelTransform,
                plateObjects = plateObjects.toList(),
                filamentSlots = plateFilamentSlots.toList().ifEmpty {
                    listOf(selectedFilament.toPlateFilamentSlot(index = 1))
                },
                availableFilaments = sliceReadyProfileStore.filaments
                    .filter { it.printerProfileId == selectedPrinter.id || it.printerProfileId.isBlank() },
                selectedPlateObjectId = selectedPlateObjectId,
                onModelTransformChanged = { transform ->
                    val objectId = selectedPlateObjectId
                    if (objectId != null && transform != null) {
                        updatePlateObject(objectId) { it.copy(transform = transform) }
                    } else {
                        currentModelTransform = transform
                    }
                    transformInvalidationKey += 1
                },
                onPlateObjectSelected = { objectId ->
                    if (objectId == null) {
                        selectedPlateObjectId = null
                    } else {
                        val selected = plateObjects.firstOrNull { objectOnPlate -> objectOnPlate.id == objectId }
                        syncSelectedObjectToLegacyState(selected)
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
                    applyPlateMutation(
                        removePlateFilamentSlot(
                            slots = plateFilamentSlots.toList(),
                            objects = plateObjects.toList(),
                            flushVolumes = plateFlushVolumes,
                            slotIndex = slotIndex
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
                        workspaceStatus = result.statusMessage
                    }
                },
                onCloneSelectedObject = {
                    cloneSelectedPlateObject()
                },
                onAutoOrientObjects = {
                    autoOrientPlateObjects()
                },
                onAutoArrangeObjects = {
                    arrangePlateObjects()
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
                    when (val savePlan = planSavePlatePrompt(plateObjects, currentModelLabel)) {
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
                            workspaceStatus = buildString {
                                append(workspaceStatus.lineSequence().joinToString("\n"))
                                append('\n')
                                append("First visible workspace frame: ")
                                append(formatDurationMs(firstFrameMs))
                            }
                        }
                    }
                },
                onFirstVisiblePreviewFrame = {
                    if (firstVisiblePreviewFrameMs == null) {
                        previewStartedAtMs?.let { startedAt ->
                            firstVisiblePreviewFrameMs = SystemClock.elapsedRealtime() - startedAt
                        }
                    }
                },
                onSlice = {
                    val sliceStartPlan =
                        if (!canRequestModelLoaderSlice(modelLoaded, sliceInProgress, sendToPrinterInProgress)) {
                            ModelLoaderSliceStartPlan.Ignore
                        } else {
                            val generatedFootprintFits = normalizeGeneratedFootprintBeforeSlice()
                            planModelLoaderSliceStart(
                                modelLoaded = modelLoaded,
                                sliceInProgress = sliceInProgress,
                                sendToPrinterInProgress = sendToPrinterInProgress,
                                generatedFootprintFits = generatedFootprintFits,
                                printableVolumePreflightFailure = if (generatedFootprintFits) printableVolumePreflightFailure() else null,
                                nativeSliceTitle = activeConfiguration.nativeSliceTitle()
                            )
                        }
                    when (sliceStartPlan) {
                        ModelLoaderSliceStartPlan.Ignore -> Unit
                        is ModelLoaderSliceStartPlan.Fail -> {
                            sliceCompletionResult = sliceStartPlan.result
                        }
                        is ModelLoaderSliceStartPlan.Start -> {
                            sliceInProgress = true
                            currentGcodeFilePath = null
                            currentSliceSummary = null
                            currentSliceTiming = null
                            currentSlicePreviewKey = 0L
                            previewStartedAtMs = null
                            firstVisiblePreviewFrameMs = null
                            workspaceMode = WorkspaceMode.Prepare
                            workspaceStatus = sliceStartPlan.statusMessage
                            coroutineScope.launch {
                                val sliceConfiguration = activeConfiguration
                                val sliceCalibrationJob = currentCalibrationJob
                                val slicePlateObjects = plateObjects.toList()
                                val sliceProfileFilaments = sliceReadyProfileStore.filaments
                                val slicePrinter = selectedPrinter
                                val sliceModelFilePath = currentModelFilePath
                                val slicePreparedMesh = currentPreparedMesh
                                val sliceModelBounds = currentModelBounds
                                val sliceModelTransform = currentModelTransform
                                val sliceGcodeFileName = effectiveGcodeFileName()
                                val activePlateSlots = plateFilamentSlots.toList().ifEmpty {
                                    listOf(selectedFilament.toPlateFilamentSlot(index = 1))
                                }
                                val preparedResult = withContext(Dispatchers.Default) {
                                    runModelLoaderSlice(
                                        context = context.applicationContext,
                                        configuration = sliceConfiguration,
                                        calibrationJob = sliceCalibrationJob,
                                        plateObjects = slicePlateObjects,
                                        profileFilaments = sliceProfileFilaments,
                                        activePlateSlots = activePlateSlots,
                                        flushVolumes = plateFlushVolumes,
                                        printer = slicePrinter,
                                        modelFilePath = sliceModelFilePath,
                                        preparedMesh = slicePreparedMesh,
                                        modelBounds = sliceModelBounds,
                                        modelTransform = sliceModelTransform,
                                        gcodeFileName = sliceGcodeFileName,
                                        onSliceRequested = onSliceRequested
                                    )
                                }
                                val completionPlan = planModelLoaderSliceCompletion(
                                    result = preparedResult,
                                    calibrationJob = sliceCalibrationJob,
                                    fallbackFileName = sliceGcodeFileName,
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
                onSendToPrinter = { uploadAction, remoteFileName ->
                    planPrinterUploadRequest(
                        gcodeFilePath = currentGcodeFilePath,
                        sendToPrinterInProgress = sendToPrinterInProgress,
                        calibrationJob = currentCalibrationJob,
                        remoteFileName = remoteFileName,
                        printerProfile = selectedPrinter,
                        uploadAction = uploadAction
                    )?.let { request ->
                        lastPrinterUploadRequest = request
                        printerUploadDialogCanRetry = false
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
                            setBrowser = { url ->
                                printerBrowserUrl = url
                                printerBrowserReturnScreenName = AppScreen.Workspace.name
                                currentScreen = AppScreen.PrinterBrowser
                            }
                        )
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
                gcodePreviewPerformanceMode = gcodePreviewPerformanceMode,
                onThemeModeSelected = onThemeModeSelected,
                onAccentPaletteSelected = onAccentPaletteSelected,
                onWorldViewColorSelected = onWorldViewColorSelected,
                onShowAdvancedProfileSettingsChanged = onShowAdvancedProfileSettingsChanged,
                onGcodePreviewPerformanceModeSelected = onGcodePreviewPerformanceModeSelected,
                onBack = { handleBackNavigation() },
                modifier = Modifier.padding(innerPadding)
            )
            }

            sliceSuccessBanner?.let { message ->
                ModelLoaderSuccessBanner(message)
            }
        }
    }

    BackHandler(enabled = currentScreen != AppScreen.Home) {
        handleBackNavigation()
    }
}

package com.mobileslicer

import android.app.Activity
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.mobileslicer.automation.AutomationConfigResolver
import com.mobileslicer.automation.AutomationSliceRequest
import com.mobileslicer.automation.AutomationSliceRunner
import com.mobileslicer.nativebridge.NativeEngineCallResult
import com.mobileslicer.nativebridge.NativeEngineCalls
import com.mobileslicer.nativebridge.NativeEngineErrorCode
import com.mobileslicer.nativebridge.NativeEngineHandle
import com.mobileslicer.nativebridge.NativeEngineSession
import com.mobileslicer.printerconnection.PrinterConnectionRepository
import com.mobileslicer.printerconnection.PrinterConnectionChoicesResult
import com.mobileslicer.printerconnection.PrinterConnectionResult
import com.mobileslicer.printerconnection.PrinterDiscoveryRepository
import com.mobileslicer.printerconnection.PrinterUploadAction
import com.mobileslicer.printerconnection.SimplyPrintOAuthClient
import com.mobileslicer.printerconnection.SimplyPrintOAuthResult
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.profiles.PrintHostType
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.storage.AppPreferenceStore
import com.mobileslicer.storage.GCODE_MIME_TYPE
import com.mobileslicer.storage.PreparedViewerMeshCache
import com.mobileslicer.storage.PrinterCredentialStore
import com.mobileslicer.storage.SavedProject
import com.mobileslicer.storage.SavedProjectRepository
import com.mobileslicer.modelsearch.importflow.ModelImportIntentResolver
import com.mobileslicer.modelsearch.thingiverse.thingiverseOAuthRedirectFrom
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.StlMeshParser
import com.mobileslicer.nativebridge.NativeEngineBridge
import com.mobileslicer.ui.theme.AccentPaletteOption
import com.mobileslicer.viewer.GcodePreviewPerformanceMode
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.TouchModelViewerView
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.ViewerPlateObject
import com.mobileslicer.ui.theme.MobileSlicerTheme
import com.mobileslicer.ui.theme.PanelAmber
import com.mobileslicer.ui.theme.PanelBlue
import com.mobileslicer.ui.theme.PanelGreen
import com.mobileslicer.ui.theme.PanelLavender
import com.mobileslicer.ui.theme.PanelSlate
import com.mobileslicer.ui.theme.LocalAppDarkTheme
import com.mobileslicer.ui.theme.ThemeModeOption
import com.mobileslicer.ui.theme.WorldViewColorOption
import com.mobileslicer.workspace.GcodeSummaryParser
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.ModelImportTiming
import com.mobileslicer.workspace.ModelLoadResult
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.SlicePipelineTiming
import com.mobileslicer.workspace.SliceResult
import com.mobileslicer.workspace.WorkspacePreparationResult
import com.mobileslicer.workspace.WorkspacePreparationTiming
import com.mobileslicer.workspace.WorkspaceScreen
import com.mobileslicer.workspace.WorkspaceSessionViewModel
import com.mobileslicer.workspace.defaultNativeModelTransform
import com.mobileslicer.workspace.modelLoadStatusMessage
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.mobileslicer.viewer.MeshBounds

class MainActivity : ComponentActivity() {
    companion object {
        internal const val TAG = "MobileSlicer"
    }

    internal val workspaceSessionViewModel: WorkspaceSessionViewModel by viewModels()
    internal val nativeEngineSession = NativeEngineSession()
    internal var stagedModelFile: File? = null
    internal var currentModelName: String? = null
    internal var nativeLoadedModelPath: String? = null
    internal val preparedViewerMeshCache = PreparedViewerMeshCache(maxEntries = 4)
    internal val printerConnectionRepository = PrinterConnectionRepository()
    internal val printerDiscoveryRepository by lazy { PrinterDiscoveryRepository(this) }
    internal val simplyPrintOAuthClient = SimplyPrintOAuthClient()
    internal lateinit var appPreferenceStore: AppPreferenceStore
    internal lateinit var printerCredentialStore: PrinterCredentialStore
    internal val pendingExternalModelImportUri = mutableStateOf<Uri?>(null)
    internal val pendingThingiverseOAuthRedirectUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appPreferenceStore = AppPreferenceStore.from(this)
        printerCredentialStore = PrinterCredentialStore.from(this)
        configureNativeRuntimePaths()
        stagedModelFile = workspaceSessionViewModel.currentModelFilePath.value
            ?.let(::File)
            ?.takeIf { it.exists() }
        currentModelName = stagedModelFile?.let(::displayStemForModelFile)
        cleanupCacheArtifacts()

        if (maybeRunAutomation(intent)) {
            return
        }
        pendingThingiverseOAuthRedirectUri.value = thingiverseOAuthRedirectFrom(
            intent = intent,
            expectedScheme = Uri.parse(BuildConfig.THINGIVERSE_REDIRECT_URI).scheme.orEmpty(),
            expectedHost = Uri.parse(BuildConfig.THINGIVERSE_REDIRECT_URI).host.orEmpty()
        )
        if (pendingThingiverseOAuthRedirectUri.value == null) {
            pendingExternalModelImportUri.value = ModelImportIntentResolver.resolve(intent)
        }

        setContent {
            var themeMode by rememberSaveable { mutableStateOf(appPreferenceStore.loadThemeMode()) }
            var accentPalette by rememberSaveable { mutableStateOf(appPreferenceStore.loadAccentPalette()) }
            var worldViewColor by rememberSaveable { mutableStateOf(appPreferenceStore.loadWorldViewColor()) }
            var showAdvancedProfileSettings by rememberSaveable {
                mutableStateOf(appPreferenceStore.loadShowAdvancedProfileSettings())
            }
            var activeStylusPaintOnly by rememberSaveable {
                mutableStateOf(appPreferenceStore.loadActiveStylusPaintOnly())
            }
            var gcodePreviewPerformanceMode by rememberSaveable {
                mutableStateOf(appPreferenceStore.loadGcodePreviewPerformanceMode())
            }
            var nativeEngineHandleForUi by remember { mutableLongStateOf(nativeEngineSession.currentRawHandle) }

            fun ensureEngineForUi(): Long {
                val handle = ensureEngine()
                if (nativeEngineHandleForUi != handle) {
                    nativeEngineHandleForUi = handle
                }
                return handle
            }

            MobileSlicerTheme(
                themeMode = themeMode,
                accentPalette = accentPalette
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ModelLoaderScreen(
                        initialOutput = "Open an STL file to prepare it on the bed, then slice or export from the workspace.",
                        initialProfileStore = loadProfileStore(),
                        initialSavedProjects = loadSavedProjects(),
                        savedProjectRootDir = File(filesDir, "saved-projects"),
                        appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.1.0",
                        appPackageName = packageName,
                        nativeEngineHandle = nativeEngineHandleForUi,
                        themeMode = themeMode,
                        accentPalette = accentPalette,
                        worldViewColor = worldViewColor,
                        showAdvancedProfileSettings = showAdvancedProfileSettings,
                        activeStylusPaintOnly = activeStylusPaintOnly,
                        gcodePreviewPerformanceMode = gcodePreviewPerformanceMode,
                        onThemeModeSelected = {
                            themeMode = it
                            appPreferenceStore.storeThemeMode(it)
                        },
                        onAccentPaletteSelected = {
                            accentPalette = it
                            appPreferenceStore.storeAccentPalette(it)
                        },
                        onWorldViewColorSelected = {
                            worldViewColor = it
                            appPreferenceStore.storeWorldViewColor(it)
                        },
                        onShowAdvancedProfileSettingsChanged = {
                            showAdvancedProfileSettings = it
                            appPreferenceStore.storeShowAdvancedProfileSettings(it)
                        },
                        onActiveStylusPaintOnlyChanged = {
                            activeStylusPaintOnly = it
                            appPreferenceStore.storeActiveStylusPaintOnly(it)
                        },
                        onGcodePreviewPerformanceModeSelected = {
                            gcodePreviewPerformanceMode = it
                            appPreferenceStore.storeGcodePreviewPerformanceMode(it)
                        },
                        onProfileStoreChanged = { store -> storeProfileStore(store) },
                        onSavedProjectsChanged = { projects -> storeSavedProjects(projects) },
                        workspaceSession = workspaceSessionViewModel,
                        externalModelImportUri = pendingExternalModelImportUri.value,
                        thingiverseOAuthRedirectUri = pendingThingiverseOAuthRedirectUri.value,
                        onExternalModelImportUriConsumed = {
                            pendingExternalModelImportUri.value = null
                            intent?.let { currentIntent ->
                                val consumedIntent = Intent(currentIntent)
                                    .setAction(Intent.ACTION_MAIN)
                                    .setData(null)
                                consumedIntent.removeExtra(Intent.EXTRA_STREAM)
                                setIntent(consumedIntent)
                            }
                        },
                        onThingiverseOAuthRedirectConsumed = {
                            pendingThingiverseOAuthRedirectUri.value = null
                            intent?.let { currentIntent ->
                                val consumedIntent = Intent(currentIntent)
                                    .setAction(Intent.ACTION_MAIN)
                                    .setData(null)
                                setIntent(consumedIntent)
                            }
                        },
                        onFreshWorkspaceStarted = {
                            clearFreshWorkspaceRuntimeArtifacts()
                        },
                        onModelSelected = { uri ->
                            ensureEngineForUi()
                            loadModelFromUri(uri)
                        },
                        onScannerWorkspaceModelSelected = { modelFile ->
                            ensureEngineForUi()
                            loadScannerWorkspaceModelFromFile(modelFile)
                        },
                        onWorkspaceMeshPreparationRequested = { modelFilePath ->
                            prepareWorkspaceMesh(modelFilePath)
                        },
                        onSliceRequested = { configJson, plateObjects, modelFilePath, preparedMesh, modelBounds, printerBed, modelTransform, suggestedGcodeFileName ->
                            ensureEngineForUi()
                            sliceCurrentModel(
                                configJson,
                                plateObjects,
                                modelFilePath,
                                preparedMesh,
                                modelBounds,
                                printerBed,
                                modelTransform,
                                suggestedGcodeFileName
                            )
                        },
                        onNativeAutoArrangeRequested = { configJson, plateObjects, printerBed, allowRotation ->
                            ensureEngineForUi()
                            planNativePlateArrangement(configJson, plateObjects, printerBed, allowRotation)
                        },
                        onNativeAutoOrientRequested = { configJson, plateObjects, selectedPlateObjectId, printerBed ->
                            ensureEngineForUi()
                            planNativeAutoOrientation(configJson, plateObjects, selectedPlateObjectId, printerBed)
                        },
                        onNativePlatePlanningPrewarmRequested = { plateObjects, printerBed ->
                            ensureEngineForUi()
                            prewarmNativePlatePlanningModels(plateObjects, printerBed)
                        },
                        onNativePlatePlanningCancelRequested = {
                            cancelNativePlatePlanning()
                        },
                        onExportRequested = { uri, gcodeFilePath -> exportGcodeToUri(uri, gcodeFilePath) },
                        onSendToPrinterRequested = { gcodeFilePath, remoteFileName, printerProfile, uploadAction, bambuOptions, onProgress ->
                            sendGcodeToPrinter(gcodeFilePath, remoteFileName, printerProfile, uploadAction, bambuOptions, onProgress)
                        },
                        onTestPrinterConnectionRequested = { printerProfile ->
                            testPrinterConnection(printerProfile)
                        },
                        onPrinterStatusRequested = { printerProfile ->
                            printerStatus(printerProfile)
                        },
                        onDiscoverPrinterHostsRequested = {
                            discoverPrinterHosts()
                        },
                        onBrowsePrinterConnectionTargetsRequested = { printerProfile ->
                            browsePrinterConnectionTargets(printerProfile)
                        },
                        onBrowsePrinterConnectionGroupsRequested = { printerProfile ->
                            browsePrinterConnectionGroups(printerProfile)
                        },
                        onSimplyPrintLoginRequested = { printerProfile ->
                            loginToSimplyPrint(printerProfile)
                        },
                        onSavedProjectNativeLoadRequested = { project, plateObjects, printerBed ->
                            ensureEngineForUi()
                            loadSavedProjectIntoNativeCache(project?.nativeProjectFilePath, plateObjects, printerBed)
                        },
                        onNativePlateCacheCurrent = { plateObjects, printerBed ->
                            markPlateObjectsAsNativeCacheCurrent(plateObjects, printerBed)
                        },
                        onShareRequested = { gcodeFilePath, fileName -> shareGcodeFile(gcodeFilePath, fileName) }
                    )
                }
            }
    }
    }

    override fun onDestroy() {
        val finishingActivity = !isChangingConfigurations
        if (finishingActivity) {
            stagedModelFile?.delete()
            stagedModelFile = null
            preparedViewerMeshCache.clear()
        }

        destroyNativeEngine()
        if (finishingActivity) {
            cleanupOrcaTempCache(
                cacheDir = cacheDir,
                retainedPaths = emptySet(),
                maxBytes = 0L,
                maxAgeMs = 0L
            )
        }
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            preparedViewerMeshCache.clear()
            NativeEngineCalls.trimMemory()
            Runtime.getRuntime().gc()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (maybeRunAutomation(intent)) {
            return
        }
        val thingiverseRedirect = thingiverseOAuthRedirectFrom(
            intent = intent,
            expectedScheme = Uri.parse(BuildConfig.THINGIVERSE_REDIRECT_URI).scheme.orEmpty(),
            expectedHost = Uri.parse(BuildConfig.THINGIVERSE_REDIRECT_URI).host.orEmpty()
        )
        if (thingiverseRedirect != null) {
            pendingThingiverseOAuthRedirectUri.value = thingiverseRedirect
            pendingExternalModelImportUri.value = null
        } else {
            pendingExternalModelImportUri.value = ModelImportIntentResolver.resolve(intent)
        }
    }

}

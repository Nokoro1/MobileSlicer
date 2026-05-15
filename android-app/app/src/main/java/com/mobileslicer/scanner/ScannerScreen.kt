package com.mobileslicer.scanner

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mobileslicer.workspace.ModelLoadResult
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val SCANNER_LOG_TAG = "MobileSlicerScanner"

private enum class ScannerUiCaptureMode(
    val displayName: String,
    val statusLabel: String
) {
    Photo("Photo", "High-res photo"),
    ArDepth("AR Depth", "RGB + raw depth")
}

private enum class ScannerFlowStage {
    Prep,
    Capture,
    Review,
    Reconstruct
}

@Composable
internal fun ScannerScreen(
    onBack: () -> Unit,
    onWorkspaceImportRequested: suspend (File) -> ModelLoadResult,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val capturedFrames = remember { mutableStateListOf<ScanFrame>() }
    val scanId = remember { "scan_${UUID.randomUUID()}" }
    val sessionDir = remember(scanId) {
        File(context.cacheDir, "scanner-sessions/$scanId").also { it.mkdirs() }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var status by remember { mutableStateOf("Capture calibrated keyframes. Mesh export remains blocked until validation passes.") }
    var exportedZipPath by remember { mutableStateOf<String?>(null) }
    var exportedPackageDirPath by remember { mutableStateOf<String?>(null) }
    var reconstructionSummaryPath by remember { mutableStateOf<String?>(null) }
    var reconstructionAuditPath by remember { mutableStateOf<String?>(null) }
    var reconstructionBlockers by remember { mutableStateOf<List<String>>(emptyList()) }
    var scannerWorkspaceDirPath by remember { mutableStateOf<String?>(null) }
    var workspaceHandoffReady by remember { mutableStateOf(false) }
    var workspaceImportInProgress by remember { mutableStateOf(false) }
    var benchmarkReportPath by remember { mutableStateOf<String?>(null) }
    var latestCalibrationGate by remember { mutableStateOf<PrintableCalibrationGateResult?>(null) }
    var latestMarkerEvidence by remember { mutableStateOf<MarkerEvidenceSummary?>(null) }
    var latestReadiness by remember { mutableStateOf<ScannerReadinessSummary?>(null) }
    var exportInProgress by remember { mutableStateOf(false) }
    var pipelineInProgress by remember { mutableStateOf(false) }
    var captureInProgress by remember { mutableStateOf(false) }
    var frameIndex by remember { mutableIntStateOf(1) }
    var measuredScaleBarMm by remember { mutableStateOf("") }
    var benchmarkFixtureId by remember { mutableStateOf("known_block_manual") }
    var benchmarkDimensionX by remember { mutableStateOf("20") }
    var benchmarkDimensionY by remember { mutableStateOf("40") }
    var benchmarkDimensionZ by remember { mutableStateOf("60") }
    var selectedCapturePass by remember { mutableStateOf(ScannerCapturePass.MidRing) }
    var flowStage by remember { mutableStateOf(ScannerFlowStage.Prep) }
    var markerMatAssetPath by remember { mutableStateOf<String?>(null) }
    var objectRoi by remember { mutableStateOf(defaultScannerObjectRoi()) }
    var arCoreCaptureView by remember { mutableStateOf<ScannerArCoreCaptureView?>(null) }
    val arCoreAvailability = remember(context) {
        scannerArCoreAvailability(context.applicationContext)
    }
    val activeCaptureMode = if (arCoreAvailability.supported) {
        ScannerUiCaptureMode.ArDepth
    } else {
        ScannerUiCaptureMode.Photo
    }
    val deviceDepthCapability = remember(context) {
        scannerDeviceDepthCapability(context.applicationContext)
    }
    val previewHeight = remember(configuration.screenHeightDp) {
        scannerPreviewHeight(configuration.screenHeightDp.dp)
    }
    val benchmarkFixtureInput = parseScannerBenchmarkFixtureInput(
        fixtureIdInput = benchmarkFixtureId,
        dimensionXInput = benchmarkDimensionX,
        dimensionYInput = benchmarkDimensionY,
        dimensionZInput = benchmarkDimensionZ
    )
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        status = if (granted) {
            "Camera ready. Capture measured evidence; no mesh handoff exists yet."
        } else {
            "Camera permission is required for local capture."
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasCameraPermission, previewView, lifecycleOwner, activeCaptureMode) {
        val targetPreview = previewView
        if (!hasCameraPermission) {
            return@LaunchedEffect
        }
        if (activeCaptureMode == ScannerUiCaptureMode.ArDepth) {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
            imageCapture = null
            boundCamera = null
            status = "Scanner readying AR depth assist. Move slowly until tracking and depth are ready."
            return@LaunchedEffect
        }
        arCoreCaptureView?.closeSession()
        arCoreCaptureView = null
        if (targetPreview == null) return@LaunchedEffect
        Log.i(SCANNER_LOG_TAG, "camera_bind_start scanId=$scanId")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(targetPreview.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                runCatching {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    imageCapture = capture
                    boundCamera = camera
                    targetPreview.post {
                        focusScannerCamera(
                            camera = camera,
                            xPx = targetPreview.width / 2f,
                            yPx = targetPreview.height / 2f,
                            widthPx = targetPreview.width.toFloat(),
                            heightPx = targetPreview.height.toFloat()
                        )
                    }
                    status = "Camera ready. Tap object, then capture keyframes."
                    Log.i(SCANNER_LOG_TAG, "camera_bind_success scanId=$scanId")
                }.onFailure {
                    imageCapture = null
                    boundCamera = null
                    status = "Camera bind failed: ${it.message}"
                    Log.e(SCANNER_LOG_TAG, "camera_bind_failed scanId=$scanId", it)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun recordCapturedFrame(frame: ScanFrame) {
        val admittedFrame = applyScannerFrameAdmissionQuality(
            frame = frame,
            previousFrames = capturedFrames
        )
        val retainFrame = shouldRetainScannerDiagnosticFrame(admittedFrame, capturedFrames)
        if (retainFrame) {
            capturedFrames += admittedFrame
        } else {
            deleteScannerFrameAssets(sessionDir, admittedFrame)
        }
        captureInProgress = false
        if (retainFrame) {
            exportedZipPath = null
            exportedPackageDirPath = null
            reconstructionSummaryPath = null
            reconstructionAuditPath = null
            reconstructionBlockers = emptyList()
            scannerWorkspaceDirPath = null
            workspaceHandoffReady = false
            benchmarkReportPath = null
            latestCalibrationGate = null
            latestMarkerEvidence = null
            latestReadiness = null
        }
        status = scannerCaptureStatus(admittedFrame, capturedFrames, retainFrame)
        Log.i(
            SCANNER_LOG_TAG,
            "capture_analyzed scanId=$scanId frame=${admittedFrame.id} " +
                "accepted=${admittedFrame.quality.accepted} " +
                "retained=$retainFrame " +
                "blur=${admittedFrame.quality.blurScore} " +
                "exposure=${admittedFrame.quality.exposureScore} " +
                "clipped=${admittedFrame.quality.materialRisk / 2f} " +
                "depth=${admittedFrame.quality.depthCoverage ?: "none"} " +
                "pass=${admittedFrame.capturePass.manifestValue} " +
                "reasons=${admittedFrame.quality.rejectionReasons.joinToString("|")}"
        )
    }

    fun startPhotoCapture() {
        val capture = imageCapture ?: run {
            status = "Camera is not ready yet."
            Log.w(SCANNER_LOG_TAG, "capture_blocked_camera_not_ready scanId=$scanId")
            return
        }
        captureInProgress = true
        val currentIndex = frameIndex++
        val frameFile = File(
            sessionDir,
            "frames/${currentIndex.toString().padStart(6, '0')}.jpg"
        )
        frameFile.parentFile?.mkdirs()
        Log.i(
            SCANNER_LOG_TAG,
            "capture_start scanId=$scanId frame=${currentIndex.toString().padStart(6, '0')} pass=${selectedCapturePass.manifestValue}"
        )
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(frameFile).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.i(
                        SCANNER_LOG_TAG,
                        "capture_saved scanId=$scanId frame=${currentIndex.toString().padStart(6, '0')} path=${frameFile.absolutePath}"
                    )
                    scope.launch {
                        val frame = withContext(Dispatchers.IO) {
                            capturedScanFrame(
                                index = currentIndex,
                                context = context,
                                sessionDir = sessionDir,
                                frameFile = frameFile,
                                capturePass = selectedCapturePass
                            )
                        }
                        recordCapturedFrame(frame)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    captureInProgress = false
                    status = "Capture failed: ${exception.message}"
                    Log.e(SCANNER_LOG_TAG, "capture_failed scanId=$scanId", exception)
                }
            }
        )
    }

    fun startArDepthCapture() {
        val arView = arCoreCaptureView ?: run {
            status = "AR depth view is not ready yet."
            Log.w(SCANNER_LOG_TAG, "capture_blocked_arcore_view_not_ready scanId=$scanId")
            return
        }
        captureInProgress = true
        val currentIndex = frameIndex++
        Log.i(
            SCANNER_LOG_TAG,
            "ar_capture_start scanId=$scanId frame=${currentIndex.toString().padStart(6, '0')} pass=${selectedCapturePass.manifestValue}"
        )
        arView.captureKeyframe(
            index = currentIndex,
            capturePass = selectedCapturePass
        ) { result ->
            val frame = result.frame
            if (frame == null) {
                captureInProgress = false
                status = "AR depth capture blocked: ${result.errors.joinToString()}"
                Log.w(
                    SCANNER_LOG_TAG,
                    "ar_capture_blocked scanId=$scanId status=${result.status} errors=${result.errors.joinToString("|")}"
                )
            } else {
                recordCapturedFrame(frame)
                Log.i(
                    SCANNER_LOG_TAG,
                    "ar_capture_done scanId=$scanId status=${result.status} warnings=${result.warnings.joinToString("|")}"
                )
            }
        }
    }

    fun startCapture() {
        when (activeCaptureMode) {
            ScannerUiCaptureMode.Photo -> startPhotoCapture()
            ScannerUiCaptureMode.ArDepth -> startArDepthCapture()
        }
    }

    fun exportPackage() {
        if (exportInProgress) {
            status = "Export already running."
            Log.w(SCANNER_LOG_TAG, "export_blocked_already_running scanId=$scanId")
            return
        }
        exportInProgress = true
        scope.launch {
            try {
                Log.i(SCANNER_LOG_TAG, "export_start scanId=$scanId frames=${capturedFrames.size}")
                val result = withContext(Dispatchers.IO) {
                    exportCapturePackage(
                        sessionDir = sessionDir,
                        scanId = scanId,
                        frames = capturedFrames.toList(),
                        measuredScaleBarMm = measuredScaleBarMm.toFloatOrNull(),
                        context = context,
                        objectRoi = objectRoi
                    )
                }
                exportedZipPath = result.zipFile.absolutePath
                exportedPackageDirPath = result.packageDir.absolutePath
                reconstructionSummaryPath = null
                reconstructionAuditPath = null
                reconstructionBlockers = emptyList()
                scannerWorkspaceDirPath = null
                workspaceHandoffReady = false
                benchmarkReportPath = null
                latestCalibrationGate = result.calibrationGate
                latestMarkerEvidence = result.markerEvidence
                latestReadiness = result.readiness
                status = if (result.validation.valid) {
                    if (result.calibrationGate.allowed) {
                        "Package validated with printable calibration evidence."
                    } else {
                        "Package validated, but printable calibration is blocked."
                    }
                } else {
                    "Package validation failed: ${result.validation.errors.joinToString()}"
                }
                Log.i(
                    SCANNER_LOG_TAG,
                    "export_done scanId=$scanId valid=${result.validation.valid} " +
                        "errors=${result.validation.errors.joinToString("|")} " +
                        "packageDir=${result.packageDir.absolutePath} zip=${result.zipFile.absolutePath}"
                )
            } finally {
                exportInProgress = false
            }
        }
    }

    fun runLocalPipeline() {
        if (pipelineInProgress) {
            status = "Reconstruction is already running."
            Log.w(SCANNER_LOG_TAG, "pipeline_blocked_already_running scanId=$scanId")
            return
        }
        val packageDirPath = exportedPackageDirPath
        if (packageDirPath == null) {
            status = "Export a valid scan package before reconstruction."
            Log.w(SCANNER_LOG_TAG, "pipeline_blocked_missing_package_dir scanId=$scanId")
            return
        }
        pipelineInProgress = true
        scope.launch {
            try {
                status = "Running local reconstruction checks."
                Log.i(SCANNER_LOG_TAG, "pipeline_start scanId=$scanId packageDir=$packageDirPath")
                val pipeline = withContext(Dispatchers.IO) {
                    val workspaceDir = File(context.cacheDir, "scanner-workspaces/$scanId")
                    runScannerLocalReconstructionPipeline(
                        packageDir = File(packageDirPath),
                        workspaceDir = workspaceDir
                    )
                }
                reconstructionSummaryPath = pipeline.workspaceDir
                    .resolve(pipeline.summaryPath)
                    .absolutePath
                reconstructionAuditPath = pipeline.workspaceDir
                    .resolve(SCANNER_RECONSTRUCTION_AUDIT_PATH)
                    .absolutePath
                scannerWorkspaceDirPath = pipeline.workspaceDir.absolutePath
                workspaceHandoffReady = pipeline.workspaceDir
                    .resolve(SCANNER_WORKSPACE_HANDOFF_PATH)
                    .takeIf { it.isFile }
                    ?.let { file ->
                        runCatching {
                            JSONObject(file.readText()).let { json ->
                                json.optBoolean("allowed", false) &&
                                    json.optBoolean("metric", false) &&
                                    json.optBoolean("workspace_handoff_ready", false)
                            }
                        }.getOrDefault(false)
                    }
                    ?: false
                reconstructionBlockers = summarizeScannerReconstructionBlockers(pipeline)
                benchmarkReportPath = null
                flowStage = ScannerFlowStage.Reconstruct
                status = when {
                    pipeline.denseReconstructionReady ->
                        "Local reconstruction reached dense readiness. Mesh export remains gated."
                    pipeline.workspaceDir.resolve(SCANNER_EXPERIMENTAL_SPARSE_PREVIEW_PATH).isFile ->
                        "Experimental sparse preview is ready. Printable mesh remains blocked by validation."
                    else ->
                        "Reconstruction blocked. Review the blocker summary below."
                }
                Log.i(
                    SCANNER_LOG_TAG,
                    "pipeline_done scanId=$scanId denseReady=${pipeline.denseReconstructionReady} errors=${pipeline.blockingErrors.joinToString("|")} summary=$reconstructionSummaryPath"
                )
            } catch (error: Throwable) {
                status = "Reconstruction failed: ${error.message ?: error::class.java.simpleName}"
                Log.e(SCANNER_LOG_TAG, "pipeline_failed scanId=$scanId", error)
            } finally {
                pipelineInProgress = false
            }
        }
    }

    fun importValidatedWorkspaceHandoff() {
        if (workspaceImportInProgress) {
            status = "Workspace import is already running."
            return
        }
        val workspaceDirPath = scannerWorkspaceDirPath
        if (workspaceDirPath == null) {
            status = "Run local reconstruction before importing to workspace."
            return
        }
        workspaceImportInProgress = true
        scope.launch {
            val workspaceDir = File(workspaceDirPath)
            try {
                val plan = withContext(Dispatchers.IO) {
                    planScannerWorkspaceImport(workspaceDir)
                }
                if (!plan.allowed || plan.modelPath == null) {
                    reconstructionBlockers = (plan.errors.ifEmpty { listOf("workspace_import_blocked") } + reconstructionBlockers)
                        .distinct()
                    status = "Workspace import blocked: ${plan.errors.take(4).joinToString()}"
                    return@launch
                }
                val modelFile = File(plan.modelPath)
                val result = onWorkspaceImportRequested(modelFile)
                withContext(Dispatchers.IO) {
                    writeScannerWorkspaceImportReceipt(
                        workspaceDir = workspaceDir,
                        plan = plan,
                        imported = result.loaded,
                        loadMessage = result.message
                    )
                }
                status = if (result.loaded) {
                    "Validated scanner model imported into workspace."
                } else {
                    "Workspace import failed: ${result.message}"
                }
            } catch (error: Throwable) {
                status = "Workspace import failed: ${error.message ?: error::class.java.simpleName}"
                Log.e(SCANNER_LOG_TAG, "workspace_import_failed scanId=$scanId", error)
            } finally {
                workspaceImportInProgress = false
            }
        }
    }

    fun runBenchmark() {
        val fixture = benchmarkFixtureInput.fixture ?: run {
            status = "Benchmark fixture invalid: ${benchmarkFixtureInput.errors.joinToString()}"
            return
        }
        scope.launch {
            Log.i(
                SCANNER_LOG_TAG,
                "benchmark_start scanId=$scanId fixture=${fixture.fixtureId} dims=${fixture.expectedDimensionsMm.joinToString("x")}"
            )
            val result = withContext(Dispatchers.IO) {
                val workspaceDir = File(context.cacheDir, "scanner-workspaces/$scanId")
                runScannerAccuracyBenchmark(
                    workspaceDir = workspaceDir,
                    fixture = fixture
                )
            }
            benchmarkReportPath = File(
                context.cacheDir,
                "scanner-workspaces/$scanId/$SCANNER_ACCURACY_BENCHMARK_PATH"
            ).absolutePath
            status = if (result.allowed) {
                "Accuracy benchmark passed for ${result.fixture.fixtureId}."
            } else {
                "Accuracy benchmark failed: ${result.errors.take(4).joinToString()}"
            }
            Log.i(
                SCANNER_LOG_TAG,
                "benchmark_done scanId=$scanId allowed=${result.allowed} errors=${result.errors.joinToString("|")} report=$benchmarkReportPath"
            )
        }
    }

    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density, fontScale = 1f)
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF080B12)),
            color = Color(0xFF080B12)
        ) {
        when (flowStage) {
            ScannerFlowStage.Prep -> ScannerPrepStage(
                onBack = onBack,
                activeCaptureMode = activeCaptureMode,
                arCoreAvailability = arCoreAvailability,
                measuredScaleBarMm = measuredScaleBarMm,
                markerMatAssetPath = markerMatAssetPath,
                latestCalibrationGate = latestCalibrationGate,
                latestMarkerEvidence = latestMarkerEvidence,
                latestReadiness = latestReadiness,
                onMeasuredScaleBarMmChanged = {
                    measuredScaleBarMm = it.filter { char -> char.isDigit() || char == '.' }.take(6)
                    exportedZipPath = null
                    exportedPackageDirPath = null
                    reconstructionSummaryPath = null
                    reconstructionAuditPath = null
                    reconstructionBlockers = emptyList()
                    scannerWorkspaceDirPath = null
                    workspaceHandoffReady = false
                    benchmarkReportPath = null
                    latestCalibrationGate = null
                    latestMarkerEvidence = null
                    latestReadiness = null
                },
                onWriteMarkerMatAssets = {
                    scope.launch {
                        val path = withContext(Dispatchers.IO) {
                            writeMarkerMatAssetsForUser(context)
                        }
                        markerMatAssetPath = path
                        status = "A4 and US Letter marker mats written to $path. Print the matching paper size at 100%."
                    }
                },
                onStart = {
                    flowStage = ScannerFlowStage.Capture
                    status = "Tap the object to lock it."
                    if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
            ScannerFlowStage.Capture -> ScannerCaptureStage(
                onBack = onBack,
                previewHeight = previewHeight,
                hasCameraPermission = hasCameraPermission,
                activeCaptureMode = activeCaptureMode,
                sessionDir = sessionDir,
                objectRoi = objectRoi,
                frames = capturedFrames,
                selectedCapturePass = selectedCapturePass,
                capturePlanComplete = nextPrintableCapturePass(capturedFrames) == null,
                captureInProgress = captureInProgress,
                measuredScaleBarMm = measuredScaleBarMm,
                latestMarkerEvidence = latestMarkerEvidence,
                coachingMessage = scannerLiveCoachingMessage(
                    frames = capturedFrames,
                    objectRoi = objectRoi,
                    selectedCapturePass = selectedCapturePass
                ),
                onObjectTap = { offset, width, height ->
                    objectRoi = ScannerObjectRoi(
                        xNormalized = (offset.x / width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                        yNormalized = (offset.y / height.coerceAtLeast(1f)).coerceIn(0f, 1f),
                        source = ScannerObjectRoiSource.UserTap
                    )
                    focusScannerCamera(
                        camera = boundCamera,
                        xPx = offset.x,
                        yPx = offset.y,
                        widthPx = width,
                        heightPx = height
                    )
                    status = "Object locked."
                    exportedZipPath = null
                    exportedPackageDirPath = null
                    reconstructionSummaryPath = null
                    reconstructionAuditPath = null
                    reconstructionBlockers = emptyList()
                    scannerWorkspaceDirPath = null
                    workspaceHandoffReady = false
                    benchmarkReportPath = null
                    latestCalibrationGate = null
                    latestMarkerEvidence = null
                    latestReadiness = null
                },
                onArCoreViewReady = {
                    arCoreCaptureView = it
                },
                onArCoreStatus = { arStatus ->
                    status = arStatus
                },
                onPreviewViewReady = {
                    previewView = it
                },
                onManualCapture = {
                    val nextPass = nextPrintableCapturePass(capturedFrames)
                    if (nextPass == null) {
                        status = "Capture plan complete. Review the accepted frames before export."
                    } else {
                        selectedCapturePass = nextPass
                        startCapture()
                    }
                },
                onReview = {
                    flowStage = ScannerFlowStage.Review
                }
            )
            ScannerFlowStage.Review -> ScannerReviewStage(
                onBack = { flowStage = ScannerFlowStage.Capture },
                frames = capturedFrames,
                measuredScaleBarMm = measuredScaleBarMm,
                exportedZipPath = exportedZipPath,
                reconstructionSummaryPath = reconstructionSummaryPath,
                latestCalibrationGate = latestCalibrationGate,
                latestMarkerEvidence = latestMarkerEvidence,
                latestReadiness = latestReadiness,
                exportInProgress = exportInProgress,
                pipelineInProgress = pipelineInProgress,
                onDeleteFrame = { frame ->
                    capturedFrames.remove(frame)
                    exportedZipPath = null
                    exportedPackageDirPath = null
                    reconstructionSummaryPath = null
                    reconstructionAuditPath = null
                    reconstructionBlockers = emptyList()
                    scannerWorkspaceDirPath = null
                    workspaceHandoffReady = false
                    benchmarkReportPath = null
                    latestCalibrationGate = null
                    latestMarkerEvidence = null
                    latestReadiness = null
                    status = "Removed frame ${frame.id} from this scan package."
                },
                onExportPackage = { exportPackage() },
                onReconstruct = { runLocalPipeline() },
                onPrep = { flowStage = ScannerFlowStage.Prep }
            )
            ScannerFlowStage.Reconstruct -> ScannerReconstructStage(
                onBack = { flowStage = ScannerFlowStage.Review },
                status = status,
                measuredScaleBarMm = measuredScaleBarMm,
                exportedZipPath = exportedZipPath,
                reconstructionSummaryPath = reconstructionSummaryPath,
                reconstructionAuditPath = reconstructionAuditPath,
                reconstructionBlockers = reconstructionBlockers,
                latestCalibrationGate = latestCalibrationGate,
                latestMarkerEvidence = latestMarkerEvidence,
                latestReadiness = latestReadiness,
                benchmarkReportPath = benchmarkReportPath,
                exportInProgress = exportInProgress,
                pipelineInProgress = pipelineInProgress,
                workspaceHandoffReady = workspaceHandoffReady,
                workspaceImportInProgress = workspaceImportInProgress,
                benchmarkFixtureId = benchmarkFixtureId,
                benchmarkDimensionX = benchmarkDimensionX,
                benchmarkDimensionY = benchmarkDimensionY,
                benchmarkDimensionZ = benchmarkDimensionZ,
                benchmarkFixtureInput = benchmarkFixtureInput,
                onFixtureIdChanged = {
                    benchmarkFixtureId = it.take(64)
                    benchmarkReportPath = null
                },
                onDimensionXChanged = {
                    benchmarkDimensionX = scannerDimensionInput(it)
                    benchmarkReportPath = null
                },
                onDimensionYChanged = {
                    benchmarkDimensionY = scannerDimensionInput(it)
                    benchmarkReportPath = null
                },
                onDimensionZChanged = {
                    benchmarkDimensionZ = scannerDimensionInput(it)
                    benchmarkReportPath = null
                },
                onRunBenchmark = { runBenchmark() },
                onImportToWorkspace = { importValidatedWorkspaceHandoff() }
            )
        }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
            boundCamera = null
            arCoreCaptureView?.closeSession()
        }
    }
}

private fun focusScannerCamera(
    camera: Camera?,
    xPx: Float,
    yPx: Float,
    widthPx: Float,
    heightPx: Float
) {
    if (camera == null || widthPx <= 0f || heightPx <= 0f) return
    val point = SurfaceOrientedMeteringPointFactory(widthPx, heightPx)
        .createPoint(xPx.coerceIn(0f, widthPx), yPx.coerceIn(0f, heightPx))
    val action = FocusMeteringAction.Builder(
        point,
        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
    )
        .setAutoCancelDuration(4, TimeUnit.SECONDS)
        .build()
    runCatching {
        camera.cameraControl.startFocusAndMetering(action)
    }
}

private fun summarizeScannerReconstructionBlockers(
    pipeline: ScannerReconstructionPipelineResult
): List<String> {
    val raw = pipeline.blockingErrors
    val previewReady = pipeline.workspaceDir.resolve(SCANNER_EXPERIMENTAL_SPARSE_PREVIEW_PATH)
        .takeIf { it.isFile }
        ?.let { file ->
            runCatching { JSONObject(file.readText()).optBoolean("allowed", false) }.getOrDefault(false)
        }
        ?: false
    val summary = mutableListOf<String>()
    if (previewReady) {
        summary += "Experimental sparse preview ready: capture evidence exists, but it is not metric or printable."
    }
    if (raw.any { it.contains("scale", ignoreCase = true) || it.contains("marker", ignoreCase = true) || it.contains("calibration", ignoreCase = true) }) {
        summary += "Calibration deferred: real-world scale is not verified, so printable STL/3MF handoff stays blocked."
    }
    if (raw.any { it.contains("metric_pose", ignoreCase = true) || it.contains("bundle", ignoreCase = true) || it.contains("pose_refinement", ignoreCase = true) }) {
        summary += "Metric pose solve blocked: camera/object geometry is not strong enough for measured reconstruction."
    }
    if (raw.any { it.contains("feature", ignoreCase = true) || it.contains("track", ignoreCase = true) || it.contains("match_graph", ignoreCase = true) }) {
        summary += "Feature evidence weak: the pipeline needs more stable object texture, sharper frames, or better object isolation."
    }
    if (raw.any { it.contains("dense", ignoreCase = true) || it.contains("surface", ignoreCase = true) || it.contains("mesh", ignoreCase = true) }) {
        summary += "Dense mesh blocked: no slicer-grade metric surface is available yet."
    }
    if (raw.any { it.contains("printability", ignoreCase = true) || it.contains("slicer", ignoreCase = true) || it.contains("export", ignoreCase = true) || it.contains("handoff", ignoreCase = true) }) {
        summary += "Export blocked: workspace handoff requires verified scale, valid topology, and printability checks."
    }
    if (summary.isEmpty()) {
        summary += raw.take(6).ifEmpty { listOf("No blockers reported.") }
    }
    if (raw.isNotEmpty()) {
        summary += "Raw blocker count: ${raw.size}. Full details are in reconstruction_summary.json."
    }
    return summary.distinct()
}

@Composable
private fun ScannerPrepStage(
    onBack: () -> Unit,
    activeCaptureMode: ScannerUiCaptureMode,
    arCoreAvailability: ScannerArCoreAvailability,
    measuredScaleBarMm: String,
    markerMatAssetPath: String?,
    latestCalibrationGate: PrintableCalibrationGateResult?,
    latestMarkerEvidence: MarkerEvidenceSummary?,
    latestReadiness: ScannerReadinessSummary?,
    onMeasuredScaleBarMmChanged: (String) -> Unit,
    onWriteMarkerMatAssets: () -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScannerFlowHeader(
            title = "Printable Scan",
            subtitle = "Guided local capture",
            onBack = onBack
        )
        ScannerPlainPanel {
            Text("Before You Scan", color = Color.White, fontWeight = FontWeight.Bold)
            ScannerPrepLine("Use matte object, textured surface, steady lighting.")
            ScannerPrepLine("Print the marker mat at 100% scale for printable output.")
            ScannerPrepLine("Place the object inside the marker ring; keep tags visible.")
            ScannerPrepLine("Measure the printed 100 mm scale bar before capture.")
            ScannerPrepLine("Avoid glass, chrome, transparent plastic.")
        }
        ScannerPlainPanel {
            Text("Capture Setup", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                text = if (activeCaptureMode == ScannerUiCaptureMode.ArDepth) {
                    "This device will capture RGB, AR pose, and raw depth together."
                } else {
                    "This device will capture calibrated photos for local reconstruction."
                },
                color = Color(0xFFD5D8E2)
            )
            Text(
                text = if (arCoreAvailability.supported) {
                    "ARCore ${arCoreAvailability.status.lowercase(Locale.US).replace('_', ' ')}"
                } else {
                    "ARCore unavailable; photo capture remains available."
                },
                color = if (arCoreAvailability.supported) Color(0xFF9BE7B0) else Color(0xFFFFC27A),
                style = MaterialTheme.typography.bodySmall
            )
        }
        ScannerScaleVerificationPanel(
            measuredScaleBarMm = measuredScaleBarMm,
            markerMatAssetPath = markerMatAssetPath,
            onMeasuredScaleBarMmChanged = onMeasuredScaleBarMmChanged,
            onWriteMarkerMatAssets = onWriteMarkerMatAssets
        )
        ScannerCalibrationReadinessPanel(
            measuredScaleBarMm = measuredScaleBarMm,
            markerEvidence = latestMarkerEvidence,
            calibrationGate = latestCalibrationGate,
            readiness = latestReadiness
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            onClick = onStart
        ) {
            Text("Start printable scan")
        }
    }
}

@Composable
private fun ScannerCaptureStage(
    onBack: () -> Unit,
    previewHeight: Dp,
    hasCameraPermission: Boolean,
    activeCaptureMode: ScannerUiCaptureMode,
    sessionDir: File,
    objectRoi: ScannerObjectRoi,
    frames: List<ScanFrame>,
    selectedCapturePass: ScannerCapturePass,
    capturePlanComplete: Boolean,
    captureInProgress: Boolean,
    measuredScaleBarMm: String,
    latestMarkerEvidence: MarkerEvidenceSummary?,
    coachingMessage: String,
    onObjectTap: (Offset, Float, Float) -> Unit,
    onArCoreViewReady: (ScannerArCoreCaptureView) -> Unit,
    onArCoreStatus: (String) -> Unit,
    onPreviewViewReady: (PreviewView) -> Unit,
    onManualCapture: () -> Unit,
    onReview: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight * 1.42f)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onObjectTap(offset, size.width.toFloat(), size.height.toFloat())
                    }
                }
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission && activeCaptureMode == ScannerUiCaptureMode.Photo) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PreviewView(viewContext).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            onPreviewViewReady(this)
                        }
                    },
                    update = onPreviewViewReady
                )
            } else if (hasCameraPermission && activeCaptureMode == ScannerUiCaptureMode.ArDepth) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        ScannerArCoreCaptureView(
                            context = viewContext,
                            sessionDir = sessionDir,
                            onStatus = onArCoreStatus
                        ).also {
                            it.onResume()
                            onArCoreViewReady(it)
                        }
                    },
                    update = onArCoreViewReady
                )
            } else {
                Text(
                    text = "Camera permission required",
                    color = Color.White,
                    modifier = Modifier.padding(24.dp)
                )
            }
            ScannerObjectRoiOverlay(objectRoi = objectRoi)
            ScannerCaptureTopOverlay(onBack = onBack)
            ScannerObjectLockLabel(objectRoi = objectRoi)
            ScannerCalibrationCaptureOverlay(
                measuredScaleBarMm = measuredScaleBarMm,
                markerEvidence = latestMarkerEvidence
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xEE080B12))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScannerProgressStrip(frames = frames, selectedCapturePass = selectedCapturePass)
            Text(
                text = coachingMessage,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                onClick = onManualCapture,
                enabled = hasCameraPermission &&
                    !captureInProgress &&
                    objectRoi.source == ScannerObjectRoiSource.UserTap &&
                    !capturePlanComplete
            ) {
                Text(
                    when {
                        captureInProgress -> "Capturing..."
                        capturePlanComplete -> "Capture complete"
                        else -> "Capture frame"
                    }
                )
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onReview,
                enabled = frames.isNotEmpty() && !captureInProgress
            ) {
                Text("Review scan")
            }
        }
    }
}

@Composable
private fun ScannerReviewStage(
    onBack: () -> Unit,
    frames: List<ScanFrame>,
    measuredScaleBarMm: String,
    exportedZipPath: String?,
    reconstructionSummaryPath: String?,
    latestCalibrationGate: PrintableCalibrationGateResult?,
    latestMarkerEvidence: MarkerEvidenceSummary?,
    latestReadiness: ScannerReadinessSummary?,
    exportInProgress: Boolean,
    pipelineInProgress: Boolean,
    onDeleteFrame: (ScanFrame) -> Unit,
    onExportPackage: () -> Unit,
    onReconstruct: () -> Unit,
    onPrep: () -> Unit
) {
    val accepted = frames.count { it.quality.accepted }
    val rejected = frames.count { !it.quality.accepted }
    val blockers = scannerReviewBlockers(frames, measuredScaleBarMm)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScannerFlowHeader("Review", "$accepted accepted, $rejected rejected", onBack)
        ScannerPlainPanel {
            Text("Coverage", color = Color.White, fontWeight = FontWeight.Bold)
            ScannerCoverageHeatmap(frames = frames)
        }
        ScannerCalibrationReadinessPanel(
            measuredScaleBarMm = measuredScaleBarMm,
            markerEvidence = latestMarkerEvidence,
            calibrationGate = latestCalibrationGate,
            readiness = latestReadiness
        )
        ScannerPlainPanel {
            Text("Blockers", color = Color.White, fontWeight = FontWeight.Bold)
            if (blockers.isEmpty()) {
                Text("Capture package is ready for local reconstruction.", color = Color(0xFF9BE7B0))
            } else {
                blockers.forEach { blocker ->
                    Text(blocker, color = Color(0xFFFFC27A))
                }
            }
        }
        ScannerFrameReviewList(
            title = "Accepted Frames",
            frames = frames.filter { it.quality.accepted },
            onDeleteFrame = onDeleteFrame
        )
        ScannerFrameReviewList(
            title = "Rejected Frames",
            frames = frames.filter { !it.quality.accepted },
            onDeleteFrame = onDeleteFrame
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onPrep
        ) {
            Text("Calibration setup")
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onExportPackage,
            enabled = frames.isNotEmpty() && !exportInProgress
        ) {
            Text(
                when {
                    exportInProgress -> "Exporting..."
                    exportedZipPath == null -> "Export scan package"
                    else -> "Re-export scan package"
                }
            )
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onReconstruct,
            enabled = exportedZipPath != null && reconstructionSummaryPath == null && !pipelineInProgress
        ) {
            Text(if (pipelineInProgress) "Reconstructing..." else "Run local reconstruction checks")
        }
    }
}

@Composable
private fun ScannerReconstructStage(
    onBack: () -> Unit,
    status: String,
    measuredScaleBarMm: String,
    exportedZipPath: String?,
    reconstructionSummaryPath: String?,
    reconstructionAuditPath: String?,
    reconstructionBlockers: List<String>,
    latestCalibrationGate: PrintableCalibrationGateResult?,
    latestMarkerEvidence: MarkerEvidenceSummary?,
    latestReadiness: ScannerReadinessSummary?,
    benchmarkReportPath: String?,
    exportInProgress: Boolean,
    pipelineInProgress: Boolean,
    workspaceHandoffReady: Boolean,
    workspaceImportInProgress: Boolean,
    benchmarkFixtureId: String,
    benchmarkDimensionX: String,
    benchmarkDimensionY: String,
    benchmarkDimensionZ: String,
    benchmarkFixtureInput: ScannerBenchmarkFixtureInputResult,
    onFixtureIdChanged: (String) -> Unit,
    onDimensionXChanged: (String) -> Unit,
    onDimensionYChanged: (String) -> Unit,
    onDimensionZChanged: (String) -> Unit,
    onRunBenchmark: () -> Unit,
    onImportToWorkspace: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScannerFlowHeader("Reconstruct", "Local printable attempt", onBack)
        ScannerPlainPanel {
            Text("Result", color = Color.White, fontWeight = FontWeight.Bold)
            Text(status, color = Color(0xFFD5D8E2))
            WorkflowStatusLine(
                label = "Package",
                value = if (exportInProgress) "exporting" else exportedZipPath?.let { "ready" } ?: "missing"
            )
            WorkflowStatusLine(
                label = "Pipeline",
                value = when {
                    pipelineInProgress -> "running"
                    reconstructionSummaryPath != null -> "complete"
                    else -> "not run"
                }
            )
            WorkflowStatusLine(label = "Benchmark", value = benchmarkReportPath?.let { "report ready" } ?: "waiting")
            WorkflowStatusLine(label = "Audit", value = reconstructionAuditPath?.let { "report ready" } ?: "waiting")
            WorkflowStatusLine(
                label = "Workspace import",
                value = when {
                    workspaceImportInProgress -> "importing"
                    workspaceHandoffReady -> "ready"
                    reconstructionSummaryPath != null -> "blocked"
                    else -> "waiting"
                }
            )
            if (reconstructionBlockers.isNotEmpty()) {
                Text("Blocker summary", color = Color.White, fontWeight = FontWeight.Bold)
                reconstructionBlockers.forEach { blocker ->
                    Text(blocker, color = Color(0xFFFFC27A))
                }
            }
            reconstructionSummaryPath?.let {
                Text("Summary: $it", color = Color(0xFF9FC7FF), style = MaterialTheme.typography.bodySmall)
            }
            reconstructionAuditPath?.let {
                Text("Audit: $it", color = Color(0xFF9FC7FF), style = MaterialTheme.typography.bodySmall)
            }
        }
        ScannerCalibrationReadinessPanel(
            measuredScaleBarMm = measuredScaleBarMm,
            markerEvidence = latestMarkerEvidence,
            calibrationGate = latestCalibrationGate,
            readiness = latestReadiness
        )
        ScannerBenchmarkFixturePanel(
            fixtureId = benchmarkFixtureId,
            dimensionX = benchmarkDimensionX,
            dimensionY = benchmarkDimensionY,
            dimensionZ = benchmarkDimensionZ,
            fixtureInput = benchmarkFixtureInput,
            onFixtureIdChanged = onFixtureIdChanged,
            onDimensionXChanged = onDimensionXChanged,
            onDimensionYChanged = onDimensionYChanged,
            onDimensionZChanged = onDimensionZChanged
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRunBenchmark,
            enabled = reconstructionSummaryPath != null && benchmarkFixtureInput.valid
        ) {
            Text("Run accuracy benchmark")
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onImportToWorkspace,
            enabled = workspaceHandoffReady && !workspaceImportInProgress
        ) {
            Text(if (workspaceImportInProgress) "Importing..." else "Import validated scan to workspace")
        }
        ScannerCurrentScopeNotes()
    }
}

@Composable
private fun ScannerFlowHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFFD5D8E2), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ScannerPlainPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xE60B0F18), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3F4658), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun ScannerPrepLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color(0xFF7DB8FF), RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, color = Color(0xFFD5D8E2))
    }
}

@Composable
private fun BoxScope.ScannerCaptureTopOverlay(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }
        Text(
            text = "Printable scan",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Color(0xAA080B12), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun BoxScope.ScannerObjectLockLabel(objectRoi: ScannerObjectRoi) {
    Text(
        text = if (objectRoi.source == ScannerObjectRoiSource.UserTap) "Object locked" else "Tap object",
        color = if (objectRoi.source == ScannerObjectRoiSource.UserTap) Color(0xFF9BE7B0) else Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .align(Alignment.Center)
            .background(Color(0xAA080B12), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun BoxScope.ScannerCalibrationCaptureOverlay(
    measuredScaleBarMm: String,
    markerEvidence: MarkerEvidenceSummary?
) {
    val scaleReady = scannerScaleBarVerification(measuredScaleBarMm)?.valid == true
    val markersReady = markerEvidence?.let {
        it.detectorStatus == "ready" && it.observationCount > 0 && it.averageReprojectionErrorPx != null
    } == true
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 58.dp)
            .background(Color(0xAA080B12), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScannerTinyStatusChip("Scale", scaleReady)
        ScannerTinyStatusChip("Markers", markersReady)
    }
}

@Composable
private fun ScannerTinyStatusChip(
    label: String,
    ready: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (ready) Color(0xFF9BE7B0) else Color(0xFFFFC27A), RoundedCornerShape(4.dp))
        )
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun ScannerProgressStrip(
    frames: List<ScanFrame>,
    selectedCapturePass: ScannerCapturePass
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        printableCapturePlan().forEach { target ->
            val accepted = frames.count { it.capturePass == target.pass && it.quality.accepted }
            ScannerPassProgress(
                label = target.label,
                accepted = accepted,
                target = target.target,
                selected = selectedCapturePass == target.pass
            )
        }
    }
}

@Composable
private fun ScannerPassProgress(
    label: String,
    accepted: Int,
    target: Int,
    selected: Boolean
) {
    val progress = (accepted.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(42.dp)) {
            drawArc(
                color = Color(0xFF303747),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = StrokeCap.Round)
            )
            drawArc(
                color = if (selected) Color(0xFF7DB8FF) else Color(0xFF9BE7B0),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "$label $accepted/$target",
            color = Color(0xFFD5D8E2),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun ScannerCoverageHeatmap(frames: List<ScanFrame>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        printableCapturePlan().forEach { target ->
            val accepted = frames.count { it.capturePass == target.pass && it.quality.accepted }
            val progress = (accepted.toFloat() / target.target.toFloat()).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(target.label, color = Color.White, modifier = Modifier.width(58.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .background(Color(0xFF303747), RoundedCornerShape(5.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(10.dp)
                            .background(
                                if (progress >= 1f) Color(0xFF9BE7B0) else Color(0xFF7DB8FF),
                                RoundedCornerShape(5.dp)
                            )
                    )
                }
                Text(
                    "$accepted/${target.target}",
                    color = Color(0xFFD5D8E2),
                    modifier = Modifier.width(54.dp)
                )
            }
        }
    }
}

@Composable
private fun ScannerFrameReviewList(
    title: String,
    frames: List<ScanFrame>,
    onDeleteFrame: (ScanFrame) -> Unit
) {
    ScannerPlainPanel {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        if (frames.isEmpty()) {
            Text("None", color = Color(0xFFD5D8E2))
        }
        frames.take(24).forEach { frame ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${frame.id}  ${frame.capturePass.displayName}", color = Color.White)
                    Text(
                        text = frame.quality.rejectionReasons.joinToString().ifBlank { "sharp measured frame" },
                        color = Color(0xFFD5D8E2),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(onClick = { onDeleteFrame(frame) }) {
                    Text("Delete")
                }
            }
        }
        if (frames.size > 24) {
            Text("${frames.size - 24} more frames kept in package.", color = Color(0xFFD5D8E2))
        }
    }
}

@Composable
private fun BoxScope.ScannerTopCaptureBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Scanner",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Local capture",
                color = Color(0xFFD5D8E2),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun BoxScope.ScannerPreviewHud(
    status: String,
    frames: List<ScanFrame>,
    selectedCapturePass: ScannerCapturePass,
    selectedCaptureMode: ScannerUiCaptureMode,
    objectRoi: ScannerObjectRoi,
    deviceDepthCapability: ScannerDeviceDepthCapability
) {
    val accepted = frames.count { it.quality.accepted }
    val rejected = frames.count { !it.quality.accepted }
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 76.dp)
            .background(Color(0xCC080B12), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = status,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${selectedCaptureMode.statusLabel} | ${selectedCapturePass.displayName}: $accepted ok / $rejected rejected",
                color = Color(0xFFD5D8E2),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (objectRoi.source == ScannerObjectRoiSource.UserTap) "Object locked" else "Tap object",
                color = if (objectRoi.source == ScannerObjectRoiSource.UserTap) Color(0xFF9BE7B0) else Color(0xFFFFC27A),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
        Text(
            text = if (deviceDepthCapability.depthOutputSupported) {
                "Depth-capable camera detected"
            } else {
                "Photo scan mode; depth not advertised"
            },
            color = if (deviceDepthCapability.depthOutputSupported) Color(0xFF9BE7B0) else Color(0xFFD5D8E2),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ScannerCaptureModePanel(
    activeCaptureMode: ScannerUiCaptureMode,
    arCoreAvailability: ScannerArCoreAvailability
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xE60B0F18), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3F4658), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Scanner Mode", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            text = if (activeCaptureMode == ScannerUiCaptureMode.ArDepth) {
                "Using AR depth assist automatically for measured pose and depth evidence."
            } else {
                "Using calibrated photo capture because AR depth is unavailable."
            },
            color = Color(0xFFD5D8E2),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = if (arCoreAvailability.supported) {
                "ARCore ${arCoreAvailability.status.lowercase(Locale.US).replace('_', ' ')}"
            } else {
                "ARCore unavailable on this device; photo capture remains available"
            },
            color = if (arCoreAvailability.supported) Color(0xFF9BE7B0) else Color(0xFFFFC27A),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ScannerCapturePlanPanel(
    frames: List<ScanFrame>,
    selectedCapturePass: ScannerCapturePass,
    onCapturePassSelected: (ScannerCapturePass) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xE60B0F18), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3F4658), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Capture Plan", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            text = "Select the pass, capture sharp keyframes, then export.",
            color = Color(0xFFD5D8E2)
        )
        ScannerCapturePass.entries.forEach { pass ->
            val passFrames = frames.filter { it.capturePass == pass }
            val accepted = passFrames.count { it.quality.accepted }
            val rejected = passFrames.count { !it.quality.accepted }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pass.displayName, color = Color.White)
                    Text("$accepted accepted, $rejected rejected", color = Color(0xFFB7BBC8))
                }
                OutlinedButton(onClick = { onCapturePassSelected(pass) }) {
                    Text(if (pass == selectedCapturePass) "Selected" else "Use")
                }
            }
        }
    }
}

@Composable
private fun ScannerObjectRoiOverlay(objectRoi: ScannerObjectRoi) {
    val clamped = objectRoi.clamped()
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(
            x = size.width * clamped.xNormalized,
            y = size.height * clamped.yNormalized
        )
        val regionSize = minOf(size.width, size.height) * 0.32f
        val topLeft = Offset(
            x = (center.x - regionSize / 2f).coerceIn(8f, size.width - regionSize - 8f),
            y = (center.y - regionSize / 2f).coerceIn(8f, size.height - regionSize - 8f)
        )
        drawRoundRect(
            color = Color(0x447DB8FF),
            topLeft = topLeft,
            size = Size(regionSize, regionSize),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )
        drawCircle(
            color = Color(0x557DB8FF),
            radius = 14f,
            center = center
        )
        drawCircle(
            color = Color(0xFF7DB8FF),
            radius = 14f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
        )
    }
}

@Composable
private fun ScannerScaleVerificationPanel(
    measuredScaleBarMm: String,
    markerMatAssetPath: String?,
    onWriteMarkerMatAssets: () -> Unit,
    onMeasuredScaleBarMmChanged: (String) -> Unit
) {
    val verification = scannerScaleBarVerification(measuredScaleBarMm)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xE60B0F18), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3F4658), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Guide Marker Mat", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            text = "The marker mat is a printable calibration sheet: AprilTag markers around the object area plus a 100 mm scale bar. Generate A4 or US Letter and print the one that matches your paper.",
            color = Color(0xFFD5D8E2)
        )
        OutlinedButton(onClick = onWriteMarkerMatAssets) {
            Text("Generate marker mat")
        }
        markerMatAssetPath?.let {
            Text("Marker mat folder: $it", color = Color(0xFF9BC7FF))
            Text(
                "Use A4 or US Letter to match your paper. Print the SVG at 100% scale; do not fit-to-page.",
                color = Color(0xFFD5D8E2),
                style = MaterialTheme.typography.bodySmall
            )
        }
        OutlinedTextField(
            value = measuredScaleBarMm,
            onValueChange = onMeasuredScaleBarMmChanged,
            label = { Text("Measured scale bar, mm") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        verification?.let {
            Text(
                text = if (it.valid) {
                    "Scale accepted. Correction ${String.format(Locale.US, "%.5f", it.scaleCorrection)}"
                } else {
                    it.message
                },
                color = if (it.valid) Color(0xFF9BE7B0) else Color(0xFFFFC27A)
            )
        }
    }
}

@Composable
private fun ScannerCalibrationReadinessPanel(
    measuredScaleBarMm: String,
    markerEvidence: MarkerEvidenceSummary?,
    calibrationGate: PrintableCalibrationGateResult?,
    readiness: ScannerReadinessSummary?
) {
    val scaleVerification = scannerScaleBarVerification(measuredScaleBarMm)
    ScannerPlainPanel {
        Text("Printable Calibration", color = Color.White, fontWeight = FontWeight.Bold)
        WorkflowStatusLine(
            label = "Scale bar",
            value = when {
                scaleVerification?.valid == true -> "verified"
                measuredScaleBarMm.isBlank() -> "not entered"
                else -> "outside tolerance"
            }
        )
        WorkflowStatusLine(
            label = "Marker detector",
            value = markerEvidence?.detectorStatus ?: "not run"
        )
        WorkflowStatusLine(
            label = "Marker observations",
            value = markerEvidence?.let {
                "${it.observationCount} tags / ${it.observedFrameCount} frames"
            } ?: "export package to test"
        )
        WorkflowStatusLine(
            label = "Reprojection",
            value = markerEvidence?.averageReprojectionErrorPx?.let {
                String.format(Locale.US, "%.2f px", it)
            } ?: "missing"
        )
        WorkflowStatusLine(
            label = "Scale confidence",
            value = markerEvidence?.let {
                "${(it.markerScaleConfidence * 100f).toInt()}%"
            } ?: "missing"
        )
        readiness?.let {
            WorkflowStatusLine(
                label = "Printable readiness",
                value = "${(it.printableReadiness * 100f).toInt()}%"
            )
        }
        val blockers = buildList {
            scaleVerification?.let {
                if (!it.valid) add("Printed scale bar is outside 1% tolerance.")
            } ?: add("Measure the printed 100 mm scale bar.")
            if (markerEvidence == null) {
                add("Export the package to run marker detection.")
            } else {
                if (markerEvidence.observationCount == 0) add("No AprilTags detected; keep the mat visible around the object.")
                if (markerEvidence.averageReprojectionErrorPx == null) add("Marker pose/reprojection is missing; printable scale is not proven.")
                markerEvidence.reasons.forEach { add(scannerHumanReadableBlocker(it)) }
            }
            calibrationGate?.reasons.orEmpty().forEach { add(scannerHumanReadableBlocker(it)) }
        }.distinct()
        if (calibrationGate?.allowed == true) {
            Text("Printable scale evidence passed.", color = Color(0xFF9BE7B0))
        } else {
            blockers.take(6).forEach { blocker ->
                Text(blocker, color = Color(0xFFFFC27A), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ScannerWorkflowStatusPanel(
    exportedZipPath: String?,
    reconstructionSummaryPath: String?,
    benchmarkReportPath: String?,
    deviceDepthCapability: ScannerDeviceDepthCapability
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xE60B0F18), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3F4658), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Workflow", color = Color.White, fontWeight = FontWeight.Bold)
        WorkflowStatusLine(label = "Package", value = exportedZipPath?.let { "ready" } ?: "not exported")
        WorkflowStatusLine(label = "Pipeline", value = reconstructionSummaryPath?.let { "run complete" } ?: "waiting")
        WorkflowStatusLine(label = "Benchmark", value = benchmarkReportPath?.let { "report ready" } ?: "waiting")
        WorkflowStatusLine(
            label = "Depth",
            value = if (deviceDepthCapability.depthOutputSupported) {
                "device capable"
            } else {
                deviceDepthCapability.reason.replace('_', ' ')
            }
        )
    }
}

@Composable
private fun WorkflowStatusLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFFD5D8E2))
        Text(value, color = Color(0xFF9BC7FF))
    }
}

@Composable
private fun ScannerBenchmarkFixturePanel(
    fixtureId: String,
    dimensionX: String,
    dimensionY: String,
    dimensionZ: String,
    fixtureInput: ScannerBenchmarkFixtureInputResult,
    onFixtureIdChanged: (String) -> Unit,
    onDimensionXChanged: (String) -> Unit,
    onDimensionYChanged: (String) -> Unit,
    onDimensionZChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xE60B0F18), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3F4658), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Accuracy Benchmark", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            text = "Known fixture dimensions for the accuracy report.",
            color = Color(0xFFD5D8E2)
        )
        OutlinedTextField(
            value = fixtureId,
            onValueChange = onFixtureIdChanged,
            label = { Text("Fixture id") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BenchmarkDimensionField(
                value = dimensionX,
                label = "X mm",
                onValueChange = onDimensionXChanged,
                modifier = Modifier.weight(1f)
            )
            BenchmarkDimensionField(
                value = dimensionY,
                label = "Y mm",
                onValueChange = onDimensionYChanged,
                modifier = Modifier.weight(1f)
            )
            BenchmarkDimensionField(
                value = dimensionZ,
                label = "Z mm",
                onValueChange = onDimensionZChanged,
                modifier = Modifier.weight(1f)
            )
        }
        if (fixtureInput.valid) {
            Text(
                text = "Fixture ready: ${
                    fixtureInput.fixture!!.expectedDimensionsMm.joinToString(" x ") {
                        String.format(Locale.US, "%.2f", it)
                    }
                } mm",
                color = Color(0xFF9BE7B0)
            )
        } else {
            Text(
                text = "Fixture blocked: ${fixtureInput.errors.joinToString()}",
                color = Color(0xFFFFC27A)
            )
        }
    }
}

@Composable
private fun BenchmarkDimensionField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
private fun ScannerCurrentScopeNotes() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF3F4658), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Validation Rules", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            text = "No mesh export until scale, provenance, topology, slicer load, and benchmark gates pass.",
            color = Color(0xFFD5D8E2)
        )
    }
}

private data class ScannerCaptureTarget(
    val pass: ScannerCapturePass,
    val label: String,
    val target: Int
)

private fun printableCapturePlan(): List<ScannerCaptureTarget> =
    listOf(
        ScannerCaptureTarget(ScannerCapturePass.MidRing, "Mid", 12),
        ScannerCaptureTarget(ScannerCapturePass.HighRing, "High", 12),
        ScannerCaptureTarget(ScannerCapturePass.LowRing, "Low", 12),
        ScannerCaptureTarget(ScannerCapturePass.TopDetail, "Detail", 8)
    )

internal fun nextPrintableCapturePass(frames: List<ScanFrame>): ScannerCapturePass? =
    printableCapturePlan().firstOrNull { target ->
        frames.count { it.capturePass == target.pass && it.quality.accepted } < target.target
    }?.pass

private fun scannerLiveCoachingMessage(
    frames: List<ScanFrame>,
    objectRoi: ScannerObjectRoi,
    selectedCapturePass: ScannerCapturePass
): String {
    if (objectRoi.source != ScannerObjectRoiSource.UserTap) return "Tap object"
    val lastRejectedReason = frames.lastOrNull { !it.quality.accepted }?.quality?.rejectionReasons?.firstOrNull()
    val rejectedForPass = frames.count { it.capturePass == selectedCapturePass && !it.quality.accepted }
    val acceptedForPass = frames.count { it.capturePass == selectedCapturePass && it.quality.accepted }
    val targetForPass = printableCapturePlan().firstOrNull { it.pass == selectedCapturePass }?.target ?: 0
    if (acceptedForPass < targetForPass && rejectedForPass >= SCANNER_MAX_RETAINED_REJECTED_FRAMES_PER_PASS) {
        return when (selectedCapturePass) {
            ScannerCapturePass.MidRing -> "Move around to a new side"
            ScannerCapturePass.HighRing -> "Raise camera, then move around"
            ScannerCapturePass.LowRing -> "Lower camera, then move around"
            ScannerCapturePass.TopDetail -> "Get closer from a new angle"
            ScannerCapturePass.Underside -> "Show underside with overlap"
        }
    }
    return when (lastRejectedReason) {
        "too_blurry" -> "Move slower"
        "clipped_highlights" -> "Too much glare"
        "insufficient_depth_coverage" -> "Depth weak, add photos"
        "object_too_small" -> "Get closer for details"
        "capture_too_fast" -> "Hold steady between captures"
        "insufficient_view_change" -> when (selectedCapturePass) {
            ScannerCapturePass.MidRing -> "Move farther around object"
            ScannerCapturePass.HighRing -> "Raise camera and rotate"
            ScannerCapturePass.LowRing -> "Lower camera and rotate"
            ScannerCapturePass.TopDetail -> "New detail angle needed"
            ScannerCapturePass.Underside -> "Need underside overlap"
        }
        else -> when (selectedCapturePass) {
            ScannerCapturePass.HighRing -> "High angle needed"
            ScannerCapturePass.TopDetail -> "Get closer for details"
            ScannerCapturePass.LowRing -> "Need lower angle"
            ScannerCapturePass.MidRing -> "Need more views of the back"
            ScannerCapturePass.Underside -> "Add underside photos"
        }
    }
}

private fun scannerCaptureStatus(
    frame: ScanFrame,
    retainedFrames: List<ScanFrame>,
    retained: Boolean
): String {
    if (frame.quality.accepted) {
        val nextPass = nextPrintableCapturePass(retainedFrames)
        return if (nextPass == null) {
            "Capture plan complete. Review accepted frames before export."
        } else {
            "Accepted ${frame.capturePass.displayName} frame ${frame.id}. Next: ${nextPass.displayName}."
        }
    }
    val reasons = frame.quality.rejectionReasons.joinToString()
    return if (retained) {
        "Rejected ${frame.capturePass.displayName} frame ${frame.id}: $reasons"
    } else {
        "Still rejected: $reasons. Move to a new angle before capturing again."
    }
}

private fun deleteScannerFrameAssets(sessionDir: File, frame: ScanFrame) {
    listOfNotNull(
        frame.rgbPath,
        frame.maskPath,
        frame.depth16Path,
        frame.depthConfidencePath
    ).forEach { relativePath ->
        runCatching { sessionDir.resolve(relativePath).delete() }
    }
}

private fun scannerReviewBlockers(
    frames: List<ScanFrame>,
    measuredScaleBarMm: String
): List<String> {
    val accepted = frames.filter { it.quality.accepted }
    return buildList {
        printableCapturePlan().forEach { target ->
            val acceptedForPass = accepted.count { it.capturePass == target.pass }
            if (acceptedForPass < target.target) {
                add("Missing ${target.label.lowercase(Locale.US)} coverage: $acceptedForPass/${target.target}")
            }
        }
        val scaleVerified = measuredScaleBarMm.toFloatOrNull()?.let {
            runCatching {
                verifyPrintedScale(
                    PrintedScaleVerificationInput(expectedMm = 100f, measuredMm = it)
                ).valid
            }.getOrDefault(false)
        } == true
        if (!scaleVerified) add("Scale not verified")
        if (accepted.size < 44) add("Too few sharp frames: ${accepted.size}/44")
    }
}

internal data class ScannerPackageExportResult(
    val packageDir: File,
    val zipFile: File,
    val validation: ScannerPackageValidationResult,
    val calibrationGate: PrintableCalibrationGateResult,
    val markerEvidence: MarkerEvidenceSummary,
    val readiness: ScannerReadinessSummary
)

private fun scannerDimensionInput(value: String): String =
    value.filter { char -> char.isDigit() || char == '.' }.take(8)

private fun scannerScaleBarVerification(measuredScaleBarMm: String): PrintedScaleVerificationResult? =
    measuredScaleBarMm.toFloatOrNull()?.let {
        runCatching {
            verifyPrintedScale(
                PrintedScaleVerificationInput(
                    expectedMm = 100f,
                    measuredMm = it
                )
            )
        }.getOrNull()
    }

private fun writeMarkerMatAssetsForUser(context: Context): String {
    val stagingDir = File(context.cacheDir, "scanner-marker-mat-export").also {
        runCatching { it.deleteRecursively() }
        it.mkdirs()
    }
    val tagProvider: (Int) -> ByteArray? = { markerId ->
        runCatching {
            context.assets.open(
                "scanner/apriltag/tag36h11/tag36_11_${markerId.toString().padStart(5, '0')}.png"
            ).use { it.readBytes() }
        }.getOrNull()
    }
    val files = writeStandardMarkerMatAssets(stagingDir, tagProvider)
    val publicPath = runCatching {
        copyMarkerMatAssetsToDownloads(context, files)
    }.getOrNull()
    if (publicPath != null) {
        return publicPath
    }
    val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        ?.resolve("scanner-marker-mat")
        ?: File(context.cacheDir, "scanner-marker-mat")
    runCatching { fallbackDir.deleteRecursively() }
    writeStandardMarkerMatAssets(fallbackDir, tagProvider)
    return fallbackDir.absolutePath
}

private fun copyMarkerMatAssetsToDownloads(
    context: Context,
    files: List<File>
): String {
    val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/MobileSlicer/scanner-marker-mat"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        files.forEach { file ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, scannerMarkerMatMimeType(file))
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create Downloads entry for ${file.name}")
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(file).use { input -> input.copyTo(output) }
            } ?: error("Unable to open Downloads stream for ${file.name}")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return "Downloads/MobileSlicer/scanner-marker-mat"
    }

    val downloadsDir = Environment
        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        .resolve("MobileSlicer/scanner-marker-mat")
    require(downloadsDir.mkdirs() || downloadsDir.isDirectory) {
        "Unable to create Downloads marker mat directory: ${downloadsDir.absolutePath}"
    }
    files.forEach { file ->
        file.copyTo(downloadsDir.resolve(file.name), overwrite = true)
    }
    return downloadsDir.absolutePath
}

private fun scannerMarkerMatMimeType(file: File): String =
    when (file.extension.lowercase(Locale.US)) {
        "svg" -> "image/svg+xml"
        "json" -> "application/json"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

private fun scannerHumanReadableBlocker(reason: String): String =
    when (reason) {
        "scale_source_not_printable" -> "Printable mode needs verified marker-mat scale, not depth-only scale."
        "calibration_missing" -> "Calibration is missing."
        "printed_scale_unverified" -> "Printed scale bar is not verified."
        "scale_confidence_low" -> "Scale confidence is too low."
        "marker_reprojection_missing" -> "Marker reprojection evidence is missing."
        "marker_reprojection_high" -> "Marker reprojection error is too high."
        "marker_observations_insufficient" -> "Not enough marker observations."
        "marker_frame_coverage_insufficient" -> "Markers were visible in too few frames."
        "marker_detector_not_ready" -> "AprilTag detector is not ready."
        "markers_not_detected" -> "No marker tags were detected."
        "marker_pose_intrinsics_missing" -> "Marker pose needs camera intrinsics for those frames."
        "two_sided_alignment_unknown" -> "Underside/two-sided alignment is unknown."
        else -> reason.replace('_', ' ')
    }

private fun scannerPreviewHeight(screenHeight: Dp): Dp {
    val target = screenHeight * 0.42f
    return target.coerceIn(300.dp, 430.dp)
}

private fun capturedScanFrame(
    index: Int,
    context: android.content.Context?,
    sessionDir: File,
    frameFile: File,
    capturePass: ScannerCapturePass
): ScanFrame {
    val relativePath = frameFile.relativeTo(sessionDir).invariantSeparatorsPath
    val qualityAnalysis = analyzeScannerJpegQuality(frameFile)
    val quality = if (qualityAnalysis == null) {
        FrameQuality(
            blurScore = 0f,
            exposureScore = 0f,
            overlapScore = 0f,
            trackingGood = null,
            depthCoverage = null,
            markerVisibility = 0f,
            scaleConfidence = 0f,
            materialRisk = 0f,
            accepted = false,
            rejectionReasons = listOf("image_decode_failed")
        )
    } else {
        evaluateScannerFrameQuality(
            ScannerFrameQualityInput(
                blurScore = qualityAnalysis.blurScore,
                exposureScore = qualityAnalysis.exposureScore,
                overlapScore = 0.5f,
                coverageGain = 0.1f,
                objectMaskArea = 0.1f,
                clippedHighlightRatio = qualityAnalysis.clippedHighlightRatio,
                trackingGood = null,
                depthCoverage = null,
                markerVisibility = 0f,
                scaleConfidence = 0f,
                materialRisk = (qualityAnalysis.clippedHighlightRatio * 2f).coerceIn(0f, 1f),
                depthRequired = false,
                markersRequired = false,
                scaleRequired = false,
                lateCapture = false
            )
        )
    }
    val imageWidth = qualityAnalysis?.width ?: 0
    val imageHeight = qualityAnalysis?.height ?: 0
    val intrinsics = context?.let {
        scannerBackCameraIntrinsics(
            context = it.applicationContext,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }
    return ScanFrame(
        id = index.toString().padStart(6, '0'),
        timestampNs = System.nanoTime(),
        rgbPath = relativePath,
        rgbSha256 = sha256Hex(frameFile),
        maskPath = null,
        maskSha256 = null,
        depth16Path = null,
        depthSha256 = null,
        depthConfidencePath = null,
        poseWorldFromCamera = null,
        intrinsics = intrinsics,
        distortion = null,
        lensFacing = "back",
        focalLengthMm = null,
        exposureTimeNs = null,
        iso = null,
        focusDistance = null,
        whiteBalanceMode = null,
        width = imageWidth,
        height = imageHeight,
        capturePass = capturePass,
        quality = quality,
        forcedCapture = false
    )
}

internal fun exportCapturePackage(
    sessionDir: File,
    scanId: String,
    frames: List<ScanFrame>,
    measuredScaleBarMm: Float?,
    context: android.content.Context?,
    objectRoi: ScannerObjectRoi = defaultScannerObjectRoi(),
    markerDetector: ScannerMarkerDetector = ScannerAprilTagNativeDetector(),
    softMaskGenerator: ScannerSoftMaskGenerator? = null
): ScannerPackageExportResult {
    val acceptedCaptureFrames = frames.filter { it.quality.accepted }
    val rejectedDiagnosticFrames = frames.filter { !it.quality.accepted }
    val activeSoftMaskGenerator = softMaskGenerator
        ?: context?.applicationContext?.let(::ScannerCompositeSoftMaskGenerator)
        ?: HeuristicScannerSoftMaskGenerator
    val softMaskResultsByFrameId = try {
        acceptedCaptureFrames.associate { frame ->
            val absoluteFramePath = sessionDir.resolve(frame.rgbPath).absolutePath
            frame.id to activeSoftMaskGenerator.generateMask(
                sessionDir = sessionDir,
                frame = frame,
                absoluteFramePath = absoluteFramePath,
                objectRoi = objectRoi
            )
        }
    } finally {
        (activeSoftMaskGenerator as? AutoCloseable)?.close()
    }
    val framesWithMasks = acceptedCaptureFrames.map { frame ->
        val mask = softMaskResultsByFrameId[frame.id]
        if (mask == null) {
            frame
        } else {
            frame.copy(
                maskPath = mask.maskPath,
                maskSha256 = mask.maskSha256
            )
        }
    }
    val softMaskResults = softMaskResultsByFrameId.values.filterNotNull()

    val rawMarkerObservations = framesWithMasks.flatMap { frame ->
        val absoluteFramePath = sessionDir.resolve(frame.rgbPath).absolutePath
        markerDetector.detectMarkers(frame, absoluteFramePath)
    }
    val calibratedMarkerObservations = calibrateMarkerObservations(
        observations = rawMarkerObservations,
        frames = framesWithMasks
    )
    val rawMarkerEvidence = summarizeMarkerEvidence(
        observations = calibratedMarkerObservations,
        acceptedFrameCount = framesWithMasks.count { it.quality.accepted },
        detectorName = markerDetector.detectorName,
        detectorStatus = markerDetector.detectorStatus
    )
    val scaleVerification = measuredScaleBarMm?.let {
        runCatching {
            verifyPrintedScale(
                PrintedScaleVerificationInput(
                    expectedMm = 100f,
                    measuredMm = it
                )
            )
        }.getOrNull()
    }
    val calibration = scaleVerification?.takeIf { it.valid }?.let {
        val scaleConfidence = minOf(0.95f, rawMarkerEvidence.markerScaleConfidence)
        ScannerCalibration(
            markerType = ScannerMarkerSystem.AprilTag.manifestValue,
            markerSizeMm = 40f,
            printedScaleBarExpectedMm = it.expectedMm,
            printedScaleBarMeasuredMm = it.measuredMm,
            scaleCorrection = it.scaleCorrection,
            scaleConfidence = scaleConfidence,
            markerReprojectionErrorPx = rawMarkerEvidence.averageReprojectionErrorPx
        )
    }
    val markerObservationsByFrame = calibratedMarkerObservations.groupBy { it.frameId }
    val framesWithMarkerEvidence = framesWithMasks.map { frame ->
        val frameMarkerVisibility = if (markerObservationsByFrame[frame.id].orEmpty().isNotEmpty()) 1f else 0f
        frame.copy(
            quality = frame.quality.copy(
                markerVisibility = frameMarkerVisibility,
                scaleConfidence = calibration?.scaleConfidence ?: 0f
            )
        )
    }
    val markerEvidence = summarizeMarkerEvidence(
        observations = calibratedMarkerObservations,
        acceptedFrameCount = framesWithMarkerEvidence.count { it.quality.accepted },
        detectorName = markerDetector.detectorName,
        detectorStatus = markerDetector.detectorStatus
    )
    val alignment = ScannerTwoSidedAlignment(
        objectMovedDuringSession = framesWithMasks.any { it.capturePass == ScannerCapturePass.Underside },
        alignmentMethod = if (framesWithMasks.any { it.capturePass == ScannerCapturePass.Underside }) {
            ScannerAlignmentMethod.Unknown
        } else {
            ScannerAlignmentMethod.NotMoved
        },
        alignmentConfidence = null,
        passCount = framesWithMarkerEvidence.map { it.capturePass }.distinct().size.coerceAtLeast(1)
    )
    val scaleSource = if (calibration != null) {
        ScannerScaleSource.VerifiedMarkerMat
    } else {
        ScannerScaleSource.None
    }
    val calibrationGate = evaluatePrintableCalibrationGate(
        scaleSource = scaleSource,
        calibration = calibration,
        alignment = alignment,
        markerEvidence = markerEvidence
    )
    val manifest = ScanPackageManifest(
        scanId = scanId,
        createdAtIso8601 = Instant.now().toString(),
        mode = ScannerMode.LocalPrintable,
        captureProfile = if (framesWithMarkerEvidence.any { it.depth16Path != null }) {
            ScannerCaptureProfile.Hybrid
        } else {
            ScannerCaptureProfile.HighResPhoto
        },
        frameCount = framesWithMarkerEvidence.size,
        acceptedFrameCount = framesWithMarkerEvidence.count { it.quality.accepted },
        forcedFrameCount = framesWithMarkerEvidence.count { it.forcedCapture },
        rejectedFrameCount = 0,
        hasArCorePoses = framesWithMarkerEvidence.any { it.poseWorldFromCamera?.size == 16 },
        hasDepth = framesWithMarkerEvidence.any { it.depth16Path != null },
        hasMasks = softMaskResults.isNotEmpty(),
        scaleSource = scaleSource,
        calibration = calibration,
        twoSidedAlignment = alignment,
        requestedOutputs = emptyList(),
        frames = framesWithMarkerEvidence
    )
    val maskSummary = scannerMaskQualitySummary(softMaskResults)
    val readiness = scannerReadinessSummary(
        frames = framesWithMarkerEvidence,
        calibrationGate = calibrationGate,
        markerEvidence = markerEvidence,
        maskSummary = maskSummary,
        depthPoseReadiness = scannerDepthPoseReadiness(framesWithMarkerEvidence)
    )
    val packageDir = File(
        sessionDir.parentFile,
        "${scanId}_package_${Instant.now().epochSecond.toString().lowercase(Locale.US)}"
    ).also {
        if (it.exists()) it.deleteRecursively()
        it.mkdirs()
    }
    copyScannerPackageFrameAssets(
        sourceDir = sessionDir,
        packageDir = packageDir,
        frames = framesWithMarkerEvidence
    )
    writeScannerRejectedCaptureDiagnostics(
        packageDir = packageDir,
        rejectedFrames = rejectedDiagnosticFrames,
        capturedFrameCount = frames.size,
        acceptedFrameCount = framesWithMarkerEvidence.size
    )
    writeScannerPackageDirectory(
        directory = packageDir,
        manifest = manifest,
        writeDiagnostics = {
            writeScannerDiagnostics(
                directory = packageDir,
                manifest = manifest,
                calibrationGate = calibrationGate,
                readiness = readiness,
                rejectedDiagnosticFrames = rejectedDiagnosticFrames,
                markerObservations = calibratedMarkerObservations,
                markerEvidence = markerEvidence,
                softMasks = softMaskResults,
                context = context
            )
        }
    )
    val zipFile = File(
        sessionDir.parentFile,
        "${scanId}_${framesWithMarkerEvidence.size}_${Instant.now().epochSecond.toString().lowercase(Locale.US)}.zip"
    )
    writeScannerPackageZip(packageDir, zipFile)
    return ScannerPackageExportResult(
        packageDir = packageDir,
        zipFile = zipFile,
        validation = validateScannerPackageZip(zipFile),
        calibrationGate = calibrationGate,
        markerEvidence = markerEvidence,
        readiness = readiness
    )
}

private fun copyScannerPackageFrameAssets(
    sourceDir: File,
    packageDir: File,
    frames: List<ScanFrame>
) {
    frames.forEach { frame ->
        listOfNotNull(
            frame.rgbPath,
            frame.maskPath,
            frame.depth16Path,
            frame.depthConfidencePath
        ).distinct().forEach { relativePath ->
            val source = sourceDir.resolve(relativePath)
            val target = packageDir.resolve(relativePath)
            require(source.isFile) {
                "Scanner package source file missing: $relativePath"
            }
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
        }
    }
}

private fun writeScannerRejectedCaptureDiagnostics(
    packageDir: File,
    rejectedFrames: List<ScanFrame>,
    capturedFrameCount: Int,
    acceptedFrameCount: Int
) {
    val diagnosticsDir = packageDir.resolve("diagnostics").also { it.mkdirs() }
    diagnosticsDir.resolve("capture_rejections_compact.json").writeText(
        JSONObject()
            .put("schema_version", SCANNER_PACKAGE_SCHEMA_VERSION)
            .put("captured_frame_count", capturedFrameCount)
            .put("accepted_frame_count", acceptedFrameCount)
            .put("rejected_frame_count", rejectedFrames.size)
            .put("rejected_image_files_exported", false)
            .put(
                "reason_counts",
                JSONObject().apply {
                    rejectedFrames
                        .flatMap { it.quality.rejectionReasons }
                        .groupingBy { it }
                        .eachCount()
                        .toSortedMap()
                        .forEach { (reason, count) -> put(reason, count) }
                }
            )
            .put(
                "pass_counts",
                JSONArray().apply {
                    ScannerCapturePass.entries.forEach { pass ->
                        val passRejected = rejectedFrames.filter { it.capturePass == pass }
                        put(
                            JSONObject()
                                .put("capture_pass", pass.manifestValue)
                                .put("rejected_frame_count", passRejected.size)
                                .put(
                                    "reason_counts",
                                    JSONObject().apply {
                                        passRejected
                                            .flatMap { it.quality.rejectionReasons }
                                            .groupingBy { it }
                                            .eachCount()
                                            .toSortedMap()
                                            .forEach { (reason, count) -> put(reason, count) }
                                    }
                                )
                        )
                    }
                }
            )
            .toString(2)
    )
}

package com.mobileslicer

import android.app.Activity
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
import com.mobileslicer.nativebridge.NativePaintCalls
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
import com.mobileslicer.profiles.NativeConfigKeys
import com.mobileslicer.storage.AppPreferenceStore
import com.mobileslicer.storage.GCODE_MIME_TYPE
import com.mobileslicer.storage.PreparedViewerMeshCache
import com.mobileslicer.storage.PrinterCredentialStore
import com.mobileslicer.storage.SavedProject
import com.mobileslicer.storage.SavedProjectRepository
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.StlMeshParser
import com.mobileslicer.nativebridge.NativeEngineBridge
import com.mobileslicer.ui.theme.AccentPaletteOption
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
import com.mobileslicer.workspace.OffscreenEglSliceThumbnailRenderer
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.PlateObjectGeometrySource
import com.mobileslicer.workspace.SlicePipelineTiming
import com.mobileslicer.workspace.SliceResult
import com.mobileslicer.workspace.WorkspacePreparationResult
import com.mobileslicer.workspace.WorkspacePreparationTiming
import com.mobileslicer.workspace.WorkspaceScreen
import com.mobileslicer.workspace.WorkspaceSessionViewModel
import com.mobileslicer.workspace.defaultNativeModelTransform
import com.mobileslicer.workspace.defaultViewerModelTransform
import com.mobileslicer.workspace.modelLoadStatusMessage
import com.mobileslicer.workspace.nativePaintPayloadJson
import com.mobileslicer.workspace.paintHash
import com.mobileslicer.workspace.renderSliceThumbnails
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
import com.mobileslicer.workspace.nativePlateTransformInputStride
import com.mobileslicer.workspace.writeTo

private data class NativePlateLoadRequest(
    val paths: Array<String>,
    val sourcePaths: Array<String>,
    val transforms: DoubleArray,
    val extruderIds: IntArray,
    val mobileObjectIds: LongArray,
    val paintPayloadJson: String,
    val signature: String
)

private fun nativePlateLoadRequest(
    plateObjects: List<PlateObject>,
    printerBed: PrinterBedSpec
): NativePlateLoadRequest? {
    if (plateObjects.isEmpty()) return null
    val sliceObjects = plateObjects.filter { objectOnPlate ->
        objectOnPlate.format == ImportedModelFormat.Stl &&
            objectOnPlate.filePath.isNotBlank() &&
            (objectOnPlate.mesh != null || objectOnPlate.bounds != null) &&
            File(objectOnPlate.filePath).exists()
    }
    if (sliceObjects.size != plateObjects.size) return null

    val paths = Array(sliceObjects.size) { index -> sliceObjects[index].filePath }
    val sourcePaths = Array(sliceObjects.size) { index -> sliceObjects[index].nativeSourceFilePath() }
    val transformStride = nativePlateTransformInputStride(sliceObjects.map { it.transform })
    val transforms = DoubleArray(sliceObjects.size * transformStride)
    val extruderIds = IntArray(sliceObjects.size)
    val mobileObjectIds = LongArray(sliceObjects.size) { index -> sliceObjects[index].id }
    val paintPayloadJson = nativePaintPayloadJson(sliceObjects)
    sliceObjects.forEachIndexed { index, objectOnPlate ->
        val objectBounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds ?: return null
        val transform = defaultNativeModelTransform(objectBounds, printerBed, objectOnPlate.transform)
        transform.writeTo(transforms, offset = index * transformStride, stride = transformStride)
        extruderIds[index] = objectOnPlate.filamentSlotIndex.coerceAtLeast(1)
    }
    val bedSignature = listOf(
        printerBed.originXmm,
        printerBed.originYmm,
        printerBed.widthMm,
        printerBed.depthMm,
        printerBed.maxHeightMm
    ).joinToString(",") { "%.3f".format(Locale.US, it) }
    val signature = "plate:bed=$bedSignature:stride=$transformStride:" + sliceObjects.mapIndexed { index, objectOnPlate ->
        val originalSlot = objectOnPlate.filamentSlotIndex.coerceAtLeast(1)
        val paintHash = objectOnPlate.paint.paintHash().takeIf { it.isNotBlank() } ?: "none"
        "$index:id=${objectOnPlate.id}:${objectOnPlate.nativeSourceKey}:slot=$originalSlot:paint=$paintHash:${objectOnPlate.transform}"
    }.joinToString("|")
    return NativePlateLoadRequest(paths, sourcePaths, transforms, extruderIds, mobileObjectIds, paintPayloadJson, signature)
}

private fun PlateObject.nativeSourceFilePath(): String =
    when (val source = geometrySource) {
        is PlateObjectGeometrySource.StepMeshConvert -> source.originalPath.takeIf { it.isNotBlank() } ?: filePath
        is PlateObjectGeometrySource.ThreeMfMeshExtract -> source.originalPath.takeIf { it.isNotBlank() } ?: filePath
        else -> filePath
    }

private fun nativeSlicePlateObjects(
    plateObjects: List<PlateObject>,
    modelFilePath: String?,
    preparedMesh: StlMesh?,
    modelBounds: MeshBounds?,
    modelTransform: ViewerModelTransform?,
    printerBed: PrinterBedSpec,
    stagedModelFile: File?
): List<PlateObject> {
    if (plateObjects.isNotEmpty()) return plateObjects
    val stagedModel = modelFilePath?.let(::File)?.takeIf { it.exists() }
        ?: stagedModelFile?.takeIf { it.exists() }
        ?: return emptyList()
    val bounds = preparedMesh?.bounds ?: modelBounds ?: return emptyList()
    return listOf(
        PlateObject(
            id = 1L,
            label = displayStemForModelFile(stagedModel),
            filePath = stagedModel.absolutePath,
            nativeSourceKey = stagedModel.absolutePath,
            filamentSlotIndex = 1,
            format = ImportedModelFormat.Stl,
            importTiming = null,
            bounds = bounds,
            mesh = preparedMesh,
            transform = modelTransform ?: defaultViewerModelTransform(printerBed)
        )
    )
}

private fun nativePlateLoadRequestFailureReason(plateObjects: List<PlateObject>): String {
    if (plateObjects.isEmpty()) return "Plate contains no objects"
    val invalidObject = plateObjects.firstOrNull { objectOnPlate ->
        objectOnPlate.format != ImportedModelFormat.Stl ||
            objectOnPlate.filePath.isBlank() ||
            (objectOnPlate.mesh == null && objectOnPlate.bounds == null) ||
            !File(objectOnPlate.filePath).exists()
    } ?: return "Native plate request could not be built"
    return when {
        invalidObject.format != ImportedModelFormat.Stl ->
            "${invalidObject.label} is ${invalidObject.format.name}; native planning currently requires STL plate objects"
        invalidObject.filePath.isBlank() ->
            "${invalidObject.label} has no source file path"
        invalidObject.mesh == null && invalidObject.bounds == null ->
            "${invalidObject.label} has no prepared mesh bounds"
        !File(invalidObject.filePath).exists() ->
            "${invalidObject.label} source file is missing"
        else -> "${invalidObject.label} cannot be planned natively"
    }
}

internal fun MainActivity.loadModelFileIntoNativeCache(modelFilePath: String): Boolean {
    val handle = NativeEngineHandle.fromRaw(ensureEngine())
    val modelFile = File(modelFilePath)
    if (handle == null || !modelFile.exists()) {
        nativeLoadedModelPath = null
        return false
    }
    if (nativeLoadedModelPath == modelFile.absolutePath) {
        return true
    }
    val loadResult = NativeEngineCalls.loadModel(handle, modelFile.absolutePath)
    return if (loadResult is NativeEngineCallResult.Success) {
        nativeLoadedModelPath = modelFile.absolutePath
        currentModelName = displayStemForModelFile(modelFile)
        true
    } else {
        NativeEngineCalls.clearGeneratedGcode(handle)
        nativeLoadedModelPath = null
        false
    }
}

internal fun MainActivity.loadPlateObjectsIntoNativeCache(
    plateObjects: List<PlateObject>,
    printerBed: PrinterBedSpec
): Boolean {
    val handle = NativeEngineHandle.fromRaw(ensureEngine())
    if (handle == null) {
        nativeLoadedModelPath = null
        return false
    }
    val request = nativePlateLoadRequest(plateObjects, printerBed) ?: run {
        NativeEngineCalls.clearGeneratedGcode(handle)
        nativeLoadedModelPath = null
        return false
    }
    if (nativeLoadedModelPath == request.signature && nativePaintBindingsCover(handle, request.mobileObjectIds)) {
        return true
    }
    val loadResult = NativeEngineCalls.loadPlateModelsV2(
        handle = handle,
        paths = request.paths,
        sourcePaths = request.sourcePaths,
        transforms = request.transforms,
        extruderIds = request.extruderIds,
        mobileObjectIds = request.mobileObjectIds,
        paintPayloadJson = request.paintPayloadJson
    )
    return if (loadResult is NativeEngineCallResult.Success) {
        nativeLoadedModelPath = request.signature
        true
    } else {
        NativeEngineCalls.clearGeneratedGcode(handle)
        nativeLoadedModelPath = null
        false
    }
}

internal fun MainActivity.markPlateObjectsAsNativeCacheCurrent(
    plateObjects: List<PlateObject>,
    printerBed: PrinterBedSpec
) {
    val handle = NativeEngineHandle.fromRaw(ensureEngine()) ?: run {
        nativeLoadedModelPath = null
        return
    }
    val request = nativePlateLoadRequest(plateObjects, printerBed) ?: run {
        nativeLoadedModelPath = null
        return
    }
    nativeLoadedModelPath = if (nativePaintBindingsCover(handle, request.mobileObjectIds)) {
        request.signature
    } else {
        null
    }
}

internal suspend fun MainActivity.prewarmNativePlatePlanningModels(
    plateObjects: List<PlateObject>,
    printerBed: PrinterBedSpec
): Boolean =
    withContext(Dispatchers.Default) {
        val startedAtMs = SystemClock.elapsedRealtime()
        val handle = NativeEngineHandle.fromRaw(ensureEngine()) ?: return@withContext false
        val request = nativePlateLoadRequest(plateObjects, printerBed) ?: return@withContext false
        val result = NativeEngineCalls.prewarmPlatePlanningModels(
            handle = handle,
            paths = request.paths
        )
        val success = result is NativeEngineCallResult.Success
        Log.i(
            "MobileSlicerPerf",
            "plate_planning_prewarm success=$success objects=${plateObjects.size} totalMs=${SystemClock.elapsedRealtime() - startedAtMs}"
        )
        success
    }

internal fun MainActivity.loadSavedProjectIntoNativeCache(
    nativeProjectFilePath: String?,
    plateObjects: List<PlateObject>,
    printerBed: PrinterBedSpec
): Boolean {
    val projectFile = nativeProjectFilePath
        ?.let(::File)
        ?.takeIf { it.exists() && it.extension.equals("3mf", ignoreCase = true) }
        ?: return loadPlateObjectsIntoNativeCache(plateObjects, printerBed)
    val handle = NativeEngineHandle.fromRaw(ensureEngine()) ?: run {
        nativeLoadedModelPath = null
        return false
    }
    val request = nativePlateLoadRequest(plateObjects, printerBed) ?: run {
        NativeEngineCalls.clearGeneratedGcode(handle)
        nativeLoadedModelPath = null
        return false
    }
    if (nativeLoadedModelPath == request.signature && nativePaintBindingsCover(handle, request.mobileObjectIds)) {
        return true
    }
    val loadResult = NativeEngineCalls.loadProject3mf(
        handle = handle,
        path = projectFile.absolutePath,
        mobileObjectIds = request.mobileObjectIds
    )
    return if (loadResult is NativeEngineCallResult.Success) {
        nativeLoadedModelPath = request.signature
        true
    } else {
        loadPlateObjectsIntoNativeCache(plateObjects, printerBed)
    }
}

private fun nativePaintBindingsCover(handle: NativeEngineHandle, objectIds: LongArray): Boolean {
    return objectIds.all { objectId ->
        NativePaintCalls.objectBoundsJson(handle, objectId) != null
    }
}

internal suspend fun MainActivity.planNativePlateArrangement(
    configJson: String,
    plateObjects: List<PlateObject>,
    printerBed: PrinterBedSpec,
    allowRotation: Boolean
): PlatePlanningOutcome<PlateAutoArrangeResult> =
    withContext(Dispatchers.Default) {
        val totalStartedAtMs = SystemClock.elapsedRealtime()
        val reservesPrimeTowerSpace = primeTowerEnabledForPlanning(configJson)
        if (!allowRotation && !reservesPrimeTowerSpace) {
            val fastPathStartedAtMs = SystemClock.elapsedRealtime()
            singleObjectAutoArrangeFastPath(plateObjects, printerBed)?.let { plannedObjects ->
                Log.i(
                    "MobileSlicerPerf",
                    "autoArrange fastPath=singleObjectCenter totalMs=${SystemClock.elapsedRealtime() - totalStartedAtMs} fastPathMs=${SystemClock.elapsedRealtime() - fastPathStartedAtMs} objects=${plateObjects.size} allowRotation=$allowRotation"
                )
                return@withContext PlatePlanningOutcome.Success(
                    PlateAutoArrangeResult(
                        objects = plannedObjects,
                        changedCount = nativePlanChangedCount(plateObjects, plannedObjects),
                        reservedPrimeTowerSpace = false,
                        centersSummary = plannedObjects.joinToString(";") { objectOnPlate ->
                            String.format(
                                Locale.US,
                                "%s=(%.2f,%.2f)",
                                objectOnPlate.label,
                                objectOnPlate.transform.centerXmm,
                                objectOnPlate.transform.centerYmm
                            )
                        }
                    )
                )
            }
        }
        val handle = NativeEngineHandle.fromRaw(ensureEngine()) ?: return@withContext PlatePlanningOutcome.Failure(
            nativeAutoArrangePlateObjectsFailureStatus("The slicer is not ready. Restart the app and try again.")
        )
        val requestStartedAtMs = SystemClock.elapsedRealtime()
        val request = nativePlateLoadRequest(plateObjects, printerBed) ?: return@withContext PlatePlanningOutcome.Failure(
            nativeAutoArrangePlateObjectsFailureStatus(nativePlateLoadRequestFailureReason(plateObjects))
        )
        val requestMs = SystemClock.elapsedRealtime() - requestStartedAtMs
        val nativeStartedAtMs = SystemClock.elapsedRealtime()
        val plannedTransforms = NativeEngineCalls.planPlateArrangement(
            handle = handle,
            paths = request.paths,
            transforms = request.transforms,
            extruderIds = request.extruderIds,
            configJson = configJson,
            allowRotation = allowRotation
        )
        val nativeMs = SystemClock.elapsedRealtime() - nativeStartedAtMs
        if (plannedTransforms == null) {
            val nativeError = NativeEngineCalls.getLastErrorMessage(handle)
            Log.w(
                MainActivity.TAG,
                "planNativePlateArrangement failed: $nativeError"
            )
            return@withContext PlatePlanningOutcome.Failure(
                nativeAutoArrangePlateObjectsFailureStatus(nativeError)
            )
        }
        val validationStartedAtMs = SystemClock.elapsedRealtime()
        val plannedBedIndices = nativeArrangementBedIndices(plannedTransforms, plateObjects.size)
        val plannedObjects = applyNativePlatePlan(
            plateObjects = plateObjects,
            bed = printerBed,
            nativeTransforms = plannedTransforms,
            requireAllOnBed = plannedBedIndices.all { it == 0 },
            requireNoOverlap = false
        ) ?: run {
            Log.w(MainActivity.TAG, "planNativePlateArrangement rejected invalid native transform output")
            return@withContext PlatePlanningOutcome.Failure(
                nativeAutoArrangePlateObjectsFailureStatus("The arranged objects did not fit the plate.")
            )
        }
        val validationMs = SystemClock.elapsedRealtime() - validationStartedAtMs
        Log.i(
            "MobileSlicerPerf",
            "autoArrange nativePipeline totalMs=${SystemClock.elapsedRealtime() - totalStartedAtMs} requestMs=$requestMs nativeMs=$nativeMs validationMs=$validationMs objects=${plateObjects.size} allowRotation=$allowRotation"
        )
        PlatePlanningOutcome.Success(
            PlateAutoArrangeResult(
                objects = plannedObjects,
                changedCount = nativePlanChangedCount(plateObjects, plannedObjects),
                reservedPrimeTowerSpace = reservesPrimeTowerSpace,
                centersSummary = plannedObjects.mapIndexed { index, objectOnPlate ->
                    String.format(
                        Locale.US,
                        "%s=(%.2f,%.2f) bed=%d",
                        objectOnPlate.label,
                        objectOnPlate.transform.centerXmm,
                        objectOnPlate.transform.centerYmm,
                        plannedBedIndices.getOrElse(index) { 0 }
                    )
                }.joinToString(";"),
                bedIndices = plannedBedIndices
            )
        )
    }

internal suspend fun MainActivity.planNativeAutoOrientation(
    configJson: String,
    plateObjects: List<PlateObject>,
    selectedPlateObjectId: Long?,
    printerBed: PrinterBedSpec
): PlatePlanningOutcome<PlateAutoOrientResult> =
    withContext(Dispatchers.Default) {
        val totalStartedAtMs = SystemClock.elapsedRealtime()
        if (plateObjects.isEmpty()) {
            return@withContext PlatePlanningOutcome.Failure(autoOrientPlateObjectsUnavailableStatus())
        }
        val selectedObject = selectedPlateObjectId?.let { selectedId ->
            plateObjects.firstOrNull { it.id == selectedId }
        }
        val targetObjects = selectedObject?.let(::listOf) ?: plateObjects
        val handle = NativeEngineHandle.fromRaw(ensureEngine()) ?: return@withContext PlatePlanningOutcome.Failure(
            nativeAutoOrientPlateObjectsFailureStatus("The slicer is not ready. Restart the app and try again.")
        )
        val requestStartedAtMs = SystemClock.elapsedRealtime()
        val request = nativePlateLoadRequest(targetObjects, printerBed) ?: return@withContext PlatePlanningOutcome.Failure(
            nativeAutoOrientPlateObjectsFailureStatus(nativePlateLoadRequestFailureReason(targetObjects))
        )
        val requestMs = SystemClock.elapsedRealtime() - requestStartedAtMs
        val nativeStartedAtMs = SystemClock.elapsedRealtime()
        val plannedTransforms = NativeEngineCalls.planAutoOrientation(
            handle = handle,
            paths = request.paths,
            transforms = request.transforms,
            extruderIds = request.extruderIds,
            configJson = configJson
        )
        val nativeMs = SystemClock.elapsedRealtime() - nativeStartedAtMs
        if (plannedTransforms == null) {
            val nativeError = NativeEngineCalls.getLastErrorMessage(handle)
            Log.w(
                MainActivity.TAG,
                "planNativeAutoOrientation failed: $nativeError"
            )
            return@withContext PlatePlanningOutcome.Failure(
                nativeAutoOrientPlateObjectsFailureStatus(nativeError)
            )
        }
        val validationStartedAtMs = SystemClock.elapsedRealtime()
        val plannedTargets = applyNativePlatePlan(
            plateObjects = targetObjects,
            bed = printerBed,
            nativeTransforms = plannedTransforms,
            requireAllOnBed = true,
            preservePlateCenters = true
        ) ?: run {
            Log.w(MainActivity.TAG, "planNativeAutoOrientation rejected invalid native transform output")
            return@withContext PlatePlanningOutcome.Failure(
                nativeAutoOrientPlateObjectsFailureStatus("The oriented objects did not fit the plate.")
            )
        }
        val validationMs = SystemClock.elapsedRealtime() - validationStartedAtMs
        val plannedTargetsById = plannedTargets.associateBy { it.id }
        val plannedObjects = plateObjects.map { objectOnPlate ->
            plannedTargetsById[objectOnPlate.id] ?: objectOnPlate
        }
        Log.i(
            "MobileSlicerPerf",
            "autoOrient nativePipeline totalMs=${SystemClock.elapsedRealtime() - totalStartedAtMs} requestMs=$requestMs nativeMs=$nativeMs validationMs=$validationMs targets=${targetObjects.size} selected=${selectedObject != null}"
        )
        PlatePlanningOutcome.Success(
            PlateAutoOrientResult(
                objects = plannedObjects,
                targetCount = targetObjects.size,
                changedCount = nativePlanChangedCount(targetObjects, plannedTargets),
                selectedOnly = selectedObject != null
            )
        )
    }

private fun primeTowerEnabledForPlanning(configJson: String): Boolean =
    runCatching {
        val json = org.json.JSONObject(configJson)
        json.optBoolean(NativeConfigKeys.PrimeTower.Enable, false)
    }.getOrDefault(false)

private fun slicePreflightSummary(
    plateObjects: List<PlateObject>,
    fallbackBounds: MeshBounds?,
    fallbackTransform: ViewerModelTransform?,
    printerBed: PrinterBedSpec,
    configJson: String
): String {
    val clearance = generatedFootprintClearanceMm(configJson)
    val rects = if (plateObjects.isNotEmpty()) {
        plateObjects.mapNotNull { objectOnPlate ->
            val bounds = objectOnPlate.mesh?.bounds ?: objectOnPlate.bounds ?: return@mapNotNull null
            generatedFootprintRect(bounds, objectOnPlate.transform, clearance)
        }
    } else {
        fallbackBounds?.let { bounds ->
            listOf(generatedFootprintRect(bounds, fallbackTransform ?: defaultViewerModelTransform(printerBed), clearance))
        }.orEmpty()
    }
    val union = unionGeneratedFootprint(rects)
    return buildString {
        append("objects=").append(plateObjects.size.coerceAtLeast(if (fallbackBounds != null) 1 else 0))
        append(" clearance=").append(String.format(Locale.US, "%.2f", clearance))
        append(" bed=").append(String.format(Locale.US, "%.1fx%.1fx%.1f", printerBed.widthMm, printerBed.depthMm, printerBed.maxHeightMm))
        if (union != null) {
            append(" footprint=")
            append(String.format(Locale.US, "%.2f,%.2f,%.2f,%.2f", union.minX, union.maxX, union.minY, union.maxY))
        }
    }
}

private fun nativeSliceConfigDiagnosticSummary(configJson: String): String {
    val json = runCatching { org.json.JSONObject(configJson) }.getOrNull()
        ?: return "config=parse_failed length=${configJson.length}"
    fun value(key: String): String =
        if (json.has(key)) json.opt(key)?.toString()?.take(96).orEmpty() else "<absent>"
    return buildString {
        append("configHash=").append(configJson.hashCode())
        append(" layer_height=").append(value("layer_height"))
        append(" sparse_infill_density=").append(value("sparse_infill_density"))
        append(" sparse_infill_pattern=").append(value("sparse_infill_pattern"))
        append(" sparse_infill_filament_present=").append(json.has("sparse_infill_filament"))
        append(" sparse_infill_filament=").append(value("sparse_infill_filament"))
        append(" post_process_present=").append(json.has("post_process"))
        append(" wall_loops=").append(value("wall_loops"))
        append(" only_one_wall_first_layer=").append(value("only_one_wall_first_layer"))
        append(" seam_gap=").append(value("seam_gap"))
        append(" active_slots=").append(value("mobile_slicer_active_filament_slot_count"))
        append(" physical_nozzles=").append(value("mobile_slicer_physical_nozzle_count"))
    }
}

internal fun MainActivity.cancelNativePlatePlanning() {
    NativeEngineHandle.fromRaw(ensureEngine())?.let { handle ->
        NativeEngineCalls.cancelPlanning(handle)
    }
}

internal fun MainActivity.sliceCurrentModel(
        configJson: String,
        plateObjects: List<PlateObject>,
        modelFilePath: String?,
        preparedMesh: StlMesh?,
        modelBounds: MeshBounds?,
        printerBed: PrinterBedSpec,
        modelTransform: ViewerModelTransform?,
        suggestedGcodeFileName: String? = null,
        allowEngineRecovery: Boolean = true
    ): SliceResult {
        val pipelineStartedAt = SystemClock.elapsedRealtime()
        val handle = NativeEngineHandle.fromRaw(ensureEngine())
        if (handle == null) {
            return SliceResult("Slice failed\nThe slicer is not ready. Restart the app and try again.", sliced = false)
        }
        val preflightClearance = generatedFootprintClearanceMm(configJson)
        val preflightFailure = printableVolumePreflightFailure(
            plateObjects = plateObjects,
            fallbackBounds = preparedMesh?.bounds ?: modelBounds,
            fallbackTransform = modelTransform,
            fallbackModelPath = modelFilePath,
            bed = printerBed,
            clearance = preflightClearance,
            defaultTransform = defaultViewerModelTransform(printerBed)
        )
        val preflightSummary = slicePreflightSummary(
            plateObjects = plateObjects,
            fallbackBounds = preparedMesh?.bounds ?: modelBounds,
            fallbackTransform = modelTransform,
            printerBed = printerBed,
            configJson = configJson
        )
        if (preflightFailure != null) {
            Log.w(MainActivity.TAG, "slice_preflight blocked $preflightSummary")
            return SliceResult(preflightFailure, sliced = false)
        }
        Log.i(MainActivity.TAG, "slice_preflight ok $preflightSummary ${nativeSliceConfigDiagnosticSummary(configJson)}")
        var modelReloadMs = 0L
        val slicePlateObjects = nativeSlicePlateObjects(
            plateObjects = plateObjects,
            modelFilePath = modelFilePath,
            preparedMesh = preparedMesh,
            modelBounds = modelBounds,
            modelTransform = modelTransform,
            printerBed = printerBed,
            stagedModelFile = stagedModelFile
        )
        val request = nativePlateLoadRequest(slicePlateObjects, printerBed)
            ?: return SliceResult("Slice failed\nOne or more plate objects are still preparing.", sliced = false)
        Log.i(
            MainActivity.TAG,
            "slice_load_plate start objects=${slicePlateObjects.size} paths=${request.paths.size} signature=${request.signature.hashCode()}"
        )
        if (nativeLoadedModelPath != request.signature || !nativePaintBindingsCover(handle, request.mobileObjectIds)) {
            val modelReloadStartedAt = SystemClock.elapsedRealtime()
            val loadPlateResult = NativeEngineCalls.loadPlateModelsV2(
                handle = handle,
                paths = request.paths,
                sourcePaths = request.sourcePaths,
                transforms = request.transforms,
                extruderIds = request.extruderIds,
                mobileObjectIds = request.mobileObjectIds,
                paintPayloadJson = request.paintPayloadJson
            )
            if (loadPlateResult !is NativeEngineCallResult.Success) {
                NativeEngineCalls.clearGeneratedGcode(handle)
                nativeLoadedModelPath = null
                Log.e(MainActivity.TAG, "slice_load_plate failed ${loadPlateResult.statusMessage}")
                return SliceResult(
                    "Slice failed\nMobileSlicer could not prepare every model on this plate.\n${loadPlateResult.statusMessage}",
                    sliced = false
                )
            }
            modelReloadMs = SystemClock.elapsedRealtime() - modelReloadStartedAt
            nativeLoadedModelPath = request.signature
            Log.i(MainActivity.TAG, "slice_load_plate done modelReloadMs=$modelReloadMs")
        } else {
            Log.i(MainActivity.TAG, "slice_load_plate cache_hit")
        }
        val configStartedAt = SystemClock.elapsedRealtime()
        val configResult = NativeEngineCalls.setConfigJson(handle, configJson)
        if (configResult !is NativeEngineCallResult.Success) {
            Log.e(MainActivity.TAG, "slice_set_config failed ${configResult.statusMessage}")
            return SliceResult(
                "Slice failed\nNative slice configuration could not be applied.\n${configResult.statusMessage}",
                sliced = false
            )
        }
        val configMs = SystemClock.elapsedRealtime() - configStartedAt
        Log.i(MainActivity.TAG, "slice_set_config done configMs=$configMs")
        val thumbnailRequests = NativeEngineCalls.getThumbnailRequests(handle)
        NativeEngineCalls.clearSliceThumbnails(handle)
        if (thumbnailRequests == null) {
            Log.w(
                MainActivity.TAG,
                "slice_thumbnail_requests unavailable error=${NativeEngineCalls.getLastErrorMessage(handle)}"
            )
        } else {
            val levelMessage = "slice_thumbnail_requests count=${thumbnailRequests.requests.size} " +
                "errors=${thumbnailRequests.hasErrors} thumbnails=${thumbnailRequests.thumbnails} " +
                "format=${thumbnailRequests.thumbnailsFormat} errorText=${thumbnailRequests.errorText.replace('\n', ' ').trim()}"
            if (thumbnailRequests.hasErrors) {
                Log.w(MainActivity.TAG, levelMessage)
            } else {
                Log.i(MainActivity.TAG, levelMessage)
            }
        }
        val hasThumbnailRequests = thumbnailRequests?.requests?.isNotEmpty() == true
        val thumbnailStartedAt = if (hasThumbnailRequests) SystemClock.elapsedRealtime() else 0L
        val thumbnailRenderResult = if (hasThumbnailRequests) {
            renderSliceThumbnails(
                requestSummary = thumbnailRequests,
                plateObjects = plateObjects,
                fallbackMesh = preparedMesh,
                fallbackTransform = modelTransform,
                printerBed = printerBed,
                includePackageThumbnails = false,
                renderer = OffscreenEglSliceThumbnailRenderer()
            )
        } else {
            null
        }
        val thumbnailMs = if (hasThumbnailRequests) SystemClock.elapsedRealtime() - thumbnailStartedAt else 0L
        if (thumbnailRenderResult != null) {
            val thumbnailBytes = thumbnailRenderResult.thumbnails.sumOf { it.rgba.size.toLong() }
            val renderedTriangles = thumbnailRenderResult.thumbnails.sumOf { it.renderedTriangleCount.toLong() }
            val sourceTriangles = thumbnailRenderResult.thumbnails.maxOfOrNull { it.sourceTriangleCount } ?: 0
            val renderers = thumbnailRenderResult.thumbnails
                .map { it.renderer }
                .distinct()
                .joinToString(",")
            var uploadedThumbnails = 0
            thumbnailRenderResult.thumbnails.forEach { thumbnail ->
                val uploadResult = NativeEngineCalls.addSliceThumbnailRgba(
                    handle = handle,
                    width = thumbnail.width,
                    height = thumbnail.height,
                    format = thumbnail.format,
                    role = thumbnail.role,
                    rgba = thumbnail.rgba
                )
                if (uploadResult is NativeEngineCallResult.Success) {
                    uploadedThumbnails++
                } else {
                    Log.w(MainActivity.TAG, "slice_thumbnail_upload failed ${uploadResult.statusMessage}")
                }
            }
            Log.i(
                MainActivity.TAG,
                "slice_thumbnail_render requested=${thumbnailRenderResult.requests.size} " +
                    "rendered=${thumbnailRenderResult.thumbnails.size} uploaded=$uploadedThumbnails thumbnailMs=$thumbnailMs " +
                    "packageThumbnails=${thumbnailRenderResult.includePackageThumbnails} " +
                    "renderers=$renderers " +
                    "bytes=$thumbnailBytes sourceTriangles=$sourceTriangles renderedTriangles=$renderedTriangles " +
                    "skip=${thumbnailRenderResult.skippedReason.orEmpty()}"
            )
        }

        val nativeSliceStartedAt = SystemClock.elapsedRealtime()
        Log.i(MainActivity.TAG, "slice_native start")
        val nativeSliceResult = NativeEngineCalls.slice(handle)
        val nativeSliceSucceeded = nativeSliceResult is NativeEngineCallResult.Success
        if (!nativeSliceSucceeded) {
            val nativeError = (nativeSliceResult as? NativeEngineCallResult.Failure)?.error?.message.orEmpty()
            Log.e(MainActivity.TAG, "slice_native failed error=$nativeError lastError=${NativeEngineCalls.getLastErrorMessage(handle)}")
            if (shouldRecoverNativeEngine(nativeError)) {
                if (allowEngineRecovery) {
                    Log.w(MainActivity.TAG, "sliceCurrentModel: native slice failed with $nativeError")
                } else {
                    Log.w(MainActivity.TAG, "sliceCurrentModel: native slice failed with $nativeError after native engine recovery")
                }
            }
        }

        val result = if (nativeSliceSucceeded) {
            val nativeSliceMs = SystemClock.elapsedRealtime() - nativeSliceStartedAt
            Log.i(MainActivity.TAG, "slice_native done nativeSliceMs=$nativeSliceMs")
            val gcodeFileName = suggestedGcodeFileName
                ?.takeIf { it.isNotBlank() }
                ?: suggestGcodeFileName(currentModelName, configJson)
            val gcodeFile = File(cacheDir, "latest-slice-$gcodeFileName")
            val writeGcodeStartedAt = SystemClock.elapsedRealtime()
            val writeGcodeResult = NativeEngineCalls.writeGcodeToFile(handle, gcodeFile.absolutePath)
            val writeGcodeMs = SystemClock.elapsedRealtime() - writeGcodeStartedAt
            if (writeGcodeResult !is NativeEngineCallResult.Success || !gcodeFile.exists() || gcodeFile.length() <= 0L) {
                val nativeError = (writeGcodeResult as? NativeEngineCallResult.Failure)?.error?.message.orEmpty()
                Log.e(MainActivity.TAG, "slice_write_gcode failed exists=${gcodeFile.exists()} length=${gcodeFile.length()} error=$nativeError")
                SliceResult(
                    buildString {
                        append("Slice failed\nNo G-code file was returned.")
                        if (nativeError.isNotBlank()) {
                            append("\n")
                            append(nativeError)
                        }
                    },
                    sliced = false
                )
            } else {
                cleanupGeneratedGcodeCache(
                    retainedPaths = retainedCachePaths(
                        listOf(gcodeFile),
                        includeCurrentGcode = false
                    )
                )
                val summaryStartedAt = SystemClock.elapsedRealtime()
                val nativeSummaryText = NativeEngineCalls.getGcodeSummary(handle)
                val summary = GcodeSummaryParser.fromNativeSummary(nativeSummaryText)
                val summaryMs = SystemClock.elapsedRealtime() - summaryStartedAt
                if (summary == null) {
                    val nativeError = NativeEngineCalls.getLastErrorMessage(handle)
                    Log.e(
                        MainActivity.TAG,
                        "slice_summary failed summaryText=${nativeSummaryText?.take(240)} error=$nativeError"
                    )
                    return SliceResult(
                        buildString {
                            append("Slice failed\nNative G-code summary was missing or incomplete.")
                            if (!nativeSummaryText.isNullOrBlank()) {
                                append("\nSummary: ")
                                append(nativeSummaryText.take(240))
                            }
                            if (nativeError.isNotBlank()) {
                                append("\n")
                                append(nativeError)
                            }
                        },
                        sliced = false,
                        timing = SlicePipelineTiming(
                            modelReloadMs = modelReloadMs,
                            configMs = configMs,
                            nativeSliceMs = nativeSliceMs,
                            writeGcodeMs = writeGcodeMs,
                            summaryMs = summaryMs,
                            thumbnailMs = thumbnailMs,
                            totalMs = SystemClock.elapsedRealtime() - pipelineStartedAt
                        )
                    )
                }
                val totalMs = SystemClock.elapsedRealtime() - pipelineStartedAt
                Log.i(
                    MainActivity.TAG,
                    "slice_complete success totalMs=$totalMs modelReloadMs=$modelReloadMs configMs=$configMs nativeSliceMs=$nativeSliceMs thumbnailMs=$thumbnailMs writeGcodeMs=$writeGcodeMs summaryMs=$summaryMs gcodeBytes=${gcodeFile.length()}"
                )
                SliceResult(
                    message = "Slice successful",
                    sliced = true,
                    gcodeFilePath = gcodeFile.absolutePath,
                    fileName = gcodeFileName,
                    summary = summary,
                    timing = SlicePipelineTiming(
                        modelReloadMs = modelReloadMs,
                        configMs = configMs,
                        nativeSliceMs = nativeSliceMs,
                        writeGcodeMs = writeGcodeMs,
                        summaryMs = summaryMs,
                        thumbnailMs = thumbnailMs,
                        totalMs = totalMs
                    )
                )
            }
        } else {
            SliceResult(describeNativeSliceFailure().userMessage, sliced = false)
        }
        return result
    }

internal fun MainActivity.shouldRecoverNativeEngine(nativeError: String): Boolean {
        val normalized = nativeError.lowercase(Locale.US)
        return normalized == "vector" || normalized.contains("bad_alloc")
    }

internal fun MainActivity.releaseSliceMemoryPressure() {
        preparedViewerMeshCache.clear()
        NativeEngineCalls.trimMemory()
        Runtime.getRuntime().gc()
    }

internal fun MainActivity.describeNativeSliceFailure(): NativeSliceFailurePresentation {
        val handle = nativeEngineSession.handleOrNull()
        val nativeError = handle?.let(NativeEngineCalls::getLastEngineError)
        if (nativeError?.code == NativeEngineErrorCode.PrintableVolumeExceeded) {
            return NativeSliceFailurePresentation(
                userMessage = "Slice failed\nPrintable volume exceeded.\nGenerated extrusion would go outside the selected printer bed or height limit.",
                automationStatus = nativeError.automationStatus
            )
        }
        return if (nativeError != null) {
            NativeSliceFailurePresentation(
                userMessage = "Slice failed\n${nativeError.message}",
                automationStatus = "nativeSlice returned false ${nativeError.automationStatus}"
            )
        } else {
            NativeSliceFailurePresentation(
                userMessage = "Slice failed",
                automationStatus = "nativeSlice returned false"
            )
        }
    }

package com.mobileslicer

import android.app.Activity
import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
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
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.PlateObjectGeometrySource
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

internal fun MainActivity.loadModelFromUri(uri: Uri): ModelLoadResult {
        val handle = NativeEngineHandle.fromRaw(ensureEngine())
        if (handle == null) {
            return ModelLoadResult(
                message = "Failed to load model\nThe slicer is not ready. Restart the app and try again.",
                loaded = false
            )
        }

        val fileName = queryDisplayName(uri) ?: "selected_model.stl"
        val normalizedName = sanitizeFileName(fileName)
        val normalizedNameLower = normalizedName.lowercase(Locale.US)
        val fileFormat = when {
            normalizedNameLower.endsWith(".stl") -> ImportedModelFormat.Stl
            normalizedNameLower.endsWith(".3mf") -> ImportedModelFormat.ThreeMf
            else -> null
        }
        if (fileFormat == null) {
            NativeEngineCalls.clearGeneratedGcode(handle)
            currentModelName = null
            return ModelLoadResult(
                message = "Failed to load model\nSelected file is not an STL or 3MF.",
                loaded = false,
                format = fileFormat
            )
        }

        val stagingStartedAt = SystemClock.elapsedRealtime()
        val stagedFile = stageModelFile(uri, normalizedName)
            ?: run {
                NativeEngineCalls.clearGeneratedGcode(handle)
                return ModelLoadResult(
                    message = "Failed to load model\nUnable to read the selected file.",
                    loaded = false,
                    format = fileFormat
                )
            }
        val stagingMs = SystemClock.elapsedRealtime() - stagingStartedAt
        var workspaceModelFile = stagedFile
        var workspaceModelFormat = fileFormat
        var workspaceGeometrySource: PlateObjectGeometrySource = PlateObjectGeometrySource.StagedFile
        val importExtraDetail = if (fileFormat == ImportedModelFormat.ThreeMf) {
            val extractedName = normalizedName.substringBeforeLast('.', normalizedName) + "-3mf-mesh.stl"
            val extractedStl = File(cacheDir, "selected-model-${SystemClock.elapsedRealtime()}-$extractedName")
            val extractionResult = NativeEngineCalls.extractModelMeshToStl(
                handle = handle,
                inputPath = stagedFile.absolutePath,
                outputStlPath = extractedStl.absolutePath
            )
            if (extractionResult !is NativeEngineCallResult.Success) {
                NativeEngineCalls.clearGeneratedGcode(handle)
                stagedFile.delete()
                extractedStl.delete()
                if (stagedModelFile?.absolutePath == stagedFile.absolutePath) {
                    stagedModelFile = null
                }
                currentModelName = null
                nativeLoadedModelPath = null
                return ModelLoadResult(
                    message = modelLoadStatusMessage(
                        loaded = false,
                        fileName = normalizedName,
                        timing = ModelImportTiming(stagingMs = stagingMs, nativeLoadMs = 0L),
                        extraDetail = "MobileSlicer could not find printable model geometry in this 3MF.\n${extractionResult.statusMessage}"
                    ),
                    loaded = false,
                    format = ImportedModelFormat.ThreeMf
                )
            }
            stagedModelFile = extractedStl
            workspaceModelFile = extractedStl
            workspaceModelFormat = ImportedModelFormat.Stl
            workspaceGeometrySource = PlateObjectGeometrySource.ThreeMfMeshExtract(
                originalPath = stagedFile.absolutePath,
                extractedStlPath = extractedStl.absolutePath
            )
            "3MF model loaded for the workspace preview."
        } else {
            "The workspace preview will finish loading after the model opens."
        }

        val bounds = runCatching {
            StlMeshParser.parseBounds(workspaceModelFile)
        }.getOrNull()

        val nativeLoadStartedAt = SystemClock.elapsedRealtime()
        val loadResult = NativeEngineCalls.loadModel(handle, workspaceModelFile.absolutePath)
        val nativeLoadMs = SystemClock.elapsedRealtime() - nativeLoadStartedAt
        val timing = ModelImportTiming(
            stagingMs = stagingMs,
            nativeLoadMs = nativeLoadMs
        )
        return if (loadResult is NativeEngineCallResult.Success) {
            currentModelName = normalizedName.substringBeforeLast('.', normalizedName)
            nativeLoadedModelPath = workspaceModelFile.absolutePath
            ModelLoadResult(
                message = modelLoadStatusMessage(
                    loaded = true,
                    fileName = normalizedName,
                    timing = timing,
                    extraDetail = importExtraDetail
                ),
                loaded = true,
                stagedFilePath = workspaceModelFile.absolutePath,
                format = workspaceModelFormat,
                loadTiming = timing,
                bounds = bounds,
                geometrySource = workspaceGeometrySource
            )
        } else {
            NativeEngineCalls.clearGeneratedGcode(handle)
            workspaceModelFile.delete()
            if (stagedModelFile?.absolutePath == workspaceModelFile.absolutePath) {
                stagedModelFile = null
            }
            currentModelName = null
            nativeLoadedModelPath = null
            ModelLoadResult(
                message = modelLoadStatusMessage(
                    loaded = false,
                    fileName = normalizedName,
                    timing = timing,
                    extraDetail = "MobileSlicer could not load this model."
                ),
                loaded = false,
                stagedFilePath = workspaceModelFile.absolutePath,
                format = workspaceModelFormat,
                loadTiming = timing,
                bounds = bounds
            )
        }
    }

internal fun MainActivity.prepareWorkspaceMesh(modelFilePath: String): WorkspacePreparationResult {
        val stagedFile = File(modelFilePath)
        if (!stagedFile.exists()) {
            return WorkspacePreparationResult(
                viewerPreparationError = "The model file is no longer available."
            )
        }

        val startedAt = SystemClock.elapsedRealtime()
        preparedViewerMeshCache.get(stagedFile)?.let { cachedMesh ->
            return WorkspacePreparationResult(
                preparedMesh = cachedMesh.mesh,
                timing = WorkspacePreparationTiming(
                    viewerMeshPrepMs = SystemClock.elapsedRealtime() - startedAt,
                    cacheHit = true,
                    sourceTriangleCount = cachedMesh.sourceTriangleCount,
                    displayTriangleCount = cachedMesh.displayTriangleCount,
                    reducedForDisplay = cachedMesh.reducedForDisplay
                )
            )
        }
        val preparedMeshResult = runCatching {
            StlMeshParser.parseForDisplay(stagedFile)
        }
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        preparedMeshResult.getOrNull()?.let {
            preparedViewerMeshCache.put(stagedFile, it)
        }
        val preparedMesh = preparedMeshResult.getOrNull()
        return WorkspacePreparationResult(
            preparedMesh = preparedMesh?.mesh,
            viewerPreparationError = preparedMeshResult.exceptionOrNull()?.message,
            timing = WorkspacePreparationTiming(
                viewerMeshPrepMs = elapsedMs,
                sourceTriangleCount = preparedMesh?.sourceTriangleCount,
                displayTriangleCount = preparedMesh?.displayTriangleCount,
                reducedForDisplay = preparedMesh?.reducedForDisplay == true
            )
        )
    }

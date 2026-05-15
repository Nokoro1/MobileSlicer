package com.mobileslicer

import android.app.Activity
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

internal fun MainActivity.cleanupCacheArtifacts(extraRetainedFiles: Collection<File> = emptyList()) {
        val retainedPaths = retainedCachePaths(extraRetainedFiles)
        cleanupGeneratedGcodeCache(cacheDir = cacheDir, retainedPaths = retainedPaths)
        cleanupStagedModelCache(cacheDir = cacheDir, retainedPaths = retainedPaths)
        cleanupShareCache(cacheDir)
        cleanupOrcaTempCache(cacheDir = cacheDir, retainedPaths = retainedPaths)
    }

internal fun MainActivity.clearFreshWorkspaceRuntimeArtifacts() {
        stagedModelFile = null
        currentModelName = null
        nativeLoadedModelPath = null
        preparedViewerMeshCache.clear()
        NativeEngineHandle.fromRaw(nativeEngineSession.currentRawHandle)?.let { handle ->
            NativeEngineCalls.clearGeneratedGcode(handle)
        }
        cleanupGeneratedGcodeCache(cacheDir = cacheDir, retainedPaths = emptySet())
        cleanupStagedModelCache(cacheDir = cacheDir, retainedPaths = emptySet())
        cleanupShareCache(cacheDir)
        cleanupOrcaTempCache(
            cacheDir = cacheDir,
            retainedPaths = emptySet(),
            maxBytes = 0L,
            maxAgeMs = 0L
        )
    }

internal fun MainActivity.cleanupGeneratedGcodeCache(
        retain: File? = null,
        retainedPaths: Set<String> = retainedCachePaths(listOfNotNull(retain))
    ) {
        cleanupGeneratedGcodeCache(cacheDir = cacheDir, retainedPaths = retainedPaths)
    }

internal fun MainActivity.retainedCachePaths(
        extraFiles: Collection<File> = emptyList(),
        includeCurrentGcode: Boolean = true
    ): Set<String> {
        val retained = LinkedHashSet<String>()
        fun retain(path: String?) {
            if (!path.isNullOrBlank()) {
                retained += File(path).absolutePath
            }
        }
        retain(stagedModelFile?.absolutePath)
        retain(workspaceSessionViewModel.currentModelFilePath.value)
        if (includeCurrentGcode) {
            retain(workspaceSessionViewModel.currentGcodeFilePath.value)
        }
        workspaceSessionViewModel.plateObjects.forEach { plateObject ->
            retain(plateObject.filePath)
        }
        extraFiles.forEach { file ->
            retained += file.absolutePath
        }
        return retained
    }

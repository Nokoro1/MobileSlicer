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
import com.mobileslicer.printerconnection.BambuLanPrintOptions
import com.mobileslicer.printerconnection.PrinterUploadAction
import com.mobileslicer.printerconnection.SimplyPrintOAuthClient
import com.mobileslicer.printerconnection.SimplyPrintOAuthResult
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.ProfileStoreRepository
import com.mobileslicer.profiles.PrintHostType
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.printerconnection.toBambuPackageFileName
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

internal fun MainActivity.exportGcodeToUri(uri: Uri, gcodeFilePath: String): String {
        val source = File(gcodeFilePath)
        if (!source.exists() || source.length() <= 0L) {
            return "Export failed\nGenerated G-code file was unavailable."
        }

        return try {
            val descriptor = contentResolver.openFileDescriptor(uri, "w")
                ?: return "Export failed\nUnable to open the destination file."
            descriptor.use { parcelDescriptor ->
                FileOutputStream(parcelDescriptor.fileDescriptor).use { output ->
                    source.inputStream().use { input ->
                        input.copyTo(output, bufferSize = 1 shl 20)
                    }
                    output.fd.sync()
                }
            }
            "Export successful\n${queryDisplayName(uri) ?: suggestGcodeFileName(currentModelName)}"
        } catch (exception: Exception) {
            Log.e(MainActivity.TAG, "Failed to export G-code to URI: $uri", exception)
            "Export failed\nUnable to write the G-code file."
        }
    }

internal fun MainActivity.testPrinterConnection(printerProfile: PrinterProfile): String = runBlocking(Dispatchers.IO) {
        printerConnectionRepository.testConnection(printerProfile).userMessage()
    }

internal suspend fun MainActivity.printerStatus(printerProfile: PrinterProfile): String = withContext(Dispatchers.IO) {
        printerConnectionRepository.fetchStatus(printerProfile).userMessage()
    }

internal suspend fun MainActivity.browsePrinterConnectionTargets(printerProfile: PrinterProfile): PrinterConnectionChoicesResult {
        return printerConnectionRepository.browseConnectionTargets(printerProfile)
    }

internal suspend fun MainActivity.discoverPrinterHosts(): PrinterConnectionChoicesResult {
        return printerDiscoveryRepository.discoverPrinters()
    }

internal suspend fun MainActivity.browsePrinterConnectionGroups(printerProfile: PrinterProfile): PrinterConnectionChoicesResult {
        return printerConnectionRepository.browseConnectionGroups(printerProfile)
    }

internal suspend fun MainActivity.loginToSimplyPrint(printerProfile: PrinterProfile): SimplyPrintOAuthResult {
        return simplyPrintOAuthClient.login { url ->
            withContext(Dispatchers.Main) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }.let { result ->
            if (!result.success) return result
            val updated = printerProfile.copy(
                printHostType = PrintHostType.SimplyPrint,
                printHost = "https://simplyprint.io/panel",
                printHostWebUi = "https://simplyprint.io/panel",
                printHostApiKey = result.accessToken,
                printHostPassword = result.refreshToken
            )
            val store = loadProfileStore()
            storeProfileStore(
                store.copy(
                    printers = store.printers.map { printer ->
                        if (printer.id == updated.id) updated else printer
                    }
                )
            )
            result
        }
    }

internal suspend fun MainActivity.sendGcodeToPrinter(
        gcodeFilePath: String,
        remoteFileName: String,
        printerProfile: PrinterProfile,
        uploadAction: PrinterUploadAction,
        bambuOptions: BambuLanPrintOptions? = null,
        onProgress: (Int) -> Unit
    ): PrinterConnectionResult = withContext(Dispatchers.IO) {
        val source = File(gcodeFilePath)
        val uploadSource = if (
            printerProfile.printHostType == PrintHostType.BambuLan &&
            !source.name.endsWith(".gcode.3mf", ignoreCase = true) &&
            !source.name.endsWith(".3mf", ignoreCase = true)
        ) {
            val handle = NativeEngineHandle.fromRaw(ensureEngine())
                ?: return@withContext PrinterConnectionResult(
                    false,
                    "Send failed",
                    "The slicer is not ready to package this file for Bambu LAN sending."
                )
            val packageFile = File(cacheDir, "latest-send-${remoteFileName.toBambuPackageFileName()}")
            cleanupGeneratedGcodeCache(retain = packageFile)
            val exportResult = NativeEngineCalls.writeBambuGcode3mfToFile(handle, packageFile.absolutePath)
            if (exportResult !is NativeEngineCallResult.Success || !packageFile.exists() || packageFile.length() <= 0L) {
                val nativeError = (exportResult as? NativeEngineCallResult.Failure)
                    ?.error
                    ?.message
                    .orEmpty()
                return@withContext PrinterConnectionResult(
                    false,
                    "Bambu package export failed",
                    nativeError.ifBlank { "Could not export the Bambu .gcode.3mf package." }
                )
            }
            packageFile
        } else {
            source
        }
        printerConnectionRepository.uploadGcode(
            profile = printerProfile,
            file = uploadSource,
            remoteFileName = remoteFileName,
            action = uploadAction,
            bambuOptions = bambuOptions,
            onProgress = onProgress
        )
    }

internal fun MainActivity.shareGcodeFile(gcodeFilePath: String, fileName: String? = null): String {
        val source = File(gcodeFilePath)
        if (!source.exists() || source.length() <= 0L) {
            return "Share failed\nGenerated G-code file was unavailable."
        }

        val shareDir = File(cacheDir, "shared").apply {
            deleteRecursively()
            mkdirs()
        }
        val target = File(shareDir, fileName?.takeIf { it.isNotBlank() } ?: suggestGcodeFileName(currentModelName))
        return try {
            source.copyTo(target, overwrite = true)

            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                target
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = GCODE_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, target.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share G-code"))
            "Share ready\n${target.name}"
        } catch (exception: Exception) {
            Log.e(MainActivity.TAG, "Failed to share generated G-code", exception)
            "Share failed\nUnable to prepare the G-code file."
        }
    }

package com.mobileslicer.profiles

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import com.mobileslicer.AppSettingOption
import com.mobileslicer.R
import com.mobileslicer.SoftPill
import com.mobileslicer.appBackgroundGradient
import com.mobileslicer.appBodyColor
import com.mobileslicer.appCardColor
import com.mobileslicer.appCardColorMuted
import com.mobileslicer.appMutedColor
import com.mobileslicer.appOutlineColor
import com.mobileslicer.appTitleColor
import com.mobileslicer.printerconnection.PrinterConnectionChoicesResult
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.nativebridge.NativeEngineBridge
import com.mobileslicer.ui.theme.AccentPaletteOption
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.printerconnection.SimplyPrintOAuthResult
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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.toImportedDeviceOrcaPrinterProfile(context: Context, sourceName: String): PrinterProfile {
    val prettyName = optString(NativeConfigKeys.Printer.Name).ifBlank {
        sourceName.substringAfterLast('/').removeSuffix(".json").removeSuffix(".JSON")
    }.ifBlank { "Imported Printer" }
    val linkedPreset = findLinkedOrcaPrinterPreset(context, this, prettyName)
    val bedBounds = parseOrcaPrintableAreaBounds(opt(NativeConfigKeys.Bed.PrintableArea))
    val nozzle = profileConfigFloat(
        NativeConfigKeys.Printer.NozzleDiameter,
        optString(NativeConfigKeys.Printer.Variant).toFloatOrNull() ?: 0.4f
    )
    val nozzles = opt(NativeConfigKeys.Printer.NozzleDiameter).orcaFloatList().ifEmpty { listOf(nozzle) }
    val machineJson = toString()
    val sourcePath = "device://${sourceName}#${machineJson.hashCode().toUInt().toString(16)}"
    val preset = OrcaPrinterPreset(
        name = prettyName,
        family = optString(NativeConfigKeys.Printer.Model).ifBlank { "Device Import" },
        searchText = prettyName.lowercase(Locale.US),
        nozzleDiameters = nozzles.joinToString(";") { formatNozzle(it) },
        profilePath = sourcePath,
        coverAssetPath = linkedPreset?.coverAssetPath.orEmpty(),
        importBundleAssetPath = "",
        bedModelAssetPath = linkedPreset?.bedModelAssetPath.orEmpty(),
        bedTextureAssetPath = linkedPreset?.bedTextureAssetPath.orEmpty(),
        bedWidthMm = bedBounds?.first ?: 220f,
        bedDepthMm = bedBounds?.second ?: 220f,
        maxHeightMm = profileConfigFloat("printable_height", 220f),
        activeNozzleDiameterMm = nozzle,
        nozzleMachinePaths = listOf(sourcePath),
        resolvedSourceChains = listOf(
            listOfNotNull(
                optString(NativeConfigKeys.Printer.Inherits).takeIf { it.isNotBlank() },
                prettyName
            ).joinToString(" -> ")
        )
    )
    val bundle = OrcaPrinterImportBundle(
        machineModelJson = machineJson,
        resolvedMachineJson = machineJson,
        nozzleMachineJsons = listOf(machineJson),
        resolvedMachineJsons = listOf(machineJson),
        processPresets = emptyList()
    )
    return preset.toImportedPrinterProfile(bundle, nozzle)
}

internal fun JSONObject.toImportedDeviceOrcaFilamentProfile(
    context: Context,
    sourceName: String,
    currentStore: ProfileStore
): FilamentProfile {
    val printer = currentStore.printers.firstOrNull { it.id == currentStore.selectedPrinterId }
        ?: error("Select a printer before importing an Orca filament preset.")
    val prettyName = optString(NativeConfigKeys.Printer.Name).ifBlank {
        sourceName.substringAfterLast('/').removeSuffix(".json").removeSuffix(".JSON")
    }.ifBlank { "Imported Filament" }
    val printerBaseline = findMobileSlicerFilamentBaseline(currentStore, this, prettyName)
    val parentBundle = findInheritedOrcaFilamentBundle(context, this)
    val parentJson = parentBundle
        ?.resolvedFilamentJson
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
    val resolved = when {
        printerBaseline != null && parentJson != null -> printerBaseline.copyWithOverlay(parentJson).copyWithOverlay(this)
        printerBaseline != null -> printerBaseline.copyWithOverlay(this)
        parentJson != null -> parentJson.copyWithOverlay(this)
        else -> error("Select or create a ${prettyName.detectFilamentMaterial() ?: "base"} filament for ${printer.name} before importing this partial Orca filament preset.")
    }
    val material = resolved.profileConfigString(NativeConfigKeys.Filament.Type, prettyName)
    val sourcePath = "device://${sourceName}#${toString().hashCode().toUInt().toString(16)}"
    val preset = OrcaFilamentPreset(
        name = prettyName.removePrefix("Generic ").ifBlank { material },
        rawName = prettyName,
        family = resolved.optString("filament_vendor").ifBlank { "Device Import" },
        materialType = material,
        vendor = resolved.optString("filament_vendor").ifBlank { "(Undefined)" },
        defaultFilamentColor = resolved.profileConfigString(NativeConfigKeys.Filament.DefaultColor),
        profilePath = sourcePath,
        importBundleAssetPath = "",
        compatiblePrinters = emptyList(),
        compatiblePrinterKeys = emptyList(),
        pickerDuplicateKey = sourcePath,
        searchText = "$prettyName $material".lowercase(Locale.US)
    )
    return preset.toImportedFilamentProfile(
        OrcaFilamentImportBundle(
            rawFilamentJson = toString(),
            resolvedFilamentJson = resolved.toString(),
            resolvedSourceChain = parentBundle?.resolvedSourceChain.orEmpty() +
                listOfNotNull(optString(NativeConfigKeys.Printer.Inherits).takeIf { it.isNotBlank() }, prettyName)
        ),
        printer = printer
    )
}

internal fun JSONObject.toImportedDeviceOrcaProcessProfile(
    context: Context,
    sourceName: String,
    currentStore: ProfileStore
): ProcessProfile {
    val printer = currentStore.printers.firstOrNull { it.id == currentStore.selectedPrinterId }
        ?: error("Select a printer before importing an Orca process preset.")
    val nozzle = printer?.nozzleDiameterMm ?: 0.4f
    val prettyName = optString(NativeConfigKeys.Printer.Name).ifBlank {
        sourceName.substringAfterLast('/').removeSuffix(".json").removeSuffix(".JSON")
    }.ifBlank { "Imported Process" }
    val sourcePath = "device://${sourceName}#${toString().hashCode().toUInt().toString(16)}"
    val parentBundle = findInheritedOrcaProcessPresetBundle(context, currentStore, this)
    val resolved = parentBundle
        ?.resolvedProcessJson
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?.copyWithOverlay(this)
        ?: this
    val resolvedMachine = jsonObjectOrNull(printer.orcaResolvedMachineJson)
    return OrcaProcessPresetBundle(
        machineName = resolvedMachine?.profileConfigString(NativeConfigKeys.Printer.Name).orEmpty(),
        nozzleDiameterMm = nozzle,
        name = prettyName,
        rawName = prettyName,
        profilePath = sourcePath,
        rawProcessJson = toString(),
        resolvedProcessJson = resolved.toString(),
        resolvedSourceChain = parentBundle?.resolvedSourceChain.orEmpty() +
            listOfNotNull(optString(NativeConfigKeys.Printer.Inherits).takeIf { it.isNotBlank() }, prettyName)
    ).toImportedProcessProfile(
        printer
    )
}

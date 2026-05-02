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

internal fun importProfilePayloadFromDeviceUri(context: Context, uri: Uri, currentStore: ProfileStore): ProfileImportPayload {
    val displayName = queryDisplayName(context, uri) ?: "imported-profiles"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Unable to read selected profile file.")
    val zipPayload = parseProfilePayloadZip(context, bytes, currentStore)
    if (zipPayload != null) return zipPayload
    return parseProfilePayloadText(context, bytes.toString(StandardCharsets.UTF_8), displayName, currentStore)
}

internal fun parseProfilePayloadZip(context: Context, bytes: ByteArray, currentStore: ProfileStore): ProfileImportPayload? {
    if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4b.toByte()) {
        return null
    }
    val parsed = mutableListOf<ProfileImportPayload>()
    ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                val text = zip.readBytes().toString(StandardCharsets.UTF_8)
                parsed += parseProfilePayloadText(context, text, entry.name.substringAfterLast('/'), currentStore)
            }
            zip.closeEntry()
        }
    }
    return when {
        parsed.isEmpty() -> error("No JSON profile files were found in the selected ZIP.")
        parsed.size == 1 -> parsed.single()
        parsed.all { it is ProfileImportPayload.Printer } -> error("This ZIP contains multiple Orca printer presets. Import one printer preset at a time for now.")
        parsed.all { it is ProfileImportPayload.Filament } -> error("This ZIP contains multiple Orca filament presets. Import one filament preset at a time for now.")
        parsed.all { it is ProfileImportPayload.Process } -> error("This ZIP contains multiple Orca process presets. Import one process preset at a time for now.")
        else -> error("This ZIP mixes profile export formats that cannot be imported together.")
    }
}

internal fun parseProfilePayloadText(
    context: Context,
    text: String,
    displayName: String,
    currentStore: ProfileStore
): ProfileImportPayload {
    runCatching {
        return ProfileImportPayload.Store(profileStoreFromDeviceTransferJson(text))
    }
    val json = runCatching { JSONObject(text) }.getOrElse {
        error("Profile file is not valid JSON.")
    }
    if (json.isOrcaPrinterPresetJson()) {
        val profile = json.toImportedDeviceOrcaPrinterProfile(context, displayName)
        val processes = findOrcaPrinterPresetForProfile(context, profile)
            ?.let { preset -> loadOrcaPrinterImportBundle(context, preset).toImportedProcessProfiles(profile) }
            .orEmpty()
        return ProfileImportPayload.Printer(profile, processes)
    }
    if (json.isOrcaFilamentPresetJson()) {
        return ProfileImportPayload.Filament(json.toImportedDeviceOrcaFilamentProfile(context, displayName, currentStore))
    }
    if (json.isOrcaProcessPresetJson()) {
        return ProfileImportPayload.Process(json.toImportedDeviceOrcaProcessProfile(context, displayName, currentStore))
    }
    error("Only MobileSlicer profile exports and Orca printer, filament, or process preset JSON files are supported here.")
}

internal fun JSONObject.isOrcaPrinterPresetJson(): Boolean =
    has(NativeConfigKeys.Bed.PrintableArea) && has("printable_height") && has(NativeConfigKeys.Printer.NozzleDiameter) &&
        (has(NativeConfigKeys.Printer.Model) || has("machine_start_gcode") || has("gcode_flavor"))

internal fun JSONObject.isOrcaFilamentPresetJson(): Boolean =
    has(NativeConfigKeys.Filament.Type) || has("nozzle_temperature") || has("filament_flow_ratio") ||
        has(NativeConfigKeys.Temperature.HotPlate) || has(NativeConfigKeys.Temperature.Bed)

internal fun JSONObject.isOrcaProcessPresetJson(): Boolean =
    has("layer_height") || has("wall_loops") || has("sparse_infill_density") ||
        has("initial_layer_speed") || has("outer_wall_speed")

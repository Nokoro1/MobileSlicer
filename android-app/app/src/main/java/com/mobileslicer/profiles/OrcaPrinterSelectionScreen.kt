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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@Composable
internal fun OrcaPrinterSelectionScreen(
    onBack: () -> Unit,
    onImport: (PrinterProfile, List<ProcessProfile>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val titleColor = appTitleColor()
    val outlineColor = appOutlineColor()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var importingProfilePath by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var nozzleSelectionPreset by remember { mutableStateOf<OrcaPrinterPreset?>(null) }
    val presets by produceState(initialValue = emptyList<OrcaPrinterPreset>(), context) {
        value = withContext(Dispatchers.IO) {
            loadOrcaPrinterPresets(context)
        }
    }
    val filteredPresets = remember(presets, query) {
        val cleanQuery = query.trim().lowercase(Locale.US)
        if (cleanQuery.isEmpty()) {
            presets
        } else {
            presets.filter { it.searchText.contains(cleanQuery) }
        }
    }
    val printerRows = remember(filteredPresets) {
        buildList {
            var previousFamily = ""
            filteredPresets.forEach { preset ->
                if (preset.family != previousFamily) {
                    add(OrcaPrinterListRow.Header(preset.family))
                    previousFamily = preset.family
                }
                add(OrcaPrinterListRow.Preset(preset))
            }
        }
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            confirmButton = {
                TextButton(onClick = { importError = null }) {
                    Text("OK")
                }
            },
            title = { Text("Printer import failed") },
            text = { Text(message) }
        )
    }

    nozzleSelectionPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { nozzleSelectionPreset = null },
            confirmButton = {},
            title = { Text("Select nozzle size") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    preset.nozzleDiametersList().forEach { nozzle ->
                        Button(
                            onClick = {
                                nozzleSelectionPreset = null
                                importingProfilePath = preset.profilePath
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            val bundle = loadOrcaPrinterImportBundle(context, preset)
                                            val profile = preset.toImportedPrinterProfile(bundle, nozzle)
                                            profile to bundle.toImportedProcessProfiles(profile)
                                        }
                                    }.onSuccess { (profile, processes) ->
                                        importingProfilePath = null
                                        onImport(profile, processes)
                                    }.onFailure { error ->
                                        importingProfilePath = null
                                        importError = error.localizedMessage ?: "Unable to read printer preset data."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("${formatNozzle(nozzle)} mm")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { nozzleSelectionPreset = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(colors = appBackgroundGradient()))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(appCardColorMuted())
                        .border(1.dp, outlineColor, RoundedCornerShape(18.dp))
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = titleColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = "Select printer",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = titleColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text("Search printers") },
                label = { Text("Printer name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = appCardColor().copy(alpha = 0.72f),
                    unfocusedContainerColor = appCardColor().copy(alpha = 0.72f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = outlineColor
                )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                if (printerRows.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (query.isNotBlank()) {
                                    "No printer presets match \"$query\"."
                                } else {
                                    "No printer presets are available."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = appBodyColor()
                            )
                            if (query.isNotBlank()) {
                                TextButton(onClick = { query = "" }) {
                                    Text("Clear search")
                                }
                            }
                        }
                    }
                }
                items(printerRows) { row ->
                    when (row) {
                        is OrcaPrinterListRow.Header -> OrcaPrinterFamilyHeader(row.family)
                        is OrcaPrinterListRow.Preset -> {
                            val preset = row.preset
                            OrcaPrinterPresetRow(
                                preset = preset,
                                enabled = importingProfilePath == null,
                                onClick = {
                                    val nozzles = preset.nozzleDiametersList()
                                    if (nozzles.size > 1) {
                                        nozzleSelectionPreset = preset
                                    } else {
                                        importingProfilePath = preset.profilePath
                                        scope.launch {
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    val bundle = loadOrcaPrinterImportBundle(context, preset)
                                                    val selectedNozzle = nozzles.firstOrNull() ?: preset.activeNozzleDiameterMm
                                                    val profile = preset.toImportedPrinterProfile(bundle, selectedNozzle)
                                                    profile to bundle.toImportedProcessProfiles(profile)
                                                }
                                            }.onSuccess { (profile, processes) ->
                                                importingProfilePath = null
                                                onImport(profile, processes)
                                            }.onFailure { error ->
                                                importingProfilePath = null
                                                importError = error.localizedMessage ?: "Unable to read printer preset data."
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        if (importingProfilePath != null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun OrcaPrinterFamilyHeader(family: String) {
    Text(
        text = family,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        color = appBodyColor(),
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
internal fun OrcaPrinterPresetRow(
    preset: OrcaPrinterPreset,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val titleColor = appTitleColor()
    val outlineColor = appOutlineColor()
    val cover = rememberAssetImage(preset.coverAssetPath)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = appCardColor().copy(alpha = 0.86f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, outlineColor.copy(alpha = 0.42f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(appCardColorMuted()),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) {
                    Image(
                        bitmap = cover,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = preset.name.take(1),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftPill(label = preset.family)
                    if (preset.nozzleDiameters.isNotBlank()) {
                        SoftPill(label = "${preset.nozzleDiameters.replace(";", ", ")} mm")
                    }
                }
            }
        }
    }
}

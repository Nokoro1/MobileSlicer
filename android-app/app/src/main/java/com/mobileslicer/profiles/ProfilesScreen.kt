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
import androidx.compose.runtime.mutableIntStateOf
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
internal fun ProfilesScreen(
    store: ProfileStore,
    showAdvancedProfileSettings: Boolean,
    onStoreChanged: (ProfileStore) -> Unit,
    onTestPrinterConnection: suspend (PrinterProfile) -> String,
    onPrinterStatus: suspend (PrinterProfile) -> String,
    onDiscoverPrinterHosts: suspend () -> PrinterConnectionChoicesResult,
    onBrowsePrinterConnectionTargets: suspend (PrinterProfile) -> PrinterConnectionChoicesResult,
    onBrowsePrinterConnectionGroups: suspend (PrinterProfile) -> PrinterConnectionChoicesResult,
    onSimplyPrintLogin: suspend (PrinterProfile) -> SimplyPrintOAuthResult,
    onOpenPrinterUi: (PrinterProfile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    val outlineColor = appOutlineColor()

    fun updateStore(transform: (ProfileStore) -> ProfileStore) {
        onStoreChanged(transform(store))
    }

    fun uniquePrinterImport(
        imported: PrinterProfile,
        importedProcesses: List<ProcessProfile>,
        existingPrinters: List<PrinterProfile>
    ): Pair<PrinterProfile, List<ProcessProfile>> {
        if (existingPrinters.none { it.id == imported.id }) {
            return imported to importedProcesses
        }
        val printer = imported.copy(
            id = ProfileStoreRepository.newCustomId(ProfileTab.Printer),
            builtIn = false
        )
        val processes = importedProcesses.map { process ->
            process.withValues(
                "id" to ProfileStoreRepository.newCustomId(ProfileTab.Process),
                "printerProfileId" to printer.id
            )
        }
        return printer to processes
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(ProfileTab.Printer.ordinal) }
    var editorRequest by remember { mutableStateOf<ProfileEditorRequest?>(null) }
    var deleteRequest by remember { mutableStateOf<ProfileDeleteRequest?>(null) }
    var showingPrinterSelection by rememberSaveable { mutableStateOf(false) }
    var showingFilamentSelection by rememberSaveable { mutableStateOf(false) }
    var showingProcessSelection by rememberSaveable { mutableStateOf(false) }
    var profileTransferError by remember { mutableStateOf<String?>(null) }
    var profileTransferMessage by remember { mutableStateOf<String?>(null) }
    var importProfilesInProgress by remember { mutableStateOf(false) }
    var exportProfilesInProgress by remember { mutableStateOf(false) }
    var showingExportProfiles by remember { mutableStateOf(false) }
    var pendingOrcaExportOption by remember { mutableStateOf<OrcaProfileExportOption?>(null) }
    val orcaExportOptions = remember(store) { store.orcaProfileExportOptions() }
    val scope = rememberCoroutineScope()
    val importProfilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importProfilesInProgress = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    importProfilePayloadFromDeviceUri(context, uri, store)
                }
            }.onSuccess { payload ->
                when (payload) {
                    is ProfileImportPayload.Store -> {
                        val result = payload.store
                        onStoreChanged(mergeImportedProfileStore(store, result.store))
                        profileTransferMessage =
                            "Imported ${result.printerCount} printer, ${result.filamentCount} filament, and ${result.processCount} process profiles."
                    }
                    is ProfileImportPayload.Printer -> {
                        var importedName = payload.profile.name
                        updateStore {
                            val (imported, importedProcesses) = uniquePrinterImport(
                                imported = payload.profile,
                                importedProcesses = payload.processes,
                                existingPrinters = it.printers
                            )
                            importedName = imported.name
                            val nextPrinters = it.printers
                                .plus(imported)
                            val nextProcessId = importedProcesses.firstOrNull()?.id.orEmpty()
                            it.copy(
                                printers = nextPrinters,
                                processes = it.processes + importedProcesses,
                                selectedPrinterId = imported.id,
                                selectedFilamentId = "",
                                selectedProcessId = nextProcessId,
                                selectedProcessIdsByPrinterId = it.rememberProcessSelection(imported.id, nextProcessId)
                            )
                        }
                        selectedTab = ProfileTab.Printer.ordinal
                        profileTransferMessage = "Imported $importedName."
                    }
                    is ProfileImportPayload.Filament -> {
                        val imported = payload.profile.bindToPrinter(store.selectedPrinterId)
                        updateStore {
                            val nextFilaments = it.filaments
                                .filterNot { profile ->
                                    profile.id == imported.id ||
                                        (profile.printerProfileId == imported.printerProfileId &&
                                            profile.orcaFilamentPath.isNotBlank() &&
                                            profile.orcaFilamentPath == imported.orcaFilamentPath)
                                }
                                .plus(imported)
                            it.copy(
                                filaments = nextFilaments,
                                selectedFilamentId = imported.id
                            )
                        }
                        selectedTab = ProfileTab.Filament.ordinal
                        profileTransferMessage = "Imported ${imported.name}."
                    }
                    is ProfileImportPayload.Process -> {
                        val imported = payload.profile
                        updateStore {
                            val nextProcesses = it.processes
                                .filterNot { profile -> profile.id == imported.id || profile.orcaProcessPath == imported.orcaProcessPath }
                                .plus(imported)
                            it.copy(
                                processes = nextProcesses,
                                selectedProcessId = imported.id
                            ).withSelectedProcessForCurrentPrinter(imported.id)
                        }
                        selectedTab = ProfileTab.Process.ordinal
                        profileTransferMessage = "Imported ${imported.name}."
                    }
                }
            }.onFailure { error ->
                profileTransferError = error.localizedMessage ?: "Unable to import profiles from this file."
            }
            importProfilesInProgress = false
        }
    }
    val exportProfilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val exportOption = pendingOrcaExportOption
        pendingOrcaExportOption = null
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            exportProfilesInProgress = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching {
                require(exportOption != null) { "Choose a profile export first." }
                withContext(Dispatchers.IO) {
                    val bytes = store.exportOrcaProfileOption(exportOption)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(bytes)
                    } ?: error("Unable to open profile export destination.")
                }
            }.onSuccess {
                profileTransferMessage = "Exported ${exportOption?.kind?.label.orEmpty()}."
            }.onFailure { error ->
                profileTransferError = error.localizedMessage ?: "Unable to export profiles."
            }
            exportProfilesInProgress = false
        }
    }

    if (showingExportProfiles) {
        AlertDialog(
            onDismissRequest = { showingExportProfiles = false },
            title = { Text("Export linked profiles") },
            text = {
                if (orcaExportOptions.isEmpty()) {
                    Text("No linked printer, filament, or process profiles are available to export.")
                } else {
                    val groupedOptions = orcaExportOptions.groupBy { it.kind.groupLabel }
                    val exportGroupOrder = listOf("Printer", "Filament", "Process")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = ".orca_printer is the full linked printer export. ZIP choices write standalone preset JSON files.",
                            color = appMutedColor(),
                            style = MaterialTheme.typography.bodySmall
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            exportGroupOrder.forEach { group ->
                                val options = groupedOptions[group].orEmpty()
                                if (options.isNotEmpty()) {
                                    item(key = "header_$group") {
                                        Text(
                                            text = group,
                                            color = appMutedColor(),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                        )
                                    }
                                    items(options, key = { it.id }) { option ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    showingExportProfiles = false
                                                    pendingOrcaExportOption = option
                                                    exportProfilesInProgress = true
                                                    exportProfilesLauncher.launch(
                                                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                                            addCategory(Intent.CATEGORY_OPENABLE)
                                                            type = option.kind.createDocumentMimeType
                                                            putExtra(Intent.EXTRA_TITLE, option.fileName)
                                                        }
                                                    )
                                                }
                                                .padding(horizontal = 2.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = option.kind.label,
                                                    color = titleColor,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = option.title,
                                                    color = bodyColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = option.subtitle,
                                                    color = appMutedColor(),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = option.kind.formatLabel,
                                                color = appMutedColor(),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showingExportProfiles = false }) {
                    Text("Close")
                }
            }
        )
    }

    LaunchedEffect(store.selectedPrinterId, store.processes.size) {
        val selectedPrinter = store.printers.firstOrNull { it.id == store.selectedPrinterId }
        val hasProcesses = store.processes.any { it.printerProfileId == store.selectedPrinterId }
        if (selectedPrinter != null &&
            !hasProcesses &&
            selectedPrinter.profileSource == "orca" &&
            selectedPrinter.orcaMachineModelPath.isNotBlank()
        ) {
            val backfilledProcesses = withContext(Dispatchers.IO) {
                val preset = findOrcaPrinterPresetForProfile(context, selectedPrinter)
                preset?.let { loadOrcaPrinterImportBundle(context, it).toImportedProcessProfiles(selectedPrinter) }
            }.orEmpty()
            if (backfilledProcesses.isNotEmpty()) {
                onStoreChanged(
                    store.copy(
                        processes = store.processes.filterNot { it.printerProfileId == selectedPrinter.id } + backfilledProcesses,
                        selectedProcessId = backfilledProcesses.first().id,
                        selectedProcessIdsByPrinterId = store.rememberProcessSelection(selectedPrinter.id, backfilledProcesses.first().id)
                    )
                )
            }
        }
    }

    if (showingPrinterSelection) {
        BackHandler { showingPrinterSelection = false }
        OrcaPrinterSelectionScreen(
            onBack = { showingPrinterSelection = false },
            onImport = { imported, importedProcesses ->
                updateStore {
                    val (printerProfile, processProfiles) = uniquePrinterImport(
                        imported = imported,
                        importedProcesses = importedProcesses,
                        existingPrinters = it.printers
                    )
                    val nextProcessId = processProfiles.firstOrNull()?.id.orEmpty()
                    it.copy(
                        printers = it.printers + printerProfile,
                        processes = it.processes + processProfiles,
                        selectedPrinterId = printerProfile.id,
                        selectedFilamentId = "",
                        selectedProcessId = nextProcessId,
                        selectedProcessIdsByPrinterId = it.rememberProcessSelection(printerProfile.id, nextProcessId)
                    )
                }
                showingPrinterSelection = false
            },
            modifier = modifier
        )
        return
    }

    if (showingFilamentSelection) {
        BackHandler { showingFilamentSelection = false }
        OrcaFilamentSelectionScreen(
            selectedPrinter = store.printers.firstOrNull { it.id == store.selectedPrinterId },
            onBack = { showingFilamentSelection = false },
	            onImport = { imported ->
	                updateStore {
	                    val bound = imported.bindToPrinter(it.selectedPrinterId)
	                    val existing = it.filaments.firstOrNull { profile ->
	                        profile.printerProfileId == it.selectedPrinterId &&
	                            profile.orcaFilamentPath.isNotBlank() &&
	                            profile.orcaFilamentPath == bound.orcaFilamentPath
	                    }
	                    if (existing != null) {
	                        it.copy(selectedFilamentId = existing.id)
	                    } else {
	                        val replacedProfiles = it.filaments.filterNot { profile ->
	                            profile.isReplaceableOrcaGenericMaterialFor(
	                                imported = bound,
	                                printerId = it.selectedPrinterId
	                            )
	                        }
	                        it.copy(
	                            filaments = replacedProfiles + bound,
	                            selectedFilamentId = bound.id
	                        )
	                    }
	                }
                showingFilamentSelection = false
            },
            modifier = modifier
        )
        return
    }

    if (showingProcessSelection) {
        BackHandler { showingProcessSelection = false }
        ProcessPresetSelectionScreen(
            printer = store.printers.firstOrNull { it.id == store.selectedPrinterId },
            onBack = { showingProcessSelection = false },
            onAdd = { preset ->
                updateStore {
                    it.copy(
                        processes = it.processes.filterNot { process -> process.id == preset.id } + preset,
                        selectedProcessId = preset.id
                    ).withSelectedProcessForCurrentPrinter(preset.id)
                }
                Toast.makeText(context, "Imported ${preset.name}", Toast.LENGTH_SHORT).show()
                showingProcessSelection = false
            },
            modifier = modifier
        )
        return
    }

    when (val request = editorRequest) {
        is ProfileEditorRequest.Printer -> {
            BackHandler { editorRequest = null }
            PrinterProfileEditorDialog(
                initial = request.profile,
                isNew = request.isNew,
                showAdvancedProfileSettings = showAdvancedProfileSettings,
                onDismiss = { editorRequest = null },
                onTestConnection = onTestPrinterConnection,
                onPrinterStatus = onPrinterStatus,
                onDiscoverPrinterHosts = onDiscoverPrinterHosts,
                onBrowseConnectionTargets = onBrowsePrinterConnectionTargets,
                onBrowseConnectionGroups = onBrowsePrinterConnectionGroups,
                onSimplyPrintLogin = onSimplyPrintLogin,
                onOpenPrinterUi = onOpenPrinterUi,
                onSave = { saved ->
                    updateStore {
                        val nextProfiles = it.printers.upsert(saved)
                        val nextVisibleFilaments = it.filaments.filter { profile -> profile.printerProfileId == saved.id }
                        it.copy(
                            printers = nextProfiles,
                            selectedPrinterId = saved.id,
                            selectedFilamentId = nextVisibleFilaments.firstOrNull()?.id.orEmpty()
                        )
                    }
                    editorRequest = null
                }
            )
            return
        }
        is ProfileEditorRequest.Filament -> {
            BackHandler { editorRequest = null }
            FilamentProfileEditorDialog(
                initial = request.profile,
                isNew = request.isNew,
                showAdvancedProfileSettings = showAdvancedProfileSettings,
                availablePrinterProfileNames = store.printers.map { it.name }.distinct().sorted(),
                availableProcessProfileNames = store.processes.map { it.name }.distinct().sorted(),
                onDismiss = { editorRequest = null },
                onSave = { saved ->
                    updateStore {
                        val bound = saved.bindToPrinter(it.selectedPrinterId)
                        val nextProfiles = it.filaments.upsert(bound)
                        it.copy(filaments = nextProfiles, selectedFilamentId = bound.id)
                    }
                    editorRequest = null
                }
            )
            return
        }
        is ProfileEditorRequest.Process -> {
            BackHandler { editorRequest = null }
            ProcessProfileEditorDialog(
                initial = request.profile,
                isNew = request.isNew,
                showAdvancedProfileSettings = showAdvancedProfileSettings,
                onDismiss = { editorRequest = null },
                onSave = { saved ->
                    updateStore {
                        val nextProfiles = it.processes.upsert(saved)
                        it.copy(processes = nextProfiles, selectedProcessId = saved.id)
                            .withSelectedProcessForCurrentPrinter(saved.id)
                    }
                    editorRequest = null
                }
            )
            return
        }
        null -> Unit
    }

    deleteRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { deleteRequest = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (request.tab) {
                            ProfileTab.Printer -> updateStore {
                                val deletedPrinter = it.printers.firstOrNull { profile -> profile.id == request.profileId }
                                clearPrinterMaterialSlotPreferences(
                                    context = context,
                                    printerId = request.profileId,
                                    legacyPrinterName = deletedPrinter?.name.orEmpty()
                                )
                                val nextProfiles = it.printers.filterNot { profile -> profile.id == request.profileId }
                                val nextFilaments = it.filaments.filterNot { profile -> profile.printerProfileId == request.profileId }
                                val nextProcesses = it.processes.filterNot { profile -> profile.printerProfileId == request.profileId }
                                val nextSelectedPrinterId = if (it.selectedPrinterId == request.profileId) {
                                    nextProfiles.firstOrNull()?.id.orEmpty()
                                } else {
                                    it.selectedPrinterId
                                }
                                val nextVisibleFilaments = nextFilaments.filter { profile -> profile.printerProfileId == nextSelectedPrinterId }
                                val nextSelectedFilamentId = if (it.selectedPrinterId == request.profileId || it.selectedFilamentId !in nextVisibleFilaments.map { profile -> profile.id }) {
                                    nextVisibleFilaments.firstOrNull()?.id.orEmpty()
                                } else {
                                    it.selectedFilamentId
                                }
                                val nextSelectedProcessId = if (it.selectedPrinterId == request.profileId) {
                                    nextProcesses.firstOrNull { process -> process.printerProfileId == nextSelectedPrinterId }?.id.orEmpty()
                                } else {
                                    it.selectedProcessId.takeIf { processId ->
                                        nextProcesses.any { process ->
                                            process.id == processId && process.printerProfileId == nextSelectedPrinterId
                                        }
                                    }.orEmpty()
                                }
                                it.copy(
                                    printers = nextProfiles,
                                    filaments = nextFilaments,
                                    processes = nextProcesses,
                                    selectedPrinterId = nextSelectedPrinterId,
                                    selectedFilamentId = nextSelectedFilamentId,
                                    selectedProcessId = nextSelectedProcessId,
                                    selectedProcessIdsByPrinterId = (it.selectedProcessIdsByPrinterId - request.profileId)
                                        .let { selections ->
                                            if (nextSelectedPrinterId.isNotBlank() && nextSelectedProcessId.isNotBlank()) {
                                                selections + (nextSelectedPrinterId to nextSelectedProcessId)
                                            } else {
                                                selections
                                            }
                                        }
                                )
                            }
                            ProfileTab.Filament -> updateStore {
                                val nextProfiles = it.filaments.filterNot { profile -> profile.id == request.profileId }
                                val nextVisible = nextProfiles.filter { profile -> profile.printerProfileId == it.selectedPrinterId }
                                it.copy(
                                    filaments = nextProfiles,
                                    selectedFilamentId = if (it.selectedFilamentId == request.profileId) nextVisible.firstOrNull()?.id.orEmpty() else it.selectedFilamentId
                                )
                            }
                            ProfileTab.Process -> updateStore {
                                val nextProfiles = it.processes.filterNot { profile -> profile.id == request.profileId }
                                val nextVisible = nextProfiles.filter { profile -> profile.printerProfileId == it.selectedPrinterId }
                                val nextSelectedProcessId = if (it.selectedProcessId == request.profileId) {
                                    nextVisible.standardProcessForPrinter(it.selectedPrinterId)?.id ?: nextVisible.firstOrNull()?.id.orEmpty()
                                } else {
                                    it.selectedProcessId
                                }
                                it.copy(
                                    processes = nextProfiles,
                                    selectedProcessId = nextSelectedProcessId,
                                    selectedProcessIdsByPrinterId = it.selectedProcessIdsByPrinterId
                                        .filterValues { processId -> processId != request.profileId }
                                        .let { selections ->
                                            if (nextSelectedProcessId.isNotBlank()) {
                                                selections + (it.selectedPrinterId to nextSelectedProcessId)
                                            } else {
                                                selections - it.selectedPrinterId
                                            }
                                        }
                                )
                            }
                        }
                        deleteRequest = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteRequest = null }) {
                    Text("Cancel")
                }
            },
            title = { Text("Delete custom profile") },
            text = { Text("Delete \"${request.profileName}\" from this device only. Built-in profiles stay available.") }
        )
    }

    profileTransferError?.let { message ->
        AlertDialog(
            onDismissRequest = { profileTransferError = null },
            confirmButton = {
                TextButton(onClick = { profileTransferError = null }) {
                    Text("OK")
                }
            },
            title = { Text("Profile transfer failed") },
            text = { Text(message) }
        )
    }

    profileTransferMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { profileTransferMessage = null },
            confirmButton = {
                TextButton(onClick = { profileTransferMessage = null }) {
                    Text("OK")
                }
            },
            title = { Text("Profiles updated") },
            text = { Text(message) }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(colors = appBackgroundGradient())
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Profiles",
                    style = MaterialTheme.typography.titleLarge,
                    color = titleColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            FilledTonalButton(
                onClick = {
                    importProfilesLauncher.launch(
                        arrayOf(
                            PROFILE_STORE_DEVICE_TRANSFER_MIME_TYPE,
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },
                enabled = !importProfilesInProgress && !exportProfilesInProgress,
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("Import profiles")
            }
            TextButton(
                onClick = {
                    showingExportProfiles = true
                },
                enabled = !importProfilesInProgress && !exportProfilesInProgress && orcaExportOptions.isNotEmpty()
            ) {
                Text("Export")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProfileTabStrip(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            ActiveProfilesCard(store = store)

            ProfilesSelectedTabContent(
                selectedTab = ProfileTab.entries[selectedTab],
                store = store,
                context = context,
                bodyColor = bodyColor,
                updateStore = ::updateStore,
                onShowPrinterSelection = { showingPrinterSelection = true },
                onShowFilamentSelection = { showingFilamentSelection = true },
                onShowProcessSelection = { showingProcessSelection = true },
                onEditorRequest = { editorRequest = it },
                onDeleteRequest = { deleteRequest = it }
            )
        }
    }
}

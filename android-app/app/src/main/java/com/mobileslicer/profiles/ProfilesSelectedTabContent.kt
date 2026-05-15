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

@Composable
internal fun ProfilesSelectedTabContent(
    selectedTab: ProfileTab,
    store: ProfileStore,
    context: Context,
    bodyColor: Color,
    updateStore: ((ProfileStore) -> ProfileStore) -> Unit,
    onShowPrinterSelection: () -> Unit,
    onShowFilamentSelection: () -> Unit,
    onShowProcessSelection: () -> Unit,
    onEditorRequest: (ProfileEditorRequest) -> Unit,
    onDeleteRequest: (ProfileDeleteRequest) -> Unit,
    workspaceProcessMode: Boolean = false,
    workspaceProcess: ProcessProfile? = null,
    workspaceHasProcessOverrides: Boolean = false,
    objectProcessMode: Boolean = false,
    objectProcessLabel: String? = null,
    objectProcessScopeLabel: String = "Object",
    objectProcess: ProcessProfile? = null,
    objectHasProcessOverrides: Boolean = false,
    objectHasProcessState: Boolean = false,
    onWorkspaceProcessSelected: ((ProcessProfile) -> Unit)? = null,
    onWorkspaceProcessTransferred: ((ProcessProfile) -> Unit)? = null,
    onObjectProcessSelected: ((ProcessProfile) -> Unit)? = null,
    onObjectProcessReset: (() -> Unit)? = null
) {
    var pendingWorkspaceProcessSwitch by remember { mutableStateOf<ProcessProfile?>(null) }
    pendingWorkspaceProcessSwitch?.let { targetProcess ->
        AlertDialog(
            onDismissRequest = { pendingWorkspaceProcessSwitch = null },
            title = { Text("Unsaved process changes") },
            text = {
                Text("Transfer current edits to ${targetProcess.name}, or discard them?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onWorkspaceProcessTransferred?.invoke(targetProcess)
                        pendingWorkspaceProcessSwitch = null
                    }
                ) {
                    Text("Transfer")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { pendingWorkspaceProcessSwitch = null }) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            onWorkspaceProcessSelected?.invoke(targetProcess)
                            pendingWorkspaceProcessSwitch = null
                        }
                    ) {
                        Text("Discard")
                    }
                }
            }
        )
    }
    when (selectedTab) {
                ProfileTab.Printer -> DomainProfilesSection(
                    title = "Printer profiles",
                    subtitle = "",
                    selectLabel = "Select printer",
                    actionLabel = "New custom printer",
                    details = "",
                    onSelect = { onShowPrinterSelection() },
                    onAdd = {
                        val base = store.printers.firstOrNull { it.id == store.selectedPrinterId }
                            ?: ProfileStoreRepository.fallbackPrinterProfile()
                        onEditorRequest(
                            ProfileEditorRequest.Printer(
                            profile = base.copy(
                                id = ProfileStoreRepository.newCustomId(ProfileTab.Printer),
                                name = "Custom ${base.name}",
                                subtitle = "Custom printer profile",
	                                builtIn = false,
	                                profileSource = "custom",
	                                thumbnailAssetPath = "",
	                                orcaFamily = base.orcaFamily,
	                                orcaMachineModelPath = base.orcaMachineModelPath,
	                                orcaMachineModelJson = base.orcaMachineModelJson,
	                                orcaResolvedMachineJson = base.orcaResolvedMachineJson,
	                                orcaMachineOverridesJson = base.orcaMachineOverridesJson,
	                                orcaNozzleMachinePaths = base.orcaNozzleMachinePaths,
	                                orcaNozzleMachineJsons = base.orcaNozzleMachineJsons,
	                                orcaResolvedMachineJsons = base.orcaResolvedMachineJsons,
	                                orcaResolvedSourceChains = base.orcaResolvedSourceChains,
	                                availableNozzleDiameters = base.availableNozzleDiameters
	                            ),
                            isNew = true
                        )
                    )
                    }
                ) {
                    if (store.printers.isEmpty()) {
                        Text(
                            text = "No printers added yet. Select a printer preset or create a custom printer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = bodyColor
                        )
                    }
                    store.printers.forEach { profile ->
                        val thumbnailAssetPath = profile.thumbnailAssetPath.ifBlank {
                            findOrcaPrinterPresetForProfile(context, profile)?.coverAssetPath.orEmpty()
                        }
                        DomainProfileCard(
                            title = profile.name,
                            selected = profile.id == store.selectedPrinterId,
                            accentColor = PanelBlue,
                            builtIn = profile.builtIn,
                            thumbnailAssetPath = thumbnailAssetPath,
                            sourceLabel = "",
                            metadataPills = listOf("${formatNozzle(profile.nozzleDiameterMm)} mm nozzle"),
                            onUse = {
                                updateStore {
                                    it.withSelectedPrinterRestoringProcess(profile.id)
                                }
                            },
                            onEdit = { onEditorRequest(ProfileEditorRequest.Printer(profile, isNew = false)) },
                            onDuplicate = {
                                onEditorRequest(
                            ProfileEditorRequest.Printer(
                                    profile = profile.copy(
                                        id = ProfileStoreRepository.newCustomId(ProfileTab.Printer),
                                        name = "${profile.name} Copy",
                                        subtitle = "Custom printer profile",
                                        builtIn = false,
	                                        profileSource = "custom",
	                                        thumbnailAssetPath = profile.thumbnailAssetPath.ifBlank {
	                                            findOrcaPrinterPresetForProfile(context, profile)?.coverAssetPath.orEmpty()
	                                        },
	                                        orcaFamily = profile.orcaFamily,
	                                        orcaMachineModelPath = profile.orcaMachineModelPath,
	                                        orcaMachineModelJson = profile.orcaMachineModelJson,
	                                        orcaResolvedMachineJson = profile.orcaResolvedMachineJson,
	                                        orcaMachineOverridesJson = profile.orcaMachineOverridesJson,
	                                        orcaNozzleMachinePaths = profile.orcaNozzleMachinePaths,
	                                        orcaNozzleMachineJsons = profile.orcaNozzleMachineJsons,
	                                        orcaResolvedMachineJsons = profile.orcaResolvedMachineJsons,
	                                        orcaResolvedSourceChains = profile.orcaResolvedSourceChains,
	                                        availableNozzleDiameters = profile.availableNozzleDiameters
	                                    ),
                                    isNew = true
                                )
                            )
                            },
                            onDelete = if (profile.builtIn) null else { { onDeleteRequest(ProfileDeleteRequest(ProfileTab.Printer, profile.id, profile.name)) } }
                        )
                    }
                }

                ProfileTab.Filament -> DomainProfilesSection(
                    title = "Filament profiles",
                    subtitle = "",
                    selectLabel = "Select filament",
                    actionLabel = "New custom filament",
                    details = "",
                    onSelect = { onShowFilamentSelection() },
                    onAdd = {
                        val visibleFilaments = store.visibleFilamentsForSelectedPrinter()
                        val base = visibleFilaments.firstOrNull { it.id == store.selectedFilamentId }
                            ?: visibleFilaments.firstOrNull()
                            ?: ProfileStoreRepository.fallbackFilamentProfile()
                        onEditorRequest(
                            ProfileEditorRequest.Filament(
                            profile = base.copy(
                                id = ProfileStoreRepository.newCustomId(ProfileTab.Filament),
                                name = "Custom ${base.name}",
                                subtitle = "Custom filament profile",
	                                builtIn = false,
	                                printerProfileId = store.selectedPrinterId,
	                                profileSource = "custom",
	                                orcaFamily = base.orcaFamily,
	                                orcaFilamentPath = base.orcaFilamentPath,
	                                orcaRawFilamentJson = base.orcaRawFilamentJson,
	                                orcaResolvedFilamentJson = base.orcaResolvedFilamentJson,
	                                orcaFilamentOverridesJson = base.orcaFilamentOverridesJson,
	                                orcaResolvedSourceChain = base.orcaResolvedSourceChain
	                            ),
                            isNew = true
                        )
                    )
                    }
                ) {
                    val visibleFilaments = store.visibleFilamentsForSelectedPrinter()
                    if (visibleFilaments.isEmpty()) {
                        Text(
                            text = "No filaments added for this printer yet. Select a material preset or create a custom filament.",
                            style = MaterialTheme.typography.bodySmall,
                            color = bodyColor
                        )
                    }
                    visibleFilaments.forEach { profile ->
                        DomainProfileCard(
                            title = profile.name,
                            selected = profile.id == store.selectedFilamentId,
                            accentColor = PanelGreen,
                            builtIn = profile.builtIn,
                            sourceLabel = "",
                            onUse = { updateStore { it.copy(selectedFilamentId = profile.id) } },
                            onEdit = { onEditorRequest(ProfileEditorRequest.Filament(profile, isNew = false)) },
                            onDuplicate = {
                                onEditorRequest(
                            ProfileEditorRequest.Filament(
                                    profile = profile.copy(
                                        id = ProfileStoreRepository.newCustomId(ProfileTab.Filament),
                                        name = "${profile.name} Copy",
                                        subtitle = "Custom filament profile",
	                                        builtIn = false,
	                                        printerProfileId = store.selectedPrinterId,
	                                        profileSource = "custom",
	                                        orcaFamily = profile.orcaFamily,
	                                        orcaFilamentPath = profile.orcaFilamentPath,
	                                        orcaRawFilamentJson = profile.orcaRawFilamentJson,
	                                        orcaResolvedFilamentJson = profile.orcaResolvedFilamentJson,
	                                        orcaFilamentOverridesJson = profile.orcaFilamentOverridesJson,
	                                        orcaResolvedSourceChain = profile.orcaResolvedSourceChain
	                                    ),
                                    isNew = true
                                )
                            )
                            },
                            onDelete = if (profile.builtIn) null else { { onDeleteRequest(ProfileDeleteRequest(ProfileTab.Filament, profile.id, profile.name)) } }
                        )
                    }
                }

                ProfileTab.Process -> DomainProfilesSection(
                    title = "Process profiles",
                    subtitle = "",
                    selectLabel = "Select/import preset",
                    actionLabel = "Duplicate selected",
                    details = "",
                    onSelect = { onShowProcessSelection() },
                    onAdd = {
                        val base = store.visibleProcessesForSelectedPrinter().firstOrNull { it.id == store.selectedProcessId }
                            ?: store.visibleProcessesForSelectedPrinter().firstOrNull()
                        if (base == null) {
                            Toast.makeText(context, "Select or import a process preset before duplicating it.", Toast.LENGTH_SHORT).show()
                        } else {
                            onEditorRequest(
                            ProfileEditorRequest.Process(
                                profile = base.withValues(
                                    "id" to ProfileStoreRepository.newCustomId(ProfileTab.Process),
                                    "name" to "${base.name} Copy",
                                    "subtitle" to "Custom process profile",
	                                    "builtIn" to false,
	                                    "profileSource" to "custom",
	                                    "printerProfileId" to store.selectedPrinterId,
	                                    "printerVariantKey" to store.printers.firstOrNull { it.id == store.selectedPrinterId }?.printerVariant.orEmpty(),
	                                    "orcaFamily" to base.orcaFamily,
	                                    "orcaProcessPath" to base.orcaProcessPath,
	                                    "orcaRawProcessJson" to base.orcaRawProcessJson,
	                                    "orcaResolvedProcessJson" to base.orcaResolvedProcessJson,
	                                    "orcaProcessOverridesJson" to base.orcaProcessOverridesJson,
	                                    "orcaResolvedSourceChain" to base.orcaResolvedSourceChain
	                                ),
                                isNew = true
                            )
                        )
                        }
                    }
                ) {
                    if (objectProcessMode && !objectProcessLabel.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.52f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "$objectProcessScopeLabel process",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = objectProcessLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = appTitleColor(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(
                                    onClick = { onObjectProcessReset?.invoke() },
                                    enabled = objectHasProcessState && onObjectProcessReset != null
                                ) {
                                    Text("Reset ${objectProcessScopeLabel.lowercase()}")
                                }
                            }
                        }
                    }
                    val visibleProcesses = store.visibleProcessesForSelectedPrinter()
                    if (store.selectedPrinterId.isBlank()) {
                        Text(
                            text = "Select a printer to load its process presets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = bodyColor
                        )
                    } else if (visibleProcesses.isEmpty()) {
                        Text(
                            text = "No process presets are available for this printer nozzle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = bodyColor
                        )
                    }
                    visibleProcesses.forEach { profile ->
                        DomainProfileCard(
                            title = profile.name,
                            selected = if (objectProcessMode && objectProcess != null) {
                                profile.id == objectProcess.id
                            } else if (workspaceProcessMode && workspaceProcess != null) {
                                profile.id == workspaceProcess.id
                            } else {
                                profile.id == store.selectedProcessId
                            },
                            accentColor = PanelLavender,
                            builtIn = profile.builtIn,
                            sourceLabel = "",
                            useLabel = when {
                                objectProcessMode -> "Use as ${objectProcessScopeLabel.lowercase()} base"
                                workspaceProcessMode -> "Apply to plate"
                                else -> "Use"
                            },
                            activeLabel = when {
                                objectProcessMode -> if (objectHasProcessOverrides) {
                                    "$objectProcessScopeLabel modified"
                                } else {
                                    "$objectProcessScopeLabel base"
                                }
                                workspaceProcessMode -> "Applied to plate"
                                else -> "Active"
                            },
                            onUse = {
                                if (objectProcessMode) {
                                    onObjectProcessSelected?.invoke(profile)
                                } else if (workspaceProcessMode && onWorkspaceProcessSelected != null) {
                                    if (
                                        workspaceHasProcessOverrides &&
                                        onWorkspaceProcessTransferred != null &&
                                        workspaceProcess?.id != profile.id
                                    ) {
                                        pendingWorkspaceProcessSwitch = profile
                                    } else {
                                        onWorkspaceProcessSelected(profile)
                                    }
                                } else {
                                    updateStore { it.withSelectedProcessForCurrentPrinter(profile.id) }
                                }
                            },
                            onEdit = {
                                val editProfile = if (objectProcessMode && objectProcess?.id == profile.id) {
                                    objectProcess
                                } else if (workspaceProcessMode && workspaceProcess?.id == profile.id) {
                                    workspaceProcess
                                } else {
                                    profile
                                }
                                onEditorRequest(
                                    ProfileEditorRequest.Process(
                                        editProfile,
                                        isNew = false,
                                        workspaceProcessMode = workspaceProcessMode,
                                        objectProcessMode = objectProcessMode,
                                        objectLabel = objectProcessLabel,
                                        processScopeLabel = objectProcessScopeLabel
                                    )
                                )
                            },
                            onDuplicate = {
                                onEditorRequest(
                            ProfileEditorRequest.Process(
                                    profile = profile.withValues(
                                        "id" to ProfileStoreRepository.newCustomId(ProfileTab.Process),
                                        "name" to "${profile.name} Copy",
                                        "subtitle" to "Custom process profile",
	                                        "builtIn" to false,
	                                        "profileSource" to "custom",
	                                        "printerProfileId" to store.selectedPrinterId,
	                                        "printerVariantKey" to store.printers.firstOrNull { it.id == store.selectedPrinterId }?.printerVariant.orEmpty(),
	                                        "orcaFamily" to profile.orcaFamily,
	                                        "orcaProcessPath" to profile.orcaProcessPath,
	                                        "orcaRawProcessJson" to profile.orcaRawProcessJson,
	                                        "orcaResolvedProcessJson" to profile.orcaResolvedProcessJson,
	                                        "orcaProcessOverridesJson" to profile.orcaProcessOverridesJson,
	                                        "orcaResolvedSourceChain" to profile.orcaResolvedSourceChain
	                                    ),
                                    isNew = true
                                )
                            )
                            },
                            onDelete = if (profile.builtIn) null else { { onDeleteRequest(ProfileDeleteRequest(ProfileTab.Process, profile.id, profile.name)) } }
                        )
                    }
                }
            }
}

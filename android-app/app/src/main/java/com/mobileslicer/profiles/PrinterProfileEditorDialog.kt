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
import com.mobileslicer.printerconnection.PrinterConnectionChoice
import com.mobileslicer.printerconnection.PrinterConnectionChoicesResult
import com.mobileslicer.printerconnection.PrinterBrowseTargetType
import com.mobileslicer.printerconnection.PrinterConnectionField
import com.mobileslicer.printerconnection.connectionCapabilities
import com.mobileslicer.printerconnection.connectionFieldSpecs
import com.mobileslicer.printerconnection.SimplyPrintOAuthResult
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
internal fun PrinterProfileEditorDialog(
    initial: PrinterProfile,
    isNew: Boolean,
    showAdvancedProfileSettings: Boolean,
    onDismiss: () -> Unit,
    onTestConnection: suspend (PrinterProfile) -> String,
    onPrinterStatus: suspend (PrinterProfile) -> String,
    onDiscoverPrinterHosts: suspend () -> PrinterConnectionChoicesResult,
    onBrowseConnectionTargets: suspend (PrinterProfile) -> PrinterConnectionChoicesResult,
    onBrowseConnectionGroups: suspend (PrinterProfile) -> PrinterConnectionChoicesResult,
    onSimplyPrintLogin: suspend (PrinterProfile) -> SimplyPrintOAuthResult,
    onOpenPrinterUi: (PrinterProfile) -> Unit,
    onSave: (PrinterProfile) -> Unit
) {
    val draft = remember(initial.id) { PrinterProfileEditorDraft(initial) }
    val options = remember { PrinterProfileEditorOptions() }
    val coroutineScope = rememberCoroutineScope()
    var testingConnection by remember(initial.id) { mutableStateOf(false) }
    var browsingConnectionTargets by remember(initial.id) { mutableStateOf(false) }
    var simplyPrintLoginInProgress by remember(initial.id) { mutableStateOf(false) }
    var refreshingPrinterStatus by remember(initial.id) { mutableStateOf(false) }
    var autoRefreshPrinterStatus by remember(initial.id) { mutableStateOf(false) }
    var connectionStatusDialog by remember(initial.id) { mutableStateOf<String?>(null) }
    var connectionTargetDialogTitle by remember(initial.id) { mutableStateOf<String?>(null) }
    var connectionTargetChoices by remember(initial.id) { mutableStateOf<List<PrinterConnectionChoice>>(emptyList()) }
    var connectionTargetDestination by remember(initial.id) { mutableStateOf("port") }
    fun currentConnectionProfile(): PrinterProfile =
        initial.copy(
            name = draft.name.ifBlank { initial.name },
            printHostType = draft.printHostType,
            printerAgent = draft.printerAgent.trim(),
            printHost = draft.printHost.trim(),
            printHostWebUi = draft.printHostWebUi.trim(),
            printHostAuthorizationType = draft.printHostAuthorizationType,
            printHostApiKey = draft.printHostApiKey,
            printHostPort = draft.printHostPort.trim(),
            printHostGroup = draft.printHostGroup.trim(),
            printHostCaFile = draft.printHostCaFile.trim(),
            printHostUser = draft.printHostUser.trim(),
            printHostPassword = draft.printHostPassword,
            printHostSslIgnoreRevoke = draft.printHostSslIgnoreRevoke,
            bambuBedType = draft.bambuBedType.trim(),
            bambuUseAms = draft.bambuUseAms,
            bambuAmsMapping = draft.bambuAmsMapping.trim(),
            bambuNozzleMapping = draft.bambuNozzleMapping.trim(),
            bambuBedLeveling = draft.bambuBedLeveling,
            bambuFlowCalibration = draft.bambuFlowCalibration,
            bambuVibrationCalibration = draft.bambuVibrationCalibration,
            bambuTimelapse = draft.bambuTimelapse
        )

    ConnectionStatusDialog(
        status = connectionStatusDialog,
        refreshing = autoRefreshPrinterStatus && refreshingPrinterStatus,
        onDismiss = {
            autoRefreshPrinterStatus = false
            connectionStatusDialog = null
        }
    )
    ConnectionTargetPickerDialog(
        title = connectionTargetDialogTitle,
        choices = connectionTargetChoices,
        onSelect = { choice ->
            when (connectionTargetDestination) {
                "group" -> draft.printHostGroup = choice.value
                "host" -> {
                    draft.printHost = choice.value
                    when {
                        "Bambu" in choice.detail -> draft.printHostType = PrintHostType.BambuLan
                        "PrusaLink" in choice.detail -> draft.printHostType = PrintHostType.PrusaLink
                        "Moonraker" in choice.detail || "OctoPrint" in choice.detail -> draft.printHostType = PrintHostType.OctoPrint
                    }
                }
                else -> draft.printHostPort = choice.value
            }
            connectionTargetDialogTitle = null
            connectionTargetChoices = emptyList()
        },
        onDismiss = {
            connectionTargetDialogTitle = null
            connectionTargetChoices = emptyList()
        }
    )

    var selectedTab by remember(initial.id) { mutableStateOf(PrinterEditorTab.BasicInformation) }
    LaunchedEffect(autoRefreshPrinterStatus, draft.printHost, draft.printHostWebUi, draft.printHostType, draft.printHostAuthorizationType, draft.printHostApiKey, draft.printHostUser, draft.printHostPassword) {
        if (!autoRefreshPrinterStatus) return@LaunchedEffect
        while (autoRefreshPrinterStatus) {
            refreshingPrinterStatus = true
            connectionStatusDialog = onPrinterStatus(currentConnectionProfile())
            refreshingPrinterStatus = false
            delay(3_000)
        }
    }
    ProfileEditorScreenScaffold(
        title = null,
        subtitle = null,
        saveLabel = if (isNew) "Create" else "Save",
        onBack = onDismiss,
        onSave = {
            onSave(draft.toPrinterProfile(initial, isNew))
        },
        headerContent = {
            ProfileTextField(draft.name, { draft.name = it }, "Profile name")
        },
        topContent = {
            ProfileEditorTabRow(
                tabs = PrinterEditorTab.entries,
                selectedTab = selectedTab,
                labelFor = { it.label },
                onSelected = { selectedTab = it }
            )
        }
    ) {
        when (selectedTab) {
            PrinterEditorTab.BasicInformation -> ProfileEditorSection("Basic information", "Build volume, bed type, and motion basics for this printer.") {
                ProfileGroupHeader("Printable space")
                if (ProfileEditorSetting.PrinterBedDimensions.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.bedWidth, { draft.bedWidth = it }, "Bed width (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.bedDepth, { draft.bedDepth = it }, "Bed depth (mm)", KeyboardType.Decimal)
                }
                ProfileTextField(draft.maxHeight, { draft.maxHeight = it }, "Max height (mm)", KeyboardType.Decimal)
                ProfileDropdownField(
                    label = "Support multi bed types",
                    selectedLabel = if (draft.supportMultiBedTypes) "Enabled" else "Disabled",
                    options = options.boolEnabledDisabledOptions,
                    onSelected = { enabled ->
                        draft.supportMultiBedTypes = enabled
                        if (enabled && draft.defaultBedType == DefaultBedType.NotSet) {
                            draft.defaultBedType = draft.toPrinterProfile(initial, isNew).effectiveOrcaBedType()
                        }
                    }
                )
                if (draft.supportMultiBedTypes) {
                    ProfileDropdownField(
                        label = "Bed type",
                        selectedLabel = draft.defaultBedType.userVisibleLabel,
                        options = options.bedTypeOptions(supportMultiBedTypes = true),
                        onSelected = { draft.defaultBedType = it }
                    )
                }
                if (ProfileEditorSetting.PrinterPrintableSpaceAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.bedExcludeArea, { draft.bedExcludeArea = it }, "Excluded bed area")
                    ProfileTextField(draft.bestObjectPosition, { draft.bestObjectPosition = it }, "Best object position")
                    ProfileTextField(draft.zOffset, { draft.zOffset = it }, "Z offset (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.preferredOrientation, { draft.preferredOrientation = it }, "Preferred orientation (degrees)", KeyboardType.Decimal)
                }
                if (ProfileEditorSetting.PrinterBasicInformationAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Advanced")
                    ProfileDropdownField(
                        label = "G-code flavor",
                        selectedLabel = draft.gcodeFlavor.displayLabel,
                        options = options.gcodeFlavorOptions,
                        onSelected = { draft.gcodeFlavor = it }
                    )
                    ProfileDropdownField(
                        label = "Pellet-modded printer",
                        selectedLabel = if (draft.pelletModdedPrinter) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.pelletModdedPrinter = it }
                    )
                    ProfileDropdownField(
                        label = "Disable remaining-time updates",
                        selectedLabel = if (draft.disableM73) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.disableM73 = it }
                    )
                    ProfileTextField(draft.thumbnails, { draft.thumbnails = it }, "G-code thumbnails")
                    ProfileDropdownField(
                        label = "Use relative E distances",
                        selectedLabel = if (draft.useRelativeEDistances) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.useRelativeEDistances = it }
                    )
                    ProfileDropdownField(
                        label = "Use firmware retraction",
                        selectedLabel = if (draft.useFirmwareRetraction) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.useFirmwareRetraction = it }
                    )
                    ProfileTextField(draft.timeCost, { draft.timeCost = it }, "Time cost (money/h)", KeyboardType.Decimal)
                }
                if (ProfileEditorSetting.PrinterBasicInformationCoolingFan.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Cooling fan")
                    ProfileTextField(draft.fanSpeedupTime, { draft.fanSpeedupTime = it }, "Fan speed-up time (s)", KeyboardType.Decimal)
                    ProfileDropdownField(
                        label = "Only overhangs",
                        selectedLabel = if (draft.fanSpeedupOverhangsOnly) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.fanSpeedupOverhangsOnly = it }
                    )
                    ProfileTextField(draft.fanKickstartTime, { draft.fanKickstartTime = it }, "Fan kick-start time (s)", KeyboardType.Decimal)
                }
                if (ProfileEditorSetting.PrinterBasicInformationExtruderClearance.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Extruder clearance")
                    ProfileTextField(draft.extruderClearanceRadius, { draft.extruderClearanceRadius = it }, "Radius (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.extruderClearanceHeightToRod, { draft.extruderClearanceHeightToRod = it }, "Height to rod (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.extruderClearanceHeightToLid, { draft.extruderClearanceHeightToLid = it }, "Height to lid (mm)", KeyboardType.Decimal)
                }
                if (ProfileEditorSetting.PrinterAdaptiveBedMeshAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Adaptive bed mesh")
                    ProfileTextField(draft.bedMeshMin, { draft.bedMeshMin = it }, "Bed mesh min")
                    ProfileTextField(draft.bedMeshMax, { draft.bedMeshMax = it }, "Bed mesh max")
                    ProfileTextField(draft.bedMeshProbeDistance, { draft.bedMeshProbeDistance = it }, "Probe point distance")
                    ProfileTextField(draft.adaptiveBedMeshMargin, { draft.adaptiveBedMeshMargin = it }, "Mesh margin (mm)", KeyboardType.Decimal)
                }
                if (ProfileEditorSetting.PrinterBasicInformationAccessory.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Accessory")
                    ProfileDropdownField(
                        label = "Nozzle type",
                        selectedLabel = draft.nozzleType.displayLabel,
                        options = options.nozzleTypeOptions,
                        onSelected = { draft.nozzleType = it }
                    )
                    ProfileDropdownField(
                        label = "Auxiliary part cooling fan",
                        selectedLabel = if (draft.auxiliaryFan) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.auxiliaryFan = it }
                    )
                }
            }
            PrinterEditorTab.Connection -> ProfileEditorSection("Connection", "Physical printer and network print-host controls.") {
                if (ProfileEditorSetting.PrinterPhysicalPrintHost.isVisible(showAdvancedProfileSettings)) {
                    Text(
                        text = "Print host",
                        style = MaterialTheme.typography.labelMedium,
                        color = appTitleColor(),
                        fontWeight = FontWeight.SemiBold
                    )
                    ProfileDropdownField(
                        label = "Printer service",
                        selectedLabel = draft.printHostType.displayLabel,
                        options = options.printHostTypeOptions,
                        onSelected = { draft.printHostType = it }
                    )
                    val connectionCapabilities = draft.printHostType.connectionCapabilities()
                    val connectionFieldSpecs = draft.printHostType.connectionFieldSpecs().associateBy { it.field }
                    fun connectionField(field: PrinterConnectionField) = connectionFieldSpecs.getValue(field)
                    fun connectionFieldVisible(field: PrinterConnectionField) = connectionField(field).visible
                    if (connectionFieldVisible(PrinterConnectionField.PrinterAgent)) {
                        ProfileTextField(draft.printerAgent, { draft.printerAgent = it }, connectionField(PrinterConnectionField.PrinterAgent).label)
                    }
                    ProfileTextField(
                        draft.printHost,
                        { draft.printHost = it },
                        connectionField(PrinterConnectionField.Host).label
                    )
                    Button(
                        onClick = {
                            autoRefreshPrinterStatus = false
                            browsingConnectionTargets = true
                            coroutineScope.launch {
                                val result = onDiscoverPrinterHosts()
                                if (result.success && result.choices.isNotEmpty()) {
                                    connectionTargetDestination = "host"
                                    connectionTargetDialogTitle = result.title
                                    connectionTargetChoices = result.choices.map { choice ->
                                        choice.copy(targetType = PrinterBrowseTargetType.Host)
                                    }
                                } else {
                                    connectionStatusDialog = result.userMessage()
                                }
                                browsingConnectionTargets = false
                            }
                        },
                        enabled = !browsingConnectionTargets,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                            Text(if (browsingConnectionTargets) "Searching..." else "Discover printers")
                    }
                    if (connectionFieldVisible(PrinterConnectionField.WebUi)) {
                        ProfileTextField(draft.printHostWebUi, { draft.printHostWebUi = it }, connectionField(PrinterConnectionField.WebUi).label)
                    }
                    if (connectionFieldVisible(PrinterConnectionField.Authorization)) {
                        ProfileDropdownField(
                            label = connectionField(PrinterConnectionField.Authorization).label,
                            selectedLabel = draft.printHostAuthorizationType.displayLabel,
                            options = options.printHostAuthorizationOptions,
                            onSelected = { draft.printHostAuthorizationType = it }
                        )
                    }
                    if (connectionFieldVisible(PrinterConnectionField.ApiKey)) {
                        ProfileTextField(draft.printHostApiKey, { draft.printHostApiKey = it }, connectionField(PrinterConnectionField.ApiKey).label)
                    }
                    if (connectionFieldVisible(PrinterConnectionField.PathOrPort)) {
                        ProfileTextField(draft.printHostPort, { draft.printHostPort = it }, connectionField(PrinterConnectionField.PathOrPort).label)
                    }
                    val connectionPickerLabel = connectionCapabilities.browseTargetsLabel
                    if (connectionPickerLabel != null) {
                        Button(
                            onClick = {
                                autoRefreshPrinterStatus = false
                                browsingConnectionTargets = true
                                coroutineScope.launch {
                                    val result = onBrowseConnectionTargets(currentConnectionProfile())
                                    if (result.success && result.choices.isNotEmpty()) {
                                        connectionTargetDestination = "port"
                                        connectionTargetDialogTitle = result.title
                                        connectionTargetChoices = result.choices
                                    } else {
                                        connectionStatusDialog = result.userMessage()
                                    }
                                    browsingConnectionTargets = false
                                }
                            },
                            enabled = !browsingConnectionTargets,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (browsingConnectionTargets) "Loading..." else connectionPickerLabel)
                        }
                    }
                    if (connectionFieldVisible(PrinterConnectionField.Group)) {
                        ProfileTextField(draft.printHostGroup, { draft.printHostGroup = it }, connectionField(PrinterConnectionField.Group).label)
                        Button(
                            onClick = {
                                autoRefreshPrinterStatus = false
                                browsingConnectionTargets = true
                                coroutineScope.launch {
                                    val result = onBrowseConnectionGroups(currentConnectionProfile())
                                    if (result.success && result.choices.isNotEmpty()) {
                                        connectionTargetDestination = "group"
                                        connectionTargetDialogTitle = result.title
                                        connectionTargetChoices = result.choices.map { choice ->
                                            choice.copy(detail = choice.detail.ifBlank { "Repetier model group" })
                                        }
                                    } else {
                                        connectionStatusDialog = result.userMessage()
                                    }
                                    browsingConnectionTargets = false
                                }
                            },
                            enabled = !browsingConnectionTargets,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (browsingConnectionTargets) "Loading..." else connectionCapabilities.browseGroupsLabel.orEmpty().ifBlank { "Browse groups" })
                        }
                    }
                    if (connectionFieldVisible(PrinterConnectionField.CaFile)) {
                        ProfileTextField(draft.printHostCaFile, { draft.printHostCaFile = it }, connectionField(PrinterConnectionField.CaFile).label)
                    }
                    if (connectionFieldVisible(PrinterConnectionField.User)) {
                        ProfileTextField(draft.printHostUser, { draft.printHostUser = it }, connectionField(PrinterConnectionField.User).label)
                    }
                    if (connectionFieldVisible(PrinterConnectionField.Password)) {
                        ProfileTextField(draft.printHostPassword, { draft.printHostPassword = it }, connectionField(PrinterConnectionField.Password).label)
                    }
                    if (connectionFieldVisible(PrinterConnectionField.SslRevoke)) {
                        ProfileDropdownField(
                            label = connectionField(PrinterConnectionField.SslRevoke).label,
                            selectedLabel = if (draft.printHostSslIgnoreRevoke) "Enabled" else "Disabled",
                            options = options.boolEnabledDisabledOptions,
                            onSelected = { draft.printHostSslIgnoreRevoke = it }
                        )
                    }
                    if (connectionFieldVisible(PrinterConnectionField.BambuBedType)) {
                        ProfileTextField(draft.bambuBedType, { draft.bambuBedType = it }, connectionField(PrinterConnectionField.BambuBedType).label)
                        ProfileDropdownField(
                            label = connectionField(PrinterConnectionField.BambuUseAms).label,
                            selectedLabel = if (draft.bambuUseAms) "Enabled" else "Disabled",
                            options = options.boolEnabledDisabledOptions,
                            onSelected = { draft.bambuUseAms = it }
                        )
                        ProfileTextField(draft.bambuAmsMapping, { draft.bambuAmsMapping = it }, connectionField(PrinterConnectionField.BambuAmsMapping).label)
                        ProfileTextField(draft.bambuNozzleMapping, { draft.bambuNozzleMapping = it }, connectionField(PrinterConnectionField.BambuNozzleMapping).label)
                        ProfileDropdownField(
                            label = connectionField(PrinterConnectionField.BambuBedLeveling).label,
                            selectedLabel = if (draft.bambuBedLeveling) "Enabled" else "Disabled",
                            options = options.boolEnabledDisabledOptions,
                            onSelected = { draft.bambuBedLeveling = it }
                        )
                        ProfileDropdownField(
                            label = connectionField(PrinterConnectionField.BambuFlowCalibration).label,
                            selectedLabel = if (draft.bambuFlowCalibration) "Enabled" else "Disabled",
                            options = options.boolEnabledDisabledOptions,
                            onSelected = { draft.bambuFlowCalibration = it }
                        )
                        ProfileDropdownField(
                            label = connectionField(PrinterConnectionField.BambuVibrationCalibration).label,
                            selectedLabel = if (draft.bambuVibrationCalibration) "Enabled" else "Disabled",
                            options = options.boolEnabledDisabledOptions,
                            onSelected = { draft.bambuVibrationCalibration = it }
                        )
                        ProfileDropdownField(
                            label = connectionField(PrinterConnectionField.BambuTimelapse).label,
                            selectedLabel = if (draft.bambuTimelapse) "Enabled" else "Disabled",
                            options = options.boolEnabledDisabledOptions,
                            onSelected = { draft.bambuTimelapse = it }
                        )
                    }
                    if (draft.printHostType == PrintHostType.SimplyPrint) {
                        Button(
                            onClick = {
                                autoRefreshPrinterStatus = false
                                simplyPrintLoginInProgress = true
                                coroutineScope.launch {
                                    val result = onSimplyPrintLogin(currentConnectionProfile())
                                    if (result.success) {
                                        draft.printHostApiKey = result.accessToken
                                        draft.printHostPassword = result.refreshToken
                                        draft.printHost = "https://simplyprint.io/panel"
                                        draft.printHostWebUi = "https://simplyprint.io/panel"
                                    }
                                    connectionStatusDialog = result.message
                                    simplyPrintLoginInProgress = false
                                }
                            },
                            enabled = !simplyPrintLoginInProgress,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (simplyPrintLoginInProgress) "Logging in..." else "Log in to SimplyPrint")
                        }
                    }
                    Button(
                        onClick = {
                            autoRefreshPrinterStatus = false
                            if (
                                draft.printHost.isBlank() &&
                                draft.printHostType != PrintHostType.PrusaConnect &&
                                draft.printHostType != PrintHostType.Obico &&
                                draft.printHostType != PrintHostType.SimplyPrint
                            ) {
                                connectionStatusDialog = "Set up a printer connection in Profiles > Printer > Connection first."
                                return@Button
                            }
                            testingConnection = true
                            coroutineScope.launch {
                                connectionStatusDialog = onTestConnection(currentConnectionProfile())
                                testingConnection = false
                            }
                        },
                        enabled = !testingConnection,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(if (testingConnection) "Testing..." else "Test connection")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (
                                    draft.printHost.isBlank() &&
                                    draft.printHostType != PrintHostType.PrusaConnect &&
                                    draft.printHostType != PrintHostType.Obico &&
                                    draft.printHostType != PrintHostType.SimplyPrint
                                ) {
                                    connectionStatusDialog = "Set up a printer connection in Profiles > Printer > Connection first."
                                    return@Button
                                }
                                connectionStatusDialog = "Refreshing printer status..."
                                autoRefreshPrinterStatus = true
                            },
                            enabled = !refreshingPrinterStatus,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (refreshingPrinterStatus) "Refreshing..." else "Refresh status")
                        }
                        Button(
                            onClick = { onOpenPrinterUi(currentConnectionProfile()) },
                            enabled = draft.printHost.isNotBlank() ||
                                draft.printHostWebUi.isNotBlank() ||
                                draft.printHostType == PrintHostType.SimplyPrint,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Open printer UI")
                        }
                    }
                }
            }
            PrinterEditorTab.Extruder -> ProfileEditorSection("Extruder", "Nozzle, retraction, and tool-change settings for this printer.") {
                ProfileGroupHeader("Basic information")
                if (ProfileEditorSetting.PrinterNozzleDiameter.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.nozzleDiameter, { draft.nozzleDiameter = it }, "Nozzle diameter (mm)", KeyboardType.Decimal)
                }
                if (ProfileEditorSetting.PrinterExtruderBasicInformationAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.nozzleVolume, { draft.nozzleVolume = it }, "Nozzle volume (mm3)", KeyboardType.Decimal)
                    ProfileGroupHeader("Layer height limits")
                    ProfileTextField(draft.minLayerHeight, { draft.minLayerHeight = it }, "Min layer height (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.maxLayerHeight, { draft.maxLayerHeight = it }, "Max layer height (mm)", KeyboardType.Decimal)
                    ProfileGroupHeader("Position")
                    ProfileTextField(draft.extruderOffset, { draft.extruderOffset = it }, "Extruder offset")
                }
                ProfileGroupHeader("Retraction")
                ProfileTextField(draft.retractionLength, { draft.retractionLength = it }, "Retraction length (mm)", KeyboardType.Decimal)
                if (ProfileEditorSetting.PrinterExtruderRetractionAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.retractRestartExtra, { draft.retractRestartExtra = it }, "Extra length on restart (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.retractionSpeed, { draft.retractionSpeed = it }, "Retraction speed (mm/s)", KeyboardType.Decimal)
                    ProfileTextField(draft.deretractionSpeed, { draft.deretractionSpeed = it }, "Deretraction speed (mm/s)", KeyboardType.Decimal)
                    ProfileTextField(draft.retractionMinimumTravel, { draft.retractionMinimumTravel = it }, "Travel distance threshold (mm)", KeyboardType.Decimal)
                    ProfileDropdownField(
                        label = "Retract on layer change",
                        selectedLabel = if (draft.retractWhenChangingLayer) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.retractWhenChangingLayer = it }
                    )
                    ProfileDropdownField(
                        label = "Wipe while retracting",
                        selectedLabel = if (draft.wipe) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.wipe = it }
                    )
                    ProfileTextField(draft.wipeDistance, { draft.wipeDistance = it }, "Wipe distance (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.retractBeforeWipe, { draft.retractBeforeWipe = it }, "Retract amount before wipe (%)", KeyboardType.Number)
                }
                ProfileGroupHeader("Z-hop")
                if (ProfileEditorSetting.PrinterExtruderRetractionAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileDropdownField(
                        label = "On surfaces",
                        selectedLabel = draft.retractLiftEnforce.displayLabel,
                        options = options.retractLiftEnforceOptions,
                        onSelected = { draft.retractLiftEnforce = it }
                    )
                    ProfileDropdownField(
                        label = "Z-hop type",
                        selectedLabel = draft.zHopType.displayLabel,
                        options = options.zHopTypeOptions,
                        onSelected = { draft.zHopType = it }
                    )
                    ProfileTextField(draft.zHop, { draft.zHop = it }, "Z-hop height (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.travelSlope, { draft.travelSlope = it }, "Traveling angle (degrees)", KeyboardType.Decimal)
                    ProfileTextField(draft.retractLiftAbove, { draft.retractLiftAbove = it }, "Only lift Z above (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.retractLiftBelow, { draft.retractLiftBelow = it }, "Only lift Z below (mm)", KeyboardType.Decimal)
                    ProfileGroupHeader("Retraction when switching material")
                    ProfileTextField(draft.retractLengthToolchange, { draft.retractLengthToolchange = it }, "Toolchange retraction length (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.retractRestartExtraToolchange, { draft.retractRestartExtraToolchange = it }, "Toolchange extra length on restart (mm)", KeyboardType.Decimal)
                }
            }
            PrinterEditorTab.MachineGcode -> ProfileEditorSection("Machine G-code", "Custom G-code blocks used before, during, and after a print.") {
                if (ProfileEditorSetting.PrinterMachineGcode.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("File header")
                    ProfileMultilineTextField(draft.fileStartGcode, { draft.fileStartGcode = it }, "File header G-code")
                    ProfileGroupHeader("Machine start G-code")
                    ProfileMultilineTextField(draft.machineStartGcode, { draft.machineStartGcode = it }, "Machine start G-code")
                    ProfileGroupHeader("Machine end G-code")
                    ProfileMultilineTextField(draft.machineEndGcode, { draft.machineEndGcode = it }, "Machine end G-code")
                    ProfileGroupHeader("Printing by object G-code")
                    ProfileMultilineTextField(draft.printingByObjectGcode, { draft.printingByObjectGcode = it }, "Printing by object G-code")
                    ProfileGroupHeader("Before layer change G-code")
                    ProfileMultilineTextField(draft.beforeLayerChangeGcode, { draft.beforeLayerChangeGcode = it }, "Before layer change G-code")
                    ProfileGroupHeader("Layer change G-code")
                    ProfileMultilineTextField(draft.layerChangeGcode, { draft.layerChangeGcode = it }, "Layer change G-code")
                    ProfileGroupHeader("Timelapse G-code")
                    ProfileMultilineTextField(draft.timeLapseGcode, { draft.timeLapseGcode = it }, "Timelapse G-code")
                    ProfileGroupHeader("Clumping detection G-code")
                    ProfileMultilineTextField(draft.wrappingDetectionGcode, { draft.wrappingDetectionGcode = it }, "Clumping detection G-code")
                    ProfileGroupHeader("Change filament G-code")
                    ProfileMultilineTextField(draft.changeFilamentGcode, { draft.changeFilamentGcode = it }, "Change filament G-code")
                    ProfileGroupHeader("Change extrusion role G-code")
                    ProfileMultilineTextField(draft.changeExtrusionRoleGcode, { draft.changeExtrusionRoleGcode = it }, "Change extrusion role G-code")
                    ProfileGroupHeader("Pause G-code")
                    ProfileMultilineTextField(draft.machinePauseGcode, { draft.machinePauseGcode = it }, "Pause G-code")
                    ProfileGroupHeader("Template custom G-code")
                    ProfileMultilineTextField(draft.templateCustomGcode, { draft.templateCustomGcode = it }, "Template custom G-code")
                } else {
                    Text(
                        text = "Enable advanced profile controls to edit machine G-code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appBodyColor()
                    )
                }
            }
            PrinterEditorTab.Multimaterial -> ProfileEditorSection("Multimaterial", "Filament change, wipe tower, and tool-change behavior.") {
                if (ProfileEditorSetting.PrinterMultimaterialAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Single extruder multi-material setup")
                    ProfileDropdownField(
                        label = "Single-extruder multimaterial",
                        selectedLabel = if (draft.singleExtruderMultiMaterial) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.singleExtruderMultiMaterial = it }
                    )
                    ProfileTextField(draft.extrudersCount, { draft.extrudersCount = it }, "Extruders")
                    ProfileDropdownField(
                        label = "Manual filament change",
                        selectedLabel = if (draft.manualFilamentChange) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.manualFilamentChange = it }
                    )
                    ProfileDropdownField(
                        label = "Bed temperature type",
                        selectedLabel = draft.bedTemperatureFormula.displayLabel,
                        options = options.bedTemperatureFormulaOptions,
                        onSelected = { draft.bedTemperatureFormula = it }
                    )
                    ProfileGroupHeader("Wipe tower")
                    ProfileDropdownField(
                        label = "Wipe tower type",
                        selectedLabel = draft.wipeTowerType.displayLabel,
                        options = options.wipeTowerTypeOptions,
                        onSelected = { draft.wipeTowerType = it }
                    )
                    ProfileDropdownField(
                        label = "Purge in prime tower",
                        selectedLabel = if (draft.purgeInPrimeTower) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.purgeInPrimeTower = it }
                    )
                    ProfileDropdownField(
                        label = "Enable filament ramming",
                        selectedLabel = if (draft.enableFilamentRamming) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.enableFilamentRamming = it }
                    )
                    ProfileGroupHeader("Single extruder multi-material parameters")
                    ProfileTextField(draft.coolingTubeRetraction, { draft.coolingTubeRetraction = it }, "Cooling tube position (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.coolingTubeLength, { draft.coolingTubeLength = it }, "Cooling tube length (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.parkingPositionRetraction, { draft.parkingPositionRetraction = it }, "Filament parking position (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.extraLoadingMove, { draft.extraLoadingMove = it }, "Extra loading distance (mm)", KeyboardType.Decimal)
                    ProfileDropdownField(
                        label = "High extruder current on filament swap",
                        selectedLabel = if (draft.highCurrentOnFilamentSwap) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.highCurrentOnFilamentSwap = it }
                    )
                    ProfileGroupHeader("Advanced")
                    ProfileTextField(draft.machineLoadFilamentTime, { draft.machineLoadFilamentTime = it }, "Filament load time (s)", KeyboardType.Decimal)
                    ProfileTextField(draft.machineUnloadFilamentTime, { draft.machineUnloadFilamentTime = it }, "Filament unload time (s)", KeyboardType.Decimal)
                    ProfileTextField(draft.machineToolChangeTime, { draft.machineToolChangeTime = it }, "Tool change time (s)", KeyboardType.Decimal)
                }
            }
            PrinterEditorTab.MotionAbility -> ProfileEditorSection("Motion ability", "Speed, acceleration, jerk, and resonance limits for this printer.") {
                if (ProfileEditorSetting.PrinterMotionAbilityAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Advanced")
                    ProfileDropdownField(
                        label = "Emit limits to G-code",
                        selectedLabel = if (draft.emitMachineLimitsToGcode) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.emitMachineLimitsToGcode = it }
                    )
                    ProfileGroupHeader("Resonance avoidance")
                    ProfileDropdownField(
                        label = "Resonance avoidance",
                        selectedLabel = if (draft.resonanceAvoidance) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.resonanceAvoidance = it }
                    )
                    ProfileTextField(draft.minResonanceAvoidanceSpeed, { draft.minResonanceAvoidanceSpeed = it }, "Resonance avoidance min speed (mm/s)", KeyboardType.Decimal)
                    ProfileTextField(draft.maxResonanceAvoidanceSpeed, { draft.maxResonanceAvoidanceSpeed = it }, "Resonance avoidance max speed (mm/s)", KeyboardType.Decimal)
                }
                ProfileGroupHeader("Speed limitation")
                ProfileTextField(draft.machineMaxSpeedX, { draft.machineMaxSpeedX = it }, "Maximum speed X (mm/s)", KeyboardType.Decimal)
                ProfileTextField(draft.machineMaxSpeedY, { draft.machineMaxSpeedY = it }, "Maximum speed Y (mm/s)", KeyboardType.Decimal)
                ProfileTextField(draft.machineMaxSpeedZ, { draft.machineMaxSpeedZ = it }, "Maximum speed Z (mm/s)", KeyboardType.Decimal)
                ProfileTextField(draft.machineMaxSpeedE, { draft.machineMaxSpeedE = it }, "Maximum speed E (mm/s)", KeyboardType.Decimal)
                ProfileGroupHeader("Acceleration limitation")
                ProfileTextField(draft.machineMaxAccelerationX, { draft.machineMaxAccelerationX = it }, "Maximum acceleration X (mm/s2)", KeyboardType.Decimal)
                ProfileTextField(draft.machineMaxAccelerationY, { draft.machineMaxAccelerationY = it }, "Maximum acceleration Y (mm/s2)", KeyboardType.Decimal)
                ProfileTextField(draft.machineMaxAccelerationZ, { draft.machineMaxAccelerationZ = it }, "Maximum acceleration Z (mm/s2)", KeyboardType.Decimal)
                ProfileTextField(draft.machineMaxAccelerationE, { draft.machineMaxAccelerationE = it }, "Maximum acceleration E (mm/s2)", KeyboardType.Decimal)
                ProfileTextField(draft.machineMaxAccelerationExtruding, { draft.machineMaxAccelerationExtruding = it }, "Maximum acceleration for extruding (mm/s2)", KeyboardType.Decimal)
                ProfileTextField(draft.machineMaxAccelerationRetracting, { draft.machineMaxAccelerationRetracting = it }, "Maximum acceleration for retracting (mm/s2)", KeyboardType.Decimal)
                if (ProfileEditorSetting.PrinterMotionAbilityAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.machineMaxAccelerationTravel, { draft.machineMaxAccelerationTravel = it }, "Maximum acceleration for travel (mm/s2)", KeyboardType.Decimal)
                }
                ProfileGroupHeader("Jerk limitation")
                if (ProfileEditorSetting.PrinterMotionAbilityAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.machineMaxJunctionDeviation, { draft.machineMaxJunctionDeviation = it }, "Maximum junction deviation (mm)", KeyboardType.Decimal)
                }
                ProfileTextField(draft.machineMaxJerkX, { draft.machineMaxJerkX = it }, "Maximum jerk X (mm/s)", KeyboardType.Decimal)
                ProfileTextField(draft.machineMaxJerkY, { draft.machineMaxJerkY = it }, "Maximum jerk Y (mm/s)", KeyboardType.Decimal)
                ProfileTextField(draft.machineMaxJerkZ, { draft.machineMaxJerkZ = it }, "Maximum jerk Z (mm/s)", KeyboardType.Decimal)
                ProfileTextField(draft.machineMaxJerkE, { draft.machineMaxJerkE = it }, "Maximum jerk E (mm/s)", KeyboardType.Decimal)
            }
            PrinterEditorTab.Notes -> ProfileEditorSection("Notes", "Optional notes stored with this printer profile.") {
                if (ProfileEditorSetting.PrinterNotes.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Notes")
                    ProfileMultilineTextField(draft.printerNotes, { draft.printerNotes = it }, "Printer notes")
                } else {
                    Text(
                        text = "Enable advanced profile controls to edit printer notes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appBodyColor()
                    )
                }
            }
        }
    }
}

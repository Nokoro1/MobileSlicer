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
internal fun FilamentProfileEditorDialog(
    initial: FilamentProfile,
    isNew: Boolean,
    showAdvancedProfileSettings: Boolean,
    availablePrinterProfileNames: List<String>,
    availableProcessProfileNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (FilamentProfile) -> Unit
) {
    val draft = remember(initial.id) { FilamentProfileEditorDraft(initial) }
    val compatiblePrinterSelection = remember(draft.compatiblePrinters) { parseProfileSelection(draft.compatiblePrinters) }
    val compatiblePrintSelection = remember(draft.compatiblePrints) { parseProfileSelection(draft.compatiblePrints) }
    val compatiblePrinterOptions = remember(availablePrinterProfileNames, compatiblePrinterSelection) {
        (availablePrinterProfileNames + compatiblePrinterSelection).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    val compatiblePrintOptions = remember(availableProcessProfileNames, compatiblePrintSelection) {
        (availableProcessProfileNames + compatiblePrintSelection).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    val options = remember { FilamentProfileEditorOptions() }
    ProfileEditorScreenScaffold(
        title = null,
        subtitle = null,
        saveLabel = if (isNew) "Create" else "Save",
        onBack = onDismiss,
        onSave = {
            onSave(draft.toFilamentProfile(initial, isNew))
        },
        headerContent = {
            ProfileTextField(draft.name, { draft.name = it }, "Profile name")
        },
        topContent = {
            ProfileEditorTabRow(
                tabs = FilamentEditorTab.entries,
                selectedTab = draft.selectedTab,
                labelFor = { it.label },
                onSelected = { draft.selectedTab = it }
            )
        }
    ) {
        when (draft.selectedTab) {
            FilamentEditorTab.Filament -> ProfileEditorSection("Filament", "Material type, flow, pressure advance, temperature, and volumetric speed.") {
                ProfileGroupHeader("Basic information")
                ProfileDropdownField(
                    label = "Material type",
                    selectedLabel = draft.materialType,
                    options = options.materialTypeOptions,
                    onSelected = { draft.materialType = it }
                )
                if (ProfileEditorSetting.FilamentBasicInformationAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.filamentVendor, { draft.filamentVendor = it }, "Vendor")
                    ProfileDropdownField(
                        label = "Soluble material",
                        selectedLabel = if (draft.filamentSoluble) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.filamentSoluble = it }
                    )
                    ProfileDropdownField(
                        label = "Support material",
                        selectedLabel = if (draft.filamentSupportMaterial) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.filamentSupportMaterial = it }
                    )
                    ProfileTextField(draft.filamentChangeLength, { draft.filamentChangeLength = it }, "Filament ramming length (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.defaultFilamentColor, { draft.defaultFilamentColor = it }, "Default color")
                }
                ProfileTextField(draft.filamentDiameter, { draft.filamentDiameter = it }, "Diameter (mm)", KeyboardType.Decimal)
                if (ProfileEditorSetting.FilamentBasicInformationAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.filamentDensity, { draft.filamentDensity = it }, "Density (g/cm3)", KeyboardType.Decimal)
                    ProfileTextField(draft.shrinkageXy, { draft.shrinkageXy = it }, "Shrinkage (XY) (%)", KeyboardType.Decimal)
                    ProfileTextField(draft.shrinkageZ, { draft.shrinkageZ = it }, "Shrinkage (Z) (%)", KeyboardType.Decimal)
                    ProfileTextField(draft.filamentCost, { draft.filamentCost = it }, "Price (money/kg)", KeyboardType.Decimal)
                }
                if (ProfileEditorSetting.FilamentBasicInformationTemperatureCore.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.softeningTemperature, { draft.softeningTemperature = it }, "Softening temperature (C)", KeyboardType.Number)
                    ProfileTextField(draft.idleTemperature, { draft.idleTemperature = it }, "Idle temperature (C)", KeyboardType.Number)
                    ProfileTextField(draft.nozzleTemperatureRangeLow, { draft.nozzleTemperatureRangeLow = it }, "Recommended nozzle temperature min (C)", KeyboardType.Number)
                    ProfileTextField(draft.nozzleTemperatureRangeHigh, { draft.nozzleTemperatureRangeHigh = it }, "Recommended nozzle temperature max (C)", KeyboardType.Number)
                }
                if (ProfileEditorSetting.FilamentFlowRatio.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Flow ratio and pressure advance")
                    ProfileTextField(draft.flowRatio, { draft.flowRatio = it }, "Flow ratio", KeyboardType.Decimal)
                }
                if (ProfileEditorSetting.FilamentPressureAdvance.isVisible(showAdvancedProfileSettings)) {
                    ProfileDropdownField(
                        label = "Pressure advance",
                        selectedLabel = if (draft.pressureAdvanceEnabled) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.pressureAdvanceEnabled = it }
                    )
                    ProfileTextField(draft.pressureAdvance, { draft.pressureAdvance = it }, "Pressure advance value", KeyboardType.Decimal)
                    ProfileDropdownField(
                        label = "Adaptive pressure advance",
                        selectedLabel = if (draft.adaptivePressureAdvanceEnabled) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.adaptivePressureAdvanceEnabled = it }
                    )
                    ProfileDropdownField(
                        label = "Adaptive pressure advance for overhangs",
                        selectedLabel = if (draft.adaptivePressureAdvanceOverhangsEnabled) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.adaptivePressureAdvanceOverhangsEnabled = it }
                    )
                    ProfileTextField(draft.adaptivePressureAdvanceBridges, { draft.adaptivePressureAdvanceBridges = it }, "Pressure advance for bridges", KeyboardType.Decimal)
                    ProfileMultilineTextField(
                        value = draft.adaptivePressureAdvanceModel,
                        onValueChange = { draft.adaptivePressureAdvanceModel = it },
                        label = "Adaptive pressure advance measurements"
                    )
                }
                if (ProfileEditorSetting.FilamentBedTemperature.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Print chamber temperature")
                    ProfileTextField(draft.chamberTemperature, { draft.chamberTemperature = it }, "Chamber temperature (C)", KeyboardType.Number)
                    ProfileDropdownField(
                        label = "Activate temperature control",
                        selectedLabel = if (draft.activateChamberTemperatureControl) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.activateChamberTemperatureControl = it }
                    )
                }
                ProfileGroupHeader("Print temperature")
                ProfileTextField(draft.nozzleTempInitialLayer, { draft.nozzleTempInitialLayer = it }, "Nozzle first layer (C)", KeyboardType.Number)
                ProfileTextField(draft.nozzleTemp, { draft.nozzleTemp = it }, "Nozzle other layers (C)", KeyboardType.Number)
                if (ProfileEditorSetting.FilamentBedTemperature.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Bed temperature")
                    ProfileTextField(draft.supertackPlateTempInitialLayer, { draft.supertackPlateTempInitialLayer = it }, "Cool Plate (SuperTack) first layer (C)", KeyboardType.Number)
                    ProfileTextField(draft.supertackPlateTemp, { draft.supertackPlateTemp = it }, "Cool Plate (SuperTack) other layers (C)", KeyboardType.Number)
                    ProfileTextField(draft.coolPlateTempInitialLayer, { draft.coolPlateTempInitialLayer = it }, "Cool Plate first layer (C)", KeyboardType.Number)
                    ProfileTextField(draft.coolPlateTemp, { draft.coolPlateTemp = it }, "Cool Plate other layers (C)", KeyboardType.Number)
                    ProfileTextField(draft.texturedCoolPlateTempInitialLayer, { draft.texturedCoolPlateTempInitialLayer = it }, "Textured Cool Plate first layer (C)", KeyboardType.Number)
                    ProfileTextField(draft.texturedCoolPlateTemp, { draft.texturedCoolPlateTemp = it }, "Textured Cool Plate other layers (C)", KeyboardType.Number)
                    ProfileTextField(draft.engineeringPlateTempInitialLayer, { draft.engineeringPlateTempInitialLayer = it }, "Engineering Plate first layer (C)", KeyboardType.Number)
                    ProfileTextField(draft.engineeringPlateTemp, { draft.engineeringPlateTemp = it }, "Engineering Plate other layers (C)", KeyboardType.Number)
                    ProfileTextField(draft.bedTempInitialLayer, { draft.bedTempInitialLayer = it }, "Smooth PEI / High Temp Plate first layer (C)", KeyboardType.Number)
                    ProfileTextField(draft.bedTemp, { draft.bedTemp = it }, "Smooth PEI / High Temp Plate other layers (C)", KeyboardType.Number)
                    ProfileTextField(draft.texturedPlateTempInitialLayer, { draft.texturedPlateTempInitialLayer = it }, "Textured PEI Plate first layer (C)", KeyboardType.Number)
                    ProfileTextField(draft.texturedPlateTemp, { draft.texturedPlateTemp = it }, "Textured PEI Plate other layers (C)", KeyboardType.Number)
                }
                if (ProfileEditorSetting.FilamentMaxVolumetricSpeed.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Volumetric speed limitation")
                    ProfileTextField(draft.maxVolumetricSpeed, { draft.maxVolumetricSpeed = it }, "Max volumetric speed (mm3/s)", KeyboardType.Decimal)
                }
                if (ProfileEditorSetting.FilamentAdaptiveVolumetricSpeed.isVisible(showAdvancedProfileSettings)) {
                    ProfileDropdownField(
                        label = "Adaptive volumetric speed",
                        selectedLabel = if (draft.adaptiveVolumetricSpeedEnabled) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.adaptiveVolumetricSpeedEnabled = it }
                    )
                    ProfileTextField(draft.volumetricSpeedCoefficients, { draft.volumetricSpeedCoefficients = it }, "Volumetric speed coefficients")
                }
            }
            FilamentEditorTab.Cooling -> ProfileEditorSection("Cooling", "Fan behavior, layer cooling, overhang cooling, and ironing overrides.") {
                ProfileGroupHeader("Cooling for specific layer")
                ProfileTextField(draft.noCoolingFirstLayers, { draft.noCoolingFirstLayers = it }, "No cooling for first X layers", KeyboardType.Number)
                if (ProfileEditorSetting.FilamentCoolingSpecificLayerAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.fullFanSpeedLayer, { draft.fullFanSpeedLayer = it }, "Full fan speed at layer", KeyboardType.Number)
                }
                ProfileGroupHeader("Part cooling fan")
                if (ProfileEditorSetting.FilamentCoolingBaseline.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.minFanSpeed, { draft.minFanSpeed = it }, "Min fan speed (%)", KeyboardType.Number)
                    ProfileTextField(draft.coolingPercent, { draft.coolingPercent = it }, "Max fan speed (%)", KeyboardType.Number)
                }
                if (ProfileEditorSetting.FilamentPartCoolingFanCore.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.fanCoolingLayerTime, { draft.fanCoolingLayerTime = it }, "Min fan speed layer time (s)", KeyboardType.Decimal)
                    ProfileTextField(draft.slowDownLayerTime, { draft.slowDownLayerTime = it }, "Max fan speed layer time (s)", KeyboardType.Decimal)
                    ProfileDropdownField(
                        label = "Keep fan always on",
                        selectedLabel = if (draft.reduceFanStopStartFrequency) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.reduceFanStopStartFrequency = it }
                    )
                    ProfileDropdownField(
                        label = "Slow printing down for better layer cooling",
                        selectedLabel = if (draft.slowDownForLayerCooling) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.slowDownForLayerCooling = it }
                    )
                    ProfileDropdownField(
                        label = "Don't slow down outer walls",
                        selectedLabel = if (draft.filamentDontSlowDownOuterWall) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.filamentDontSlowDownOuterWall = it }
                    )
                    ProfileDropdownField(
                        label = "Force cooling for overhangs and bridges",
                        selectedLabel = if (draft.enableOverhangBridgeFan) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.enableOverhangBridgeFan = it }
                    )
                }
                if (ProfileEditorSetting.FilamentPartCoolingFanAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileTextField(draft.slowDownMinSpeed, { draft.slowDownMinSpeed = it }, "Min print speed (mm/s)", KeyboardType.Decimal)
                    ProfileDropdownField(
                        label = "Overhang cooling activation threshold",
                        selectedLabel = draft.overhangFanThreshold,
                        options = options.overhangFanThresholdOptions,
                        onSelected = { draft.overhangFanThreshold = it }
                    )
                    ProfileTextField(draft.overhangFanSpeed, { draft.overhangFanSpeed = it }, "Overhangs and external bridges fan speed (%)", KeyboardType.Number)
                    ProfileTextField(draft.filamentInternalBridgeFanSpeed, { draft.filamentInternalBridgeFanSpeed = it }, "Internal bridges fan speed (%)", KeyboardType.Number)
                    ProfileTextField(draft.filamentSupportInterfaceFanSpeed, { draft.filamentSupportInterfaceFanSpeed = it }, "Support interface fan speed (%)", KeyboardType.Number)
                    ProfileTextField(draft.ironingFanSpeed, { draft.ironingFanSpeed = it }, "Ironing fan speed (%)", KeyboardType.Number)
                }
                if (ProfileEditorSetting.FilamentAuxiliaryCoolingFan.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Auxiliary part cooling fan")
                    ProfileTextField(draft.additionalCoolingFanSpeed, { draft.additionalCoolingFanSpeed = it }, "Fan speed (%)", KeyboardType.Number)
                }
                if (ProfileEditorSetting.FilamentExhaustFan.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Exhaust fan")
                    ProfileDropdownField(
                        label = "Activate air filtration",
                        selectedLabel = if (draft.activateAirFiltration) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.activateAirFiltration = it }
                    )
                    ProfileTextField(draft.duringPrintExhaustFanSpeed, { draft.duringPrintExhaustFanSpeed = it }, "During print fan speed (%)", KeyboardType.Number)
                    ProfileTextField(draft.completePrintExhaustFanSpeed, { draft.completePrintExhaustFanSpeed = it }, "Complete print fan speed (%)", KeyboardType.Number)
                }
            }
            FilamentEditorTab.SettingOverrides -> ProfileEditorSection("Setting overrides", "Filament-side retraction overrides. Blank values inherit printer preset behavior.") {
                if (ProfileEditorSetting.FilamentRetractionOverrides.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Retraction")
                    ProfileTextField(draft.retractionLength, { draft.retractionLength = it }, "Retraction length (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.filamentZHop, { draft.filamentZHop = it }, "Z-hop height (mm)", KeyboardType.Decimal)
                    ProfileDropdownField(
                        label = "Z-hop type",
                        selectedLabel = draft.filamentZHopType?.displayLabel ?: "Inherit",
                        options = options.filamentZHopTypeOptions,
                        onSelected = { draft.filamentZHopType = it }
                    )
                    ProfileTextField(draft.filamentRetractLiftAbove, { draft.filamentRetractLiftAbove = it }, "Only lift Z above (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.filamentRetractLiftBelow, { draft.filamentRetractLiftBelow = it }, "Only lift Z below (mm)", KeyboardType.Decimal)
                    ProfileDropdownField(
                        label = "On surfaces",
                        selectedLabel = draft.filamentRetractLiftEnforce?.displayLabel ?: "Inherit",
                        options = options.filamentRetractLiftEnforceOptions,
                        onSelected = { draft.filamentRetractLiftEnforce = it }
                    )
                    ProfileTextField(draft.retractionSpeed, { draft.retractionSpeed = it }, "Retraction speed (mm/s)", KeyboardType.Decimal)
                    ProfileTextField(draft.deretractionSpeed, { draft.deretractionSpeed = it }, "Deretraction speed (mm/s)", KeyboardType.Decimal)
                    ProfileTextField(draft.filamentRetractRestartExtra, { draft.filamentRetractRestartExtra = it }, "Extra length on restart (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.filamentRetractionMinimumTravel, { draft.filamentRetractionMinimumTravel = it }, "Travel distance threshold (mm)", KeyboardType.Decimal)
                    ProfileDropdownField(
                        label = "Retract on layer change",
                        selectedLabel = when (draft.filamentRetractWhenChangingLayer) {
                            true -> "Enabled"
                            false -> "Disabled"
                            null -> "Inherit"
                        },
                        options = options.nullableBoolOptions,
                        onSelected = { draft.filamentRetractWhenChangingLayer = it }
                    )
                    ProfileDropdownField(
                        label = "Wipe while retracting",
                        selectedLabel = when (draft.filamentWipe) {
                            true -> "Enabled"
                            false -> "Disabled"
                            null -> "Inherit"
                        },
                        options = options.nullableBoolOptions,
                        onSelected = { draft.filamentWipe = it }
                    )
                    ProfileTextField(draft.filamentWipeDistance, { draft.filamentWipeDistance = it }, "Wipe distance (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.filamentRetractBeforeWipe, { draft.filamentRetractBeforeWipe = it }, "Retract amount before wipe (%)", KeyboardType.Number)
                    ProfileDropdownField(
                        label = "Long retraction when cut",
                        selectedLabel = when (draft.filamentLongRetractionsWhenCut) {
                            true -> "Enabled"
                            false -> "Disabled"
                            null -> "Inherit"
                        },
                        options = options.nullableBoolOptions,
                        onSelected = { draft.filamentLongRetractionsWhenCut = it }
                    )
                    ProfileTextField(draft.filamentRetractionDistancesWhenCut, { draft.filamentRetractionDistancesWhenCut = it }, "Retraction distances when cut")
                }
                if (ProfileEditorSetting.FilamentIroningOverrides.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Ironing")
                    ProfileTextField(draft.filamentIroningFlow, { draft.filamentIroningFlow = it }, "Ironing flow (%)", KeyboardType.Decimal)
                    ProfileTextField(draft.filamentIroningSpacing, { draft.filamentIroningSpacing = it }, "Ironing spacing (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.filamentIroningInset, { draft.filamentIroningInset = it }, "Ironing inset (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.filamentIroningSpeed, { draft.filamentIroningSpeed = it }, "Ironing speed (mm/s)", KeyboardType.Decimal)
                }
            }
            FilamentEditorTab.Advanced -> ProfileEditorSection("Advanced", "Filament G-code and advanced material behavior.") {
                if (ProfileEditorSetting.FilamentGcodeAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Filament start G-code")
                    ProfileMultilineTextField(
                        value = draft.filamentStartGcode,
                        onValueChange = { draft.filamentStartGcode = it },
                        label = "Filament start G-code"
                    )
                    ProfileGroupHeader("Filament end G-code")
                    ProfileMultilineTextField(
                        value = draft.filamentEndGcode,
                        onValueChange = { draft.filamentEndGcode = it },
                        label = "Filament end G-code"
                    )
                }
            }
            FilamentEditorTab.Multimaterial -> ProfileEditorSection("Multimaterial", "Purge, ramming, and filament-change settings for multi-material prints.") {
                if (ProfileEditorSetting.FilamentMultimaterialAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Wipe tower parameters")
                    ProfileTextField(draft.minimalPurgeOnWipeTower, { draft.minimalPurgeOnWipeTower = it }, "Minimal purge on wipe tower (mm3)", KeyboardType.Decimal)
                    ProfileTextField(draft.towerInterfacePreExtrusionDistance, { draft.towerInterfacePreExtrusionDistance = it }, "Interface layer pre-extrusion distance (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.towerInterfacePreExtrusionLength, { draft.towerInterfacePreExtrusionLength = it }, "Interface layer pre-extrusion length (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.towerIroningArea, { draft.towerIroningArea = it }, "Tower ironing area (mm2)", KeyboardType.Decimal)
                    ProfileTextField(draft.towerInterfacePurgeVolume, { draft.towerInterfacePurgeVolume = it }, "Interface layer purge length (mm)", KeyboardType.Decimal)
                    ProfileTextField(draft.towerInterfacePrintTemperature, { draft.towerInterfacePrintTemperature = it }, "Interface layer print temperature (C)", KeyboardType.Number)

                    ProfileGroupHeader("Multi-filament")
                    ProfileDropdownField(
                        label = "Long retraction when extruder change",
                        selectedLabel = when (draft.longRetractionsWhenExtruderChange) {
                            true -> "Enabled"
                            false -> "Disabled"
                            null -> "Inherit"
                        },
                        options = options.nullableBoolOptions,
                        onSelected = { draft.longRetractionsWhenExtruderChange = it }
                    )
                    ProfileTextField(draft.retractionDistanceWhenExtruderChange, { draft.retractionDistanceWhenExtruderChange = it }, "Retraction distance when extruder change (mm)", KeyboardType.Decimal)

                    ProfileGroupHeader("Tool change parameters for single-extruder multimaterial printers")
                    ProfileTextField(draft.loadingSpeedStart, { draft.loadingSpeedStart = it }, "Loading speed at the start (mm/s)", KeyboardType.Decimal)
                    ProfileTextField(draft.loadingSpeed, { draft.loadingSpeed = it }, "Loading speed (mm/s)", KeyboardType.Decimal)
                    ProfileTextField(draft.unloadingSpeedStart, { draft.unloadingSpeedStart = it }, "Unloading speed at the start (mm/s)", KeyboardType.Decimal)
                    ProfileTextField(draft.unloadingSpeed, { draft.unloadingSpeed = it }, "Unloading speed (mm/s)", KeyboardType.Decimal)
                    ProfileTextField(draft.toolchangeDelay, { draft.toolchangeDelay = it }, "Delay after unloading (s)", KeyboardType.Decimal)
                    ProfileTextField(draft.coolingMoves, { draft.coolingMoves = it }, "Number of cooling moves", KeyboardType.Number)
                    ProfileTextField(draft.coolingInitialSpeed, { draft.coolingInitialSpeed = it }, "Speed of the first cooling move (mm/s)", KeyboardType.Decimal)
                    ProfileTextField(draft.coolingFinalSpeed, { draft.coolingFinalSpeed = it }, "Speed of the last cooling move (mm/s)", KeyboardType.Decimal)
                    ProfileTextField(draft.stampingLoadingSpeed, { draft.stampingLoadingSpeed = it }, "Stamping loading speed (mm/s)", KeyboardType.Decimal)
                    ProfileTextField(draft.stampingDistance, { draft.stampingDistance = it }, "Stamping distance (mm)", KeyboardType.Decimal)
                    ProfileMultilineTextField(draft.rammingParameters, { draft.rammingParameters = it }, "Ramming parameters")

                    ProfileGroupHeader("Tool change parameters for multi-extruder printers")
                    ProfileDropdownField(
                        label = "Enable ramming for multi-tool setups",
                        selectedLabel = if (draft.multitoolRamming) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { draft.multitoolRamming = it }
                    )
                    ProfileTextField(draft.multitoolRammingVolume, { draft.multitoolRammingVolume = it }, "Multi-tool ramming volume (mm3)", KeyboardType.Decimal)
                    ProfileTextField(draft.multitoolRammingFlow, { draft.multitoolRammingFlow = it }, "Multi-tool ramming flow (mm3/s)", KeyboardType.Decimal)
                } else {
                    Text(
                        text = "Enable advanced profile controls to edit filament multimaterial settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appBodyColor()
                    )
                }
            }
            FilamentEditorTab.Dependencies -> ProfileEditorSection("Dependencies", "Limit this filament to compatible printers and process profiles.") {
                if (ProfileEditorSetting.FilamentDependenciesAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Compatible printers")
                    ProfileDropdownField(
                        label = "All printers",
                        selectedLabel = if (draft.compatiblePrinters.isBlank()) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { enabled -> draft.compatiblePrinters = if (enabled) "" else encodeProfileSelection(compatiblePrinterOptions.take(1)) }
                    )
                    if (draft.compatiblePrinters.isNotBlank()) {
                        compatiblePrinterOptions.forEach { printerName ->
                            val selected = printerName in compatiblePrinterSelection
                            ProfileDropdownField(
                                label = printerName,
                                selectedLabel = if (selected) "Enabled" else "Disabled",
                                options = options.boolEnabledDisabledOptions,
                                onSelected = { enabled ->
                                    draft.compatiblePrinters = encodeProfileSelection(compatiblePrinterSelection.toggleSelection(printerName, enabled))
                                }
                            )
                        }
                    }
                    ProfileMultilineTextField(draft.compatiblePrintersCondition, { draft.compatiblePrintersCondition = it }, "Condition")
                    ProfileGroupHeader("Compatible process profiles")
                    ProfileDropdownField(
                        label = "All process profiles",
                        selectedLabel = if (draft.compatiblePrints.isBlank()) "Enabled" else "Disabled",
                        options = options.boolEnabledDisabledOptions,
                        onSelected = { enabled -> draft.compatiblePrints = if (enabled) "" else encodeProfileSelection(compatiblePrintOptions.take(1)) }
                    )
                    if (draft.compatiblePrints.isNotBlank()) {
                        compatiblePrintOptions.forEach { processName ->
                            val selected = processName in compatiblePrintSelection
                            ProfileDropdownField(
                                label = processName,
                                selectedLabel = if (selected) "Enabled" else "Disabled",
                                options = options.boolEnabledDisabledOptions,
                                onSelected = { enabled ->
                                    draft.compatiblePrints = encodeProfileSelection(compatiblePrintSelection.toggleSelection(processName, enabled))
                                }
                            )
                        }
                    }
                    ProfileMultilineTextField(draft.compatiblePrintsCondition, { draft.compatiblePrintsCondition = it }, "Condition")
                } else {
                    Text(
                        text = "Enable advanced profile controls to edit filament compatibility settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appBodyColor()
                    )
                }
            }
            FilamentEditorTab.Notes -> ProfileEditorSection("Notes", "Optional notes stored with this filament profile.") {
                if (ProfileEditorSetting.FilamentNotesAdvanced.isVisible(showAdvancedProfileSettings)) {
                    ProfileGroupHeader("Notes")
                    ProfileMultilineTextField(
                        value = draft.filamentNotes,
                        onValueChange = { draft.filamentNotes = it },
                        label = "Filament notes"
                    )
                } else {
                    Text(
                        text = "Enable advanced profile controls to edit filament notes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appBodyColor()
                    )
                }
            }
        }
    }
}

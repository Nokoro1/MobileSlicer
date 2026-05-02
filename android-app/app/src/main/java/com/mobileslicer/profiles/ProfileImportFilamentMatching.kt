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

internal fun findInheritedOrcaFilamentBundle(context: Context, json: JSONObject): OrcaFilamentImportBundle? {
    val inherited = json.optString(NativeConfigKeys.Printer.Inherits).takeIf { it.isNotBlank() } ?: return null
    val inheritedKeys = listOf(
        inherited,
        inherited.substringBefore('@').trim()
    ).map { it.cleanProfileMatchKey() }.filter { it.isNotBlank() }
    val preset = loadOrcaFilamentPresets(context).firstOrNull { preset ->
        val keys = listOf(preset.rawName, preset.name).map { it.cleanProfileMatchKey() }
        inheritedKeys.any { inheritedKey -> inheritedKey in keys }
    } ?: return null
    return runCatching { loadOrcaFilamentImportBundle(context, preset) }.getOrNull()
}

internal fun findMobileSlicerFilamentBaseline(
    currentStore: ProfileStore,
    json: JSONObject,
    displayName: String
): JSONObject? {
    val materialHints = listOf(
        json.profileConfigString(NativeConfigKeys.Filament.Type),
        json.optString(NativeConfigKeys.Filament.SettingsId),
        json.optString(NativeConfigKeys.Printer.Name),
        json.optString(NativeConfigKeys.Printer.Inherits),
        displayName
    ).mapNotNull { it.detectFilamentMaterial() }
    val printerFilaments = currentStore.visibleFilamentsForSelectedPrinter()
    val selected = printerFilaments.firstOrNull { it.id == currentStore.selectedFilamentId }
    val baselineProfile = selected
        ?.takeIf { selectedProfile ->
            materialHints.isEmpty() ||
                materialHints.any { hint -> selectedProfile.materialType.equals(hint, ignoreCase = true) }
        }
        ?: printerFilaments.firstOrNull { profile ->
            materialHints.any { hint -> profile.materialType.equals(hint, ignoreCase = true) || profile.name.equals(hint, ignoreCase = true) }
        }
    return baselineProfile?.toOrcaBaselineJson()
}

internal fun FilamentProfile.toOrcaBaselineJson(): JSONObject = JSONObject()
    .put(NativeConfigKeys.Filament.Type, materialType)
    .put("filament_vendor", vendor)
    .put("filament_soluble", soluble)
    .put("filament_is_support", supportMaterial)
    .put(NativeConfigKeys.Filament.ExtruderVariant, filamentExtruderVariant)
    .put(NativeConfigKeys.Filament.SelfIndex, filamentSelfIndex)
    .put("filament_change_length", filamentChangeLengthMm)
    .put("required_nozzle_HRC", requiredNozzleHrc)
    .put(NativeConfigKeys.Filament.DefaultColor, defaultFilamentColor)
    .put("filament_diameter", diameterMm)
    .put("filament_adhesiveness_category", adhesivenessCategory)
    .put(NativeConfigKeys.Filament.Density, densityGPerCm3)
    .put("filament_shrink", shrinkageXyPercent)
    .put("filament_shrinkage_compensation_z", shrinkageZPercent)
    .put("filament_cost", costPerKg)
    .put("temperature_vitrification", softeningTemperatureC)
    .put("idle_temperature", idleTemperatureC)
    .put("nozzle_temperature_range_low", nozzleTemperatureRangeLowC)
    .put("nozzle_temperature_range_high", nozzleTemperatureRangeHighC)
    .put("filament_flow_ratio", flowRatio)
    .put("enable_pressure_advance", pressureAdvanceEnabled)
    .put(NativeConfigKeys.Filament.PressureAdvance, pressureAdvance)
    .put(NativeConfigKeys.Filament.MaxVolumetricSpeed, maxVolumetricSpeedMm3PerSec)
    .put("filament_adaptive_volumetric_speed", adaptiveVolumetricSpeedEnabled)
    .put("volumetric_speed_coefficients", volumetricSpeedCoefficients)
    .put("nozzle_temperature_initial_layer", nozzleTemperatureInitialLayerC)
    .put("nozzle_temperature", nozzleTemperatureC)
    .put("chamber_temperature", chamberTemperatureC)
    .put("activate_chamber_temp_control", activateChamberTemperatureControl)
    .put(NativeConfigKeys.Temperature.HotPlateInitialLayer, bedTemperatureInitialLayerC)
    .put(NativeConfigKeys.Temperature.HotPlate, bedTemperatureC)
    .put(NativeConfigKeys.Temperature.BedInitialLayer, bedTemperatureInitialLayerC)
    .put(NativeConfigKeys.Temperature.Bed, bedTemperatureC)
    .put("fan_min_speed", minFanSpeedPercent)
    .put("fan_max_speed", coolingPercent)
    .put("close_fan_the_first_x_layers", noCoolingFirstLayers)
    .put("full_fan_speed_layer", fullFanSpeedLayer)
    .put("fan_cooling_layer_time", fanCoolingLayerTimeSeconds)
    .put("slow_down_layer_time", slowDownLayerTimeSeconds)
    .put("reduce_fan_stop_start_freq", reduceFanStopStartFrequency)
    .put("slow_down_for_layer_cooling", slowDownForLayerCooling)
    .put("dont_slow_down_outer_wall", dontSlowDownOuterWall)
    .put("slow_down_min_speed", slowDownMinSpeedMmPerSec)
    .put("enable_overhang_bridge_fan", enableOverhangBridgeFan)
    .put("overhang_fan_threshold", overhangFanThreshold)
    .put("overhang_fan_speed", overhangFanSpeedPercent)
    .put("internal_bridge_fan_speed", internalBridgeFanSpeedPercent)
    .put("support_material_interface_fan_speed", supportMaterialInterfaceFanSpeedPercent)
    .put("ironing_fan_speed", ironingFanSpeedPercent)
    .put("additional_cooling_fan_speed", additionalCoolingFanSpeedPercent)
    .put("activate_air_filtration", activateAirFiltration)
    .put("during_print_exhaust_fan_speed", duringPrintExhaustFanSpeedPercent)
    .put("complete_print_exhaust_fan_speed", completePrintExhaustFanSpeedPercent)
    .put("filament_notes", filamentNotes)
    .put("filament_start_gcode", filamentStartGcode)
    .put("filament_end_gcode", filamentEndGcode)

internal fun String.detectFilamentMaterial(): String? {
    val upper = uppercase(Locale.US)
    val known = listOf("PETG-CF", "PLA-CF", "PA-CF", "PLA+", "ABS", "ASA", "PETG", "PLA", "TPU", "PA", "PC")
    return known.firstOrNull { material ->
        Regex("""(^|[^A-Z0-9+.-])${Regex.escape(material)}([^A-Z0-9+.-]|$)""").containsMatchIn(upper)
    }
}

internal val orcaFilamentCompatibleKeysCache = Collections.synchronizedMap(mutableMapOf<String, List<String>>())
internal val orcaFilamentCompatibilityCache = Collections.synchronizedMap(
    object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > 4096
    }
)

internal fun compatibleOrcaFilamentPresets(
    presets: List<OrcaFilamentPreset>,
    printer: PrinterProfile
): List<OrcaFilamentPreset> =
    presets.filter { preset -> preset.isCompatibleWithPrinter(printer) }

internal fun filteredOrcaFilamentPresets(
    presets: List<OrcaFilamentPreset>,
    selectedPrinter: PrinterProfile?,
    query: String,
    recommendedOnly: Boolean
): List<OrcaFilamentPreset> {
    val cleanQuery = query.trim().lowercase(Locale.US)
    val searched = if (cleanQuery.isEmpty()) {
        presets
    } else {
        presets.filter { it.searchText.contains(cleanQuery) }
    }
    return if (recommendedOnly && selectedPrinter != null) {
        compatibleOrcaFilamentPresets(searched, selectedPrinter)
    } else {
        searched
    }
}

internal data class OrcaFilamentPickerRow(
    val preset: OrcaFilamentPreset,
    val searchText: String,
    val compatibleWithSelectedPrinter: Boolean?
)

private val orcaFilamentPickerRowsCacheLock = Any()
private val cachedOrcaFilamentPickerRows = object : LinkedHashMap<String, List<OrcaFilamentPickerRow>>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<OrcaFilamentPickerRow>>?): Boolean =
        size > 8
}

internal fun loadOrcaFilamentPickerRows(
    context: Context,
    selectedPrinter: PrinterProfile?
): List<OrcaFilamentPickerRow> {
    val cacheKey = selectedPrinter?.filamentCompatibilityCacheKey() ?: "all-printers"
    synchronized(orcaFilamentPickerRowsCacheLock) {
        cachedOrcaFilamentPickerRows[cacheKey]?.let { return it }
    }
    val rows = buildOrcaFilamentPickerRows(
        presets = loadOrcaFilamentPresets(context),
        selectedPrinter = selectedPrinter
    )
    synchronized(orcaFilamentPickerRowsCacheLock) {
        cachedOrcaFilamentPickerRows[cacheKey]?.let { return it }
        cachedOrcaFilamentPickerRows[cacheKey] = rows
    }
    return rows
}

internal fun buildOrcaFilamentPickerRows(
    presets: List<OrcaFilamentPreset>,
    selectedPrinter: PrinterProfile?
): List<OrcaFilamentPickerRow> {
    if (presets.isEmpty()) return emptyList()
    val selectedKeys = selectedPrinter?.filamentPickerMatchKeys().orEmpty()
    val merged = LinkedHashMap<String, OrcaFilamentPickerCandidate>()
    presets.forEach { preset ->
        val key = preset.filamentPickerDuplicateKey()
        val compatible = selectedPrinter?.let { printer -> preset.isCompatibleWithPrinter(printer) }
        val current = merged[key]
        if (current == null) {
            merged[key] = OrcaFilamentPickerCandidate(
                preset = preset,
                searchTexts = linkedSetOf(preset.searchText),
                compatibleWithSelectedPrinter = compatible
            )
        } else {
            current.searchTexts.add(preset.searchText)
            if (preset.filamentPickerRepresentativeScore(compatible, selectedKeys) >
                current.preset.filamentPickerRepresentativeScore(current.compatibleWithSelectedPrinter, selectedKeys)
            ) {
                current.preset = preset
                current.compatibleWithSelectedPrinter = compatible
            }
        }
    }
    return merged.values
        .map { candidate ->
            OrcaFilamentPickerRow(
                preset = candidate.preset,
                searchText = candidate.searchTexts.joinToString(" "),
                compatibleWithSelectedPrinter = candidate.compatibleWithSelectedPrinter
            )
        }
        .sortedWith(compareBy<OrcaFilamentPickerRow> { it.preset.materialType }.thenBy { it.preset.name })
}

private fun OrcaFilamentPreset.filamentPickerDuplicateKey(): String =
    genericMaterialDuplicateKey().ifBlank {
        pickerDuplicateKey.ifBlank {
            listOf(name, materialType, vendor).joinToString("|") { it.cleanProfileMatchKey() }
        }
    }.ifBlank { profilePath }

private fun OrcaFilamentPreset.genericMaterialDuplicateKey(): String {
    val nameKey = name.cleanProfileMatchKey()
    val materialKey = materialType.cleanProfileMatchKey()
    return if (nameKey.isNotBlank() && nameKey == materialKey) {
        "$nameKey|$materialKey"
    } else {
        ""
    }
}

private fun OrcaFilamentPreset.isGenericVendor(): Boolean =
    vendor.cleanProfileMatchKey() == "generic"

internal fun OrcaFilamentPreset.isSystemGenericMaterial(): Boolean =
    family.equals("OrcaFilamentLibrary", ignoreCase = true) &&
        isGenericVendor() &&
        rawName.contains("@System", ignoreCase = true)

private fun OrcaFilamentPreset.isPlainGenericMaterialPreset(): Boolean {
    if (!isGenericVendor()) return false
    val materialKey = materialType.cleanProfileMatchKey()
    if (materialKey.isBlank()) return false
    val baseKey = rawName.substringBefore('@').cleanProfileMatchKey()
    val pathKey = profilePath
        .substringAfterLast('/')
        .removeSuffix(".json")
        .substringBefore('@')
        .cleanProfileMatchKey()
    val familyKey = family.cleanProfileMatchKey()
    return baseKey.isPlainGenericMaterialKey(materialKey, familyKey) ||
        pathKey.isPlainGenericMaterialKey(materialKey, familyKey)
}

private fun String.isPlainGenericMaterialKey(materialKey: String, familyKey: String): Boolean {
    if (isBlank()) return false
    val plainKeys = setOf(materialKey, "generic $materialKey")
    if (this in plainKeys) return true
    val withoutFamily = if (familyKey.isNotBlank() && startsWith("$familyKey ")) {
        removePrefix("$familyKey ").trim()
    } else {
        this
    }
    return withoutFamily in plainKeys
}

internal fun findSystemGenericOrcaFilamentPreset(
    presets: List<OrcaFilamentPreset>,
    materialType: String
): OrcaFilamentPreset? {
    val materialKey = materialType.cleanProfileMatchKey()
    if (materialKey.isBlank()) return null
    return presets.firstOrNull { preset ->
        preset.isSystemGenericMaterial() &&
            preset.materialType.cleanProfileMatchKey() == materialKey
    }
}

internal fun findGenericOrcaFilamentPresetForPrinter(
    presets: List<OrcaFilamentPreset>,
    printer: PrinterProfile,
    materialType: String
): OrcaFilamentPreset? {
    val materialKey = materialType.cleanProfileMatchKey()
    if (materialKey.isBlank()) return null
    return presets
        .asSequence()
        .filter { preset ->
            preset.isGenericVendor() &&
                preset.isPlainGenericMaterialPreset() &&
                preset.materialType.cleanProfileMatchKey() == materialKey &&
                preset.isCompatibleWithPrinter(printer)
        }
        .maxByOrNull { preset ->
            preset.selectedPrinterKeyScore(printer.filamentPickerMatchKeys()) * 100 -
                if (preset.isSystemGenericMaterial()) 1 else 0
        }
}

internal fun findGenericOrcaFilamentFallbackPreset(
    presets: List<OrcaFilamentPreset>,
    printer: PrinterProfile,
    filament: FilamentProfile
): OrcaFilamentPreset? {
    val printerGeneric = findGenericOrcaFilamentPresetForPrinter(
        presets = presets,
        printer = printer,
        materialType = filament.materialType
    )
    val systemGeneric = findSystemGenericOrcaFilamentPreset(
        presets = presets,
        materialType = filament.materialType
    )
    return if (filament.isPlainGenericMaterialProfile()) {
        printerGeneric ?: systemGeneric
    } else {
        systemGeneric ?: printerGeneric
    }
}

internal fun resolveOrcaFilamentPresetForImport(
    presets: List<OrcaFilamentPreset>,
    selectedPreset: OrcaFilamentPreset,
    selectedPrinter: PrinterProfile?
): OrcaFilamentPreset {
    if (selectedPrinter == null) return selectedPreset
    if (!selectedPreset.isGenericVendor() || !selectedPreset.isPlainGenericMaterialPreset()) {
        return selectedPreset
    }
    return findGenericOrcaFilamentPresetForPrinter(
        presets = presets,
        printer = selectedPrinter,
        materialType = selectedPreset.materialType
    ) ?: selectedPreset
}

internal fun FilamentProfile.isReplaceableOrcaGenericMaterialFor(
    imported: FilamentProfile,
    printerId: String
): Boolean =
    id != imported.id &&
        printerProfileId == printerId &&
        imported.printerProfileId == printerId &&
        profileSource == "orca" &&
        imported.profileSource == "orca" &&
        vendor.equals("Generic", ignoreCase = true) &&
        imported.vendor.equals("Generic", ignoreCase = true) &&
        materialType.equals(imported.materialType, ignoreCase = true) &&
        isPlainGenericMaterialProfile() &&
        imported.isPlainGenericMaterialProfile()

internal fun ProfileStore.withSystemGenericOrcaFilamentFallback(context: Context): ProfileStore {
    val printer = printers.firstOrNull { it.id == selectedPrinterId } ?: return this
    val filament = filaments.firstOrNull { it.id == selectedFilamentId && it.printerProfileId == selectedPrinterId }
        ?: return this
    if (!filament.canUseGenericOrcaFallback(printer)) return this
    val presets = loadOrcaFilamentPresets(context)
    val preset = findGenericOrcaFilamentFallbackPreset(
        presets = presets,
        printer = printer,
        filament = filament
    ) ?: return this
    if (filament.orcaFilamentPath == preset.profilePath) return this
    val imported = runCatching {
        preset.toImportedFilamentProfile(loadOrcaFilamentImportBundle(context, preset), printer)
            .bindToPrinter(selectedPrinterId)
    }.getOrNull() ?: return this
    val existing = filaments.firstOrNull { candidate ->
        candidate.printerProfileId == selectedPrinterId &&
            candidate.orcaFilamentPath == imported.orcaFilamentPath
    }
    return if (existing != null) {
        copy(selectedFilamentId = existing.id)
    } else {
        copy(
            filaments = filaments + imported,
            selectedFilamentId = imported.id
        )
    }
}

private fun FilamentProfile.canUseGenericOrcaFallback(printer: PrinterProfile): Boolean =
    printer.profileSource == "orca" &&
        vendor.equals("Generic", ignoreCase = true) &&
        materialType.isNotBlank() &&
        (
            needsSystemGenericOrcaFallback(printer) ||
                isSystemGenericOrcaFilamentProfile()
            )

internal fun FilamentProfile.needsSystemGenericOrcaFallback(printer: PrinterProfile): Boolean =
    printer.profileSource == "orca" &&
        vendor.equals("Generic", ignoreCase = true) &&
        materialType.isNotBlank() &&
        !isSystemGenericOrcaFilamentProfile() &&
        (
            !orcaFamily.equals(printer.orcaFamily, ignoreCase = true) ||
                !isPlainGenericMaterialProfile()
            )

private fun FilamentProfile.isSystemGenericOrcaFilamentProfile(): Boolean =
    orcaFamily.equals("OrcaFilamentLibrary", ignoreCase = true) &&
        orcaFilamentPath.contains("@System", ignoreCase = true)

internal fun FilamentProfile.isPlainGenericMaterialProfile(): Boolean {
    val materialKey = materialType.cleanProfileMatchKey()
    if (materialKey.isBlank()) return false
    val settingsKey = resolvedFilamentSettingsId()
        .substringBefore('@')
        .cleanProfileMatchKey()
    val pathKey = orcaFilamentPath
        .substringAfterLast('/')
        .removeSuffix(".json")
        .substringBefore('@')
        .cleanProfileMatchKey()
    val familyKey = orcaFamily.cleanProfileMatchKey()
    return settingsKey.isPlainGenericMaterialKey(materialKey, familyKey) ||
        pathKey.isPlainGenericMaterialKey(materialKey, familyKey)
}

private fun FilamentProfile.resolvedFilamentSettingsId(): String {
    if (orcaResolvedFilamentJson.isBlank()) return ""
    return runCatching {
        JSONObject(orcaResolvedFilamentJson).optString(NativeConfigKeys.Filament.SettingsId)
    }.getOrDefault("")
}

private fun OrcaFilamentPreset.manifestDuplicateKey(): String =
    pickerDuplicateKey.ifBlank {
        listOf(name, materialType, vendor).joinToString("|") { it.cleanProfileMatchKey() }
    }

internal fun filteredOrcaFilamentPickerRows(
    rows: List<OrcaFilamentPickerRow>,
    query: String,
    recommendedOnly: Boolean
): List<OrcaFilamentPickerRow> {
    val cleanQuery = query.trim().lowercase(Locale.US)
    return rows.filter { row ->
        (!recommendedOnly || row.compatibleWithSelectedPrinter == true) &&
            (cleanQuery.isEmpty() || row.searchText.contains(cleanQuery))
    }
}

private data class OrcaFilamentPickerCandidate(
    var preset: OrcaFilamentPreset,
    val searchTexts: LinkedHashSet<String>,
    var compatibleWithSelectedPrinter: Boolean?
)

private fun OrcaFilamentPreset.filamentPickerRepresentativeScore(
    compatibleWithSelectedPrinter: Boolean?,
    selectedKeys: Set<String>
): Int =
    (if (compatibleWithSelectedPrinter == true) 10_000 else 0) +
        (selectedPrinterKeyScore(selectedKeys) * 100) +
        (if (isSystemGenericMaterial()) 75 else 0) +
        (if (isGenericVendor()) 50 else 0) +
        (if (manifestDuplicateKey().isNotBlank()) 10 else 0) +
        compatiblePrinterKeys.size -
        rawName.length

internal fun OrcaFilamentPreset.isCompatibleWithPrinter(printer: PrinterProfile): Boolean {
    if (compatiblePrinters.isEmpty() && compatiblePrinterKeys.isEmpty()) {
        return isSystemGenericMaterial()
    }
    val cacheKey = "$profilePath|${compatiblePrinters.hashCode()}|${compatiblePrinterKeys.hashCode()}|${printer.filamentCompatibilityCacheKey()}"
    orcaFilamentCompatibilityCache[cacheKey]?.let { return it }
    val compatible = isCompatibleWithPrinterUncached(printer)
    orcaFilamentCompatibilityCache[cacheKey] = compatible
    return compatible
}

internal fun OrcaFilamentPreset.isCompatibleWithPrinterUncached(printer: PrinterProfile): Boolean {
    val printerKeys = printer.filamentPickerMatchKeys()
    val compatibleKeys = normalizedCompatiblePrinterKeys()
    if (compatibleKeys.any { compatible -> compatible in printerKeys }) return true
    if (!isFamilyLevelFallbackFor(printer)) return false
    val nozzleKey = "${formatNozzle(printer.nozzleDiameterMm)} nozzle".cleanProfileMatchKey()
    return family.equals(printer.orcaFamily, ignoreCase = true) &&
        compatibleKeys.any { compatible -> nozzleKey.isNotBlank() && compatible.contains(nozzleKey) }
}

internal fun OrcaFilamentPreset.normalizedCompatiblePrinterKeys(): List<String> {
    val cacheKey = "$profilePath|${compatiblePrinters.hashCode()}|${compatiblePrinterKeys.hashCode()}"
    synchronized(orcaFilamentCompatibleKeysCache) {
        orcaFilamentCompatibleKeysCache[cacheKey]?.let { return it }
    }
    val keys = (compatiblePrinterKeys + compatiblePrinters)
        .map { it.cleanProfileMatchKey() }
        .filter { it.isNotBlank() }
        .distinct()
    synchronized(orcaFilamentCompatibleKeysCache) {
        orcaFilamentCompatibleKeysCache[cacheKey] = keys
    }
    return keys
}

internal fun PrinterProfile.filamentCompatibilityCacheKey(): String =
    buildString {
        append(id)
        append('|')
        append(name)
        append('|')
        append(nozzleDiameterMm)
        append('|')
        append(printerModel)
        append('|')
        append(orcaFamily)
        append('|')
        append(orcaResolvedMachineJson.hashCode())
        append('|')
        append(orcaResolvedSourceChains.hashCode())
    }

private fun PrinterProfile.filamentPickerMatchKeys(): Set<String> =
    buildList {
        add(name)
        add("$name ${formatNozzle(nozzleDiameterMm)} nozzle")
        add(printerModel)
        add(orcaFamily)
        addAll(orcaResolvedSourceChains)
        jsonObjectOrNull(orcaResolvedMachineJson)?.let { resolved ->
            add(resolved.optString(NativeConfigKeys.Printer.Name))
            add(resolved.optString(NativeConfigKeys.Printer.Model))
            add(resolved.optString(NativeConfigKeys.Printer.Inherits))
        }
    }.map { it.cleanProfileMatchKey() }
        .filter { it.isNotBlank() }
        .toSet()

private fun OrcaFilamentPreset.selectedPrinterKeyScore(selectedKeys: Set<String>): Int {
    if (selectedKeys.isEmpty()) return 0
    return normalizedCompatiblePrinterKeys().count { compatible -> compatible in selectedKeys }
}

internal fun OrcaFilamentPreset.isFamilyLevelFallbackFor(printer: PrinterProfile): Boolean {
    if (!family.equals(printer.orcaFamily, ignoreCase = true)) return false
    if (!rawName.contains("@")) return true
    val suffix = rawName.substringAfter("@").trim().removeNozzleSuffix()
    return suffix.cleanProfileMatchKey() == printer.orcaFamily.cleanProfileMatchKey()
}

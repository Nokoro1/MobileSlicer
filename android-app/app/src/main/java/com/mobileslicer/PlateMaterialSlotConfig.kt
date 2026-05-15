package com.mobileslicer

import android.content.Context
import com.mobileslicer.profiles.FilamentProfile
import com.mobileslicer.profiles.NativeConfigKeys
import com.mobileslicer.profiles.applySingleFilamentSlotNativeRuntimeBoundary
import com.mobileslicer.profiles.normalizeNativeShellThicknessScalars
import com.mobileslicer.workspace.PlateFilamentSlot
import com.mobileslicer.workspace.PlateFlushVolumes
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.paintHash
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal fun FilamentProfile.toPlateFilamentSlot(index: Int): PlateFilamentSlot =
    PlateFilamentSlot(
        index = index,
        filamentProfileId = id,
        label = name,
        materialType = materialType,
        colorHex = defaultFilamentColor.ifBlank { "#8FC1FF" }
    )

internal fun syncPlateFilamentSlotsWithProfiles(
    slots: List<PlateFilamentSlot>,
    availableFilaments: List<FilamentProfile>,
    fallbackFilament: FilamentProfile
): List<PlateFilamentSlot> {
    val filamentsById = availableFilaments.associateBy { it.id }
    return slots
        .sortedBy { it.index }
        .mapIndexed { index, slot ->
            val filament = filamentsById[slot.filamentProfileId] ?: fallbackFilament.takeIf { it.id == slot.filamentProfileId }
            if (filament == null) {
                slot.copy(index = index + 1)
            } else {
                filament.toPlateFilamentSlot(index = index + 1).copy(
                    colorHex = slot.colorHex.ifBlank { filament.defaultFilamentColor.ifBlank { "#8FC1FF" } },
                    physicalNozzleIndex = slot.physicalNozzleIndex
                )
            }
        }
}

private fun FilamentProfile?.hasOrcaFilamentContext(): Boolean =
    this != null && (
        profileSource == "orca" ||
            orcaFamily.isNotBlank() ||
            orcaFilamentPath.isNotBlank() ||
            orcaResolvedFilamentJson.isNotBlank()
        )

private fun FilamentProfile?.resolvedFilamentJson(): JSONObject? {
    if (this == null) return null
    val resolved = orcaResolvedFilamentJson
        .takeIf { it.isNotBlank() }
        ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
    val overrides = orcaFilamentOverridesJson
        .takeIf { it.isNotBlank() }
        ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
    if (resolved == null && overrides == null) return null
    return (resolved ?: JSONObject()).apply {
        overrides?.keys()?.forEach { key ->
            put(key, overrides.opt(key))
        }
    }
}

private fun resolvedPlateFilamentColor(
    slot: PlateFilamentSlot,
    filament: FilamentProfile?
): String {
    val resolvedColor = filament.resolvedFilamentJson()
        ?.optString(NativeConfigKeys.Filament.Color)
        .orEmpty()
    val slotColor = slot.colorHex.ifBlank {
        filament?.defaultFilamentColor?.takeIf { it.isNotBlank() } ?: "#8FC1FF"
    }
    if (!slotColor.equals("#8FC1FF", ignoreCase = true)) {
        return slotColor
    }
    return resolvedColor
        .ifBlank { if (filament.hasOrcaFilamentContext()) "#26A69A" else "" }
        .ifBlank { slotColor }
}

private fun JSONObject.optNativeScalarString(key: String): String {
    if (!has(key) || isNull(key)) return ""
    val value = opt(key)
    return when (value) {
        is JSONArray -> if (value.length() > 0) value.optString(0) else ""
        else -> value?.toString().orEmpty()
    }
}

internal fun applyPlateFilamentSlotsToNativeConfig(
    configJson: String,
    slots: List<PlateFilamentSlot>,
    plateObjects: List<PlateObject>,
    filaments: List<FilamentProfile>,
    flushVolumes: PlateFlushVolumes?
): String {
    return applyPlateFilamentSlotsToNativeConfigResult(
        configJson = configJson,
        slots = slots,
        plateObjects = plateObjects,
        filaments = filaments,
        flushVolumes = flushVolumes
    ).json
}

internal data class PlateSliceConfigResult(
    val json: String,
    val cacheHit: Boolean
)

private data class PlateSliceConfigCacheKey(
    val configHash: Int,
    val slotSignature: Int,
    val objectSlotSignature: Int,
    val activePaintSignature: Int,
    val filamentSignature: Int,
    val flushVolumesSignature: Int
)

private object PlateSliceConfigCache {
    private const val MaxEntries = 8
    private val configs = object : LinkedHashMap<PlateSliceConfigCacheKey, String>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PlateSliceConfigCacheKey, String>?): Boolean =
            size > MaxEntries
    }

    @Synchronized
    fun get(key: PlateSliceConfigCacheKey): String? = configs[key]

    @Synchronized
    fun put(key: PlateSliceConfigCacheKey, value: String) {
        configs[key] = value
    }
}

internal fun applyPlateFilamentSlotsToNativeConfigResult(
    configJson: String,
    slots: List<PlateFilamentSlot>,
    plateObjects: List<PlateObject>,
    filaments: List<FilamentProfile>,
    flushVolumes: PlateFlushVolumes?
): PlateSliceConfigResult {
    val sortedSlots = activePlateFilamentSlots(slots, plateObjects).ifEmpty {
        return PlateSliceConfigResult(json = configJson, cacheHit = false)
    }
    val cacheKey = PlateSliceConfigCacheKey(
        configHash = configJson.hashCode(),
        slotSignature = sortedSlots.hashCode(),
        objectSlotSignature = plateObjects.map { it.id to it.filamentSlotIndex }.hashCode(),
        activePaintSignature = plateObjects.map { it.id to it.paint.paintHash() }.hashCode(),
        filamentSignature = filaments.map { it.id to it.hashCode() }.hashCode(),
        flushVolumesSignature = flushVolumes.hashCode()
    )
    PlateSliceConfigCache.get(cacheKey)?.let {
        return PlateSliceConfigResult(json = it, cacheHit = true)
    }
    val json = runCatching { JSONObject(configJson) }.getOrElse {
        return PlateSliceConfigResult(json = configJson, cacheHit = false)
    }
    val filamentsById = filaments.associateBy { it.id }
    val slotFilaments = sortedSlots.map { slot ->
        filamentsById[slot.filamentProfileId]
    }

    fun resolvedFilamentValue(filament: FilamentProfile?, key: String): String {
        return filament.resolvedFilamentJson()?.optNativeScalarString(key).orEmpty()
    }

    fun resolvedFilamentGcode(
        filament: FilamentProfile?,
        key: String,
        fallback: (FilamentProfile) -> String
    ): String {
        return resolvedFilamentValue(filament, key)
            .ifBlank { filament?.let(fallback).orEmpty() }
    }

    val physicalNozzleCount = json.nativePhysicalNozzleCount().coerceAtLeast(1)
    val singlePhysicalNozzle = physicalNozzleCount == 1
    val orcaNozzleVectorCount = physicalNozzleCount

    if (sortedSlots.size <= 1) {
        val slot = sortedSlots.first()
        val filament = slotFilaments.firstOrNull()
        val resolvedFilamentJson = filament.resolvedFilamentJson()
        val color = resolvedPlateFilamentColor(slot, filament)
        fun singleString(key: String, fallback: () -> String): String =
            resolvedFilamentValue(filament, key).ifBlank(fallback)

        fun singleNumber(key: String, fallback: () -> Number): Number =
            resolvedFilamentValue(filament, key).toDoubleOrNull() ?: fallback()

        json.put(NativeConfigKeys.Filament.Color, color)
        json.put("extruder_colour", color)
        json.put(NativeConfigKeys.Filament.DefaultColor, resolvedFilamentJson?.optString(NativeConfigKeys.Filament.DefaultColor, "") ?: color)
        json.put(NativeConfigKeys.Filament.Type, singleString(NativeConfigKeys.Filament.Type) {
            filament?.materialType.orEmpty()
                .ifBlank { slot.materialType }
                .ifBlank { "PLA" }
        })
        json.put("filament_vendor", singleString("filament_vendor") { filament?.vendor.orEmpty() })
        json.put(NativeConfigKeys.Filament.SettingsId, singleString(NativeConfigKeys.Filament.SettingsId) {
            filament?.name.orEmpty().ifBlank { slot.label }
        })
        json.put(NativeConfigKeys.Filament.Ids, singleString(NativeConfigKeys.Filament.Ids) {
            filament?.id.orEmpty().ifBlank { slot.filamentProfileId }
        })
        json.put(NativeConfigKeys.Filament.ExtruderVariant, singleString(NativeConfigKeys.Filament.ExtruderVariant) {
            filament?.filamentExtruderVariant.orEmpty().ifBlank { "Direct Drive Standard" }
        })
        json.put("filament_start_gcode", resolvedFilamentGcode(filament, "filament_start_gcode") {
            it.filamentStartGcode
        })
        json.put("filament_end_gcode", resolvedFilamentGcode(filament, "filament_end_gcode") {
            it.filamentEndGcode
        })
        json.put("filament_diameter", singleNumber("filament_diameter") { filament?.diameterMm?.toDouble() ?: 1.75 })
        json.put(NativeConfigKeys.Filament.Density, singleNumber(NativeConfigKeys.Filament.Density) { filament?.densityGPerCm3?.toDouble() ?: 1.24 })
        json.put("filament_flow_ratio", singleNumber("filament_flow_ratio") { filament?.flowRatio?.toDouble() ?: 0.98 })
        json.put(NativeConfigKeys.Filament.MaxVolumetricSpeed, singleNumber(NativeConfigKeys.Filament.MaxVolumetricSpeed) { filament?.maxVolumetricSpeedMm3PerSec?.toDouble() ?: 12.0 })
        json.put("nozzle_temperature_initial_layer", singleNumber("nozzle_temperature_initial_layer") { filament?.nozzleTemperatureInitialLayerC ?: 210 })
        json.put("nozzle_temperature", singleNumber("nozzle_temperature") { filament?.nozzleTemperatureC ?: 210 })
        json.put(NativeConfigKeys.Temperature.BedInitialLayer, singleNumber(NativeConfigKeys.Temperature.BedInitialLayer) { filament?.bedTemperatureInitialLayerC ?: 60 })
        json.put(NativeConfigKeys.Temperature.Bed, singleNumber(NativeConfigKeys.Temperature.Bed) { filament?.bedTemperatureC ?: 60 })
        json.put(NativeConfigKeys.Temperature.HotPlateInitialLayer, singleNumber(NativeConfigKeys.Temperature.HotPlateInitialLayer) { filament?.bedTemperatureInitialLayerC ?: 60 })
        json.put(NativeConfigKeys.Temperature.HotPlate, singleNumber(NativeConfigKeys.Temperature.HotPlate) { filament?.bedTemperatureC ?: 60 })
        json.put(NativeConfigKeys.Temperature.CoolPlateInitialLayer, singleNumber(NativeConfigKeys.Temperature.CoolPlateInitialLayer) { filament?.coolPlateTemperatureInitialLayerC ?: 35 })
        json.put(NativeConfigKeys.Temperature.CoolPlate, singleNumber(NativeConfigKeys.Temperature.CoolPlate) { filament?.coolPlateTemperatureC ?: 35 })
        json.put(NativeConfigKeys.Temperature.TexturedCoolPlateInitialLayer, singleNumber(NativeConfigKeys.Temperature.TexturedCoolPlateInitialLayer) { filament?.texturedCoolPlateTemperatureInitialLayerC ?: 40 })
        json.put(NativeConfigKeys.Temperature.TexturedCoolPlate, singleNumber(NativeConfigKeys.Temperature.TexturedCoolPlate) { filament?.texturedCoolPlateTemperatureC ?: 40 })
        json.put(NativeConfigKeys.Temperature.EngineeringPlateInitialLayer, singleNumber(NativeConfigKeys.Temperature.EngineeringPlateInitialLayer) { filament?.engineeringPlateTemperatureInitialLayerC ?: 45 })
        json.put(NativeConfigKeys.Temperature.EngineeringPlate, singleNumber(NativeConfigKeys.Temperature.EngineeringPlate) { filament?.engineeringPlateTemperatureC ?: 45 })
        json.put(NativeConfigKeys.Temperature.SupertackPlateInitialLayer, singleNumber(NativeConfigKeys.Temperature.SupertackPlateInitialLayer) { filament?.supertackPlateTemperatureInitialLayerC ?: 35 })
        json.put(NativeConfigKeys.Temperature.SupertackPlate, singleNumber(NativeConfigKeys.Temperature.SupertackPlate) { filament?.supertackPlateTemperatureC ?: 35 })
        json.put(NativeConfigKeys.Temperature.TexturedPlateInitialLayer, singleNumber(NativeConfigKeys.Temperature.TexturedPlateInitialLayer) { filament?.texturedPlateTemperatureInitialLayerC ?: 45 })
        json.put(NativeConfigKeys.Temperature.TexturedPlate, singleNumber(NativeConfigKeys.Temperature.TexturedPlate) { filament?.texturedPlateTemperatureC ?: 45 })
        json.applyPaintAwareConfigValidation(
            plateObjects = plateObjects,
            activeSlots = sortedSlots
        )
        json.put(NativeConfigKeys.PrimeTower.SingleExtruderMultiMaterial, singlePhysicalNozzle)
        json.put(NativeConfigKeys.PrimeTower.SingleExtruderMultiMaterialPriming, false)
        json.put("flush_multiplier", JSONArray().apply {
            repeat(orcaNozzleVectorCount) { put(0.3) }
        })
        json.put("flush_volumes_matrix", JSONArray().apply {
            repeat(orcaNozzleVectorCount) { put(0.0) }
        })
        json.put("flush_volumes_vector", "140,140")
        json.applySingleFilamentSlotNativeRuntimeBoundary()
        json.normalizeNativeShellThicknessScalars()
        val result = json.toString()
        PlateSliceConfigCache.put(cacheKey, result)
        return PlateSliceConfigResult(json = result, cacheHit = false)
    }

    fun colors(): JSONArray = JSONArray().apply {
        sortedSlots.forEachIndexed { index, slot ->
            put(resolvedPlateFilamentColor(slot, slotFilaments[index]))
        }
    }

    fun stringArray(valueFor: (PlateFilamentSlot, FilamentProfile?) -> String): JSONArray =
        JSONArray().apply {
            sortedSlots.forEachIndexed { index, slot ->
                put(valueFor(slot, slotFilaments[index]))
            }
        }

    fun numberArray(valueFor: (FilamentProfile?) -> Number): JSONArray =
        JSONArray().apply {
            slotFilaments.forEach { filament ->
                put(valueFor(filament))
            }
        }

    fun numberArrayFromResolved(
        key: String,
        fallback: (FilamentProfile?) -> Number
    ): JSONArray =
        JSONArray().apply {
            slotFilaments.forEach { filament ->
                put(resolvedFilamentValue(filament, key).toDoubleOrNull() ?: fallback(filament))
            }
        }

    fun booleanArrayFromResolved(
        key: String,
        fallback: (FilamentProfile?) -> Boolean
    ): JSONArray =
        JSONArray().apply {
            slotFilaments.forEach { filament ->
                val resolved = resolvedFilamentValue(filament, key).trim().lowercase(Locale.US)
                put(
                    when (resolved) {
                        "1", "true", "yes", "on" -> true
                        "0", "false", "no", "off" -> false
                        else -> fallback(filament)
                    }
                )
            }
        }

    fun putIfMissing(key: String, value: Any) {
        val current = json.opt(key)
        if (!json.has(key) || json.isNull(key) || current?.toString().orEmpty().isBlank()) {
            json.put(key, value)
        }
    }

    val requestedPrimeTower = json.optBoolean(NativeConfigKeys.PrimeTower.Enable, false)
    val actuallyUsedSlotIndexes = actuallyUsedPlateFilamentSlotIndexes(sortedSlots, plateObjects)
    val multiMaterialPlate = actuallyUsedSlotIndexes.size > 1
    val primeTowerEnabled = multiMaterialPlate && requestedPrimeTower
    json.applyPaintAwareConfigValidation(
        plateObjects = plateObjects,
        activeSlots = sortedSlots
    )
    json.put(NativeConfigKeys.PrimeTower.SingleExtruderMultiMaterial, singlePhysicalNozzle)
    json.put(NativeConfigKeys.PrimeTower.SingleExtruderMultiMaterialPriming, false)
    json.put(NativeConfigKeys.PrimeTower.Enable, primeTowerEnabled)
    json.put(NativeConfigKeys.PrimeTower.Purge, primeTowerEnabled)
    json.put(NativeConfigKeys.Mobile.ActiveFilamentSlotCount, sortedSlots.size)
    json.put("mobile_slicer_physical_nozzle_count", physicalNozzleCount)
    json.put(NativeConfigKeys.Filament.Map, JSONArray().apply {
        sortedSlots.forEachIndexed { index, _ ->
            val explicitNozzleIndex = sortedSlots[index].physicalNozzleIndex?.coerceIn(1, physicalNozzleCount)
            put(explicitNozzleIndex ?: if (singlePhysicalNozzle) 1 else (index % physicalNozzleCount) + 1)
        }
    })
    json.put(
        NativeConfigKeys.Filament.MapMode,
        if (sortedSlots.any { it.physicalNozzleIndex != null }) {
            "Manual"
        } else if (singlePhysicalNozzle || sortedSlots.size <= physicalNozzleCount) {
            "Auto For Flush"
        } else {
            "Manual"
        }
    )
    putIfMissing("prime_tower_width", 35.0)
    putIfMissing("prime_volume", 25.0)
    json.put(NativeConfigKeys.Filament.SelfIndex, JSONArray().apply {
        sortedSlots.forEachIndexed { index, _ -> put((index + 1).toString()) }
    })
    json.put(NativeConfigKeys.Filament.Color, colors())
    json.put(NativeConfigKeys.Filament.DefaultColor, colors())
    json.put(NativeConfigKeys.Filament.ColorType, JSONArray().apply {
        sortedSlots.forEach { put("1") }
    })
    json.put(NativeConfigKeys.Filament.Type, stringArray { slot, filament ->
        resolvedFilamentValue(filament, NativeConfigKeys.Filament.Type)
            .ifBlank { filament?.materialType.orEmpty() }
            .ifBlank { slot.materialType }
            .ifBlank { "PLA" }
    })
    json.put("filament_vendor", stringArray { _, filament ->
        resolvedFilamentValue(filament, "filament_vendor")
            .ifBlank { filament?.vendor.orEmpty() }
    })
    json.put(NativeConfigKeys.Filament.SettingsId, stringArray { slot, filament ->
        resolvedFilamentValue(filament, NativeConfigKeys.Filament.SettingsId)
            .ifBlank { filament?.name.orEmpty() }
            .ifBlank { slot.label }
    })
    json.put(NativeConfigKeys.Filament.Ids, stringArray { slot, filament ->
        resolvedFilamentValue(filament, NativeConfigKeys.Filament.Ids)
            .ifBlank { filament?.id.orEmpty() }
            .ifBlank { slot.filamentProfileId }
    })
    json.put(NativeConfigKeys.Filament.ExtruderVariant, stringArray { _, filament ->
        resolvedFilamentValue(filament, NativeConfigKeys.Filament.ExtruderVariant)
            .ifBlank { filament?.filamentExtruderVariant.orEmpty() }
            .ifBlank { "Direct Drive Standard" }
    })
    json.put("filament_start_gcode", stringArray { _, filament ->
        resolvedFilamentGcode(filament, "filament_start_gcode") { it.filamentStartGcode }
    })
    json.put("filament_end_gcode", stringArray { _, filament ->
        resolvedFilamentGcode(filament, "filament_end_gcode") { it.filamentEndGcode }
    })
    json.put("filament_diameter", numberArrayFromResolved("filament_diameter") { it?.diameterMm?.toDouble() ?: 1.75 })
    json.put("filament_density", numberArrayFromResolved("filament_density") { it?.densityGPerCm3?.toDouble() ?: 1.24 })
    json.put("filament_adhesiveness_category", numberArrayFromResolved("filament_adhesiveness_category") { 200 })
    json.put("filament_flow_ratio", numberArrayFromResolved("filament_flow_ratio") { it?.flowRatio?.toDouble() ?: 0.98 })
    json.put("filament_max_volumetric_speed", numberArrayFromResolved("filament_max_volumetric_speed") { it?.maxVolumetricSpeedMm3PerSec?.toDouble() ?: 12.0 })
    json.put("filament_flush_volumetric_speed", numberArrayFromResolved("filament_flush_volumetric_speed") { 0.0 })
    json.put("filament_flush_temp", numberArrayFromResolved("filament_flush_temp") { 0 })
    json.put("nozzle_temperature_initial_layer", numberArrayFromResolved("nozzle_temperature_initial_layer") { it?.nozzleTemperatureInitialLayerC ?: 210 })
    json.put("nozzle_temperature", numberArrayFromResolved("nozzle_temperature") { it?.nozzleTemperatureC ?: 210 })
    json.put("nozzle_temperature_range_low", numberArrayFromResolved("nozzle_temperature_range_low") { 190 })
    json.put("nozzle_temperature_range_high", numberArrayFromResolved("nozzle_temperature_range_high") { it?.nozzleTemperatureC?.plus(20) ?: 230 })
    json.put("bed_temperature_initial_layer", numberArrayFromResolved("bed_temperature_initial_layer") { it?.bedTemperatureInitialLayerC ?: 60 })
    json.put("bed_temperature", numberArrayFromResolved("bed_temperature") { it?.bedTemperatureC ?: 60 })
    json.put("hot_plate_temp_initial_layer", numberArrayFromResolved("hot_plate_temp_initial_layer") { it?.bedTemperatureInitialLayerC ?: 60 })
    json.put("hot_plate_temp", numberArrayFromResolved("hot_plate_temp") { it?.bedTemperatureC ?: 60 })
    json.put("cool_plate_temp_initial_layer", numberArrayFromResolved("cool_plate_temp_initial_layer") { it?.coolPlateTemperatureInitialLayerC ?: 35 })
    json.put("cool_plate_temp", numberArrayFromResolved("cool_plate_temp") { it?.coolPlateTemperatureC ?: 35 })
    json.put("textured_cool_plate_temp_initial_layer", numberArrayFromResolved("textured_cool_plate_temp_initial_layer") { it?.texturedCoolPlateTemperatureInitialLayerC ?: 40 })
    json.put("textured_cool_plate_temp", numberArrayFromResolved("textured_cool_plate_temp") { it?.texturedCoolPlateTemperatureC ?: 40 })
    json.put("eng_plate_temp_initial_layer", numberArrayFromResolved("eng_plate_temp_initial_layer") { it?.engineeringPlateTemperatureInitialLayerC ?: 45 })
    json.put("eng_plate_temp", numberArrayFromResolved("eng_plate_temp") { it?.engineeringPlateTemperatureC ?: 45 })
    json.put("supertack_plate_temp_initial_layer", numberArrayFromResolved("supertack_plate_temp_initial_layer") { it?.supertackPlateTemperatureInitialLayerC ?: 35 })
    json.put("supertack_plate_temp", numberArrayFromResolved("supertack_plate_temp") { it?.supertackPlateTemperatureC ?: 35 })
    json.put("textured_plate_temp_initial_layer", numberArrayFromResolved("textured_plate_temp_initial_layer") { it?.texturedPlateTemperatureInitialLayerC ?: 45 })
    json.put("textured_plate_temp", numberArrayFromResolved("textured_plate_temp") { it?.texturedPlateTemperatureC ?: 45 })
    json.put("cooling_baseline", numberArrayFromResolved("cooling_baseline") { it?.coolingPercent ?: 100 })
    json.put("fan_max_speed", numberArrayFromResolved("fan_max_speed") { it?.coolingPercent ?: 100 })
    json.put("fan_min_speed", numberArrayFromResolved("fan_min_speed") { it?.minFanSpeedPercent ?: 30 })
    json.put("close_fan_the_first_x_layers", numberArrayFromResolved("close_fan_the_first_x_layers") { it?.noCoolingFirstLayers ?: 1 })
    json.put("filament_soluble", booleanArrayFromResolved("filament_soluble") { it?.soluble == true })
    json.put("filament_is_support", booleanArrayFromResolved("filament_is_support") { it?.supportMaterial == true })
    json.put("filament_minimal_purge_on_wipe_tower", numberArrayFromResolved("filament_minimal_purge_on_wipe_tower") { 15.0 })
    json.put("filament_tower_interface_pre_extrusion_dist", numberArrayFromResolved("filament_tower_interface_pre_extrusion_dist") { 0.0 })
    json.put("filament_tower_interface_pre_extrusion_length", numberArrayFromResolved("filament_tower_interface_pre_extrusion_length") { 0.0 })
    json.put("filament_tower_ironing_area", numberArrayFromResolved("filament_tower_ironing_area") { 0.0 })
    json.put("filament_tower_interface_purge_volume", numberArrayFromResolved("filament_tower_interface_purge_volume") { 0.0 })
    json.put("filament_tower_interface_print_temp", numberArrayFromResolved("filament_tower_interface_print_temp") { -1 })
    json.put("filament_loading_speed_start", numberArrayFromResolved("filament_loading_speed_start") { 3.0 })
    json.put("filament_loading_speed", numberArrayFromResolved("filament_loading_speed") { 28.0 })
    json.put("filament_unloading_speed_start", numberArrayFromResolved("filament_unloading_speed_start") { 100.0 })
    json.put("filament_unloading_speed", numberArrayFromResolved("filament_unloading_speed") { 90.0 })
    json.put("filament_toolchange_delay", numberArrayFromResolved("filament_toolchange_delay") { 0.0 })
    json.put("filament_cooling_moves", numberArrayFromResolved("filament_cooling_moves") { 4 })
    json.put("filament_cooling_initial_speed", numberArrayFromResolved("filament_cooling_initial_speed") { 2.2 })
    json.put("filament_cooling_final_speed", numberArrayFromResolved("filament_cooling_final_speed") { 3.4 })
    json.put("filament_stamping_loading_speed", numberArrayFromResolved("filament_stamping_loading_speed") { 20.0 })
    json.put("filament_stamping_distance", numberArrayFromResolved("filament_stamping_distance") { 0.0 })
    val normalizedFlushVolumes = ensureFlushVolumesForSlots(
        slots = sortedSlots,
        existing = flushVolumes,
        regenerateFromColors = flushVolumes == null,
        nozzleCount = orcaNozzleVectorCount
    )
    json.put("flush_multiplier", JSONArray().apply {
        normalizedFlushVolumes.multipliers.forEach { put(it) }
    })
    json.put("flush_volumes_matrix", JSONArray().apply {
        normalizedFlushVolumes.matrix.forEach { put(it) }
    })
    json.put("mobile_slicer_plate_filament_slots", JSONArray().apply {
        sortedSlots.forEach { slot ->
            put(
                JSONObject()
                    .put("index", slot.index)
                    .put("filamentProfileId", slot.filamentProfileId)
                    .put("label", slot.label)
                    .put("materialType", slot.materialType)
                    .put("colorHex", slot.colorHex)
                    .also { slotJson ->
                        slot.physicalNozzleIndex?.let { slotJson.put("physicalNozzleIndex", it) }
                    }
            )
        }
    })
    json.normalizeNativeShellThicknessScalars()
    val result = json.toString()
    PlateSliceConfigCache.put(cacheKey, result)
    return PlateSliceConfigResult(json = result, cacheHit = false)
}

internal fun activePlateFilamentSlots(
    slots: List<PlateFilamentSlot>,
    plateObjects: List<PlateObject>
): List<PlateFilamentSlot> {
    val sortedSlots = slots.sortedBy { it.index }
    if (sortedSlots.isEmpty()) return emptyList()
    val availableSlotIndexes = sortedSlots.map { it.index }.toSet()
    val usedSlotIndexes = plateObjects
        .flatMap { objectOnPlate ->
            buildList {
                add(objectOnPlate.filamentSlotIndex.coerceAtLeast(1))
                objectOnPlate.paint.color
                    ?.takeIf { layer -> !layer.isEmpty && !layer.isStale }
                    ?.referencedSlotIndexes
                    ?.filter { slotIndex -> slotIndex > 0 }
                    ?.let { addAll(it) }
            }
        }
        .toSortedSet()
    if (usedSlotIndexes.isEmpty()) {
        return sortedSlots.take(1)
    }
    val validUsedSlotIndexes = usedSlotIndexes.filter { it in availableSlotIndexes }
    if (validUsedSlotIndexes.isEmpty()) {
        return sortedSlots.take(1)
    }
    val paintedColorSlotIndexes = plateObjects
        .flatMap { objectOnPlate ->
            objectOnPlate.paint.color
                ?.takeIf { layer -> !layer.isEmpty && !layer.isStale }
                ?.referencedSlotIndexes
                .orEmpty()
        }
        .filter { it > 0 }
    if (paintedColorSlotIndexes.isNotEmpty()) {
        val maxSlot = (validUsedSlotIndexes + paintedColorSlotIndexes.filter { it in availableSlotIndexes })
            .maxOrNull()
            ?.coerceAtLeast(1)
            ?: 1
        return sortedSlots
            .filter { it.index <= maxSlot }
            .ifEmpty { sortedSlots.take(maxSlot.coerceAtMost(sortedSlots.size).coerceAtLeast(1)) }
    }
    if (validUsedSlotIndexes.size == 1 && validUsedSlotIndexes.first() == sortedSlots.first().index) {
        return sortedSlots.take(1)
    }
    val maxSlot = validUsedSlotIndexes.maxOrNull() ?: return sortedSlots.take(1)
    return sortedSlots
        .filter { it.index <= maxSlot }
        .ifEmpty { sortedSlots.take(1) }
}

internal fun actuallyUsedPlateFilamentSlotIndexes(
    activeSlots: List<PlateFilamentSlot>,
    plateObjects: List<PlateObject>
): Set<Int> {
    val availableSlotIndexes = activeSlots.map { it.index }.toSet()
    val objectSlots = plateObjects
        .map { it.filamentSlotIndex.coerceAtLeast(1) }
        .filter { it in availableSlotIndexes }
    val paintedColorSlots = plateObjects
        .flatMap { objectOnPlate ->
            objectOnPlate.paint.color
                ?.takeIf { layer -> !layer.isEmpty && !layer.isStale }
                ?.referencedSlotIndexes
                .orEmpty()
        }
        .filter { it in availableSlotIndexes }
    return (objectSlots + paintedColorSlots).toSet()
}

private fun JSONObject.applyPaintAwareConfigValidation(
    plateObjects: List<PlateObject>,
    activeSlots: List<PlateFilamentSlot>
) {
    val activeLayers = plateObjects.flatMap { objectOnPlate ->
        objectOnPlate.paint.layers().filter { layer -> !layer.isEmpty && !layer.isStale }
    }
    if (activeLayers.isEmpty()) return

    val warnings = JSONArray()
    if (activeLayers.any { it.mode == com.mobileslicer.workspace.PaintMode.Support }) {
        put("enable_support", true)
        put("support_type", supportTypeForPaintedSupport(optString("support_type")))
    }
    if (activeLayers.any { it.mode == com.mobileslicer.workspace.PaintMode.Seam } && optString("seam_position").isBlank()) {
        put("seam_position", "aligned")
    }
    val colorSlots = activeLayers
        .filter { it.mode == com.mobileslicer.workspace.PaintMode.Color }
        .flatMap { it.referencedSlotIndexes }
        .filter { it > 0 }
        .toSortedSet()
    if (colorSlots.isNotEmpty()) {
        val configuredSlots = activeSlots.map { it.index }.toSet()
        val missingSlots = colorSlots.filterNot { it in configuredSlots }
        if (missingSlots.isNotEmpty()) {
            warnings.put("Color paint references missing filament slots: ${missingSlots.joinToString(", ")}")
        }
        if (configuredSlots.size < 2 && colorSlots.any { it > 1 }) {
            warnings.put("Color paint requires a multi-filament native config.")
        }
    }
    if (warnings.length() > 0) {
        put("mobile_slicer_paint_config_warnings", warnings)
    } else {
        remove("mobile_slicer_paint_config_warnings")
    }
}

private fun supportTypeForPaintedSupport(currentSupportType: String): String =
    when (currentSupportType) {
        "tree(auto)",
        "tree(manual)" -> "tree(manual)"
        "normal(manual)" -> "normal(manual)"
        else -> "normal(manual)"
    }

internal fun ensureFlushVolumesForSlots(
    slots: List<PlateFilamentSlot>,
    existing: PlateFlushVolumes?,
    regenerateFromColors: Boolean,
    nozzleCount: Int = 1
): PlateFlushVolumes {
    val sortedSlots = slots.sortedBy { it.index }
    val slotCount = sortedSlots.size.coerceAtLeast(1)
    val normalizedNozzleCount = nozzleCount.coerceAtLeast(1)
    val storedMultipliers = existing?.multipliers?.ifEmpty { listOf(0.3) } ?: listOf(0.3)
    val multipliers = List(normalizedNozzleCount) { index ->
        storedMultipliers.getOrNull(index) ?: storedMultipliers.lastOrNull() ?: 0.3
    }
    val expectedMatrixSize = slotCount * slotCount * multipliers.size
    if (!regenerateFromColors && existing != null) {
        val normalized = existing.normalized()
        if (normalized.slotCount == slotCount && normalized.multipliers.size == multipliers.size && normalized.matrix.size == expectedMatrixSize) {
            return normalized
        }
        return resizeFlushVolumesMatrix(
            slots = sortedSlots,
            existing = normalized,
            multipliers = multipliers
        )
    }
    return PlateFlushVolumes(
        slotCount = slotCount,
        multipliers = multipliers,
        matrix = colorAwareFlushVolumesMatrix(sortedSlots, multipliers.size)
    ).normalized()
}

private fun resizeFlushVolumesMatrix(
    slots: List<PlateFilamentSlot>,
    existing: PlateFlushVolumes,
    multipliers: List<Double>
): PlateFlushVolumes {
    val slotCount = slots.size.coerceAtLeast(1)
    val oldSlotCount = existing.slotCount.coerceAtLeast(1)
    val oldMatrixPerNozzle = oldSlotCount * oldSlotCount
    val newMatrixPerNozzle = slotCount * slotCount
    val matrix = MutableList(newMatrixPerNozzle * multipliers.size) { 0.0 }
    multipliers.indices.forEach { nozzleIndex ->
        slots.forEachIndexed { fromIndex, fromSlot ->
            slots.forEachIndexed { toIndex, toSlot ->
                val targetIndex = nozzleIndex * newMatrixPerNozzle + fromIndex * slotCount + toIndex
                matrix[targetIndex] = when {
                    fromIndex == toIndex -> 0.0
                    fromIndex < oldSlotCount && toIndex < oldSlotCount && nozzleIndex < existing.multipliers.size -> {
                        val sourceIndex = nozzleIndex * oldMatrixPerNozzle + fromIndex * oldSlotCount + toIndex
                        existing.matrix.getOrNull(sourceIndex) ?: colorAwareFlushVolume(fromSlot.colorHex, toSlot.colorHex)
                    }
                    else -> colorAwareFlushVolume(fromSlot.colorHex, toSlot.colorHex)
                }
            }
        }
    }
    return PlateFlushVolumes(
        slotCount = slotCount,
        multipliers = multipliers,
        matrix = matrix
    ).normalized()
}

private fun colorAwareFlushVolumesMatrix(
    slots: List<PlateFilamentSlot>,
    nozzleCount: Int
): List<Double> {
    val slotCount = slots.size.coerceAtLeast(1)
    return List(slotCount * slotCount * nozzleCount.coerceAtLeast(1)) { index ->
        val localIndex = index % (slotCount * slotCount)
        val fromIndex = localIndex / slotCount
        val toIndex = localIndex % slotCount
        if (fromIndex == toIndex) {
            0.0
        } else {
            colorAwareFlushVolume(slots[fromIndex].colorHex, slots[toIndex].colorHex)
        }
    }
}

private fun colorAwareFlushVolume(fromHex: String, toHex: String): Double {
    val from = parseHexColorRgb(fromHex) ?: return 280.0
    val to = parseHexColorRgb(toHex) ?: return 280.0
    val luminanceDelta = kotlin.math.abs(to.luminance - from.luminance)
    val colorDistance = kotlin.math.sqrt(
        square(to.red - from.red) + square(to.green - from.green) + square(to.blue - from.blue)
    ) / 441.67295593
    val darkerTargetPenalty = (from.luminance - to.luminance).coerceAtLeast(0.0) * 70.0
    return (180.0 + colorDistance * 180.0 + luminanceDelta * 120.0 + darkerTargetPenalty)
        .coerceIn(140.0, 420.0)
}

private data class SlotRgb(
    val red: Double,
    val green: Double,
    val blue: Double
) {
    val luminance: Double = (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255.0
}

private fun parseHexColorRgb(hex: String): SlotRgb? {
    val normalized = hex.trim().removePrefix("#")
    if (normalized.length != 6) return null
    val value = normalized.toIntOrNull(radix = 16) ?: return null
    return SlotRgb(
        red = ((value shr 16) and 0xFF).toDouble(),
        green = ((value shr 8) and 0xFF).toDouble(),
        blue = (value and 0xFF).toDouble()
    )
}

private fun square(value: Double): Double = value * value

private fun JSONObject.nativePhysicalNozzleCount(): Int {
    val nozzleValue = opt("nozzle_diameter")
    val nozzleCount = when (nozzleValue) {
        is JSONArray -> nozzleValue.length()
        is String -> nozzleValue.split(',', ';').map { it.trim() }.count { it.isNotBlank() }
        else -> 0
    }
    if (nozzleCount > 0) return nozzleCount
    return when (val extruders = opt(NativeConfigKeys.Printer.ExtrudersCount)) {
        is Number -> extruders.toInt()
        is String -> extruders.toIntOrNull() ?: 1
        else -> 1
    }.coerceAtLeast(1)
}

internal data class PrinterMaterialSlotState(
    val slots: List<PlateFilamentSlot>,
    val flushVolumes: PlateFlushVolumes?
)

private const val PrinterMaterialSlotsPreferences = "printer_material_slots"

internal fun loadPrinterMaterialSlotState(
    context: Context,
    printerId: String,
    availableFilaments: List<FilamentProfile>,
    fallbackFilament: FilamentProfile
): PrinterMaterialSlotState {
    val stored = context.getSharedPreferences(PrinterMaterialSlotsPreferences, Context.MODE_PRIVATE)
        .getString("slots_$printerId", null)
    val filamentsById = availableFilaments.associateBy { it.id }
    val loadedState = runCatching {
        if (stored.isNullOrBlank()) {
            PrinterMaterialSlotState(emptyList(), null)
        } else {
            val root = stored.trim()
            val slotsArray = if (root.startsWith("{")) {
                JSONObject(root).optJSONArray("slots") ?: JSONArray()
            } else {
                JSONArray(root)
            }
            val flushVolumes = if (root.startsWith("{")) {
                JSONObject(root).optJSONObject("flushVolumes")?.toPlateFlushVolumes()
            } else {
                null
            }
            val slots = List(slotsArray.length()) { index ->
                val json = slotsArray.getJSONObject(index)
                val slotIndex = json.optInt("index", index + 1).coerceAtLeast(1)
                val filamentId = json.optString("filamentProfileId")
                val filament = filamentsById[filamentId]
                if (filament != null) {
                    filament.toPlateFilamentSlot(index = slotIndex).copy(
                        colorHex = json.optString("colorHex", filament.defaultFilamentColor.ifBlank { "#8FC1FF" })
                    ).copy(
                        physicalNozzleIndex = json.optInt("physicalNozzleIndex", 0).takeIf { it > 0 }
                    )
                } else {
                    PlateFilamentSlot(
                        index = slotIndex,
                        filamentProfileId = filamentId,
                        label = json.optString("label", fallbackFilament.name),
                        materialType = json.optString("materialType", fallbackFilament.materialType),
                        colorHex = json.optString("colorHex", fallbackFilament.defaultFilamentColor.ifBlank { "#8FC1FF" }),
                        physicalNozzleIndex = json.optInt("physicalNozzleIndex", 0).takeIf { it > 0 }
                    )
                }
            }
            PrinterMaterialSlotState(slots, flushVolumes)
        }
    }.getOrDefault(PrinterMaterialSlotState(emptyList(), null))

    val slots = loadedState.slots
        .ifEmpty { listOf(fallbackFilament.toPlateFilamentSlot(index = 1)) }
        .sortedBy { it.index }
        .mapIndexed { index, slot -> slot.copy(index = index + 1) }
    return PrinterMaterialSlotState(
        slots = slots,
        flushVolumes = loadedState.flushVolumes
    )
}

internal fun persistPrinterMaterialSlotState(
    context: Context,
    printerId: String,
    slots: List<PlateFilamentSlot>,
    flushVolumes: PlateFlushVolumes?
) {
    if (printerId.isBlank()) return
    val slotsArray = JSONArray().apply {
        slots.sortedBy { it.index }.forEach { slot ->
            put(
                JSONObject()
                    .put("index", slot.index)
                    .put("filamentProfileId", slot.filamentProfileId)
                    .put("label", slot.label)
                    .put("materialType", slot.materialType)
                    .put("colorHex", slot.colorHex)
                    .also { slotJson ->
                        slot.physicalNozzleIndex?.let { slotJson.put("physicalNozzleIndex", it) }
                    }
            )
        }
    }
    val json = JSONObject()
        .put("slots", slotsArray)
        .also { root ->
            flushVolumes?.normalized()?.let { root.put("flushVolumes", it.toJson()) }
        }
    context.getSharedPreferences(PrinterMaterialSlotsPreferences, Context.MODE_PRIVATE)
        .edit()
        .putString("slots_$printerId", json.toString())
        .apply()
}

private fun PlateFlushVolumes.toJson(): JSONObject = JSONObject()
    .put("slotCount", slotCount)
    .put("multipliers", JSONArray().apply {
        multipliers.forEach { put(it) }
    })
    .put("matrix", JSONArray().apply {
        matrix.forEach { put(it) }
    })

private fun JSONObject.toPlateFlushVolumes(): PlateFlushVolumes =
    PlateFlushVolumes(
        slotCount = optInt("slotCount", 1).coerceAtLeast(1),
        multipliers = optJSONArray("multipliers")?.let { array ->
            List(array.length()) { index -> array.optDouble(index, 0.3) }
        }.orEmpty(),
        matrix = optJSONArray("matrix")?.let { array ->
            List(array.length()) { index -> array.optDouble(index, 0.0) }
        }.orEmpty()
    ).normalized()

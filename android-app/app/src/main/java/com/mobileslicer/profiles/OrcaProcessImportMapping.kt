package com.mobileslicer.profiles

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal fun OrcaPrinterImportBundle.toImportedProcessProfiles(printer: PrinterProfile): List<ProcessProfile> =
    processPresets
        .filter { process -> kotlin.math.abs(process.nozzleDiameterMm - printer.nozzleDiameterMm) < 0.001f }
        .map { process -> process.toImportedProcessProfile(printer) }

internal fun OrcaProcessPresetBundle.toImportedProcessProfile(printer: PrinterProfile): ProcessProfile {
    val resolved = runCatching { JSONObject(resolvedProcessJson) }.getOrElse { JSONObject() }
    val printSpeed = resolved.processConfigInt("print_speed", 60)
    return newProcessProfileUnchecked(
        0 to "orca_process_${"${printer.id}|$profilePath|$nozzleDiameterMm".hashCode().toUInt().toString(16)}",
        1 to name,
        2 to "",
        3 to false,
        4 to resolved.processConfigFloatAny(
            listOf("initial_layer_print_height", "first_layer_height"),
            resolved.processConfigFloat("layer_height", 0.2f)
        ),
        5 to resolved.processConfigFloat("layer_height", 0.2f),
        6 to resolved.processConfigFloatAny(
            listOf("initial_layer_speed", "first_layer_print_speed"),
            derivedFirstLayerPrintSpeedMmPerSec(printSpeed)
        ),
        7 to resolved.processConfigFloatAny(
            listOf("initial_layer_infill_speed", "first_layer_infill_speed"),
            derivedFirstLayerInfillSpeedMmPerSec(printSpeed)
        ),
        8 to resolved.processConfigPercentIntAny(
            listOf("initial_layer_travel_speed", "initial_layer_travel_speed_percent"),
            DEFAULT_FIRST_LAYER_TRAVEL_SPEED_PERCENT
        ),
        9 to resolved.processConfigInt("slow_down_layers", DEFAULT_SLOW_DOWN_LAYERS),
        10 to resolved.processConfigFloat("initial_layer_acceleration", DEFAULT_INITIAL_LAYER_ACCELERATION_MM_PER_SEC2),
        11 to resolved.processConfigFloat("initial_layer_jerk", DEFAULT_INITIAL_LAYER_JERK_MM_PER_SEC),
        12 to resolved.processConfigFloat("first_layer_flow_ratio", DEFAULT_FIRST_LAYER_FLOW_RATIO),
        13 to resolved.processConfigString("print_extruder_id", "1"),
        14 to resolved.processConfigString("print_extruder_variant", printer.printerExtruderVariant.ifBlank { "Direct Drive Standard" }),
        15 to resolved.processConfigFloat("outer_wall_speed", derivedOuterWallSpeedMmPerSec(printSpeed)),
        16 to resolved.processConfigFloat("inner_wall_speed", derivedInnerWallSpeedMmPerSec(printSpeed)),
        17 to resolved.processConfigFloat("top_surface_speed", derivedTopSurfaceSpeedMmPerSec(printSpeed)),
        18 to resolved.processConfigFloat("travel_speed", derivedTravelSpeedMmPerSec(printSpeed)),
        19 to resolved.processConfigFloat("default_acceleration", DEFAULT_DEFAULT_ACCELERATION_MM_PER_SEC2),
        20 to resolved.processConfigFloat("outer_wall_acceleration", DEFAULT_OUTER_WALL_ACCELERATION_MM_PER_SEC2),
        21 to resolved.processConfigFloat("inner_wall_acceleration", DEFAULT_INNER_WALL_ACCELERATION_MM_PER_SEC2),
        22 to resolved.processConfigFloat("top_surface_acceleration", DEFAULT_TOP_SURFACE_ACCELERATION_MM_PER_SEC2),
        23 to resolved.processConfigFloat("sparse_infill_acceleration", DEFAULT_SPARSE_INFILL_ACCELERATION_MM_PER_SEC2),
        24 to resolved.processConfigString("internal_solid_infill_acceleration", "100%"),
        25 to resolved.processConfigFloat("travel_acceleration", 10000f),
        26 to resolved.processConfigBoolean("accel_to_decel_enable", false),
        27 to resolved.processConfigInt("accel_to_decel_factor", 50),
        28 to resolved.processConfigFloat("default_junction_deviation", 0f),
        29 to resolved.processConfigFloat("default_jerk", 0f),
        30 to resolved.processConfigFloat("inner_wall_jerk", DEFAULT_INNER_WALL_JERK_MM_PER_SEC),
        31 to resolved.processConfigFloat("infill_jerk", 9f),
        32 to resolved.processConfigFloat("top_surface_jerk", 9f),
        33 to resolved.processConfigFloat("travel_jerk", 12f),
        34 to resolved.processConfigFloat("inner_wall_flow_ratio", DEFAULT_INNER_WALL_FLOW_RATIO),
        35 to resolved.processConfigFloat("outer_wall_jerk", DEFAULT_OUTER_WALL_JERK_MM_PER_SEC),
        36 to resolved.processConfigFloat("outer_wall_flow_ratio", DEFAULT_OUTER_WALL_FLOW_RATIO),
        37 to resolved.processConfigFloat("top_solid_infill_flow_ratio", DEFAULT_TOP_SOLID_INFILL_FLOW_RATIO),
        38 to resolved.processConfigFloat("bottom_solid_infill_flow_ratio", DEFAULT_BOTTOM_SOLID_INFILL_FLOW_RATIO),
        39 to resolved.processConfigString("overhang_1_4_speed", DEFAULT_OVERHANG_1_4_SPEED),
        40 to resolved.processConfigString("overhang_2_4_speed", DEFAULT_OVERHANG_2_4_SPEED),
        41 to resolved.processConfigString("overhang_3_4_speed", DEFAULT_OVERHANG_3_4_SPEED),
        42 to resolved.processConfigString("overhang_4_4_speed", DEFAULT_OVERHANG_4_4_SPEED),
        44 to resolved.processConfigBoolean("slowdown_for_curled_perimeters", true),
        45 to resolved.processConfigFloat("overhang_flow_ratio", DEFAULT_OVERHANG_FLOW_RATIO),
        47 to resolved.processConfigString("bridge_acceleration", "50%").ifBlank { "50%" },
        48 to resolved.processConfigFloat("bridge_speed", derivedBridgeSpeedMmPerSec(printSpeed)),
        49 to resolved.processConfigFloat("small_perimeter_speed", derivedSmallPerimeterSpeedMmPerSec(printSpeed)),
        50 to resolved.processConfigFloat("small_perimeter_threshold", DEFAULT_SMALL_PERIMETER_THRESHOLD_MM),
        51 to resolved.processConfigFloat("bridge_angle", 0f),
        52 to resolved.processConfigInt("bridge_density", 100),
        53 to resolved.processConfigFloat("bridge_flow", 1f),
        54 to resolved.processConfigBoolean("bridge_no_support", false),
        55 to ProcessQualitySurfaceDetails(
            onlyOneWallFirstLayer = resolved.processConfigBoolean("only_one_wall_first_layer", false),
            wallDirection = WallDirection.fromConfigValue(resolved.processConfigString("wall_direction", WallDirection.Auto.configValue))
        ),
        56 to resolved.processConfigFloat("internal_bridge_angle", 0f),
        57 to resolved.processConfigInt("internal_bridge_density", 100),
        58 to resolved.processConfigFloat("internal_bridge_flow", 1f),
        59 to resolved.processConfigString("internal_bridge_speed", "150%").ifBlank { "150%" },
        60 to resolved.processConfigString("internal_bridge_fan_speed", "100%"),
        61 to resolved.processConfigString("internal_bridge_support_thickness", "50%"),
        62 to resolved.processConfigFloat("max_volumetric_extrusion_rate_slope", 0f),
        63 to resolved.processConfigFloat("max_volumetric_extrusion_rate_slope_segment_length", 3f),
        64 to resolved.processConfigBoolean("extrusion_rate_smoothing_external_perimeter_only", false),
        67 to resolved.processConfigFloat("sparse_infill_speed", derivedSparseInfillSpeedMmPerSec(printSpeed)),
        68 to resolved.processConfigFloat("internal_solid_infill_speed", derivedInternalSolidInfillSpeedMmPerSec(printSpeed)),
        69 to resolved.processConfigFloat("gap_infill_speed", derivedGapInfillSpeedMmPerSec(printSpeed)),
        71 to resolved.processConfigInt("top_shell_layers", 6),
        72 to resolved.processConfigInt("bottom_shell_layers", 6),
        73 to resolved.processConfigFloat("top_shell_thickness", 0.6f),
        74 to resolved.processConfigFloat("bottom_shell_thickness", 0.6f),
        75 to resolved.processConfigInt("top_surface_density", 100),
        76 to resolved.processConfigInt("bottom_surface_density", 100),
        77 to ProcessSeamPosition.fromConfigValue(resolved.processConfigString("seam_position", ProcessSeamPosition.Aligned.configValue)),
        84 to resolved.processConfigBoolean("precise_outer_wall", true),
        85 to resolved.processConfigBoolean("only_one_wall_top", true),
        86 to TopSurfacePattern.fromConfigValue(resolved.processConfigString("top_surface_pattern", TopSurfacePattern.MonotonicLine.configValue)),
        87 to BottomSurfacePattern.fromConfigValue(resolved.processConfigString("bottom_surface_pattern", BottomSurfacePattern.MonotonicLine.configValue)),
        88 to InternalSolidInfillPattern.fromConfigValue(resolved.processConfigString("internal_solid_infill_pattern", InternalSolidInfillPattern.MonotonicLine.configValue)),
        91 to resolved.processConfigString("line_width", "0").ifBlank { "0" },
        92 to resolved.processConfigString("outer_wall_line_width", "0").ifBlank { "0" },
        93 to resolved.processConfigString("inner_wall_line_width", "0").ifBlank { "0" },
        94 to resolved.processConfigString("initial_layer_line_width", "0").ifBlank { "0" },
        96 to resolved.processConfigString("top_surface_line_width", "0").ifBlank { "0" },
        97 to resolved.processConfigString("internal_solid_infill_line_width", "0").ifBlank { "0" },
        99 to resolved.processConfigString("sparse_infill_line_width", "0").ifBlank { "0" },
        111 to resolved.processConfigFloat("elefant_foot_compensation", 0f),
        115 to WallGenerator.fromConfigValue(resolved.processConfigString("wall_generator", WallGenerator.Classic.configValue)),
        124 to resolved.processConfigInt("wall_loops", 2),
        125 to resolved.processConfigInt("sparse_infill_density", 15),
        126 to SparseInfillPattern.fromConfigValue(resolved.processConfigString("sparse_infill_pattern", SparseInfillPattern.Grid.configValue)),
        268 to ProcessStrengthInfillDetails(
            filterOutGapFillMm = resolved.processConfigFloat("filter_out_gap_fill", 0.5f)
        ),
        133 to resolved.processConfigBoolean("enable_support", DEFAULT_ENABLE_SUPPORT),
        134 to SupportType.fromConfigValue(resolved.processConfigString("support_type", SupportType.NormalAuto.configValue)),
        135 to SupportStyle.fromConfigValue(resolved.processConfigString("support_style", SupportStyle.Default.configValue)),
        136 to resolved.processConfigInt("support_threshold_angle", DEFAULT_SUPPORT_THRESHOLD_ANGLE_DEGREES),
        138 to resolved.processConfigBoolean("support_on_build_plate_only", DEFAULT_SUPPORT_BUILDPLATE_ONLY),
        188 to resolved.processConfigBoolean("enable_prime_tower", DEFAULT_ENABLE_PRIME_TOWER),
        189 to resolved.processConfigFloat("prime_tower_width", DEFAULT_PRIME_TOWER_WIDTH_MM),
        207 to ProcessPrimeTowerDetails(
            wipeTowerXmm = resolved.processConfigFloat("wipe_tower_x", 15f),
            wipeTowerYmm = resolved.processConfigFloat("wipe_tower_y", 220f),
            primeTowerSkipPoints = resolved.processConfigBoolean("prime_tower_skip_points", true),
            primeVolumeMm3 = resolved.processConfigFloat("prime_volume", 45f),
            primeTowerBrimWidthMm = resolved.processConfigFloat("prime_tower_brim_width", 3f),
            primeTowerInfillGapPercent = resolved.processConfigInt("prime_tower_infill_gap", 150),
            wipeTowerRotationAngleDegrees = resolved.processConfigFloat("wipe_tower_rotation_angle", 0f),
            wipeTowerBridgingMm = resolved.processConfigFloat("wipe_tower_bridging", 10f),
            wipeTowerExtraSpacingPercent = resolved.processConfigInt("wipe_tower_extra_spacing", 100),
            wipeTowerExtraFlowPercent = resolved.processConfigInt("wipe_tower_extra_flow", 100),
            wipeTowerMaxPurgeSpeedMmPerSec = resolved.processConfigFloat("wipe_tower_max_purge_speed", 90f),
            wipeTowerWallType = WipeTowerWallType.fromConfigValue(resolved.processConfigString("wipe_tower_wall_type", WipeTowerWallType.Rectangle.configValue)),
            wipeTowerConeAngleDegrees = resolved.processConfigFloat("wipe_tower_cone_angle", 30f),
            wipeTowerExtraRibLengthMm = resolved.processConfigFloat("wipe_tower_extra_rib_length", 0f),
            wipeTowerRibWidthMm = resolved.processConfigFloat("wipe_tower_rib_width", 8f),
            wipeTowerFilletWall = resolved.processConfigBoolean("wipe_tower_fillet_wall", true)
        ),
        194 to resolved.processConfigInt("standby_temperature_delta", DEFAULT_STANDBY_TEMPERATURE_DELTA_C),
        195 to resolved.processConfigBoolean("wipe_tower_no_sparse_layers", DEFAULT_WIPE_TOWER_NO_SPARSE_LAYERS),
        199 to resolved.processConfigIntAny(listOf("skirt_loops", "skirts"), 2),
        200 to resolved.processConfigFloat("brim_width", 0f),
        269 to ProcessGcodeOutputDetails(
            gcodeLabelObjects = resolved.processConfigBoolean("gcode_label_objects", false),
            excludeObject = resolved.processConfigBoolean("exclude_object", false)
        ),
        258 to "orca",
        259 to printer.id,
        260 to printer.printerVariant,
        261 to printer.nozzleDiameterMm,
        262 to printer.orcaFamily,
        263 to profilePath,
        264 to rawProcessJson,
        265 to resolvedProcessJson,
        266 to resolvedSourceChain
    )
}

private fun JSONObject.processConfigString(key: String, defaultValue: String = ""): String {
    if (!has(key) || isNull(key)) return defaultValue
    val value = opt(key)
    return when (value) {
        is JSONArray -> if (value.length() == 1) value.optString(0, defaultValue) else value.toString()
        else -> value?.toString().orEmpty().ifBlank { defaultValue }
    }
}

private fun JSONObject.processConfigFloat(key: String, defaultValue: Float): Float {
    val value = processConfigScalar(key) ?: return defaultValue
    return value.toString().trim().toFloatOrNull() ?: defaultValue
}

private fun JSONObject.processConfigFloatAny(keys: List<String>, defaultValue: Float): Float {
    keys.forEach { key ->
        val value = processConfigScalar(key) ?: return@forEach
        value.toString().trim().trimEnd('%').toFloatOrNull()?.let { return it }
    }
    return defaultValue
}

private fun JSONObject.processConfigInt(key: String, defaultValue: Int): Int {
    val value = processConfigScalar(key) ?: return defaultValue
    return value.toString().trim().toIntOrNull()
        ?: value.toString().trim().toFloatOrNull()?.toInt()
        ?: defaultValue
}

private fun JSONObject.processConfigIntAny(keys: List<String>, defaultValue: Int): Int {
    keys.forEach { key ->
        val value = processConfigScalar(key) ?: return@forEach
        value.toString().trim().toIntOrNull()?.let { return it }
        value.toString().trim().toFloatOrNull()?.toInt()?.let { return it }
    }
    return defaultValue
}

private fun JSONObject.processConfigPercentIntAny(keys: List<String>, defaultValue: Int): Int {
    keys.forEach { key ->
        val value = processConfigScalar(key) ?: return@forEach
        value.toString().trim().trimEnd('%').toFloatOrNull()?.toInt()?.let { return it }
    }
    return defaultValue
}

private fun JSONObject.processConfigBoolean(key: String, defaultValue: Boolean): Boolean {
    val value = processConfigScalar(key) ?: return defaultValue
    return when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        else -> when (value.toString().trim().lowercase(Locale.US)) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> defaultValue
        }
    }
}

private fun JSONObject.processConfigScalar(key: String): Any? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key)
    return if (value is JSONArray) {
        if (value.length() == 0) null else value.opt(0)
    } else {
        value
    }
}

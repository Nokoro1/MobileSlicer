package com.mobileslicer.profiles

import org.json.JSONObject

internal fun JSONObject.removeUnresolvedMobileProcessDefaults(processJson: JSONObject?) {
    removeIfUnresolvedDefault(processJson, "brim_ears", "0", "false")
    removeIfUnresolvedDefault(processJson, "combine_brims", "0", "false")
    removeIfUnresolvedDefault(processJson, "support_object_elevation", "0", "0.0")
}

internal fun JSONObject.applyOrcaProcessTemplateDefaults(printer: PrinterProfile, process: ProcessProfile) {
    val processKeys = process.resolvedOrcaProcessKeySet()
    putIfBlankOrValue("accel_to_decel_enable", "0", "1")
    putIfBlankOrValue("accel_to_decel_enable", "0", "true")
    putIfBlankOrValue(NativeConfigKeys.Process.WallDirection, "auto", "ccw")
    putIfBlankOrValue(NativeConfigKeys.Process.WallDirection, "auto", "clockwise")
    putIfBlankOrValue(NativeConfigKeys.Process.WallDirection, "auto", "counter-clockwise")
    putIfBlankOrZero("bridge_density", "100%")
    putIfBlankOrZero("bridge_speed", "50")
    putIfBlankOrValue("filter_out_gap_fill", "0.5", "0")
    putIfBlankOrValue("filter_out_gap_fill", "0.5", "0.0")
    if (printer.printerAgent.equals("qidi", ignoreCase = true) || printer.orcaFamily.equals("Qidi", ignoreCase = true)) {
        putIfBlankOrZero("filter_out_gap_fill", "2")
        putIfBlankOrValue("filter_out_gap_fill", "2", "0.5")
    }
    if (printer.hasFlashforgeContext() || process.hasFlashforgeContext()) {
        putIfBlankOrValue("internal_solid_infill_acceleration", "7000", "100%")
        putIfBlankOrValue("wipe_speed", "200", "80%")
    }
    putIfBlankOrZero("initial_layer_min_bead_width", "85%")
    putIfBlankOrZero("internal_bridge_density", "100%")
    putIfBlankOrZero("internal_bridge_speed", "50")
    if ("max_bridge_length" !in processKeys) {
        putIfBlankOrZero("max_bridge_length", "10")
    }
    putIfBlankOrValue("ooze_prevention", "1", "0")
    putIfBlankOrValue("ooze_prevention", "1", "false")
    putIfBlankOrValue("prime_volume", "15", "45")
    putIfBlankOrValue("prime_volume", "15", "45.0")
    putIfBlankOrValue("wipe_tower_wall_type", "cone", "rectangle")
    putIfBlankOrValue(NativeConfigKeys.Process.WipeTowerConeAngle, "15", "30")
    putIfBlankOrValue(NativeConfigKeys.Process.WipeTowerConeAngle, "15", "30.0")
    putIfBlankOrValue(NativeConfigKeys.Process.WipeTowerX, "165", "15")
    putIfBlankOrValue(NativeConfigKeys.Process.WipeTowerX, "165", "15.0")
    putIfBlankOrValue(NativeConfigKeys.Process.WipeTowerY, "250", "220")
    putIfBlankOrValue(NativeConfigKeys.Process.WipeTowerY, "250", "220.0")
    putIfBlank("initial_layer_travel_speed", "100%")
    putIfBlankOrValue("internal_solid_infill_pattern", "monotonic", "monotonicline")
    putIfBlankOrZero("resolution", "0.001")
    putIfBlankOrZero("small_perimeter_speed", "50%")
    putIfBlankOrValue("small_perimeter_speed", "50%", "15")
    putIfBlankOrValue("small_perimeter_speed", "50%", "15.0")
    if (printer.printerAgent.equals("qidi", ignoreCase = true) || printer.orcaFamily.equals("Qidi", ignoreCase = true)) {
        putIfBlankOrZero("small_perimeter_threshold", "4")
    }
    putIfBlankOrZero("solid_infill_direction", "45")
    putIfBlankOrValue("sparse_infill_acceleration", "100%", "500")
    putIfBlankOrValue("sparse_infill_acceleration", "100%", "500.0")
    putIfBlankOrZero("support_bottom_interface_spacing", "0.5")
    putIfBlankOrZero("support_bottom_z_distance", "0.2")
    putIfBlankOrValue("support_interface_speed", "80", "100")
    putIfBlankOrValue("support_interface_speed", "80", "100.0")
    putIfBlankOrZero("support_ironing_flow", "10%")
    putIfBlankOrZero("support_ironing_spacing", "0.1")
    putIfBlankOrValue("support_object_xy_distance", "0.5", "50")
    putIfBlankOrValue("support_object_xy_distance", "0.5", "50%")
    putIfBlankOrZero("top_bottom_infill_wall_overlap", "25%")
    if ("detect_narrow_internal_solid_infill" !in processKeys) {
        putIfBlankOrValue("detect_narrow_internal_solid_infill", "1", "0")
        putIfBlankOrValue("detect_narrow_internal_solid_infill", "1", "false")
    }
    if ("ensure_vertical_shell_thickness" !in processKeys) {
        putIfBlankOrValue("ensure_vertical_shell_thickness", "ensure_all", "none")
    }
    if (printer.hasBambuContext() || hasBambuContext()) {
        applyBambuProcessInfillSkinDefaults(processKeys, process)
    }
    if (!printer.hasBambuContext() && !hasBambuContext() && "only_one_wall_top" !in processKeys) {
        putIfBlankOrValue("only_one_wall_top", "0", "1")
        putIfBlankOrValue("only_one_wall_top", "0", "true")
    }
    if ("default_jerk" in processKeys) {
        putIfBlankOrValue("default_jerk", "9", "0")
        putIfBlankOrValue("default_jerk", "9", "0.0")
    }
    if ("top_surface_jerk" in processKeys) {
        putIfBlankOrValue("top_surface_jerk", "7", "9")
        putIfBlankOrValue("top_surface_jerk", "7", "9.0")
    }
    if ("travel_jerk" in processKeys) {
        putIfBlankOrValue("travel_jerk", "9", "12")
        putIfBlankOrValue("travel_jerk", "9", "12.0")
    }
    putIfBlankOrValue(NativeConfigKeys.Process.WallDirection, "auto", "ccw")
    putIfBlankOrValue(NativeConfigKeys.Process.WallDirection, "auto", "counterclockwise")
    if (!printer.hasBambuContext() && !hasBambuContext() && "wall_generator" !in processKeys) {
        putIfBlankOrValue("wall_generator", "arachne", "classic")
    }
}

private fun ProcessProfile.resolvedOrcaProcessKeySet(): Set<String> =
    buildSet {
        runCatching { JSONObject(orcaResolvedProcessJson) }.getOrNull()?.keys()?.forEach { add(it) }
        runCatching { JSONObject(orcaProcessOverridesJson) }.getOrNull()?.keys()?.forEach { add(it) }
    }

private fun PrinterProfile.hasFlashforgeContext(): Boolean =
    printerAgent.equals("flashforge", ignoreCase = true) ||
        printerModel.contains("flashforge", ignoreCase = true) ||
        name.contains("flashforge", ignoreCase = true) ||
        orcaFamily.equals("Flashforge", ignoreCase = true) ||
        orcaMachineModelPath.contains("Flashforge", ignoreCase = true)

private fun ProcessProfile.hasFlashforgeContext(): Boolean =
    name.contains("Flashforge", ignoreCase = true) ||
        printerVariantKey.contains("Flashforge", ignoreCase = true) ||
        orcaFamily.equals("Flashforge", ignoreCase = true) ||
        orcaProcessPath.contains("Flashforge", ignoreCase = true) ||
        orcaResolvedSourceChain.any { it.contains("Flashforge", ignoreCase = true) }

private fun JSONObject.applyBambuProcessInfillSkinDefaults(processKeys: Set<String>, process: ProcessProfile) {
    val printSettingsId = scalarString(NativeConfigKeys.Process.SettingsId).ifBlank { process.name }
    val preserveStrengthSkin = printSettingsId.contains("Strength", ignoreCase = true)
    if (!preserveStrengthSkin || "skin_infill_density" !in processKeys) {
        putIfBlankOrValue("skin_infill_density", "15%", "25")
        putIfBlankOrValue("skin_infill_density", "15%", "25%")
    }
    if (!preserveStrengthSkin || "skeleton_infill_density" !in processKeys) {
        putIfBlankOrValue("skeleton_infill_density", "15%", "25")
        putIfBlankOrValue("skeleton_infill_density", "15%", "25%")
    }
    val defaultLineWidth = bambuDefaultInfillSkinLineWidth()
    if ("skin_infill_line_width" !in processKeys ||
        scalarString("skin_infill_line_width") == "100%"
    ) {
        putIfBlankOrValue("skin_infill_line_width", defaultLineWidth, "100%")
    }
    if ("skeleton_infill_line_width" !in processKeys ||
        scalarString("skeleton_infill_line_width") == "100%"
    ) {
        putIfBlankOrValue("skeleton_infill_line_width", defaultLineWidth, "100%")
    }
}

private fun JSONObject.bambuDefaultInfillSkinLineWidth(): String {
    val nozzleDiameter = scalarString(NativeConfigKeys.Printer.NozzleDiameter).toDoubleOrNull() ?: return "0.45"
    return when {
        nozzleDiameter <= 0.25 -> "0.22"
        nozzleDiameter <= 0.45 -> "0.45"
        nozzleDiameter <= 0.65 -> "0.62"
        else -> "0.82"
    }
}

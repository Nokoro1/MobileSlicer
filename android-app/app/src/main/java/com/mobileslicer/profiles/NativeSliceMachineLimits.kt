package com.mobileslicer.profiles

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.applyOrcaPrinterHardwareDefaults(printer: PrinterProfile) {
    if (printer.singleExtruderMultiMaterial && scalarString(NativeConfigKeys.Printer.ExtruderAmsCount).isBlank()) {
        put(NativeConfigKeys.Printer.ExtruderAmsCount, "1#0|4#0;1#0|4#0")
    }
    if (printer.hasQidiQ2Context()) {
        if (scalarString(NativeConfigKeys.Printer.ExtruderAmsCount).isBlank() ||
            scalarString(NativeConfigKeys.Printer.ExtruderAmsCount) == "1#0|4#0;"
        ) {
            put(NativeConfigKeys.Printer.ExtruderAmsCount, "1#0|4#0;1#0|4#0")
        }
        putIfBlankOrValue(NativeConfigKeys.Process.WipeTowerType, "type1", "type2")
        putIfBlankOrValue(NativeConfigKeys.Process.WipeTowerX, "165", "15")
        putIfBlankOrValue(NativeConfigKeys.Process.WipeTowerX, "165", "221.5")
        putIfBlankOrValue(NativeConfigKeys.Process.WipeTowerX, "165", "221.500")
        putIfBlankOrValue(NativeConfigKeys.Process.WipeTowerY, "250", "220")
        putIfBlankOrValue(NativeConfigKeys.Process.WipeTowerY, "250", "13.5")
        putIfBlankOrValue(NativeConfigKeys.Process.WipeTowerY, "250", "13.500")
    }
    putIfBlankOrValue(NativeConfigKeys.Printer.MachineMaxJunctionDeviation, "0.01", "0.013")
    putIfBlankOrValue(NativeConfigKeys.Printer.MachineMaxJunctionDeviation, "0.01", "0.013000000268220901")
}

internal fun JSONObject.expandOrcaMachineLimitVectors() {
    val keys = setOf(
        "machine_max_speed_x",
        "machine_max_speed_y",
        "machine_max_speed_z",
        "machine_max_speed_e",
        "machine_max_acceleration_x",
        "machine_max_acceleration_y",
        "machine_max_acceleration_z",
        "machine_max_acceleration_e",
        "machine_max_acceleration_extruding",
        "machine_max_acceleration_retracting",
        "machine_max_acceleration_travel",
        "machine_max_jerk_x",
        "machine_max_jerk_y",
        "machine_max_jerk_z",
        "machine_max_jerk_e"
    )
    keys.forEach { key ->
        val value = opt(key) ?: return@forEach
        if (nativeArrayLength(value) >= 2) return@forEach
        val scalar = nativeExpansionScalar(value)
        put(key, JSONArray().put(scalar).put(scalar))
    }
}

private fun PrinterProfile.hasQidiQ2Context(): Boolean =
    printerAgent.equals("qidi", ignoreCase = true) &&
        printerModel.equals("Qidi Q2", ignoreCase = true) ||
        orcaFamily.equals("Qidi", ignoreCase = true) &&
        (
            printerModel.equals("Qidi Q2", ignoreCase = true) ||
                name.contains("Qidi Q2", ignoreCase = true) ||
                orcaMachineModelPath.contains("Qidi Q2", ignoreCase = true) ||
                orcaResolvedMachineJson.contains("Qidi Q2", ignoreCase = true)
            )

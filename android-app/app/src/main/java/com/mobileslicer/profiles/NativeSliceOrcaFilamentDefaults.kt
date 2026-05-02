package com.mobileslicer.profiles

import org.json.JSONObject

internal fun JSONObject.applyOrcaFilamentTemplateDefaults(filament: FilamentProfile) {
    val material = resolvedOrcaFilamentMaterial(filament)
    if (material.equals("ABS", ignoreCase = true) || material.equals("ASA", ignoreCase = true)) {
        putIfBlankOrValue("fan_min_speed", "10", "30")
        putIfBlankOrValue("fan_min_speed", "10", "80")
    }
    if (material.equals("ABS", ignoreCase = true) ||
        material.equals("ASA", ignoreCase = true) ||
        material.equals("PLA", ignoreCase = true)
    ) {
        putIfBlankOrValue(
            NativeConfigKeys.Temperature.TexturedCoolPlate,
            "40",
            scalarString(NativeConfigKeys.Temperature.HotPlate)
        )
        putIfBlankOrValue(
            NativeConfigKeys.Temperature.TexturedCoolPlateInitialLayer,
            "40",
            scalarString(NativeConfigKeys.Temperature.HotPlateInitialLayer)
        )
    }
    putIfBlank(NativeConfigKeys.Filament.DefaultColor, "\"\"")
    putIfBlank("volumetric_speed_coefficients", "\"\"")
}

internal fun JSONObject.applyOrcaFilamentIdentityDefaults(filament: FilamentProfile) {
    val material = resolvedOrcaFilamentMaterial(filament)
    if (material.isNotBlank()) {
        val currentType = scalarString(NativeConfigKeys.Filament.Type)
        if (currentType.isBlank() ||
            currentType == "0" ||
            currentType.equals("PLA", ignoreCase = true) && !material.equals("PLA", ignoreCase = true)
        ) {
            put(NativeConfigKeys.Filament.Type, material)
        }
    }
    val density = material.defaultFilamentDensity()
    if (density.isNotBlank()) {
        putIfBlankOrZero(NativeConfigKeys.Filament.Density, density)
    }
    val volumetricSpeed = material.defaultOrcaVolumetricSpeed()
    if (volumetricSpeed.isNotBlank()) {
        val currentSpeed = scalarString(NativeConfigKeys.Filament.MaxVolumetricSpeed).toDoubleOrNull()
        if (currentSpeed == null || currentSpeed <= 0.0 || currentSpeed == 2.0 && !material.equals("PLA", ignoreCase = true)) {
            put(NativeConfigKeys.Filament.MaxVolumetricSpeed, volumetricSpeed)
        }
    }
}

private fun JSONObject.resolvedOrcaFilamentMaterial(filament: FilamentProfile): String =
    sequenceOf(
        scalarString(NativeConfigKeys.Filament.SettingsId),
        scalarString(NativeConfigKeys.Filament.Ids),
        optString(NativeConfigKeys.Printer.Name),
        optString(NativeConfigKeys.Printer.Inherits),
        filament.orcaFilamentPath,
        filament.name,
        filament.materialType,
        scalarString(NativeConfigKeys.Filament.Type)
    )
        .mapNotNull { it.detectFilamentMaterial() }
        .firstOrNull()
        .orEmpty()

private fun String.defaultFilamentDensity(): String =
    when (uppercase(java.util.Locale.US)) {
        "PLA", "PLA+" -> "1.24"
        "PETG" -> "1.27"
        "ABS" -> "1.04"
        "ASA" -> "1.07"
        "TPU" -> "1.20"
        "PA", "PA-CF" -> "1.12"
        "PC" -> "1.20"
        "PLA-CF" -> "1.30"
        "PETG-CF" -> "1.30"
        else -> ""
    }

private fun String.defaultOrcaVolumetricSpeed(): String =
    when (uppercase(java.util.Locale.US)) {
        "ABS", "ASA" -> "17"
        "PETG", "PETG-CF" -> "12"
        "PLA", "PLA+" -> "12"
        "PLA-CF" -> "12"
        "TPU" -> "3.2"
        "PA", "PA-CF", "PC" -> "12"
        else -> ""
    }

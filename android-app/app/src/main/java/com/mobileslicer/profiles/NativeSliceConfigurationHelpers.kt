package com.mobileslicer.profiles

import org.json.JSONObject
import org.json.JSONArray

internal fun JSONObject.restoreResolvedOrcaParityValues(
    printerJson: JSONObject?,
    filamentJson: JSONObject?,
    processJson: JSONObject?
) {
    copyResolvedValues(printerJson, resolvedPrinterParityKeys)
    copyResolvedValues(filamentJson, resolvedFilamentParityKeys)
    copyResolvedValues(processJson, resolvedProcessParityKeys)
    processJson?.opt(NativeConfigKeys.Compatibility.CompatiblePrinters)?.let { compatiblePrinters ->
        put(NativeConfigKeys.Compatibility.PrintCompatiblePrinters, compatiblePrinters)
    }
    filamentJson?.scalarString(NativeConfigKeys.Filament.Id)
        ?.takeIf { it.isNotBlank() && !hasNonBlankNativeScalar(NativeConfigKeys.Filament.Ids) }
        ?.let { filamentId ->
        put(NativeConfigKeys.Filament.Ids, filamentId)
    }
    if (optString(NativeConfigKeys.Bed.Shape).isBlank()) {
        printerJson?.opt(NativeConfigKeys.Bed.PrintableArea).toNativeConfigListString()?.let {
            put(NativeConfigKeys.Bed.Shape, it)
        }
    }
    val resolvedDefaultBedType = normalizedOrcaBedTypeName(printerJson?.scalarString(NativeConfigKeys.Bed.DefaultType).orEmpty())
    if (resolvedDefaultBedType in knownOrcaBedTypes &&
        printerJson?.hasNonBlankNativeScalar(NativeConfigKeys.Bed.CurrentType) != true &&
        scalarString(NativeConfigKeys.Bed.CurrentType).isBlank()
    ) {
        put(NativeConfigKeys.Bed.CurrentType, resolvedDefaultBedType)
    }
}

private fun JSONObject.copyResolvedValues(source: JSONObject?, keys: Set<String>) {
    if (source == null) return
    keys.forEach { key ->
        if (source.has(key) && !source.isNull(key)) {
            if (source.nativeResolvedString(key).isBlank()) {
                if (key in resolvedProfileIdentityKeys) {
                    return@forEach
                }
                remove(key)
                return@forEach
            }
            put(key, source.opt(key))
        }
    }
}

internal fun JSONObject.hasNonBlankNativeScalar(key: String): Boolean =
    when (val value = opt(key)) {
        is JSONArray -> firstScalarString(value)?.isNotBlank() == true
        null -> false
        else -> value.toString().trim().trim('"').isNotBlank()
    }

internal fun JSONObject.putIfBlankOrZero(key: String, value: String) {
    val current = scalarString(key).trim()
    if (current.isBlank() || current == "0" || current == "0%" || current == "0.0") {
        put(key, value)
    }
}

internal fun JSONObject.putIfBlank(key: String, value: String) {
    if (scalarString(key).isBlank()) {
        put(key, value)
    }
}

internal fun JSONObject.putIfBlankOrValue(key: String, value: String, currentValue: String) {
    val current = scalarString(key)
    if (current.isBlank() || current == currentValue) {
        put(key, value)
    }
}

internal fun JSONObject.removeIfUnresolvedDefault(
    source: JSONObject?,
    key: String,
    vararg defaultValues: String
) {
    if (source?.has(key) == true) return
    removeIfDefaultValue(key, *defaultValues)
}

private fun JSONObject.removeIfDefaultValue(
    key: String,
    vararg defaultValues: String
) {
    val current = scalarString(key)
    if (current in defaultValues) {
        remove(key)
    }
}

package com.mobileslicer.profiles

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

internal fun ActiveSlicerConfiguration.toNativeSliceConfigJson(context: Context? = null): String {
    return toNativeSliceConfigBuildResult(context).json
}

internal fun ActiveSlicerConfiguration.toNativeSliceConfigBuildResult(context: Context? = null): NativeSliceConfigBuildResult {
    val totalStartedAtMs = elapsedRealtimeForConfigTiming()
    val keyStartedAtMs = elapsedRealtimeForConfigTiming()
    val cacheKey = NativeSliceConfigCacheKey(
        printerId = printer.id,
        filamentId = filament.id,
        processId = process.id,
        printerConfigSignature = printer.nativeConfigSignatureHash(),
        filamentConfigSignature = filament.nativeConfigSignatureHash(),
        processConfigSignature = process.nativeConfigSignatureHash(),
        assetBackedResolve = context != null
    )
    val keyMs = elapsedRealtimeForConfigTiming() - keyStartedAtMs
    NativeSliceConfigCache.get(cacheKey)?.let {
        return NativeSliceConfigBuildResult(
            json = it,
            cacheHit = true,
            timing = NativeSliceConfigTiming(
                keyMs = keyMs,
                totalMs = elapsedRealtimeForConfigTiming() - totalStartedAtMs
            )
        )
    }
    val profileJsonStartedAtMs = elapsedRealtimeForConfigTiming()
    val profileJsons = resolveNativeSliceProfileJsons(context)
    val printerJson = profileJsons.printerJson
    val filamentJson = profileJsons.filamentJson
    val processJson = profileJsons.processJson
    val printerOverridesJson = profileJsons.printerOverridesJson
    val filamentOverridesJson = profileJsons.filamentOverridesJson
    val processOverridesJson = profileJsons.processOverridesJson
    val profileJsonMs = elapsedRealtimeForConfigTiming() - profileJsonStartedAtMs
    val hasResolvedOrcaContext =
        printerJson != null && (
            printer.profileSource == "orca" ||
                printer.orcaMachineModelPath.isNotBlank() ||
                printer.orcaFamily.isNotBlank()
            ) ||
            filamentJson != null && (
                filament.profileSource == "orca" ||
                    filament.orcaFilamentPath.isNotBlank() ||
                    filament.orcaFamily.isNotBlank()
                ) ||
            processJson != null && (
                process.profileSource == "orca" ||
                    process.orcaProcessPath.isNotBlank() ||
                process.orcaFamily.isNotBlank()
                )

    val mergeStartedAtMs = elapsedRealtimeForConfigTiming()
    val json = profileJsons.mergeResolvedProfileJson()
    val mergeMs = elapsedRealtimeForConfigTiming() - mergeStartedAtMs

    val nativeDefaultsStartedAtMs = elapsedRealtimeForConfigTiming()
    val resolvedNativeToolCount = json.nativeToolCount()
    json.applyNativeMobileDefaults(this, profileJsons)
    val preserveOrcaResolvedValues = hasResolvedOrcaContext ||
        printerJson != null ||
        filamentJson != null ||
        processJson != null ||
        printer.profileSource == "orca" ||
        filament.profileSource == "orca" ||
        process.profileSource == "orca"
    if (preserveOrcaResolvedValues) {
        json.restoreResolvedOrcaParityValues(printerJson, filamentJson, processJson)
    }
    json.applySingleMaterialNativeSliceDefaults(
        preserveOrcaResolvedValues = preserveOrcaResolvedValues
    )
    json.applyBambuNativeSliceSafetyDefaults(
        printer = printer,
        preserveOrcaResolvedValues = preserveOrcaResolvedValues
    )
    if (preserveOrcaResolvedValues) {
        json.mergeJsonObject(printerJson)
        json.mergeJsonObject(filamentJson)
        json.mergeJsonObject(processJson)
        json.restoreResolvedOrcaParityValues(printerJson, filamentJson, processJson)
        json.applyOrcaProfileIdentityDefaults(printer, filament, process)
        json.restoreResolvedOrcaParityValues(printerJson, filamentJson, processJson)
        json.removeUnresolvedMobileProcessDefaults(processJson)
        json.applyFinalOrcaParityNormalization(printer, process)
        json.restoreResolvedOrcaParityValues(printerJson, filamentJson, processJson)
        json.applySingleMaterialNativeSliceDefaults(
            preserveOrcaResolvedValues = true
        )
    }
    val nativeDefaultsMs = elapsedRealtimeForConfigTiming() - nativeDefaultsStartedAtMs
    val normalizeStartedAtMs = elapsedRealtimeForConfigTiming()
    json.applyNativeFinalOverridesAndNormalization(
        profileJsons = profileJsons,
        preserveOrcaResolvedValues = preserveOrcaResolvedValues,
        resolvedNativeToolCount = resolvedNativeToolCount
    )
    val result = json.toString()
    val normalizeMs = elapsedRealtimeForConfigTiming() - normalizeStartedAtMs
    NativeSliceConfigCache.put(cacheKey, result)
    return NativeSliceConfigBuildResult(
        json = result,
        cacheHit = false,
        timing = NativeSliceConfigTiming(
            keyMs = keyMs,
            profileJsonMs = profileJsonMs,
            mergeMs = mergeMs,
            nativeDefaultsMs = nativeDefaultsMs,
            normalizeMs = normalizeMs,
            totalMs = elapsedRealtimeForConfigTiming() - totalStartedAtMs
        )
    )
}

private data class NativeSliceResolvedProfileJsons(
    val printerJson: JSONObject?,
    val filamentJson: JSONObject?,
    val processJson: JSONObject?,
    val printerOverridesJson: JSONObject?,
    val filamentOverridesJson: JSONObject?,
    val processOverridesJson: JSONObject?
)

private fun ActiveSlicerConfiguration.resolveNativeSliceProfileJsons(context: Context?): NativeSliceResolvedProfileJsons {
    val printerModelJson = runCatching {
        JSONObject(printer.orcaMachineModelJson)
    }.getOrNull()
    val printerResolvedJson = runCatching {
        JSONObject(printer.orcaResolvedMachineJson)
    }.getOrNull()
    val printerOverridesJson = jsonObjectOrNull(printer.orcaMachineOverridesJson)
    val printerJson = printerModelJson?.copyJsonObject()?.apply {
        mergeJsonObject(printerResolvedJson)
        mergeJsonObject(printerOverridesJson)
    } ?: printerResolvedJson
        ?.let { it.copyJsonObject().apply { mergeJsonObject(printerOverridesJson) } }

    val storedFilamentJson = runCatching {
        JSONObject(filament.orcaResolvedFilamentJson)
    }.getOrNull()
    val filamentOverridesJson = jsonObjectOrNull(filament.orcaFilamentOverridesJson)
    val filamentJson = context?.resolveOrcaFilamentJsonFromAssets(
        printer = printer,
        filament = filament,
        storedFilamentJson = storedFilamentJson
    )?.apply {
        mergeJsonObject(filamentOverridesJson)
    } ?: storedFilamentJson?.let { it.copyJsonObject().apply { mergeJsonObject(filamentOverridesJson) } }

    val storedProcessJson = runCatching {
        JSONObject(process.orcaResolvedProcessJson)
    }.getOrNull()
    val processOverridesJson = jsonObjectOrNull(process.orcaProcessOverridesJson)
    val processJson = context?.resolveOrcaProcessJsonFromAssets(
        printer = printer,
        process = process,
        storedProcessJson = storedProcessJson
    )?.apply {
        mergeJsonObject(processOverridesJson)
    } ?: storedProcessJson?.let {
        it.copyJsonObject().apply {
            mergeJsonObject(processOverridesJson)
        }
    }

    return NativeSliceResolvedProfileJsons(
        printerJson = printerJson,
        filamentJson = filamentJson,
        processJson = processJson,
        printerOverridesJson = printerOverridesJson,
        filamentOverridesJson = filamentOverridesJson,
        processOverridesJson = processOverridesJson
    )
}

private fun NativeSliceResolvedProfileJsons.mergeResolvedProfileJson(): JSONObject =
    (printerJson?.copyJsonObject() ?: JSONObject())
        .apply {
            mergeJsonObject(filamentJson)
            mergeJsonObject(processJson)
        }

private fun JSONObject.applyNativeMobileDefaults(
    active: ActiveSlicerConfiguration,
    profileJsons: NativeSliceResolvedProfileJsons
) {
    put("mobile_slicer_printer_source", active.printer.profileSource)
    put("mobile_slicer_orca_machine_model_path", active.printer.orcaMachineModelPath)
    put("mobile_slicer_orca_family", active.printer.orcaFamily)
    put("mobile_slicer_filament_source", active.filament.profileSource)
    put("mobile_slicer_orca_filament_path", active.filament.orcaFilamentPath)
    put("mobile_slicer_orca_filament_family", active.filament.orcaFamily)
    put("mobile_slicer_process_source", active.process.profileSource)
    put("mobile_slicer_orca_process_path", active.process.orcaProcessPath)
    put("mobile_slicer_orca_process_family", active.process.orcaFamily)

    putNativePrinterConfiguration(active.printer)
    putNativeFilamentConfiguration(active.filament)
    putNativeProcessConfiguration(active.process)
    putNativeFilamentOverrideConfiguration(active.filament)
    restoreResolvedOrcaParityValues(profileJsons.printerJson, profileJsons.filamentJson, profileJsons.processJson)
    applyOrcaProfileIdentityDefaults(active.printer, active.filament, active.process)
}

private fun JSONObject.applyNativeFinalOverridesAndNormalization(
    profileJsons: NativeSliceResolvedProfileJsons,
    preserveOrcaResolvedValues: Boolean,
    resolvedNativeToolCount: Int
) {
    applyExplicitOrcaOverrideLayers(
        printerOverridesJson = profileJsons.printerOverridesJson,
        filamentOverridesJson = profileJsons.filamentOverridesJson,
        processOverridesJson = profileJsons.processOverridesJson
    )
    normalizeManualBrimWidthOverride(profileJsons.processOverridesJson)
    val nativeToolCount = if (optInt(NativeConfigKeys.Printer.ExtrudersCount, 0) == 1) 1 else resolvedNativeToolCount
    normalizeNativeScalarListStrings()
    normalizeNativePointStrings()
    if (!preserveOrcaResolvedValues || nativeToolCount > 1) {
        expandActiveMaterialAcrossNativeToolVectors(nativeToolCount)
    }
}

private fun JSONObject.applyExplicitOrcaOverrideLayers(
    printerOverridesJson: JSONObject?,
    filamentOverridesJson: JSONObject?,
    processOverridesJson: JSONObject?
) {
    mergeJsonObject(printerOverridesJson)
    mergeJsonObject(filamentOverridesJson)
    mergeJsonObject(processOverridesJson)
}

private fun JSONObject.normalizeManualBrimWidthOverride(processOverridesJson: JSONObject?) {
    if (processOverridesJson?.has("brim_width") == true &&
        optString("brim_type") == BrimType.Auto.configValue &&
        optDouble("brim_width", 0.0) > 0.0
    ) {
        put("brim_type", BrimType.OuterOnly.configValue)
    }
}

private fun elapsedRealtimeForConfigTiming(): Long =
    runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)

private fun Context.resolveOrcaFilamentJsonFromAssets(
    printer: PrinterProfile,
    filament: FilamentProfile,
    storedFilamentJson: JSONObject?
): JSONObject? {
    val identity = storedFilamentJson.orcaFilamentIdentityValues() +
        listOf(
            filament.orcaFilamentPath,
            filament.name.takeUnless { it.isBroadFilamentIdentity(filament.materialType) }.orEmpty()
        )
    if (identity.all { it.isBlank() }) return storedFilamentJson

    val printerKey = printerSettingsIdForExport(printer).cleanOrcaIdentity()
    val candidates = runCatching { loadOrcaFilamentPresets(this) }.getOrDefault(emptyList())
    val requestedMaterial = sequenceOf(
        storedFilamentJson?.scalarOrcaValue("filament_type"),
        filament.materialType,
        identity.firstNotNullOfOrNull { it.detectFilamentMaterial() }
    )
        .filterNotNull()
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val requestedBroadIdentity = identity.any { it.isBroadOrcaSystemFilamentIdentity(requestedMaterial) }
    val compatibleMaterialPreset = if (requestedBroadIdentity && printerKey.isNotBlank()) {
        candidates
            .filter { preset ->
                printerKey in preset.compatiblePrinterKeys &&
                    requestedMaterial.isNotBlank() &&
                    preset.materialType.equals(requestedMaterial, ignoreCase = true)
            }
            .maxWithOrNull(
                compareBy<OrcaFilamentPreset> { preset ->
                    if (preset.family.cleanOrcaIdentity() == printer.orcaFamily.cleanOrcaIdentity()) 2 else 0
                }.thenBy { preset ->
                    if (preset.rawName.contains("Generic", ignoreCase = true) ||
                        preset.name.contains("Generic", ignoreCase = true)
                    ) 1 else 0
                }.thenByDescending { preset -> preset.rawName.length + preset.name.length }
            )
    } else {
        null
    }
    val matchedPreset = compatibleMaterialPreset ?: candidates
        .filter { preset ->
            val presetIdentities = listOf(
                preset.profilePath,
                preset.rawName,
                preset.name,
                preset.pickerDuplicateKey
            )
            val identityMatch = identity.any { current ->
                orcaFilamentIdentityMatchesPreset(current, presetIdentities)
            }
            identityMatch &&
                (
                    preset.compatiblePrinterKeys.isEmpty() ||
                        printerKey.isBlank() ||
                        printerKey in preset.compatiblePrinterKeys
                    )
        }
        .maxWithOrNull(
            compareBy<OrcaFilamentPreset> { preset ->
                if (printerKey.isNotBlank() && printerKey in preset.compatiblePrinterKeys) 2 else 0
            }.thenBy { preset ->
                if (preset.family.cleanOrcaIdentity() == printer.orcaFamily.cleanOrcaIdentity()) 1 else 0
            }
        ) ?: return storedFilamentJson

    val assetJson = runCatching {
        JSONObject(loadOrcaFilamentImportBundle(this, matchedPreset).resolvedFilamentJson)
    }.getOrNull() ?: return storedFilamentJson

    return if (storedFilamentJson == null) {
        assetJson
    } else {
        assetJson.copyJsonObject().apply {
            mergeJsonObject(storedFilamentJson)
            repairOrcaFilamentParityValues(assetJson)
        }
    }
}

private fun Context.resolveOrcaProcessJsonFromAssets(
    printer: PrinterProfile,
    process: ProcessProfile,
    storedProcessJson: JSONObject?
): JSONObject? {
    val hasOrcaIdentity = process.profileSource == "orca" ||
        process.orcaProcessPath.isNotBlank() ||
        process.orcaFamily.isNotBlank() ||
        storedProcessJson?.optString("print_settings_id").orEmpty().isNotBlank() ||
        process.name.isNotBlank()
    if (!hasOrcaIdentity) return storedProcessJson

    val printerPreset = findOrcaPrinterPresetForProfile(this, printer) ?: return storedProcessJson
    val processPreset = runCatching { loadOrcaPrinterImportBundle(this, printerPreset) }
        .getOrNull()
        ?.processPresets
        ?.let { presets -> findResolvedOrcaProcessFallback(process, printer, presets) }
        ?: return storedProcessJson
    return runCatching { JSONObject(processPreset.resolvedProcessJson) }.getOrNull()
        ?: storedProcessJson
}

internal fun repairNativeSliceConfigWithOrcaFilamentAssets(
    context: Context,
    printer: PrinterProfile,
    filamentOverridesJson: String = "",
    configJson: String
): String {
    return repairNativeSliceConfigWithOrcaFilamentAssetsResult(
        context = context,
        printer = printer,
        filamentOverridesJson = filamentOverridesJson,
        configJson = configJson
    ).json
}

private data class NativeSliceConfigRepairCacheKey(
    val printerId: String,
    val printerHash: Int,
    val configHash: Int,
    val filamentOverridesHash: Int
)

private object NativeSliceConfigRepairCache {
    private const val MaxEntries = 8
    private val configs = object : LinkedHashMap<NativeSliceConfigRepairCacheKey, String>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<NativeSliceConfigRepairCacheKey, String>?): Boolean =
            size > MaxEntries
    }

    @Synchronized
    fun get(key: NativeSliceConfigRepairCacheKey): String? = configs[key]

    @Synchronized
    fun put(key: NativeSliceConfigRepairCacheKey, value: String) {
        configs[key] = value
    }
}

internal fun repairNativeSliceConfigWithOrcaFilamentAssetsResult(
    context: Context,
    printer: PrinterProfile,
    filamentOverridesJson: String = "",
    configJson: String
): NativeSliceConfigRepairResult {
    val startedAtMs = SystemClock.elapsedRealtime()
    val cacheKey = NativeSliceConfigRepairCacheKey(
        printerId = printer.id,
        printerHash = printer.hashCode(),
        configHash = configJson.hashCode(),
        filamentOverridesHash = filamentOverridesJson.hashCode()
    )
    NativeSliceConfigRepairCache.get(cacheKey)?.let {
        return NativeSliceConfigRepairResult(
            json = it,
            cacheHit = true,
            elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
        )
    }
    val json = runCatching { JSONObject(configJson) }.getOrNull() ?: return configJson
        .let {
            NativeSliceConfigRepairResult(
                json = it,
                cacheHit = false,
                elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
            )
        }
    if (json.optInt("mobile_slicer_active_filament_slot_count", 1) > 1) {
        return NativeSliceConfigRepairResult(
            json = configJson,
            cacheHit = false,
            elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
        ).also { NativeSliceConfigRepairCache.put(cacheKey, it.json) }
    }
    val filamentIdentityJson = JSONObject().apply {
        listOf(
            "filament_settings_id",
            "filament_id",
            "filament_ids",
            "filament_type",
            "filament_vendor",
            "default_filament_colour",
            "mobile_slicer_orca_filament_path",
            "mobile_slicer_orca_filament_family",
            "mobile_slicer_filament_source"
        ).forEach { key ->
            if (json.has(key) && !json.isNull(key)) put(key, json.opt(key))
        }
    }
    val repaired = context.resolveOrcaFilamentJsonFromAssets(
        printer = printer,
        filament = profileStoreFallbackFilamentProfile().copy(
            id = "",
            name = json.scalarOrcaValue("filament_settings_id"),
            materialType = json.scalarOrcaValue("filament_type"),
            vendor = json.scalarOrcaValue("filament_vendor"),
            defaultFilamentColor = json.scalarOrcaValue("default_filament_colour"),
            printerProfileId = printer.id,
            profileSource = json.optString("mobile_slicer_filament_source"),
            orcaFamily = json.optString("mobile_slicer_orca_filament_family"),
            orcaFilamentPath = json.optString("mobile_slicer_orca_filament_path"),
            orcaResolvedFilamentJson = filamentIdentityJson.toString()
        ),
        storedFilamentJson = filamentIdentityJson
    ) ?: return NativeSliceConfigRepairResult(
        json = configJson,
        cacheHit = false,
        elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
    ).also { NativeSliceConfigRepairCache.put(cacheKey, it.json) }
    if (repaired.toString() == json.toString()) {
        return NativeSliceConfigRepairResult(
            json = configJson,
            cacheHit = false,
            elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
        ).also { NativeSliceConfigRepairCache.put(cacheKey, it.json) }
    }
    json.mergeJsonObject(repaired)
    json.mergeJsonObject(jsonObjectOrNull(filamentOverridesJson))
    val repairedConfigJson = json.toString()
    NativeSliceConfigRepairCache.put(cacheKey, repairedConfigJson)
    return NativeSliceConfigRepairResult(
        json = repairedConfigJson,
        cacheHit = false,
        elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
    )
}

private fun JSONObject?.orcaFilamentIdentityValues(): List<String> {
    if (this == null) return emptyList()
    return listOf(
        scalarOrcaValue("filament_settings_id"),
        scalarOrcaValue("filament_id"),
        scalarOrcaValue("filament_ids"),
        optString("name"),
        optString("inherits")
    )
}

private fun JSONObject.repairOrcaFilamentParityValues(assetJson: JSONObject) {
    val keys = assetJson.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (assetJson.has(key) && !assetJson.isNull(key)) {
            val assetValue = assetJson.opt(key)
            if (key == "filament_settings_id" && !assetValue.hasNonBlankNativeValue()) {
                continue
            }
            put(key, assetValue)
        }
    }
}

private fun Any?.hasNonBlankNativeValue(): Boolean {
    val value = this ?: return false
    return when (value) {
        is JSONArray -> {
            for (index in 0 until value.length()) {
                val item = value.opt(index) ?: continue
                if (item.toString().trim().trim('"').isNotBlank()) return true
            }
            false
        }
        is String -> {
            val trimmed = value.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                runCatching { JSONArray(trimmed) }.getOrNull()?.let { array ->
                    for (index in 0 until array.length()) {
                        val item = array.opt(index) ?: continue
                        if (item.toString().trim().trim('"').isNotBlank()) return true
                    }
                }
            }
            trimmed.trim('"').isNotBlank()
        }
        else -> value.toString().trim().trim('"').isNotBlank()
    }
}

private fun JSONObject.scalarOrcaValue(key: String): String {
    val value = opt(key) ?: return ""
    return when (value) {
        is JSONArray -> {
            for (index in 0 until value.length()) {
                val item = value.opt(index)?.toString()?.trim()?.trim('"').orEmpty()
                if (item.isNotBlank()) return item
            }
            ""
        }
        is String -> {
            val trimmed = value.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                runCatching { JSONArray(trimmed) }.getOrNull()?.let { array ->
                    for (index in 0 until array.length()) {
                        val item = array.opt(index)?.toString()?.trim()?.trim('"').orEmpty()
                        if (item.isNotBlank()) return item
                    }
                }
            }
            trimmed.trim('"')
        }
        else -> value.toString().trim().trim('"')
    }
}

private fun String.cleanOrcaIdentity(): String =
    trim()
        .trim('"')
        .removeSuffix(".json")
        .substringAfterLast('/')
        .cleanProfileMatchKey()

internal fun orcaFilamentIdentityMatchesPreset(identity: String, presetIdentities: List<String>): Boolean {
    val clean = identity.cleanOrcaIdentity()
    if (!clean.isSpecificOrcaIdentity()) return false
    return presetIdentities.any { presetValue ->
        val presetClean = presetValue.cleanOrcaIdentity()
        presetClean == clean ||
            (clean.length >= 10 && presetClean.length >= 10 && (presetClean.contains(clean) || clean.contains(presetClean)))
    }
}

private fun String.isSpecificOrcaIdentity(): Boolean {
    val clean = cleanOrcaIdentity()
    if (clean.isBlank()) return false
    if (clean in broadFilamentIdentityTokens) return false
    return clean.length >= 4
}

private fun String.isBroadFilamentIdentity(materialType: String): Boolean {
    val clean = cleanOrcaIdentity()
    if (clean.isBlank()) return true
    val material = materialType.cleanOrcaIdentity()
    return clean == material || clean in broadFilamentIdentityTokens
}

private fun String.isBroadOrcaSystemFilamentIdentity(materialType: String): Boolean {
    val clean = cleanOrcaIdentity()
    if (clean.isBroadFilamentIdentity(materialType)) return true
    if (!clean.contains("system")) return false
    return clean
        .replace(" system", "")
        .replace(" atsystem", "")
        .replace(" system ", " ")
        .trim()
        .isBroadFilamentIdentity(materialType)
}

private val broadFilamentIdentityTokens = setOf(
    "pla",
    "petg",
    "abs",
    "asa",
    "tpu",
    "pc",
    "pa",
    "pva",
    "hips",
    "generic pla",
    "generic petg",
    "generic abs",
    "generic asa",
    "generic tpu",
    "generic pc",
    "generic pa",
    "generic pla system",
    "generic petg system",
    "generic abs system",
    "generic asa system",
    "generic tpu system",
    "generic pc system",
    "generic pa system"
)

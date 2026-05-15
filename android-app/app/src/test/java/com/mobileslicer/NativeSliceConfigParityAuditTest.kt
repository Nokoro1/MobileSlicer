package com.mobileslicer

import com.mobileslicer.profiles.GeneratedOrcaSettingMetadata
import com.mobileslicer.profiles.OrcaApplicationStatus
import com.mobileslicer.profiles.OrcaNativeMappingKind
import com.mobileslicer.profiles.OrcaPreservationStatus
import com.mobileslicer.profiles.OrcaSettingDangerClass
import com.mobileslicer.profiles.OrcaSettingRegistry
import com.mobileslicer.profiles.OrcaSettingScope
import com.mobileslicer.profiles.resolvedFilamentParityKeys
import com.mobileslicer.profiles.resolvedPrinterParityKeys
import com.mobileslicer.profiles.resolvedProcessParityKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NativeSliceConfigParityAuditTest {
    @Test
    fun emittedRealOrcaConfigKeysAreCoveredByPolicyRegistry() {
        val emittedScopes = emittedNativeConfigKeyScopes()
        val orcaKeys = orcaConfigKeys()
        val nativeAppliedKeys = nativeAppliedOrcaKeys()

        val missingPolicyCoverage = emittedScopes.keys
            .filter { it in orcaKeys }
            .mapNotNull { key ->
                val definition = OrcaSettingRegistry.definitionForNativeConfigKey(
                    key = key,
                    storageScopes = emittedScopes.getValue(key),
                    nativeAppliedKeys = nativeAppliedKeys,
                    documentedNonOrcaKeys = documentedNativeConfigAliasesAndMetadata
                )
                key.takeIf {
                    definition.policy.preservationStatus == OrcaPreservationStatus.MIGRATION_UNKNOWN ||
                        definition.policy.applicationStatus == OrcaApplicationStatus.MIGRATION_UNKNOWN
                }
            }
            .sorted()

        assertEquals(emptyList<String>(), missingPolicyCoverage)
    }

    @Test
    fun emittedNonOrcaConfigKeysAreIntentionalAliasesOrMetadata() {
        val emittedKeys = emittedNativeConfigKeys()
        val orcaKeys = orcaConfigKeys()

        val undocumentedNonOrcaKeys = emittedKeys
            .filterNot { it in orcaKeys }
            .filterNot { it in documentedNativeConfigAliasesAndMetadata }
            .sorted()

        assertEquals(emptyList<String>(), undocumentedNonOrcaKeys)
    }

    @Test
    fun nativeAppliedManifestIsExplicitAndNotEmpty() {
        val manifestKeys = nativeAppliedOrcaKeys()
        val orcaKeys = orcaConfigKeys()
        val unknownManifestKeys = manifestKeys
            .filterNot { it in orcaKeys }
            .sorted()

        assertTrue("Native applied-key manifest must not be empty", manifestKeys.isNotEmpty())
        assertEquals(emptyList<String>(), unknownManifestKeys)
    }

    @Test
    fun emittedAndPreservedConfigKeysHaveRegistryPolicy() {
        val nativeAppliedKeys = nativeAppliedOrcaKeys()
        val emittedScopes = emittedNativeConfigKeyScopes()
        val preservedScopes = preservedProfileKeyScopes()
        val allKeys = emittedScopes.keys + preservedScopes.keys + nativeAppliedKeys

        val missingClassification = allKeys.mapNotNull { key ->
            val definition = OrcaSettingRegistry.definitionForNativeConfigKey(
                key = key,
                storageScopes = emittedScopes[key].orEmpty() + preservedScopes[key].orEmpty(),
                nativeAppliedKeys = nativeAppliedKeys,
                documentedNonOrcaKeys = documentedNativeConfigAliasesAndMetadata
            )
            val policy = definition.policy
            when {
                policy.preservationStatus == OrcaPreservationStatus.MIGRATION_UNKNOWN -> key
                policy.applicationStatus == OrcaApplicationStatus.MIGRATION_UNKNOWN -> key
                else -> null
            }
        }.sorted()

        assertEquals(emptyList<String>(), missingClassification)
    }

    @Test
    fun nativeAppliedManifestAndRegistryAgreeForEveryEmittedOrcaKey() {
        val nativeAppliedKeys = nativeAppliedOrcaKeys()
        val emittedScopes = emittedNativeConfigKeyScopes()
        val orcaKeys = orcaConfigKeys()

        val mismatches = emittedScopes.keys
            .filter { it in orcaKeys }
            .mapNotNull { key ->
                val definition = OrcaSettingRegistry.definitionForNativeConfigKey(
                    key = key,
                    storageScopes = emittedScopes.getValue(key),
                    nativeAppliedKeys = nativeAppliedKeys,
                    documentedNonOrcaKeys = documentedNativeConfigAliasesAndMetadata
                )
                val isManifestApplied = key in nativeAppliedKeys
                val isPolicyApplied = definition.policy.applicationStatus == OrcaApplicationStatus.APPLIED
                val isPolicyNotApplied = definition.policy.nativeMapping == OrcaNativeMappingKind.NOT_APPLIED
                when {
                    isManifestApplied && !isPolicyApplied -> "$key manifest-applied policy=${definition.policy.applicationStatus}"
                    !isManifestApplied && isPolicyApplied -> "$key policy-applied missing-manifest"
                    !isManifestApplied && !isPolicyNotApplied -> "$key missing-manifest mapping=${definition.policy.nativeMapping}"
                    else -> null
                }
            }
            .sorted()

        assertEquals(emptyList<String>(), mismatches)
    }

    @Test
    fun activeConfigSurfaceHasNoNeedsMappingBacklog() {
        val nativeAppliedKeys = nativeAppliedOrcaKeys()
        val emittedScopes = emittedNativeConfigKeyScopes()
        val preservedScopes = preservedProfileKeyScopes()
        val allKeys = emittedScopes.keys + preservedScopes.keys + nativeAppliedKeys

        val needsMapping = allKeys.mapNotNull { key ->
            val definition = OrcaSettingRegistry.definitionForNativeConfigKey(
                key = key,
                storageScopes = emittedScopes[key].orEmpty() + preservedScopes[key].orEmpty(),
                nativeAppliedKeys = nativeAppliedKeys,
                documentedNonOrcaKeys = documentedNativeConfigAliasesAndMetadata
            )
            key.takeIf { definition.policy.applicationStatus == OrcaApplicationStatus.NEEDS_MAPPING }
        }.sorted()

        assertEquals(emptyList<String>(), needsMapping)
    }

    @Test
    fun processEditorRenderedCallbacksAreWiredToDraft() {
        val tabSources = sourceFiles("app/src/main/java/com/mobileslicer/profiles") { file ->
            file.name.startsWith("ProcessProfile") && file.name.endsWith("Tab.kt")
        }.joinToString("\n") { it.readText() }
        val selectedTabSource = sourceFile("app/src/main/java/com/mobileslicer/profiles/ProcessProfileEditorSelectedTabs.kt").readText()
        val declaredCallbacks = Regex("""\b(on\w+Change)\s*:""")
            .findAll(tabSources)
            .map { it.groupValues[1] }
            .toSortedSet()
        val wiredCallbacks = Regex("""\b(on\w+Change)\s*=""")
            .findAll(selectedTabSource)
            .map { it.groupValues[1] }
            .toSet()

        val unwiredCallbacks = declaredCallbacks
            .filterNot { it in wiredCallbacks }
            .sorted()

        assertEquals(emptyList<String>(), unwiredCallbacks)
    }

    @Test
    fun processEditorInitialDraftFieldsAreSavedOrDocumentedAsHidden() {
        val draftSource = sourceFile("app/src/main/java/com/mobileslicer/profiles/ProcessProfileEditorDraft.kt").readText()
        val mappingSource = sourceFile("app/src/main/java/com/mobileslicer/profiles/ProcessProfileEditorMapping.kt").readText()
        val selectedTabSource = sourceFile("app/src/main/java/com/mobileslicer/profiles/ProcessProfileEditorSelectedTabs.kt").readText()
        val initialBackedDraftFields = Regex("""var\s+(\w+)\s+by\s+mutableStateOf\(initial\.(\w+)\)""")
            .findAll(draftSource)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
        val hiddenOrHeaderFields = setOf(
            "name",
            "subtitle",
            "adaptiveLayerHeight",
            "printExtruderId",
            "printExtruderVariant",
            "dontSlowDownOuterWall",
            "internalBridgeFanSpeed",
            "wallInfillOrder",
            "supportMaterialInterfaceFanSpeed",
            "filamentMapMode",
            "allowMixTemp",
            "allowMulticolorOnePlate"
        )

        val unsavedDraftFields = initialBackedDraftFields
            .map { it.first }
            .filterNot { field -> Regex("""\b${Regex.escape(field)}\b""").containsMatchIn(mappingSource) }
            .sorted()
        val undocumentedHiddenFields = initialBackedDraftFields
            .map { it.first }
            .filterNot { field -> Regex("""\bdraft\.${Regex.escape(field)}\b""").containsMatchIn(selectedTabSource) }
            .filterNot { it in hiddenOrHeaderFields }
            .sorted()

        assertEquals(emptyList<String>(), unsavedDraftFields)
        assertEquals(emptyList<String>(), undocumentedHiddenFields)
    }

    @Test
    fun processEditorTabParametersAreRenderedOrDocumentedStoredOnly() {
        val storedOnlyParameters = setOf("showAdvancedProfileSettings")
        val unusedParameters = sourceFiles("app/src/main/java/com/mobileslicer/profiles") { file ->
            file.name.startsWith("ProcessProfile") && file.name.endsWith("Tab.kt")
        }.flatMap { file ->
            val source = file.readText()
            val match = Regex("""internal fun \w+\((.*?)\) \{""", RegexOption.DOT_MATCHES_ALL).find(source)
                ?: return@flatMap emptyList()
            val parameters = Regex("""\b(\w+)\s*:""")
                .findAll(match.groupValues[1])
                .map { it.groupValues[1] }
                .toList()
            val body = source.substring(match.range.last + 1)
            parameters.mapNotNull { parameter ->
                "$parameter in ${file.name}".takeIf {
                    parameter !in storedOnlyParameters &&
                        Regex("""\b${Regex.escape(parameter)}\b""").findAll(body).count() == 0
                }
            }
        }.sorted()

        assertEquals(emptyList<String>(), unusedParameters)
    }

    @Test
    fun processNativeConfigUsesSlicerFacingPropertiesOrDocumentsStoredOnlyFields() {
        val profileSource = sourceFile("app/src/main/java/com/mobileslicer/profiles/ProfileModels.kt").readText()
        val nativeSource = sourceFile("app/src/main/java/com/mobileslicer/profiles/NativeSliceProcessConfiguration.kt").readText()
        val processProperties = Regex("""val\s+(\w+):[^\n]+get\(\) = (?:value\("[^"]+"\)|\w+Details\.)""")
            .findAll(profileSource)
            .map { it.groupValues[1] }
            .toSortedSet()
        val nativeReferenced = Regex("""(?:process\.|this\.|(?<!fun )\b)(\w+)""")
            .findAll(nativeSource)
            .map { it.groupValues[1] }
            .toSet()
        val storedOnlyOrContainerFields = setOf(
            "id",
            "name",
            "subtitle",
            "builtIn",
            "profileSource",
            "printerProfileId",
            "printerVariantKey",
            "nozzleDiameterMm",
            "orcaFamily",
            "orcaProcessPath",
            "orcaRawProcessJson",
            "orcaResolvedProcessJson",
            "orcaProcessOverridesJson",
            "orcaResolvedSourceChain",
            "qualitySurfaceDetails",
            "strengthInfillDetails",
            "primeTowerDetails",
            "specialModeDetails",
            "gcodeOutputDetails",
            "adaptiveLayerHeight"
        )

        val uncoveredProperties = processProperties
            .filterNot { it in nativeReferenced }
            .filterNot { it in storedOnlyOrContainerFields }
            .sorted()

        assertEquals(emptyList<String>(), uncoveredProperties)
    }

    @Test
    fun preservedProfileOnlyKeysDoNotBecomeNativePassThrough() {
        val nativeAppliedKeys = nativeAppliedOrcaKeys()
        val preservedScopes = preservedProfileKeyScopes()
        val keysThatPreviouslyBrokeSlicing = setOf(
            "detect_narrow_internal_solid_infill",
            "enable_extra_bridge_layer",
            "ensure_vertical_shell_thickness",
            "fill_multiline",
            "filter_out_gap_fill",
            "gap_fill_flow_ratio",
            "min_bead_width",
            "min_feature_size",
            "sparse_infill_flow_ratio",
            "wall_distribution_count",
            "wall_transition_angle",
            "wall_transition_filter_deviation",
            "wall_transition_length"
        )

        val accidentalNativeApplication = keysThatPreviouslyBrokeSlicing
            .filter { it in nativeAppliedKeys }
            .sorted()

        val nonStoredPolicy = keysThatPreviouslyBrokeSlicing
            .mapNotNull { key ->
                val definition = OrcaSettingRegistry.definitionForNativeConfigKey(
                    key = key,
                    storageScopes = preservedScopes[key].orEmpty(),
                    nativeAppliedKeys = nativeAppliedKeys,
                    documentedNonOrcaKeys = documentedNativeConfigAliasesAndMetadata
                )
                key.takeUnless {
                    definition.policy.applicationStatus == OrcaApplicationStatus.STORED_ONLY ||
                        definition.policy.applicationStatus == OrcaApplicationStatus.BLOCKED_ON_MOBILE
                }?.let { "$it=${definition.policy.applicationStatus}" }
            }
            .sorted()

        assertEquals(emptyList<String>(), accidentalNativeApplication)
        assertEquals(emptyList<String>(), nonStoredPolicy)
    }

    private fun sourceFile(path: String): File =
        File(path).takeIf { it.isFile } ?: File("../$path").takeIf { it.isFile }
            ?: error("Missing source file: $path")

    private fun sourceFiles(directory: String, predicate: (File) -> Boolean): List<File> =
        (File(directory).takeIf { it.isDirectory } ?: File("../$directory").takeIf { it.isDirectory }
            ?: error("Missing source directory: $directory"))
            .listFiles()
            .orEmpty()
            .filter(predicate)

    @Test
    fun generatedOrcaMetadataIsThePolicyFactSourceForKnownKeys() {
        val nativeAppliedKeys = nativeAppliedOrcaKeys()
        val emittedScopes = emittedNativeConfigKeyScopes()
        val preservedScopes = preservedProfileKeyScopes()
        val allKnownOrcaKeys = (emittedScopes.keys + preservedScopes.keys + nativeAppliedKeys)
            .filter { it in GeneratedOrcaSettingMetadata.all }

        val missingGeneratedMetadata = allKnownOrcaKeys
            .filter { key ->
                val definition = OrcaSettingRegistry.definitionForNativeConfigKey(
                    key = key,
                    storageScopes = emittedScopes[key].orEmpty() + preservedScopes[key].orEmpty(),
                    nativeAppliedKeys = nativeAppliedKeys,
                    documentedNonOrcaKeys = documentedNativeConfigAliasesAndMetadata
                )
                definition.metadata == null || !definition.metadata.isDefinedInPrintConfig
            }
            .sorted()

        assertEquals(emptyList<String>(), missingGeneratedMetadata)
        assertTrue("Expected generated Orca metadata to be used", GeneratedOrcaSettingMetadata.all.isNotEmpty())
    }

    @Test
    fun writesNativeConfigRegistryReport() {
        val nativeAppliedKeys = nativeAppliedOrcaKeys()
        val emittedScopes = emittedNativeConfigKeyScopes()
        val preservedScopes = preservedProfileKeyScopes()
        val allKeys = (emittedScopes.keys + preservedScopes.keys + nativeAppliedKeys).sorted()
        val definitions = allKeys.map { key ->
            OrcaSettingRegistry.definitionForNativeConfigKey(
                key = key,
                storageScopes = emittedScopes[key].orEmpty() + preservedScopes[key].orEmpty(),
                nativeAppliedKeys = nativeAppliedKeys,
                documentedNonOrcaKeys = documentedNativeConfigAliasesAndMetadata
            )
        }

        val report = buildString {
            appendLine("# Orca Native Config Registry Report")
            appendLine()
            appendLine("Generated by `NativeSliceConfigParityAuditTest`.")
            appendLine()
            appendLine("| Bucket | Count |")
            appendLine("| --- | ---: |")
            OrcaApplicationStatus.entries.forEach { status ->
                appendLine("| application `$status` | ${definitions.count { it.policy.applicationStatus == status }} |")
            }
            OrcaPreservationStatus.entries.forEach { status ->
                appendLine("| preservation `$status` | ${definitions.count { it.policy.preservationStatus == status }} |")
            }
            appendLine("| native applied manifest | ${nativeAppliedKeys.size} |")
            appendLine("| total emitted/preserved/native-applied keys | ${allKeys.size} |")

            fun section(title: String, keys: List<String>) {
                appendLine()
                appendLine("## $title")
                appendLine()
                if (keys.isEmpty()) {
                    appendLine("_None._")
                } else {
                    keys.forEach { appendLine("* `$it`") }
                }
            }

            section(
                "Applied",
                definitions
                    .filter { it.policy.applicationStatus == OrcaApplicationStatus.APPLIED }
                    .map { it.policy.key }
                    .sorted()
            )
            section(
                "Needs Mapping",
                definitions
                    .filter { it.policy.applicationStatus == OrcaApplicationStatus.NEEDS_MAPPING }
                    .map { it.policy.key }
                    .sorted()
            )
            OrcaSettingDangerClass.entries.forEach { dangerClass ->
                section(
                    "Needs Mapping - $dangerClass",
                    definitions
                        .filter {
                            it.policy.applicationStatus == OrcaApplicationStatus.NEEDS_MAPPING &&
                                it.policy.dangerClass == dangerClass
                        }
                        .map { it.policy.key }
                        .sorted()
                )
            }
            section(
                "Blocked On Mobile",
                definitions
                    .filter { it.policy.applicationStatus == OrcaApplicationStatus.BLOCKED_ON_MOBILE }
                    .map { it.policy.key }
                    .sorted()
            )
            section(
                "Metadata Or Non-Orca Runtime",
                definitions
                    .filter {
                        it.policy.applicationStatus == OrcaApplicationStatus.METADATA_ONLY ||
                            it.policy.applicationStatus == OrcaApplicationStatus.NOT_ORCA_RUNTIME
                    }
                    .map { it.policy.key }
                    .sorted()
            )
        }

        val reportFile = File("build/reports/orca-setting-registry/native-config-policy-report.md")
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(report)
        assertTrue(reportFile.exists())
    }

    private fun emittedNativeConfigKeys(): Set<String> {
        return emittedNativeConfigKeyScopes().keys
    }

    private fun emittedNativeConfigKeyScopes(): Map<String, Set<OrcaSettingScope>> {
        val nativeConfigConstants = nativeConfigKeyConstants()
        val sources = listOf(
            "src/main/java/com/mobileslicer/profiles/NativeSliceProcessConfiguration.kt" to OrcaSettingScope.PROCESS,
            "src/main/java/com/mobileslicer/profiles/NativeSlicePrinterConfiguration.kt" to OrcaSettingScope.PRINTER,
            "src/main/java/com/mobileslicer/profiles/NativeSliceFilamentConfiguration.kt" to OrcaSettingScope.FILAMENT
        )
        val result = mutableMapOf<String, MutableSet<OrcaSettingScope>>()
        sources.forEach { (relativePath, scope) ->
            val source = File(relativePath).readText()
            Regex("""\.put\("([^"]+)"""").findAll(source).forEach { match ->
                result.getOrPut(match.groupValues[1]) { mutableSetOf() } += scope
            }
            Regex("""\.put\((NativeConfigKeys\.[A-Za-z]+\.[A-Za-z]+)""")
                .findAll(source)
                .forEach { match ->
                    result.getOrPut(nativeConfigConstants.getValue(match.groupValues[1])) { mutableSetOf() } += scope
                }
        }
        return result
    }

    private fun preservedProfileKeyScopes(): Map<String, Set<OrcaSettingScope>> =
        buildMap {
            resolvedPrinterParityKeys.forEach { put(it, setOf(OrcaSettingScope.PRINTER)) }
            resolvedFilamentParityKeys.forEach { put(it, setOf(OrcaSettingScope.FILAMENT)) }
            resolvedProcessParityKeys.forEach { put(it, setOf(OrcaSettingScope.PROCESS)) }
        }

    private fun nativeConfigKeyConstants(): Map<String, String> {
        val source = File("src/main/java/com/mobileslicer/profiles/NativeSliceConfigKeys.kt").readText()
        val values = mutableMapOf<String, String>()
        val objectStack = mutableListOf<String>()
        source.lineSequence().forEach { line ->
            Regex("""\s*object\s+(\w+)\s+\{""").find(line)?.let { match ->
                objectStack += match.groupValues[1]
                return@forEach
            }
            Regex("""const val\s+(\w+)\s*=\s*"([^"]+)"""").find(line)?.let { match ->
                val objectName = objectStack.lastOrNull()
                if (objectName != null) {
                    values["NativeConfigKeys.$objectName.${match.groupValues[1]}"] = match.groupValues[2]
                }
            }
            if (line.trim() == "}" && objectStack.isNotEmpty()) {
                objectStack.removeAt(objectStack.lastIndex)
            }
        }
        return values
    }

    private fun orcaConfigKeys(): Set<String> {
        val printConfigCpp = repoFile("vendor/orcaslicer/src/libslic3r/PrintConfig.cpp").readText()
        val printConfigHpp = repoFile("vendor/orcaslicer/src/libslic3r/PrintConfig.hpp").readText()
        val addKeys = Regex("""(?m)^\s*def\s*=.*add\("([^"]+)"\s*,""")
            .findAll(printConfigCpp)
            .map { it.groupValues[1] }
        val macroKeys = Regex("""\(\(ConfigOption[^,]+,\s*([A-Za-z0-9_]+)\)\)""")
            .findAll(printConfigHpp)
            .map { it.groupValues[1] }
        return (addKeys + macroKeys).toSet() + GeneratedOrcaSettingMetadata.all.keys
    }

    private fun nativeAppliedOrcaKeys(): Set<String> =
        Regex(""""([A-Za-z0-9_]+)"""")
            .findAll(repoFile("engine-wrapper/orca_wrapper_applied_key_manifest.h").readText())
            .map { it.groupValues[1] }
            .toSet()

    private fun repoFile(relativePath: String): File =
        generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)

    private val documentedNativeConfigAliasesAndMetadata = setOf(
        "active_feeder_motor_name",
        "apply_top_surface_compensation",
        "auto_disable_filter_on_overheat",
        "auto_toolchange_command",
        "bed_depth_mm",
        "bed_model",
        "bed_shape",
        "bed_temperature",
        "bed_temperature_initial_layer",
        "bed_texture",
        "bed_texture_area",
        "bottom_texture_end_name",
        "bottom_texture_rect",
        "box_id",
        "cooling_baseline",
        "cooling_filter_enabled",
        "creality_flush_time",
        "enable_pre_heating",
        "extruder_clearance_dist_to_rod",
        "extruder_clearance_max_radius",
        "extruder_max_nozzle_count",
        "extruders_count",
        "family",
        "fan_direction",
        "filament_id",
        "filament_long_retractions_when_cut",
        "filament_retraction_distances_when_cut",
        "filament_retraction_length",
        "filament_wipe",
        "filament_wipe_distance",
        "filament_z_hop_types",
        "first_layer_height",
        "first_layer_infill_speed",
        "first_layer_print_speed",
        "group_algo_with_time",
        "hotend_cooling_rate",
        "hotend_heating_rate",
        "hotend_model",
        "image_bed_type",
        "initial_layer_travel_speed_percent",
        "internal_bridge_support_thickness",
        "is_artillery",
        "is_support_3mf",
        "is_support_air_condition",
        "is_support_mqtt",
        "is_support_multi_box",
        "is_support_timelapse",
        "machine_LED_light_exist",
        "machine_hotend_change_time",
        "machine_platform_motion_enable",
        "machine_prepare_compensation_time",
        "machine_switch_extruder_time",
        "machine_tech",
        "max_height_mm",
        "multi_zone",
        "multi_zone_number",
        "pause_gcode",
        "printhost_group",
        "ramming_pressure_advance_value",
        "remaining_times",
        "retract_on_top_layer",
        "right_icon_offset_bed",
        "scan_folder",
        "skirts",
        "support_box_temp_control",
        "support_cooling_filter",
        "support_multi_filament",
        "support_wan_network",
        "thumbnails_internal",
        "thumbnails_internal_switch",
        "tool_change_temprature_wait",
        "toolchange_gcode",
        "url",
        "use_active_pellet_feeding",
        "use_double_extruder_default_texture",
        "use_extruder_rotation_volume",
        "use_rect_grid",
        "z_hop_when_prime",
        "z_lift_type"
    )

}

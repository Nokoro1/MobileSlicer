package com.mobileslicer.profiles

internal enum class OrcaSettingScope {
    PRINTER,
    FILAMENT,
    PROCESS,
    OBJECT,
    REGION,
    PLATE,
    MACHINE_METADATA,
    HOST_INTEGRATION,
    MOBILE_RUNTIME,
    UI_METADATA
}

internal enum class OrcaPreservationStatus {
    PRESERVED_FOR_IMPORT_EXPORT,
    PRESERVED_FOR_DISPLAY_ONLY,
    NOT_PRESERVED,
    MOBILE_RUNTIME_ONLY,
    MIGRATION_UNKNOWN
}

internal enum class OrcaApplicationStatus {
    APPLIED,
    STORED_ONLY,
    BLOCKED_ON_MOBILE,
    NEEDS_MAPPING,
    METADATA_ONLY,
    NOT_ORCA_RUNTIME,
    MIGRATION_UNKNOWN
}

internal enum class OrcaNativeMappingKind {
    DIRECT,
    NORMALIZED,
    CUSTOM,
    OBJECT_SCOPED,
    FILAMENT_VECTOR,
    PRINTER_VECTOR,
    NOT_APPLIED
}

internal enum class OrcaSettingDangerClass {
    SAFE_SCALAR,
    NEEDS_NORMALIZATION,
    SCOPED_INDEX,
    MULTI_MATERIAL,
    TOOL_OR_NOZZLE_MAPPING,
    HOST_OR_NETWORK,
    DESKTOP_SCRIPT,
    DESKTOP_PATH,
    METADATA,
    UNKNOWN
}

internal data class MobileOrcaSettingPolicy(
    val key: String,
    val storageScopes: Set<OrcaSettingScope>,
    val applicationScopes: Set<OrcaSettingScope>,
    val preservationStatus: OrcaPreservationStatus,
    val applicationStatus: OrcaApplicationStatus,
    val nativeMapping: OrcaNativeMappingKind,
    val dangerClass: OrcaSettingDangerClass,
    val mobileReason: String,
    val proof: String,
    val uiVisibleAsActive: Boolean = false
)

internal data class CombinedOrcaSettingDefinition(
    val metadata: OrcaSettingMetadata?,
    val policy: MobileOrcaSettingPolicy
)

internal object OrcaSettingPolicyOverlay {
    private val blockedHostOrNetworkKeys = setOf(
        "bbl_use_printhost",
        "print_host",
        "print_host_webui",
        "printer_agent",
        "printhost_apikey",
        "printhost_authorization_type",
        "printhost_cafile",
        "printhost_group",
        "printhost_password",
        "printhost_port",
        "printhost_ssl_ignore_revoke",
        "printhost_user"
    )

    private val desktopPathKeys = setOf(
        "bed_custom_model",
        "bed_custom_texture",
        "bed_model",
        "bed_texture",
        "bottom_texture_end_name",
        "scan_folder",
        "thumbnails",
        "thumbnails_format",
        "thumbnails_internal",
        "thumbnails_internal_switch"
    )

    private val blockedMobileFeatureKeys = setOf(
        "enable_wrapping_detection",
        "gcode_add_line_number",
        "timelapse_type"
    )

    private val desktopScriptKeys = setOf(
        "post_process"
    )

    private val scopedIndexKeys = setOf(
        "support_filament",
        "support_interface_filament"
    )

    private val blockedGlobalScopedIndexKeys = setOf(
        "sparse_infill_filament"
    )

    private val explicitMultiMaterialProcessKeys = setOf(
        "flush_into_infill",
        "flush_into_objects",
        "flush_into_support",
        "single_extruder_multi_material_priming",
        "ooze_prevention",
        "prime_volume"
    )

    private val mobileRuntimeKeys = setOf(
        NativeConfigKeys.Mobile.ActiveFilamentSlotCount,
        NativeConfigKeys.Mobile.SingleMaterialNativeSlice
    )

    private val metadataOnlyKeys = setOf(
        "active_feeder_motor_name",
        "box_id",
        "family",
        "filament_notes",
        "filename_format",
        "hotend_model",
        "image_bed_type",
        "notes",
        "nozzle_hrc",
        "printer_notes",
        "url"
    )

    private val preservedCompatibilityOnlyKeys = setOf(
        "nozzle_flush_dataset"
    )

    fun policyFor(
        key: String,
        metadata: OrcaSettingMetadata?,
        storageScopes: Set<OrcaSettingScope>,
        nativeAppliedKeys: Set<String>,
        documentedNonOrcaKeys: Set<String>
    ): MobileOrcaSettingPolicy {
        if (key in mobileRuntimeKeys) {
            return MobileOrcaSettingPolicy(
                key = key,
                storageScopes = setOf(OrcaSettingScope.MOBILE_RUNTIME),
                applicationScopes = setOf(OrcaSettingScope.MOBILE_RUNTIME),
                preservationStatus = OrcaPreservationStatus.MOBILE_RUNTIME_ONLY,
                applicationStatus = OrcaApplicationStatus.NOT_ORCA_RUNTIME,
                nativeMapping = OrcaNativeMappingKind.CUSTOM,
                dangerClass = OrcaSettingDangerClass.METADATA,
                mobileReason = "MobileSlicer runtime boundary key, not an Orca desktop setting.",
                proof = "Documented native config runtime metadata."
            )
        }

        if (metadata == null && key in nativeAppliedKeys) {
            return MobileOrcaSettingPolicy(
                key = key,
                storageScopes = storageScopes,
                applicationScopes = storageScopes.ifEmpty { inferredScopesForKey(key) },
                preservationStatus = OrcaPreservationStatus.PRESERVED_FOR_IMPORT_EXPORT,
                applicationStatus = OrcaApplicationStatus.APPLIED,
                nativeMapping = inferredMappingKind(key),
                dangerClass = inferredDangerClass(key),
                mobileReason = "Native wrapper applies this key, but generated Orca metadata does not expose it.",
                proof = "Native applied-key manifest + parity audit."
            )
        }

        if (metadata == null) {
            return MobileOrcaSettingPolicy(
                key = key,
                storageScopes = if (key in documentedNonOrcaKeys) storageScopes else emptySet(),
                applicationScopes = emptySet(),
                preservationStatus = if (key in documentedNonOrcaKeys) {
                    OrcaPreservationStatus.PRESERVED_FOR_DISPLAY_ONLY
                } else {
                    OrcaPreservationStatus.MIGRATION_UNKNOWN
                },
                applicationStatus = if (key in documentedNonOrcaKeys) {
                    OrcaApplicationStatus.NOT_ORCA_RUNTIME
                } else {
                    OrcaApplicationStatus.MIGRATION_UNKNOWN
                },
                nativeMapping = OrcaNativeMappingKind.NOT_APPLIED,
                dangerClass = if (key in documentedNonOrcaKeys) {
                    OrcaSettingDangerClass.METADATA
                } else {
                    OrcaSettingDangerClass.UNKNOWN
                },
                mobileReason = if (key in documentedNonOrcaKeys) {
                    "Documented MobileSlicer alias or metadata key."
                } else {
                    "Unknown key with no generated Orca metadata and no MobileSlicer classification."
                },
                proof = if (key in documentedNonOrcaKeys) {
                    "Native config alias/metadata allowlist."
                } else {
                    "None"
                }
            )
        }

        if (key in desktopScriptKeys) {
            return blockedPolicy(
                key = key,
                storageScopes = storageScopes,
                dangerClass = OrcaSettingDangerClass.DESKTOP_SCRIPT,
                reason = "Desktop post-processing scripts are preserved but never executed on Android."
            )
        }

        if (key in blockedMobileFeatureKeys) {
            return blockedPolicy(
                key = key,
                storageScopes = storageScopes,
                dangerClass = OrcaSettingDangerClass.NEEDS_NORMALIZATION,
                reason = "Desktop or firmware-adjacent behavior is preserved but not exposed as Android slicing behavior."
            )
        }

        if (key in nativeAppliedKeys) {
            return MobileOrcaSettingPolicy(
                key = key,
                storageScopes = storageScopes,
                applicationScopes = storageScopes.ifEmpty { inferredScopesForKey(key) },
                preservationStatus = OrcaPreservationStatus.PRESERVED_FOR_IMPORT_EXPORT,
                applicationStatus = OrcaApplicationStatus.APPLIED,
                nativeMapping = inferredMappingKind(key),
                dangerClass = inferredDangerClass(key),
                mobileReason = "Explicit native wrapper mapping is covered by the applied-key manifest.",
                proof = "Native applied-key manifest + parity audit."
            )
        }

        if (key in blockedHostOrNetworkKeys) {
            return blockedPolicy(
                key = key,
                storageScopes = storageScopes + OrcaSettingScope.HOST_INTEGRATION,
                dangerClass = OrcaSettingDangerClass.HOST_OR_NETWORK,
                reason = "Printer host credentials and remote host behavior are not native slicing controls."
            )
        }

        if (key in blockedGlobalScopedIndexKeys) {
            return blockedPolicy(
                key = key,
                storageScopes = storageScopes + OrcaSettingScope.OBJECT + OrcaSettingScope.REGION,
                dangerClass = OrcaSettingDangerClass.SCOPED_INDEX,
                reason = "This 1-based material/tool selector is applied only through object or paint scoped paths, never as a global process override."
            )
        }

        if (key in desktopPathKeys) {
            return metadataPreservedPolicy(
                key = key,
                storageScopes = storageScopes,
                dangerClass = OrcaSettingDangerClass.DESKTOP_PATH,
                reason = "Desktop asset/path metadata is preserved but not applied as Android slicing behavior."
            )
        }

        if (key in scopedIndexKeys) {
            return preservedOnlyPolicy(
                key = key,
                storageScopes = storageScopes,
                dangerClass = OrcaSettingDangerClass.SCOPED_INDEX,
                reason = "This key needs explicit object/material/tool scope before it can be safely applied."
            )
        }

        if (key in explicitMultiMaterialProcessKeys) {
            return preservedOnlyPolicy(
                key = key,
                storageScopes = storageScopes,
                dangerClass = OrcaSettingDangerClass.MULTI_MATERIAL,
                reason = "Multi-material process behavior is preserved until a native mapping is explicitly proven."
            )
        }

        if (key in metadataOnlyKeys) {
            return MobileOrcaSettingPolicy(
                key = key,
                storageScopes = storageScopes,
                applicationScopes = emptySet(),
                preservationStatus = OrcaPreservationStatus.PRESERVED_FOR_DISPLAY_ONLY,
                applicationStatus = OrcaApplicationStatus.METADATA_ONLY,
                nativeMapping = OrcaNativeMappingKind.NOT_APPLIED,
                dangerClass = OrcaSettingDangerClass.METADATA,
                mobileReason = "Identity/display metadata, not a slicing control.",
                proof = "Metadata-only policy classification."
            )
        }

        if (key in preservedCompatibilityOnlyKeys) {
            return metadataPreservedPolicy(
                key = key,
                storageScopes = storageScopes,
                dangerClass = OrcaSettingDangerClass.METADATA,
                reason = "Compatibility data is preserved for profile fidelity but is not a current Android slicing control."
            )
        }

        return MobileOrcaSettingPolicy(
            key = key,
            storageScopes = storageScopes,
            applicationScopes = emptySet(),
            preservationStatus = OrcaPreservationStatus.PRESERVED_FOR_IMPORT_EXPORT,
            applicationStatus = OrcaApplicationStatus.STORED_ONLY,
            nativeMapping = OrcaNativeMappingKind.NOT_APPLIED,
            dangerClass = inferredDangerClass(key),
            mobileReason = "Generated Orca key is preserved for import/export but is not active Android slicing behavior.",
            proof = "Generated Orca metadata; no native applied-key manifest entry."
        )
    }

    private fun blockedPolicy(
        key: String,
        storageScopes: Set<OrcaSettingScope>,
        dangerClass: OrcaSettingDangerClass,
        reason: String
    ) = MobileOrcaSettingPolicy(
        key = key,
        storageScopes = storageScopes,
        applicationScopes = emptySet(),
        preservationStatus = OrcaPreservationStatus.PRESERVED_FOR_IMPORT_EXPORT,
        applicationStatus = OrcaApplicationStatus.BLOCKED_ON_MOBILE,
        nativeMapping = OrcaNativeMappingKind.NOT_APPLIED,
        dangerClass = dangerClass,
        mobileReason = reason,
        proof = "Blocked by MobileSlicer policy."
    )

    private fun preservedOnlyPolicy(
        key: String,
        storageScopes: Set<OrcaSettingScope>,
        dangerClass: OrcaSettingDangerClass,
        reason: String
    ) = MobileOrcaSettingPolicy(
        key = key,
        storageScopes = storageScopes,
        applicationScopes = emptySet(),
        preservationStatus = OrcaPreservationStatus.PRESERVED_FOR_IMPORT_EXPORT,
        applicationStatus = OrcaApplicationStatus.STORED_ONLY,
        nativeMapping = OrcaNativeMappingKind.NOT_APPLIED,
        dangerClass = dangerClass,
        mobileReason = reason,
        proof = "Preserved-only policy classification."
    )

    private fun metadataPreservedPolicy(
        key: String,
        storageScopes: Set<OrcaSettingScope>,
        dangerClass: OrcaSettingDangerClass,
        reason: String
    ) = MobileOrcaSettingPolicy(
        key = key,
        storageScopes = storageScopes,
        applicationScopes = emptySet(),
        preservationStatus = OrcaPreservationStatus.PRESERVED_FOR_IMPORT_EXPORT,
        applicationStatus = OrcaApplicationStatus.METADATA_ONLY,
        nativeMapping = OrcaNativeMappingKind.NOT_APPLIED,
        dangerClass = dangerClass,
        mobileReason = reason,
        proof = "Preserved compatibility-only policy classification."
    )

    private fun inferredScopesForKey(key: String): Set<OrcaSettingScope> =
        when {
            key.startsWith("filament_") ||
                key in setOf(
                    "nozzle_temperature",
                    "nozzle_temperature_initial_layer",
                    "bed_temperature",
                    "bed_temperature_initial_layer",
                    "cool_plate_temp",
                    "cool_plate_temp_initial_layer",
                    "textured_cool_plate_temp",
                    "textured_cool_plate_temp_initial_layer",
                    "eng_plate_temp",
                    "eng_plate_temp_initial_layer",
                    "supertack_plate_temp",
                    "supertack_plate_temp_initial_layer",
                    "hot_plate_temp",
                    "hot_plate_temp_initial_layer",
                    "textured_plate_temp",
                    "textured_plate_temp_initial_layer"
                ) -> setOf(OrcaSettingScope.FILAMENT)
            key.startsWith("machine_") ||
                key.startsWith("printer_") ||
                key.startsWith("bed_") ||
                key.startsWith("extruder_") ||
                key.startsWith("nozzle_") -> setOf(OrcaSettingScope.PRINTER)
            else -> setOf(OrcaSettingScope.PROCESS)
        }

    private fun inferredMappingKind(key: String): OrcaNativeMappingKind =
        when {
            key.contains("filament") ||
                key.contains("extruder") ||
                key.contains("nozzle") -> OrcaNativeMappingKind.FILAMENT_VECTOR
            key.contains("bed") ||
                key.contains("printable") -> OrcaNativeMappingKind.NORMALIZED
            key.contains("support") ||
                key.contains("prime") ||
                key.contains("wipe_tower") -> OrcaNativeMappingKind.CUSTOM
            else -> OrcaNativeMappingKind.DIRECT
        }

    private fun inferredDangerClass(key: String): OrcaSettingDangerClass =
        when {
            key.contains("filament") ||
                key.contains("extruder") ||
                key.contains("tool") ||
                key.contains("nozzle") -> OrcaSettingDangerClass.MULTI_MATERIAL
            key in blockedHostOrNetworkKeys ||
                key.contains("url") -> OrcaSettingDangerClass.HOST_OR_NETWORK
            key.contains("gcode") -> OrcaSettingDangerClass.NEEDS_NORMALIZATION
            key.contains("path") ||
                key.contains("texture") ||
                key.contains("model") -> OrcaSettingDangerClass.DESKTOP_PATH
            else -> OrcaSettingDangerClass.SAFE_SCALAR
        }
}

package com.mobileslicer.profiles

internal object OrcaSettingRegistry {
    fun definitionForNativeConfigKey(
        key: String,
        storageScopes: Set<OrcaSettingScope>,
        nativeAppliedKeys: Set<String>,
        documentedNonOrcaKeys: Set<String>
    ): CombinedOrcaSettingDefinition {
        val metadata = GeneratedOrcaSettingMetadata.all[key]
        return CombinedOrcaSettingDefinition(
            metadata = metadata,
            policy = OrcaSettingPolicyOverlay.policyFor(
                key = key,
                metadata = metadata,
                storageScopes = storageScopes,
                nativeAppliedKeys = nativeAppliedKeys,
                documentedNonOrcaKeys = documentedNonOrcaKeys
            )
        )
    }
}

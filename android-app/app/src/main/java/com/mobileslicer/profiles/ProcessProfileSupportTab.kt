package com.mobileslicer.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import com.mobileslicer.AppSettingOption

@Composable
internal fun ProcessSupportTabContent(
    showAdvancedProfileSettings: Boolean,
    boolEnabledDisabledOptions: List<AppSettingOption<Boolean>>,
    supportTypeOptions: List<AppSettingOption<SupportType>>,
    supportStyleOptions: List<AppSettingOption<SupportStyle>>,
    ironingPatternOptions: List<AppSettingOption<IroningPattern>>,
    supportBasePatternOptions: List<AppSettingOption<SupportBasePattern>>,
    supportInterfacePatternOptions: List<AppSettingOption<SupportInterfacePattern>>,
    enableSupport: Boolean,
    onEnableSupportChange: (Boolean) -> Unit,
    supportType: SupportType,
    onSupportTypeChange: (SupportType) -> Unit,
    supportStyle: SupportStyle,
    onSupportStyleChange: (SupportStyle) -> Unit,
    supportThresholdAngle: String,
    onSupportThresholdAngleChange: (String) -> Unit,
    supportThresholdOverlap: String,
    onSupportThresholdOverlapChange: (String) -> Unit,
    raftFirstLayerDensity: String,
    onRaftFirstLayerDensityChange: (String) -> Unit,
    raftFirstLayerExpansion: String,
    onRaftFirstLayerExpansionChange: (String) -> Unit,
    supportBuildplateOnly: Boolean,
    onSupportBuildplateOnlyChange: (Boolean) -> Unit,
    supportCriticalRegionsOnly: Boolean,
    onSupportCriticalRegionsOnlyChange: (Boolean) -> Unit,
    supportRemoveSmallOverhang: Boolean,
    onSupportRemoveSmallOverhangChange: (Boolean) -> Unit,
    raftLayers: String,
    onRaftLayersChange: (String) -> Unit,
    raftContactDistance: String,
    onRaftContactDistanceChange: (String) -> Unit,
    raftExpansion: String,
    onRaftExpansionChange: (String) -> Unit,
    supportFilament: String,
    onSupportFilamentChange: (String) -> Unit,
    supportInterfaceFilament: String,
    onSupportInterfaceFilamentChange: (String) -> Unit,
    supportInterfaceNotForBody: Boolean,
    onSupportInterfaceNotForBodyChange: (Boolean) -> Unit,
    supportIroning: Boolean,
    onSupportIroningChange: (Boolean) -> Unit,
    supportIroningPattern: IroningPattern,
    onSupportIroningPatternChange: (IroningPattern) -> Unit,
    supportIroningFlow: String,
    onSupportIroningFlowChange: (String) -> Unit,
    supportIroningSpacing: String,
    onSupportIroningSpacingChange: (String) -> Unit,
    supportTopZDistance: String,
    onSupportTopZDistanceChange: (String) -> Unit,
    supportBottomZDistance: String,
    onSupportBottomZDistanceChange: (String) -> Unit,
    treeSupportWallCount: String,
    onTreeSupportWallCountChange: (String) -> Unit,
    supportBasePattern: SupportBasePattern,
    onSupportBasePatternChange: (SupportBasePattern) -> Unit,
    supportBasePatternSpacing: String,
    onSupportBasePatternSpacingChange: (String) -> Unit,
    supportAngle: String,
    onSupportAngleChange: (String) -> Unit,
    supportInterfaceTopLayers: String,
    onSupportInterfaceTopLayersChange: (String) -> Unit,
    supportInterfaceBottomLayers: String,
    onSupportInterfaceBottomLayersChange: (String) -> Unit,
    supportInterfacePattern: SupportInterfacePattern,
    onSupportInterfacePatternChange: (SupportInterfacePattern) -> Unit,
    supportInterfaceSpacing: String,
    onSupportInterfaceSpacingChange: (String) -> Unit,
    supportBottomInterfaceSpacing: String,
    onSupportBottomInterfaceSpacingChange: (String) -> Unit,
    supportExpansion: String,
    onSupportExpansionChange: (String) -> Unit,
    supportObjectXyDistance: String,
    onSupportObjectXyDistanceChange: (String) -> Unit,
    supportObjectFirstLayerGap: String,
    onSupportObjectFirstLayerGapChange: (String) -> Unit,
    bridgeNoSupport: Boolean,
    onBridgeNoSupportChange: (Boolean) -> Unit,
    supportMaxBridgeLength: String,
    onSupportMaxBridgeLengthChange: (String) -> Unit,
    supportInterfaceLoopPattern: Boolean,
    onSupportInterfaceLoopPatternChange: (Boolean) -> Unit,
    supportObjectElevation: String,
    onSupportObjectElevationChange: (String) -> Unit,
    independentSupportLayerHeight: Boolean,
    onIndependentSupportLayerHeightChange: (Boolean) -> Unit,
    treeSupportTipDiameter: String,
    onTreeSupportTipDiameterChange: (String) -> Unit,
    treeSupportBranchDistance: String,
    onTreeSupportBranchDistanceChange: (String) -> Unit,
    treeSupportBranchDistanceOrganic: String,
    onTreeSupportBranchDistanceOrganicChange: (String) -> Unit,
    treeSupportTopRate: String,
    onTreeSupportTopRateChange: (String) -> Unit,
    treeSupportBranchDiameter: String,
    onTreeSupportBranchDiameterChange: (String) -> Unit,
    treeSupportBranchDiameterOrganic: String,
    onTreeSupportBranchDiameterOrganicChange: (String) -> Unit,
    treeSupportBranchDiameterAngle: String,
    onTreeSupportBranchDiameterAngleChange: (String) -> Unit,
    treeSupportBranchAngle: String,
    onTreeSupportBranchAngleChange: (String) -> Unit,
    treeSupportBranchAngleOrganic: String,
    onTreeSupportBranchAngleOrganicChange: (String) -> Unit,
    treeSupportPreferredBranchAngle: String,
    onTreeSupportPreferredBranchAngleChange: (String) -> Unit,
    treeSupportAutoBrim: Boolean,
    onTreeSupportAutoBrimChange: (Boolean) -> Unit,
    treeSupportBrimWidth: String,
    onTreeSupportBrimWidthChange: (String) -> Unit,
) {
    ProfileEditorSection("Support", "Support generation, contact gaps, and tree support settings.") {
        ProfileGroupHeader("Support")
        ProfileDropdownField(
            label = "Enable support",
            selectedLabel = if (enableSupport) "Enabled" else "Disabled",
            options = boolEnabledDisabledOptions,
            onSelected = onEnableSupportChange
        )
        ProfileDropdownField(
            label = "Support type",
            selectedLabel = supportType.displayLabel,
            options = supportTypeOptions,
            onSelected = onSupportTypeChange
        )
        if (ProfileEditorSetting.ProcessSupportAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileDropdownField(
                label = "Support style",
                selectedLabel = supportStyle.displayLabel,
                options = supportStyleOptions,
                onSelected = onSupportStyleChange
            )
        }
        ProfileTextField(
            supportThresholdAngle,
            onSupportThresholdAngleChange,
            "Support threshold angle (degrees)",
            KeyboardType.Number
        )
        ProfileTextField(
            supportThresholdOverlap,
            onSupportThresholdOverlapChange,
            "Support threshold overlap (mm or %)",
            KeyboardType.Text
        )
        if (ProfileEditorSetting.ProcessSupportAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileGroupHeader("Raft")
            ProfileTextField(raftFirstLayerDensity, onRaftFirstLayerDensityChange, "Raft/support first layer density (%)", KeyboardType.Number)
            ProfileTextField(raftFirstLayerExpansion, onRaftFirstLayerExpansionChange, "Raft/support first layer expansion (mm)", KeyboardType.Decimal)
        }
        ProfileDropdownField(
            label = "Support on build plate only",
            selectedLabel = if (supportBuildplateOnly) "Enabled" else "Disabled",
            options = boolEnabledDisabledOptions,
            onSelected = onSupportBuildplateOnlyChange
        )
        if (ProfileEditorSetting.ProcessSupportAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileDropdownField(
                label = "Support critical regions only",
                selectedLabel = if (supportCriticalRegionsOnly) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onSupportCriticalRegionsOnlyChange
            )
            ProfileDropdownField(
                label = "Ignore small overhangs",
                selectedLabel = if (supportRemoveSmallOverhang) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onSupportRemoveSmallOverhangChange
            )
            ProfileTextField(raftLayers, onRaftLayersChange, "Raft layers", KeyboardType.Number)
            ProfileTextField(raftContactDistance, onRaftContactDistanceChange, "Raft contact Z distance (mm)", KeyboardType.Decimal)
            ProfileTextField(raftExpansion, onRaftExpansionChange, "Raft expansion (mm)", KeyboardType.Decimal)
        }
        ProfileGroupHeader("Support filament")
        ProfileTextField(supportFilament, onSupportFilamentChange, "Support/raft base filament", KeyboardType.Number)
        ProfileTextField(supportInterfaceFilament, onSupportInterfaceFilamentChange, "Support/raft interface filament", KeyboardType.Number)
        ProfileDropdownField(
            label = "Avoid interface filament for base",
            selectedLabel = if (supportInterfaceNotForBody) "Enabled" else "Disabled",
            options = boolEnabledDisabledOptions,
            onSelected = onSupportInterfaceNotForBodyChange
        )
        if (ProfileEditorSetting.ProcessSupportAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileGroupHeader("Support ironing")
            ProfileDropdownField(
                label = "Support ironing",
                selectedLabel = if (supportIroning) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onSupportIroningChange
            )
            ProfileDropdownField(
                label = "Support ironing pattern",
                selectedLabel = supportIroningPattern.displayLabel,
                options = ironingPatternOptions,
                onSelected = onSupportIroningPatternChange
            )
            ProfileTextField(
                supportIroningFlow,
                onSupportIroningFlowChange,
                "Support ironing flow (%)",
                KeyboardType.Decimal
            )
            ProfileTextField(
                supportIroningSpacing,
                onSupportIroningSpacingChange,
                "Support ironing spacing (mm)",
                KeyboardType.Decimal
            )
            ProfileGroupHeader("Advanced")
            ProfileTextField(
                supportTopZDistance,
                onSupportTopZDistanceChange,
                "Support top Z distance (mm)",
                KeyboardType.Decimal
            )
            ProfileTextField(
                supportBottomZDistance,
                onSupportBottomZDistanceChange,
                "Support bottom Z distance (mm)",
                KeyboardType.Decimal
            )
            ProfileTextField(treeSupportWallCount, onTreeSupportWallCountChange, "Support wall loops", KeyboardType.Number)
            ProfileDropdownField(
                label = "Support base pattern",
                selectedLabel = supportBasePattern.displayLabel,
                options = supportBasePatternOptions,
                onSelected = onSupportBasePatternChange
            )
            ProfileTextField(
                supportBasePatternSpacing,
                onSupportBasePatternSpacingChange,
                "Support base pattern spacing (mm)",
                KeyboardType.Decimal
            )
            ProfileTextField(
                supportAngle,
                onSupportAngleChange,
                "Support pattern angle (degrees)",
                KeyboardType.Decimal
            )
            ProfileTextField(
                supportInterfaceTopLayers,
                onSupportInterfaceTopLayersChange,
                "Support interface top layers",
                KeyboardType.Number
            )
            ProfileTextField(
                supportInterfaceBottomLayers,
                onSupportInterfaceBottomLayersChange,
                "Support interface bottom layers",
                KeyboardType.Number
            )
            ProfileDropdownField(
                label = "Support interface pattern",
                selectedLabel = supportInterfacePattern.displayLabel,
                options = supportInterfacePatternOptions,
                onSelected = onSupportInterfacePatternChange
            )
            ProfileTextField(
                supportInterfaceSpacing,
                onSupportInterfaceSpacingChange,
                "Support interface spacing (mm)",
                KeyboardType.Decimal
            )
            ProfileTextField(
                supportBottomInterfaceSpacing,
                onSupportBottomInterfaceSpacingChange,
                "Support bottom interface spacing (mm)",
                KeyboardType.Decimal
            )
            ProfileTextField(
                supportExpansion,
                onSupportExpansionChange,
                "Support expansion (mm)",
                KeyboardType.Decimal
            )
            ProfileTextField(
                supportObjectXyDistance,
                onSupportObjectXyDistanceChange,
                "Support object XY distance (mm)",
                KeyboardType.Decimal
            )
            ProfileTextField(
                supportObjectFirstLayerGap,
                onSupportObjectFirstLayerGapChange,
                "Support object first layer gap (mm)",
                KeyboardType.Decimal
            )
            ProfileDropdownField(
                label = "Don't support bridges",
                selectedLabel = if (bridgeNoSupport) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onBridgeNoSupportChange
            )
            ProfileTextField(
                supportMaxBridgeLength,
                onSupportMaxBridgeLengthChange,
                "Support max bridge length (mm)",
                KeyboardType.Decimal
            )
            ProfileDropdownField(
                label = "Support interface loop pattern",
                selectedLabel = if (supportInterfaceLoopPattern) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onSupportInterfaceLoopPatternChange
            )
            ProfileTextField(
                supportObjectElevation,
                onSupportObjectElevationChange,
                "Support object elevation (mm)",
                KeyboardType.Decimal
            )
            ProfileDropdownField(
                label = "Independent support layer height",
                selectedLabel = if (independentSupportLayerHeight) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onIndependentSupportLayerHeightChange
            )
            ProfileGroupHeader("Tree supports")
            ProfileTextField(treeSupportTipDiameter, onTreeSupportTipDiameterChange, "Tip diameter (mm)", KeyboardType.Decimal)
            ProfileTextField(treeSupportBranchDistance, onTreeSupportBranchDistanceChange, "Tree support branch distance (mm)", KeyboardType.Decimal)
            ProfileTextField(treeSupportBranchDistanceOrganic, onTreeSupportBranchDistanceOrganicChange, "Organic branch distance (mm)", KeyboardType.Decimal)
            ProfileTextField(treeSupportTopRate, onTreeSupportTopRateChange, "Branch density (%)", KeyboardType.Number)
            ProfileTextField(treeSupportBranchDiameter, onTreeSupportBranchDiameterChange, "Tree support branch diameter (mm)", KeyboardType.Decimal)
            ProfileTextField(treeSupportBranchDiameterOrganic, onTreeSupportBranchDiameterOrganicChange, "Organic branch diameter (mm)", KeyboardType.Decimal)
            ProfileTextField(treeSupportBranchDiameterAngle, onTreeSupportBranchDiameterAngleChange, "Branch diameter angle (degrees)", KeyboardType.Decimal)
            ProfileTextField(treeSupportBranchAngle, onTreeSupportBranchAngleChange, "Tree support branch angle (degrees)", KeyboardType.Decimal)
            ProfileTextField(treeSupportBranchAngleOrganic, onTreeSupportBranchAngleOrganicChange, "Organic branch angle (degrees)", KeyboardType.Decimal)
            ProfileTextField(treeSupportPreferredBranchAngle, onTreeSupportPreferredBranchAngleChange, "Preferred branch angle (degrees)", KeyboardType.Decimal)
            ProfileDropdownField(
                label = "Auto brim width",
                selectedLabel = if (treeSupportAutoBrim) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onTreeSupportAutoBrimChange
            )
            ProfileTextField(treeSupportBrimWidth, onTreeSupportBrimWidthChange, "Tree support brim width (mm)", KeyboardType.Decimal)
        }
    }
}

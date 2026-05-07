package com.mobileslicer.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import com.mobileslicer.AppSettingOption

@Composable
internal fun ProcessMultimaterialTabContent(
    showAdvancedProfileSettings: Boolean,
    boolEnabledDisabledOptions: List<AppSettingOption<Boolean>>,
    wipeTowerWallTypeOptions: List<AppSettingOption<WipeTowerWallType>>,
    enablePrimeTower: Boolean,
    onEnablePrimeTowerChange: (Boolean) -> Unit,
    primeTowerWidth: String,
    onPrimeTowerWidthChange: (String) -> Unit,
    primeTowerDetails: PrimeTowerDetailsDraft,
    onPrimeTowerDetailsChange: (PrimeTowerDetailsDraft) -> Unit,
    enableTowerInterfaceFeatures: Boolean,
    onEnableTowerInterfaceFeaturesChange: (Boolean) -> Unit,
    enableTowerInterfaceCooldownDuringTower: Boolean,
    onEnableTowerInterfaceCooldownDuringTowerChange: (Boolean) -> Unit,
    singleExtruderMultiMaterialPriming: Boolean,
    onSingleExtruderMultiMaterialPrimingChange: (Boolean) -> Unit,
    standbyTemperatureDelta: String,
    onStandbyTemperatureDeltaChange: (String) -> Unit,
    wipeTowerNoSparseLayers: Boolean,
    onWipeTowerNoSparseLayersChange: (Boolean) -> Unit,
    sparseInfillFilament: String,
    onSparseInfillFilamentChange: (String) -> Unit,
    flushIntoInfill: Boolean,
    onFlushIntoInfillChange: (Boolean) -> Unit,
    flushIntoObjects: Boolean,
    onFlushIntoObjectsChange: (Boolean) -> Unit,
    flushIntoSupport: Boolean,
    onFlushIntoSupportChange: (Boolean) -> Unit,
    interfaceShells: Boolean,
    onInterfaceShellsChange: (Boolean) -> Unit,
) {
    ProfileEditorSection("Multimaterial", "Prime tower, purge, and filament assignment behavior.") {
        if (ProfileEditorSetting.ProcessMultimaterialCore.isVisible(showAdvancedProfileSettings)) {
            ProfileGroupHeader("Prime tower")
            ProfileDropdownField(
                label = "Enable prime tower",
                selectedLabel = if (enablePrimeTower) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onEnablePrimeTowerChange
            )
            ProfileTextField(primeTowerWidth, onPrimeTowerWidthChange, "Prime tower width (mm)", KeyboardType.Decimal)
            ProfileTextField(
                primeTowerDetails.wipeTowerX,
                { onPrimeTowerDetailsChange(primeTowerDetails.copy(wipeTowerX = it)) },
                "Wipe tower X (mm)",
                KeyboardType.Decimal
            )
            ProfileTextField(
                primeTowerDetails.wipeTowerY,
                { onPrimeTowerDetailsChange(primeTowerDetails.copy(wipeTowerY = it)) },
                "Wipe tower Y (mm)",
                KeyboardType.Decimal
            )
            ProfileTextField(
                primeTowerDetails.primeVolume,
                { onPrimeTowerDetailsChange(primeTowerDetails.copy(primeVolume = it)) },
                "Prime volume (mm³)",
                KeyboardType.Decimal
            )
        }

        if (ProfileEditorSetting.ProcessMultimaterialAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileGroupHeader("Advanced")
            ProfileDropdownField(
                label = "Interface shells",
                selectedLabel = if (interfaceShells) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onInterfaceShellsChange
            )
            ProfileTextField(sparseInfillFilament, onSparseInfillFilamentChange, "Sparse infill filament", KeyboardType.Number)
            ProfileDropdownField(
                label = "Prime tower skip points",
                selectedLabel = if (primeTowerDetails.primeTowerSkipPoints) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = { onPrimeTowerDetailsChange(primeTowerDetails.copy(primeTowerSkipPoints = it)) }
            )
            ProfileTextField(
                primeTowerDetails.primeTowerBrimWidth,
                { onPrimeTowerDetailsChange(primeTowerDetails.copy(primeTowerBrimWidth = it)) },
                "Prime tower brim width (mm)",
                KeyboardType.Decimal
            )
            ProfileTextField(
                primeTowerDetails.primeTowerInfillGap,
                { onPrimeTowerDetailsChange(primeTowerDetails.copy(primeTowerInfillGap = it)) },
                "Prime tower infill gap (%)",
                KeyboardType.Number
            )
            ProfileTextField(
                primeTowerDetails.wipeTowerRotationAngle,
                { onPrimeTowerDetailsChange(primeTowerDetails.copy(wipeTowerRotationAngle = it)) },
                "Wipe tower rotation angle",
                KeyboardType.Decimal
            )
            ProfileTextField(
                primeTowerDetails.wipeTowerBridging,
                { onPrimeTowerDetailsChange(primeTowerDetails.copy(wipeTowerBridging = it)) },
                "Wipe tower bridging (mm)",
                KeyboardType.Decimal
            )
            ProfileTextField(
                primeTowerDetails.wipeTowerExtraSpacing,
                { onPrimeTowerDetailsChange(primeTowerDetails.copy(wipeTowerExtraSpacing = it)) },
                "Wipe tower extra spacing (%)",
                KeyboardType.Number
            )
            ProfileTextField(
                primeTowerDetails.wipeTowerExtraFlow,
                { onPrimeTowerDetailsChange(primeTowerDetails.copy(wipeTowerExtraFlow = it)) },
                "Wipe tower extra flow (%)",
                KeyboardType.Number
            )
            ProfileTextField(
                primeTowerDetails.wipeTowerMaxPurgeSpeed,
                { onPrimeTowerDetailsChange(primeTowerDetails.copy(wipeTowerMaxPurgeSpeed = it)) },
                "Wipe tower max purge speed (mm/s)",
                KeyboardType.Decimal
            )
            ProfileDropdownField(
                label = "Wipe tower wall type",
                selectedLabel = primeTowerDetails.wipeTowerWallType.displayLabel,
                options = wipeTowerWallTypeOptions,
                onSelected = { onPrimeTowerDetailsChange(primeTowerDetails.copy(wipeTowerWallType = it)) }
            )
            ProfileTextField(
                primeTowerDetails.wipeTowerConeAngle,
                { onPrimeTowerDetailsChange(primeTowerDetails.copy(wipeTowerConeAngle = it)) },
                "Wipe tower cone angle",
                KeyboardType.Decimal
            )
            ProfileTextField(
                primeTowerDetails.wipeTowerExtraRibLength,
                { onPrimeTowerDetailsChange(primeTowerDetails.copy(wipeTowerExtraRibLength = it)) },
                "Wipe tower extra rib length (mm)",
                KeyboardType.Decimal
            )
            ProfileTextField(
                primeTowerDetails.wipeTowerRibWidth,
                { onPrimeTowerDetailsChange(primeTowerDetails.copy(wipeTowerRibWidth = it)) },
                "Wipe tower rib width (mm)",
                KeyboardType.Decimal
            )
            ProfileDropdownField(
                label = "Wipe tower fillet wall",
                selectedLabel = if (primeTowerDetails.wipeTowerFilletWall) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = { onPrimeTowerDetailsChange(primeTowerDetails.copy(wipeTowerFilletWall = it)) }
            )
            ProfileDropdownField(
                label = "Enable tower interface features",
                selectedLabel = if (enableTowerInterfaceFeatures) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onEnableTowerInterfaceFeaturesChange
            )
            ProfileDropdownField(
                label = "Tower interface cooldown during tower",
                selectedLabel = if (enableTowerInterfaceCooldownDuringTower) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onEnableTowerInterfaceCooldownDuringTowerChange
            )
            ProfileDropdownField(
                label = "Wipe tower no sparse layers",
                selectedLabel = if (wipeTowerNoSparseLayers) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onWipeTowerNoSparseLayersChange
            )
            ProfileDropdownField(
                label = "Single extruder multimaterial priming",
                selectedLabel = if (singleExtruderMultiMaterialPriming) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onSingleExtruderMultiMaterialPrimingChange
            )
            ProfileTextField(standbyTemperatureDelta, onStandbyTemperatureDeltaChange, "Standby temperature delta", KeyboardType.Number)
        }

        if (ProfileEditorSetting.ProcessMultimaterialFlushOptions.isVisible(showAdvancedProfileSettings)) {
            ProfileGroupHeader("Flush options")
            ProfileDropdownField(
                label = "Flush into objects' infill",
                selectedLabel = if (flushIntoInfill) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onFlushIntoInfillChange
            )
            ProfileDropdownField(
                label = "Flush into objects",
                selectedLabel = if (flushIntoObjects) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onFlushIntoObjectsChange
            )
            ProfileDropdownField(
                label = "Flush into this object support",
                selectedLabel = if (flushIntoSupport) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onFlushIntoSupportChange
            )
        }
    }
}

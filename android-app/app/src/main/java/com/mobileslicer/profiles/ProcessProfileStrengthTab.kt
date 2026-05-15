package com.mobileslicer.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import com.mobileslicer.AppSettingOption

@Composable
internal fun ProcessStrengthTabContent(
    showAdvancedProfileSettings: Boolean,
    boolEnabledDisabledOptions: List<AppSettingOption<Boolean>>,
    topSurfacePatternOptions: List<AppSettingOption<TopSurfacePattern>>,
    bottomSurfacePatternOptions: List<AppSettingOption<BottomSurfacePattern>>,
    internalSolidPatternOptions: List<AppSettingOption<InternalSolidInfillPattern>>,
    sparseInfillPatternOptions: List<AppSettingOption<SparseInfillPattern>>,
    gapFillTargetOptions: List<AppSettingOption<String>>,
    infillCombinationOptions: List<AppSettingOption<Boolean>>,
    ensureVerticalShellThicknessOptions: List<AppSettingOption<EnsureVerticalShellThicknessMode>>,
    wallCount: String,
    onWallCountChange: (String) -> Unit,
    wallCountChanged: Boolean = false,
    alternateExtraWall: Boolean,
    onAlternateExtraWallChange: (Boolean) -> Unit,
    topShellLayers: String,
    onTopShellLayersChange: (String) -> Unit,
    topShellLayersChanged: Boolean = false,
    topShellThickness: String,
    onTopShellThicknessChange: (String) -> Unit,
    topShellThicknessChanged: Boolean = false,
    topSurfaceDensityPercent: String,
    onTopSurfaceDensityPercentChange: (String) -> Unit,
    topSurfaceDensityPercentChanged: Boolean = false,
    topSurfacePattern: TopSurfacePattern,
    onTopSurfacePatternChange: (TopSurfacePattern) -> Unit,
    topSurfacePatternChanged: Boolean = false,
    bottomShellLayers: String,
    onBottomShellLayersChange: (String) -> Unit,
    bottomShellLayersChanged: Boolean = false,
    bottomShellThickness: String,
    onBottomShellThicknessChange: (String) -> Unit,
    bottomShellThicknessChanged: Boolean = false,
    bottomSurfaceDensityPercent: String,
    onBottomSurfaceDensityPercentChange: (String) -> Unit,
    bottomSurfaceDensityPercentChanged: Boolean = false,
    bottomSurfacePattern: BottomSurfacePattern,
    onBottomSurfacePatternChange: (BottomSurfacePattern) -> Unit,
    bottomSurfacePatternChanged: Boolean = false,
    topBottomInfillWallOverlapPercent: String,
    onTopBottomInfillWallOverlapPercentChange: (String) -> Unit,
    internalSolidInfillPattern: InternalSolidInfillPattern,
    onInternalSolidInfillPatternChange: (InternalSolidInfillPattern) -> Unit,
    infillPercent: String,
    onInfillPercentChange: (String) -> Unit,
    infillPercentChanged: Boolean = false,
    fillMultiline: String,
    onFillMultilineChange: (String) -> Unit,
    fillMultilineChanged: Boolean = false,
    sparseInfillPattern: SparseInfillPattern,
    onSparseInfillPatternChange: (SparseInfillPattern) -> Unit,
    sparseInfillPatternChanged: Boolean = false,
    extraSolidInfills: String,
    onExtraSolidInfillsChange: (String) -> Unit,
    detectThinWall: Boolean,
    onDetectThinWallChange: (Boolean) -> Unit,
    infillDirectionDegrees: String,
    onInfillDirectionDegreesChange: (String) -> Unit,
    sparseInfillRotateTemplate: String,
    onSparseInfillRotateTemplateChange: (String) -> Unit,
    skinInfillDensity: String,
    onSkinInfillDensityChange: (String) -> Unit,
    skeletonInfillDensity: String,
    onSkeletonInfillDensityChange: (String) -> Unit,
    infillLockDepth: String,
    onInfillLockDepthChange: (String) -> Unit,
    skinInfillDepth: String,
    onSkinInfillDepthChange: (String) -> Unit,
    skinInfillLineWidth: String,
    onSkinInfillLineWidthChange: (String) -> Unit,
    skeletonInfillLineWidth: String,
    onSkeletonInfillLineWidthChange: (String) -> Unit,
    symmetricInfillYAxis: Boolean,
    onSymmetricInfillYAxisChange: (Boolean) -> Unit,
    infillShiftStep: String,
    onInfillShiftStepChange: (String) -> Unit,
    lateralLatticeAngle1: String,
    onLateralLatticeAngle1Change: (String) -> Unit,
    lateralLatticeAngle2: String,
    onLateralLatticeAngle2Change: (String) -> Unit,
    infillOverhangAngle: String,
    onInfillOverhangAngleChange: (String) -> Unit,
    infillAnchorMax: String,
    onInfillAnchorMaxChange: (String) -> Unit,
    infillAnchor: String,
    onInfillAnchorChange: (String) -> Unit,
    solidInfillDirectionDegrees: String,
    onSolidInfillDirectionDegreesChange: (String) -> Unit,
    solidInfillRotateTemplate: String,
    onSolidInfillRotateTemplateChange: (String) -> Unit,
    gapFillTarget: String,
    onGapFillTargetChange: (String) -> Unit,
    filterOutGapFill: String,
    onFilterOutGapFillChange: (String) -> Unit,
    infillWallOverlapPercent: String,
    onInfillWallOverlapPercentChange: (String) -> Unit,
    alignInfillDirectionToModel: Boolean,
    onAlignInfillDirectionToModelChange: (Boolean) -> Unit,
    bridgeAngleDegrees: String,
    onBridgeAngleDegreesChange: (String) -> Unit,
    internalBridgeAngleDegrees: String,
    onInternalBridgeAngleDegreesChange: (String) -> Unit,
    minimumSparseInfillAreaMm2: String,
    onMinimumSparseInfillAreaMm2Change: (String) -> Unit,
    infillCombination: Boolean,
    onInfillCombinationChange: (Boolean) -> Unit,
    infillCombinationMaxLayerHeight: String,
    onInfillCombinationMaxLayerHeightChange: (String) -> Unit,
    detectNarrowInternalSolidInfill: Boolean,
    onDetectNarrowInternalSolidInfillChange: (Boolean) -> Unit,
    ensureVerticalShellThickness: EnsureVerticalShellThicknessMode,
    onEnsureVerticalShellThicknessChange: (EnsureVerticalShellThicknessMode) -> Unit,
) {
    ProfileEditorSection("Strength", "Shell, wall, infill, and adhesion controls.") {
        ProfileGroupHeader("Walls")
        ProfileTextField(wallCount, onWallCountChange, "Wall / perimeter count", KeyboardType.Number, changed = wallCountChanged)
        if (ProfileEditorSetting.ProcessStrengthAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileDropdownField(
                label = "Alternate extra wall",
                selectedLabel = if (alternateExtraWall) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onAlternateExtraWallChange
            )
            ProfileDropdownField(
                label = "Detect thin walls",
                selectedLabel = if (detectThinWall) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onDetectThinWallChange
            )
        }
        ProfileGroupHeader("Top/bottom shells")
        ProfileTextField(topShellLayers, onTopShellLayersChange, "Top shell layers", KeyboardType.Number, changed = topShellLayersChanged)
        ProfileTextField(topShellThickness, onTopShellThicknessChange, "Top shell thickness (mm)", KeyboardType.Decimal, changed = topShellThicknessChanged)
        ProfileTextField(topSurfaceDensityPercent, onTopSurfaceDensityPercentChange, "Top surface density (%)", KeyboardType.Number, changed = topSurfaceDensityPercentChanged)
        ProfileDropdownField(
            label = "Top surface pattern",
            selectedLabel = topSurfacePattern.displayLabel,
            options = topSurfacePatternOptions,
            onSelected = onTopSurfacePatternChange,
            changed = topSurfacePatternChanged
        )
        ProfileTextField(bottomShellLayers, onBottomShellLayersChange, "Bottom shell layers", KeyboardType.Number, changed = bottomShellLayersChanged)
        ProfileTextField(bottomShellThickness, onBottomShellThicknessChange, "Bottom shell thickness (mm)", KeyboardType.Decimal, changed = bottomShellThicknessChanged)
        ProfileTextField(bottomSurfaceDensityPercent, onBottomSurfaceDensityPercentChange, "Bottom surface density (%)", KeyboardType.Number, changed = bottomSurfaceDensityPercentChanged)
        ProfileDropdownField(
            label = "Bottom surface pattern",
            selectedLabel = bottomSurfacePattern.displayLabel,
            options = bottomSurfacePatternOptions,
            onSelected = onBottomSurfacePatternChange,
            changed = bottomSurfacePatternChanged
        )
        if (ProfileEditorSetting.ProcessStrengthAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileTextField(
                topBottomInfillWallOverlapPercent,
                onTopBottomInfillWallOverlapPercentChange,
                "Top / bottom solid infill wall overlap (%)",
                KeyboardType.Number
            )
        }
        ProfileGroupHeader("Infill")
        ProfileTextField(infillPercent, onInfillPercentChange, "Infill density (%)", KeyboardType.Number, changed = infillPercentChanged)
        ProfileTextField(fillMultiline, onFillMultilineChange, "Fill multiline", KeyboardType.Number, changed = fillMultilineChanged)
        ProfileDropdownField(
            label = "Sparse infill pattern",
            selectedLabel = sparseInfillPattern.displayLabel,
            options = sparseInfillPatternOptions,
            onSelected = onSparseInfillPatternChange,
            changed = sparseInfillPatternChanged
        )
        if (ProfileEditorSetting.ProcessStrengthAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileTextField(infillDirectionDegrees, onInfillDirectionDegreesChange, "Sparse infill direction (degrees)", KeyboardType.Decimal)
            ProfileTextField(sparseInfillRotateTemplate, onSparseInfillRotateTemplateChange, "Sparse infill rotate template")
            ProfileTextField(skinInfillDensity, onSkinInfillDensityChange, "Skin infill density (%)", KeyboardType.Number)
            ProfileTextField(skeletonInfillDensity, onSkeletonInfillDensityChange, "Skeleton infill density (%)", KeyboardType.Number)
            ProfileTextField(infillLockDepth, onInfillLockDepthChange, "Infill lock depth (mm)", KeyboardType.Decimal)
            ProfileTextField(skinInfillDepth, onSkinInfillDepthChange, "Skin infill depth (mm)", KeyboardType.Decimal)
            ProfileTextField(skinInfillLineWidth, onSkinInfillLineWidthChange, "Skin line width (mm or %)")
            ProfileTextField(skeletonInfillLineWidth, onSkeletonInfillLineWidthChange, "Skeleton line width (mm or %)")
            ProfileDropdownField(
                label = "Symmetric infill Y axis",
                selectedLabel = if (symmetricInfillYAxis) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onSymmetricInfillYAxisChange
            )
            ProfileTextField(infillShiftStep, onInfillShiftStepChange, "Infill shift step (mm)", KeyboardType.Decimal)
            ProfileTextField(lateralLatticeAngle1, onLateralLatticeAngle1Change, "Lateral lattice angle 1 (degrees)", KeyboardType.Decimal)
            ProfileTextField(lateralLatticeAngle2, onLateralLatticeAngle2Change, "Lateral lattice angle 2 (degrees)", KeyboardType.Decimal)
            ProfileTextField(infillOverhangAngle, onInfillOverhangAngleChange, "Infill overhang angle (degrees)", KeyboardType.Decimal)
            ProfileTextField(infillAnchorMax, onInfillAnchorMaxChange, "Sparse infill anchor max length (mm or %)")
            ProfileTextField(infillAnchor, onInfillAnchorChange, "Sparse infill anchor length (mm or %)")
        }
        ProfileDropdownField(
            label = "Internal solid infill pattern",
            selectedLabel = internalSolidInfillPattern.displayLabel,
            options = internalSolidPatternOptions,
            onSelected = onInternalSolidInfillPatternChange
        )
        if (ProfileEditorSetting.ProcessStrengthAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileTextField(solidInfillDirectionDegrees, onSolidInfillDirectionDegreesChange, "Solid infill direction (degrees)", KeyboardType.Decimal)
            ProfileTextField(solidInfillRotateTemplate, onSolidInfillRotateTemplateChange, "Solid infill rotate template")
            ProfileDropdownField(
                label = "Apply gap fill",
                selectedLabel = gapFillTargetOptions.firstOrNull { it.value == gapFillTarget }?.title ?: gapFillTarget,
                options = gapFillTargetOptions,
                onSelected = onGapFillTargetChange
            )
            ProfileTextField(filterOutGapFill, onFilterOutGapFillChange, "Filter out tiny gaps (mm)", KeyboardType.Decimal)
            ProfileTextField(infillWallOverlapPercent, onInfillWallOverlapPercentChange, "Infill / wall overlap (%)", KeyboardType.Number)
            ProfileGroupHeader("Advanced")
            ProfileDropdownField(
                label = "Align infill direction to model",
                selectedLabel = if (alignInfillDirectionToModel) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onAlignInfillDirectionToModelChange
            )
            ProfileTextField(extraSolidInfills, onExtraSolidInfillsChange, "Insert solid layers")
            ProfileTextField(bridgeAngleDegrees, onBridgeAngleDegreesChange, "Bridge angle (degrees)", KeyboardType.Decimal)
            ProfileTextField(internalBridgeAngleDegrees, onInternalBridgeAngleDegreesChange, "Internal bridge angle (degrees)", KeyboardType.Decimal)
            ProfileTextField(minimumSparseInfillAreaMm2, onMinimumSparseInfillAreaMm2Change, "Minimum sparse infill area (mm²)", KeyboardType.Decimal)
            ProfileDropdownField(
                label = "Infill combination",
                selectedLabel = if (infillCombination) "Enabled" else "Disabled",
                options = infillCombinationOptions,
                onSelected = onInfillCombinationChange
            )
            ProfileTextField(infillCombinationMaxLayerHeight, onInfillCombinationMaxLayerHeightChange, "Infill combination max layer height (mm or %)")
            ProfileDropdownField(
                label = "Detect narrow internal solid infill",
                selectedLabel = if (detectNarrowInternalSolidInfill) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onDetectNarrowInternalSolidInfillChange
            )
            ProfileDropdownField(
                label = "Ensure vertical shell thickness",
                selectedLabel = ensureVerticalShellThickness.displayLabel,
                options = ensureVerticalShellThicknessOptions,
                onSelected = onEnsureVerticalShellThicknessChange
            )
        }
    }
}

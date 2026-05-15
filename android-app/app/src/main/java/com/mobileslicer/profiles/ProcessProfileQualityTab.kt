package com.mobileslicer.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import com.mobileslicer.AppSettingOption

@Composable
internal fun ProcessQualityTabContent(
    showAdvancedProfileSettings: Boolean,
    boolEnabledDisabledOptions: List<AppSettingOption<Boolean>>,
    seamPositionOptions: List<AppSettingOption<ProcessSeamPosition>>,
    seamScarfTypeOptions: List<AppSettingOption<SeamScarfType>>,
    ironingTypeOptions: List<AppSettingOption<ProcessIroningType>>,
    ironingPatternOptions: List<AppSettingOption<IroningPattern>>,
    wallGeneratorOptions: List<AppSettingOption<WallGenerator>>,
    wallSequenceOptions: List<AppSettingOption<WallSequence>>,
    wallDirectionOptions: List<AppSettingOption<WallDirection>>,
    extraBridgeLayerOptions: List<AppSettingOption<ExtraBridgeLayerMode>>,
    internalBridgeFilterOptions: List<AppSettingOption<InternalBridgeFilterMode>>,
    counterboreHoleBridgingOptions: List<AppSettingOption<CounterboreHoleBridging>>,
    firstLayerHeight: String,
    onFirstLayerHeightChange: (String) -> Unit,
    layerHeight: String,
    onLayerHeightChange: (String) -> Unit,
    lineWidth: String,
    onLineWidthChange: (String) -> Unit,
    initialLayerLineWidth: String,
    onInitialLayerLineWidthChange: (String) -> Unit,
    outerWallLineWidth: String,
    onOuterWallLineWidthChange: (String) -> Unit,
    innerWallLineWidth: String,
    onInnerWallLineWidthChange: (String) -> Unit,
    topSurfaceLineWidth: String,
    onTopSurfaceLineWidthChange: (String) -> Unit,
    sparseInfillLineWidth: String,
    onSparseInfillLineWidthChange: (String) -> Unit,
    internalSolidInfillLineWidth: String,
    onInternalSolidInfillLineWidthChange: (String) -> Unit,
    supportLineWidth: String,
    onSupportLineWidthChange: (String) -> Unit,
    seamPosition: ProcessSeamPosition,
    onSeamPositionChange: (ProcessSeamPosition) -> Unit,
    staggeredInnerSeams: Boolean,
    onStaggeredInnerSeamsChange: (Boolean) -> Unit,
    seamGap: String,
    onSeamGapChange: (String) -> Unit,
    seamScarfType: SeamScarfType,
    onSeamScarfTypeChange: (SeamScarfType) -> Unit,
    seamScarfConditional: Boolean,
    onSeamScarfConditionalChange: (Boolean) -> Unit,
    scarfAngleThreshold: String,
    onScarfAngleThresholdChange: (String) -> Unit,
    scarfOverhangThreshold: String,
    onScarfOverhangThresholdChange: (String) -> Unit,
    scarfJointSpeed: String,
    onScarfJointSpeedChange: (String) -> Unit,
    scarfJointFlowRatio: String,
    onScarfJointFlowRatioChange: (String) -> Unit,
    seamScarfStartHeight: String,
    onSeamScarfStartHeightChange: (String) -> Unit,
    seamScarfEntireLoop: Boolean,
    onSeamScarfEntireLoopChange: (Boolean) -> Unit,
    seamScarfMinLength: String,
    onSeamScarfMinLengthChange: (String) -> Unit,
    seamScarfSteps: String,
    onSeamScarfStepsChange: (String) -> Unit,
    seamScarfInnerWalls: Boolean,
    onSeamScarfInnerWallsChange: (Boolean) -> Unit,
    roleBasedWipeSpeed: Boolean,
    onRoleBasedWipeSpeedChange: (Boolean) -> Unit,
    wipeSpeed: String,
    onWipeSpeedChange: (String) -> Unit,
    wipeOnLoops: Boolean,
    onWipeOnLoopsChange: (Boolean) -> Unit,
    wipeBeforeExternalLoop: Boolean,
    onWipeBeforeExternalLoopChange: (Boolean) -> Unit,
    hasScarfJointSeam: Boolean,
    onHasScarfJointSeamChange: (Boolean) -> Unit,
    preciseOuterWall: Boolean,
    onPreciseOuterWallChange: (Boolean) -> Unit,
    sliceClosingRadius: String,
    onSliceClosingRadiusChange: (String) -> Unit,
    applyTopSurfaceCompensation: Boolean,
    onApplyTopSurfaceCompensationChange: (Boolean) -> Unit,
    resolution: String,
    onResolutionChange: (String) -> Unit,
    enableArcFitting: Boolean,
    onEnableArcFittingChange: (Boolean) -> Unit,
    xyHoleCompensation: String,
    onXyHoleCompensationChange: (String) -> Unit,
    xyContourCompensation: String,
    onXyContourCompensationChange: (String) -> Unit,
    elefantFootCompensation: String,
    onElefantFootCompensationChange: (String) -> Unit,
    elefantFootCompensationLayers: String,
    onElefantFootCompensationLayersChange: (String) -> Unit,
    preciseZHeight: Boolean,
    onPreciseZHeightChange: (Boolean) -> Unit,
    holeToPolyhole: Boolean,
    onHoleToPolyholeChange: (Boolean) -> Unit,
    holeToPolyholeThreshold: String,
    onHoleToPolyholeThresholdChange: (String) -> Unit,
    holeToPolyholeTwisted: Boolean,
    onHoleToPolyholeTwistedChange: (Boolean) -> Unit,
    ironingType: ProcessIroningType,
    onIroningTypeChange: (ProcessIroningType) -> Unit,
    ironingPattern: IroningPattern,
    onIroningPatternChange: (IroningPattern) -> Unit,
    ironingFlow: String,
    onIroningFlowChange: (String) -> Unit,
    ironingSpacing: String,
    onIroningSpacingChange: (String) -> Unit,
    ironingInset: String,
    onIroningInsetChange: (String) -> Unit,
    ironingAngle: String,
    onIroningAngleChange: (String) -> Unit,
    ironingAngleFixed: Boolean,
    onIroningAngleFixedChange: (Boolean) -> Unit,
    wallGenerator: WallGenerator,
    onWallGeneratorChange: (WallGenerator) -> Unit,
    wallTransitionAngle: String,
    onWallTransitionAngleChange: (String) -> Unit,
    wallTransitionFilterDeviation: String,
    onWallTransitionFilterDeviationChange: (String) -> Unit,
    wallTransitionLength: String,
    onWallTransitionLengthChange: (String) -> Unit,
    wallDistributionCount: String,
    onWallDistributionCountChange: (String) -> Unit,
    initialLayerMinBeadWidthPercent: String,
    onInitialLayerMinBeadWidthPercentChange: (String) -> Unit,
    minBeadWidth: String,
    onMinBeadWidthChange: (String) -> Unit,
    minFeatureSize: String,
    onMinFeatureSizeChange: (String) -> Unit,
    minLengthFactor: String,
    onMinLengthFactorChange: (String) -> Unit,
    wallSequence: WallSequence,
    onWallSequenceChange: (WallSequence) -> Unit,
    printInfillFirst: Boolean,
    onPrintInfillFirstChange: (Boolean) -> Unit,
    wallDirection: WallDirection,
    onWallDirectionChange: (WallDirection) -> Unit,
    printFlowRatio: String,
    onPrintFlowRatioChange: (String) -> Unit,
    setOtherFlowRatios: Boolean,
    onSetOtherFlowRatiosChange: (Boolean) -> Unit,
    onlyOneWallFirstLayer: Boolean,
    onOnlyOneWallFirstLayerChange: (Boolean) -> Unit,
    onlyOneWallTopSurfaces: Boolean,
    onOnlyOneWallTopSurfacesChange: (Boolean) -> Unit,
    minWidthTopSurface: String,
    onMinWidthTopSurfaceChange: (String) -> Unit,
    firstLayerFlowRatio: String,
    onFirstLayerFlowRatioChange: (String) -> Unit,
    outerWallFlowRatio: String,
    onOuterWallFlowRatioChange: (String) -> Unit,
    innerWallFlowRatio: String,
    onInnerWallFlowRatioChange: (String) -> Unit,
    topSolidInfillFlowRatio: String,
    onTopSolidInfillFlowRatioChange: (String) -> Unit,
    bottomSolidInfillFlowRatio: String,
    onBottomSolidInfillFlowRatioChange: (String) -> Unit,
    overhangFlowRatio: String,
    onOverhangFlowRatioChange: (String) -> Unit,
    sparseInfillFlowRatio: String,
    onSparseInfillFlowRatioChange: (String) -> Unit,
    internalSolidInfillFlowRatio: String,
    onInternalSolidInfillFlowRatioChange: (String) -> Unit,
    gapFillFlowRatio: String,
    onGapFillFlowRatioChange: (String) -> Unit,
    supportFlowRatio: String,
    onSupportFlowRatioChange: (String) -> Unit,
    supportInterfaceFlowRatio: String,
    onSupportInterfaceFlowRatioChange: (String) -> Unit,
    reduceCrossingWall: Boolean,
    onReduceCrossingWallChange: (Boolean) -> Unit,
    maxTravelDetourDistance: String,
    onMaxTravelDetourDistanceChange: (String) -> Unit,
    smallAreaInfillFlowCompensation: Boolean,
    onSmallAreaInfillFlowCompensationChange: (Boolean) -> Unit,
    bridgeFlowRatio: String,
    onBridgeFlowRatioChange: (String) -> Unit,
    internalBridgeFlowRatio: String,
    onInternalBridgeFlowRatioChange: (String) -> Unit,
    bridgeDensityPercent: String,
    onBridgeDensityPercentChange: (String) -> Unit,
    internalBridgeDensityPercent: String,
    onInternalBridgeDensityPercentChange: (String) -> Unit,
    thickBridges: Boolean,
    onThickBridgesChange: (Boolean) -> Unit,
    thickInternalBridges: Boolean,
    onThickInternalBridgesChange: (Boolean) -> Unit,
    extraBridgeLayer: ExtraBridgeLayerMode,
    onExtraBridgeLayerChange: (ExtraBridgeLayerMode) -> Unit,
    dontFilterInternalBridges: InternalBridgeFilterMode,
    onDontFilterInternalBridgesChange: (InternalBridgeFilterMode) -> Unit,
    counterboreHoleBridging: CounterboreHoleBridging,
    onCounterboreHoleBridgingChange: (CounterboreHoleBridging) -> Unit,
    detectOverhangWall: Boolean,
    onDetectOverhangWallChange: (Boolean) -> Unit,
    makeOverhangPrintable: Boolean,
    onMakeOverhangPrintableChange: (Boolean) -> Unit,
    makeOverhangPrintableAngle: String,
    onMakeOverhangPrintableAngleChange: (String) -> Unit,
    makeOverhangPrintableHoleSize: String,
    onMakeOverhangPrintableHoleSizeChange: (String) -> Unit,
    extraPerimetersOnOverhangs: Boolean,
    onExtraPerimetersOnOverhangsChange: (Boolean) -> Unit,
    overhangReverse: Boolean,
    onOverhangReverseChange: (Boolean) -> Unit,
    overhangReverseInternalOnly: Boolean,
    onOverhangReverseInternalOnlyChange: (Boolean) -> Unit,
    overhangReverseThreshold: String,
    onOverhangReverseThresholdChange: (String) -> Unit,
) {
    ProfileEditorSection("Quality", "Layer height, line width, seams, walls, bridging, and overhang settings.") {
        ProfileGroupHeader("Layer height")
        ProfileTextField(layerHeight, onLayerHeightChange, "Layer height (mm)", KeyboardType.Decimal)
        ProfileTextField(firstLayerHeight, onFirstLayerHeightChange, "Initial layer height (mm)", KeyboardType.Decimal)
        if (ProfileEditorSetting.ProcessQualityAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileGroupHeader("Line width")
            ProfileTextField(lineWidth, onLineWidthChange, "Default (mm or %)")
            ProfileTextField(initialLayerLineWidth, onInitialLayerLineWidthChange, "First layer (mm or %)")
            ProfileTextField(outerWallLineWidth, onOuterWallLineWidthChange, "Outer wall (mm or %)")
            ProfileTextField(innerWallLineWidth, onInnerWallLineWidthChange, "Inner wall (mm or %)")
            ProfileTextField(topSurfaceLineWidth, onTopSurfaceLineWidthChange, "Top surface (mm or %)")
            ProfileTextField(sparseInfillLineWidth, onSparseInfillLineWidthChange, "Sparse infill (mm or %)")
            ProfileTextField(internalSolidInfillLineWidth, onInternalSolidInfillLineWidthChange, "Internal solid infill (mm or %)")
            ProfileTextField(supportLineWidth, onSupportLineWidthChange, "Support (mm or %)", KeyboardType.Text)
        }
        ProfileGroupHeader("Seam")
        ProfileDropdownField(
            label = "Seam position",
            selectedLabel = seamPosition.displayLabel,
            options = seamPositionOptions,
            onSelected = onSeamPositionChange
        )
        if (ProfileEditorSetting.ProcessSeamAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileDropdownField(
                label = "Staggered inner seams",
                selectedLabel = if (staggeredInnerSeams) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onStaggeredInnerSeamsChange
            )
            ProfileTextField(seamGap, onSeamGapChange, "Seam gap (mm or %)")
            ProfileDropdownField(
                label = "Scarf joint seam",
                selectedLabel = seamScarfType.displayLabel,
                options = seamScarfTypeOptions,
                onSelected = onSeamScarfTypeChange
            )
            ProfileDropdownField(
                label = "Conditional scarf joint",
                selectedLabel = if (seamScarfConditional) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onSeamScarfConditionalChange
            )
            ProfileTextField(scarfAngleThreshold, onScarfAngleThresholdChange, "Conditional angle threshold (degrees)", KeyboardType.Number)
            ProfileTextField(scarfOverhangThreshold, onScarfOverhangThresholdChange, "Conditional overhang threshold (%)", KeyboardType.Number)
            ProfileTextField(scarfJointSpeed, onScarfJointSpeedChange, "Scarf joint speed (mm/s or %)")
            ProfileTextField(seamScarfStartHeight, onSeamScarfStartHeightChange, "Scarf start height (mm or %)")
            ProfileDropdownField(
                label = "Scarf around entire wall",
                selectedLabel = if (seamScarfEntireLoop) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onSeamScarfEntireLoopChange
            )
            ProfileTextField(seamScarfMinLength, onSeamScarfMinLengthChange, "Scarf length (mm)", KeyboardType.Decimal)
            ProfileTextField(seamScarfSteps, onSeamScarfStepsChange, "Scarf steps", KeyboardType.Number)
            ProfileTextField(scarfJointFlowRatio, onScarfJointFlowRatioChange, "Scarf joint flow ratio", KeyboardType.Decimal)
            ProfileDropdownField(
                label = "Scarf joint seam",
                selectedLabel = if (hasScarfJointSeam) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onHasScarfJointSeamChange
            )
            ProfileDropdownField(
                label = "Scarf joint for inner walls",
                selectedLabel = if (seamScarfInnerWalls) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onSeamScarfInnerWallsChange
            )
            ProfileDropdownField(
                label = "Role based wipe speed",
                selectedLabel = if (roleBasedWipeSpeed) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onRoleBasedWipeSpeedChange
            )
            ProfileTextField(wipeSpeed, onWipeSpeedChange, "Wipe speed (mm/s or %)")
            ProfileDropdownField(
                label = "Wipe on loops",
                selectedLabel = if (wipeOnLoops) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onWipeOnLoopsChange
            )
            ProfileDropdownField(
                label = "Wipe before external loop",
                selectedLabel = if (wipeBeforeExternalLoop) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onWipeBeforeExternalLoopChange
            )
        }
        ProfileGroupHeader("Precision")
        if (ProfileEditorSetting.ProcessQualityAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileTextField(sliceClosingRadius, onSliceClosingRadiusChange, "Slice gap closing radius (mm)", KeyboardType.Decimal)
            ProfileTextField(resolution, onResolutionChange, "Resolution (mm)", KeyboardType.Decimal)
            ProfileDropdownField(
                label = "Arc fitting",
                selectedLabel = if (enableArcFitting) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onEnableArcFittingChange
            )
            ProfileTextField(xyHoleCompensation, onXyHoleCompensationChange, "X-Y hole compensation (mm)", KeyboardType.Decimal)
            ProfileTextField(xyContourCompensation, onXyContourCompensationChange, "X-Y contour compensation (mm)", KeyboardType.Decimal)
            ProfileTextField(
                elefantFootCompensation,
                onElefantFootCompensationChange,
                "Elefant foot compensation (mm)",
                KeyboardType.Decimal
            )
            ProfileTextField(elefantFootCompensationLayers, onElefantFootCompensationLayersChange, "Elefant foot compensation layers", KeyboardType.Number)
            ProfileDropdownField(
                label = "Precise wall",
                selectedLabel = if (preciseOuterWall) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onPreciseOuterWallChange
            )
            ProfileDropdownField(
                label = "Precise Z height",
                selectedLabel = if (preciseZHeight) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onPreciseZHeightChange
            )
            ProfileDropdownField(
                label = "Top surface compensation",
                selectedLabel = if (applyTopSurfaceCompensation) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onApplyTopSurfaceCompensationChange
            )
            ProfileDropdownField(
                label = "Convert holes to polyholes",
                selectedLabel = if (holeToPolyhole) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onHoleToPolyholeChange
            )
            ProfileTextField(holeToPolyholeThreshold, onHoleToPolyholeThresholdChange, "Polyhole detection margin (mm or %)")
            ProfileDropdownField(
                label = "Polyhole twist",
                selectedLabel = if (holeToPolyholeTwisted) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onHoleToPolyholeTwistedChange
            )
        } else {
            ProfileDropdownField(
                label = "Precise wall",
                selectedLabel = if (preciseOuterWall) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onPreciseOuterWallChange
            )
        }
        if (ProfileEditorSetting.ProcessIroningAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileGroupHeader("Ironing")
            ProfileDropdownField(
                label = "Ironing type",
                selectedLabel = ironingType.displayLabel,
                options = ironingTypeOptions,
                onSelected = onIroningTypeChange
            )
            ProfileDropdownField(
                label = "Ironing pattern",
                selectedLabel = ironingPattern.displayLabel,
                options = ironingPatternOptions,
                onSelected = onIroningPatternChange
            )
            ProfileTextField(ironingFlow, onIroningFlowChange, "Ironing flow (%)", KeyboardType.Decimal)
            ProfileTextField(ironingSpacing, onIroningSpacingChange, "Ironing line spacing (mm)", KeyboardType.Decimal)
            ProfileTextField(ironingInset, onIroningInsetChange, "Ironing inset (mm)", KeyboardType.Decimal)
            ProfileTextField(ironingAngle, onIroningAngleChange, "Ironing angle offset (degrees)", KeyboardType.Decimal)
            ProfileDropdownField(
                label = "Fixed ironing angle",
                selectedLabel = if (ironingAngleFixed) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onIroningAngleFixedChange
            )
        }
        if (ProfileEditorSetting.ProcessQualityAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileGroupHeader("Wall generator")
            ProfileDropdownField(
                label = "Wall generator",
                selectedLabel = wallGenerator.displayLabel,
                options = wallGeneratorOptions,
                onSelected = onWallGeneratorChange
            )
            ProfileTextField(wallTransitionLength, onWallTransitionLengthChange, "Wall transition length (%)", KeyboardType.Number)
            ProfileTextField(wallTransitionFilterDeviation, onWallTransitionFilterDeviationChange, "Wall transitioning filter margin (%)", KeyboardType.Number)
            ProfileTextField(wallTransitionAngle, onWallTransitionAngleChange, "Wall transitioning threshold angle (degrees)", KeyboardType.Decimal)
            ProfileTextField(wallDistributionCount, onWallDistributionCountChange, "Wall distribution count", KeyboardType.Number)
            ProfileTextField(
                initialLayerMinBeadWidthPercent,
                onInitialLayerMinBeadWidthPercentChange,
                "First layer minimum wall width (%)",
                KeyboardType.Number
            )
            ProfileTextField(minBeadWidth, onMinBeadWidthChange, "Minimum wall width (%)", KeyboardType.Number)
            ProfileTextField(minFeatureSize, onMinFeatureSizeChange, "Minimum feature size (%)", KeyboardType.Number)
            ProfileTextField(minLengthFactor, onMinLengthFactorChange, "Minimum wall length (mm)", KeyboardType.Decimal)
        }
        ProfileGroupHeader("Walls and surfaces")
        if (ProfileEditorSetting.ProcessQualityAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileDropdownField(
                label = "Walls printing order",
                selectedLabel = wallSequence.displayLabel,
                options = wallSequenceOptions,
                onSelected = onWallSequenceChange
            )
            ProfileDropdownField(
                label = "Print infill first",
                selectedLabel = if (printInfillFirst) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onPrintInfillFirstChange
            )
            ProfileDropdownField(
                label = "Wall loop direction",
                selectedLabel = wallDirection.displayLabel,
                options = wallDirectionOptions,
                onSelected = onWallDirectionChange
            )
            ProfileTextField(printFlowRatio, onPrintFlowRatioChange, "Flow ratio", KeyboardType.Decimal)
            ProfileTextField(topSolidInfillFlowRatio, onTopSolidInfillFlowRatioChange, "Top surface flow ratio", KeyboardType.Decimal)
            ProfileTextField(bottomSolidInfillFlowRatio, onBottomSolidInfillFlowRatioChange, "Bottom surface flow ratio", KeyboardType.Decimal)
            ProfileDropdownField(
                label = "Set other flow ratios",
                selectedLabel = if (setOtherFlowRatios) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onSetOtherFlowRatiosChange
            )
            ProfileTextField(firstLayerFlowRatio, onFirstLayerFlowRatioChange, "First layer flow ratio", KeyboardType.Decimal)
            ProfileTextField(outerWallFlowRatio, onOuterWallFlowRatioChange, "Outer wall flow ratio", KeyboardType.Decimal)
            ProfileTextField(innerWallFlowRatio, onInnerWallFlowRatioChange, "Inner wall flow ratio", KeyboardType.Decimal)
            ProfileTextField(overhangFlowRatio, onOverhangFlowRatioChange, "Overhang flow ratio", KeyboardType.Decimal)
            ProfileTextField(sparseInfillFlowRatio, onSparseInfillFlowRatioChange, "Sparse infill flow ratio", KeyboardType.Decimal)
            ProfileTextField(internalSolidInfillFlowRatio, onInternalSolidInfillFlowRatioChange, "Internal solid infill flow ratio", KeyboardType.Decimal)
            ProfileTextField(gapFillFlowRatio, onGapFillFlowRatioChange, "Gap fill flow ratio", KeyboardType.Decimal)
            ProfileTextField(supportFlowRatio, onSupportFlowRatioChange, "Support flow ratio", KeyboardType.Decimal)
            ProfileTextField(supportInterfaceFlowRatio, onSupportInterfaceFlowRatioChange, "Support interface flow ratio", KeyboardType.Decimal)
        }
        ProfileDropdownField(
            label = "Only one wall on first layer",
            selectedLabel = if (onlyOneWallFirstLayer) "Enabled" else "Disabled",
            options = boolEnabledDisabledOptions,
            onSelected = onOnlyOneWallFirstLayerChange
        )
        ProfileDropdownField(
            label = "Only one wall on top surfaces",
            selectedLabel = if (onlyOneWallTopSurfaces) "Enabled" else "Disabled",
            options = boolEnabledDisabledOptions,
            onSelected = onOnlyOneWallTopSurfacesChange
        )
        if (ProfileEditorSetting.ProcessQualityAdvanced.isVisible(showAdvancedProfileSettings)) {
            ProfileTextField(minWidthTopSurface, onMinWidthTopSurfaceChange, "One wall threshold (mm or %)")
            ProfileDropdownField(
                label = "Avoid crossing walls",
                selectedLabel = if (reduceCrossingWall) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onReduceCrossingWallChange
            )
            ProfileTextField(maxTravelDetourDistance, onMaxTravelDetourDistanceChange, "Avoid crossing walls max detour (mm or %)")
            ProfileDropdownField(
                label = "Small area flow compensation",
                selectedLabel = if (smallAreaInfillFlowCompensation) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onSmallAreaInfillFlowCompensationChange
            )
            ProfileGroupHeader("Bridging")
            ProfileTextField(bridgeFlowRatio, onBridgeFlowRatioChange, "Bridge flow ratio", KeyboardType.Decimal)
            ProfileTextField(internalBridgeFlowRatio, onInternalBridgeFlowRatioChange, "Internal bridge flow ratio", KeyboardType.Decimal)
            ProfileTextField(bridgeDensityPercent, onBridgeDensityPercentChange, "Bridge density (%)", KeyboardType.Number)
            ProfileTextField(internalBridgeDensityPercent, onInternalBridgeDensityPercentChange, "Internal bridge density (%)", KeyboardType.Number)
            ProfileDropdownField(
                label = "Thick bridges",
                selectedLabel = if (thickBridges) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onThickBridgesChange
            )
            ProfileDropdownField(
                label = "Thick internal bridges",
                selectedLabel = if (thickInternalBridges) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onThickInternalBridgesChange
            )
            ProfileDropdownField(
                label = "Extra bridge layers",
                selectedLabel = extraBridgeLayer.displayLabel,
                options = extraBridgeLayerOptions,
                onSelected = onExtraBridgeLayerChange
            )
            ProfileDropdownField(
                label = "Internal bridge filtering",
                selectedLabel = dontFilterInternalBridges.displayLabel,
                options = internalBridgeFilterOptions,
                onSelected = onDontFilterInternalBridgesChange
            )
            ProfileDropdownField(
                label = "Bridge counterbore holes",
                selectedLabel = counterboreHoleBridging.displayLabel,
                options = counterboreHoleBridgingOptions,
                onSelected = onCounterboreHoleBridgingChange
            )
            ProfileGroupHeader("Overhangs")
            ProfileDropdownField(
                label = "Detect overhang walls",
                selectedLabel = if (detectOverhangWall) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onDetectOverhangWallChange
            )
            ProfileDropdownField(
                label = "Make overhangs printable",
                selectedLabel = if (makeOverhangPrintable) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onMakeOverhangPrintableChange
            )
            ProfileTextField(makeOverhangPrintableAngle, onMakeOverhangPrintableAngleChange, "Maximum angle (degrees)", KeyboardType.Decimal)
            ProfileTextField(makeOverhangPrintableHoleSize, onMakeOverhangPrintableHoleSizeChange, "Hole area (mm2)", KeyboardType.Decimal)
            ProfileDropdownField(
                label = "Extra perimeters on overhangs",
                selectedLabel = if (extraPerimetersOnOverhangs) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onExtraPerimetersOnOverhangsChange
            )
            ProfileDropdownField(
                label = "Reverse on even",
                selectedLabel = if (overhangReverse) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onOverhangReverseChange
            )
            ProfileDropdownField(
                label = "Reverse only internal perimeters",
                selectedLabel = if (overhangReverseInternalOnly) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onOverhangReverseInternalOnlyChange
            )
            ProfileTextField(overhangReverseThreshold, onOverhangReverseThresholdChange, "Reverse threshold (mm or %)")
        }
    }
}

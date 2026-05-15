package com.mobileslicer.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import com.mobileslicer.AppSettingOption

@Composable
internal fun ProcessSpeedTabContent(
    showAdvancedProfileSettings: Boolean,
    boolEnabledDisabledOptions: List<AppSettingOption<Boolean>>,
    firstLayerPrintSpeed: String,
    onFirstLayerPrintSpeedChange: (String) -> Unit,
    firstLayerInfillSpeed: String,
    onFirstLayerInfillSpeedChange: (String) -> Unit,
    firstLayerTravelSpeedPercent: String,
    onFirstLayerTravelSpeedPercentChange: (String) -> Unit,
    slowDownLayers: String,
    onSlowDownLayersChange: (String) -> Unit,
    outerWallSpeed: String,
    onOuterWallSpeedChange: (String) -> Unit,
    innerWallSpeed: String,
    onInnerWallSpeedChange: (String) -> Unit,
    smallPerimeterSpeed: String,
    onSmallPerimeterSpeedChange: (String) -> Unit,
    smallPerimeterThreshold: String,
    onSmallPerimeterThresholdChange: (String) -> Unit,
    sparseInfillSpeed: String,
    onSparseInfillSpeedChange: (String) -> Unit,
    internalSolidInfillSpeed: String,
    onInternalSolidInfillSpeedChange: (String) -> Unit,
    topSurfaceSpeed: String,
    onTopSurfaceSpeedChange: (String) -> Unit,
    ironingSpeed: String,
    onIroningSpeedChange: (String) -> Unit,
    gapInfillSpeed: String,
    onGapInfillSpeedChange: (String) -> Unit,
    supportSpeed: String,
    onSupportSpeedChange: (String) -> Unit,
    supportInterfaceSpeed: String,
    onSupportInterfaceSpeedChange: (String) -> Unit,
    overhang1_4Speed: String,
    onOverhang1_4SpeedChange: (String) -> Unit,
    overhang2_4Speed: String,
    onOverhang2_4SpeedChange: (String) -> Unit,
    overhang3_4Speed: String,
    onOverhang3_4SpeedChange: (String) -> Unit,
    overhang4_4Speed: String,
    onOverhang4_4SpeedChange: (String) -> Unit,
    bridgeSpeed: String,
    onBridgeSpeedChange: (String) -> Unit,
    internalBridgeSpeed: String,
    onInternalBridgeSpeedChange: (String) -> Unit,
    travelSpeed: String,
    onTravelSpeedChange: (String) -> Unit,
    defaultAcceleration: String,
    onDefaultAccelerationChange: (String) -> Unit,
    outerWallAcceleration: String,
    onOuterWallAccelerationChange: (String) -> Unit,
    innerWallAcceleration: String,
    onInnerWallAccelerationChange: (String) -> Unit,
    bridgeAcceleration: String,
    onBridgeAccelerationChange: (String) -> Unit,
    sparseInfillAcceleration: String,
    onSparseInfillAccelerationChange: (String) -> Unit,
    internalSolidInfillAcceleration: String,
    onInternalSolidInfillAccelerationChange: (String) -> Unit,
    initialLayerAcceleration: String,
    onInitialLayerAccelerationChange: (String) -> Unit,
    topSurfaceAcceleration: String,
    onTopSurfaceAccelerationChange: (String) -> Unit,
    travelAcceleration: String,
    onTravelAccelerationChange: (String) -> Unit,
    accelToDecelEnable: Boolean,
    onAccelToDecelEnableChange: (Boolean) -> Unit,
    accelToDecelFactor: String,
    onAccelToDecelFactorChange: (String) -> Unit,
    defaultJunctionDeviation: String,
    onDefaultJunctionDeviationChange: (String) -> Unit,
    defaultJerk: String,
    onDefaultJerkChange: (String) -> Unit,
    outerWallJerk: String,
    onOuterWallJerkChange: (String) -> Unit,
    innerWallJerk: String,
    onInnerWallJerkChange: (String) -> Unit,
    infillJerk: String,
    onInfillJerkChange: (String) -> Unit,
    topSurfaceJerk: String,
    onTopSurfaceJerkChange: (String) -> Unit,
    initialLayerJerk: String,
    onInitialLayerJerkChange: (String) -> Unit,
    travelJerk: String,
    onTravelJerkChange: (String) -> Unit,
    enableOverhangSpeed: Boolean,
    onEnableOverhangSpeedChange: (Boolean) -> Unit,
    slowdownForCurledPerimeters: Boolean,
    onSlowdownForCurledPerimetersChange: (Boolean) -> Unit,
    internalBridgeFlowRatio: String,
    onInternalBridgeFlowRatioChange: (String) -> Unit,
    internalBridgeSupportThickness: String,
    onInternalBridgeSupportThicknessChange: (String) -> Unit,
    maxVolumetricExtrusionRateSlope: String,
    onMaxVolumetricExtrusionRateSlopeChange: (String) -> Unit,
    maxVolumetricExtrusionRateSlopeSegmentLength: String,
    onMaxVolumetricExtrusionRateSlopeSegmentLengthChange: (String) -> Unit,
    extrusionRateSmoothingExternalPerimeterOnly: Boolean,
    onExtrusionRateSmoothingExternalPerimeterOnlyChange: (Boolean) -> Unit,
) {
    ProfileEditorSection("Speed", "Print, travel, acceleration, jerk, and overhang speed settings.") {
        if (ProfileEditorSetting.ProcessSpeedAndAcceleration.isVisible(showAdvancedProfileSettings)) {
            ProfileGroupHeader("First layer speed")
            ProfileTextField(firstLayerPrintSpeed, onFirstLayerPrintSpeedChange, "First layer (mm/s)", KeyboardType.Decimal)
            ProfileTextField(firstLayerInfillSpeed, onFirstLayerInfillSpeedChange, "First layer infill (mm/s)", KeyboardType.Decimal)
            ProfileTextField(firstLayerTravelSpeedPercent, onFirstLayerTravelSpeedPercentChange, "First layer travel speed (%)", KeyboardType.Number)
            ProfileTextField(slowDownLayers, onSlowDownLayersChange, "Number of slow layers", KeyboardType.Number)
            ProfileGroupHeader("Other layers speed")
            ProfileTextField(outerWallSpeed, onOuterWallSpeedChange, "Outer wall (mm/s)", KeyboardType.Decimal)
            ProfileTextField(innerWallSpeed, onInnerWallSpeedChange, "Inner wall (mm/s)", KeyboardType.Decimal)
            ProfileTextField(smallPerimeterSpeed, onSmallPerimeterSpeedChange, "Small perimeters (mm/s)", KeyboardType.Decimal)
            ProfileTextField(smallPerimeterThreshold, onSmallPerimeterThresholdChange, "Small perimeter threshold (mm)", KeyboardType.Decimal)
            ProfileTextField(sparseInfillSpeed, onSparseInfillSpeedChange, "Sparse infill (mm/s)", KeyboardType.Decimal)
            ProfileTextField(internalSolidInfillSpeed, onInternalSolidInfillSpeedChange, "Internal solid infill (mm/s)", KeyboardType.Decimal)
            ProfileTextField(topSurfaceSpeed, onTopSurfaceSpeedChange, "Top surface (mm/s)", KeyboardType.Decimal)
            ProfileTextField(gapInfillSpeed, onGapInfillSpeedChange, "Gap infill (mm/s)", KeyboardType.Decimal)
            ProfileTextField(ironingSpeed, onIroningSpeedChange, "Ironing (mm/s)", KeyboardType.Decimal)
            ProfileTextField(supportSpeed, onSupportSpeedChange, "Support speed (mm/s)", KeyboardType.Decimal)
            ProfileTextField(supportInterfaceSpeed, onSupportInterfaceSpeedChange, "Support interface speed (mm/s)", KeyboardType.Decimal)
            ProfileGroupHeader("Overhang speed")
            ProfileDropdownField(
                label = "Slow down for overhang",
                selectedLabel = if (enableOverhangSpeed) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onEnableOverhangSpeedChange
            )
            ProfileDropdownField(
                label = "Slow down for curled perimeters",
                selectedLabel = if (slowdownForCurledPerimeters) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onSlowdownForCurledPerimetersChange
            )
            ProfileTextField(overhang1_4Speed, onOverhang1_4SpeedChange, "Overhang 10% speed (mm/s or %)")
            ProfileTextField(overhang2_4Speed, onOverhang2_4SpeedChange, "Overhang 25% speed (mm/s or %)")
            ProfileTextField(overhang3_4Speed, onOverhang3_4SpeedChange, "Overhang 50% speed (mm/s or %)")
            ProfileTextField(overhang4_4Speed, onOverhang4_4SpeedChange, "Overhang 75% speed (mm/s or %)")
            ProfileTextField(bridgeSpeed, onBridgeSpeedChange, "Bridge (mm/s)", KeyboardType.Decimal)
            ProfileTextField(internalBridgeSpeed, onInternalBridgeSpeedChange, "Internal bridge speed (mm/s or %)")
            ProfileTextField(internalBridgeFlowRatio, onInternalBridgeFlowRatioChange, "Internal bridge flow ratio", KeyboardType.Decimal)
            ProfileTextField(internalBridgeSupportThickness, onInternalBridgeSupportThicknessChange, "Internal bridge support thickness (mm or %)")
            ProfileGroupHeader("Travel speed")
            ProfileTextField(travelSpeed, onTravelSpeedChange, "Travel (mm/s)", KeyboardType.Decimal)
            ProfileGroupHeader("Acceleration")
            ProfileTextField(defaultAcceleration, onDefaultAccelerationChange, "Default accel (mm/s²)", KeyboardType.Decimal)
            ProfileTextField(outerWallAcceleration, onOuterWallAccelerationChange, "Outer wall accel (mm/s²)", KeyboardType.Decimal)
            ProfileTextField(innerWallAcceleration, onInnerWallAccelerationChange, "Inner wall accel (mm/s²)", KeyboardType.Decimal)
            ProfileTextField(bridgeAcceleration, onBridgeAccelerationChange, "Bridge accel (mm/s² or %)")
            ProfileTextField(sparseInfillAcceleration, onSparseInfillAccelerationChange, "Sparse infill accel (mm/s²)", KeyboardType.Decimal)
            ProfileTextField(internalSolidInfillAcceleration, onInternalSolidInfillAccelerationChange, "Internal solid infill accel (mm/s² or %)")
            ProfileTextField(initialLayerAcceleration, onInitialLayerAccelerationChange, "First layer accel (mm/s²)", KeyboardType.Decimal)
            ProfileTextField(topSurfaceAcceleration, onTopSurfaceAccelerationChange, "Top surface accel (mm/s²)", KeyboardType.Decimal)
            ProfileTextField(travelAcceleration, onTravelAccelerationChange, "Travel accel (mm/s²)", KeyboardType.Decimal)
            ProfileDropdownField(
                label = "Enable accel_to_decel",
                selectedLabel = if (accelToDecelEnable) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onAccelToDecelEnableChange
            )
            ProfileTextField(accelToDecelFactor, onAccelToDecelFactorChange, "accel_to_decel (%)", KeyboardType.Number)
            ProfileGroupHeader("Jerk(XY)")
            ProfileTextField(defaultJunctionDeviation, onDefaultJunctionDeviationChange, "Junction deviation (mm)", KeyboardType.Decimal)
            ProfileTextField(defaultJerk, onDefaultJerkChange, "Default jerk (mm/s)", KeyboardType.Decimal)
            ProfileTextField(outerWallJerk, onOuterWallJerkChange, "Outer wall jerk (mm/s)", KeyboardType.Decimal)
            ProfileTextField(innerWallJerk, onInnerWallJerkChange, "Inner wall jerk (mm/s)", KeyboardType.Decimal)
            ProfileTextField(infillJerk, onInfillJerkChange, "Infill jerk (mm/s)", KeyboardType.Decimal)
            ProfileTextField(topSurfaceJerk, onTopSurfaceJerkChange, "Top surface jerk (mm/s)", KeyboardType.Decimal)
            ProfileTextField(initialLayerJerk, onInitialLayerJerkChange, "First layer jerk (mm/s)", KeyboardType.Decimal)
            ProfileTextField(travelJerk, onTravelJerkChange, "Travel jerk (mm/s)", KeyboardType.Decimal)
            ProfileGroupHeader("Advanced")
            ProfileTextField(maxVolumetricExtrusionRateSlope, onMaxVolumetricExtrusionRateSlopeChange, "Extrusion rate smoothing (mm³/s²)", KeyboardType.Decimal)
            ProfileTextField(maxVolumetricExtrusionRateSlopeSegmentLength, onMaxVolumetricExtrusionRateSlopeSegmentLengthChange, "Smoothing segment length (mm)", KeyboardType.Decimal)
            ProfileDropdownField(
                label = "Apply only on external features",
                selectedLabel = if (extrusionRateSmoothingExternalPerimeterOnly) "Enabled" else "Disabled",
                options = boolEnabledDisabledOptions,
                onSelected = onExtrusionRateSmoothingExternalPerimeterOnlyChange
            )
        }
    }
}

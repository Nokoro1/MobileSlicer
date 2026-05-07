package com.mobileslicer.profiles

internal data class ProcessPrimeTowerDetails(
    val wipeTowerXmm: Float = 15f,
    val wipeTowerYmm: Float = 220f,
    val primeTowerSkipPoints: Boolean = true,
    val primeVolumeMm3: Float = 45f,
    val primeTowerBrimWidthMm: Float = 3f,
    val primeTowerInfillGapPercent: Int = 150,
    val wipeTowerRotationAngleDegrees: Float = 0f,
    val wipeTowerBridgingMm: Float = 10f,
    val wipeTowerExtraSpacingPercent: Int = 100,
    val wipeTowerExtraFlowPercent: Int = 100,
    val wipeTowerMaxPurgeSpeedMmPerSec: Float = 90f,
    val wipeTowerWallType: WipeTowerWallType = WipeTowerWallType.Rectangle,
    val wipeTowerConeAngleDegrees: Float = 30f,
    val wipeTowerExtraRibLengthMm: Float = 0f,
    val wipeTowerRibWidthMm: Float = 8f,
    val wipeTowerFilletWall: Boolean = true
)

internal data class ProcessStrengthInfillDetails(
    val skinInfillDensity: Int = 25,
    val skeletonInfillDensity: Int = 25,
    val infillLockDepthMm: Float = 1f,
    val skinInfillDepthMm: Float = 2f,
    val skinInfillLineWidth: String = "100%",
    val skeletonInfillLineWidth: String = "100%",
    val symmetricInfillYAxis: Boolean = false,
    val infillShiftStepMm: Float = 0.4f,
    val infillOverhangAngleDegrees: Float = 60f,
    val gapFillTarget: String = "nowhere",
    val filterOutGapFillMm: Float = 0.5f
)

internal data class ProcessQualitySurfaceDetails(
    val thickInternalBridges: Boolean = true,
    val extraBridgeLayer: ExtraBridgeLayerMode = ExtraBridgeLayerMode.Disabled,
    val preciseZHeight: Boolean = false,
    val onlyOneWallFirstLayer: Boolean = false,
    val printInfillFirst: Boolean = false,
    val wallDirection: WallDirection = WallDirection.Auto,
    val printFlowRatio: Float = 1f,
    val elefantFootCompensationLayers: Int = 1,
    val internalSolidInfillFlowRatio: Float = 1f,
    val setOtherFlowRatios: Boolean = false,
    val smallAreaInfillFlowCompensation: Boolean = false,
    val makeOverhangPrintable: Boolean = false,
    val makeOverhangPrintableAngleDegrees: Float = 55f,
    val makeOverhangPrintableHoleSizeMm2: Float = 0f,
    val overhangReverse: Boolean = false,
    val overhangReverseInternalOnly: Boolean = false,
    val overhangReverseThreshold: String = "50%"
)

internal data class ProcessSpecialModeDetails(
    val spiralModeSmooth: Boolean = false,
    val spiralModeMaxXySmoothing: String = "200%",
    val spiralStartingFlowRatio: Float = 0f,
    val spiralFinishingFlowRatio: Float = 0f,
    val timelapseType: TimelapseType = TimelapseType.Traditional,
    val enableWrappingDetection: Boolean = false
)

internal data class ProcessGcodeOutputDetails(
    val gcodeAddLineNumber: Boolean = false,
    val gcodeComments: Boolean = false,
    val gcodeLabelObjects: Boolean = false,
    val excludeObject: Boolean = false
)

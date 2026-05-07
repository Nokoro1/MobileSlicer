package com.mobileslicer.profiles

internal data class PrimeTowerDetailsDraft(
    val wipeTowerX: String,
    val wipeTowerY: String,
    val primeTowerSkipPoints: Boolean,
    val primeVolume: String,
    val primeTowerBrimWidth: String,
    val primeTowerInfillGap: String,
    val wipeTowerRotationAngle: String,
    val wipeTowerBridging: String,
    val wipeTowerExtraSpacing: String,
    val wipeTowerExtraFlow: String,
    val wipeTowerMaxPurgeSpeed: String,
    val wipeTowerWallType: WipeTowerWallType,
    val wipeTowerConeAngle: String,
    val wipeTowerExtraRibLength: String,
    val wipeTowerRibWidth: String,
    val wipeTowerFilletWall: Boolean
) {
    companion object {
        fun fromProfile(profile: ProcessProfile): PrimeTowerDetailsDraft = PrimeTowerDetailsDraft(
            wipeTowerX = profile.wipeTowerXmm.toString(),
            wipeTowerY = profile.wipeTowerYmm.toString(),
            primeTowerSkipPoints = profile.primeTowerSkipPoints,
            primeVolume = profile.primeVolumeMm3.toString(),
            primeTowerBrimWidth = profile.primeTowerBrimWidthMm.toString(),
            primeTowerInfillGap = profile.primeTowerInfillGapPercent.toString(),
            wipeTowerRotationAngle = profile.wipeTowerRotationAngleDegrees.toString(),
            wipeTowerBridging = profile.wipeTowerBridgingMm.toString(),
            wipeTowerExtraSpacing = profile.wipeTowerExtraSpacingPercent.toString(),
            wipeTowerExtraFlow = profile.wipeTowerExtraFlowPercent.toString(),
            wipeTowerMaxPurgeSpeed = profile.wipeTowerMaxPurgeSpeedMmPerSec.toString(),
            wipeTowerWallType = profile.wipeTowerWallType,
            wipeTowerConeAngle = profile.wipeTowerConeAngleDegrees.toString(),
            wipeTowerExtraRibLength = profile.wipeTowerExtraRibLengthMm.toString(),
            wipeTowerRibWidth = profile.wipeTowerRibWidthMm.toString(),
            wipeTowerFilletWall = profile.wipeTowerFilletWall
        )
    }

    fun toDetails(initial: ProcessProfile): ProcessPrimeTowerDetails = ProcessPrimeTowerDetails(
        wipeTowerXmm = wipeTowerX.parseFloatAtLeastForProcessDraft(0f) ?: initial.wipeTowerXmm,
        wipeTowerYmm = wipeTowerY.parseFloatAtLeastForProcessDraft(0f) ?: initial.wipeTowerYmm,
        primeTowerSkipPoints = primeTowerSkipPoints,
        primeVolumeMm3 = primeVolume.parseFloatAtLeastForProcessDraft(1f) ?: initial.primeVolumeMm3,
        primeTowerBrimWidthMm = primeTowerBrimWidth.parseFloatAtLeastForProcessDraft(-1f) ?: initial.primeTowerBrimWidthMm,
        primeTowerInfillGapPercent = primeTowerInfillGap.parseIntAtLeastForProcessDraft(100) ?: initial.primeTowerInfillGapPercent,
        wipeTowerRotationAngleDegrees = wipeTowerRotationAngle.parseFloatInForProcessDraft(0f, 360f) ?: initial.wipeTowerRotationAngleDegrees,
        wipeTowerBridgingMm = wipeTowerBridging.parseFloatAtLeastForProcessDraft(0f) ?: initial.wipeTowerBridgingMm,
        wipeTowerExtraSpacingPercent = wipeTowerExtraSpacing.parseIntInForProcessDraft(100, 300) ?: initial.wipeTowerExtraSpacingPercent,
        wipeTowerExtraFlowPercent = wipeTowerExtraFlow.parseIntInForProcessDraft(100, 300) ?: initial.wipeTowerExtraFlowPercent,
        wipeTowerMaxPurgeSpeedMmPerSec = wipeTowerMaxPurgeSpeed.parseFloatAtLeastForProcessDraft(10f) ?: initial.wipeTowerMaxPurgeSpeedMmPerSec,
        wipeTowerWallType = wipeTowerWallType,
        wipeTowerConeAngleDegrees = wipeTowerConeAngle.parseFloatInForProcessDraft(0f, 90f) ?: initial.wipeTowerConeAngleDegrees,
        wipeTowerExtraRibLengthMm = wipeTowerExtraRibLength.parseFloatInForProcessDraft(-300f, 300f) ?: initial.wipeTowerExtraRibLengthMm,
        wipeTowerRibWidthMm = wipeTowerRibWidth.parseFloatInForProcessDraft(0f, 300f) ?: initial.wipeTowerRibWidthMm,
        wipeTowerFilletWall = wipeTowerFilletWall
    )
}

internal data class SpecialModeDetailsDraft(
    val spiralModeSmooth: Boolean,
    val spiralModeMaxXySmoothing: String,
    val spiralStartingFlowRatio: String,
    val spiralFinishingFlowRatio: String,
    val timelapseType: TimelapseType,
    val enableWrappingDetection: Boolean
) {
    companion object {
        fun fromProfile(profile: ProcessProfile): SpecialModeDetailsDraft = SpecialModeDetailsDraft(
            spiralModeSmooth = profile.spiralModeSmooth,
            spiralModeMaxXySmoothing = profile.spiralModeMaxXySmoothing,
            spiralStartingFlowRatio = profile.spiralStartingFlowRatio.toString(),
            spiralFinishingFlowRatio = profile.spiralFinishingFlowRatio.toString(),
            timelapseType = profile.timelapseType,
            enableWrappingDetection = profile.enableWrappingDetection
        )
    }

    fun toDetails(initial: ProcessProfile): ProcessSpecialModeDetails = ProcessSpecialModeDetails(
        spiralModeSmooth = spiralModeSmooth,
        spiralModeMaxXySmoothing = spiralModeMaxXySmoothing.trim().ifBlank { initial.spiralModeMaxXySmoothing },
        spiralStartingFlowRatio = spiralStartingFlowRatio.parseFloatInForProcessDraft(0f, 1f) ?: initial.spiralStartingFlowRatio,
        spiralFinishingFlowRatio = spiralFinishingFlowRatio.parseFloatInForProcessDraft(0f, 1f) ?: initial.spiralFinishingFlowRatio,
        timelapseType = timelapseType,
        enableWrappingDetection = enableWrappingDetection
    )
}

internal data class GcodeOutputDetailsDraft(
    val gcodeAddLineNumber: Boolean,
    val gcodeComments: Boolean,
    val gcodeLabelObjects: Boolean,
    val excludeObject: Boolean
) {
    companion object {
        fun fromProfile(profile: ProcessProfile): GcodeOutputDetailsDraft = GcodeOutputDetailsDraft(
            gcodeAddLineNumber = profile.gcodeAddLineNumber,
            gcodeComments = profile.gcodeComments,
            gcodeLabelObjects = profile.gcodeLabelObjects,
            excludeObject = profile.excludeObject
        )
    }

    fun toDetails(): ProcessGcodeOutputDetails = ProcessGcodeOutputDetails(
        gcodeAddLineNumber = gcodeAddLineNumber,
        gcodeComments = gcodeComments,
        gcodeLabelObjects = gcodeLabelObjects,
        excludeObject = excludeObject
    )
}

private fun String.parseFloatAtLeastForProcessDraft(min: Float): Float? =
    toFloatOrNull()?.takeIf { it >= min }

private fun String.parseFloatInForProcessDraft(min: Float, max: Float): Float? =
    toFloatOrNull()?.takeIf { it in min..max }

private fun String.parseIntAtLeastForProcessDraft(min: Int): Int? =
    toIntOrNull()?.takeIf { it >= min }

private fun String.parseIntInForProcessDraft(min: Int, max: Int): Int? =
    toIntOrNull()?.takeIf { it in min..max }

package com.mobileslicer.profiles

import org.json.JSONArray
import org.json.JSONObject

internal class ProcessProfile private constructor(
    private val values: Map<String, Any?>
) {
    internal constructor(vararg overrides: Pair<Int, Any?>) : this(processProfileValues(overrides.asIterable()))

    internal fun contentHash(): Int = values.hashCode()

    internal fun withValues(vararg overrides: Pair<String, Any?>): ProcessProfile =
        ProcessProfile(values + overrides)

    internal fun copy(
        id: String = this.id,
        name: String = this.name,
        orcaResolvedProcessJson: String = this.orcaResolvedProcessJson,
        fuzzySkin: FuzzySkinType = this.fuzzySkin,
        fuzzySkinThicknessMm: Float = this.fuzzySkinThicknessMm,
        fuzzySkinPointDistanceMm: Float = this.fuzzySkinPointDistanceMm,
        fuzzySkinFirstLayer: Boolean = this.fuzzySkinFirstLayer,
        fuzzySkinMode: FuzzySkinMode = this.fuzzySkinMode,
        fuzzySkinNoiseType: FuzzySkinNoiseType = this.fuzzySkinNoiseType,
        fuzzySkinScaleMm: Float = this.fuzzySkinScaleMm,
        fuzzySkinOctaves: Int = this.fuzzySkinOctaves,
        fuzzySkinPersistence: Float = this.fuzzySkinPersistence,
        ironingType: ProcessIroningType = this.ironingType,
        ironingPattern: IroningPattern = this.ironingPattern,
        ironingFlowPercent: Float = this.ironingFlowPercent,
        ironingSpacingMm: Float = this.ironingSpacingMm,
        ironingInsetMm: Float = this.ironingInsetMm,
        ironingAngleDegrees: Float = this.ironingAngleDegrees,
        ironingAngleFixed: Boolean = this.ironingAngleFixed,
        ironingSpeedMmPerSec: Float = this.ironingSpeedMmPerSec,
        initialLayerAccelerationMmPerSec2: Float = this.initialLayerAccelerationMmPerSec2,
        initialLayerJerkMmPerSec: Float = this.initialLayerJerkMmPerSec,
        firstLayerFlowRatio: Float = this.firstLayerFlowRatio,
        defaultAccelerationMmPerSec2: Float = this.defaultAccelerationMmPerSec2,
        innerWallJerkMmPerSec: Float = this.innerWallJerkMmPerSec,
        innerWallFlowRatio: Float = this.innerWallFlowRatio,
        outerWallJerkMmPerSec: Float = this.outerWallJerkMmPerSec,
        outerWallFlowRatio: Float = this.outerWallFlowRatio,
        topSolidInfillFlowRatio: Float = this.topSolidInfillFlowRatio,
        bottomSolidInfillFlowRatio: Float = this.bottomSolidInfillFlowRatio,
        overhang1_4Speed: String = this.overhang1_4Speed,
        overhang2_4Speed: String = this.overhang2_4Speed,
        overhang3_4Speed: String = this.overhang3_4Speed,
        overhang4_4Speed: String = this.overhang4_4Speed,
        overhangFlowRatio: Float = this.overhangFlowRatio,
        dontSlowDownOuterWall: Boolean = this.dontSlowDownOuterWall,
        enablePrimeTower: Boolean = this.enablePrimeTower,
        primeTowerWidthMm: Float = this.primeTowerWidthMm,
        primeTowerDetails: ProcessPrimeTowerDetails = this.primeTowerDetails,
        standbyTemperatureDeltaC: Int = this.standbyTemperatureDeltaC,
        wipeTowerNoSparseLayers: Boolean = this.wipeTowerNoSparseLayers,
        flushIntoInfill: Boolean = this.flushIntoInfill,
        flushIntoObjects: Boolean = this.flushIntoObjects,
        flushIntoSupport: Boolean = this.flushIntoSupport,
        specialModeDetails: ProcessSpecialModeDetails = this.specialModeDetails,
        gcodeOutputDetails: ProcessGcodeOutputDetails = this.gcodeOutputDetails,
        postProcessScripts: String = this.postProcessScripts,
        notes: String = this.notes,
        enableSupport: Boolean = this.enableSupport,
        supportType: SupportType = this.supportType,
        supportStyle: SupportStyle = this.supportStyle,
        supportThresholdAngleDegrees: Int = this.supportThresholdAngleDegrees,
        supportBuildplateOnly: Boolean = this.supportBuildplateOnly,
        supportTopZDistanceMm: Float = this.supportTopZDistanceMm,
        supportBottomZDistanceMm: Float = this.supportBottomZDistanceMm,
        supportInterfaceTopLayers: Int = this.supportInterfaceTopLayers,
        supportInterfaceBottomLayers: Int = this.supportInterfaceBottomLayers,
        supportInterfaceSpacingMm: Float = this.supportInterfaceSpacingMm,
        supportBottomInterfaceSpacingMm: Float = this.supportBottomInterfaceSpacingMm,
        supportInterfaceSpeedMmPerSec: Float = this.supportInterfaceSpeedMmPerSec,
        supportInterfaceFlowRatio: Float = this.supportInterfaceFlowRatio,
        supportMaterialInterfaceFanSpeed: String = this.supportMaterialInterfaceFanSpeed,
        supportInterfacePattern: SupportInterfacePattern = this.supportInterfacePattern,
        supportInterfaceLoopPattern: Boolean = this.supportInterfaceLoopPattern,
        supportLineWidth: String = this.supportLineWidth,
        supportBasePattern: SupportBasePattern = this.supportBasePattern,
        supportBasePatternSpacingMm: Float = this.supportBasePatternSpacingMm,
        supportSpeedMmPerSec: Float = this.supportSpeedMmPerSec,
        supportFlowRatio: Float = this.supportFlowRatio,
        supportObjectElevationMm: Float = this.supportObjectElevationMm,
        supportMaxBridgeLengthMm: Float = this.supportMaxBridgeLengthMm,
        supportIroning: Boolean = this.supportIroning,
        supportIroningFlowPercent: Float = this.supportIroningFlowPercent,
        supportIroningSpacingMm: Float = this.supportIroningSpacingMm,
        supportExpansionMm: Float = this.supportExpansionMm,
        supportObjectXyDistanceMm: Float = this.supportObjectXyDistanceMm
    ): ProcessProfile = withValues(
        "id" to id,
        "name" to name,
        "orcaResolvedProcessJson" to orcaResolvedProcessJson,
        "fuzzySkin" to fuzzySkin,
        "fuzzySkinThicknessMm" to fuzzySkinThicknessMm,
        "fuzzySkinPointDistanceMm" to fuzzySkinPointDistanceMm,
        "fuzzySkinFirstLayer" to fuzzySkinFirstLayer,
        "fuzzySkinMode" to fuzzySkinMode,
        "fuzzySkinNoiseType" to fuzzySkinNoiseType,
        "fuzzySkinScaleMm" to fuzzySkinScaleMm,
        "fuzzySkinOctaves" to fuzzySkinOctaves,
        "fuzzySkinPersistence" to fuzzySkinPersistence,
        "ironingType" to ironingType,
        "ironingPattern" to ironingPattern,
        "ironingFlowPercent" to ironingFlowPercent,
        "ironingSpacingMm" to ironingSpacingMm,
        "ironingInsetMm" to ironingInsetMm,
        "ironingAngleDegrees" to ironingAngleDegrees,
        "ironingAngleFixed" to ironingAngleFixed,
        "ironingSpeedMmPerSec" to ironingSpeedMmPerSec,
        "initialLayerAccelerationMmPerSec2" to initialLayerAccelerationMmPerSec2,
        "initialLayerJerkMmPerSec" to initialLayerJerkMmPerSec,
        "firstLayerFlowRatio" to firstLayerFlowRatio,
        "defaultAccelerationMmPerSec2" to defaultAccelerationMmPerSec2,
        "innerWallJerkMmPerSec" to innerWallJerkMmPerSec,
        "innerWallFlowRatio" to innerWallFlowRatio,
        "outerWallJerkMmPerSec" to outerWallJerkMmPerSec,
        "outerWallFlowRatio" to outerWallFlowRatio,
        "topSolidInfillFlowRatio" to topSolidInfillFlowRatio,
        "bottomSolidInfillFlowRatio" to bottomSolidInfillFlowRatio,
        "overhang1_4Speed" to overhang1_4Speed,
        "overhang2_4Speed" to overhang2_4Speed,
        "overhang3_4Speed" to overhang3_4Speed,
        "overhang4_4Speed" to overhang4_4Speed,
        "overhangFlowRatio" to overhangFlowRatio,
        "dontSlowDownOuterWall" to dontSlowDownOuterWall,
        "enablePrimeTower" to enablePrimeTower,
        "primeTowerWidthMm" to primeTowerWidthMm,
        "primeTowerDetails" to primeTowerDetails,
        "standbyTemperatureDeltaC" to standbyTemperatureDeltaC,
        "wipeTowerNoSparseLayers" to wipeTowerNoSparseLayers,
        "flushIntoInfill" to flushIntoInfill,
        "flushIntoObjects" to flushIntoObjects,
        "flushIntoSupport" to flushIntoSupport,
        "specialModeDetails" to specialModeDetails,
        "gcodeOutputDetails" to gcodeOutputDetails,
        "postProcessScripts" to postProcessScripts,
        "notes" to notes,
        "enableSupport" to enableSupport,
        "supportType" to supportType,
        "supportStyle" to supportStyle,
        "supportThresholdAngleDegrees" to supportThresholdAngleDegrees,
        "supportBuildplateOnly" to supportBuildplateOnly,
        "supportTopZDistanceMm" to supportTopZDistanceMm,
        "supportBottomZDistanceMm" to supportBottomZDistanceMm,
        "supportInterfaceTopLayers" to supportInterfaceTopLayers,
        "supportInterfaceBottomLayers" to supportInterfaceBottomLayers,
        "supportInterfaceSpacingMm" to supportInterfaceSpacingMm,
        "supportBottomInterfaceSpacingMm" to supportBottomInterfaceSpacingMm,
        "supportInterfaceSpeedMmPerSec" to supportInterfaceSpeedMmPerSec,
        "supportInterfaceFlowRatio" to supportInterfaceFlowRatio,
        "supportMaterialInterfaceFanSpeed" to supportMaterialInterfaceFanSpeed,
        "supportInterfacePattern" to supportInterfacePattern,
        "supportInterfaceLoopPattern" to supportInterfaceLoopPattern,
        "supportLineWidth" to supportLineWidth,
        "supportBasePattern" to supportBasePattern,
        "supportBasePatternSpacingMm" to supportBasePatternSpacingMm,
        "supportSpeedMmPerSec" to supportSpeedMmPerSec,
        "supportFlowRatio" to supportFlowRatio,
        "supportObjectElevationMm" to supportObjectElevationMm,
        "supportMaxBridgeLengthMm" to supportMaxBridgeLengthMm,
        "supportIroning" to supportIroning,
        "supportIroningFlowPercent" to supportIroningFlowPercent,
        "supportIroningSpacingMm" to supportIroningSpacingMm,
        "supportExpansionMm" to supportExpansionMm,
        "supportObjectXyDistanceMm" to supportObjectXyDistanceMm
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> value(name: String): T = values[name] as T

    val id: String get() = value("id")
    val name: String get() = value("name")
    val subtitle: String get() = value("subtitle")
    val builtIn: Boolean get() = value("builtIn")
    val firstLayerHeightMm: Float get() = value("firstLayerHeightMm")
    val layerHeightMm: Float get() = value("layerHeightMm")
    val firstLayerPrintSpeedMmPerSec: Float get() = value("firstLayerPrintSpeedMmPerSec")
    val firstLayerInfillSpeedMmPerSec: Float get() = value("firstLayerInfillSpeedMmPerSec")
    val firstLayerTravelSpeedPercent: Int get() = value("firstLayerTravelSpeedPercent")
    val slowDownLayers: Int get() = value("slowDownLayers")
    val initialLayerAccelerationMmPerSec2: Float get() = value("initialLayerAccelerationMmPerSec2")
    val initialLayerJerkMmPerSec: Float get() = value("initialLayerJerkMmPerSec")
    val firstLayerFlowRatio: Float get() = value("firstLayerFlowRatio")
    val printExtruderId: String get() = value("printExtruderId")
    val printExtruderVariant: String get() = value("printExtruderVariant")
    val outerWallSpeedMmPerSec: Float get() = value("outerWallSpeedMmPerSec")
    val innerWallSpeedMmPerSec: Float get() = value("innerWallSpeedMmPerSec")
    val topSurfaceSpeedMmPerSec: Float get() = value("topSurfaceSpeedMmPerSec")
    val travelSpeedMmPerSec: Float get() = value("travelSpeedMmPerSec")
    val defaultAccelerationMmPerSec2: Float get() = value("defaultAccelerationMmPerSec2")
    val outerWallAccelerationMmPerSec2: Float get() = value("outerWallAccelerationMmPerSec2")
    val innerWallAccelerationMmPerSec2: Float get() = value("innerWallAccelerationMmPerSec2")
    val topSurfaceAccelerationMmPerSec2: Float get() = value("topSurfaceAccelerationMmPerSec2")
    val sparseInfillAccelerationMmPerSec2: Float get() = value("sparseInfillAccelerationMmPerSec2")
    val internalSolidInfillAcceleration: String get() = value("internalSolidInfillAcceleration")
    val travelAccelerationMmPerSec2: Float get() = value("travelAccelerationMmPerSec2")
    val accelToDecelEnable: Boolean get() = value("accelToDecelEnable")
    val accelToDecelFactorPercent: Int get() = value("accelToDecelFactorPercent")
    val defaultJunctionDeviationMm: Float get() = value("defaultJunctionDeviationMm")
    val defaultJerkMmPerSec: Float get() = value("defaultJerkMmPerSec")
    val innerWallJerkMmPerSec: Float get() = value("innerWallJerkMmPerSec")
    val infillJerkMmPerSec: Float get() = value("infillJerkMmPerSec")
    val topSurfaceJerkMmPerSec: Float get() = value("topSurfaceJerkMmPerSec")
    val travelJerkMmPerSec: Float get() = value("travelJerkMmPerSec")
    val innerWallFlowRatio: Float get() = value("innerWallFlowRatio")
    val outerWallJerkMmPerSec: Float get() = value("outerWallJerkMmPerSec")
    val outerWallFlowRatio: Float get() = value("outerWallFlowRatio")
    val topSolidInfillFlowRatio: Float get() = value("topSolidInfillFlowRatio")
    val bottomSolidInfillFlowRatio: Float get() = value("bottomSolidInfillFlowRatio")
    val overhang1_4Speed: String get() = value("overhang1_4Speed")
    val overhang2_4Speed: String get() = value("overhang2_4Speed")
    val overhang3_4Speed: String get() = value("overhang3_4Speed")
    val overhang4_4Speed: String get() = value("overhang4_4Speed")
    val enableOverhangSpeed: Boolean get() = value("enableOverhangSpeed")
    val slowdownForCurledPerimeters: Boolean get() = value("slowdownForCurledPerimeters")
    val overhangFlowRatio: Float get() = value("overhangFlowRatio")
    val dontSlowDownOuterWall: Boolean get() = value("dontSlowDownOuterWall")
    val bridgeAcceleration: String get() = value("bridgeAcceleration")
    val bridgeSpeedMmPerSec: Float get() = value("bridgeSpeedMmPerSec")
    val smallPerimeterSpeedMmPerSec: Float get() = value("smallPerimeterSpeedMmPerSec")
    val smallPerimeterThresholdMm: Float get() = value("smallPerimeterThresholdMm")
    val bridgeAngleDegrees: Float get() = value("bridgeAngleDegrees")
    val bridgeDensityPercent: Int get() = value("bridgeDensityPercent")
    val bridgeFlowRatio: Float get() = value("bridgeFlowRatio")
    val bridgeNoSupport: Boolean get() = value("bridgeNoSupport")
    val qualitySurfaceDetails: ProcessQualitySurfaceDetails get() = value("qualitySurfaceDetails")
    val internalBridgeAngleDegrees: Float get() = value("internalBridgeAngleDegrees")
    val internalBridgeDensityPercent: Int get() = value("internalBridgeDensityPercent")
    val internalBridgeFlowRatio: Float get() = value("internalBridgeFlowRatio")
    val internalBridgeSpeed: String get() = value("internalBridgeSpeed")
    val internalBridgeFanSpeed: String get() = value("internalBridgeFanSpeed")
    val internalBridgeSupportThickness: String get() = value("internalBridgeSupportThickness")
    val maxVolumetricExtrusionRateSlope: Float get() = value("maxVolumetricExtrusionRateSlope")
    val maxVolumetricExtrusionRateSlopeSegmentLengthMm: Float get() = value("maxVolumetricExtrusionRateSlopeSegmentLengthMm")
    val extrusionRateSmoothingExternalPerimeterOnly: Boolean get() = value("extrusionRateSmoothingExternalPerimeterOnly")
    val sparseInfillSpeedMmPerSec: Float get() = value("sparseInfillSpeedMmPerSec")
    val internalSolidInfillSpeedMmPerSec: Float get() = value("internalSolidInfillSpeedMmPerSec")
    val gapInfillSpeedMmPerSec: Float get() = value("gapInfillSpeedMmPerSec")
    val adaptiveLayerHeight: Boolean get() = value("adaptiveLayerHeight")
    val topShellLayers: Int get() = value("topShellLayers")
    val bottomShellLayers: Int get() = value("bottomShellLayers")
    val topShellThicknessMm: Float get() = value("topShellThicknessMm")
    val bottomShellThicknessMm: Float get() = value("bottomShellThicknessMm")
    val topSurfaceDensityPercent: Int get() = value("topSurfaceDensityPercent")
    val bottomSurfaceDensityPercent: Int get() = value("bottomSurfaceDensityPercent")
    val seamPosition: ProcessSeamPosition get() = value("seamPosition")
    val staggeredInnerSeams: Boolean get() = value("staggeredInnerSeams")
    val roleBasedWipeSpeed: Boolean get() = value("roleBasedWipeSpeed")
    val wipeSpeed: String get() = value("wipeSpeed")
    val wipeOnLoops: Boolean get() = value("wipeOnLoops")
    val wipeBeforeExternalLoop: Boolean get() = value("wipeBeforeExternalLoop")
    val preciseOuterWall: Boolean get() = value("preciseOuterWall")
    val onlyOneWallTopSurfaces: Boolean get() = value("onlyOneWallTopSurfaces")
    val topSurfacePattern: TopSurfacePattern get() = value("topSurfacePattern")
    val bottomSurfacePattern: BottomSurfacePattern get() = value("bottomSurfacePattern")
    val internalSolidInfillPattern: InternalSolidInfillPattern get() = value("internalSolidInfillPattern")
    val solidInfillDirectionDegrees: Float get() = value("solidInfillDirectionDegrees")
    val solidInfillRotateTemplate: String get() = value("solidInfillRotateTemplate")
    val lineWidth: String get() = value("lineWidth")
    val outerWallLineWidth: String get() = value("outerWallLineWidth")
    val innerWallLineWidth: String get() = value("innerWallLineWidth")
    val initialLayerLineWidth: String get() = value("initialLayerLineWidth")
    val initialLayerMinBeadWidthPercent: Int get() = value("initialLayerMinBeadWidthPercent")
    val topSurfaceLineWidth: String get() = value("topSurfaceLineWidth")
    val internalSolidInfillLineWidth: String get() = value("internalSolidInfillLineWidth")
    val minWidthTopSurface: String get() = value("minWidthTopSurface")
    val sparseInfillLineWidth: String get() = value("sparseInfillLineWidth")
    val infillDirectionDegrees: Float get() = value("infillDirectionDegrees")
    val sparseInfillRotateTemplate: String get() = value("sparseInfillRotateTemplate")
    val alignInfillDirectionToModel: Boolean get() = value("alignInfillDirectionToModel")
    val infillWallOverlapPercent: Int get() = value("infillWallOverlapPercent")
    val topBottomInfillWallOverlapPercent: Int get() = value("topBottomInfillWallOverlapPercent")
    val infillAnchor: String get() = value("infillAnchor")
    val infillAnchorMax: String get() = value("infillAnchorMax")
    val infillCombination: Boolean get() = value("infillCombination")
    val infillCombinationMaxLayerHeight: String get() = value("infillCombinationMaxLayerHeight")
    val minimumSparseInfillAreaMm2: Float get() = value("minimumSparseInfillAreaMm2")
    val detectThinWall: Boolean get() = value("detectThinWall")
    val detectOverhangWall: Boolean get() = value("detectOverhangWall")
    val thickBridges: Boolean get() = value("thickBridges")
    val sliceClosingRadiusMm: Float get() = value("sliceClosingRadiusMm")
    val resolutionMm: Float get() = value("resolutionMm")
    val interfaceShells: Boolean get() = value("interfaceShells")
    val dontFilterInternalBridges: InternalBridgeFilterMode get() = value("dontFilterInternalBridges")
    val detectNarrowInternalSolidInfill: Boolean get() = value("detectNarrowInternalSolidInfill")
    val elefantFootCompensationMm: Float get() = value("elefantFootCompensationMm")
    val applyTopSurfaceCompensation: Boolean get() = value("applyTopSurfaceCompensation")
    val ensureVerticalShellThickness: EnsureVerticalShellThicknessMode get() = value("ensureVerticalShellThickness")
    val wallGenerator: WallGenerator get() = value("wallGenerator")
    val wallTransitionAngleDegrees: Float get() = value("wallTransitionAngleDegrees")
    val wallTransitionFilterDeviationPercent: Int get() = value("wallTransitionFilterDeviationPercent")
    val wallTransitionLengthPercent: Int get() = value("wallTransitionLengthPercent")
    val wallDistributionCount: Int get() = value("wallDistributionCount")
    val minBeadWidthPercent: Int get() = value("minBeadWidthPercent")
    val minFeatureSizePercent: Int get() = value("minFeatureSizePercent")
    val minLengthFactorMm: Float get() = value("minLengthFactorMm")
    val wallInfillOrder: WallInfillOrder get() = value("wallInfillOrder")
    val extraPerimetersOnOverhangs: Boolean get() = value("extraPerimetersOnOverhangs")
    val xyHoleCompensationMm: Float get() = value("xyHoleCompensationMm")
    val xyContourCompensationMm: Float get() = value("xyContourCompensationMm")
    val wallCount: Int get() = value("wallCount")
    val infillPercent: Int get() = value("infillPercent")
    val sparseInfillPattern: SparseInfillPattern get() = value("sparseInfillPattern")
    val strengthInfillDetails: ProcessStrengthInfillDetails get() = value("strengthInfillDetails")
    val sparseInfillFilament: Int get() = value("sparseInfillFilament")
    val sparseInfillFlowRatio: Float get() = value("sparseInfillFlowRatio")
    val lateralLatticeAngle1Degrees: Float get() = value("lateralLatticeAngle1Degrees")
    val lateralLatticeAngle2Degrees: Float get() = value("lateralLatticeAngle2Degrees")
    val fillMultiline: Int get() = value("fillMultiline")
    val gapFillFlowRatio: Float get() = value("gapFillFlowRatio")
    val enableSupport: Boolean get() = value("enableSupport")
    val supportType: SupportType get() = value("supportType")
    val supportStyle: SupportStyle get() = value("supportStyle")
    val supportThresholdAngleDegrees: Int get() = value("supportThresholdAngleDegrees")
    val supportThresholdOverlap: String get() = value("supportThresholdOverlap")
    val supportBuildplateOnly: Boolean get() = value("supportBuildplateOnly")
    val supportCriticalRegionsOnly: Boolean get() = value("supportCriticalRegionsOnly")
    val supportRemoveSmallOverhang: Boolean get() = value("supportRemoveSmallOverhang")
    val raftFirstLayerDensityPercent: Int get() = value("raftFirstLayerDensityPercent")
    val raftFirstLayerExpansionMm: Float get() = value("raftFirstLayerExpansionMm")
    val raftLayers: Int get() = value("raftLayers")
    val raftContactDistanceMm: Float get() = value("raftContactDistanceMm")
    val raftExpansionMm: Float get() = value("raftExpansionMm")
    val supportFilament: Int get() = value("supportFilament")
    val supportInterfaceFilament: Int get() = value("supportInterfaceFilament")
    val supportInterfaceNotForBody: Boolean get() = value("supportInterfaceNotForBody")
    val supportTopZDistanceMm: Float get() = value("supportTopZDistanceMm")
    val supportBottomZDistanceMm: Float get() = value("supportBottomZDistanceMm")
    val supportInterfaceTopLayers: Int get() = value("supportInterfaceTopLayers")
    val supportInterfaceBottomLayers: Int get() = value("supportInterfaceBottomLayers")
    val supportInterfaceSpacingMm: Float get() = value("supportInterfaceSpacingMm")
    val supportBottomInterfaceSpacingMm: Float get() = value("supportBottomInterfaceSpacingMm")
    val supportInterfaceSpeedMmPerSec: Float get() = value("supportInterfaceSpeedMmPerSec")
    val supportInterfaceFlowRatio: Float get() = value("supportInterfaceFlowRatio")
    val supportMaterialInterfaceFanSpeed: String get() = value("supportMaterialInterfaceFanSpeed")
    val supportInterfacePattern: SupportInterfacePattern get() = value("supportInterfacePattern")
    val supportInterfaceLoopPattern: Boolean get() = value("supportInterfaceLoopPattern")
    val supportLineWidth: String get() = value("supportLineWidth")
    val supportBasePattern: SupportBasePattern get() = value("supportBasePattern")
    val supportBasePatternSpacingMm: Float get() = value("supportBasePatternSpacingMm")
    val supportAngleDegrees: Float get() = value("supportAngleDegrees")
    val supportSpeedMmPerSec: Float get() = value("supportSpeedMmPerSec")
    val supportFlowRatio: Float get() = value("supportFlowRatio")
    val supportObjectElevationMm: Float get() = value("supportObjectElevationMm")
    val supportObjectFirstLayerGapMm: Float get() = value("supportObjectFirstLayerGapMm")
    val supportMaxBridgeLengthMm: Float get() = value("supportMaxBridgeLengthMm")
    val supportIroning: Boolean get() = value("supportIroning")
    val supportIroningPattern: IroningPattern get() = value("supportIroningPattern")
    val supportIroningFlowPercent: Float get() = value("supportIroningFlowPercent")
    val supportIroningSpacingMm: Float get() = value("supportIroningSpacingMm")
    val supportExpansionMm: Float get() = value("supportExpansionMm")
    val supportObjectXyDistanceMm: Float get() = value("supportObjectXyDistanceMm")
    val independentSupportLayerHeight: Boolean get() = value("independentSupportLayerHeight")
    val treeSupportBranchAngleDegrees: Float get() = value("treeSupportBranchAngleDegrees")
    val treeSupportBranchDiameterMm: Float get() = value("treeSupportBranchDiameterMm")
    val treeSupportWallCount: Int get() = value("treeSupportWallCount")
    val treeSupportTipDiameterMm: Float get() = value("treeSupportTipDiameterMm")
    val treeSupportBranchDistanceMm: Float get() = value("treeSupportBranchDistanceMm")
    val treeSupportBranchDistanceOrganicMm: Float get() = value("treeSupportBranchDistanceOrganicMm")
    val treeSupportTopRatePercent: Int get() = value("treeSupportTopRatePercent")
    val treeSupportBranchDiameterOrganicMm: Float get() = value("treeSupportBranchDiameterOrganicMm")
    val treeSupportBranchDiameterAngleDegrees: Float get() = value("treeSupportBranchDiameterAngleDegrees")
    val treeSupportBranchAngleOrganicDegrees: Float get() = value("treeSupportBranchAngleOrganicDegrees")
    val treeSupportPreferredBranchAngleDegrees: Float get() = value("treeSupportPreferredBranchAngleDegrees")
    val treeSupportAutoBrim: Boolean get() = value("treeSupportAutoBrim")
    val treeSupportBrimWidthMm: Float get() = value("treeSupportBrimWidthMm")
    val enablePrimeTower: Boolean get() = value("enablePrimeTower")
    val primeTowerWidthMm: Float get() = value("primeTowerWidthMm")
    val primeTowerDetails: ProcessPrimeTowerDetails get() = value("primeTowerDetails")
    val enableTowerInterfaceFeatures: Boolean get() = value("enableTowerInterfaceFeatures")
    val enableTowerInterfaceCooldownDuringTower: Boolean get() = value("enableTowerInterfaceCooldownDuringTower")
    val singleExtruderMultiMaterialPriming: Boolean get() = value("singleExtruderMultiMaterialPriming")
    val standbyTemperatureDeltaC: Int get() = value("standbyTemperatureDeltaC")
    val wipeTowerNoSparseLayers: Boolean get() = value("wipeTowerNoSparseLayers")
    val flushIntoInfill: Boolean get() = value("flushIntoInfill")
    val flushIntoObjects: Boolean get() = value("flushIntoObjects")
    val flushIntoSupport: Boolean get() = value("flushIntoSupport")
    val skirts: Int get() = value("skirts")
    val brimWidthMm: Float get() = value("brimWidthMm")
    val fuzzySkin: FuzzySkinType get() = value("fuzzySkin")
    val fuzzySkinThicknessMm: Float get() = value("fuzzySkinThicknessMm")
    val fuzzySkinPointDistanceMm: Float get() = value("fuzzySkinPointDistanceMm")
    val fuzzySkinFirstLayer: Boolean get() = value("fuzzySkinFirstLayer")
    val fuzzySkinMode: FuzzySkinMode get() = value("fuzzySkinMode")
    val fuzzySkinNoiseType: FuzzySkinNoiseType get() = value("fuzzySkinNoiseType")
    val fuzzySkinScaleMm: Float get() = value("fuzzySkinScaleMm")
    val fuzzySkinOctaves: Int get() = value("fuzzySkinOctaves")
    val fuzzySkinPersistence: Float get() = value("fuzzySkinPersistence")
    val ironingType: ProcessIroningType get() = value("ironingType")
    val ironingPattern: IroningPattern get() = value("ironingPattern")
    val ironingFlowPercent: Float get() = value("ironingFlowPercent")
    val ironingSpacingMm: Float get() = value("ironingSpacingMm")
    val ironingInsetMm: Float get() = value("ironingInsetMm")
    val ironingAngleDegrees: Float get() = value("ironingAngleDegrees")
    val ironingAngleFixed: Boolean get() = value("ironingAngleFixed")
    val ironingSpeedMmPerSec: Float get() = value("ironingSpeedMmPerSec")
    val seamGap: String get() = value("seamGap")
    val seamScarfType: SeamScarfType get() = value("seamScarfType")
    val seamScarfConditional: Boolean get() = value("seamScarfConditional")
    val scarfAngleThresholdDegrees: Int get() = value("scarfAngleThresholdDegrees")
    val scarfOverhangThresholdPercent: Int get() = value("scarfOverhangThresholdPercent")
    val scarfJointSpeed: String get() = value("scarfJointSpeed")
    val scarfJointFlowRatio: Float get() = value("scarfJointFlowRatio")
    val seamScarfStartHeight: String get() = value("seamScarfStartHeight")
    val seamScarfEntireLoop: Boolean get() = value("seamScarfEntireLoop")
    val seamScarfMinLengthMm: Float get() = value("seamScarfMinLengthMm")
    val seamScarfSteps: Int get() = value("seamScarfSteps")
    val seamScarfInnerWalls: Boolean get() = value("seamScarfInnerWalls")
    val hasScarfJointSeam: Boolean get() = value("hasScarfJointSeam")
    val counterboreHoleBridging: CounterboreHoleBridging get() = value("counterboreHoleBridging")
    val wallSequence: WallSequence get() = value("wallSequence")
    val enableArcFitting: Boolean get() = value("enableArcFitting")
    val reduceCrossingWall: Boolean get() = value("reduceCrossingWall")
    val maxTravelDetourDistance: String get() = value("maxTravelDetourDistance")
    val holeToPolyhole: Boolean get() = value("holeToPolyhole")
    val holeToPolyholeThreshold: String get() = value("holeToPolyholeThreshold")
    val holeToPolyholeTwisted: Boolean get() = value("holeToPolyholeTwisted")
    val alternateExtraWall: Boolean get() = value("alternateExtraWall")
    val extraSolidInfills: String get() = value("extraSolidInfills")
    val skirtType: SkirtType get() = value("skirtType")
    val minSkirtLengthMm: Float get() = value("minSkirtLengthMm")
    val skirtDistanceMm: Float get() = value("skirtDistanceMm")
    val skirtStartAngleDegrees: Float get() = value("skirtStartAngleDegrees")
    val skirtSpeedMmPerSec: Float get() = value("skirtSpeedMmPerSec")
    val skirtHeightLayers: Int get() = value("skirtHeightLayers")
    val draftShield: DraftShield get() = value("draftShield")
    val singleLoopDraftShield: Boolean get() = value("singleLoopDraftShield")
    val brimType: BrimType get() = value("brimType")
    val brimObjectGapMm: Float get() = value("brimObjectGapMm")
    val brimUseEfcOutline: Boolean get() = value("brimUseEfcOutline")
    val combineBrims: Boolean get() = value("combineBrims")
    val brimEars: Boolean get() = value("brimEars")
    val brimEarsDetectionLengthMm: Float get() = value("brimEarsDetectionLengthMm")
    val brimEarsMaxAngleDegrees: Float get() = value("brimEarsMaxAngleDegrees")
    val slicingMode: SlicingMode get() = value("slicingMode")
    val printSequence: PrintSequence get() = value("printSequence")
    val printOrder: PrintOrder get() = value("printOrder")
    val spiralMode: Boolean get() = value("spiralMode")
    val specialModeDetails: ProcessSpecialModeDetails get() = value("specialModeDetails")
    val reduceInfillRetraction: Boolean get() = value("reduceInfillRetraction")
    val gcodeOutputDetails: ProcessGcodeOutputDetails get() = value("gcodeOutputDetails")
    val filamentMapMode: FilamentMapMode get() = value("filamentMapMode")
    val allowMixTemp: Boolean get() = value("allowMixTemp")
    val allowMulticolorOnePlate: Boolean get() = value("allowMulticolorOnePlate")
    val filenameFormat: String get() = value("filenameFormat")
    val postProcessScripts: String get() = value("postProcessScripts")
    val notes: String get() = value("notes")
    val profileSource: String get() = value("profileSource")
    val printerProfileId: String get() = value("printerProfileId")
    val printerVariantKey: String get() = value("printerVariantKey")
    val nozzleDiameterMm: Float get() = value("nozzleDiameterMm")
    val orcaFamily: String get() = value("orcaFamily")
    val orcaProcessPath: String get() = value("orcaProcessPath")
    val orcaRawProcessJson: String get() = value("orcaRawProcessJson")
	    val orcaResolvedProcessJson: String get() = value("orcaResolvedProcessJson")
	    val orcaProcessOverridesJson: String get() = value("orcaProcessOverridesJson")
	    val orcaResolvedSourceChain: List<String> get() = value("orcaResolvedSourceChain")

val thickInternalBridges: Boolean get() = qualitySurfaceDetails.thickInternalBridges
    val extraBridgeLayer: ExtraBridgeLayerMode get() = qualitySurfaceDetails.extraBridgeLayer
    val preciseZHeight: Boolean get() = qualitySurfaceDetails.preciseZHeight
    val onlyOneWallFirstLayer: Boolean get() = qualitySurfaceDetails.onlyOneWallFirstLayer
    val printInfillFirst: Boolean get() = qualitySurfaceDetails.printInfillFirst
    val wallDirection: WallDirection get() = qualitySurfaceDetails.wallDirection
    val printFlowRatio: Float get() = qualitySurfaceDetails.printFlowRatio
    val elefantFootCompensationLayers: Int get() = qualitySurfaceDetails.elefantFootCompensationLayers
    val internalSolidInfillFlowRatio: Float get() = qualitySurfaceDetails.internalSolidInfillFlowRatio
    val setOtherFlowRatios: Boolean get() = qualitySurfaceDetails.setOtherFlowRatios
    val smallAreaInfillFlowCompensation: Boolean get() = qualitySurfaceDetails.smallAreaInfillFlowCompensation
    val makeOverhangPrintable: Boolean get() = qualitySurfaceDetails.makeOverhangPrintable
    val makeOverhangPrintableAngleDegrees: Float get() = qualitySurfaceDetails.makeOverhangPrintableAngleDegrees
    val makeOverhangPrintableHoleSizeMm2: Float get() = qualitySurfaceDetails.makeOverhangPrintableHoleSizeMm2
    val overhangReverse: Boolean get() = qualitySurfaceDetails.overhangReverse
    val overhangReverseInternalOnly: Boolean get() = qualitySurfaceDetails.overhangReverseInternalOnly
    val overhangReverseThreshold: String get() = qualitySurfaceDetails.overhangReverseThreshold
    val skinInfillDensity: Int get() = strengthInfillDetails.skinInfillDensity
    val skeletonInfillDensity: Int get() = strengthInfillDetails.skeletonInfillDensity
    val infillLockDepthMm: Float get() = strengthInfillDetails.infillLockDepthMm
    val skinInfillDepthMm: Float get() = strengthInfillDetails.skinInfillDepthMm
    val skinInfillLineWidth: String get() = strengthInfillDetails.skinInfillLineWidth
    val skeletonInfillLineWidth: String get() = strengthInfillDetails.skeletonInfillLineWidth
    val symmetricInfillYAxis: Boolean get() = strengthInfillDetails.symmetricInfillYAxis
    val infillShiftStepMm: Float get() = strengthInfillDetails.infillShiftStepMm
    val infillOverhangAngleDegrees: Float get() = strengthInfillDetails.infillOverhangAngleDegrees
    val gapFillTarget: String get() = strengthInfillDetails.gapFillTarget
    val filterOutGapFillMm: Float get() = strengthInfillDetails.filterOutGapFillMm
    val primeTowerSkipPoints: Boolean get() = primeTowerDetails.primeTowerSkipPoints
    val wipeTowerXmm: Float get() = primeTowerDetails.wipeTowerXmm
    val wipeTowerYmm: Float get() = primeTowerDetails.wipeTowerYmm
    val primeVolumeMm3: Float get() = primeTowerDetails.primeVolumeMm3
    val primeTowerBrimWidthMm: Float get() = primeTowerDetails.primeTowerBrimWidthMm
    val primeTowerInfillGapPercent: Int get() = primeTowerDetails.primeTowerInfillGapPercent
    val wipeTowerRotationAngleDegrees: Float get() = primeTowerDetails.wipeTowerRotationAngleDegrees
    val wipeTowerBridgingMm: Float get() = primeTowerDetails.wipeTowerBridgingMm
    val wipeTowerExtraSpacingPercent: Int get() = primeTowerDetails.wipeTowerExtraSpacingPercent
    val wipeTowerExtraFlowPercent: Int get() = primeTowerDetails.wipeTowerExtraFlowPercent
    val wipeTowerMaxPurgeSpeedMmPerSec: Float get() = primeTowerDetails.wipeTowerMaxPurgeSpeedMmPerSec
    val wipeTowerWallType: WipeTowerWallType get() = primeTowerDetails.wipeTowerWallType
    val wipeTowerConeAngleDegrees: Float get() = primeTowerDetails.wipeTowerConeAngleDegrees
    val wipeTowerExtraRibLengthMm: Float get() = primeTowerDetails.wipeTowerExtraRibLengthMm
    val wipeTowerRibWidthMm: Float get() = primeTowerDetails.wipeTowerRibWidthMm
    val wipeTowerFilletWall: Boolean get() = primeTowerDetails.wipeTowerFilletWall
    val spiralModeSmooth: Boolean get() = specialModeDetails.spiralModeSmooth
    val spiralModeMaxXySmoothing: String get() = specialModeDetails.spiralModeMaxXySmoothing
    val spiralStartingFlowRatio: Float get() = specialModeDetails.spiralStartingFlowRatio
    val spiralFinishingFlowRatio: Float get() = specialModeDetails.spiralFinishingFlowRatio
    val timelapseType: TimelapseType get() = specialModeDetails.timelapseType
    val enableWrappingDetection: Boolean get() = specialModeDetails.enableWrappingDetection
    val gcodeAddLineNumber: Boolean get() = gcodeOutputDetails.gcodeAddLineNumber
    val gcodeComments: Boolean get() = gcodeOutputDetails.gcodeComments
    val gcodeLabelObjects: Boolean get() = gcodeOutputDetails.gcodeLabelObjects
    val excludeObject: Boolean get() = gcodeOutputDetails.excludeObject
}

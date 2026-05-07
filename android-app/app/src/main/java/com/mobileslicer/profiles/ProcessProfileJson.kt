package com.mobileslicer.profiles

import org.json.JSONArray
import org.json.JSONObject

internal fun ProcessProfile.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("subtitle", subtitle)
    .put("builtIn", builtIn)
    .put("firstLayerHeightMm", firstLayerHeightMm.toDouble())
    .put("layerHeightMm", layerHeightMm.toDouble())
    .put("firstLayerPrintSpeedMmPerSec", firstLayerPrintSpeedMmPerSec.toDouble())
    .put("firstLayerInfillSpeedMmPerSec", firstLayerInfillSpeedMmPerSec.toDouble())
    .put("firstLayerTravelSpeedPercent", firstLayerTravelSpeedPercent)
    .put("slowDownLayers", slowDownLayers)
    .put("initialLayerAccelerationMmPerSec2", initialLayerAccelerationMmPerSec2.toDouble())
    .put("initialLayerJerkMmPerSec", initialLayerJerkMmPerSec.toDouble())
    .put("firstLayerFlowRatio", firstLayerFlowRatio.toDouble())
    .put("printExtruderId", printExtruderId)
    .put("printExtruderVariant", printExtruderVariant)
    .put("outerWallSpeedMmPerSec", outerWallSpeedMmPerSec.toDouble())
    .put("innerWallSpeedMmPerSec", innerWallSpeedMmPerSec.toDouble())
    .put("topSurfaceSpeedMmPerSec", topSurfaceSpeedMmPerSec.toDouble())
    .put("travelSpeedMmPerSec", travelSpeedMmPerSec.toDouble())
    .put("defaultAccelerationMmPerSec2", defaultAccelerationMmPerSec2.toDouble())
    .put("outerWallAccelerationMmPerSec2", outerWallAccelerationMmPerSec2.toDouble())
    .put("innerWallAccelerationMmPerSec2", innerWallAccelerationMmPerSec2.toDouble())
    .put("topSurfaceAccelerationMmPerSec2", topSurfaceAccelerationMmPerSec2.toDouble())
    .put("sparseInfillAccelerationMmPerSec2", sparseInfillAccelerationMmPerSec2.toDouble())
    .put("internalSolidInfillAcceleration", internalSolidInfillAcceleration)
    .put("travelAccelerationMmPerSec2", travelAccelerationMmPerSec2.toDouble())
    .put("accelToDecelEnable", accelToDecelEnable)
    .put("accelToDecelFactorPercent", accelToDecelFactorPercent)
    .put("defaultJunctionDeviationMm", defaultJunctionDeviationMm.toDouble())
    .put("defaultJerkMmPerSec", defaultJerkMmPerSec.toDouble())
    .put("innerWallJerkMmPerSec", innerWallJerkMmPerSec.toDouble())
    .put("infillJerkMmPerSec", infillJerkMmPerSec.toDouble())
    .put("topSurfaceJerkMmPerSec", topSurfaceJerkMmPerSec.toDouble())
    .put("travelJerkMmPerSec", travelJerkMmPerSec.toDouble())
    .put("innerWallFlowRatio", innerWallFlowRatio.toDouble())
    .put("outerWallJerkMmPerSec", outerWallJerkMmPerSec.toDouble())
    .put("outerWallFlowRatio", outerWallFlowRatio.toDouble())
    .put("topSolidInfillFlowRatio", topSolidInfillFlowRatio.toDouble())
    .put("bottomSolidInfillFlowRatio", bottomSolidInfillFlowRatio.toDouble())
    .put("overhang1_4Speed", overhang1_4Speed)
    .put("overhang2_4Speed", overhang2_4Speed)
    .put("overhang3_4Speed", overhang3_4Speed)
    .put("overhang4_4Speed", overhang4_4Speed)
    .put("enableOverhangSpeed", enableOverhangSpeed)
    .put("slowdownForCurledPerimeters", slowdownForCurledPerimeters)
    .put("overhangFlowRatio", overhangFlowRatio.toDouble())
    .put("dontSlowDownOuterWall", dontSlowDownOuterWall)
    .put("bridgeAcceleration", bridgeAcceleration)
    .put("bridgeSpeedMmPerSec", bridgeSpeedMmPerSec.toDouble())
    .put("smallPerimeterSpeedMmPerSec", smallPerimeterSpeedMmPerSec.toDouble())
    .put("smallPerimeterThresholdMm", smallPerimeterThresholdMm.toDouble())
    .put("bridgeAngleDegrees", bridgeAngleDegrees.toDouble())
    .put("bridgeDensityPercent", bridgeDensityPercent)
    .put("bridgeFlowRatio", bridgeFlowRatio.toDouble())
    .put("bridgeNoSupport", bridgeNoSupport)
    .put("thickInternalBridges", thickInternalBridges)
    .put("extraBridgeLayer", extraBridgeLayer.configValue)
    .put("internalBridgeAngleDegrees", internalBridgeAngleDegrees.toDouble())
    .put("internalBridgeDensityPercent", internalBridgeDensityPercent)
    .put("internalBridgeFlowRatio", internalBridgeFlowRatio.toDouble())
    .put("internalBridgeSpeed", internalBridgeSpeed)
    .put("internalBridgeFanSpeed", internalBridgeFanSpeed)
    .put("internalBridgeSupportThickness", internalBridgeSupportThickness)
    .put("maxVolumetricExtrusionRateSlope", maxVolumetricExtrusionRateSlope.toDouble())
    .put("maxVolumetricExtrusionRateSlopeSegmentLengthMm", maxVolumetricExtrusionRateSlopeSegmentLengthMm.toDouble())
    .put("extrusionRateSmoothingExternalPerimeterOnly", extrusionRateSmoothingExternalPerimeterOnly)
    .put("sparseInfillSpeedMmPerSec", sparseInfillSpeedMmPerSec.toDouble())
    .put("internalSolidInfillSpeedMmPerSec", internalSolidInfillSpeedMmPerSec.toDouble())
    .put("gapInfillSpeedMmPerSec", gapInfillSpeedMmPerSec.toDouble())
    .put("adaptiveLayerHeight", adaptiveLayerHeight)
    .put("topShellLayers", topShellLayers)
    .put("bottomShellLayers", bottomShellLayers)
    .put("topShellThicknessMm", topShellThicknessMm.toDouble())
    .put("bottomShellThicknessMm", bottomShellThicknessMm.toDouble())
    .put("topSurfaceDensityPercent", topSurfaceDensityPercent)
    .put("bottomSurfaceDensityPercent", bottomSurfaceDensityPercent)
    .put("seamPosition", seamPosition.configValue)
    .put("preciseOuterWall", preciseOuterWall)
    .put("preciseZHeight", preciseZHeight)
    .put("onlyOneWallFirstLayer", onlyOneWallFirstLayer)
    .put("onlyOneWallTopSurfaces", onlyOneWallTopSurfaces)
    .put("printInfillFirst", printInfillFirst)
    .put("wallDirection", wallDirection.configValue)
    .put("printFlowRatio", printFlowRatio.toDouble())
    .put("topSurfacePattern", topSurfacePattern.configValue)
    .put("bottomSurfacePattern", bottomSurfacePattern.configValue)
    .put("internalSolidInfillPattern", internalSolidInfillPattern.configValue)
    .put("solidInfillDirectionDegrees", solidInfillDirectionDegrees.toDouble())
    .put("solidInfillRotateTemplate", solidInfillRotateTemplate)
    .put("lineWidth", lineWidth)
    .put("outerWallLineWidth", outerWallLineWidth)
    .put("innerWallLineWidth", innerWallLineWidth)
    .put("initialLayerLineWidth", initialLayerLineWidth)
    .put("initialLayerMinBeadWidthPercent", initialLayerMinBeadWidthPercent)
    .put("elefantFootCompensationLayers", elefantFootCompensationLayers)
    .put("topSurfaceLineWidth", topSurfaceLineWidth)
    .put("internalSolidInfillLineWidth", internalSolidInfillLineWidth)
    .put("minWidthTopSurface", minWidthTopSurface)
    .put("sparseInfillLineWidth", sparseInfillLineWidth)
    .put("infillDirectionDegrees", infillDirectionDegrees.toDouble())
    .put("sparseInfillRotateTemplate", sparseInfillRotateTemplate)
    .put("alignInfillDirectionToModel", alignInfillDirectionToModel)
    .put("infillWallOverlapPercent", infillWallOverlapPercent)
    .put("topBottomInfillWallOverlapPercent", topBottomInfillWallOverlapPercent)
    .put("infillAnchor", infillAnchor)
    .put("infillAnchorMax", infillAnchorMax)
    .put("infillCombination", infillCombination)
    .put("infillCombinationMaxLayerHeight", infillCombinationMaxLayerHeight)
    .put("minimumSparseInfillAreaMm2", minimumSparseInfillAreaMm2.toDouble())
    .put("detectThinWall", detectThinWall)
    .put("detectOverhangWall", detectOverhangWall)
    .put("makeOverhangPrintable", makeOverhangPrintable)
    .put("makeOverhangPrintableAngleDegrees", makeOverhangPrintableAngleDegrees.toDouble())
    .put("makeOverhangPrintableHoleSizeMm2", makeOverhangPrintableHoleSizeMm2.toDouble())
    .put("overhangReverse", overhangReverse)
    .put("overhangReverseInternalOnly", overhangReverseInternalOnly)
    .put("overhangReverseThreshold", overhangReverseThreshold)
    .put("thickBridges", thickBridges)
    .put("sliceClosingRadiusMm", sliceClosingRadiusMm.toDouble())
    .put("resolutionMm", resolutionMm.toDouble())
    .put("interfaceShells", interfaceShells)
    .put("dontFilterInternalBridges", dontFilterInternalBridges.configValue)
    .put("detectNarrowInternalSolidInfill", detectNarrowInternalSolidInfill)
    .put("elefantFootCompensationMm", elefantFootCompensationMm.toDouble())
    .put("applyTopSurfaceCompensation", applyTopSurfaceCompensation)
    .put("ensureVerticalShellThickness", ensureVerticalShellThickness.configValue)
    .put("wallGenerator", wallGenerator.configValue)
    .put("wallTransitionAngleDegrees", wallTransitionAngleDegrees.toDouble())
    .put("wallTransitionFilterDeviationPercent", wallTransitionFilterDeviationPercent)
    .put("wallTransitionLengthPercent", wallTransitionLengthPercent)
    .put("wallDistributionCount", wallDistributionCount)
    .put("minBeadWidthPercent", minBeadWidthPercent)
    .put("minFeatureSizePercent", minFeatureSizePercent)
    .put("minLengthFactorMm", minLengthFactorMm.toDouble())
    .put("wallInfillOrder", wallInfillOrder.configValue)
    .put("extraPerimetersOnOverhangs", extraPerimetersOnOverhangs)
    .put("xyHoleCompensationMm", xyHoleCompensationMm.toDouble())
    .put("xyContourCompensationMm", xyContourCompensationMm.toDouble())
    .put("wallCount", wallCount)
    .put("infillPercent", infillPercent)
    .put("sparseInfillPattern", sparseInfillPattern.configValue)
    .put("skinInfillDensity", skinInfillDensity)
    .put("skeletonInfillDensity", skeletonInfillDensity)
    .put("infillLockDepthMm", infillLockDepthMm.toDouble())
    .put("skinInfillDepthMm", skinInfillDepthMm.toDouble())
    .put("skinInfillLineWidth", skinInfillLineWidth)
    .put("skeletonInfillLineWidth", skeletonInfillLineWidth)
    .put("symmetricInfillYAxis", symmetricInfillYAxis)
    .put("infillShiftStepMm", infillShiftStepMm.toDouble())
    .put("infillOverhangAngleDegrees", infillOverhangAngleDegrees.toDouble())
    .put("sparseInfillFilament", sparseInfillFilament)
    .put("sparseInfillFlowRatio", sparseInfillFlowRatio.toDouble())
    .put("internalSolidInfillFlowRatio", internalSolidInfillFlowRatio.toDouble())
    .put("setOtherFlowRatios", setOtherFlowRatios)
    .put("smallAreaInfillFlowCompensation", smallAreaInfillFlowCompensation)
    .put("lateralLatticeAngle1Degrees", lateralLatticeAngle1Degrees.toDouble())
    .put("lateralLatticeAngle2Degrees", lateralLatticeAngle2Degrees.toDouble())
    .put("fillMultiline", fillMultiline)
    .put("gapFillTarget", gapFillTarget)
    .put("filterOutGapFillMm", filterOutGapFillMm.toDouble())
    .put("gapFillFlowRatio", gapFillFlowRatio.toDouble())
    .put("enableSupport", enableSupport)
    .put("supportType", supportType.configValue)
    .put("supportStyle", supportStyle.configValue)
    .put("supportThresholdAngleDegrees", supportThresholdAngleDegrees)
    .put("supportThresholdOverlap", supportThresholdOverlap)
    .put("supportBuildplateOnly", supportBuildplateOnly)
    .put("supportCriticalRegionsOnly", supportCriticalRegionsOnly)
    .put("supportRemoveSmallOverhang", supportRemoveSmallOverhang)
    .put("raftFirstLayerDensityPercent", raftFirstLayerDensityPercent)
    .put("raftFirstLayerExpansionMm", raftFirstLayerExpansionMm.toDouble())
    .put("raftLayers", raftLayers)
    .put("raftContactDistanceMm", raftContactDistanceMm.toDouble())
    .put("raftExpansionMm", raftExpansionMm.toDouble())
    .put("supportFilament", supportFilament)
    .put("supportInterfaceFilament", supportInterfaceFilament)
    .put("supportInterfaceNotForBody", supportInterfaceNotForBody)
    .put("supportTopZDistanceMm", supportTopZDistanceMm.toDouble())
    .put("supportBottomZDistanceMm", supportBottomZDistanceMm.toDouble())
    .put("supportInterfaceTopLayers", supportInterfaceTopLayers)
    .put("supportInterfaceBottomLayers", supportInterfaceBottomLayers)
    .put("supportInterfaceSpacingMm", supportInterfaceSpacingMm.toDouble())
    .put("supportBottomInterfaceSpacingMm", supportBottomInterfaceSpacingMm.toDouble())
    .put("supportInterfaceSpeedMmPerSec", supportInterfaceSpeedMmPerSec.toDouble())
    .put("supportInterfaceFlowRatio", supportInterfaceFlowRatio.toDouble())
    .put("supportMaterialInterfaceFanSpeed", supportMaterialInterfaceFanSpeed)
    .put("supportInterfacePattern", supportInterfacePattern.configValue)
    .put("supportInterfaceLoopPattern", supportInterfaceLoopPattern)
    .put("supportLineWidth", supportLineWidth)
    .put("supportBasePattern", supportBasePattern.configValue)
    .put("supportBasePatternSpacingMm", supportBasePatternSpacingMm.toDouble())
    .put("supportAngleDegrees", supportAngleDegrees.toDouble())
    .put("supportSpeedMmPerSec", supportSpeedMmPerSec.toDouble())
    .put("supportFlowRatio", supportFlowRatio.toDouble())
    .put("supportObjectElevationMm", supportObjectElevationMm.toDouble())
    .put("supportObjectFirstLayerGapMm", supportObjectFirstLayerGapMm.toDouble())
    .put("supportMaxBridgeLengthMm", supportMaxBridgeLengthMm.toDouble())
    .put("supportIroning", supportIroning)
    .put("supportIroningPattern", supportIroningPattern.configValue)
    .put("supportIroningFlowPercent", supportIroningFlowPercent.toDouble())
    .put("supportIroningSpacingMm", supportIroningSpacingMm.toDouble())
    .put("supportExpansionMm", supportExpansionMm.toDouble())
    .put("supportObjectXyDistanceMm", supportObjectXyDistanceMm.toDouble())
    .put("independentSupportLayerHeight", independentSupportLayerHeight)
    .put("treeSupportBranchAngleDegrees", treeSupportBranchAngleDegrees.toDouble())
    .put("treeSupportBranchDiameterMm", treeSupportBranchDiameterMm.toDouble())
    .put("treeSupportWallCount", treeSupportWallCount)
    .put("enablePrimeTower", enablePrimeTower)
    .put("primeTowerWidthMm", primeTowerWidthMm.toDouble())
    .put("wipeTowerXmm", wipeTowerXmm.toDouble())
    .put("wipeTowerYmm", wipeTowerYmm.toDouble())
    .put("primeTowerSkipPoints", primeTowerSkipPoints)
    .put("primeVolumeMm3", primeVolumeMm3.toDouble())
    .put("primeTowerBrimWidthMm", primeTowerBrimWidthMm.toDouble())
    .put("primeTowerInfillGapPercent", primeTowerInfillGapPercent)
    .put("wipeTowerRotationAngleDegrees", wipeTowerRotationAngleDegrees.toDouble())
    .put("wipeTowerBridgingMm", wipeTowerBridgingMm.toDouble())
    .put("wipeTowerExtraSpacingPercent", wipeTowerExtraSpacingPercent)
    .put("wipeTowerExtraFlowPercent", wipeTowerExtraFlowPercent)
    .put("wipeTowerMaxPurgeSpeedMmPerSec", wipeTowerMaxPurgeSpeedMmPerSec.toDouble())
    .put("wipeTowerWallType", wipeTowerWallType.configValue)
    .put("wipeTowerConeAngleDegrees", wipeTowerConeAngleDegrees.toDouble())
    .put("wipeTowerExtraRibLengthMm", wipeTowerExtraRibLengthMm.toDouble())
    .put("wipeTowerRibWidthMm", wipeTowerRibWidthMm.toDouble())
    .put("wipeTowerFilletWall", wipeTowerFilletWall)
    .put("enableTowerInterfaceFeatures", enableTowerInterfaceFeatures)
    .put("enableTowerInterfaceCooldownDuringTower", enableTowerInterfaceCooldownDuringTower)
    .put("singleExtruderMultiMaterialPriming", singleExtruderMultiMaterialPriming)
    .put("standbyTemperatureDeltaC", standbyTemperatureDeltaC)
    .put("wipeTowerNoSparseLayers", wipeTowerNoSparseLayers)
    .put("flushIntoInfill", flushIntoInfill)
    .put("flushIntoObjects", flushIntoObjects)
    .put("flushIntoSupport", flushIntoSupport)
    .put("staggeredInnerSeams", staggeredInnerSeams)
    .put("seamGap", seamGap)
    .put("seamScarfType", seamScarfType.configValue)
    .put("seamScarfConditional", seamScarfConditional)
    .put("scarfAngleThresholdDegrees", scarfAngleThresholdDegrees)
    .put("scarfOverhangThresholdPercent", scarfOverhangThresholdPercent)
    .put("scarfJointSpeed", scarfJointSpeed)
    .put("scarfJointFlowRatio", scarfJointFlowRatio.toDouble())
    .put("seamScarfStartHeight", seamScarfStartHeight)
    .put("seamScarfEntireLoop", seamScarfEntireLoop)
    .put("seamScarfMinLengthMm", seamScarfMinLengthMm.toDouble())
    .put("seamScarfSteps", seamScarfSteps)
    .put("seamScarfInnerWalls", seamScarfInnerWalls)
    .put("roleBasedWipeSpeed", roleBasedWipeSpeed)
    .put("wipeSpeed", wipeSpeed)
    .put("wipeOnLoops", wipeOnLoops)
    .put("wipeBeforeExternalLoop", wipeBeforeExternalLoop)
    .put("hasScarfJointSeam", hasScarfJointSeam)
    .put("counterboreHoleBridging", counterboreHoleBridging.configValue)
    .put("wallSequence", wallSequence.configValue)
    .put("enableArcFitting", enableArcFitting)
    .put("reduceCrossingWall", reduceCrossingWall)
    .put("maxTravelDetourDistance", maxTravelDetourDistance)
    .put("holeToPolyhole", holeToPolyhole)
    .put("holeToPolyholeThreshold", holeToPolyholeThreshold)
    .put("holeToPolyholeTwisted", holeToPolyholeTwisted)
    .put("alternateExtraWall", alternateExtraWall)
    .put("extraSolidInfills", extraSolidInfills)
    .put("skirtType", skirtType.configValue)
    .put("minSkirtLengthMm", minSkirtLengthMm.toDouble())
    .put("skirtDistanceMm", skirtDistanceMm.toDouble())
    .put("skirtStartAngleDegrees", skirtStartAngleDegrees.toDouble())
    .put("skirtSpeedMmPerSec", skirtSpeedMmPerSec.toDouble())
    .put("skirtHeightLayers", skirtHeightLayers)
    .put("draftShield", draftShield.configValue)
    .put("singleLoopDraftShield", singleLoopDraftShield)
    .put("brimType", brimType.configValue)
    .put("brimObjectGapMm", brimObjectGapMm.toDouble())
    .put("brimUseEfcOutline", brimUseEfcOutline)
    .put("combineBrims", combineBrims)
    .put("brimEars", brimEars)
    .put("brimEarsDetectionLengthMm", brimEarsDetectionLengthMm.toDouble())
    .put("brimEarsMaxAngleDegrees", brimEarsMaxAngleDegrees.toDouble())
    .put("slicingMode", slicingMode.configValue)
    .put("printSequence", printSequence.configValue)
    .put("printOrder", printOrder.configValue)
    .put("spiralMode", spiralMode)
    .put("spiralModeSmooth", spiralModeSmooth)
    .put("spiralModeMaxXySmoothing", spiralModeMaxXySmoothing)
    .put("spiralStartingFlowRatio", spiralStartingFlowRatio.toDouble())
    .put("spiralFinishingFlowRatio", spiralFinishingFlowRatio.toDouble())
    .put("timelapseType", timelapseType.configValue)
    .put("enableWrappingDetection", enableWrappingDetection)
    .put("reduceInfillRetraction", reduceInfillRetraction)
    .put("gcodeAddLineNumber", gcodeAddLineNumber)
    .put("gcodeComments", gcodeComments)
    .put("gcodeLabelObjects", gcodeLabelObjects)
    .put("excludeObject", excludeObject)
    .put("filamentMapMode", filamentMapMode.configValue)
    .put("allowMixTemp", allowMixTemp)
    .put("allowMulticolorOnePlate", allowMulticolorOnePlate)
    .put("filenameFormat", filenameFormat)
    .put("postProcessScripts", postProcessScripts)
    .put("notes", notes)
    .put("profileSource", profileSource)
    .put("printerProfileId", printerProfileId)
    .put("printerVariantKey", printerVariantKey)
    .put("nozzleDiameterMm", nozzleDiameterMm.toDouble())
    .put("orcaFamily", orcaFamily)
	    .put("orcaProcessPath", orcaProcessPath)
	    .put("orcaRawProcessJson", orcaRawProcessJson)
	    .put("orcaResolvedProcessJson", orcaResolvedProcessJson)
	    .put("orcaProcessOverridesJson", orcaProcessOverridesJson)
	    .put("orcaResolvedSourceChain", JSONArray().apply {
        orcaResolvedSourceChain.forEach { put(it) }
    })
    .put("skirts", skirts)
    .put("brimWidthMm", brimWidthMm.toDouble())
    .put("fuzzySkin", fuzzySkin.configValue)
    .put("fuzzySkinThicknessMm", fuzzySkinThicknessMm.toDouble())
    .put("fuzzySkinPointDistanceMm", fuzzySkinPointDistanceMm.toDouble())
    .put("fuzzySkinFirstLayer", fuzzySkinFirstLayer)
    .put("fuzzySkinMode", fuzzySkinMode.configValue)
    .put("fuzzySkinNoiseType", fuzzySkinNoiseType.configValue)
    .put("fuzzySkinScaleMm", fuzzySkinScaleMm.toDouble())
    .put("fuzzySkinOctaves", fuzzySkinOctaves)
    .put("fuzzySkinPersistence", fuzzySkinPersistence.toDouble())
    .put("ironingType", ironingType.configValue)
    .put("ironingPattern", ironingPattern.configValue)
    .put("ironingFlowPercent", ironingFlowPercent.toDouble())
    .put("ironingSpacingMm", ironingSpacingMm.toDouble())
    .put("ironingInsetMm", ironingInsetMm.toDouble())
    .put("ironingAngleDegrees", ironingAngleDegrees.toDouble())
    .put("ironingAngleFixed", ironingAngleFixed)
    .put("ironingSpeedMmPerSec", ironingSpeedMmPerSec.toDouble())

internal fun JSONObject.toProcessProfile(): ProcessProfile {
    val legacyPrintSpeedMmPerSec = optInt("printSpeedMmPerSec", 60)
    return newProcessProfileUnchecked(
        0 to getString("id"),
        1 to getString("name"),
        2 to optString("subtitle", ""),
        3 to optBoolean("builtIn", false),
        4 to optDouble("firstLayerHeightMm", optDouble("layerHeightMm", 0.2)).toFloat(),
        5 to optDouble("layerHeightMm", 0.2).toFloat(),
        6 to optDouble("firstLayerPrintSpeedMmPerSec", derivedFirstLayerPrintSpeedMmPerSec(legacyPrintSpeedMmPerSec).toDouble()).toFloat(),
        7 to optDouble("firstLayerInfillSpeedMmPerSec", derivedFirstLayerInfillSpeedMmPerSec(legacyPrintSpeedMmPerSec).toDouble()).toFloat(),
        8 to optInt("firstLayerTravelSpeedPercent", DEFAULT_FIRST_LAYER_TRAVEL_SPEED_PERCENT),
        9 to optInt("slowDownLayers", DEFAULT_SLOW_DOWN_LAYERS),
        10 to optDouble("initialLayerAccelerationMmPerSec2", DEFAULT_INITIAL_LAYER_ACCELERATION_MM_PER_SEC2.toDouble()).toFloat(),
        11 to optDouble("initialLayerJerkMmPerSec", DEFAULT_INITIAL_LAYER_JERK_MM_PER_SEC.toDouble()).toFloat(),
        12 to optDouble("firstLayerFlowRatio", DEFAULT_FIRST_LAYER_FLOW_RATIO.toDouble()).toFloat(),
        13 to optString("printExtruderId", "1"),
        14 to optString("printExtruderVariant", "Direct Drive Standard"),
        15 to optDouble("outerWallSpeedMmPerSec", derivedOuterWallSpeedMmPerSec(legacyPrintSpeedMmPerSec).toDouble()).toFloat(),
        16 to optDouble("innerWallSpeedMmPerSec", derivedInnerWallSpeedMmPerSec(legacyPrintSpeedMmPerSec).toDouble()).toFloat(),
        17 to optDouble("topSurfaceSpeedMmPerSec", derivedTopSurfaceSpeedMmPerSec(legacyPrintSpeedMmPerSec).toDouble()).toFloat(),
        18 to optDouble("travelSpeedMmPerSec", derivedTravelSpeedMmPerSec(legacyPrintSpeedMmPerSec).toDouble()).toFloat(),
        19 to optDouble("defaultAccelerationMmPerSec2", DEFAULT_DEFAULT_ACCELERATION_MM_PER_SEC2.toDouble()).toFloat(),
        20 to optDouble("outerWallAccelerationMmPerSec2", DEFAULT_OUTER_WALL_ACCELERATION_MM_PER_SEC2.toDouble()).toFloat(),
        21 to optDouble("innerWallAccelerationMmPerSec2", DEFAULT_INNER_WALL_ACCELERATION_MM_PER_SEC2.toDouble()).toFloat(),
        22 to optDouble("topSurfaceAccelerationMmPerSec2", DEFAULT_TOP_SURFACE_ACCELERATION_MM_PER_SEC2.toDouble()).toFloat(),
        23 to optDouble("sparseInfillAccelerationMmPerSec2", DEFAULT_SPARSE_INFILL_ACCELERATION_MM_PER_SEC2.toDouble()).toFloat(),
        24 to optString("internalSolidInfillAcceleration", "100%"),
        25 to optDouble("travelAccelerationMmPerSec2", 10000.0).toFloat(),
        26 to optBoolean("accelToDecelEnable", false),
        27 to optInt("accelToDecelFactorPercent", 50),
        28 to optDouble("defaultJunctionDeviationMm", 0.0).toFloat(),
        29 to optDouble("defaultJerkMmPerSec", 0.0).toFloat(),
        30 to optDouble("innerWallJerkMmPerSec", DEFAULT_INNER_WALL_JERK_MM_PER_SEC.toDouble()).toFloat(),
        31 to optDouble("infillJerkMmPerSec", 9.0).toFloat(),
        32 to optDouble("topSurfaceJerkMmPerSec", 9.0).toFloat(),
        33 to optDouble("travelJerkMmPerSec", 12.0).toFloat(),
        34 to optDouble("innerWallFlowRatio", DEFAULT_INNER_WALL_FLOW_RATIO.toDouble()).toFloat(),
        35 to optDouble("outerWallJerkMmPerSec", DEFAULT_OUTER_WALL_JERK_MM_PER_SEC.toDouble()).toFloat(),
        36 to optDouble("outerWallFlowRatio", DEFAULT_OUTER_WALL_FLOW_RATIO.toDouble()).toFloat(),
        37 to optDouble("topSolidInfillFlowRatio", DEFAULT_TOP_SOLID_INFILL_FLOW_RATIO.toDouble()).toFloat(),
        38 to optDouble("bottomSolidInfillFlowRatio", DEFAULT_BOTTOM_SOLID_INFILL_FLOW_RATIO.toDouble()).toFloat(),
        39 to optString("overhang1_4Speed", DEFAULT_OVERHANG_1_4_SPEED),
        40 to optString("overhang2_4Speed", DEFAULT_OVERHANG_2_4_SPEED),
        41 to optString("overhang3_4Speed", DEFAULT_OVERHANG_3_4_SPEED),
        42 to optString("overhang4_4Speed", DEFAULT_OVERHANG_4_4_SPEED),
        67 to optDouble("sparseInfillSpeedMmPerSec", derivedSparseInfillSpeedMmPerSec(legacyPrintSpeedMmPerSec).toDouble()).toFloat(),
        68 to optDouble("internalSolidInfillSpeedMmPerSec", derivedInternalSolidInfillSpeedMmPerSec(legacyPrintSpeedMmPerSec).toDouble()).toFloat(),
        69 to optDouble("gapInfillSpeedMmPerSec", derivedGapInfillSpeedMmPerSec(legacyPrintSpeedMmPerSec).toDouble()).toFloat(),
        71 to optInt("topShellLayers", 6),
        72 to optInt("bottomShellLayers", 6),
        73 to optDouble("topShellThicknessMm", 0.6).toFloat(),
        74 to optDouble("bottomShellThicknessMm", 0.6).toFloat(),
        75 to optInt("topSurfaceDensityPercent", 100),
        76 to optInt("bottomSurfaceDensityPercent", 100),
        77 to ProcessSeamPosition.fromConfigValue(optString("seamPosition", ProcessSeamPosition.Aligned.configValue)),
        55 to ProcessQualitySurfaceDetails(
            wallDirection = WallDirection.fromConfigValue(optString("wallDirection", WallDirection.Auto.configValue))
        ),
        84 to optBoolean("preciseOuterWall", true),
        85 to optBoolean("onlyOneWallTopSurfaces", true),
        86 to TopSurfacePattern.fromConfigValue(optString("topSurfacePattern", TopSurfacePattern.MonotonicLine.configValue)),
        87 to BottomSurfacePattern.fromConfigValue(optString("bottomSurfacePattern", BottomSurfacePattern.MonotonicLine.configValue)),
        88 to InternalSolidInfillPattern.fromConfigValue(optString("internalSolidInfillPattern", InternalSolidInfillPattern.MonotonicLine.configValue)),
        91 to optString("lineWidth", "0"),
        92 to optString("outerWallLineWidth", "0"),
        93 to optString("innerWallLineWidth", "0"),
        94 to optString("initialLayerLineWidth", "0"),
        96 to optString("topSurfaceLineWidth", "0"),
        97 to optString("internalSolidInfillLineWidth", "0"),
        99 to optString("sparseInfillLineWidth", "0"),
        111 to optDouble("elefantFootCompensationMm", 0.0).toFloat(),
        115 to WallGenerator.fromConfigValue(optString("wallGenerator", WallGenerator.Classic.configValue)),
        124 to optInt("wallCount", 2),
        125 to optInt("infillPercent", 15),
        126 to SparseInfillPattern.fromConfigValue(optString("sparseInfillPattern", SparseInfillPattern.Grid.configValue)),
        268 to ProcessStrengthInfillDetails(
            filterOutGapFillMm = optDouble("filterOutGapFillMm", 0.5).toFloat()
        ),
        133 to optBoolean("enableSupport", DEFAULT_ENABLE_SUPPORT),
        134 to SupportType.fromConfigValue(optString("supportType", SupportType.NormalAuto.configValue)),
        135 to SupportStyle.fromConfigValue(optString("supportStyle", SupportStyle.Default.configValue)),
        136 to optInt("supportThresholdAngleDegrees", DEFAULT_SUPPORT_THRESHOLD_ANGLE_DEGREES),
        138 to optBoolean("supportBuildplateOnly", DEFAULT_SUPPORT_BUILDPLATE_ONLY),
        188 to optBoolean("enablePrimeTower", DEFAULT_ENABLE_PRIME_TOWER),
        189 to optDouble("primeTowerWidthMm", DEFAULT_PRIME_TOWER_WIDTH_MM.toDouble()).toFloat(),
        207 to ProcessPrimeTowerDetails(
            wipeTowerXmm = optDouble("wipeTowerXmm", 15.0).toFloat(),
            wipeTowerYmm = optDouble("wipeTowerYmm", 220.0).toFloat(),
            primeTowerSkipPoints = optBoolean("primeTowerSkipPoints", true),
            primeVolumeMm3 = optDouble("primeVolumeMm3", 45.0).toFloat(),
            primeTowerBrimWidthMm = optDouble("primeTowerBrimWidthMm", 3.0).toFloat(),
            primeTowerInfillGapPercent = optInt("primeTowerInfillGapPercent", 150),
            wipeTowerRotationAngleDegrees = optDouble("wipeTowerRotationAngleDegrees", 0.0).toFloat(),
            wipeTowerBridgingMm = optDouble("wipeTowerBridgingMm", 10.0).toFloat(),
            wipeTowerExtraSpacingPercent = optInt("wipeTowerExtraSpacingPercent", 100),
            wipeTowerExtraFlowPercent = optInt("wipeTowerExtraFlowPercent", 100),
            wipeTowerMaxPurgeSpeedMmPerSec = optDouble("wipeTowerMaxPurgeSpeedMmPerSec", 90.0).toFloat(),
            wipeTowerWallType = WipeTowerWallType.fromConfigValue(optString("wipeTowerWallType", WipeTowerWallType.Rectangle.configValue)),
            wipeTowerConeAngleDegrees = optDouble("wipeTowerConeAngleDegrees", 30.0).toFloat(),
            wipeTowerExtraRibLengthMm = optDouble("wipeTowerExtraRibLengthMm", 0.0).toFloat(),
            wipeTowerRibWidthMm = optDouble("wipeTowerRibWidthMm", 8.0).toFloat(),
            wipeTowerFilletWall = optBoolean("wipeTowerFilletWall", true)
        ),
        194 to optInt("standbyTemperatureDeltaC", DEFAULT_STANDBY_TEMPERATURE_DELTA_C),
        195 to optBoolean("wipeTowerNoSparseLayers", DEFAULT_WIPE_TOWER_NO_SPARSE_LAYERS),
        199 to optInt("skirts", 2),
        200 to optDouble("brimWidthMm", 0.0).toFloat(),
        258 to optString("profileSource", if (optBoolean("builtIn", false)) "builtin" else "custom"),
        259 to optString("printerProfileId", ""),
        260 to optString("printerVariantKey", ""),
        261 to optDouble("nozzleDiameterMm", 0.4).toFloat(),
        262 to optString("orcaFamily", ""),
        263 to optString("orcaProcessPath", ""),
	        264 to optString("orcaRawProcessJson", ""),
	        265 to optString("orcaResolvedProcessJson", ""),
	        266 to optJSONArray("orcaResolvedSourceChain")?.let { array ->
	            List(array.length()) { index -> array.optString(index) }
	        }.orEmpty(),
        267 to optString("orcaProcessOverridesJson", ""),
        269 to ProcessGcodeOutputDetails(
            gcodeAddLineNumber = optBoolean("gcodeAddLineNumber", false),
            gcodeComments = optBoolean("gcodeComments", false),
            gcodeLabelObjects = optBoolean("gcodeLabelObjects", false),
            excludeObject = optBoolean("excludeObject", false)
        )
	    )
	}

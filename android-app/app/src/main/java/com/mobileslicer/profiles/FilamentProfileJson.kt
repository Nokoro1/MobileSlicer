package com.mobileslicer.profiles

import org.json.JSONArray
import org.json.JSONObject

internal fun FilamentProfile.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("subtitle", subtitle)
    .put("builtIn", builtIn)
    .put("materialType", materialType)
    .put("vendor", vendor)
    .put("soluble", soluble)
    .put("supportMaterial", supportMaterial)
    .put("filamentExtruderVariant", filamentExtruderVariant)
    .put("filamentSelfIndex", filamentSelfIndex)
    .put("filamentChangeLengthMm", filamentChangeLengthMm.toDouble())
    .put("requiredNozzleHrc", requiredNozzleHrc)
    .put("defaultFilamentColor", defaultFilamentColor)
    .put("diameterMm", diameterMm.toDouble())
    .put("adhesivenessCategory", adhesivenessCategory)
    .put("densityGPerCm3", densityGPerCm3.toDouble())
    .put("shrinkageXyPercent", shrinkageXyPercent.toDouble())
    .put("shrinkageZPercent", shrinkageZPercent.toDouble())
    .put("costPerKg", costPerKg.toDouble())
    .put("softeningTemperatureC", softeningTemperatureC)
    .put("idleTemperatureC", idleTemperatureC)
    .put("nozzleTemperatureRangeLowC", nozzleTemperatureRangeLowC)
    .put("nozzleTemperatureRangeHighC", nozzleTemperatureRangeHighC)
    .put("flowRatio", flowRatio.toDouble())
    .put("retractionLengthMm", retractionLengthMm?.toDouble() ?: JSONObject.NULL)
    .put("retractionSpeedMmPerSec", retractionSpeedMmPerSec?.toDouble() ?: JSONObject.NULL)
    .put("deretractionSpeedMmPerSec", deretractionSpeedMmPerSec?.toDouble() ?: JSONObject.NULL)
    .put("pressureAdvanceEnabled", pressureAdvanceEnabled)
    .put("pressureAdvance", pressureAdvance.toDouble())
    .put("pelletFlowCoefficient", pelletFlowCoefficient.toDouble())
    .put("adaptivePressureAdvanceEnabled", adaptivePressureAdvanceEnabled)
    .put("adaptivePressureAdvanceOverhangsEnabled", adaptivePressureAdvanceOverhangsEnabled)
    .put("adaptivePressureAdvanceBridges", adaptivePressureAdvanceBridges.toDouble())
    .put("adaptivePressureAdvanceModel", adaptivePressureAdvanceModel)
    .put("maxVolumetricSpeedMm3PerSec", maxVolumetricSpeedMm3PerSec.toDouble())
    .put("adaptiveVolumetricSpeedEnabled", adaptiveVolumetricSpeedEnabled)
    .put("volumetricSpeedCoefficients", volumetricSpeedCoefficients)
    .put("nozzleTemperatureInitialLayerC", nozzleTemperatureInitialLayerC)
    .put("nozzleTemperatureC", nozzleTemperatureC)
    .put("chamberTemperatureC", chamberTemperatureC)
    .put("activateChamberTemperatureControl", activateChamberTemperatureControl)
    .put("supertackPlateTemperatureInitialLayerC", supertackPlateTemperatureInitialLayerC)
    .put("supertackPlateTemperatureC", supertackPlateTemperatureC)
    .put("coolPlateTemperatureInitialLayerC", coolPlateTemperatureInitialLayerC)
    .put("coolPlateTemperatureC", coolPlateTemperatureC)
    .put("texturedCoolPlateTemperatureInitialLayerC", texturedCoolPlateTemperatureInitialLayerC)
    .put("texturedCoolPlateTemperatureC", texturedCoolPlateTemperatureC)
    .put("engineeringPlateTemperatureInitialLayerC", engineeringPlateTemperatureInitialLayerC)
    .put("engineeringPlateTemperatureC", engineeringPlateTemperatureC)
    .put("bedTemperatureInitialLayerC", bedTemperatureInitialLayerC)
    .put("bedTemperatureC", bedTemperatureC)
    .put("texturedPlateTemperatureInitialLayerC", texturedPlateTemperatureInitialLayerC)
    .put("texturedPlateTemperatureC", texturedPlateTemperatureC)
    .put("minFanSpeedPercent", minFanSpeedPercent)
    .put("coolingPercent", coolingPercent)
    .put("noCoolingFirstLayers", noCoolingFirstLayers)
    .put("fullFanSpeedLayer", fullFanSpeedLayer)
    .put("fanCoolingLayerTimeSeconds", fanCoolingLayerTimeSeconds.toDouble())
    .put("slowDownLayerTimeSeconds", slowDownLayerTimeSeconds.toDouble())
    .put("reduceFanStopStartFrequency", reduceFanStopStartFrequency)
    .put("slowDownForLayerCooling", slowDownForLayerCooling)
    .put("dontSlowDownOuterWall", dontSlowDownOuterWall)
    .put("slowDownMinSpeedMmPerSec", slowDownMinSpeedMmPerSec.toDouble())
    .put("enableOverhangBridgeFan", enableOverhangBridgeFan)
    .put("overhangFanThreshold", overhangFanThreshold)
    .put("overhangFanSpeedPercent", overhangFanSpeedPercent)
    .put("internalBridgeFanSpeedPercent", internalBridgeFanSpeedPercent)
    .put("supportMaterialInterfaceFanSpeedPercent", supportMaterialInterfaceFanSpeedPercent)
    .put("ironingFanSpeedPercent", ironingFanSpeedPercent)
    .put("additionalCoolingFanSpeedPercent", additionalCoolingFanSpeedPercent)
    .put("activateAirFiltration", activateAirFiltration)
    .put("duringPrintExhaustFanSpeedPercent", duringPrintExhaustFanSpeedPercent)
    .put("completePrintExhaustFanSpeedPercent", completePrintExhaustFanSpeedPercent)
    .put("minimalPurgeOnWipeTowerMm3", minimalPurgeOnWipeTowerMm3.toDouble())
    .put("towerInterfacePreExtrusionDistanceMm", towerInterfacePreExtrusionDistanceMm.toDouble())
    .put("towerInterfacePreExtrusionLengthMm", towerInterfacePreExtrusionLengthMm.toDouble())
    .put("towerIroningAreaMm2", towerIroningAreaMm2.toDouble())
    .put("towerInterfacePurgeVolumeMm", towerInterfacePurgeVolumeMm.toDouble())
    .put("towerInterfacePrintTemperatureC", towerInterfacePrintTemperatureC)
    .put("longRetractionsWhenExtruderChange", longRetractionsWhenExtruderChange ?: JSONObject.NULL)
    .put("retractionDistanceWhenExtruderChangeMm", retractionDistanceWhenExtruderChangeMm?.toDouble() ?: JSONObject.NULL)
    .put("loadingSpeedStartMmPerSec", loadingSpeedStartMmPerSec.toDouble())
    .put("loadingSpeedMmPerSec", loadingSpeedMmPerSec.toDouble())
    .put("unloadingSpeedStartMmPerSec", unloadingSpeedStartMmPerSec.toDouble())
    .put("unloadingSpeedMmPerSec", unloadingSpeedMmPerSec.toDouble())
    .put("toolchangeDelaySeconds", toolchangeDelaySeconds.toDouble())
    .put("coolingMoves", coolingMoves)
    .put("coolingInitialSpeedMmPerSec", coolingInitialSpeedMmPerSec.toDouble())
    .put("coolingFinalSpeedMmPerSec", coolingFinalSpeedMmPerSec.toDouble())
    .put("stampingLoadingSpeedMmPerSec", stampingLoadingSpeedMmPerSec.toDouble())
    .put("stampingDistanceMm", stampingDistanceMm.toDouble())
    .put("rammingParameters", rammingParameters)
    .put("multitoolRamming", multitoolRamming)
    .put("multitoolRammingVolumeMm3", multitoolRammingVolumeMm3.toDouble())
    .put("multitoolRammingFlowMm3PerSec", multitoolRammingFlowMm3PerSec.toDouble())
    .put("compatiblePrinters", compatiblePrinters)
    .put("compatiblePrintersCondition", compatiblePrintersCondition)
    .put("compatiblePrints", compatiblePrints)
    .put("compatiblePrintsCondition", compatiblePrintsCondition)
    .put("filamentNotes", filamentNotes)
    .put("filamentStartGcode", filamentStartGcode)
    .put("filamentEndGcode", filamentEndGcode)
    .put("printerProfileId", printerProfileId)
    .put("profileSource", profileSource)
    .put("orcaFamily", orcaFamily)
	    .put("orcaFilamentPath", orcaFilamentPath)
	    .put("orcaRawFilamentJson", orcaRawFilamentJson)
	    .put("orcaResolvedFilamentJson", orcaResolvedFilamentJson)
	    .put("orcaFilamentOverridesJson", orcaFilamentOverridesJson)
	    .put("orcaResolvedSourceChain", JSONArray().apply { orcaResolvedSourceChain.forEach { put(it) } })
    .put("zHopMm", zHopMm?.toDouble() ?: JSONObject.NULL)
    .put("zHopType", zHopType?.configValue ?: JSONObject.NULL)
    .put("retractLiftAboveMm", retractLiftAboveMm?.toDouble() ?: JSONObject.NULL)
    .put("retractLiftBelowMm", retractLiftBelowMm?.toDouble() ?: JSONObject.NULL)
    .put("retractLiftEnforce", retractLiftEnforce?.configValue ?: JSONObject.NULL)
    .put("retractRestartExtraMm", retractRestartExtraMm?.toDouble() ?: JSONObject.NULL)
    .put("retractionMinimumTravelMm", retractionMinimumTravelMm?.toDouble() ?: JSONObject.NULL)
    .put("retractWhenChangingLayer", retractWhenChangingLayer ?: JSONObject.NULL)
    .put("wipe", wipe ?: JSONObject.NULL)
    .put("wipeDistanceMm", wipeDistanceMm?.toDouble() ?: JSONObject.NULL)
    .put("retractBeforeWipePercent", retractBeforeWipePercent ?: JSONObject.NULL)
    .put("longRetractionsWhenCut", longRetractionsWhenCut ?: JSONObject.NULL)
    .put("retractionDistancesWhenCut", retractionDistancesWhenCut)
    .put("ironingFlowPercent", ironingFlowPercent?.toDouble() ?: JSONObject.NULL)
    .put("ironingSpacingMm", ironingSpacingMm?.toDouble() ?: JSONObject.NULL)
    .put("ironingInsetMm", ironingInsetMm?.toDouble() ?: JSONObject.NULL)
    .put("ironingSpeedMmPerSec", ironingSpeedMmPerSec?.toDouble() ?: JSONObject.NULL)

internal fun JSONObject.toFilamentProfile(): FilamentProfile = FilamentProfile(
    id = getString("id"),
    name = getString("name"),
    subtitle = optString("subtitle", ""),
    builtIn = optBoolean("builtIn", false),
    materialType = optString("materialType", "PLA"),
    vendor = optString("vendor", "(Undefined)"),
    soluble = optBoolean("soluble", false),
    supportMaterial = optBoolean("supportMaterial", false),
    filamentExtruderVariant = optString("filamentExtruderVariant", "Direct Drive Standard"),
    filamentSelfIndex = optString("filamentSelfIndex", "1"),
    filamentChangeLengthMm = optDouble("filamentChangeLengthMm", 10.0).toFloat(),
    requiredNozzleHrc = optInt("requiredNozzleHrc", 0),
    defaultFilamentColor = optString("defaultFilamentColor", ""),
    diameterMm = optDouble("diameterMm", 1.75).toFloat(),
    adhesivenessCategory = optInt("adhesivenessCategory", 0),
    densityGPerCm3 = optDouble(
        "densityGPerCm3",
        genericFilamentDensityForMaterial(optString("materialType", "PLA")).toDouble()
    ).toFloat(),
    shrinkageXyPercent = optDouble("shrinkageXyPercent", 100.0).toFloat(),
    shrinkageZPercent = optDouble("shrinkageZPercent", 100.0).toFloat(),
    costPerKg = optDouble("costPerKg", 0.0).toFloat(),
    softeningTemperatureC = optInt("softeningTemperatureC", 100),
    idleTemperatureC = optInt("idleTemperatureC", 0),
    nozzleTemperatureRangeLowC = optInt("nozzleTemperatureRangeLowC", 190),
    nozzleTemperatureRangeHighC = optInt("nozzleTemperatureRangeHighC", 240),
    flowRatio = optDouble("flowRatio", 1.0).toFloat(),
    retractionLengthMm = if (has("retractionLengthMm") && !isNull("retractionLengthMm")) optDouble("retractionLengthMm").toFloat() else null,
    zHopMm = if (has("zHopMm") && !isNull("zHopMm")) optDouble("zHopMm").toFloat() else null,
    zHopType = if (has("zHopType") && !isNull("zHopType")) ZHopType.fromConfigValue(optString("zHopType")) else null,
    retractLiftAboveMm = if (has("retractLiftAboveMm") && !isNull("retractLiftAboveMm")) optDouble("retractLiftAboveMm").toFloat() else null,
    retractLiftBelowMm = if (has("retractLiftBelowMm") && !isNull("retractLiftBelowMm")) optDouble("retractLiftBelowMm").toFloat() else null,
    retractLiftEnforce = if (has("retractLiftEnforce") && !isNull("retractLiftEnforce")) RetractLiftEnforce.fromConfigValue(optString("retractLiftEnforce")) else null,
    retractionSpeedMmPerSec = if (has("retractionSpeedMmPerSec") && !isNull("retractionSpeedMmPerSec")) optDouble("retractionSpeedMmPerSec").toFloat() else null,
    deretractionSpeedMmPerSec = if (has("deretractionSpeedMmPerSec") && !isNull("deretractionSpeedMmPerSec")) optDouble("deretractionSpeedMmPerSec").toFloat() else null,
    retractRestartExtraMm = if (has("retractRestartExtraMm") && !isNull("retractRestartExtraMm")) optDouble("retractRestartExtraMm").toFloat() else null,
    retractionMinimumTravelMm = if (has("retractionMinimumTravelMm") && !isNull("retractionMinimumTravelMm")) optDouble("retractionMinimumTravelMm").toFloat() else null,
    retractWhenChangingLayer = if (has("retractWhenChangingLayer") && !isNull("retractWhenChangingLayer")) optBoolean("retractWhenChangingLayer") else null,
    wipe = if (has("wipe") && !isNull("wipe")) optBoolean("wipe") else null,
    wipeDistanceMm = if (has("wipeDistanceMm") && !isNull("wipeDistanceMm")) optDouble("wipeDistanceMm").toFloat() else null,
    retractBeforeWipePercent = if (has("retractBeforeWipePercent") && !isNull("retractBeforeWipePercent")) optInt("retractBeforeWipePercent") else null,
    longRetractionsWhenCut = if (has("longRetractionsWhenCut") && !isNull("longRetractionsWhenCut")) optBoolean("longRetractionsWhenCut") else null,
    retractionDistancesWhenCut = optString("retractionDistancesWhenCut", ""),
    ironingFlowPercent = if (has("ironingFlowPercent") && !isNull("ironingFlowPercent")) optDouble("ironingFlowPercent").toFloat() else null,
    ironingSpacingMm = if (has("ironingSpacingMm") && !isNull("ironingSpacingMm")) optDouble("ironingSpacingMm").toFloat() else null,
    ironingInsetMm = if (has("ironingInsetMm") && !isNull("ironingInsetMm")) optDouble("ironingInsetMm").toFloat() else null,
    ironingSpeedMmPerSec = if (has("ironingSpeedMmPerSec") && !isNull("ironingSpeedMmPerSec")) optDouble("ironingSpeedMmPerSec").toFloat() else null,
    pressureAdvanceEnabled = optBoolean("pressureAdvanceEnabled", false),
    pressureAdvance = optDouble("pressureAdvance", 0.0).toFloat(),
    pelletFlowCoefficient = optDouble("pelletFlowCoefficient", 0.4157).toFloat(),
    adaptivePressureAdvanceEnabled = optBoolean("adaptivePressureAdvanceEnabled", false),
    adaptivePressureAdvanceOverhangsEnabled = optBoolean("adaptivePressureAdvanceOverhangsEnabled", false),
    adaptivePressureAdvanceBridges = optDouble("adaptivePressureAdvanceBridges", 0.0).toFloat(),
    adaptivePressureAdvanceModel = optString("adaptivePressureAdvanceModel", "0,0,0\n0,0,0"),
    maxVolumetricSpeedMm3PerSec = optDouble(
        "maxVolumetricSpeedMm3PerSec",
        genericFilamentMaxVolumetricSpeedForMaterial(optString("materialType", "PLA")).toDouble()
    ).toFloat(),
    adaptiveVolumetricSpeedEnabled = optBoolean("adaptiveVolumetricSpeedEnabled", false),
    volumetricSpeedCoefficients = optString("volumetricSpeedCoefficients", "").trim(),
    nozzleTemperatureInitialLayerC = optInt("nozzleTemperatureInitialLayerC", optInt("nozzleTemperatureC", 210)),
    nozzleTemperatureC = optInt("nozzleTemperatureC", 210),
    chamberTemperatureC = optInt("chamberTemperatureC", 0),
    activateChamberTemperatureControl = optBoolean("activateChamberTemperatureControl", false),
    supertackPlateTemperatureInitialLayerC = optInt("supertackPlateTemperatureInitialLayerC", 35),
    supertackPlateTemperatureC = optInt("supertackPlateTemperatureC", 35),
    coolPlateTemperatureInitialLayerC = optInt("coolPlateTemperatureInitialLayerC", 35),
    coolPlateTemperatureC = optInt("coolPlateTemperatureC", 35),
    texturedCoolPlateTemperatureInitialLayerC = optInt("texturedCoolPlateTemperatureInitialLayerC", 40),
    texturedCoolPlateTemperatureC = optInt("texturedCoolPlateTemperatureC", 40),
    engineeringPlateTemperatureInitialLayerC = optInt("engineeringPlateTemperatureInitialLayerC", 45),
    engineeringPlateTemperatureC = optInt("engineeringPlateTemperatureC", 45),
    bedTemperatureInitialLayerC = optInt("bedTemperatureInitialLayerC", optInt("bedTemperatureC", 60)),
    bedTemperatureC = optInt("bedTemperatureC", 60),
    texturedPlateTemperatureInitialLayerC = optInt("texturedPlateTemperatureInitialLayerC", 45),
    texturedPlateTemperatureC = optInt("texturedPlateTemperatureC", 45),
    minFanSpeedPercent = optInt("minFanSpeedPercent", 30),
    coolingPercent = optInt("coolingPercent", 100),
    noCoolingFirstLayers = optInt("noCoolingFirstLayers", 1),
    fullFanSpeedLayer = optInt("fullFanSpeedLayer", 0),
    fanCoolingLayerTimeSeconds = optDouble("fanCoolingLayerTimeSeconds", 60.0).toFloat(),
    slowDownLayerTimeSeconds = optDouble("slowDownLayerTimeSeconds", 5.0).toFloat(),
    reduceFanStopStartFrequency = optBoolean("reduceFanStopStartFrequency", false),
    slowDownForLayerCooling = optBoolean("slowDownForLayerCooling", true),
    dontSlowDownOuterWall = optBoolean("dontSlowDownOuterWall", false),
    slowDownMinSpeedMmPerSec = optDouble("slowDownMinSpeedMmPerSec", 10.0).toFloat(),
    enableOverhangBridgeFan = optBoolean("enableOverhangBridgeFan", true),
    overhangFanThreshold = optString("overhangFanThreshold", "95%"),
    overhangFanSpeedPercent = optInt("overhangFanSpeedPercent", 100),
    internalBridgeFanSpeedPercent = optInt("internalBridgeFanSpeedPercent", -1),
    supportMaterialInterfaceFanSpeedPercent = optInt("supportMaterialInterfaceFanSpeedPercent", -1),
    ironingFanSpeedPercent = optInt("ironingFanSpeedPercent", -1),
    additionalCoolingFanSpeedPercent = optInt("additionalCoolingFanSpeedPercent", 0),
    activateAirFiltration = optBoolean("activateAirFiltration", false),
    duringPrintExhaustFanSpeedPercent = optInt("duringPrintExhaustFanSpeedPercent", 60),
    completePrintExhaustFanSpeedPercent = optInt("completePrintExhaustFanSpeedPercent", 80),
    minimalPurgeOnWipeTowerMm3 = optDouble("minimalPurgeOnWipeTowerMm3", 15.0).toFloat(),
    towerInterfacePreExtrusionDistanceMm = optDouble("towerInterfacePreExtrusionDistanceMm", 10.0).toFloat(),
    towerInterfacePreExtrusionLengthMm = optDouble("towerInterfacePreExtrusionLengthMm", 0.0).toFloat(),
    towerIroningAreaMm2 = optDouble("towerIroningAreaMm2", 4.0).toFloat(),
    towerInterfacePurgeVolumeMm = optDouble("towerInterfacePurgeVolumeMm", 20.0).toFloat(),
    towerInterfacePrintTemperatureC = optInt("towerInterfacePrintTemperatureC", -1),
    longRetractionsWhenExtruderChange = if (has("longRetractionsWhenExtruderChange") && !isNull("longRetractionsWhenExtruderChange")) optBoolean("longRetractionsWhenExtruderChange") else null,
    retractionDistanceWhenExtruderChangeMm = if (has("retractionDistanceWhenExtruderChangeMm") && !isNull("retractionDistanceWhenExtruderChangeMm")) optDouble("retractionDistanceWhenExtruderChangeMm").toFloat() else null,
    loadingSpeedStartMmPerSec = optDouble("loadingSpeedStartMmPerSec", 3.0).toFloat(),
    loadingSpeedMmPerSec = optDouble("loadingSpeedMmPerSec", 28.0).toFloat(),
    unloadingSpeedStartMmPerSec = optDouble("unloadingSpeedStartMmPerSec", 100.0).toFloat(),
    unloadingSpeedMmPerSec = optDouble("unloadingSpeedMmPerSec", 90.0).toFloat(),
    toolchangeDelaySeconds = optDouble("toolchangeDelaySeconds", 0.0).toFloat(),
    coolingMoves = optInt("coolingMoves", 4),
    coolingInitialSpeedMmPerSec = optDouble("coolingInitialSpeedMmPerSec", 2.2).toFloat(),
    coolingFinalSpeedMmPerSec = optDouble("coolingFinalSpeedMmPerSec", 3.4).toFloat(),
    stampingLoadingSpeedMmPerSec = optDouble("stampingLoadingSpeedMmPerSec", 0.0).toFloat(),
    stampingDistanceMm = optDouble("stampingDistanceMm", 0.0).toFloat(),
    rammingParameters = optString("rammingParameters", "120 100 6.6 6.8 7.2 7.6 7.9 8.2 8.7 9.4 9.9 10.0| 0.05 6.6 0.45 6.8 0.95 7.8 1.45 8.3 1.95 9.7 2.45 10 2.95 7.6 3.45 7.6 3.95 7.6 4.45 7.6 4.95 7.6"),
    multitoolRamming = optBoolean("multitoolRamming", false),
    multitoolRammingVolumeMm3 = optDouble("multitoolRammingVolumeMm3", 10.0).toFloat(),
    multitoolRammingFlowMm3PerSec = optDouble("multitoolRammingFlowMm3PerSec", 10.0).toFloat(),
    compatiblePrinters = optString("compatiblePrinters", ""),
    compatiblePrintersCondition = optString("compatiblePrintersCondition", ""),
    compatiblePrints = optString("compatiblePrints", ""),
    compatiblePrintsCondition = optString("compatiblePrintsCondition", ""),
    filamentNotes = optString("filamentNotes", ""),
    filamentStartGcode = optString("filamentStartGcode", ""),
    filamentEndGcode = optString("filamentEndGcode", ""),
    printerProfileId = optString("printerProfileId", ""),
    profileSource = optString("profileSource", if (optBoolean("builtIn", false)) "builtin" else "custom"),
    orcaFamily = optString("orcaFamily", ""),
	    orcaFilamentPath = optString("orcaFilamentPath", ""),
	    orcaRawFilamentJson = optString("orcaRawFilamentJson", ""),
	    orcaResolvedFilamentJson = optString("orcaResolvedFilamentJson", ""),
	    orcaFilamentOverridesJson = optString("orcaFilamentOverridesJson", ""),
	    orcaResolvedSourceChain = optJSONArray("orcaResolvedSourceChain").toProfileJsonStringList()
)

internal fun genericFilamentDensityForMaterial(materialType: String): Float {
    val normalized = materialType.trim().uppercase()
    return when {
        normalized == "PLA" || normalized == "PLA+" -> 1.26f
        normalized == "PET" || normalized == "PETG" -> 1.27f
        normalized == "ABS" || normalized == "ASA" -> 1.04f
        normalized == "TPU" -> 1.21f
        else -> 1.26f
    }
}

internal fun genericFilamentMaxVolumetricSpeedForMaterial(materialType: String): Float {
    val normalized = materialType.trim().uppercase()
    return when {
        normalized == "PLA" || normalized == "PLA+" -> 12f
        normalized == "PET" || normalized == "PETG" -> 25f
        normalized == "ABS" || normalized == "ASA" -> 28.6f
        normalized == "TPU" -> 15f
        else -> 12f
    }
}

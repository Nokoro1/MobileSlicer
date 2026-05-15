package com.mobileslicer.profiles

import com.mobileslicer.viewer.PrinterBedSpec
import java.util.Locale

internal fun thumbnailFormatFromThumbnails(thumbnails: String): String {
    val format = thumbnails
        .split(',')
        .firstOrNull()
        ?.substringAfter('/', missingDelimiterValue = "PNG")
        ?.trim()
        ?.uppercase()
        .orEmpty()
    return when (format) {
        "PNG", "JPG", "QOI", "BTT_TFT", "COLPIC" -> format
        else -> "PNG"
    }
}

internal fun thumbnailsForExport(printer: PrinterProfile): String {
    return printer.thumbnails
}

internal fun printerSettingsIdForExport(printer: PrinterProfile): String {
    printer.resolvedOrcaMachineNameFromJson().takeIf { it.isNotBlank() }?.let { return it }
    if (printer.isResolvedQidiQ2()) {
        return "Qidi Q2 ${printer.nozzleDiameterMm.formatNozzleDiameter()} nozzle"
    }
    return printer.name
}

private fun PrinterProfile.isResolvedQidiQ2(): Boolean {
    val resolvedName = resolvedOrcaMachineNameFromJson()
    return printerAgent.equals("qidi", ignoreCase = true) &&
        sequenceOf(printerModel, name, resolvedName)
            .any { value -> value.equals("Qidi Q2", ignoreCase = true) || value.startsWith("Qidi Q2 ", ignoreCase = true) }
}

private fun PrinterProfile.resolvedOrcaMachineNameFromJson(): String =
    runCatching { org.json.JSONObject(orcaResolvedMachineJson).optString("name") }
        .getOrNull()
        .orEmpty()
        .trim()

private fun Float.formatNozzleDiameter(): String =
    String.format(Locale.US, "%.1f", this)

internal data class PrinterProfile(
    val id: String,
    val name: String,
    val subtitle: String,
    val builtIn: Boolean,
    val bedWidthMm: Float,
    val bedDepthMm: Float,
    val maxHeightMm: Float,
    val bedExcludeArea: String = "",
    val wrappingExcludeArea: String = "",
    val headWrapDetectZone: String = "",
    val bedCustomTexture: String = "",
    val bedCustomModel: String = "",
    val bedModel: String = "",
    val bedModelAssetPath: String = "",
    val bedShape: String = "",
    val bedTexture: String = "",
    val bedTextureAssetPath: String = "",
    val bedTextureArea: String = "",
    val bottomTextureRect: String = "",
    val bottomTextureEndName: String = "",
    val imageBedType: String = "",
    val useDoubleExtruderDefaultTexture: String = "",
    val useRectGrid: Boolean = false,
    val supportMultiBedTypes: Boolean = false,
    val defaultBedType: DefaultBedType = DefaultBedType.NotSet,
    val bestObjectPosition: String = "0.5x0.5",
    val zOffsetMm: Float = 0f,
    val preferredOrientationDegrees: Float = 0f,
    val bedMeshMin: String = "-99999x-99999",
    val bedMeshMax: String = "99999x99999",
    val bedMeshProbeDistance: String = "50x50",
    val adaptiveBedMeshMarginMm: Float = 0f,
    val nozzleDiameterMm: Float,
    val filamentDiameterMm: Float,
    val nozzleVolumeMm3: Float = 0f,
    val nozzleVolumeType: NozzleVolumeType = NozzleVolumeType.Standard,
    val nozzleHeightMm: Float = 2.5f,
    val grabLengthMm: Float = 0f,
    val extruderVariantList: String = "Direct Drive Standard",
    val printerExtruderId: String = "1",
    val printerExtruderVariant: String = "Direct Drive Standard",
    val masterExtruderId: Int = 1,
    val physicalExtruderMap: String = "0",
    val extrudersCount: String = "",
    val extruderAmsCount: String = "",
    val extruderMaxNozzleCount: String = "",
    val extruderType: ExtruderType = ExtruderType.DirectDrive,
    val extruderColor: String = "",
    val extruderPrintableHeightMm: Float = 0f,
    val extruderPrintableArea: String = "",
    val minLayerHeightMm: Float = 0.07f,
    val maxLayerHeightMm: Float = 0f,
    val extruderOffset: String = "0x0",
    val printerModel: String = "",
    val machineTech: String = "",
    val machineFamily: String = "",
    val printerTechnology: PrinterTechnology = PrinterTechnology.Fff,
    val printerVariant: String = "",
    val hotendModel: String = "",
    val boxId: String = "",
    val enablePreHeating: Boolean = false,
    val fanDirection: String = "",
    val hotendCoolingRate: String = "",
    val hotendHeatingRate: String = "",
    val activeFeederMotorName: String = "",
    val autoDisableFilterOnOverheat: String = "",
    val autoToolchangeCommand: String = "",
    val coolingFilterEnabled: String = "",
    val crealityFlushTime: String = "",
    val groupAlgoWithTime: String = "",
    val isArtillery: String = "",
    val isSupport3mf: String = "",
    val isSupportAirCondition: String = "",
    val isSupportMqtt: String = "",
    val isSupportMultiBox: String = "",
    val isSupportTimelapse: String = "",
    val machineLedLightExist: String = "",
    val machineHotendChangeTime: String = "",
    val machinePlatformMotionEnable: String = "",
    val machinePrepareCompensationTime: String = "",
    val multiZone: String = "",
    val multiZoneNumber: String = "",
    val nozzleFlushDataset: String = "",
    val rammingPressureAdvanceValue: String = "",
    val rightIconOffsetBed: String = "",
    val scanFolder: String = "",
    val supportBoxTempControl: String = "",
    val supportCoolingFilter: String = "",
    val supportMultiFilament: String = "",
    val supportObjectSkipFlush: String = "",
    val supportWanNetwork: String = "",
    val toolChangeTemperatureWait: String = "",
    val upwardCompatibleMachine: String = "",
    val vendorUrl: String = "",
    val useActivePelletFeeding: String = "",
    val useExtruderRotationVolume: String = "",
    val printerStructure: PrinterStructure = PrinterStructure.Undefined,
    val gcodeFlavor: GcodeFlavor = GcodeFlavor.MarlinLegacy,
    val pelletModdedPrinter: Boolean = false,
    val useThirdPartyPrintHost: Boolean = false,
    val scanFirstLayer: Boolean = false,
    val useRelativeEDistances: Boolean = true,
    val useFirmwareRetraction: Boolean = false,
    val powerLossRecoveryMode: PowerLossRecoveryMode = PowerLossRecoveryMode.PrinterConfiguration,
    val disableM73: Boolean = false,
    val thumbnails: String = "48x48/PNG,300x300/PNG",
    val thumbnailsInternal: String = "",
    val thumbnailsInternalSwitch: String = "",
    val remainingTimes: String = "",
    val printHostType: PrintHostType = PrintHostType.OctoPrint,
    val printerAgent: String = "",
    val printHost: String = "",
    val printHostWebUi: String = "",
    val printHostAuthorizationType: PrintHostAuthorizationType = PrintHostAuthorizationType.Key,
    val printHostApiKey: String = "",
    val printHostPort: String = "",
    val printHostGroup: String = "",
    val printHostCaFile: String = "",
    val printHostUser: String = "",
    val printHostPassword: String = "",
    val printHostSslIgnoreRevoke: Boolean = false,
    val bambuBedType: String = "",
    val bambuUseAms: Boolean = false,
    val bambuAmsMapping: String = "",
    val bambuNozzleMapping: String = "",
    val bambuBedLeveling: Boolean = true,
    val bambuFlowCalibration: Boolean = false,
    val bambuVibrationCalibration: Boolean = false,
    val bambuTimelapse: Boolean = false,
    val timeCost: Float = 0f,
    val fanSpeedupTimeSeconds: Float = 0f,
    val fanSpeedupOverhangsOnly: Boolean = true,
    val fanKickstartTimeSeconds: Float = 0f,
    val extruderClearanceRadiusMm: Float = 40f,
    val extruderClearanceHeightToRodMm: Float = 40f,
    val extruderClearanceHeightToLidMm: Float = 120f,
    val extruderClearanceDistToRodMm: Float = 0f,
    val nozzleType: NozzleType = NozzleType.Undefined,
    val nozzleHrc: Int = 0,
    val auxiliaryFan: Boolean = false,
    val supportChamberTempControl: Boolean = true,
    val supportAirFiltration: Boolean = true,
    val singleExtruderMultiMaterial: Boolean = true,
    val manualFilamentChange: Boolean = false,
    val bedTemperatureFormula: BedTemperatureFormula = BedTemperatureFormula.ByHighestTemp,
    val wipeTowerType: WipeTowerType = WipeTowerType.Type2,
    val purgeInPrimeTower: Boolean = true,
    val enableFilamentRamming: Boolean = true,
    val coolingTubeRetractionMm: Float = 91.5f,
    val coolingTubeLengthMm: Float = 5f,
    val parkingPositionRetractionMm: Float = 92f,
    val extraLoadingMoveMm: Float = -2f,
    val highCurrentOnFilamentSwap: Boolean = false,
    val machineLoadFilamentTimeSeconds: Float = 0f,
    val machineUnloadFilamentTimeSeconds: Float = 0f,
    val machineToolChangeTimeSeconds: Float = 0f,
    val fileStartGcode: String = "",
    val machineStartGcode: String = "",
    val machineEndGcode: String = "",
    val printingByObjectGcode: String = "",
    val beforeLayerChangeGcode: String = "",
    val layerChangeGcode: String = "",
    val timeLapseGcode: String = "",
    val wrappingDetectionGcode: String = "",
    val changeFilamentGcode: String = "",
    val changeExtrusionRoleGcode: String = "",
    val machinePauseGcode: String = "",
    val templateCustomGcode: String = "",
    val emitMachineLimitsToGcode: Boolean = true,
    val resonanceAvoidance: Boolean = false,
    val silentMode: Boolean = false,
    val minResonanceAvoidanceSpeedMmPerSec: Float = 70f,
    val maxResonanceAvoidanceSpeedMmPerSec: Float = 120f,
    val machineMaxSpeedX: Float = 500f,
    val machineMaxSpeedY: Float = 500f,
    val machineMaxSpeedZ: Float = 12f,
    val machineMaxSpeedE: Float = 120f,
    val machineMaxAccelerationX: Float = 1000f,
    val machineMaxAccelerationY: Float = 1000f,
    val machineMaxAccelerationZ: Float = 500f,
    val machineMaxAccelerationE: Float = 5000f,
    val machineMaxAccelerationExtruding: Float = 1500f,
    val machineMaxAccelerationRetracting: Float = 1500f,
    val machineMaxAccelerationTravel: Float = 0f,
    val machineMaxJerkX: Float = 10f,
    val machineMaxJerkY: Float = 10f,
    val machineMaxJerkZ: Float = 0.2f,
    val machineMaxJerkE: Float = 2.5f,
    val machineMaxJunctionDeviation: Float = 0.01f,
    val machineMinExtrudingRateMmPerSec: Float = 0f,
    val machineMinTravelRateMmPerSec: Float = 0f,
    val retractionLengthMm: Float = 0.8f,
    val retractRestartExtraMm: Float = 0f,
    val retractionSpeedMmPerSec: Float = 30f,
    val deretractionSpeedMmPerSec: Float = 0f,
    val retractionMinimumTravelMm: Float = 2f,
    val retractWhenChangingLayer: Boolean = false,
    val retractOnTopLayer: String = "",
    val wipe: Boolean = false,
    val wipeDistanceMm: Float = 1f,
    val retractBeforeWipePercent: Int = 100,
    val retractLiftEnforce: RetractLiftEnforce = RetractLiftEnforce.AllSurfaces,
    val zHopType: ZHopType = ZHopType.Slope,
    val zHopWhenPrime: String = "",
    val zLiftType: String = "",
    val zHopMm: Float = 0.4f,
    val travelSlopeDegrees: Float = 3f,
    val retractLiftAboveMm: Float = 0f,
    val retractLiftBelowMm: Float = 0f,
    val retractLengthToolchangeMm: Float = 10f,
    val retractRestartExtraToolchangeMm: Float = 0f,
    val enableLongRetractionWhenCut: LongRetractionWhenCutMode = LongRetractionWhenCutMode.Disabled,
    val longRetractionsWhenCut: Boolean = false,
    val retractionDistanceWhenCutMm: Float = 18f,
    val printerNotes: String = "",
    val profileSource: String = "custom",
    val thumbnailAssetPath: String = "",
    val orcaFamily: String = "",
	    val orcaMachineModelPath: String = "",
	    val orcaMachineModelJson: String = "",
	    val orcaResolvedMachineJson: String = "",
	    val orcaMachineOverridesJson: String = "",
	    val orcaNozzleMachinePaths: List<String> = emptyList(),
    val orcaNozzleMachineJsons: List<String> = emptyList(),
    val orcaResolvedMachineJsons: List<String> = emptyList(),
    val orcaResolvedSourceChains: List<String> = emptyList(),
    val availableNozzleDiameters: List<Float> = emptyList()
) {
    private val resolvedBedModelAssetPath: String
        get() = bedModelAssetPath.ifBlank {
            derivedOrcaBedModelAssetPath(
                family = machineFamily.ifBlank { orcaFamily },
                bedModel = bedModel
            )
        }

    private val resolvedBedTextureAssetPath: String
        get() = bedTextureAssetPath.ifBlank {
            derivedOrcaBedTextureAssetPath(
                family = machineFamily.ifBlank { orcaFamily },
                bedTexture = bedTexture
            )
        }

    private val resolvedBedTextureIncludesGrid: Boolean
        get() = orcaBedTextureIncludesGrid(
            assetPath = resolvedBedTextureAssetPath,
            textureName = bedTexture
        )

    fun toBedSpec(): PrinterBedSpec = PrinterBedSpec(
        widthMm = bedWidthMm,
        depthMm = bedDepthMm,
        maxHeightMm = maxHeightMm,
        originXmm = nativeBedOrigin().first,
        originYmm = nativeBedOrigin().second,
        bedModelAssetPath = resolvedBedModelAssetPath,
        bedTextureAssetPath = resolvedBedTextureAssetPath,
        bedTextureIncludesGrid = resolvedBedTextureIncludesGrid
    )
}

internal fun PrinterProfile.withBambuDefaultConnectionType(hasExplicitPrintHostType: Boolean): PrinterProfile {
    if (!hasBambuContext() || printHostType == PrintHostType.BambuLan) return this
    val legacyGenericDefault =
        printHostType == PrintHostType.OctoPrint &&
            printHost.isBlank() &&
            printHostApiKey.isBlank() &&
            printHostPort.isBlank()
    return if (!hasExplicitPrintHostType || legacyGenericDefault) {
        copy(printHostType = PrintHostType.BambuLan)
    } else {
        this
    }
}

private fun PrinterProfile.nativeBedOrigin(): Pair<Float, Float> {
    val points = parseNativeBedPoints(bedShape)
        .ifEmpty { parseNativeBedPoints(runCatching { org.json.JSONObject(orcaResolvedMachineJson).opt("printable_area") }.getOrNull()) }
    if (points.size < 2) return 0f to 0f
    val minX = points.minOf { it.first }
    val minY = points.minOf { it.second }
    return minX to minY
}

private fun parseNativeBedPoints(value: Any?): List<Pair<Float, Float>> {
    val rawPoints = when (value) {
        is org.json.JSONArray -> List(value.length()) { index -> value.optString(index) }
        is String -> value.split(';', ',').map { it.trim() }
        else -> emptyList()
    }
    return rawPoints.mapNotNull { raw ->
        val normalized = raw.trim().replace(',', 'x')
        val x = normalized.substringBefore('x', missingDelimiterValue = "").trim().toFloatOrNull()
        val y = normalized.substringAfter('x', missingDelimiterValue = "").trim().toFloatOrNull()
        if (x != null && y != null) x to y else null
    }
}

private fun derivedOrcaBedModelAssetPath(family: String, bedModel: String): String {
    if (family.isBlank() || bedModel.isBlank()) return ""
    val assetName = "${family}_${bedModel}"
        .map { character -> if (character.isLetterOrDigit()) character.lowercaseChar() else '_' }
        .joinToString("")
        .trim('_')
    if (assetName.isBlank()) return ""
    return "orca-printers/bed-models/$assetName"
}

private fun derivedOrcaBedTextureAssetPath(family: String, bedTexture: String): String {
    if (family.isBlank() || bedTexture.isBlank()) return ""
    val stem = bedTexture.substringBeforeLast('.', bedTexture)
    val assetName = "${family}_${stem}"
        .map { character -> if (character.isLetterOrDigit()) character.lowercaseChar() else '_' }
        .joinToString("")
        .trim('_')
    if (assetName.isBlank()) return ""
    return "orca-printers/bed-textures/$assetName.png"
}

private fun orcaBedTextureIncludesGrid(assetPath: String, textureName: String): Boolean {
    val key = "$assetPath $textureName".lowercase(Locale.US)
    return key.contains("prusa_") ||
        key.contains("prusa ") ||
        key.contains("coreone") ||
        key.contains("coreonel") ||
        key.contains("mk3") ||
        key.contains("mk4") ||
        key.contains("miniis") ||
        key.contains("mini.")
}

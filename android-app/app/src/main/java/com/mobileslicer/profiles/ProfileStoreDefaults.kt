package com.mobileslicer.profiles

internal fun profileStoreDefaultPrinterProfiles(): List<PrinterProfile> = emptyList()

internal fun profileStoreFallbackPrinterProfile(): PrinterProfile =
    PrinterProfile(
        id = "fallback_printer",
        name = "Add Printer",
        subtitle = "Import a printer preset or create a custom printer.",
        builtIn = false,
        bedWidthMm = 220f,
        bedDepthMm = 220f,
        maxHeightMm = 220f,
        nozzleDiameterMm = 0.4f,
        filamentDiameterMm = 1.75f
    )

internal fun profileStoreFallbackFilamentProfile(): FilamentProfile =
    FilamentProfile(
        id = "fallback_filament",
        name = "Add Filament",
        subtitle = "Import a filament preset or create a custom filament.",
        builtIn = false,
        materialType = "PLA",
        densityGPerCm3 = 1.24f,
        flowRatio = 1f,
        retractionLengthMm = null,
        retractionSpeedMmPerSec = null,
        deretractionSpeedMmPerSec = null,
        pressureAdvanceEnabled = false,
        pressureAdvance = 0f,
        maxVolumetricSpeedMm3PerSec = 12f,
        adaptiveVolumetricSpeedEnabled = false,
        volumetricSpeedCoefficients = "",
        nozzleTemperatureInitialLayerC = 210,
        nozzleTemperatureC = 210,
        bedTemperatureInitialLayerC = 60,
        bedTemperatureC = 60,
        coolingPercent = 100,
        noCoolingFirstLayers = 1
    )

internal fun profileStoreDefaultFilamentProfiles(): List<FilamentProfile> = listOf(
    FilamentProfile(
        id = "generic_pla",
        name = "Generic PLA",
        subtitle = "General-purpose PLA for everyday prints and quick slicing checks.",
        builtIn = true,
        materialType = "PLA",
        densityGPerCm3 = genericFilamentDensityForMaterial("PLA"),
        flowRatio = 1f,
        retractionLengthMm = null,
        retractionSpeedMmPerSec = null,
        deretractionSpeedMmPerSec = null,
        pressureAdvanceEnabled = false,
        pressureAdvance = 0f,
        maxVolumetricSpeedMm3PerSec = genericFilamentMaxVolumetricSpeedForMaterial("PLA"),
        adaptiveVolumetricSpeedEnabled = false,
        volumetricSpeedCoefficients = "",
        nozzleTemperatureInitialLayerC = 210,
        nozzleTemperatureC = 210,
        bedTemperatureInitialLayerC = 60,
        bedTemperatureC = 60,
        coolingPercent = 100,
        noCoolingFirstLayers = 1,
        filamentStartGcode = "",
        filamentEndGcode = ""
    ),
    FilamentProfile(
        id = "pla_plus",
        name = "PLA+",
        subtitle = "Slightly tougher PLA profile for stronger everyday prints.",
        builtIn = true,
        materialType = "PLA+",
        densityGPerCm3 = genericFilamentDensityForMaterial("PLA+"),
        flowRatio = 1f,
        retractionLengthMm = null,
        retractionSpeedMmPerSec = null,
        deretractionSpeedMmPerSec = null,
        pressureAdvanceEnabled = false,
        pressureAdvance = 0f,
        maxVolumetricSpeedMm3PerSec = genericFilamentMaxVolumetricSpeedForMaterial("PLA+"),
        adaptiveVolumetricSpeedEnabled = false,
        volumetricSpeedCoefficients = "",
        nozzleTemperatureInitialLayerC = 220,
        nozzleTemperatureC = 220,
        bedTemperatureInitialLayerC = 60,
        bedTemperatureC = 60,
        coolingPercent = 100,
        noCoolingFirstLayers = 1,
        filamentStartGcode = "",
        filamentEndGcode = ""
    ),
    FilamentProfile(
        id = "petg",
        name = "PETG",
        subtitle = "Utility material profile for durable parts and brackets.",
        builtIn = true,
        materialType = "PETG",
        densityGPerCm3 = genericFilamentDensityForMaterial("PETG"),
        flowRatio = 1f,
        retractionLengthMm = null,
        retractionSpeedMmPerSec = null,
        deretractionSpeedMmPerSec = null,
        pressureAdvanceEnabled = false,
        pressureAdvance = 0f,
        maxVolumetricSpeedMm3PerSec = genericFilamentMaxVolumetricSpeedForMaterial("PETG"),
        adaptiveVolumetricSpeedEnabled = false,
        volumetricSpeedCoefficients = "",
        nozzleTemperatureInitialLayerC = 245,
        nozzleTemperatureC = 245,
        bedTemperatureInitialLayerC = 80,
        bedTemperatureC = 80,
        coolingPercent = 35,
        noCoolingFirstLayers = 1,
        filamentStartGcode = "",
        filamentEndGcode = ""
    ),
    FilamentProfile(
        id = "abs",
        name = "ABS",
        subtitle = "Higher-temperature engineering baseline for enclosed printers.",
        builtIn = true,
        materialType = "ABS",
        densityGPerCm3 = genericFilamentDensityForMaterial("ABS"),
        flowRatio = 1f,
        retractionLengthMm = null,
        retractionSpeedMmPerSec = null,
        deretractionSpeedMmPerSec = null,
        pressureAdvanceEnabled = false,
        pressureAdvance = 0f,
        maxVolumetricSpeedMm3PerSec = genericFilamentMaxVolumetricSpeedForMaterial("ABS"),
        adaptiveVolumetricSpeedEnabled = false,
        volumetricSpeedCoefficients = "",
        nozzleTemperatureInitialLayerC = 250,
        nozzleTemperatureC = 250,
        bedTemperatureInitialLayerC = 100,
        bedTemperatureC = 100,
        coolingPercent = 10,
        noCoolingFirstLayers = 1,
        filamentStartGcode = "",
        filamentEndGcode = ""
    )
)

internal fun profileStoreDefaultProcessProfiles(): List<ProcessProfile> = emptyList()
internal fun profileStoreFallbackProcessProfile(printerProfileId: String = "", nozzleDiameterMm: Float = 0.4f): ProcessProfile =
    error("No process profile is available for printer '$printerProfileId' and nozzle '$nozzleDiameterMm'. Import or create a process preset first.")

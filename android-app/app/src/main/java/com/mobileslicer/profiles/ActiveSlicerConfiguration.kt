package com.mobileslicer.profiles

import org.json.JSONArray
import org.json.JSONObject

internal data class ActiveSlicerConfiguration(
    val printer: PrinterProfile,
    val filament: FilamentProfile,
    val process: ProcessProfile
) {
    fun workspaceBedLabel(): String = String.format(
        java.util.Locale.US,
        "%.0f × %.0f × %.0f mm bed",
        printer.bedWidthMm,
        printer.bedDepthMm,
        printer.maxHeightMm
    )

    fun printerHardwareLabel(): String = String.format(
        java.util.Locale.US,
        "%.2f mm nozzle • %.2f mm filament",
        printer.nozzleDiameterMm,
        printer.filamentDiameterMm
    )

    fun filamentLabel(): String = buildString {
        append(filament.materialType)
        append(" • ")
        append(String.format(java.util.Locale.US, "%.2f flow", filament.flowRatio))
        append(" • ")
        append(
            if (filament.retractionLengthMm == null && filament.retractionSpeedMmPerSec == null) {
                "Retract inherit"
            } else {
                buildString {
                    append("Retract ")
                    append(
                        filament.retractionLengthMm?.let {
                            String.format(java.util.Locale.US, "%.2f mm", it)
                        } ?: "length inherit"
                    )
                    append(" @ ")
                    append(
                        filament.retractionSpeedMmPerSec?.let {
                            String.format(java.util.Locale.US, "%.0f mm/s", it)
                        } ?: "speed inherit"
                    )
                }
            }
        )
        append(" • ")
        append(
            filament.deretractionSpeedMmPerSec?.let {
                if (it == 0f) {
                    "Deretract same as retract"
                } else {
                    String.format(java.util.Locale.US, "Deretract %.0f mm/s", it)
                }
            } ?: "Deretract inherit"
        )
        append(" • ")
        append(if (filament.pressureAdvanceEnabled) {
            String.format(java.util.Locale.US, "PA %.3f", filament.pressureAdvance)
        } else {
            "PA off"
        })
        append(" • ")
        append(String.format(java.util.Locale.US, "%.1f mm3/s max flow", filament.maxVolumetricSpeedMm3PerSec))
        append(" • ")
        append(if (filament.adaptiveVolumetricSpeedEnabled) "Adaptive flow on" else "Adaptive flow off")
        if (filament.volumetricSpeedCoefficients.isNotBlank()) {
            append(" • Adaptive fit coeffs set")
        }
        append(" • ")
        append("Nozzle ")
        append(filament.nozzleTemperatureInitialLayerC)
        append("/")
        append(filament.nozzleTemperatureC)
        append("C")
        append(" • ")
        append("Bed ")
        append(filament.bedTemperatureInitialLayerC)
        append("/")
        append(filament.bedTemperatureC)
        append("C")
        append(" • Fan ")
        append(filament.coolingPercent)
        append("%")
        append(" • No cooling ")
        append(filament.noCoolingFirstLayers)
        append(" layer")
        if (filament.noCoolingFirstLayers != 1) {
            append("s")
        }
    }

    fun processLabel(): String = buildString {
        append(String.format(java.util.Locale.US, "%.2f mm initial", process.firstLayerHeightMm))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.2f mm", process.layerHeightMm))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.1f mm/s first layer", process.firstLayerPrintSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.1f mm/s infill", process.firstLayerInfillSpeedMmPerSec))
        append(" • ")
        append(process.firstLayerTravelSpeedPercent)
        append("% travel")
        append(" • ")
        append(process.slowDownLayers)
        append(" slow")
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f / %.0f outer/inner", process.outerWallSpeedMmPerSec, process.innerWallSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f top", process.topSurfaceSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f travel", process.travelSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f/%.0f wall accel", process.outerWallAccelerationMmPerSec2, process.innerWallAccelerationMmPerSec2))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f top accel", process.topSurfaceAccelerationMmPerSec2))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f sparse accel", process.sparseInfillAccelerationMmPerSec2))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f bridge", process.bridgeSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f small <= %.1f", process.smallPerimeterSpeedMmPerSec, process.smallPerimeterThresholdMm))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f sparse", process.sparseInfillSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f solid", process.internalSolidInfillSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f gap", process.gapInfillSpeedMmPerSec))
        append(" • ")
        append(process.topShellLayers)
        append("/")
        append(process.bottomShellLayers)
        append(" shell")
        append(" • ")
        append(process.topSurfaceDensityPercent)
        append("/")
        append(process.bottomSurfaceDensityPercent)
        append("% skin")
        append(" • ")
        append(process.topSurfacePattern.displayLabel)
        append("/")
        append(process.bottomSurfacePattern.displayLabel)
        append(" top/bottom")
        append(" • ")
        append(process.internalSolidInfillPattern.displayLabel)
        append(" solid")
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f° solid", process.solidInfillDirectionDegrees))
        if (process.solidInfillRotateTemplate.isNotBlank()) {
            append(" • solid template")
        }
        if (process.lineWidth != "0" || process.topSurfaceLineWidth != "0" || process.internalSolidInfillLineWidth != "0") {
            append(" • widths")
        }
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f° infill", process.infillDirectionDegrees))
        if (process.sparseInfillRotateTemplate.isNotBlank()) {
            append(" • infill template")
        }
        if (process.alignInfillDirectionToModel) {
            append(" • infill follows model")
        }
        append(" • ")
        append(process.seamPosition.detailLabel)
        append(" • ")
        append(if (process.preciseOuterWall) "Precise wall" else "Standard wall spacing")
        append(" • ")
        append(if (process.onlyOneWallTopSurfaces) "One-wall top" else "Multi-wall top")
        append(" • ")
        append(process.wallCount)
        append(" walls")
        append(" • ")
        append(process.infillPercent)
        append("% infill")
        append(" • ")
        append(process.sparseInfillPattern.displayLabel)
        append(" fill")
        append(process.skirts)
        append(" skirts")
        append(" • ")
        append(String.format(java.util.Locale.US, "%.1f mm brim", process.brimWidthMm))
        if (process.fuzzySkin != FuzzySkinType.Disabled) {
            append(" • ")
            append(process.fuzzySkin.detailLabel)
            append(" fuzzy")
        }
    }

    fun nativeSliceTitle(): String = process.name

    fun nativeSliceBody(): String = buildString {
        append(String.format(java.util.Locale.US, "%.2f mm initial", process.firstLayerHeightMm))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.2f mm", process.layerHeightMm))
        append(" layer")
        append(" • ")
        append(String.format(java.util.Locale.US, "%.1f mm/s first layer", process.firstLayerPrintSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.1f mm/s infill", process.firstLayerInfillSpeedMmPerSec))
        append(" • ")
        append(process.firstLayerTravelSpeedPercent)
        append("% travel")
        append(" • ")
        append(process.slowDownLayers)
        append(" slow")
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f / %.0f outer/inner", process.outerWallSpeedMmPerSec, process.innerWallSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f top", process.topSurfaceSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f travel", process.travelSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f/%.0f wall accel", process.outerWallAccelerationMmPerSec2, process.innerWallAccelerationMmPerSec2))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f top accel", process.topSurfaceAccelerationMmPerSec2))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f sparse accel", process.sparseInfillAccelerationMmPerSec2))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f bridge", process.bridgeSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f small <= %.1f", process.smallPerimeterSpeedMmPerSec, process.smallPerimeterThresholdMm))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f sparse", process.sparseInfillSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f solid", process.internalSolidInfillSpeedMmPerSec))
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f gap", process.gapInfillSpeedMmPerSec))
        append(" • ")
        append(process.topShellLayers)
        append("/")
        append(process.bottomShellLayers)
        append(" shell")
        append(" • ")
        append(process.topSurfaceDensityPercent)
        append("/")
        append(process.bottomSurfaceDensityPercent)
        append("% skin")
        append(" • ")
        append(process.topSurfacePattern.displayLabel)
        append("/")
        append(process.bottomSurfacePattern.displayLabel)
        append(" top/bottom")
        append(" • ")
        append(process.internalSolidInfillPattern.displayLabel)
        append(" solid")
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f° solid", process.solidInfillDirectionDegrees))
        if (process.solidInfillRotateTemplate.isNotBlank()) {
            append(" • solid template")
        }
        if (process.lineWidth != "0" || process.topSurfaceLineWidth != "0" || process.internalSolidInfillLineWidth != "0") {
            append(" • widths")
        }
        append(" • ")
        append(String.format(java.util.Locale.US, "%.0f° infill", process.infillDirectionDegrees))
        if (process.sparseInfillRotateTemplate.isNotBlank()) {
            append(" • infill template")
        }
        if (process.alignInfillDirectionToModel) {
            append(" • infill follows model")
        }
        append(" • ")
        append(if (process.preciseOuterWall) "Precise wall" else "Standard wall spacing")
        append(" • ")
        append(if (process.onlyOneWallTopSurfaces) "One-wall top" else "Multi-wall top")
        append(" • ")
        append(process.wallCount)
        append(" walls")
        append(" • ")
        append(process.infillPercent)
        append("% infill")
        append(" • ")
        append(process.sparseInfillPattern.displayLabel)
        append(" fill")
        append(" • ")
        append(process.skirts)
        append(" skirts")
        append(" • ")
        append(String.format(java.util.Locale.US, "%.1f mm brim", process.brimWidthMm))
    }

    fun nativeSliceValidationBody(): String =
        "Core slicer settings are covered by automated release validation across printer, filament, process, adhesion, and G-code export paths. Network and device checks are kept behind explicit automation flags, while normal release builds keep automation disabled."

    fun appLayerSummaryTitle(): String = "${filament.name} • ${process.name}"

    fun appLayerSummaryBody(): String = buildString {
        append(printerHardwareLabel())
        append(" • ")
        append(filamentLabel())
        append(" • ")
        append(processLabel())
    }

    fun appLayerOnlyBody(): String = buildString {
        append("Workspace bed: ")
        append(workspaceBedLabel())
        append(" • Workspace rendering, slicer configuration, and G-code export use separate validation paths. Bed dimensions reach the slicer as printable_area/printable_height and fail slicing when emitted extrusion exceeds the printable volume. Filament material, flow ratio, retraction, pressure advance, max volumetric speed, adaptive volumetric speed, volumetric speed coefficients, and filament start/end G-code are mapped through Orca-compatible configuration keys.")
    }


}

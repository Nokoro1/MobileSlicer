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

    fun nativeSliceProofBody(): String =
        "Current Stage 2 proof on RFCYA01ANVE: Nozzle diameter, filament diameter, filament max volumetric speed, layer height, first layer height, first-layer speed cluster, outer wall speed, inner wall speed, top surface speed, travel speed, outer wall acceleration, inner wall acceleration, top surface acceleration, sparse infill acceleration, bridge speed, small perimeter speed, small perimeter threshold, sparse infill speed, internal solid infill speed, top shell layers, bottom shell layers, seam position, sparse infill density, sparse infill pattern, print speed baseline, brim width, skirt output, and precise wall are Device-proven. Skirt parity proof covers disabled, combined, per-object, and brim-plus-skirt outputs with finite in-bed first-layer geometry. Flow ratio, filament retraction length, filament retraction speed, filament deretraction speed, pressure advance, volumetric speed coefficients, and adaptive volumetric speed are Config-only Waydroid-validated ownership lanes only; they are not accepted device proof yet. Gap infill speed remains Config-labeling-only effect. Top surface pattern, only one wall on top surfaces, and wall count are Stronger-fixture proven. First-layer nozzle and bed temperatures are Start-sequence only, while later-layer nozzle and bed temperatures are Layer-change command only. Cooling baseline and no cooling for first X layers are Fan-command only. The weak cached ms_box fixture is too weak for wall-count proof."

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
        append(" • Workspace still renders the original STL mesh only, not emitted toolpaths or layer walls. Bed dimensions now reach slicer as printable_area/printable_height and stay Error-state only when emitted extrusion exceeds that volume. Filament material still reaches slicer only as Orca filament_type and stays Source-wired. Flow ratio now reaches slicer as Orca filament_flow_ratio. Filament retraction core overrides now reach slicer as Orca filament_retraction_length, filament_retraction_speed, and filament_deretraction_speed when explicitly set, and otherwise stay inherited from printer/extruder defaults. Pressure advance now reaches slicer as Orca enable_pressure_advance and pressure_advance. Max volumetric speed now reaches slicer as explicit Orca filament_max_volumetric_speed. Adaptive volumetric speed now reaches slicer as Orca filament_adaptive_volumetric_speed, volumetric speed coefficients reach slicer as Orca volumetric_speed_coefficients, and filament start/end G-code reaches slicer as Orca filament_start_gcode and filament_end_gcode; all retraction, pressure-advance, adaptive-flow, and filament G-code fields stay Config-only until phone proof returns.")
    }


}

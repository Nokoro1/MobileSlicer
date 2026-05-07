package com.mobileslicer.calibration

import com.mobileslicer.profiles.ProfileStore
import java.util.Locale

internal data class CalibrationResultApplySpec(
    val title: String,
    val valueLabel: String,
    val helperText: String
)

internal data class CalibrationResultApplyOutcome(
    val store: ProfileStore,
    val applied: Boolean,
    val message: String
)

internal fun CalibrationJob.resultApplySpec(): CalibrationResultApplySpec? = when (type) {
    CalibrationType.PressureAdvance -> CalibrationResultApplySpec(
        title = "Save pressure advance",
        valueLabel = "Measured PA",
        helperText = "Updates pressure advance on the selected filament."
    )
    CalibrationType.FlowRate -> CalibrationResultApplySpec(
        title = "Save flow ratio",
        valueLabel = "Measured flow ratio",
        helperText = "Updates filament_flow_ratio on the selected filament."
    )
    CalibrationType.TemperatureTower -> CalibrationResultApplySpec(
        title = "Save nozzle temperature",
        valueLabel = "Best temperature (C)",
        helperText = "Updates nozzle temperature on the selected filament."
    )
    CalibrationType.MaxVolumetricSpeed -> CalibrationResultApplySpec(
        title = "Save max volumetric speed",
        valueLabel = "Max flow (mm3/s)",
        helperText = "Updates filament_max_volumetric_speed on the selected filament."
    )
    CalibrationType.Retraction -> CalibrationResultApplySpec(
        title = "Save retraction length",
        valueLabel = "Retraction length (mm)",
        helperText = "Updates retraction length on the selected filament."
    )
    CalibrationType.Cornering -> CalibrationResultApplySpec(
        title = "Save cornering result",
        valueLabel = "Measured cornering value",
        helperText = "Updates junction deviation when enabled, otherwise process jerk fields."
    )
    CalibrationType.InputShapingFrequency,
    CalibrationType.InputShapingDamping,
    CalibrationType.Vfa,
    CalibrationType.Tolerance -> null
}

internal fun ProfileStore.applyCalibrationResult(
    job: CalibrationJob,
    rawValue: String
): CalibrationResultApplyOutcome {
    val value = rawValue.trim().toFloatOrNull()
        ?: return CalibrationResultApplyOutcome(this, applied = false, message = "Enter a numeric calibration result.")
    if (!value.isFinite() || value < 0f) {
        return CalibrationResultApplyOutcome(this, applied = false, message = "Enter a positive calibration result.")
    }

    val filamentIndex = filaments.indexOfFirst { it.id == selectedFilamentId }
    val processIndex = processes.indexOfFirst { it.id == selectedProcessId }

    fun updateSelectedFilament(transform: (com.mobileslicer.profiles.FilamentProfile) -> com.mobileslicer.profiles.FilamentProfile): CalibrationResultApplyOutcome {
        if (filamentIndex < 0) {
            return CalibrationResultApplyOutcome(this, applied = false, message = "No selected filament profile to update.")
        }
        val nextFilaments = filaments.toMutableList()
        nextFilaments[filamentIndex] = transform(nextFilaments[filamentIndex])
        return CalibrationResultApplyOutcome(
            store = copy(filaments = nextFilaments),
            applied = true,
            message = "${job.type.title} result saved to ${nextFilaments[filamentIndex].name}."
        )
    }

    return when (job.type) {
        CalibrationType.PressureAdvance -> updateSelectedFilament {
            it.copy(pressureAdvanceEnabled = true, pressureAdvance = value.coerceIn(0f, 2f))
        }
        CalibrationType.FlowRate -> updateSelectedFilament {
            it.copy(flowRatio = value.coerceIn(0.01f, 2f))
        }
        CalibrationType.TemperatureTower -> updateSelectedFilament {
            val temperature = value.toInt().coerceIn(0, 1500)
            it.copy(nozzleTemperatureInitialLayerC = temperature, nozzleTemperatureC = temperature)
        }
        CalibrationType.MaxVolumetricSpeed -> updateSelectedFilament {
            it.copy(maxVolumetricSpeedMm3PerSec = value.coerceAtLeast(0f))
        }
        CalibrationType.Retraction -> updateSelectedFilament {
            it.copy(retractionLengthMm = value.coerceAtLeast(0f))
        }
        CalibrationType.Cornering -> {
            if (processIndex < 0) {
                CalibrationResultApplyOutcome(this, applied = false, message = "No selected process profile to update.")
            } else {
                val process = processes[processIndex]
                val nextProcess = if (process.defaultJunctionDeviationMm > 0f) {
                    process.withValues("defaultJunctionDeviationMm" to value)
                } else {
                    process.withValues(
                        "defaultJerkMmPerSec" to value,
                        "outerWallJerkMmPerSec" to value,
                        "innerWallJerkMmPerSec" to value,
                        "infillJerkMmPerSec" to value,
                        "topSurfaceJerkMmPerSec" to value,
                        "travelJerkMmPerSec" to value
                    )
                }
                val nextProcesses = processes.toMutableList()
                nextProcesses[processIndex] = nextProcess
                CalibrationResultApplyOutcome(
                    store = copy(processes = nextProcesses),
                    applied = true,
                    message = "Cornering result ${String.format(Locale.US, "%.3f", value)} saved to ${nextProcess.name}."
                )
            }
        }
        CalibrationType.InputShapingFrequency,
        CalibrationType.InputShapingDamping,
        CalibrationType.Vfa,
        CalibrationType.Tolerance -> CalibrationResultApplyOutcome(
            this,
            applied = false,
            message = "${job.type.title} does not have a saved profile field in Mobile Slicer yet."
        )
    }
}

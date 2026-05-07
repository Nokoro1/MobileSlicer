package com.mobileslicer.calibration

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mobileslicer.CompactWorkspaceBadge
import com.mobileslicer.R
import com.mobileslicer.appBackgroundGradient
import com.mobileslicer.appBodyColor
import com.mobileslicer.appCardColor
import com.mobileslicer.appCardColorMuted
import com.mobileslicer.appMutedColor
import com.mobileslicer.appOutlineColor
import com.mobileslicer.appTitleColor
import com.mobileslicer.profiles.GcodeFlavor
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.activeConfiguration
import org.json.JSONObject
import java.io.File
import java.util.Locale

internal enum class CalibrationType(
    val title: String,
    val subtitle: String,
    val defaultRange: String,
    val overrideSummary: String
) {
    PressureAdvance(
        title = "Pressure Advance",
        subtitle = "Tune pressure advance with line, pattern, and tower tests.",
        defaultRange = "K-value range",
        overrideSummary = "Overrides PA values and calibration-specific speeds."
    ),
    FlowRate(
        title = "Flow Rate",
        subtitle = "Coarse and fine extrusion multiplier calibration.",
        defaultRange = "Coarse / fine pass",
        overrideSummary = "Overrides flow ratio test geometry and print settings."
    ),
    TemperatureTower(
        title = "Temperature Tower",
        subtitle = "Find the best nozzle temperature for the selected filament.",
        defaultRange = "Temperature steps",
        overrideSummary = "Overrides nozzle temperatures by tower band."
    ),
    MaxVolumetricSpeed(
        title = "Max Volumetric Speed",
        subtitle = "Find the reliable flow ceiling for this material and hotend.",
        defaultRange = "Flow-speed steps",
        overrideSummary = "Overrides speed and volumetric-flow stress settings."
    ),
    Vfa(
        title = "VFA",
        subtitle = "Find speeds that reduce vertical fine artifacts.",
        defaultRange = "Speed bands",
        overrideSummary = "Overrides speed bands for artifact inspection."
    ),
    Retraction(
        title = "Retraction",
        subtitle = "Retraction distance and stringing tower.",
        defaultRange = "Retraction steps",
        overrideSummary = "Overrides retraction distance and related tower settings."
    ),
    InputShapingFrequency(
        title = "Input Shaping Frequency",
        subtitle = "Ringing tower for frequency selection.",
        defaultRange = "Frequency sweep",
        overrideSummary = "Overrides speed, acceleration, and ringing pattern settings."
    ),
    InputShapingDamping(
        title = "Input Shaping Damping",
        subtitle = "Damping comparison for input shaping.",
        defaultRange = "Damping sweep",
        overrideSummary = "Overrides damping test parameters."
    ),
    Cornering(
        title = "Cornering",
        subtitle = "Square-corner velocity calibration.",
        defaultRange = "Cornering speeds",
        overrideSummary = "Overrides speed and cornering stress-test parameters."
    ),
    Tolerance(
        title = "Tolerance",
        subtitle = "Check dimensional clearance and part fit.",
        defaultRange = "Fit test",
        overrideSummary = "Uses the bundled tolerance model with the selected profiles."
    )
}

internal data class CalibrationOptions(
    val extruderType: String = "DDE",
    val method: String = "PA Tower",
    val filamentType: String = "PLA",
    val startValue: String,
    val endValue: String,
    val stepValue: String,
    val printNumbers: Boolean = false,
    val flowPass: String = FLOW_RATE_COMPLETE,
    val flowRatioBaseline: String = "1.00",
    val patternAccelerations: String = "",
    val patternSpeeds: String = "",
    val testModel: String = CALIBRATION_TEST_MODEL_RINGING,
    val shaperType: String = ""
) {
    fun summary(type: CalibrationType): String = when (type) {
        CalibrationType.PressureAdvance ->
            "$extruderType • $method • PA $startValue to $endValue step $stepValue"
        CalibrationType.TemperatureTower ->
            "$filamentType • $startValue°C to $endValue°C step $stepValue°C"
        CalibrationType.MaxVolumetricSpeed ->
            "$startValue to $endValue mm3/s step $stepValue"
        CalibrationType.FlowRate ->
            if (flowPass == FLOW_RATE_FINE) {
                "$flowPass • base flow ${validatedFlowRatio(flowRatioBaseline)}"
            } else {
                flowPass
            }
        CalibrationType.Retraction ->
            "Retraction $startValue to $endValue mm step $stepValue"
        CalibrationType.Vfa ->
            "Speed $startValue to $endValue mm/s step $stepValue"
        CalibrationType.InputShapingFrequency ->
            "Frequency $startValue to $endValue Hz step $stepValue"
        CalibrationType.InputShapingDamping ->
            "Damping $startValue to $endValue step $stepValue"
        CalibrationType.Cornering ->
            "Cornering $startValue to $endValue mm/s step $stepValue"
        CalibrationType.Tolerance ->
            "Tolerance test model"
    }

    fun toMetadataJson(type: CalibrationType): JSONObject = JSONObject()
        .put("type", type.name)
        .put("title", type.title)
        .put("extruder_type", extruderType)
        .put("method", method)
        .put("filament_type", filamentType)
        .put("start", startValue)
        .put("end", endValue)
        .put("step", stepValue)
        .put("print_numbers", printNumbers)
        .put("flow_pass", flowPass)
        .put("flow_ratio_baseline", validatedFlowRatio(flowRatioBaseline))
        .put("pattern_accelerations", patternAccelerations)
        .put("pattern_speeds", patternSpeeds)
        .put("test_model", testModel)
        .put("shaper_type", shaperType)
}

internal const val FLOW_RATE_COMPLETE = "Complete Calibration"
internal const val FLOW_RATE_FINE = "Fine Calibration based on flow ratio"
internal const val CALIBRATION_TEST_MODEL_RINGING = "Ringing Tower"
internal const val CALIBRATION_TEST_MODEL_FAST = "Fast Tower"
internal const val CALIBRATION_TEST_MODEL_CORNERING = "Cornering"

private fun validatedFlowRatio(value: String): String {
    val ratio = value.toDoubleOrNull()
    return if (ratio != null && ratio > 0.0 && ratio < 2.0) {
        String.format(Locale.US, "%.3f", ratio).trimEnd('0').trimEnd('.')
    } else {
        "1"
    }
}

internal data class CalibrationJob(
    val type: CalibrationType,
    val printerName: String,
    val filamentName: String,
    val processName: String,
    val nozzleDiameterMm: Float,
    val options: CalibrationOptions
) {
    val modelLabel: String = "${type.title} Calibration"

    fun gcodeFileName(): String = when (type) {
        CalibrationType.FlowRate -> "Filament Flow Rate Calibration.gcode"
        else -> "${type.title} Calibration.gcode"
    }

    fun workspaceStatus(): String = buildString {
        append("Calibration added to plate: ")
        append(type.title)
        append('\n')
        append("Profiles: ")
        append(printerName)
        append(" / ")
        append(filamentName)
        append(" / ")
        append(processName)
        append('\n')
        append("Options: ")
        append(options.summary(type))
        append('\n')
        append(type.overrideSummary)
        append('\n')
        append("Overrides are temporary for this calibration slice and do not change saved profiles.")
    }

    fun applyTemporaryOverrides(configJson: String): String {
        val json = runCatching { JSONObject(configJson) }.getOrElse { JSONObject() }
        json.put("mobile_slicer_calibration", options.toMetadataJson(type))
        json.put("mobile_slicer_calibration_active", true)
        json.put("mobile_slicer_calibration_type", type.name)
        json.put("mobile_slicer_calibration_nozzle_diameter_mm", nozzleDiameterMm.toDouble())
        json.put("mobile_slicer_forced_process_profile", forcedProfileName())
        json.putBool("calibration_print_numbers", options.printNumbers)
        applySharedForcedCalibrationProfile(json)
        when (type) {
            CalibrationType.PressureAdvance -> {
                applyPressureAdvanceProfile(json)
            }
            CalibrationType.FlowRate -> {
                applyFlowRateProfile(json)
            }
            CalibrationType.TemperatureTower -> {
                applyTemperatureProfile(json)
            }
            CalibrationType.MaxVolumetricSpeed -> {
                applyMaxVolumetricProfile(json)
            }
            CalibrationType.Retraction -> {
                applyRetractionProfile(json)
            }
            CalibrationType.Vfa -> {
                applyVfaProfile(json)
            }
            CalibrationType.InputShapingFrequency -> {
                applyInputShapingProfile(json, mode = "frequency")
            }
            CalibrationType.InputShapingDamping -> {
                applyInputShapingProfile(json, mode = "damping")
            }
            CalibrationType.Cornering -> {
                applyCorneringProfile(json)
            }
            CalibrationType.Tolerance -> {
                applyToleranceProfile(json)
            }
        }
        return json.toString()
    }

    private fun forcedProfileName(): String = "Calibration - ${type.title}"

    private fun applySharedForcedCalibrationProfile(json: JSONObject) {
        if (type != CalibrationType.FlowRate) {
            json.putBool("gcode_label_objects", false)
        }
        json.putBool("resonance_avoidance", false)
        json.putBool("enable_wrapping_detection", false)
        json.putBool("overhang_reverse", false)
        json.putBool("alternate_extra_wall", false)
        json.putStringValue("seam_slope_type", "none")
    }

    private fun applyPressureAdvanceProfile(json: JSONObject) {
        json.putBool("enable_pressure_advance", true)
        json.putNumber("pressure_advance", 0.0)
        json.putBool("adaptive_pressure_advance", false)
        json.putBool("filament_retract_when_changing_layer", false)
        json.putBool("filament_wipe", false)
        json.putBool("wipe", false)
        json.putBool("retract_when_changing_layer", false)
        json.putNumber("max_volumetric_extrusion_rate_slope", 0.0)
        json.putStringValue("calibration_pa_method", options.method)
        json.putStringValue("calibration_extruder_type", options.extruderType)
        json.putNumberString("calibration_pa_start", options.startValue)
        json.putNumberString("calibration_pa_end", options.endValue)
        json.putNumberString("calibration_pa_step", options.stepValue)
        json.putStringValue("calibration_pa_pattern_accelerations", options.patternAccelerations)
        json.putStringValue("calibration_pa_pattern_speeds", options.patternSpeeds)
        if (options.method == "PA Tower") {
            json.putNumber("slow_down_layer_time", 1.0)
            json.putNumber("outer_wall_speed", 80.0)
            json.putNumber("inner_wall_speed", 80.0)
            json.putStringValue("seam_position", "back")
            json.putNumber("wall_loops", 2)
            json.putNumber("top_shell_layers", 0)
            json.putNumber("bottom_shell_layers", 0)
            json.putNumber("sparse_infill_density", 0)
            json.putStringValue("brim_type", "brim_ears")
            json.putNumber("brim_object_gap", 0.0)
            json.putNumber("brim_ears_max_angle", 135.0)
            json.putNumber("brim_width", 6.0)
        } else {
            json.putStringValue("print_sequence", "by layer")
            json.putNumber("outer_wall_acceleration", 1200.0)
            json.putNumber("default_junction_deviation", 0.0)
            json.putNumber("wall_loops", 1)
            json.putNumber("sparse_infill_density", 0)
            json.putNumber("brim_width", 0)
        }
    }

    private fun applyFlowRateProfile(json: JSONObject) {
        val lineWidth = nozzleDiameterMm * 1.2f
        val layerHeight = nozzleDiameterMm / 2f
        val isFinePass = options.flowPass == FLOW_RATE_FINE
        if (isFinePass) {
            json.putNumberString("filament_flow_ratio", validatedFlowRatio(options.flowRatioBaseline), fallback = 1.0)
        }
        json.putNumber("print_flow_ratio", 1.0)
        json.putNumber("wall_loops", 1)
        json.putBool("only_one_wall_top", true)
        json.putBool("thick_internal_bridges", false)
        json.putStringValue("enable_extra_bridge_layer", "disabled")
        json.putNumber("internal_bridge_density", 100)
        json.putNumber("sparse_infill_density", 35)
        json.putStringValue("min_width_top_surface", "100%")
        json.putNumber("bottom_shell_layers", 2)
        json.putNumber("top_shell_layers", 5)
        json.putNumber("top_shell_thickness", 0)
        json.putNumber("bottom_shell_thickness", 0)
        json.putBool("detect_thin_wall", true)
        json.putNumber("filter_out_gap_fill", 0)
        json.putStringValue("sparse_infill_pattern", "rectilinear")
        json.putStringValue("top_surface_line_width", lineWidth.mmConfigString())
        json.putStringValue("internal_solid_infill_line_width", lineWidth.mmConfigString())
        json.putStringValue("top_surface_pattern", "archimedeanchords")
        json.putNumber("top_solid_infill_flow_ratio", 1.0)
        json.putNumber("infill_direction", 45)
        json.putNumber("solid_infill_direction", 135)
        json.putBool("align_infill_direction_to_model", true)
        json.putStringValue("ironing_type", "no ironing")
        json.putStringValue("gap_fill_target", "nowhere")
        json.putNumber("max_volumetric_extrusion_rate_slope", 0)
        json.putBool("calib_flowrate_topinfill_special_order", true)
        json.putNumber("layer_height", layerHeight)
        json.putNumber("initial_layer_print_height", layerHeight)
        json.putBool("reduce_crossing_wall", true)
        json.putStringValue("calibration_flow_pass", options.flowPass)
        json.putNumberString("calibration_flow_ratio_baseline", validatedFlowRatio(options.flowRatioBaseline), fallback = 1.0)
        json.putNumberString("calibration_flow_start", options.startValue)
        json.putNumberString("calibration_flow_end", options.endValue)
        json.putNumberString("calibration_flow_step", options.stepValue)
    }

    private fun applyTemperatureProfile(json: JSONObject) {
        val startTemp = options.startValue.toIntOrNull() ?: 230
        json.putNumber("nozzle_temperature_initial_layer", startTemp)
        json.putNumber("nozzle_temperature", startTemp)
        json.putStringValue("filament_type", options.filamentType)
        json.putStringValue("brim_type", "outer_only")
        json.putNumber("brim_width", 5.0)
        json.putNumber("brim_object_gap", 0.0)
        json.putNumberString("calibration_temp_start", options.startValue)
        json.putNumberString("calibration_temp_end", options.endValue)
        json.putNumberString("calibration_temp_step", options.stepValue)
    }

    private fun applyMaxVolumetricProfile(json: JSONObject) {
        val lineWidth = nozzleDiameterMm * 1.75f
        val layerHeight = nozzleDiameterMm * 0.8f
        json.putNumber("filament_max_volumetric_speed", 200.0)
        json.putNumber("slow_down_layer_time", 0.0)
        json.putBool("enable_overhang_speed", false)
        json.putNumber("wall_loops", 1)
        json.putNumber("top_shell_layers", 0)
        json.putNumber("bottom_shell_layers", 0)
        json.putNumber("sparse_infill_density", 0)
        json.putStringValue("outer_wall_line_width", lineWidth.mmConfigString())
        json.putNumber("layer_height", layerHeight)
        json.putStringValue("brim_type", "outer_and_inner")
        json.putNumber("brim_width", 5.0)
        json.putNumber("brim_object_gap", 0.0)
        json.putStringValue("timelapse_type", "0")
        json.putBool("spiral_mode", true)
        json.putNumber("max_volumetric_extrusion_rate_slope", 0.0)
        json.putNumberString("calibration_volumetric_start", options.startValue)
        json.putNumberString("calibration_volumetric_end", options.endValue)
        json.putNumberString("calibration_volumetric_step", options.stepValue)
    }

    private fun applyRetractionProfile(json: JSONObject) {
        val layerHeight = when {
            nozzleDiameterMm <= 0.1f -> 0.05f
            nozzleDiameterMm <= 0.2f -> 0.1f
            else -> 0.2f
        }
        json.putBool("use_firmware_retraction", false)
        json.putNumberString("filament_retraction_length", options.startValue, fallback = 0.0)
        json.putNumberString("retraction_length", options.startValue, fallback = 0.0)
        json.putNumber("wall_loops", 2)
        json.putNumber("top_shell_layers", 0)
        json.putNumber("bottom_shell_layers", 3)
        json.putNumber("sparse_infill_density", 0)
        json.putNumber("layer_height", layerHeight)
        json.putNumber("initial_layer_print_height", layerHeight)
        json.putStringValue("seam_position", "aligned")
        json.putStringValue("wall_sequence", "inner wall/outer wall")
        json.putNumberString("calibration_retraction_start", options.startValue)
        json.putNumberString("calibration_retraction_end", options.endValue)
        json.putNumberString("calibration_retraction_step", options.stepValue)
    }

    private fun applyVfaProfile(json: JSONObject) {
        json.putNumber("slow_down_layer_time", 0.0)
        json.putBool("enable_overhang_speed", false)
        json.putStringValue("timelapse_type", "0")
        json.putNumber("wall_loops", 1)
        json.putNumber("top_shell_layers", 0)
        json.putNumber("bottom_shell_layers", 1)
        json.putNumber("sparse_infill_density", 0)
        json.putBool("detect_thin_wall", false)
        json.putBool("spiral_mode", true)
        json.putStringValue("brim_type", "outer_only")
        json.putNumber("brim_width", 3.0)
        json.putNumber("brim_object_gap", 0.0)
        json.putNumberString("calibration_vfa_start", options.startValue)
        json.putNumberString("calibration_vfa_end", options.endValue)
        json.putNumberString("calibration_vfa_step", options.stepValue)
    }

    private fun applyInputShapingProfile(json: JSONObject, mode: String) {
        json.putBool("enable_pressure_advance", true)
        json.putNumber("pressure_advance", 0.0)
        json.putBool("adaptive_pressure_advance", false)
        json.putNumber("slow_down_layer_time", 0.0)
        json.putNumber("slow_down_min_speed", 0.0)
        json.putBool("slow_down_for_layer_cooling", false)
        json.putBool("enable_overhang_speed", false)
        json.putStringValue("timelapse_type", "0")
        json.putNumber("wall_loops", 1)
        json.putNumber("top_shell_layers", 0)
        json.putNumber("bottom_shell_layers", 1)
        json.putNumber("sparse_infill_density", 0)
        json.putBool("detect_thin_wall", false)
        json.putBool("spiral_mode", true)
        json.putBool("spiral_mode_smooth", false)
        json.putStringValue("bottom_surface_pattern", "rectilinear")
        json.putNumber("outer_wall_speed", 200.0)
        json.putNumber("default_acceleration", 20000.0)
        json.putNumber("outer_wall_acceleration", 20000.0)
        json.putStringValue("brim_type", "outer_only")
        json.putNumber("brim_width", 3.0)
        json.putNumber("brim_object_gap", 0.0)
        json.putNumber("calibration_test_model", options.testModel.toOrcaCalibrationTestModelIndex(default = 0))
        json.putStringValue("calibration_shaper_type", options.shaperType)
        json.putStringValue("calibration_input_shaping_mode", mode)
        json.putNumberString("calibration_input_shaping_start", options.startValue)
        json.putNumberString("calibration_input_shaping_end", options.endValue)
        json.putNumberString("calibration_input_shaping_step", options.stepValue)
    }

    private fun applyCorneringProfile(json: JSONObject) {
        json.putNumberString("machine_max_jerk_x", options.endValue, fallback = 20.0)
        json.putNumberString("machine_max_jerk_y", options.endValue, fallback = 20.0)
        json.putNumber("default_jerk", 0.0)
        json.putBool("enable_pressure_advance", true)
        json.putNumber("pressure_advance", 0.0)
        json.putBool("adaptive_pressure_advance", false)
        json.putNumber("slow_down_layer_time", 0.0)
        json.putNumber("slow_down_min_speed", 0.0)
        json.putBool("slow_down_for_layer_cooling", false)
        json.putNumber("filament_max_volumetric_speed", 200.0)
        json.putBool("enable_overhang_speed", false)
        json.putStringValue("timelapse_type", "0")
        json.putNumber("wall_loops", 1)
        json.putNumber("top_shell_layers", 0)
        json.putNumber("bottom_shell_layers", 1)
        json.putNumber("sparse_infill_density", 0)
        json.putBool("detect_thin_wall", false)
        json.putBool("spiral_mode", true)
        json.putBool("spiral_mode_smooth", false)
        json.putStringValue("bottom_surface_pattern", "rectilinear")
        json.putNumber("outer_wall_speed", 200.0)
        json.putNumber("default_acceleration", 2000.0)
        json.putNumber("outer_wall_acceleration", 2000.0)
        json.putStringValue("brim_type", "outer_only")
        json.putNumber("brim_width", 3.0)
        json.putNumber("brim_object_gap", 0.0)
        json.putNumber("calibration_test_model", options.testModel.toOrcaCalibrationTestModelIndex(default = 2))
        json.putStringValue("calibration_shaper_type", options.shaperType)
        json.putNumberString("calibration_cornering_start", options.startValue)
        json.putNumberString("calibration_cornering_end", options.endValue)
        json.putNumberString("calibration_cornering_step", options.stepValue)
    }

    private fun applyToleranceProfile(json: JSONObject) {
        json.putBool("mobile_slicer_calibration_tolerance_model", true)
    }

    fun orcaCalibrationAssetPath(): String = orcaCalibrationAssetPaths().first()

    fun orcaCalibrationAssetPaths(): List<String> = when (type) {
        CalibrationType.PressureAdvance -> when (options.method) {
            "PA Line" -> "calib_stl/pressure_advance/pressure_advance_test.stl"
            "PA Pattern" -> "calib_stl/pressure_advance/pa_pattern.stl"
            else -> "calib_stl/pressure_advance/tower_with_seam.stl"
        }.let(::listOf)
        CalibrationType.FlowRate -> when (options.flowPass) {
            FLOW_RATE_FINE -> listOf(
                "calib_stl/filament_flow/flowrate-test-pass2-objects/flowrate_0.stl",
                "calib_stl/filament_flow/flowrate-test-pass2-objects/flowrate_m1.stl",
                "calib_stl/filament_flow/flowrate-test-pass2-objects/flowrate_m2.stl",
                "calib_stl/filament_flow/flowrate-test-pass2-objects/flowrate_m3.stl",
                "calib_stl/filament_flow/flowrate-test-pass2-objects/flowrate_m4.stl",
                "calib_stl/filament_flow/flowrate-test-pass2-objects/flowrate_m5.stl",
                "calib_stl/filament_flow/flowrate-test-pass2-objects/flowrate_m6.stl",
                "calib_stl/filament_flow/flowrate-test-pass2-objects/flowrate_m7.stl",
                "calib_stl/filament_flow/flowrate-test-pass2-objects/flowrate_m8.stl",
                "calib_stl/filament_flow/flowrate-test-pass2-objects/flowrate_m9.stl"
            )
            else -> listOf(
                "calib_stl/filament_flow/flowrate-test-pass1-objects/flowrate_0.stl",
                "calib_stl/filament_flow/flowrate-test-pass1-objects/flowrate_10.stl",
                "calib_stl/filament_flow/flowrate-test-pass1-objects/flowrate_15.stl",
                "calib_stl/filament_flow/flowrate-test-pass1-objects/flowrate_20.stl",
                "calib_stl/filament_flow/flowrate-test-pass1-objects/flowrate_5.stl",
                "calib_stl/filament_flow/flowrate-test-pass1-objects/flowrate_m10.stl",
                "calib_stl/filament_flow/flowrate-test-pass1-objects/flowrate_m15.stl",
                "calib_stl/filament_flow/flowrate-test-pass1-objects/flowrate_m20.stl",
                "calib_stl/filament_flow/flowrate-test-pass1-objects/flowrate_m5.stl"
            )
        }
        CalibrationType.TemperatureTower -> listOf("calib_stl/temperature_tower/temperature_tower.stl")
        CalibrationType.MaxVolumetricSpeed -> listOf("calib_stl/volumetric_speed/SpeedTestStructure.stl")
        CalibrationType.Vfa -> listOf("calib_stl/vfa/vfa.stl")
        CalibrationType.Retraction -> listOf("calib_stl/retraction/retraction_tower.stl")
        CalibrationType.InputShapingFrequency -> listOf("calib_stl/input_shaping/ringing_tower.stl")
        CalibrationType.InputShapingDamping -> listOf("calib_stl/input_shaping/fast_tower_test.stl")
        CalibrationType.Cornering -> listOf("calib_stl/cornering/SCV-V2.stl")
        CalibrationType.Tolerance -> listOf("calib_stl/tolerance/OrcaToleranceTest.stl")
    }

    fun preservesAssetPlacement(): Boolean = type == CalibrationType.FlowRate
}

internal fun CalibrationJob.unsupportedFirmwareMessage(gcodeFlavor: GcodeFlavor): String? {
    if (type != CalibrationType.InputShapingFrequency && type != CalibrationType.InputShapingDamping) {
        return null
    }
    return when (gcodeFlavor) {
        GcodeFlavor.Marlin2,
        GcodeFlavor.Klipper,
        GcodeFlavor.RepRapFirmware -> null
        GcodeFlavor.MarlinLegacy ->
            "Input shaping requires firmware support.\nSet this printer profile's G-code flavor to Marlin 2, Klipper, or RepRapFirmware after confirming the printer firmware supports input shaping."
    }
}

private fun String.toOrcaCalibrationTestModelIndex(default: Int): Int = when (this) {
    CALIBRATION_TEST_MODEL_RINGING -> 0
    CALIBRATION_TEST_MODEL_FAST -> 1
    CALIBRATION_TEST_MODEL_CORNERING -> 2
    else -> default
}

private fun JSONObject.putNumber(key: String, value: Number) {
    put(key, value)
}

private fun JSONObject.putNumberString(key: String, value: String, fallback: Double = 0.0) {
    put(key, value.toDoubleOrNull() ?: fallback)
}

private fun JSONObject.putBool(key: String, value: Boolean) {
    put(key, value)
}

private fun JSONObject.putStringValue(key: String, value: String) {
    put(key, value)
}

private fun Float.mmConfigString(): String = String.format(Locale.US, "%.3f", this).trimEnd('0').trimEnd('.')

internal fun defaultCalibrationOptions(type: CalibrationType, filamentName: String): CalibrationOptions = when (type) {
    CalibrationType.PressureAdvance -> pressureAdvanceDefaults(method = "PA Tower", extruderType = "DDE")
    CalibrationType.FlowRate -> CalibrationOptions(
        startValue = "0.95",
        endValue = "1.05",
        stepValue = "0.01",
        flowPass = FLOW_RATE_COMPLETE,
        flowRatioBaseline = "1.00"
    )
    CalibrationType.TemperatureTower -> {
        val material = filamentName.uppercase(Locale.US).substringBefore(" ")
        val (filamentType, start, end) = when {
            "ABS" in material || "ASA" in material -> Triple("ABS/ASA", "270", "230")
            "PCTG" in material -> Triple("PCTG", "280", "240")
            "PETG" in material -> Triple("PETG", "250", "230")
            "TPU" in material -> Triple("TPU", "240", "210")
            "PA-CF" in material -> Triple("PA-CF", "320", "280")
            "PET-CF" in material -> Triple("PET-CF", "320", "280")
            else -> Triple("PLA", "230", "190")
        }
        CalibrationOptions(
            filamentType = filamentType,
            startValue = start,
            endValue = end,
            stepValue = "5"
        )
    }
    CalibrationType.MaxVolumetricSpeed -> CalibrationOptions(
        startValue = "5",
        endValue = "20",
        stepValue = "0.5"
    )
    CalibrationType.Vfa -> CalibrationOptions(
        startValue = "40",
        endValue = "200",
        stepValue = "10"
    )
    CalibrationType.Retraction -> CalibrationOptions(
        startValue = "0",
        endValue = "2",
        stepValue = "0.1"
    )
    CalibrationType.InputShapingFrequency -> CalibrationOptions(
        startValue = "15",
        endValue = "60",
        stepValue = "5",
        testModel = CALIBRATION_TEST_MODEL_RINGING
    )
    CalibrationType.InputShapingDamping -> CalibrationOptions(
        startValue = "0.05",
        endValue = "0.30",
        stepValue = "0.05",
        testModel = CALIBRATION_TEST_MODEL_FAST
    )
    CalibrationType.Cornering -> CalibrationOptions(
        startValue = "5",
        endValue = "20",
        stepValue = "1",
        testModel = CALIBRATION_TEST_MODEL_CORNERING
    )
    CalibrationType.Tolerance -> CalibrationOptions(
        startValue = "0",
        endValue = "0",
        stepValue = "0"
    )
}

internal fun pressureAdvanceDefaults(method: String, extruderType: String): CalibrationOptions {
    val isBowden = extruderType == "Bowden"
    val (endValue, stepValue, printNumbers) = when (method) {
        "PA Pattern" -> Triple(
            if (isBowden) "1.0" else "0.08",
            if (isBowden) "0.05" else "0.005",
            true
        )
        "PA Line" -> Triple(
            if (isBowden) "1.0" else "0.1",
            if (isBowden) "0.02" else "0.002",
            true
        )
        else -> Triple(
            if (isBowden) "1.0" else "0.1",
            if (isBowden) "0.02" else "0.002",
            false
        )
    }
    return CalibrationOptions(
        extruderType = extruderType,
        method = method,
        startValue = "0",
        endValue = endValue,
        stepValue = stepValue,
        printNumbers = printNumbers
    )
}

internal fun writeOrcaCalibrationModels(context: Context, targetDir: File, job: CalibrationJob): List<File> {
    targetDir.mkdirs()
    val timestamp = System.currentTimeMillis()
    return job.orcaCalibrationAssetPaths().map { assetPath ->
        val assetName = assetPath.substringAfterLast('/').removeSuffix(".stl")
        val target = File(targetDir, "${job.type.name.lowercase(Locale.US)}-$timestamp-$assetName.stl")
        context.assets.open(assetPath).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        target
    }
}

internal fun writeOrcaCalibrationStl(context: Context, target: File, job: CalibrationJob) {
    target.parentFile?.mkdirs()
    context.assets.open(job.orcaCalibrationAssetPath()).use { input ->
        target.outputStream().use { output ->
            input.copyTo(output)
        }
    }
}

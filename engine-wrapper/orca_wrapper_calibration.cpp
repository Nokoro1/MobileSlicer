#include "orca_wrapper_calibration.h"

#include "orca_wrapper_utils.h"

namespace mobileslicer::orca_wrapper {

Slic3r::Calib_Params extract_calibration_params(const std::string& json)
{
    Slic3r::Calib_Params params;
    params.mode = Slic3r::CalibMode::Calib_None;
    params.extruder_id = 0;
    params.start = 0.0;
    params.end = 0.0;
    params.step = 0.0;
    params.print_numbers = false;
    params.freqStartX = 0.0;
    params.freqEndX = 0.0;
    params.freqStartY = 0.0;
    params.freqEndY = 0.0;
    params.test_model = 0;
    params.shaper_type = "";

    if (const auto active = extract_bool(json, "mobile_slicer_calibration_active"); !active || !*active) {
        return params;
    }

    const auto type = extract_string(json, "mobile_slicer_calibration_type").value_or("");
    if (type == "PressureAdvance") {
        const auto method = extract_string(json, "calibration_pa_method").value_or("PA Tower");
        if (method == "PA Line") {
            params.mode = Slic3r::CalibMode::Calib_PA_Line;
        } else if (method == "PA Pattern") {
            params.mode = Slic3r::CalibMode::Calib_PA_Pattern;
        } else {
            params.mode = Slic3r::CalibMode::Calib_PA_Tower;
        }
        params.start = extract_number(json, "calibration_pa_start").value_or(0.0);
        params.end = extract_number(json, "calibration_pa_end").value_or(0.1);
        params.step = extract_number(json, "calibration_pa_step").value_or(0.002);
        params.print_numbers = extract_bool(json, "calibration_print_numbers").value_or(false);
        if (const auto accelerations = extract_string(json, "calibration_pa_pattern_accelerations")) {
            params.accelerations = parse_number_list(*accelerations);
        }
        if (const auto speeds = extract_string(json, "calibration_pa_pattern_speeds")) {
            params.speeds = parse_number_list(*speeds);
        }
    } else if (type == "FlowRate") {
        params.mode = Slic3r::CalibMode::Calib_Flow_Rate;
        params.start = extract_number(json, "calibration_flow_start").value_or(0.95);
        params.end = extract_number(json, "calibration_flow_end").value_or(1.05);
        params.step = extract_number(json, "calibration_flow_step").value_or(0.01);
    } else if (type == "TemperatureTower") {
        params.mode = Slic3r::CalibMode::Calib_Temp_Tower;
        params.start = extract_number(json, "calibration_temp_start").value_or(230.0);
        params.end = extract_number(json, "calibration_temp_end").value_or(190.0);
        params.step = extract_number(json, "calibration_temp_step").value_or(5.0);
    } else if (type == "MaxVolumetricSpeed") {
        params.mode = Slic3r::CalibMode::Calib_Vol_speed_Tower;
        params.start = extract_number(json, "calibration_volumetric_start").value_or(5.0);
        params.end = extract_number(json, "calibration_volumetric_end").value_or(20.0);
        params.step = extract_number(json, "calibration_volumetric_step").value_or(0.5);
    } else if (type == "Vfa") {
        params.mode = Slic3r::CalibMode::Calib_VFA_Tower;
        params.start = extract_number(json, "calibration_vfa_start").value_or(40.0);
        params.end = extract_number(json, "calibration_vfa_end").value_or(200.0);
        params.step = extract_number(json, "calibration_vfa_step").value_or(10.0);
    } else if (type == "Retraction") {
        params.mode = Slic3r::CalibMode::Calib_Retraction_tower;
        params.start = extract_number(json, "calibration_retraction_start").value_or(0.0);
        params.end = extract_number(json, "calibration_retraction_end").value_or(2.0);
        params.step = extract_number(json, "calibration_retraction_step").value_or(0.1);
    } else if (type == "InputShapingFrequency") {
        params.mode = Slic3r::CalibMode::Calib_Input_shaping_freq;
        params.step = extract_number(json, "calibration_input_shaping_step").value_or(5.0);
        params.freqStartX = extract_number(json, "calibration_input_shaping_start").value_or(15.0);
        params.freqEndX = extract_number(json, "calibration_input_shaping_end").value_or(60.0);
        params.freqStartY = params.freqStartX;
        params.freqEndY = params.freqEndX;
        params.test_model = static_cast<int>(extract_number(json, "calibration_test_model").value_or(0.0));
        params.shaper_type = extract_string(json, "calibration_shaper_type").value_or("");
    } else if (type == "InputShapingDamping") {
        params.mode = Slic3r::CalibMode::Calib_Input_shaping_damp;
        params.start = extract_number(json, "calibration_input_shaping_start").value_or(0.05);
        params.end = extract_number(json, "calibration_input_shaping_end").value_or(0.30);
        params.step = extract_number(json, "calibration_input_shaping_step").value_or(0.05);
        params.freqStartX = 40.0;
        params.freqEndX = 40.0;
        params.freqStartY = 40.0;
        params.freqEndY = 40.0;
        params.test_model = static_cast<int>(extract_number(json, "calibration_test_model").value_or(0.0));
        params.shaper_type = extract_string(json, "calibration_shaper_type").value_or("");
    } else if (type == "Cornering") {
        params.mode = Slic3r::CalibMode::Calib_Cornering;
        params.start = extract_number(json, "calibration_cornering_start").value_or(5.0);
        params.end = extract_number(json, "calibration_cornering_end").value_or(20.0);
        params.step = extract_number(json, "calibration_cornering_step").value_or(1.0);
        params.test_model = static_cast<int>(extract_number(json, "calibration_test_model").value_or(2.0));
        params.shaper_type = extract_string(json, "calibration_shaper_type").value_or("");
    }
    return params;
}

} // namespace mobileslicer::orca_wrapper

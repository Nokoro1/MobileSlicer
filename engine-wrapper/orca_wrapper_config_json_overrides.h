#ifndef ORCA_WRAPPER_CONFIG_JSON_OVERRIDES_H
#define ORCA_WRAPPER_CONFIG_JSON_OVERRIDES_H

// Private implementation header. Include through orca_wrapper_config_helpers.h after scalar helpers.
static void apply_android_runtime_gcode_baseline(Slic3r::DynamicPrintConfig& config)
{
    // Keep vendor start/end/custom G-code and thumbnail settings intact so
    // exported G-code follows the selected Orca printer profile.
    (void)config;
}

static void apply_json_overrides(const std::string& json, Slic3r::DynamicPrintConfig& config)
{
    if (json.empty()) {
        return;
    }

    if (const auto value = extract_number(json, "layer_height")) {
        config.set_deserialize_strict("layer_height", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "first_layer_height")) {
        const auto serialized = std::to_string(*value);
        // Orca's effective first-layer slicing path reads initial_layer_print_height.
        // Keep first_layer_height in sync as well so exported compatibility comments stay aligned.
        config.set_deserialize_strict("initial_layer_print_height", serialized);
        config.set_deserialize_strict("first_layer_height", serialized);
    }
    if (const auto value = extract_number(json, "top_shell_layers")) {
        config.set_deserialize_strict("top_shell_layers", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "bottom_shell_layers")) {
        config.set_deserialize_strict("bottom_shell_layers", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_string(json, "top_shell_thickness")) {
        config.set_deserialize_strict("top_shell_thickness", *value);
    } else if (const auto value = extract_number(json, "top_shell_thickness")) {
        config.set_deserialize_strict("top_shell_thickness", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "bottom_shell_thickness")) {
        config.set_deserialize_strict("bottom_shell_thickness", *value);
    } else if (const auto value = extract_number(json, "bottom_shell_thickness")) {
        config.set_deserialize_strict("bottom_shell_thickness", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "printer_settings_id")) {
        config.set_deserialize_strict("printer_settings_id", *value);
    }
    if (const auto value = extract_string(json, "filament_settings_id")) {
        config.set_deserialize_strict("filament_settings_id", *value);
    }
    if (const auto value = extract_string(json, "print_settings_id")) {
        config.set_deserialize_strict("print_settings_id", *value);
    }
    if (const auto value = extract_string(json, "default_filament_profile")) {
        config.set_deserialize_strict("default_filament_profile", *value);
    }
    if (const auto value = extract_string(json, "default_print_profile")) {
        config.set_deserialize_strict("default_print_profile", *value);
    }
    for (const char* key : {
        "accel_to_decel_factor",
        "default_jerk",
        "gap_fill_target",
        "host_type",
        "infill_jerk",
        "print_compatible_printers",
        "printhost_authorization_type",
        "thumbnails",
        "thumbnails_format",
        "top_surface_jerk",
        "travel_acceleration",
        "upward_compatible_machine",
        "wall_direction",
        "wipe_tower_cone_angle"
    }) {
        set_config_string_if_present(json, config, key);
    }
    for (const char* key : {
        "combine_brims",
        "exclude_object",
        "gcode_label_objects",
        "printhost_ssl_ignore_revoke",
        "slowdown_for_curled_perimeters"
    }) {
        set_config_bool_if_present(json, config, key);
    }
    if (const auto value = extract_string(json, "seam_position")) {
        config.set_deserialize_strict("seam_position", *value);
    }
    if (const auto value = extract_config_scalar_or_list_string(json, "bed_shape");
        value && !value->empty()) {
        config.set_deserialize_strict("printable_area", *value);
    } else if (const auto value = extract_config_scalar_or_list_string(json, "printable_area");
        value && !value->empty()) {
        config.set_deserialize_strict("printable_area", *value);
    }
    const auto bed_width = extract_number_any(json, {"bed_width_mm", "bed_width"});
    const auto bed_depth = extract_number_any(json, {"bed_depth_mm", "bed_depth"});
    const auto max_height = extract_number_any(json, {"max_height_mm", "printable_height"});
    if (bed_width && bed_depth && max_height) {
        apply_printable_volume_override(*bed_width, *bed_depth, *max_height, config);
    }
    if (const auto value = extract_string(json, "bed_exclude_area")) {
        if (!value->empty()) {
            config.set_deserialize_strict("bed_exclude_area", *value);
        }
    }
    if (const auto value = extract_string(json, "wrapping_exclude_area")) {
        if (!value->empty()) {
            config.set_deserialize_strict("wrapping_exclude_area", *value);
        }
    }
    if (const auto value = extract_string(json, "head_wrap_detect_zone")) {
        if (!value->empty()) {
            config.set_deserialize_strict("head_wrap_detect_zone", *value);
        }
    }
    if (const auto value = extract_bool(json, "support_multi_bed_types")) {
        config.set_deserialize_strict("support_multi_bed_types", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "default_bed_type")) {
        config.set_deserialize_strict("default_bed_type", *value);
    }
    if (const auto value = extract_string(json, "best_object_pos")) {
        if (!value->empty()) {
            config.set_deserialize_strict("best_object_pos", *value);
        }
    }
    if (const auto value = extract_number(json, "z_offset")) {
        config.set_deserialize_strict("z_offset", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "preferred_orientation")) {
        config.set_deserialize_strict("preferred_orientation", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "bed_mesh_min")) {
        if (!value->empty()) {
            config.set_deserialize_strict("bed_mesh_min", *value);
        }
    }
    if (const auto value = extract_string(json, "bed_mesh_max")) {
        if (!value->empty()) {
            config.set_deserialize_strict("bed_mesh_max", *value);
        }
    }
    if (const auto value = extract_string(json, "bed_mesh_probe_distance")) {
        if (!value->empty()) {
            config.set_deserialize_strict("bed_mesh_probe_distance", *value);
        }
    }
    if (const auto value = extract_number(json, "adaptive_bed_mesh_margin")) {
        config.set_deserialize_strict("adaptive_bed_mesh_margin", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "nozzle_diameter")) {
        config.set_deserialize_strict("nozzle_diameter", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "nozzle_volume")) {
        config.set_deserialize_strict("nozzle_volume", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "nozzle_volume_type")) {
        config.set_deserialize_strict("nozzle_volume_type", *value);
    }
    if (const auto value = extract_string(json, "default_nozzle_volume_type")) {
        config.set_deserialize_strict("default_nozzle_volume_type", *value);
    }
    if (const auto value = extract_number(json, "nozzle_height")) {
        config.set_deserialize_strict("nozzle_height", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "grab_length")) {
        config.set_deserialize_strict("grab_length", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "extruder_variant_list")) {
        config.set_deserialize_strict("extruder_variant_list", *value);
    }
    if (const auto value = extract_string(json, "printer_extruder_id")) {
        config.set_deserialize_strict("printer_extruder_id", *value);
    }
    if (const auto value = extract_string(json, "printer_extruder_variant")) {
        config.set_deserialize_strict("printer_extruder_variant", *value);
    }
    if (const auto value = extract_number(json, "master_extruder_id")) {
        config.set_deserialize_strict("master_extruder_id", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_string(json, "physical_extruder_map")) {
        config.set_deserialize_strict("physical_extruder_map", *value);
    }
    if (const auto value = extract_string(json, "extruder_ams_count")) {
        config.set_deserialize_strict("extruder_ams_count", *value);
    }
    if (const auto value = extract_string(json, "extruder_type")) {
        config.set_deserialize_strict("extruder_type", *value);
    }
    if (const auto value = extract_string(json, "extruder_colour")) {
        config.set_deserialize_strict("extruder_colour", *value);
    }
    if (const auto value = extract_number(json, "extruder_printable_height")) {
        config.set_deserialize_strict("extruder_printable_height", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "extruder_printable_area")) {
        if (!value->empty()) {
            config.set_deserialize_strict("extruder_printable_area", *value);
        }
    }
    if (const auto value = extract_number(json, "min_layer_height")) {
        config.set_deserialize_strict("min_layer_height", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "max_layer_height")) {
        config.set_deserialize_strict("max_layer_height", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "extruder_offset")) {
        if (!value->empty()) {
            config.set_deserialize_strict("extruder_offset", *value);
        }
    }
    if (const auto value = extract_string(json, "printer_model")) {
        config.set_deserialize_strict("printer_model", *value);
    }
    if (const auto value = extract_string(json, "printer_technology")) {
        config.set_deserialize_strict("printer_technology", *value);
    }
    if (const auto value = extract_string(json, "printer_variant")) {
        config.set_deserialize_strict("printer_variant", *value);
    }
    if (const auto value = extract_string(json, "printer_structure")) {
        config.set_deserialize_strict("printer_structure", *value);
    }
    if (const auto value = extract_string(json, "gcode_flavor")) {
        config.set_deserialize_strict("gcode_flavor", *value);
    }
    if (const auto value = extract_bool(json, "pellet_modded_printer")) {
        config.set_deserialize_strict("pellet_modded_printer", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "bbl_use_printhost")) {
        config.set_deserialize_strict("bbl_use_printhost", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "scan_first_layer")) {
        config.set_deserialize_strict("scan_first_layer", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "printer_notes")) {
        config.set_deserialize_strict("printer_notes", *value);
    }
    if (const auto value = extract_bool(json, "use_relative_e_distances")) {
        config.set_deserialize_strict("use_relative_e_distances", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "use_firmware_retraction")) {
        config.set_deserialize_strict("use_firmware_retraction", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "enable_power_loss_recovery")) {
        config.set_deserialize_strict("enable_power_loss_recovery", *value);
    }
    if (const auto value = extract_bool(json, "disable_m73")) {
        config.set_deserialize_strict("disable_m73", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "printer_agent")) {
        config.set_deserialize_strict("printer_agent", *value);
    }
    if (const auto value = extract_number(json, "time_cost")) {
        config.set_deserialize_strict("time_cost", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "fan_speedup_time")) {
        config.set_deserialize_strict("fan_speedup_time", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "fan_speedup_overhangs")) {
        config.set_deserialize_strict("fan_speedup_overhangs", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "fan_kickstart")) {
        config.set_deserialize_strict("fan_kickstart", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "extruder_clearance_radius")) {
        config.set_deserialize_strict("extruder_clearance_radius", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "extruder_clearance_max_radius")) {
        config.set_deserialize_strict("extruder_clearance_radius", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "extruder_clearance_height_to_rod")) {
        config.set_deserialize_strict("extruder_clearance_height_to_rod", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "extruder_clearance_height_to_lid")) {
        config.set_deserialize_strict("extruder_clearance_height_to_lid", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "nozzle_type")) {
        config.set_deserialize_strict("nozzle_type", *value);
    }
    if (const auto value = extract_bool(json, "auxiliary_fan")) {
        config.set_deserialize_strict("auxiliary_fan", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "support_object_skip_flush")) {
        config.set_deserialize_strict("support_object_skip_flush", std::to_string(static_cast<int>(*value)));
    } else if (const auto value = extract_bool(json, "support_object_skip_flush")) {
        config.set_deserialize_strict("support_object_skip_flush", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "enable_long_retraction_when_cut")) {
        config.set_deserialize_strict("enable_long_retraction_when_cut", std::to_string(static_cast<int>(*value)));
    } else if (const auto value = extract_bool(json, "enable_long_retraction_when_cut")) {
        config.set_deserialize_strict("enable_long_retraction_when_cut", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "support_chamber_temp_control")) {
        config.set_deserialize_strict("support_chamber_temp_control", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "support_air_filtration")) {
        config.set_deserialize_strict("support_air_filtration", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "single_extruder_multi_material")) {
        config.set_deserialize_strict("single_extruder_multi_material", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "manual_filament_change")) {
        config.set_deserialize_strict("manual_filament_change", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "bed_temperature_formula")) {
        config.set_deserialize_strict("bed_temperature_formula", *value);
    }
    if (const auto value = extract_string(json, "wipe_tower_type")) {
        config.set_deserialize_strict("wipe_tower_type", *value);
    }
    if (const auto value = extract_string(json, "wipe_tower_x")) {
        config.set_deserialize_strict("wipe_tower_x", *value);
    } else if (const auto value = extract_number(json, "wipe_tower_x")) {
        config.set_deserialize_strict("wipe_tower_x", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "wipe_tower_y")) {
        config.set_deserialize_strict("wipe_tower_y", *value);
    } else if (const auto value = extract_number(json, "wipe_tower_y")) {
        config.set_deserialize_strict("wipe_tower_y", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "purge_in_prime_tower")) {
        config.set_deserialize_strict("purge_in_prime_tower", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "enable_filament_ramming")) {
        config.set_deserialize_strict("enable_filament_ramming", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "cooling_tube_retraction")) {
        config.set_deserialize_strict("cooling_tube_retraction", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "cooling_tube_length")) {
        config.set_deserialize_strict("cooling_tube_length", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "parking_pos_retraction")) {
        config.set_deserialize_strict("parking_pos_retraction", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "extra_loading_move")) {
        config.set_deserialize_strict("extra_loading_move", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "high_current_on_filament_swap")) {
        config.set_deserialize_strict("high_current_on_filament_swap", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "machine_load_filament_time")) {
        config.set_deserialize_strict("machine_load_filament_time", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_unload_filament_time")) {
        config.set_deserialize_strict("machine_unload_filament_time", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_tool_change_time")) {
        config.set_deserialize_strict("machine_tool_change_time", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_switch_extruder_time")) {
        config.set_deserialize_strict("machine_tool_change_time", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "file_start_gcode")) {
        config.set_deserialize_strict("file_start_gcode", *value);
    }
    if (const auto value = extract_string(json, "machine_start_gcode")) {
        config.set_deserialize_strict("machine_start_gcode", *value);
    }
    if (const auto value = extract_string(json, "machine_end_gcode")) {
        config.set_deserialize_strict("machine_end_gcode", *value);
    }
    if (const auto value = extract_string(json, "printing_by_object_gcode")) {
        config.set_deserialize_strict("printing_by_object_gcode", *value);
    }
    if (const auto value = extract_string(json, "before_layer_change_gcode")) {
        config.set_deserialize_strict("before_layer_change_gcode", *value);
    }
    if (const auto value = extract_string(json, "layer_change_gcode")) {
        config.set_deserialize_strict("layer_change_gcode", *value);
    }
    if (const auto value = extract_string(json, "time_lapse_gcode")) {
        config.set_deserialize_strict("time_lapse_gcode", *value);
    }
    if (const auto value = extract_string(json, "wrapping_detection_gcode")) {
        config.set_deserialize_strict("wrapping_detection_gcode", *value);
    }
    if (const auto value = extract_string_any(json, {"change_filament_gcode", "toolchange_gcode"})) {
        config.set_deserialize_strict("change_filament_gcode", *value);
    }
    if (const auto value = extract_string(json, "change_extrusion_role_gcode")) {
        config.set_deserialize_strict("change_extrusion_role_gcode", *value);
    }
    if (const auto value = extract_string_any(json, {"machine_pause_gcode", "pause_gcode"})) {
        config.set_deserialize_strict("machine_pause_gcode", *value);
    }
    if (const auto value = extract_string(json, "template_custom_gcode")) {
        config.set_deserialize_strict("template_custom_gcode", *value);
    }
    if (const auto value = extract_bool(json, "emit_machine_limits_to_gcode")) {
        config.set_deserialize_strict("emit_machine_limits_to_gcode", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "resonance_avoidance")) {
        config.set_deserialize_strict("resonance_avoidance", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "silent_mode")) {
        config.set_deserialize_strict("silent_mode", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "min_resonance_avoidance_speed")) {
        config.set_deserialize_strict("min_resonance_avoidance_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "max_resonance_avoidance_speed")) {
        config.set_deserialize_strict("max_resonance_avoidance_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_speed_x")) {
        config.set_deserialize_strict("machine_max_speed_x", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_speed_y")) {
        config.set_deserialize_strict("machine_max_speed_y", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_speed_z")) {
        config.set_deserialize_strict("machine_max_speed_z", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_speed_e")) {
        config.set_deserialize_strict("machine_max_speed_e", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_acceleration_x")) {
        config.set_deserialize_strict("machine_max_acceleration_x", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_acceleration_y")) {
        config.set_deserialize_strict("machine_max_acceleration_y", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_acceleration_z")) {
        config.set_deserialize_strict("machine_max_acceleration_z", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_acceleration_e")) {
        config.set_deserialize_strict("machine_max_acceleration_e", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_acceleration_extruding")) {
        config.set_deserialize_strict("machine_max_acceleration_extruding", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_acceleration_retracting")) {
        config.set_deserialize_strict("machine_max_acceleration_retracting", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_acceleration_travel")) {
        config.set_deserialize_strict("machine_max_acceleration_travel", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_jerk_x")) {
        config.set_deserialize_strict("machine_max_jerk_x", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_jerk_y")) {
        config.set_deserialize_strict("machine_max_jerk_y", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_jerk_z")) {
        config.set_deserialize_strict("machine_max_jerk_z", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_jerk_e")) {
        config.set_deserialize_strict("machine_max_jerk_e", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_max_junction_deviation")) {
        config.set_deserialize_strict("machine_max_junction_deviation", std::to_string(*value));
    }
    for (const char* key : {
        "machine_max_speed_x",
        "machine_max_speed_y",
        "machine_max_speed_z",
        "machine_max_speed_e",
        "machine_max_acceleration_x",
        "machine_max_acceleration_y",
        "machine_max_acceleration_z",
        "machine_max_acceleration_e",
        "machine_max_acceleration_extruding",
        "machine_max_acceleration_retracting",
        "machine_max_acceleration_travel",
        "machine_max_jerk_x",
        "machine_max_jerk_y",
        "machine_max_jerk_z",
        "machine_max_jerk_e",
        "machine_min_extruding_rate",
        "machine_min_travel_rate"
    }) {
        if (std::string(key) == "extruder_printable_area") {
            set_config_points_groups_if_present(json, config, key);
        } else {
            set_config_string_if_present(json, config, key);
        }
    }
    if (const auto value = extract_number(json, "machine_min_extruding_rate")) {
        config.set_deserialize_strict("machine_min_extruding_rate", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "machine_min_travel_rate")) {
        config.set_deserialize_strict("machine_min_travel_rate", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "retraction_length")) {
        config.set_deserialize_strict("retraction_length", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "retract_restart_extra")) {
        config.set_deserialize_strict("retract_restart_extra", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "retraction_speed")) {
        config.set_deserialize_strict("retraction_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "deretraction_speed")) {
        config.set_deserialize_strict("deretraction_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "retraction_minimum_travel")) {
        config.set_deserialize_strict("retraction_minimum_travel", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "retract_when_changing_layer")) {
        config.set_deserialize_strict("retract_when_changing_layer", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "wipe")) {
        config.set_deserialize_strict("wipe", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "wipe_distance")) {
        config.set_deserialize_strict("wipe_distance", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "retract_before_wipe")) {
        config.set_deserialize_strict("retract_before_wipe", std::to_string(static_cast<int>(*value)) + "%");
    }
    if (const auto value = extract_string(json, "retract_lift_enforce")) {
        config.set_deserialize_strict("retract_lift_enforce", *value);
    }
    if (const auto value = extract_string(json, "z_hop_types")) {
        config.set_deserialize_strict("z_hop_types", *value);
    }
    if (const auto value = extract_number(json, "z_hop")) {
        config.set_deserialize_strict("z_hop", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "travel_slope")) {
        config.set_deserialize_strict("travel_slope", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "retract_lift_above")) {
        config.set_deserialize_strict("retract_lift_above", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "retract_lift_below")) {
        config.set_deserialize_strict("retract_lift_below", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "retract_length_toolchange")) {
        config.set_deserialize_strict("retract_length_toolchange", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "retract_restart_extra_toolchange")) {
        config.set_deserialize_strict("retract_restart_extra_toolchange", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_diameter")) {
        config.set_deserialize_strict("filament_diameter", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "filament_type")) {
        config.set_deserialize_strict("filament_type", *value);
    }
    if (const auto value = extract_string(json, "filament_vendor")) {
        config.set_deserialize_strict("filament_vendor", *value);
    }
    if (const auto value = extract_bool(json, "filament_soluble")) {
        config.set_deserialize_strict("filament_soluble", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "filament_is_support")) {
        config.set_deserialize_strict("filament_is_support", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "filament_change_length")) {
        config.set_deserialize_strict("filament_change_length", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "filament_extruder_variant")) {
        config.set_deserialize_strict("filament_extruder_variant", *value);
    }
    if (const auto value = extract_string(json, "filament_self_index")) {
        config.set_deserialize_strict("filament_self_index", *value);
    }
    if (const auto value = extract_number(json, "required_nozzle_HRC")) {
        config.set_deserialize_strict("required_nozzle_HRC", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_string(json, "default_filament_colour")) {
        config.set_deserialize_strict("default_filament_colour", *value);
    }
    if (const auto value = extract_string(json, "filament_colour")) {
        config.set_deserialize_strict("filament_colour", *value);
    }
    if (const auto value = extract_string(json, "filament_multi_colour")) {
        config.set_deserialize_strict("filament_multi_colour", *value);
    }
    if (const auto value = extract_number(json, "filament_colour_type")) {
        config.set_deserialize_strict("filament_colour_type", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_string(json, "filament_settings_id")) {
        config.set_deserialize_strict("filament_settings_id", *value);
    }
    if (const auto value = extract_string(json, "filament_ids")) {
        config.set_deserialize_strict("filament_ids", *value);
    }
    if (const auto value = extract_string(json, "flush_volumes_matrix")) {
        config.set_deserialize_strict("flush_volumes_matrix", *value);
    }
    if (const auto value = extract_string(json, "flush_volumes_vector")) {
        config.set_deserialize_strict("flush_volumes_vector", *value);
    }
    if (const auto value = extract_number(json, "filament_adhesiveness_category")) {
        config.set_deserialize_strict("filament_adhesiveness_category", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "filament_density")) {
        if (*value > 0.0) {
            config.set_deserialize_strict("filament_density", std::to_string(*value));
        }
    }
    if (const auto value = extract_number(json, "filament_shrink")) {
        config.set_deserialize_strict("filament_shrink", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_shrinkage_compensation_z")) {
        config.set_deserialize_strict("filament_shrinkage_compensation_z", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_cost")) {
        config.set_deserialize_strict("filament_cost", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "temperature_vitrification")) {
        config.set_deserialize_strict("temperature_vitrification", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "idle_temperature")) {
        config.set_deserialize_strict("idle_temperature", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "nozzle_temperature_range_low")) {
        config.set_deserialize_strict("nozzle_temperature_range_low", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "nozzle_temperature_range_high")) {
        config.set_deserialize_strict("nozzle_temperature_range_high", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "filament_flow_ratio")) {
        config.set_deserialize_strict("filament_flow_ratio", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "pellet_flow_coefficient")) {
        config.set_deserialize_strict("pellet_flow_coefficient", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "enable_pressure_advance")) {
        config.set_deserialize_strict("enable_pressure_advance", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "pressure_advance")) {
        config.set_deserialize_strict("pressure_advance", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "adaptive_pressure_advance")) {
        config.set_deserialize_strict("adaptive_pressure_advance", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "adaptive_pressure_advance_overhangs")) {
        config.set_deserialize_strict("adaptive_pressure_advance_overhangs", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "adaptive_pressure_advance_bridges")) {
        config.set_deserialize_strict("adaptive_pressure_advance_bridges", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "adaptive_pressure_advance_model")) {
        config.set_deserialize_strict("adaptive_pressure_advance_model", Slic3r::escape_strings_cstyle(std::vector<std::string>{*value}));
    }
    if (const auto value = extract_number(json, "filament_max_volumetric_speed")) {
        config.set_deserialize_strict("filament_max_volumetric_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_retraction_length")) {
        config.set_deserialize_strict("filament_retraction_length", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_z_hop")) {
        config.set_deserialize_strict("filament_z_hop", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "filament_z_hop_types")) {
        config.set_deserialize_strict("filament_z_hop_types", *value);
    }
    if (const auto value = extract_number(json, "filament_retract_lift_above")) {
        config.set_deserialize_strict("filament_retract_lift_above", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_retract_lift_below")) {
        config.set_deserialize_strict("filament_retract_lift_below", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "filament_retract_lift_enforce")) {
        config.set_deserialize_strict("filament_retract_lift_enforce", *value);
    }
    if (const auto value = extract_number(json, "filament_retraction_speed")) {
        config.set_deserialize_strict("filament_retraction_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_deretraction_speed")) {
        config.set_deserialize_strict("filament_deretraction_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_retract_restart_extra")) {
        config.set_deserialize_strict("filament_retract_restart_extra", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_retraction_minimum_travel")) {
        config.set_deserialize_strict("filament_retraction_minimum_travel", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "filament_retract_when_changing_layer")) {
        config.set_deserialize_strict("filament_retract_when_changing_layer", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "filament_wipe")) {
        config.set_deserialize_strict("filament_wipe", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "filament_wipe_distance")) {
        config.set_deserialize_strict("filament_wipe_distance", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_retract_before_wipe")) {
        config.set_deserialize_strict("filament_retract_before_wipe", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_bool(json, "filament_long_retractions_when_cut")) {
        config.set_deserialize_strict("filament_long_retractions_when_cut", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "filament_retraction_distances_when_cut")) {
        if (!value->empty()) {
            config.set_deserialize_strict("filament_retraction_distances_when_cut", *value);
        }
    }
    if (const auto value = extract_number(json, "filament_ironing_flow")) {
        config.set_deserialize_strict("filament_ironing_flow", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_ironing_spacing")) {
        config.set_deserialize_strict("filament_ironing_spacing", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_ironing_inset")) {
        config.set_deserialize_strict("filament_ironing_inset", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_ironing_speed")) {
        config.set_deserialize_strict("filament_ironing_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "full_fan_speed_layer")) {
        config.set_deserialize_strict("full_fan_speed_layer", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "fan_cooling_layer_time")) {
        config.set_deserialize_strict("fan_cooling_layer_time", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "slow_down_layer_time")) {
        config.set_deserialize_strict("slow_down_layer_time", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "reduce_fan_stop_start_freq")) {
        config.set_deserialize_strict("reduce_fan_stop_start_freq", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "slow_down_for_layer_cooling")) {
        config.set_deserialize_strict("slow_down_for_layer_cooling", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "dont_slow_down_outer_wall")) {
        config.set_deserialize_strict("dont_slow_down_outer_wall", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "slow_down_min_speed")) {
        config.set_deserialize_strict("slow_down_min_speed", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "enable_overhang_bridge_fan")) {
        config.set_deserialize_strict("enable_overhang_bridge_fan", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "overhang_fan_threshold")) {
        config.set_deserialize_strict("overhang_fan_threshold", *value);
    }
    if (const auto value = extract_number(json, "overhang_fan_speed")) {
        config.set_deserialize_strict("overhang_fan_speed", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "internal_bridge_fan_speed")) {
        config.set_deserialize_strict("internal_bridge_fan_speed", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "support_material_interface_fan_speed")) {
        config.set_deserialize_strict("support_material_interface_fan_speed", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "ironing_fan_speed")) {
        config.set_deserialize_strict("ironing_fan_speed", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "additional_cooling_fan_speed")) {
        config.set_deserialize_strict("additional_cooling_fan_speed", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_bool(json, "activate_air_filtration")) {
        config.set_deserialize_strict("activate_air_filtration", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "during_print_exhaust_fan_speed")) {
        config.set_deserialize_strict("during_print_exhaust_fan_speed", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "complete_print_exhaust_fan_speed")) {
        config.set_deserialize_strict("complete_print_exhaust_fan_speed", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "filament_minimal_purge_on_wipe_tower")) {
        config.set_deserialize_strict("filament_minimal_purge_on_wipe_tower", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_tower_interface_pre_extrusion_dist")) {
        config.set_deserialize_strict("filament_tower_interface_pre_extrusion_dist", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_tower_interface_pre_extrusion_length")) {
        config.set_deserialize_strict("filament_tower_interface_pre_extrusion_length", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_tower_ironing_area")) {
        config.set_deserialize_strict("filament_tower_ironing_area", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_tower_interface_purge_volume")) {
        config.set_deserialize_strict("filament_tower_interface_purge_volume", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_tower_interface_print_temp")) {
        config.set_deserialize_strict("filament_tower_interface_print_temp", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_bool(json, "long_retractions_when_ec")) {
        config.set_deserialize_strict("long_retractions_when_ec", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "retraction_distances_when_ec")) {
        config.set_deserialize_strict("retraction_distances_when_ec", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_loading_speed_start")) {
        config.set_deserialize_strict("filament_loading_speed_start", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_loading_speed")) {
        config.set_deserialize_strict("filament_loading_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_unloading_speed_start")) {
        config.set_deserialize_strict("filament_unloading_speed_start", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_unloading_speed")) {
        config.set_deserialize_strict("filament_unloading_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_toolchange_delay")) {
        config.set_deserialize_strict("filament_toolchange_delay", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_cooling_moves")) {
        config.set_deserialize_strict("filament_cooling_moves", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "filament_cooling_initial_speed")) {
        config.set_deserialize_strict("filament_cooling_initial_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_cooling_final_speed")) {
        config.set_deserialize_strict("filament_cooling_final_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_stamping_loading_speed")) {
        config.set_deserialize_strict("filament_stamping_loading_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_stamping_distance")) {
        config.set_deserialize_strict("filament_stamping_distance", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "filament_ramming_parameters")) {
        config.set_deserialize_strict("filament_ramming_parameters", *value);
    }
    if (const auto value = extract_bool(json, "filament_multitool_ramming")) {
        config.set_deserialize_strict("filament_multitool_ramming", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "filament_multitool_ramming_volume")) {
        config.set_deserialize_strict("filament_multitool_ramming_volume", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "filament_multitool_ramming_flow")) {
        config.set_deserialize_strict("filament_multitool_ramming_flow", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "compatible_printers")) {
        config.set_deserialize_strict("compatible_printers", *value);
    }
    if (const auto value = extract_string(json, "compatible_printers_condition")) {
        config.set_deserialize_strict("compatible_printers_condition", *value);
    }
    if (const auto value = extract_string(json, "compatible_prints")) {
        config.set_deserialize_strict("compatible_prints", *value);
    }
    if (const auto value = extract_string(json, "compatible_prints_condition")) {
        config.set_deserialize_strict("compatible_prints_condition", *value);
    }
    if (const auto value = extract_string(json, "filament_notes")) {
        config.set_deserialize_strict("filament_notes", Slic3r::escape_strings_cstyle(std::vector<std::string>{*value}));
    }
    if (const auto value = extract_bool(json, "filament_adaptive_volumetric_speed")) {
        config.set_deserialize_strict("filament_adaptive_volumetric_speed", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "volumetric_speed_coefficients")) {
        config.set_deserialize_strict("volumetric_speed_coefficients", *value);
    }
    if (!apply_string_vector_override(config, json, "filament_start_gcode")) {
        const auto value = extract_string(json, "filament_start_gcode");
        if (value) {
        config.set_deserialize_strict("filament_start_gcode", value->empty() ? " " : *value);
        }
    }
    if (!apply_string_vector_override(config, json, "filament_end_gcode")) {
        const auto value = extract_string(json, "filament_end_gcode");
        if (value) {
        config.set_deserialize_strict("filament_end_gcode", value->empty() ? " " : *value);
        }
    }
    const auto nozzle_temperature_initial_layer = extract_number_any(
        json,
        {"nozzle_temperature_initial_layer", "print_temperature_initial_layer"});
    const auto nozzle_temperature = extract_number_any(json, {"nozzle_temperature", "print_temperature"});
    if (nozzle_temperature_initial_layer || nozzle_temperature) {
        const auto later_layer_nozzle = nozzle_temperature ? *nozzle_temperature : *nozzle_temperature_initial_layer;
        const auto first_layer_nozzle = nozzle_temperature_initial_layer ? *nozzle_temperature_initial_layer : later_layer_nozzle;
        config.set_deserialize_strict("nozzle_temperature", std::to_string(static_cast<int>(later_layer_nozzle)));
        config.set_deserialize_strict("nozzle_temperature_initial_layer", std::to_string(static_cast<int>(first_layer_nozzle)));
    }
    if (const auto value = extract_number_any(json, {"chamber_temperature", "chamber_temperatures"})) {
        config.set_deserialize_strict("chamber_temperature", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_bool(json, "activate_chamber_temp_control")) {
        config.set_deserialize_strict("activate_chamber_temp_control", *value ? "1" : "0");
    }
    for (const char* key : {
        "supertack_plate_temp_initial_layer",
        "supertack_plate_temp",
        "cool_plate_temp_initial_layer",
        "cool_plate_temp",
        "textured_cool_plate_temp_initial_layer",
        "textured_cool_plate_temp",
        "eng_plate_temp_initial_layer",
        "eng_plate_temp",
        "textured_plate_temp_initial_layer",
        "textured_plate_temp"
    }) {
        if (const auto value = extract_number(json, key)) {
            config.set_deserialize_strict(key, std::to_string(static_cast<int>(*value)));
        }
    }
    const auto bed_temperature_initial_layer = extract_number_any(
        json,
        {"bed_temperature_initial_layer", "hot_plate_temp_initial_layer"});
    const auto bed_temperature = extract_number_any(json, {"bed_temperature", "hot_plate_temp"});
    if (bed_temperature_initial_layer || bed_temperature) {
        apply_bed_temperature_split_override(
            bed_temperature_initial_layer ? bed_temperature_initial_layer : bed_temperature,
            bed_temperature ? bed_temperature : bed_temperature_initial_layer,
            config);
    }
    if (const auto value = extract_number(json, "cooling_baseline")) {
        apply_cooling_baseline_override(static_cast<int>(*value), config);
    }
    if (const auto value = extract_number(json, "close_fan_the_first_x_layers")) {
        config.set_deserialize_strict("close_fan_the_first_x_layers", std::to_string(static_cast<int>(*value)));
    }
    apply_orca_filament_identity_guard(json, config);
    if (const auto value = extract_number_any(json, {"sparse_infill_density", "fill_density"})) {
        config.set_deserialize_strict("sparse_infill_density", std::to_string(*value) + "%");
    }
    if (const auto value = extract_number_any(json, {"wall_loops", "perimeters"})) {
        config.set_deserialize_strict("wall_loops", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_string(json, "curr_bed_type")) {
        config.set_deserialize_strict("curr_bed_type", *value);
    }
    if (const auto value = extract_number_any(json, {"initial_layer_speed", "first_layer_print_speed"})) {
        config.set_deserialize_strict("initial_layer_speed", std::to_string(*value));
    }
    if (const auto value = extract_number_any(json, {"initial_layer_infill_speed", "first_layer_infill_speed"})) {
        config.set_deserialize_strict("initial_layer_infill_speed", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "initial_layer_travel_speed")) {
        config.set_deserialize_strict("initial_layer_travel_speed", *value);
    } else if (const auto value = extract_number_any(json, {"initial_layer_travel_speed_percent", "first_layer_travel_speed_percent"})) {
        config.set_deserialize_strict("initial_layer_travel_speed", std::to_string(static_cast<int>(*value)) + "%");
    }
    if (const auto value = extract_number(json, "slow_down_layers")) {
        config.set_deserialize_strict("slow_down_layers", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "outer_wall_speed")) {
        config.set_deserialize_strict("outer_wall_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "inner_wall_speed")) {
        config.set_deserialize_strict("inner_wall_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "top_surface_speed")) {
        config.set_deserialize_strict("top_surface_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "travel_speed")) {
        config.set_deserialize_strict("travel_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "outer_wall_acceleration")) {
        config.set_deserialize_strict("outer_wall_acceleration", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "inner_wall_acceleration")) {
        config.set_deserialize_strict("inner_wall_acceleration", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "top_surface_acceleration")) {
        config.set_deserialize_strict("top_surface_acceleration", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "sparse_infill_acceleration")) {
        config.set_deserialize_strict("sparse_infill_acceleration", *value);
    } else if (const auto value = extract_number(json, "sparse_infill_acceleration")) {
        config.set_deserialize_strict("sparse_infill_acceleration", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "internal_solid_infill_acceleration")) {
        config.set_deserialize_strict("internal_solid_infill_acceleration", *value);
    } else if (const auto value = extract_number(json, "internal_solid_infill_acceleration")) {
        config.set_deserialize_strict("internal_solid_infill_acceleration", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "bridge_acceleration")) {
        config.set_deserialize_strict("bridge_acceleration", *value);
    }
    if (const auto value = extract_string(json, "wall_sequence")) {
        config.set_deserialize_strict("wall_sequence", *value);
    }
    if (const auto value = extract_bool(json, "reduce_crossing_wall")) {
        config.set_deserialize_strict("reduce_crossing_wall", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "max_travel_detour_distance")) {
        config.set_deserialize_strict("max_travel_detour_distance", *value);
    }
    if (const auto value = extract_string(json, "seam_gap")) {
        config.set_deserialize_strict("seam_gap", *value);
    }
    if (const auto value = extract_string(json, "seam_slope_type")) {
        config.set_deserialize_strict("seam_slope_type", *value);
    }
    if (const auto value = extract_bool(json, "seam_slope_conditional")) {
        config.set_deserialize_strict("seam_slope_conditional", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "scarf_angle_threshold")) {
        config.set_deserialize_strict("scarf_angle_threshold", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "scarf_overhang_threshold")) {
        config.set_deserialize_strict("scarf_overhang_threshold", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_string(json, "scarf_joint_speed")) {
        config.set_deserialize_strict("scarf_joint_speed", *value);
    }
    if (const auto value = extract_number(json, "scarf_joint_flow_ratio")) {
        config.set_deserialize_strict("scarf_joint_flow_ratio", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "seam_slope_start_height")) {
        config.set_deserialize_strict("seam_slope_start_height", *value);
    }
    if (const auto value = extract_bool(json, "seam_slope_entire_loop")) {
        config.set_deserialize_strict("seam_slope_entire_loop", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "seam_slope_min_length")) {
        config.set_deserialize_strict("seam_slope_min_length", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "seam_slope_steps")) {
        config.set_deserialize_strict("seam_slope_steps", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_bool(json, "seam_slope_inner_walls")) {
        config.set_deserialize_strict("seam_slope_inner_walls", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "has_scarf_joint_seam")) {
        config.set_deserialize_strict("has_scarf_joint_seam", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "counterbore_hole_bridging")) {
        config.set_deserialize_strict("counterbore_hole_bridging", *value);
    }
    if (const auto value = extract_bool(json, "enable_arc_fitting")) {
        config.set_deserialize_strict("enable_arc_fitting", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "hole_to_polyhole")) {
        config.set_deserialize_strict("hole_to_polyhole", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "hole_to_polyhole_threshold")) {
        config.set_deserialize_strict("hole_to_polyhole_threshold", *value);
    }
    if (const auto value = extract_bool(json, "hole_to_polyhole_twisted")) {
        config.set_deserialize_strict("hole_to_polyhole_twisted", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "resolution")) {
        config.set_deserialize_strict("resolution", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "elefant_foot_compensation")) {
        config.set_deserialize_strict("elefant_foot_compensation", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "bridge_speed")) {
        config.set_deserialize_strict("bridge_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "bridge_angle")) {
        config.set_deserialize_strict("bridge_angle", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "bridge_density")) {
        config.set_deserialize_strict("bridge_density", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "bridge_flow")) {
        config.set_deserialize_strict("bridge_flow", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "bridge_no_support")) {
        config.set_deserialize_strict("bridge_no_support", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "max_bridge_length")) {
        config.set_deserialize_strict("max_bridge_length", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "internal_bridge_angle")) {
        config.set_deserialize_strict("internal_bridge_angle", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "internal_bridge_density")) {
        config.set_deserialize_strict("internal_bridge_density", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "internal_bridge_flow")) {
        config.set_deserialize_strict("internal_bridge_flow", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "internal_bridge_speed")) {
        config.set_deserialize_strict("internal_bridge_speed", *value);
    }
    if (const auto value = extract_string(json, "internal_bridge_fan_speed")) {
        config.set_deserialize_strict("internal_bridge_fan_speed", *value);
    }
    if (const auto value = extract_string(json, "internal_bridge_support_thickness")) {
        config.set_deserialize_strict("internal_bridge_support_thickness", *value);
    }
    if (const auto value = extract_string(json, "small_perimeter_speed")) {
        config.set_deserialize_strict("small_perimeter_speed", *value);
    } else if (const auto value = extract_number(json, "small_perimeter_speed")) {
        config.set_deserialize_strict("small_perimeter_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "small_perimeter_threshold")) {
        config.set_deserialize_strict("small_perimeter_threshold", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "sparse_infill_speed")) {
        config.set_deserialize_strict("sparse_infill_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "internal_solid_infill_speed")) {
        config.set_deserialize_strict("internal_solid_infill_speed", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "role_based_wipe_speed")) {
        config.set_deserialize_strict("role_based_wipe_speed", *value ? "1" : "0");
    } else if (const auto value = extract_number(json, "role_based_wipe_speed")) {
        config.set_deserialize_strict("role_based_wipe_speed", *value != 0.0 ? "1" : "0");
    }
    if (const auto value = extract_string(json, "wipe_speed")) {
        config.set_deserialize_strict("wipe_speed", *value);
    } else if (const auto value = extract_number(json, "wipe_speed")) {
        config.set_deserialize_strict("wipe_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "gap_infill_speed")) {
        config.set_deserialize_strict("gap_infill_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "initial_layer_acceleration")) {
        config.set_deserialize_strict("initial_layer_acceleration", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "initial_layer_jerk")) {
        config.set_deserialize_strict("initial_layer_jerk", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "first_layer_flow_ratio")) {
        config.set_deserialize_strict("first_layer_flow_ratio", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "print_extruder_id")) {
        config.set_deserialize_strict("print_extruder_id", *value);
    }
    if (const auto value = extract_string(json, "print_extruder_variant")) {
        config.set_deserialize_strict("print_extruder_variant", *value);
    }
    if (const auto value = extract_number(json, "default_acceleration")) {
        config.set_deserialize_strict("default_acceleration", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "inner_wall_jerk")) {
        config.set_deserialize_strict("inner_wall_jerk", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "inner_wall_flow_ratio")) {
        config.set_deserialize_strict("inner_wall_flow_ratio", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "outer_wall_jerk")) {
        config.set_deserialize_strict("outer_wall_jerk", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "outer_wall_flow_ratio")) {
        config.set_deserialize_strict("outer_wall_flow_ratio", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "top_solid_infill_flow_ratio")) {
        config.set_deserialize_strict("top_solid_infill_flow_ratio", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "bottom_solid_infill_flow_ratio")) {
        config.set_deserialize_strict("bottom_solid_infill_flow_ratio", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "enable_support")) {
        config.set_deserialize_strict("enable_support", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "support_type")) {
        config.set_deserialize_strict("support_type", *value);
    }
    if (const auto value = extract_string(json, "support_style")) {
        config.set_deserialize_strict("support_style", *value);
    }
    if (const auto value = extract_number(json, "support_threshold_angle")) {
        config.set_deserialize_strict("support_threshold_angle", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "raft_layers")) {
        config.set_deserialize_strict("raft_layers", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "raft_first_layer_density")) {
        config.set_deserialize_strict("raft_first_layer_density", std::to_string(static_cast<int>(*value)) + "%");
    }
    if (const auto value = extract_number(json, "raft_first_layer_expansion")) {
        config.set_deserialize_strict("raft_first_layer_expansion", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "raft_contact_distance")) {
        config.set_deserialize_strict("raft_contact_distance", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "raft_expansion")) {
        config.set_deserialize_strict("raft_expansion", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "support_filament")) {
        config.set_deserialize_strict("support_filament", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "support_interface_filament")) {
        config.set_deserialize_strict("support_interface_filament", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_bool(json, "support_on_build_plate_only")) {
        config.set_deserialize_strict("support_on_build_plate_only", *value ? "1" : "0");
    } else if (const auto legacy_value = extract_bool(json, "support_buildplate_only")) {
        config.set_deserialize_strict("support_on_build_plate_only", *legacy_value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "support_top_z_distance")) {
        config.set_deserialize_strict("support_top_z_distance", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "support_bottom_z_distance")) {
        config.set_deserialize_strict("support_bottom_z_distance", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "support_interface_top_layers")) {
        config.set_deserialize_strict("support_interface_top_layers", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "support_interface_bottom_layers")) {
        config.set_deserialize_strict("support_interface_bottom_layers", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "support_interface_spacing")) {
        config.set_deserialize_strict("support_interface_spacing", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "support_bottom_interface_spacing")) {
        config.set_deserialize_strict("support_bottom_interface_spacing", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "support_interface_speed")) {
        config.set_deserialize_strict("support_interface_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "support_interface_flow_ratio")) {
        config.set_deserialize_strict("support_interface_flow_ratio", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "support_material_interface_fan_speed")) {
        config.set_deserialize_strict("support_material_interface_fan_speed", *value);
    }
    if (const auto value = extract_string(json, "support_interface_pattern")) {
        config.set_deserialize_strict("support_interface_pattern", *value);
    }
    if (const auto value = extract_bool(json, "support_interface_loop_pattern")) {
        config.set_deserialize_strict("support_interface_loop_pattern", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "support_line_width")) {
        config.set_deserialize_strict("support_line_width", *value);
    }
    if (const auto value = extract_string(json, "support_base_pattern")) {
        config.set_deserialize_strict("support_base_pattern", *value);
    }
    if (const auto value = extract_number(json, "support_base_pattern_spacing")) {
        config.set_deserialize_strict("support_base_pattern_spacing", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "support_speed")) {
        config.set_deserialize_strict("support_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "support_flow_ratio")) {
        config.set_deserialize_strict("support_flow_ratio", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "support_object_elevation")) {
        config.set_deserialize_strict("support_object_elevation", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "support_ironing")) {
        config.set_deserialize_strict("support_ironing", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "support_ironing_pattern")) {
        config.set_deserialize_strict("support_ironing_pattern", *value);
    }
    if (const auto value = extract_number(json, "support_ironing_flow")) {
        config.set_deserialize_strict("support_ironing_flow", std::to_string(*value) + "%");
    }
    if (const auto value = extract_number(json, "support_ironing_spacing")) {
        config.set_deserialize_strict("support_ironing_spacing", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "support_expansion")) {
        config.set_deserialize_strict("support_expansion", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "support_object_xy_distance")) {
        config.set_deserialize_strict("support_object_xy_distance", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "independent_support_layer_height")) {
        config.set_deserialize_strict("independent_support_layer_height", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "tree_support_branch_angle")) {
        config.set_deserialize_strict("tree_support_branch_angle", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "tree_support_branch_diameter")) {
        config.set_deserialize_strict("tree_support_branch_diameter", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "tree_support_wall_count")) {
        config.set_deserialize_strict("tree_support_wall_count", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_string(json, "skirt_type")) {
        config.set_deserialize_strict("skirt_type", *value);
    }
    if (const auto value = extract_number(json, "skirt_distance")) {
        config.set_deserialize_strict("skirt_distance", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "min_skirt_length")) {
        config.set_deserialize_strict("min_skirt_length", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "skirt_start_angle")) {
        config.set_deserialize_strict("skirt_start_angle", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "skirt_speed")) {
        config.set_deserialize_strict("skirt_speed", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "skirt_height")) {
        config.set_deserialize_strict("skirt_height", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_string(json, "draft_shield")) {
        config.set_deserialize_strict("draft_shield", *value);
    }
    if (const auto value = extract_bool(json, "single_loop_draft_shield")) {
        config.set_deserialize_strict("single_loop_draft_shield", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "brim_type")) {
        config.set_deserialize_strict("brim_type", *value);
    }
    if (const auto value = extract_number(json, "brim_object_gap")) {
        config.set_deserialize_strict("brim_object_gap", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "brim_ears")) {
        config.set_deserialize_strict("brim_ears", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "brim_ears_detection_length")) {
        config.set_deserialize_strict("brim_ears_detection_length", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "brim_ears_max_angle")) {
        config.set_deserialize_strict("brim_ears_max_angle", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "print_sequence")) {
        config.set_deserialize_strict("print_sequence", *value);
    }
    if (const auto value = extract_bool(json, "spiral_mode")) {
        config.set_deserialize_strict("spiral_mode", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "reduce_infill_retraction")) {
        config.set_deserialize_strict("reduce_infill_retraction", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "filament_map_mode")) {
        config.set_deserialize_strict("filament_map_mode", *value);
    }
    if (const auto value = extract_bool(json, "allow_mix_temp")) {
        config.set_deserialize_strict("allow_mix_temp", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "allow_multicolor_oneplate")) {
        config.set_deserialize_strict("allow_multicolor_oneplate", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "filename_format")) {
        config.set_deserialize_strict("filename_format", *value);
    }
    if (const auto value = extract_string(json, "ironing_type")) {
        config.set_deserialize_strict("ironing_type", *value);
    }
    if (const auto value = extract_string(json, "ironing_pattern")) {
        config.set_deserialize_strict("ironing_pattern", *value);
    }
    if (const auto value = extract_number(json, "ironing_flow")) {
        config.set_deserialize_strict("ironing_flow", std::to_string(*value) + "%");
    }
    if (const auto value = extract_number(json, "ironing_spacing")) {
        config.set_deserialize_strict("ironing_spacing", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "ironing_inset")) {
        config.set_deserialize_strict("ironing_inset", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "ironing_angle")) {
        config.set_deserialize_strict("ironing_angle", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "ironing_angle_fixed")) {
        config.set_deserialize_strict("ironing_angle_fixed", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "ironing_speed")) {
        config.set_deserialize_strict("ironing_speed", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "enable_prime_tower")) {
        config.set_deserialize_strict("enable_prime_tower", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "prime_tower_width")) {
        config.set_deserialize_strict("prime_tower_width", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "standby_temperature_delta")) {
        config.set_deserialize_strict("standby_temperature_delta", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_bool(json, "wipe_tower_no_sparse_layers")) {
        config.set_deserialize_strict("wipe_tower_no_sparse_layers", *value ? "1" : "0");
    }
    const auto overhang_1_4_speed = extract_string(json, "overhang_1_4_speed");
    const auto overhang_2_4_speed = extract_string(json, "overhang_2_4_speed");
    const auto overhang_3_4_speed = extract_string(json, "overhang_3_4_speed");
    const auto overhang_4_4_speed = extract_string(json, "overhang_4_4_speed");
    const bool has_default_overhang_speed_buckets = all_float_or_percent_values_zero_or_missing({
        overhang_1_4_speed,
        overhang_2_4_speed,
        overhang_3_4_speed,
        overhang_4_4_speed
    });
    if (has_default_overhang_speed_buckets) {
        config.set_deserialize_strict("enable_overhang_speed", "0");
    } else {
        set_nonzero_float_or_percent_override(config, "overhang_1_4_speed", overhang_1_4_speed);
        set_nonzero_float_or_percent_override(config, "overhang_2_4_speed", overhang_2_4_speed);
        set_nonzero_float_or_percent_override(config, "overhang_3_4_speed", overhang_3_4_speed);
        set_nonzero_float_or_percent_override(config, "overhang_4_4_speed", overhang_4_4_speed);
    }
    if (const auto value = extract_number(json, "overhang_flow_ratio")) {
        config.set_deserialize_strict("overhang_flow_ratio", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "dont_slow_down_outer_wall")) {
        config.set_deserialize_strict("dont_slow_down_outer_wall", *value ? "1" : "0");
    }
    if (!has_default_overhang_speed_buckets) if (const auto value = extract_bool(json, "enable_overhang_speed")) {
        config.set_deserialize_strict("enable_overhang_speed", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "top_surface_density")) {
        config.set_deserialize_strict("top_surface_density", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "bottom_surface_density")) {
        config.set_deserialize_strict("bottom_surface_density", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_bool(json, "precise_outer_wall")) {
        config.set_deserialize_strict("precise_outer_wall", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "only_one_wall_top")) {
        config.set_deserialize_strict("only_one_wall_top", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "top_surface_pattern")) {
        config.set_deserialize_strict("top_surface_pattern", *value);
    }
    if (const auto value = extract_string(json, "bottom_surface_pattern")) {
        config.set_deserialize_strict("bottom_surface_pattern", *value);
    }
    if (const auto value = extract_string(json, "internal_solid_infill_pattern")) {
        config.set_deserialize_strict("internal_solid_infill_pattern", *value);
    }
    if (const auto value = extract_number(json, "solid_infill_direction")) {
        config.set_deserialize_strict("solid_infill_direction", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "solid_infill_rotate_template")) {
        config.set_deserialize_strict("solid_infill_rotate_template", *value);
    }
    if (const auto value = extract_string(json, "line_width")) {
        config.set_deserialize_strict("line_width", *value);
    }
    if (const auto value = extract_string(json, "outer_wall_line_width")) {
        config.set_deserialize_strict("outer_wall_line_width", *value);
    }
    if (const auto value = extract_string(json, "inner_wall_line_width")) {
        config.set_deserialize_strict("inner_wall_line_width", *value);
    }
    if (const auto value = extract_string(json, "initial_layer_line_width")) {
        config.set_deserialize_strict("initial_layer_line_width", *value);
    }
    if (const auto value = extract_number(json, "initial_layer_min_bead_width")) {
        config.set_deserialize_strict("initial_layer_min_bead_width", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_string(json, "top_surface_line_width")) {
        config.set_deserialize_strict("top_surface_line_width", *value);
    }
    if (const auto value = extract_string(json, "internal_solid_infill_line_width")) {
        config.set_deserialize_strict("internal_solid_infill_line_width", *value);
    }
    if (const auto value = extract_string(json, "min_width_top_surface")) {
        config.set_deserialize_strict("min_width_top_surface", *value);
    }
    if (const auto value = extract_string(json, "sparse_infill_line_width")) {
        config.set_deserialize_strict("sparse_infill_line_width", *value);
    }
    if (const auto value = extract_string(json, "skin_infill_density")) {
        config.set_deserialize_strict("skin_infill_density", *value);
    } else if (const auto value = extract_number(json, "skin_infill_density")) {
        config.set_deserialize_strict("skin_infill_density", std::to_string(*value) + "%");
    }
    if (const auto value = extract_string(json, "skeleton_infill_density")) {
        config.set_deserialize_strict("skeleton_infill_density", *value);
    } else if (const auto value = extract_number(json, "skeleton_infill_density")) {
        config.set_deserialize_strict("skeleton_infill_density", std::to_string(*value) + "%");
    }
    if (const auto value = extract_string(json, "skin_infill_line_width")) {
        config.set_deserialize_strict("skin_infill_line_width", *value);
    }
    if (const auto value = extract_string(json, "skeleton_infill_line_width")) {
        config.set_deserialize_strict("skeleton_infill_line_width", *value);
    }
    if (const auto value = extract_number(json, "infill_direction")) {
        config.set_deserialize_strict("infill_direction", std::to_string(*value));
    }
    if (const auto value = extract_string(json, "sparse_infill_rotate_template")) {
        config.set_deserialize_strict("sparse_infill_rotate_template", *value);
    }
    if (const auto value = extract_bool(json, "align_infill_direction_to_model")) {
        config.set_deserialize_strict("align_infill_direction_to_model", *value ? "1" : "0");
    }
    if (const auto value = extract_number(json, "infill_wall_overlap")) {
        config.set_deserialize_strict("infill_wall_overlap", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "top_bottom_infill_wall_overlap")) {
        config.set_deserialize_strict("top_bottom_infill_wall_overlap", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_string(json, "infill_anchor")) {
        config.set_deserialize_strict("infill_anchor", *value);
    }
    if (const auto value = extract_string(json, "infill_anchor_max")) {
        config.set_deserialize_strict("infill_anchor_max", *value);
    }
    if (const auto value = extract_bool(json, "infill_combination")) {
        config.set_deserialize_strict("infill_combination", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "infill_combination_max_layer_height")) {
        config.set_deserialize_strict("infill_combination_max_layer_height", *value);
    }
    if (const auto value = extract_bool(json, "alternate_extra_wall")) {
        config.set_deserialize_strict("alternate_extra_wall", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "extra_solid_infills")) {
        config.set_deserialize_strict("extra_solid_infills", *value);
    }
    if (const auto value = extract_number(json, "minimum_sparse_infill_area")) {
        config.set_deserialize_strict("minimum_sparse_infill_area", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "detect_thin_wall")) {
        config.set_deserialize_strict("detect_thin_wall", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "detect_overhang_wall")) {
        config.set_deserialize_strict("detect_overhang_wall", *value ? "1" : "0");
    }
    if (const auto value = extract_bool(json, "thick_bridges")) {
        config.set_deserialize_strict("thick_bridges", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "dont_filter_internal_bridges")) {
        config.set_deserialize_strict("dont_filter_internal_bridges", *value);
    }
    if (const auto value = extract_string(json, "wall_generator")) {
        config.set_deserialize_strict("wall_generator", *value);
    }
    if (const auto value = extract_string(json, "wall_infill_order")) {
        config.set_deserialize_strict("wall_infill_order", *value);
    }
    if (const auto value = extract_bool(json, "extra_perimeters_on_overhangs")) {
        config.set_deserialize_strict("extra_perimeters_on_overhangs", *value ? "1" : "0");
    }
    apply_android_adhesion_overrides(json, config);
    if (const auto value = extract_string(json, "sparse_infill_pattern")) {
        config.set_deserialize_strict("sparse_infill_pattern", *value);
    }
    if (const auto value = extract_string(json, "fuzzy_skin")) {
        config.set_deserialize_strict("fuzzy_skin", *value);
    }
    if (const auto value = extract_number(json, "fuzzy_skin_thickness")) {
        config.set_deserialize_strict("fuzzy_skin_thickness", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "fuzzy_skin_point_distance")) {
        config.set_deserialize_strict("fuzzy_skin_point_distance", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "fuzzy_skin_first_layer")) {
        config.set_deserialize_strict("fuzzy_skin_first_layer", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "fuzzy_skin_mode")) {
        config.set_deserialize_strict("fuzzy_skin_mode", *value);
    }
    if (const auto value = extract_string(json, "fuzzy_skin_noise_type")) {
        config.set_deserialize_strict("fuzzy_skin_noise_type", *value);
    }
    if (const auto value = extract_number(json, "fuzzy_skin_scale")) {
        config.set_deserialize_strict("fuzzy_skin_scale", std::to_string(*value));
    }
    if (const auto value = extract_number(json, "fuzzy_skin_octaves")) {
        config.set_deserialize_strict("fuzzy_skin_octaves", std::to_string(static_cast<int>(*value)));
    }
    if (const auto value = extract_number(json, "fuzzy_skin_persistence")) {
        config.set_deserialize_strict("fuzzy_skin_persistence", std::to_string(*value));
    }
    if (const auto value = extract_bool(json, "gcode_comments")) {
        config.set_deserialize_strict("gcode_comments", *value ? "1" : "0");
    }
    if (const auto value = extract_string(json, "start_gcode")) {
        config.set_deserialize_strict("start_gcode", *value);
    }

    // Multi-material plate overlays arrive from Android as JSON arrays. The
    // scalar helpers intentionally read the first array item for legacy
    // single-material flows, so reapply Orca vector-valued keys through the
    // scalar-or-list path before slicing.
    for (const char* key : {
        "nozzle_diameter",
        "nozzle_volume",
        "min_layer_height",
        "max_layer_height",
        "extruder_offset",
        "extruder_type",
        "extruder_colour",
        "extruder_printable_height",
        "extruder_printable_area",
        "extruder_variant_list",
        "physical_extruder_map",
        "printer_extruder_id",
        "printer_extruder_variant",
        "print_extruder_id",
        "print_extruder_variant",
        "nozzle_type",
        "nozzle_height",
        "nozzle_volume_type",
        "default_nozzle_volume_type",
        "grab_length",
        "hotend_heating_rate",
        "hotend_cooling_rate",
        "retraction_distances_when_cut",
        "enable_long_retraction_when_cut",
        "retraction_length",
        "retract_restart_extra",
        "retraction_speed",
        "deretraction_speed",
        "retraction_minimum_travel",
        "retract_when_changing_layer",
        "retract_on_top_layer",
        "wipe",
        "wipe_distance",
        "retract_before_wipe",
        "retract_lift_enforce",
        "z_hop_types",
        "z_hop_when_prime",
        "z_lift_type",
        "z_hop",
        "travel_slope",
        "retract_lift_above",
        "retract_lift_below",
        "retract_length_toolchange",
        "retract_restart_extra_toolchange",
        "filament_diameter",
        "filament_type",
        "filament_vendor",
        "filament_soluble",
        "filament_is_support",
        "filament_extruder_variant",
        "filament_self_index",
        "filament_settings_id",
        "filament_ids",
        "filament_colour",
        "filament_multi_colour",
        "filament_colour_type",
        "filament_map",
        "filament_change_length",
        "required_nozzle_HRC",
        "default_filament_colour",
        "filament_adhesiveness_category",
        "filament_density",
        "filament_shrink",
        "filament_shrinkage_compensation_z",
        "filament_cost",
        "temperature_vitrification",
        "idle_temperature",
        "nozzle_temperature_range_low",
        "nozzle_temperature_range_high",
        "filament_flow_ratio",
        "enable_pressure_advance",
        "pressure_advance",
        "pellet_flow_coefficient",
        "adaptive_pressure_advance",
        "adaptive_pressure_advance_overhangs",
        "adaptive_pressure_advance_bridges",
        "filament_max_volumetric_speed",
        "filament_adaptive_volumetric_speed",
        "volumetric_speed_coefficients",
        "nozzle_temperature_initial_layer",
        "nozzle_temperature",
        "filament_flush_volumetric_speed",
        "filament_flush_temp",
        "filament_ironing_flow",
        "filament_ironing_spacing",
        "filament_ironing_inset",
        "filament_ironing_speed",
        "filament_long_retractions_when_cut",
        "filament_retraction_distances_when_cut",
        "long_retractions_when_ec",
        "retraction_distances_when_ec",
        "chamber_temperature",
        "activate_chamber_temp_control",
        "supertack_plate_temp_initial_layer",
        "supertack_plate_temp",
        "cool_plate_temp_initial_layer",
        "cool_plate_temp",
        "textured_cool_plate_temp_initial_layer",
        "textured_cool_plate_temp",
        "eng_plate_temp_initial_layer",
        "eng_plate_temp",
        "bed_temperature_initial_layer",
        "bed_temperature",
        "hot_plate_temp_initial_layer",
        "hot_plate_temp",
        "textured_plate_temp_initial_layer",
        "textured_plate_temp",
        "fan_min_speed",
        "fan_max_speed",
        "cooling_baseline",
        "close_fan_the_first_x_layers",
        "full_fan_speed_layer",
        "fan_cooling_layer_time",
        "slow_down_layer_time",
        "reduce_fan_stop_start_freq",
        "slow_down_for_layer_cooling",
        "dont_slow_down_outer_wall",
        "slow_down_min_speed",
        "enable_overhang_bridge_fan",
        "overhang_fan_threshold",
        "overhang_fan_speed",
        "internal_bridge_fan_speed",
        "support_material_interface_fan_speed",
        "ironing_fan_speed",
        "additional_cooling_fan_speed",
        "activate_air_filtration",
        "during_print_exhaust_fan_speed",
        "complete_print_exhaust_fan_speed",
        "filament_retraction_length",
        "filament_z_hop",
        "filament_z_hop_types",
        "filament_retraction_speed",
        "filament_deretraction_speed",
        "filament_retraction_minimum_travel",
        "filament_retract_when_changing_layer",
        "filament_wipe",
        "filament_wipe_distance",
        "filament_retract_before_wipe",
        "filament_minimal_purge_on_wipe_tower",
        "filament_tower_interface_pre_extrusion_dist",
        "filament_tower_interface_pre_extrusion_length",
        "filament_tower_ironing_area",
        "filament_tower_interface_purge_volume",
        "filament_tower_interface_print_temp",
        "filament_loading_speed_start",
        "filament_loading_speed",
        "filament_unloading_speed_start",
        "filament_unloading_speed",
        "filament_toolchange_delay",
        "filament_cooling_moves",
        "filament_cooling_initial_speed",
        "filament_cooling_final_speed",
        "filament_stamping_loading_speed",
        "filament_stamping_distance",
        "filament_multitool_ramming",
        "filament_multitool_ramming_volume",
        "filament_multitool_ramming_flow",
        "flush_multiplier",
        "flush_volumes_matrix",
        "filament_map"
    }) {
        if (is_bool_vector_config_key(key)) {
            continue;
        }
        if (is_orca_string_vector_config_key(key)) {
            continue;
        }
        if (std::string(key) == "extruder_printable_area") {
            set_config_points_groups_if_present(json, config, key);
        } else {
            set_config_string_if_present(json, config, key);
        }
    }
    for (const char* key : {
        "extruder_colour",
        "extruder_variant_list",
        "printer_extruder_variant",
        "print_extruder_variant",
        "filament_type",
        "filament_vendor",
        "filament_extruder_variant",
        "filament_settings_id",
        "filament_ids",
        "filament_colour",
        "filament_multi_colour",
        "default_filament_colour",
        "volumetric_speed_coefficients",
        "filament_ramming_parameters"
    }) {
        set_config_orca_string_list_if_present(json, config, key);
    }
    for (const char* key : {
        "retract_when_changing_layer",
        "wipe",
        "filament_soluble",
        "filament_is_support",
        "filament_adaptive_volumetric_speed",
        "enable_pressure_advance",
        "adaptive_pressure_advance",
        "adaptive_pressure_advance_overhangs",
        "adaptive_pressure_advance_bridges",
        "long_retractions_when_cut",
        "long_retractions_when_ec",
        "activate_chamber_temp_control",
        "reduce_fan_stop_start_freq",
        "slow_down_for_layer_cooling",
        "dont_slow_down_outer_wall",
        "enable_overhang_bridge_fan",
        "activate_air_filtration",
        "filament_retract_when_changing_layer",
        "filament_wipe",
        "filament_long_retractions_when_cut",
        "filament_multitool_ramming"
    }) {
        set_config_bool_list_if_present(json, config, key);
    }

    const std::string printer_model = extract_string(json, "printer_model").value_or("");
    const int mobile_slot_count = static_cast<int>(extract_number(json, "mobile_slicer_active_filament_slot_count").value_or(1.0));
    if (printer_model.find("Bambu Lab H2D") != std::string::npos && mobile_slot_count <= 1) {
        // Android currently sends one active filament/nozzle config entry. Keep
        // the H2D dual-nozzle desktop mapper out of that single-entry slice.
        config.set_deserialize_strict("filament_map", "1");
        config.set_deserialize_strict("filament_map_mode", "Auto For Flush");
    }
}


#endif // ORCA_WRAPPER_CONFIG_JSON_OVERRIDES_H

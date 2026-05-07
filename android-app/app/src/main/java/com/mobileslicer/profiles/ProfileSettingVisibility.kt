package com.mobileslicer.profiles

internal enum class ProfileSettingVisibility {
    Simple,
    Advanced
}

internal enum class ProfileEditorSetting(
    val orcaKeys: List<String>,
    val visibility: ProfileSettingVisibility,
    val source: String,
    val overrideReason: String? = null
) {
    PrinterBedDimensions(
        orcaKeys = listOf("printable_area"),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: printable_area mode comAdvanced"
    ),
    PrinterPrintableSpaceCore(
        orcaKeys = listOf("support_multi_bed_types"),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Basic information > Printable space; PrintConfig.cpp mode comSimple"
    ),
    PrinterPrintableSpaceAdvanced(
        orcaKeys = listOf(
            "bed_exclude_area",
            "wrapping_exclude_area",
            "head_wrap_detect_zone",
            "bed_custom_texture",
            "bed_custom_model",
            "bed_model",
            "bed_shape",
            "bed_texture",
            "bed_texture_area",
            "bottom_texture_rect",
            "bottom_texture_end_name",
            "image_bed_type",
            "use_double_extruder_default_texture",
            "use_rect_grid",
            "default_bed_type",
            "best_object_pos",
            "z_offset",
            "preferred_orientation"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Basic information > Printable space / create_bed_shape_widget and Basic information > Advanced commented wrapping_exclude_area row; vendor/orcaslicer/src/libslic3r/Preset.hpp and PresetBundle.cpp define/load bed_model, bed_texture, image_bed_type, use_double_extruder_default_texture, and bottom_texture_* printer-model metadata",
        overrideReason = "head_wrap_detect_zone is a PrintConfig-defined machine compatibility field used by GCode.cpp placeholders, not a normal live TabPrinter row. bed_model, bed_texture, image_bed_type, bottom_texture_*, use_double_extruder_default_texture, bed_texture_area, bed_shape, default_bed_type, wrapping_exclude_area, and use_rect_grid are Orca profile/model metadata used by bed rendering or vendor presets rather than normal live PrintConfig rows; Android preserves these keys for import/export while rendering only the live Orca printable-space rows."
    ),
    PrinterAdaptiveBedMeshAdvanced(
        orcaKeys = listOf("bed_mesh_min", "bed_mesh_max", "bed_mesh_probe_distance", "adaptive_bed_mesh_margin"),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Basic information > Adaptive bed mesh; PrintConfig.cpp mode comAdvanced"
    ),
    PrinterMaxHeight(
        orcaKeys = listOf("printable_height"),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: printable_height mode comSimple"
    ),
    PrinterNozzleDiameter(
        orcaKeys = listOf("nozzle_diameter"),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: nozzle_diameter mode comAdvanced"
    ),
    PrinterExtruderBasicInformationAdvanced(
        orcaKeys = listOf(
            "nozzle_volume",
            "nozzle_volume_type",
            "default_nozzle_volume_type",
            "nozzle_height",
            "grab_length",
            "extruder_variant_list",
            "printer_extruder_id",
            "printer_extruder_variant",
            "master_extruder_id",
            "physical_extruder_map",
            "extruders_count",
            "extruder_ams_count",
            "extruder_max_nozzle_count",
            "extruder_type",
            "extruder_colour",
            "extruder_printable_height",
            "extruder_printable_area",
            "min_layer_height",
            "max_layer_height",
            "extruder_offset"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Extruder > Basic information / Layer height limits / Position; PrintConfig.cpp mode comAdvanced; nozzle_volume_type is defined as comSimple but the normal row is commented out and Orca updates it from printer/extruder variant flows",
        overrideReason = "nozzle_volume_type, default_nozzle_volume_type, nozzle_height, grab_length, and the extruder variant/map/count/AMS/max-nozzle keys are metadata used by Orca's per-extruder variant mapping and vendor machine presets rather than normal live rows in the supplied Orca UI. Android preserves those keys for import/export while rendering only nozzle_volume, layer-height limits, and extruder_offset in the normal Extruder tab."
    ),
    PrinterBasicInformationAdvanced(
        orcaKeys = listOf(
            "printer_model",
            "machine_tech",
            "family",
            "printer_technology",
            "printer_variant",
            "hotend_model",
            "box_id",
            "enable_pre_heating",
            "fan_direction",
            "hotend_cooling_rate",
            "hotend_heating_rate",
            "active_feeder_motor_name",
            "auto_disable_filter_on_overheat",
            "auto_toolchange_command",
            "cooling_filter_enabled",
            "creality_flush_time",
            "group_algo_with_time",
            "is_artillery",
            "is_support_3mf",
            "is_support_air_condition",
            "is_support_mqtt",
            "is_support_multi_box",
            "is_support_timelapse",
            "machine_LED_light_exist",
            "machine_hotend_change_time",
            "machine_platform_motion_enable",
            "machine_prepare_compensation_time",
            "multi_zone",
            "multi_zone_number",
            "nozzle_flush_dataset",
            "ramming_pressure_advance_value",
            "right_icon_offset_bed",
            "scan_folder",
            "support_box_temp_control",
            "support_cooling_filter",
            "support_multi_filament",
            "support_object_skip_flush",
            "support_wan_network",
            "tool_change_temprature_wait",
            "upward_compatible_machine",
            "url",
            "use_active_pellet_feeding",
            "use_extruder_rotation_volume",
            "printer_structure",
            "gcode_flavor",
            "pellet_modded_printer",
            "bbl_use_printhost",
            "scan_first_layer",
            "enable_power_loss_recovery",
            "disable_m73",
            "thumbnails",
            "thumbnails_internal",
            "thumbnails_internal_switch",
            "remaining_times",
            "use_relative_e_distances",
            "use_firmware_retraction",
            "time_cost"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Basic information > Advanced; Preset.hpp/PresetBundle.cpp define/load machine_tech, family, and hotend_model printer-model metadata; vendor machine JSON defines box_id, enable_pre_heating, fan_direction, hotend_*_rate, support capability, platform, update, and integration metadata",
        overrideReason = "printer_model, printer_technology, printer_variant, machine_tech, family, hotend_model, box_id, enable_pre_heating, fan_direction, hotend_*_rate, printer_structure, and vendor platform/support keys are preset/vendor identity or machine metadata rather than normal live rows in the supplied Orca UI. Android preserves those keys for import/export while rendering Orca's live Basic information > Advanced rows."
    ),
    PrinterPhysicalPrintHost(
        orcaKeys = listOf(
            "host_type",
            "printer_agent",
            "print_host",
            "print_host_webui",
            "printhost_authorization_type",
            "printhost_apikey",
            "printhost_port",
            "printhost_cafile",
            "printhost_user",
            "printhost_password",
            "printhost_ssl_ignore_revoke"
        ),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/slic3r/GUI/PhysicalPrinterDialog.cpp: physical printer host controls; PrintConfig.cpp mode comAdvanced",
        overrideReason = "Orca exposes these as physical-printer dialog controls rather than TabPrinter::build() rows. Android surfaces them in a dedicated Printer > Connection tab so users can connect printers without enabling advanced slicer controls."
    ),
    PrinterBasicInformationCoolingFan(
        orcaKeys = listOf("fan_speedup_time", "fan_speedup_overhangs", "fan_kickstart"),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Basic information > Cooling Fan; PrintConfig.cpp mode comAdvanced"
    ),
    PrinterBasicInformationExtruderClearance(
        orcaKeys = listOf(
            "extruder_clearance_radius",
            "extruder_clearance_height_to_rod",
            "extruder_clearance_height_to_lid",
            "extruder_clearance_dist_to_rod"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Basic information > Extruder Clearance; PrintConfig.cpp mode comAdvanced; extruder_clearance_dist_to_rod is vendor/profile metadata present in Orca machine JSON",
        overrideReason = "extruder_clearance_dist_to_rod is not a live PrintConfig row in the current vendored tree, but Orca machine profiles include it alongside the live extruder-clearance fields. Android preserves it for import/export while rendering the three live Orca clearance rows."
    ),
    PrinterBasicInformationAccessory(
        orcaKeys = listOf(
            "nozzle_type",
            "nozzle_hrc",
            "auxiliary_fan",
            "support_chamber_temp_control",
            "support_air_filtration"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Basic information > Accessory",
        overrideReason = "nozzle_type and auxiliary_fan are the supplied Orca Accessory rows. Android preserves nozzle_hrc, chamber temperature, and air-filtration developer keys for import/export but does not render them in the normal mobile Printer UI."
    ),
    PrinterNotes(
        orcaKeys = listOf("printer_notes"),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Printer > Notes; PrintConfig.cpp mode comAdvanced"
    ),
    PrinterMultimaterialAdvanced(
        orcaKeys = listOf(
            "single_extruder_multi_material",
            "manual_filament_change",
            "bed_temperature_formula",
            "wipe_tower_type",
            "purge_in_prime_tower",
            "enable_filament_ramming",
            "cooling_tube_retraction",
            "cooling_tube_length",
            "parking_pos_retraction",
            "extra_loading_move",
            "high_current_on_filament_swap",
            "machine_load_filament_time",
            "machine_unload_filament_time",
            "machine_tool_change_time"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Printer > Multimaterial; PrintConfig.cpp mode comAdvanced"
    ),
    PrinterMachineGcode(
        orcaKeys = listOf(
            "file_start_gcode",
            "machine_start_gcode",
            "machine_end_gcode",
            "printing_by_object_gcode",
            "before_layer_change_gcode",
            "layer_change_gcode",
            "time_lapse_gcode",
            "wrapping_detection_gcode",
            "change_filament_gcode",
            "toolchange_gcode",
            "change_extrusion_role_gcode",
            "machine_pause_gcode",
            "pause_gcode",
            "template_custom_gcode"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: printer Machine G-code fields mode comAdvanced",
        overrideReason = "pause_gcode and toolchange_gcode are legacy/import aliases absent from current live Orca config keys; Android maps them through machine_pause_gcode and change_filament_gcode."
    ),
    PrinterMotionAbilityCore(
        orcaKeys = listOf(
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
            "machine_max_jerk_x",
            "machine_max_jerk_y",
            "machine_max_jerk_z",
            "machine_max_jerk_e"
        ),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Motion ability speed/acceleration/jerk limitation groups",
        overrideReason = "Generated metadata currently misses Orca's loop-generated XYZE machine limit keys; PrintConfig.cpp defines these as comSimple in the axis loop."
    ),
    PrinterMotionAbilityAdvanced(
        orcaKeys = listOf(
            "emit_machine_limits_to_gcode",
            "resonance_avoidance",
            "silent_mode",
            "min_resonance_avoidance_speed",
            "max_resonance_avoidance_speed",
            "machine_max_acceleration_travel",
            "machine_max_junction_deviation",
            "machine_min_extruding_rate",
            "machine_min_travel_rate"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Motion ability Advanced / Resonance Avoidance / travel acceleration / junction deviation; PrintConfig.cpp comDevelop silent_mode and minimum feedrates are config compatibility",
        overrideReason = "Generated metadata currently misses some loop-generated machine-limit GUI placements; the live keys are Orca printer controls with comAdvanced metadata. Android preserves silent_mode and minimum feedrate keys for import/export because the supplied Orca Motion ability UI does not show those rows."
    ),
    PrinterExtruderRetractionCore(
        orcaKeys = listOf("retraction_length", "z_hop"),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Extruder > Retraction / Z-Hop; PrintConfig.cpp mode comSimple"
    ),
    PrinterExtruderRetractionAdvanced(
        orcaKeys = listOf(
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
            "travel_slope",
            "retract_lift_above",
            "retract_lift_below",
            "retract_length_toolchange",
            "retract_restart_extra_toolchange",
            "enable_long_retraction_when_cut",
            "long_retractions_when_cut",
            "retraction_distances_when_cut"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Extruder Retraction / Z-Hop / Retraction when switching material; PrintConfig.cpp mode comAdvanced/comDevelop",
        overrideReason = "retract_on_top_layer, z_hop_when_prime, z_lift_type, and long-retraction-when-cut developer keys are absent from the supplied current Orca printer UI. Android preserves them for import/export while rendering only the live retraction, Z-Hop, and toolchange retraction rows."
    ),
    FilamentMaterialType(
        orcaKeys = listOf("filament_type"),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: filament_type mode comSimple"
    ),
    FilamentFlowRatio(
        orcaKeys = listOf(
            "pellet_flow_coefficient",
            "filament_flow_ratio",
            "adaptive_pressure_advance",
            "adaptive_pressure_advance_overhangs",
            "adaptive_pressure_advance_bridges",
            "adaptive_pressure_advance_model"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Filament > Flow ratio and Pressure Advance",
        overrideReason = "pellet_flow_coefficient is a pellet-printer-only Orca field; Android preserves it in config import/export but does not render it until printer-aware pellet mode exists."
    ),
    FilamentNozzleTemperature(
        orcaKeys = listOf("nozzle_temperature_initial_layer", "nozzle_temperature"),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: nozzle temperature line is in the main Filament temperature page"
    ),
    FilamentBedTemperature(
        orcaKeys = listOf(
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
            "hot_plate_temp_initial_layer",
            "hot_plate_temp",
            "textured_plate_temp_initial_layer",
            "textured_plate_temp"
        ),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Filament > Print chamber temperature / Bed temperature"
    ),
    FilamentBasicInformationAdvanced(
        orcaKeys = listOf(
            "filament_vendor",
            "filament_soluble",
            "filament_is_support",
            "filament_change_length",
            "required_nozzle_HRC",
            "default_filament_colour",
            "filament_adhesiveness_category",
            "filament_density",
            "filament_shrink",
            "filament_shrinkage_compensation_z",
            "filament_cost"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Filament > Basic information",
        overrideReason = "required_nozzle_HRC and filament_adhesiveness_category are comDevelop metadata; Android preserves them in config import/export but does not render developer rows in the normal mobile Filament UI."
    ),
    FilamentBasicInformationTemperatureCore(
        orcaKeys = listOf("temperature_vitrification", "idle_temperature", "nozzle_temperature_range_low", "nozzle_temperature_range_high"),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Filament > Basic information; simple/default mode temperature controls"
    ),
    FilamentMaxVolumetricSpeed(
        orcaKeys = listOf("filament_max_volumetric_speed"),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: filament_max_volumetric_speed mode comAdvanced"
    ),
    FilamentCoolingBaseline(
        orcaKeys = listOf("fan_min_speed", "fan_max_speed"),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: fan_min_speed and fan_max_speed mode comSimple"
    ),
    FilamentNoCoolingFirstLayers(
        orcaKeys = listOf("close_fan_the_first_x_layers"),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: close_fan_the_first_x_layers mode comSimple"
    ),
    FilamentCoolingSpecificLayerAdvanced(
        orcaKeys = listOf("full_fan_speed_layer"),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Filament > Cooling > Cooling for specific layer"
    ),
    FilamentPartCoolingFanCore(
        orcaKeys = listOf(
            "fan_cooling_layer_time",
            "slow_down_layer_time",
            "reduce_fan_stop_start_freq",
            "slow_down_for_layer_cooling",
            "dont_slow_down_outer_wall",
            "enable_overhang_bridge_fan"
        ),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Filament > Cooling > Part cooling fan",
        overrideReason = "Some keys have default-mode metadata in PrintConfig.cpp, but Orca places them in the normal Part cooling fan group."
    ),
    FilamentPartCoolingFanAdvanced(
        orcaKeys = listOf(
            "slow_down_min_speed",
            "overhang_fan_threshold",
            "overhang_fan_speed",
            "internal_bridge_fan_speed",
            "support_material_interface_fan_speed",
            "ironing_fan_speed"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Filament > Cooling > Part cooling fan; PrintConfig.cpp mode comAdvanced"
    ),
    FilamentAuxiliaryCoolingFan(
        orcaKeys = listOf("additional_cooling_fan_speed"),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Filament > Cooling > Auxiliary part cooling fan; PrintConfig.cpp mode comSimple"
    ),
    FilamentExhaustFan(
        orcaKeys = listOf("activate_air_filtration", "during_print_exhaust_fan_speed", "complete_print_exhaust_fan_speed"),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Filament > Cooling > Exhaust fan; PrintConfig.cpp mode comSimple"
    ),
    FilamentRetractionOverrides(
        orcaKeys = listOf(
            "filament_retraction_length",
            "filament_z_hop",
            "filament_z_hop_types",
            "filament_retract_lift_above",
            "filament_retract_lift_below",
            "filament_retract_lift_enforce",
            "filament_retraction_speed",
            "filament_deretraction_speed",
            "filament_retract_restart_extra",
            "filament_retraction_minimum_travel",
            "filament_retract_when_changing_layer",
            "filament_wipe",
            "filament_wipe_distance",
            "filament_retract_before_wipe",
            "filament_long_retractions_when_cut",
            "filament_retraction_distances_when_cut"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: filament retraction overrides are exposed from the filament overrides page",
        overrideReason = "These filament override keys are variant-backed in Orca and are not normal live add(...) definitions in this vendored PrintConfig.cpp."
    ),
    FilamentIroningOverrides(
        orcaKeys = listOf(
            "filament_ironing_flow",
            "filament_ironing_spacing",
            "filament_ironing_inset",
            "filament_ironing_speed"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Filament > Setting Overrides > Ironing"
    ),
    FilamentMultimaterialAdvanced(
        orcaKeys = listOf(
            "filament_minimal_purge_on_wipe_tower",
            "filament_tower_interface_pre_extrusion_dist",
            "filament_tower_interface_pre_extrusion_length",
            "filament_tower_ironing_area",
            "filament_tower_interface_purge_volume",
            "filament_tower_interface_print_temp",
            "long_retractions_when_ec",
            "retraction_distances_when_ec",
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
            "filament_ramming_parameters",
            "filament_multitool_ramming",
            "filament_multitool_ramming_volume",
            "filament_multitool_ramming_flow"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Filament > Multimaterial"
    ),
    FilamentDependenciesAdvanced(
        orcaKeys = listOf(
            "compatible_printers",
            "compatible_printers_condition",
            "compatible_prints",
            "compatible_prints_condition"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Filament > Dependencies"
    ),
    FilamentNotesAdvanced(
        orcaKeys = listOf("filament_notes"),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Filament > Notes"
    ),
    FilamentPressureAdvance(
        orcaKeys = listOf("enable_pressure_advance", "pressure_advance"),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: pressure_advance mode comAdvanced",
        overrideReason = "The enable flag has default mode metadata, but it controls the advanced pressure_advance value and stays grouped behind advanced controls."
    ),
    FilamentAdaptiveVolumetricSpeed(
        orcaKeys = listOf("filament_adaptive_volumetric_speed", "volumetric_speed_coefficients"),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: filament_adaptive_volumetric_speed mode comDevelop",
        overrideReason = "Adaptive volumetric speed is experimental/develop in Orca, and coefficients are the companion fitted-value field."
    ),
    FilamentGcodeAdvanced(
        orcaKeys = listOf("filament_start_gcode", "filament_end_gcode"),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: filament start/end G-code live on Filament > Advanced"
    ),
    FilamentVariantMetadataAdvanced(
        orcaKeys = listOf("filament_extruder_variant", "filament_self_index"),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: filament extruder variant metadata used by the Orca preset variant synchronization path",
        overrideReason = "These keys are PrintConfig-defined Orca metadata rather than normal TabFilament live rows; Android surfaces them on Filament > Advanced as config compatibility."
    ),
    ProcessQualityCore(
        orcaKeys = listOf(
            "initial_layer_print_height",
            "first_layer_height",
            "layer_height",
            "seam_position",
            "precise_outer_wall",
            "only_one_wall_first_layer",
            "only_one_wall_top"
        ),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp and Tab.cpp: core Quality controls are visible in Orca's default process UI",
        overrideReason = "first_layer_height is an auxiliary config key kept in sync with initial_layer_print_height for app compatibility."
    ),
    ProcessQualityAdvanced(
        orcaKeys = listOf(
            "line_width",
            "initial_layer_line_width",
            "outer_wall_line_width",
            "inner_wall_line_width",
            "top_surface_line_width",
            "sparse_infill_line_width",
            "internal_solid_infill_line_width",
            "support_line_width",
            "resolution",
            "slice_closing_radius",
            "enable_arc_fitting",
            "xy_hole_compensation",
            "xy_contour_compensation",
            "elefant_foot_compensation",
            "elefant_foot_compensation_layers",
            "precise_z_height",
            "hole_to_polyhole",
            "hole_to_polyhole_threshold",
            "hole_to_polyhole_twisted",
            "wall_generator",
            "wall_transition_angle",
            "wall_transition_filter_deviation",
            "wall_transition_length",
            "wall_distribution_count",
            "initial_layer_min_bead_width",
            "min_bead_width",
            "min_feature_size",
            "min_length_factor",
            "is_infill_first",
            "wall_direction",
            "print_flow_ratio",
            "set_other_flow_ratios",
            "min_width_top_surface",
            "first_layer_flow_ratio",
            "outer_wall_flow_ratio",
            "inner_wall_flow_ratio",
            "top_solid_infill_flow_ratio",
            "bottom_solid_infill_flow_ratio",
            "overhang_flow_ratio",
            "sparse_infill_flow_ratio",
            "internal_solid_infill_flow_ratio",
            "gap_fill_flow_ratio",
            "support_flow_ratio",
            "support_interface_flow_ratio",
            "bridge_flow",
            "internal_bridge_flow",
            "bridge_density",
            "internal_bridge_density",
            "small_area_infill_flow_compensation",
            "detect_overhang_wall",
            "make_overhang_printable",
            "make_overhang_printable_angle",
            "make_overhang_printable_hole_size",
            "extra_perimeters_on_overhangs",
            "overhang_reverse",
            "overhang_reverse_internal_only",
            "overhang_reverse_threshold",
            "reduce_crossing_wall",
            "max_travel_detour_distance",
            "thick_bridges",
            "thick_internal_bridges",
            "enable_extra_bridge_layer",
            "dont_filter_internal_bridges"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: these surfaced Quality detail controls are advanced or documented app exceptions",
        overrideReason = "Some app compatibility keys are absent or default-mode in the current vendored config surface and are intentionally hidden with nearby Orca advanced detail controls."
    ),
    ProcessSeamAdvanced(
        orcaKeys = listOf(
            "staggered_inner_seams",
            "seam_gap",
            "seam_slope_type",
            "seam_slope_conditional",
            "scarf_angle_threshold",
            "scarf_overhang_threshold",
            "scarf_joint_speed",
            "scarf_joint_flow_ratio",
            "seam_slope_start_height",
            "seam_slope_entire_loop",
            "seam_slope_min_length",
            "seam_slope_steps",
            "seam_slope_inner_walls",
            "role_based_wipe_speed",
            "wipe_speed",
            "wipe_on_loops",
            "wipe_before_external_loop",
            "has_scarf_joint_seam",
            "counterbore_hole_bridging",
            "wall_sequence"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: seam scarf controls and wall_sequence live on Process > Quality"
    ),
    ProcessStrengthCore(
        orcaKeys = listOf(
            "wall_loops",
            "top_shell_layers",
            "top_shell_thickness",
            "top_surface_density",
            "top_surface_pattern",
            "bottom_shell_layers",
            "bottom_shell_thickness",
            "bottom_surface_density",
            "bottom_surface_pattern",
            "internal_solid_infill_pattern"
        ),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: these controls appear in Orca Strength shell/infill groups",
        overrideReason = "Default-mode shell and pattern controls stay visible because Orca places them in normal Strength groups."
    ),
    ProcessStrengthAdvanced(
        orcaKeys = listOf(
            "top_bottom_infill_wall_overlap",
            "detect_thin_wall",
            "infill_direction",
            "sparse_infill_rotate_template",
            "skin_infill_density",
            "skeleton_infill_density",
            "infill_lock_depth",
            "skin_infill_depth",
            "skin_infill_line_width",
            "skeleton_infill_line_width",
            "symmetric_infill_y_axis",
            "infill_shift_step",
            "lateral_lattice_angle_1",
            "lateral_lattice_angle_2",
            "infill_overhang_angle",
            "infill_anchor_max",
            "infill_anchor",
            "solid_infill_direction",
            "solid_infill_rotate_template",
            "gap_fill_target",
            "filter_out_gap_fill",
            "infill_wall_overlap",
            "align_infill_direction_to_model",
            "bridge_angle",
            "internal_bridge_angle",
            "minimum_sparse_infill_area",
            "infill_combination",
            "infill_combination_max_layer_height",
            "alternate_extra_wall",
            "extra_solid_infills",
            "detect_narrow_internal_solid_infill",
            "ensure_vertical_shell_thickness"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: these surfaced Strength detail controls are mode comAdvanced"
    ),
    ProcessSpeedAndAcceleration(
        orcaKeys = listOf(
            "initial_layer_speed",
            "initial_layer_infill_speed",
            "initial_layer_travel_speed",
            "slow_down_layers",
            "outer_wall_speed",
            "inner_wall_speed",
            "small_perimeter_speed",
            "small_perimeter_threshold",
            "sparse_infill_speed",
            "internal_solid_infill_speed",
            "top_surface_speed",
            "gap_infill_speed",
            "support_speed",
            "support_interface_speed",
            "ironing_speed",
            "enable_overhang_speed",
            "slowdown_for_curled_perimeters",
            "bridge_speed",
            "travel_speed",
            "default_acceleration",
            "outer_wall_acceleration",
            "inner_wall_acceleration",
            "bridge_acceleration",
            "sparse_infill_acceleration",
            "internal_solid_infill_acceleration",
            "initial_layer_acceleration",
            "top_surface_acceleration",
            "travel_acceleration",
            "accel_to_decel_enable",
            "accel_to_decel_factor",
            "default_junction_deviation",
            "default_jerk",
            "infill_jerk",
            "top_surface_jerk",
            "travel_jerk",
            "max_volumetric_extrusion_rate_slope",
            "max_volumetric_extrusion_rate_slope_segment_length",
            "extrusion_rate_smoothing_external_perimeter_only"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: current speed and acceleration controls are mode comAdvanced"
    ),
    ProcessInfillCore(
        orcaKeys = listOf("sparse_infill_density", "fill_multiline", "sparse_infill_pattern"),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: these appear in Orca Strength > Infill",
        overrideReason = "Default-mode infill controls stay visible because Orca places them in Strength > Infill."
    ),
    ProcessSupportCore(
        orcaKeys = listOf(
            "enable_support",
            "support_type",
            "support_threshold_angle",
            "support_threshold_overlap",
            "support_on_build_plate_only",
            "support_interface_not_for_body",
            "support_filament",
            "support_interface_filament"
        ),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: support_type, support_threshold_angle, support_threshold_overlap, support_on_build_plate_only, support_filament, support_interface_filament, and support_interface_not_for_body are mode comSimple"
    ),
    ProcessSupportAdvanced(
        orcaKeys = listOf(
            "support_style",
            "support_critical_regions_only",
            "support_remove_small_overhang",
            "raft_first_layer_density",
            "raft_first_layer_expansion",
            "raft_layers",
            "raft_contact_distance",
            "raft_expansion",
            "support_ironing",
            "support_ironing_pattern",
            "support_ironing_flow",
            "support_ironing_spacing",
            "support_top_z_distance",
            "support_bottom_z_distance",
            "support_base_pattern",
            "support_base_pattern_spacing",
            "support_angle",
            "support_interface_top_layers",
            "support_interface_bottom_layers",
            "support_interface_pattern",
            "support_interface_spacing",
            "support_bottom_interface_spacing",
            "support_expansion",
            "support_object_xy_distance",
            "support_object_first_layer_gap",
            "bridge_no_support",
            "max_bridge_length",
            "support_interface_loop_pattern",
            "support_object_elevation",
            "independent_support_layer_height",
            "tree_support_tip_diameter",
            "tree_support_branch_distance",
            "tree_support_branch_distance_organic",
            "tree_support_top_rate",
            "tree_support_branch_angle",
            "tree_support_branch_angle_organic",
            "tree_support_branch_diameter",
            "tree_support_branch_diameter_organic",
            "tree_support_branch_diameter_angle",
            "tree_support_angle_slow",
            "tree_support_auto_brim",
            "tree_support_brim_width",
            "tree_support_wall_count"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: these surfaced support detail controls are mode comAdvanced",
        overrideReason = "Tree support brim controls report default GUI mode in generated metadata, but stay behind Advanced controls with the rest of tree-support detail tuning."
    ),
    ProcessMultimaterialCore(
        orcaKeys = listOf("enable_prime_tower", "prime_tower_width", "prime_volume"),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: enable_prime_tower, prime_tower_width, and prime_volume mode comSimple"
    ),
    ProcessMultimaterialAdvanced(
        orcaKeys = listOf(
            "interface_shells",
            "sparse_infill_filament",
            "prime_tower_skip_points",
            "prime_tower_brim_width",
            "prime_tower_infill_gap",
            "wipe_tower_rotation_angle",
            "wipe_tower_bridging",
            "wipe_tower_extra_spacing",
            "wipe_tower_extra_flow",
            "wipe_tower_max_purge_speed",
            "wipe_tower_wall_type",
            "wipe_tower_cone_angle",
            "wipe_tower_extra_rib_length",
            "wipe_tower_rib_width",
            "wipe_tower_fillet_wall",
            "enable_tower_interface_features",
            "enable_tower_interface_cooldown_during_tower",
            "wipe_tower_no_sparse_layers",
            "single_extruder_multi_material_priming",
            "standby_temperature_delta"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Process > Multimaterial > Prime tower / Filament for Features / Ooze prevention / Advanced; PrintConfig.cpp mode metadata"
    ),
    ProcessMultimaterialFlushOptions(
        orcaKeys = listOf(
            "flush_into_infill",
            "flush_into_objects",
            "flush_into_support"
        ),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Process > Multimaterial > Flush options; PrintConfig.cpp defines these rows without comAdvanced/comDevelop mode"
    ),
    ProcessAdhesionAndFuzzySkinCore(
        orcaKeys = listOf(
            "brim_type",
            "skirt_loops",
            "skirt_height",
            "brim_width",
            "print_sequence",
            "spiral_mode",
            "spiral_mode_smooth",
            "timelapse_type",
            "fuzzy_skin",
            "fuzzy_skin_thickness",
            "fuzzy_skin_point_distance",
            "fuzzy_skin_first_layer",
            "fuzzy_skin_mode",
            "fuzzy_skin_noise_type"
        ),
        visibility = ProfileSettingVisibility.Simple,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: skirt/brim and base fuzzy-skin controls are simple/default visible",
        overrideReason = "Skirt values remain import/export visible and Android native output is covered by the skirt parity device matrix. Default/simple fuzzy-skin controls are shown in Orca's regular Others page."
    ),
    ProcessOthersAdvanced(
        orcaKeys = listOf(
            "skirt_type",
            "min_skirt_length",
            "skirt_distance",
            "skirt_start_angle",
            "skirt_speed",
            "draft_shield",
            "single_loop_draft_shield",
            "brim_object_gap",
            "brim_use_efc_outline",
            "combine_brims",
            "brim_ears",
            "brim_ears_detection_length",
            "brim_ears_max_angle",
            "slicing_mode",
            "print_order",
            "spiral_mode_max_xy_smoothing",
            "spiral_starting_flow_ratio",
            "spiral_finishing_flow_ratio",
            "enable_wrapping_detection",
            "reduce_infill_retraction",
            "gcode_add_line_number",
            "gcode_comments",
            "gcode_label_objects",
            "exclude_object",
            "filament_map_mode",
            "print_extruder_id",
            "print_extruder_variant",
            "allow_mix_temp",
            "allow_multicolor_oneplate",
            "filename_format",
            "post_process",
            "notes"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/slic3r/GUI/Tab.cpp: Process > Others > G-code output / Post-processing Scripts / Notes; PrintConfig.cpp, OrcaSlicer.cpp, and Plater/FilamentMapDialog.cpp for compatibility controls",
        overrideReason = "allow_mix_temp, allow_multicolor_oneplate, filament_map_mode, and print extruder variant keys are project/plate or preset synchronization metadata rather than normal live Process Tab.cpp rows. Android preserves those keys for import/export while rendering the live Others rows."
    ),
    ProcessFuzzySkinDetail(
        orcaKeys = listOf("fuzzy_skin_scale", "fuzzy_skin_octaves", "fuzzy_skin_persistence"),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: fuzzy-skin detail controls are mode comAdvanced"
    ),
    ProcessIroningAdvanced(
        orcaKeys = listOf(
            "ironing_type",
            "ironing_pattern",
            "ironing_flow",
            "ironing_spacing",
            "ironing_inset",
            "ironing_angle",
            "ironing_angle_fixed"
        ),
        visibility = ProfileSettingVisibility.Advanced,
        source = "vendor/orcaslicer/src/libslic3r/PrintConfig.cpp: process ironing controls are mode comAdvanced"
    )
}

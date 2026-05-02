package com.mobileslicer.profiles

internal object NativeConfigKeys {
    object Bed {
        const val Shape = "bed_shape"
        const val PrintableArea = "printable_area"
        const val CurrentType = "curr_bed_type"
        const val DefaultType = "default_bed_type"
        const val MeshMin = "bed_mesh_min"
        const val MeshMax = "bed_mesh_max"
        const val MeshProbeDistance = "bed_mesh_probe_distance"
    }

    object Compatibility {
        const val CompatiblePrinters = "compatible_printers"
        const val PrintCompatiblePrinters = "print_compatible_printers"
    }

    object Filament {
        const val Id = "filament_id"
        const val Ids = "filament_ids"
        const val SettingsId = "filament_settings_id"
        const val Type = "filament_type"
        const val Color = "filament_colour"
        const val MultiColor = "filament_multi_colour"
        const val ColorType = "filament_colour_type"
        const val Map = "filament_map"
        const val MapMode = "filament_map_mode"
        const val SelfIndex = "filament_self_index"
        const val ExtruderVariant = "filament_extruder_variant"
        const val DefaultColor = "default_filament_colour"
        const val Density = "filament_density"
        const val MaxVolumetricSpeed = "filament_max_volumetric_speed"
        const val PressureAdvance = "pressure_advance"
    }

    object PrimeTower {
        const val Enable = "enable_prime_tower"
        const val Width = "prime_tower_width"
        const val PrimeVolume = "prime_volume"
        const val OozePrevention = "ooze_prevention"
        const val WipeTowerWallType = "wipe_tower_wall_type"
        const val Purge = "purge_in_prime_tower"
        const val SingleExtruderMultiMaterial = "single_extruder_multi_material"
        const val SingleExtruderMultiMaterialPriming = "single_extruder_multi_material_priming"
        const val AllowMulticolorOnePlate = "allow_multicolor_oneplate"
        const val FlushIntoInfill = "flush_into_infill"
        const val FlushIntoObjects = "flush_into_objects"
        const val FlushIntoSupport = "flush_into_support"
    }

    object Printer {
        const val SettingsId = "printer_settings_id"
        const val Model = "printer_model"
        const val Variant = "printer_variant"
        const val Inherits = "inherits"
        const val Name = "name"
        const val Notes = "printer_notes"
        const val DefaultPrintProfile = "default_print_profile"
        const val DefaultFilamentProfile = "default_filament_profile"
        const val ExtrudersCount = "extruders_count"
        const val ExtruderId = "printer_extruder_id"
        const val ExtruderVariant = "printer_extruder_variant"
        const val ExtruderVariantList = "extruder_variant_list"
        const val PhysicalExtruderMap = "physical_extruder_map"
        const val NozzleDiameter = "nozzle_diameter"
        const val ExtruderOffset = "extruder_offset"
        const val ExtruderColor = "extruder_colour"
        const val ExtruderAmsCount = "extruder_ams_count"
        const val MachineMaxJunctionDeviation = "machine_max_junction_deviation"
    }

    object Process {
        const val SettingsId = "print_settings_id"
        const val ExtruderId = "print_extruder_id"
        const val ExtruderVariant = "print_extruder_variant"
        const val WallDirection = "wall_direction"
        const val WipeTowerType = "wipe_tower_type"
        const val WipeTowerX = "wipe_tower_x"
        const val WipeTowerY = "wipe_tower_y"
        const val WipeTowerConeAngle = "wipe_tower_cone_angle"
    }

    object Temperature {
        const val CoolPlateInitialLayer = "cool_plate_temp_initial_layer"
        const val CoolPlate = "cool_plate_temp"
        const val TexturedCoolPlateInitialLayer = "textured_cool_plate_temp_initial_layer"
        const val TexturedCoolPlate = "textured_cool_plate_temp"
        const val EngineeringPlateInitialLayer = "eng_plate_temp_initial_layer"
        const val EngineeringPlate = "eng_plate_temp"
        const val SupertackPlateInitialLayer = "supertack_plate_temp_initial_layer"
        const val SupertackPlate = "supertack_plate_temp"
        const val TexturedPlateInitialLayer = "textured_plate_temp_initial_layer"
        const val TexturedPlate = "textured_plate_temp"
        const val BedInitialLayer = "bed_temperature_initial_layer"
        const val Bed = "bed_temperature"
        const val HotPlateInitialLayer = "hot_plate_temp_initial_layer"
        const val HotPlate = "hot_plate_temp"
    }

    object Mobile {
        const val ActiveFilamentSlotCount = "mobile_slicer_active_filament_slot_count"
        const val SingleMaterialNativeSlice = "mobile_slicer_single_material_native_slice"
    }
}

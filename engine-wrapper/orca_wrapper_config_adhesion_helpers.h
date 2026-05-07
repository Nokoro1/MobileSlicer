#ifndef ORCA_WRAPPER_CONFIG_ADHESION_HELPERS_H
#define ORCA_WRAPPER_CONFIG_ADHESION_HELPERS_H

// Private implementation header. Include through orca_wrapper_config_helpers.h after scalar helpers.
static void apply_android_adhesion_overrides(const std::string& json, Slic3r::DynamicPrintConfig& config)
{
    if (const auto value = extract_number(json, "skirt_loops")) {
        const int loops = *value > 0.0 ? static_cast<int>(*value) : 0;
        config.set_deserialize_strict("skirt_loops", std::to_string(loops));
    } else if (const auto value = extract_number(json, "skirts")) {
        const int loops = *value > 0.0 ? static_cast<int>(*value) : 0;
        config.set_deserialize_strict("skirt_loops", std::to_string(loops));
    }
    if (const auto value = extract_number(json, "brim_width")) {
        config.set_deserialize_strict("brim_width", std::to_string(*value));
        // The surfaced app control is manual brim width. Orca defaults brim_type to
        // auto_brim, which leaves brim generation under automatic analysis instead of
        // following the user-specified width directly.
        const auto brim_type = extract_string(json, "brim_type");
        if (!brim_type || (*brim_type == "auto_brim" && *value > 0.0)) {
            config.set_deserialize_strict("brim_type", *value > 0.0 ? "outer_only" : "no_brim");
        }
    }
}

#endif // ORCA_WRAPPER_CONFIG_ADHESION_HELPERS_H

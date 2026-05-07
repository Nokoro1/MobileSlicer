#ifndef ORCA_WRAPPER_CONFIG_SCALAR_HELPERS_H
#define ORCA_WRAPPER_CONFIG_SCALAR_HELPERS_H

// Private implementation header. Include through orca_wrapper_config_helpers.h.
static void set_config_string_if_present(const std::string& json, Slic3r::DynamicPrintConfig& config, const char* key)
{
    if (const auto value = extract_config_scalar_or_list_string(json, key)) {
        config.set_deserialize_strict(key, *value);
    }
}

static void set_config_points_groups_if_present(const std::string& json, Slic3r::DynamicPrintConfig& config, const char* key)
{
    if (const auto value = extract_config_scalar_or_list_string(json, key, '#')) {
        config.set_deserialize_strict(key, *value);
    }
}

static std::string escape_orca_string_vector_item(const std::string& value)
{
    bool should_quote = value.empty();
    for (char ch : value) {
        if (ch == ' ' || ch == '\t' || ch == '\\' || ch == '"' || ch == '\r' || ch == '\n' || ch == ';') {
            should_quote = true;
            break;
        }
    }
    if (!should_quote) {
        return value;
    }

    std::string escaped;
    escaped.reserve(value.size() + 2);
    escaped.push_back('"');
    for (char ch : value) {
        switch (ch) {
            case '\\':
            case '"':
                escaped.push_back('\\');
                escaped.push_back(ch);
                break;
            case '\r':
                escaped += "\\r";
                break;
            case '\n':
                escaped += "\\n";
                break;
            default:
                escaped.push_back(ch);
                break;
        }
    }
    escaped.push_back('"');
    return escaped;
}

static std::string normalize_orca_string_vector_list(const std::string& value)
{
    std::stringstream stream(value);
    std::string item;
    std::vector<std::string> values;
    while (std::getline(stream, item, ',')) {
        values.push_back(escape_orca_string_vector_item(trim_copy(item)));
    }
    std::ostringstream joined;
    for (size_t i = 0; i < values.size(); ++i) {
        if (i > 0) {
            joined << ";";
        }
        joined << values[i];
    }
    return joined.str();
}

static void set_config_orca_string_list_if_present(const std::string& json, Slic3r::DynamicPrintConfig& config, const char* key)
{
    if (const auto value = extract_config_scalar_or_list_string(json, key)) {
        const std::string normalized = normalize_orca_string_vector_list(*value);
        config.set_deserialize_strict(key, normalized);
    }
}

static bool is_orca_string_vector_config_key(const char* key)
{
    static const std::unordered_set<std::string> string_vector_keys = {
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
    };
    return string_vector_keys.find(key) != string_vector_keys.end();
}

static std::string normalize_bool_scalar_or_list_string(const std::string& value)
{
    std::stringstream stream(value);
    std::string item;
    std::vector<std::string> values;
    while (std::getline(stream, item, ',')) {
        std::string normalized = trim_copy(item);
        const std::string lower = lowercase_copy(normalized);
        if (lower == "true" || lower == "yes" || lower == "on") {
            normalized = "1";
        } else if (lower == "false" || lower == "no" || lower == "off") {
            normalized = "0";
        }
        if (!normalized.empty()) {
            values.push_back(normalized);
        }
    }
    std::ostringstream joined;
    for (size_t i = 0; i < values.size(); ++i) {
        if (i > 0) {
            joined << ",";
        }
        joined << values[i];
    }
    return joined.str();
}

static void set_config_bool_list_if_present(const std::string& json, Slic3r::DynamicPrintConfig& config, const char* key)
{
    if (const auto value = extract_config_scalar_or_list_string(json, key)) {
        const std::string normalized = normalize_bool_scalar_or_list_string(*value);
        if (!normalized.empty()) {
            config.set_deserialize_strict(key, normalized);
        }
    }
}

static bool is_bool_vector_config_key(const char* key)
{
    static const std::unordered_set<std::string> bool_vector_keys = {
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
    };
    return bool_vector_keys.find(key) != bool_vector_keys.end();
}

static void set_config_bool_if_present(const std::string& json, Slic3r::DynamicPrintConfig& config, const char* key)
{
    if (const auto value = extract_bool(json, key)) {
        config.set_deserialize_strict(key, *value ? "1" : "0");
    } else if (const auto value = extract_number(json, key)) {
        config.set_deserialize_strict(key, *value != 0.0 ? "1" : "0");
    } else if (const auto value = extract_string(json, key); value && !value->empty()) {
        config.set_deserialize_strict(key, *value);
    }
}

static size_t config_option_size(const Slic3r::DynamicPrintConfig& config, const char* key)
{
    const Slic3r::ConfigOption* option = config.option(key);
    const auto* vector_option = dynamic_cast<const Slic3r::ConfigOptionVectorBase*>(option);
    return vector_option != nullptr ? vector_option->size() : (option != nullptr ? 1 : 0);
}

static std::string config_option_serialized(const Slic3r::DynamicPrintConfig& config, const char* key)
{
    const Slic3r::ConfigOption* option = config.option(key);
    return option != nullptr ? option->serialize() : "";
}

static void apply_bed_temperature_split_override(
    std::optional<double> first_layer_bed_temperature,
    std::optional<double> bed_temperature,
    Slic3r::DynamicPrintConfig& config);

struct OrcaFilamentIdentityDefaults {
    const char* material = "";
    const char* vendor = "Generic";
    double density = 0.0;
    double max_volumetric_speed = 0.0;
    double flow_ratio = 0.0;
    int nozzle_temperature = 0;
    int nozzle_temperature_initial_layer = 0;
    int nozzle_temperature_range_low = 0;
    int nozzle_temperature_range_high = 0;
    int bed_temperature = 0;
    int bed_temperature_initial_layer = 0;
};

static std::optional<OrcaFilamentIdentityDefaults> orca_filament_defaults_from_identity(const std::string& json)
{
    std::string identity;
    for (const char* key : {"filament_settings_id", "filament_ids", "filament_id", "mobile_slicer_orca_filament_path"}) {
        if (const auto value = extract_string(json, key)) {
            identity += " ";
            identity += *value;
        }
    }
    if (identity.empty()) {
        return std::nullopt;
    }
    const std::string lower = lowercase_copy(identity);
    if (lower.find("petg-cf") != std::string::npos) {
        return OrcaFilamentIdentityDefaults{"PETG-CF", "Generic", 1.30, 12.0, 0.95, 255, 255, 230, 280, 80, 80};
    }
    if (lower.find("pla-cf") != std::string::npos) {
        return OrcaFilamentIdentityDefaults{"PLA-CF", "Generic", 1.30, 12.0, 0.95, 220, 220, 190, 240, 55, 55};
    }
    if (lower.find("pa-cf") != std::string::npos) {
        return OrcaFilamentIdentityDefaults{"PA-CF", "Generic", 1.12, 12.0, 0.95, 280, 280, 250, 300, 90, 90};
    }
    if (lower.find("abs") != std::string::npos) {
        return OrcaFilamentIdentityDefaults{"ABS", "Generic", 1.04, 17.0, 0.95, 250, 250, 240, 280, 90, 90};
    }
    if (lower.find("asa") != std::string::npos) {
        return OrcaFilamentIdentityDefaults{"ASA", "Generic", 1.07, 17.0, 0.95, 250, 250, 240, 280, 90, 90};
    }
    if (lower.find("petg") != std::string::npos) {
        return OrcaFilamentIdentityDefaults{"PETG", "Generic", 1.27, 12.0, 0.95, 245, 245, 220, 270, 80, 80};
    }
    if (lower.find("tpu") != std::string::npos) {
        return OrcaFilamentIdentityDefaults{"TPU", "Generic", 1.20, 3.2, 1.0, 230, 230, 210, 250, 50, 50};
    }
    if (lower.find(" pc") != std::string::npos || lower.find("pc ") != std::string::npos) {
        return OrcaFilamentIdentityDefaults{"PC", "Generic", 1.20, 12.0, 0.95, 270, 270, 250, 300, 100, 100};
    }
    if (lower.find(" pla") != std::string::npos || lower.find("pla ") != std::string::npos) {
        return OrcaFilamentIdentityDefaults{"PLA", "Generic", 1.24, 12.0, 0.98, 220, 220, 190, 240, 55, 55};
    }
    return std::nullopt;
}

static void apply_orca_filament_identity_guard(const std::string& json, Slic3r::DynamicPrintConfig& config)
{
    const auto defaults = orca_filament_defaults_from_identity(json);
    if (!defaults) {
        return;
    }

    const std::string current_type = lowercase_copy(extract_string(json, "filament_type").value_or(""));
    const bool type_stale = current_type.empty() || current_type == "pla" && std::string(defaults->material) != "PLA";
    const double density = extract_number(json, "filament_density").value_or(0.0);
    const double volumetric = extract_number(json, "filament_max_volumetric_speed").value_or(0.0);
    const bool numeric_stale = density <= 0.0 || volumetric <= 0.0 ||
        (volumetric <= 2.0 && std::string(defaults->material) != "PLA");
    if (!type_stale && !numeric_stale) {
        return;
    }

    config.set_deserialize_strict("filament_type", defaults->material);
    config.set_deserialize_strict("filament_vendor", defaults->vendor);
    config.set_deserialize_strict("filament_density", std::to_string(defaults->density));
    config.set_deserialize_strict("filament_flow_ratio", std::to_string(defaults->flow_ratio));
    config.set_deserialize_strict("filament_max_volumetric_speed", std::to_string(defaults->max_volumetric_speed));
    config.set_deserialize_strict("nozzle_temperature", std::to_string(defaults->nozzle_temperature));
    config.set_deserialize_strict("nozzle_temperature_initial_layer", std::to_string(defaults->nozzle_temperature_initial_layer));
    config.set_deserialize_strict("nozzle_temperature_range_low", std::to_string(defaults->nozzle_temperature_range_low));
    config.set_deserialize_strict("nozzle_temperature_range_high", std::to_string(defaults->nozzle_temperature_range_high));
    apply_bed_temperature_split_override(defaults->bed_temperature_initial_layer, defaults->bed_temperature, config);

    std::ostringstream message;
    message << "orca_filament_identity_guard material=" << defaults->material
            << " stale_type=" << (type_stale ? 1 : 0)
            << " stale_numeric=" << (numeric_stale ? 1 : 0);
    log_native_info("orca_slice", message.str());
}

static void set_speed_override(Slic3r::DynamicPrintConfig& config, const char* key, double value)
{
    config.set_deserialize_strict(key, std::to_string(value));
}

static bool is_zero_float_or_percent(const std::string& value)
{
    std::string trimmed = value;
    trimmed.erase(trimmed.begin(), std::find_if(trimmed.begin(), trimmed.end(), [](unsigned char ch) {
        return std::isspace(ch) == 0;
    }));
    trimmed.erase(std::find_if(trimmed.rbegin(), trimmed.rend(), [](unsigned char ch) {
        return std::isspace(ch) == 0;
    }).base(), trimmed.end());
    if (trimmed.empty()) {
        return true;
    }
    if (trimmed.size() >= 2 && trimmed.front() == '[' && trimmed.back() == ']') {
        std::string list = trimmed.substr(1, trimmed.size() - 2);
        std::replace(list.begin(), list.end(), ';', ',');
        std::stringstream stream(list);
        std::string item;
        bool saw_value = false;
        while (std::getline(stream, item, ',')) {
            item.erase(item.begin(), std::find_if(item.begin(), item.end(), [](unsigned char ch) {
                return std::isspace(ch) == 0;
            }));
            item.erase(std::find_if(item.rbegin(), item.rend(), [](unsigned char ch) {
                return std::isspace(ch) == 0;
            }).base(), item.end());
            if (item.size() >= 2 && item.front() == '"' && item.back() == '"') {
                item = item.substr(1, item.size() - 2);
            }
            if (item.empty()) {
                continue;
            }
            saw_value = true;
            if (!is_zero_float_or_percent(item)) {
                return false;
            }
        }
        return saw_value;
    }

    const bool percent = !trimmed.empty() && trimmed.back() == '%';
    if (percent) {
        trimmed.pop_back();
    }
    try {
        return std::stod(trimmed) == 0.0;
    } catch (...) {
        return false;
    }
}

static std::optional<std::string> normalize_float_or_percent_scalar(const std::optional<std::string>& value)
{
    if (!value) {
        return std::nullopt;
    }
    std::string trimmed = *value;
    trimmed.erase(trimmed.begin(), std::find_if(trimmed.begin(), trimmed.end(), [](unsigned char ch) {
        return std::isspace(ch) == 0;
    }));
    trimmed.erase(std::find_if(trimmed.rbegin(), trimmed.rend(), [](unsigned char ch) {
        return std::isspace(ch) == 0;
    }).base(), trimmed.end());
    if (trimmed.size() >= 2 && trimmed.front() == '[' && trimmed.back() == ']') {
        std::string list = trimmed.substr(1, trimmed.size() - 2);
        std::replace(list.begin(), list.end(), ';', ',');
        std::stringstream stream(list);
        std::string item;
        while (std::getline(stream, item, ',')) {
            item.erase(item.begin(), std::find_if(item.begin(), item.end(), [](unsigned char ch) {
                return std::isspace(ch) == 0;
            }));
            item.erase(std::find_if(item.rbegin(), item.rend(), [](unsigned char ch) {
                return std::isspace(ch) == 0;
            }).base(), item.end());
            if (item.size() >= 2 && item.front() == '"' && item.back() == '"') {
                item = item.substr(1, item.size() - 2);
            }
            if (!item.empty()) {
                return item;
            }
        }
        return std::nullopt;
    }
    return trimmed.empty() ? std::nullopt : std::optional<std::string>(trimmed);
}

static void set_nonzero_float_or_percent_override(
    Slic3r::DynamicPrintConfig& config,
    const char* key,
    const std::optional<std::string>& value
) {
    const auto scalar = normalize_float_or_percent_scalar(value);
    if (scalar && !is_zero_float_or_percent(*scalar)) {
        config.set_deserialize_strict(key, *scalar);
    }
}

static bool all_float_or_percent_values_zero_or_missing(std::initializer_list<std::optional<std::string>> values)
{
    for (const auto& value : values) {
        if (value && !is_zero_float_or_percent(*value)) {
            return false;
        }
    }
    return true;
}

static void apply_bed_temperature_override(int temperature_c, Slic3r::DynamicPrintConfig& config)
{
    const auto value = std::to_string(temperature_c);
    config.set_deserialize_strict("hot_plate_temp", value);
    config.set_deserialize_strict("hot_plate_temp_initial_layer", value);

    if (const auto* bed_type = config.option<Slic3r::ConfigOptionEnum<Slic3r::BedType>>("curr_bed_type")) {
        const auto current_bed_key = Slic3r::get_bed_temp_key(bed_type->value);
        const auto current_bed_key_first_layer = Slic3r::get_bed_temp_1st_layer_key(bed_type->value);
        if (!current_bed_key.empty()) {
            config.set_deserialize_strict(current_bed_key, value);
        }
        if (!current_bed_key_first_layer.empty()) {
            config.set_deserialize_strict(current_bed_key_first_layer, value);
        }
    }
}

static void apply_bed_temperature_split_override(
    std::optional<double> initial_layer_temperature_c,
    std::optional<double> later_layer_temperature_c,
    Slic3r::DynamicPrintConfig& config)
{
    const auto current_bed = later_layer_temperature_c ? std::to_string(static_cast<int>(*later_layer_temperature_c)) : std::string();
    const auto first_layer_bed = initial_layer_temperature_c ? std::to_string(static_cast<int>(*initial_layer_temperature_c)) : std::string();

    if (!current_bed.empty()) {
        config.set_deserialize_strict("hot_plate_temp", current_bed);
    }
    if (!first_layer_bed.empty()) {
        config.set_deserialize_strict("hot_plate_temp_initial_layer", first_layer_bed);
    }

    if (const auto* bed_type = config.option<Slic3r::ConfigOptionEnum<Slic3r::BedType>>("curr_bed_type")) {
        const auto current_bed_key = Slic3r::get_bed_temp_key(bed_type->value);
        const auto current_bed_key_first_layer = Slic3r::get_bed_temp_1st_layer_key(bed_type->value);
        if (!current_bed.empty() && !current_bed_key.empty()) {
            config.set_deserialize_strict(current_bed_key, current_bed);
        }
        if (!first_layer_bed.empty() && !current_bed_key_first_layer.empty()) {
            config.set_deserialize_strict(current_bed_key_first_layer, first_layer_bed);
        }
    }
}

static void apply_cooling_baseline_override(int cooling_percent, Slic3r::DynamicPrintConfig& config)
{
    const auto value = std::to_string(std::clamp(cooling_percent, 0, 100));
    config.set_deserialize_strict("fan_min_speed", value);
    config.set_deserialize_strict("fan_max_speed", value);
}

static void apply_printable_volume_override(double bed_width_mm, double bed_depth_mm, double max_height_mm, Slic3r::DynamicPrintConfig& config)
{
    const double width = std::max(1.0, bed_width_mm);
    const double depth = std::max(1.0, bed_depth_mm);
    const double height = std::max(1.0, max_height_mm);
    double center_x = width * 0.5;
    double center_y = depth * 0.5;
    if (const auto* printable_area = config.option<Slic3r::ConfigOptionPoints>("printable_area");
        printable_area != nullptr && !printable_area->values.empty()) {
        double existing_min_x = printable_area->values.front().x();
        double existing_max_x = printable_area->values.front().x();
        double existing_min_y = printable_area->values.front().y();
        double existing_max_y = printable_area->values.front().y();
        for (const auto& point : printable_area->values) {
            existing_min_x = std::min(existing_min_x, point.x());
            existing_max_x = std::max(existing_max_x, point.x());
            existing_min_y = std::min(existing_min_y, point.y());
            existing_max_y = std::max(existing_max_y, point.y());
        }
        center_x = (existing_min_x + existing_max_x) * 0.5;
        center_y = (existing_min_y + existing_max_y) * 0.5;
    }
    const double min_x = center_x - width * 0.5;
    const double max_x = center_x + width * 0.5;
    const double min_y = center_y - depth * 0.5;
    const double max_y = center_y + depth * 0.5;

    std::ostringstream area;
    area << min_x << "x" << min_y << ","
         << max_x << "x" << min_y << ","
         << max_x << "x" << max_y << ","
         << min_x << "x" << max_y;

    config.set_deserialize_strict("printable_area", area.str());
    config.set_deserialize_strict("printable_height", std::to_string(height));
}


#endif // ORCA_WRAPPER_CONFIG_SCALAR_HELPERS_H

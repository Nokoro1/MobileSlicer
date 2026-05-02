#include "orca_wrapper.h"
#include "orca_wrapper_calibration.h"
#include "orca_wrapper_gcode.h"
#include "orca_wrapper_model_overrides.h"
#include "orca_wrapper_printable_validation.h"
#include "orca_wrapper_utils.h"

#include "libslic3r/Config.hpp"
#include "libslic3r/Exception.hpp"
#include "libslic3r/Format/STL.hpp"
#include "libslic3r/Layer.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/Arrange.hpp"
#include "libslic3r/Orient.hpp"
#include "libslic3r/calib.hpp"
#include <tbb/global_control.h>

#if defined(MOBILE_SLICER_ENABLE_VGCODE)
#include "libvgcode/include/GCodeInputData.hpp"
#include "libvgcode/include/Types.hpp"
#include "libvgcode/include/Viewer.hpp"
#include <GLES3/gl3.h>
#endif

#include <algorithm>
#include <chrono>
#include <cctype>
#include <cstdint>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <functional>
#include <iomanip>
#include <limits>
#include <cmath>
#include <cstring>
#include <mutex>
#include <optional>
#include <regex>
#include <set>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <unistd.h>
#include <vector>

#ifdef __ANDROID__
#include <android/log.h>
#endif

extern "C" void orca_android_set_resources_dir(const char* path);
extern "C" void orca_android_set_temporary_dir(const char* path);

namespace {

constexpr int ORCA_SUCCESS = 0;
constexpr int ORCA_ERROR_INVALID_ARGUMENT = -1;
constexpr int ORCA_ERROR_LOAD_MODEL = -2;
constexpr int ORCA_ERROR_CONFIG = -3;
constexpr int ORCA_ERROR_SLICE = -4;
constexpr int ORCA_ERROR_ARRANGE_NO_FIT = -5;
constexpr int ORCA_PLATE_PRINTABLE_AREA_ERROR = 1 << 2;
constexpr int ORCA_PLATE_PRINTABLE_HEIGHT_ERROR = 1 << 3;

struct OrcaEngineImpl {
    mutable std::recursive_mutex mutex;
    std::optional<Slic3r::Model> model;
    std::string config_json;
    std::string gcode;
    std::filesystem::path gcode_path;
    bool gcode_path_owned{false};
    std::string gcode_summary;
    bool gcode_summary_enriched{false};
    std::string slice_metrics;
    std::string last_error;
    std::string preview_range_plan;
#if defined(MOBILE_SLICER_ENABLE_VGCODE)
    libvgcode::GCodeInputData cached_preview_input;
    size_t cached_preview_source_size{0};
    bool cached_preview_valid{false};
    bool cached_preview_complete{false};
#endif
};

static constexpr bool kVerboseNativeTimingLogs = false;

using namespace mobileslicer::orca_wrapper;

#if defined(MOBILE_SLICER_ENABLE_VGCODE)
} // namespace

struct OrcaGcodeViewer {
    libvgcode::Viewer viewer;
    libvgcode::GCodeInputData input_data;
    std::string last_error;
    bool initialized{false};
};

namespace {

static void log_native_info(const char* stage, const std::string& message);

static constexpr size_t kMaxPreviewVertices = 1000000;

static constexpr size_t kMaxCachedPreviewVertices = 1000000;
struct PreviewCacheStatus {
    bool cache_hit{false};
    bool cache_valid{false};
    bool cache_complete{false};
    bool cache_built{false};
    bool vertex_limit_reached{false};
    size_t source_size{0};
    size_t cached_vertices{0};
    uint32_t cached_layers{0};
    long parse_ms{0};
};

static void log_print_region_extruders(const char* label, const Slic3r::Print& print)
{
    std::ostringstream message;
    message << label;
    const auto& objects = print.objects();
    for (size_t object_index = 0; object_index < objects.size(); ++object_index) {
        const Slic3r::PrintObject* object = objects[object_index];
        if (object == nullptr) {
            continue;
        }
        message << " object" << object_index;
        message << " object_extruders=[";
        const std::vector<unsigned int> object_extruders = object->object_extruders();
        for (size_t index = 0; index < object_extruders.size(); ++index) {
            if (index > 0) {
                message << ",";
            }
            message << object_extruders[index];
        }
        message << "] regions=[";
        const auto regions = object->all_regions();
        for (size_t region_index = 0; region_index < regions.size(); ++region_index) {
            if (region_index > 0) {
                message << ";";
            }
            const auto& config = regions[region_index].get().config();
            message << config.wall_filament.value
                    << "/" << config.sparse_infill_filament.value
                    << "/" << config.solid_infill_filament.value;
        }
        message << "]";
        message << " layers=[";
        size_t emitted_layers = 0;
        for (const Slic3r::Layer* layer : object->layers()) {
            if (layer == nullptr) {
                continue;
            }
            if (emitted_layers >= 3) {
                break;
            }
            if (emitted_layers > 0) {
                message << ";";
            }
            message << "z" << layer->print_z << ":";
            for (size_t region_index = 0; region_index < layer->regions().size(); ++region_index) {
                if (region_index > 0) {
                    message << ",";
                }
                const Slic3r::LayerRegion* layer_region = layer->regions()[region_index];
                if (layer_region == nullptr) {
                    message << "null";
                    continue;
                }
                const auto& config = layer_region->region().config();
                message << config.wall_filament.value
                        << "/" << config.sparse_infill_filament.value
                        << "/" << config.solid_infill_filament.value
                        << "e" << (layer_region->has_extrusions() ? 1 : 0);
            }
            ++emitted_layers;
        }
        message << "]";
    }
    log_native_info("orca_slice", message.str());
}

#endif

static void log_native_error(const char* stage, const char* message)
{
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_ERROR, "MobileSlicerNative", "%s: %s", stage, message);
#else
    (void)stage;
    (void)message;
#endif
}

static void log_native_info(const char* stage, const std::string& message)
{
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "MobileSlicerNative", "%s: %s", stage, message.c_str());
#else
    (void)stage;
    (void)message;
#endif
}

static void configure_android_slice_runtime_once()
{
#ifdef __ANDROID__
    static std::once_flag once;
    std::call_once(once, [] {
        if (kVerboseNativeTimingLogs) {
            log_native_info("orca_slice", "android_native_parallelism=default");
        }
    });
#endif
}

#if defined(MOBILE_SLICER_ENABLE_VGCODE)
static libvgcode::EViewType preview_view_type_for_loaded_viewer(const libvgcode::Viewer& viewer)
{
    if (viewer.get_used_extruders_count() > 1) {
        return libvgcode::EViewType::ColorPrint;
    }
    return libvgcode::EViewType::FeatureType;
}

static std::optional<libvgcode::EViewType> preview_view_type_from_mobile_id(int view_type, const libvgcode::Viewer& viewer)
{
    if (view_type < 0) {
        return preview_view_type_for_loaded_viewer(viewer);
    }
    switch (view_type) {
        case 1: return libvgcode::EViewType::FeatureType;
        case 2: return libvgcode::EViewType::ColorPrint;
        case 3: return libvgcode::EViewType::Speed;
        case 7: return libvgcode::EViewType::VolumetricFlowRate;
        case 9: return libvgcode::EViewType::LayerTimeLinear;
        case 11: return libvgcode::EViewType::FanSpeed;
        case 12: return libvgcode::EViewType::Temperature;
        default: return std::nullopt;
    }
}
#endif

static bool apply_string_vector_override(
    Slic3r::DynamicPrintConfig& config,
    const std::string& json,
    const std::string& key)
{
    auto values = extract_string_vector_exact(json, key);
    if (!values) {
        return false;
    }
    for (std::string& value : *values) {
        if (value.empty()) {
            value = " ";
        }
    }
    config.set_key_value(key, new Slic3r::ConfigOptionStrings(*values));
    return true;
}

#if defined(MOBILE_SLICER_ENABLE_VGCODE)
static std::optional<libvgcode::Color> parse_preview_hex_color(std::string value)
{
    value = trim_copy(value);
    if (value.empty()) {
        return std::nullopt;
    }
    if (value.front() == '#') {
        value.erase(value.begin());
    }
    if (value.size() != 6) {
        return std::nullopt;
    }
    try {
        const auto parsed = static_cast<unsigned int>(std::stoul(value, nullptr, 16));
        return libvgcode::Color{
            static_cast<uint8_t>((parsed >> 16) & 0xff),
            static_cast<uint8_t>((parsed >> 8) & 0xff),
            static_cast<uint8_t>(parsed & 0xff)
        };
    } catch (...) {
        return std::nullopt;
    }
}

static std::vector<libvgcode::Color> preview_palette_from_config_json(const std::string& config_json)
{
    std::vector<libvgcode::Color> colors;
    auto raw_colors = extract_config_scalar_or_list_string(config_json, "filament_colour");
    if (!raw_colors) {
        raw_colors = extract_config_scalar_or_list_string(config_json, "default_filament_colour");
    }
    if (!raw_colors) {
        return colors;
    }
    std::stringstream stream(*raw_colors);
    std::string item;
    while (std::getline(stream, item, ',')) {
        if (const auto color = parse_preview_hex_color(item)) {
            colors.push_back(*color);
        }
    }
    return colors;
}

static void apply_preview_palette_from_config_json(libvgcode::GCodeInputData& data, const std::string& config_json)
{
    const std::vector<libvgcode::Color> colors = preview_palette_from_config_json(config_json);
    if (colors.empty()) {
        return;
    }
    data.tools_colors = colors;
    data.color_print_colors = colors;
}
#endif

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
    if (const auto value = extract_number(json, "skirt_loops")) {
        config.set_deserialize_strict("skirt_loops", std::to_string(static_cast<int>(*value)));
    } else if (const auto value = extract_number(json, "skirts")) {
        config.set_deserialize_strict("skirt_loops", std::to_string(static_cast<int>(*value)));
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
        "flush_volumes_matrix"
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

#include "orca_wrapper_gcode_io.inc"

static std::optional<double> config_first_float(const Slic3r::DynamicPrintConfig& config, const char* key)
{
    if (const auto* option = config.option<Slic3r::ConfigOptionFloat>(key); option != nullptr) {
        return static_cast<double>(option->value);
    }
    if (const auto* option = config.option<Slic3r::ConfigOptionFloats>(key); option != nullptr && !option->values.empty()) {
        return static_cast<double>(option->get_at(0));
    }
    return std::nullopt;
}

struct TowerPlacementRect {
    double min_x = 0.0;
    double max_x = 0.0;
    double min_y = 0.0;
    double max_y = 0.0;
};

static double rect_distance_sq(const TowerPlacementRect& a, const TowerPlacementRect& b)
{
    const double dx = a.max_x < b.min_x ? b.min_x - a.max_x : b.max_x < a.min_x ? a.min_x - b.max_x : 0.0;
    const double dy = a.max_y < b.min_y ? b.min_y - a.max_y : b.max_y < a.min_y ? a.min_y - b.max_y : 0.0;
    return dx * dx + dy * dy;
}

static bool rects_intersect(const TowerPlacementRect& a, const TowerPlacementRect& b)
{
    return a.min_x < b.max_x && a.max_x > b.min_x && a.min_y < b.max_y && a.max_y > b.min_y;
}

static std::vector<TowerPlacementRect> collect_model_placement_rects(const Slic3r::Model& model, double margin)
{
    std::vector<TowerPlacementRect> rects;
    for (const Slic3r::ModelObject* object : model.objects) {
        if (object == nullptr || !object->printable) {
            continue;
        }
        for (size_t instance_index = 0; instance_index < object->instances.size(); ++instance_index) {
            const Slic3r::BoundingBoxf3 bbox = object->instance_bounding_box(instance_index);
            if (!bbox.defined) {
                continue;
            }
            rects.push_back({
                bbox.min.x() - margin,
                bbox.max.x() + margin,
                bbox.min.y() - margin,
                bbox.max.y() + margin
            });
        }
    }
    return rects;
}

static double max_config_float_vector_value(const Slic3r::DynamicPrintConfig& config, const char* key)
{
    const auto* option = config.option<Slic3r::ConfigOptionFloats>(key);
    if (option == nullptr || option->values.empty()) {
        return 0.0;
    }
    double max_value = 0.0;
    for (double value : option->values) {
        if (std::isfinite(value)) {
            max_value = std::max(max_value, value);
        }
    }
    return max_value;
}

static double estimate_wipe_tower_depth(const Slic3r::DynamicPrintConfig& config, double tower_width)
{
    const double layer_height = std::max(
        0.05,
        config_first_float(config, "layer_height")
            .value_or(config_first_float(config, "initial_layer_print_height").value_or(0.2))
    );
    const int material_slots = std::max(
        1,
        static_cast<int>(std::lround(config_first_float(config, "mobile_slicer_active_filament_slot_count").value_or(1.0)))
    );
    const double max_flush_volume = max_config_float_vector_value(config, "flush_volumes_matrix") *
        static_cast<double>(std::max(1, material_slots - 1));
    const double prime_volume = config_first_float(config, "prime_volume").value_or(45.0);
    const double infill_spacing = std::max(0.05, config_first_float(config, "prime_tower_infill_gap").value_or(100.0) / 100.0);
    const double volume_based_depth =
        (max_flush_volume + prime_volume * static_cast<double>(material_slots)) *
        infill_spacing /
        std::max(1.0, layer_height * tower_width);
    return std::max(tower_width, volume_based_depth * 1.25);
}

static TowerPlacementRect tower_rect(double x, double y, double width, double depth, double brim_width, double margin)
{
    return {
        x - brim_width - margin,
        x + width + brim_width + margin,
        y - brim_width - margin,
        y + depth + brim_width + margin
    };
}

static void clamp_wipe_tower_to_printable_area(Slic3r::DynamicPrintConfig& config)
{
    if (const auto* enabled = config.option<Slic3r::ConfigOptionBool>("enable_prime_tower"); enabled == nullptr || !enabled->value) {
        return;
    }
    const PrintableVolumeBounds bounds = extract_printable_volume_bounds(config);
    if (!bounds.valid) {
        return;
    }

    constexpr double margin = 5.0;
    const double tower_width = std::max(1.0, config_first_float(config, "prime_tower_width").value_or(35.0));
    const double brim_width = std::max(0.0, config_first_float(config, "prime_tower_brim_width").value_or(0.0));
    const double tower_depth = estimate_wipe_tower_depth(config, tower_width);
    const double min_x = bounds.min_x + brim_width + margin;
    const double min_y = bounds.min_y + brim_width + margin;
    const double max_x = bounds.max_x - tower_width - brim_width - margin;
    const double max_y = bounds.max_y - tower_depth - brim_width - margin;
    if (max_x < min_x || max_y < min_y) {
        std::ostringstream message;
        message << "wipe_tower_clamp failed reason=tower_keepout_too_large"
                << " footprint=[" << tower_width << "x" << tower_depth << "]"
                << " printable=[" << bounds.min_x << "," << bounds.max_x
                << "]x[" << bounds.min_y << "," << bounds.max_y << "]";
        log_native_info("orca_slice", message.str());
        throw std::runtime_error(
            "Prime tower does not fit inside the selected printer printable area. Reduce tower width, use fewer material slots, or choose a larger build plate."
        );
    }
    double tower_x = config_first_float(config, "wipe_tower_x").value_or(bounds.min_x);
    double tower_y = config_first_float(config, "wipe_tower_y").value_or(bounds.min_y);
    const double clamped_x = std::clamp(tower_x, min_x, max_x);
    const double clamped_y = std::clamp(tower_y, min_y, max_y);

    if (std::abs(clamped_x - tower_x) > 0.001 || std::abs(clamped_y - tower_y) > 0.001) {
        config.set_deserialize_strict("wipe_tower_x", std::to_string(clamped_x));
        config.set_deserialize_strict("wipe_tower_y", std::to_string(clamped_y));
        std::ostringstream message;
        message << "wipe_tower_clamped"
                << " from=[" << tower_x << "," << tower_y << "]"
                << " to=[" << clamped_x << "," << clamped_y << "]"
                << " printable=[" << bounds.min_x << "," << bounds.max_x
                << "]x[" << bounds.min_y << "," << bounds.max_y << "]"
                << " footprint=[" << tower_width << "x" << tower_depth << "]"
                << " brim=" << brim_width;
        log_native_info("orca_slice", message.str());
    }
}

static void relocate_wipe_tower_away_from_objects(Slic3r::DynamicPrintConfig& config, const Slic3r::Model& model)
{
    if (const auto* enabled = config.option<Slic3r::ConfigOptionBool>("enable_prime_tower"); enabled == nullptr || !enabled->value) {
        return;
    }
    const PrintableVolumeBounds bounds = extract_printable_volume_bounds(config);
    if (!bounds.valid) {
        return;
    }

    const double margin = 5.0;
    const double tower_width = std::max(1.0, config_first_float(config, "prime_tower_width").value_or(35.0));
    const double brim_width = std::max(0.0, config_first_float(config, "prime_tower_brim_width").value_or(0.0));
    const double tower_depth = estimate_wipe_tower_depth(config, tower_width);
    const double min_x = bounds.min_x + brim_width + margin;
    const double min_y = bounds.min_y + brim_width + margin;
    const double max_x = bounds.max_x - tower_width - brim_width - margin;
    const double max_y = bounds.max_y - tower_depth - brim_width - margin;
    if (max_x < min_x || max_y < min_y) {
        log_native_info("orca_slice", "wipe_tower_relocation failed reason=tower_keepout_too_large");
        throw std::runtime_error(
            "Prime tower does not fit inside the selected printer printable area. Reduce tower width, use fewer material slots, or choose a larger build plate."
        );
    }

    const std::vector<TowerPlacementRect> object_rects = collect_model_placement_rects(model, margin);
    if (object_rects.empty()) {
        return;
    }

    const double current_x = std::clamp(config_first_float(config, "wipe_tower_x").value_or(min_x), min_x, max_x);
    const double current_y = std::clamp(config_first_float(config, "wipe_tower_y").value_or(min_y), min_y, max_y);
    const auto is_clear = [&](const TowerPlacementRect& rect) {
        return std::none_of(object_rects.begin(), object_rects.end(), [&](const TowerPlacementRect& object_rect) {
            return rects_intersect(rect, object_rect);
        });
    };

    const TowerPlacementRect current_rect = tower_rect(current_x, current_y, tower_width, tower_depth, brim_width, margin);
    if (is_clear(current_rect)) {
        if (std::abs(current_x - config_first_float(config, "wipe_tower_x").value_or(current_x)) > 0.001 ||
            std::abs(current_y - config_first_float(config, "wipe_tower_y").value_or(current_y)) > 0.001) {
            config.set_deserialize_strict("wipe_tower_x", std::to_string(current_x));
            config.set_deserialize_strict("wipe_tower_y", std::to_string(current_y));
        }
        return;
    }

    struct Candidate {
        double x = 0.0;
        double y = 0.0;
        double score = -1.0;
    };
    std::optional<Candidate> best;
    auto consider = [&](double x, double y) {
        x = std::clamp(x, min_x, max_x);
        y = std::clamp(y, min_y, max_y);
        const TowerPlacementRect rect = tower_rect(x, y, tower_width, tower_depth, brim_width, margin);
        if (!is_clear(rect)) {
            return;
        }
        double nearest_object_distance_sq = std::numeric_limits<double>::max();
        for (const TowerPlacementRect& object_rect : object_rects) {
            nearest_object_distance_sq = std::min(nearest_object_distance_sq, rect_distance_sq(rect, object_rect));
        }
        const double edge_distance = std::min({
            std::abs(x - min_x),
            std::abs(y - min_y),
            std::abs(max_x - x),
            std::abs(max_y - y)
        });
        const double current_distance_sq = (x - current_x) * (x - current_x) + (y - current_y) * (y - current_y);
        const double score = nearest_object_distance_sq - edge_distance * 0.01 - current_distance_sq * 0.0001;
        if (!best.has_value() || score > best->score) {
            best = Candidate{x, y, score};
        }
    };

    consider(min_x, min_y);
    consider(max_x, min_y);
    consider(min_x, max_y);
    consider(max_x, max_y);
    const double step = 10.0;
    for (double y = min_y; y <= max_y + 0.001; y += step) {
        for (double x = min_x; x <= max_x + 0.001; x += step) {
            consider(x, y);
        }
    }

    if (!best.has_value()) {
        log_native_info("orca_slice", "wipe_tower_relocation failed reason=no_clear_keepout");
        throw std::runtime_error(
            "Prime tower does not fit with the current object layout. Move objects apart, reduce tower width, or use fewer material slots."
        );
    }

    config.set_deserialize_strict("wipe_tower_x", std::to_string(best->x));
    config.set_deserialize_strict("wipe_tower_y", std::to_string(best->y));
    std::ostringstream message;
    message
        << "wipe_tower_relocated"
        << " from=[" << current_x << "," << current_y << "]"
        << " to=[" << best->x << "," << best->y << "]"
        << " footprint=[" << tower_width << "x" << tower_depth << "]"
        << " brim=" << brim_width
        << " objects=" << object_rects.size();
    log_native_info("orca_slice", message.str());
}

} // namespace

struct OrcaEngine {
    OrcaEngineImpl impl;
};

static void clear_last_error(OrcaEngine* engine)
{
    if (engine != nullptr) {
        std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
        engine->impl.last_error.clear();
    }
}

static void set_last_error(OrcaEngine* engine, const std::string& message)
{
    if (engine != nullptr) {
        std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
        engine->impl.last_error = message;
    }
}

static bool ensure_gcode_loaded_unlocked(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return false;
    }
    if (engine->impl.gcode.empty() && !engine->impl.gcode_path.empty()) {
        engine->impl.gcode = read_file_to_string(engine->impl.gcode_path);
    }
    return !engine->impl.gcode.empty();
}

static void clear_generated_gcode(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (!engine->impl.gcode_path.empty()) {
        std::error_code ec;
        if (engine->impl.gcode_path_owned) {
            std::filesystem::remove(engine->impl.gcode_path, ec);
        }
        engine->impl.gcode_path.clear();
        engine->impl.gcode_path_owned = false;
    }
    engine->impl.gcode.clear();
    engine->impl.gcode_summary.clear();
    engine->impl.gcode_summary_enriched = false;
    engine->impl.slice_metrics.clear();
#if defined(MOBILE_SLICER_ENABLE_VGCODE)
    engine->impl.cached_preview_input = libvgcode::GCodeInputData{};
    engine->impl.cached_preview_source_size = 0;
    engine->impl.cached_preview_valid = false;
    engine->impl.cached_preview_complete = false;
#endif
}

static Slic3r::Model load_transformed_plate_model_for_planning(
    const char* const* paths,
    const double* transforms,
    const int* extruder_ids,
    int count)
{
    Slic3r::Model combined_model;
    std::unordered_map<std::string, Slic3r::Model> model_cache;
    for (int index = 0; index < count; ++index) {
        const char* path = paths[index];
        if (path == nullptr || path[0] == '\0') {
            throw std::runtime_error("plate model path is empty");
        }

        const std::string path_key(path);
        auto cached_model = model_cache.find(path_key);
        if (cached_model == model_cache.end()) {
            Slic3r::Model model_for_cache;
            if (has_stl_extension(path)) {
                if (!Slic3r::load_stl(path, &model_for_cache, nullptr, nullptr, 80)) {
                    throw std::runtime_error("stl load failed");
                }
                model_for_cache.add_default_instances();
                for (Slic3r::ModelObject* object : model_for_cache.objects) {
                    object->input_file = path;
                }
            } else {
                model_for_cache = Slic3r::Model::read_from_file(
                    path,
                    nullptr,
                    nullptr,
                    Slic3r::LoadStrategy::AddDefaultInstances | Slic3r::LoadStrategy::LoadModel);
            }
            cached_model = model_cache.emplace(path_key, std::move(model_for_cache)).first;
        }
        const Slic3r::Model& loaded_model = cached_model->second;
        if (loaded_model.objects.empty()) {
            throw std::runtime_error("loaded plate model contains no objects");
        }

        const int transform_offset = index * 7;
        const double x_mm = transforms[transform_offset + 0];
        const double y_mm = transforms[transform_offset + 1];
        const double z_mm = transforms[transform_offset + 2];
        const double rotation_x = transforms[transform_offset + 3];
        const double rotation_y = transforms[transform_offset + 4];
        const double rotation_z = transforms[transform_offset + 5];
        const double uniform_scale = transforms[transform_offset + 6];
        if (!std::isfinite(x_mm) ||
            !std::isfinite(y_mm) ||
            !std::isfinite(z_mm) ||
            !std::isfinite(rotation_x) ||
            !std::isfinite(rotation_y) ||
            !std::isfinite(rotation_z) ||
            !std::isfinite(uniform_scale) ||
            uniform_scale <= 0.0) {
            throw std::runtime_error("invalid plate model transform");
        }

        const int extruder_id = std::max(1, extruder_ids[index]);
        const Slic3r::Vec3d offset(x_mm, y_mm, z_mm);
        const Slic3r::Vec3d rotation(rotation_x, rotation_y, rotation_z);
        const Slic3r::Vec3d scaling(uniform_scale, uniform_scale, uniform_scale);
        for (Slic3r::ModelObject* source_object : loaded_model.objects) {
            if (source_object == nullptr) {
                continue;
            }
            if (source_object->instances.empty()) {
                source_object->add_instance();
            }
            Slic3r::ModelObject* combined_object = combined_model.add_object(*source_object);
            if (combined_object == nullptr) {
                continue;
            }
            combined_object->input_file = path;
            combined_object->config.set_key_value("extruder", new Slic3r::ConfigOptionInt(extruder_id));
            for (Slic3r::ModelVolume* volume : combined_object->volumes) {
                if (volume != nullptr) {
                    volume->config.set_key_value("extruder", new Slic3r::ConfigOptionInt(extruder_id));
                }
            }
            if (combined_object->instances.empty()) {
                combined_object->add_instance();
            }
            for (Slic3r::ModelInstance* instance : combined_object->instances) {
                if (instance != nullptr) {
                    instance->set_scaling_factor(scaling);
                    instance->set_rotation(rotation);
                    instance->set_offset(offset);
                }
            }
            combined_object->invalidate_bounding_box();
        }
    }
    if (combined_model.objects.empty()) {
        throw std::runtime_error("plate contains no objects");
    }
    return combined_model;
}

static std::optional<Slic3r::arrangement::ArrangePolygon> wipe_tower_arrange_exclusion(const Slic3r::DynamicPrintConfig& config)
{
    const auto* enabled = config.option<Slic3r::ConfigOptionBool>("enable_prime_tower");
    if (enabled == nullptr || !enabled->value) {
        return std::nullopt;
    }
    const PrintableVolumeBounds bounds = extract_printable_volume_bounds(config);
    if (!bounds.valid) {
        return std::nullopt;
    }
    constexpr double margin = 5.0;
    const double tower_width = std::max(1.0, config_first_float(config, "prime_tower_width").value_or(35.0));
    const double brim_width = std::max(0.0, config_first_float(config, "prime_tower_brim_width").value_or(0.0));
    const double tower_depth = estimate_wipe_tower_depth(config, tower_width);
    const double x = std::clamp(
        config_first_float(config, "wipe_tower_x").value_or(bounds.max_x - tower_width - brim_width - margin),
        bounds.min_x + brim_width + margin,
        bounds.max_x - tower_width - brim_width - margin
    );
    const double y = std::clamp(
        config_first_float(config, "wipe_tower_y").value_or(bounds.max_y - tower_depth - brim_width - margin),
        bounds.min_y + brim_width + margin,
        bounds.max_y - tower_depth - brim_width - margin
    );
    const TowerPlacementRect rect = tower_rect(x, y, tower_width, tower_depth, brim_width, margin);
    Slic3r::Polygon contour;
    contour.points = {
        Slic3r::Point(Slic3r::scaled(rect.min_x), Slic3r::scaled(rect.min_y)),
        Slic3r::Point(Slic3r::scaled(rect.max_x), Slic3r::scaled(rect.min_y)),
        Slic3r::Point(Slic3r::scaled(rect.max_x), Slic3r::scaled(rect.max_y)),
        Slic3r::Point(Slic3r::scaled(rect.min_x), Slic3r::scaled(rect.max_y))
    };
    Slic3r::arrangement::ArrangePolygon ap;
    ap.poly.contour = std::move(contour);
    ap.bed_idx = 0;
    ap.is_virt_object = true;
    ap.is_wipe_tower = true;
    ap.name = "Prime tower";
    return ap;
}

extern "C" OrcaEngine* orca_create(void)
{
    try {
        return new OrcaEngine();
    } catch (...) {
        return nullptr;
    }
}

extern "C" void orca_set_runtime_paths(const char* resources_dir, const char* temporary_dir)
{
    orca_android_set_resources_dir(resources_dir);
    orca_android_set_temporary_dir(temporary_dir);
}

extern "C" void orca_destroy(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return;
    }
    {
        std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
        clear_generated_gcode(engine);
    }
    delete engine;
}

extern "C" int orca_load_model(OrcaEngine* engine, const char* path)
{
    if (engine == nullptr || path == nullptr || path[0] == '\0') {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);

    try {
        Slic3r::Model model;
        if (has_stl_extension(path)) {
            if (!Slic3r::load_stl(path, &model, nullptr, nullptr, 80)) {
                set_last_error(engine, "stl load failed");
                return ORCA_ERROR_LOAD_MODEL;
            }
            model.add_default_instances();
            for (Slic3r::ModelObject* object : model.objects) {
                object->input_file = path;
            }
        } else {
            model = Slic3r::Model::read_from_file(
                path,
                nullptr,
                nullptr,
                Slic3r::LoadStrategy::AddDefaultInstances | Slic3r::LoadStrategy::LoadModel);
        }

        if (model.objects.empty()) {
            set_last_error(engine, "loaded model contains no objects");
            return ORCA_ERROR_LOAD_MODEL;
        }

        engine->impl.model = std::move(model);
        clear_generated_gcode(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_load_model", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_load_model", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" int orca_load_plate_models(OrcaEngine* engine, const char* const* paths, const double* transforms, const int* extruder_ids, int count)
{
    if (engine == nullptr || paths == nullptr || transforms == nullptr || extruder_ids == nullptr || count <= 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);

    try {
        Slic3r::Model combined_model;
        std::unordered_map<std::string, Slic3r::Model> model_cache;
        for (int index = 0; index < count; ++index) {
            const char* path = paths[index];
            if (path == nullptr || path[0] == '\0') {
                set_last_error(engine, "plate model path is empty");
                return ORCA_ERROR_INVALID_ARGUMENT;
            }

            const std::string path_key(path);
            auto cached_model = model_cache.find(path_key);
            if (cached_model == model_cache.end()) {
                Slic3r::Model model_for_cache;
                if (has_stl_extension(path)) {
                    if (!Slic3r::load_stl(path, &model_for_cache, nullptr, nullptr, 80)) {
                        set_last_error(engine, "stl load failed");
                        return ORCA_ERROR_LOAD_MODEL;
                    }
                    model_for_cache.add_default_instances();
                    for (Slic3r::ModelObject* object : model_for_cache.objects) {
                        object->input_file = path;
                    }
                } else {
                    model_for_cache = Slic3r::Model::read_from_file(
                        path,
                        nullptr,
                        nullptr,
                        Slic3r::LoadStrategy::AddDefaultInstances | Slic3r::LoadStrategy::LoadModel);
                }
                cached_model = model_cache.emplace(path_key, std::move(model_for_cache)).first;
            }
            const Slic3r::Model& loaded_model = cached_model->second;

            if (loaded_model.objects.empty()) {
                set_last_error(engine, "loaded plate model contains no objects");
                return ORCA_ERROR_LOAD_MODEL;
            }

            const int transform_offset = index * 7;
            const double x_mm = transforms[transform_offset + 0];
            const double y_mm = transforms[transform_offset + 1];
            const double z_mm = transforms[transform_offset + 2];
            const double rotation_x = transforms[transform_offset + 3];
            const double rotation_y = transforms[transform_offset + 4];
            const double rotation_z = transforms[transform_offset + 5];
            const double uniform_scale = transforms[transform_offset + 6];
            const int extruder_id = std::max(1, extruder_ids[index]);
            if (!std::isfinite(x_mm) ||
                !std::isfinite(y_mm) ||
                !std::isfinite(z_mm) ||
                !std::isfinite(rotation_x) ||
                !std::isfinite(rotation_y) ||
                !std::isfinite(rotation_z) ||
                !std::isfinite(uniform_scale) ||
                uniform_scale <= 0.0) {
                set_last_error(engine, "invalid plate model transform");
                return ORCA_ERROR_INVALID_ARGUMENT;
            }

            const Slic3r::Vec3d offset(x_mm, y_mm, z_mm);
            const Slic3r::Vec3d rotation(rotation_x, rotation_y, rotation_z);
            const Slic3r::Vec3d scaling(uniform_scale, uniform_scale, uniform_scale);
            for (Slic3r::ModelObject* source_object : loaded_model.objects) {
                if (source_object == nullptr) {
                    continue;
                }
                if (source_object->instances.empty()) {
                    source_object->add_instance();
                }
                Slic3r::ModelObject* combined_object = combined_model.add_object(*source_object);
                if (combined_object == nullptr) {
                    continue;
                }
                combined_object->input_file = path;
                combined_object->config.set_key_value("extruder", new Slic3r::ConfigOptionInt(extruder_id));
                combined_object->config.set_key_value("wall_filament", new Slic3r::ConfigOptionInt(extruder_id));
                combined_object->config.set_key_value("sparse_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                combined_object->config.set_key_value("solid_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                int assigned_volume_count = 0;
                for (Slic3r::ModelVolume* volume : combined_object->volumes) {
                    if (volume == nullptr) {
                        continue;
                    }
                    volume->config.set_key_value("extruder", new Slic3r::ConfigOptionInt(extruder_id));
                    volume->config.set_key_value("wall_filament", new Slic3r::ConfigOptionInt(extruder_id));
                    volume->config.set_key_value("sparse_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                    volume->config.set_key_value("solid_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                    ++assigned_volume_count;
                }
                {
                    std::ostringstream message;
                    message << "plate_model index=" << index
                            << " extruder=" << extruder_id
                            << " volumes=" << assigned_volume_count
                            << " path=" << path;
                    log_native_info("orca_load_plate_models", message.str());
                }
                if (combined_object->instances.empty()) {
                    combined_object->add_instance();
                }
                for (Slic3r::ModelInstance* instance : combined_object->instances) {
                    if (instance != nullptr) {
                        instance->set_scaling_factor(scaling);
                        instance->set_rotation(rotation);
                        instance->set_offset(offset);
                    }
                }
                combined_object->invalidate_bounding_box();
            }
        }

        if (combined_model.objects.empty()) {
            set_last_error(engine, "plate contains no objects");
            return ORCA_ERROR_LOAD_MODEL;
        }

        engine->impl.model = std::move(combined_model);
        clear_generated_gcode(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_load_plate_models", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_load_plate_models", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" int orca_plan_plate_arrangement(OrcaEngine* engine, const char* const* paths, const double* transforms, const int* extruder_ids, int count, const char* config_json, int allow_rotation, double* out_transforms)
{
    if (engine == nullptr || paths == nullptr || transforms == nullptr || extruder_ids == nullptr || out_transforms == nullptr || count <= 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);

    try {
        Slic3r::DynamicPrintConfig config = Slic3r::DynamicPrintConfig::full_print_config();
        apply_android_runtime_gcode_baseline(config);
        apply_json_overrides(config_json != nullptr ? std::string(config_json) : engine->impl.config_json, config);

        Slic3r::Model model = load_transformed_plate_model_for_planning(paths, transforms, extruder_ids, count);
        Slic3r::arrangement::ArrangePolygons items;
        items.reserve(static_cast<size_t>(count));
        std::vector<Slic3r::ModelInstance*> instances;
        instances.reserve(static_cast<size_t>(count));

        int item_index = 0;
        for (Slic3r::ModelObject* object : model.objects) {
            if (object == nullptr) {
                continue;
            }
            for (size_t instance_index = 0; instance_index < object->instances.size(); ++instance_index) {
                Slic3r::ModelInstance* instance = object->instances[instance_index];
                if (instance == nullptr) {
                    continue;
                }
                Slic3r::arrangement::ArrangePolygon ap;
                instance->get_arrange_polygon(&ap, config);
                const auto original_ap_bbox = ap.poly.contour.bounding_box();
                const double polygon_area_mm2 = std::abs(ap.poly.contour.area()) *
                                                static_cast<double>(SCALING_FACTOR * SCALING_FACTOR);
                const double polygon_bbox_width_mm = Slic3r::unscale<double>(original_ap_bbox.size().x());
                const double polygon_bbox_depth_mm = Slic3r::unscale<double>(original_ap_bbox.size().y());
                const double polygon_bbox_area_mm2 = std::max(0.0, polygon_bbox_width_mm) * std::max(0.0, polygon_bbox_depth_mm);
                const bool unusable_polygon =
                    ap.poly.contour.points.size() < 3 ||
                    !std::isfinite(polygon_area_mm2) ||
                    polygon_area_mm2 < 1.0 ||
                    (polygon_bbox_area_mm2 > 1.0 && polygon_area_mm2 > polygon_bbox_area_mm2 * 100.0);
                if (unusable_polygon) {
                    std::ostringstream message;
                    message << "Native Orca arrange polygon is invalid for "
                            << (object->name.empty() ? "Object" : object->name)
                            << " points=" << ap.poly.contour.points.size()
                            << " area_mm2=" << polygon_area_mm2
                            << " bbox=(" << polygon_bbox_width_mm << "," << polygon_bbox_depth_mm << ").";
                    set_last_error(engine, message.str());
                    log_native_error("orca_plan_plate_arrangement", message.str().c_str());
                    return ORCA_ERROR_ARRANGE_NO_FIT;
                }
                {
                    const auto ap_bbox = ap.poly.contour.bounding_box();
                    std::ostringstream message;
                    message << "input id=" << item_index
                            << " points=" << ap.poly.contour.points.size()
                            << " area_mm2=" << (std::abs(ap.poly.contour.area()) *
                                static_cast<double>(SCALING_FACTOR * SCALING_FACTOR))
                            << " bbox=(" << Slic3r::unscale<double>(ap_bbox.size().x())
                            << "," << Slic3r::unscale<double>(ap_bbox.size().y()) << ")"
                            << " trans=(" << Slic3r::unscale<double>(ap.translation.x())
                            << "," << Slic3r::unscale<double>(ap.translation.y()) << ")"
                            << " extruders=[";
                    for (size_t extruder_index = 0; extruder_index < ap.extrude_ids.size(); ++extruder_index) {
                        if (extruder_index > 0) {
                            message << ",";
                        }
                        message << ap.extrude_ids[extruder_index];
                    }
                    message << "]";
                    log_native_info("orca_plan_plate_arrangement", message.str());
                }
                // Match Orca's ArrangeJob/ModelArrange helpers: selected items
                // start on the physical bed. libnest2d only fixes items when
                // markAsFixedInBin() is called; a negative bin id is also its
                // "unfit" sentinel and will be skipped by FirstFit.
                ap.bed_idx = 0;
                ap.itemid = item_index++;
                ap.name = object->name.empty() ? "Object" : object->name;
                ap.height = object->instance_bounding_box(instance_index).size().z();
                items.emplace_back(std::move(ap));
                instances.push_back(instance);
            }
        }

        if (items.size() != static_cast<size_t>(count)) {
            set_last_error(engine, "Native arrange currently requires one arrangeable STL instance per plate object.");
            return ORCA_ERROR_ARRANGE_NO_FIT;
        }

        Slic3r::arrangement::ArrangePolygons excludes;
        if (auto tower = wipe_tower_arrange_exclusion(config)) {
            excludes.emplace_back(std::move(*tower));
        }

        Slic3r::arrangement::ArrangeParams params;
        params.min_obj_distance = Slic3r::scaled(std::max(0.0, Slic3r::min_object_distance(config)));
        params.allow_rotations = allow_rotation != 0;
        params.allow_multi_materials_on_same_plate = true;
        params.parallel = false;
        params.stopcondition = []() { return false; };
        params.progressind = [](unsigned packed_count, std::string name) {
            log_native_info("orca_plan_plate_arrangement",
                "packed count=" + std::to_string(packed_count) + " name=" + name);
        };
        Slic3r::arrangement::update_arrange_params(params, &config, items);
        Slic3r::arrangement::update_selected_items_inflation(items, &config, params);

        Slic3r::Points bed_points = Slic3r::arrangement::get_shrink_bedpts(&config, params);

        {
            std::ostringstream message;
            message << "items=" << items.size() << " excludes=" << excludes.size()
                    << " bed_points=" << bed_points.size()
                    << " min_distance=" << Slic3r::unscale<double>(params.min_obj_distance)
                    << " allow_rotation=" << (params.allow_rotations ? 1 : 0)
                    << " parallel=" << (params.parallel ? 1 : 0)
                    << " params=" << params.to_json()
                    << " bed=[";
            for (size_t index = 0; index < bed_points.size(); ++index) {
                if (index > 0) {
                    message << ",";
                }
                message << "(" << Slic3r::unscale<double>(bed_points[index].x())
                        << "," << Slic3r::unscale<double>(bed_points[index].y()) << ")";
            }
            message << "]";
            log_native_info("orca_plan_plate_arrangement", message.str());
        }
        Slic3r::arrangement::arrange(items, excludes, bed_points, params);
        {
            std::ostringstream message;
            message << "result";
            for (size_t index = 0; index < items.size(); ++index) {
                const auto& item = items[index];
                const auto transformed_bbox = item.transformed_poly().contour.bounding_box();
                message << " index=" << index
                        << " order=" << item.itemid
                        << " bed=" << item.bed_idx
                        << " xy=(" << Slic3r::unscale<double>(item.translation.x())
                        << "," << Slic3r::unscale<double>(item.translation.y()) << ")"
                        << " rz=" << item.rotation
                        << " bboxMin=(" << Slic3r::unscale<double>(transformed_bbox.min.x())
                        << "," << Slic3r::unscale<double>(transformed_bbox.min.y()) << ")"
                        << " bboxMax=(" << Slic3r::unscale<double>(transformed_bbox.max.x())
                        << "," << Slic3r::unscale<double>(transformed_bbox.max.y()) << ")";
            }
            log_native_info("orca_plan_plate_arrangement", message.str());
        }
        for (size_t index = 0; index < items.size(); ++index) {
            const auto& item = items[index];
            if (!item.is_arranged() || item.bed_idx != 0) {
                std::ostringstream message;
                message << "Objects do not fit on the selected build plate.";
                if (!item.name.empty()) {
                    message << " " << item.name << " could not be arranged.";
                }
                set_last_error(engine, message.str());
                return ORCA_ERROR_ARRANGE_NO_FIT;
            }
            const size_t offset = index * 7;
            Slic3r::ModelInstance* instance = index < instances.size() ? instances[index] : nullptr;
            if (instance == nullptr) {
                set_last_error(engine, "Native arrange could not resolve an arranged object instance.");
                return ORCA_ERROR_LOAD_MODEL;
            }
            instance->apply_arrange_result(item.translation.cast<double>(), item.rotation);
            const Slic3r::Vec3d arranged_offset = instance->get_offset();
            const Slic3r::Vec3d arranged_rotation = instance->get_rotation();
            const Slic3r::Vec3d arranged_scaling = instance->get_scaling_factor();
            out_transforms[offset + 0] = arranged_offset.x();
            out_transforms[offset + 1] = arranged_offset.y();
            out_transforms[offset + 2] = arranged_offset.z();
            out_transforms[offset + 3] = arranged_rotation.x();
            out_transforms[offset + 4] = arranged_rotation.y();
            out_transforms[offset + 5] = arranged_rotation.z();
            out_transforms[offset + 6] = arranged_scaling.x();
        }

        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_plan_plate_arrangement", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_plan_plate_arrangement", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" int orca_plan_auto_orientation(OrcaEngine* engine, const char* const* paths, const double* transforms, const int* extruder_ids, int count, const char* config_json, double* out_transforms)
{
    if (engine == nullptr || paths == nullptr || transforms == nullptr || extruder_ids == nullptr || out_transforms == nullptr || count <= 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);

    try {
        Slic3r::DynamicPrintConfig config = Slic3r::DynamicPrintConfig::full_print_config();
        apply_android_runtime_gcode_baseline(config);
        apply_json_overrides(config_json != nullptr ? std::string(config_json) : engine->impl.config_json, config);

        Slic3r::Model model = load_transformed_plate_model_for_planning(paths, transforms, extruder_ids, count);
        if (model.objects.size() != static_cast<size_t>(count)) {
            set_last_error(engine, "Native auto-orient currently requires one STL object per plate object.");
            return ORCA_ERROR_LOAD_MODEL;
        }

        size_t oriented_count = 0;
        for (size_t index = 0; index < model.objects.size(); ++index) {
            Slic3r::ModelObject* object = model.objects[index];
            if (object == nullptr || object->instances.empty()) {
                set_last_error(engine, "Native auto-orient could not find an object instance.");
                return ORCA_ERROR_LOAD_MODEL;
            }

            Slic3r::orientation::OrientMesh mesh;
            mesh.name = object->name;
            mesh.mesh = object->mesh();
            if (object->config.has("support_threshold_angle")) {
                mesh.overhang_angle = object->config.opt_int("support_threshold_angle");
            } else if (config.has("support_threshold_angle")) {
                mesh.overhang_angle = config.opt_int("support_threshold_angle");
            }
            Slic3r::ModelInstance* instance = object->instances.front();
            try {
                Slic3r::orientation::OrientMeshs orient_meshes;
                orient_meshes.emplace_back(std::move(mesh));
                Slic3r::orientation::OrientParams params;
                params.min_volume = true;
                params.parallel = false;
                params.progressind = {};
                params.stopcondition = {};
                Slic3r::orientation::OrientMeshs excludes;
                Slic3r::orientation::orient(orient_meshes, excludes, params);
                instance->rotate(orient_meshes.front().rotation_matrix);
                object->invalidate_bounding_box();
                object->ensure_on_bed();
                ++oriented_count;
            } catch (const std::exception& exception) {
                std::ostringstream message;
                message << "object=" << index << " skipped reason=" << exception.what();
                log_native_error("orca_plan_auto_orientation", message.str().c_str());
            } catch (...) {
                std::ostringstream message;
                message << "object=" << index << " skipped reason=unknown";
                log_native_error("orca_plan_auto_orientation", message.str().c_str());
            }

            const Slic3r::Vec3d offset = instance->get_offset();
            const Slic3r::Vec3d rotation = instance->get_rotation();
            const Slic3r::Vec3d scaling = instance->get_scaling_factor();
            const int out_offset = static_cast<int>(index) * 7;
            out_transforms[out_offset + 0] = offset.x();
            out_transforms[out_offset + 1] = offset.y();
            out_transforms[out_offset + 2] = offset.z();
            out_transforms[out_offset + 3] = rotation.x();
            out_transforms[out_offset + 4] = rotation.y();
            out_transforms[out_offset + 5] = rotation.z();
            out_transforms[out_offset + 6] = scaling.x();
        }
        {
            std::ostringstream message;
            message << "objects=" << model.objects.size() << " oriented=" << oriented_count;
            log_native_info("orca_plan_auto_orientation", message.str());
        }
        set_last_error(engine, "");
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_plan_auto_orientation", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown native auto-orient failure");
        log_native_error("orca_plan_auto_orientation", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" int orca_set_model_placement(OrcaEngine* engine, double x_mm, double y_mm, double z_mm)
{
    if (engine == nullptr || !std::isfinite(x_mm) || !std::isfinite(y_mm) || !std::isfinite(z_mm)) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);

    try {
        if (!engine->impl.model.has_value()) {
            set_last_error(engine, "no model loaded");
            return ORCA_ERROR_LOAD_MODEL;
        }
        Slic3r::Model& model = *engine->impl.model;
        if (model.objects.empty()) {
            set_last_error(engine, "no model loaded");
            return ORCA_ERROR_LOAD_MODEL;
        }

        const Slic3r::Vec3d placement(x_mm, y_mm, z_mm);
        for (Slic3r::ModelObject* object : model.objects) {
            if (object == nullptr) {
                continue;
            }
            if (object->instances.empty()) {
                object->add_instance();
            }
            for (Slic3r::ModelInstance* instance : object->instances) {
                if (instance != nullptr) {
                    instance->set_offset(placement);
                }
            }
            object->invalidate_bounding_box();
        }
        clear_generated_gcode(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_set_model_placement", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_set_model_placement", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" int orca_set_model_transform(
    OrcaEngine* engine,
    double x_mm,
    double y_mm,
    double z_mm,
    double rotation_x_radians,
    double rotation_y_radians,
    double rotation_z_radians,
    double uniform_scale
) {
    if (engine == nullptr ||
        !std::isfinite(x_mm) ||
        !std::isfinite(y_mm) ||
        !std::isfinite(z_mm) ||
        !std::isfinite(rotation_x_radians) ||
        !std::isfinite(rotation_y_radians) ||
        !std::isfinite(rotation_z_radians) ||
        !std::isfinite(uniform_scale) ||
        uniform_scale <= 0.0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);

    try {
        if (!engine->impl.model.has_value()) {
            set_last_error(engine, "no model loaded");
            return ORCA_ERROR_LOAD_MODEL;
        }
        Slic3r::Model& model = *engine->impl.model;
        if (model.objects.empty()) {
            set_last_error(engine, "no model loaded");
            return ORCA_ERROR_LOAD_MODEL;
        }

        const Slic3r::Vec3d offset(x_mm, y_mm, z_mm);
        const Slic3r::Vec3d rotation(rotation_x_radians, rotation_y_radians, rotation_z_radians);
        const Slic3r::Vec3d scaling(uniform_scale, uniform_scale, uniform_scale);
        for (Slic3r::ModelObject* object : model.objects) {
            if (object == nullptr) {
                continue;
            }
            if (object->instances.empty()) {
                object->add_instance();
            }
            for (Slic3r::ModelInstance* instance : object->instances) {
                if (instance != nullptr) {
                    instance->set_scaling_factor(scaling);
                    instance->set_rotation(rotation);
                    instance->set_offset(offset);
                }
            }
            object->invalidate_bounding_box();
        }
        clear_generated_gcode(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_set_model_transform", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_set_model_transform", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" int orca_set_config_json(OrcaEngine* engine, const char* json)
{
    if (engine == nullptr || json == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);

    try {
        engine->impl.config_json = json;
        invalidate_json_scalar_index();
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_set_config_json", exception.what());
        return ORCA_ERROR_CONFIG;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_set_config_json", "unknown exception");
        return ORCA_ERROR_CONFIG;
    }
}

extern "C" int orca_slice(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    configure_android_slice_runtime_once();
    if (!engine->impl.model.has_value()) {
        set_last_error(engine, "no model loaded");
        return ORCA_ERROR_SLICE;
    }
    clear_last_error(engine);
    clear_generated_gcode(engine);
    invalidate_json_scalar_index();

    try {
        const auto total_start = std::chrono::steady_clock::now();
        auto stage_start = total_start;
        const auto log_stage_elapsed = [&](const char* stage_name) {
            if (!kVerboseNativeTimingLogs) {
                stage_start = std::chrono::steady_clock::now();
                return;
            }
            const auto now = std::chrono::steady_clock::now();
            const auto stage_ms = std::chrono::duration_cast<std::chrono::milliseconds>(now - stage_start).count();
            const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(now - total_start).count();
            std::ostringstream message;
            message << stage_name << " stageMs=" << stage_ms << " totalMs=" << total_ms;
            log_native_info("orca_slice", message.str());
            stage_start = now;
        };

        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message << "begin objects=" << engine->impl.model->objects.size();
            log_native_info("orca_slice", message.str());
        }

        const auto config_full_start = std::chrono::steady_clock::now();
        Slic3r::DynamicPrintConfig config = Slic3r::DynamicPrintConfig::full_print_config();
        const long config_full_ms = elapsed_ms_since(config_full_start);
        const auto config_seed_start = std::chrono::steady_clock::now();
        config.set_deserialize_strict("gcode_comments", "1");
        config.set_deserialize_strict("start_gcode", "");
        // Keep the shipping wrapper aligned with the bounded reference parity probe:
        // FullPrintConfig::defaults() seeds a non-empty machine_start_gcode block,
        // which otherwise injects setup-only commands like G28 / G1 Z5 into MobileSlicer output.
        config.set_deserialize_strict("machine_start_gcode", "");
        // The same seeded config base carries a non-empty machine_end_gcode block.
        // Blank it here so the Android wrapper follows the parity probe's bounded
        // neutralized end-command baseline instead of appending preset finalization.
        config.set_deserialize_strict("machine_end_gcode", "");
        const long config_seed_ms = elapsed_ms_since(config_seed_start);
        const auto config_override_start = std::chrono::steady_clock::now();
        apply_json_overrides(engine->impl.config_json, config);
        const long config_override_ms = elapsed_ms_since(config_override_start);
        const auto config_runtime_start = std::chrono::steady_clock::now();
        apply_android_runtime_gcode_baseline(config);
        clamp_wipe_tower_to_printable_area(config);
        const long config_runtime_ms = elapsed_ms_since(config_runtime_start);
        const auto config_bounds_start = std::chrono::steady_clock::now();
        const PrintableVolumeBounds printable_bounds = extract_printable_volume_bounds(config);
        const long config_bounds_ms = elapsed_ms_since(config_bounds_start);
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "config_detail"
                << " fullMs=" << config_full_ms
                << " seedMs=" << config_seed_ms
                << " overridesMs=" << config_override_ms
                << " runtimeBaselineMs=" << config_runtime_ms
                << " boundsMs=" << config_bounds_ms
                << " jsonBytes=" << engine->impl.config_json.size();
            log_native_info("orca_slice", message.str());
        }
        log_stage_elapsed("config");

        Slic3r::Print print;
        Slic3r::Model& model = *engine->impl.model;

        const auto object_overrides_start = std::chrono::steady_clock::now();
        apply_model_object_overrides(engine->impl.config_json, model);
        const long object_overrides_ms = elapsed_ms_since(object_overrides_start);

        const auto instance_start = std::chrono::steady_clock::now();
        size_t added_instances = 0;
        for (Slic3r::ModelObject* object : model.objects) {
            if (object->instances.empty()) {
                object->add_instance();
                ++added_instances;
            }
        }
        const long instance_ms = elapsed_ms_since(instance_start);

        const auto bed_assign_start = std::chrono::steady_clock::now();
        for (Slic3r::ModelObject* object : model.objects) {
            object->ensure_on_bed();
            print.auto_assign_extruders(object);
        }
        relocate_wipe_tower_away_from_objects(config, model);
        const long bed_assign_ms = elapsed_ms_since(bed_assign_start);
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "prepare_model_detail"
                << " objectOverridesMs=" << object_overrides_ms
                << " instanceMs=" << instance_ms
                << " bedAssignMs=" << bed_assign_ms
                << " addedInstances=" << added_instances
                << " objects=" << model.objects.size();
            log_native_info("orca_slice", message.str());
        }
        log_stage_elapsed("prepare_model");

        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "orca_config_vectors_pre_apply"
                << " nozzle_diameter_size=" << config_option_size(config, "nozzle_diameter")
                << " nozzle_diameter=" << config_option_serialized(config, "nozzle_diameter")
                << " nozzle_volume_size=" << config_option_size(config, "nozzle_volume")
                << " nozzle_volume=" << config_option_serialized(config, "nozzle_volume")
                << " extruder_printable_area_size=" << config_option_size(config, "extruder_printable_area")
                << " extruder_printable_area=" << config_option_serialized(config, "extruder_printable_area")
                << " physical_extruder_map_size=" << config_option_size(config, "physical_extruder_map")
                << " physical_extruder_map=" << config_option_serialized(config, "physical_extruder_map")
                << " printer_extruder_id_size=" << config_option_size(config, "printer_extruder_id")
                << " printer_extruder_id=" << config_option_serialized(config, "printer_extruder_id")
                << " filament_map_mode=" << config_option_serialized(config, "filament_map_mode")
                << " filament_map_size=" << config_option_size(config, "filament_map")
                << " filament_map=" << config_option_serialized(config, "filament_map")
                << " flush_volumes_matrix_size=" << config_option_size(config, "flush_volumes_matrix")
                << " flush_volumes_matrix=" << config_option_serialized(config, "flush_volumes_matrix")
                << " semm=" << config_option_serialized(config, "single_extruder_multi_material")
                << " mobile_slots=" << config_option_serialized(config, "mobile_slicer_active_filament_slot_count")
                << " mobile_nozzles=" << config_option_serialized(config, "mobile_slicer_physical_nozzle_count");
            log_native_info("orca_slice", message.str());
        }
        const auto apply_start = std::chrono::steady_clock::now();
        print.apply(model, config);
        const long apply_ms = elapsed_ms_since(apply_start);
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "orca_config_vectors"
                << " filament_diameter_size=" << config_option_size(config, "filament_diameter")
                << " filament_diameter=" << config_option_serialized(config, "filament_diameter")
                << " filament_type_size=" << config_option_size(config, "filament_type")
                << " filament_type=" << config_option_serialized(config, "filament_type")
                << " filament_colour_size=" << config_option_size(config, "filament_colour")
                << " filament_colour=" << config_option_serialized(config, "filament_colour")
                << " filament_map_size=" << config_option_size(config, "filament_map")
                << " filament_map=" << config_option_serialized(config, "filament_map")
                << " nozzle_temperature_size=" << config_option_size(config, "nozzle_temperature")
                << " nozzle_temperature=" << config_option_serialized(config, "nozzle_temperature")
                << " semm=" << config_option_serialized(config, "single_extruder_multi_material")
                << " prime_tower=" << config_option_serialized(config, "enable_prime_tower");
            log_native_info("orca_slice", message.str());
        }
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message << "orca_model_extruders";
            for (size_t object_index = 0; object_index < model.objects.size(); ++object_index) {
                const Slic3r::ModelObject* object = model.objects[object_index];
                if (object == nullptr) {
                    continue;
                }
                message << " object" << object_index << "=" << object->config.extruder();
                message << " volumes=[";
                for (size_t volume_index = 0; volume_index < object->volumes.size(); ++volume_index) {
                    const Slic3r::ModelVolume* volume = object->volumes[volume_index];
                    if (volume_index > 0) {
                        message << ",";
                    }
                    message << (volume != nullptr ? volume->extruder_id() : 0);
                }
                message << "]";
            }
            log_native_info("orca_slice", message.str());
        }
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message << "orca_print_extruders all=[";
            const std::vector<unsigned int> extruders = print.object_extruders();
            for (size_t index = 0; index < extruders.size(); ++index) {
                if (index > 0) {
                    message << ",";
                }
                message << extruders[index];
            }
            message << "]";
            log_native_info("orca_slice", message.str());
        }
        if (kVerboseNativeTimingLogs) {
            log_print_region_extruders("orca_regions_after_apply", print);
        }
        const auto calibration_start = std::chrono::steady_clock::now();
        const auto calibration_params = extract_calibration_params(engine->impl.config_json);
        long set_calibration_ms = 0;
        if (calibration_params.mode != Slic3r::CalibMode::Calib_None) {
            const auto set_calibration_start = std::chrono::steady_clock::now();
            print.set_calib_params(calibration_params);
            set_calibration_ms = elapsed_ms_since(set_calibration_start);
        }
        const long calibration_ms = elapsed_ms_since(calibration_start);
        const auto validate_start = std::chrono::steady_clock::now();
        print.validate();
        const long validate_ms = elapsed_ms_since(validate_start);
        const auto status_start = std::chrono::steady_clock::now();
        print.set_status_silent();
        const long status_ms = elapsed_ms_since(status_start);
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "apply_validate_detail"
                << " applyMs=" << apply_ms
                << " calibrationExtractMs=" << calibration_ms
                << " calibrationSetMs=" << set_calibration_ms
                << " validateMs=" << validate_ms
                << " statusMs=" << status_ms;
            log_native_info("orca_slice", message.str());
        }
        log_stage_elapsed("apply_validate");

        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "process_input"
                << " layer_height=" << extract_number(engine->impl.config_json, "layer_height").value_or(-1.0)
                << " first_layer_height=" << extract_number(engine->impl.config_json, "first_layer_height").value_or(-1.0)
                << " nozzle_diameter=" << extract_number(engine->impl.config_json, "nozzle_diameter").value_or(-1.0)
                << " prime_tower=" << (extract_bool(engine->impl.config_json, "enable_prime_tower").value_or(false) ? 1 : 0)
                << " semm=" << (extract_bool(engine->impl.config_json, "single_extruder_multi_material").value_or(false) ? 1 : 0)
                << " support=" << (extract_bool(engine->impl.config_json, "enable_support").value_or(false) ? 1 : 0)
                << " objects=" << model.objects.size();
            if (const auto printer = extract_string(engine->impl.config_json, "printer_model")) {
                message << " printer_model=" << *printer;
            }
            if (const auto printer_settings = extract_string(engine->impl.config_json, "printer_settings_id")) {
                message << " printer_settings_id=" << *printer_settings;
            }
            if (const auto filament_settings = extract_string(engine->impl.config_json, "filament_settings_id")) {
                message << " filament_settings_id=" << *filament_settings;
            }
            if (const auto filament_ids = extract_string(engine->impl.config_json, "filament_ids")) {
                message << " filament_ids=" << *filament_ids;
            }
            if (const auto filament_vendor = extract_string(engine->impl.config_json, "filament_vendor")) {
                message << " filament_vendor=" << *filament_vendor;
            }
            if (const auto process = extract_string(engine->impl.config_json, "print_settings_id")) {
                message << " print_settings_id=" << *process;
            }
            log_native_info("orca_slice", message.str());
        }

        print.process();
        log_stage_elapsed("process");
        if (kVerboseNativeTimingLogs) {
            log_print_region_extruders("orca_regions_after_process", print);
        }
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message << "support_summary objects=" << print.objects().size();
            for (size_t index = 0; index < print.objects().size(); ++index) {
                const auto* object = print.objects()[index];
                if (object == nullptr) {
                    continue;
                }
                size_t support_layers = object->support_layers().size();
                message
                    << " [idx=" << index
                    << " enable_support=" << (object->config().enable_support.value ? 1 : 0)
                    << " support_type=" << static_cast<int>(object->config().support_type.value)
                    << " buildplate_only=" << (object->config().support_on_build_plate_only.value ? 1 : 0)
                    << " max_bridge_length=" << object->config().max_bridge_length.value
                    << " support_layers=" << support_layers
                    << "]";
            }
            log_native_info("orca_slice", message.str());
        }

        const auto temp_path_start = std::chrono::steady_clock::now();
        const auto gcode_path = make_temp_gcode_path();
        const long temp_path_ms = elapsed_ms_since(temp_path_start);
        Slic3r::GCodeProcessorResult gcode_result;
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message << "export_gcode attempt=1 tempPathMs=" << temp_path_ms;
            log_native_info("orca_slice", message.str());
        }
        const auto export_call_start = std::chrono::steady_clock::now();
        print.export_gcode(gcode_path.string(), &gcode_result, nullptr);
        const long export_call_ms = elapsed_ms_since(export_call_start);
        log_stage_elapsed("export_gcode");
        if ((gcode_result.gcode_check_result.error_code & ORCA_PLATE_PRINTABLE_AREA_ERROR) != 0 ||
            (gcode_result.gcode_check_result.error_code & ORCA_PLATE_PRINTABLE_HEIGHT_ERROR) != 0) {
            const auto violation = detect_printable_volume_violation(gcode_path, printable_bounds);
            if (!violation.any()) {
                log_native_info("orca_slice",
                    "printable volume warning ignored errorCode=" + std::to_string(gcode_result.gcode_check_result.error_code) +
                    " reason=no_print_extrusion_outside_bounds");
            } else {
            std::ostringstream message;
            message << "printable volume exceeded errorCode=" << gcode_result.gcode_check_result.error_code;
            if (printable_bounds.valid) {
                message
                    << " printableBounds=["
                    << printable_bounds.min_x << "," << printable_bounds.max_x
                    << "]x[" << printable_bounds.min_y << "," << printable_bounds.max_y
                    << "] z<=" << printable_bounds.max_z;
            } else {
                message << " printableBounds=invalid";
            }
            if (violation.has_extrusion) {
                message
                    << " emittedBounds=["
                    << violation.min_x << "," << violation.max_x
                    << "]x[" << violation.min_y << "," << violation.max_y
                    << "] z<=" << violation.max_z;
            } else {
                message << " emittedBounds=none";
            }
            message
                << " fallbackAreaExceeded=" << (violation.printable_area_exceeded ? 1 : 0)
                << " fallbackHeightExceeded=" << (violation.printable_height_exceeded ? 1 : 0);
            if (violation.offending_line != 0) {
                message
                    << " offendingLine=" << violation.offending_line
                    << " offendingGcode=\"" << violation.offending_gcode << "\"";
            }
            message << " failedGcode=" << gcode_path.string();
            set_last_error(engine, message.str());
            log_native_error("orca_slice", message.str().c_str());
            return ORCA_ERROR_SLICE;
            }
        }
        const auto file_stat_start = std::chrono::steady_clock::now();
        std::error_code ec;
        const uintmax_t gcode_size = std::filesystem::file_size(gcode_path, ec);
        const long file_stat_ms = elapsed_ms_since(file_stat_start);
        if (ec || gcode_size == 0) {
            std::filesystem::remove(gcode_path, ec);
            set_last_error(engine, "export completed but no G-code was produced");
            return ORCA_ERROR_SLICE;
        }
        engine->impl.gcode_path = gcode_path;
        engine->impl.gcode_path_owned = true;
        const auto summary_parse_start = std::chrono::steady_clock::now();
        engine->impl.gcode_summary = summarize_gcode_file_for_android(gcode_path);
        engine->impl.gcode_summary = enrich_gcode_summary_from_processor(engine->impl.gcode_summary, gcode_result, &print);
        engine->impl.gcode_summary_enriched = true;
#if defined(MOBILE_SLICER_ENABLE_VGCODE)
        engine->impl.cached_preview_input = libvgcode::GCodeInputData{};
        engine->impl.cached_preview_source_size = 0;
        engine->impl.cached_preview_valid = false;
        engine->impl.cached_preview_complete = false;
        const size_t preview_moves = gcode_result.moves.size();
        size_t processor_preview_vertices = 0;
        long processor_preview_build_ms = 0;
        if (gcode_result.moves.size() >= 2 &&
            gcode_result.moves.size() * static_cast<size_t>(2) <= static_cast<size_t>(kMaxCachedPreviewVertices)) {
            const auto processor_preview_start = std::chrono::steady_clock::now();
            bool processor_preview_limit_reached = false;
            engine->impl.cached_preview_input = to_vgcode_input_data_from_processor_result(
                gcode_result,
                kMaxCachedPreviewVertices,
                &processor_preview_limit_reached);
            apply_preview_palette_from_config_json(engine->impl.cached_preview_input, engine->impl.config_json);
            engine->impl.cached_preview_source_size = static_cast<size_t>(gcode_size);
            engine->impl.cached_preview_valid = !engine->impl.cached_preview_input.vertices.empty();
            engine->impl.cached_preview_complete = engine->impl.cached_preview_valid && !processor_preview_limit_reached;
            processor_preview_vertices = engine->impl.cached_preview_input.vertices.size();
            processor_preview_build_ms = elapsed_ms_since(processor_preview_start);
            if (kVerboseNativeTimingLogs) {
                log_native_info(
                    "gcode_processor_preview_cache",
                    "moves=" + std::to_string(gcode_result.moves.size()) +
                        " vertices=" + std::to_string(engine->impl.cached_preview_input.vertices.size()) +
                        " complete=" + std::string(engine->impl.cached_preview_complete ? "true" : "false") +
                        " buildMs=" + std::to_string(processor_preview_build_ms));
            }
        } else if (kVerboseNativeTimingLogs) {
            log_native_info(
                "gcode_processor_preview_cache",
                "skipped moves=" + std::to_string(gcode_result.moves.size()) +
                    " reason=too_large_for_exact_cache");
        }
        engine->impl.slice_metrics =
            "previewMoves=" + std::to_string(preview_moves) +
            "|previewCacheBuilt=" + std::string(engine->impl.cached_preview_valid ? "1" : "0") +
            "|previewCacheComplete=" + std::string(engine->impl.cached_preview_complete ? "1" : "0") +
            "|previewCachedVertices=" + std::to_string(processor_preview_vertices) +
            "|previewCacheBuildMs=" + std::to_string(processor_preview_build_ms);
#else
        engine->impl.slice_metrics =
            "previewMoves=0|previewCacheBuilt=0|previewCacheComplete=0|previewCachedVertices=0|previewCacheBuildMs=0";
#endif
        const long summary_parse_ms = elapsed_ms_since(summary_parse_start);
        const float normal_print_time = gcode_result.print_statistics
            .modes[static_cast<size_t>(Slic3r::PrintEstimatedStatistics::ETimeMode::Normal)]
            .time;
        long summary_replace_ms = 0;
        if (normal_print_time > 0.0f && is_reasonable_print_time_seconds(normal_print_time)) {
            const auto summary_replace_start = std::chrono::steady_clock::now();
            engine->impl.gcode_summary = replace_summary_field(
                engine->impl.gcode_summary,
                "time",
                format_duration_seconds(static_cast<long>(std::lround(normal_print_time))));
            summary_replace_ms = elapsed_ms_since(summary_replace_start);
            if (kVerboseNativeTimingLogs) {
                log_native_info("orca_slice",
                    "summary_time_source=gcode_processor seconds=" + std::to_string(static_cast<long>(std::lround(normal_print_time))));
            }
        } else if (normal_print_time > 0.0f) {
            if (kVerboseNativeTimingLogs) {
                log_native_info("orca_slice",
                    "summary_time_source=gcode_processor_rejected seconds=" + std::to_string(static_cast<long>(std::lround(normal_print_time))) +
                    " maxSeconds=" + std::to_string(kMaxReasonablePrintTimeSeconds));
            }
        } else {
            if (kVerboseNativeTimingLogs) {
                log_native_info("orca_slice",
                    "summary_time_source=gcode_footer processorSeconds=" + std::to_string(static_cast<long>(std::lround(normal_print_time))));
            }
        }
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "export_summary_detail"
                << " exportCallMs=" << export_call_ms
                << " fileStatMs=" << file_stat_ms
                << " summaryParseMs=" << summary_parse_ms
                << " summaryReplaceMs=" << summary_replace_ms
                << " bytes=" << gcode_size
                << " processorSeconds=" << static_cast<long>(std::lround(normal_print_time));
            log_native_info("orca_slice", message.str());
        }
        log_stage_elapsed("summarize_gcode");

        std::vector<Slic3r::GCodeProcessorResult::MoveVertex>().swap(gcode_result.moves);
        std::vector<size_t>().swap(gcode_result.lines_ends);

        return ORCA_SUCCESS;
    } catch (const Slic3r::SlicingErrors& errors) {
        for (const auto& error : errors.errors_) {
            set_last_error(engine, error.what());
            log_native_error("orca_slice", error.what());
        }
        return ORCA_ERROR_SLICE;
    } catch (const Slic3r::SlicingError& error) {
        set_last_error(engine, error.what());
        log_native_error("orca_slice", error.what());
        return ORCA_ERROR_SLICE;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_slice", exception.what());
        return ORCA_ERROR_SLICE;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_slice", "unknown exception");
        return ORCA_ERROR_SLICE;
    }
}

extern "C" const char* orca_get_gcode(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (!ensure_gcode_loaded_unlocked(engine)) {
        return nullptr;
    }
    thread_local std::string gcode_snapshot;
    gcode_snapshot = engine->impl.gcode;
    return gcode_snapshot.c_str();
}

extern "C" const char* orca_get_gcode_summary(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (engine != nullptr && engine->impl.gcode_summary.empty() && !engine->impl.gcode_path.empty()) {
        engine->impl.gcode_summary = summarize_gcode_file_for_android(engine->impl.gcode_path);
        engine->impl.gcode_summary_enriched = false;
    }
    if (engine->impl.gcode_summary.empty()) {
        return nullptr;
    }
    thread_local std::string summary_snapshot;
    summary_snapshot = engine->impl.gcode_summary;
    return summary_snapshot.c_str();
}

extern "C" const char* orca_get_enriched_gcode_summary(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (engine->impl.gcode_summary.empty() && !engine->impl.gcode_path.empty()) {
        engine->impl.gcode_summary = summarize_gcode_file_for_android(engine->impl.gcode_path);
        engine->impl.gcode_summary_enriched = false;
    }
    if (engine->impl.gcode_summary.empty()) {
        return nullptr;
    }
    if (!engine->impl.gcode_summary_enriched) {
        const auto enrich_start = std::chrono::steady_clock::now();
        if (!ensure_gcode_loaded_unlocked(engine)) {
            thread_local std::string summary_snapshot;
            summary_snapshot = engine->impl.gcode_summary;
            return summary_snapshot.c_str();
        }
        bool vertex_limit_reached = false;
        const libvgcode::GCodeInputData input = to_vgcode_input_data_from_gcode_text(
            engine->impl.gcode,
            -1,
            -1,
            kMaxCachedPreviewVertices,
            &vertex_limit_reached);
        engine->impl.gcode_summary = enrich_gcode_summary_from_preview_input(engine->impl.gcode_summary, input);
        engine->impl.gcode_summary_enriched = true;
        if (kVerboseNativeTimingLogs) {
            log_native_info(
                "gcode_summary_enrich",
                "summaryEnrichMs=" + std::to_string(elapsed_ms_since(enrich_start)));
        }
    }
    thread_local std::string summary_snapshot;
    summary_snapshot = engine->impl.gcode_summary;
    return summary_snapshot.c_str();
}

extern "C" const char* orca_get_slice_metrics(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (engine->impl.slice_metrics.empty()) {
        return nullptr;
    }
    thread_local std::string metrics_snapshot;
    metrics_snapshot = engine->impl.slice_metrics;
    return metrics_snapshot.c_str();
}

extern "C" int orca_write_gcode_to_file(OrcaEngine* engine, const char* path)
{
    if (engine == nullptr || path == nullptr || path[0] == '\0') {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (engine->impl.gcode_path.empty() && engine->impl.gcode.empty()) {
        set_last_error(engine, "no generated G-code is available");
        return ORCA_ERROR_SLICE;
    }

    try {
        if (!engine->impl.gcode_path.empty()) {
            const std::filesystem::path source_path = engine->impl.gcode_path;
            const bool source_owned = engine->impl.gcode_path_owned;
            if (source_owned) {
                std::error_code ec;
                std::filesystem::remove(path, ec);
                ec.clear();
                std::filesystem::rename(source_path, path, ec);
                if (ec) {
                    std::filesystem::copy_file(
                        source_path,
                        path,
                        std::filesystem::copy_options::overwrite_existing
                    );
                    std::error_code cleanup_ec;
                    std::filesystem::remove(source_path, cleanup_ec);
                }
            } else {
                std::filesystem::copy_file(
                    source_path,
                    path,
                    std::filesystem::copy_options::overwrite_existing
                );
            }
            engine->impl.gcode.clear();
            engine->impl.gcode_path = path;
            engine->impl.gcode_path_owned = false;
        } else {
            std::ofstream output(path, std::ios::binary | std::ios::trunc);
            if (!output) {
                set_last_error(engine, "unable to open G-code output path");
                return ORCA_ERROR_SLICE;
            }
            output.write(engine->impl.gcode.data(), static_cast<std::streamsize>(engine->impl.gcode.size()));
            if (!output) {
                set_last_error(engine, "unable to write G-code output file");
                return ORCA_ERROR_SLICE;
            }
            engine->impl.gcode_path = path;
            engine->impl.gcode_path_owned = false;
        }
        clear_last_error(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_write_gcode_to_file", exception.what());
        return ORCA_ERROR_SLICE;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_write_gcode_to_file", "unknown exception");
        return ORCA_ERROR_SLICE;
    }
}

extern "C" const char* orca_get_last_error(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (engine == nullptr || engine->impl.last_error.empty()) {
        return nullptr;
    }
    thread_local std::string error_snapshot;
    error_snapshot = engine->impl.last_error;
    return error_snapshot.c_str();
}

#if defined(MOBILE_SLICER_ENABLE_VGCODE)
static bool ensure_gcode_preview_cache(OrcaEngine* engine, std::string& error, PreviewCacheStatus* status = nullptr)
{
    error.clear();
    if (engine == nullptr) {
        error = "no native engine is available";
        return false;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (!ensure_gcode_loaded_unlocked(engine)) {
        error = "no generated G-code is available";
        return false;
    }
    const size_t source_size = engine->impl.gcode.size();
    if (status != nullptr) {
        status->source_size = source_size;
    }
    if (engine->impl.cached_preview_valid &&
        engine->impl.cached_preview_source_size == source_size) {
        if (status != nullptr) {
            status->cache_hit = true;
            status->cache_valid = engine->impl.cached_preview_valid;
            status->cache_complete = engine->impl.cached_preview_complete;
            status->cached_vertices = engine->impl.cached_preview_input.vertices.size();
            status->cached_layers = gcode_input_layer_count(engine->impl.cached_preview_input);
        }
        return engine->impl.cached_preview_complete;
    }

    const auto parse_start = std::chrono::steady_clock::now();
    bool vertex_limit_reached = false;
    engine->impl.cached_preview_input = to_vgcode_input_data_from_gcode_text(
        engine->impl.gcode,
        -1,
        -1,
        kMaxCachedPreviewVertices,
        &vertex_limit_reached);
    apply_preview_palette_from_config_json(engine->impl.cached_preview_input, engine->impl.config_json);
    engine->impl.cached_preview_source_size = source_size;
    engine->impl.cached_preview_valid = !engine->impl.cached_preview_input.vertices.empty();
    engine->impl.cached_preview_complete = engine->impl.cached_preview_valid && !vertex_limit_reached;

    const auto parse_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - parse_start).count();
    if (status != nullptr) {
        status->cache_built = true;
        status->cache_valid = engine->impl.cached_preview_valid;
        status->cache_complete = engine->impl.cached_preview_complete;
        status->vertex_limit_reached = vertex_limit_reached;
        status->cached_vertices = engine->impl.cached_preview_input.vertices.size();
        status->cached_layers = gcode_input_layer_count(engine->impl.cached_preview_input);
        status->parse_ms = parse_ms;
    }
    if (kVerboseNativeTimingLogs) {
        log_native_info("gcode_preview_cache",
            "vertices=" + std::to_string(engine->impl.cached_preview_input.vertices.size()) +
            " layers=" + std::to_string(gcode_input_layer_count(engine->impl.cached_preview_input)) +
            " sourceBytes=" + std::to_string(source_size) +
            " complete=" + std::string(engine->impl.cached_preview_complete ? "true" : "false") +
            " vertexLimitReached=" + std::string(vertex_limit_reached ? "true" : "false") +
            " parseMs=" + std::to_string(parse_ms));
    }

    if (!engine->impl.cached_preview_valid) {
        error = "Sliced G-code did not contain renderable extrusion toolpath vertices.";
        return false;
    }
    return engine->impl.cached_preview_complete;
}

extern "C" OrcaGcodeViewer* orca_gcode_viewer_create(void)
{
    try {
        return new OrcaGcodeViewer();
    } catch (...) {
        return nullptr;
    }
}

extern "C" void orca_gcode_viewer_destroy(OrcaGcodeViewer* viewer)
{
    if (viewer == nullptr) {
        return;
    }
    if (viewer->initialized) {
        viewer->viewer.shutdown();
        viewer->initialized = false;
    }
    delete viewer;
}

extern "C" int orca_gcode_viewer_init(OrcaGcodeViewer* viewer)
{
    if (viewer == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    if (viewer->initialized) {
        return ORCA_SUCCESS;
    }
    const GLubyte* version = glGetString(GL_VERSION);
    if (version == nullptr) {
        viewer->last_error = "OpenGL version is unavailable.";
        return ORCA_ERROR_SLICE;
    }
    try {
        viewer->viewer.init(reinterpret_cast<const char*>(version));
        viewer->initialized = true;
        viewer->last_error.clear();
        return ORCA_SUCCESS;
    } catch (const std::exception& e) {
        viewer->last_error = e.what();
        return ORCA_ERROR_SLICE;
    } catch (...) {
        viewer->last_error = "libvgcode initialization failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" int orca_gcode_viewer_shutdown(OrcaGcodeViewer* viewer)
{
    if (viewer == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    if (!viewer->initialized) {
        return ORCA_SUCCESS;
    }
    try {
        viewer->viewer.shutdown();
        viewer->initialized = false;
        viewer->last_error.clear();
        return ORCA_SUCCESS;
    } catch (...) {
        viewer->last_error = "libvgcode shutdown failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" int orca_gcode_viewer_load_gcode(OrcaGcodeViewer* viewer, const char* gcode)
{
    if (viewer == nullptr || gcode == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    if (!viewer->initialized) {
        const int init_result = orca_gcode_viewer_init(viewer);
        if (init_result != ORCA_SUCCESS) {
            return init_result;
        }
    }
    try {
        viewer->input_data = to_vgcode_input_data_from_gcode_text(gcode, -1, -1);
        if (viewer->input_data.vertices.empty()) {
            viewer->last_error = "Sliced G-code did not contain renderable extrusion toolpath vertices.";
            return ORCA_ERROR_SLICE;
        }
        if (viewer->input_data.vertices.size() >= kMaxPreviewVertices) {
            viewer->last_error = "Sliced G-code preview is too large for the current phone preview limit. Vertex limit: " +
                std::to_string(kMaxPreviewVertices) + ".";
            return ORCA_ERROR_SLICE;
        }
        log_native_info("gcode_viewer_load_text", "vertices=" + std::to_string(viewer->input_data.vertices.size()) +
            " fidelity=exact");
        viewer->viewer.load(std::move(viewer->input_data));
        viewer->viewer.set_view_type(preview_view_type_for_loaded_viewer(viewer->viewer));
        viewer->viewer.set_time_mode(libvgcode::ETimeMode::Normal);
        viewer->last_error.clear();
        return ORCA_SUCCESS;
    } catch (const std::exception& e) {
        viewer->last_error = e.what();
        return ORCA_ERROR_SLICE;
    } catch (...) {
        viewer->last_error = "libvgcode preview text load failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" int orca_gcode_viewer_load_latest_slice(OrcaGcodeViewer* viewer, OrcaEngine* engine, long min_layer, long max_layer, int lod_hint)
{
    if (viewer == nullptr || engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> engine_lock(engine->impl.mutex);
    if (!ensure_gcode_loaded_unlocked(engine)) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    if (!viewer->initialized) {
        const int init_result = orca_gcode_viewer_init(viewer);
        if (init_result != ORCA_SUCCESS) {
            return init_result;
        }
    }
    try {
        const size_t vertex_budget = lod_hint > 0 ?
            std::min(static_cast<size_t>(lod_hint), kMaxPreviewVertices) :
            kMaxPreviewVertices;
        std::string cache_error;
        PreviewCacheStatus cache_status;
        const auto total_start = std::chrono::steady_clock::now();
        const bool cache_available = ensure_gcode_preview_cache(engine, cache_error, &cache_status);
        size_t preview_vertices = 0;
        bool loaded_directly_from_cache = false;
        long selected_parse_ms = 0;
        long libvgcode_load_ms = 0;
        if (cache_available) {
            const auto range_load_start = std::chrono::steady_clock::now();
            preview_vertices = viewer->viewer.load_layer_range(
                engine->impl.cached_preview_input,
                min_layer,
                max_layer,
                vertex_budget);
            libvgcode_load_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - range_load_start).count();
            loaded_directly_from_cache = preview_vertices > 0 && preview_vertices < vertex_budget;
        } else {
            const auto selected_parse_start = std::chrono::steady_clock::now();
            viewer->input_data = to_vgcode_input_data_from_gcode_text(
                engine->impl.gcode,
                min_layer,
                max_layer,
                vertex_budget);
            apply_preview_palette_from_config_json(viewer->input_data, engine->impl.config_json);
            selected_parse_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - selected_parse_start).count();
            preview_vertices = viewer->input_data.vertices.size();
        }
        if (preview_vertices == 0) {
            viewer->last_error = cache_error.empty() ?
                "Selected G-code preview layers did not contain renderable extrusion toolpath vertices." :
                cache_error;
            return ORCA_ERROR_SLICE;
        }
        log_native_info("gcode_viewer_load_latest_slice",
            "range=" + std::to_string(min_layer) + "-" + std::to_string(max_layer) +
            " vertices=" + std::to_string(preview_vertices) +
            " budget=" + std::to_string(vertex_budget) +
            " cache=" + std::string(cache_available ? (loaded_directly_from_cache ? "range" : "fallback") : "miss") +
            " cachedVertices=" + std::to_string(cache_status.cached_vertices) +
            " cachedLayers=" + std::to_string(cache_status.cached_layers));
        if (preview_vertices >= vertex_budget) {
            viewer->last_error = "Selected G-code preview is too large for exact phone preview. Vertex limit: " +
                std::to_string(vertex_budget) + ". Narrow the layer range to keep the preview accurate.";
            return ORCA_ERROR_SLICE;
        }
        if (!loaded_directly_from_cache) {
            const auto fallback_load_start = std::chrono::steady_clock::now();
            viewer->viewer.load(std::move(viewer->input_data));
            libvgcode_load_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - fallback_load_start).count();
        }
        const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - total_start).count();
        if (kVerboseNativeTimingLogs) {
            log_native_info("gcode_viewer_load_latest_slice", "vertices=" + std::to_string(preview_vertices) +
                " requestedMinLayer=" + std::to_string(min_layer) +
                " requestedMaxLayer=" + std::to_string(max_layer) +
                " loadedMinLayer=" + std::to_string(min_layer) +
                " loadedMaxLayer=" + std::to_string(max_layer) +
                " cache=" + std::string(cache_available ? (loaded_directly_from_cache ? "range" : "fallback") : "miss") +
                " cacheValid=" + std::string(cache_status.cache_valid ? "true" : "false") +
                " cacheComplete=" + std::string(cache_status.cache_complete ? "true" : "false") +
                " cacheBuilt=" + std::string(cache_status.cache_built ? "true" : "false") +
                " cachedVertices=" + std::to_string(cache_status.cached_vertices) +
                " cachedLayers=" + std::to_string(cache_status.cached_layers) +
                " selectedParseMs=" + std::to_string(selected_parse_ms) +
                " libvgcodeLoadMs=" + std::to_string(libvgcode_load_ms) +
                " totalMs=" + std::to_string(total_ms) +
                " fidelity=exact");
        }
        viewer->viewer.set_view_type(preview_view_type_for_loaded_viewer(viewer->viewer));
        viewer->viewer.set_time_mode(libvgcode::ETimeMode::Normal);
        viewer->last_error.clear();
        return ORCA_SUCCESS;
    } catch (const std::exception& e) {
        viewer->last_error = e.what();
        return ORCA_ERROR_SLICE;
    } catch (...) {
        viewer->last_error = "libvgcode preview slice-range load failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" const char* orca_gcode_preview_suggest_layer_ranges(OrcaEngine* engine, long min_layer, long max_layer, long vertex_budget)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (!ensure_gcode_loaded_unlocked(engine)) {
        set_last_error(engine, "no generated G-code is available for preview range planning");
        return nullptr;
    }
    const size_t budget = static_cast<size_t>(
        std::max<long>(1L, std::min<long>(vertex_budget, static_cast<long>(kMaxPreviewVertices - 1))));
    try {
        std::string error;
        const auto plan_start = std::chrono::steady_clock::now();
        const bool cached_counts_available =
            engine->impl.cached_preview_valid &&
            engine->impl.cached_preview_complete &&
            engine->impl.cached_preview_source_size == engine->impl.gcode.size();
        const std::vector<size_t> layer_counts = cached_counts_available ?
            count_preview_vertices_by_layer_from_input_data(engine->impl.cached_preview_input) :
            count_preview_vertices_by_layer_from_gcode_text(engine->impl.gcode);
        engine->impl.preview_range_plan = pack_preview_layer_ranges_from_counts(
            layer_counts,
            std::max<long>(0L, min_layer),
            std::max<long>(min_layer, max_layer),
            budget,
            error);
        if (engine->impl.preview_range_plan.empty()) {
            set_last_error(engine, error.empty() ? "unable to plan exact G-code preview ranges" : error);
            return nullptr;
        }
        if (kVerboseNativeTimingLogs) {
            log_native_info(
                "gcode_preview_range_plan",
                "ranges=" + engine->impl.preview_range_plan +
                    " layers=" + std::to_string(layer_counts.size()) +
                    " budget=" + std::to_string(budget) +
                    " counts=" + std::string(cached_counts_available ? "cache" : "text") +
                    " planMs=" + std::to_string(elapsed_ms_since(plan_start)));
        }
        thread_local std::string preview_range_plan_snapshot;
        preview_range_plan_snapshot = engine->impl.preview_range_plan;
        return preview_range_plan_snapshot.c_str();
    } catch (const std::exception& e) {
        set_last_error(engine, e.what());
        return nullptr;
    } catch (...) {
        set_last_error(engine, "G-code preview range planning failed");
        return nullptr;
    }
}

extern "C" int orca_gcode_viewer_render(OrcaGcodeViewer* viewer, const float* view_matrix, const float* projection_matrix)
{
    if (viewer == nullptr || view_matrix == nullptr || projection_matrix == nullptr || !viewer->initialized) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    try {
        libvgcode::Mat4x4 view;
        libvgcode::Mat4x4 projection;
        std::copy(view_matrix, view_matrix + view.size(), view.begin());
        std::copy(projection_matrix, projection_matrix + projection.size(), projection.begin());
        viewer->viewer.render(view, projection);
        return ORCA_SUCCESS;
    } catch (const std::exception& e) {
        viewer->last_error = e.what();
        return ORCA_ERROR_SLICE;
    } catch (...) {
        viewer->last_error = "libvgcode render failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" long orca_gcode_viewer_get_layers_count(OrcaGcodeViewer* viewer)
{
    if (viewer == nullptr) {
        return 0;
    }
    return static_cast<long>(viewer->viewer.get_layers_count());
}

extern "C" int orca_gcode_viewer_set_layers_view_range(OrcaGcodeViewer* viewer, long min_layer, long max_layer)
{
    if (viewer == nullptr || min_layer < 0 || max_layer < 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    viewer->viewer.set_layers_view_range(static_cast<size_t>(min_layer), static_cast<size_t>(max_layer));
    return ORCA_SUCCESS;
}

extern "C" int orca_gcode_viewer_set_extrusion_width_scale(OrcaGcodeViewer* viewer, float scale)
{
    if (viewer == nullptr || !std::isfinite(scale) || scale <= 0.0f) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    viewer->viewer.set_extrusion_width_scale(scale);
    return ORCA_SUCCESS;
}

extern "C" int orca_gcode_viewer_set_path_visibility(OrcaGcodeViewer* viewer, int kind, int id, int visible)
{
    if (viewer == nullptr || !viewer->initialized) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    try {
        const bool target_visible = visible != 0;
        if (kind == 0) {
            if (id < 0 || id >= static_cast<int>(libvgcode::EGCodeExtrusionRole::COUNT)) {
                return ORCA_ERROR_INVALID_ARGUMENT;
            }
            const auto role = static_cast<libvgcode::EGCodeExtrusionRole>(id);
            if (viewer->viewer.is_extrusion_role_visible(role) != target_visible) {
                viewer->viewer.toggle_extrusion_role_visibility(role);
            }
        } else if (kind == 1) {
            if (id < 0 || id >= static_cast<int>(libvgcode::EOptionType::COUNT)) {
                return ORCA_ERROR_INVALID_ARGUMENT;
            }
            const auto option = static_cast<libvgcode::EOptionType>(id);
            if (viewer->viewer.is_option_visible(option) != target_visible) {
                viewer->viewer.toggle_option_visibility(option);
            }
        } else {
            return ORCA_ERROR_INVALID_ARGUMENT;
        }
        viewer->last_error.clear();
        return ORCA_SUCCESS;
    } catch (const std::exception& e) {
        viewer->last_error = e.what();
        return ORCA_ERROR_SLICE;
    } catch (...) {
        viewer->last_error = "libvgcode path visibility update failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" int orca_gcode_viewer_set_view_type(OrcaGcodeViewer* viewer, int view_type)
{
    if (viewer == nullptr || !viewer->initialized) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    try {
        const auto mapped_view_type = preview_view_type_from_mobile_id(view_type, viewer->viewer);
        if (!mapped_view_type) {
            return ORCA_ERROR_INVALID_ARGUMENT;
        }
        viewer->viewer.set_view_type(*mapped_view_type);
        viewer->last_error.clear();
        return ORCA_SUCCESS;
    } catch (const std::exception& e) {
        viewer->last_error = e.what();
        return ORCA_ERROR_SLICE;
    } catch (...) {
        viewer->last_error = "libvgcode view type update failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" const char* orca_gcode_viewer_get_last_error(OrcaGcodeViewer* viewer)
{
    if (viewer == nullptr || viewer->last_error.empty()) {
        return nullptr;
    }
    return viewer->last_error.c_str();
}
#else
extern "C" OrcaGcodeViewer* orca_gcode_viewer_create(void) { return nullptr; }
extern "C" void orca_gcode_viewer_destroy(OrcaGcodeViewer*) {}
extern "C" int orca_gcode_viewer_init(OrcaGcodeViewer*) { return ORCA_ERROR_SLICE; }
extern "C" int orca_gcode_viewer_shutdown(OrcaGcodeViewer*) { return ORCA_ERROR_SLICE; }
extern "C" int orca_gcode_viewer_load_gcode(OrcaGcodeViewer*, const char*) { return ORCA_ERROR_SLICE; }
extern "C" int orca_gcode_viewer_load_latest_slice(OrcaGcodeViewer*, OrcaEngine*, long, long, int) { return ORCA_ERROR_SLICE; }
extern "C" int orca_gcode_viewer_render(OrcaGcodeViewer*, const float*, const float*) { return ORCA_ERROR_SLICE; }
extern "C" long orca_gcode_viewer_get_layers_count(OrcaGcodeViewer*) { return 0; }
extern "C" int orca_gcode_viewer_set_layers_view_range(OrcaGcodeViewer*, long, long) { return ORCA_ERROR_SLICE; }
extern "C" int orca_gcode_viewer_set_extrusion_width_scale(OrcaGcodeViewer*, float) { return ORCA_ERROR_SLICE; }
extern "C" int orca_gcode_viewer_set_path_visibility(OrcaGcodeViewer*, int, int, int) { return ORCA_ERROR_SLICE; }
extern "C" int orca_gcode_viewer_set_view_type(OrcaGcodeViewer*, int) { return ORCA_ERROR_SLICE; }
extern "C" const char* orca_gcode_viewer_get_last_error(OrcaGcodeViewer*) { return nullptr; }
#endif

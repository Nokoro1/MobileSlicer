#include "orca_wrapper_model_overrides.h"

#include "orca_wrapper_internal.h"
#include "orca_wrapper_utils.h"

#include "libslic3r/Config.hpp"
#include "libslic3r/Format/STL.hpp"
#include "libslic3r/Geometry.hpp"
#include "libslic3r/Model.hpp"

#include <cctype>
#include <cmath>
#include <exception>
#include <filesystem>
#include <optional>
#include <sstream>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace mobileslicer::orca_wrapper {

static std::vector<std::string> json_array_objects(std::string_view raw)
{
    std::vector<std::string> objects;
    raw = trim_view(raw);
    if (raw.size() < 2 || raw.front() != '[' || raw.back() != ']') {
        return objects;
    }
    size_t cursor = 1;
    const size_t end = raw.size() - 1;
    while (cursor < end) {
        while (cursor < end && (std::isspace(static_cast<unsigned char>(raw[cursor])) != 0 || raw[cursor] == ',')) {
            ++cursor;
        }
        if (cursor >= end || raw[cursor] != '{') {
            break;
        }
        const size_t begin = cursor;
        int depth = 0;
        bool in_string = false;
        bool escaped = false;
        for (; cursor < end; ++cursor) {
            const char ch = raw[cursor];
            if (in_string) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    in_string = false;
                }
                continue;
            }
            if (ch == '"') {
                in_string = true;
            } else if (ch == '{') {
                ++depth;
            } else if (ch == '}') {
                --depth;
                if (depth == 0) {
                    ++cursor;
                    objects.emplace_back(raw.substr(begin, cursor - begin));
                    break;
                }
            }
        }
    }
    return objects;
}

static bool skip_json_string_view(std::string_view raw, size_t& cursor)
{
    if (cursor >= raw.size() || raw[cursor] != '"') {
        return false;
    }
    ++cursor;
    bool escaped = false;
    for (; cursor < raw.size(); ++cursor) {
        const char ch = raw[cursor];
        if (escaped) {
            escaped = false;
            continue;
        }
        if (ch == '\\') {
            escaped = true;
            continue;
        }
        if (ch == '"') {
            ++cursor;
            return true;
        }
    }
    return false;
}

static bool skip_json_value_view(std::string_view raw, size_t& cursor)
{
    while (cursor < raw.size() && std::isspace(static_cast<unsigned char>(raw[cursor])) != 0) {
        ++cursor;
    }
    if (cursor >= raw.size()) {
        return false;
    }
    if (raw[cursor] == '"') {
        return skip_json_string_view(raw, cursor);
    }
    if (raw[cursor] == '{' || raw[cursor] == '[') {
        std::vector<char> closing_stack;
        bool in_string = false;
        bool escaped = false;
        for (; cursor < raw.size(); ++cursor) {
            const char ch = raw[cursor];
            if (in_string) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    in_string = false;
                }
                continue;
            }
            if (ch == '"') {
                in_string = true;
            } else if (ch == '{') {
                closing_stack.push_back('}');
            } else if (ch == '[') {
                closing_stack.push_back(']');
            } else if ((ch == '}' || ch == ']') && !closing_stack.empty() && closing_stack.back() == ch) {
                closing_stack.pop_back();
                if (closing_stack.empty()) {
                    ++cursor;
                    return true;
                }
            }
        }
        return false;
    }
    while (cursor < raw.size() && raw[cursor] != ',' && raw[cursor] != '}') {
        ++cursor;
    }
    return true;
}

static std::vector<std::pair<std::string, std::string_view>> json_object_members(std::string_view raw)
{
    std::vector<std::pair<std::string, std::string_view>> members;
    raw = trim_view(raw);
    if (raw.size() < 2 || raw.front() != '{' || raw.back() != '}') {
        return members;
    }
    size_t cursor = 1;
    const size_t end = raw.size() - 1;
    while (cursor < end) {
        while (cursor < end && (std::isspace(static_cast<unsigned char>(raw[cursor])) != 0 || raw[cursor] == ',')) {
            ++cursor;
        }
        if (cursor >= end || raw[cursor] != '"') {
            break;
        }
        const size_t key_begin = cursor + 1;
        if (!skip_json_string_view(raw, cursor)) {
            break;
        }
        const size_t key_end = cursor - 1;
        while (cursor < end && std::isspace(static_cast<unsigned char>(raw[cursor])) != 0) {
            ++cursor;
        }
        if (cursor >= end || raw[cursor] != ':') {
            break;
        }
        ++cursor;
        while (cursor < end && std::isspace(static_cast<unsigned char>(raw[cursor])) != 0) {
            ++cursor;
        }
        const size_t value_begin = cursor;
        if (!skip_json_value_view(raw, cursor)) {
            break;
        }
        const size_t value_end = cursor;
        members.emplace_back(
            unescape_json_string(raw.substr(key_begin, key_end - key_begin)),
            raw.substr(value_begin, value_end - value_begin)
        );
    }
    return members;
}

static std::optional<std::string_view> json_object_member_value(std::string_view raw, const std::string& key)
{
    for (const auto& [member_key, raw_value] : json_object_members(raw)) {
        if (member_key == key) {
            return raw_value;
        }
    }
    return std::nullopt;
}

static std::optional<std::vector<double>> json_number_array(std::string_view raw)
{
    raw = trim_view(raw);
    if (raw.size() < 2 || raw.front() != '[' || raw.back() != ']') {
        return std::nullopt;
    }
    std::vector<double> values;
    size_t cursor = 1;
    const size_t end = raw.size() - 1;
    while (cursor < end) {
        while (cursor < end && (std::isspace(static_cast<unsigned char>(raw[cursor])) != 0 || raw[cursor] == ',')) {
            ++cursor;
        }
        if (cursor >= end) {
            break;
        }
        const size_t begin = cursor;
        while (cursor < end && raw[cursor] != ',' && raw[cursor] != ']') {
            ++cursor;
        }
        const std::string token = trim_copy(raw.substr(begin, cursor - begin));
        if (token.empty()) {
            continue;
        }
        try {
            size_t parsed = 0;
            const double value = std::stod(token, &parsed);
            if (parsed != token.size() || !std::isfinite(value)) {
                return std::nullopt;
            }
            values.push_back(value);
        } catch (...) {
            return std::nullopt;
        }
    }
    return values;
}

static std::optional<std::string> config_value_from_json(std::string_view raw)
{
    raw = trim_view(raw);
    if (raw.empty() || raw == "null") {
        return std::nullopt;
    }
    if (raw == "true") {
        return std::string("1");
    }
    if (raw == "false") {
        return std::string("0");
    }
    if (raw.size() >= 2 && raw.front() == '"' && raw.back() == '"') {
        return unescape_json_string(raw.substr(1, raw.size() - 2));
    }
    return trim_copy(raw);
}

static std::optional<Slic3r::Transform3d> modifier_transform_from_json(std::string_view raw)
{
    raw = trim_view(raw);
    if (raw.empty()) {
        return std::nullopt;
    }
    const auto x = extract_number(std::string(raw), "xMm");
    const auto y = extract_number(std::string(raw), "yMm");
    const auto z = extract_number(std::string(raw), "zMm");
    const auto scale = extract_number(std::string(raw), "uniformScale");
    if (!x || !y || !z || !scale || !std::isfinite(*scale) || *scale <= 0.0) {
        return std::nullopt;
    }

    const Slic3r::Vec3d translation(*x, *y, *z);
    const Slic3r::Vec3d scaling(*scale, *scale, *scale);
    if (const auto raw_matrix = json_object_member_value(raw, "orientationMatrix")) {
        const auto matrix_values = json_number_array(*raw_matrix);
        if (matrix_values && matrix_values->size() == 9) {
            Slic3r::Transform3d orientation = Slic3r::Transform3d::Identity();
            for (int row = 0; row < 3; ++row) {
                for (int column = 0; column < 3; ++column) {
                    orientation.matrix()(row, column) = (*matrix_values)[static_cast<size_t>(row * 3 + column)];
                }
            }
            return Slic3r::Geometry::translation_transform(translation) *
                orientation *
                Slic3r::Geometry::scale_transform(scaling);
        }
    }

    const double rotation_x = extract_number(std::string(raw), "rotationXRadians").value_or(0.0);
    const double rotation_y = extract_number(std::string(raw), "rotationYRadians").value_or(0.0);
    const double rotation_z = extract_number(std::string(raw), "rotationZRadians").value_or(0.0);
    if (!std::isfinite(rotation_x) || !std::isfinite(rotation_y) || !std::isfinite(rotation_z)) {
        return std::nullopt;
    }
    return Slic3r::Geometry::assemble_transform(
        translation,
        Slic3r::Vec3d(rotation_x, rotation_y, rotation_z),
        scaling
    );
}

static bool is_non_object_profile_metadata_key(const std::string& key)
{
    return key == "name" ||
        key == "inherits" ||
        key == "from" ||
        key == "type" ||
        key == "version" ||
        key == "setting_id" ||
        key == "print_settings_id" ||
        key == "printer_settings_id" ||
        key == "filament_settings_id" ||
        key == "compatible_printers" ||
        key == "compatible_printers_condition" ||
        key == "compatible_prints" ||
        key == "compatible_prints_condition";
}

static void apply_process_json_to_object(
    const std::string& json,
    Slic3r::ModelObject& object,
    Slic3r::ConfigSubstitutionContext& ctxt)
{
    if (const auto seam_position = extract_string(json, "seam_position")) {
        object.config.set_deserialize("seam_position", *seam_position, ctxt);
    }
    if (const auto value = extract_bool(json, "enable_support")) {
        object.config.set_deserialize("enable_support", *value ? "1" : "0", ctxt);
    }
    if (const auto value = extract_string(json, "support_type")) {
        object.config.set_deserialize("support_type", *value, ctxt);
    }
    if (const auto value = extract_string(json, "support_style")) {
        object.config.set_deserialize("support_style", *value, ctxt);
    }
    if (const auto value = extract_number(json, "support_threshold_angle")) {
        object.config.set_deserialize("support_threshold_angle", std::to_string(static_cast<int>(*value)), ctxt);
    }
    if (const auto value = extract_number(json, "raft_layers")) {
        object.config.set_deserialize("raft_layers", std::to_string(static_cast<int>(*value)), ctxt);
    }
    if (const auto value = extract_bool(json, "support_on_build_plate_only")) {
        object.config.set_deserialize("support_on_build_plate_only", *value ? "1" : "0", ctxt);
    } else if (const auto legacy_value = extract_bool(json, "support_buildplate_only")) {
        object.config.set_deserialize("support_on_build_plate_only", *legacy_value ? "1" : "0", ctxt);
    }
    if (const auto value = extract_number(json, "support_top_z_distance")) {
        object.config.set_deserialize("support_top_z_distance", std::to_string(*value), ctxt);
    }
    if (const auto value = extract_number(json, "support_bottom_z_distance")) {
        object.config.set_deserialize("support_bottom_z_distance", std::to_string(*value), ctxt);
    }
    if (const auto value = extract_number(json, "support_interface_top_layers")) {
        object.config.set_deserialize("support_interface_top_layers", std::to_string(static_cast<int>(*value)), ctxt);
    }
    if (const auto value = extract_number(json, "support_interface_bottom_layers")) {
        object.config.set_deserialize("support_interface_bottom_layers", std::to_string(static_cast<int>(*value)), ctxt);
    }
    if (const auto value = extract_number(json, "support_interface_spacing")) {
        object.config.set_deserialize("support_interface_spacing", std::to_string(*value), ctxt);
    }
    if (const auto value = extract_number(json, "support_bottom_interface_spacing")) {
        object.config.set_deserialize("support_bottom_interface_spacing", std::to_string(*value), ctxt);
    }
    if (const auto value = extract_string(json, "support_interface_pattern")) {
        object.config.set_deserialize("support_interface_pattern", *value, ctxt);
    }
    if (const auto value = extract_bool(json, "support_interface_loop_pattern")) {
        object.config.set_deserialize("support_interface_loop_pattern", *value ? "1" : "0", ctxt);
    }
    if (const auto value = extract_string(json, "support_line_width")) {
        object.config.set_deserialize("support_line_width", *value, ctxt);
    }
    if (const auto value = extract_string(json, "support_base_pattern")) {
        object.config.set_deserialize("support_base_pattern", *value, ctxt);
    }
    if (const auto value = extract_number(json, "support_base_pattern_spacing")) {
        object.config.set_deserialize("support_base_pattern_spacing", std::to_string(*value), ctxt);
    }
    if (const auto value = extract_number(json, "support_speed")) {
        object.config.set_deserialize("support_speed", std::to_string(*value), ctxt);
    }
    if (const auto value = extract_bool(json, "support_ironing")) {
        object.config.set_deserialize("support_ironing", *value ? "1" : "0", ctxt);
    }
    if (const auto value = extract_number(json, "support_ironing_flow")) {
        object.config.set_deserialize("support_ironing_flow", std::to_string(*value) + "%", ctxt);
    }
    if (const auto value = extract_number(json, "support_ironing_spacing")) {
        object.config.set_deserialize("support_ironing_spacing", std::to_string(*value), ctxt);
    }
    if (const auto value = extract_number(json, "support_expansion")) {
        object.config.set_deserialize("support_expansion", std::to_string(*value), ctxt);
    }
    if (const auto value = extract_number(json, "support_object_xy_distance")) {
        object.config.set_deserialize("support_object_xy_distance", std::to_string(*value), ctxt);
    }
}

static void apply_explicit_process_json_to_object(
    const std::string& json,
    Slic3r::ModelObject& object,
    int object_index,
    Slic3r::ConfigSubstitutionContext& ctxt)
{
    int accepted_count = 0;
    int ignored_count = 0;
    std::vector<std::string> accepted_keys;
    std::vector<std::string> ignored_keys;
    for (const auto& [key, raw_value] : json_object_members(json)) {
        if (key.empty() ||
            starts_with_case_insensitive(key, "mobile_slicer_") ||
            is_non_object_profile_metadata_key(key)) {
            ++ignored_count;
            append_unique_limited(ignored_keys, key, 12);
            continue;
        }
        const auto config_value = config_value_from_json(raw_value);
        if (!config_value) {
            ++ignored_count;
            append_unique_limited(ignored_keys, key, 12);
            continue;
        }
        try {
            object.config.set_deserialize(key, *config_value, ctxt);
            ++accepted_count;
            append_unique_limited(accepted_keys, key, 12);
        } catch (const std::exception&) {
            ++ignored_count;
            append_unique_limited(ignored_keys, key, 12);
            // Orca stores process, object, region, printer, and filament keys in
            // adjacent config JSON. At object scope, only keys accepted by
            // ModelObject::config should be applied; unsupported keys remain
            // preserved in the mobile profile JSON but must not abort slicing.
        } catch (...) {
            ++ignored_count;
            append_unique_limited(ignored_keys, key, 12);
        }
    }
    std::ostringstream message;
    message << "objectIndex=" << object_index
            << " accepted=" << accepted_count
            << " ignored=" << ignored_count;
    if (!accepted_keys.empty()) {
        message << " acceptedKeys=";
        for (size_t i = 0; i < accepted_keys.size(); ++i) {
            if (i > 0) {
                message << ",";
            }
            message << accepted_keys[i];
        }
    }
    if (!ignored_keys.empty()) {
        message << " ignoredKeys=";
        for (size_t i = 0; i < ignored_keys.size(); ++i) {
            if (i > 0) {
                message << ",";
            }
            message << ignored_keys[i];
        }
    }
    log_native_info("object_process_override", message.str());
}

static void apply_explicit_process_json_to_volume(
    const std::string& json,
    Slic3r::ModelVolume& volume,
    int object_index,
    int volume_index,
    Slic3r::ConfigSubstitutionContext& ctxt)
{
    int accepted_count = 0;
    int ignored_count = 0;
    std::vector<std::string> accepted_keys;
    std::vector<std::string> ignored_keys;
    for (const auto& [key, raw_value] : json_object_members(json)) {
        if (key.empty() ||
            starts_with_case_insensitive(key, "mobile_slicer_") ||
            is_non_object_profile_metadata_key(key)) {
            ++ignored_count;
            append_unique_limited(ignored_keys, key, 12);
            continue;
        }
        const auto config_value = config_value_from_json(raw_value);
        if (!config_value) {
            ++ignored_count;
            append_unique_limited(ignored_keys, key, 12);
            continue;
        }
        try {
            volume.config.set_deserialize(key, *config_value, ctxt);
            ++accepted_count;
            append_unique_limited(accepted_keys, key, 12);
        } catch (const std::exception&) {
            ++ignored_count;
            append_unique_limited(ignored_keys, key, 12);
        } catch (...) {
            ++ignored_count;
            append_unique_limited(ignored_keys, key, 12);
        }
    }
    std::ostringstream message;
    message << "objectIndex=" << object_index
            << " volumeIndex=" << volume_index
            << " accepted=" << accepted_count
            << " ignored=" << ignored_count;
    if (!accepted_keys.empty()) {
        message << " acceptedKeys=";
        for (size_t i = 0; i < accepted_keys.size(); ++i) {
            if (i > 0) {
                message << ",";
            }
            message << accepted_keys[i];
        }
    }
    if (!ignored_keys.empty()) {
        message << " ignoredKeys=";
        for (size_t i = 0; i < ignored_keys.size(); ++i) {
            if (i > 0) {
                message << ",";
            }
            message << ignored_keys[i];
        }
    }
    log_native_info("modifier_process_override", message.str());
}

static bool load_modifier_stl_mesh(const std::string& path, Slic3r::TriangleMesh& mesh)
{
    if (path.empty()) {
        return false;
    }
    Slic3r::Model modifier_model;
    if (!Slic3r::load_stl(path.c_str(), &modifier_model, nullptr, nullptr, 80)) {
        return false;
    }
    if (modifier_model.objects.empty() || modifier_model.objects.front() == nullptr ||
        modifier_model.objects.front()->volumes.empty() || modifier_model.objects.front()->volumes.front() == nullptr) {
        return false;
    }
    mesh = modifier_model.objects.front()->volumes.front()->mesh();
    return !mesh.empty();
}

static void apply_modifier_process_overrides(const std::string& json, Slic3r::Model& model, Slic3r::ConfigSubstitutionContext& ctxt)
{
    const auto raw_modifier_overrides = indexed_json_value(json, "mobile_slicer_modifier_process_overrides");
    if (!raw_modifier_overrides) {
        return;
    }
    for (const std::string& override_json : json_array_objects(*raw_modifier_overrides)) {
        const auto raw_config = indexed_json_value(override_json, "config");
        const auto plate_object_index = extract_number(override_json, "plateObjectIndex");
        const auto path = extract_string(override_json, "path");
        if (!raw_config || !plate_object_index || !path) {
            continue;
        }
        const int object_index = static_cast<int>(*plate_object_index);
        if (object_index < 0 || object_index >= static_cast<int>(model.objects.size()) || model.objects[object_index] == nullptr) {
            continue;
        }
        Slic3r::TriangleMesh modifier_mesh;
        if (!load_modifier_stl_mesh(*path, modifier_mesh)) {
            log_native_info("modifier_process_override", "objectIndex=" + std::to_string(object_index) + " ignored=1 ignoredKeys=path");
            continue;
        }
        Slic3r::ModelObject& object = *model.objects[object_index];
        Slic3r::ModelVolume* volume = object.add_volume(std::move(modifier_mesh), Slic3r::ModelVolumeType::PARAMETER_MODIFIER, false);
        if (volume == nullptr) {
            continue;
        }
        volume->name = extract_string(override_json, "label").value_or("MobileSlicer modifier");
        if (const auto raw_transform = json_object_member_value(override_json, "transform")) {
            if (const auto transform = modifier_transform_from_json(*raw_transform)) {
                volume->set_transformation(*transform);
            } else {
                log_native_info("modifier_process_override", "objectIndex=" + std::to_string(object_index) + " ignored=1 ignoredKeys=transform");
            }
        }
        const int volume_index = static_cast<int>(object.volumes.size()) - 1;
        apply_explicit_process_json_to_volume(std::string(*raw_config), *volume, object_index, volume_index, ctxt);
        object.invalidate_bounding_box();
    }
}

void apply_model_object_overrides(const std::string& json, Slic3r::Model& model)
{
    if (json.empty()) {
        return;
    }

    const bool flow_rate_calibration =
        extract_bool(json, "mobile_slicer_calibration_active").value_or(false) &&
        extract_string(json, "mobile_slicer_calibration_type").value_or("") == "FlowRate";
    Slic3r::ConfigSubstitutionContext ctxt { Slic3r::ForwardCompatibilitySubstitutionRule::Disable };
    for (Slic3r::ModelObject* object : model.objects) {
        if (object == nullptr) {
            continue;
        }

        // PrintApply rebuilds object config from the default object config and then
        // reapplies ModelObject::config on top. Mirror object-owning overrides here so
        // default object-level values do not overwrite the wrapper config before export.
        apply_process_json_to_object(json, *object, ctxt);
        if (flow_rate_calibration) {
            std::string object_name = object->name;
            if (object_name.find("flowrate_") == std::string::npos && !object->input_file.empty()) {
                object_name = std::filesystem::path(object->input_file).stem().string();
            }
            const auto marker = object_name.find("flowrate_");
            if (marker != std::string::npos) {
                std::string modifier_text = object_name.substr(marker + std::string("flowrate_").size());
                const auto extension_marker = modifier_text.find('.');
                if (extension_marker != std::string::npos) {
                    modifier_text = modifier_text.substr(0, extension_marker);
                }
                const auto separator_marker = modifier_text.find_first_of("-_ ");
                if (separator_marker != std::string::npos) {
                    modifier_text = modifier_text.substr(0, separator_marker);
                }
                if (!modifier_text.empty() && modifier_text[0] == 'm') {
                    modifier_text[0] = '-';
                }
                try {
                    const double modifier = std::stod(modifier_text);
                    const double print_flow_ratio = 1.0 + modifier / 100.0;
                    object->config.set_deserialize("print_flow_ratio", std::to_string(print_flow_ratio), ctxt);
                } catch (...) {
                }
            }
        }
    }

    const auto raw_object_overrides = indexed_json_value(json, "mobile_slicer_object_process_overrides");
    if (!raw_object_overrides) {
        apply_modifier_process_overrides(json, model, ctxt);
        return;
    }
    for (const std::string& override_json : json_array_objects(*raw_object_overrides)) {
        const auto raw_config = indexed_json_value(override_json, "config");
        const auto plate_object_index = extract_number(override_json, "plateObjectIndex");
        if (!raw_config || !plate_object_index) {
            continue;
        }
        const int object_index = static_cast<int>(*plate_object_index);
        if (object_index < 0 || object_index >= static_cast<int>(model.objects.size()) || model.objects[object_index] == nullptr) {
            continue;
        }
        const std::string config_json(*raw_config);
        apply_explicit_process_json_to_object(config_json, *model.objects[object_index], object_index, ctxt);
    }
    apply_modifier_process_overrides(json, model, ctxt);
}

} // namespace mobileslicer::orca_wrapper

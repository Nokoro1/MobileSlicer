#include <filesystem>
#include <fstream>
#include <iostream>
#include <map>
#include <memory>
#include <cstdint>
#include <limits>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

#include "nlohmann/json.hpp"

#include "libslic3r/Config.hpp"
#include "libslic3r/Format/STL.hpp"
#include "libslic3r/GCode/GCodeProcessor.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/Slicing.hpp"
#include "libslic3r/TriangleSelector.hpp"

namespace {

using json = nlohmann::json;

std::string read_file_to_string(const char *path)
{
    std::ifstream input(path, std::ios::binary);
    std::ostringstream buffer;
    buffer << input.rdbuf();
    return buffer.str();
}

bool require_numeric(const json &root, const char *key, std::vector<std::string> &errors, double &out_value)
{
    auto it = root.find(key);
    if (it == root.end()) {
        errors.emplace_back(std::string("missing required key: ") + key);
        return false;
    }
    if (!it->is_number()) {
        errors.emplace_back(std::string("key must be numeric: ") + key);
        return false;
    }
    out_value = it->get<double>();
    return true;
}

bool optional_numeric(const json &root, const char *key, std::vector<std::string> &errors, double &out_value)
{
    auto it = root.find(key);
    if (it == root.end())
        return false;
    if (!it->is_number()) {
        errors.emplace_back(std::string("key must be numeric: ") + key);
        return false;
    }
    out_value = it->get<double>();
    return true;
}

bool optional_string(const json &root, const char *key, std::vector<std::string> &errors, std::string &out_value)
{
    auto it = root.find(key);
    if (it == root.end())
        return false;
    if (!it->is_string()) {
        errors.emplace_back(std::string("key must be a string: ") + key);
        return false;
    }
    out_value = it->get<std::string>();
    return true;
}

bool optional_bool(const json &root, const char *key, std::vector<std::string> &errors, bool &out_value)
{
    auto it = root.find(key);
    if (it == root.end())
        return false;
    if (!it->is_boolean()) {
        errors.emplace_back(std::string("key must be a boolean: ") + key);
        return false;
    }
    out_value = it->get<bool>();
    return true;
}

void set_scalar_option(Slic3r::DynamicPrintConfig &config, const char *key, double value)
{
    Slic3r::ConfigSubstitutionContext ctxt { Slic3r::ForwardCompatibilitySubstitutionRule::Disable };
    config.set_deserialize(key, std::to_string(value), ctxt);
}

void set_int_option(Slic3r::DynamicPrintConfig &config, const char *key, int value)
{
    Slic3r::ConfigSubstitutionContext ctxt { Slic3r::ForwardCompatibilitySubstitutionRule::Disable };
    config.set_deserialize(key, std::to_string(value), ctxt);
}

void set_string_option(Slic3r::DynamicPrintConfig &config, const char *key, const std::string &value)
{
    Slic3r::ConfigSubstitutionContext ctxt { Slic3r::ForwardCompatibilitySubstitutionRule::Disable };
    config.set_deserialize(key, value, ctxt);
}

Slic3r::DynamicPrintConfig make_full_print_config()
{
    Slic3r::DynamicPrintConfig config;
    config.apply(Slic3r::FullPrintConfig::defaults());
    return config;
}

void apply_printable_volume_override(double bed_width_mm, double bed_depth_mm, double max_height_mm, Slic3r::DynamicPrintConfig &config)
{
    const double width = std::max(1.0, bed_width_mm);
    const double depth = std::max(1.0, bed_depth_mm);
    const double height = std::max(1.0, max_height_mm);
    const double min_x = -width * 0.5;
    const double max_x = width * 0.5;
    const double min_y = -depth * 0.5;
    const double max_y = depth * 0.5;

    std::ostringstream area;
    area << min_x << "x" << min_y << ","
         << max_x << "x" << min_y << ","
         << max_x << "x" << max_y << ","
         << min_x << "x" << max_y;

    set_string_option(config, "printable_area", area.str());
    set_scalar_option(config, "printable_height", height);
}

void apply_bed_temperature_override(int temperature_c, Slic3r::DynamicPrintConfig &config)
{
    const std::string value = std::to_string(temperature_c);
    set_string_option(config, "hot_plate_temp", value);
    set_string_option(config, "hot_plate_temp_initial_layer", value);

    if (const auto *bed_type = config.option<Slic3r::ConfigOptionEnum<Slic3r::BedType>>("curr_bed_type")) {
        const auto current_bed_key = Slic3r::get_bed_temp_key(bed_type->value);
        const auto current_bed_key_first_layer = Slic3r::get_bed_temp_1st_layer_key(bed_type->value);
        if (!current_bed_key.empty())
            set_string_option(config, current_bed_key.c_str(), value);
        if (!current_bed_key_first_layer.empty())
            set_string_option(config, current_bed_key_first_layer.c_str(), value);
    }
}

void apply_cooling_baseline_override(int cooling_percent, Slic3r::DynamicPrintConfig &config)
{
    const std::string value = std::to_string(std::clamp(cooling_percent, 0, 100));
    set_string_option(config, "fan_min_speed", value);
    set_string_option(config, "fan_max_speed", value);
}

bool apply_reference_json_to_full_config(const std::string &body, Slic3r::DynamicPrintConfig &config, std::vector<std::string> &errors)
{
    json root = json::parse(body, nullptr, false, true);
    if (root.is_discarded()) {
        errors.emplace_back("invalid JSON payload");
        return false;
    }
    if (!root.is_object()) {
        errors.emplace_back("top-level JSON value must be an object");
        return false;
    }

    double layer_height = 0.0;
    double first_layer_height = 0.0;
    double nozzle_diameter = 0.0;
    double filament_diameter = 0.0;
    double bed_temperature = 0.0;
    double nozzle_temperature = 0.0;
    require_numeric(root, "layer_height", errors, layer_height);
    require_numeric(root, "first_layer_height", errors, first_layer_height);
    require_numeric(root, "nozzle_diameter", errors, nozzle_diameter);
    require_numeric(root, "filament_diameter", errors, filament_diameter);
    require_numeric(root, "bed_temperature", errors, bed_temperature);
    if (!optional_numeric(root, "nozzle_temperature", errors, nozzle_temperature))
        require_numeric(root, "print_temperature", errors, nozzle_temperature);

    double bed_width_mm = 0.0;
    double bed_depth_mm = 0.0;
    double max_height_mm = 0.0;
    double wall_loops = 0.0;
    double sparse_infill_density = 0.0;
    double skirt_loops = 0.0;
    double cooling_baseline = 0.0;
    double top_shell_layers = 0.0;
    double bottom_shell_layers = 0.0;
    double first_layer_print_speed = 0.0;
    double brim_width = 0.0;
    std::string filament_type;
    std::string seam_position;
    std::string support_type;
    std::string top_surface_pattern;
    std::string sparse_infill_pattern;
    bool precise_outer_wall = false;
    bool enable_support = false;

    const bool has_bed_width = optional_numeric(root, "bed_width_mm", errors, bed_width_mm);
    const bool has_bed_depth = optional_numeric(root, "bed_depth_mm", errors, bed_depth_mm);
    const bool has_max_height = optional_numeric(root, "max_height_mm", errors, max_height_mm);
    const bool has_wall_loops =
        optional_numeric(root, "wall_loops", errors, wall_loops) ||
        optional_numeric(root, "perimeters", errors, wall_loops);
    const bool has_sparse_infill_density = optional_numeric(root, "sparse_infill_density", errors, sparse_infill_density);
    const bool has_skirt_loops =
        optional_numeric(root, "skirt_loops", errors, skirt_loops) ||
        optional_numeric(root, "skirts", errors, skirt_loops);
    const bool has_cooling_baseline = optional_numeric(root, "cooling_baseline", errors, cooling_baseline);
    const bool has_top_shell_layers = optional_numeric(root, "top_shell_layers", errors, top_shell_layers);
    const bool has_bottom_shell_layers = optional_numeric(root, "bottom_shell_layers", errors, bottom_shell_layers);
    const bool has_first_layer_print_speed =
        optional_numeric(root, "first_layer_print_speed", errors, first_layer_print_speed) ||
        optional_numeric(root, "initial_layer_speed", errors, first_layer_print_speed);
    const bool has_brim_width = optional_numeric(root, "brim_width", errors, brim_width);
    const bool has_filament_type = optional_string(root, "filament_type", errors, filament_type);
    const bool has_seam_position = optional_string(root, "seam_position", errors, seam_position);
    const bool has_support_type = optional_string(root, "support_type", errors, support_type);
    const bool has_top_surface_pattern = optional_string(root, "top_surface_pattern", errors, top_surface_pattern);
    const bool has_sparse_infill_pattern = optional_string(root, "sparse_infill_pattern", errors, sparse_infill_pattern);
    const bool has_precise_outer_wall = optional_bool(root, "precise_outer_wall", errors, precise_outer_wall);
    const bool has_enable_support = optional_bool(root, "enable_support", errors, enable_support);

    if (!errors.empty())
        return false;

    set_scalar_option(config, "layer_height", layer_height);
    set_scalar_option(config, "initial_layer_print_height", first_layer_height);
    set_scalar_option(config, "nozzle_diameter", nozzle_diameter);
    set_scalar_option(config, "filament_diameter", filament_diameter);
    apply_bed_temperature_override(static_cast<int>(bed_temperature), config);
    set_int_option(config, "nozzle_temperature", static_cast<int>(nozzle_temperature));
    set_int_option(config, "nozzle_temperature_initial_layer", static_cast<int>(nozzle_temperature));

    if (has_bed_width && has_bed_depth && has_max_height)
        apply_printable_volume_override(bed_width_mm, bed_depth_mm, max_height_mm, config);
    if (has_wall_loops)
        set_int_option(config, "wall_loops", static_cast<int>(wall_loops));
    if (has_sparse_infill_density)
        set_string_option(config, "sparse_infill_density", std::to_string(sparse_infill_density) + "%");
    if (has_top_shell_layers)
        set_int_option(config, "top_shell_layers", static_cast<int>(top_shell_layers));
    if (has_bottom_shell_layers)
        set_int_option(config, "bottom_shell_layers", static_cast<int>(bottom_shell_layers));
    if (has_skirt_loops)
        set_int_option(config, "skirt_loops", static_cast<int>(skirt_loops));
    if (has_brim_width)
        set_scalar_option(config, "brim_width", brim_width);
    if (has_cooling_baseline)
        apply_cooling_baseline_override(static_cast<int>(cooling_baseline), config);
    if (has_first_layer_print_speed)
        set_scalar_option(config, "initial_layer_speed", first_layer_print_speed);
    if (has_filament_type)
        set_string_option(config, "filament_type", filament_type);
    if (has_seam_position)
        set_string_option(config, "seam_position", seam_position);
    if (has_enable_support)
        set_string_option(config, "enable_support", enable_support ? "1" : "0");
    if (has_support_type)
        set_string_option(config, "support_type", support_type);
    if (has_top_surface_pattern)
        set_string_option(config, "top_surface_pattern", top_surface_pattern);
    if (has_sparse_infill_pattern)
        set_string_option(config, "sparse_infill_pattern", sparse_infill_pattern);
    if (has_precise_outer_wall)
        set_string_option(config, "precise_outer_wall", precise_outer_wall ? "1" : "0");

    // Keep the experimental probe on the narrow real slicing/export path without
    // depending on preset-specific custom-gcode placeholder definitions.
    // Neutralize all custom-gcode fields that might contain preset-specific
    // placeholder syntax to avoid late validation failures.
    config.option<Slic3r::ConfigOptionStrings>("filament_start_gcode", true)->values = { "" };
    config.option<Slic3r::ConfigOptionStrings>("filament_end_gcode", true)->values = { "" };
    config.option<Slic3r::ConfigOptionString>("layer_change_gcode", true)->value = "";
    config.option<Slic3r::ConfigOptionString>("before_layer_change_gcode", true)->value = "";
    config.option<Slic3r::ConfigOptionString>("change_filament_gcode", true)->value = "";
    config.option<Slic3r::ConfigOptionString>("machine_start_gcode", true)->value = "";
    config.option<Slic3r::ConfigOptionString>("machine_end_gcode", true)->value = "";
    config.option<Slic3r::ConfigOptionString>("time_lapse_gcode", true)->value = "";
    config.option<Slic3r::ConfigOptionString>("change_extrusion_role_gcode", true)->value = "";
    config.option<Slic3r::ConfigOptionString>("printing_by_object_gcode", true)->value = "";
    config.option<Slic3r::ConfigOptionString>("machine_pause_gcode", true)->value = "";
    config.option<Slic3r::ConfigOptionString>("template_custom_gcode", true)->value = "";

    const std::map<std::string, std::string> validation_errors = config.validate(false);
    for (const auto &entry : validation_errors)
        errors.emplace_back(entry.first + ": " + entry.second);
    return errors.empty();
}

std::string temp_gcode_path()
{
    return "/data/local/tmp/orca_android_libslic3r_print_probe_output.gcode";
}

std::string first_nonempty_line(const std::string &text)
{
    std::istringstream input(text);
    std::string line;
    while (std::getline(input, line)) {
        if (!line.empty())
            return line;
    }
    return {};
}

std::uint64_t fnv1a64(const std::string &text)
{
    std::uint64_t hash = 1469598103934665603ull;
    for (const unsigned char ch : text) {
        hash ^= ch;
        hash *= 1099511628211ull;
    }
    return hash;
}

template <typename Vec3Like>
std::string format_vec3(const Vec3Like &v)
{
    std::ostringstream out;
    out << v.x() << "," << v.y() << "," << v.z();
    return out.str();
}

std::string format_layer_pairs(const std::vector<double> &layers)
{
    std::ostringstream out;
    for (size_t idx = 0; idx + 1 < layers.size(); idx += 2) {
        if (idx != 0)
            out << " ";
        out << "[" << layers[idx] << "," << layers[idx + 1] << "]";
    }
    return out.str();
}

Slic3r::Vec3f facet_center(const Slic3r::TriangleMesh &mesh, int facet_idx)
{
    const Slic3r::Vec3i32 face = mesh.its.indices[facet_idx];
    return (mesh.its.vertices[face[0]] + mesh.its.vertices[face[1]] + mesh.its.vertices[face[2]]) / 3.f;
}

Slic3r::Vec3f facet_normal(const Slic3r::TriangleMesh &mesh, int facet_idx)
{
    const Slic3r::Vec3i32 face = mesh.its.indices[facet_idx];
    Slic3r::Vec3f normal = (mesh.its.vertices[face[1]] - mesh.its.vertices[face[0]])
        .cross(mesh.its.vertices[face[2]] - mesh.its.vertices[face[0]]);
    const float length = normal.norm();
    return length > 1e-8f ? normal / length : Slic3r::Vec3f(0.f, 0.f, 1.f);
}

int first_downward_facet_above_bed(const Slic3r::TriangleMesh &mesh)
{
    int best_facet = 0;
    float best_z = 1.f;
    float min_vertex_z = std::numeric_limits<float>::max();
    float max_vertex_z = std::numeric_limits<float>::lowest();
    for (const Slic3r::Vec3f &vertex : mesh.its.vertices) {
        min_vertex_z = std::min(min_vertex_z, vertex.z());
        max_vertex_z = std::max(max_vertex_z, vertex.z());
    }
    const float min_center_z = min_vertex_z + std::max(0.5f, (max_vertex_z - min_vertex_z) * 0.05f);
    for (int facet = 0; facet < int(mesh.facets_count()); ++facet) {
        const Slic3r::Vec3f center = facet_center(mesh, facet);
        if (center.z() <= min_center_z)
            continue;
        const float normal_z = facet_normal(mesh, facet).z();
        if (normal_z < best_z) {
            best_z = normal_z;
            best_facet = facet;
        }
    }
    return best_facet;
}

std::vector<std::pair<int, std::string>> serialized_annotation_triangles(
    const Slic3r::FacetsAnnotation &annotation,
    int facet_count)
{
    std::vector<std::pair<int, std::string>> result;
    for (int facet = 0; facet < facet_count; ++facet) {
        std::string payload = annotation.get_triangle_as_string(facet);
        if (!payload.empty())
            result.emplace_back(facet, std::move(payload));
    }
    return result;
}

bool apply_probe_paint(Slic3r::Model &model, const std::string &mode, std::string &error)
{
    if (model.objects.empty() || model.objects.front() == nullptr || model.objects.front()->volumes.empty()) {
        error = "paint probe model has no target volume";
        return false;
    }

    Slic3r::ModelVolume *volume = model.objects.front()->volumes.front();
    if (volume == nullptr || volume->mesh().empty()) {
        error = "paint probe target volume has no mesh";
        return false;
    }

    const Slic3r::TriangleMesh &mesh = volume->mesh();
    const int facet = first_downward_facet_above_bed(mesh);
    const Slic3r::Vec3f center = facet_center(mesh, facet);
    const Slic3r::Vec3f normal = facet_normal(mesh, facet);
    const Slic3r::Vec3f camera = center + normal * 100.f;
    const Slic3r::Transform3d identity = Slic3r::Transform3d::Identity();
    const Slic3r::TriangleSelector::ClippingPlane clipping;

    Slic3r::TriangleSelector selector(mesh);
    Slic3r::EnforcerBlockerType paint_state = Slic3r::EnforcerBlockerType::ENFORCER;
    if (mode == "color")
        paint_state = Slic3r::EnforcerBlockerType::Extruder2;
    else if (mode == "fuzzy")
        paint_state = Slic3r::EnforcerBlockerType::FUZZY_SKIN;

    if (mode == "color") {
        selector.bucket_fill_select_triangles(center, facet, clipping, 180.f, true, true);
        selector.seed_fill_apply_on_triangles(paint_state);
    } else {
        selector.select_patch(
            facet,
            Slic3r::TriangleSelector::SinglePointCursor::cursor_factory(
                center,
                camera,
                12.0f,
                Slic3r::TriangleSelector::CursorType::CIRCLE,
                identity,
                clipping),
            paint_state,
            identity,
            true,
            0.f);
    }

    if (!selector.has_facets(paint_state)) {
        error = "paint probe TriangleSelector produced no target-state facets";
        return false;
    }

    Slic3r::FacetsAnnotation *annotation = nullptr;
    if (mode == "support") {
        annotation = &volume->supported_facets;
    } else if (mode == "seam") {
        annotation = &volume->seam_facets;
    } else if (mode == "color") {
        annotation = &volume->mmu_segmentation_facets;
    } else if (mode == "fuzzy") {
        annotation = &volume->fuzzy_skin_facets;
    } else {
        error = "paint probe mode must be support, seam, color, or fuzzy";
        return false;
    }

    if (!annotation->set(selector)) {
        error = "paint probe FacetsAnnotation rejected selector data";
        return false;
    }
    const std::vector<std::pair<int, std::string>> replay_triangles =
        serialized_annotation_triangles(*annotation, int(mesh.facets_count()));
    if (replay_triangles.empty()) {
        error = "paint probe annotation produced no serialized triangle payload";
        return false;
    }

    annotation->reset();
    annotation->reserve(int(mesh.facets_count()));
    for (const auto &triangle : replay_triangles)
        annotation->set_triangle_from_string(triangle.first, triangle.second);
    annotation->shrink_to_fit();

    std::cerr << "debug: paint_probe mode=" << mode
              << " facet=" << facet
              << " normal=" << format_vec3(normal)
              << " replay_triangles=" << replay_triangles.size()
              << " payload_triangle=" << replay_triangles.front().first
              << " payload_hex_len=" << replay_triangles.front().second.size()
              << "\n";
    return true;
}

void configure_mode_for_probe(const std::string &paint_mode, Slic3r::DynamicPrintConfig &config)
{
    if (paint_mode == "color") {
        set_string_option(config, "wall_generator", "classic");
        set_scalar_option(config, "line_width", 0.42);
        set_scalar_option(config, "inner_wall_line_width", 0.42);
        set_scalar_option(config, "outer_wall_line_width", 0.42);
        set_scalar_option(config, "sparse_infill_line_width", 0.42);
        set_scalar_option(config, "internal_solid_infill_line_width", 0.42);
        set_scalar_option(config, "top_surface_line_width", 0.42);
        set_scalar_option(config, "support_line_width", 0.42);
        config.option<Slic3r::ConfigOptionFloats>("nozzle_diameter", true)->values = { 0.4, 0.4, 0.4 };
        config.option<Slic3r::ConfigOptionFloats>("filament_diameter", true)->values = { 1.75, 1.75, 1.75 };
        config.option<Slic3r::ConfigOptionStrings>("filament_colour", true)->values = { "#FF5038", "#2A8CFF", "#06D6A0" };
        config.option<Slic3r::ConfigOptionStrings>("filament_type", true)->values = { "PLA", "PLA", "PLA" };
        config.option<Slic3r::ConfigOptionInts>("nozzle_temperature", true)->values = { 210, 210, 210 };
        config.option<Slic3r::ConfigOptionInts>("nozzle_temperature_initial_layer", true)->values = { 210, 210, 210 };
        config.option<Slic3r::ConfigOptionStrings>("filament_start_gcode", true)->values = { "", "", "" };
        config.option<Slic3r::ConfigOptionStrings>("filament_end_gcode", true)->values = { "", "", "" };
        config.option<Slic3r::ConfigOptionFloats>("flush_volumes_matrix", true)->values = {
              0.f, 120.f, 120.f,
            120.f,   0.f, 120.f,
            120.f, 120.f,   0.f,
        };
        config.option<Slic3r::ConfigOptionFloats>("flush_multiplier", true)->values = { 1.f };
    } else if (paint_mode == "fuzzy") {
        set_string_option(config, "fuzzy_skin", "none");
        set_scalar_option(config, "fuzzy_skin_thickness", 0.2);
        set_scalar_option(config, "fuzzy_skin_point_distance", 0.3);
        set_string_option(config, "fuzzy_skin_mode", "displacement");
    }
}

void dump_probe_state(const Slic3r::Model &model, const Slic3r::DynamicPrintConfig &config, const Slic3r::Print &print)
{
    std::cerr << "debug: model_objects=" << model.objects.size() << "\n";
    for (size_t object_idx = 0; object_idx < model.objects.size(); ++object_idx) {
        const Slic3r::ModelObject *object = model.objects[object_idx];
        const auto raw_mesh_bbox = object->raw_mesh_bounding_box();
        const auto instance_bbox = object->instance_bounding_box(0);
        std::cerr << "debug: model_object[" << object_idx << "] raw_mesh_bbox_min=" << format_vec3(raw_mesh_bbox.min)
                  << " raw_mesh_bbox_max=" << format_vec3(raw_mesh_bbox.max)
                  << " instance_bbox_min=" << format_vec3(instance_bbox.min)
                  << " instance_bbox_max=" << format_vec3(instance_bbox.max)
                  << " instances=" << object->instances.size()
                  << " volumes=" << object->volumes.size()
                  << "\n";
    }

    std::cerr << "debug: config layer_height=" << config.opt_float("layer_height")
              << " initial_layer_print_height=" << config.opt_float("initial_layer_print_height")
              << " nozzle_diameter=" << config.opt_float("nozzle_diameter", 0)
              << " filament_diameter=" << config.opt_float("filament_diameter", 0)
              << " printable_height=" << config.opt_float("printable_height")
              << " print_sequence=" << config.option("print_sequence")->serialize()
              << "\n";

    std::cerr << "debug: print_objects=" << print.objects().size() << "\n";

    for (size_t print_object_idx = 0; print_object_idx < print.objects().size(); ++print_object_idx) {
        const Slic3r::PrintObject *object = print.objects()[print_object_idx];
        const Slic3r::SlicingParameters &params = object->slicing_parameters();
        std::vector<double> layer_height_profile;
        Slic3r::PrintObject::update_layer_height_profile(*object->model_object(), params, layer_height_profile);
        const std::vector<double> generated_layers =
            Slic3r::generate_object_layers(params, layer_height_profile, object->config().precise_z_height.value);

        std::cerr << "debug: print_object[" << print_object_idx << "] size=" << object->size().x() << ","
                  << object->size().y() << "," << object->size().z()
                  << " height=" << object->height()
                  << " max_z=" << object->max_z()
                  << " center_offset=" << object->center_offset().x() << "," << object->center_offset().y()
                  << "\n";
        std::cerr << "debug: slicing_params layer_height=" << params.layer_height
                  << " first_print_layer_height=" << params.first_print_layer_height
                  << " first_object_layer_height=" << params.first_object_layer_height
                  << " object_print_z_min=" << params.object_print_z_min
                  << " object_print_z_max=" << params.object_print_z_max
                  << " object_print_z_uncompensated_max=" << params.object_print_z_uncompensated_max
                  << " object_print_z_height=" << params.object_print_z_height()
                  << " min_layer_height=" << params.min_layer_height
                  << " max_layer_height=" << params.max_layer_height
                  << " shrinkage_compensation_z=" << params.object_shrinkage_compensation_z
                  << "\n";
        std::cerr << "debug: layer_height_profile=" << format_layer_pairs(layer_height_profile) << "\n";
        std::cerr << "debug: generated_layers=" << format_layer_pairs(generated_layers) << "\n";
    }
}

} // namespace

int main(int argc, char **argv)
{
    if (argc != 3 && argc != 4) {
        std::cerr << "usage: orca_android_libslic3r_print_gcode_probe <input.stl> <config.json> [paint-support|paint-seam|paint-color|paint-fuzzy]\n";
        return 2;
    }

    try {
        Slic3r::Model model;
        if (!Slic3r::load_stl(argv[1], &model)) {
            std::cerr << "error: load_stl returned false for: " << argv[1] << "\n";
            return 1;
        }
        if (model.objects.empty() || model.objects.front()->volumes.empty()) {
            std::cerr << "error: model load incomplete after STL import\n";
            return 1;
        }

        for (Slic3r::ModelObject *object : model.objects) {
            if (object->instances.empty())
                object->add_instance();
            object->ensure_on_bed();
        }

        std::string paint_mode;
        if (argc == 4) {
            const std::string paint_arg(argv[3]);
            if (paint_arg == "paint-support") {
                paint_mode = "support";
            } else if (paint_arg == "paint-seam") {
                paint_mode = "seam";
            } else if (paint_arg == "paint-color") {
                paint_mode = "color";
            } else if (paint_arg == "paint-fuzzy") {
                paint_mode = "fuzzy";
            } else {
                std::cerr << "error: unsupported paint mode argument: " << paint_arg << "\n";
                return 2;
            }

            std::string paint_error;
            if (!apply_probe_paint(model, paint_mode, paint_error)) {
                std::cerr << "error: " << paint_error << "\n";
                return 1;
            }
        }

        const std::string json_body = read_file_to_string(argv[2]);
        if (json_body.empty()) {
            std::cerr << "error: failed to read config JSON from " << argv[2] << "\n";
            return 1;
        }

        Slic3r::DynamicPrintConfig config = make_full_print_config();
        std::vector<std::string> config_errors;
        if (!apply_reference_json_to_full_config(json_body, config, config_errors)) {
            for (const std::string &error : config_errors)
                std::cerr << "error: " << error << "\n";
            return 1;
        }
        configure_mode_for_probe(paint_mode, config);
        const std::map<std::string, std::string> mode_validation_errors = config.validate(false);
        if (!mode_validation_errors.empty()) {
            for (const auto &entry : mode_validation_errors)
                std::cerr << "error: " << entry.first << ": " << entry.second << "\n";
            return 1;
        }

        Slic3r::Print print;
        for (Slic3r::ModelObject *object : model.objects)
            print.auto_assign_extruders(object);

        const auto status = print.apply(model, config);
        if (status != Slic3r::Print::APPLY_STATUS_UNCHANGED && status != Slic3r::Print::APPLY_STATUS_CHANGED) {
            std::cerr << "error: Print::apply returned unexpected status: " << static_cast<int>(status) << "\n";
            return 1;
        }

        dump_probe_state(model, config, print);

        print.validate();
        print.set_status_callback([](const Slic3r::PrintBase::SlicingStatus &status) {
            std::cerr << "status: percent=" << status.percent
                      << " text=" << status.text
                      << " flags=" << status.flags
                      << "\n";
        });
        std::cerr << "debug: entering print.process()\n";
        print.process();
        std::cerr << "debug: print.process() completed\n";

        const std::string out_path = temp_gcode_path();
        Slic3r::GCodeProcessorResult gcode_result;
        print.export_gcode(out_path, &gcode_result, nullptr);
        const std::string gcode = read_file_to_string(out_path.c_str());
        if (gcode.empty()) {
            std::cerr << "error: export_gcode produced an empty file\n";
            return 1;
        }

        std::cout << "objects=" << model.objects.size()
                  << " volumes=" << model.objects.front()->volumes.size()
                  << " facets=" << model.objects.front()->volumes.front()->mesh().facets_count()
                  << "\n";
        if (!paint_mode.empty())
            std::cout << "paint_mode=" << paint_mode << "\n";
        std::cout << "gcode_bytes=" << gcode.size() << "\n";
        std::cout << "gcode_fnv1a64=" << fnv1a64(gcode) << "\n";
        std::cout << "gcode_first_line=" << first_nonempty_line(gcode) << "\n";
        return 0;
    } catch (const std::exception &error) {
        std::cerr << "error: exception during print probe: " << error.what() << "\n";
        return 1;
    }
}

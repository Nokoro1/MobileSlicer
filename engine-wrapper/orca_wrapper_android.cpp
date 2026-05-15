#include "orca_wrapper.h"

#include "orca-android-core/orca_android_core.hpp"

#include <fstream>
#include <new>
#include <string>

struct OrcaEngine {
    orca_android_core::ModelLoader loader;
    std::string config_json;
    std::string last_error;
};

namespace {

constexpr int ORCA_SUCCESS = 0;
constexpr int ORCA_ERROR_INVALID_ARGUMENT = -1;
constexpr int ORCA_ERROR_LOAD_MODEL = -2;
constexpr int ORCA_ERROR_CONFIG = -3;
constexpr int ORCA_ERROR_SLICE = -4;

constexpr const char* DEFAULT_SLICE_CONFIG_JSON = R"({
    "layer_height": 0.2,
    "first_layer_height": 0.2,
    "travel_speed": 120
})";

} // namespace

extern "C" void orca_set_runtime_paths(const char*, const char*)
{
}

extern "C" OrcaEngine* orca_create(void)
{
    try {
        return new OrcaEngine();
    } catch (...) {
        return nullptr;
    }
}

extern "C" void orca_destroy(OrcaEngine* engine)
{
    delete engine;
}

extern "C" int orca_load_model(OrcaEngine* engine, const char* path)
{
    if (engine == nullptr || path == nullptr || path[0] == '\0') {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }

    try {
        engine->last_error.clear();
        engine->loader.clear_generated_gcode();
        if (!engine->loader.load_model(path)) {
            engine->last_error = "reduced Android wrapper rejected the model";
            return ORCA_ERROR_LOAD_MODEL;
        }
        return ORCA_SUCCESS;
    } catch (...) {
        engine->last_error = "reduced Android wrapper load failed";
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" int orca_load_plate_models(OrcaEngine* engine, const char* const* paths, const double*, const int*, int count)
{
    if (engine == nullptr || paths == nullptr || count <= 0 || paths[0] == nullptr || paths[0][0] == '\0') {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }

    try {
        engine->last_error.clear();
        engine->loader.clear_generated_gcode();
        // The reduced Android wrapper is retained for lightweight host probes. It does
        // not model a true multi-object plate, so it falls back to the first object.
        if (!engine->loader.load_model(paths[0])) {
            engine->last_error = "reduced Android wrapper rejected the plate model";
            return ORCA_ERROR_LOAD_MODEL;
        }
        return ORCA_SUCCESS;
    } catch (...) {
        engine->last_error = "reduced Android wrapper plate load failed";
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" void orca_clear_generated_gcode(OrcaEngine* engine)
{
    if (engine != nullptr) {
        engine->loader.clear_generated_gcode();
    }
}

extern "C" int orca_plan_plate_arrangement(OrcaEngine* engine, const char* const* paths, const double* transforms, int, const int* extruder_ids, int count, const char*, int, double* out_transforms, int* out_bed_indices)
{
    if (engine == nullptr || paths == nullptr || transforms == nullptr || extruder_ids == nullptr || out_transforms == nullptr || out_bed_indices == nullptr || count <= 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    engine->last_error = "native Orca arrangement is unavailable in the reduced Android wrapper";
    return ORCA_ERROR_LOAD_MODEL;
}

extern "C" int orca_plan_auto_orientation(OrcaEngine* engine, const char* const* paths, const double* transforms, int, const int* extruder_ids, int count, const char*, double* out_transforms)
{
    if (engine == nullptr || paths == nullptr || transforms == nullptr || extruder_ids == nullptr || out_transforms == nullptr || count <= 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    engine->last_error = "native Orca auto-orient is unavailable in the reduced Android wrapper";
    return ORCA_ERROR_LOAD_MODEL;
}

extern "C" int orca_prewarm_plate_planning_models(OrcaEngine* engine, const char* const* paths, int count)
{
    if (engine == nullptr || paths == nullptr || count <= 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    engine->last_error = "native Orca planning prewarm is unavailable in the reduced Android wrapper";
    return ORCA_ERROR_LOAD_MODEL;
}

extern "C" int orca_extract_model_mesh_to_stl(OrcaEngine* engine, const char* input_path, const char* output_stl_path)
{
    if (engine == nullptr || input_path == nullptr || input_path[0] == '\0' ||
        output_stl_path == nullptr || output_stl_path[0] == '\0') {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    engine->last_error = "Mesh extraction requires the real libslic3r wrapper.";
    return ORCA_ERROR_LOAD_MODEL;
}

extern "C" int orca_convert_step_to_stl(
    OrcaEngine* engine,
    const char* input_path,
    const char* output_stl_path,
    double,
    double)
{
    if (engine == nullptr || input_path == nullptr || input_path[0] == '\0' ||
        output_stl_path == nullptr || output_stl_path[0] == '\0') {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    engine->last_error = "STEP import requires the real OCCT-backed libslic3r wrapper.";
    return ORCA_ERROR_LOAD_MODEL;
}

extern "C" int orca_cancel_planning(OrcaEngine* engine)
{
    return engine == nullptr ? ORCA_ERROR_INVALID_ARGUMENT : ORCA_SUCCESS;
}

extern "C" int orca_set_model_placement(OrcaEngine* engine, double, double, double)
{
    if (engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    engine->last_error.clear();
    return ORCA_SUCCESS;
}

extern "C" int orca_set_model_transform(OrcaEngine* engine, double, double, double, double, double, double, double)
{
    if (engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    engine->last_error.clear();
    return ORCA_SUCCESS;
}

extern "C" int orca_set_config_json(OrcaEngine* engine, const char* json)
{
    if (engine == nullptr || json == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }

    try {
        engine->last_error.clear();
        engine->config_json = json;
        if (!engine->loader.set_config_json(json)) {
            engine->last_error = "reduced Android wrapper rejected the slice config";
            return ORCA_ERROR_CONFIG;
        }
        return ORCA_SUCCESS;
    } catch (...) {
        engine->last_error = "reduced Android wrapper config failed";
        return ORCA_ERROR_CONFIG;
    }
}

extern "C" int orca_slice(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }

    try {
        engine->last_error.clear();
        const char* config_json = engine->config_json.empty() ? DEFAULT_SLICE_CONFIG_JSON : engine->config_json.c_str();
        if (!engine->loader.set_config_json(config_json)) {
            engine->last_error = "reduced Android wrapper rejected the slice config";
            return ORCA_ERROR_CONFIG;
        }
        if (!engine->loader.slice()) {
            engine->last_error = "reduced Android wrapper slice failed";
            return ORCA_ERROR_SLICE;
        }
        return ORCA_SUCCESS;
    } catch (...) {
        engine->last_error = "reduced Android wrapper slice failed";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" const char* orca_get_gcode(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return nullptr;
    }

    const std::string& gcode = engine->loader.gcode();
    return gcode.empty() ? nullptr : gcode.c_str();
}

extern "C" const char* orca_get_gcode_summary(OrcaEngine*)
{
    return nullptr;
}

extern "C" const char* orca_get_enriched_gcode_summary(OrcaEngine*)
{
    return nullptr;
}

extern "C" const char* orca_get_thumbnail_requests_json(OrcaEngine*)
{
    return nullptr;
}

extern "C" void orca_clear_slice_thumbnails(OrcaEngine*)
{
}

extern "C" int orca_add_slice_thumbnail_rgba(OrcaEngine*, int, int, const char*, const char*, const unsigned char*, int)
{
    return ORCA_ERROR_INVALID_ARGUMENT;
}

extern "C" int orca_write_gcode_to_file(OrcaEngine* engine, const char* path)
{
    if (engine == nullptr || path == nullptr || path[0] == '\0') {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }

    const std::string& gcode = engine->loader.gcode();
    if (gcode.empty()) {
        engine->last_error = "no generated G-code is available";
        return ORCA_ERROR_SLICE;
    }

    try {
        std::ofstream output(path, std::ios::binary | std::ios::trunc);
        if (!output) {
            engine->last_error = "unable to open G-code output path";
            return ORCA_ERROR_SLICE;
        }
        output.write(gcode.data(), static_cast<std::streamsize>(gcode.size()));
        if (!output) {
            engine->last_error = "unable to write G-code output file";
            return ORCA_ERROR_SLICE;
        }
        engine->last_error.clear();
        return ORCA_SUCCESS;
    } catch (...) {
        engine->last_error = "reduced Android wrapper G-code write failed";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" const char* orca_get_last_error(OrcaEngine* engine)
{
    if (engine == nullptr || engine->last_error.empty()) {
        return nullptr;
    }
    return engine->last_error.c_str();
}

extern "C" OrcaGcodeViewer* orca_gcode_viewer_create(void)
{
    return nullptr;
}

extern "C" void orca_gcode_viewer_destroy(OrcaGcodeViewer*)
{
}

extern "C" int orca_gcode_viewer_init(OrcaGcodeViewer*)
{
    return ORCA_ERROR_SLICE;
}

extern "C" int orca_gcode_viewer_shutdown(OrcaGcodeViewer*)
{
    return ORCA_ERROR_SLICE;
}

extern "C" int orca_gcode_viewer_load_gcode(OrcaGcodeViewer*, const char*)
{
    return ORCA_ERROR_SLICE;
}

extern "C" void orca_set_gcode_preview_generation(OrcaEngine*, long)
{
}

extern "C" int orca_gcode_viewer_load_latest_slice(OrcaGcodeViewer*, OrcaEngine*, long, long, int, long)
{
    return ORCA_ERROR_SLICE;
}

extern "C" const char* orca_gcode_preview_suggest_layer_ranges(OrcaEngine*, long, long, long)
{
    return nullptr;
}

extern "C" const char* orca_gcode_preview_plan_layer_ranges(OrcaEngine*, long, long, int)
{
    return "";
}

extern "C" int orca_gcode_viewer_render(OrcaGcodeViewer*, const float*, const float*)
{
    return ORCA_ERROR_SLICE;
}

extern "C" long orca_gcode_viewer_get_layers_count(OrcaGcodeViewer*)
{
    return 0;
}

extern "C" int orca_gcode_viewer_set_layers_view_range(OrcaGcodeViewer*, long, long)
{
    return ORCA_ERROR_SLICE;
}

extern "C" int orca_gcode_viewer_set_extrusion_width_scale(OrcaGcodeViewer*, float)
{
    return ORCA_ERROR_SLICE;
}

extern "C" int orca_gcode_viewer_set_path_visibility(OrcaGcodeViewer*, int, int, int)
{
    return ORCA_ERROR_SLICE;
}

extern "C" int orca_gcode_viewer_set_view_type(OrcaGcodeViewer*, int)
{
    return ORCA_ERROR_SLICE;
}

extern "C" const char* orca_gcode_viewer_get_last_error(OrcaGcodeViewer*)
{
    return nullptr;
}

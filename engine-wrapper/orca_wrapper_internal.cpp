#include "orca_wrapper_internal.h"

#include <algorithm>
#include <filesystem>
#include <fstream>
#include <sstream>

#ifdef __ANDROID__
#include <android/log.h>
#endif

namespace {

std::string read_file_to_string(const std::filesystem::path& path)
{
    std::ifstream input(path, std::ios::binary);
    std::ostringstream buffer;
    buffer << input.rdbuf();
    return buffer.str();
}

} // namespace

PaintVolumeBounds native_volume_bounds(const Slic3r::TriangleMesh& mesh)
{
    PaintVolumeBounds bounds;
    if (mesh.its.vertices.empty()) {
        return bounds;
    }

    bounds.min_x = bounds.max_x = mesh.its.vertices.front().x();
    bounds.min_y = bounds.max_y = mesh.its.vertices.front().y();
    bounds.min_z = bounds.max_z = mesh.its.vertices.front().z();
    for (const Slic3r::Vec3f& vertex : mesh.its.vertices) {
        bounds.min_x = std::min(bounds.min_x, vertex.x());
        bounds.min_y = std::min(bounds.min_y, vertex.y());
        bounds.min_z = std::min(bounds.min_z, vertex.z());
        bounds.max_x = std::max(bounds.max_x, vertex.x());
        bounds.max_y = std::max(bounds.max_y, vertex.y());
        bounds.max_z = std::max(bounds.max_z, vertex.z());
    }
    return bounds;
}

void log_native_error(const char* stage, const char* message)
{
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_ERROR, "MobileSlicerNative", "%s: %s", stage, message);
#else
    (void)stage;
    (void)message;
#endif
}

void log_native_info(const char* stage, const std::string& message)
{
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "MobileSlicerNative", "%s: %s", stage, message.c_str());
#else
    (void)stage;
    (void)message;
#endif
}

void clear_last_error(OrcaEngine* engine)
{
    if (engine != nullptr) {
        std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
        engine->impl.last_error.clear();
    }
}

void set_last_error(OrcaEngine* engine, const std::string& message)
{
    if (engine != nullptr) {
        std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
        engine->impl.last_error = message;
    }
}

bool ensure_gcode_loaded_unlocked(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return false;
    }
    if (engine->impl.gcode.empty() && !engine->impl.gcode_path.empty()) {
        engine->impl.gcode = read_file_to_string(engine->impl.gcode_path);
    }
    return !engine->impl.gcode.empty();
}

void clear_generated_gcode(OrcaEngine* engine)
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
    engine->impl.cached_preview_layer_counts.clear();
    engine->impl.cached_preview_layer_counts_source_size = 0;
    engine->impl.cached_preview_valid = false;
    engine->impl.cached_preview_complete = false;
#endif
}

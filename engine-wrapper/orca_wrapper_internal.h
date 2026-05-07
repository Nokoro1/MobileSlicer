#ifndef ORCA_WRAPPER_INTERNAL_H
#define ORCA_WRAPPER_INTERNAL_H

#include "orca_wrapper.h"
#include "orca_native_paint.h"

#include "libslic3r/Model.hpp"
#include "libslic3r/TriangleMesh.hpp"
#include "libslic3r/TriangleSelector.hpp"

#if defined(MOBILE_SLICER_ENABLE_VGCODE)
#include "libvgcode/include/GCodeInputData.hpp"
#endif

#include <atomic>
#include <cstdint>
#include <filesystem>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

constexpr int ORCA_SUCCESS = 0;
constexpr int ORCA_ERROR_INVALID_ARGUMENT = -1;
constexpr int ORCA_ERROR_LOAD_MODEL = -2;
constexpr int ORCA_ERROR_CONFIG = -3;
constexpr int ORCA_ERROR_SLICE = -4;
constexpr int ORCA_ERROR_ARRANGE_NO_FIT = -5;
constexpr int ORCA_PLATE_PRINTABLE_AREA_ERROR = 1 << 2;
constexpr int ORCA_PLATE_PRINTABLE_HEIGHT_ERROR = 1 << 3;

struct PaintVolumeBounds {
    float min_x{0.f};
    float min_y{0.f};
    float min_z{0.f};
    float max_x{0.f};
    float max_y{0.f};
    float max_z{0.f};
};

struct OrcaEngineImpl {
    struct PaintObjectBinding {
        long long mobile_object_id{0};
        std::vector<int> model_object_indices;
        std::vector<int> volume_triangle_counts;
        std::vector<std::string> volume_fingerprints;
        std::vector<PaintVolumeBounds> volume_bounds;
    };

    mutable std::recursive_mutex mutex;
    std::optional<Slic3r::Model> model;
    std::uint64_t model_generation{0};
    std::unordered_map<long long, PaintObjectBinding> paint_object_bindings;
    std::unique_ptr<mobileslicer::orca_paint::OrcaPaintSession> paint_session;
    int paint_tool_color_slot{1};
    float paint_tool_smart_fill_angle_deg{30.f};
    float paint_tool_overhang_angle_deg{0.f};
    Slic3r::TriangleSelector::ClippingPlane paint_tool_clipping;
    std::string paint_serialized_payload;
    std::string paint_overlay_snapshot;
    std::vector<float> paint_overlay_snapshot_interleaved;
    std::vector<float> paint_overlay_delta_interleaved;
    std::string paint_remapped_payload;
    std::string paint_binding_debug_snapshot;
    std::string paint_object_bounds_snapshot;
    std::string last_cut_result_json;
    std::string last_split_result_json;
    std::string config_json;
    std::string gcode;
    std::filesystem::path gcode_path;
    bool gcode_path_owned{false};
    std::string gcode_summary;
    bool gcode_summary_enriched{false};
    std::string slice_metrics;
    std::string last_error;
    std::string preview_range_plan;
    std::atomic<long> gcode_preview_generation{0};
    std::atomic<long> planning_generation{0};
#if defined(MOBILE_SLICER_ENABLE_VGCODE)
    libvgcode::GCodeInputData cached_preview_input;
    size_t cached_preview_source_size{0};
    std::vector<size_t> cached_preview_layer_counts;
    size_t cached_preview_layer_counts_source_size{0};
    bool cached_preview_valid{false};
    bool cached_preview_complete{false};
#endif
};

struct OrcaEngine {
    OrcaEngineImpl impl;
};

PaintVolumeBounds native_volume_bounds(const Slic3r::TriangleMesh& mesh);
void log_native_error(const char* stage, const char* message);
void log_native_info(const char* stage, const std::string& message);
void clear_last_error(OrcaEngine* engine);
void set_last_error(OrcaEngine* engine, const std::string& message);
bool ensure_gcode_loaded_unlocked(OrcaEngine* engine);
void clear_generated_gcode(OrcaEngine* engine);

#endif

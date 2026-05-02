#include "orca_wrapper_gcode.h"

#include "orca_wrapper_utils.h"

#include "libslic3r/ExtrusionEntity.hpp"
#include "libslic3r/GCode/GCodeProcessor.hpp"
#include "libslic3r/Print.hpp"

#if defined(MOBILE_SLICER_ENABLE_VGCODE)
#include "libvgcode/include/GCodeInputData.hpp"
#include "libvgcode/include/Types.hpp"
#endif

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <functional>
#include <fstream>
#include <iomanip>
#include <istream>
#include <limits>
#include <map>
#include <optional>
#include <sstream>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace mobileslicer::orca_wrapper {
namespace {

static constexpr bool kVerboseNativeTimingLogs = false;
static constexpr size_t kMaxPreviewVertices = 1000000;

static void log_native_info(const char*, const std::string&)
{
}

#define elapsed_ms_since internal_elapsed_ms_since
#define format_duration_seconds internal_format_duration_seconds
#define is_reasonable_print_time_seconds internal_is_reasonable_print_time_seconds
#define replace_summary_field internal_replace_summary_field
#define summarize_gcode_for_android internal_summarize_gcode_for_android
#define summarize_gcode_file_for_android internal_summarize_gcode_file_for_android
#define enrich_gcode_summary_from_processor internal_enrich_gcode_summary_from_processor
#define to_vgcode_input_data_from_processor_result internal_to_vgcode_input_data_from_processor_result
#define to_vgcode_input_data_from_gcode_text internal_to_vgcode_input_data_from_gcode_text
#define to_vgcode_input_data_from_generated_gcode_file internal_to_vgcode_input_data_from_generated_gcode_file
#define gcode_input_layer_count internal_gcode_input_layer_count
#define count_preview_vertices_by_layer_from_processor_result internal_count_preview_vertices_by_layer_from_processor_result
#define count_preview_vertices_by_layer_from_input_data internal_count_preview_vertices_by_layer_from_input_data
#define count_preview_vertices_in_layer_range internal_count_preview_vertices_in_layer_range
#define count_preview_vertices_by_layer_from_gcode_text internal_count_preview_vertices_by_layer_from_gcode_text
#define count_preview_vertices_by_layer_from_gcode_file internal_count_preview_vertices_by_layer_from_gcode_file
#define pack_preview_layer_ranges_from_counts internal_pack_preview_layer_ranges_from_counts
#define enrich_gcode_summary_from_preview_input internal_enrich_gcode_summary_from_preview_input

#include "orca_wrapper_gcode_summary.inc"

#if defined(MOBILE_SLICER_ENABLE_VGCODE)
#include "orca_wrapper_preview_input.inc"
#endif

#undef elapsed_ms_since
#undef format_duration_seconds
#undef is_reasonable_print_time_seconds
#undef replace_summary_field
#undef summarize_gcode_for_android
#undef summarize_gcode_file_for_android
#undef enrich_gcode_summary_from_processor
#undef to_vgcode_input_data_from_processor_result
#undef to_vgcode_input_data_from_gcode_text
#undef to_vgcode_input_data_from_generated_gcode_file
#undef gcode_input_layer_count
#undef count_preview_vertices_by_layer_from_processor_result
#undef count_preview_vertices_by_layer_from_input_data
#undef count_preview_vertices_in_layer_range
#undef count_preview_vertices_by_layer_from_gcode_text
#undef count_preview_vertices_by_layer_from_gcode_file
#undef pack_preview_layer_ranges_from_counts
#undef enrich_gcode_summary_from_preview_input

} // namespace

long elapsed_ms_since(std::chrono::steady_clock::time_point start)
{
    return internal_elapsed_ms_since(start);
}

std::string format_duration_seconds(long seconds)
{
    return internal_format_duration_seconds(seconds);
}

bool is_reasonable_print_time_seconds(double seconds)
{
    return internal_is_reasonable_print_time_seconds(seconds);
}

std::string replace_summary_field(std::string summary, std::string_view key, const std::string& value)
{
    return internal_replace_summary_field(std::move(summary), key, value);
}

std::string summarize_gcode_for_android(const std::string& gcode)
{
    return internal_summarize_gcode_for_android(gcode);
}

std::string summarize_gcode_file_for_android(const std::filesystem::path& path)
{
    return internal_summarize_gcode_file_for_android(path);
}

#if defined(MOBILE_SLICER_ENABLE_VGCODE)
std::string enrich_gcode_summary_from_processor(
    std::string summary,
    const Slic3r::GCodeProcessorResult& result,
    const Slic3r::Print* print)
{
    return internal_enrich_gcode_summary_from_processor(std::move(summary), result, print);
}

libvgcode::GCodeInputData to_vgcode_input_data_from_processor_result(
    const Slic3r::GCodeProcessorResult& result,
    size_t max_vertices,
    bool* vertex_limit_reached)
{
    return internal_to_vgcode_input_data_from_processor_result(result, max_vertices, vertex_limit_reached);
}

libvgcode::GCodeInputData to_vgcode_input_data_from_gcode_text(
    const std::string& gcode,
    long min_layer,
    long max_layer,
    size_t max_vertices,
    bool* vertex_limit_reached,
    const std::function<bool()>& should_cancel,
    size_t expected_vertices)
{
    return internal_to_vgcode_input_data_from_gcode_text(
        gcode,
        min_layer,
        max_layer,
        max_vertices,
        vertex_limit_reached,
        should_cancel,
        expected_vertices);
}

libvgcode::GCodeInputData to_vgcode_input_data_from_generated_gcode_file(
    const std::filesystem::path& path,
    long min_layer,
    long max_layer,
    size_t max_vertices,
    bool* vertex_limit_reached,
    const std::function<bool()>& should_cancel,
    size_t expected_vertices)
{
    return internal_to_vgcode_input_data_from_generated_gcode_file(
        path,
        min_layer,
        max_layer,
        max_vertices,
        vertex_limit_reached,
        should_cancel,
        expected_vertices);
}

uint32_t gcode_input_layer_count(const libvgcode::GCodeInputData& data)
{
    return internal_gcode_input_layer_count(data);
}

std::vector<size_t> count_preview_vertices_by_layer_from_input_data(const libvgcode::GCodeInputData& data)
{
    return internal_count_preview_vertices_by_layer_from_input_data(data);
}

size_t count_preview_vertices_in_layer_range(
    const std::vector<size_t>& layer_vertices,
    long min_layer,
    long max_layer,
    size_t max_vertices)
{
    return internal_count_preview_vertices_in_layer_range(layer_vertices, min_layer, max_layer, max_vertices);
}

std::vector<size_t> count_preview_vertices_by_layer_from_processor_result(const Slic3r::GCodeProcessorResult& result)
{
    return internal_count_preview_vertices_by_layer_from_processor_result(result);
}

std::vector<size_t> count_preview_vertices_by_layer_from_gcode_text(const std::string& gcode)
{
    return internal_count_preview_vertices_by_layer_from_gcode_text(gcode);
}

std::vector<size_t> count_preview_vertices_by_layer_from_gcode_file(const std::filesystem::path& path)
{
    return internal_count_preview_vertices_by_layer_from_gcode_file(path);
}

std::string pack_preview_layer_ranges_from_counts(
    const std::vector<size_t>& counts,
    long min_layer,
    long max_layer,
    size_t vertex_budget,
    std::string& error)
{
    return internal_pack_preview_layer_ranges_from_counts(counts, min_layer, max_layer, vertex_budget, error);
}

std::string enrich_gcode_summary_from_preview_input(std::string summary, const libvgcode::GCodeInputData& input)
{
    return internal_enrich_gcode_summary_from_preview_input(std::move(summary), input);
}
#endif

} // namespace mobileslicer::orca_wrapper

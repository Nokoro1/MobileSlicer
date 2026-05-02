#pragma once

#include <chrono>
#include <cstddef>
#include <cstdint>
#include <filesystem>
#include <string>
#include <string_view>
#include <vector>

#if defined(MOBILE_SLICER_ENABLE_VGCODE)
#include "libvgcode/include/GCodeInputData.hpp"
#include "libslic3r/GCode/GCodeProcessor.hpp"
#include "libslic3r/Print.hpp"
#endif

namespace mobileslicer::orca_wrapper {

inline constexpr long kMaxReasonablePrintTimeSeconds = 90L * 24L * 60L * 60L;

long elapsed_ms_since(std::chrono::steady_clock::time_point start);

std::string format_duration_seconds(long seconds);
bool is_reasonable_print_time_seconds(double seconds);
std::string replace_summary_field(std::string summary, std::string_view key, const std::string& value);
std::string summarize_gcode_for_android(const std::string& gcode);
std::string summarize_gcode_file_for_android(const std::filesystem::path& path);

#if defined(MOBILE_SLICER_ENABLE_VGCODE)
std::string enrich_gcode_summary_from_processor(
    std::string summary,
    const Slic3r::GCodeProcessorResult& result,
    const Slic3r::Print* print = nullptr);

libvgcode::GCodeInputData to_vgcode_input_data_from_processor_result(
    const Slic3r::GCodeProcessorResult& result,
    size_t max_vertices,
    bool* vertex_limit_reached = nullptr);

libvgcode::GCodeInputData to_vgcode_input_data_from_gcode_text(
    const std::string& gcode,
    long min_layer = -1,
    long max_layer = -1,
    size_t max_vertices = 1000000,
    bool* vertex_limit_reached = nullptr);

uint32_t gcode_input_layer_count(const libvgcode::GCodeInputData& data);
std::vector<size_t> count_preview_vertices_by_layer_from_processor_result(const Slic3r::GCodeProcessorResult& result);
std::vector<size_t> count_preview_vertices_by_layer_from_input_data(const libvgcode::GCodeInputData& data);
std::vector<size_t> count_preview_vertices_by_layer_from_gcode_text(const std::string& gcode);
std::vector<size_t> count_preview_vertices_by_layer_from_gcode_file(const std::filesystem::path& path);

std::string pack_preview_layer_ranges_from_counts(
    const std::vector<size_t>& counts,
    long min_layer,
    long max_layer,
    size_t vertex_budget,
    std::string& error);

std::string enrich_gcode_summary_from_preview_input(std::string summary, const libvgcode::GCodeInputData& input);
#endif

} // namespace mobileslicer::orca_wrapper

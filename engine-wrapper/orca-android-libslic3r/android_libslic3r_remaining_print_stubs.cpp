#include "libslic3r/Arachne/WallToolPaths.hpp"
#include "libslic3r/Polygon.hpp"
#include "libslic3r/Brim.hpp"
#include "libslic3r/Clipper2Utils.hpp"
#include "libslic3r/Feature/FuzzySkin/FuzzySkin.hpp"
#include "libslic3r/Feature/Interlocking/InterlockingGenerator.hpp"
#include "libslic3r/FlushVolPredictor.hpp"
#include "libslic3r/GCode/FanMover.hpp"
#include "libslic3r/GCode/ConflictChecker.hpp"
#include "libslic3r/GCode/SeamPlacer.hpp"
#include "libslic3r/GCode/Thumbnails.hpp"
#include "libslic3r/GCode/TimelapsePosPicker.hpp"
#include "libslic3r/GCode/WipeTower.hpp"
#include "libslic3r/GCode/WipeTower2.hpp"
#include "libslic3r/PerimeterGenerator.hpp"
#include "libslic3r/PNGReadWrite.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/MutablePolygon.hpp"
#include "libslic3r/ShortestPath.hpp"
#include "libslic3r/SVG.hpp"
#include "libslic3r/Fill/FillAdaptive.hpp"
#include "libslic3r/Fill/Fill3DHoneycomb.hpp"
#include "libslic3r/Fill/FillConcentric.hpp"
#include "libslic3r/Fill/FillConcentricInternal.hpp"
#include "libslic3r/Fill/FillGyroid.hpp"
#include "libslic3r/Fill/FillHoneycomb.hpp"
#include "libslic3r/Fill/FillLine.hpp"
#include "libslic3r/Fill/FillLightning.hpp"
#include "libslic3r/Fill/FillPlanePath.hpp"
#include "libslic3r/Fill/FillTpmsD.hpp"
#include "libslic3r/Fill/FillTpmsFK.hpp"
#include "libslic3r/Support/SupportSpotsGenerator.hpp"
#include "libslic3r/Support/SupportCommon.hpp"
#include "libslic3r/Support/TreeSupport.hpp"
#include "libslic3r/GCode/GCodeProcessor.hpp"
#include "libslic3r/Time.hpp"
#include "libslic3r/Tesselate.hpp"
#include "libslic3r/TriangleMeshSlicer.hpp"
#include "libslic3r/Utils.hpp"
#include "libslic3r/calib.hpp"
#include "libslic3r/miniz_extension.hpp"

#include <boost/algorithm/string/predicate.hpp>
#include <boost/format.hpp>

#include <jpeglib.h>

#include <cassert>
#include <cfloat>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <filesystem>
#include <memory>
#include <numeric>
#include <string>
#include <string_view>
#include <vector>

namespace Slic3r {

namespace {
std::string g_resources_dir = "/tmp";
}

extern "C" void orca_android_set_resources_dir(const char* path)
{
    if (path != nullptr && path[0] != '\0') {
        g_resources_dir = path;
    }
}

static constexpr size_t kMinGCodeProcessorExtruders = 5;
static constexpr float kDefaultAcceleration = 1500.0f;
static constexpr float kDefaultRetractAcceleration = 1500.0f;
static constexpr float kDefaultTravelAcceleration = 1250.0f;
static constexpr size_t kMinExtrudersCount = 5;
static constexpr float kDefaultFilamentDiameter = 1.75f;
static constexpr int kDefaultFilamentHrc = 0;
static constexpr float kDefaultFilamentDensity = 1.245f;
static constexpr float kDefaultFilamentCost = 29.99f;

static float get_option_value(const ConfigOptionFloats &option, size_t id)
{
    return id < option.size() ? static_cast<float>(option.get_at(id)) : 0.0f;
}

std::vector<std::vector<ExPolygons>> multi_material_segmentation_by_painting(
    const PrintObject &print_object,
    const std::function<void()> &throw_on_cancel_callback);

std::vector<std::vector<ExPolygons>> fuzzy_skin_segmentation_by_painting(
    const PrintObject &print_object,
    const std::function<void()> &throw_on_cancel_callback);

const std::string &resources_dir()
{
    return g_resources_dir;
}

std::string log_memory_info(bool /* ignore_loglevel */)
{
    return {};
}

bool check_layer_id_pattern(const std::string & /* pattern */, int /* layer_id */)
{
    return false;
}

void name_tbb_thread_pool_threads_set_locale() {}

Polylines Paths64_to_polylines(const Clipper2Lib::Paths64 &in)
{
    Polylines out;
    out.reserve(in.size());
    for (const Clipper2Lib::Path64 &path64 : in) {
        Points points;
        points.reserve(path64.size());
        for (const Clipper2Lib::Point64 &point64 : path64)
            points.emplace_back(point64.x, point64.y);
        out.emplace_back(std::move(points));
    }
    return out;
}

template <typename Container>
static Clipper2Lib::Paths64 slic3r_points_to_paths64(const Container &in)
{
    Clipper2Lib::Paths64 out;
    out.reserve(in.size());
    for (const auto &item : in) {
        Clipper2Lib::Path64 path;
        path.reserve(item.size());
        for (const Point &point : item.points)
            path.emplace_back(point.x(), point.y());
        out.emplace_back(std::move(path));
    }
    return out;
}

Clipper2Lib::Paths64 Slic3rPolylines_to_Paths64(const Polylines &in)
{
    return slic3r_points_to_paths64(in);
}

void SVG::draw(const ExPolygon & /* expolygon */, std::string /* fill */, const float /* fill_opacity */) {}

void SVG::draw(const ExPolygons & /* expolygons */, std::string /* fill */, const float /* fill_opacity */) {}

void SVG::draw(const Lines & /* lines */, std::string /* stroke */, coordf_t /* stroke_width */) {}

void SVG::draw(const Line & /* line */, std::string /* stroke */, coordf_t /* stroke_width */) {}

void SVG::draw(const Polyline & /* polyline */, std::string /* stroke */, coordf_t /* stroke_width */) {}

bool SVG::open(const char * /* filename */, const BoundingBox &bbox, const coord_t /* bbox_offset */, bool flip_y)
{
    origin = bbox.min;
    height = unscale<float>(bbox.max.y() - bbox.min.y()) * 10.f;
    flipY = flip_y;
    f = nullptr;
    return true;
}

void SVG::draw_outline(const ExPolygon & /* polygon */, std::string /* stroke_outer */, std::string /* stroke_holes */, coordf_t /* stroke_width */) {}

void SVG::draw_outline(const ExPolygons & /* polygons */, std::string /* stroke_outer */, std::string /* stroke_holes */, coordf_t /* stroke_width */) {}

void SVG::draw_outline(const Polygon & /* polygon */, std::string /* stroke */, coordf_t /* stroke_width */) {}

void SVG::draw_outline(const Polygons & /* polygons */, std::string /* stroke */, coordf_t /* stroke_width */) {}

void SVG::draw_text(const Point & /* pt */, const char * /* text */, const char * /* color */, int /* font_size */) {}

void SVG::draw_legend(const Point & /* pt */, const char * /* text */, const char * /* color */) {}

void SVG::Close()
{
    f = nullptr;
}

#ifndef ORCA_ANDROID_REAL_GCODE_PROCESSOR
const std::vector<std::string> GCodeProcessor::Reserved_Tags = {
    " FEATURE: ",
    " WIPE_START",
    " WIPE_END",
    " LAYER_HEIGHT: ",
    " LINE_WIDTH: ",
    " CHANGE_LAYER",
    " COLOR_CHANGE",
    " PAUSE_PRINTING",
    " CUSTOM_GCODE",
    "_GP_FIRST_LINE_M73_PLACEHOLDER",
    "_GP_LAST_LINE_M73_PLACEHOLDER",
    "_GP_ESTIMATED_PRINTING_TIME_PLACEHOLDER",
    "_GP_TOTAL_LAYER_NUMBER_PLACEHOLDER",
    " MANUAL_TOOL_CHANGE ",
    "_DURING_PRINT_EXHAUST_FAN",
    " WIPE_TOWER_START",
    " WIPE_TOWER_END",
    " PA_CHANGE:",
    "@PRINT_TIME_SEC@",
    "@USED_FILAMENT_LENGTH@"
};

const std::vector<std::string> GCodeProcessor::Reserved_Tags_compatible = {
    "TYPE:",
    "WIPE_START",
    "WIPE_END",
    "HEIGHT:",
    "WIDTH:",
    "LAYER_CHANGE",
    "COLOR_CHANGE",
    "PAUSE_PRINT",
    "CUSTOM_GCODE",
    "_GP_FIRST_LINE_M73_PLACEHOLDER",
    "_GP_LAST_LINE_M73_PLACEHOLDER",
    "_GP_ESTIMATED_PRINTING_TIME_PLACEHOLDER",
    "_GP_TOTAL_LAYER_NUMBER_PLACEHOLDER",
    " MANUAL_TOOL_CHANGE ",
    "_DURING_PRINT_EXHAUST_FAN",
    " WIPE_TOWER_START",
    " WIPE_TOWER_END",
    " PA_CHANGE:",
    "@PRINT_TIME_SEC@",
    "@USED_FILAMENT_LENGTH@"
};
bool GCodeProcessor::s_IsBBLPrinter = true;
unsigned int GCodeProcessor::s_result_id = 0;

CommandProcessor::CommandProcessor()
{
    root = std::make_unique<TrieNode>();
}

void CommandProcessor::register_command(const std::string &str, command_handler_t handler, bool early_quit)
{
    TrieNode *node = root.get();
    for (char ch : str) {
        auto iter = node->children.find(ch);
        if (iter == node->children.end()) {
            std::unique_ptr<TrieNode> new_node = std::make_unique<TrieNode>();
            auto *raw_ptr = new_node.get();
            node->children[ch] = std::move(new_node);
            node = raw_ptr;
        } else {
            node = iter->second.get();
        }
    }
    if (node->handler != nullptr)
        assert(false);
    node->handler = handler;
    node->early_quit = early_quit;
}

bool CommandProcessor::process_comand(std::string_view cmd, const GCodeReader::GCodeLine &line)
{
    TrieNode *node = root.get();
    for (char ch : cmd) {
        if (node->early_quit && node->handler) {
            node->handler(line);
            return true;
        }
        auto iter = node->children.find(ch);
        if (iter == node->children.end())
            return false;
        node = iter->second.get();
    }
    if (node == nullptr || node->handler == nullptr)
        return false;
    node->handler(line);
    return true;
}

GCodeProcessor::GCodeProcessor()
    : m_options_z_corrector(m_result)
{
    reset();
}

void GCodeProcessor::reset()
{
    m_result.reset();
    m_result.id = ++s_result_id;

    m_producer = EProducer::Unknown;
    m_units = EUnits::Millimeters;
    m_global_positioning_type = EPositioningType::Absolute;
    m_e_local_positioning_type = EPositioningType::Absolute;
    m_extruder_id = 0;
    m_extruder_offsets = std::vector<Vec3f>(1, Vec3f::Zero());
    m_flavor = gcfRepRapSprinter;
    m_origin = { 0.0f, 0.0f, 0.0f, 0.0f };
    m_start_position = m_end_position = m_origin;
    m_cached_position.reset();
    m_extrusion_role = erNone;

    m_wiping = false;
    m_flushing = false;
    m_virtual_flushing = false;
    m_wipe_tower = false;

    m_nozzle_volume.assign(MAXIMUM_EXTRUDER_NUMBER, 0.0f);
    m_remaining_volume.assign(MAXIMUM_EXTRUDER_NUMBER, 0.0f);
    m_filament_nozzle_temp.clear();
    m_filament_nozzle_temp_first_layer.clear();
    m_physical_extruder_map.clear();
    m_filament_maps.clear();
    m_last_filament_id.assign(MAXIMUM_EXTRUDER_NUMBER, static_cast<unsigned char>(-1));
    m_filament_id.assign(MAXIMUM_EXTRUDER_NUMBER, static_cast<unsigned char>(-1));
    m_extruder_colors.assign(kMinGCodeProcessorExtruders, 0);
    for (size_t i = 0; i < kMinGCodeProcessorExtruders; ++i)
        m_extruder_colors[i] = static_cast<unsigned char>(i);
    m_extruder_temps.assign(kMinGCodeProcessorExtruders, 0.0f);

    m_object_label_id = -1;
    m_print_z = 0.0f;
    m_x_offset = 0.0;
    m_y_offset = 0.0;
    m_line_id = 0;
    m_last_line_id = 0;
    m_feedrate = 0.0f;
    m_width = 0.0f;
    m_height = 0.0f;
    m_forced_width = 0.0f;
    m_forced_height = 0.0f;
    m_mm3_per_mm = 0.0f;
    m_travel_dist = 0.0f;
    m_fan_speed = 0.0f;
    m_z_offset = 0.0f;
    m_pressure_advance = 0.0f;

    m_is_XL_printer = false;
    m_highest_bed_temp = 0;
    m_extruded_last_z = 0.0f;
    m_first_layer_height = 0.0f;
    m_zero_layer_height = 0.0f;
    m_processing_start_custom_gcode = false;
    m_g1_line_id = 0;
    m_layer_id = 0;
    m_cp_color.reset();
    m_time_processor.reset();
    m_used_filaments.reset();
    m_last_default_color_id = 0;
    m_options_z_corrector.reset();
    m_detect_layer_based_on_tag = false;
    m_seams_count = 0;
    m_measure_g29_time = false;
    m_single_extruder_multi_material = false;
    m_preheat_time = 0.f;
    m_preheat_steps = 1;
    m_manual_filament_change = false;
    m_disable_m73 = false;
}

void GCodeProcessor::apply_config(const PrintConfig &config)
{
    m_parser.apply_config(config);

    m_flavor = config.gcode_flavor;
    m_single_extruder_multi_material = config.single_extruder_multi_material;

    size_t filament_count = config.filament_diameter.values.size();
    m_result.filaments_count = filament_count;

    m_is_XL_printer = is_XL_printer(config);
    m_preheat_time = config.preheat_time;
    m_preheat_steps = config.preheat_steps;
    if (m_preheat_steps < 1)
        m_preheat_steps = 1;
    m_result.backtrace_enabled = config.ooze_prevention && m_preheat_time > 0 &&
        (m_is_XL_printer || (!m_single_extruder_multi_material && filament_count > 1));

    assert(config.nozzle_volume.size() == config.nozzle_diameter.size());
    m_nozzle_volume.resize(config.nozzle_volume.size());
    for (size_t idx = 0; idx < config.nozzle_volume.size(); ++idx)
        m_nozzle_volume[idx] = config.nozzle_volume.values[idx];

    m_physical_extruder_map = config.physical_extruder_map.values;

    m_extruder_offsets.resize(filament_count);
    m_extruder_colors.resize(filament_count);
    m_result.filament_diameters.resize(filament_count);
    m_result.required_nozzle_HRC.resize(filament_count);
    m_result.filament_densities.resize(filament_count);
    m_result.filament_vitrification_temperature.resize(filament_count);
    m_result.filament_costs.resize(filament_count);
    m_extruder_temps.resize(filament_count);
    m_filament_nozzle_temp.resize(filament_count);
    m_filament_nozzle_temp_first_layer.resize(filament_count);
    m_result.nozzle_hrc = static_cast<int>(config.nozzle_hrc.getInt());
    std::vector<NozzleType>(config.nozzle_type.size()).swap(m_result.nozzle_type);
    for (size_t idx = 0; idx < m_result.nozzle_type.size(); ++idx)
        m_result.nozzle_type[idx] = NozzleType(config.nozzle_type.values[idx]);

    std::vector<int> filament_map = config.filament_map.values;
    filament_map.resize(filament_count, config.master_extruder_id.value);

    for (size_t i = 0; i < filament_count; ++i) {
        m_extruder_offsets[i] = to_3d(config.extruder_offset.get_at(filament_map[i] - 1).cast<float>().eval(), 0.f);
        m_extruder_colors[i] = static_cast<unsigned char>(i);
        m_filament_nozzle_temp_first_layer[i] = static_cast<int>(config.nozzle_temperature_initial_layer.get_at(i));
        m_filament_nozzle_temp[i] = static_cast<int>(config.nozzle_temperature.get_at(i));
        if (m_filament_nozzle_temp[i] == 0)
            m_filament_nozzle_temp[i] = m_filament_nozzle_temp_first_layer[i];
        m_result.filament_diameters[i] = static_cast<float>(config.filament_diameter.get_at(i));
        m_result.required_nozzle_HRC[i] = static_cast<int>(config.required_nozzle_HRC.get_at(i));
        m_result.filament_densities[i] = static_cast<float>(config.filament_density.get_at(i));
        m_result.filament_vitrification_temperature[i] = static_cast<float>(config.temperature_vitrification.get_at(i));
        m_result.filament_costs[i] = static_cast<float>(config.filament_cost.get_at(i));
    }

    if (m_flavor == gcfMarlinLegacy || m_flavor == gcfMarlinFirmware || m_flavor == gcfKlipper || m_flavor == gcfRepRapFirmware) {
        m_time_processor.machine_limits = reinterpret_cast<const MachineEnvelopeConfig&>(config);
        if (m_flavor == gcfMarlinLegacy || m_flavor == gcfKlipper)
            m_time_processor.machine_limits.machine_max_acceleration_travel = m_time_processor.machine_limits.machine_max_acceleration_extruding;
        if (m_flavor == gcfRepRapFirmware) {
            m_time_processor.machine_limits.machine_min_travel_rate.values.assign(m_time_processor.machine_limits.machine_min_travel_rate.size(), 0.);
            m_time_processor.machine_limits.machine_min_extruding_rate.values.assign(m_time_processor.machine_limits.machine_min_extruding_rate.size(), 0.);
        }
    }

    m_time_processor.filament_load_times = static_cast<float>(config.machine_load_filament_time.value);
    m_time_processor.filament_unload_times = static_cast<float>(config.machine_unload_filament_time.value);
    m_time_processor.machine_tool_change_time = static_cast<float>(config.machine_tool_change_time.value);

    for (size_t i = 0; i < static_cast<size_t>(PrintEstimatedStatistics::ETimeMode::Count); ++i) {
        float max_acceleration = get_option_value(m_time_processor.machine_limits.machine_max_acceleration_extruding, i);
        m_time_processor.machines[i].max_acceleration = max_acceleration;
        m_time_processor.machines[i].acceleration = (max_acceleration > 0.0f) ? max_acceleration : kDefaultAcceleration;
        float max_retract_acceleration = get_option_value(m_time_processor.machine_limits.machine_max_acceleration_retracting, i);
        m_time_processor.machines[i].max_retract_acceleration = max_retract_acceleration;
        m_time_processor.machines[i].retract_acceleration =
            (max_retract_acceleration > 0.0f) ? max_retract_acceleration : kDefaultRetractAcceleration;
        float max_travel_acceleration = get_option_value(m_time_processor.machine_limits.machine_max_acceleration_travel, i);
        if (!GCodeWriter::supports_separate_travel_acceleration(config.gcode_flavor.value))
            max_travel_acceleration = 0;
        m_time_processor.machines[i].max_travel_acceleration = max_travel_acceleration;
        m_time_processor.machines[i].travel_acceleration =
            (max_travel_acceleration > 0.0f) ? max_travel_acceleration : kDefaultTravelAcceleration;
    }

    m_disable_m73 = config.disable_m73;

    const ConfigOptionFloat *initial_layer_print_height = config.option<ConfigOptionFloat>("initial_layer_print_height");
    if (initial_layer_print_height != nullptr)
        m_first_layer_height = std::abs(initial_layer_print_height->value);

    m_result.printable_height = config.printable_height;

    auto filament_maps = config.option<ConfigOptionInts>("filament_map");
    if (filament_maps != nullptr) {
        m_filament_maps = filament_maps->values;
        std::transform(m_filament_maps.begin(), m_filament_maps.end(), m_filament_maps.begin(), [](int value) { return value - 1; });
    }

    const ConfigOptionBool *spiral_vase = config.option<ConfigOptionBool>("spiral_mode");
    if (spiral_vase != nullptr) {
        m_detect_layer_based_on_tag = spiral_vase->value;
        m_result.spiral_vase_mode = spiral_vase->value;
    }

    const ConfigOptionBool *has_scarf_joint_seam = config.option<ConfigOptionBool>("has_scarf_joint_seam");
    if (has_scarf_joint_seam != nullptr)
        m_detect_layer_based_on_tag = m_detect_layer_based_on_tag || has_scarf_joint_seam->value;

    const ConfigOptionBool *manual_filament_change = config.option<ConfigOptionBool>("manual_filament_change");
    if (manual_filament_change != nullptr)
        m_manual_filament_change = manual_filament_change->value;

    const ConfigOptionFloat *z_offset = config.option<ConfigOptionFloat>("z_offset");
    if (z_offset != nullptr)
        m_z_offset = z_offset->value;
}

void GCodeProcessor::enable_stealth_time_estimator(bool enabled)
{
    m_time_processor.machines[static_cast<size_t>(PrintEstimatedStatistics::ETimeMode::Stealth)].enabled = enabled;
}

bool GCodeProcessor::get_last_z_from_gcode(const std::string & /* gcode_str */, double & /* z */)
{
    return false;
}

bool GCodeProcessor::get_last_position_from_gcode(const std::string & /* gcode_str */, Vec3f & /* pos */)
{
    return false;
}

void GCodeProcessor::initialize(const std::string &filename)
{
    m_result.filename = filename;
    if (m_result.moves.empty())
        this->initialize_result_moves();
}

bool GCodeProcessor::check_multi_extruder_gcode_valid(
    const int /* extruder_size */,
    const Pointfs /* plate_printable_area */,
    const double /* plate_printable_height */,
    const Pointfs /* wrapping_exclude_area */,
    const std::vector<Polygons> & /* unprintable_areas */,
    const std::vector<double> & /* printable_heights */,
    const std::vector<int> &filament_map,
    const std::vector<std::set<int>> & /* unprintable_filament_types */)
{
    m_result.gcode_check_result.reset();
    m_result.limit_filament_maps.assign(filament_map.size(), 0);
    return true;
}

void GCodeProcessor::finalize(bool /* post_process */) {}

void GCodeProcessor::process_file(const std::string &filename, std::function<void()> /* cancel_callback */)
{
    this->initialize(filename);
    this->finalize(false);
}

void GCodeProcessor::process_buffer(const std::string & /* buffer */) {}

void GCodeProcessor::CachedPosition::reset()
{
    std::fill(position.begin(), position.end(), FLT_MAX);
    feedrate = FLT_MAX;
}

void GCodeProcessor::CpColor::reset()
{
    counter = 0;
    current = 0;
}

void GCodeProcessor::TimeMachine::State::reset()
{
    feedrate = 0.0f;
    safe_feedrate = 0.0f;
    axis_feedrate = { 0.0f, 0.0f, 0.0f, 0.0f };
    abs_axis_feedrate = { 0.0f, 0.0f, 0.0f, 0.0f };
    enter_direction = { 0.0f, 0.0f, 0.0f };
    exit_direction = { 0.0f, 0.0f, 0.0f };
}

void GCodeProcessor::TimeMachine::CustomGCodeTime::reset()
{
    needed = false;
    cache = 0.0f;
    times = std::vector<std::pair<CustomGCode::Type, float>>();
}

void GCodeProcessor::TimeMachine::reset()
{
    enabled = false;
    acceleration = 0.0f;
    max_acceleration = 0.0f;
    retract_acceleration = 0.0f;
    max_retract_acceleration = 0.0f;
    travel_acceleration = 0.0f;
    max_travel_acceleration = 0.0f;
    extrude_factor_override_percentage = 1.0f;
    time = 0.0f;
    stop_times = std::vector<StopTime>();
    curr.reset();
    prev.reset();
    gcode_time.reset();
    blocks = std::vector<TimeBlock>();
    g1_times_cache = std::vector<G1LinesCacheItem>();
    first_layer_time = 0.0f;
    prepare_time = 0.0f;
}

void GCodeProcessor::TimeProcessor::reset()
{
    extruder_unloaded = true;
    machine_envelope_processing_enabled = false;
    machine_limits = MachineEnvelopeConfig();
    filament_load_times = 0.0f;
    filament_unload_times = 0.0f;
    machine_tool_change_time = 0.0f;

    for (size_t i = 0; i < static_cast<size_t>(PrintEstimatedStatistics::ETimeMode::Count); ++i)
        machines[i].reset();
    machines[static_cast<size_t>(PrintEstimatedStatistics::ETimeMode::Normal)].enabled = true;
}

void GCodeProcessor::UsedFilaments::reset()
{
    color_change_cache = 0.0f;
    volumes_per_color_change = std::vector<double>();

    model_extrude_cache = 0.0f;
    model_volumes_per_filament.clear();

    flush_per_filament.clear();

    role_cache = 0.0f;
    filaments_per_role.clear();

    wipe_tower_cache = 0.0f;
    wipe_tower_volumes_per_filament.clear();

    support_volume_cache = 0.0f;
    support_volumes_per_filament.clear();

    total_volume_cache = 0.0f;
    total_volumes_per_filament.clear();
}

void GCodeProcessorResult::reset()
{
    lock();

    moves.clear();
    lines_ends.clear();
    printable_area = Pointfs();
    bed_exclude_area = Pointfs();
    wrapping_exclude_area = Pointfs();
    toolpath_outside = false;
    label_object_enabled = false;
    long_retraction_when_cut = false;
    timelapse_warning_code = 0;
    printable_height = 0.0f;
    settings_ids.reset();
    filaments_count = 0;
    backtrace_enabled = false;
    extruder_colors = std::vector<std::string>();
    filament_diameters = std::vector<float>(kMinExtrudersCount, kDefaultFilamentDiameter);
    required_nozzle_HRC = std::vector<int>(kMinExtrudersCount, kDefaultFilamentHrc);
    filament_densities = std::vector<float>(kMinExtrudersCount, kDefaultFilamentDensity);
    filament_costs = std::vector<float>(kMinExtrudersCount, kDefaultFilamentCost);
    custom_gcode_per_print_z = std::vector<CustomGCode::Item>();
    spiral_vase_mode = false;
    layer_filaments.clear();
    filament_change_count_map.clear();
    warnings.clear();

    unlock();
    BOOST_LOG_TRIVIAL(info) << __FUNCTION__ << boost::format(" %1%: this=%2% reset finished") % __LINE__ % this;
}

bool GCodeProcessor::contains_reserved_tags(const std::string &gcode, unsigned int max_count, std::vector<std::string> &found_tag)
{
    max_count = std::max(max_count, 1U);

    bool ret = false;

    CNumericLocalesSetter locales_setter;

    GCodeReader parser;
    auto &_tags = s_IsBBLPrinter ? Reserved_Tags : Reserved_Tags_compatible;
    parser.parse_buffer(gcode, [&ret, &found_tag, max_count, _tags](GCodeReader &parser, const GCodeReader::GCodeLine &line) {
        std::string comment = line.raw();
        if (comment.length() > 2 && comment.front() == ';') {
            comment = comment.substr(1);
            for (const std::string &s : _tags) {
                if (boost::starts_with(comment, s)) {
                    ret = true;
                    found_tag.push_back(comment);
                    if (found_tag.size() == max_count) {
                        parser.quit_parsing();
                        return;
                    }
                }
            }
        }
    });

    return ret;
}

int GCodeProcessor::get_gcode_last_filament(const std::string & /* gcode_str */)
{
    return -1;
}
#endif // ORCA_ANDROID_REAL_GCODE_PROCESSOR

size_t get_utf8_sequence_length(const char *seq, size_t size)
{
    if (seq == nullptr || size == 0)
        return 0;

    const unsigned char lead = static_cast<unsigned char>(seq[0]);
    if ((lead & 0x80) == 0x00)
        return 1;
    if ((lead & 0xE0) == 0xC0)
        return size >= 2 ? 2 : 1;
    if ((lead & 0xF0) == 0xE0)
        return size >= 3 ? 3 : 1;
    if ((lead & 0xF8) == 0xF0)
        return size >= 4 ? 4 : 1;
    return 1;
}

size_t get_utf8_sequence_length(const std::string &text, size_t pos)
{
    return pos < text.size() ? get_utf8_sequence_length(text.c_str() + pos, text.size() - pos) : 0;
}

std::error_code rename_file(const std::string &from, const std::string &to)
{
    std::error_code ec;
    std::filesystem::rename(from, to, ec);
    return ec;
}

namespace png {
bool write_rgb_to_file_scaled(const std::string & /* file_name_utf8 */, size_t /* width */, size_t /* height */, const std::vector<uint8_t> & /* data_rgb */, size_t /* scale */)
{
    return false;
}
} // namespace png

namespace Feature::FuzzySkin {
#ifndef ORCA_ANDROID_REAL_FUZZY_SKIN
void group_region_by_fuzzify(PerimeterGenerator & /* g */) {}

Polygon apply_fuzzy_skin(const Polygon &polygon, const PerimeterGenerator & /* perimeter_generator */, size_t /* loop_idx */, bool /* is_contour */)
{
    return polygon;
}

void apply_fuzzy_skin(Arachne::ExtrusionLine * /* extrusion */, const PerimeterGenerator & /* perimeter_generator */, bool /* is_contour */) {}
#endif
} // namespace Feature::FuzzySkin

void InterlockingGenerator::generate_interlocking_structure(PrintObject * /* print_object */, const std::function<void()> & /* throw_on_cancel */) {}

namespace SupportSpotsGenerator {
} // namespace SupportSpotsGenerator

namespace FillAdaptive {
} // namespace FillAdaptive

namespace FillLightning {
} // namespace FillLightning

#ifndef ORCA_ANDROID_REAL_CONCENTRIC_INTERNAL
void FillConcentricInternal::fill_surface_extrusion(const Surface * /* surface */,
                                                    const FillParams & /* params */,
                                                    ExtrusionEntitiesPtr & /* out */)
{
}
#endif

ConflictResultOpt ConflictChecker::find_inter_of_lines_in_diff_objs(
    PrintObjectPtrs /* objs */, std::optional<const FakeWipeTower *> /* wtdptr */)
{
    return std::nullopt;
}

void TimelapsePosPicker::init(const Print *print, const Point &plate_offset)
{
    this->print = print;
    m_plate_offset = plate_offset;
}

void TimelapsePosPicker::reset()
{
    m_all_layer_pos.reset();
    bbox_cache.clear();
}

Point TimelapsePosPicker::pick_pos(const PosPickCtx & /* ctx */)
{
    return DefaultTimelapsePos;
}

namespace GCodeThumbnails {
struct CompressedPNG : CompressedImageBuffer
{
    ~CompressedPNG() override { if (data) mz_free(data); }
    std::string_view tag() const override { return std::string_view("thumbnail"); }
};

struct CompressedJPG : CompressedImageBuffer
{
    ~CompressedJPG() override { if (data) std::free(data); }
    std::string_view tag() const override { return std::string_view("thumbnail_JPG"); }
};

struct CompressedQOI : CompressedImageBuffer
{
    ~CompressedQOI() override { if (data) std::free(data); }
    std::string_view tag() const override { return std::string_view("thumbnail_QOI"); }
};

struct CompressedColPic : CompressedImageBuffer
{
    ~CompressedColPic() override { if (data) std::free(data); }
    std::string_view tag() const override { return std::string_view("thumbnail_QIDI"); }
};

struct CompressedBIQU : CompressedImageBuffer
{
    ~CompressedBIQU() override { if (data) std::free(data); }
    std::string_view tag() const override { return std::string_view("thumbnail_BIQU"); }
};

std::pair<GCodeThumbnailDefinitionsList, ThumbnailErrors> make_and_check_thumbnail_list(const ConfigBase &config)
{
    if (const auto thumbnails_value = config.option<ConfigOptionString>("thumbnails")) {
        return make_and_check_thumbnail_list(thumbnails_value->value);
    }
    return {};
}

std::string get_hex(const unsigned int input)
{
    char buffer[16];
    std::snprintf(buffer, sizeof(buffer), "%X", input);
    return buffer;
}

std::string rjust(std::string input, unsigned int width, char fill_char)
{
    if (input.size() >= width)
        return input;
    return std::string(width - input.size(), fill_char) + input;
}

namespace {
typedef struct
{
    unsigned short color16;
    unsigned char  a0;
    unsigned char  a1;
    unsigned char  a2;
    unsigned char  reserved0;
    unsigned short reserved1;
    unsigned int   count;
} ColPicColorEntry;

typedef struct
{
    unsigned char  encode_version;
    unsigned char  reserved0;
    unsigned short once_list_count;
    unsigned int   picture_width;
    unsigned int   picture_height;
    unsigned int   marker;
    unsigned int   list_data_size;
    unsigned int   color_data_size;
    unsigned int   reserved1;
    unsigned int   reserved2;
} ColPicHeader;

void add_colpic_color(unsigned short value, ColPicColorEntry* list, int* list_count, int max_count)
{
    int count = *list_count;
    if (count >= max_count)
        return;
    for (int index = 0; index < count; ++index) {
        if (list[index].color16 == value) {
            list[index].count++;
            return;
        }
    }
    ColPicColorEntry* entry = &list[count];
    entry->color16 = value;
    entry->a0 = static_cast<unsigned char>(value >> 11);
    entry->a1 = static_cast<unsigned char>((value << 5) >> 10);
    entry->a2 = static_cast<unsigned char>((value << 11) >> 11);
    entry->reserved0 = 0;
    entry->reserved1 = 0;
    entry->count = 1;
    *list_count = count + 1;
}

int encode_colpic_pixels(unsigned short* pixels, unsigned short* color_list, int color_count, int pixel_count, unsigned char* output, int output_capacity)
{
    int pixel_index = 0;
    int output_index = 0;
    int last_segment = 0;
    while (pixel_count > 0) {
        int run = 1;
        for (int index = 0; index < pixel_count - 1; ++index) {
            if (pixels[pixel_index + index] != pixels[pixel_index + index + 1])
                break;
            run++;
            if (run == 255)
                break;
        }
        int color_index = 0;
        for (int index = 0; index < color_count; ++index) {
            if (color_list[index] == pixels[pixel_index]) {
                color_index = index;
                break;
            }
        }
        const unsigned char target = static_cast<unsigned char>(color_index % 32);
        const unsigned char segment = static_cast<unsigned char>(color_index / 32);
        if (last_segment != segment) {
            if (output_index >= output_capacity)
                return output_index;
            output[output_index++] = static_cast<unsigned char>((7 << 5) + segment);
            last_segment = segment;
        }
        if (run <= 6) {
            if (output_index >= output_capacity)
                return output_index;
            output[output_index++] = static_cast<unsigned char>((run << 5) + target);
        } else {
            if (output_index + 1 >= output_capacity)
                return output_index;
            output[output_index++] = target;
            output[output_index++] = static_cast<unsigned char>(run);
        }
        pixel_index += run;
        pixel_count -= run;
    }
    return output_index;
}

int encode_colpic(unsigned short* pixels, int width, int height, unsigned char* output, int output_capacity, int max_colors)
{
    ColPicColorEntry color_entries[1024];
    int color_count = 0;
    const int pixel_count = width * height;
    max_colors = std::min(max_colors, 1024);
    for (int index = 0; index < pixel_count; ++index) {
        add_colpic_color(pixels[index], color_entries, &color_count, 1024);
    }
    for (int index = 1; index < color_count; ++index) {
        ColPicColorEntry current = color_entries[index];
        for (int prior = 0; prior < index; ++prior) {
            if (current.count >= color_entries[prior].count) {
                std::memmove(&color_entries[prior + 1], &color_entries[prior], (index - prior) * sizeof(ColPicColorEntry));
                std::memcpy(&color_entries[prior], &current, sizeof(ColPicColorEntry));
                break;
            }
        }
    }
    while (color_count > max_colors) {
        ColPicColorEntry least_used = color_entries[color_count - 1];
        int nearest_index = 0;
        int nearest_distance = 255;
        for (int index = 0; index < max_colors; ++index) {
            int dr = std::abs(static_cast<int>(color_entries[index].a0) - static_cast<int>(least_used.a0));
            int dg = std::abs(static_cast<int>(color_entries[index].a1) - static_cast<int>(least_used.a1));
            int db = std::abs(static_cast<int>(color_entries[index].a2) - static_cast<int>(least_used.a2));
            int distance = dr + dg + db;
            if (distance < nearest_distance) {
                nearest_distance = distance;
                nearest_index = index;
            }
        }
        for (int index = 0; index < pixel_count; ++index) {
            if (pixels[index] == least_used.color16) {
                pixels[index] = color_entries[nearest_index].color16;
            }
        }
        color_count--;
    }

    if (output_capacity <= static_cast<int>(sizeof(ColPicHeader)))
        return 0;
    std::memset(output, 0, sizeof(ColPicHeader));
    auto* header = reinterpret_cast<ColPicHeader*>(output);
    header->encode_version = 3;
    header->once_list_count = 0;
    header->marker = 0x05DDC33C;
    header->list_data_size = color_count * 2;
    auto* colors = reinterpret_cast<unsigned short*>(&output[sizeof(ColPicHeader)]);
    for (int index = 0; index < color_count; ++index) {
        colors[index] = color_entries[index].color16;
    }
    const int color_data_size = encode_colpic_pixels(
        pixels,
        colors,
        header->list_data_size >> 1,
        pixel_count,
        &output[sizeof(ColPicHeader) + header->list_data_size],
        output_capacity - sizeof(ColPicHeader) - header->list_data_size
    );
    header->color_data_size = color_data_size;
    header->picture_width = width;
    header->picture_height = height;
    return sizeof(ColPicHeader) + header->list_data_size + header->color_data_size;
}

int encode_colpic_string(unsigned short* pixels, int width, int height, unsigned char* output, int output_capacity, int max_colors)
{
    int byte_count = encode_colpic(pixels, width, height, output, output_capacity, max_colors);
    if (byte_count == 0)
        return 0;
    int padding = 3 - (byte_count % 3);
    while (padding > 0) {
        output[byte_count++] = 0;
        padding--;
    }
    if ((byte_count * 4 / 3) >= output_capacity)
        return 0;
    int read_index = byte_count;
    int write_index = byte_count * 4 / 3;
    while (read_index > 0) {
        read_index -= 3;
        write_index -= 4;
        unsigned char encoded[4];
        encoded[0] = static_cast<unsigned char>(output[read_index] >> 2);
        encoded[1] = static_cast<unsigned char>((output[read_index] & 3) << 4);
        encoded[1] += static_cast<unsigned char>(output[read_index + 1] >> 4);
        encoded[2] = static_cast<unsigned char>((output[read_index + 1] & 15) << 2);
        encoded[2] += static_cast<unsigned char>(output[read_index + 2] >> 6);
        encoded[3] = static_cast<unsigned char>(output[read_index + 2] & 63);
        for (unsigned char& value : encoded) {
            value += 48;
            if (value == static_cast<unsigned char>('\\'))
                value = 126;
        }
        output[write_index] = encoded[0];
        output[write_index + 1] = encoded[1];
        output[write_index + 2] = encoded[2];
        output[write_index + 3] = encoded[3];
    }
    byte_count = byte_count * 4 / 3;
    output[byte_count] = 0;
    return byte_count;
}

std::unique_ptr<CompressedImageBuffer> compress_thumbnail_png(const ThumbnailData &data)
{
    auto out = std::make_unique<CompressedPNG>();
    out->data = tdefl_write_image_to_png_file_in_memory_ex(
        static_cast<const void*>(data.pixels.data()),
        data.width,
        data.height,
        4,
        &out->size,
        MZ_DEFAULT_LEVEL,
        1
    );
    return out;
}

std::unique_ptr<CompressedImageBuffer> compress_thumbnail_jpg(const ThumbnailData &data)
{
    if (data.width == 0 || data.height == 0 || data.pixels.size() < static_cast<size_t>(data.width) * static_cast<size_t>(data.height) * 4) {
        return {};
    }

    std::vector<unsigned char> rgba_pixels(data.pixels.size());
    const unsigned int row_size = data.width * 4;
    for (unsigned int y = 0; y < data.height; ++y) {
        std::memcpy(
            rgba_pixels.data() + static_cast<size_t>(data.height - y - 1) * row_size,
            data.pixels.data() + static_cast<size_t>(y) * row_size,
            row_size
        );
    }

    std::vector<unsigned char*> row_pointers;
    row_pointers.reserve(data.height);
    for (unsigned int y = 0; y < data.height; ++y) {
        row_pointers.emplace_back(rgba_pixels.data() + static_cast<size_t>(y) * row_size);
    }

    unsigned char* compressed_data = nullptr;
    unsigned long compressed_size = 0;

    jpeg_error_mgr error_manager;
    jpeg_compress_struct compressor;
    compressor.err = jpeg_std_error(&error_manager);
    jpeg_create_compress(&compressor);
    jpeg_mem_dest(&compressor, &compressed_data, &compressed_size);

    compressor.image_width = data.width;
    compressor.image_height = data.height;
    compressor.input_components = 4;
    compressor.in_color_space = JCS_EXT_RGBA;

    jpeg_set_defaults(&compressor);
    jpeg_set_quality(&compressor, 85, TRUE);
    jpeg_start_compress(&compressor, TRUE);
    jpeg_write_scanlines(&compressor, row_pointers.data(), data.height);
    jpeg_finish_compress(&compressor);
    jpeg_destroy_compress(&compressor);

    if (compressed_data == nullptr || compressed_size == 0) {
        std::free(compressed_data);
        return {};
    }

    auto out = std::make_unique<CompressedJPG>();
    out->data = std::malloc(static_cast<size_t>(compressed_size));
    if (out->data == nullptr) {
        std::free(compressed_data);
        return {};
    }
    out->size = static_cast<size_t>(compressed_size);
    std::memcpy(out->data, compressed_data, out->size);
    std::free(compressed_data);
    return out;
}

struct QoiPixel
{
    unsigned char r {0};
    unsigned char g {0};
    unsigned char b {0};
    unsigned char a {255};
};

int qoi_hash(const QoiPixel& pixel)
{
    return (pixel.r * 3 + pixel.g * 5 + pixel.b * 7 + pixel.a * 11) % 64;
}

void qoi_write_u32(std::vector<unsigned char>& output, unsigned int value)
{
    output.push_back(static_cast<unsigned char>((value >> 24) & 0xff));
    output.push_back(static_cast<unsigned char>((value >> 16) & 0xff));
    output.push_back(static_cast<unsigned char>((value >> 8) & 0xff));
    output.push_back(static_cast<unsigned char>(value & 0xff));
}

std::unique_ptr<CompressedImageBuffer> compress_thumbnail_qoi(const ThumbnailData &data)
{
    if (data.width == 0 || data.height == 0 || data.pixels.size() < static_cast<size_t>(data.width) * static_cast<size_t>(data.height) * 4) {
        return {};
    }

    std::vector<unsigned char> output;
    output.reserve(14 + data.pixels.size() + 8);
    output.push_back('q');
    output.push_back('o');
    output.push_back('i');
    output.push_back('f');
    qoi_write_u32(output, data.width);
    qoi_write_u32(output, data.height);
    output.push_back(4);
    output.push_back(0);

    QoiPixel index[64];
    QoiPixel previous;
    int run = 0;

    const auto flush_run = [&output, &run]() {
        if (run > 0) {
            output.push_back(static_cast<unsigned char>(0xc0 | (run - 1)));
            run = 0;
        }
    };

    for (int y = static_cast<int>(data.height) - 1; y >= 0; --y) {
        for (unsigned int x = 0; x < data.width; ++x) {
            const size_t pixel_index = (static_cast<size_t>(y) * data.width + x) * 4;
            QoiPixel pixel{
                data.pixels[pixel_index],
                data.pixels[pixel_index + 1],
                data.pixels[pixel_index + 2],
                data.pixels[pixel_index + 3],
            };
            if (pixel.r == previous.r && pixel.g == previous.g && pixel.b == previous.b && pixel.a == previous.a) {
                ++run;
                if (run == 62) {
                    flush_run();
                }
                continue;
            }

            flush_run();
            const int hash = qoi_hash(pixel);
            if (index[hash].r == pixel.r && index[hash].g == pixel.g && index[hash].b == pixel.b && index[hash].a == pixel.a) {
                output.push_back(static_cast<unsigned char>(hash));
            } else {
                index[hash] = pixel;
                if (pixel.a == previous.a) {
                    const int dr = static_cast<int>(pixel.r) - static_cast<int>(previous.r);
                    const int dg = static_cast<int>(pixel.g) - static_cast<int>(previous.g);
                    const int db = static_cast<int>(pixel.b) - static_cast<int>(previous.b);
                    const int dr_dg = dr - dg;
                    const int db_dg = db - dg;
                    if (dr >= -2 && dr <= 1 && dg >= -2 && dg <= 1 && db >= -2 && db <= 1) {
                        output.push_back(static_cast<unsigned char>(0x40 | ((dr + 2) << 4) | ((dg + 2) << 2) | (db + 2)));
                    } else if (dg >= -32 && dg <= 31 && dr_dg >= -8 && dr_dg <= 7 && db_dg >= -8 && db_dg <= 7) {
                        output.push_back(static_cast<unsigned char>(0x80 | (dg + 32)));
                        output.push_back(static_cast<unsigned char>(((dr_dg + 8) << 4) | (db_dg + 8)));
                    } else {
                        output.push_back(0xfe);
                        output.push_back(pixel.r);
                        output.push_back(pixel.g);
                        output.push_back(pixel.b);
                    }
                } else {
                    output.push_back(0xff);
                    output.push_back(pixel.r);
                    output.push_back(pixel.g);
                    output.push_back(pixel.b);
                    output.push_back(pixel.a);
                }
            }
            previous = pixel;
        }
    }
    flush_run();
    output.insert(output.end(), {0, 0, 0, 0, 0, 0, 0, 1});

    auto out = std::make_unique<CompressedQOI>();
    out->size = output.size();
    out->data = std::malloc(out->size);
    if (out->data == nullptr) {
        return {};
    }
    std::memcpy(out->data, output.data(), out->size);
    return out;
}

std::unique_ptr<CompressedImageBuffer> compress_thumbnail_colpic(const ThumbnailData &data)
{
    constexpr int max_size = 512;
    int width = static_cast<int>(data.width);
    int height = static_cast<int>(data.height);
    if (width <= 0 || height <= 0) {
        return {};
    }
    if (width > max_size || height > max_size) {
        const double aspect = static_cast<double>(width) / static_cast<double>(height);
        if (aspect > 1.0) {
            width = max_size;
            height = static_cast<int>(max_size / aspect);
        } else {
            height = max_size;
            width = static_cast<int>(max_size * aspect);
        }
    }

    std::vector<unsigned short> color16(static_cast<size_t>(width) * static_cast<size_t>(height));
    const unsigned char* pixels = data.pixels.data();
    int output_index = width * height - 1;
    for (int row = 0; row < height; ++row) {
        const int row_offset = row * width;
        for (int col = 0; col < width; ++col) {
            const int pixel_index = 4 * (row_offset + width - col - 1);
            int r = static_cast<int>(pixels[pixel_index]) >> 3;
            int g = static_cast<int>(pixels[pixel_index + 1]) >> 2;
            int b = static_cast<int>(pixels[pixel_index + 2]) >> 3;
            const int a = static_cast<int>(pixels[pixel_index + 3]);
            if (a == 0) {
                r = 46 >> 3;
                g = 51 >> 2;
                b = 72 >> 3;
            }
            color16[output_index--] = static_cast<unsigned short>((r << 11) | (g << 5) | b);
        }
    }

    std::vector<unsigned char> encoded(static_cast<size_t>(height) * static_cast<size_t>(width) * 10 + 1);
    const int encoded_size = encode_colpic_string(color16.data(), width, height, encoded.data(), static_cast<int>(encoded.size()), 1024);
    if (encoded_size <= 0) {
        return {};
    }
    auto out = std::make_unique<CompressedColPic>();
    out->size = static_cast<size_t>(encoded_size);
    out->data = std::malloc(out->size + 1);
    if (out->data == nullptr) {
        return {};
    }
    std::memcpy(out->data, encoded.data(), out->size + 1);
    return out;
}

std::unique_ptr<CompressedImageBuffer> compress_thumbnail_btt_tft(const ThumbnailData &data)
{
    if (data.width == 0 || data.height == 0 || data.pixels.size() < static_cast<size_t>(data.width) * static_cast<size_t>(data.height) * 4) {
        return {};
    }

    const unsigned int row_size = data.width * 4;
    std::string encoded;
    encoded.reserve(static_cast<size_t>(data.height) * (data.width * 4 + 3) + 1);
    for (unsigned int y = 0; y < data.height; ++y) {
        encoded.push_back(';');
        const unsigned int source_y = data.height - y - 1;
        for (unsigned int x = 0; x < data.width; ++x) {
            const size_t pixel_index = static_cast<size_t>(source_y) * row_size + static_cast<size_t>(x) * 4;
            const uint8_t alpha = data.pixels[pixel_index + 3];
            const uint8_t red = static_cast<uint8_t>((alpha * data.pixels[pixel_index]) / 255);
            const uint8_t green = static_cast<uint8_t>((alpha * data.pixels[pixel_index + 1]) / 255);
            const uint8_t blue = static_cast<uint8_t>((alpha * data.pixels[pixel_index + 2]) / 255);
            std::string color = rjust(get_hex(((red >> 3) << 11) | ((green >> 2) << 5) | (blue >> 3)), 4, '0');
            if (color == "0020" || color == "0841" || color == "0861") {
                color = "0000";
            }
            encoded += color;
        }
        encoded += "\r\n";
    }

    auto out = std::make_unique<CompressedBIQU>();
    out->size = encoded.size() + 1;
    out->data = std::malloc(out->size);
    if (out->data == nullptr) {
        return {};
    }
    std::memcpy(out->data, encoded.c_str(), out->size);
    return out;
}
} // namespace

std::unique_ptr<CompressedImageBuffer> compress_thumbnail(const ThumbnailData &data, GCodeThumbnailsFormat format)
{
    if (format == GCodeThumbnailsFormat::BTT_TFT) {
        return compress_thumbnail_btt_tft(data);
    }
    if (format == GCodeThumbnailsFormat::QOI) {
        return compress_thumbnail_qoi(data);
    }
    if (format == GCodeThumbnailsFormat::JPG) {
        return compress_thumbnail_jpg(data);
    }
    if (format == GCodeThumbnailsFormat::ColPic) {
        return compress_thumbnail_colpic(data);
    }
    return compress_thumbnail_png(data);
}
} // namespace GCodeThumbnails

const std::string &FanMover::process_gcode(const std::string & /* gcode */, bool /* flush */)
{
    m_process_output.clear();
    return m_process_output;
}

std::string debug_out_path(const char *name, ...)
{
    va_list args;
    va_start(args, name);
    char buffer[512];
    vsnprintf(buffer, sizeof(buffer), name, args);
    va_end(args);
    return std::string("/tmp/") + buffer;
}

} // namespace Slic3r

namespace FlushPredict {
float calc_color_distance(const RGBColor & /* rgb1 */, const RGBColor & /* rgb2 */)
{
    return 0.f;
}
} // namespace FlushPredict

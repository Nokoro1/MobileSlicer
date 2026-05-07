#ifndef ORCA_WRAPPER_PREVIEW_HELPERS_H
#define ORCA_WRAPPER_PREVIEW_HELPERS_H

// Private implementation header. Include through orca_wrapper_module_context.h.
#if defined(MOBILE_SLICER_ENABLE_VGCODE)
} // namespace

struct OrcaGcodeViewer {
    libvgcode::Viewer viewer;
    libvgcode::GCodeInputData input_data;
    std::string last_error;
    std::string last_load_metrics;
    bool initialized{false};
};

namespace {

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

struct GcodeProcessorBufferReleaseStats {
    size_t move_bytes_before{0};
    size_t line_end_bytes_before{0};
    size_t move_bytes_retained{0};
    size_t line_end_bytes_retained{0};
    long release_ms{0};
};

static GcodeProcessorBufferReleaseStats release_gcode_processor_buffers(
    Slic3r::GCodeProcessorResult& result)
{
    const auto release_start = std::chrono::steady_clock::now();
    GcodeProcessorBufferReleaseStats stats;
    stats.move_bytes_before = result.moves.size() * sizeof(Slic3r::GCodeProcessorResult::MoveVertex);
    stats.line_end_bytes_before = result.lines_ends.size() * sizeof(size_t);

    std::vector<Slic3r::GCodeProcessorResult::MoveVertex>().swap(result.moves);
    std::vector<size_t>().swap(result.lines_ends);

    stats.move_bytes_retained = result.moves.capacity() * sizeof(Slic3r::GCodeProcessorResult::MoveVertex);
    stats.line_end_bytes_retained = result.lines_ends.capacity() * sizeof(size_t);
    stats.release_ms = elapsed_ms_since(release_start);
    return stats;
}

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

static void apply_preview_option_visibility_for_view_type(
    libvgcode::Viewer& viewer,
    libvgcode::EViewType view_type)
{
    const bool seams_visible = view_type == libvgcode::EViewType::FeatureType;
    if (viewer.is_option_visible(libvgcode::EOptionType::Seams) != seams_visible) {
        viewer.toggle_option_visibility(libvgcode::EOptionType::Seams);
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

#endif // ORCA_WRAPPER_PREVIEW_HELPERS_H

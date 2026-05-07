#ifndef ORCA_WRAPPER_WIPE_TOWER_HELPERS_H
#define ORCA_WRAPPER_WIPE_TOWER_HELPERS_H

// Private implementation header. Include through orca_wrapper_config_helpers.h after G-code IO helpers.
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

static double estimate_wipe_tower_depth(const Slic3r::DynamicPrintConfig& config, double tower_width)
{
    // Match Orca's PartPlate::estimate_wipe_tower_size rectangle path. The
    // tower preview/keepout is driven by prime_volume, not the full flush
    // matrix. Using flush_volumes_matrix here made low-layer-height painted
    // multicolor jobs produce bed-sized fake tower depths.
    const double layer_height = std::max(
        0.05,
        config_first_float(config, "layer_height")
            .value_or(config_first_float(config, "initial_layer_print_height").value_or(0.2))
    );
    const int material_slots = std::max(
        1,
        static_cast<int>(std::lround(config_first_float(config, "mobile_slicer_active_filament_slot_count").value_or(1.0)))
    );
    const int extruder_count = std::max(
        1,
        static_cast<int>(std::lround(config_first_float(config, "mobile_slicer_physical_nozzle_count").value_or(1.0)))
    );
    const double prime_volume = config_first_float(config, "prime_volume").value_or(45.0);
    const double infill_spacing = std::max(0.05, config_first_float(config, "prime_tower_infill_gap").value_or(100.0) / 100.0);
    const double volume = prime_volume * static_cast<double>(
        extruder_count == 2 ? material_slots : std::max(0, material_slots - 1)
    );
    const double depth = volume * infill_spacing / std::max(1.0, layer_height * tower_width);
    return std::max(0.0, depth);
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

} // namespace

#endif // ORCA_WRAPPER_WIPE_TOWER_HELPERS_H

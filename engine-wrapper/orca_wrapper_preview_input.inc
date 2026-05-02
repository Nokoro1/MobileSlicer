static std::string summary_escape(std::string_view value)
{
    std::ostringstream encoded;
    encoded << std::uppercase << std::hex;
    for (const unsigned char ch : value) {
        if ((ch >= 'A' && ch <= 'Z') ||
            (ch >= 'a' && ch <= 'z') ||
            (ch >= '0' && ch <= '9') ||
            ch == ' ' || ch == '-' || ch == '_' || ch == '.' || ch == '#') {
            encoded << static_cast<char>(ch);
        } else {
            encoded << '%' << std::setw(2) << std::setfill('0') << static_cast<int>(ch) << std::setfill(' ');
        }
    }
    return encoded.str();
}

static const char* hex_for_rgb(uint8_t r, uint8_t g, uint8_t b)
{
    static thread_local std::array<std::string, 8> cache;
    static thread_local size_t next = 0;
    std::ostringstream color;
    color << '#'
          << std::uppercase << std::hex << std::setfill('0')
          << std::setw(2) << static_cast<int>(r)
          << std::setw(2) << static_cast<int>(g)
          << std::setw(2) << static_cast<int>(b);
    cache[next] = color.str();
    const char* result = cache[next].c_str();
    next = (next + 1) % cache.size();
    return result;
}

static const char* color_for_libvgcode_role(libvgcode::EGCodeExtrusionRole role)
{
    switch (role) {
        case libvgcode::EGCodeExtrusionRole::Perimeter: return "#FFE64D";
        case libvgcode::EGCodeExtrusionRole::ExternalPerimeter: return "#FF7D38";
        case libvgcode::EGCodeExtrusionRole::OverhangPerimeter: return "#1F1FFF";
        case libvgcode::EGCodeExtrusionRole::InternalInfill: return "#B03029";
        case libvgcode::EGCodeExtrusionRole::SolidInfill: return "#9654CC";
        case libvgcode::EGCodeExtrusionRole::TopSolidInfill: return "#F04040";
        case libvgcode::EGCodeExtrusionRole::Ironing: return "#FF8C69";
        case libvgcode::EGCodeExtrusionRole::BridgeInfill: return "#4D80BA";
        case libvgcode::EGCodeExtrusionRole::GapFill: return "#FFFFFF";
        case libvgcode::EGCodeExtrusionRole::Skirt: return "#00876E";
        case libvgcode::EGCodeExtrusionRole::SupportMaterial: return "#00FF00";
        case libvgcode::EGCodeExtrusionRole::SupportMaterialInterface: return "#008000";
        case libvgcode::EGCodeExtrusionRole::WipeTower: return "#B3E3AB";
        case libvgcode::EGCodeExtrusionRole::Custom: return "#5ED194";
        case libvgcode::EGCodeExtrusionRole::BottomSurface: return "#665CC7";
        case libvgcode::EGCodeExtrusionRole::InternalBridgeInfill: return "#4D80BA";
        case libvgcode::EGCodeExtrusionRole::Brim: return "#003B6E";
        case libvgcode::EGCodeExtrusionRole::SupportTransition: return "#004000";
        case libvgcode::EGCodeExtrusionRole::Mixed: return "#808080";
        case libvgcode::EGCodeExtrusionRole::None:
        case libvgcode::EGCodeExtrusionRole::COUNT:
        default: return "#E6B3B3";
    }
}

static const char* color_for_libvgcode_option(libvgcode::EOptionType option)
{
    switch (option) {
        case libvgcode::EOptionType::Travels: return "#38489B";
        case libvgcode::EOptionType::Wipes: return "#FFFF00";
        case libvgcode::EOptionType::Retractions: return "#CD22D6";
        case libvgcode::EOptionType::Unretractions: return "#49ADCF";
        case libvgcode::EOptionType::Seams: return "#E6E6E6";
        case libvgcode::EOptionType::ToolChanges: return "#C1BE63";
        case libvgcode::EOptionType::ColorChanges: return "#DA948B";
        case libvgcode::EOptionType::PausePrints: return "#52F083";
        case libvgcode::EOptionType::CustomGCodes: return "#E2D243";
        case libvgcode::EOptionType::COUNT:
        default: return "#808080";
    }
}

static libvgcode::EGCodeExtrusionRole libvgcode_role_from_slic3r_role(Slic3r::ExtrusionRole role)
{
    switch (role) {
        case Slic3r::erPerimeter: return libvgcode::EGCodeExtrusionRole::Perimeter;
        case Slic3r::erExternalPerimeter: return libvgcode::EGCodeExtrusionRole::ExternalPerimeter;
        case Slic3r::erOverhangPerimeter: return libvgcode::EGCodeExtrusionRole::OverhangPerimeter;
        case Slic3r::erInternalInfill: return libvgcode::EGCodeExtrusionRole::InternalInfill;
        case Slic3r::erSolidInfill: return libvgcode::EGCodeExtrusionRole::SolidInfill;
        case Slic3r::erTopSolidInfill: return libvgcode::EGCodeExtrusionRole::TopSolidInfill;
        case Slic3r::erBottomSurface: return libvgcode::EGCodeExtrusionRole::BottomSurface;
        case Slic3r::erIroning: return libvgcode::EGCodeExtrusionRole::Ironing;
        case Slic3r::erBridgeInfill: return libvgcode::EGCodeExtrusionRole::BridgeInfill;
        case Slic3r::erInternalBridgeInfill: return libvgcode::EGCodeExtrusionRole::InternalBridgeInfill;
        case Slic3r::erGapFill: return libvgcode::EGCodeExtrusionRole::GapFill;
        case Slic3r::erSkirt: return libvgcode::EGCodeExtrusionRole::Skirt;
        case Slic3r::erBrim: return libvgcode::EGCodeExtrusionRole::Brim;
        case Slic3r::erSupportMaterial: return libvgcode::EGCodeExtrusionRole::SupportMaterial;
        case Slic3r::erSupportMaterialInterface: return libvgcode::EGCodeExtrusionRole::SupportMaterialInterface;
        case Slic3r::erSupportTransition: return libvgcode::EGCodeExtrusionRole::SupportTransition;
        case Slic3r::erWipeTower: return libvgcode::EGCodeExtrusionRole::WipeTower;
        case Slic3r::erCustom: return libvgcode::EGCodeExtrusionRole::Custom;
        case Slic3r::erMixed: return libvgcode::EGCodeExtrusionRole::Mixed;
        case Slic3r::erNone:
        case Slic3r::erCount:
        default: return libvgcode::EGCodeExtrusionRole::None;
    }
}

static libvgcode::EOptionType libvgcode_option_from_move_type(Slic3r::EMoveType type)
{
    switch (type) {
        case Slic3r::EMoveType::Travel: return libvgcode::EOptionType::Travels;
        case Slic3r::EMoveType::Wipe: return libvgcode::EOptionType::Wipes;
        case Slic3r::EMoveType::Retract: return libvgcode::EOptionType::Retractions;
        case Slic3r::EMoveType::Unretract: return libvgcode::EOptionType::Unretractions;
        case Slic3r::EMoveType::Seam: return libvgcode::EOptionType::Seams;
        case Slic3r::EMoveType::Tool_change: return libvgcode::EOptionType::ToolChanges;
        case Slic3r::EMoveType::Color_change: return libvgcode::EOptionType::ColorChanges;
        case Slic3r::EMoveType::Pause_Print: return libvgcode::EOptionType::PausePrints;
        case Slic3r::EMoveType::Custom_GCode: return libvgcode::EOptionType::CustomGCodes;
        default: return libvgcode::EOptionType::COUNT;
    }
}

static libvgcode::EMoveType libvgcode_move_type_from_slic3r_move_type(Slic3r::EMoveType type)
{
    switch (type) {
        case Slic3r::EMoveType::Noop: return libvgcode::EMoveType::Noop;
        case Slic3r::EMoveType::Retract: return libvgcode::EMoveType::Retract;
        case Slic3r::EMoveType::Unretract: return libvgcode::EMoveType::Unretract;
        case Slic3r::EMoveType::Seam: return libvgcode::EMoveType::Seam;
        case Slic3r::EMoveType::Tool_change: return libvgcode::EMoveType::ToolChange;
        case Slic3r::EMoveType::Color_change: return libvgcode::EMoveType::ColorChange;
        case Slic3r::EMoveType::Pause_Print: return libvgcode::EMoveType::PausePrint;
        case Slic3r::EMoveType::Custom_GCode: return libvgcode::EMoveType::CustomGCode;
        case Slic3r::EMoveType::Travel: return libvgcode::EMoveType::Travel;
        case Slic3r::EMoveType::Wipe: return libvgcode::EMoveType::Wipe;
        case Slic3r::EMoveType::Extrude: return libvgcode::EMoveType::Extrude;
        default: return libvgcode::EMoveType::COUNT;
    }
}

static std::string label_for_option(libvgcode::EOptionType option)
{
    switch (option) {
        case libvgcode::EOptionType::Travels: return "Travel";
        case libvgcode::EOptionType::Wipes: return "Wipe";
        case libvgcode::EOptionType::Retractions: return "Retract";
        case libvgcode::EOptionType::Unretractions: return "Unretract";
        case libvgcode::EOptionType::Seams: return "Seams";
        case libvgcode::EOptionType::ToolChanges: return "Filament changes";
        case libvgcode::EOptionType::ColorChanges: return "Color changes";
        case libvgcode::EOptionType::PausePrints: return "Pause prints";
        case libvgcode::EOptionType::CustomGCodes: return "Custom G-code";
        default: return "Option";
    }
}

static double filament_length_m_from_volume_mm3(double volume_mm3, double diameter_mm)
{
    const double radius_mm = diameter_mm > 0.0 ? diameter_mm * 0.5 : 0.875;
    const double area_mm2 = M_PI * radius_mm * radius_mm;
    return area_mm2 > 0.0 ? (volume_mm3 / area_mm2) / 1000.0 : 0.0;
}

static double filament_grams_from_volume_mm3(double volume_mm3, double density_g_per_cm3)
{
    const double density = density_g_per_cm3 > 0.0 ? density_g_per_cm3 : 1.24;
    return volume_mm3 * density / 1000.0;
}

static double map_value_or_zero(const std::map<size_t, double>& values, size_t key)
{
    const auto it = values.find(key);
    return it == values.end() ? 0.0 : it->second;
}

static std::string enrich_gcode_summary_from_processor(
    std::string summary,
    const Slic3r::GCodeProcessorResult& result,
    const Slic3r::Print* print = nullptr)
{
    using NormalMode = Slic3r::PrintEstimatedStatistics::ETimeMode;
    const size_t normal_index = static_cast<size_t>(NormalMode::Normal);
    const double total_time = result.print_statistics.modes[normal_index].time;
    const double prepare_time = result.print_statistics.modes[normal_index].prepare_time;
    std::map<Slic3r::ExtrusionRole, double> role_times;
    std::map<libvgcode::EOptionType, double> option_times;
    if (result.move_time_summary_valid) {
        role_times = result.move_role_times;
        for (const auto& [move_type, seconds] : result.move_type_times) {
            const auto option = libvgcode_option_from_move_type(move_type);
            if (option != libvgcode::EOptionType::COUNT) {
                option_times[option] += seconds;
            }
        }
    } else {
        for (const auto& move : result.moves) {
            const double seconds = move.time[normal_index];
            if (seconds <= 0.0) {
                continue;
            }
            if (move.type == Slic3r::EMoveType::Extrude) {
                role_times[move.extrusion_role] += seconds;
            } else {
                const auto option = libvgcode_option_from_move_type(move.type);
                if (option != libvgcode::EOptionType::COUNT) {
                    option_times[option] += seconds;
                }
            }
        }
    }

    const std::vector<Slic3r::ExtrusionRole> role_order = {
        Slic3r::erPerimeter,
        Slic3r::erExternalPerimeter,
        Slic3r::erOverhangPerimeter,
        Slic3r::erInternalInfill,
        Slic3r::erSolidInfill,
        Slic3r::erTopSolidInfill,
        Slic3r::erBridgeInfill,
        Slic3r::erGapFill,
        Slic3r::erCustom,
        Slic3r::erBottomSurface,
        Slic3r::erInternalBridgeInfill,
        Slic3r::erBrim,
        Slic3r::erSkirt,
        Slic3r::erSupportMaterial,
        Slic3r::erSupportMaterialInterface,
        Slic3r::erWipeTower,
        Slic3r::erIroning
    };
    std::ostringstream line_types;
    line_types << std::fixed << std::setprecision(5);
    bool first_row = true;
    auto append_row = [&](const char* kind, int id, const std::string& label, const char* color, double seconds, double meters, double grams, bool visible, bool force_row) {
        if (!force_row && seconds <= 0.001 && meters <= 0.00001 && grams <= 0.00001) {
            return;
        }
        if (!first_row) {
            line_types << ';';
        }
        first_row = false;
        const double percent = total_time > 0.0 ? (seconds / total_time) * 100.0 : 0.0;
        line_types << kind << ','
                   << id << ','
                   << summary_escape(label) << ','
                   << color << ','
                   << seconds << ','
                   << percent << ','
                   << meters << ','
                   << grams << ','
                   << (visible ? 1 : 0);
    };
    for (const auto role : role_order) {
        const auto usage_it = result.print_statistics.used_filaments_per_role.find(role);
        const double meters = usage_it == result.print_statistics.used_filaments_per_role.end() ? 0.0 : usage_it->second.first;
        const double grams = usage_it == result.print_statistics.used_filaments_per_role.end() ? 0.0 : usage_it->second.second;
        const auto vg_role = libvgcode_role_from_slic3r_role(role);
        append_row(
            "role",
            static_cast<int>(vg_role),
            Slic3r::ExtrusionEntity::role_to_string(role),
            color_for_libvgcode_role(vg_role),
            role_times[role],
            meters,
            grams,
            true,
            false);
    }
    const std::vector<libvgcode::EOptionType> option_order = {
        libvgcode::EOptionType::Travels,
        libvgcode::EOptionType::Wipes,
        libvgcode::EOptionType::Retractions,
        libvgcode::EOptionType::Unretractions,
        libvgcode::EOptionType::Seams,
        libvgcode::EOptionType::ToolChanges,
        libvgcode::EOptionType::ColorChanges
    };
    for (const auto option : option_order) {
        append_row(
            "option",
            static_cast<int>(option),
            label_for_option(option),
            color_for_libvgcode_option(option),
            option_times[option],
            0.0,
            0.0,
            option == libvgcode::EOptionType::Seams,
            true);
    }

    std::ostringstream filaments;
    filaments << std::fixed << std::setprecision(5);
    const Slic3r::WipeTowerData* wipe_tower_data = print != nullptr ? &print->wipe_tower_data() : nullptr;
    double total_cost = 0.0;
    bool has_any_cost = false;
    size_t filament_count = std::max(result.filaments_count, result.filament_diameters.size());
    auto widen_filament_count = [&filament_count](const std::map<size_t, double>& values) {
        if (!values.empty()) {
            filament_count = std::max(filament_count, values.rbegin()->first + 1);
        }
    };
    widen_filament_count(result.print_statistics.model_volumes_per_extruder);
    widen_filament_count(result.print_statistics.support_volumes_per_extruder);
    widen_filament_count(result.print_statistics.flush_per_filament);
    widen_filament_count(result.print_statistics.wipe_tower_volumes_per_extruder);
    widen_filament_count(result.print_statistics.total_volumes_per_extruder);
    if (wipe_tower_data != nullptr) {
        filament_count = std::max(filament_count, wipe_tower_data->used_filament.size());
    }
    bool first_filament = true;
    for (size_t index = 0; index < filament_count; ++index) {
        const double diameter = index < result.filament_diameters.size() ? result.filament_diameters[index] : 1.75;
        const double density = index < result.filament_densities.size() ? result.filament_densities[index] : 1.24;
        const double model_volume = map_value_or_zero(result.print_statistics.model_volumes_per_extruder, index);
        const double support_volume = map_value_or_zero(result.print_statistics.support_volumes_per_extruder, index);
        const double flushed_volume = map_value_or_zero(result.print_statistics.flush_per_filament, index);
        double tower_volume = map_value_or_zero(result.print_statistics.wipe_tower_volumes_per_extruder, index);
        if (tower_volume <= 0.0 && wipe_tower_data != nullptr && index < wipe_tower_data->used_filament.size()) {
            const double tower_length_mm = std::max(0.0f, wipe_tower_data->used_filament[index]);
            const double radius_mm = diameter > 0.0 ? diameter * 0.5 : 0.875;
            tower_volume = tower_length_mm * M_PI * radius_mm * radius_mm;
        }
        const double mapped_total_volume = map_value_or_zero(result.print_statistics.total_volumes_per_extruder, index);
        const double component_total_volume = model_volume + support_volume + flushed_volume + tower_volume;
        const double total_volume = std::max(mapped_total_volume, component_total_volume);
        if (model_volume <= 0.0 && support_volume <= 0.0 && flushed_volume <= 0.0 && tower_volume <= 0.0 && total_volume <= 0.0) {
            continue;
        }
        const auto meters = [&](double volume) { return filament_length_m_from_volume_mm3(volume, diameter); };
        const auto grams = [&](double volume) { return filament_grams_from_volume_mm3(volume, density); };
        const double total_grams = grams(total_volume);
        const double cost_per_kg = index < result.filament_costs.size() ? result.filament_costs[index] : 0.0;
        const double cost = cost_per_kg > 0.0 ? total_grams * cost_per_kg / 1000.0 : 0.0;
        if (cost > 0.0) {
            total_cost += cost;
            has_any_cost = true;
        }
        std::string color = "#8FC1FF";
        if (index < result.extruder_colors.size() && !result.extruder_colors[index].empty()) {
            color = result.extruder_colors[index];
        }
        std::string label = "Filament " + std::to_string(index + 1);
        if (index < result.settings_ids.filament.size() && !result.settings_ids.filament[index].empty()) {
            label = result.settings_ids.filament[index];
        }
        if (!first_filament) {
            filaments << ';';
        }
        first_filament = false;
        filaments << (index + 1) << ','
                  << summary_escape(label) << ','
                  << summary_escape(color) << ','
                  << meters(model_volume) << ',' << grams(model_volume) << ','
                  << meters(support_volume) << ',' << grams(support_volume) << ','
                  << meters(flushed_volume) << ',' << grams(flushed_volume) << ','
                  << meters(tower_volume) << ',' << grams(tower_volume) << ','
                  << meters(total_volume) << ',' << total_grams << ','
                  << cost;
    }

    std::ostringstream totals;
    const unsigned int print_toolchanges = print != nullptr ?
        static_cast<unsigned int>(std::max(0, print->print_statistics().total_toolchanges)) :
        0;
    const unsigned int filament_changes = std::max(result.print_statistics.total_filament_changes, print_toolchanges);
    const unsigned int extruder_changes = std::max(result.print_statistics.total_extruder_changes, print_toolchanges);

    totals << std::fixed << std::setprecision(5)
           << "totalSeconds=" << total_time
           << ",prepareSeconds=" << prepare_time
           << ",modelSeconds=" << std::max(0.0, total_time - prepare_time)
           << ",cost=" << (has_any_cost ? total_cost : 0.0)
           << ",filamentChanges=" << filament_changes
           << ",extruderChanges=" << extruder_changes;

    summary = replace_summary_field(summary, "previewLineTypes", line_types.str());
    summary = replace_summary_field(summary, "previewFilaments", filaments.str());
    summary = replace_summary_field(summary, "previewTotals", totals.str());
    return summary;
}

static libvgcode::Vec3 libvgcode_vec3_from_slic3r_vec3(const Slic3r::Vec3f& value)
{
    return {
        static_cast<float>(value.x()),
        static_cast<float>(value.y()),
        static_cast<float>(value.z())
    };
}

static libvgcode::PathVertex processor_move_to_path_vertex(
    const Slic3r::GCodeProcessorResult::MoveVertex& move,
    const libvgcode::Vec3& position,
    std::array<float, static_cast<size_t>(Slic3r::PrintEstimatedStatistics::ETimeMode::Count)> times)
{
    libvgcode::PathVertex vertex;
    vertex.position = position;
    vertex.height = move.height;
    vertex.width = move.width;
    vertex.feedrate = move.feedrate;
    vertex.actual_feedrate = move.actual_feedrate;
    vertex.mm3_per_mm = move.mm3_per_mm;
    vertex.fan_speed = move.fan_speed;
    vertex.temperature = move.temperature;
    vertex.role = libvgcode_role_from_slic3r_role(move.extrusion_role);
    vertex.type = libvgcode_move_type_from_slic3r_move_type(move.type);
    vertex.gcode_id = static_cast<uint32_t>(move.gcode_id);
    vertex.layer_id = static_cast<uint32_t>(move.layer_id);
    vertex.extruder_id = static_cast<uint8_t>(move.extruder_id);
    vertex.color_id = static_cast<uint8_t>(move.cp_color_id);
    vertex.times = times;
    vertex.layer_duration = move.layer_duration;
    vertex.pressure_advance = move.pressure_advance;
    return vertex;
}

static void rebuild_gcode_layer_vertex_ranges(libvgcode::GCodeInputData& data)
{
    data.layer_vertex_ranges.clear();
    if (data.vertices.empty()) {
        return;
    }

    uint32_t previous_layer = data.vertices.front().layer_id;
    data.layer_vertex_ranges.resize(static_cast<size_t>(previous_layer) + 1);
    data.layer_vertex_ranges[static_cast<size_t>(previous_layer)].begin = 0;

    for (size_t index = 1; index < data.vertices.size(); ++index) {
        const uint32_t layer_id = data.vertices[index].layer_id;
        if (layer_id < previous_layer) {
            data.layer_vertex_ranges.clear();
            return;
        }
        if (layer_id != previous_layer) {
            data.layer_vertex_ranges[static_cast<size_t>(previous_layer)].end = index;
            if (data.layer_vertex_ranges.size() <= layer_id) {
                data.layer_vertex_ranges.resize(static_cast<size_t>(layer_id) + 1);
            }
            data.layer_vertex_ranges[static_cast<size_t>(layer_id)].begin = index;
            previous_layer = layer_id;
        }
    }

    data.layer_vertex_ranges[static_cast<size_t>(previous_layer)].end = data.vertices.size();
}

static libvgcode::GCodeInputData to_vgcode_input_data_from_processor_result(
    const Slic3r::GCodeProcessorResult& result,
    size_t max_vertices,
    bool* vertex_limit_reached = nullptr)
{
    if (vertex_limit_reached != nullptr) {
        *vertex_limit_reached = false;
    }
    libvgcode::GCodeInputData data;
    const auto& moves = result.moves;
    if (moves.size() < 2) {
        return data;
    }
    const size_t reserved_vertices = std::min(max_vertices, moves.size() * static_cast<size_t>(2));
    data.vertices.reserve(reserved_vertices);
    for (size_t i = 1; i < moves.size(); ++i) {
        if (max_vertices > 0 && data.vertices.size() >= max_vertices) {
            if (vertex_limit_reached != nullptr) {
                *vertex_limit_reached = true;
            }
            break;
        }
        const auto& current = moves[i];
        const auto& previous = moves[i - 1];
        const auto current_type = libvgcode_move_type_from_slic3r_move_type(current.type);
        if (current_type == libvgcode::EMoveType::COUNT) {
            continue;
        }
        const auto option_type = libvgcode::move_type_to_option(current_type);
        if (
            option_type == libvgcode::EOptionType::COUNT ||
            option_type == libvgcode::EOptionType::Travels ||
            option_type == libvgcode::EOptionType::Wipes
        ) {
            if (
                data.vertices.empty() ||
                previous.type != current.type ||
                previous.extrusion_role != current.extrusion_role ||
                previous.mm3_per_mm != current.mm3_per_mm
            ) {
                const auto zero_times = std::array<float, static_cast<size_t>(Slic3r::PrintEstimatedStatistics::ETimeMode::Count)>{ 0.0f, 0.0f };
                data.vertices.push_back(processor_move_to_path_vertex(
                    current,
                    libvgcode_vec3_from_slic3r_vec3(previous.position),
                    zero_times));
                if (max_vertices > 0 && data.vertices.size() >= max_vertices) {
                    if (vertex_limit_reached != nullptr) {
                        *vertex_limit_reached = true;
                    }
                    break;
                }
            }
        }
        data.vertices.push_back(processor_move_to_path_vertex(
            current,
            libvgcode_vec3_from_slic3r_vec3(current.position),
            current.time));
    }
    data.spiral_vase_mode = result.spiral_vase_mode;
    data.vertices.shrink_to_fit();
    rebuild_gcode_layer_vertex_ranges(data);
    return data;
}

static std::vector<size_t> count_preview_vertices_by_layer_from_processor_result(
    const Slic3r::GCodeProcessorResult& result)
{
    const auto& moves = result.moves;
    if (moves.size() < 2) {
        return {};
    }
    std::vector<size_t> layer_vertices;
    auto add_vertex = [&layer_vertices](uint32_t layer_id) {
        if (layer_vertices.size() <= layer_id) {
            layer_vertices.resize(static_cast<size_t>(layer_id) + 1, 0);
        }
        ++layer_vertices[static_cast<size_t>(layer_id)];
    };
    for (size_t i = 1; i < moves.size(); ++i) {
        const auto& current = moves[i];
        const auto& previous = moves[i - 1];
        const auto current_type = libvgcode_move_type_from_slic3r_move_type(current.type);
        if (current_type == libvgcode::EMoveType::COUNT) {
            continue;
        }
        const auto option_type = libvgcode::move_type_to_option(current_type);
        if (
            option_type == libvgcode::EOptionType::COUNT ||
            option_type == libvgcode::EOptionType::Travels ||
            option_type == libvgcode::EOptionType::Wipes
        ) {
            if (
                layer_vertices.empty() ||
                previous.type != current.type ||
                previous.extrusion_role != current.extrusion_role ||
                previous.mm3_per_mm != current.mm3_per_mm
            ) {
                add_vertex(static_cast<uint32_t>(current.layer_id));
            }
        }
        add_vertex(static_cast<uint32_t>(current.layer_id));
    }
    return layer_vertices;
}

static bool starts_with_text(std::string_view value, const char* prefix)
{
    return value.rfind(prefix, 0) == 0;
}

static bool parse_float_after_prefix(std::string_view value, const char* prefix, float& out)
{
    if (!starts_with_text(value, prefix)) {
        return false;
    }
    const size_t prefix_size = std::char_traits<char>::length(prefix);
    const std::string tail(value.substr(prefix_size));
    char* end = nullptr;
    const float parsed = std::strtof(tail.c_str(), &end);
    if (end == tail.c_str()) {
        return false;
    }
    out = parsed;
    return true;
}

static libvgcode::EGCodeExtrusionRole role_from_type_marker(std::string_view marker)
{
    marker = trim_view(marker);
    std::string normalized(marker);
    std::transform(normalized.begin(), normalized.end(), normalized.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });

    if (normalized == "skirt") return libvgcode::EGCodeExtrusionRole::Skirt;
    if (normalized == "brim") return libvgcode::EGCodeExtrusionRole::Brim;
    if (normalized == "inner wall" || normalized == "internal perimeter") return libvgcode::EGCodeExtrusionRole::Perimeter;
    if (normalized == "outer wall" || normalized == "external perimeter") return libvgcode::EGCodeExtrusionRole::ExternalPerimeter;
    if (normalized == "overhang wall" || normalized == "overhang perimeter") return libvgcode::EGCodeExtrusionRole::OverhangPerimeter;
    if (normalized == "sparse infill" || normalized == "internal infill") return libvgcode::EGCodeExtrusionRole::InternalInfill;
    if (normalized == "internal solid infill" || normalized == "solid infill") return libvgcode::EGCodeExtrusionRole::SolidInfill;
    if (normalized == "top surface" || normalized == "top solid infill") return libvgcode::EGCodeExtrusionRole::TopSolidInfill;
    if (normalized == "bottom surface") return libvgcode::EGCodeExtrusionRole::BottomSurface;
    if (normalized == "bridge" || normalized == "internal bridge") return libvgcode::EGCodeExtrusionRole::InternalBridgeInfill;
    if (normalized == "gap infill" || normalized == "gap fill") return libvgcode::EGCodeExtrusionRole::GapFill;
    if (normalized.find("support interface") != std::string::npos) return libvgcode::EGCodeExtrusionRole::SupportMaterialInterface;
    if (normalized.find("support") != std::string::npos) return libvgcode::EGCodeExtrusionRole::SupportMaterial;
    if (normalized == "iron" || normalized == "ironing") return libvgcode::EGCodeExtrusionRole::Ironing;
    if (normalized == "prime tower" || normalized == "wipe tower") return libvgcode::EGCodeExtrusionRole::WipeTower;
    if (normalized == "custom") return libvgcode::EGCodeExtrusionRole::Custom;
    return libvgcode::EGCodeExtrusionRole::None;
}

static bool role_is_previewable_toolpath(libvgcode::EGCodeExtrusionRole role)
{
    (void)role;
    return true;
}

static bool should_preview_extrusion_segment(
    libvgcode::EGCodeExtrusionRole role,
    double distance,
    float mm3_per_mm)
{
    if (role == libvgcode::EGCodeExtrusionRole::WipeTower && distance > 25.0 && mm3_per_mm < 0.005f) {
        return false;
    }
    return true;
}

static void append_path_vertex(
    libvgcode::GCodeInputData& data,
    const libvgcode::Vec3& previous_position,
    const libvgcode::Vec3& position,
    float height,
    float width,
    float feedrate,
    float mm3_per_mm,
    libvgcode::EGCodeExtrusionRole role,
    libvgcode::EMoveType type,
    uint32_t gcode_id,
    uint32_t layer_id,
    uint8_t extruder_id,
    uint8_t color_id,
    float elapsed_time,
    bool force_path_break)
{
    const bool path_break = force_path_break ||
        data.vertices.empty() ||
        data.vertices.back().type != type ||
        data.vertices.back().role != role ||
        data.vertices.back().layer_id != layer_id ||
        data.vertices.back().extruder_id != extruder_id ||
        data.vertices.back().color_id != color_id;

    if (path_break) {
        if (!data.vertices.empty()) {
            libvgcode::PathVertex separator = data.vertices.back();
            separator.type = libvgcode::EMoveType::Noop;
            separator.role = libvgcode::EGCodeExtrusionRole::None;
            separator.times = { 0.0f, 0.0f };
            data.vertices.push_back(separator);
        }

        libvgcode::PathVertex start_vertex;
        start_vertex.position = previous_position;
        start_vertex.height = height;
        start_vertex.width = width;
        start_vertex.feedrate = feedrate;
        start_vertex.actual_feedrate = feedrate;
        start_vertex.mm3_per_mm = mm3_per_mm;
        start_vertex.role = role;
        start_vertex.type = type;
        start_vertex.gcode_id = gcode_id;
        start_vertex.layer_id = layer_id;
        start_vertex.extruder_id = extruder_id;
        start_vertex.color_id = color_id;
        start_vertex.times = { 0.0f, 0.0f };
        data.vertices.push_back(start_vertex);
    }

    libvgcode::PathVertex vertex;
    vertex.position = position;
    vertex.height = height;
    vertex.width = width;
    vertex.feedrate = feedrate;
    vertex.actual_feedrate = feedrate;
    vertex.mm3_per_mm = mm3_per_mm;
    vertex.role = role;
    vertex.type = type;
    vertex.gcode_id = gcode_id;
    vertex.layer_id = layer_id;
    vertex.extruder_id = extruder_id;
    vertex.color_id = color_id;
    vertex.times = { elapsed_time, 0.0f };
    data.vertices.push_back(vertex);
}

static std::optional<uint8_t> parse_tool_select_command(std::string_view command)
{
    if (command.empty() || (command[0] != 'T' && command[0] != 't')) {
        return std::nullopt;
    }
    if (command.size() < 2 || !std::isdigit(static_cast<unsigned char>(command[1]))) {
        return std::nullopt;
    }
    const std::string tail(command.substr(1));
    const char* start = tail.c_str();
    char* end = nullptr;
    const unsigned long parsed = std::strtoul(start, &end, 10);
    if (end == start || parsed > std::numeric_limits<uint8_t>::max()) {
        return std::nullopt;
    }
    return static_cast<uint8_t>(parsed);
}

static bool should_fold_into_top_surface(libvgcode::EGCodeExtrusionRole role)
{
    return role == libvgcode::EGCodeExtrusionRole::SolidInfill ||
        role == libvgcode::EGCodeExtrusionRole::InternalInfill ||
        role == libvgcode::EGCodeExtrusionRole::BridgeInfill ||
        role == libvgcode::EGCodeExtrusionRole::InternalBridgeInfill ||
        role == libvgcode::EGCodeExtrusionRole::BottomSurface;
}

static void normalize_top_surface_layers(libvgcode::GCodeInputData& data)
{
    uint32_t max_layer_id = 0;
    for (const libvgcode::PathVertex& vertex : data.vertices) {
        max_layer_id = std::max(max_layer_id, vertex.layer_id);
    }

    std::vector<bool> top_surface_layers(static_cast<size_t>(max_layer_id) + 1, false);
    for (const libvgcode::PathVertex& vertex : data.vertices) {
        if (vertex.type == libvgcode::EMoveType::Extrude &&
            vertex.role == libvgcode::EGCodeExtrusionRole::TopSolidInfill) {
            top_surface_layers[static_cast<size_t>(vertex.layer_id)] = true;
        }
    }

    for (libvgcode::PathVertex& vertex : data.vertices) {
        if (vertex.type == libvgcode::EMoveType::Extrude &&
            top_surface_layers[static_cast<size_t>(vertex.layer_id)] &&
            should_fold_into_top_surface(vertex.role)) {
            vertex.role = libvgcode::EGCodeExtrusionRole::TopSolidInfill;
        }
    }
}

static libvgcode::GCodeInputData to_vgcode_input_data_from_gcode_text(
    const std::string& gcode,
    long min_layer,
    long max_layer,
    size_t max_vertices = kMaxPreviewVertices,
    bool* vertex_limit_reached = nullptr,
    const std::function<bool()>& should_cancel = {})
{
    if (vertex_limit_reached != nullptr) {
        *vertex_limit_reached = false;
    }
    libvgcode::GCodeInputData data;
    const std::initializer_list<libvgcode::Color> default_tool_colors = {
        { 255, 125, 56 },
        { 92, 157, 246 },
        { 219, 68, 55 },
        { 241, 196, 15 },
        { 39, 174, 96 },
        { 189, 195, 199 },
        { 155, 89, 182 },
        { 26, 188, 156 }
    };
    data.tools_colors.insert(data.tools_colors.end(), default_tool_colors.begin(), default_tool_colors.end());
    data.color_print_colors.insert(data.color_print_colors.end(), default_tool_colors.begin(), default_tool_colors.end());
    const bool layer_markers_available =
        gcode.find("LAYER_CHANGE") != std::string::npos ||
        gcode.find("CHANGE_LAYER") != std::string::npos;

    double x = 0.0;
    double y = 0.0;
    double z = 0.0;
    double e = 0.0;
    float feedrate = 50.0f;
    float height = 0.2f;
    float width = 0.42f;
    bool absolute_xyz = true;
    bool absolute_e = true;
    uint32_t layer_id = 0;
    uint32_t line_id = 0;
    uint8_t current_extruder_id = 0;
    libvgcode::EGCodeExtrusionRole role = libvgcode::EGCodeExtrusionRole::Custom;
    double last_extrusion_z = 0.0;
    bool pending_layer_change = false;
    bool saw_layer_change_marker = false;
    bool first_marked_layer_started = false;
    bool print_toolpath_started = !layer_markers_available;
    bool preview_position_synced = !layer_markers_available;
    bool extrusion_path_open = false;
    bool have_extrusion_z = false;
    size_t skipped_custom_extrusions = 0;
    size_t preprint_custom_extrusions = 0;
    size_t arc_extrusion_segments = 0;
    const bool layer_filter_enabled = min_layer >= 0 && max_layer >= min_layer;
    GcodeMoveWords move_words;
    bool parse_cancelled = false;

    std::istringstream stream(gcode);
    std::string raw_line;
    while (std::getline(stream, raw_line)) {
        ++line_id;
        if ((line_id & 0xFFu) == 0 && should_cancel && should_cancel()) {
            parse_cancelled = true;
            break;
        }
        const size_t comment_pos = raw_line.find(';');
        const std::string_view raw_line_view(raw_line);
        const std::string_view command = trim_view(raw_line_view.substr(0, comment_pos));
        const std::string_view comment = comment_pos == std::string::npos ?
            std::string_view() :
            trim_view(raw_line_view.substr(comment_pos + 1));

        if (!comment.empty()) {
            if (starts_with_text(comment, "TYPE:")) {
                role = role_from_type_marker(comment.substr(5));
                if (role_is_previewable_toolpath(role)) {
                    print_toolpath_started = true;
                    preview_position_synced = false;
                }
            } else if (starts_with_text(comment, "FEATURE:")) {
                role = role_from_type_marker(comment.substr(8));
                if (role_is_previewable_toolpath(role)) {
                    print_toolpath_started = true;
                    preview_position_synced = false;
                }
            } else if (comment == "LAYER_CHANGE" || comment == "CHANGE_LAYER") {
                saw_layer_change_marker = true;
                pending_layer_change = true;
                print_toolpath_started = true;
                preview_position_synced = false;
            } else if (!parse_float_after_prefix(comment, "HEIGHT:", height)) {
                if (!parse_float_after_prefix(comment, "WIDTH:", width)) {
                    parse_float_after_prefix(comment, " LAYER_HEIGHT: ", height);
                    parse_float_after_prefix(comment, " LINE_WIDTH: ", width);
                }
            }
        }

        if (command.empty()) {
            continue;
        }
        if (is_preview_ignored_command(command)) {
            continue;
        }
        if (command_matches(command, "G90")) {
            absolute_xyz = true;
            continue;
        }
        if (command_matches(command, "G91")) {
            absolute_xyz = false;
            continue;
        }
        if (command_matches(command, "M82")) {
            absolute_e = true;
            continue;
        }
        if (command_matches(command, "M83")) {
            absolute_e = false;
            continue;
        }
        if (const auto selected_tool = parse_tool_select_command(command)) {
            current_extruder_id = *selected_tool;
            extrusion_path_open = false;
            continue;
        }

        const bool is_arc_move = command_matches(command, "G2") || command_matches(command, "G3");
        const bool is_move = command_matches(command, "G0") || command_matches(command, "G1") || is_arc_move;
        if (!is_move) {
            if (command_matches(command, "G92")) {
                if (parse_gcode_move_words(command, move_words)) {
                    if (move_words.has_x) x = move_words.x;
                    if (move_words.has_y) y = move_words.y;
                    if (move_words.has_z) z = move_words.z;
                    if (move_words.has_e) e = move_words.e;
                }
            }
            continue;
        }

        if (!parse_gcode_move_words(command, move_words)) {
            continue;
        }

        const double px = x;
        const double py = y;
        const double pz = z;
        const double pe = e;
        if (move_words.has_x) x = absolute_xyz ? move_words.x : x + move_words.x;
        if (move_words.has_y) y = absolute_xyz ? move_words.y : y + move_words.y;
        if (move_words.has_z) z = absolute_xyz ? move_words.z : z + move_words.z;
        if (move_words.has_e) e = absolute_e ? move_words.e : e + move_words.e;
        if (move_words.has_f) feedrate = static_cast<float>(move_words.f / 60.0);

        const double dx = x - px;
        const double dy = y - py;
        const double dz = z - pz;
        const double de = e - pe;
        const double distance = is_arc_move
            ? estimate_arc_distance_mm(command_matches(command, "G2"), move_words, px, py, pz, x, y, z)
            : std::sqrt(dx * dx + dy * dy + dz * dz);
        if (distance == 0.0 && de == 0.0) {
            continue;
        }

        libvgcode::EMoveType type = libvgcode::EMoveType::Travel;
        if (de > 0.0 && distance > 0.0) {
            type = libvgcode::EMoveType::Extrude;
        } else if (de < 0.0 && distance == 0.0) {
            type = libvgcode::EMoveType::Retract;
        } else if (de > 0.0 && distance == 0.0) {
            type = libvgcode::EMoveType::Unretract;
        }

        if (type != libvgcode::EMoveType::Extrude) {
            extrusion_path_open = false;
            if (print_toolpath_started) {
                preview_position_synced = true;
            }
            continue;
        }
        if (!print_toolpath_started && role == libvgcode::EGCodeExtrusionRole::Custom) {
            print_toolpath_started = true;
            preview_position_synced = true;
            ++preprint_custom_extrusions;
        }
        if (!print_toolpath_started) {
            extrusion_path_open = false;
            continue;
        }
        if (!role_is_previewable_toolpath(role)) {
            extrusion_path_open = false;
            ++skipped_custom_extrusions;
            continue;
        }
        if (!preview_position_synced) {
            preview_position_synced = true;
            extrusion_path_open = false;
            continue;
        }
        if (max_vertices > 0 && data.vertices.size() >= max_vertices) {
            if (vertex_limit_reached != nullptr) {
                *vertex_limit_reached = true;
            }
            break;
        }
        if (layer_markers_available) {
            if (pending_layer_change) {
                if (first_marked_layer_started) {
                    ++layer_id;
                } else {
                    first_marked_layer_started = true;
                }
            } else if (saw_layer_change_marker && !first_marked_layer_started) {
                first_marked_layer_started = true;
            }
        } else if (have_extrusion_z && z > last_extrusion_z + 0.0001) {
            ++layer_id;
        }
        pending_layer_change = false;
        last_extrusion_z = z;
        have_extrusion_z = true;
        if (layer_filter_enabled && static_cast<long>(layer_id) > max_layer) {
            break;
        }
        if (layer_filter_enabled &&
            (static_cast<long>(layer_id) < min_layer || static_cast<long>(layer_id) > max_layer)) {
            extrusion_path_open = false;
            continue;
        }
        const float segment_height = std::max(0.01f, height);
        const double filament_radius = 0.875;
        const double filament_area = M_PI * filament_radius * filament_radius;
        const float mm3_per_mm = (type == libvgcode::EMoveType::Extrude && distance > 0.0) ?
            static_cast<float>((de * filament_area) / distance) :
            0.0f;
        if (!should_preview_extrusion_segment(role, distance, mm3_per_mm)) {
            extrusion_path_open = false;
            preview_position_synced = true;
            continue;
        }
        const float segment_width = (type == libvgcode::EMoveType::Extrude && mm3_per_mm > 0.0f) ?
            std::max(width, mm3_per_mm / segment_height) :
            width;
        const float elapsed_time = feedrate > 0.0f ?
            static_cast<float>((distance > 0.0 ? distance : std::abs(de)) / feedrate) :
            0.0f;
        const uint32_t preview_layer_id = layer_filter_enabled ?
            static_cast<uint32_t>(static_cast<long>(layer_id) - min_layer) :
            layer_id;

        const size_t segment_count = is_arc_move
            ? std::clamp(static_cast<size_t>(std::ceil(distance / 0.6)), static_cast<size_t>(4), static_cast<size_t>(96))
            : static_cast<size_t>(1);
        libvgcode::Vec3 segment_start{ static_cast<float>(px), static_cast<float>(py), static_cast<float>(pz) };
        for (size_t segment = 1; segment <= segment_count; ++segment) {
            if (should_cancel && should_cancel()) {
                parse_cancelled = true;
                break;
            }
            if (max_vertices > 0 && data.vertices.size() >= max_vertices) {
                if (vertex_limit_reached != nullptr) {
                    *vertex_limit_reached = true;
                }
                break;
            }
            const double fraction = static_cast<double>(segment) / static_cast<double>(segment_count);
            const libvgcode::Vec3 segment_end = is_arc_move
                ? interpolate_arc_position(command_matches(command, "G2"), move_words, px, py, pz, x, y, z, fraction)
                : libvgcode::Vec3{ static_cast<float>(x), static_cast<float>(y), static_cast<float>(z) };
            append_path_vertex(
                data,
                segment_start,
                segment_end,
                segment_height,
                segment_width,
                feedrate,
                mm3_per_mm,
                role,
                type,
                line_id,
                preview_layer_id,
                current_extruder_id,
                current_extruder_id,
                elapsed_time / static_cast<float>(segment_count),
                !extrusion_path_open && segment == 1);
            segment_start = segment_end;
        }
        if (parse_cancelled) {
            break;
        }
        if (is_arc_move) {
            arc_extrusion_segments += segment_count;
        }
        extrusion_path_open = true;
    }

    if (parse_cancelled) {
        data.vertices.clear();
        data.vertices.shrink_to_fit();
        return data;
    }
    normalize_top_surface_layers(data);
    if (kVerboseNativeTimingLogs && skipped_custom_extrusions > 0) {
        log_native_info("gcode_preview_parser",
            "skipped_custom_extrusions=" + std::to_string(skipped_custom_extrusions));
    }
    if (kVerboseNativeTimingLogs && preprint_custom_extrusions > 0) {
        log_native_info("gcode_preview_parser",
            "preprint_custom_extrusions=" + std::to_string(preprint_custom_extrusions));
    }
    if (kVerboseNativeTimingLogs && arc_extrusion_segments > 0) {
        log_native_info("gcode_preview_parser",
            "arc_extrusion_segments=" + std::to_string(arc_extrusion_segments));
    }
    data.vertices.shrink_to_fit();
    rebuild_gcode_layer_vertex_ranges(data);
    return data;
}

static uint32_t gcode_input_layer_count(const libvgcode::GCodeInputData& data)
{
    uint32_t layer_count = 0;
    for (const libvgcode::PathVertex& vertex : data.vertices) {
        layer_count = std::max(layer_count, vertex.layer_id + 1);
    }
    return layer_count;
}

static std::vector<size_t> count_preview_vertices_by_layer_from_input_data(const libvgcode::GCodeInputData& data)
{
    if (!data.layer_vertex_ranges.empty()) {
        std::vector<size_t> layer_vertices;
        layer_vertices.reserve(data.layer_vertex_ranges.size());
        for (const libvgcode::LayerVertexRange& range : data.layer_vertex_ranges) {
            layer_vertices.push_back(range.end - range.begin);
        }
        return layer_vertices;
    }

    std::vector<size_t> layer_vertices;
    for (const libvgcode::PathVertex& vertex : data.vertices) {
        if (layer_vertices.size() <= vertex.layer_id) {
            layer_vertices.resize(static_cast<size_t>(vertex.layer_id) + 1, 0);
        }
        ++layer_vertices[static_cast<size_t>(vertex.layer_id)];
    }
    return layer_vertices;
}

static std::string pack_preview_layer_ranges_from_counts(
    const std::vector<size_t>& layer_vertices,
    long min_layer,
    long max_layer,
    size_t budget,
    std::string& error)
{
    if (layer_vertices.empty()) {
        error = "empty layer vertex count";
        return "";
    }
    const long capped_max_layer = std::min<long>(max_layer, static_cast<long>(layer_vertices.size()) - 1L);
    if (capped_max_layer < min_layer) {
        error = "empty capped range";
        return "";
    }

    std::ostringstream plan;
    long start = min_layer;
    while (start <= capped_max_layer) {
        if (layer_vertices[static_cast<size_t>(start)] >= budget) {
            error = "single layer exceeds budget layer=" + std::to_string(start) +
                " vertices=" + std::to_string(layer_vertices[static_cast<size_t>(start)]) +
                " budget=" + std::to_string(budget);
            return "";
        }
        size_t total = 0;
        long end = start;
        while (end <= capped_max_layer) {
            const size_t layer_count = layer_vertices[static_cast<size_t>(end)];
            if (end > start && total + layer_count >= budget) {
                break;
            }
            total += layer_count;
            ++end;
        }
        const long inclusive_end = std::max(start, end - 1L);
        if (plan.tellp() > 0) {
            plan << ';';
        }
        plan << start << '-' << inclusive_end;
        start = inclusive_end + 1L;
    }
    return plan.str();
}

struct PreviewVertexCounter {
    bool empty{true};
    libvgcode::EMoveType previous_type{libvgcode::EMoveType::Noop};
    libvgcode::EGCodeExtrusionRole previous_role{libvgcode::EGCodeExtrusionRole::None};
    uint32_t previous_layer{0};
    std::vector<size_t> layer_vertices;

    void emit(
        libvgcode::EGCodeExtrusionRole role,
        libvgcode::EMoveType type,
        uint32_t layer_id,
        bool force_path_break)
    {
        const bool path_break = force_path_break ||
            empty ||
            previous_type != type ||
            previous_role != role ||
            previous_layer != layer_id;
        if (path_break) {
            if (!empty) {
                add(previous_layer);
            }
            add(layer_id);
        }
        add(layer_id);
        empty = false;
        previous_type = type;
        previous_role = role;
        previous_layer = layer_id;
    }

    void add(uint32_t layer_id)
    {
        if (layer_vertices.size() <= layer_id) {
            layer_vertices.resize(static_cast<size_t>(layer_id) + 1, 0);
        }
        ++layer_vertices[static_cast<size_t>(layer_id)];
    }
};

static std::vector<size_t> count_preview_vertices_by_layer_from_gcode_text(const std::string& gcode)
{
    PreviewVertexCounter counter;
    const bool layer_markers_available =
        gcode.find("LAYER_CHANGE") != std::string::npos ||
        gcode.find("CHANGE_LAYER") != std::string::npos;

    double x = 0.0;
    double y = 0.0;
    double z = 0.0;
    double e = 0.0;
    bool absolute_xyz = true;
    bool absolute_e = true;
    uint32_t layer_id = 0;
    libvgcode::EGCodeExtrusionRole role = libvgcode::EGCodeExtrusionRole::Custom;
    double last_extrusion_z = 0.0;
    bool pending_layer_change = false;
    bool saw_layer_change_marker = false;
    bool first_marked_layer_started = false;
    bool print_toolpath_started = !layer_markers_available;
    bool preview_position_synced = !layer_markers_available;
    bool extrusion_path_open = false;
    bool have_extrusion_z = false;
    GcodeMoveWords move_words;

    std::istringstream stream(gcode);
    std::string raw_line;
    while (std::getline(stream, raw_line)) {
        const size_t comment_pos = raw_line.find(';');
        const std::string_view raw_line_view(raw_line);
        const std::string_view command = trim_view(raw_line_view.substr(0, comment_pos));
        const std::string_view comment = comment_pos == std::string::npos ?
            std::string_view() :
            trim_view(raw_line_view.substr(comment_pos + 1));

        if (!comment.empty()) {
            if (starts_with_text(comment, "TYPE:")) {
                role = role_from_type_marker(comment.substr(5));
                if (role_is_previewable_toolpath(role)) {
                    print_toolpath_started = true;
                    preview_position_synced = false;
                }
            } else if (starts_with_text(comment, "FEATURE:")) {
                role = role_from_type_marker(comment.substr(8));
                if (role_is_previewable_toolpath(role)) {
                    print_toolpath_started = true;
                    preview_position_synced = false;
                }
            } else if (comment == "LAYER_CHANGE" || comment == "CHANGE_LAYER") {
                saw_layer_change_marker = true;
                pending_layer_change = true;
                print_toolpath_started = true;
                preview_position_synced = false;
            }
        }

        if (command.empty() || is_preview_ignored_command(command)) {
            continue;
        }
        if (command_matches(command, "G90")) {
            absolute_xyz = true;
            continue;
        }
        if (command_matches(command, "G91")) {
            absolute_xyz = false;
            continue;
        }
        if (command_matches(command, "M82")) {
            absolute_e = true;
            continue;
        }
        if (command_matches(command, "M83")) {
            absolute_e = false;
            continue;
        }

        const bool is_arc_move = command_matches(command, "G2") || command_matches(command, "G3");
        const bool is_move = command_matches(command, "G0") || command_matches(command, "G1") || is_arc_move;
        if (!is_move) {
            if (command_matches(command, "G92") && parse_gcode_move_words(command, move_words)) {
                if (move_words.has_x) x = move_words.x;
                if (move_words.has_y) y = move_words.y;
                if (move_words.has_z) z = move_words.z;
                if (move_words.has_e) e = move_words.e;
            }
            continue;
        }

        if (!parse_gcode_move_words(command, move_words)) {
            continue;
        }

        const double px = x;
        const double py = y;
        const double pz = z;
        const double pe = e;
        if (move_words.has_x) x = absolute_xyz ? move_words.x : x + move_words.x;
        if (move_words.has_y) y = absolute_xyz ? move_words.y : y + move_words.y;
        if (move_words.has_z) z = absolute_xyz ? move_words.z : z + move_words.z;
        if (move_words.has_e) e = absolute_e ? move_words.e : e + move_words.e;

        const double dx = x - px;
        const double dy = y - py;
        const double dz = z - pz;
        const double de = e - pe;
        const double distance = is_arc_move
            ? estimate_arc_distance_mm(command_matches(command, "G2"), move_words, px, py, pz, x, y, z)
            : std::sqrt(dx * dx + dy * dy + dz * dz);
        if (!(de > 0.0 && distance > 0.0)) {
            extrusion_path_open = false;
            if (print_toolpath_started) {
                preview_position_synced = true;
            }
            continue;
        }
        if (!print_toolpath_started && role == libvgcode::EGCodeExtrusionRole::Custom) {
            print_toolpath_started = true;
            preview_position_synced = true;
        }
        if (!print_toolpath_started || !role_is_previewable_toolpath(role)) {
            extrusion_path_open = false;
            continue;
        }
        if (!preview_position_synced) {
            preview_position_synced = true;
            extrusion_path_open = false;
            continue;
        }
        if (layer_markers_available) {
            if (pending_layer_change) {
                if (first_marked_layer_started) {
                    ++layer_id;
                } else {
                    first_marked_layer_started = true;
                }
            } else if (saw_layer_change_marker && !first_marked_layer_started) {
                first_marked_layer_started = true;
            }
        } else if (have_extrusion_z && z > last_extrusion_z + 0.0001) {
            ++layer_id;
        }
        pending_layer_change = false;
        last_extrusion_z = z;
        have_extrusion_z = true;

        const double filament_radius = 0.875;
        const double filament_area = M_PI * filament_radius * filament_radius;
        const float mm3_per_mm = static_cast<float>((de * filament_area) / distance);
        if (!should_preview_extrusion_segment(role, distance, mm3_per_mm)) {
            extrusion_path_open = false;
            preview_position_synced = true;
            continue;
        }

        const size_t segment_count = is_arc_move
            ? std::clamp(static_cast<size_t>(std::ceil(distance / 0.6)), static_cast<size_t>(4), static_cast<size_t>(96))
            : static_cast<size_t>(1);
        for (size_t segment = 1; segment <= segment_count; ++segment) {
            counter.emit(role, libvgcode::EMoveType::Extrude, layer_id, !extrusion_path_open && segment == 1);
        }
        extrusion_path_open = true;
    }

    return counter.layer_vertices;
}

struct PreviewLayerCountParser {
    explicit PreviewLayerCountParser(bool layer_markers_available)
        : layer_markers_available(layer_markers_available),
          print_toolpath_started(!layer_markers_available),
          preview_position_synced(!layer_markers_available)
    {
    }

    PreviewVertexCounter counter;
    double x = 0.0;
    double y = 0.0;
    double z = 0.0;
    double e = 0.0;
    bool absolute_xyz = true;
    bool absolute_e = true;
    uint32_t layer_id = 0;
    libvgcode::EGCodeExtrusionRole role = libvgcode::EGCodeExtrusionRole::Custom;
    double last_extrusion_z = 0.0;
    bool layer_markers_available = false;
    bool pending_layer_change = false;
    bool saw_layer_change_marker = false;
    bool first_marked_layer_started = false;
    bool print_toolpath_started = true;
    bool preview_position_synced = true;
    bool extrusion_path_open = false;
    bool have_extrusion_z = false;
    GcodeMoveWords move_words;

    bool process_line(std::string_view raw_line)
    {
        const size_t comment_pos = raw_line.find(';');
        const std::string_view command = trim_view(raw_line.substr(0, comment_pos));
        const std::string_view comment = comment_pos == std::string::npos ?
            std::string_view() :
            trim_view(raw_line.substr(comment_pos + 1));
        bool line_has_layer_marker = false;

        if (!comment.empty()) {
            if (starts_with_text(comment, "TYPE:")) {
                role = role_from_type_marker(comment.substr(5));
                if (role_is_previewable_toolpath(role)) {
                    print_toolpath_started = true;
                    preview_position_synced = false;
                }
            } else if (starts_with_text(comment, "FEATURE:")) {
                role = role_from_type_marker(comment.substr(8));
                if (role_is_previewable_toolpath(role)) {
                    print_toolpath_started = true;
                    preview_position_synced = false;
                }
            } else if (comment == "LAYER_CHANGE" || comment == "CHANGE_LAYER") {
                line_has_layer_marker = true;
                saw_layer_change_marker = true;
                pending_layer_change = true;
                print_toolpath_started = true;
                preview_position_synced = false;
            }
        }

        if (command.empty() || is_preview_ignored_command(command)) {
            return line_has_layer_marker;
        }
        if (command_matches(command, "G90")) {
            absolute_xyz = true;
            return line_has_layer_marker;
        }
        if (command_matches(command, "G91")) {
            absolute_xyz = false;
            return line_has_layer_marker;
        }
        if (command_matches(command, "M82")) {
            absolute_e = true;
            return line_has_layer_marker;
        }
        if (command_matches(command, "M83")) {
            absolute_e = false;
            return line_has_layer_marker;
        }

        const bool is_arc_move = command_matches(command, "G2") || command_matches(command, "G3");
        const bool is_move = command_matches(command, "G0") || command_matches(command, "G1") || is_arc_move;
        if (!is_move) {
            if (command_matches(command, "G92") && parse_gcode_move_words(command, move_words)) {
                if (move_words.has_x) x = move_words.x;
                if (move_words.has_y) y = move_words.y;
                if (move_words.has_z) z = move_words.z;
                if (move_words.has_e) e = move_words.e;
            }
            return line_has_layer_marker;
        }

        if (!parse_gcode_move_words(command, move_words)) {
            return line_has_layer_marker;
        }

        const double px = x;
        const double py = y;
        const double pz = z;
        const double pe = e;
        if (move_words.has_x) x = absolute_xyz ? move_words.x : x + move_words.x;
        if (move_words.has_y) y = absolute_xyz ? move_words.y : y + move_words.y;
        if (move_words.has_z) z = absolute_xyz ? move_words.z : z + move_words.z;
        if (move_words.has_e) e = absolute_e ? move_words.e : e + move_words.e;

        const double dx = x - px;
        const double dy = y - py;
        const double dz = z - pz;
        const double de = e - pe;
        const double distance = is_arc_move
            ? estimate_arc_distance_mm(command_matches(command, "G2"), move_words, px, py, pz, x, y, z)
            : std::sqrt(dx * dx + dy * dy + dz * dz);
        if (!(de > 0.0 && distance > 0.0)) {
            extrusion_path_open = false;
            if (print_toolpath_started) {
                preview_position_synced = true;
            }
            return line_has_layer_marker;
        }
        if (!print_toolpath_started && role == libvgcode::EGCodeExtrusionRole::Custom) {
            print_toolpath_started = true;
            preview_position_synced = true;
        }
        if (!print_toolpath_started || !role_is_previewable_toolpath(role)) {
            extrusion_path_open = false;
            return line_has_layer_marker;
        }
        if (!preview_position_synced) {
            preview_position_synced = true;
            extrusion_path_open = false;
            return line_has_layer_marker;
        }
        if (layer_markers_available) {
            if (pending_layer_change) {
                if (first_marked_layer_started) {
                    ++layer_id;
                } else {
                    first_marked_layer_started = true;
                }
            } else if (saw_layer_change_marker && !first_marked_layer_started) {
                first_marked_layer_started = true;
            }
        } else if (have_extrusion_z && z > last_extrusion_z + 0.0001) {
            ++layer_id;
        }
        pending_layer_change = false;
        last_extrusion_z = z;
        have_extrusion_z = true;

        const double filament_radius = 0.875;
        const double filament_area = M_PI * filament_radius * filament_radius;
        const float mm3_per_mm = static_cast<float>((de * filament_area) / distance);
        if (!should_preview_extrusion_segment(role, distance, mm3_per_mm)) {
            extrusion_path_open = false;
            preview_position_synced = true;
            return line_has_layer_marker;
        }

        const size_t segment_count = is_arc_move
            ? std::clamp(static_cast<size_t>(std::ceil(distance / 0.6)), static_cast<size_t>(4), static_cast<size_t>(96))
            : static_cast<size_t>(1);
        for (size_t segment = 1; segment <= segment_count; ++segment) {
            counter.emit(role, libvgcode::EMoveType::Extrude, layer_id, !extrusion_path_open && segment == 1);
        }
        extrusion_path_open = true;
        return line_has_layer_marker;
    }
};

static std::vector<size_t> count_preview_vertices_by_layer_from_gcode_stream(
    std::istream& stream)
{
    PreviewLayerCountParser no_marker_parser(false);
    PreviewLayerCountParser marker_parser(true);
    bool saw_layer_marker = false;

    std::string raw_line;
    while (std::getline(stream, raw_line)) {
        if (saw_layer_marker) {
            marker_parser.process_line(raw_line);
        } else {
            const bool marker_seen_by_no_marker_parser = no_marker_parser.process_line(raw_line);
            const bool marker_seen_by_marker_parser = marker_parser.process_line(raw_line);
            saw_layer_marker = marker_seen_by_no_marker_parser || marker_seen_by_marker_parser;
        }
    }

    return saw_layer_marker ?
        marker_parser.counter.layer_vertices :
        no_marker_parser.counter.layer_vertices;
}

static std::vector<size_t> count_preview_vertices_by_layer_from_gcode_file(const std::filesystem::path& path)
{
    std::ifstream stream(path);
    if (!stream) {
        return {};
    }
    return count_preview_vertices_by_layer_from_gcode_stream(stream);
}

static std::string label_for_libvgcode_role(libvgcode::EGCodeExtrusionRole role)
{
    switch (role) {
        case libvgcode::EGCodeExtrusionRole::Perimeter: return "Inner wall";
        case libvgcode::EGCodeExtrusionRole::ExternalPerimeter: return "Outer wall";
        case libvgcode::EGCodeExtrusionRole::OverhangPerimeter: return "Overhang wall";
        case libvgcode::EGCodeExtrusionRole::InternalInfill: return "Sparse infill";
        case libvgcode::EGCodeExtrusionRole::SolidInfill: return "Internal solid infill";
        case libvgcode::EGCodeExtrusionRole::TopSolidInfill: return "Top surface";
        case libvgcode::EGCodeExtrusionRole::Ironing: return "Ironing";
        case libvgcode::EGCodeExtrusionRole::BridgeInfill: return "Bridge";
        case libvgcode::EGCodeExtrusionRole::GapFill: return "Gap infill";
        case libvgcode::EGCodeExtrusionRole::Skirt: return "Skirt";
        case libvgcode::EGCodeExtrusionRole::SupportMaterial: return "Support";
        case libvgcode::EGCodeExtrusionRole::SupportMaterialInterface: return "Support interface";
        case libvgcode::EGCodeExtrusionRole::WipeTower: return "Wipe tower";
        case libvgcode::EGCodeExtrusionRole::Custom: return "Custom";
        case libvgcode::EGCodeExtrusionRole::BottomSurface: return "Bottom surface";
        case libvgcode::EGCodeExtrusionRole::InternalBridgeInfill: return "Internal Bridge";
        case libvgcode::EGCodeExtrusionRole::Brim: return "Brim";
        case libvgcode::EGCodeExtrusionRole::SupportTransition: return "Support transition";
        default: return "Other";
    }
}

static std::string enrich_gcode_summary_from_preview_input(std::string summary, const libvgcode::GCodeInputData& input)
{
    std::map<libvgcode::EGCodeExtrusionRole, double> role_times;
    std::map<libvgcode::EGCodeExtrusionRole, double> role_meters;
    std::map<libvgcode::EGCodeExtrusionRole, double> role_grams;
    std::map<libvgcode::EOptionType, double> option_times;
    std::map<size_t, double> model_volumes;
    std::map<size_t, double> support_volumes;
    std::map<size_t, double> tower_volumes;
    double total_time = 0.0;
    double total_volume = 0.0;
    int filament_change_count = 0;
    int extruder_change_count = 0;

    for (size_t index = 1; index < input.vertices.size(); ++index) {
        const libvgcode::PathVertex& previous = input.vertices[index - 1];
        const libvgcode::PathVertex& current = input.vertices[index];
        const double seconds = std::max(0.0f, current.times[0]);
        total_time += seconds;
        if (current.type == libvgcode::EMoveType::Extrude && role_is_previewable_toolpath(current.role)) {
            const double dx = static_cast<double>(current.position[0] - previous.position[0]);
            const double dy = static_cast<double>(current.position[1] - previous.position[1]);
            const double dz = static_cast<double>(current.position[2] - previous.position[2]);
            const double distance = std::sqrt(dx * dx + dy * dy + dz * dz);
            const double volume = distance * std::max(0.0f, current.mm3_per_mm);
            role_times[current.role] += seconds;
            role_meters[current.role] += filament_length_m_from_volume_mm3(volume, 1.75);
            role_grams[current.role] += filament_grams_from_volume_mm3(volume, 1.24);
            const size_t filament_id = static_cast<size_t>(current.extruder_id);
            if (current.role == libvgcode::EGCodeExtrusionRole::WipeTower) {
                tower_volumes[filament_id] += volume;
            } else if (current.role == libvgcode::EGCodeExtrusionRole::SupportMaterial ||
                       current.role == libvgcode::EGCodeExtrusionRole::SupportMaterialInterface ||
                       current.role == libvgcode::EGCodeExtrusionRole::SupportTransition) {
                support_volumes[filament_id] += volume;
            } else {
                model_volumes[filament_id] += volume;
            }
            total_volume += volume;
        } else {
            const auto option = libvgcode::move_type_to_option(current.type);
            if (option != libvgcode::EOptionType::COUNT) {
                option_times[option] += seconds;
                if (option == libvgcode::EOptionType::ToolChanges) {
                    ++filament_change_count;
                    ++extruder_change_count;
                } else if (option == libvgcode::EOptionType::ColorChanges) {
                    ++filament_change_count;
                }
            }
        }
    }

    const std::vector<libvgcode::EGCodeExtrusionRole> role_order = {
        libvgcode::EGCodeExtrusionRole::Perimeter,
        libvgcode::EGCodeExtrusionRole::ExternalPerimeter,
        libvgcode::EGCodeExtrusionRole::OverhangPerimeter,
        libvgcode::EGCodeExtrusionRole::InternalInfill,
        libvgcode::EGCodeExtrusionRole::SolidInfill,
        libvgcode::EGCodeExtrusionRole::TopSolidInfill,
        libvgcode::EGCodeExtrusionRole::BridgeInfill,
        libvgcode::EGCodeExtrusionRole::GapFill,
        libvgcode::EGCodeExtrusionRole::Custom,
        libvgcode::EGCodeExtrusionRole::BottomSurface,
        libvgcode::EGCodeExtrusionRole::InternalBridgeInfill,
        libvgcode::EGCodeExtrusionRole::Brim,
        libvgcode::EGCodeExtrusionRole::SupportMaterial,
        libvgcode::EGCodeExtrusionRole::SupportMaterialInterface,
        libvgcode::EGCodeExtrusionRole::WipeTower,
        libvgcode::EGCodeExtrusionRole::Ironing
    };
    std::ostringstream line_types;
    line_types << std::fixed << std::setprecision(5);
    bool first_row = true;
    auto append_row = [&](const char* kind, int id, const std::string& label, const char* color, double seconds, double meters, double grams, bool visible, bool force_row) {
        if (!force_row && seconds <= 0.001 && meters <= 0.00001 && grams <= 0.00001) {
            return;
        }
        if (!first_row) {
            line_types << ';';
        }
        first_row = false;
        const double percent = total_time > 0.0 ? (seconds / total_time) * 100.0 : 0.0;
        line_types << kind << ','
                   << id << ','
                   << summary_escape(label) << ','
                   << color << ','
                   << seconds << ','
                   << percent << ','
                   << meters << ','
                   << grams << ','
                   << (visible ? 1 : 0);
    };
    for (const auto role : role_order) {
        append_row(
            "role",
            static_cast<int>(role),
            label_for_libvgcode_role(role),
            color_for_libvgcode_role(role),
            role_times[role],
            role_meters[role],
            role_grams[role],
            true,
            false);
    }
    const std::vector<libvgcode::EOptionType> option_order = {
        libvgcode::EOptionType::Travels,
        libvgcode::EOptionType::Wipes,
        libvgcode::EOptionType::Retractions,
        libvgcode::EOptionType::Unretractions,
        libvgcode::EOptionType::Seams,
        libvgcode::EOptionType::ToolChanges,
        libvgcode::EOptionType::ColorChanges
    };
    for (const auto option : option_order) {
        append_row(
            "option",
            static_cast<int>(option),
            label_for_option(option),
            color_for_libvgcode_option(option),
            option_times[option],
            0.0,
            0.0,
            option == libvgcode::EOptionType::Seams,
            true);
    }

    std::ostringstream filaments;
    filaments << std::fixed << std::setprecision(5);
    bool first_filament = true;
    std::set<size_t> filament_ids;
    for (const auto& [extruder_id, volume] : model_volumes) {
        filament_ids.insert(extruder_id);
    }
    for (const auto& [extruder_id, volume] : support_volumes) {
        filament_ids.insert(extruder_id);
    }
    for (const auto& [extruder_id, volume] : tower_volumes) {
        filament_ids.insert(extruder_id);
    }
    for (const size_t extruder_id : filament_ids) {
        const double model_volume = map_value_or_zero(model_volumes, extruder_id);
        const double support_volume = map_value_or_zero(support_volumes, extruder_id);
        const double tower_volume = map_value_or_zero(tower_volumes, extruder_id);
        const double volume = model_volume + support_volume + tower_volume;
        if (volume <= 0.0) {
            continue;
        }
        if (!first_filament) {
            filaments << ';';
        }
        first_filament = false;
        const double meters = filament_length_m_from_volume_mm3(volume, 1.75);
        const double grams = filament_grams_from_volume_mm3(volume, 1.24);
        std::string color = "#8FC1FF";
        if (extruder_id < input.tools_colors.size()) {
            const auto& tool_color = input.tools_colors[extruder_id];
            color = hex_for_rgb(tool_color[0], tool_color[1], tool_color[2]);
        }
        filaments << (extruder_id + 1) << ','
                  << summary_escape("Filament " + std::to_string(extruder_id + 1)) << ','
                  << summary_escape(color) << ','
                  << filament_length_m_from_volume_mm3(model_volume, 1.75) << ',' << filament_grams_from_volume_mm3(model_volume, 1.24) << ','
                  << filament_length_m_from_volume_mm3(support_volume, 1.75) << ',' << filament_grams_from_volume_mm3(support_volume, 1.24) << ','
                  << 0.0 << ',' << 0.0 << ','
                  << filament_length_m_from_volume_mm3(tower_volume, 1.75) << ',' << filament_grams_from_volume_mm3(tower_volume, 1.24) << ','
                  << meters << ',' << grams << ','
                  << 0.0;
    }

    std::ostringstream totals;
    totals << std::fixed << std::setprecision(5)
           << "totalSeconds=" << total_time
           << ",prepareSeconds=0.00000"
           << ",modelSeconds=" << total_time
           << ",cost=0.00000"
           << ",filamentChanges=" << filament_change_count
           << ",extruderChanges=" << extruder_change_count;

    summary = replace_summary_field(summary, "previewLineTypes", line_types.str());
    summary = replace_summary_field(summary, "previewFilaments", filaments.str());
    summary = replace_summary_field(summary, "previewTotals", totals.str());
    if (total_volume > 0.0) {
        std::ostringstream grams;
        grams << std::fixed << std::setprecision(2) << filament_grams_from_volume_mm3(total_volume, 1.24);
        summary = replace_summary_field(summary, "grams", grams.str());
    }
    return summary;
}

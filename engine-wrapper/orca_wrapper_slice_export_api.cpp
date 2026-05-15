#include "orca_wrapper_module_context.h"

static void cut_first_model_object_for_calibration(
    Slic3r::Model& model,
    double z,
    Slic3r::ModelObjectCutAttributes attributes
) {
    if (model.objects.empty() || !std::isfinite(z)) {
        return;
    }
    if (!attributes.has(Slic3r::ModelObjectCutAttribute::KeepUpper) &&
        !attributes.has(Slic3r::ModelObjectCutAttribute::KeepLower)) {
        return;
    }

    Slic3r::ModelObject* object = model.objects.front();
    if (object == nullptr) {
        return;
    }
    if (object->instances.empty()) {
        object->add_instance();
    }
    if (object->instances.empty() || object->instances.front() == nullptr) {
        return;
    }

    const Slic3r::Vec3d instance_offset = object->instances.front()->get_offset();
    Slic3r::Cut cut(
        object,
        0,
        Slic3r::Geometry::translation_transform(z * Slic3r::Vec3d::UnitZ() - instance_offset),
        attributes
    );
    const Slic3r::ModelObjectPtrs new_objects = cut.perform_with_plane();
    if (new_objects.empty()) {
        return;
    }

    model.delete_object(static_cast<size_t>(0));
    for (Slic3r::ModelObject* cut_object : new_objects) {
        if (cut_object == nullptr) {
            continue;
        }
        Slic3r::ModelObject* inserted = model.add_object(*cut_object);
        inserted->sort_volumes(true);
        inserted->ensure_on_bed();
    }
}

static double first_model_object_height(const Slic3r::Model& model)
{
    if (model.objects.empty() || model.objects.front() == nullptr) {
        return 0.0;
    }
    return model.objects.front()->bounding_box_exact().size().z();
}

static std::optional<Slic3r::PlateBBoxData> build_sliced_plate_bbox_data(
    Slic3r::Print& print,
    const Slic3r::GCodeProcessorResult& gcode_result
)
{
    Slic3r::PlateBBoxData bbox_data;
    bbox_data.is_seq_print = print.config().print_sequence.value == Slic3r::PrintSequence::ByObject;
    bbox_data.bed_type = Slic3r::bed_type_to_gcode_string(print.config().curr_bed_type.value);
    bbox_data.first_layer_time = gcode_result.initial_layer_time;

    unsigned int first_extruder = print.get_tool_ordering().first_extruder();
    if (first_extruder == static_cast<unsigned int>(-1)) {
        first_extruder = 0;
    }
    bbox_data.first_extruder = static_cast<int>(first_extruder);

    if (const auto* nozzle_diameters = print.config().option<Slic3r::ConfigOptionFloats>("nozzle_diameter");
        nozzle_diameters != nullptr && !nozzle_diameters->values.empty()) {
        bbox_data.nozzle_diameter = static_cast<float>(nozzle_diameters->get_at(first_extruder));
    }

    const Slic3r::Vec3d origin = print.get_plate_origin();
    const Slic3r::Vec2d origin_2d(origin.x(), origin.y());
    Slic3r::BoundingBoxf bbox_all;
    const auto unscaled_bboxf = [](const Slic3r::BoundingBox& scaled_bbox) {
        const auto bbox = Slic3r::unscaled(scaled_bbox);
        return Slic3r::BoundingBoxf(bbox.min, bbox.max);
    };

    const size_t print_object_count = print.objects().size();
    for (size_t object_index = 0; object_index < print_object_count; ++object_index) {
        Slic3r::PrintObject* object = print.get_object(object_index);
        if (object == nullptr) {
            continue;
        }
        Slic3r::BBoxData object_bbox;
        const Slic3r::BoundingBox scaled_bbox =
            object->get_first_layer_bbox(object_bbox.area, object_bbox.layer_height, object_bbox.name);
        if (!scaled_bbox.defined) {
            continue;
        }
        Slic3r::BoundingBoxf bbox = unscaled_bboxf(scaled_bbox);
        bbox.min -= origin_2d;
        bbox.max -= origin_2d;
        bbox_all.merge(bbox);
        object_bbox.area *= static_cast<float>(SCALING_FACTOR * SCALING_FACTOR);
        object_bbox.id = object->id().id;
        object_bbox.bbox = {bbox.min.x(), bbox.min.y(), bbox.max.x(), bbox.max.y()};
        bbox_data.bbox_objs.emplace_back(std::move(object_bbox));
    }

    if (print.has_wipe_tower()) {
        const Slic3r::Points wipe_tower_corners = print.first_layer_wipe_tower_corners();
        if (wipe_tower_corners.size() >= 3) {
            Slic3r::BoundingBox scaled_bbox{wipe_tower_corners[0], wipe_tower_corners[2]};
            if (scaled_bbox.defined) {
                Slic3r::BoundingBoxf bbox = unscaled_bboxf(scaled_bbox);
                bbox.min -= origin_2d;
                bbox.max -= origin_2d;
                bbox_all.merge(bbox);

                Slic3r::BBoxData wipe_tower_bbox;
                wipe_tower_bbox.id = 1000;
                wipe_tower_bbox.bbox = {bbox.min.x(), bbox.min.y(), bbox.max.x(), bbox.max.y()};
                wipe_tower_bbox.area = static_cast<float>(bbox.area());
                wipe_tower_bbox.layer_height = static_cast<float>(print.skirt_first_layer_height());
                wipe_tower_bbox.name = "wipe_tower";
                bbox_data.bbox_objs.emplace_back(std::move(wipe_tower_bbox));
            }
        }
    }

    if (!bbox_all.defined || !bbox_data.is_valid()) {
        return std::nullopt;
    }
    bbox_data.bbox_all = {bbox_all.min.x(), bbox_all.min.y(), bbox_all.max.x(), bbox_all.max.y()};

    std::vector<unsigned int> filament_ids = print.get_slice_used_filaments(false);
    if (filament_ids.empty()) {
        filament_ids.push_back(first_extruder);
    }
    const auto* filament_colors = print.config().option<Slic3r::ConfigOptionStrings>("filament_colour");
    for (const unsigned int filament_id : filament_ids) {
        bbox_data.filament_ids.push_back(static_cast<int>(filament_id));
        if (filament_colors != nullptr && !filament_colors->values.empty()) {
            bbox_data.filament_colors.push_back(filament_colors->get_at(filament_id));
        } else {
            bbox_data.filament_colors.emplace_back("#FFFFFF");
        }
    }

    return bbox_data;
}

static void prepare_orca_pa_pattern_model(
    const std::string& json,
    Slic3r::Model& model,
    Slic3r::DynamicPrintConfig& config,
    const Slic3r::Calib_Params& params
) {
    if (model.objects.empty() || model.objects.front() == nullptr || model.objects.front()->volumes.empty()) {
        return;
    }

    const double nozzle_diameter = config.option<Slic3r::ConfigOptionFloats>("nozzle_diameter")->get_at(0);
    config.set_key_value("initial_layer_speed", new Slic3r::ConfigOptionFloat(30.0));
    config.set_key_value("line_width", new Slic3r::ConfigOptionFloatOrPercent(nozzle_diameter * 1.125, false));
    config.set_key_value("initial_layer_line_width", new Slic3r::ConfigOptionFloatOrPercent(nozzle_diameter * 1.4, false));
    config.set_key_value("skirt_loops", new Slic3r::ConfigOptionInt(0));
    config.set_key_value("wall_loops", new Slic3r::ConfigOptionInt(3));
    config.set_key_value("brim_type", new Slic3r::ConfigOptionEnum<Slic3r::BrimType>(Slic3r::BrimType::btNoBrim));
    config.set_key_value("print_sequence", new Slic3r::ConfigOptionEnum<Slic3r::PrintSequence>(Slic3r::PrintSequence::ByLayer));

    std::vector<double> speeds = params.speeds;
    if (speeds.empty()) {
        const double speed = Slic3r::CalibPressureAdvance::find_optimal_PA_speed(
            config,
            config.get_abs_value("line_width", nozzle_diameter),
            config.get_abs_value("layer_height"),
            params.extruder_id,
            0
        );
        config.set_key_value("outer_wall_speed", new Slic3r::ConfigOptionFloat(speed));
        speeds.push_back(speed);
    } else if (speeds.size() == 1) {
        config.set_key_value("outer_wall_speed", new Slic3r::ConfigOptionFloat(speeds.front()));
    }

    std::vector<double> accelerations = params.accelerations;
    if (accelerations.empty()) {
        double acceleration = config.opt_float("outer_wall_acceleration", 0.0);
        if (acceleration == 0.0) {
            acceleration = config.opt_float("inner_wall_acceleration", 0.0);
        }
        if (acceleration == 0.0) {
            acceleration = config.opt_float("default_acceleration", 0.0);
        }
        if (acceleration > 0.0) {
            config.set_key_value("outer_wall_acceleration", new Slic3r::ConfigOptionFloat(acceleration));
            accelerations.push_back(acceleration);
        }
    } else if (accelerations.size() == 1) {
        config.set_key_value("outer_wall_acceleration", new Slic3r::ConfigOptionFloat(accelerations.front()));
    } else {
        config.set_key_value(
            "outer_wall_acceleration",
            new Slic3r::ConfigOptionFloat(*std::max_element(accelerations.begin(), accelerations.end()))
        );
    }

    if (accelerations.empty()) {
        accelerations.push_back(config.opt_float("outer_wall_acceleration", 1200.0));
    }

    Slic3r::ModelObject* base_object = model.objects.front();
    if (base_object->instances.empty()) {
        base_object->add_instance();
    }

    Slic3r::Vec3d plate_origin(0.0, 0.0, 0.0);
    Slic3r::CalibPressureAdvancePattern pa_pattern(params, config, true, *base_object, plate_origin);

    const double bed_width = extract_number(json, "bed_width_mm").value_or(0.0);
    const double bed_depth = extract_number(json, "bed_depth_mm").value_or(0.0);
    const double footprint_x = pa_pattern.print_size_x() + 4.0;
    const double footprint_y = pa_pattern.print_size_y() + 4.0;
    const size_t test_count = std::max<size_t>(1, speeds.size() * accelerations.size());
    const size_t columns = bed_width > footprint_x
        ? std::max<size_t>(1, static_cast<size_t>(std::floor(bed_width / footprint_x)))
        : 1;
    const size_t rows = static_cast<size_t>(std::ceil(static_cast<double>(test_count) / static_cast<double>(columns)));
    const double total_width = std::min<double>(static_cast<double>(std::min(columns, test_count)) * footprint_x, bed_width > 0.0 ? bed_width : footprint_x);
    const double total_depth = std::min<double>(static_cast<double>(rows) * footprint_y, bed_depth > 0.0 ? bed_depth : footprint_y);
    const double start_x = std::max(0.0, (bed_width - total_width) / 2.0);
    const double start_y = std::max(0.0, (bed_depth - total_depth) / 2.0);

    while (model.objects.size() < test_count) {
        Slic3r::ModelObject* clone = model.add_object(*base_object);
        if (clone->instances.empty()) {
            clone->add_instance();
        }
    }
    while (model.objects.size() > test_count) {
        model.delete_object(model.objects.size() - 1);
    }

    std::vector<Slic3r::CustomGCode::Info> generated;
    generated.reserve(test_count);
    for (size_t test_index = 0; test_index < test_count; ++test_index) {
        Slic3r::ModelObject* object = model.objects[test_index];
        if (object == nullptr || object->volumes.empty()) {
            continue;
        }
        if (object->instances.empty()) {
            object->add_instance();
        }

        const double speed = speeds[test_index % speeds.size()];
        const double acceleration = accelerations[test_index / speeds.size()];
        object->name = "pa_pattern_" + std::to_string(static_cast<int>(std::lround(speed))) +
            "_" + std::to_string(static_cast<int>(std::lround(acceleration)));
        if (speeds.size() > 1) {
            object->config.set_key_value("outer_wall_speed", new Slic3r::ConfigOptionFloat(speed));
        }
        if (accelerations.size() > 1) {
            object->config.set_key_value("outer_wall_acceleration", new Slic3r::ConfigOptionFloat(acceleration));
        }

        const size_t column = test_index % columns;
        const size_t row = test_index / columns;
        const Slic3r::Vec3d pattern_offset(
            start_x + column * footprint_x + pa_pattern.print_size_x() / 2.0,
            start_y + row * footprint_y + pa_pattern.print_size_y() / 2.0,
            0.0
        );
        const Slic3r::Vec3d object_offset = pattern_offset + pa_pattern.handle_pos_offset();
        object->instances.front()->set_offset(object_offset);
        object->ensure_on_bed();

        Slic3r::CalibPressureAdvancePattern object_pattern(params, config, true, *object, plate_origin);
        object_pattern.set_start_offset(pattern_offset - Slic3r::Vec3d(pa_pattern.print_size_x() / 2.0, pa_pattern.print_size_y() / 2.0, 0.0));
        generated.push_back(object_pattern.generate_custom_gcodes(config, true, *object, plate_origin));
    }

    if (generated.empty()) {
        return;
    }

    Slic3r::CustomGCode::Info combined = std::move(generated.front());
    for (size_t generated_index = 1; generated_index < generated.size(); ++generated_index) {
        Slic3r::CustomGCode::Info& next = generated[generated_index];
        const size_t layer_count = std::min(combined.gcodes.size(), next.gcodes.size());
        for (size_t layer_index = 0; layer_index < layer_count; ++layer_index) {
            combined.gcodes[layer_index].extra += next.gcodes[layer_index].extra;
        }
    }

    model.curr_plate_index = 0;
    model.plates_custom_gcodes[0] = std::move(combined);
    model.calib_pa_pattern = std::make_unique<Slic3r::CalibPressureAdvancePattern>(pa_pattern);
}

static void apply_orca_calibration_model_preparation(
    const std::string& json,
    Slic3r::Model& model,
    Slic3r::DynamicPrintConfig& config
)
{
    if (!extract_bool(json, "mobile_slicer_calibration_active").value_or(false) || model.objects.empty()) {
        return;
    }

    const Slic3r::Calib_Params params = mobileslicer::orca_wrapper::extract_calibration_params(json);
    if (params.mode == Slic3r::CalibMode::Calib_None) {
        return;
    }

    for (Slic3r::ModelObject* object : model.objects) {
        if (object != nullptr && object->instances.empty()) {
            object->add_instance();
        }
    }

    switch (params.mode) {
    case Slic3r::CalibMode::Calib_PA_Pattern: {
        prepare_orca_pa_pattern_model(json, model, config, params);
        break;
    }
    case Slic3r::CalibMode::Calib_PA_Tower: {
        const double height = std::ceil((params.end - params.start) / params.step) + 1.0;
        if (height > 0.0 && height < first_model_object_height(model)) {
            cut_first_model_object_for_calibration(model, height, Slic3r::ModelObjectCutAttribute::KeepLower);
        }
        break;
    }
    case Slic3r::CalibMode::Calib_Temp_Tower: {
        double block_count = std::lround((500.0 - params.end) / 5.0 + 1.0);
        if (block_count > 0.0) {
            const double height = block_count * 10.0 - EPSILON;
            if (height < first_model_object_height(model)) {
                cut_first_model_object_for_calibration(model, height, Slic3r::ModelObjectCutAttribute::KeepLower);
            }
        }
        block_count = std::lround((500.0 - params.start) / 5.0);
        if (block_count > 0.0) {
            const double height = block_count * 10.0 + EPSILON;
            if (height < first_model_object_height(model)) {
                cut_first_model_object_for_calibration(model, height, Slic3r::ModelObjectCutAttribute::KeepUpper);
            }
        }
        break;
    }
    case Slic3r::CalibMode::Calib_Vol_speed_Tower: {
        if (Slic3r::ModelObject* object = model.objects.front()) {
            const double bed_width = extract_number(json, "bed_width_mm").value_or(0.0);
            const double object_width = object->bounding_box_exact().size().x();
            if (bed_width > 10.0 && object_width > 0.0) {
                const double scale = (bed_width - 10.0) / object_width;
                if (scale > 0.0 && scale < 1.0) {
                    object->scale(scale, 1.0, 1.0);
                }
            }
        }
        const double height = (params.end - params.start + 1.0) / params.step;
        if (height > 0.0 && height < first_model_object_height(model)) {
            cut_first_model_object_for_calibration(model, height, Slic3r::ModelObjectCutAttribute::KeepLower);
        }
        break;
    }
    case Slic3r::CalibMode::Calib_VFA_Tower: {
        const double height = 5.0 * ((params.end - params.start) / params.step + 1.0);
        if (height > 0.0 && height < first_model_object_height(model)) {
            cut_first_model_object_for_calibration(model, height, Slic3r::ModelObjectCutAttribute::KeepLower);
        }
        break;
    }
    case Slic3r::CalibMode::Calib_Retraction_tower: {
        const double layer_height = std::max(0.01, extract_number(json, "layer_height").value_or(0.2));
        const double height = 1.0 + 0.4 + ((params.end - params.start) / params.step) - layer_height;
        if (height > 0.0 && height < first_model_object_height(model)) {
            cut_first_model_object_for_calibration(model, height, Slic3r::ModelObjectCutAttribute::KeepLower);
        }
        break;
    }
    default:
        break;
    }
}

static Slic3r::Calib_Params adjust_orca_calibration_params_for_android_config(
    const std::string& json,
    Slic3r::Calib_Params params
) {
    if (params.mode == Slic3r::CalibMode::Calib_Vol_speed_Tower) {
        const double nozzle_diameter = extract_number(json, "nozzle_diameter").value_or(0.4);
        const double line_width = nozzle_diameter * 1.75;
        const double layer_height = nozzle_diameter * 0.8;
        const double flow_ratio = extract_number(json, "filament_flow_ratio").value_or(1.0);
        const double mm3_per_mm = Slic3r::Flow(line_width, layer_height, nozzle_diameter).mm3_per_mm() * flow_ratio;
        if (std::isfinite(mm3_per_mm) && mm3_per_mm > 0.0) {
            params.start /= mm3_per_mm;
            params.end /= mm3_per_mm;
            params.step /= mm3_per_mm;
        }
    }
    return params;
}

static void apply_calibration_slice_safety_overrides(
    const std::string& json,
    Slic3r::DynamicPrintConfig& config
) {
    if (!extract_bool(json, "mobile_slicer_calibration_active").value_or(false)) {
        return;
    }
    if (extract_string(json, "mobile_slicer_calibration_type").value_or("") == "FlowRate") {
        return;
    }
    // Calibration models are generated and cut outside Orca's normal object
    // labelling flow. Keep object labels disabled so G-code export does not
    // attempt to encode stale label object ids after calibration preparation.
    config.set_deserialize_strict("gcode_label_objects", "0");
}

extern "C" int orca_set_model_placement(OrcaEngine* engine, double x_mm, double y_mm, double z_mm)
{
    if (engine == nullptr || !std::isfinite(x_mm) || !std::isfinite(y_mm) || !std::isfinite(z_mm)) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);

    try {
        if (!engine->impl.model.has_value()) {
            set_last_error(engine, "no model loaded");
            return ORCA_ERROR_LOAD_MODEL;
        }
        Slic3r::Model& model = *engine->impl.model;
        if (model.objects.empty()) {
            set_last_error(engine, "no model loaded");
            return ORCA_ERROR_LOAD_MODEL;
        }

        const Slic3r::Vec3d placement(x_mm, y_mm, z_mm);
        for (Slic3r::ModelObject* object : model.objects) {
            if (object == nullptr) {
                continue;
            }
            if (object->instances.empty()) {
                object->add_instance();
            }
            for (Slic3r::ModelInstance* instance : object->instances) {
                if (instance != nullptr) {
                    instance->set_offset(placement);
                }
            }
            object->invalidate_bounding_box();
        }
        clear_generated_gcode(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_set_model_placement", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_set_model_placement", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" int orca_set_model_transform(
    OrcaEngine* engine,
    double x_mm,
    double y_mm,
    double z_mm,
    double rotation_x_radians,
    double rotation_y_radians,
    double rotation_z_radians,
    double uniform_scale
) {
    if (engine == nullptr ||
        !std::isfinite(x_mm) ||
        !std::isfinite(y_mm) ||
        !std::isfinite(z_mm) ||
        !std::isfinite(rotation_x_radians) ||
        !std::isfinite(rotation_y_radians) ||
        !std::isfinite(rotation_z_radians) ||
        !std::isfinite(uniform_scale) ||
        uniform_scale <= 0.0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);

    try {
        if (!engine->impl.model.has_value()) {
            set_last_error(engine, "no model loaded");
            return ORCA_ERROR_LOAD_MODEL;
        }
        Slic3r::Model& model = *engine->impl.model;
        if (model.objects.empty()) {
            set_last_error(engine, "no model loaded");
            return ORCA_ERROR_LOAD_MODEL;
        }

        const Slic3r::Vec3d offset(x_mm, y_mm, z_mm);
        const Slic3r::Vec3d rotation(rotation_x_radians, rotation_y_radians, rotation_z_radians);
        const Slic3r::Vec3d scaling(uniform_scale, uniform_scale, uniform_scale);
        for (Slic3r::ModelObject* object : model.objects) {
            if (object == nullptr) {
                continue;
            }
            if (object->instances.empty()) {
                object->add_instance();
            }
            for (Slic3r::ModelInstance* instance : object->instances) {
                if (instance != nullptr) {
                    instance->set_scaling_factor(scaling);
                    instance->set_rotation(rotation);
                    instance->set_offset(offset);
                }
            }
            object->invalidate_bounding_box();
        }
        clear_generated_gcode(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_set_model_transform", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_set_model_transform", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" int orca_set_config_json(OrcaEngine* engine, const char* json)
{
    if (engine == nullptr || json == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);

    try {
        engine->impl.config_json = json;
        invalidate_json_scalar_index();
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_set_config_json", exception.what());
        return ORCA_ERROR_CONFIG;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_set_config_json", "unknown exception");
        return ORCA_ERROR_CONFIG;
    }
}

extern "C" int orca_slice(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    configure_android_slice_runtime_once();
    if (!engine->impl.model.has_value()) {
        set_last_error(engine, "no model loaded");
        return ORCA_ERROR_SLICE;
    }
    clear_last_error(engine);
    clear_generated_gcode(engine);
    invalidate_json_scalar_index();

    const char* slice_stage = "start";
    const auto log_slice_exception = [&](const std::string& message) {
        std::ostringstream detail;
        detail << "stage=" << slice_stage << " error=" << message;
        set_last_error(engine, detail.str());
        log_native_error("orca_slice", detail.str().c_str());
    };

    try {
        slice_stage = "initialize";
        const auto total_start = std::chrono::steady_clock::now();
        auto stage_start = total_start;
        const auto log_stage_elapsed = [&](const char* stage_name) {
            if (!kVerboseNativeTimingLogs) {
                stage_start = std::chrono::steady_clock::now();
                return;
            }
            const auto now = std::chrono::steady_clock::now();
            const auto stage_ms = std::chrono::duration_cast<std::chrono::milliseconds>(now - stage_start).count();
            const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(now - total_start).count();
            std::ostringstream message;
            message << stage_name << " stageMs=" << stage_ms << " totalMs=" << total_ms;
            log_native_info("orca_slice", message.str());
            stage_start = now;
        };

        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message << "begin objects=" << engine->impl.model->objects.size();
            log_native_info("orca_slice", message.str());
        }

        slice_stage = "config";
        const auto config_full_start = std::chrono::steady_clock::now();
        Slic3r::DynamicPrintConfig config = Slic3r::DynamicPrintConfig::full_print_config();
        const long config_full_ms = elapsed_ms_since(config_full_start);
        const auto config_seed_start = std::chrono::steady_clock::now();
        config.set_deserialize_strict("gcode_comments", "1");
        config.set_deserialize_strict("start_gcode", "");
        // Keep the shipping wrapper aligned with the bounded reference parity probe:
        // FullPrintConfig::defaults() seeds a non-empty machine_start_gcode block,
        // which otherwise injects setup-only commands like G28 / G1 Z5 into MobileSlicer output.
        config.set_deserialize_strict("machine_start_gcode", "");
        // The same seeded config base carries a non-empty machine_end_gcode block.
        // Blank it here so the Android wrapper follows the parity probe's bounded
        // neutralized end-command baseline instead of appending preset finalization.
        config.set_deserialize_strict("machine_end_gcode", "");
        const long config_seed_ms = elapsed_ms_since(config_seed_start);
        const auto config_override_start = std::chrono::steady_clock::now();
        apply_json_overrides(engine->impl.config_json, config);
        apply_calibration_slice_safety_overrides(engine->impl.config_json, config);
        const long config_override_ms = elapsed_ms_since(config_override_start);
        const auto config_runtime_start = std::chrono::steady_clock::now();
        apply_android_runtime_gcode_baseline(config);
        clamp_wipe_tower_to_printable_area(config);
        const long config_runtime_ms = elapsed_ms_since(config_runtime_start);
        const auto config_bounds_start = std::chrono::steady_clock::now();
        const PrintableVolumeBounds printable_bounds = extract_printable_volume_bounds(config);
        const long config_bounds_ms = elapsed_ms_since(config_bounds_start);
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "config_detail"
                << " fullMs=" << config_full_ms
                << " seedMs=" << config_seed_ms
                << " overridesMs=" << config_override_ms
                << " runtimeBaselineMs=" << config_runtime_ms
                << " boundsMs=" << config_bounds_ms
                << " jsonBytes=" << engine->impl.config_json.size();
            log_native_info("orca_slice", message.str());
        }
        log_stage_elapsed("config");

        Slic3r::Print print;
        print.set_plate_origin(Slic3r::Vec3d::Zero());
        Slic3r::Model model = *engine->impl.model;

        slice_stage = "prepare_model";
        const auto calibration_model_start = std::chrono::steady_clock::now();
        apply_orca_calibration_model_preparation(engine->impl.config_json, model, config);
        const long calibration_model_ms = elapsed_ms_since(calibration_model_start);

        const auto object_overrides_start = std::chrono::steady_clock::now();
        apply_model_object_overrides(engine->impl.config_json, model);
        const long object_overrides_ms = elapsed_ms_since(object_overrides_start);

        const auto instance_start = std::chrono::steady_clock::now();
        size_t added_instances = 0;
        for (Slic3r::ModelObject* object : model.objects) {
            if (object->instances.empty()) {
                object->add_instance();
                ++added_instances;
            }
        }
        const long instance_ms = elapsed_ms_since(instance_start);

        const auto bed_assign_start = std::chrono::steady_clock::now();
        for (Slic3r::ModelObject* object : model.objects) {
            object->ensure_on_bed();
            print.auto_assign_extruders(object);
        }
        const long bed_assign_ms = elapsed_ms_since(bed_assign_start);
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "prepare_model_detail"
                << " calibrationModelMs=" << calibration_model_ms
                << " objectOverridesMs=" << object_overrides_ms
                << " instanceMs=" << instance_ms
                << " bedAssignMs=" << bed_assign_ms
                << " addedInstances=" << added_instances
                << " objects=" << model.objects.size();
            log_native_info("orca_slice", message.str());
        }
        log_stage_elapsed("prepare_model");

        slice_stage = "print_apply";
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "orca_config_vectors_pre_apply"
                << " nozzle_diameter_size=" << config_option_size(config, "nozzle_diameter")
                << " nozzle_diameter=" << config_option_serialized(config, "nozzle_diameter")
                << " nozzle_volume_size=" << config_option_size(config, "nozzle_volume")
                << " nozzle_volume=" << config_option_serialized(config, "nozzle_volume")
                << " extruder_printable_area_size=" << config_option_size(config, "extruder_printable_area")
                << " extruder_printable_area=" << config_option_serialized(config, "extruder_printable_area")
                << " physical_extruder_map_size=" << config_option_size(config, "physical_extruder_map")
                << " physical_extruder_map=" << config_option_serialized(config, "physical_extruder_map")
                << " printer_extruder_id_size=" << config_option_size(config, "printer_extruder_id")
                << " printer_extruder_id=" << config_option_serialized(config, "printer_extruder_id")
                << " filament_map_mode=" << config_option_serialized(config, "filament_map_mode")
                << " filament_map_size=" << config_option_size(config, "filament_map")
                << " filament_map=" << config_option_serialized(config, "filament_map")
                << " flush_volumes_matrix_size=" << config_option_size(config, "flush_volumes_matrix")
                << " flush_volumes_matrix=" << config_option_serialized(config, "flush_volumes_matrix")
                << " semm=" << config_option_serialized(config, "single_extruder_multi_material")
                << " mobile_slots=" << config_option_serialized(config, "mobile_slicer_active_filament_slot_count")
                << " mobile_nozzles=" << config_option_serialized(config, "mobile_slicer_physical_nozzle_count");
            log_native_info("orca_slice", message.str());
        }
        const auto apply_start = std::chrono::steady_clock::now();
        print.apply(model, config);
        const long apply_ms = elapsed_ms_since(apply_start);
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "orca_config_vectors"
                << " filament_diameter_size=" << config_option_size(config, "filament_diameter")
                << " filament_diameter=" << config_option_serialized(config, "filament_diameter")
                << " filament_type_size=" << config_option_size(config, "filament_type")
                << " filament_type=" << config_option_serialized(config, "filament_type")
                << " filament_colour_size=" << config_option_size(config, "filament_colour")
                << " filament_colour=" << config_option_serialized(config, "filament_colour")
                << " filament_map_size=" << config_option_size(config, "filament_map")
                << " filament_map=" << config_option_serialized(config, "filament_map")
                << " nozzle_temperature_size=" << config_option_size(config, "nozzle_temperature")
                << " nozzle_temperature=" << config_option_serialized(config, "nozzle_temperature")
                << " semm=" << config_option_serialized(config, "single_extruder_multi_material")
                << " prime_tower=" << config_option_serialized(config, "enable_prime_tower");
            log_native_info("orca_slice", message.str());
        }
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message << "orca_model_extruders";
            for (size_t object_index = 0; object_index < model.objects.size(); ++object_index) {
                const Slic3r::ModelObject* object = model.objects[object_index];
                if (object == nullptr) {
                    continue;
                }
                message << " object" << object_index << "=" << object->config.extruder();
                message << " volumes=[";
                for (size_t volume_index = 0; volume_index < object->volumes.size(); ++volume_index) {
                    const Slic3r::ModelVolume* volume = object->volumes[volume_index];
                    if (volume_index > 0) {
                        message << ",";
                    }
                    message << (volume != nullptr ? volume->extruder_id() : 0);
                }
                message << "]";
            }
            log_native_info("orca_slice", message.str());
        }
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message << "orca_print_extruders all=[";
            const std::vector<unsigned int> extruders = print.object_extruders();
            for (size_t index = 0; index < extruders.size(); ++index) {
                if (index > 0) {
                    message << ",";
                }
                message << extruders[index];
            }
            message << "]";
            log_native_info("orca_slice", message.str());
        }
        if (kVerboseNativeTimingLogs) {
            log_print_region_extruders("orca_regions_after_apply", print);
        }
        const auto calibration_start = std::chrono::steady_clock::now();
        const auto calibration_params = adjust_orca_calibration_params_for_android_config(
            engine->impl.config_json,
            extract_calibration_params(engine->impl.config_json)
        );
        long set_calibration_ms = 0;
        if (calibration_params.mode != Slic3r::CalibMode::Calib_None) {
            const auto set_calibration_start = std::chrono::steady_clock::now();
            print.set_calib_params(calibration_params);
            set_calibration_ms = elapsed_ms_since(set_calibration_start);
        }
        const long calibration_ms = elapsed_ms_since(calibration_start);
        slice_stage = "print_validate";
        const auto validate_start = std::chrono::steady_clock::now();
        print.validate();
        const long validate_ms = elapsed_ms_since(validate_start);
        const auto status_start = std::chrono::steady_clock::now();
        print.set_status_silent();
        const long status_ms = elapsed_ms_since(status_start);
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "apply_validate_detail"
                << " applyMs=" << apply_ms
                << " calibrationExtractMs=" << calibration_ms
                << " calibrationSetMs=" << set_calibration_ms
                << " validateMs=" << validate_ms
                << " statusMs=" << status_ms;
            log_native_info("orca_slice", message.str());
        }
        log_stage_elapsed("apply_validate");

        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "process_input"
                << " layer_height=" << extract_number(engine->impl.config_json, "layer_height").value_or(-1.0)
                << " first_layer_height=" << extract_number(engine->impl.config_json, "first_layer_height").value_or(-1.0)
                << " nozzle_diameter=" << extract_number(engine->impl.config_json, "nozzle_diameter").value_or(-1.0)
                << " prime_tower=" << (extract_bool(engine->impl.config_json, "enable_prime_tower").value_or(false) ? 1 : 0)
                << " semm=" << (extract_bool(engine->impl.config_json, "single_extruder_multi_material").value_or(false) ? 1 : 0)
                << " support=" << (extract_bool(engine->impl.config_json, "enable_support").value_or(false) ? 1 : 0)
                << " objects=" << model.objects.size();
            if (const auto printer = extract_string(engine->impl.config_json, "printer_model")) {
                message << " printer_model=" << *printer;
            }
            if (const auto printer_settings = extract_string(engine->impl.config_json, "printer_settings_id")) {
                message << " printer_settings_id=" << *printer_settings;
            }
            if (const auto filament_settings = extract_string(engine->impl.config_json, "filament_settings_id")) {
                message << " filament_settings_id=" << *filament_settings;
            }
            if (const auto filament_ids = extract_string(engine->impl.config_json, "filament_ids")) {
                message << " filament_ids=" << *filament_ids;
            }
            if (const auto filament_vendor = extract_string(engine->impl.config_json, "filament_vendor")) {
                message << " filament_vendor=" << *filament_vendor;
            }
            if (const auto process = extract_string(engine->impl.config_json, "print_settings_id")) {
                message << " print_settings_id=" << *process;
            }
            log_native_info("orca_slice", message.str());
        }

        slice_stage = "print_process";
        print.process();
        log_stage_elapsed("process");
        if (kVerboseNativeTimingLogs) {
            log_print_region_extruders("orca_regions_after_process", print);
        }
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message << "support_summary objects=" << print.objects().size();
            for (size_t index = 0; index < print.objects().size(); ++index) {
                const auto* object = print.objects()[index];
                if (object == nullptr) {
                    continue;
                }
                size_t support_layers = object->support_layers().size();
                message
                    << " [idx=" << index
                    << " enable_support=" << (object->config().enable_support.value ? 1 : 0)
                    << " support_type=" << static_cast<int>(object->config().support_type.value)
                    << " buildplate_only=" << (object->config().support_on_build_plate_only.value ? 1 : 0)
                    << " max_bridge_length=" << object->config().max_bridge_length.value
                    << " support_layers=" << support_layers
                    << "]";
            }
            log_native_info("orca_slice", message.str());
        }

        slice_stage = "export_gcode";
        const auto temp_path_start = std::chrono::steady_clock::now();
        const auto gcode_path = make_temp_gcode_path();
        const long temp_path_ms = elapsed_ms_since(temp_path_start);
        Slic3r::GCodeProcessorResult gcode_result;
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message << "export_gcode attempt=1 tempPathMs=" << temp_path_ms;
            log_native_info("orca_slice", message.str());
        }
        Slic3r::ThumbnailsGeneratorCallback thumbnail_cb =
            [engine](const Slic3r::ThumbnailsParams& params) -> Slic3r::ThumbnailsList {
            Slic3r::ThumbnailsList thumbnails;
            for (const Slic3r::Vec2d& size : params.sizes) {
                const unsigned int requested_width = static_cast<unsigned int>(std::lround(size(0)));
                const unsigned int requested_height = static_cast<unsigned int>(std::lround(size(1)));
                if (requested_width == 0 || requested_height == 0) {
                    continue;
                }
                const auto match = std::find_if(
                    engine->impl.slice_thumbnails.begin(),
                    engine->impl.slice_thumbnails.end(),
                    [requested_width, requested_height](const OrcaEngineImpl::SliceThumbnailRgba& thumbnail) {
                        return thumbnail.role == "gcode" &&
                            thumbnail.width == requested_width &&
                            thumbnail.height == requested_height;
                    });
                if (match == engine->impl.slice_thumbnails.end()) {
                    continue;
                }
                Slic3r::ThumbnailData data;
                data.set(match->width, match->height);
                data.pixels = match->rgba;
                if (data.is_valid()) {
                    thumbnails.push_back(std::move(data));
                }
            }
            return thumbnails;
        };
        Slic3r::ThumbnailsGeneratorCallback* thumbnail_cb_ptr =
            engine->impl.slice_thumbnails.empty() ? nullptr : &thumbnail_cb;
        const auto export_call_start = std::chrono::steady_clock::now();
        print.export_gcode(
            gcode_path.string(),
            &gcode_result,
            thumbnail_cb_ptr != nullptr ? *thumbnail_cb_ptr : Slic3r::ThumbnailsGeneratorCallback{});
        const long export_call_ms = elapsed_ms_since(export_call_start);
        engine->impl.sliced_plate_bbox_data = build_sliced_plate_bbox_data(print, gcode_result);
        if (engine->impl.sliced_plate_bbox_data.has_value()) {
            const Slic3r::PlateBBoxData& bbox_data = *engine->impl.sliced_plate_bbox_data;
            log_native_info(
                "orca_slice",
                "sliced_3mf_bbox objects=" + std::to_string(bbox_data.bbox_objs.size()) +
                    " filaments=" + std::to_string(bbox_data.filament_ids.size()) +
                    " firstLayerTime=" + std::to_string(bbox_data.first_layer_time));
        } else {
            log_native_info("orca_slice", "sliced_3mf_bbox unavailable");
        }
        log_stage_elapsed("export_gcode");
        if ((gcode_result.gcode_check_result.error_code & ORCA_PLATE_PRINTABLE_AREA_ERROR) != 0 ||
            (gcode_result.gcode_check_result.error_code & ORCA_PLATE_PRINTABLE_HEIGHT_ERROR) != 0) {
            const auto violation = detect_printable_volume_violation(gcode_path, printable_bounds);
            if (!violation.any()) {
                log_native_info("orca_slice",
                    "printable volume warning ignored errorCode=" + std::to_string(gcode_result.gcode_check_result.error_code) +
                    " reason=no_print_extrusion_outside_bounds");
            } else {
            std::ostringstream message;
            message << "printable volume exceeded errorCode=" << gcode_result.gcode_check_result.error_code;
            if (printable_bounds.valid) {
                message
                    << " printableBounds=["
                    << printable_bounds.min_x << "," << printable_bounds.max_x
                    << "]x[" << printable_bounds.min_y << "," << printable_bounds.max_y
                    << "] z<=" << printable_bounds.max_z;
            } else {
                message << " printableBounds=invalid";
            }
            if (violation.has_extrusion) {
                message
                    << " emittedBounds=["
                    << violation.min_x << "," << violation.max_x
                    << "]x[" << violation.min_y << "," << violation.max_y
                    << "] z<=" << violation.max_z;
            } else {
                message << " emittedBounds=none";
            }
            message
                << " fallbackAreaExceeded=" << (violation.printable_area_exceeded ? 1 : 0)
                << " fallbackHeightExceeded=" << (violation.printable_height_exceeded ? 1 : 0);
            if (violation.offending_line != 0) {
                message
                    << " offendingLine=" << violation.offending_line
                    << " offendingGcode=\"" << violation.offending_gcode << "\"";
            }
            message << " failedGcode=" << gcode_path.string();
            set_last_error(engine, message.str());
            log_native_error("orca_slice", message.str().c_str());
            return ORCA_ERROR_SLICE;
            }
        }
        const auto file_stat_start = std::chrono::steady_clock::now();
        std::error_code ec;
        const uintmax_t gcode_size = std::filesystem::file_size(gcode_path, ec);
        const long file_stat_ms = elapsed_ms_since(file_stat_start);
        if (ec || gcode_size == 0) {
            std::filesystem::remove(gcode_path, ec);
            set_last_error(engine, "export completed but no G-code was produced");
            return ORCA_ERROR_SLICE;
        }
        engine->impl.gcode_path = gcode_path;
        engine->impl.gcode_path_owned = true;
        slice_stage = "summarize_gcode";
        const auto summary_parse_start = std::chrono::steady_clock::now();
        engine->impl.gcode_summary = summarize_gcode_file_for_android(gcode_path);
        engine->impl.gcode_summary = enrich_gcode_summary_from_processor(engine->impl.gcode_summary, gcode_result, &print);
        engine->impl.gcode_summary_enriched = true;
        std::string slice_metrics;
#if defined(MOBILE_SLICER_ENABLE_VGCODE)
        engine->impl.cached_preview_input = libvgcode::GCodeInputData{};
        engine->impl.cached_preview_source_size = 0;
        const size_t processor_move_count =
            gcode_result.released_move_count > 0 ? gcode_result.released_move_count : gcode_result.moves.size();
        const size_t processor_move_bytes =
            gcode_result.released_move_bytes > 0 ?
                gcode_result.released_move_bytes :
                processor_move_count * sizeof(Slic3r::GCodeProcessorResult::MoveVertex);
        const size_t processor_line_end_count =
            gcode_result.released_line_end_count > 0 ? gcode_result.released_line_end_count : gcode_result.lines_ends.size();
        const size_t processor_line_end_bytes =
            gcode_result.released_line_end_bytes > 0 ?
                gcode_result.released_line_end_bytes :
                processor_line_end_count * sizeof(size_t);
        const bool processor_moves_available = gcode_result.moves.size() >= 2;
        const bool exact_preview_cache_eligible =
            processor_moves_available &&
            gcode_result.moves.size() * static_cast<size_t>(2) <= static_cast<size_t>(kMaxCachedPreviewVertices);
        const bool processor_layer_counts_available =
            !gcode_result.mobile_preview_layer_vertex_counts.empty();
        if (processor_layer_counts_available) {
            engine->impl.cached_preview_layer_counts = gcode_result.mobile_preview_layer_vertex_counts;
            engine->impl.cached_preview_layer_counts_source_size = static_cast<size_t>(gcode_size);
        } else if (exact_preview_cache_eligible) {
            engine->impl.cached_preview_layer_counts = count_preview_vertices_by_layer_from_processor_result(gcode_result);
            engine->impl.cached_preview_layer_counts_source_size = static_cast<size_t>(gcode_size);
        } else {
            engine->impl.cached_preview_layer_counts.clear();
            engine->impl.cached_preview_layer_counts_source_size = 0;
        }
        engine->impl.cached_preview_valid = false;
        engine->impl.cached_preview_complete = false;
        const size_t preview_moves = processor_move_count;
        size_t processor_preview_vertices = 0;
        long processor_preview_build_ms = 0;
        if (exact_preview_cache_eligible) {
            const auto processor_preview_start = std::chrono::steady_clock::now();
            bool processor_preview_limit_reached = false;
            engine->impl.cached_preview_input = to_vgcode_input_data_from_processor_result(
                gcode_result,
                kMaxCachedPreviewVertices,
                &processor_preview_limit_reached);
            apply_preview_palette_from_config_json(engine->impl.cached_preview_input, engine->impl.config_json);
            engine->impl.cached_preview_source_size = static_cast<size_t>(gcode_size);
            engine->impl.cached_preview_valid = !engine->impl.cached_preview_input.vertices.empty();
            engine->impl.cached_preview_complete = engine->impl.cached_preview_valid && !processor_preview_limit_reached;
            processor_preview_vertices = engine->impl.cached_preview_input.vertices.size();
            processor_preview_build_ms = elapsed_ms_since(processor_preview_start);
            if (kVerboseNativeTimingLogs) {
                log_native_info(
                    "gcode_processor_preview_cache",
                    "moves=" + std::to_string(gcode_result.moves.size()) +
                        " vertices=" + std::to_string(engine->impl.cached_preview_input.vertices.size()) +
                        " complete=" + std::string(engine->impl.cached_preview_complete ? "true" : "false") +
                        " buildMs=" + std::to_string(processor_preview_build_ms));
            }
        } else if (kVerboseNativeTimingLogs) {
            log_native_info(
                "gcode_processor_preview_cache",
                "skipped moves=" + std::to_string(gcode_result.moves.size()) +
                    " reason=too_large_for_exact_cache");
        }
        slice_metrics =
            "previewMoves=" + std::to_string(preview_moves) +
            "|previewCacheBuilt=" + std::string(engine->impl.cached_preview_valid ? "1" : "0") +
            "|previewCacheComplete=" + std::string(engine->impl.cached_preview_complete ? "1" : "0") +
            "|previewCachedVertices=" + std::to_string(processor_preview_vertices) +
            "|previewCacheBuildMs=" + std::to_string(processor_preview_build_ms) +
            "|gcodeBytes=" + std::to_string(gcode_size) +
            "|processorMoveBytes=" + std::to_string(processor_move_bytes) +
            "|processorLineEndBytes=" + std::to_string(processor_line_end_bytes) +
            "|previewLayerCountBytes=" + std::to_string(engine->impl.cached_preview_layer_counts.size() * sizeof(size_t)) +
            "|previewLayerCountsFromProcessor=" + std::string(processor_layer_counts_available ? "1" : "0") +
            "|exactPreviewCacheEligible=" + std::string(exact_preview_cache_eligible ? "1" : "0") +
            "|processorMovesReleasedDuringExport=" + std::string(gcode_result.released_move_bytes > 0 ? "1" : "0") +
            "|nativeExportStartRssKb=" + std::to_string(gcode_result.mobile_export_start_rss_kb) +
            "|nativeAfterSetupRssKb=" + std::to_string(gcode_result.mobile_after_setup_rss_kb) +
            "|nativeAfterLayersRssKb=" + std::to_string(gcode_result.mobile_after_layers_rss_kb) +
            "|nativeAfterFooterRssKb=" + std::to_string(gcode_result.mobile_after_footer_rss_kb) +
            "|nativeAfterGenerationRssKb=" + std::to_string(gcode_result.mobile_after_generation_rss_kb) +
            "|nativeAfterFinalizeRssKb=" + std::to_string(gcode_result.mobile_after_finalize_rss_kb) +
            "|nativeAfterReleaseRssKb=" + std::to_string(gcode_result.mobile_after_release_rss_kb) +
            "|nativeAfterStatsRssKb=" + std::to_string(gcode_result.mobile_after_stats_rss_kb);
#else
        slice_metrics =
            "previewMoves=0|previewCacheBuilt=0|previewCacheComplete=0|previewCachedVertices=0|previewCacheBuildMs=0"
            "|gcodeBytes=" + std::to_string(gcode_size) +
            "|processorMoveBytes=0|processorLineEndBytes=0|previewLayerCountBytes=0|exactPreviewCacheEligible=0|processorMovesReleasedDuringExport=0"
            "|nativeExportStartRssKb=0|nativeAfterSetupRssKb=0|nativeAfterLayersRssKb=0|nativeAfterFooterRssKb=0"
            "|nativeAfterGenerationRssKb=0|nativeAfterFinalizeRssKb=0|nativeAfterReleaseRssKb=0|nativeAfterStatsRssKb=0";
#endif
        const long summary_parse_ms = elapsed_ms_since(summary_parse_start);
        const float normal_print_time = gcode_result.print_statistics
            .modes[static_cast<size_t>(Slic3r::PrintEstimatedStatistics::ETimeMode::Normal)]
            .time;
        long summary_replace_ms = 0;
        if (normal_print_time > 0.0f && is_reasonable_print_time_seconds(normal_print_time)) {
            const auto summary_replace_start = std::chrono::steady_clock::now();
            engine->impl.gcode_summary = replace_summary_field(
                engine->impl.gcode_summary,
                "time",
                format_duration_seconds(static_cast<long>(std::lround(normal_print_time))));
            summary_replace_ms = elapsed_ms_since(summary_replace_start);
            if (kVerboseNativeTimingLogs) {
                log_native_info("orca_slice",
                    "summary_time_source=gcode_processor seconds=" + std::to_string(static_cast<long>(std::lround(normal_print_time))));
            }
        } else if (normal_print_time > 0.0f) {
            if (kVerboseNativeTimingLogs) {
                log_native_info("orca_slice",
                    "summary_time_source=gcode_processor_rejected seconds=" + std::to_string(static_cast<long>(std::lround(normal_print_time))) +
                    " maxSeconds=" + std::to_string(kMaxReasonablePrintTimeSeconds));
            }
        } else {
            if (kVerboseNativeTimingLogs) {
                log_native_info("orca_slice",
                    "summary_time_source=gcode_footer processorSeconds=" + std::to_string(static_cast<long>(std::lround(normal_print_time))));
            }
        }
#if defined(MOBILE_SLICER_ENABLE_VGCODE)
        const GcodeProcessorBufferReleaseStats processor_release_stats =
            release_gcode_processor_buffers(gcode_result);
        engine->impl.slice_metrics =
            slice_metrics +
            "|processorMoveBytesRetained=" + std::to_string(processor_release_stats.move_bytes_retained) +
            "|processorLineEndBytesRetained=" + std::to_string(processor_release_stats.line_end_bytes_retained) +
            "|processorReleaseMs=" + std::to_string(processor_release_stats.release_ms);
#else
        std::vector<Slic3r::GCodeProcessorResult::MoveVertex>().swap(gcode_result.moves);
        std::vector<size_t>().swap(gcode_result.lines_ends);
        engine->impl.slice_metrics =
            slice_metrics +
            "|processorMoveBytesRetained=0|processorLineEndBytesRetained=0|processorReleaseMs=0";
#endif
        const size_t native_before_return_rss_kb = current_process_rss_kb();
        engine->impl.slice_metrics +=
            "|nativeBeforeReturnRssKb=" + std::to_string(native_before_return_rss_kb);
        if (kVerboseNativeTimingLogs) {
            std::ostringstream message;
            message
                << "export_summary_detail"
                << " exportCallMs=" << export_call_ms
                << " fileStatMs=" << file_stat_ms
                << " summaryParseMs=" << summary_parse_ms
                << " summaryReplaceMs=" << summary_replace_ms
                << " bytes=" << gcode_size
                << " processorSeconds=" << static_cast<long>(std::lround(normal_print_time));
            log_native_info("orca_slice", message.str());
        }
        log_stage_elapsed("summarize_gcode");

        return ORCA_SUCCESS;
    } catch (const Slic3r::SlicingErrors& errors) {
        for (const auto& error : errors.errors_) {
            log_slice_exception(error.what());
        }
        return ORCA_ERROR_SLICE;
    } catch (const Slic3r::SlicingError& error) {
        log_slice_exception(error.what());
        return ORCA_ERROR_SLICE;
    } catch (const std::exception& exception) {
        log_slice_exception(exception.what());
        return ORCA_ERROR_SLICE;
    } catch (...) {
        log_slice_exception("unknown exception");
        return ORCA_ERROR_SLICE;
    }
}

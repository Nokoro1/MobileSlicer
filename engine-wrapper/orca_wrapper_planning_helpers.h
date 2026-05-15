#ifndef ORCA_WRAPPER_PLANNING_HELPERS_H
#define ORCA_WRAPPER_PLANNING_HELPERS_H

// Private implementation header. Include through orca_wrapper_module_context.h after config helpers.
struct PlannedPlateModel {
    Slic3r::Model model;
    std::vector<int> request_index_by_object;
};

struct NativePlateTransform {
    Slic3r::Vec3d offset;
    Slic3r::Vec3d rotation;
    Slic3r::Vec3d scaling;
    Slic3r::Transform3d orientation_matrix;
    bool has_orientation_matrix { false };
};

static std::optional<std::string> planning_model_cache_key(const char* path)
{
    if (path == nullptr || path[0] == '\0') {
        return std::nullopt;
    }
    std::error_code error;
    const std::filesystem::path file_path(path);
    if (!std::filesystem::exists(file_path, error) || error) {
        return std::nullopt;
    }
    const auto size = std::filesystem::file_size(file_path, error);
    if (error) {
        return std::nullopt;
    }
    const auto last_write = std::filesystem::last_write_time(file_path, error);
    if (error) {
        return std::nullopt;
    }
    const auto mtime = static_cast<long long>(last_write.time_since_epoch().count());
    return std::string(path) + "|size=" + std::to_string(static_cast<unsigned long long>(size)) +
        "|mtime=" + std::to_string(mtime);
}

static Slic3r::Model load_source_planning_model_from_file(const char* path)
{
    Slic3r::Model model;
    if (has_stl_extension(path)) {
        if (!Slic3r::load_stl(path, &model, nullptr, nullptr, 80)) {
            throw std::runtime_error("stl load failed");
        }
        model.add_default_instances();
        for (Slic3r::ModelObject* object : model.objects) {
            if (object != nullptr) {
                object->input_file = path;
            }
        }
    } else {
        model = Slic3r::Model::read_from_file(
            path,
            nullptr,
            nullptr,
            Slic3r::LoadStrategy::AddDefaultInstances | Slic3r::LoadStrategy::LoadModel);
    }
    for (Slic3r::ModelObject* object : model.objects) {
        if (object != nullptr && object->instances.empty()) {
            object->add_instance();
        }
    }
    if (model.objects.empty()) {
        throw std::runtime_error("loaded plate model contains no objects");
    }
    return model;
}

static const Slic3r::Model& cached_source_planning_model(OrcaEngine* engine, const char* path, bool* cache_hit = nullptr)
{
    if (engine == nullptr) {
        throw std::runtime_error("native planning cache has no engine");
    }
    const std::optional<std::string> key = planning_model_cache_key(path);
    if (!key) {
        throw std::runtime_error("plate model path is unavailable for planning cache");
    }

    ++engine->impl.planning_cache_generation;
    for (OrcaEngineImpl::PlanningModelCacheEntry& entry : engine->impl.planning_model_cache) {
        if (entry.key == *key) {
            entry.last_used_generation = engine->impl.planning_cache_generation;
            if (cache_hit != nullptr) {
                *cache_hit = true;
            }
            return entry.model;
        }
    }

    OrcaEngineImpl::PlanningModelCacheEntry entry;
    entry.key = *key;
    entry.model = load_source_planning_model_from_file(path);
    entry.last_used_generation = engine->impl.planning_cache_generation;
    engine->impl.planning_model_cache.emplace_back(std::move(entry));
    constexpr size_t max_planning_cache_entries = 6;
    while (engine->impl.planning_model_cache.size() > max_planning_cache_entries) {
        auto oldest = std::min_element(
            engine->impl.planning_model_cache.begin(),
            engine->impl.planning_model_cache.end(),
            [](const auto& left, const auto& right) {
                return left.last_used_generation < right.last_used_generation;
            });
        if (oldest == engine->impl.planning_model_cache.end()) {
            break;
        }
        engine->impl.planning_model_cache.erase(oldest);
    }
    if (cache_hit != nullptr) {
        *cache_hit = false;
    }
    return engine->impl.planning_model_cache.back().model;
}

static int prewarm_source_planning_models(OrcaEngine* engine, const char* const* paths, int count)
{
    if (engine == nullptr || paths == nullptr || count <= 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    try {
        int misses = 0;
        for (int index = 0; index < count; ++index) {
            bool cache_hit = false;
            cached_source_planning_model(engine, paths[index], &cache_hit);
            if (!cache_hit) {
                ++misses;
            }
        }
        std::ostringstream message;
        message << "count=" << count
                << " misses=" << misses
                << " entries=" << engine->impl.planning_model_cache.size();
        log_native_info("orca_prewarm_plate_planning_models", message.str());
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_prewarm_plate_planning_models", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown native planning prewarm failure");
        log_native_error("orca_prewarm_plate_planning_models", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

static NativePlateTransform read_native_plate_transform(const double* transforms, int index, int transform_stride)
{
    if (transform_stride != 7 && transform_stride != 16) {
        throw std::runtime_error("unsupported plate model transform stride");
    }
    const int transform_offset = index * transform_stride;
    const double x_mm = transforms[transform_offset + 0];
    const double y_mm = transforms[transform_offset + 1];
    const double z_mm = transforms[transform_offset + 2];
    const double uniform_scale = transform_stride == 16 ? transforms[transform_offset + 12] : transforms[transform_offset + 6];
    const double rotation_x = transform_stride == 16 ? transforms[transform_offset + 13] : transforms[transform_offset + 3];
    const double rotation_y = transform_stride == 16 ? transforms[transform_offset + 14] : transforms[transform_offset + 4];
    const double rotation_z = transform_stride == 16 ? transforms[transform_offset + 15] : transforms[transform_offset + 5];
    if (!std::isfinite(x_mm) ||
        !std::isfinite(y_mm) ||
        !std::isfinite(z_mm) ||
        !std::isfinite(rotation_x) ||
        !std::isfinite(rotation_y) ||
        !std::isfinite(rotation_z) ||
        !std::isfinite(uniform_scale) ||
        uniform_scale <= 0.0) {
        throw std::runtime_error("invalid plate model transform");
    }

    NativePlateTransform transform {
        Slic3r::Vec3d(x_mm, y_mm, z_mm),
        Slic3r::Vec3d(rotation_x, rotation_y, rotation_z),
        Slic3r::Vec3d(uniform_scale, uniform_scale, uniform_scale),
        Slic3r::Transform3d::Identity(),
        transform_stride == 16
    };
    if (transform.has_orientation_matrix) {
        for (int row = 0; row < 3; ++row) {
            for (int column = 0; column < 3; ++column) {
                const double value = transforms[transform_offset + 3 + row * 3 + column];
                if (!std::isfinite(value)) {
                    throw std::runtime_error("invalid plate model orientation matrix");
                }
                transform.orientation_matrix.matrix()(row, column) = value;
            }
        }
    }
    return transform;
}

static void apply_native_plate_transform(Slic3r::ModelInstance* instance, const NativePlateTransform& transform)
{
    if (instance == nullptr) {
        return;
    }
    instance->set_scaling_factor(transform.scaling);
    if (transform.has_orientation_matrix) {
        instance->set_rotation(Slic3r::Vec3d(0.0, 0.0, 0.0));
        instance->rotate(transform.orientation_matrix.linear());
    } else {
        instance->set_rotation(transform.rotation);
    }
    instance->set_offset(transform.offset);
}

static PlannedPlateModel load_transformed_plate_model_for_planning(
    OrcaEngine* engine,
    const char* const* paths,
    const double* transforms,
    int transform_stride,
    const int* extruder_ids,
    int count)
{
    PlannedPlateModel planned;
    std::unordered_map<std::string, Slic3r::Model> model_cache;
    for (int index = 0; index < count; ++index) {
        const char* path = paths[index];
        if (path == nullptr || path[0] == '\0') {
            throw std::runtime_error("plate model path is empty");
        }

        const std::string path_key(path);
        auto cached_model = model_cache.find(path_key);
        if (cached_model == model_cache.end()) {
            bool persistent_cache_hit = false;
            Slic3r::Model model_for_cache = cached_source_planning_model(engine, path, &persistent_cache_hit);
            log_native_info(
                "orca_plan_model_cache",
                std::string(persistent_cache_hit ? "hit " : "miss ") + path_key);
            cached_model = model_cache.emplace(path_key, std::move(model_for_cache)).first;
        }
        const Slic3r::Model& loaded_model = cached_model->second;
        if (loaded_model.objects.empty()) {
            throw std::runtime_error("loaded plate model contains no objects");
        }

        const NativePlateTransform transform = read_native_plate_transform(transforms, index, transform_stride);
        const int extruder_id = std::max(1, extruder_ids[index]);
        for (Slic3r::ModelObject* source_object : loaded_model.objects) {
            if (source_object == nullptr) {
                continue;
            }
            if (source_object->instances.empty()) {
                source_object->add_instance();
            }
            Slic3r::ModelObject* combined_object = planned.model.add_object(*source_object);
            if (combined_object == nullptr) {
                continue;
            }
            planned.request_index_by_object.push_back(index);
            combined_object->input_file = path;
            combined_object->config.set_key_value("extruder", new Slic3r::ConfigOptionInt(extruder_id));
            for (Slic3r::ModelVolume* volume : combined_object->volumes) {
                if (volume != nullptr) {
                    volume->config.set_key_value("extruder", new Slic3r::ConfigOptionInt(extruder_id));
                }
            }
            if (combined_object->instances.empty()) {
                combined_object->add_instance();
            }
            for (Slic3r::ModelInstance* instance : combined_object->instances) {
                apply_native_plate_transform(instance, transform);
            }
            combined_object->invalidate_bounding_box();
        }
    }
    if (planned.model.objects.empty()) {
        throw std::runtime_error("plate contains no objects");
    }
    if (planned.request_index_by_object.size() != planned.model.objects.size()) {
        throw std::runtime_error("native plate planning request/object mapping failed");
    }
    return planned;
}

static bool add_arrange_polygon_points_for_group(
    Slic3r::arrangement::ArrangePolygon& group,
    const Slic3r::arrangement::ArrangePolygon& source,
    const Slic3r::Vec2crd& group_origin)
{
    Slic3r::ExPolygon transformed = source.poly;
    transformed.rotate(source.rotation);
    transformed.translate(
        source.translation.x() - group_origin.x(),
        source.translation.y() - group_origin.y()
    );
    const Slic3r::Points& points = transformed.contour.points;
    if (points.size() < 3) {
        return false;
    }
    std::copy(points.begin(), points.end(), std::back_inserter(group.poly.contour.points));
    group.height = std::max(group.height, source.height);
    group.extrude_ids.insert(group.extrude_ids.end(), source.extrude_ids.begin(), source.extrude_ids.end());
    return true;
}

static std::optional<Slic3r::arrangement::ArrangePolygon> wipe_tower_arrange_exclusion(const Slic3r::DynamicPrintConfig& config)
{
    const auto* enabled = config.option<Slic3r::ConfigOptionBool>("enable_prime_tower");
    if (enabled == nullptr || !enabled->value) {
        return std::nullopt;
    }
    const PrintableVolumeBounds bounds = extract_printable_volume_bounds(config);
    if (!bounds.valid) {
        return std::nullopt;
    }
    constexpr double margin = 5.0;
    const double tower_width = std::max(1.0, config_first_float(config, "prime_tower_width").value_or(35.0));
    const double brim_width = std::max(0.0, config_first_float(config, "prime_tower_brim_width").value_or(0.0));
    const double tower_depth = estimate_wipe_tower_depth(config, tower_width);
    const double x = std::clamp(
        config_first_float(config, "wipe_tower_x").value_or(bounds.max_x - tower_width - brim_width - margin),
        bounds.min_x + brim_width + margin,
        bounds.max_x - tower_width - brim_width - margin
    );
    const double y = std::clamp(
        config_first_float(config, "wipe_tower_y").value_or(bounds.max_y - tower_depth - brim_width - margin),
        bounds.min_y + brim_width + margin,
        bounds.max_y - tower_depth - brim_width - margin
    );
    const TowerPlacementRect rect = tower_rect(x, y, tower_width, tower_depth, brim_width, margin);
    Slic3r::Polygon contour;
    contour.points = {
        Slic3r::Point(Slic3r::scaled(rect.min_x), Slic3r::scaled(rect.min_y)),
        Slic3r::Point(Slic3r::scaled(rect.max_x), Slic3r::scaled(rect.min_y)),
        Slic3r::Point(Slic3r::scaled(rect.max_x), Slic3r::scaled(rect.max_y)),
        Slic3r::Point(Slic3r::scaled(rect.min_x), Slic3r::scaled(rect.max_y))
    };
    Slic3r::arrangement::ArrangePolygon ap;
    ap.poly.contour = std::move(contour);
    ap.bed_idx = 0;
    ap.is_virt_object = true;
    ap.is_wipe_tower = true;
    ap.name = "Prime tower";
    return ap;
}

static bool arrange_polygons_overlap(const Slic3r::arrangement::ArrangePolygons& items, std::string* reason = nullptr)
{
    for (size_t left_index = 0; left_index < items.size(); ++left_index) {
        const Slic3r::arrangement::ArrangePolygon& left = items[left_index];
        if (!left.is_arranged()) {
            if (reason != nullptr) {
                std::ostringstream message;
                message << "item " << left_index << " was not arranged"
                        << " bed=" << left.bed_idx;
                *reason = message.str();
            }
            return true;
        }
        const auto left_bbox = left.transformed_poly().contour.bounding_box();
        for (size_t right_index = left_index + 1; right_index < items.size(); ++right_index) {
            const Slic3r::arrangement::ArrangePolygon& right = items[right_index];
            if (!right.is_arranged()) {
                if (reason != nullptr) {
                    std::ostringstream message;
                    message << "item " << right_index << " was not arranged"
                            << " bed=" << right.bed_idx;
                    *reason = message.str();
                }
                return true;
            }
            if (left.bed_idx != right.bed_idx) {
                continue;
            }
            const auto right_bbox = right.transformed_poly().contour.bounding_box();
            const bool bounding_boxes_overlap =
                left_bbox.min.x() < right_bbox.max.x() &&
                left_bbox.max.x() > right_bbox.min.x() &&
                left_bbox.min.y() < right_bbox.max.y() &&
                left_bbox.max.y() > right_bbox.min.y();
            if (!bounding_boxes_overlap) {
                continue;
            }
            const Slic3r::ExPolygon left_poly = left.transformed_poly();
            const Slic3r::ExPolygon right_poly = right.transformed_poly();
            if (left_poly.overlaps(right_poly) || right_poly.overlaps(left_poly)) {
                if (reason != nullptr) {
                    std::ostringstream message;
                    message << "items " << left_index << " and " << right_index
                            << " overlap after native arrange"
                            << " left=(" << Slic3r::unscale<double>(left_bbox.min.x())
                            << "," << Slic3r::unscale<double>(left_bbox.min.y())
                            << ")-(" << Slic3r::unscale<double>(left_bbox.max.x())
                            << "," << Slic3r::unscale<double>(left_bbox.max.y())
                            << ") right=(" << Slic3r::unscale<double>(right_bbox.min.x())
                            << "," << Slic3r::unscale<double>(right_bbox.min.y())
                            << ")-(" << Slic3r::unscale<double>(right_bbox.max.x())
                            << "," << Slic3r::unscale<double>(right_bbox.max.y()) << ")";
                    *reason = message.str();
                }
                return true;
            }
        }
    }
    return false;
}

static void write_planned_transform(
    double* out_transforms,
    size_t result_index,
    Slic3r::ModelInstance* instance)
{
    constexpr size_t stride = 16;
    const size_t offset = result_index * stride;
    const Slic3r::Vec3d arranged_offset = instance->get_offset();
    const Slic3r::Vec3d arranged_rotation = instance->get_rotation();
    const Slic3r::Vec3d arranged_scaling = instance->get_scaling_factor();
    const Slic3r::Transform3d rotation_matrix = instance->get_transformation().get_rotation_matrix();
    out_transforms[offset + 0] = arranged_offset.x();
    out_transforms[offset + 1] = arranged_offset.y();
    out_transforms[offset + 2] = arranged_offset.z();
    out_transforms[offset + 3] = rotation_matrix.matrix()(0, 0);
    out_transforms[offset + 4] = rotation_matrix.matrix()(0, 1);
    out_transforms[offset + 5] = rotation_matrix.matrix()(0, 2);
    out_transforms[offset + 6] = rotation_matrix.matrix()(1, 0);
    out_transforms[offset + 7] = rotation_matrix.matrix()(1, 1);
    out_transforms[offset + 8] = rotation_matrix.matrix()(1, 2);
    out_transforms[offset + 9] = rotation_matrix.matrix()(2, 0);
    out_transforms[offset + 10] = rotation_matrix.matrix()(2, 1);
    out_transforms[offset + 11] = rotation_matrix.matrix()(2, 2);
    out_transforms[offset + 12] = arranged_scaling.x();
    out_transforms[offset + 13] = arranged_rotation.x();
    out_transforms[offset + 14] = arranged_rotation.y();
    out_transforms[offset + 15] = arranged_rotation.z();
}

static double stable_polygon_area_mm2(const Slic3r::Polygon& polygon)
{
    if (polygon.points.size() < 3) {
        return 0.0;
    }

    const Slic3r::Point& origin = polygon.points.front();
    long double twice_scaled_area = 0.0L;
    Slic3r::Point previous = polygon.points.back();
    for (const Slic3r::Point& current : polygon.points) {
        const long double previous_x = static_cast<long double>(previous.x()) - static_cast<long double>(origin.x());
        const long double previous_y = static_cast<long double>(previous.y()) - static_cast<long double>(origin.y());
        const long double current_x = static_cast<long double>(current.x()) - static_cast<long double>(origin.x());
        const long double current_y = static_cast<long double>(current.y()) - static_cast<long double>(origin.y());
        twice_scaled_area += previous_x * current_y - current_x * previous_y;
        previous = current;
    }

    const long double scale = static_cast<long double>(SCALING_FACTOR);
    const long double area_mm2 = std::abs(twice_scaled_area) * scale * scale / 2.0L;
    if (!std::isfinite(static_cast<double>(area_mm2)) ||
        area_mm2 > static_cast<long double>(std::numeric_limits<double>::max())) {
        return std::numeric_limits<double>::infinity();
    }
    return static_cast<double>(area_mm2);
}

#endif // ORCA_WRAPPER_PLANNING_HELPERS_H

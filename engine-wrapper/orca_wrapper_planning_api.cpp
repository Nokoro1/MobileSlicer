#include "orca_wrapper_module_context.h"

extern "C" int orca_plan_plate_arrangement(OrcaEngine* engine, const char* const* paths, const double* transforms, int transform_stride, const int* extruder_ids, int count, const char* config_json, int allow_rotation, double* out_transforms)
{
    if (engine == nullptr || paths == nullptr || transforms == nullptr || extruder_ids == nullptr || out_transforms == nullptr || count <= 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    const long planning_generation = ++engine->impl.planning_generation;

    try {
        Slic3r::DynamicPrintConfig config = Slic3r::DynamicPrintConfig::full_print_config();
        apply_android_runtime_gcode_baseline(config);
        apply_json_overrides(config_json != nullptr ? std::string(config_json) : engine->impl.config_json, config);

        PlannedPlateModel planned = load_transformed_plate_model_for_planning(paths, transforms, transform_stride, extruder_ids, count);
        Slic3r::Model& model = planned.model;
        Slic3r::arrangement::ArrangePolygons items;
        items.reserve(static_cast<size_t>(count));
        std::vector<std::vector<Slic3r::ModelInstance*>> grouped_instances(static_cast<size_t>(count));
        std::vector<std::string> grouped_names(static_cast<size_t>(count));

        int item_index = 0;
        std::vector<Slic3r::arrangement::ArrangePolygon> grouped_polygons(static_cast<size_t>(count));
        std::vector<Slic3r::arrangement::ArrangePolygon> direct_polygons(static_cast<size_t>(count));
        std::vector<Slic3r::Vec2crd> grouped_origins(static_cast<size_t>(count), Slic3r::Vec2crd{0, 0});
        std::vector<bool> has_group_polygon(static_cast<size_t>(count), false);
        std::vector<bool> has_direct_polygon(static_cast<size_t>(count), false);
        for (size_t object_index = 0; object_index < model.objects.size(); ++object_index) {
            Slic3r::ModelObject* object = model.objects[object_index];
            if (object == nullptr) {
                continue;
            }
            const int request_index = planned.request_index_by_object[object_index];
            if (request_index < 0 || request_index >= count) {
                set_last_error(engine, "Native arrange request/object mapping is invalid.");
                return ORCA_ERROR_LOAD_MODEL;
            }
            Slic3r::arrangement::ArrangePolygon& group_ap = grouped_polygons[static_cast<size_t>(request_index)];
            if (grouped_names[static_cast<size_t>(request_index)].empty()) {
                grouped_names[static_cast<size_t>(request_index)] = object->name.empty() ? "Object" : object->name;
            }
            for (size_t instance_index = 0; instance_index < object->instances.size(); ++instance_index) {
                Slic3r::ModelInstance* instance = object->instances[instance_index];
                if (instance == nullptr) {
                    continue;
                }
                Slic3r::arrangement::ArrangePolygon ap;
                instance->get_arrange_polygon(&ap, config);
                ap.bed_idx = 0;
                ap.name = object->name.empty() ? "Object" : object->name;
                ap.height = object->instance_bounding_box(instance_index).size().z();
                ap.brim_width = 1.0;
                const auto original_ap_bbox = ap.poly.contour.bounding_box();
                const double polygon_area_mm2 = stable_polygon_area_mm2(ap.poly.contour);
                const double polygon_bbox_width_mm = Slic3r::unscale<double>(original_ap_bbox.size().x());
                const double polygon_bbox_depth_mm = Slic3r::unscale<double>(original_ap_bbox.size().y());
                const double polygon_bbox_area_mm2 = std::max(0.0, polygon_bbox_width_mm) * std::max(0.0, polygon_bbox_depth_mm);
                const bool unusable_polygon =
                    ap.poly.contour.points.size() < 3 ||
                    !std::isfinite(polygon_area_mm2) ||
                    polygon_area_mm2 < 1.0 ||
                    (polygon_bbox_area_mm2 > 1.0 && polygon_area_mm2 > polygon_bbox_area_mm2 * 100.0);
                if (unusable_polygon) {
                    std::ostringstream message;
                    message << "Native Orca arrange polygon is invalid for "
                            << (object->name.empty() ? "Object" : object->name)
                            << " points=" << ap.poly.contour.points.size()
                            << " area_mm2=" << polygon_area_mm2
                            << " bbox=(" << polygon_bbox_width_mm << "," << polygon_bbox_depth_mm << ").";
                    set_last_error(engine, message.str());
                    log_native_error("orca_plan_plate_arrangement", message.str().c_str());
                    return ORCA_ERROR_ARRANGE_NO_FIT;
                }
                {
                    const auto ap_bbox = ap.poly.contour.bounding_box();
                    std::ostringstream message;
                    message << "input id=" << item_index
                            << " points=" << ap.poly.contour.points.size()
                            << " area_mm2=" << polygon_area_mm2
                            << " bbox=(" << Slic3r::unscale<double>(ap_bbox.size().x())
                            << "," << Slic3r::unscale<double>(ap_bbox.size().y()) << ")"
                            << " trans=(" << Slic3r::unscale<double>(ap.translation.x())
                            << "," << Slic3r::unscale<double>(ap.translation.y()) << ")"
                            << " extruders=[";
                    for (size_t extruder_index = 0; extruder_index < ap.extrude_ids.size(); ++extruder_index) {
                        if (extruder_index > 0) {
                            message << ",";
                        }
                        message << ap.extrude_ids[extruder_index];
                    }
                    message << "]";
                    log_native_info("orca_plan_plate_arrangement", message.str());
                }
                if (!has_group_polygon[static_cast<size_t>(request_index)]) {
                    grouped_origins[static_cast<size_t>(request_index)] = ap.translation;
                    direct_polygons[static_cast<size_t>(request_index)] = ap;
                    has_direct_polygon[static_cast<size_t>(request_index)] = true;
                }
                if (!add_arrange_polygon_points_for_group(
                        group_ap,
                        ap,
                        grouped_origins[static_cast<size_t>(request_index)])) {
                    set_last_error(engine, "Native Orca arrange could not build a grouped arrange polygon.");
                    return ORCA_ERROR_ARRANGE_NO_FIT;
                }
                grouped_instances[static_cast<size_t>(request_index)].push_back(instance);
                has_group_polygon[static_cast<size_t>(request_index)] = true;
            }
        }

        for (int request_index = 0; request_index < count; ++request_index) {
            const size_t request_slot = static_cast<size_t>(request_index);
            const bool use_direct_instance_polygon =
                grouped_instances[request_slot].size() == 1 && has_direct_polygon[request_slot];
            Slic3r::arrangement::ArrangePolygon ap = use_direct_instance_polygon
                ? std::move(direct_polygons[request_slot])
                : std::move(grouped_polygons[request_slot]);
            if (!has_group_polygon[request_slot] || ap.poly.contour.points.size() < 3) {
                set_last_error(engine, "Native arrange could not build an arrangeable polygon for every plate object.");
                return ORCA_ERROR_ARRANGE_NO_FIT;
            }
            if (!use_direct_instance_polygon) {
                ap.poly.contour.points = std::move(Slic3r::Geometry::convex_hull(ap.poly.contour.points).points);
            }
            const auto group_bbox = ap.poly.contour.bounding_box();
            const double polygon_area_mm2 = stable_polygon_area_mm2(ap.poly.contour);
            const double polygon_bbox_width_mm = Slic3r::unscale<double>(group_bbox.size().x());
            const double polygon_bbox_depth_mm = Slic3r::unscale<double>(group_bbox.size().y());
            const double polygon_bbox_area_mm2 = std::max(0.0, polygon_bbox_width_mm) * std::max(0.0, polygon_bbox_depth_mm);
            const bool unusable_polygon =
                ap.poly.contour.points.size() < 3 ||
                !std::isfinite(polygon_area_mm2) ||
                polygon_area_mm2 < 1.0 ||
                (polygon_bbox_area_mm2 > 1.0 && polygon_area_mm2 > polygon_bbox_area_mm2 * 100.0);
            if (unusable_polygon) {
                std::ostringstream message;
                message << "Native Orca arrange polygon is invalid for "
                        << grouped_names[request_slot]
                        << " points=" << ap.poly.contour.points.size()
                        << " area_mm2=" << polygon_area_mm2
                        << " bbox=(" << polygon_bbox_width_mm << "," << polygon_bbox_depth_mm << ").";
                set_last_error(engine, message.str());
                log_native_error("orca_plan_plate_arrangement", message.str().c_str());
                return ORCA_ERROR_ARRANGE_NO_FIT;
            }
            {
                std::ostringstream message;
                message << "arrange_item id=" << request_index
                        << " native_instances=" << grouped_instances[request_slot].size()
                        << " source=" << (use_direct_instance_polygon ? "orca_instance" : "group_hull")
                        << " points=" << ap.poly.contour.points.size()
                        << " area_mm2=" << polygon_area_mm2
                        << " bbox=(" << polygon_bbox_width_mm << "," << polygon_bbox_depth_mm << ")";
                log_native_info("orca_plan_plate_arrangement", message.str());
            }
            ap.bed_idx = 0;
            if (!use_direct_instance_polygon) {
                ap.translation = grouped_origins[request_slot];
            }
            ap.itemid = item_index++;
            ap.name = grouped_names[request_slot].empty()
                ? "Object"
                : grouped_names[request_slot];
            items.emplace_back(std::move(ap));
        }

        if (items.size() != static_cast<size_t>(count)) {
            set_last_error(engine, "Native arrange could not produce one grouped arrange item per plate object.");
            return ORCA_ERROR_ARRANGE_NO_FIT;
        }

        Slic3r::arrangement::ArrangePolygons excludes;
        if (auto tower = wipe_tower_arrange_exclusion(config)) {
            excludes.emplace_back(std::move(*tower));
        }

        Slic3r::arrangement::ArrangeParams params;
        params.min_obj_distance = 0;
        params.allow_rotations = allow_rotation != 0;
        params.allow_multi_materials_on_same_plate = true;
        params.parallel = false;
        params.stopcondition = [engine, planning_generation]() {
            return engine->impl.planning_generation.load() != planning_generation;
        };
        unsigned packed_item_count = 0;
        params.progressind = [&packed_item_count](unsigned packed_count, std::string name) {
            packed_item_count = std::max(packed_item_count, packed_count);
            log_native_info("orca_plan_plate_arrangement",
                "packed count=" + std::to_string(packed_count) + " name=" + name);
        };
        Slic3r::arrangement::update_arrange_params(params, &config, items);
        Slic3r::arrangement::update_selected_items_inflation(items, &config, params);
        Slic3r::arrangement::update_selected_items_axis_align(items, &config, params);

        Slic3r::Points bed_points = Slic3r::arrangement::get_shrink_bedpts(&config, params);

        {
            std::ostringstream message;
            message << "items=" << items.size() << " excludes=" << excludes.size()
                    << " bed_points=" << bed_points.size()
                    << " min_distance=" << Slic3r::unscale<double>(params.min_obj_distance)
                    << " allow_rotation=" << (params.allow_rotations ? 1 : 0)
                    << " parallel=" << (params.parallel ? 1 : 0)
                    << " params=" << params.to_json()
                    << " bed=[";
            for (size_t index = 0; index < bed_points.size(); ++index) {
                if (index > 0) {
                    message << ",";
                }
                message << "(" << Slic3r::unscale<double>(bed_points[index].x())
                        << "," << Slic3r::unscale<double>(bed_points[index].y()) << ")";
            }
            message << "]";
            log_native_info("orca_plan_plate_arrangement", message.str());
        }
        Slic3r::arrangement::arrange(items, excludes, bed_points, params);
        if (engine->impl.planning_generation.load() != planning_generation) {
            set_last_error(engine, "Native arrange was superseded by a newer planning request.");
            return ORCA_ERROR_ARRANGE_NO_FIT;
        }
        if (packed_item_count != items.size()) {
            std::ostringstream message;
            message << "Native Orca arrange did not pack every item on the physical plate. "
                    << "packed=" << packed_item_count << " requested=" << items.size() << ".";
            set_last_error(engine, message.str());
            log_native_error("orca_plan_plate_arrangement", message.str().c_str());
            return ORCA_ERROR_ARRANGE_NO_FIT;
        }
        {
            std::ostringstream message;
            message << "result";
            for (size_t index = 0; index < items.size(); ++index) {
                const auto& item = items[index];
                const auto transformed_bbox = item.transformed_poly().contour.bounding_box();
                message << " index=" << index
                        << " order=" << item.itemid
                        << " bed=" << item.bed_idx
                        << " xy=(" << Slic3r::unscale<double>(item.translation.x())
                        << "," << Slic3r::unscale<double>(item.translation.y()) << ")"
                        << " rz=" << item.rotation
                        << " bboxMin=(" << Slic3r::unscale<double>(transformed_bbox.min.x())
                        << "," << Slic3r::unscale<double>(transformed_bbox.min.y()) << ")"
                        << " bboxMax=(" << Slic3r::unscale<double>(transformed_bbox.max.x())
                        << "," << Slic3r::unscale<double>(transformed_bbox.max.y()) << ")";
            }
            log_native_info("orca_plan_plate_arrangement", message.str());
        }
        std::string overlap_reason;
        if (arrange_polygons_overlap(items, &overlap_reason)) {
            std::string message = "Native Orca arrange returned invalid placement: " + overlap_reason;
            set_last_error(engine, message);
            log_native_error("orca_plan_plate_arrangement", message.c_str());
            return ORCA_ERROR_ARRANGE_NO_FIT;
        }
        for (size_t index = 0; index < items.size(); ++index) {
            const auto& item = items[index];
            if (!item.is_arranged() || item.bed_idx != 0) {
                std::ostringstream message;
                message << "Objects do not fit on the selected build plate.";
                if (!item.name.empty()) {
                    message << " " << item.name << " could not be arranged.";
                }
                set_last_error(engine, message.str());
                return ORCA_ERROR_ARRANGE_NO_FIT;
            }
            const size_t result_index = index;
            const std::vector<Slic3r::ModelInstance*>& instances = grouped_instances[result_index];
            if (instances.empty() || instances.front() == nullptr) {
                set_last_error(engine, "Native arrange could not resolve an arranged object instance.");
                return ORCA_ERROR_LOAD_MODEL;
            }
            if (instances.size() == 1) {
                instances.front()->apply_arrange_result(item.translation.cast<double>(), item.rotation);
            } else {
                const Slic3r::Vec2d original_origin = Slic3r::unscale(grouped_origins[result_index]).cast<double>();
                const Slic3r::Vec2d arranged_origin = Slic3r::unscale(item.translation).cast<double>();
                const double cos_rotation = std::cos(item.rotation);
                const double sin_rotation = std::sin(item.rotation);
                for (Slic3r::ModelInstance* instance : instances) {
                    if (instance == nullptr) {
                        continue;
                    }
                    const Slic3r::Vec3d original_offset = instance->get_offset();
                    Slic3r::Vec2d local_offset(
                        original_offset.x() - original_origin.x(),
                        original_offset.y() - original_origin.y()
                    );
                    Slic3r::Vec2d rotated_offset(
                        local_offset.x() * cos_rotation - local_offset.y() * sin_rotation,
                        local_offset.x() * sin_rotation + local_offset.y() * cos_rotation
                    );
                    instance->set_offset(Slic3r::X, arranged_origin.x() + rotated_offset.x());
                    instance->set_offset(Slic3r::Y, arranged_origin.y() + rotated_offset.y());
                    instance->set_rotation(Slic3r::Z, instance->get_rotation(Slic3r::Z) + item.rotation);
                    instance->get_object()->invalidate_bounding_box();
                }
            }
            write_planned_transform(out_transforms, result_index, instances.front());
        }

        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_plan_plate_arrangement", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_plan_plate_arrangement", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" int orca_plan_auto_orientation(OrcaEngine* engine, const char* const* paths, const double* transforms, int transform_stride, const int* extruder_ids, int count, const char* config_json, double* out_transforms)
{
    if (engine == nullptr || paths == nullptr || transforms == nullptr || extruder_ids == nullptr || out_transforms == nullptr || count <= 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    const long planning_generation = ++engine->impl.planning_generation;

    try {
        Slic3r::DynamicPrintConfig config = Slic3r::DynamicPrintConfig::full_print_config();
        apply_android_runtime_gcode_baseline(config);
        apply_json_overrides(config_json != nullptr ? std::string(config_json) : engine->impl.config_json, config);

        PlannedPlateModel planned = load_transformed_plate_model_for_planning(paths, transforms, transform_stride, extruder_ids, count);
        Slic3r::Model& model = planned.model;
        std::vector<std::vector<Slic3r::ModelObject*>> grouped_objects(static_cast<size_t>(count));
        for (size_t object_index = 0; object_index < model.objects.size(); ++object_index) {
            const int request_index = planned.request_index_by_object[object_index];
            if (request_index < 0 || request_index >= count || model.objects[object_index] == nullptr) {
                set_last_error(engine, "Native auto-orient request/object mapping is invalid.");
                return ORCA_ERROR_LOAD_MODEL;
            }
            grouped_objects[static_cast<size_t>(request_index)].push_back(model.objects[object_index]);
        }
        size_t oriented_count = 0;
        for (int request_index = 0; request_index < count; ++request_index) {
            const std::vector<Slic3r::ModelObject*>& objects = grouped_objects[static_cast<size_t>(request_index)];
            if (objects.empty() || objects.front() == nullptr || objects.front()->instances.empty()) {
                set_last_error(engine, "Native auto-orient could not find an object instance.");
                return ORCA_ERROR_LOAD_MODEL;
            }

            Slic3r::orientation::OrientMesh mesh;
            mesh.name = objects.front()->name;
            mesh.mesh = objects.front()->mesh();
            for (size_t group_index = 1; group_index < objects.size(); ++group_index) {
                if (objects[group_index] != nullptr) {
                    mesh.mesh.merge(objects[group_index]->mesh());
                }
            }
            if (objects.front()->config.has("support_threshold_angle")) {
                mesh.overhang_angle = objects.front()->config.opt_int("support_threshold_angle");
            } else if (config.has("support_threshold_angle")) {
                mesh.overhang_angle = config.opt_int("support_threshold_angle");
            }
            try {
                Slic3r::orientation::OrientMeshs orient_meshes;
                orient_meshes.emplace_back(std::move(mesh));
                Slic3r::orientation::OrientParams params;
                Slic3r::orientation::OrientParamsArea params_area;
                std::memcpy(&params, &params_area, sizeof(params));
                params.min_volume = false;
                params.parallel = false;
                params.progressind = [](unsigned, std::string) {};
                params.stopcondition = [engine, planning_generation]() {
                    return engine->impl.planning_generation.load() != planning_generation;
                };
                Slic3r::orientation::OrientMeshs excludes;
                Slic3r::orientation::orient(orient_meshes, excludes, params);
                if (engine->impl.planning_generation.load() != planning_generation) {
                    set_last_error(engine, "Native auto-orient was superseded by a newer planning request.");
                    return ORCA_ERROR_LOAD_MODEL;
                }
                for (Slic3r::ModelObject* object : objects) {
                    if (object == nullptr) {
                        continue;
                    }
                    for (Slic3r::ModelInstance* instance : object->instances) {
                        if (instance != nullptr) {
                            instance->rotate(orient_meshes.front().rotation_matrix);
                        }
                    }
                    object->invalidate_bounding_box();
                    object->ensure_on_bed();
                }
                ++oriented_count;
            } catch (const std::exception& exception) {
                std::ostringstream message;
                message << "request=" << request_index << " skipped reason=" << exception.what();
                log_native_error("orca_plan_auto_orientation", message.str().c_str());
            } catch (...) {
                std::ostringstream message;
                message << "request=" << request_index << " skipped reason=unknown";
                log_native_error("orca_plan_auto_orientation", message.str().c_str());
            }

            write_planned_transform(out_transforms, static_cast<size_t>(request_index), objects.front()->instances.front());
        }
        {
            std::ostringstream message;
            message << "requests=" << count << " native_objects=" << model.objects.size() << " oriented=" << oriented_count;
            log_native_info("orca_plan_auto_orientation", message.str());
        }
        if (oriented_count != static_cast<size_t>(count)) {
            std::ostringstream message;
            message << "Native auto-orient could not produce an orientation for every requested object. "
                    << "oriented=" << oriented_count << " requested=" << count << ".";
            set_last_error(engine, message.str());
            return ORCA_ERROR_LOAD_MODEL;
        }
        set_last_error(engine, "");
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_plan_auto_orientation", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown native auto-orient failure");
        log_native_error("orca_plan_auto_orientation", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" int orca_cancel_planning(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    ++engine->impl.planning_generation;
    return ORCA_SUCCESS;
}

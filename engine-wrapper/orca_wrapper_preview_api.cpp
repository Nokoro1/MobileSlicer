#include "orca_wrapper_module_context.h"

extern "C" const char* orca_get_gcode(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (!ensure_gcode_loaded_unlocked(engine)) {
        return nullptr;
    }
    thread_local std::string gcode_snapshot;
    gcode_snapshot = engine->impl.gcode;
    return gcode_snapshot.c_str();
}

extern "C" const char* orca_get_gcode_summary(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (engine != nullptr && engine->impl.gcode_summary.empty() && !engine->impl.gcode_path.empty()) {
        engine->impl.gcode_summary = summarize_gcode_file_for_android(engine->impl.gcode_path);
        engine->impl.gcode_summary_enriched = false;
    }
    if (engine->impl.gcode_summary.empty()) {
        return nullptr;
    }
    thread_local std::string summary_snapshot;
    summary_snapshot = engine->impl.gcode_summary;
    return summary_snapshot.c_str();
}

extern "C" const char* orca_get_enriched_gcode_summary(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (engine->impl.gcode_summary.empty() && !engine->impl.gcode_path.empty()) {
        engine->impl.gcode_summary = summarize_gcode_file_for_android(engine->impl.gcode_path);
        engine->impl.gcode_summary_enriched = false;
    }
    if (engine->impl.gcode_summary.empty()) {
        return nullptr;
    }
    if (!engine->impl.gcode_summary_enriched) {
        const auto enrich_start = std::chrono::steady_clock::now();
        if (!ensure_gcode_loaded_unlocked(engine)) {
            thread_local std::string summary_snapshot;
            summary_snapshot = engine->impl.gcode_summary;
            return summary_snapshot.c_str();
        }
        bool vertex_limit_reached = false;
        const libvgcode::GCodeInputData input = to_vgcode_input_data_from_gcode_text(
            engine->impl.gcode,
            -1,
            -1,
            kMaxCachedPreviewVertices,
            &vertex_limit_reached);
        engine->impl.gcode_summary = enrich_gcode_summary_from_preview_input(engine->impl.gcode_summary, input);
        engine->impl.gcode_summary_enriched = true;
        if (kVerboseNativeTimingLogs) {
            log_native_info(
                "gcode_summary_enrich",
                "summaryEnrichMs=" + std::to_string(elapsed_ms_since(enrich_start)));
        }
    }
    thread_local std::string summary_snapshot;
    summary_snapshot = engine->impl.gcode_summary;
    return summary_snapshot.c_str();
}

extern "C" const char* orca_get_slice_metrics(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (engine->impl.slice_metrics.empty()) {
        return nullptr;
    }
    thread_local std::string metrics_snapshot;
    metrics_snapshot = engine->impl.slice_metrics;
    return metrics_snapshot.c_str();
}

extern "C" int orca_write_gcode_to_file(OrcaEngine* engine, const char* path)
{
    if (engine == nullptr || path == nullptr || path[0] == '\0') {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (engine->impl.gcode_path.empty() && engine->impl.gcode.empty()) {
        set_last_error(engine, "no generated G-code is available");
        return ORCA_ERROR_SLICE;
    }

    try {
        if (!engine->impl.gcode_path.empty()) {
            const std::filesystem::path source_path = engine->impl.gcode_path;
            const bool source_owned = engine->impl.gcode_path_owned;
            if (source_owned) {
                std::error_code ec;
                std::filesystem::remove(path, ec);
                ec.clear();
                std::filesystem::rename(source_path, path, ec);
                if (ec) {
                    std::filesystem::copy_file(
                        source_path,
                        path,
                        std::filesystem::copy_options::overwrite_existing
                    );
                    std::error_code cleanup_ec;
                    std::filesystem::remove(source_path, cleanup_ec);
                }
            } else {
                std::filesystem::copy_file(
                    source_path,
                    path,
                    std::filesystem::copy_options::overwrite_existing
                );
            }
            engine->impl.gcode.clear();
            engine->impl.gcode_path = path;
            engine->impl.gcode_path_owned = false;
        } else {
            std::ofstream output(path, std::ios::binary | std::ios::trunc);
            if (!output) {
                set_last_error(engine, "unable to open G-code output path");
                return ORCA_ERROR_SLICE;
            }
            output.write(engine->impl.gcode.data(), static_cast<std::streamsize>(engine->impl.gcode.size()));
            if (!output) {
                set_last_error(engine, "unable to write G-code output file");
                return ORCA_ERROR_SLICE;
            }
            engine->impl.gcode_path = path;
            engine->impl.gcode_path_owned = false;
        }
        clear_last_error(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_write_gcode_to_file", exception.what());
        return ORCA_ERROR_SLICE;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_write_gcode_to_file", "unknown exception");
        return ORCA_ERROR_SLICE;
    }
}

extern "C" int orca_write_project_3mf_to_file(OrcaEngine* engine, const char* path)
{
    if (engine == nullptr || path == nullptr || path[0] == '\0') {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (!engine->impl.model.has_value()) {
        set_last_error(engine, "no model loaded");
        return ORCA_ERROR_LOAD_MODEL;
    }

    try {
        Slic3r::DynamicPrintConfig config = Slic3r::DynamicPrintConfig::full_print_config();
        config.set_deserialize_strict("gcode_comments", "1");
        config.set_deserialize_strict("start_gcode", "");
        config.set_deserialize_strict("machine_start_gcode", "");
        config.set_deserialize_strict("machine_end_gcode", "");
        apply_json_overrides(engine->impl.config_json, config);
        apply_android_runtime_gcode_baseline(config);

        Slic3r::PlateDataPtrs plate_data_list;
        struct PlateDataListGuard {
            Slic3r::PlateDataPtrs& list;
            ~PlateDataListGuard()
            {
                Slic3r::release_PlateData_list(list);
            }
        } plate_data_guard{plate_data_list};

        auto* plate = new Slic3r::PlateData();
        plate->plate_index = 0;
        plate->plate_name = "Plate 1";
        plate->is_sliced_valid = false;
        plate->locked = false;
        plate->config.apply(config);
        for (size_t object_index = 0; object_index < engine->impl.model->objects.size(); ++object_index) {
            const Slic3r::ModelObject* object = engine->impl.model->objects[object_index];
            if (object == nullptr) {
                continue;
            }
            const size_t instance_count = std::max<size_t>(1, object->instances.size());
            for (size_t instance_index = 0; instance_index < instance_count; ++instance_index) {
                plate->objects_and_instances.emplace_back(
                    static_cast<int>(object_index),
                    static_cast<int>(instance_index)
                );
            }
        }
        plate_data_list.push_back(plate);

        Slic3r::StoreParams store_params;
        store_params.path = path;
        store_params.model = &(*engine->impl.model);
        store_params.plate_data_list = plate_data_list;
        store_params.export_plate_idx = 0;
        store_params.config = &config;
        store_params.strategy =
            Slic3r::SaveStrategy::Silence |
            Slic3r::SaveStrategy::SplitModel |
            Slic3r::SaveStrategy::SkipAuxiliary |
            Slic3r::SaveStrategy::Zip64;

        if (!Slic3r::store_bbs_3mf(store_params)) {
            set_last_error(engine, "Project .3mf export failed");
            return ORCA_ERROR_SLICE;
        }

        clear_last_error(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_write_project_3mf_to_file", exception.what());
        return ORCA_ERROR_SLICE;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_write_project_3mf_to_file", "unknown exception");
        return ORCA_ERROR_SLICE;
    }
}

extern "C" int orca_write_bambu_gcode_3mf_to_file(OrcaEngine* engine, const char* path)
{
    if (engine == nullptr || path == nullptr || path[0] == '\0') {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (!engine->impl.model.has_value()) {
        set_last_error(engine, "no model loaded");
        return ORCA_ERROR_LOAD_MODEL;
    }
    if (engine->impl.gcode_path.empty() && engine->impl.gcode.empty()) {
        set_last_error(engine, "no generated G-code is available");
        return ORCA_ERROR_SLICE;
    }

    try {
        if (engine->impl.gcode_path.empty()) {
            const auto temp_gcode_path = make_temp_gcode_path();
            std::ofstream output(temp_gcode_path, std::ios::binary | std::ios::trunc);
            if (!output) {
                set_last_error(engine, "unable to open temporary G-code path");
                return ORCA_ERROR_SLICE;
            }
            output.write(engine->impl.gcode.data(), static_cast<std::streamsize>(engine->impl.gcode.size()));
            if (!output) {
                set_last_error(engine, "unable to write temporary G-code file");
                return ORCA_ERROR_SLICE;
            }
            engine->impl.gcode_path = temp_gcode_path;
            engine->impl.gcode_path_owned = true;
        }

        Slic3r::DynamicPrintConfig config = Slic3r::DynamicPrintConfig::full_print_config();
        config.set_deserialize_strict("gcode_comments", "1");
        config.set_deserialize_strict("start_gcode", "");
        config.set_deserialize_strict("machine_start_gcode", "");
        config.set_deserialize_strict("machine_end_gcode", "");
        apply_json_overrides(engine->impl.config_json, config);
        apply_android_runtime_gcode_baseline(config);

        Slic3r::PlateDataPtrs plate_data_list;
        struct PlateDataListGuard {
            Slic3r::PlateDataPtrs& list;
            ~PlateDataListGuard()
            {
                Slic3r::release_PlateData_list(list);
            }
        } plate_data_guard{plate_data_list};

        auto* plate = new Slic3r::PlateData();
        plate->plate_index = 0;
        plate->plate_name = "Plate 1";
        plate->gcode_file = engine->impl.gcode_path.string();
        plate->is_sliced_valid = true;
        plate->locked = false;
        plate->config.apply(config);
        for (size_t object_index = 0; object_index < engine->impl.model->objects.size(); ++object_index) {
            const Slic3r::ModelObject* object = engine->impl.model->objects[object_index];
            if (object == nullptr) {
                continue;
            }
            const size_t instance_count = std::max<size_t>(1, object->instances.size());
            for (size_t instance_index = 0; instance_index < instance_count; ++instance_index) {
                plate->objects_and_instances.emplace_back(
                    static_cast<int>(object_index),
                    static_cast<int>(instance_index)
                );
            }
        }
        plate_data_list.push_back(plate);

        Slic3r::StoreParams store_params;
        store_params.path = path;
        store_params.model = &(*engine->impl.model);
        store_params.plate_data_list = plate_data_list;
        store_params.export_plate_idx = 0;
        store_params.config = &config;
        store_params.strategy =
            Slic3r::SaveStrategy::Silence |
            Slic3r::SaveStrategy::SplitModel |
            Slic3r::SaveStrategy::WithGcode |
            Slic3r::SaveStrategy::SkipAuxiliary |
            Slic3r::SaveStrategy::Zip64;

        if (!Slic3r::store_bbs_3mf(store_params)) {
            set_last_error(engine, "Bambu .gcode.3mf export failed");
            return ORCA_ERROR_SLICE;
        }

        clear_last_error(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_write_bambu_gcode_3mf_to_file", exception.what());
        return ORCA_ERROR_SLICE;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_write_bambu_gcode_3mf_to_file", "unknown exception");
        return ORCA_ERROR_SLICE;
    }
}

extern "C" const char* orca_get_last_error(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (engine == nullptr || engine->impl.last_error.empty()) {
        return nullptr;
    }
    thread_local std::string error_snapshot;
    error_snapshot = engine->impl.last_error;
    return error_snapshot.c_str();
}

#if defined(MOBILE_SLICER_ENABLE_VGCODE)
static bool ensure_gcode_preview_cache(OrcaEngine* engine, std::string& error, PreviewCacheStatus* status = nullptr)
{
    error.clear();
    if (engine == nullptr) {
        error = "no native engine is available";
        return false;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (!ensure_gcode_loaded_unlocked(engine)) {
        error = "no generated G-code is available";
        return false;
    }
    const size_t source_size = engine->impl.gcode.size();
    if (status != nullptr) {
        status->source_size = source_size;
    }
    if (engine->impl.cached_preview_valid &&
        engine->impl.cached_preview_source_size == source_size) {
        if (status != nullptr) {
            status->cache_hit = true;
            status->cache_valid = engine->impl.cached_preview_valid;
            status->cache_complete = engine->impl.cached_preview_complete;
            status->cached_vertices = engine->impl.cached_preview_input.vertices.size();
            status->cached_layers = gcode_input_layer_count(engine->impl.cached_preview_input);
        }
        return engine->impl.cached_preview_complete;
    }

    const auto parse_start = std::chrono::steady_clock::now();
    bool vertex_limit_reached = false;
    engine->impl.cached_preview_input = to_vgcode_input_data_from_gcode_text(
        engine->impl.gcode,
        -1,
        -1,
        kMaxCachedPreviewVertices,
        &vertex_limit_reached);
    apply_preview_palette_from_config_json(engine->impl.cached_preview_input, engine->impl.config_json);
    engine->impl.cached_preview_source_size = source_size;
    engine->impl.cached_preview_valid = !engine->impl.cached_preview_input.vertices.empty();
    engine->impl.cached_preview_complete = engine->impl.cached_preview_valid && !vertex_limit_reached;
    if (engine->impl.cached_preview_complete) {
        engine->impl.cached_preview_layer_counts = count_preview_vertices_by_layer_from_input_data(engine->impl.cached_preview_input);
        engine->impl.cached_preview_layer_counts_source_size = source_size;
    }

    const auto parse_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - parse_start).count();
    if (status != nullptr) {
        status->cache_built = true;
        status->cache_valid = engine->impl.cached_preview_valid;
        status->cache_complete = engine->impl.cached_preview_complete;
        status->vertex_limit_reached = vertex_limit_reached;
        status->cached_vertices = engine->impl.cached_preview_input.vertices.size();
        status->cached_layers = gcode_input_layer_count(engine->impl.cached_preview_input);
        status->parse_ms = parse_ms;
    }
    if (kVerboseNativeTimingLogs) {
        log_native_info("gcode_preview_cache",
            "vertices=" + std::to_string(engine->impl.cached_preview_input.vertices.size()) +
            " layers=" + std::to_string(gcode_input_layer_count(engine->impl.cached_preview_input)) +
            " sourceBytes=" + std::to_string(source_size) +
            " complete=" + std::string(engine->impl.cached_preview_complete ? "true" : "false") +
            " vertexLimitReached=" + std::string(vertex_limit_reached ? "true" : "false") +
            " parseMs=" + std::to_string(parse_ms));
    }

    if (!engine->impl.cached_preview_valid) {
        error = "Sliced G-code did not contain renderable extrusion toolpath vertices.";
        return false;
    }
    return engine->impl.cached_preview_complete;
}

static bool cached_preview_covers_layer_range(const OrcaEngineImpl& impl, long min_layer, long max_layer)
{
    if (!impl.cached_preview_valid || min_layer < 0 || max_layer < min_layer) {
        return false;
    }
    if (impl.cached_preview_input.layer_vertex_ranges.empty()) {
        return false;
    }
    const long cached_layers = static_cast<long>(impl.cached_preview_input.layer_vertex_ranges.size());
    if (max_layer >= cached_layers) {
        return false;
    }
    if (impl.cached_preview_complete) {
        return true;
    }
    // An incomplete prefix cache may stop inside its last indexed layer.
    return max_layer + 1 < cached_layers;
}

static void populate_preview_cache_status_from_existing(const OrcaEngineImpl& impl, PreviewCacheStatus& status)
{
    status.cache_hit = impl.cached_preview_valid;
    status.cache_valid = impl.cached_preview_valid;
    status.cache_complete = impl.cached_preview_complete;
    status.source_size = impl.gcode.size();
    status.cached_vertices = impl.cached_preview_input.vertices.size();
    status.cached_layers = gcode_input_layer_count(impl.cached_preview_input);
}

static bool requested_preview_range_covers_known_layers(const OrcaEngineImpl& impl, long min_layer, long max_layer)
{
    if (min_layer > 0) {
        return false;
    }
    const size_t source_size = impl.gcode.size();
    if (
        !impl.cached_preview_layer_counts.empty() &&
        impl.cached_preview_layer_counts_source_size == source_size
    ) {
        return max_layer >= static_cast<long>(impl.cached_preview_layer_counts.size()) - 1L;
    }
    return max_layer < 0 || max_layer >= std::numeric_limits<int>::max();
}

static bool is_gcode_preview_generation_current(const OrcaEngine* engine, long generation)
{
    return engine != nullptr &&
        (generation <= 0 ||
            engine->impl.gcode_preview_generation.load(std::memory_order_relaxed) == generation);
}

extern "C" void orca_set_gcode_preview_generation(OrcaEngine* engine, long generation)
{
    if (engine == nullptr) {
        return;
    }
    engine->impl.gcode_preview_generation.store(generation, std::memory_order_relaxed);
}

extern "C" OrcaGcodeViewer* orca_gcode_viewer_create(void)
{
    try {
        return new OrcaGcodeViewer();
    } catch (...) {
        return nullptr;
    }
}

extern "C" void orca_gcode_viewer_destroy(OrcaGcodeViewer* viewer)
{
    if (viewer == nullptr) {
        return;
    }
    if (viewer->initialized) {
        viewer->viewer.shutdown();
        viewer->initialized = false;
    }
    delete viewer;
}

extern "C" int orca_gcode_viewer_init(OrcaGcodeViewer* viewer)
{
    if (viewer == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    if (viewer->initialized) {
        return ORCA_SUCCESS;
    }
    const GLubyte* version = glGetString(GL_VERSION);
    if (version == nullptr) {
        viewer->last_error = "OpenGL version is unavailable.";
        return ORCA_ERROR_SLICE;
    }
    try {
        viewer->viewer.init(reinterpret_cast<const char*>(version));
        viewer->initialized = true;
        viewer->last_error.clear();
        return ORCA_SUCCESS;
    } catch (const std::exception& e) {
        viewer->last_error = e.what();
        return ORCA_ERROR_SLICE;
    } catch (...) {
        viewer->last_error = "libvgcode initialization failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" int orca_gcode_viewer_shutdown(OrcaGcodeViewer* viewer)
{
    if (viewer == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    if (!viewer->initialized) {
        return ORCA_SUCCESS;
    }
    try {
        viewer->viewer.shutdown();
        viewer->initialized = false;
        viewer->last_error.clear();
        return ORCA_SUCCESS;
    } catch (...) {
        viewer->last_error = "libvgcode shutdown failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" int orca_gcode_viewer_load_gcode(OrcaGcodeViewer* viewer, const char* gcode)
{
    if (viewer == nullptr || gcode == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    if (!viewer->initialized) {
        const int init_result = orca_gcode_viewer_init(viewer);
        if (init_result != ORCA_SUCCESS) {
            return init_result;
        }
    }
    try {
        viewer->input_data = to_vgcode_input_data_from_gcode_text(gcode, -1, -1);
        if (viewer->input_data.vertices.empty()) {
            viewer->last_error = "Sliced G-code did not contain renderable extrusion toolpath vertices.";
            return ORCA_ERROR_SLICE;
        }
        if (viewer->input_data.vertices.size() >= kMaxPreviewVertices) {
            viewer->last_error = "Sliced G-code preview is too large for the current phone preview limit. Vertex limit: " +
                std::to_string(kMaxPreviewVertices) + ".";
            return ORCA_ERROR_SLICE;
        }
        log_native_info("gcode_viewer_load_text", "vertices=" + std::to_string(viewer->input_data.vertices.size()) +
            " fidelity=exact");
        viewer->last_load_metrics =
            "source=text|vertices=" + std::to_string(viewer->input_data.vertices.size()) +
            "|selectedParseMs=0|libvgcodeLoadMs=0|totalMs=0|cache=none";
        viewer->viewer.load(std::move(viewer->input_data));
        const auto view_type = preview_view_type_for_loaded_viewer(viewer->viewer);
        viewer->viewer.set_view_type(view_type);
        apply_preview_option_visibility_for_view_type(viewer->viewer, view_type);
        viewer->viewer.set_time_mode(libvgcode::ETimeMode::Normal);
        viewer->last_error.clear();
        return ORCA_SUCCESS;
    } catch (const std::exception& e) {
        viewer->last_error = e.what();
        return ORCA_ERROR_SLICE;
    } catch (...) {
        viewer->last_error = "libvgcode preview text load failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" int orca_gcode_viewer_load_latest_slice(
    OrcaGcodeViewer* viewer,
    OrcaEngine* engine,
    long min_layer,
    long max_layer,
    int lod_hint,
    long generation)
{
    if (viewer == nullptr || engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> engine_lock(engine->impl.mutex);
    if (engine->impl.gcode.empty() && engine->impl.gcode_path.empty()) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    if (!viewer->initialized) {
        const int init_result = orca_gcode_viewer_init(viewer);
        if (init_result != ORCA_SUCCESS) {
            return init_result;
        }
    }
    if (!is_gcode_preview_generation_current(engine, generation)) {
        viewer->last_error.clear();
        return ORCA_SUCCESS;
    }
    try {
        const size_t vertex_budget = lod_hint > 0 ?
            std::min(static_cast<size_t>(lod_hint), kMaxPreviewVertices) :
            kMaxPreviewVertices;
        std::string cache_error;
        PreviewCacheStatus cache_status;
        const auto total_start = std::chrono::steady_clock::now();
        bool cache_covers_selected_range = cached_preview_covers_layer_range(engine->impl, min_layer, max_layer);
        if (cache_covers_selected_range) {
            populate_preview_cache_status_from_existing(engine->impl, cache_status);
        }
        const bool should_build_preview_cache =
            !cache_covers_selected_range &&
            requested_preview_range_covers_known_layers(engine->impl, min_layer, max_layer);
        const bool cache_available = should_build_preview_cache ?
            ensure_gcode_preview_cache(engine, cache_error, &cache_status) :
            false;
        size_t preview_vertices = 0;
        bool loaded_directly_from_cache = false;
        long selected_parse_ms = 0;
        long libvgcode_load_ms = 0;
        cache_covers_selected_range =
            cache_available || cached_preview_covers_layer_range(engine->impl, min_layer, max_layer);
        if (cache_covers_selected_range) {
            if (!cache_status.cache_valid) {
                populate_preview_cache_status_from_existing(engine->impl, cache_status);
            }
            const auto range_load_start = std::chrono::steady_clock::now();
            preview_vertices = viewer->viewer.load_layer_range(
                engine->impl.cached_preview_input,
                min_layer,
                max_layer,
                vertex_budget);
            libvgcode_load_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - range_load_start).count();
            loaded_directly_from_cache = preview_vertices > 0 && preview_vertices < vertex_budget;
        } else {
            const auto selected_parse_start = std::chrono::steady_clock::now();
            auto should_cancel = [engine, generation]() {
                return !is_gcode_preview_generation_current(engine, generation);
            };
            const size_t expected_vertices = count_preview_vertices_in_layer_range(
                engine->impl.cached_preview_layer_counts,
                min_layer,
                max_layer,
                vertex_budget);
            const bool can_parse_generated_file =
                !engine->impl.gcode_path.empty() &&
                !engine->impl.cached_preview_layer_counts.empty();
            if (can_parse_generated_file) {
                size_t source_size = engine->impl.gcode.size();
                if (source_size == 0) {
                    std::error_code ec;
                    const uintmax_t file_size = std::filesystem::file_size(engine->impl.gcode_path, ec);
                    if (!ec) {
                        source_size = static_cast<size_t>(file_size);
                    }
                }
                viewer->input_data = to_vgcode_input_data_from_generated_gcode_file(
                    engine->impl.gcode_path,
                    min_layer,
                    max_layer,
                    vertex_budget,
                    nullptr,
                    should_cancel,
                    expected_vertices);
            } else {
                if (!ensure_gcode_loaded_unlocked(engine)) {
                    viewer->last_error = "no generated G-code is available for preview.";
                    return ORCA_ERROR_SLICE;
                }
                viewer->input_data = to_vgcode_input_data_from_gcode_text(
                    engine->impl.gcode,
                    min_layer,
                    max_layer,
                    vertex_budget,
                    nullptr,
                    should_cancel,
                    expected_vertices);
            }
            if (should_cancel()) {
                viewer->last_error.clear();
                return ORCA_SUCCESS;
            }
            apply_preview_palette_from_config_json(viewer->input_data, engine->impl.config_json);
            selected_parse_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - selected_parse_start).count();
            preview_vertices = viewer->input_data.vertices.size();
        }
        if (preview_vertices == 0) {
            viewer->last_error = cache_error.empty() ?
                "Selected G-code preview layers did not contain renderable extrusion toolpath vertices." :
                cache_error;
            return ORCA_ERROR_SLICE;
        }
        log_native_info("gcode_viewer_load_latest_slice",
            "range=" + std::to_string(min_layer) + "-" + std::to_string(max_layer) +
            " vertices=" + std::to_string(preview_vertices) +
            " budget=" + std::to_string(vertex_budget) +
            " cache=" + std::string(cache_covers_selected_range ? (loaded_directly_from_cache ? "range" : "fallback") : "miss") +
            " cachedVertices=" + std::to_string(cache_status.cached_vertices) +
            " cachedLayers=" + std::to_string(cache_status.cached_layers));
        if (preview_vertices >= vertex_budget) {
            viewer->last_error = "Selected G-code preview is too large for exact phone preview. Vertex limit: " +
                std::to_string(vertex_budget) + ". Narrow the layer range to keep the preview accurate.";
            return ORCA_ERROR_SLICE;
        }
        if (!loaded_directly_from_cache) {
            const auto fallback_load_start = std::chrono::steady_clock::now();
            viewer->viewer.load(std::move(viewer->input_data));
            libvgcode_load_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - fallback_load_start).count();
        }
        const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - total_start).count();
        if (kVerboseNativeTimingLogs) {
            log_native_info("gcode_viewer_load_latest_slice", "vertices=" + std::to_string(preview_vertices) +
                " requestedMinLayer=" + std::to_string(min_layer) +
                " requestedMaxLayer=" + std::to_string(max_layer) +
                " loadedMinLayer=" + std::to_string(min_layer) +
                " loadedMaxLayer=" + std::to_string(max_layer) +
                " cache=" + std::string(cache_covers_selected_range ? (loaded_directly_from_cache ? "range" : "fallback") : "miss") +
                " cacheValid=" + std::string(cache_status.cache_valid ? "true" : "false") +
                " cacheComplete=" + std::string(cache_status.cache_complete ? "true" : "false") +
                " cacheBuilt=" + std::string(cache_status.cache_built ? "true" : "false") +
                " cachedVertices=" + std::to_string(cache_status.cached_vertices) +
                " cachedLayers=" + std::to_string(cache_status.cached_layers) +
                " selectedParseMs=" + std::to_string(selected_parse_ms) +
                " libvgcodeLoadMs=" + std::to_string(libvgcode_load_ms) +
                " totalMs=" + std::to_string(total_ms) +
                " fidelity=exact");
        }
        viewer->last_load_metrics =
            "source=latestSlice" +
            std::string("|vertices=") + std::to_string(preview_vertices) +
            "|selectedParseMs=" + std::to_string(selected_parse_ms) +
            "|libvgcodeLoadMs=" + std::to_string(libvgcode_load_ms) +
            "|totalMs=" + std::to_string(total_ms) +
            "|cache=" + std::string(cache_covers_selected_range ? (loaded_directly_from_cache ? "range" : "fallback") : "miss") +
            "|cacheValid=" + std::string(cache_status.cache_valid ? "1" : "0") +
            "|cacheComplete=" + std::string(cache_status.cache_complete ? "1" : "0") +
            "|cacheBuilt=" + std::string(cache_status.cache_built ? "1" : "0") +
            "|cachedVertices=" + std::to_string(cache_status.cached_vertices) +
            "|cachedLayers=" + std::to_string(cache_status.cached_layers);
        const auto view_type = preview_view_type_for_loaded_viewer(viewer->viewer);
        viewer->viewer.set_view_type(view_type);
        apply_preview_option_visibility_for_view_type(viewer->viewer, view_type);
        viewer->viewer.set_time_mode(libvgcode::ETimeMode::Normal);
        viewer->last_error.clear();
        return ORCA_SUCCESS;
    } catch (const std::exception& e) {
        viewer->last_error = e.what();
        return ORCA_ERROR_SLICE;
    } catch (...) {
        viewer->last_error = "libvgcode preview slice-range load failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" const char* orca_gcode_preview_suggest_layer_ranges(OrcaEngine* engine, long min_layer, long max_layer, long vertex_budget)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    const size_t budget = static_cast<size_t>(
        std::max<long>(1L, std::min<long>(vertex_budget, static_cast<long>(kMaxPreviewVertices - 1))));
    try {
        std::string error;
        const auto plan_start = std::chrono::steady_clock::now();
        size_t source_size = engine->impl.gcode.size();
        if (source_size == 0 && !engine->impl.gcode_path.empty()) {
            std::error_code ec;
            const uintmax_t file_size = std::filesystem::file_size(engine->impl.gcode_path, ec);
            if (!ec) {
                source_size = static_cast<size_t>(file_size);
            }
        }
        const bool cached_counts_available =
            !engine->impl.cached_preview_layer_counts.empty() &&
            engine->impl.cached_preview_layer_counts_source_size == source_size;
        std::vector<size_t> counted_layer_counts;
        bool counted_from_file = false;
        bool counted_from_text = false;
        if (!cached_counts_available) {
            if (!engine->impl.gcode_path.empty()) {
                counted_layer_counts = count_preview_vertices_by_layer_from_gcode_file(engine->impl.gcode_path);
                counted_from_file = !counted_layer_counts.empty();
            }
            if (!counted_from_file && !ensure_gcode_loaded_unlocked(engine)) {
                set_last_error(engine, "no generated G-code is available for preview range planning");
                return nullptr;
            }
            if (!counted_from_file) {
                if (source_size == 0) {
                    source_size = engine->impl.gcode.size();
                }
                counted_layer_counts = count_preview_vertices_by_layer_from_gcode_text(engine->impl.gcode);
                counted_from_text = !counted_layer_counts.empty();
            }
            if (!counted_layer_counts.empty() && source_size > 0) {
                engine->impl.cached_preview_layer_counts = counted_layer_counts;
                engine->impl.cached_preview_layer_counts_source_size = source_size;
            }
        }
        const bool cached_counts_after_compute =
            !engine->impl.cached_preview_layer_counts.empty() &&
            engine->impl.cached_preview_layer_counts_source_size == source_size;
        const std::vector<size_t>& layer_counts = cached_counts_after_compute ?
            engine->impl.cached_preview_layer_counts :
            counted_layer_counts;
        engine->impl.preview_range_plan = pack_preview_layer_ranges_from_counts(
            layer_counts,
            std::max<long>(0L, min_layer),
            std::max<long>(min_layer, max_layer),
            budget,
            error);
        if (engine->impl.preview_range_plan.empty()) {
            set_last_error(engine, error.empty() ? "unable to plan exact G-code preview ranges" : error);
            return nullptr;
        }
        if (kVerboseNativeTimingLogs) {
            log_native_info(
                "gcode_preview_range_plan",
                "ranges=" + engine->impl.preview_range_plan +
                    " layers=" + std::to_string(layer_counts.size()) +
                    " budget=" + std::to_string(budget) +
                    " counts=" + std::string(
                        cached_counts_available ? "cache" : (
                            counted_from_file ? "file-cache" : (counted_from_text ? "text-cache" : "none"))) +
                    " planMs=" + std::to_string(elapsed_ms_since(plan_start)));
        }
        thread_local std::string preview_range_plan_snapshot;
        preview_range_plan_snapshot = engine->impl.preview_range_plan;
        return preview_range_plan_snapshot.c_str();
    } catch (const std::exception& e) {
        set_last_error(engine, e.what());
        return nullptr;
    } catch (...) {
        set_last_error(engine, "G-code preview range planning failed");
        return nullptr;
    }
}

extern "C" int orca_gcode_viewer_render(OrcaGcodeViewer* viewer, const float* view_matrix, const float* projection_matrix)
{
    if (viewer == nullptr || view_matrix == nullptr || projection_matrix == nullptr || !viewer->initialized) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    try {
        libvgcode::Mat4x4 view;
        libvgcode::Mat4x4 projection;
        std::copy(view_matrix, view_matrix + view.size(), view.begin());
        std::copy(projection_matrix, projection_matrix + projection.size(), projection.begin());
        viewer->viewer.render(view, projection);
        return ORCA_SUCCESS;
    } catch (const std::exception& e) {
        viewer->last_error = e.what();
        return ORCA_ERROR_SLICE;
    } catch (...) {
        viewer->last_error = "libvgcode render failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" long orca_gcode_viewer_get_layers_count(OrcaGcodeViewer* viewer)
{
    if (viewer == nullptr) {
        return 0;
    }
    return static_cast<long>(viewer->viewer.get_layers_count());
}

extern "C" int orca_gcode_viewer_set_layers_view_range(OrcaGcodeViewer* viewer, long min_layer, long max_layer)
{
    if (viewer == nullptr || min_layer < 0 || max_layer < 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    viewer->viewer.set_layers_view_range(static_cast<size_t>(min_layer), static_cast<size_t>(max_layer));
    return ORCA_SUCCESS;
}

extern "C" int orca_gcode_viewer_set_extrusion_width_scale(OrcaGcodeViewer* viewer, float scale)
{
    if (viewer == nullptr || !std::isfinite(scale) || scale <= 0.0f) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    viewer->viewer.set_extrusion_width_scale(scale);
    return ORCA_SUCCESS;
}

extern "C" int orca_gcode_viewer_set_path_visibility(OrcaGcodeViewer* viewer, int kind, int id, int visible)
{
    if (viewer == nullptr || !viewer->initialized) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    try {
        const bool target_visible = visible != 0;
        if (kind == 0) {
            if (id < 0 || id >= static_cast<int>(libvgcode::EGCodeExtrusionRole::COUNT)) {
                return ORCA_ERROR_INVALID_ARGUMENT;
            }
            const auto role = static_cast<libvgcode::EGCodeExtrusionRole>(id);
            if (viewer->viewer.is_extrusion_role_visible(role) != target_visible) {
                viewer->viewer.toggle_extrusion_role_visibility(role);
            }
        } else if (kind == 1) {
            if (id < 0 || id >= static_cast<int>(libvgcode::EOptionType::COUNT)) {
                return ORCA_ERROR_INVALID_ARGUMENT;
            }
            const auto option = static_cast<libvgcode::EOptionType>(id);
            if (viewer->viewer.is_option_visible(option) != target_visible) {
                viewer->viewer.toggle_option_visibility(option);
            }
        } else {
            return ORCA_ERROR_INVALID_ARGUMENT;
        }
        viewer->last_error.clear();
        return ORCA_SUCCESS;
    } catch (const std::exception& e) {
        viewer->last_error = e.what();
        return ORCA_ERROR_SLICE;
    } catch (...) {
        viewer->last_error = "libvgcode path visibility update failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" int orca_gcode_viewer_set_view_type(OrcaGcodeViewer* viewer, int view_type)
{
    if (viewer == nullptr || !viewer->initialized) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    try {
        const auto mapped_view_type = preview_view_type_from_mobile_id(view_type, viewer->viewer);
        if (!mapped_view_type) {
            return ORCA_ERROR_INVALID_ARGUMENT;
        }
        viewer->viewer.set_view_type(*mapped_view_type);
        apply_preview_option_visibility_for_view_type(viewer->viewer, *mapped_view_type);
        viewer->last_error.clear();
        return ORCA_SUCCESS;
    } catch (const std::exception& e) {
        viewer->last_error = e.what();
        return ORCA_ERROR_SLICE;
    } catch (...) {
        viewer->last_error = "libvgcode view type update failed.";
        return ORCA_ERROR_SLICE;
    }
}

extern "C" const char* orca_gcode_viewer_get_last_error(OrcaGcodeViewer* viewer)
{
    if (viewer == nullptr || viewer->last_error.empty()) {
        return nullptr;
    }
    return viewer->last_error.c_str();
}

extern "C" const char* orca_gcode_viewer_get_last_load_metrics(OrcaGcodeViewer* viewer)
{
    if (viewer == nullptr || viewer->last_load_metrics.empty()) {
        return nullptr;
    }
    return viewer->last_load_metrics.c_str();
}
#else
extern "C" OrcaGcodeViewer* orca_gcode_viewer_create(void) { return nullptr; }
extern "C" void orca_gcode_viewer_destroy(OrcaGcodeViewer*) {}
extern "C" int orca_gcode_viewer_init(OrcaGcodeViewer*) { return ORCA_ERROR_SLICE; }
extern "C" int orca_gcode_viewer_shutdown(OrcaGcodeViewer*) { return ORCA_ERROR_SLICE; }
extern "C" int orca_gcode_viewer_load_gcode(OrcaGcodeViewer*, const char*) { return ORCA_ERROR_SLICE; }
extern "C" void orca_set_gcode_preview_generation(OrcaEngine*, long) {}
extern "C" int orca_gcode_viewer_load_latest_slice(OrcaGcodeViewer*, OrcaEngine*, long, long, int, long) { return ORCA_ERROR_SLICE; }
extern "C" int orca_gcode_viewer_render(OrcaGcodeViewer*, const float*, const float*) { return ORCA_ERROR_SLICE; }
extern "C" long orca_gcode_viewer_get_layers_count(OrcaGcodeViewer*) { return 0; }
extern "C" int orca_gcode_viewer_set_layers_view_range(OrcaGcodeViewer*, long, long) { return ORCA_ERROR_SLICE; }
extern "C" int orca_gcode_viewer_set_extrusion_width_scale(OrcaGcodeViewer*, float) { return ORCA_ERROR_SLICE; }
extern "C" int orca_gcode_viewer_set_path_visibility(OrcaGcodeViewer*, int, int, int) { return ORCA_ERROR_SLICE; }
extern "C" int orca_gcode_viewer_set_view_type(OrcaGcodeViewer*, int) { return ORCA_ERROR_SLICE; }
extern "C" const char* orca_gcode_viewer_get_last_error(OrcaGcodeViewer*) { return nullptr; }
extern "C" const char* orca_gcode_viewer_get_last_load_metrics(OrcaGcodeViewer*) { return nullptr; }
#endif

#include "orca_wrapper_module_context.h"

extern "C" OrcaEngine* orca_create(void)
{
    try {
        return new OrcaEngine();
    } catch (...) {
        return nullptr;
    }
}

extern "C" void orca_destroy(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return;
    }
    {
        std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
        clear_generated_gcode(engine);
    }
    delete engine;
}

extern "C" int orca_load_model(OrcaEngine* engine, const char* path)
{
    if (engine == nullptr || path == nullptr || path[0] == '\0') {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    const long planning_generation = ++engine->impl.planning_generation;
    clear_generated_gcode(engine);

    try {
        Slic3r::Model model;
        if (has_stl_extension(path)) {
            if (!Slic3r::load_stl(path, &model, nullptr, nullptr, 80)) {
                set_last_error(engine, "stl load failed");
                return ORCA_ERROR_LOAD_MODEL;
            }
            model.add_default_instances();
            for (Slic3r::ModelObject* object : model.objects) {
                object->input_file = path;
            }
        } else {
            model = Slic3r::Model::read_from_file(
                path,
                nullptr,
                nullptr,
                Slic3r::LoadStrategy::AddDefaultInstances | Slic3r::LoadStrategy::LoadModel);
        }

        if (model.objects.empty()) {
            set_last_error(engine, "loaded model contains no objects");
            return ORCA_ERROR_LOAD_MODEL;
        }

        engine->impl.model = std::move(model);
        engine->impl.imported_project_plate_data.clear();
        engine->impl.imported_project_config.reset();
        ++engine->impl.model_generation;
        engine->impl.paint_object_bindings.clear();
        invalidate_paint_session_unlocked(engine);
        clear_generated_gcode(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_load_model", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_load_model", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" void orca_clear_generated_gcode(OrcaEngine* engine)
{
    clear_generated_gcode(engine);
}

static bool validate_binary_stl_output(const char* path, size_t expected_min_facets, std::string& error)
{
    if (path == nullptr || path[0] == '\0') {
        error = "converted STEP output path is empty";
        return false;
    }

    std::error_code ec;
    const std::filesystem::path output_path(path);
    if (!std::filesystem::is_regular_file(output_path, ec)) {
        error = "converted STEP STL was not written";
        return false;
    }

    const auto file_size = std::filesystem::file_size(output_path, ec);
    if (ec) {
        error = "converted STEP STL size could not be read";
        return false;
    }
    if (file_size < 84) {
        error = "converted STEP STL is too small to contain binary STL geometry";
        return false;
    }

    std::ifstream input(path, std::ios::binary);
    if (!input) {
        error = "converted STEP STL could not be reopened";
        return false;
    }

    input.seekg(80, std::ios::beg);
    unsigned char facet_count_bytes[4] = {0, 0, 0, 0};
    input.read(reinterpret_cast<char*>(facet_count_bytes), sizeof(facet_count_bytes));
    if (input.gcount() != static_cast<std::streamsize>(sizeof(facet_count_bytes))) {
        error = "converted STEP STL triangle count could not be read";
        return false;
    }

    const std::uint32_t written_facets =
        static_cast<std::uint32_t>(facet_count_bytes[0]) |
        (static_cast<std::uint32_t>(facet_count_bytes[1]) << 8) |
        (static_cast<std::uint32_t>(facet_count_bytes[2]) << 16) |
        (static_cast<std::uint32_t>(facet_count_bytes[3]) << 24);
    if (written_facets == 0) {
        error = "converted STEP STL contains zero triangles";
        return false;
    }
    if (expected_min_facets > 0 && written_facets < expected_min_facets) {
        std::ostringstream message;
        message << "converted STEP STL lost triangles while writing: expected at least "
                << expected_min_facets << ", wrote " << written_facets;
        error = message.str();
        return false;
    }

    const std::uint64_t expected_size = 84ULL + static_cast<std::uint64_t>(written_facets) * 50ULL;
    if (file_size != expected_size) {
        std::ostringstream message;
        message << "converted STEP STL has invalid binary size: expected "
                << expected_size << " bytes for " << written_facets
                << " triangles, got " << file_size;
        error = message.str();
        return false;
    }

    return true;
}

static std::string split_filesystem_safe_slug(std::string value)
{
    if (value.empty()) {
        return "split-result";
    }
    for (char& ch : value) {
        const unsigned char byte = static_cast<unsigned char>(ch);
        if (!std::isalnum(byte) && ch != '-' && ch != '_' && ch != '.') {
            ch = '-';
        }
    }
    while (!value.empty() && value.front() == '-') value.erase(value.begin());
    while (!value.empty() && value.back() == '-') value.pop_back();
    return value.empty() ? std::string("split-result") : value;
}

static void split_append_json_string(std::ostringstream& out, const std::string& value)
{
    out << '"';
    for (const char ch : value) {
        switch (ch) {
        case '\\': out << "\\\\"; break;
        case '"': out << "\\\""; break;
        case '\n': out << "\\n"; break;
        case '\r': out << "\\r"; break;
        case '\t': out << "\\t"; break;
        default: out << ch; break;
        }
    }
    out << '"';
}

static bool split_export_object_stl(Slic3r::ModelObject& object, const std::string& output_path)
{
    std::error_code ec;
    const std::filesystem::path path(output_path);
    if (path.has_parent_path()) {
        std::filesystem::create_directories(path.parent_path(), ec);
        if (ec) {
            return false;
        }
    }
    return Slic3r::store_stl(output_path.c_str(), &object, true);
}

static bool split_export_volume_stl(const Slic3r::ModelObject& source_object, const Slic3r::ModelVolume& volume, const std::string& output_path)
{
    Slic3r::Model export_model;
    Slic3r::ModelObject* export_object = export_model.add_object();
    export_object->name = volume.name.empty() ? source_object.name : volume.name;
    export_object->config.assign_config(source_object.config);
    export_object->add_volume(volume);
    if (source_object.instances.empty()) {
        export_object->add_instance();
    } else {
        for (const Slic3r::ModelInstance* instance : source_object.instances) {
            export_object->add_instance(*instance);
        }
    }
    return split_export_object_stl(*export_object, output_path);
}

static void split_append_result_item(
    std::ostringstream& out,
    bool& first,
    const std::string& label,
    const std::string& role,
    const std::string& path,
    int volume_count)
{
    if (!first) {
        out << ',';
    }
    first = false;
    out << '{';
    out << "\"label\":";
    split_append_json_string(out, label.empty() ? std::string("Split result") : label);
    out << ",\"role\":";
    split_append_json_string(out, role);
    out << ",\"filePath\":";
    split_append_json_string(out, path);
    out << ",\"volumeCount\":" << volume_count;
    out << '}';
}

extern "C" int orca_extract_model_mesh_to_stl(OrcaEngine* engine, const char* input_path, const char* output_stl_path)
{
    if (engine == nullptr ||
        input_path == nullptr ||
        input_path[0] == '\0' ||
        output_stl_path == nullptr ||
        output_stl_path[0] == '\0') {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }

    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);

    try {
        Slic3r::DynamicPrintConfig config = Slic3r::DynamicPrintConfig::full_print_config();
        Slic3r::ConfigSubstitutionContext config_substitutions(
            Slic3r::ForwardCompatibilitySubstitutionRule::EnableSilent);
        Slic3r::PlateDataPtrs plate_data_list;
        std::vector<Slic3r::Preset*> project_presets;
        struct ExtractImportGuard {
            Slic3r::PlateDataPtrs& plate_data_list;
            std::vector<Slic3r::Preset*>& project_presets;
            ~ExtractImportGuard()
            {
                Slic3r::release_PlateData_list(plate_data_list);
                for (Slic3r::Preset* preset : project_presets) {
                    delete preset;
                }
                project_presets.clear();
            }
        } import_guard{plate_data_list, project_presets};

        bool is_bbs_3mf = false;
        Slic3r::Semver file_version;
        Slic3r::Model model = Slic3r::Model::read_from_file(
            input_path,
            &config,
            &config_substitutions,
            Slic3r::LoadStrategy::AddDefaultInstances |
                Slic3r::LoadStrategy::LoadModel |
                Slic3r::LoadStrategy::Silence,
            &plate_data_list,
            &project_presets,
            &is_bbs_3mf,
            &file_version);
        if (model.objects.empty()) {
            set_last_error(engine, "loaded model contains no mesh objects");
            return ORCA_ERROR_LOAD_MODEL;
        }
        if (!Slic3r::store_stl(output_stl_path, &model, true)) {
            set_last_error(engine, "failed to extract model mesh as STL");
            return ORCA_ERROR_LOAD_MODEL;
        }
        clear_last_error(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_extract_model_mesh_to_stl", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_extract_model_mesh_to_stl", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" int orca_convert_step_to_stl(
    OrcaEngine* engine,
    const char* input_path,
    const char* output_stl_path,
    double linear_deflection,
    double angle_deflection)
{
    if (engine == nullptr ||
        input_path == nullptr ||
        input_path[0] == '\0' ||
        output_stl_path == nullptr ||
        output_stl_path[0] == '\0' ||
        linear_deflection <= 0.0 ||
        angle_deflection <= 0.0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }

    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);

#if defined(ORCA_ANDROID_REAL_STEP)
    try {
        Slic3r::Model model;
        bool cancel = false;
        Slic3r::Step step_file{std::string(input_path)};
        const bool loaded = step_file.load() == Slic3r::Step::Step_Status::LOAD_SUCCESS;
        const bool meshed = loaded &&
            step_file.mesh(&model, cancel, false, linear_deflection, angle_deflection) ==
                Slic3r::Step::Step_Status::MESH_SUCCESS;
        if (cancel) {
            set_last_error(engine, "STEP import was cancelled");
            return ORCA_ERROR_LOAD_MODEL;
        }
        if (!loaded || !meshed || model.objects.empty()) {
            set_last_error(engine, "STEP import produced no printable mesh geometry");
            return ORCA_ERROR_LOAD_MODEL;
        }
        size_t facet_count = 0;
        for (Slic3r::ModelObject* object : model.objects) {
            if (object == nullptr) {
                continue;
            }
            const Slic3r::TriangleMesh raw_mesh = object->raw_mesh();
            facet_count += raw_mesh.facets_count();
            if (object->instances.empty() && !raw_mesh.empty()) {
                object->add_instance();
            }
        }
        if (facet_count == 0) {
            set_last_error(engine, "STEP import produced no triangulated facets");
            return ORCA_ERROR_LOAD_MODEL;
        }
        if (!Slic3r::store_stl(output_stl_path, &model, true)) {
            set_last_error(engine, "failed to write converted STEP mesh as STL");
            return ORCA_ERROR_LOAD_MODEL;
        }
        std::string validation_error;
        if (!validate_binary_stl_output(output_stl_path, facet_count, validation_error)) {
            set_last_error(engine, validation_error);
            return ORCA_ERROR_LOAD_MODEL;
        }
        clear_last_error(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_convert_step_to_stl", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_convert_step_to_stl", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
#else
    set_last_error(engine, "STEP import requires the OCCT-backed Android STEP importer; rebuild with ORCA_ANDROID_REAL_STEP.");
    return ORCA_ERROR_LOAD_MODEL;
#endif
}

extern "C" int orca_split_model_mesh_to_stls(OrcaEngine* engine, const char* input_path, const char* output_directory, int split_mode)
{
    if (engine == nullptr ||
        input_path == nullptr ||
        input_path[0] == '\0' ||
        output_directory == nullptr ||
        output_directory[0] == '\0' ||
        (split_mode != 0 && split_mode != 1)) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }

    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    engine->impl.last_split_result_json.clear();

    try {
        Slic3r::DynamicPrintConfig config = Slic3r::DynamicPrintConfig::full_print_config();
        Slic3r::ConfigSubstitutionContext config_substitutions(
            Slic3r::ForwardCompatibilitySubstitutionRule::EnableSilent);
        Slic3r::PlateDataPtrs plate_data_list;
        std::vector<Slic3r::Preset*> project_presets;
        struct SplitImportGuard {
            Slic3r::PlateDataPtrs& plate_data_list;
            std::vector<Slic3r::Preset*>& project_presets;
            ~SplitImportGuard()
            {
                Slic3r::release_PlateData_list(plate_data_list);
                for (Slic3r::Preset* preset : project_presets) {
                    delete preset;
                }
                project_presets.clear();
            }
        } import_guard{plate_data_list, project_presets};

        bool is_bbs_3mf = false;
        Slic3r::Semver file_version;
        Slic3r::Model model = Slic3r::Model::read_from_file(
            input_path,
            &config,
            &config_substitutions,
            Slic3r::LoadStrategy::AddDefaultInstances |
                Slic3r::LoadStrategy::LoadModel |
                Slic3r::LoadStrategy::Silence,
            &plate_data_list,
            &project_presets,
            &is_bbs_3mf,
            &file_version);
        if (model.objects.empty()) {
            set_last_error(engine, "loaded model contains no mesh objects");
            return ORCA_ERROR_LOAD_MODEL;
        }

        const std::filesystem::path output_dir(output_directory);
        std::error_code ec;
        std::filesystem::create_directories(output_dir, ec);
        if (ec) {
            set_last_error(engine, "failed to create split output directory");
            return ORCA_ERROR_LOAD_MODEL;
        }

        std::ostringstream result_json;
        result_json << "{\"schemaVersion\":1,\"mode\":";
        split_append_json_string(result_json, split_mode == 0 ? "objects" : "parts");
        result_json << ",\"sourcePath\":";
        split_append_json_string(result_json, input_path);
        result_json << ",\"objects\":[";
        bool first_item = true;
        int exported_count = 0;

        if (split_mode == 0) {
            std::vector<Slic3r::ModelObject*> export_objects;
            std::vector<std::unique_ptr<Slic3r::Model>> owned_models;
            if (model.objects.size() > 1) {
                export_objects = model.objects;
            } else {
                owned_models.emplace_back(std::make_unique<Slic3r::Model>(model));
                Slic3r::ModelObjectPtrs split_objects;
                owned_models.back()->objects.front()->split(&split_objects);
                export_objects.assign(split_objects.begin(), split_objects.end());
            }
            for (Slic3r::ModelObject* object : export_objects) {
                if (object == nullptr || object->volumes.empty()) {
                    continue;
                }
                const std::string label = object->name.empty() ?
                    "Object " + std::to_string(exported_count + 1) :
                    object->name;
                const std::string file_name =
                    std::to_string(exported_count + 1) + "-" + split_filesystem_safe_slug(label) + ".stl";
                const std::string output_path = (output_dir / file_name).string();
                if (!split_export_object_stl(*object, output_path)) {
                    set_last_error(engine, "failed to export split object STL");
                    return ORCA_ERROR_LOAD_MODEL;
                }
                split_append_result_item(result_json, first_item, label, "object", output_path, static_cast<int>(object->volumes.size()));
                ++exported_count;
            }
        } else {
            for (Slic3r::ModelObject* object : model.objects) {
                if (object == nullptr) {
                    continue;
                }
                size_t volume_index = 0;
                while (volume_index < object->volumes.size()) {
                    Slic3r::ModelVolume* volume = object->volumes[volume_index];
                    if (volume != nullptr && volume->is_model_part() && volume->is_splittable()) {
                        const size_t created = volume->split(16);
                        volume_index += std::max<size_t>(created, 1);
                    } else {
                        ++volume_index;
                    }
                }
                for (const Slic3r::ModelVolume* volume : object->volumes) {
                    if (volume == nullptr || !volume->is_model_part() || volume->mesh().facets_count() < 3) {
                        continue;
                    }
                    const std::string base_label = volume->name.empty() ?
                        (object->name.empty() ? "Part " + std::to_string(exported_count + 1) : object->name) :
                        volume->name;
                    const std::string file_name =
                        std::to_string(exported_count + 1) + "-" + split_filesystem_safe_slug(base_label) + ".stl";
                    const std::string output_path = (output_dir / file_name).string();
                    if (!split_export_volume_stl(*object, *volume, output_path)) {
                        set_last_error(engine, "failed to export split part STL");
                        return ORCA_ERROR_LOAD_MODEL;
                    }
                    split_append_result_item(result_json, first_item, base_label, "part", output_path, 1);
                    ++exported_count;
                }
            }
        }

        result_json << "]}";
        if (exported_count <= 1) {
            set_last_error(engine, split_mode == 0 ?
                "the selected model could not be split to multiple objects" :
                "the selected model could not be split to multiple parts");
            return ORCA_ERROR_LOAD_MODEL;
        }
        engine->impl.last_split_result_json = result_json.str();
        clear_last_error(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_split_model_mesh_to_stls", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_split_model_mesh_to_stls", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" const char* orca_get_last_split_result_json(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    return engine->impl.last_split_result_json.empty() ? nullptr : engine->impl.last_split_result_json.c_str();
}

extern "C" int orca_load_project_3mf(OrcaEngine* engine, const char* path, const long long* mobile_object_ids, int count)
{
    if (engine == nullptr || path == nullptr || path[0] == '\0' || mobile_object_ids == nullptr || count <= 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    clear_generated_gcode(engine);

    try {
        Slic3r::DynamicPrintConfig config = Slic3r::DynamicPrintConfig::full_print_config();
        Slic3r::ConfigSubstitutionContext config_substitutions(
            Slic3r::ForwardCompatibilitySubstitutionRule::EnableSilent);
        Slic3r::PlateDataPtrs plate_data_list;
        std::vector<Slic3r::Preset*> project_presets;
        struct Project3mfImportGuard {
            Slic3r::PlateDataPtrs& plate_data_list;
            std::vector<Slic3r::Preset*>& project_presets;
            ~Project3mfImportGuard()
            {
                Slic3r::release_PlateData_list(plate_data_list);
                for (Slic3r::Preset* preset : project_presets) {
                    delete preset;
                }
                project_presets.clear();
            }
        } import_guard{plate_data_list, project_presets};

        bool is_bbs_3mf = false;
        Slic3r::Semver file_version;
        Slic3r::Model model = Slic3r::Model::read_from_file(
            path,
            &config,
            &config_substitutions,
            Slic3r::LoadStrategy::AddDefaultInstances |
                Slic3r::LoadStrategy::LoadModel |
                Slic3r::LoadStrategy::LoadConfig |
                Slic3r::LoadStrategy::Silence,
            &plate_data_list,
            &project_presets,
            &is_bbs_3mf,
            &file_version);

        if (model.objects.empty()) {
            set_last_error(engine, "loaded project .3mf contains no objects");
            return ORCA_ERROR_LOAD_MODEL;
        }

        for (Slic3r::ModelObject* object : model.objects) {
            if (object != nullptr && object->instances.empty()) {
                object->add_instance();
            }
        }

        engine->impl.model = std::move(model);
        engine->impl.imported_project_config = config;
        engine->impl.imported_project_plate_data.clear();
        engine->impl.imported_project_plate_data.reserve(plate_data_list.size());
        for (const Slic3r::PlateData* plate_data : plate_data_list) {
            if (plate_data != nullptr) {
                engine->impl.imported_project_plate_data.push_back(*plate_data);
            }
        }
        ++engine->impl.model_generation;
        rebuild_paint_bindings_unlocked(engine, mobile_object_ids, count);
        invalidate_paint_session_unlocked(engine);
        clear_generated_gcode(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_load_project_3mf", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_load_project_3mf", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

extern "C" int orca_load_plate_models(OrcaEngine* engine, const char* const* paths, const double* transforms, const int* extruder_ids, int count)
{
    if (engine == nullptr || paths == nullptr || transforms == nullptr || extruder_ids == nullptr || count <= 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    clear_generated_gcode(engine);

    try {
        Slic3r::Model combined_model;
        std::unordered_map<std::string, Slic3r::Model> model_cache;
        for (int index = 0; index < count; ++index) {
            const char* path = paths[index];
            if (path == nullptr || path[0] == '\0') {
                set_last_error(engine, "plate model path is empty");
                return ORCA_ERROR_INVALID_ARGUMENT;
            }

            const std::string path_key(path);
            auto cached_model = model_cache.find(path_key);
            if (cached_model == model_cache.end()) {
                Slic3r::Model model_for_cache;
                if (has_stl_extension(path)) {
                    if (!Slic3r::load_stl(path, &model_for_cache, nullptr, nullptr, 80)) {
                        set_last_error(engine, "stl load failed");
                        return ORCA_ERROR_LOAD_MODEL;
                    }
                    model_for_cache.add_default_instances();
                    for (Slic3r::ModelObject* object : model_for_cache.objects) {
                        object->input_file = path;
                    }
                } else {
                    model_for_cache = Slic3r::Model::read_from_file(
                        path,
                        nullptr,
                        nullptr,
                        Slic3r::LoadStrategy::AddDefaultInstances | Slic3r::LoadStrategy::LoadModel);
                }
                cached_model = model_cache.emplace(path_key, std::move(model_for_cache)).first;
            }
            const Slic3r::Model& loaded_model = cached_model->second;

            if (loaded_model.objects.empty()) {
                set_last_error(engine, "loaded plate model contains no objects");
                return ORCA_ERROR_LOAD_MODEL;
            }

            const int transform_offset = index * 7;
            const double x_mm = transforms[transform_offset + 0];
            const double y_mm = transforms[transform_offset + 1];
            const double z_mm = transforms[transform_offset + 2];
            const double rotation_x = transforms[transform_offset + 3];
            const double rotation_y = transforms[transform_offset + 4];
            const double rotation_z = transforms[transform_offset + 5];
            const double uniform_scale = transforms[transform_offset + 6];
            const int extruder_id = std::max(1, extruder_ids[index]);
            if (!std::isfinite(x_mm) ||
                !std::isfinite(y_mm) ||
                !std::isfinite(z_mm) ||
                !std::isfinite(rotation_x) ||
                !std::isfinite(rotation_y) ||
                !std::isfinite(rotation_z) ||
                !std::isfinite(uniform_scale) ||
                uniform_scale <= 0.0) {
                set_last_error(engine, "invalid plate model transform");
                return ORCA_ERROR_INVALID_ARGUMENT;
            }

            const Slic3r::Vec3d offset(x_mm, y_mm, z_mm);
            const Slic3r::Vec3d rotation(rotation_x, rotation_y, rotation_z);
            const Slic3r::Vec3d scaling(uniform_scale, uniform_scale, uniform_scale);
            for (Slic3r::ModelObject* source_object : loaded_model.objects) {
                if (source_object == nullptr) {
                    continue;
                }
                if (source_object->instances.empty()) {
                    source_object->add_instance();
                }
                Slic3r::ModelObject* combined_object = combined_model.add_object(*source_object);
                if (combined_object == nullptr) {
                    continue;
                }
                combined_object->input_file = path;
                combined_object->config.set_key_value("extruder", new Slic3r::ConfigOptionInt(extruder_id));
                combined_object->config.set_key_value("wall_filament", new Slic3r::ConfigOptionInt(extruder_id));
                combined_object->config.set_key_value("sparse_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                combined_object->config.set_key_value("solid_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                int assigned_volume_count = 0;
                for (Slic3r::ModelVolume* volume : combined_object->volumes) {
                    if (volume == nullptr) {
                        continue;
                    }
                    volume->config.set_key_value("extruder", new Slic3r::ConfigOptionInt(extruder_id));
                    volume->config.set_key_value("wall_filament", new Slic3r::ConfigOptionInt(extruder_id));
                    volume->config.set_key_value("sparse_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                    volume->config.set_key_value("solid_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                    ++assigned_volume_count;
                }
                {
                    std::ostringstream message;
                    message << "plate_model index=" << index
                            << " extruder=" << extruder_id
                            << " volumes=" << assigned_volume_count
                            << " path=" << path;
                    log_native_info("orca_load_plate_models", message.str());
                }
                if (combined_object->instances.empty()) {
                    combined_object->add_instance();
                }
                for (Slic3r::ModelInstance* instance : combined_object->instances) {
                    if (instance != nullptr) {
                        instance->set_scaling_factor(scaling);
                        instance->set_rotation(rotation);
                        instance->set_offset(offset);
                    }
                }
                combined_object->invalidate_bounding_box();
            }
        }

        if (combined_model.objects.empty()) {
            set_last_error(engine, "plate contains no objects");
            return ORCA_ERROR_LOAD_MODEL;
        }

        engine->impl.model = std::move(combined_model);
        engine->impl.imported_project_plate_data.clear();
        engine->impl.imported_project_config.reset();
        ++engine->impl.model_generation;
        engine->impl.paint_object_bindings.clear();
        invalidate_paint_session_unlocked(engine);
        clear_generated_gcode(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_load_plate_models", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_load_plate_models", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

#include "orca_wrapper.h"
#include "orca_wrapper_internal.h"

#include <cstdlib>
#include <iostream>
#include <memory>
#include <mutex>
#include <string>

namespace {

struct EngineDeleter {
    void operator()(OrcaEngine* engine) const
    {
        if (engine != nullptr) {
            orca_destroy(engine);
        }
    }
};

using EnginePtr = std::unique_ptr<OrcaEngine, EngineDeleter>;

int fail(const std::string& message)
{
    std::cerr << message << "\n";
    return 1;
}

bool contains(const std::string& value, const char* needle)
{
    return value.find(needle) != std::string::npos;
}

std::string bounds_json_or_empty(OrcaEngine* engine, long long object_id)
{
    const char* json = orca_paint_object_bounds_json(engine, object_id);
    return json == nullptr ? std::string{} : std::string(json);
}

int assert_valid_loaded_bounds(const char* model_path)
{
    EnginePtr engine(orca_create());
    if (!engine) {
        return fail("orca_create returned null");
    }

    const char* paths[] = {model_path};
    const double transforms[] = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0};
    const int extruders[] = {1};
    const long long object_ids[] = {42};
    const char* paint_payload = "{\"objects\":[{\"mobileObjectId\":42,\"layers\":[]}]}";
    const int load_result = orca_load_plate_models_v2(
        engine.get(),
        paths,
        transforms,
        7,
        extruders,
        object_ids,
        paint_payload,
        1);
    if (load_result != 0) {
        const char* error = orca_get_last_error(engine.get());
        return fail(std::string("orca_load_plate_models_v2 failed: ") + (error == nullptr ? "" : error));
    }

    const std::string valid_json = bounds_json_or_empty(engine.get(), 42);
    if (valid_json.empty()) {
        return fail("bounds JSON is null for loaded object id");
    }
    if (!contains(valid_json, "\"mobileObjectId\":42") ||
        !contains(valid_json, "\"volumeBounds\":[") ||
        !contains(valid_json, "\"minX\":") ||
        !contains(valid_json, "\"maxZ\":")) {
        return fail("bounds JSON is missing expected object id or bounds keys: " + valid_json);
    }

    if (!bounds_json_or_empty(engine.get(), 777).empty()) {
        return fail("bounds JSON should be null for missing object id");
    }
    if (!bounds_json_or_empty(engine.get(), 0).empty()) {
        return fail("bounds JSON should be null for invalid object id");
    }
    return 0;
}

int assert_synthetic_binding_bounds()
{
    EnginePtr engine(orca_create());
    if (!engine) {
        return fail("orca_create returned null for synthetic binding checks");
    }

    {
        std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
        OrcaEngineImpl::PaintObjectBinding multi_volume;
        multi_volume.mobile_object_id = 99;
        multi_volume.volume_bounds.push_back(PaintVolumeBounds{0.f, 1.f, 2.f, 3.f, 4.f, 5.f});
        multi_volume.volume_bounds.push_back(PaintVolumeBounds{-1.f, -2.f, -3.f, 6.f, 7.f, 8.f});
        engine->impl.paint_object_bindings[multi_volume.mobile_object_id] = multi_volume;

        OrcaEngineImpl::PaintObjectBinding empty_mesh;
        empty_mesh.mobile_object_id = 100;
        empty_mesh.volume_triangle_counts.push_back(0);
        empty_mesh.volume_fingerprints.emplace_back();
        empty_mesh.volume_bounds.emplace_back();
        engine->impl.paint_object_bindings[empty_mesh.mobile_object_id] = empty_mesh;
    }

    const std::string multi_json = bounds_json_or_empty(engine.get(), 99);
    if (!contains(multi_json, "\"mobileObjectId\":99") ||
        !contains(multi_json, "\"minX\":0") ||
        !contains(multi_json, "\"minX\":-1") ||
        !contains(multi_json, "\"maxZ\":8")) {
        return fail("multi-volume binding bounds JSON is incomplete: " + multi_json);
    }

    const std::string empty_json = bounds_json_or_empty(engine.get(), 100);
    if (!contains(empty_json, "\"mobileObjectId\":100") ||
        !contains(empty_json, "\"minX\":0") ||
        !contains(empty_json, "\"maxZ\":0")) {
        return fail("empty mesh binding bounds JSON is incomplete: " + empty_json);
    }
    return 0;
}

} // namespace

int main(int argc, char** argv)
{
    const char* model_path = argc > 1 ? argv[1] : std::getenv("ORCA_PAINT_BOUNDS_PROBE_STL");
    if (model_path == nullptr || model_path[0] == '\0') {
        return fail("usage: orca_android_paint_object_bounds_probe <cube.stl>");
    }

    if (const int result = assert_valid_loaded_bounds(model_path); result != 0) {
        return result;
    }
    if (const int result = assert_synthetic_binding_bounds(); result != 0) {
        return result;
    }
    return 0;
}

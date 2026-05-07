#ifndef ORCA_WRAPPER_PAINT_BINDING_HELPERS_H
#define ORCA_WRAPPER_PAINT_BINDING_HELPERS_H

// Private implementation header. Include through orca_wrapper_module_context.h after config helpers.
static void invalidate_paint_session_unlocked(OrcaEngine* engine)
{
    engine->impl.paint_session.reset();
    engine->impl.paint_serialized_payload.clear();
    engine->impl.paint_overlay_snapshot.clear();
}

static void rebuild_paint_bindings_unlocked(
    OrcaEngine* engine,
    const long long* mobile_object_ids,
    int count)
{
    engine->impl.paint_object_bindings.clear();
    if (!engine->impl.model.has_value() || mobile_object_ids == nullptr || count <= 0) {
        return;
    }

    const Slic3r::Model& model = *engine->impl.model;
    const int bind_count = std::min<int>(count, int(model.objects.size()));
    for (int object_index = 0; object_index < bind_count; ++object_index) {
        const long long mobile_object_id = mobile_object_ids[object_index];
        if (mobile_object_id <= 0) {
            continue;
        }
        const Slic3r::ModelObject* object = model.objects[object_index];
        if (object == nullptr) {
            continue;
        }

        OrcaEngineImpl::PaintObjectBinding binding;
        binding.mobile_object_id = mobile_object_id;
        binding.model_object_indices.push_back(object_index);
        binding.volume_triangle_counts.reserve(object->volumes.size());
        binding.volume_fingerprints.reserve(object->volumes.size());
        binding.volume_bounds.reserve(object->volumes.size());
        for (const Slic3r::ModelVolume* volume : object->volumes) {
            if (volume == nullptr || volume->mesh().empty()) {
                binding.volume_triangle_counts.push_back(0);
                binding.volume_fingerprints.emplace_back();
                binding.volume_bounds.emplace_back();
            } else {
                binding.volume_triangle_counts.push_back(int(volume->mesh().facets_count()));
                binding.volume_fingerprints.push_back(mobileslicer::orca_paint::mesh_fingerprint(volume->mesh()));
                binding.volume_bounds.push_back(native_volume_bounds(volume->mesh()));
            }
        }
        engine->impl.paint_object_bindings[mobile_object_id] = std::move(binding);
    }
}

#endif // ORCA_WRAPPER_PAINT_BINDING_HELPERS_H

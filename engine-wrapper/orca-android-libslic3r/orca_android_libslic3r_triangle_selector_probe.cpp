#include <exception>
#include <iostream>
#include <memory>
#include <string>
#include <vector>

#include "libslic3r/Model.hpp"
#include "libslic3r/TriangleMesh.hpp"
#include "libslic3r/TriangleSelector.hpp"
#include "orca_native_paint.h"

namespace {

int fail(const std::string &message)
{
    std::cerr << message << "\n";
    return 1;
}

Slic3r::Vec3f facet_center(const Slic3r::TriangleMesh &mesh, int facet_idx)
{
    const Slic3r::Vec3i32 face = mesh.its.indices[facet_idx];
    return (mesh.its.vertices[face[0]] + mesh.its.vertices[face[1]] + mesh.its.vertices[face[2]]) / 3.f;
}

std::string first_nonempty_triangle_string(const Slic3r::FacetsAnnotation &annotation, int facet_count, int &triangle_idx)
{
    for (int i = 0; i < facet_count; ++i) {
        std::string value = annotation.get_triangle_as_string(i);
        if (!value.empty()) {
            triangle_idx = i;
            return value;
        }
    }

    triangle_idx = -1;
    return {};
}

mobileslicer::orca_paint::Ray paint_ray_for_facet(const Slic3r::TriangleMesh &mesh, int facet_idx)
{
    const Slic3r::Vec3i32 face = mesh.its.indices[facet_idx];
    const Slic3r::Vec3f center = facet_center(mesh, facet_idx);
    Slic3r::Vec3f normal = (mesh.its.vertices[face[1]] - mesh.its.vertices[face[0]])
        .cross(mesh.its.vertices[face[2]] - mesh.its.vertices[face[0]]);
    const float normal_length = normal.norm();
    normal = normal_length > 1e-8f ? normal / normal_length : Slic3r::Vec3f(0.f, 0.f, 1.f);
    return {
        center + normal * 100.f,
        -normal
    };
}

mobileslicer::orca_paint::Brush support_brush()
{
    mobileslicer::orca_paint::Brush brush;
    brush.shape = mobileslicer::orca_paint::BrushShape::Circle;
    brush.state = Slic3r::EnforcerBlockerType::ENFORCER;
    brush.radius_mm = 2.0f;
    brush.triangle_splitting = true;
    return brush;
}

mobileslicer::orca_paint::Brush fill_brush()
{
    mobileslicer::orca_paint::Brush brush = support_brush();
    brush.shape = mobileslicer::orca_paint::BrushShape::BucketFill;
    brush.smart_fill_angle_deg = 180.0f;
    return brush;
}

mobileslicer::orca_paint::Brush smart_fill_brush()
{
    mobileslicer::orca_paint::Brush brush = support_brush();
    brush.shape = mobileslicer::orca_paint::BrushShape::SmartFill;
    brush.smart_fill_angle_deg = 30.0f;
    brush.highlight_by_angle_deg = 0.0f;
    return brush;
}

mobileslicer::orca_paint::Brush color_brush()
{
    mobileslicer::orca_paint::Brush brush = support_brush();
    brush.state = Slic3r::EnforcerBlockerType::Extruder3;
    return brush;
}

mobileslicer::orca_paint::Brush fuzzy_brush()
{
    mobileslicer::orca_paint::Brush brush = support_brush();
    brush.state = Slic3r::EnforcerBlockerType::FUZZY_SKIN;
    return brush;
}

std::vector<mobileslicer::orca_paint::Ray> axis_ray_candidates(const Slic3r::TriangleMesh &mesh)
{
    Slic3r::Vec3f min_corner = mesh.its.vertices.front();
    Slic3r::Vec3f max_corner = mesh.its.vertices.front();
    for (const Slic3r::Vec3f &vertex : mesh.its.vertices) {
        min_corner = min_corner.cwiseMin(vertex);
        max_corner = max_corner.cwiseMax(vertex);
    }
    const Slic3r::Vec3f center = (min_corner + max_corner) * 0.5f;
    const float distance = (max_corner - min_corner).norm() + 100.f;
    const std::vector<Slic3r::Vec3f> directions {
        Slic3r::Vec3f(1.f, 0.f, 0.f),
        Slic3r::Vec3f(-1.f, 0.f, 0.f),
        Slic3r::Vec3f(0.f, 1.f, 0.f),
        Slic3r::Vec3f(0.f, -1.f, 0.f),
        Slic3r::Vec3f(0.f, 0.f, 1.f),
        Slic3r::Vec3f(0.f, 0.f, -1.f),
    };

    std::vector<mobileslicer::orca_paint::Ray> rays;
    rays.reserve(directions.size());
    for (const Slic3r::Vec3f &direction : directions) {
        rays.push_back({
            center - direction * distance,
            direction
        });
    }
    return rays;
}

} // namespace

int main()
{
    try {
        Slic3r::TriangleMesh mesh(Slic3r::its_make_cube(20.0, 20.0, 20.0));
        if (mesh.empty() || mesh.facets_count() <= 0)
            return fail("cube mesh is empty");

        constexpr int start_facet = 0;
        const Slic3r::Vec3f center = facet_center(mesh, start_facet);
        const Slic3r::Vec3f camera = center + Slic3r::Vec3f(0.f, 0.f, 100.f);
        const Slic3r::Transform3d identity = Slic3r::Transform3d::Identity();
        const Slic3r::TriangleSelector::ClippingPlane clipping;

        Slic3r::TriangleSelector selector(mesh);
        selector.select_patch(
            start_facet,
            Slic3r::TriangleSelector::SinglePointCursor::cursor_factory(
                center,
                camera,
                2.0f,
                Slic3r::TriangleSelector::CursorType::CIRCLE,
                identity,
                clipping),
            Slic3r::EnforcerBlockerType::ENFORCER,
            identity,
            true,
            0.f);

        if (!selector.has_facets(Slic3r::EnforcerBlockerType::ENFORCER))
            return fail("TriangleSelector did not mark enforcer facets");

        const Slic3r::TriangleSelector::TriangleSplittingData serialized = selector.serialize();
        if (serialized.triangles_to_split.empty() || serialized.bitstream.empty())
            return fail("TriangleSelector serialized empty data after select_patch");

        Slic3r::TriangleSelector restored_selector(mesh);
        restored_selector.deserialize(serialized, false);
        if (!restored_selector.has_facets(Slic3r::EnforcerBlockerType::ENFORCER))
            return fail("TriangleSelector deserialize did not restore enforcer facets");

        Slic3r::Model model;
        Slic3r::ModelObject* object = model.add_object();
        Slic3r::ModelVolume* volume = object->add_volume(mesh);
        if (volume == nullptr)
            return fail("failed to create model volume for FacetsAnnotation probe");

        Slic3r::FacetsAnnotation& annotation = volume->supported_facets;
        if (!annotation.set(selector))
            return fail("FacetsAnnotation did not accept selector data");

        int triangle_idx = -1;
        const std::string triangle_payload =
            first_nonempty_triangle_string(annotation, int(mesh.facets_count()), triangle_idx);
        if (triangle_payload.empty() || triangle_idx < 0)
            return fail("FacetsAnnotation produced no per-triangle payload");

        Slic3r::Model restored_model;
        Slic3r::ModelObject* restored_object = restored_model.add_object();
        Slic3r::ModelVolume* restored_volume = restored_object->add_volume(mesh);
        if (restored_volume == nullptr)
            return fail("failed to create restored model volume for FacetsAnnotation probe");

        Slic3r::FacetsAnnotation& restored_annotation = restored_volume->supported_facets;
        restored_annotation.set_triangle_from_string(triangle_idx, triangle_payload);
        if (restored_annotation.get_triangle_as_string(triangle_idx) != triangle_payload)
            return fail("FacetsAnnotation triangle string failed to round-trip");

        if (!restored_annotation.has_facets(*restored_volume, Slic3r::EnforcerBlockerType::ENFORCER))
            return fail("FacetsAnnotation restored payload has no enforcer state");

        Slic3r::Model session_model;
        Slic3r::ModelObject* session_object = session_model.add_object();
        Slic3r::ModelVolume* session_volume = session_object->add_volume(mesh);
        if (session_volume == nullptr)
            return fail("failed to create model volume for OrcaPaintSession probe");

        mobileslicer::orca_paint::OrcaPaintSession session(
            *session_object,
            42,
            mobileslicer::orca_paint::PaintMode::Support,
            1);
        std::optional<mobileslicer::orca_paint::Ray> hit_ray;
        for (const mobileslicer::orca_paint::Ray &candidate : axis_ray_candidates(mesh)) {
            if (session.hit_test(candidate)) {
                hit_ray = candidate;
                break;
            }
        }
        if (!hit_ray)
            return fail("OrcaPaintSession probe could not find a ray that hits the cube source mesh");
        const mobileslicer::orca_paint::Brush brush = support_brush();
        if (!session.stroke_begin(*hit_ray, brush))
            return fail("OrcaPaintSession stroke_begin failed on cube source mesh");

        const mobileslicer::orca_paint::Ray miss_ray {
            Slic3r::Vec3f(500.f, 500.f, 500.f),
            Slic3r::Vec3f(0.f, 0.f, -1.f)
        };
        if (!session.stroke_move(miss_ray, brush))
            return fail("OrcaPaintSession stroke_move treated an intermittent miss as fatal");
        if (!session.stroke_move(*hit_ray, brush))
            return fail("OrcaPaintSession stroke_move failed to continue after intermittent miss");
        if (!session.stroke_end())
            return fail("OrcaPaintSession stroke_end failed after active stroke");

        const std::string payload_json = session.serialize_payload_json();
        if (payload_json.find("\"objects\"") == std::string::npos ||
            payload_json.find("\"layers\"") == std::string::npos ||
            payload_json.find("\"volumes\"") == std::string::npos ||
            payload_json.find("\"hexBits\"") == std::string::npos) {
            return fail("OrcaPaintSession payload JSON is not replay-compatible or is empty");
        }

        const std::string overlay_json = session.overlay_snapshot_json();
        if (overlay_json.find("\"coordinateSpace\":\"sourceMesh\"") == std::string::npos ||
            overlay_json.find("\"requiresSelectedObjectModelMatrix\":true") == std::string::npos ||
            overlay_json.find("\"vertices\"") == std::string::npos ||
            overlay_json.find("\"normals\"") == std::string::npos) {
            return fail("OrcaPaintSession overlay JSON is missing the source-mesh transform contract");
        }

        mobileslicer::orca_paint::OrcaPaintSession fill_session(
            *session_object,
            43,
            mobileslicer::orca_paint::PaintMode::Support,
            1);
        if (!fill_session.stroke_begin(*hit_ray, fill_brush()) ||
            fill_session.serialize_payload_json().find("\"hexBits\"") == std::string::npos) {
            return fail("OrcaPaintSession fill brush did not create replay payload");
        }

        mobileslicer::orca_paint::OrcaPaintSession smart_fill_session(
            *session_object,
            430,
            mobileslicer::orca_paint::PaintMode::Support,
            1);
        if (!smart_fill_session.stroke_begin(*hit_ray, smart_fill_brush()) ||
            smart_fill_session.serialize_payload_json().find("\"hexBits\"") == std::string::npos) {
            return fail("OrcaPaintSession smart-fill brush did not create replay payload");
        }

        mobileslicer::orca_paint::OrcaPaintSession color_session(
            *session_object,
            44,
            mobileslicer::orca_paint::PaintMode::Color,
            1);
        if (!color_session.stroke_begin(*hit_ray, color_brush()) ||
            color_session.serialize_payload_json().find("\"mode\":\"Color\"") == std::string::npos ||
            !color_session.commit_to_model() ||
            session_volume->mmu_segmentation_facets.empty()) {
            return fail("OrcaPaintSession color paint did not write mmu_segmentation_facets");
        }
        int color_triangle_idx = -1;
        const std::string color_triangle_payload = first_nonempty_triangle_string(
            session_volume->mmu_segmentation_facets,
            int(mesh.facets_count()),
            color_triangle_idx);
        std::string remapped_color_triangle_payload;
        std::vector<int> color_slot_remap(4, 0);
        color_slot_remap[1] = 1;
        color_slot_remap[2] = 2;
        color_slot_remap[3] = 2;
        std::string color_remap_error;
        if (color_triangle_payload.empty() ||
            !mobileslicer::orca_paint::remap_color_hex_bits(
                color_triangle_payload,
                color_slot_remap,
                remapped_color_triangle_payload,
                &color_remap_error)) {
            return fail("native color slot remap failed: " + color_remap_error);
        }
        Slic3r::Model remapped_color_model;
        Slic3r::ModelObject* remapped_color_object = remapped_color_model.add_object();
        Slic3r::ModelVolume* remapped_color_volume = remapped_color_object->add_volume(mesh);
        remapped_color_volume->mmu_segmentation_facets.set_triangle_from_string(
            color_triangle_idx,
            remapped_color_triangle_payload);
        if (!remapped_color_volume->mmu_segmentation_facets.has_facets(
                *remapped_color_volume,
                Slic3r::EnforcerBlockerType::Extruder2)) {
            return fail("native color slot remap did not rewrite painted state to target slot");
        }
        std::string deleted_slot_result;
        std::string deleted_slot_error;
        std::vector<int> deleted_slot_remap(4, 0);
        deleted_slot_remap[1] = 1;
        deleted_slot_remap[2] = 2;
        deleted_slot_remap[3] = 0;
        if (mobileslicer::orca_paint::remap_color_hex_bits(
                color_triangle_payload,
                deleted_slot_remap,
                deleted_slot_result,
                &deleted_slot_error)) {
            return fail("native color slot remap unexpectedly succeeded for a deleted painted slot");
        }
        if (deleted_slot_error.find("deleted filament slot") == std::string::npos) {
            return fail("native color slot remap did not explain deleted-slot failure: " + deleted_slot_error);
        }

        mobileslicer::orca_paint::OrcaPaintSession fuzzy_session(
            *session_object,
            45,
            mobileslicer::orca_paint::PaintMode::FuzzySkin,
            1);
        if (!fuzzy_session.stroke_begin(*hit_ray, fuzzy_brush()) ||
            fuzzy_session.serialize_payload_json().find("\"mode\":\"FuzzySkin\"") == std::string::npos ||
            !fuzzy_session.commit_to_model() ||
            session_volume->fuzzy_skin_facets.empty()) {
            return fail("OrcaPaintSession fuzzy paint did not write fuzzy_skin_facets");
        }

        Slic3r::TriangleMesh shifted_mesh(Slic3r::its_make_cube(20.0, 20.0, 20.0));
        shifted_mesh.translate(60.f, 0.f, 0.f);
        Slic3r::Model multi_model;
        Slic3r::ModelObject* first_object = multi_model.add_object();
        Slic3r::ModelObject* second_object = multi_model.add_object();
        if (first_object == nullptr || second_object == nullptr)
            return fail("failed to create multi-object paint model");
        if (first_object->add_volume(mesh, false) == nullptr)
            return fail("failed to add first multi-object volume");
        Slic3r::ModelVolume* second_volume = second_object->add_volume(shifted_mesh, false);
        if (second_volume == nullptr)
            return fail("failed to add second multi-object volume");

        std::vector<Slic3r::ModelObject*> bound_objects { first_object, second_object };
        mobileslicer::orca_paint::OrcaPaintSession multi_session(
            bound_objects,
            46,
            mobileslicer::orca_paint::PaintMode::Support,
            1);
        std::optional<mobileslicer::orca_paint::Ray> shifted_hit_ray;
        for (const mobileslicer::orca_paint::Ray &candidate : axis_ray_candidates(shifted_mesh)) {
            const std::optional<mobileslicer::orca_paint::PaintHit> hit = multi_session.hit_test(candidate);
            if (hit && hit->volume_index == 1) {
                shifted_hit_ray = candidate;
                break;
            }
        }
        if (!shifted_hit_ray)
            return fail("multi-ModelObject paint session did not hit the second bound object's source mesh");
        if (!multi_session.stroke_begin(*shifted_hit_ray, support_brush()) ||
            !multi_session.stroke_end() ||
            !multi_session.commit_to_model() ||
            second_volume->supported_facets.empty()) {
            return fail("multi-ModelObject paint session did not paint the hit bound object");
        }
        const std::string multi_payload_json = multi_session.serialize_payload_json();
        if (multi_payload_json.find("\"volumeIndex\":1") == std::string::npos ||
            multi_payload_json.find("\"mobileObjectId\":46") == std::string::npos) {
            return fail("multi-ModelObject paint session did not serialize flattened source volume indices");
        }

        std::cout << "facets=" << mesh.facets_count()
                  << " split_triangles=" << serialized.triangles_to_split.size()
                  << " bitstream_bits=" << serialized.bitstream.size()
                  << " payload_triangle=" << triangle_idx
                  << " payload_hex_len=" << triangle_payload.size()
                  << " session_payload_len=" << payload_json.size()
                  << " overlay_len=" << overlay_json.size()
                  << " fill_payload=ok"
                  << " smart_fill_payload=ok"
                  << " color_payload=ok"
                  << " color_remap=ok"
                  << " color_remap_deleted=ok"
                  << " fuzzy_payload=ok"
                  << " multi_object_payload=ok"
                  << "\n";
        return 0;
    } catch (const std::exception &ex) {
        return fail(std::string("exception: ") + ex.what());
    }
}

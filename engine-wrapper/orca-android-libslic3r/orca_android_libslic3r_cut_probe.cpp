#include <cmath>
#include <exception>
#include <iostream>
#include <string>
#include <vector>

#include "libslic3r/CutUtils.hpp"
#include "libslic3r/Geometry.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/TriangleMesh.hpp"

namespace {

int fail(const std::string& message)
{
    std::cerr << message << "\n";
    return 1;
}

Slic3r::Model make_cube_model()
{
    Slic3r::Model model;
    Slic3r::ModelObject* object = model.add_object();
    object->name = "cut_probe_cube";
    Slic3r::TriangleMesh mesh(Slic3r::its_make_cube(20.0, 20.0, 20.0));
    object->add_volume(mesh);
    object->add_instance();
    return model;
}

Slic3r::Transform3d centered_cut_matrix()
{
    return Slic3r::Geometry::translation_transform(10.0 * Slic3r::Vec3d::UnitZ());
}

bool all_objects_have_finite_nonempty_parts(const Slic3r::ModelObjectPtrs& objects)
{
    if (objects.empty())
        return false;

    for (const Slic3r::ModelObject* object : objects) {
        if (object == nullptr || object->volumes.empty())
            return false;
        bool has_model_part = false;
        for (const Slic3r::ModelVolume* volume : object->volumes) {
            if (volume == nullptr)
                return false;
            if (volume->is_model_part()) {
                has_model_part = true;
                if (volume->mesh().empty())
                    return false;
                const Slic3r::BoundingBoxf3 bounds = volume->mesh().bounding_box();
                if (!bounds.defined ||
                    !std::isfinite(bounds.min.x()) || !std::isfinite(bounds.min.y()) || !std::isfinite(bounds.min.z()) ||
                    !std::isfinite(bounds.max.x()) || !std::isfinite(bounds.max.y()) || !std::isfinite(bounds.max.z())) {
                    return false;
                }
            }
        }
        if (!has_model_part)
            return false;
    }
    return true;
}

indexed_triangle_set connector_mesh(const Slic3r::CutConnectorAttributes& attributes)
{
    int sector_count = 0;
    switch (attributes.shape) {
    case Slic3r::CutConnectorShape::Triangle: sector_count = 3; break;
    case Slic3r::CutConnectorShape::Square: sector_count = 4; break;
    case Slic3r::CutConnectorShape::Circle: sector_count = 180; break;
    case Slic3r::CutConnectorShape::Hexagon: sector_count = 6; break;
    default: sector_count = 180; break;
    }

    if (attributes.type == Slic3r::CutConnectorType::Snap)
        return Slic3r::its_make_snap(1.0, 1.0, 0.3f, 0.15f);
    if (attributes.style == Slic3r::CutConnectorStyle::Prism)
        return Slic3r::its_make_cylinder(1.0, 1.0, 2.0 * PI / double(sector_count));
    if (attributes.type == Slic3r::CutConnectorType::Plug)
        return Slic3r::its_make_frustum(1.0, 1.0, 2.0 * PI / double(sector_count));
    return Slic3r::its_make_frustum_dowel(1.0, 1.0, sector_count);
}

void add_connector_volume(
    Slic3r::ModelObject& object,
    Slic3r::CutConnectorType type,
    Slic3r::CutConnectorStyle style,
    Slic3r::CutConnectorShape shape)
{
    Slic3r::CutConnector connector(
        Slic3r::Vec3d(10.0, 10.0, 10.0),
        Slic3r::Transform3d::Identity(),
        2.0f,
        3.0f,
        0.05f,
        0.1f,
        0.0f,
        Slic3r::CutConnectorAttributes(type, style, shape));

    if (connector.attribs.type == Slic3r::CutConnectorType::Dowel) {
        if (connector.attribs.style == Slic3r::CutConnectorStyle::Prism)
            connector.height *= 2.0f;
    } else {
        connector.pos += Slic3r::Vec3d::UnitZ() * 0.5 * double(connector.height);
    }

    Slic3r::TriangleMesh mesh(connector_mesh(connector.attribs));
    Slic3r::ModelVolume* volume = object.add_volume(std::move(mesh), Slic3r::ModelVolumeType::NEGATIVE_VOLUME);
    volume->name = "Connector-1";
    volume->set_transformation(
        Slic3r::Geometry::translation_transform(connector.pos) *
        connector.rotation_m *
        Slic3r::Geometry::rotation_transform(-connector.z_angle * Slic3r::Vec3d::UnitZ()) *
        Slic3r::Geometry::scale_transform(Slic3r::Vec3f(connector.radius, connector.radius, connector.height).cast<double>()));
    volume->cut_info = { connector.attribs.type, connector.radius_tolerance, connector.height_tolerance };
}

bool has_cut_connector_volume(const Slic3r::ModelObjectPtrs& objects)
{
    for (const Slic3r::ModelObject* object : objects)
        for (const Slic3r::ModelVolume* volume : object->volumes)
            if (volume->cut_info.is_connector)
                return true;
    return false;
}

struct ConnectorVolumeStats {
    size_t model_parts = 0;
    size_t negative_or_modifier_parts = 0;
    size_t processed = 0;
};

ConnectorVolumeStats connector_volume_stats(const Slic3r::ModelObject* object)
{
    ConnectorVolumeStats stats;
    if (object == nullptr)
        return stats;

    for (const Slic3r::ModelVolume* volume : object->volumes) {
        if (volume == nullptr || !volume->cut_info.is_connector)
            continue;
        if (volume->is_model_part())
            ++stats.model_parts;
        else
            ++stats.negative_or_modifier_parts;
        if (volume->cut_info.is_processed)
            ++stats.processed;
    }
    return stats;
}

int validate_connector_result(
    const std::string& name,
    const Slic3r::ModelObjectPtrs& result,
    Slic3r::CutConnectorType type,
    bool create_dowels)
{
    if (result.size() < 2)
        return fail(name + " did not produce upper/lower cut objects");

    const ConnectorVolumeStats upper = connector_volume_stats(result[0]);
    const ConnectorVolumeStats lower = connector_volume_stats(result[1]);
    if (upper.processed == 0 || lower.processed == 0)
        return fail(name + " did not process connector metadata on both cut halves");

    if (type == Slic3r::CutConnectorType::Plug || type == Slic3r::CutConnectorType::Snap) {
        if (upper.negative_or_modifier_parts == 0)
            return fail(name + " missing upper negative connector hole");
        if (lower.model_parts == 0)
            return fail(name + " missing lower positive connector plug");
        return 0;
    }

    if (create_dowels) {
        if (upper.negative_or_modifier_parts == 0 || lower.negative_or_modifier_parts == 0)
            return fail(name + " missing dowel holes on both cut halves");
        bool has_dowel_object = false;
        for (size_t i = 2; i < result.size(); ++i) {
            const ConnectorVolumeStats dowel = connector_volume_stats(result[i]);
            if (dowel.model_parts > 0 && dowel.processed > 0) {
                has_dowel_object = true;
                break;
            }
        }
        if (!has_dowel_object)
            return fail(name + " missing separate positive dowel object");
    }
    return 0;
}

int run_plane_case(const std::string& name, Slic3r::ModelObjectCutAttributes attributes, size_t expected_objects)
{
    Slic3r::Model model = make_cube_model();
    Slic3r::Cut cut(model.objects.front(), 0, centered_cut_matrix(), attributes);
    const Slic3r::ModelObjectPtrs& result = cut.perform_with_plane();
    if (result.size() != expected_objects) {
        std::cerr << name << " expected objects=" << expected_objects << " actual=" << result.size() << "\n";
        return 1;
    }
    if (!all_objects_have_finite_nonempty_parts(result))
        return fail(name + " produced invalid result geometry");
    return 0;
}

int run_connector_case(
    const std::string& name,
    Slic3r::CutConnectorType type,
    Slic3r::CutConnectorStyle style,
    Slic3r::CutConnectorShape shape,
    bool create_dowels)
{
    Slic3r::Model model = make_cube_model();
    add_connector_volume(*model.objects.front(), type, style, shape);
    Slic3r::ModelObjectCutAttributes attributes =
        Slic3r::ModelObjectCutAttribute::KeepUpper |
        Slic3r::ModelObjectCutAttribute::KeepLower;
    if (create_dowels)
        attributes = attributes | Slic3r::ModelObjectCutAttribute::CreateDowels;

    Slic3r::Cut cut(model.objects.front(), 0, centered_cut_matrix(), attributes);
    const Slic3r::ModelObjectPtrs& result = cut.perform_with_plane();
    const size_t minimum_objects = create_dowels ? 3 : 2;
    if (result.size() < minimum_objects) {
        std::cerr << name << " expected at least objects=" << minimum_objects << " actual=" << result.size() << "\n";
        return 1;
    }
    if (!all_objects_have_finite_nonempty_parts(result))
        return fail(name + " produced invalid result geometry");
    if (!has_cut_connector_volume(result))
        return fail(name + " produced no connector metadata");
    if (int rc = validate_connector_result(name, result, type, create_dowels); rc != 0)
        return rc;
    return 0;
}

int run_groove_case()
{
    Slic3r::Model model = make_cube_model();
    Slic3r::Cut::Groove groove;
    groove.depth = 2.0f;
    groove.width = 4.0f;
    groove.flaps_angle = float(PI / 4.0);
    groove.angle = 0.0f;
    groove.depth_tolerance = 0.1f;
    groove.width_tolerance = 0.1f;

    Slic3r::Cut cut(model.objects.front(), 0, centered_cut_matrix());
    const Slic3r::ModelObjectPtrs& result = cut.perform_with_groove(groove, Slic3r::Transform3d::Identity());
    if (result.size() != 2)
        return fail("groove cut did not produce two objects");
    if (!all_objects_have_finite_nonempty_parts(result))
        return fail("groove cut produced invalid result geometry");
    return 0;
}

} // namespace

int main()
{
    try {
        if (int rc = run_plane_case(
                "plane_keep_both",
                Slic3r::ModelObjectCutAttribute::KeepUpper |
                    Slic3r::ModelObjectCutAttribute::KeepLower,
                2); rc != 0) {
            return rc;
        }
        if (int rc = run_plane_case(
                "plane_keep_upper",
                Slic3r::ModelObjectCutAttribute::KeepUpper,
                1); rc != 0) {
            return rc;
        }
        if (int rc = run_plane_case(
                "plane_keep_lower",
                Slic3r::ModelObjectCutAttribute::KeepLower,
                1); rc != 0) {
            return rc;
        }
        if (int rc = run_plane_case(
                "plane_keep_as_parts",
                Slic3r::ModelObjectCutAttribute::KeepUpper |
                    Slic3r::ModelObjectCutAttribute::KeepLower |
                    Slic3r::ModelObjectCutAttribute::KeepAsParts,
                1); rc != 0) {
            return rc;
        }
        if (int rc = run_connector_case(
                "plug_connector",
                Slic3r::CutConnectorType::Plug,
                Slic3r::CutConnectorStyle::Prism,
                Slic3r::CutConnectorShape::Circle,
                false); rc != 0) {
            return rc;
        }
        if (int rc = run_connector_case(
                "dowel_connector",
                Slic3r::CutConnectorType::Dowel,
                Slic3r::CutConnectorStyle::Prism,
                Slic3r::CutConnectorShape::Hexagon,
                true); rc != 0) {
            return rc;
        }
        if (int rc = run_connector_case(
                "snap_connector",
                Slic3r::CutConnectorType::Snap,
                Slic3r::CutConnectorStyle::Prism,
                Slic3r::CutConnectorShape::Circle,
                false); rc != 0) {
            return rc;
        }
        if (int rc = run_groove_case(); rc != 0)
            return rc;

        std::cout << "orca_android_libslic3r_cut_probe passed\n";
        return 0;
    } catch (const std::exception& ex) {
        std::cerr << "exception: " << ex.what() << "\n";
        return 1;
    }
}

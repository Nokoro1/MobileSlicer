#ifndef MOBILE_SLICER_ORCA_NATIVE_PAINT_H
#define MOBILE_SLICER_ORCA_NATIVE_PAINT_H

#include "libslic3r/Model.hpp"
#include "libslic3r/Point.hpp"
#include "libslic3r/TriangleSelector.hpp"

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>

namespace mobileslicer::orca_paint {

enum class PaintMode {
    Support,
    Seam,
    Color,
    FuzzySkin,
};

enum class BrushShape {
    Circle,
    Sphere,
    BucketFill,
    SmartFill,
    Pointer,
    HeightRange,
    GapFill,
};

struct Ray {
    Slic3r::Vec3f origin;
    Slic3r::Vec3f direction;
};

struct Brush {
    BrushShape shape{BrushShape::Circle};
    Slic3r::EnforcerBlockerType state{Slic3r::EnforcerBlockerType::ENFORCER};
    float radius_mm{2.f};
    bool triangle_splitting{true};
    float highlight_by_angle_deg{0.f};
    float smart_fill_angle_deg{30.f};
    float height_mm{2.f};
    Slic3r::TriangleSelector::ClippingPlane clipping;
};

struct PaintHit {
    int volume_index{-1};
    int facet_index{-1};
    float distance{0.f};
    Slic3r::Vec3f point{0.f, 0.f, 0.f};
    Slic3r::Vec3f normal{0.f, 0.f, 0.f};
};

struct TrianglePayload {
    int triangle_index{-1};
    std::string hex;
};

struct VolumePayload {
    int volume_index{-1};
    int triangle_count{0};
    std::string source_mesh_fingerprint;
    std::vector<TrianglePayload> triangles;
};

struct PaintPayload {
    int schema_version{1};
    PaintMode mode{PaintMode::Support};
    std::uint64_t object_id{0};
    std::vector<VolumePayload> volumes;
};

std::string paint_payload_to_json(const PaintPayload& payload);
bool remap_color_hex_bits(const std::string& hex, const std::vector<int>& old_slot_to_new_slot, std::string& remapped_hex, std::string* error);

class OrcaPaintSession {
public:
    OrcaPaintSession(Slic3r::ModelObject& object, std::uint64_t object_id, PaintMode mode, std::uint64_t model_generation);
    OrcaPaintSession(const std::vector<Slic3r::ModelObject*>& objects, std::uint64_t object_id, PaintMode mode, std::uint64_t model_generation);
    ~OrcaPaintSession();

    OrcaPaintSession(const OrcaPaintSession&) = delete;
    OrcaPaintSession& operator=(const OrcaPaintSession&) = delete;

    std::uint64_t model_generation() const { return m_model_generation; }
    std::uint64_t object_id() const { return m_object_id; }
    PaintMode mode() const { return m_mode; }

    std::optional<PaintHit> hit_test(const Ray& ray) const;

    bool stroke_begin(const Ray& ray, const Brush& brush);
    bool stroke_move(const Ray& ray, const Brush& brush);
    bool stroke_end();

    bool clear();
    bool undo();
    bool redo();
    bool can_undo() const;
    bool can_redo() const;

    bool commit_to_model();
    PaintPayload serialize_payload() const;
    std::string serialize_payload_json() const;
    int total_source_facets() const;
    std::string overlay_snapshot_json() const;
    std::vector<float> overlay_snapshot_interleaved() const;
    std::string overlay_delta_json();
    std::vector<float> overlay_delta_interleaved();
    bool deserialize_payload(const PaintPayload& payload);

private:
    struct Impl;
    std::unique_ptr<Impl> m_impl;
    std::uint64_t m_object_id{0};
    PaintMode m_mode{PaintMode::Support};
    std::uint64_t m_model_generation{0};
};

std::string mesh_fingerprint(const Slic3r::TriangleMesh& mesh);

} // namespace mobileslicer::orca_paint

#endif

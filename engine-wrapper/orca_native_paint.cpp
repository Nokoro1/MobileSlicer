#include "orca_native_paint.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstring>
#include <functional>
#include <iterator>
#include <limits>
#include <numeric>
#include <sstream>
#include <stdexcept>
#include <unordered_map>

namespace mobileslicer::orca_paint {
namespace {

// Keep live paint feedback small enough for one frame on Android. Larger
// batches make flat/high-density regions allocate huge JNI FloatArrays and
// stall or OOM while dragging.
constexpr size_t kPaintOverlayDeltaSourceTriangleBudget = 24;
constexpr size_t kPaintOverlayDeltaScanSourceTriangleBudget = 80;
constexpr int kPaintOverlaySpatialGridResolution = 24;
constexpr size_t kPaintUndoHistoryMaxBytes = 8 * 1024 * 1024;

struct Bounds {
    Slic3r::Vec3f min{
        std::numeric_limits<float>::max(),
        std::numeric_limits<float>::max(),
        std::numeric_limits<float>::max()};
    Slic3r::Vec3f max{
        -std::numeric_limits<float>::max(),
        -std::numeric_limits<float>::max(),
        -std::numeric_limits<float>::max()};

    void expand(const Slic3r::Vec3f& point)
    {
        min = min.cwiseMin(point);
        max = max.cwiseMax(point);
    }

    void expand(const Bounds& other)
    {
        expand(other.min);
        expand(other.max);
    }

    int longest_axis() const
    {
        const Slic3r::Vec3f extents = max - min;
        if (extents.x() >= extents.y() && extents.x() >= extents.z())
            return 0;
        return extents.y() >= extents.z() ? 1 : 2;
    }
};

Bounds triangle_bounds(const Slic3r::TriangleMesh& mesh, int facet_idx)
{
    const Slic3r::Vec3i32 face = mesh.its.indices[facet_idx];
    Bounds bounds;
    bounds.expand(mesh.its.vertices[face[0]]);
    bounds.expand(mesh.its.vertices[face[1]]);
    bounds.expand(mesh.its.vertices[face[2]]);
    return bounds;
}

Bounds mesh_bounds(const Slic3r::TriangleMesh& mesh)
{
    Bounds bounds;
    for (const Slic3r::Vec3f& vertex : mesh.its.vertices)
        bounds.expand(vertex);
    return bounds;
}

void append_bounds_json(std::ostringstream& out, const Bounds& bounds)
{
    out << "{\"minX\":" << bounds.min.x()
        << ",\"minY\":" << bounds.min.y()
        << ",\"minZ\":" << bounds.min.z()
        << ",\"maxX\":" << bounds.max.x()
        << ",\"maxY\":" << bounds.max.y()
        << ",\"maxZ\":" << bounds.max.z()
        << '}';
}

Slic3r::Vec3f triangle_center(const Slic3r::TriangleMesh& mesh, int facet_idx)
{
    const Slic3r::Vec3i32 face = mesh.its.indices[facet_idx];
    return (mesh.its.vertices[face[0]] + mesh.its.vertices[face[1]] + mesh.its.vertices[face[2]]) / 3.f;
}

int overlay_spatial_bucket_for_point(const Slic3r::Vec3f& point, const Bounds& bounds)
{
    const Slic3r::Vec3f extents = bounds.max - bounds.min;
    int coords[3]{0, 0, 0};
    for (int axis = 0; axis < 3; ++axis) {
        if (extents[axis] <= 1e-6f || !std::isfinite(extents[axis]))
            continue;
        const float normalized = (point[axis] - bounds.min[axis]) / extents[axis];
        coords[axis] = std::clamp(
            int(std::floor(normalized * float(kPaintOverlaySpatialGridResolution))),
            0,
            kPaintOverlaySpatialGridResolution - 1);
    }
    return coords[0] +
        coords[1] * kPaintOverlaySpatialGridResolution +
        coords[2] * kPaintOverlaySpatialGridResolution * kPaintOverlaySpatialGridResolution;
}

Slic3r::Vec3f triangle_normal(const Slic3r::TriangleMesh& mesh, int facet_idx)
{
    const Slic3r::Vec3i32 face = mesh.its.indices[facet_idx];
    const Slic3r::Vec3f& a = mesh.its.vertices[face[0]];
    const Slic3r::Vec3f& b = mesh.its.vertices[face[1]];
    const Slic3r::Vec3f& c = mesh.its.vertices[face[2]];
    Slic3r::Vec3f normal = (b - a).cross(c - a);
    const float norm = normal.norm();
    return norm > 0.f ? normal / norm : Slic3r::Vec3f(0.f, 0.f, 1.f);
}

bool intersect_bounds(const Bounds& bounds, const Ray& ray, float max_distance)
{
    float tmin = 0.f;
    float tmax = max_distance;

    for (int axis = 0; axis < 3; ++axis) {
        const float origin = ray.origin[axis];
        const float direction = ray.direction[axis];
        if (std::abs(direction) < 1e-8f) {
            if (origin < bounds.min[axis] || origin > bounds.max[axis])
                return false;
            continue;
        }

        const float inv_direction = 1.f / direction;
        float near_t = (bounds.min[axis] - origin) * inv_direction;
        float far_t = (bounds.max[axis] - origin) * inv_direction;
        if (near_t > far_t)
            std::swap(near_t, far_t);

        tmin = std::max(tmin, near_t);
        tmax = std::min(tmax, far_t);
        if (tmin > tmax)
            return false;
    }

    return true;
}

bool intersect_triangle(const Slic3r::TriangleMesh& mesh, int facet_idx, const Ray& ray, float& out_t)
{
    const Slic3r::Vec3i32 face = mesh.its.indices[facet_idx];
    const Slic3r::Vec3f& v0 = mesh.its.vertices[face[0]];
    const Slic3r::Vec3f& v1 = mesh.its.vertices[face[1]];
    const Slic3r::Vec3f& v2 = mesh.its.vertices[face[2]];

    const Slic3r::Vec3f edge1 = v1 - v0;
    const Slic3r::Vec3f edge2 = v2 - v0;
    const Slic3r::Vec3f pvec = ray.direction.cross(edge2);
    const float det = edge1.dot(pvec);
    if (std::abs(det) < 1e-8f)
        return false;

    const float inv_det = 1.f / det;
    const Slic3r::Vec3f tvec = ray.origin - v0;
    const float u = tvec.dot(pvec) * inv_det;
    if (u < 0.f || u > 1.f)
        return false;

    const Slic3r::Vec3f qvec = tvec.cross(edge1);
    const float v = ray.direction.dot(qvec) * inv_det;
    if (v < 0.f || u + v > 1.f)
        return false;

    const float t = edge2.dot(qvec) * inv_det;
    if (t <= 0.f)
        return false;

    out_t = t;
    return true;
}

Slic3r::EnforcerBlockerType max_state_for_mode(PaintMode mode)
{
    return mode == PaintMode::Color ?
        Slic3r::EnforcerBlockerType::ExtruderMax :
        Slic3r::EnforcerBlockerType::BLOCKER;
}

Slic3r::FacetsAnnotation& annotation_for_mode(Slic3r::ModelVolume& volume, PaintMode mode)
{
    switch (mode) {
    case PaintMode::Support:
        return volume.supported_facets;
    case PaintMode::Seam:
        return volume.seam_facets;
    case PaintMode::Color:
        return volume.mmu_segmentation_facets;
    case PaintMode::FuzzySkin:
        return volume.fuzzy_skin_facets;
    }
    return volume.supported_facets;
}

const Slic3r::FacetsAnnotation& annotation_for_mode(const Slic3r::ModelVolume& volume, PaintMode mode)
{
    return annotation_for_mode(const_cast<Slic3r::ModelVolume&>(volume), mode);
}

std::string triangle_as_string(const Slic3r::TriangleSelector::TriangleSplittingData& data, int triangle_idx)
{
    std::string out;
    auto triangle_it = std::lower_bound(
        data.triangles_to_split.begin(),
        data.triangles_to_split.end(),
        triangle_idx,
        [](const Slic3r::TriangleSelector::TriangleBitStreamMapping& lhs, int rhs) {
            return lhs.triangle_idx < rhs;
        });

    if (triangle_it == data.triangles_to_split.end() || triangle_it->triangle_idx != triangle_idx)
        return out;

    int offset = triangle_it->bitstream_start_idx;
    const int end = ++triangle_it == data.triangles_to_split.end() ? int(data.bitstream.size()) : triangle_it->bitstream_start_idx;
    while (offset < end) {
        int next_code = 0;
        for (int i = 3; i >= 0; --i) {
            next_code <<= 1;
            next_code |= int(data.bitstream[offset + i]);
        }
        offset += 4;
        out.insert(out.begin(), next_code < 10 ? char(next_code + '0') : char((next_code - 10) + 'A'));
    }
    return out;
}

std::string triangle_mapping_as_string(
    const Slic3r::TriangleSelector::TriangleSplittingData& data,
    std::vector<Slic3r::TriangleSelector::TriangleBitStreamMapping>::const_iterator triangle_it)
{
    std::string out;
    if (triangle_it == data.triangles_to_split.end())
        return out;

    int offset = triangle_it->bitstream_start_idx;
    const int end = std::next(triangle_it) == data.triangles_to_split.end() ?
        int(data.bitstream.size()) :
        std::next(triangle_it)->bitstream_start_idx;
    while (offset < end) {
        int next_code = 0;
        for (int i = 3; i >= 0; --i) {
            next_code <<= 1;
            next_code |= int(data.bitstream[offset + i]);
        }
        offset += 4;
        out.insert(out.begin(), next_code < 10 ? char(next_code + '0') : char((next_code - 10) + 'A'));
    }
    return out;
}

bool append_triangle_from_string(Slic3r::TriangleSelector::TriangleSplittingData& data, int triangle_idx, const std::string& hex)
{
    if (hex.empty())
        return false;
    if (!data.triangles_to_split.empty() && data.triangles_to_split.back().triangle_idx >= triangle_idx)
        return false;

    data.triangles_to_split.emplace_back(triangle_idx, int(data.bitstream.size()));
    const size_t bitstream_start_idx = data.bitstream.size();
    for (auto it = hex.crbegin(); it != hex.crend(); ++it) {
        const char ch = *it;
        int dec = 0;
        if (ch >= '0' && ch <= '9')
            dec = int(ch - '0');
        else if (ch >= 'A' && ch <= 'F')
            dec = 10 + int(ch - 'A');
        else
            return false;

        for (int i = 0; i < 4; ++i)
            data.bitstream.insert(data.bitstream.end(), bool(dec & (1 << i)));
    }

    data.update_used_states(bitstream_start_idx);
    return true;
}

void mark_used_color_slots_from_hex(const std::string& hex, std::vector<bool>& used_slots)
{
    std::vector<bool> bits;
    bits.reserve(hex.size() * 4);
    for (auto it = hex.crbegin(); it != hex.crend(); ++it) {
        const char ch = *it;
        int value = 0;
        if (ch >= '0' && ch <= '9')
            value = int(ch - '0');
        else if (ch >= 'A' && ch <= 'F')
            value = 10 + int(ch - 'A');
        else if (ch >= 'a' && ch <= 'f')
            value = 10 + int(ch - 'a');
        else
            return;
        for (int bit = 0; bit < 4; ++bit)
            bits.push_back((value & (1 << bit)) != 0);
    }

    size_t cursor = 0;
    auto read_nibble = [&]() -> std::optional<int> {
        if (cursor + 4 > bits.size())
            return std::nullopt;
        int value = 0;
        for (int bit = 0; bit < 4; ++bit)
            value |= int(bits[cursor++]) << bit;
        return value;
    };

    std::function<void()> walk_node = [&]() {
        const std::optional<int> code = read_nibble();
        if (!code)
            return;
        const int split_sides = *code & 0b11;
        const int child_count = split_sides == 0 ? 0 : split_sides + 1;
        if (child_count > 0) {
            for (int child = 0; child < child_count; ++child)
                walk_node();
            return;
        }

        int state = 0;
        if ((*code & 0b1100) == 0b1100) {
            const std::optional<int> extended = read_nibble();
            if (!extended)
                return;
            state = *extended + 3;
        } else {
            state = *code >> 2;
        }
        if (state >= int(Slic3r::EnforcerBlockerType::Extruder1) &&
            state <= int(Slic3r::EnforcerBlockerType::ExtruderMax)) {
            const int slot = state - int(Slic3r::EnforcerBlockerType::Extruder1) + 1;
            if (slot >= 0 && slot < int(used_slots.size()))
                used_slots[size_t(slot)] = true;
        }
    };
    walk_node();
}

void append_json_escaped(std::ostringstream& out, const std::string& value)
{
    out << '"';
    for (const char ch : value) {
        switch (ch) {
        case '\\':
            out << "\\\\";
            break;
        case '"':
            out << "\\\"";
            break;
        case '\n':
            out << "\\n";
            break;
        case '\r':
            out << "\\r";
            break;
        case '\t':
            out << "\\t";
            break;
        default:
            out << ch;
            break;
        }
    }
    out << '"';
}

const char* mode_name(PaintMode mode)
{
    switch (mode) {
    case PaintMode::Support:
        return "Support";
    case PaintMode::Seam:
        return "Seam";
    case PaintMode::Color:
        return "Color";
    case PaintMode::FuzzySkin:
        return "FuzzySkin";
    }
    return "Support";
}

std::vector<Slic3r::EnforcerBlockerType> overlay_states_for_mode(PaintMode mode)
{
    switch (mode) {
    case PaintMode::Color: {
        std::vector<Slic3r::EnforcerBlockerType> states;
        for (int state = int(Slic3r::EnforcerBlockerType::Extruder1);
             state <= int(Slic3r::EnforcerBlockerType::ExtruderMax);
             ++state) {
            states.push_back(static_cast<Slic3r::EnforcerBlockerType>(state));
        }
        return states;
    }
    case PaintMode::FuzzySkin:
        return {Slic3r::EnforcerBlockerType::FUZZY_SKIN};
    case PaintMode::Support:
    case PaintMode::Seam:
        return {
            Slic3r::EnforcerBlockerType::ENFORCER,
            Slic3r::EnforcerBlockerType::BLOCKER,
        };
    }
    return {Slic3r::EnforcerBlockerType::ENFORCER};
}

int overlay_color_for_state(PaintMode mode, Slic3r::EnforcerBlockerType state)
{
    if (mode == PaintMode::Color) {
        static constexpr std::array<int, 16> colors = {
            0x6688A2FF, 0x66FF7043, 0x66FFD166, 0x6606D6A0,
            0x66EF476F, 0x66118AB2, 0x667833FF, 0x66F15BB5,
            0x6600BBF9, 0x669B5DE5, 0x66FEE440, 0x6600F5D4,
            0x66FF9F1C, 0x662EC4B6, 0x66E71D36, 0x66A7C957,
        };
        const int index = std::clamp(
            int(state) - int(Slic3r::EnforcerBlockerType::Extruder1),
            0,
            int(colors.size()) - 1);
        return colors[size_t(index)];
    }
    if (mode == PaintMode::FuzzySkin)
        return 0xFF9C4DCC;
    if (mode == PaintMode::Seam)
        return state == Slic3r::EnforcerBlockerType::BLOCKER ? 0xFFE94B5F : 0xFF2F80ED;
    if (mode == PaintMode::Support)
        return state == Slic3r::EnforcerBlockerType::BLOCKER ? 0xFFE94B5F : 0xFF20B455;
    if (state == Slic3r::EnforcerBlockerType::ENFORCER)
        return 0xFF2F80ED;
    if (state == Slic3r::EnforcerBlockerType::BLOCKER)
        return 0xFFE94B5F;
    return 0xFFFFAA00;
}

} // namespace

class TriangleBvh {
public:
    explicit TriangleBvh(const Slic3r::TriangleMesh& mesh)
        : m_mesh(mesh)
    {
        std::vector<int> facets(mesh.facets_count());
        std::iota(facets.begin(), facets.end(), 0);
        m_centers.reserve(facets.size());
        for (const int facet : facets)
            m_centers.push_back(triangle_center(m_mesh, facet));
        if (!facets.empty())
            build_node(facets.begin(), facets.end());
    }

    std::optional<PaintHit> hit_test(const Ray& ray, int volume_index) const
    {
        if (m_nodes.empty())
            return std::nullopt;

        float best_t = std::numeric_limits<float>::max();
        int best_facet = -1;
        hit_node(0, ray, best_t, best_facet);
        if (best_facet < 0)
            return std::nullopt;

        PaintHit hit;
        hit.volume_index = volume_index;
        hit.facet_index = best_facet;
        hit.distance = best_t;
        hit.point = ray.origin + ray.direction * best_t;
        hit.normal = triangle_normal(m_mesh, best_facet);
        return hit;
    }

private:
    struct Node {
        Bounds bounds;
        int left{-1};
        int right{-1};
        int begin{0};
        int end{0};
        bool leaf{false};
    };

    using FacetIterator = std::vector<int>::iterator;

    int build_node(FacetIterator begin, FacetIterator end)
    {
        Node node;
        for (auto it = begin; it != end; ++it)
            node.bounds.expand(triangle_bounds(m_mesh, *it));

        const int node_index = int(m_nodes.size());
        m_nodes.push_back(node);

        const int count = int(std::distance(begin, end));
        if (count <= 8) {
            m_nodes[node_index].leaf = true;
            m_nodes[node_index].begin = int(m_facets.size());
            m_facets.insert(m_facets.end(), begin, end);
            m_nodes[node_index].end = int(m_facets.size());
            return node_index;
        }

        const int axis = node.bounds.longest_axis();
        const FacetIterator middle = begin + count / 2;
        std::nth_element(begin, middle, end, [this, axis](int lhs, int rhs) {
            return m_centers[size_t(lhs)][axis] < m_centers[size_t(rhs)][axis];
        });

        m_nodes[node_index].left = build_node(begin, middle);
        m_nodes[node_index].right = build_node(middle, end);
        return node_index;
    }

    void hit_node(int node_index, const Ray& ray, float& best_t, int& best_facet) const
    {
        const Node& node = m_nodes[node_index];
        if (!intersect_bounds(node.bounds, ray, best_t))
            return;

        if (node.leaf) {
            for (int i = node.begin; i < node.end; ++i) {
                float t = 0.f;
                if (intersect_triangle(m_mesh, m_facets[i], ray, t) && t < best_t) {
                    best_t = t;
                    best_facet = m_facets[i];
                }
            }
            return;
        }

        hit_node(node.left, ray, best_t, best_facet);
        hit_node(node.right, ray, best_t, best_facet);
    }

    const Slic3r::TriangleMesh& m_mesh;
    std::vector<Slic3r::Vec3f> m_centers;
    std::vector<Node> m_nodes;
    std::vector<int> m_facets;
};

struct OrcaPaintSession::Impl {
    struct VolumeState {
        Slic3r::ModelVolume* volume{nullptr};
        int source_volume_index{-1};
        std::unique_ptr<Slic3r::TriangleSelector> selector;
        std::unique_ptr<TriangleBvh> bvh;
        std::string fingerprint;
        Bounds bounds;
        std::vector<int> overlay_bucket_for_source;
        std::unordered_map<int, std::vector<int>> overlay_sources_by_bucket;
    };

    explicit Impl(const std::vector<Slic3r::ModelObject*>& objects, PaintMode paint_mode)
        : mode(paint_mode)
    {
        size_t reserve_count = 0;
        for (const Slic3r::ModelObject* object : objects) {
            if (object != nullptr)
                reserve_count += object->volumes.size();
        }
        volumes.reserve(reserve_count);

        int flattened_volume_index = 0;
        for (Slic3r::ModelObject* object : objects) {
            if (object == nullptr)
                continue;
            for (Slic3r::ModelVolume* volume : object->volumes) {
                const int source_volume_index = flattened_volume_index++;
                if (volume == nullptr || volume->mesh().empty())
                    continue;

                VolumeState state;
                state.volume = volume;
                state.source_volume_index = source_volume_index;
                state.selector = std::make_unique<Slic3r::TriangleSelector>(volume->mesh());
                state.selector->deserialize(annotation_for_mode(*volume, mode).get_data(), false, max_state_for_mode(mode));
                state.bvh = std::make_unique<TriangleBvh>(volume->mesh());
                state.fingerprint = mesh_fingerprint(volume->mesh());
                state.bounds = mesh_bounds(volume->mesh());
                const int source_triangle_count = int(volume->mesh().its.indices.size());
                std::vector<Slic3r::Vec3f> triangle_centers;
                triangle_centers.reserve(size_t(source_triangle_count));
                for (int source = 0; source < source_triangle_count; ++source)
                    triangle_centers.push_back(triangle_center(volume->mesh(), source));
                state.overlay_bucket_for_source.assign(size_t(source_triangle_count), -1);
                state.overlay_sources_by_bucket.reserve(size_t(source_triangle_count / 16 + 1));
                for (int source = 0; source < source_triangle_count; ++source) {
                    const int bucket = overlay_spatial_bucket_for_point(triangle_centers[size_t(source)], state.bounds);
                    state.overlay_bucket_for_source[size_t(source)] = bucket;
                    state.overlay_sources_by_bucket[bucket].push_back(source);
                }
                volumes.emplace_back(std::move(state));
            }
        }
    }

    PaintMode mode{PaintMode::Support};
    std::vector<VolumeState> volumes;
    std::vector<std::vector<Slic3r::TriangleSelector::TriangleSplittingData>> undo_stack;
    std::vector<std::vector<Slic3r::TriangleSelector::TriangleSplittingData>> redo_stack;
    std::optional<PaintHit> previous_hit;
    std::optional<Ray> previous_ray;
    bool stroke_active{false};

    static size_t snapshot_cost(const std::vector<Slic3r::TriangleSelector::TriangleSplittingData>& snapshot)
    {
        size_t cost = 0;
        for (const Slic3r::TriangleSelector::TriangleSplittingData& data : snapshot) {
            cost += data.triangles_to_split.size() * sizeof(Slic3r::TriangleSelector::TriangleBitStreamMapping);
            cost += (data.bitstream.size() + 7) / 8;
            cost += (data.used_states.size() + 7) / 8;
        }
        return cost;
    }

    static void prune_history_to_budget(std::vector<std::vector<Slic3r::TriangleSelector::TriangleSplittingData>>& history)
    {
        size_t total = 0;
        for (const auto& snapshot : history)
            total += snapshot_cost(snapshot);
        while (!history.empty() && total > kPaintUndoHistoryMaxBytes) {
            total -= snapshot_cost(history.front());
            history.erase(history.begin());
        }
    }

    std::vector<Slic3r::TriangleSelector::TriangleSplittingData> snapshot() const
    {
        std::vector<Slic3r::TriangleSelector::TriangleSplittingData> data;
        data.reserve(volumes.size());
        for (const VolumeState& volume : volumes)
            data.emplace_back(volume.selector->serialize());
        return data;
    }

    void restore(const std::vector<Slic3r::TriangleSelector::TriangleSplittingData>& snapshot)
    {
        for (size_t i = 0; i < volumes.size() && i < snapshot.size(); ++i)
            volumes[i].selector->deserialize(snapshot[i], true, max_state_for_mode(mode));
    }

    void apply_brush_to_hit(VolumeState& volume, const PaintHit& hit, const Ray& ray, const Brush& brush, const std::optional<PaintHit>& previous)
    {
        if (brush.shape == BrushShape::BucketFill) {
            volume.selector->bucket_fill_select_triangles(
                hit.point,
                hit.facet_index,
                brush.clipping,
                brush.smart_fill_angle_deg,
                true,
                true);
            volume.selector->seed_fill_apply_on_triangles(brush.state);
            return;
        }

        if (brush.shape == BrushShape::Pointer) {
            volume.selector->bucket_fill_select_triangles(
                hit.point,
                hit.facet_index,
                brush.clipping,
                -1.f,
                false,
                true);
            volume.selector->seed_fill_apply_on_triangles(brush.state);
            return;
        }

        if (brush.shape == BrushShape::SmartFill) {
            volume.selector->seed_fill_select_triangles(
                hit.point,
                hit.facet_index,
                Slic3r::Transform3d::Identity(),
                brush.clipping,
                brush.smart_fill_angle_deg,
                brush.highlight_by_angle_deg,
                true);
            volume.selector->seed_fill_apply_on_triangles(brush.state);
            return;
        }

        if (brush.shape == BrushShape::GapFill) {
            volume.selector->bucket_fill_select_triangles(
                hit.point,
                hit.facet_index,
                brush.clipping,
                brush.smart_fill_angle_deg,
                true,
                true);
            volume.selector->seed_fill_apply_on_triangles(brush.state);
            return;
        }

        if (brush.shape == BrushShape::HeightRange) {
            volume.selector->select_patch(
                hit.facet_index,
                Slic3r::TriangleSelector::SinglePointCursor::cursor_factory(
                    hit.point.z(),
                    ray.origin,
                    brush.height_mm,
                    Slic3r::Transform3d::Identity(),
                    brush.clipping),
                brush.state,
                Slic3r::Transform3d::Identity(),
                brush.triangle_splitting,
                brush.highlight_by_angle_deg);
            return;
        }

        const Slic3r::TriangleSelector::CursorType cursor_type =
            brush.shape == BrushShape::Sphere ? Slic3r::TriangleSelector::CursorType::SPHERE : Slic3r::TriangleSelector::CursorType::CIRCLE;
        if (previous && previous->volume_index == hit.volume_index) {
            volume.selector->select_patch(
                hit.facet_index,
                Slic3r::TriangleSelector::DoublePointCursor::cursor_factory(
                    previous->point,
                    hit.point,
                    ray.origin,
                    brush.radius_mm,
                    cursor_type,
                    Slic3r::Transform3d::Identity(),
                    brush.clipping),
                brush.state,
                Slic3r::Transform3d::Identity(),
                brush.triangle_splitting,
                brush.highlight_by_angle_deg);
        } else {
            volume.selector->select_patch(
                hit.facet_index,
                Slic3r::TriangleSelector::SinglePointCursor::cursor_factory(
                    hit.point,
                    ray.origin,
                    brush.radius_mm,
                    cursor_type,
                    Slic3r::Transform3d::Identity(),
                    brush.clipping),
                brush.state,
                Slic3r::Transform3d::Identity(),
                brush.triangle_splitting,
                brush.highlight_by_angle_deg);
        }
    }

    void push_undo_snapshot(std::vector<Slic3r::TriangleSelector::TriangleSplittingData> snapshot_data)
    {
        undo_stack.emplace_back(std::move(snapshot_data));
        prune_history_to_budget(undo_stack);
        redo_stack.clear();
    }
};

OrcaPaintSession::OrcaPaintSession(Slic3r::ModelObject& object, std::uint64_t object_id, PaintMode mode, std::uint64_t model_generation)
    : OrcaPaintSession(std::vector<Slic3r::ModelObject*>{&object}, object_id, mode, model_generation)
{
}

OrcaPaintSession::OrcaPaintSession(const std::vector<Slic3r::ModelObject*>& objects, std::uint64_t object_id, PaintMode mode, std::uint64_t model_generation)
    : m_impl(std::make_unique<Impl>(objects, mode))
    , m_object_id(object_id)
    , m_mode(mode)
    , m_model_generation(model_generation)
{
}

OrcaPaintSession::~OrcaPaintSession() = default;

std::optional<PaintHit> OrcaPaintSession::hit_test(const Ray& ray) const
{
    if (ray.direction.squaredNorm() <= 0.f)
        return std::nullopt;

    Ray normalized = ray;
    normalized.direction.normalize();

    std::optional<PaintHit> best_hit;
    for (int i = 0; i < int(m_impl->volumes.size()); ++i) {
        std::optional<PaintHit> hit = m_impl->volumes[i].bvh->hit_test(normalized, i);
        if (hit && (!best_hit || hit->distance < best_hit->distance))
            best_hit = hit;
    }
    return best_hit;
}

bool OrcaPaintSession::stroke_begin(const Ray& ray, const Brush& brush)
{
    Ray normalized = ray;
    if (normalized.direction.squaredNorm() > 0.f)
        normalized.direction.normalize();
    const std::optional<PaintHit> hit = hit_test(normalized);
    if (!hit)
        return false;

    m_impl->push_undo_snapshot(m_impl->snapshot());
    m_impl->stroke_active = true;
    m_impl->previous_hit = hit;
    m_impl->previous_ray = normalized;

    Impl::VolumeState& volume = m_impl->volumes[hit->volume_index];
    m_impl->apply_brush_to_hit(volume, *hit, normalized, brush, std::nullopt);
    return true;
}

bool OrcaPaintSession::stroke_move(const Ray& ray, const Brush& brush)
{
    if (!m_impl->stroke_active)
        return stroke_begin(ray, brush);

    Ray normalized = ray;
    if (normalized.direction.squaredNorm() > 0.f)
        normalized.direction.normalize();
    const std::optional<PaintHit> hit = hit_test(normalized);
    if (!hit) {
        // Keep the stroke alive through intermittent off-mesh samples. Resetting
        // previous_hit prevents a later re-entry from painting a capsule across
        // the skipped gap.
        m_impl->previous_hit.reset();
        m_impl->previous_ray = normalized;
        return true;
    }

    if (
        m_impl->previous_hit &&
        m_impl->previous_ray &&
        m_impl->previous_hit->volume_index == hit->volume_index &&
        (brush.shape == BrushShape::Circle || brush.shape == BrushShape::Sphere)
    ) {
        const float min_spacing = std::max(0.15f, brush.radius_mm * 0.34f);
        const Slic3r::Vec3f delta = hit->point - m_impl->previous_hit->point;
        if (delta.squaredNorm() < min_spacing * min_spacing)
            return true;
        const int steps = std::clamp(int(std::ceil(delta.norm() / min_spacing)), 1, 6);
        for (int step = 1; step <= steps; ++step) {
            const float t = float(step) / float(steps);
            Ray sample;
            sample.origin = m_impl->previous_ray->origin * (1.f - t) + normalized.origin * t;
            sample.direction = m_impl->previous_ray->direction * (1.f - t) + normalized.direction * t;
            if (sample.direction.squaredNorm() <= 0.f)
                continue;
            sample.direction.normalize();
            const std::optional<PaintHit> sample_hit = hit_test(sample);
            if (!sample_hit) {
                m_impl->previous_hit.reset();
                continue;
            }
            Impl::VolumeState& sample_volume = m_impl->volumes[sample_hit->volume_index];
            m_impl->apply_brush_to_hit(sample_volume, *sample_hit, sample, brush, m_impl->previous_hit);
            m_impl->previous_hit = sample_hit;
        }
        m_impl->previous_ray = normalized;
        return true;
    }

    Impl::VolumeState& volume = m_impl->volumes[hit->volume_index];
    m_impl->apply_brush_to_hit(volume, *hit, normalized, brush, m_impl->previous_hit);

    m_impl->previous_hit = hit;
    m_impl->previous_ray = normalized;
    return true;
}

bool OrcaPaintSession::stroke_end()
{
    const bool was_active = m_impl->stroke_active;
    m_impl->stroke_active = false;
    m_impl->previous_hit.reset();
    m_impl->previous_ray.reset();
    if (was_active && !m_impl->undo_stack.empty() && m_impl->undo_stack.back() == m_impl->snapshot())
        m_impl->undo_stack.pop_back();
    return was_active;
}

bool OrcaPaintSession::clear()
{
    m_impl->push_undo_snapshot(m_impl->snapshot());
    for (Impl::VolumeState& volume : m_impl->volumes)
        volume.selector = std::make_unique<Slic3r::TriangleSelector>(volume.volume->mesh());
    return true;
}

bool OrcaPaintSession::undo()
{
    if (m_impl->undo_stack.empty())
        return false;
    m_impl->redo_stack.emplace_back(m_impl->snapshot());
    Impl::prune_history_to_budget(m_impl->redo_stack);
    m_impl->restore(m_impl->undo_stack.back());
    m_impl->undo_stack.pop_back();
    return true;
}

bool OrcaPaintSession::redo()
{
    if (m_impl->redo_stack.empty())
        return false;
    m_impl->undo_stack.emplace_back(m_impl->snapshot());
    Impl::prune_history_to_budget(m_impl->undo_stack);
    m_impl->restore(m_impl->redo_stack.back());
    m_impl->redo_stack.pop_back();
    return true;
}

bool OrcaPaintSession::can_undo() const
{
    return !m_impl->undo_stack.empty();
}

bool OrcaPaintSession::can_redo() const
{
    return !m_impl->redo_stack.empty();
}

bool OrcaPaintSession::commit_to_model()
{
    bool changed = false;
    for (Impl::VolumeState& volume : m_impl->volumes)
        changed = annotation_for_mode(*volume.volume, m_mode).set(*volume.selector) || changed;
    return changed;
}

std::string paint_payload_to_json(const PaintPayload& payload)
{
    std::ostringstream out;
    out << "{\"schemaVersion\":" << payload.schema_version
        << ",\"objects\":[{\"mobileObjectId\":" << payload.object_id
        << ",\"layers\":[{\"mode\":";
    append_json_escaped(out, mode_name(payload.mode));
    out << ",\"schemaVersion\":" << payload.schema_version
        << ",\"colorSlots\":[";
    bool first_color_slot = true;
    if (payload.mode == PaintMode::Color) {
        std::vector<bool> used_slots(17, false);
        for (const VolumePayload& volume : payload.volumes) {
            for (const TrianglePayload& triangle : volume.triangles) {
                if (triangle.hex.empty())
                    continue;
                mark_used_color_slots_from_hex(triangle.hex, used_slots);
            }
        }
        for (size_t slot = 1; slot < used_slots.size(); ++slot) {
            if (!used_slots[slot])
                continue;
            if (!first_color_slot)
                out << ',';
            first_color_slot = false;
            out << slot;
        }
    }
    out << "],\"volumes\":[";
    for (size_t volume_index = 0; volume_index < payload.volumes.size(); ++volume_index) {
        const VolumePayload& volume = payload.volumes[volume_index];
        if (volume_index > 0)
            out << ',';
        out << "{\"volumeIndex\":" << volume.volume_index
            << ",\"triangleCount\":" << volume.triangle_count
            << ",\"meshFingerprint\":";
        append_json_escaped(out, volume.source_mesh_fingerprint);
        out << ",\"triangles\":[";
        for (size_t triangle_index = 0; triangle_index < volume.triangles.size(); ++triangle_index) {
            const TrianglePayload& triangle = volume.triangles[triangle_index];
            if (triangle_index > 0)
                out << ',';
            out << "{\"triangleIndex\":" << triangle.triangle_index
                << ",\"hexBits\":";
            append_json_escaped(out, triangle.hex);
            out << '}';
        }
        out << "]}";
    }
    out << "]}]}]}";
    return out.str();
}

PaintPayload OrcaPaintSession::serialize_payload() const
{
    PaintPayload payload;
    payload.mode = m_mode;
    payload.object_id = m_object_id;
    payload.volumes.reserve(m_impl->volumes.size());

    for (int volume_index = 0; volume_index < int(m_impl->volumes.size()); ++volume_index) {
        const Impl::VolumeState& volume = m_impl->volumes[volume_index];
        const Slic3r::TriangleSelector::TriangleSplittingData data = volume.selector->serialize();

        VolumePayload volume_payload;
        volume_payload.volume_index = volume.source_volume_index;
        volume_payload.source_mesh_fingerprint = volume.fingerprint;

        volume_payload.triangle_count = int(volume.volume->mesh().facets_count());
        volume_payload.triangles.reserve(data.triangles_to_split.size());
        for (auto triangle_it = data.triangles_to_split.begin(); triangle_it != data.triangles_to_split.end(); ++triangle_it) {
            if (triangle_it->triangle_idx < 0 || triangle_it->triangle_idx >= volume_payload.triangle_count)
                continue;
            std::string hex = triangle_mapping_as_string(data, triangle_it);
            if (!hex.empty())
                volume_payload.triangles.push_back({triangle_it->triangle_idx, std::move(hex)});
        }

        if (!volume_payload.triangles.empty())
            payload.volumes.emplace_back(std::move(volume_payload));
    }

    return payload;
}

bool remap_color_hex_bits(const std::string& hex, const std::vector<int>& old_slot_to_new_slot, std::string& remapped_hex, std::string* error)
{
    std::vector<bool> input_bits;
    input_bits.reserve(hex.size() * 4);
    for (auto it = hex.crbegin(); it != hex.crend(); ++it) {
        const char ch = *it;
        int value = 0;
        if (ch >= '0' && ch <= '9')
            value = int(ch - '0');
        else if (ch >= 'A' && ch <= 'F')
            value = 10 + int(ch - 'A');
        else if (ch >= 'a' && ch <= 'f')
            value = 10 + int(ch - 'a');
        else {
            if (error != nullptr)
                *error = "color paint triangle contains non-hex bits";
            return false;
        }
        for (int bit = 0; bit < 4; ++bit)
            input_bits.push_back((value & (1 << bit)) != 0);
    }

    std::vector<bool> output_bits;
    output_bits.reserve(input_bits.size());
    size_t cursor = 0;

    auto read_nibble = [&]() -> std::optional<int> {
        if (cursor + 4 > input_bits.size())
            return std::nullopt;
        int value = 0;
        for (int bit = 0; bit < 4; ++bit)
            value |= int(input_bits[cursor++]) << bit;
        return value;
    };
    auto write_nibble = [&](int value) {
        for (int bit = 0; bit < 4; ++bit)
            output_bits.push_back((value & (1 << bit)) != 0);
    };
    auto write_leaf_state = [&](int state) {
        if (state >= 3) {
            write_nibble(0b1100);
            write_nibble(state - 3);
        } else {
            write_nibble(state << 2);
        }
    };

    std::function<bool()> remap_node = [&]() -> bool {
        const std::optional<int> code = read_nibble();
        if (!code) {
            if (error != nullptr)
                *error = "color paint triangle bitstream ended mid-node";
            return false;
        }
        const int split_sides = *code & 0b11;
        const int child_count = split_sides == 0 ? 0 : split_sides + 1;
        if (child_count > 0) {
            write_nibble(*code);
            for (int child = 0; child < child_count; ++child) {
                if (!remap_node())
                    return false;
            }
            return true;
        }

        int state = 0;
        if ((*code & 0b1100) == 0b1100) {
            const std::optional<int> extended = read_nibble();
            if (!extended) {
                if (error != nullptr)
                    *error = "color paint triangle bitstream ended mid-state";
                return false;
            }
            state = *extended + 3;
        } else {
            state = *code >> 2;
        }

        if (state >= int(Slic3r::EnforcerBlockerType::Extruder1) &&
            state <= int(Slic3r::EnforcerBlockerType::ExtruderMax)) {
            const int old_slot = state - int(Slic3r::EnforcerBlockerType::Extruder1) + 1;
            if (old_slot <= 0 || old_slot >= int(old_slot_to_new_slot.size())) {
                if (error != nullptr)
                    *error = "color paint references a filament slot without a native remap entry";
                return false;
            }
            const int new_slot = old_slot_to_new_slot[size_t(old_slot)];
            if (new_slot <= 0) {
                if (error != nullptr)
                    *error = "color paint references a deleted filament slot";
                return false;
            }
            if (new_slot > int(Slic3r::EnforcerBlockerType::ExtruderMax)) {
                if (error != nullptr)
                    *error = "color paint remap target exceeds Orca's supported extruder range";
                return false;
            }
            state = int(Slic3r::EnforcerBlockerType::Extruder1) + new_slot - 1;
        }
        write_leaf_state(state);
        return true;
    };

    if (!remap_node())
        return false;
    if (cursor != input_bits.size()) {
        if (error != nullptr)
            *error = "color paint triangle bitstream contains trailing bits";
        return false;
    }

    remapped_hex.clear();
    remapped_hex.reserve((output_bits.size() + 3) / 4);
    for (size_t offset = 0; offset < output_bits.size(); offset += 4) {
        int value = 0;
        for (int bit = 0; bit < 4 && offset + size_t(bit) < output_bits.size(); ++bit)
            value |= int(output_bits[offset + size_t(bit)]) << bit;
        remapped_hex.insert(remapped_hex.begin(), value < 10 ? char('0' + value) : char('A' + value - 10));
    }
    return !remapped_hex.empty();
}

std::string OrcaPaintSession::serialize_payload_json() const
{
    return paint_payload_to_json(serialize_payload());
}

int OrcaPaintSession::total_source_facets() const
{
    int total = 0;
    for (const Impl::VolumeState& volume : m_impl->volumes)
        total += int(volume.volume->mesh().facets_count());
    return total;
}

std::string OrcaPaintSession::overlay_snapshot_json() const
{
    std::ostringstream out;
    out << "{\"schemaVersion\":1,\"mode\":";
    append_json_escaped(out, mode_name(m_mode));
    out << ",\"mobileObjectId\":" << m_object_id
        << ",\"coordinateSpace\":\"sourceMesh\""
        << ",\"requiresSelectedObjectModelMatrix\":true"
        << ",\"layers\":[";

    bool first_layer = true;
    const std::vector<Slic3r::EnforcerBlockerType> states = overlay_states_for_mode(m_mode);
    for (const Impl::VolumeState& volume : m_impl->volumes) {
        for (const Slic3r::EnforcerBlockerType state : states) {
            if (!volume.selector->has_facets(state))
                continue;
            const indexed_triangle_set facets = volume.selector->get_facets(state);
            if (facets.indices.empty())
                continue;

            if (!first_layer)
                out << ',';
            first_layer = false;
            out << "{\"id\":";
            std::ostringstream layer_id;
            layer_id << "v" << volume.source_volume_index << ":" << int(state);
            append_json_escaped(out, layer_id.str());
            out << ",\"volumeIndex\":" << volume.source_volume_index
                << ",\"state\":" << int(state)
                << ",\"colorInt\":" << overlay_color_for_state(m_mode, state)
                << ",\"coordinateSpace\":\"sourceMesh\",\"sourceBounds\":";
            append_bounds_json(out, mesh_bounds(volume.volume->mesh()));
            out
                << ",\"vertices\":[";

            bool first_vertex = true;
            std::ostringstream normals;
            normals << ",\"normals\":[";
            bool first_normal = true;
            for (const Slic3r::Vec3i32& face : facets.indices) {
                Slic3r::Vec3f normal = (facets.vertices[face[1]] - facets.vertices[face[0]])
                    .cross(facets.vertices[face[2]] - facets.vertices[face[0]]);
                const float normal_length = normal.norm();
                normal = normal_length > 1e-8f ? normal / normal_length : Slic3r::Vec3f(0.f, 0.f, 1.f);
                for (int i = 0; i < 3; ++i) {
                    const Slic3r::Vec3f& vertex = facets.vertices[face[i]];
                    if (!first_vertex)
                        out << ',';
                    first_vertex = false;
                    out << vertex.x() << ',' << vertex.y() << ',' << vertex.z();
                    if (!first_normal)
                        normals << ',';
                    first_normal = false;
                    normals << normal.x() << ',' << normal.y() << ',' << normal.z();
                }
            }
            normals << ']';
            out << ']' << normals.str() << '}';
        }
    }
    out << "]}";
    return out.str();
}

std::string OrcaPaintSession::overlay_delta_json()
{
    std::ostringstream out;
    out << "{\"schemaVersion\":1,\"mode\":";
    append_json_escaped(out, mode_name(m_mode));
    out << ",\"mobileObjectId\":" << m_object_id
        << ",\"coordinateSpace\":\"sourceMesh\""
        << ",\"requiresSelectedObjectModelMatrix\":true"
        << ",\"delta\":true"
        << ",\"layers\":[";

    bool first_layer = true;
    const std::vector<Slic3r::EnforcerBlockerType> states = overlay_states_for_mode(m_mode);
    for (Impl::VolumeState& volume : m_impl->volumes) {
        const std::vector<int> dirty_sources = volume.selector->consume_dirty_source_triangles(kPaintOverlayDeltaSourceTriangleBudget);
        if (dirty_sources.empty())
            continue;
        for (const Slic3r::EnforcerBlockerType state : states) {
            const indexed_triangle_set facets = volume.selector->get_facets_for_sources(state, dirty_sources);
            if (facets.indices.empty())
                continue;

            if (!first_layer)
                out << ',';
            first_layer = false;
            out << "{\"id\":";
            std::ostringstream layer_id;
            layer_id << "delta:v" << volume.source_volume_index << ":" << int(state) << ":" << dirty_sources.front();
            append_json_escaped(out, layer_id.str());
            out << ",\"volumeIndex\":" << volume.source_volume_index
                << ",\"state\":" << int(state)
                << ",\"colorInt\":" << overlay_color_for_state(m_mode, state)
                << ",\"coordinateSpace\":\"sourceMesh\",\"sourceBounds\":";
            append_bounds_json(out, mesh_bounds(volume.volume->mesh()));
            out << ",\"vertices\":[";

            bool first_vertex = true;
            std::ostringstream normals;
            normals << ",\"normals\":[";
            bool first_normal = true;
            for (const Slic3r::Vec3i32& face : facets.indices) {
                Slic3r::Vec3f normal = (facets.vertices[face[1]] - facets.vertices[face[0]])
                    .cross(facets.vertices[face[2]] - facets.vertices[face[0]]);
                const float normal_length = normal.norm();
                normal = normal_length > 1e-8f ? normal / normal_length : Slic3r::Vec3f(0.f, 0.f, 1.f);
                for (int i = 0; i < 3; ++i) {
                    const Slic3r::Vec3f& vertex = facets.vertices[face[i]];
                    if (!first_vertex)
                        out << ',';
                    first_vertex = false;
                    out << vertex.x() << ',' << vertex.y() << ',' << vertex.z();
                    if (!first_normal)
                        normals << ',';
                    first_normal = false;
                    normals << normal.x() << ',' << normal.y() << ',' << normal.z();
                }
            }
            normals << ']';
            out << ']' << normals.str() << '}';
        }
    }
    out << "]}";
    return out.str();
}

std::vector<float> OrcaPaintSession::overlay_snapshot_interleaved() const
{
    std::vector<float> out;
    out.push_back(0.f); // layer count, patched before return.

    int layer_count = 0;
    const std::vector<Slic3r::EnforcerBlockerType> states = overlay_states_for_mode(m_mode);
    for (const Impl::VolumeState& volume : m_impl->volumes) {
        for (const Slic3r::EnforcerBlockerType state : states) {
            if (!volume.selector->has_facets(state))
                continue;
            const indexed_triangle_set facets = volume.selector->get_facets(state);
            if (facets.indices.empty())
                continue;

            const size_t header_index = out.size();
            out.push_back(float(int(state)));
            out.push_back(float(volume.source_volume_index));
            out.push_back(volume.bounds.min.x());
            out.push_back(volume.bounds.min.y());
            out.push_back(volume.bounds.min.z());
            out.push_back(volume.bounds.max.x());
            out.push_back(volume.bounds.max.y());
            out.push_back(volume.bounds.max.z());
            out.push_back(0.f); // vertex count, patched after vertices.

            int vertex_count = 0;
            for (const Slic3r::Vec3i32& face : facets.indices) {
                Slic3r::Vec3f normal = (facets.vertices[face[1]] - facets.vertices[face[0]])
                    .cross(facets.vertices[face[2]] - facets.vertices[face[0]]);
                const float normal_length = normal.norm();
                normal = normal_length > 1e-8f ? normal / normal_length : Slic3r::Vec3f(0.f, 0.f, 1.f);
                for (int i = 0; i < 3; ++i) {
                    const Slic3r::Vec3f& vertex = facets.vertices[face[i]];
                    out.push_back(vertex.x());
                    out.push_back(vertex.y());
                    out.push_back(vertex.z());
                    out.push_back(normal.x());
                    out.push_back(normal.y());
                    out.push_back(normal.z());
                    ++vertex_count;
                }
            }
            out[header_index + 8] = float(vertex_count);
            ++layer_count;
        }
    }
    out[0] = float(layer_count);
    return out;
}

std::vector<float> OrcaPaintSession::overlay_delta_interleaved()
{
    std::vector<float> out;
    out.push_back(0.f); // negative layer count means replace-by-stable-overlay-key delta.

    int layer_count = 0;
    const std::vector<Slic3r::EnforcerBlockerType> states = overlay_states_for_mode(m_mode);
    for (Impl::VolumeState& volume : m_impl->volumes) {
        size_t scanned_sources = 0;
        while (scanned_sources < kPaintOverlayDeltaScanSourceTriangleBudget) {
            const std::vector<int> dirty_sources = volume.selector->consume_dirty_source_triangles(kPaintOverlayDeltaSourceTriangleBudget);
            if (dirty_sources.empty())
                break;
            scanned_sources += dirty_sources.size();
            const int layer_count_before_chunk = layer_count;

            std::vector<int> dirty_buckets;
            dirty_buckets.reserve(dirty_sources.size());
            for (int source_triangle : dirty_sources) {
                if (source_triangle < 0)
                    continue;
                if (source_triangle >= int(volume.overlay_bucket_for_source.size()))
                    continue;
                const int bucket = volume.overlay_bucket_for_source[size_t(source_triangle)];
                if (bucket >= 0)
                    dirty_buckets.push_back(bucket);
            }
            if (dirty_buckets.empty())
                continue;
            std::sort(dirty_buckets.begin(), dirty_buckets.end());
            dirty_buckets.erase(std::unique(dirty_buckets.begin(), dirty_buckets.end()), dirty_buckets.end());

            for (int bucket : dirty_buckets) {
                auto bucket_sources_it = volume.overlay_sources_by_bucket.find(bucket);
                if (bucket_sources_it == volume.overlay_sources_by_bucket.end() || bucket_sources_it->second.empty())
                    continue;

                // Negative source-triangle keys are stable overlay bucket ids. Android's
                // replace-by-source-key path removes the previous bucket upload for all
                // states, which avoids accumulating one GL draw/upload per source triangle
                // on dense flat meshes while still rendering exact TriangleSelector facets.
                const int overlay_key = -bucket - 1;
                const std::vector<int>& bucket_sources = bucket_sources_it->second;
                std::vector<std::pair<Slic3r::EnforcerBlockerType, indexed_triangle_set>> visible_facets;
                visible_facets.reserve(states.size());
                for (const Slic3r::EnforcerBlockerType state : states) {
                    if (!volume.selector->has_facets(state))
                        continue;
                    indexed_triangle_set facets = volume.selector->get_facets_for_sources(state, bucket_sources);
                    if (!facets.indices.empty())
                        visible_facets.emplace_back(state, std::move(facets));
                }

                if (visible_facets.empty()) {
                    if (states.empty())
                        continue;
                    const size_t header_index = out.size();
                    out.push_back(float(int(states.front())));
                    out.push_back(float(volume.source_volume_index));
                    out.push_back(float(overlay_key));
                    out.push_back(volume.bounds.min.x());
                    out.push_back(volume.bounds.min.y());
                    out.push_back(volume.bounds.min.z());
                    out.push_back(volume.bounds.max.x());
                    out.push_back(volume.bounds.max.y());
                    out.push_back(volume.bounds.max.z());
                    out.push_back(0.f);
                    out[header_index + 9] = 0.f;
                    ++layer_count;
                    continue;
                }

                for (const auto& [state, facets] : visible_facets) {
                    const size_t header_index = out.size();
                    out.push_back(float(int(state)));
                    out.push_back(float(volume.source_volume_index));
                    out.push_back(float(overlay_key));
                    out.push_back(volume.bounds.min.x());
                    out.push_back(volume.bounds.min.y());
                    out.push_back(volume.bounds.min.z());
                    out.push_back(volume.bounds.max.x());
                    out.push_back(volume.bounds.max.y());
                    out.push_back(volume.bounds.max.z());
                    out.push_back(0.f); // vertex count, patched after vertices.

                    int vertex_count = 0;
                    for (const Slic3r::Vec3i32& face : facets.indices) {
                        Slic3r::Vec3f normal = (facets.vertices[face[1]] - facets.vertices[face[0]])
                            .cross(facets.vertices[face[2]] - facets.vertices[face[0]]);
                        const float normal_length = normal.norm();
                        normal = normal_length > 1e-8f ? normal / normal_length : Slic3r::Vec3f(0.f, 0.f, 1.f);
                        for (int i = 0; i < 3; ++i) {
                            const Slic3r::Vec3f& vertex = facets.vertices[face[i]];
                            out.push_back(vertex.x());
                            out.push_back(vertex.y());
                            out.push_back(vertex.z());
                            out.push_back(normal.x());
                            out.push_back(normal.y());
                            out.push_back(normal.z());
                            ++vertex_count;
                        }
                    }
                    out[header_index + 9] = float(vertex_count);
                    ++layer_count;
                }
            }
            if (layer_count > layer_count_before_chunk) {
                out[0] = -float(layer_count);
                return out;
            }
        }
    }
    out[0] = -float(layer_count);
    return out;
}

bool OrcaPaintSession::deserialize_payload(const PaintPayload& payload)
{
    if (payload.mode != m_mode || payload.object_id != m_object_id)
        return false;

    for (Impl::VolumeState& volume : m_impl->volumes)
        volume.selector = std::make_unique<Slic3r::TriangleSelector>(volume.volume->mesh());

    for (const VolumePayload& volume_payload : payload.volumes) {
        auto volume_it = std::find_if(
            m_impl->volumes.begin(),
            m_impl->volumes.end(),
            [&volume_payload](const Impl::VolumeState& volume) {
                return volume.source_volume_index == volume_payload.volume_index;
            });
        if (volume_it == m_impl->volumes.end())
            return false;

        Impl::VolumeState& volume = *volume_it;
        if (volume_payload.source_mesh_fingerprint != volume.fingerprint)
            return false;

        Slic3r::TriangleSelector::TriangleSplittingData data;
        int previous_triangle = -1;
        for (const TrianglePayload& triangle : volume_payload.triangles) {
            if (triangle.triangle_index <= previous_triangle || triangle.hex.empty())
                return false;
            if (!append_triangle_from_string(data, triangle.triangle_index, triangle.hex))
                return false;
            previous_triangle = triangle.triangle_index;
        }
        volume.selector->deserialize(data, true, max_state_for_mode(m_mode));
    }
    return true;
}

std::string mesh_fingerprint(const Slic3r::TriangleMesh& mesh)
{
    std::uint64_t hash = 1469598103934665603ull;
    auto mix = [&hash](std::uint64_t value) {
        hash ^= value;
        hash *= 1099511628211ull;
    };
    auto mix_float = [&mix](float value) {
        std::uint32_t bits = 0;
        static_assert(sizeof(bits) == sizeof(value));
        std::memcpy(&bits, &value, sizeof(value));
        mix(bits);
    };

    mix(mesh.its.vertices.size());
    mix(mesh.its.indices.size());
    for (const Slic3r::Vec3f& vertex : mesh.its.vertices) {
        mix_float(vertex.x());
        mix_float(vertex.y());
        mix_float(vertex.z());
    }
    for (const Slic3r::Vec3i32& face : mesh.its.indices) {
        mix(std::uint64_t(face[0]));
        mix(std::uint64_t(face[1]));
        mix(std::uint64_t(face[2]));
    }

    std::ostringstream out;
    out << "fnv1a64:" << std::hex << hash;
    return out.str();
}

} // namespace mobileslicer::orca_paint

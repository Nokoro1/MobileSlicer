#include "orca_wrapper_module_context.h"

static bool skip_json_string_view(std::string_view value, size_t& cursor)
{
    if (cursor >= value.size() || value[cursor] != '"') {
        return false;
    }
    ++cursor;
    bool escaped = false;
    for (; cursor < value.size(); ++cursor) {
        const char ch = value[cursor];
        if (escaped) {
            escaped = false;
        } else if (ch == '\\') {
            escaped = true;
        } else if (ch == '"') {
            ++cursor;
            return true;
        }
    }
    return false;
}

static bool skip_json_value_view(std::string_view value, size_t& cursor)
{
    while (cursor < value.size() && std::isspace(static_cast<unsigned char>(value[cursor])) != 0) {
        ++cursor;
    }
    if (cursor >= value.size()) {
        return false;
    }
    if (value[cursor] == '"') {
        return skip_json_string_view(value, cursor);
    }
    if (value[cursor] == '{' || value[cursor] == '[') {
        const char open = value[cursor];
        const char close = open == '{' ? '}' : ']';
        int depth = 0;
        for (; cursor < value.size(); ++cursor) {
            if (value[cursor] == '"') {
                if (!skip_json_string_view(value, cursor)) {
                    return false;
                }
                --cursor;
            } else if (value[cursor] == open) {
                ++depth;
            } else if (value[cursor] == close) {
                --depth;
                if (depth == 0) {
                    ++cursor;
                    return true;
                }
            }
        }
        return false;
    }
    while (cursor < value.size() && value[cursor] != ',' && value[cursor] != '}' && value[cursor] != ']') {
        ++cursor;
    }
    return true;
}

static std::vector<std::string> json_array_items(std::string_view raw)
{
    std::vector<std::string> items;
    raw = trim_view(raw);
    if (raw.size() < 2 || raw.front() != '[' || raw.back() != ']') {
        return items;
    }
    raw.remove_prefix(1);
    raw.remove_suffix(1);
    size_t cursor = 0;
    while (cursor < raw.size()) {
        while (cursor < raw.size() && (std::isspace(static_cast<unsigned char>(raw[cursor])) != 0 || raw[cursor] == ',')) {
            ++cursor;
        }
        if (cursor >= raw.size()) {
            break;
        }
        const size_t begin = cursor;
        if (!skip_json_value_view(raw, cursor)) {
            items.clear();
            return items;
        }
        items.emplace_back(raw.substr(begin, cursor - begin));
    }
    return items;
}

static std::string json_string_value(const std::string& json, const std::string& key)
{
    return extract_string(json, key).value_or("");
}

static int json_int_value(const std::string& json, const std::string& key, int fallback = 0)
{
    if (const auto raw_value = indexed_json_value(json, key)) {
        std::string raw = trim_copy(*raw_value);
        if (!raw.empty() && raw.front() == '"' && raw.back() == '"') {
            raw = unescape_json_string(std::string_view(raw).substr(1, raw.size() - 2));
        }
        try {
            return std::stoi(raw);
        } catch (...) {
            return fallback;
        }
    }
    return fallback;
}

static long long json_long_value(const std::string& json, const std::string& key, long long fallback = 0)
{
    if (const auto raw_value = indexed_json_value(json, key)) {
        std::string raw = trim_copy(*raw_value);
        if (!raw.empty() && raw.front() == '"' && raw.back() == '"') {
            raw = unescape_json_string(std::string_view(raw).substr(1, raw.size() - 2));
        }
        try {
            return std::stoll(raw);
        } catch (...) {
            return fallback;
        }
    }
    return fallback;
}

static std::vector<std::string> json_array_member_items(const std::string& json, const std::string& key)
{
    const auto raw = indexed_json_value(json, key);
    return raw ? json_array_items(*raw) : std::vector<std::string>{};
}

static bool normalize_hex_bits(std::string& value)
{
    if (value.empty()) {
        return false;
    }
    for (char& ch : value) {
        if (ch >= 'a' && ch <= 'f') {
            ch = static_cast<char>(std::toupper(static_cast<unsigned char>(ch)));
        }
        if (!((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F'))) {
            return false;
        }
    }
    return true;
}

static std::vector<PaintObjectReplay> parse_paint_replay_payload(const char* payload_json, std::string* error)
{
    std::vector<PaintObjectReplay> payload;
    if (payload_json == nullptr || payload_json[0] == '\0') {
        return payload;
    }
    invalidate_json_scalar_index();
    const std::string root(payload_json);
    const auto raw_objects = indexed_json_value(root, "objects");
    if (!raw_objects) {
        if (error != nullptr) *error = "paint payload is missing objects";
        return {};
    }
    const std::vector<std::string> object_items = json_array_items(*raw_objects);
    if (object_items.empty()) {
        if (error != nullptr) *error = "paint payload objects is empty";
        return {};
    }
    for (const std::string& object_json : object_items) {
        invalidate_json_scalar_index();
        PaintObjectReplay object;
        object.mobile_object_id = json_long_value(object_json, "mobileObjectId", 0);
        if (object.mobile_object_id <= 0) {
            if (error != nullptr) *error = "paint payload object has invalid mobileObjectId";
            return {};
        }
        for (const std::string& layer_json : json_array_member_items(object_json, "layers")) {
            invalidate_json_scalar_index();
            PaintLayerReplay layer;
            layer.mode = json_string_value(layer_json, "mode");
            if (layer.mode.empty()) {
                if (error != nullptr) *error = "paint payload layer is missing mode";
                return {};
            }
            for (const std::string& color_slot_json : json_array_member_items(layer_json, "colorSlots")) {
                try {
                    const int color_slot = std::stoi(trim_copy(color_slot_json));
                    if (color_slot > 0) {
                        layer.color_slots.push_back(color_slot);
                    }
                } catch (...) {
                    if (error != nullptr) *error = "paint payload layer has invalid colorSlots";
                    return {};
                }
            }
            for (const std::string& volume_json : json_array_member_items(layer_json, "volumes")) {
                invalidate_json_scalar_index();
                PaintVolumeReplay volume;
                volume.volume_index = json_int_value(volume_json, "volumeIndex", -1);
                volume.triangle_count = json_int_value(volume_json, "triangleCount", 0);
                volume.mesh_fingerprint = json_string_value(volume_json, "meshFingerprint");
                if (volume.volume_index < 0) {
                    if (error != nullptr) *error = "paint payload volume has invalid volumeIndex";
                    return {};
                }
                for (const std::string& triangle_json : json_array_member_items(volume_json, "triangles")) {
                    invalidate_json_scalar_index();
                    PaintTriangleReplay triangle;
                    triangle.triangle_index = json_int_value(triangle_json, "triangleIndex", -1);
                    triangle.hex_bits = json_string_value(triangle_json, "hexBits");
                    if (triangle.triangle_index < 0 || !normalize_hex_bits(triangle.hex_bits)) {
                        if (error != nullptr) *error = "paint payload triangle has invalid hexBits";
                        return {};
                    }
                    volume.triangles.emplace_back(std::move(triangle));
                }
                if (!volume.triangles.empty()) {
                    layer.volumes.emplace_back(std::move(volume));
                }
            }
            if (!layer.volumes.empty()) {
                object.layers.emplace_back(std::move(layer));
            }
        }
        if (!object.layers.empty()) {
            payload.emplace_back(std::move(object));
        }
    }
    return payload;
}

static void append_json_string(std::ostringstream& out, const std::string& value)
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

static std::string paint_replay_payload_to_json(const std::vector<PaintObjectReplay>& payload)
{
    std::ostringstream out;
    out << "{\"schemaVersion\":1,\"objects\":[";
    for (size_t object_index = 0; object_index < payload.size(); ++object_index) {
        const PaintObjectReplay& object = payload[object_index];
        if (object_index > 0)
            out << ',';
        out << "{\"mobileObjectId\":" << object.mobile_object_id << ",\"layers\":[";
        for (size_t layer_index = 0; layer_index < object.layers.size(); ++layer_index) {
            const PaintLayerReplay& layer = object.layers[layer_index];
            if (layer_index > 0)
                out << ',';
            out << "{\"mode\":";
            append_json_string(out, layer.mode);
            out << ",\"schemaVersion\":1,\"colorSlots\":[";
            for (size_t slot_index = 0; slot_index < layer.color_slots.size(); ++slot_index) {
                if (slot_index > 0)
                    out << ',';
                out << layer.color_slots[slot_index];
            }
            out << "],\"volumes\":[";
            for (size_t volume_index = 0; volume_index < layer.volumes.size(); ++volume_index) {
                const PaintVolumeReplay& volume = layer.volumes[volume_index];
                if (volume_index > 0)
                    out << ',';
                out << "{\"volumeIndex\":" << volume.volume_index
                    << ",\"triangleCount\":" << volume.triangle_count
                    << ",\"meshFingerprint\":";
                append_json_string(out, volume.mesh_fingerprint);
                out << ",\"triangles\":[";
                for (size_t triangle_index = 0; triangle_index < volume.triangles.size(); ++triangle_index) {
                    const PaintTriangleReplay& triangle = volume.triangles[triangle_index];
                    if (triangle_index > 0)
                        out << ',';
                    out << "{\"triangleIndex\":" << triangle.triangle_index
                        << ",\"hexBits\":";
                    append_json_string(out, triangle.hex_bits);
                    out << '}';
                }
                out << "]}";
            }
            out << "]}";
        }
        out << "]}";
    }
    out << "]}";
    return out.str();
}

static Slic3r::FacetsAnnotation* facets_annotation_for_mode(Slic3r::ModelVolume& volume, const std::string& mode)
{
    if (mode == "Support") {
        return &volume.supported_facets;
    }
    if (mode == "Seam") {
        return &volume.seam_facets;
    }
    if (mode == "Color") {
        return &volume.mmu_segmentation_facets;
    }
    if (mode == "FuzzySkin") {
        return &volume.fuzzy_skin_facets;
    }
    return nullptr;
}

static bool apply_paint_replay_payload(
    Slic3r::Model& model,
    const std::unordered_map<long long, OrcaEngineImpl::PaintObjectBinding>& bindings,
    const std::vector<PaintObjectReplay>& payload,
    std::string* error)
{
    for (const PaintObjectReplay& object : payload) {
        const auto binding_it = bindings.find(object.mobile_object_id);
        if (binding_it == bindings.end()) {
            if (error != nullptr) *error = "paint payload references an unknown mobileObjectId";
            return false;
        }
        const OrcaEngineImpl::PaintObjectBinding& binding = binding_it->second;
        for (const PaintLayerReplay& layer : object.layers) {
            for (const PaintVolumeReplay& volume_payload : layer.volumes) {
                if (volume_payload.volume_index < 0 ||
                    volume_payload.volume_index >= int(binding.volume_triangle_counts.size())) {
                    if (error != nullptr) *error = "paint payload references an unknown volumeIndex";
                    return false;
                }
                const int expected_triangles = binding.volume_triangle_counts[volume_payload.volume_index];
                if (volume_payload.triangle_count > 0 && volume_payload.triangle_count != expected_triangles) {
                    if (error != nullptr) *error = "paint payload triangleCount does not match loaded source mesh";
                    return false;
                }
                const std::string& expected_fingerprint = binding.volume_fingerprints[volume_payload.volume_index];
                if (!volume_payload.mesh_fingerprint.empty() &&
                    starts_with_case_insensitive(volume_payload.mesh_fingerprint, "fnv1a64:") &&
                    volume_payload.mesh_fingerprint != expected_fingerprint) {
                    if (error != nullptr) *error = "paint payload native mesh fingerprint does not match loaded source mesh";
                    return false;
                }

                int remaining_volume_index = volume_payload.volume_index;
                Slic3r::ModelVolume* target_volume = nullptr;
                for (const int object_index : binding.model_object_indices) {
                    if (object_index < 0 || object_index >= int(model.objects.size()) || model.objects[object_index] == nullptr) {
                        continue;
                    }
                    Slic3r::ModelObject* model_object = model.objects[object_index];
                    if (remaining_volume_index < int(model_object->volumes.size())) {
                        target_volume = model_object->volumes[remaining_volume_index];
                        break;
                    }
                    remaining_volume_index -= int(model_object->volumes.size());
                }
                if (target_volume == nullptr) {
                    if (error != nullptr) *error = "paint payload could not resolve target ModelVolume";
                    return false;
                }
                Slic3r::FacetsAnnotation* annotation = facets_annotation_for_mode(*target_volume, layer.mode);
                if (annotation == nullptr) {
                    if (error != nullptr) *error = "paint payload layer has unsupported mode";
                    return false;
                }

                annotation->reset();
                annotation->reserve(expected_triangles);
                int previous_triangle = -1;
                for (const PaintTriangleReplay& triangle : volume_payload.triangles) {
                    if (triangle.triangle_index <= previous_triangle || triangle.triangle_index >= expected_triangles) {
                        if (error != nullptr) *error = "paint payload triangles must be sorted and within source mesh bounds";
                        return false;
                    }
                    annotation->set_triangle_from_string(triangle.triangle_index, triangle.hex_bits);
                    previous_triangle = triangle.triangle_index;
                }
                annotation->shrink_to_fit();
                {
                    Slic3r::TriangleSelector selector(target_volume->mesh());
                    selector.deserialize(annotation->get_data(), false, layer.mode == "Color" ? Slic3r::EnforcerBlockerType::ExtruderMax :
                        (layer.mode == "Support" || layer.mode == "Seam" ? Slic3r::EnforcerBlockerType::BLOCKER : Slic3r::EnforcerBlockerType::FUZZY_SKIN));
                    annotation->set(selector);
                }
                annotation->touch();

                if (layer.mode == "Color" && int(volume_payload.triangles.size()) == expected_triangles) {
                    const std::vector<Slic3r::EnforcerBlockerType> used_states =
                        Slic3r::TriangleSelector::extract_used_facet_states(annotation->get_data());
                    const bool has_single_payload_slot = layer.color_slots.size() == 1 &&
                        layer.color_slots.front() >= 1 &&
                        layer.color_slots.front() <= int(Slic3r::EnforcerBlockerType::ExtruderMax);
                    const bool has_single_extracted_slot = used_states.size() == 1 &&
                        int(used_states.front()) >= int(Slic3r::EnforcerBlockerType::Extruder1) &&
                        int(used_states.front()) <= int(Slic3r::EnforcerBlockerType::ExtruderMax);
                    if ((has_single_payload_slot || has_single_extracted_slot) &&
                        std::all_of(volume_payload.triangles.begin(), volume_payload.triangles.end(), [](const PaintTriangleReplay& triangle) {
                            return triangle.hex_bits.size() == 1;
                        })) {
                        const int extruder_id = has_single_payload_slot ? layer.color_slots.front() : int(used_states.front());
                        if (Slic3r::ModelObject* object = target_volume->get_object(); object != nullptr) {
                            object->config.set_key_value("extruder", new Slic3r::ConfigOptionInt(extruder_id));
                            object->config.set_key_value("wall_filament", new Slic3r::ConfigOptionInt(extruder_id));
                            object->config.set_key_value("sparse_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                            object->config.set_key_value("solid_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                        }
                        target_volume->config.set_key_value("extruder", new Slic3r::ConfigOptionInt(extruder_id));
                        target_volume->config.set_key_value("wall_filament", new Slic3r::ConfigOptionInt(extruder_id));
                        target_volume->config.set_key_value("sparse_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                        target_volume->config.set_key_value("solid_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                    }
                }
            }
        }
    }
    return true;
}

static std::vector<double> json_number_array_exact(const std::string& json, const std::string& key, size_t expected_count, bool* ok = nullptr)
{
    if (ok != nullptr) {
        *ok = false;
    }
    std::vector<double> values;
    const auto raw = indexed_json_value(json, key);
    if (!raw) {
        return values;
    }
    const std::vector<std::string> items = json_array_items(*raw);
    if (items.size() != expected_count) {
        return {};
    }
    values.reserve(items.size());
    try {
        for (const std::string& item : items) {
            const double value = std::stod(trim_copy(item));
            if (!std::isfinite(value)) {
                return {};
            }
            values.push_back(value);
        }
    } catch (...) {
        return {};
    }
    if (ok != nullptr) {
        *ok = true;
    }
    return values;
}

static Slic3r::Transform3d transform_from_row_major_4x4(const std::vector<double>& values)
{
    Slic3r::Transform3d transform = Slic3r::Transform3d::Identity();
    if (values.size() != 16) {
        return transform;
    }
    for (int row = 0; row < 4; ++row)
        for (int col = 0; col < 4; ++col)
            transform(row, col) = values[size_t(row * 4 + col)];
    return transform;
}

static bool json_bool_member(const std::string& json, const std::string& key, bool fallback)
{
    if (const auto value = extract_bool(json, key)) {
        return *value;
    }
    if (const auto value = extract_number(json, key)) {
        return *value != 0.0;
    }
    return fallback;
}

static float json_float_member(const std::string& json, const std::string& key, float fallback)
{
    if (const auto value = extract_number(json, key)) {
        if (std::isfinite(*value)) {
            return float(*value);
        }
    }
    return fallback;
}

static Slic3r::CutConnectorType cut_connector_type_from_string(const std::string& value)
{
    const std::string normalized = lowercase_copy(value);
    if (normalized == "dowel") return Slic3r::CutConnectorType::Dowel;
    if (normalized == "snap") return Slic3r::CutConnectorType::Snap;
    return Slic3r::CutConnectorType::Plug;
}

static Slic3r::CutConnectorStyle cut_connector_style_from_string(const std::string& value)
{
    const std::string normalized = lowercase_copy(value);
    if (normalized == "frustum") return Slic3r::CutConnectorStyle::Frustum;
    return Slic3r::CutConnectorStyle::Prism;
}

static Slic3r::CutConnectorShape cut_connector_shape_from_string(const std::string& value)
{
    const std::string normalized = lowercase_copy(value);
    if (normalized == "triangle") return Slic3r::CutConnectorShape::Triangle;
    if (normalized == "square") return Slic3r::CutConnectorShape::Square;
    if (normalized == "hexagon" || normalized == "hex") return Slic3r::CutConnectorShape::Hexagon;
    return Slic3r::CutConnectorShape::Circle;
}

static indexed_triangle_set cut_connector_mesh(
    const Slic3r::CutConnectorAttributes& attributes,
    float snap_space_proportion = 0.3f,
    float snap_bulge_proportion = 0.15f)
{
    int sector_count = 180;
    switch (attributes.shape) {
    case Slic3r::CutConnectorShape::Triangle: sector_count = 3; break;
    case Slic3r::CutConnectorShape::Square: sector_count = 4; break;
    case Slic3r::CutConnectorShape::Hexagon: sector_count = 6; break;
    case Slic3r::CutConnectorShape::Circle: sector_count = 180; break;
    default: sector_count = 180; break;
    }
    if (attributes.type == Slic3r::CutConnectorType::Snap)
        return Slic3r::its_make_snap(1.0, 1.0, snap_space_proportion, snap_bulge_proportion);
    if (attributes.style == Slic3r::CutConnectorStyle::Prism)
        return Slic3r::its_make_cylinder(1.0, 1.0, 2.0 * PI / double(sector_count));
    if (attributes.type == Slic3r::CutConnectorType::Plug)
        return Slic3r::its_make_frustum(1.0, 1.0, 2.0 * PI / double(sector_count));
    return Slic3r::its_make_frustum_dowel(1.0, 1.0, sector_count);
}

static std::string filesystem_safe_slug(std::string value)
{
    if (value.empty()) {
        return "cut-result";
    }
    for (char& ch : value) {
        const unsigned char byte = static_cast<unsigned char>(ch);
        if (!std::isalnum(byte) && ch != '-' && ch != '_' && ch != '.') {
            ch = '-';
        }
    }
    while (!value.empty() && value.front() == '-') value.erase(value.begin());
    while (!value.empty() && value.back() == '-') value.pop_back();
    return value.empty() ? std::string("cut-result") : value;
}

static bool export_cut_result_stl(Slic3r::ModelObject& object, const std::string& output_path)
{
    if (output_path.empty()) {
        return true;
    }
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

static bool add_cut_connector_volume_from_json(
    Slic3r::ModelObject& object,
    const std::string& connector_json,
    const std::string& connector_name,
    const Slic3r::Vec3d& cut_normal,
    std::string* error,
    int& dowels_count)
{
    invalidate_json_scalar_index();
    const Slic3r::CutConnectorAttributes attributes(
        cut_connector_type_from_string(json_string_value(connector_json, "type")),
        cut_connector_style_from_string(json_string_value(connector_json, "style")),
        cut_connector_shape_from_string(json_string_value(connector_json, "shape")));

    if (attributes.type == Slic3r::CutConnectorType::Snap && attributes.shape != Slic3r::CutConnectorShape::Circle) {
        if (error != nullptr) *error = "snap connectors must use circle shape";
        return false;
    }
    if (attributes.type == Slic3r::CutConnectorType::Dowel && attributes.style == Slic3r::CutConnectorStyle::Frustum) {
        if (error != nullptr) *error = "dowel connectors do not support frustum style";
        return false;
    }

    bool position_ok = false;
    const std::vector<double> position = json_number_array_exact(connector_json, "position", 3, &position_ok);
    if (!position_ok) {
        if (error != nullptr) *error = "connector position must contain three finite numbers";
        return false;
    }

    Slic3r::Transform3d rotation_matrix = Slic3r::Transform3d::Identity();
    bool rotation_ok = false;
    const std::vector<double> rotation_values = json_number_array_exact(connector_json, "rotationMatrix", 16, &rotation_ok);
    if (rotation_ok) {
        rotation_matrix = transform_from_row_major_4x4(rotation_values);
    }

    Slic3r::CutConnector connector(
        Slic3r::Vec3d(position[0], position[1], position[2]),
        rotation_matrix,
        json_float_member(connector_json, "radius", 2.5f),
        json_float_member(connector_json, "height", 3.0f),
        json_float_member(connector_json, "radiusTolerance", 0.0f),
        json_float_member(connector_json, "heightTolerance", 0.1f),
        json_float_member(connector_json, "zAngle", 0.0f),
        attributes);
    if (connector.radius <= 0.f || connector.height <= 0.f || connector.radius_tolerance < 0.f || connector.height_tolerance < 0.f) {
        if (error != nullptr) *error = "connector radius/height/tolerance values are invalid";
        return false;
    }

    if (connector.attribs.type == Slic3r::CutConnectorType::Dowel) {
        if (connector.attribs.style == Slic3r::CutConnectorStyle::Prism)
            connector.height *= 2.0f;
        ++dowels_count;
    } else {
        connector.pos += cut_normal * (0.5 * double(connector.height));
    }

    Slic3r::TriangleMesh mesh(cut_connector_mesh(
        connector.attribs,
        json_float_member(connector_json, "snapSpaceProportion", 0.3f),
        json_float_member(connector_json, "snapBulgeProportion", 0.15f)));
    Slic3r::ModelVolume* volume = object.add_volume(std::move(mesh), Slic3r::ModelVolumeType::NEGATIVE_VOLUME);
    if (volume == nullptr) {
        if (error != nullptr) *error = "failed to create connector volume";
        return false;
    }
    volume->name = connector_name.empty() ? "Connector" : connector_name;
    volume->set_transformation(
        Slic3r::Geometry::translation_transform(connector.pos) *
        connector.rotation_m *
        Slic3r::Geometry::rotation_transform(-connector.z_angle * Slic3r::Vec3d::UnitZ()) *
        Slic3r::Geometry::scale_transform(Slic3r::Vec3f(connector.radius, connector.radius, connector.height).cast<double>()));
    volume->cut_info = { connector.attribs.type, connector.radius_tolerance, connector.height_tolerance };
    return true;
}

static void refresh_binding_volume_metadata(Slic3r::Model& model, OrcaEngineImpl::PaintObjectBinding& binding)
{
    binding.volume_triangle_counts.clear();
    binding.volume_fingerprints.clear();
    binding.volume_bounds.clear();
    for (const int object_index : binding.model_object_indices) {
        if (object_index < 0 || object_index >= int(model.objects.size())) {
            continue;
        }
        const Slic3r::ModelObject* object = model.objects[size_t(object_index)];
        if (object == nullptr) {
            continue;
        }
        for (const Slic3r::ModelVolume* volume : object->volumes) {
            if (volume == nullptr) {
                binding.volume_triangle_counts.push_back(0);
                binding.volume_fingerprints.emplace_back();
                binding.volume_bounds.emplace_back();
            } else {
                binding.volume_triangle_counts.push_back(volume->mesh().empty() ? 0 : int(volume->mesh().facets_count()));
                binding.volume_fingerprints.push_back(volume->mesh().empty() ? std::string{} : mobileslicer::orca_paint::mesh_fingerprint(volume->mesh()));
                binding.volume_bounds.push_back(volume->mesh().empty() ? PaintVolumeBounds{} : native_volume_bounds(volume->mesh()));
            }
        }
    }
}

static long long next_cut_mobile_object_id(const std::unordered_map<long long, OrcaEngineImpl::PaintObjectBinding>& bindings)
{
    long long max_id = 0;
    for (const auto& [mobile_object_id, _] : bindings) {
        max_id = std::max(max_id, mobile_object_id);
    }
    return max_id + 1;
}

static bool cut_result_object_is_dowel(const Slic3r::ModelObject* object)
{
    if (object == nullptr) {
        return false;
    }
    bool has_dowel_connector = false;
    bool has_model_part = false;
    for (const Slic3r::ModelVolume* volume : object->volumes) {
        if (volume == nullptr) {
            continue;
        }
        if (volume->is_model_part()) {
            has_model_part = true;
        }
        if (volume->cut_info.is_connector &&
            volume->cut_info.connector_type == Slic3r::CutConnectorType::Dowel) {
            has_dowel_connector = true;
        }
    }
    return has_model_part && has_dowel_connector;
}

static std::string role_for_cut_result(const Slic3r::ModelObject* object, size_t index, size_t object_count)
{
    if (cut_result_object_is_dowel(object)) {
        return "dowel";
    }
    if (object_count >= 2) {
        if (index == 0) return "upper";
        if (index == 1) return "lower";
    }
    return "part";
}

static void update_cut_id_for_orca_parity(
    Slic3r::CutObjectBase& cut_id,
    Slic3r::ModelObjectCutAttributes attributes,
    int dowels_count)
{
    if (!attributes.has(Slic3r::ModelObjectCutAttribute::KeepUpper) ||
        !attributes.has(Slic3r::ModelObjectCutAttribute::KeepLower) ||
        attributes.has(Slic3r::ModelObjectCutAttribute::InvalidateCutInfo)) {
        return;
    }

    if (cut_id.id().invalid()) {
        cut_id.init();
    }

    int cut_object_count = -1;
    if (attributes.has(Slic3r::ModelObjectCutAttribute::KeepUpper)) cut_object_count++;
    if (attributes.has(Slic3r::ModelObjectCutAttribute::KeepLower)) cut_object_count++;
    if (attributes.has(Slic3r::ModelObjectCutAttribute::CreateDowels)) cut_object_count += dowels_count;
    if (cut_object_count > 0) {
        cut_id.increase_check_sum(size_t(cut_object_count));
    }
}

static void append_cut_result_object_json(
    std::ostringstream& out,
    const Slic3r::Model& model,
    long long mobile_object_id,
    int model_object_index,
    const std::string& role,
    const std::string& file_path)
{
    const Slic3r::ModelObject* object = model.objects[size_t(model_object_index)];
    out << "{\"mobileObjectId\":" << mobile_object_id
        << ",\"modelObjectIndex\":" << model_object_index
        << ",\"label\":";
    append_json_string(out, object != nullptr ? object->name : std::string("Cut result"));
    out << ",\"role\":";
    append_json_string(out, role);
    if (!file_path.empty()) {
        out << ",\"filePath\":";
        append_json_string(out, file_path);
    }
    out
        << ",\"volumeCount\":" << (object != nullptr ? object->volumes.size() : 0);
    if (object != nullptr && object->is_cut()) {
        out << ",\"cutMetadata\":{"
            << "\"id\":" << object->cut_id.id().id
            << ",\"checkSum\":" << object->cut_id.check_sum()
            << ",\"connectorsCount\":" << object->cut_id.connectors_cnt()
            << "}";
    }
    if (object != nullptr) {
        Slic3r::BoundingBoxf3 bounds;
        for (const Slic3r::ModelVolume* volume : object->volumes) {
            if (volume != nullptr && volume->is_model_part() && !volume->mesh().empty()) {
                bounds.merge(volume->mesh().bounding_box());
            }
        }
        if (bounds.defined) {
            out << ",\"bounds\":{\"minX\":" << bounds.min.x()
                << ",\"minY\":" << bounds.min.y()
                << ",\"minZ\":" << bounds.min.z()
                << ",\"maxX\":" << bounds.max.x()
                << ",\"maxY\":" << bounds.max.y()
                << ",\"maxZ\":" << bounds.max.z()
                << "}";
        }
    }
    out << "}";
}

extern "C" int orca_cut_object(OrcaEngine* engine, const char* request_json)
{
    if (engine == nullptr || request_json == nullptr || request_json[0] == '\0') {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    engine->impl.last_cut_result_json.clear();
    if (!engine->impl.model.has_value()) {
        set_last_error(engine, "cut requires a loaded native model");
        return ORCA_ERROR_LOAD_MODEL;
    }

    const auto started = std::chrono::steady_clock::now();
    const std::string request(request_json);
    invalidate_json_scalar_index();
    const long long source_mobile_object_id = json_long_value(request, "mobileObjectId", 0);
    if (source_mobile_object_id <= 0) {
        set_last_error(engine, "cut request has invalid mobileObjectId");
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    auto binding_it = engine->impl.paint_object_bindings.find(source_mobile_object_id);
    if (binding_it == engine->impl.paint_object_bindings.end() || binding_it->second.model_object_indices.empty()) {
        set_last_error(engine, "cut request object id is not bound in native model");
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    if (binding_it->second.model_object_indices.size() != 1) {
        set_last_error(engine, "cut request currently requires a single bound native model object");
        return ORCA_ERROR_INVALID_ARGUMENT;
    }

    const int source_model_object_index = binding_it->second.model_object_indices.front();
    Slic3r::Model& model = *engine->impl.model;
    if (source_model_object_index < 0 || source_model_object_index >= int(model.objects.size()) || model.objects[size_t(source_model_object_index)] == nullptr) {
        set_last_error(engine, "cut request object binding is stale");
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    Slic3r::ModelObject* source_object = model.objects[size_t(source_model_object_index)];
    const int instance_index = json_int_value(request, "instanceIndex", 0);
    if (instance_index < 0 || instance_index >= int(source_object->instances.size())) {
        set_last_error(engine, "cut request instanceIndex is invalid");
        return ORCA_ERROR_INVALID_ARGUMENT;
    }

    bool cut_matrix_ok = false;
    const std::vector<double> cut_matrix_values = json_number_array_exact(request, "cutMatrix", 16, &cut_matrix_ok);
    if (!cut_matrix_ok) {
        set_last_error(engine, "cut request cutMatrix must contain sixteen finite numbers");
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    const Slic3r::Transform3d cut_matrix = transform_from_row_major_4x4(cut_matrix_values);
    Slic3r::Vec3d cut_normal = cut_matrix.linear() * Slic3r::Vec3d::UnitZ();
    const double cut_normal_norm = cut_normal.norm();
    if (cut_normal_norm <= std::numeric_limits<double>::epsilon() || !std::isfinite(cut_normal_norm)) {
        set_last_error(engine, "cut request cutMatrix has invalid normal");
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    cut_normal /= cut_normal_norm;

    const std::string mode = lowercase_copy(json_string_value(request, "mode"));

    std::string attributes_json;
    if (const auto raw_attributes = indexed_json_value(request, "attributes")) {
        attributes_json = std::string(*raw_attributes);
    }
    const std::vector<std::string> connector_items = json_array_member_items(request, "connectors");
    const bool has_connectors = !connector_items.empty();
    const std::string output_directory = json_string_value(request, "outputDirectory");

    bool keep_upper = json_bool_member(attributes_json, "keepUpper", true);
    bool keep_lower = json_bool_member(attributes_json, "keepLower", true);
    bool keep_as_parts = json_bool_member(attributes_json, "keepAsParts", false);
    const bool flip_upper = json_bool_member(attributes_json, "flipUpper", false);
    const bool flip_lower = json_bool_member(attributes_json, "flipLower", false);
    const bool place_on_cut_upper = json_bool_member(attributes_json, "placeOnCutUpper", false);
    const bool place_on_cut_lower = json_bool_member(attributes_json, "placeOnCutLower", false);
    if (has_connectors) {
        keep_upper = true;
        keep_lower = true;
        keep_as_parts = false;
    }
    if (!keep_upper && !keep_lower) {
        set_last_error(engine, "cut request must keep at least one part");
        return ORCA_ERROR_INVALID_ARGUMENT;
    }

    Slic3r::ModelObjectCutAttributes attributes;
    if (keep_upper) attributes = attributes | Slic3r::ModelObjectCutAttribute::KeepUpper;
    if (keep_lower) attributes = attributes | Slic3r::ModelObjectCutAttribute::KeepLower;
    if (keep_as_parts) attributes = attributes | Slic3r::ModelObjectCutAttribute::KeepAsParts;
    if (flip_upper) attributes = attributes | Slic3r::ModelObjectCutAttribute::FlipUpper;
    if (flip_lower) attributes = attributes | Slic3r::ModelObjectCutAttribute::FlipLower;
    if (place_on_cut_upper) attributes = attributes | Slic3r::ModelObjectCutAttribute::PlaceOnCutUpper;
    if (place_on_cut_lower) attributes = attributes | Slic3r::ModelObjectCutAttribute::PlaceOnCutLower;

    Slic3r::Model cut_input_model;
    Slic3r::ModelObject* cut_object = cut_input_model.add_object(*source_object);
    int dowels_count = 0;
    const size_t connector_id_start = cut_object->cut_id.connectors_cnt();
    size_t connector_id = connector_id_start;
    for (const std::string& connector_json : connector_items) {
        std::string connector_error;
        const std::string connector_name = "Connector-" + std::to_string(++connector_id);
        if (!add_cut_connector_volume_from_json(*cut_object, connector_json, connector_name, cut_normal, &connector_error, dowels_count)) {
            set_last_error(engine, connector_error.empty() ? "invalid cut connector" : connector_error);
            return ORCA_ERROR_INVALID_ARGUMENT;
        }
    }
    if (connector_id > connector_id_start) {
        cut_object->cut_id.increase_connectors_cnt(connector_id - connector_id_start);
    }
    if (dowels_count > 0) {
        attributes = attributes | Slic3r::ModelObjectCutAttribute::CreateDowels;
    }
    update_cut_id_for_orca_parity(cut_object->cut_id, attributes, dowels_count);

    try {
        Slic3r::Cut cut(cut_object, instance_index, cut_matrix, attributes);
        const Slic3r::ModelObjectPtrs* result_objects = nullptr;
        if (mode == "contour") {
            const std::vector<std::string> part_items = json_array_member_items(request, "parts");
            if (part_items.empty()) {
                set_last_error(engine, "contour cut requires a non-empty parts array");
                return ORCA_ERROR_INVALID_ARGUMENT;
            }
            std::vector<Slic3r::Cut::Part> parts;
            parts.reserve(part_items.size());
            for (const std::string& part_json : part_items) {
                parts.push_back(Slic3r::Cut::Part{
                    json_bool_member(part_json, "selected", false),
                    json_bool_member(part_json, "isModifier", false)
                });
            }
            result_objects = &cut.perform_by_contour(std::move(parts), dowels_count);
        } else if (mode == "groove" || mode == "tongueandgroove" || mode == "tongue_and_groove") {
            std::string groove_json;
            if (const auto raw_groove = indexed_json_value(request, "groove")) {
                groove_json = std::string(*raw_groove);
            }
            Slic3r::Cut::Groove groove;
            groove.depth = json_float_member(groove_json, "depth", 2.0f);
            groove.width = json_float_member(groove_json, "width", 4.0f);
            groove.flaps_angle = json_float_member(groove_json, "flapsAngleRadians", float(PI / 4.0));
            groove.angle = json_float_member(groove_json, "angleRadians", 0.0f);
            groove.depth_tolerance = json_float_member(groove_json, "depthTolerance", 0.1f);
            groove.width_tolerance = json_float_member(groove_json, "widthTolerance", 0.1f);
            if (groove.depth <= 0.f || groove.width <= 0.f || groove.flaps_angle <= 0.f || groove.depth_tolerance < 0.f || groove.width_tolerance < 0.f) {
                set_last_error(engine, "cut groove parameters are invalid");
                return ORCA_ERROR_INVALID_ARGUMENT;
            }
            result_objects = &cut.perform_with_groove(groove, Slic3r::Transform3d::Identity(), keep_as_parts);
        } else {
            result_objects = &cut.perform_with_plane();
        }

        if (result_objects == nullptr || result_objects->empty()) {
            set_last_error(engine, "cut produced no result objects");
            return ORCA_ERROR_SLICE;
        }

        model.delete_object(size_t(source_model_object_index));
        for (auto& [mobile_object_id, binding] : engine->impl.paint_object_bindings) {
            if (mobile_object_id == source_mobile_object_id) {
                continue;
            }
            for (int& index : binding.model_object_indices) {
                if (index > source_model_object_index) {
                    --index;
                }
            }
        }

        engine->impl.paint_object_bindings.erase(source_mobile_object_id);
        long long next_mobile_object_id = next_cut_mobile_object_id(engine->impl.paint_object_bindings);
        std::ostringstream result_json;
        result_json << "{\"schemaVersion\":1"
                    << ",\"sourceMobileObjectId\":" << source_mobile_object_id
                    << ",\"cutGroupId\":\"cut-" << source_mobile_object_id << "-" << (engine->impl.model_generation + 1) << "\""
                    << ",\"objects\":[";
        size_t appended_result_count = 0;
        for (size_t result_index = 0; result_index < result_objects->size(); ++result_index) {
            const Slic3r::ModelObject* result_object = (*result_objects)[result_index];
            if (result_object == nullptr || result_object->volumes.empty()) {
                continue;
            }
            Slic3r::ModelObject* inserted_object = model.add_object(*result_object);
            if (inserted_object == nullptr) {
                continue;
            }
            const int inserted_index = int(model.objects.size()) - 1;
            const long long mobile_object_id = next_mobile_object_id++;
            std::string output_file_path;
            if (!output_directory.empty()) {
                const std::string role = role_for_cut_result(result_object, result_index, result_objects->size());
                output_file_path = (std::filesystem::path(output_directory) /
                    (filesystem_safe_slug(std::string("cut-") + std::to_string(source_mobile_object_id) + "-" + role + "-" + std::to_string(mobile_object_id)) + ".stl")).string();
                if (!export_cut_result_stl(*inserted_object, output_file_path)) {
                    model.delete_object(size_t(inserted_index));
                    set_last_error(engine, "failed to export cut result STL");
                    return ORCA_ERROR_SLICE;
                }
            }
            OrcaEngineImpl::PaintObjectBinding binding;
            binding.mobile_object_id = mobile_object_id;
            binding.model_object_indices.push_back(inserted_index);
            refresh_binding_volume_metadata(model, binding);
            engine->impl.paint_object_bindings[mobile_object_id] = std::move(binding);
            if (appended_result_count > 0) {
                result_json << ",";
            }
            append_cut_result_object_json(
                result_json,
                model,
                mobile_object_id,
                inserted_index,
                role_for_cut_result(inserted_object, result_index, result_objects->size()),
                output_file_path);
            ++appended_result_count;
        }
        if (appended_result_count == 0) {
            set_last_error(engine, "cut produced no usable result objects");
            return ORCA_ERROR_SLICE;
        }
        for (auto& [mobile_object_id, binding] : engine->impl.paint_object_bindings) {
            refresh_binding_volume_metadata(model, binding);
        }
        result_json << "],\"durationMs\":" << elapsed_ms_since(started) << "}";
        engine->impl.last_cut_result_json = result_json.str();
        ++engine->impl.model_generation;
        invalidate_paint_session_unlocked(engine);
        clear_generated_gcode(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_cut_object", exception.what());
        return ORCA_ERROR_SLICE;
    } catch (...) {
        set_last_error(engine, "unknown cut exception");
        log_native_error("orca_cut_object", "unknown exception");
        return ORCA_ERROR_SLICE;
    }
}

extern "C" const char* orca_get_last_cut_result_json(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    return engine->impl.last_cut_result_json.empty() ? nullptr : engine->impl.last_cut_result_json.c_str();
}

extern "C" int orca_load_plate_models_v2(
    OrcaEngine* engine,
    const char* const* paths,
    const double* transforms,
    int transform_stride,
    const int* extruder_ids,
    const long long* mobile_object_ids,
    const char* paint_payload_json,
    int count)
{
    if (engine == nullptr || paths == nullptr || transforms == nullptr || extruder_ids == nullptr || mobile_object_ids == nullptr || count <= 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    clear_generated_gcode(engine);

    std::string paint_parse_error;
    const std::vector<PaintObjectReplay> paint_payload = parse_paint_replay_payload(paint_payload_json, &paint_parse_error);
    if (!paint_parse_error.empty()) {
        set_last_error(engine, paint_parse_error);
        return ORCA_ERROR_CONFIG;
    }

    try {
        Slic3r::Model combined_model;
        std::unordered_map<std::string, Slic3r::Model> model_cache;
        std::unordered_map<long long, OrcaEngineImpl::PaintObjectBinding> bindings;
        for (int index = 0; index < count; ++index) {
            const char* path = paths[index];
            if (path == nullptr || path[0] == '\0') {
                set_last_error(engine, "plate model path is empty");
                return ORCA_ERROR_INVALID_ARGUMENT;
            }
            const long long mobile_object_id = mobile_object_ids[index];
            if (mobile_object_id <= 0) {
                set_last_error(engine, "plate model mobileObjectId is invalid");
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

            const NativePlateTransform transform = read_native_plate_transform(transforms, index, transform_stride);
            const int extruder_id = std::max(1, extruder_ids[index]);

            OrcaEngineImpl::PaintObjectBinding& binding = bindings[mobile_object_id];
            binding.mobile_object_id = mobile_object_id;

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
                const int combined_object_index = int(combined_model.objects.size()) - 1;
                binding.model_object_indices.push_back(combined_object_index);
                combined_object->input_file = path;
                combined_object->config.set_key_value("extruder", new Slic3r::ConfigOptionInt(extruder_id));
                combined_object->config.set_key_value("wall_filament", new Slic3r::ConfigOptionInt(extruder_id));
                combined_object->config.set_key_value("sparse_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                combined_object->config.set_key_value("solid_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                int assigned_volume_count = 0;
                for (Slic3r::ModelVolume* volume : combined_object->volumes) {
                    if (volume == nullptr) {
                        binding.volume_triangle_counts.push_back(0);
                        binding.volume_fingerprints.emplace_back();
                        binding.volume_bounds.emplace_back();
                        continue;
                    }
                    volume->config.set_key_value("extruder", new Slic3r::ConfigOptionInt(extruder_id));
                    volume->config.set_key_value("wall_filament", new Slic3r::ConfigOptionInt(extruder_id));
                    volume->config.set_key_value("sparse_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                    volume->config.set_key_value("solid_infill_filament", new Slic3r::ConfigOptionInt(extruder_id));
                    binding.volume_triangle_counts.push_back(volume->mesh().empty() ? 0 : int(volume->mesh().facets_count()));
                    binding.volume_fingerprints.push_back(volume->mesh().empty() ? std::string{} : mobileslicer::orca_paint::mesh_fingerprint(volume->mesh()));
                    binding.volume_bounds.push_back(volume->mesh().empty() ? PaintVolumeBounds{} : native_volume_bounds(volume->mesh()));
                    ++assigned_volume_count;
                }
                {
                    std::ostringstream message;
                    message << "plate_model_v2 index=" << index
                            << " mobileObjectId=" << mobile_object_id
                            << " extruder=" << extruder_id
                            << " volumes=" << assigned_volume_count
                            << " path=" << path;
                    log_native_info("orca_load_plate_models_v2", message.str());
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

        if (combined_model.objects.empty()) {
            set_last_error(engine, "plate contains no objects");
            return ORCA_ERROR_LOAD_MODEL;
        }

        std::string replay_error;
        if (!paint_payload.empty() && !apply_paint_replay_payload(combined_model, bindings, paint_payload, &replay_error)) {
            set_last_error(engine, replay_error.empty() ? "paint annotation replay failed" : replay_error);
            return ORCA_ERROR_CONFIG;
        }

        engine->impl.model = std::move(combined_model);
        engine->impl.paint_object_bindings = std::move(bindings);
        ++engine->impl.model_generation;
        invalidate_paint_session_unlocked(engine);
        clear_generated_gcode(engine);
        return ORCA_SUCCESS;
    } catch (const std::exception& exception) {
        set_last_error(engine, exception.what());
        log_native_error("orca_load_plate_models_v2", exception.what());
        return ORCA_ERROR_LOAD_MODEL;
    } catch (...) {
        set_last_error(engine, "unknown exception");
        log_native_error("orca_load_plate_models_v2", "unknown exception");
        return ORCA_ERROR_LOAD_MODEL;
    }
}

static std::optional<mobileslicer::orca_paint::PaintMode> paint_mode_from_int(int mode)
{
    switch (mode) {
    case 0:
        return mobileslicer::orca_paint::PaintMode::Support;
    case 1:
        return mobileslicer::orca_paint::PaintMode::Seam;
    case 2:
        return mobileslicer::orca_paint::PaintMode::Color;
    case 3:
        return mobileslicer::orca_paint::PaintMode::FuzzySkin;
    default:
        return std::nullopt;
    }
}

static bool live_paint_mode_supported(mobileslicer::orca_paint::PaintMode mode)
{
    return mode == mobileslicer::orca_paint::PaintMode::Support ||
        mode == mobileslicer::orca_paint::PaintMode::Seam ||
        mode == mobileslicer::orca_paint::PaintMode::Color ||
        mode == mobileslicer::orca_paint::PaintMode::FuzzySkin;
}

static mobileslicer::orca_paint::Ray paint_ray_from_floats(const float* ray)
{
    return {
        Slic3r::Vec3f(ray[0], ray[1], ray[2]),
        Slic3r::Vec3f(ray[3], ray[4], ray[5]),
    };
}

static mobileslicer::orca_paint::Brush paint_brush_from_args(
    mobileslicer::orca_paint::PaintMode mode,
    int shape,
    int action,
    float radius_mm,
    int color_slot,
    float smart_fill_angle_deg,
    float overhang_angle_deg,
    const Slic3r::TriangleSelector::ClippingPlane& clipping)
{
    mobileslicer::orca_paint::Brush brush;
    switch (shape) {
    case 1:
        brush.shape = mobileslicer::orca_paint::BrushShape::Sphere;
        break;
    case 2:
        brush.shape = mobileslicer::orca_paint::BrushShape::BucketFill;
        break;
    case 3:
        brush.shape = mobileslicer::orca_paint::BrushShape::SmartFill;
        break;
    case 4:
        brush.shape = mobileslicer::orca_paint::BrushShape::Pointer;
        break;
    case 5:
        brush.shape = mobileslicer::orca_paint::BrushShape::HeightRange;
        break;
    case 6:
        brush.shape = mobileslicer::orca_paint::BrushShape::GapFill;
        break;
    default:
        brush.shape = mobileslicer::orca_paint::BrushShape::Circle;
        break;
    }
    brush.radius_mm = std::isfinite(radius_mm) && radius_mm > 0.f ? radius_mm : 2.f;
    brush.height_mm = brush.radius_mm;
    brush.smart_fill_angle_deg = std::isfinite(smart_fill_angle_deg) ? std::clamp(smart_fill_angle_deg, -1.f, 180.f) : 30.f;
    brush.highlight_by_angle_deg = std::isfinite(overhang_angle_deg) ? std::clamp(overhang_angle_deg, 0.f, 90.f) : 0.f;
    brush.clipping = clipping;
    if (action == 1) {
        brush.state = Slic3r::EnforcerBlockerType::NONE;
        return brush;
    }
    if (mode == mobileslicer::orca_paint::PaintMode::Color) {
        const int clamped_slot = std::clamp(color_slot, 1, int(Slic3r::EnforcerBlockerType::ExtruderMax));
        brush.state = static_cast<Slic3r::EnforcerBlockerType>(int(Slic3r::EnforcerBlockerType::Extruder1) + clamped_slot - 1);
        return brush;
    }
    if (mode == mobileslicer::orca_paint::PaintMode::FuzzySkin) {
        brush.state = Slic3r::EnforcerBlockerType::FUZZY_SKIN;
        return brush;
    }
    if (action == 3) {
        brush.state = Slic3r::EnforcerBlockerType::BLOCKER;
    } else {
        brush.state = Slic3r::EnforcerBlockerType::ENFORCER;
    }
    return brush;
}

static bool validate_paint_tool_args(OrcaEngine* engine, int shape, int action)
{
    if (shape < 0 || shape > 6) {
        set_last_error(engine, "paint brush shape is unsupported; native supports circle(0), sphere(1), fill(2), smart fill(3), triangle(4), height range(5), gap fill(6)");
        return false;
    }
    if (action < 0 || action > 3) {
        set_last_error(engine, "paint brush action is not implemented in native paint core");
        return false;
    }
    return true;
}

extern "C" int orca_paint_set_tool(OrcaEngine* engine, int shape, int action, float, float, int color_slot)
{
    if (engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    if (!validate_paint_tool_args(engine, shape, action)) {
        return ORCA_ERROR_CONFIG;
    }
    if (color_slot < 0 || color_slot > int(Slic3r::EnforcerBlockerType::ExtruderMax)) {
        set_last_error(engine, "paint color slot is outside Orca's supported extruder range");
        return ORCA_ERROR_CONFIG;
    }
    engine->impl.paint_tool_color_slot = std::max(1, color_slot);
    return ORCA_SUCCESS;
}

extern "C" int orca_paint_set_tool_options(OrcaEngine* engine, float smart_fill_angle_deg, float overhang_angle_deg, const float* clipping_plane)
{
    if (engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    if (!std::isfinite(smart_fill_angle_deg) || smart_fill_angle_deg < -1.f || smart_fill_angle_deg > 180.f) {
        set_last_error(engine, "paint smart-fill angle must be in [-1, 180] degrees");
        return ORCA_ERROR_CONFIG;
    }
    if (!std::isfinite(overhang_angle_deg) || overhang_angle_deg < 0.f || overhang_angle_deg > 90.f) {
        set_last_error(engine, "paint overhang/highlight angle must be in [0, 90] degrees");
        return ORCA_ERROR_CONFIG;
    }
    Slic3r::TriangleSelector::ClippingPlane clipping;
    if (clipping_plane != nullptr) {
        for (int i = 0; i < 4; ++i) {
            if (!std::isfinite(clipping_plane[i])) {
                set_last_error(engine, "paint clipping plane values must be finite");
                return ORCA_ERROR_CONFIG;
            }
        }
        clipping = Slic3r::TriangleSelector::ClippingPlane(
            std::array<float, 4>{clipping_plane[0], clipping_plane[1], clipping_plane[2], clipping_plane[3]});
    }
    engine->impl.paint_tool_smart_fill_angle_deg = smart_fill_angle_deg;
    engine->impl.paint_tool_overhang_angle_deg = overhang_angle_deg;
    engine->impl.paint_tool_clipping = clipping;
    return ORCA_SUCCESS;
}

static mobileslicer::orca_paint::OrcaPaintSession* active_paint_session_unlocked(OrcaEngine* engine)
{
    if (engine == nullptr || !engine->impl.paint_session) {
        return nullptr;
    }
    if (engine->impl.paint_session->model_generation() != engine->impl.model_generation) {
        invalidate_paint_session_unlocked(engine);
        return nullptr;
    }
    return engine->impl.paint_session.get();
}

static std::string paint_binding_debug_json_unlocked(const OrcaEngine* engine)
{
    std::ostringstream out;
    const bool has_model = engine != nullptr && engine->impl.model.has_value();
    out << "{\"schemaVersion\":1"
        << ",\"hasModel\":" << (has_model ? "true" : "false")
        << ",\"modelGeneration\":" << (engine != nullptr ? engine->impl.model_generation : 0)
        << ",\"modelObjectCount\":" << (has_model ? engine->impl.model->objects.size() : 0)
        << ",\"boundObjectCount\":" << (engine != nullptr ? engine->impl.paint_object_bindings.size() : 0)
        << ",\"boundObjects\":[";
    if (engine != nullptr) {
        bool first_binding = true;
        for (const auto& [mobile_object_id, binding] : engine->impl.paint_object_bindings) {
            if (!first_binding) {
                out << ',';
            }
            first_binding = false;
            out << "{\"mobileObjectId\":" << mobile_object_id
                << ",\"modelObjectIndices\":[";
            for (size_t index = 0; index < binding.model_object_indices.size(); ++index) {
                if (index > 0) {
                    out << ',';
                }
                out << binding.model_object_indices[index];
            }
            out << "],\"volumeTriangleCounts\":[";
            for (size_t index = 0; index < binding.volume_triangle_counts.size(); ++index) {
                if (index > 0) {
                    out << ',';
                }
                out << binding.volume_triangle_counts[index];
            }
            out << "],\"volumeFingerprints\":[";
            for (size_t index = 0; index < binding.volume_fingerprints.size(); ++index) {
                if (index > 0) {
                    out << ',';
                }
                append_json_string(out, binding.volume_fingerprints[index]);
            }
            out << "],\"volumeBounds\":[";
            for (size_t index = 0; index < binding.volume_bounds.size(); ++index) {
                if (index > 0) {
                    out << ',';
                }
                const PaintVolumeBounds& bounds = binding.volume_bounds[index];
                out << "{\"minX\":" << bounds.min_x
                    << ",\"minY\":" << bounds.min_y
                    << ",\"minZ\":" << bounds.min_z
                    << ",\"maxX\":" << bounds.max_x
                    << ",\"maxY\":" << bounds.max_y
                    << ",\"maxZ\":" << bounds.max_z
                    << '}';
            }
            out << "]}";
        }
    }
    out << "]}";
    return out.str();
}

static std::string paint_binding_error_message_unlocked(const OrcaEngine* engine, long long requested_mobile_object_id, const std::string& reason)
{
    std::ostringstream out;
    out << reason
        << "; requestedMobileObjectId=" << requested_mobile_object_id
        << "; bindingDebug=" << paint_binding_debug_json_unlocked(engine);
    return out.str();
}

extern "C" const char* orca_paint_binding_debug_json(OrcaEngine* engine)
{
    static thread_local std::string binding_debug_snapshot;
    if (engine == nullptr) {
        binding_debug_snapshot = paint_binding_debug_json_unlocked(nullptr);
        return binding_debug_snapshot.c_str();
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    engine->impl.paint_binding_debug_snapshot = paint_binding_debug_json_unlocked(engine);
    return engine->impl.paint_binding_debug_snapshot.c_str();
}

extern "C" const char* orca_paint_object_bounds_json(OrcaEngine* engine, long long mobile_object_id)
{
    if (engine == nullptr || mobile_object_id <= 0) {
        return nullptr;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    const auto binding_it = engine->impl.paint_object_bindings.find(mobile_object_id);
    if (binding_it == engine->impl.paint_object_bindings.end()) {
        return nullptr;
    }
    std::ostringstream out;
    out << "{\"schemaVersion\":1"
        << ",\"mobileObjectId\":" << mobile_object_id
        << ",\"volumeBounds\":[";
    const auto& volume_bounds = binding_it->second.volume_bounds;
    for (size_t index = 0; index < volume_bounds.size(); ++index) {
        if (index > 0) {
            out << ',';
        }
        const PaintVolumeBounds& bounds = volume_bounds[index];
        out << "{\"minX\":" << bounds.min_x
            << ",\"minY\":" << bounds.min_y
            << ",\"minZ\":" << bounds.min_z
            << ",\"maxX\":" << bounds.max_x
            << ",\"maxY\":" << bounds.max_y
            << ",\"maxZ\":" << bounds.max_z
            << '}';
    }
    out << "]}";
    engine->impl.paint_object_bounds_snapshot = out.str();
    return engine->impl.paint_object_bounds_snapshot.c_str();
}

extern "C" int orca_paint_begin_session(OrcaEngine* engine, long long mobile_object_id, int mode)
{
    if (engine == nullptr || mobile_object_id <= 0) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    const std::optional<mobileslicer::orca_paint::PaintMode> paint_mode = paint_mode_from_int(mode);
    if (!paint_mode) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }

    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    if (!live_paint_mode_supported(*paint_mode)) {
        set_last_error(engine, "native live painting mode is not supported");
        return ORCA_ERROR_CONFIG;
    }
    if (!engine->impl.model.has_value()) {
        set_last_error(engine, paint_binding_error_message_unlocked(engine, mobile_object_id, "paint session requires a loaded native model"));
        return ORCA_ERROR_LOAD_MODEL;
    }
    auto binding_it = engine->impl.paint_object_bindings.find(mobile_object_id);
    if (binding_it == engine->impl.paint_object_bindings.end()) {
        set_last_error(engine, paint_binding_error_message_unlocked(engine, mobile_object_id, "paint session object id is not bound in native model"));
        return ORCA_ERROR_LOAD_MODEL;
    }
    if (binding_it->second.model_object_indices.empty()) {
        set_last_error(engine, paint_binding_error_message_unlocked(engine, mobile_object_id, "paint session object binding has no model objects"));
        return ORCA_ERROR_LOAD_MODEL;
    }
    std::vector<Slic3r::ModelObject*> bound_objects;
    bound_objects.reserve(binding_it->second.model_object_indices.size());
    for (const int model_object_index : binding_it->second.model_object_indices) {
        if (model_object_index < 0 || model_object_index >= int(engine->impl.model->objects.size())) {
            set_last_error(engine, paint_binding_error_message_unlocked(engine, mobile_object_id, "paint session object binding is stale"));
            return ORCA_ERROR_LOAD_MODEL;
        }
        Slic3r::ModelObject* object = engine->impl.model->objects[model_object_index];
        if (object == nullptr) {
            set_last_error(engine, paint_binding_error_message_unlocked(engine, mobile_object_id, "paint session bound object is null"));
            return ORCA_ERROR_LOAD_MODEL;
        }
        bound_objects.push_back(object);
    }

    if (mobileslicer::orca_paint::OrcaPaintSession* existing = active_paint_session_unlocked(engine)) {
        existing->commit_to_model();
        engine->impl.paint_serialized_payload = existing->serialize_payload_json();
        clear_generated_gcode(engine);
    }
    engine->impl.paint_session = std::make_unique<mobileslicer::orca_paint::OrcaPaintSession>(
        bound_objects,
        static_cast<std::uint64_t>(mobile_object_id),
        *paint_mode,
        engine->impl.model_generation);
    engine->impl.paint_serialized_payload.clear();
    engine->impl.paint_overlay_snapshot.clear();
    return ORCA_SUCCESS;
}

extern "C" int orca_paint_end_session(OrcaEngine* engine, int commit)
{
    if (engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
    if (session == nullptr) {
        set_last_error(engine, "no active paint session");
        return ORCA_ERROR_LOAD_MODEL;
    }
    if (commit != 0) {
        session->commit_to_model();
        engine->impl.paint_serialized_payload = session->serialize_payload_json();
        clear_generated_gcode(engine);
    }
    engine->impl.paint_overlay_snapshot.clear();
    engine->impl.paint_session.reset();
    return ORCA_SUCCESS;
}

extern "C" int orca_paint_hit_test(OrcaEngine* engine, const float* ray, float* out_hit)
{
    if (engine == nullptr || ray == nullptr || out_hit == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
    if (session == nullptr) {
        set_last_error(engine, "no active paint session");
        return ORCA_ERROR_LOAD_MODEL;
    }
    const std::optional<mobileslicer::orca_paint::PaintHit> hit = session->hit_test(paint_ray_from_floats(ray));
    if (!hit) {
        return ORCA_ERROR_LOAD_MODEL;
    }
    out_hit[0] = float(hit->volume_index);
    out_hit[1] = float(hit->facet_index);
    out_hit[2] = hit->distance;
    out_hit[3] = hit->point.x();
    out_hit[4] = hit->point.y();
    out_hit[5] = hit->point.z();
    out_hit[6] = hit->normal.x();
    out_hit[7] = hit->normal.y();
    out_hit[8] = hit->normal.z();
    return ORCA_SUCCESS;
}

extern "C" int orca_paint_hit_test_ray(OrcaEngine* engine, const float* ray, float* out_hit)
{
    return orca_paint_hit_test(engine, ray, out_hit);
}

extern "C" int orca_paint_stroke_begin(OrcaEngine* engine, const float* ray, int shape, int action, float radius_mm)
{
    if (engine == nullptr || ray == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
    if (session == nullptr) {
        set_last_error(engine, "no active paint session");
        return ORCA_ERROR_LOAD_MODEL;
    }
    if (!validate_paint_tool_args(engine, shape, action)) {
        return ORCA_ERROR_CONFIG;
    }
    if (!session->stroke_begin(
            paint_ray_from_floats(ray),
            paint_brush_from_args(
                session->mode(),
                shape,
                action,
                radius_mm,
                engine->impl.paint_tool_color_slot,
                engine->impl.paint_tool_smart_fill_angle_deg,
                engine->impl.paint_tool_overhang_angle_deg,
                engine->impl.paint_tool_clipping))) {
        return ORCA_ERROR_LOAD_MODEL;
    }
    return ORCA_SUCCESS;
}

extern "C" int orca_paint_stroke_begin_ray(OrcaEngine* engine, const float* ray, int shape, int action, float radius_mm)
{
    return orca_paint_stroke_begin(engine, ray, shape, action, radius_mm);
}

extern "C" int orca_paint_stroke_move(OrcaEngine* engine, const float* ray, int shape, int action, float radius_mm)
{
    if (engine == nullptr || ray == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
    if (session == nullptr) {
        set_last_error(engine, "no active paint session");
        return ORCA_ERROR_LOAD_MODEL;
    }
    if (!validate_paint_tool_args(engine, shape, action)) {
        return ORCA_ERROR_CONFIG;
    }
    if (!session->stroke_move(
            paint_ray_from_floats(ray),
            paint_brush_from_args(
                session->mode(),
                shape,
                action,
                radius_mm,
                engine->impl.paint_tool_color_slot,
                engine->impl.paint_tool_smart_fill_angle_deg,
                engine->impl.paint_tool_overhang_angle_deg,
                engine->impl.paint_tool_clipping))) {
        return ORCA_ERROR_LOAD_MODEL;
    }
    return ORCA_SUCCESS;
}

extern "C" int orca_paint_stroke_move_ray(OrcaEngine* engine, const float* ray, int shape, int action, float radius_mm)
{
    return orca_paint_stroke_move(engine, ray, shape, action, radius_mm);
}

extern "C" int orca_paint_stroke_end(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
    if (session == nullptr) {
        set_last_error(engine, "no active paint session");
        return ORCA_ERROR_LOAD_MODEL;
    }
    session->stroke_end();
    engine->impl.paint_overlay_snapshot.clear();
    return ORCA_SUCCESS;
}

extern "C" int orca_paint_undo(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
    if (session == nullptr || !session->undo()) {
        return ORCA_ERROR_LOAD_MODEL;
    }
    session->commit_to_model();
    engine->impl.paint_serialized_payload = session->serialize_payload_json();
    engine->impl.paint_overlay_snapshot.clear();
    clear_generated_gcode(engine);
    return ORCA_SUCCESS;
}

extern "C" int orca_paint_redo(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
    if (session == nullptr || !session->redo()) {
        return ORCA_ERROR_LOAD_MODEL;
    }
    session->commit_to_model();
    engine->impl.paint_serialized_payload = session->serialize_payload_json();
    engine->impl.paint_overlay_snapshot.clear();
    clear_generated_gcode(engine);
    return ORCA_SUCCESS;
}

extern "C" int orca_paint_clear(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return ORCA_ERROR_INVALID_ARGUMENT;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
    if (session == nullptr) {
        set_last_error(engine, "no active paint session");
        return ORCA_ERROR_LOAD_MODEL;
    }
    session->clear();
    session->commit_to_model();
    engine->impl.paint_serialized_payload = session->serialize_payload_json();
    engine->impl.paint_overlay_snapshot.clear();
    clear_generated_gcode(engine);
    return ORCA_SUCCESS;
}

extern "C" int orca_paint_can_undo(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return 0;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
    return session != nullptr && session->can_undo() ? 1 : 0;
}

extern "C" int orca_paint_can_redo(OrcaEngine* engine)
{
    if (engine == nullptr) {
        return 0;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
    return session != nullptr && session->can_redo() ? 1 : 0;
}

extern "C" const char* orca_paint_serialize(OrcaEngine* engine)
{
    static thread_local std::string empty;
    if (engine == nullptr) {
        empty.clear();
        return empty.c_str();
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
    if (session != nullptr) {
        engine->impl.paint_serialized_payload = session->serialize_payload_json();
    }
    return engine->impl.paint_serialized_payload.c_str();
}

extern "C" const char* orca_paint_get_overlay(OrcaEngine* engine)
{
    static thread_local std::string empty;
    if (engine == nullptr) {
        empty.clear();
        return empty.c_str();
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
    if (session != nullptr) {
        if (session->total_source_facets() > kPaintOverlaySnapshotSourceFacetLimit) {
            engine->impl.paint_overlay_snapshot.clear();
            return engine->impl.paint_overlay_snapshot.c_str();
        }
        engine->impl.paint_overlay_snapshot = session->overlay_snapshot_json();
    }
    return engine->impl.paint_overlay_snapshot.c_str();
}

extern "C" int orca_paint_get_overlay_interleaved(OrcaEngine* engine, float* out_values, int max_values)
{
    if (engine == nullptr || max_values < 0) {
        return 0;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (out_values == nullptr || engine->impl.paint_overlay_snapshot_interleaved.empty()) {
        mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
        if (session == nullptr) {
            engine->impl.paint_overlay_snapshot_interleaved.clear();
            return 0;
        }
        engine->impl.paint_overlay_snapshot_interleaved = session->overlay_snapshot_interleaved();
    }
    const int required = int(engine->impl.paint_overlay_snapshot_interleaved.size());
    if (out_values != nullptr && max_values >= required && required > 0) {
        std::copy(
            engine->impl.paint_overlay_snapshot_interleaved.begin(),
            engine->impl.paint_overlay_snapshot_interleaved.end(),
            out_values);
        engine->impl.paint_overlay_snapshot_interleaved.clear();
    }
    return required;
}

extern "C" const char* orca_paint_get_overlay_delta(OrcaEngine* engine)
{
    static thread_local std::string empty;
    if (engine == nullptr) {
        empty.clear();
        return empty.c_str();
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
    if (session != nullptr) {
        engine->impl.paint_overlay_snapshot = session->overlay_delta_json();
    }
    return engine->impl.paint_overlay_snapshot.c_str();
}

extern "C" int orca_paint_get_overlay_delta_interleaved(OrcaEngine* engine, float* out_values, int max_values)
{
    constexpr int kMaxPaintOverlayDeltaInterleavedFloats = 4200000;
    if (engine == nullptr || max_values < 0) {
        return 0;
    }
    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    if (out_values == nullptr || engine->impl.paint_overlay_delta_interleaved.empty()) {
        mobileslicer::orca_paint::OrcaPaintSession* session = active_paint_session_unlocked(engine);
        if (session == nullptr) {
            engine->impl.paint_overlay_delta_interleaved.clear();
            return 0;
        }
        engine->impl.paint_overlay_delta_interleaved = session->overlay_delta_interleaved();
    }
    const int required = int(engine->impl.paint_overlay_delta_interleaved.size());
    if (required > kMaxPaintOverlayDeltaInterleavedFloats) {
        return required;
    }
    if (out_values != nullptr && max_values >= required && required > 0) {
        std::copy(
            engine->impl.paint_overlay_delta_interleaved.begin(),
            engine->impl.paint_overlay_delta_interleaved.end(),
            out_values);
        engine->impl.paint_overlay_delta_interleaved.clear();
    }
    return required;
}

extern "C" const char* orca_paint_remap_color_slots(OrcaEngine* engine, const char* payload_json, const int* old_slot_to_new_slot, int slot_count)
{
    static thread_local std::string empty;
    if (engine == nullptr || payload_json == nullptr || old_slot_to_new_slot == nullptr || slot_count <= 0) {
        empty.clear();
        return empty.c_str();
    }

    std::lock_guard<std::recursive_mutex> lock(engine->impl.mutex);
    clear_last_error(engine);
    engine->impl.paint_remapped_payload.clear();

    std::string parse_error;
    std::vector<PaintObjectReplay> payload = parse_paint_replay_payload(payload_json, &parse_error);
    if (!parse_error.empty()) {
        set_last_error(engine, parse_error);
        return engine->impl.paint_remapped_payload.c_str();
    }

    std::vector<int> remap(size_t(slot_count) + 1, 0);
    for (int old_slot = 1; old_slot <= slot_count; ++old_slot)
        remap[size_t(old_slot)] = old_slot_to_new_slot[old_slot - 1];

    for (PaintObjectReplay& object : payload) {
        for (PaintLayerReplay& layer : object.layers) {
            if (layer.mode != "Color")
                continue;

            std::set<int> remapped_slots;
            for (const int old_slot : layer.color_slots) {
                if (old_slot <= 0 || old_slot >= int(remap.size())) {
                    set_last_error(engine, "color paint references a filament slot without a native remap entry");
                    return engine->impl.paint_remapped_payload.c_str();
                }
                const int new_slot = remap[size_t(old_slot)];
                if (new_slot <= 0) {
                    set_last_error(engine, "color paint references a deleted filament slot");
                    return engine->impl.paint_remapped_payload.c_str();
                }
                if (new_slot > int(Slic3r::EnforcerBlockerType::ExtruderMax)) {
                    set_last_error(engine, "color paint remap target exceeds Orca's supported extruder range");
                    return engine->impl.paint_remapped_payload.c_str();
                }
                remapped_slots.insert(new_slot);
            }

            for (PaintVolumeReplay& volume : layer.volumes) {
                for (PaintTriangleReplay& triangle : volume.triangles) {
                    std::string remapped_hex;
                    std::string remap_error;
                    if (!mobileslicer::orca_paint::remap_color_hex_bits(triangle.hex_bits, remap, remapped_hex, &remap_error)) {
                        set_last_error(engine, remap_error.empty() ? "color paint triangle remap failed" : remap_error);
                        return engine->impl.paint_remapped_payload.c_str();
                    }
                    triangle.hex_bits = std::move(remapped_hex);
                }
            }

            if (!layer.color_slots.empty())
                layer.color_slots.assign(remapped_slots.begin(), remapped_slots.end());
        }
    }

    engine->impl.paint_remapped_payload = paint_replay_payload_to_json(payload);
    return engine->impl.paint_remapped_payload.c_str();
}

// Non-shipping Orca thumbnail renderer extraction probe.
//
// This file intentionally has no dependency on GLCanvas3D, wxWidgets,
// GUI_App, MainFrame, Plater, GLVolumeCollection, or Android EGL. It records
// the smallest role/camera contract that can be extracted from Orca's desktop
// thumbnail path and shared with MobileSlicer's Android renderer boundary.

#include <array>
#include <iostream>
#include <string_view>

namespace mobileslicer::orca_thumbnail_probe {

enum class Role {
    Gcode,
    Plate,
    NoLight,
    Top,
    Pick,
};

enum class CameraMode {
    AngledIso,
    TopPlate,
};

struct RoleContract {
    Role role;
    CameraMode camera_mode;
    bool picking;
    bool ban_light;
    float pitch_degrees;
    float yaw_degrees;
    float camera_distance_factor;
    float zoom_to_box_margin_factor;
    float broad_footprint_zoom_to_box_margin_factor;
    float box_horizontal_margin_factor;
    float box_vertical_margin_factor;
    float top_plate_margin;
};

constexpr float kAngledPitchDegrees = 45.0f;
constexpr float kAngledYawDegrees = -45.0f;
constexpr float kAngledCameraDistanceFactor = 4.0f;
constexpr float kAngledZoomToBoxMarginFactor = 1.025f;
constexpr float kAngledBroadFootprintZoomToBoxMarginFactor = 1.38f;
constexpr float kAngledBoxHorizontalMarginFactor = 0.01f;
constexpr float kAngledBoxVerticalMarginFactor = 0.02f;
constexpr float kTopPlateMargin = 1.02f;
constexpr int kSmallThumbnailSupersampleFactor = 2;
constexpr int kPackageSupersampleMaxOutputDimension = 128;
constexpr int kGcodeSupersampleMaxOutputDimension = 300;
constexpr int kSupersampleMaxRenderDimension = 600;

constexpr std::array<RoleContract, 5> kRoleContracts = {{
    {
        Role::Gcode,
        CameraMode::AngledIso,
        false,
        false,
        kAngledPitchDegrees,
        kAngledYawDegrees,
        kAngledCameraDistanceFactor,
        kAngledZoomToBoxMarginFactor,
        kAngledBroadFootprintZoomToBoxMarginFactor,
        kAngledBoxHorizontalMarginFactor,
        kAngledBoxVerticalMarginFactor,
        0.0f,
    },
    {
        Role::Plate,
        CameraMode::AngledIso,
        false,
        false,
        kAngledPitchDegrees,
        kAngledYawDegrees,
        kAngledCameraDistanceFactor,
        kAngledZoomToBoxMarginFactor,
        kAngledBroadFootprintZoomToBoxMarginFactor,
        kAngledBoxHorizontalMarginFactor,
        kAngledBoxVerticalMarginFactor,
        0.0f,
    },
    {
        Role::NoLight,
        CameraMode::AngledIso,
        false,
        true,
        kAngledPitchDegrees,
        kAngledYawDegrees,
        kAngledCameraDistanceFactor,
        kAngledZoomToBoxMarginFactor,
        kAngledBroadFootprintZoomToBoxMarginFactor,
        kAngledBoxHorizontalMarginFactor,
        kAngledBoxVerticalMarginFactor,
        0.0f,
    },
    {
        Role::Top,
        CameraMode::TopPlate,
        false,
        false,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        kTopPlateMargin,
    },
    {
        Role::Pick,
        CameraMode::TopPlate,
        true,
        true,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        kTopPlateMargin,
    },
}};

constexpr std::string_view role_name(Role role)
{
    switch (role) {
    case Role::Gcode: return "gcode";
    case Role::Plate: return "plate";
    case Role::NoLight: return "no_light";
    case Role::Top: return "top";
    case Role::Pick: return "pick";
    }
    return "unknown";
}

constexpr std::string_view camera_mode_name(CameraMode mode)
{
    switch (mode) {
    case CameraMode::AngledIso: return "angled_iso";
    case CameraMode::TopPlate: return "top_plate";
    }
    return "unknown";
}

} // namespace mobileslicer::orca_thumbnail_probe

#ifdef MOBILE_SLICER_ORCA_THUMBNAIL_CONTRACT_PROBE_MAIN
int main()
{
    using namespace mobileslicer::orca_thumbnail_probe;
    std::cout << "{\n";
    std::cout << "  \"schema_version\": 1,\n";
    std::cout << "  \"source\": \"vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp\",\n";
    std::cout << "  \"roles\": [\n";
    for (std::size_t index = 0; index < kRoleContracts.size(); ++index) {
        const RoleContract &contract = kRoleContracts[index];
        std::cout << "    {\n";
        std::cout << "      \"role\": \"" << role_name(contract.role) << "\",\n";
        std::cout << "      \"camera_mode\": \"" << camera_mode_name(contract.camera_mode) << "\",\n";
        std::cout << "      \"picking\": " << (contract.picking ? "true" : "false") << ",\n";
        std::cout << "      \"ban_light\": " << (contract.ban_light ? "true" : "false") << ",\n";
        std::cout << "      \"pitch_degrees\": " << contract.pitch_degrees << ",\n";
        std::cout << "      \"yaw_degrees\": " << contract.yaw_degrees << ",\n";
        std::cout << "      \"camera_distance_factor\": " << contract.camera_distance_factor << ",\n";
        std::cout << "      \"zoom_to_box_margin_factor\": " << contract.zoom_to_box_margin_factor << ",\n";
        std::cout << "      \"broad_footprint_zoom_to_box_margin_factor\": " << contract.broad_footprint_zoom_to_box_margin_factor << ",\n";
        std::cout << "      \"box_horizontal_margin_factor\": " << contract.box_horizontal_margin_factor << ",\n";
        std::cout << "      \"box_vertical_margin_factor\": " << contract.box_vertical_margin_factor << ",\n";
        std::cout << "      \"top_plate_margin\": " << contract.top_plate_margin << "\n";
        std::cout << "    }" << (index + 1 == kRoleContracts.size() ? "\n" : ",\n");
    }
    std::cout << "  ],\n";
    std::cout << "  \"supersampling\": {\n";
    std::cout << "    \"small_thumbnail_supersample_factor\": " << kSmallThumbnailSupersampleFactor << ",\n";
    std::cout << "    \"package_supersample_max_output_dimension\": " << kPackageSupersampleMaxOutputDimension << ",\n";
    std::cout << "    \"gcode_supersample_max_output_dimension\": " << kGcodeSupersampleMaxOutputDimension << ",\n";
    std::cout << "    \"supersample_max_render_dimension\": " << kSupersampleMaxRenderDimension << "\n";
    std::cout << "  }\n";
    std::cout << "}\n";
    return 0;
}
#endif

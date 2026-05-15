#ifndef ORCA_WRAPPER_MODULE_COMMON_H
#define ORCA_WRAPPER_MODULE_COMMON_H

// Private implementation header. Include through orca_wrapper_module_context.h.
#include "orca_wrapper_internal.h"
#include "orca_wrapper.h"
#include "orca_wrapper_calibration.h"
#include "orca_wrapper_gcode.h"
#include "orca_wrapper_model_overrides.h"
#include "orca_wrapper_printable_validation.h"
#include "orca_wrapper_utils.h"
#include "orca_native_paint.h"

#include "libslic3r/Config.hpp"
#include "libslic3r/Exception.hpp"
#include "libslic3r/Format/STEP.hpp"
#include "libslic3r/Format/STL.hpp"
#include "libslic3r/Format/bbs_3mf.hpp"
#include "libslic3r/GCode/Thumbnails.hpp"
#include "libslic3r/Geometry/ConvexHull.hpp"
#include "libslic3r/Layer.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/Arrange.hpp"
#include "libslic3r/CutUtils.hpp"
#include "libslic3r/Orient.hpp"
#include "libslic3r/calib.hpp"
#include "nlohmann/json.hpp"
#include <tbb/global_control.h>

#if defined(MOBILE_SLICER_ENABLE_VGCODE)
#include "libvgcode/include/GCodeInputData.hpp"
#include "libvgcode/include/Types.hpp"
#include "libvgcode/include/Viewer.hpp"
#include <GLES3/gl3.h>
#endif

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cctype>
#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <functional>
#include <iomanip>
#include <limits>
#include <cmath>
#include <cstring>
#include <memory>
#include <mutex>
#include <optional>
#include <regex>
#include <set>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <unistd.h>
#include <vector>

#ifdef __ANDROID__
#include <android/log.h>
#endif

namespace {

static constexpr bool kVerboseNativeTimingLogs = false;
static constexpr int kPaintOverlaySnapshotSourceFacetLimit = 12000;

using namespace mobileslicer::orca_wrapper;

static size_t current_process_rss_kb()
{
#ifdef __ANDROID__
    FILE* status = std::fopen("/proc/self/status", "r");
    if (status == nullptr) {
        return 0;
    }

    char line[256];
    size_t rss_kb = 0;
    while (std::fgets(line, sizeof(line), status) != nullptr) {
        if (std::sscanf(line, "VmRSS: %zu kB", &rss_kb) == 1) {
            break;
        }
    }
    std::fclose(status);
    return rss_kb;
#else
    return 0;
#endif
}


#endif // ORCA_WRAPPER_MODULE_COMMON_H

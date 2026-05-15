// Stub implementations for symbols referenced by compiled libslic3r units
// but not exercised in the config implementation subset.
//
// Each stub avoids pulling in a large subsystem:
//   MedialAxis              -> Voronoi.cpp -> Arachne (wall generation)
//   GCodeThumbnails          -> Thumbnails.cpp -> jpeglib, qoi (image libs)
//   utils functions          -> utils.cpp -> boost::log, locale, filesystem

#include "libslic3r/clipper.hpp"
#include "libslic3r/ExPolygon.hpp"
#include "libslic3r/GCode/Thumbnails.hpp"
#include "libslic3r/Geometry/MedialAxis.hpp"
#include "libslic3r/Point.hpp"
#include "libslic3r/Polyline.hpp"
#include "libslic3r/ShortestPath.hpp"
#include "libslic3r/Utils.hpp"

#include <algorithm>
#include <boost/algorithm/string/case_conv.hpp>
#include <boost/algorithm/string/trim.hpp>
#include <sstream>
#include <string>
#include <string_view>

namespace Slic3r {

namespace Geometry {

#ifndef ORCA_ANDROID_REAL_MEDIAL_AXIS
// --- MedialAxis stubs ---

// Static ExPolygon to satisfy the reference member.
static ExPolygon s_stub_expolygon;

MedialAxis::MedialAxis(double /* min_width */, double /* max_width */,
                        const ExPolygon & /* expolygon */)
    : m_expolygon(s_stub_expolygon), m_min_width(0), m_max_width(0)
{
}

void MedialAxis::build(ThickPolylines * /* polylines */) {}
void MedialAxis::build(Polylines * /* polylines */) {}
#endif

} // namespace Geometry

// --- utils.cpp stubs ---

std::string header_slic3r_generated()
{
    // Moonraker forks used by printer vendors often gate thumbnail extraction
    // on known slicer aliases. Keep MobileSlicer visible while presenting an
    // OrcaSlicer-compatible generator marker so Fluidd/QIDI metadata parsers
    // index embedded PNG thumbnails instead of falling back to a generic file.
    return "OrcaSlicer 2.3.0-compatible MobileSlicer (android-experimental)";
}

bool is_gcode_file(const std::string &path)
{
    if (path.size() < 6) return false;
    std::string ext = path.substr(path.size() - 6);
    std::transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
    return ext == ".gcode";
}

bool is_json_file(const std::string &path)
{
    if (path.size() < 5) return false;
    std::string ext = path.substr(path.size() - 5);
    std::transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
    return ext == ".json";
}

// --- GCodeThumbnails stubs ---

namespace GCodeThumbnails {

static bool parse_thumbnail_format(const std::string& raw, GCodeThumbnailsFormat& format)
{
    std::string upper = raw;
    boost::trim(upper);
    boost::to_upper(upper);
    if (upper.empty() || upper == "PNG") {
        format = GCodeThumbnailsFormat::PNG;
        return true;
    }
    if (upper == "JPG" || upper == "JPEG") {
        format = GCodeThumbnailsFormat::JPG;
        return true;
    }
    if (upper == "BTT" || upper == "BTT_TFT" || upper == "BIQU") {
        format = GCodeThumbnailsFormat::BTT_TFT;
        return true;
    }
    if (upper == "QOI") {
        format = GCodeThumbnailsFormat::QOI;
        return true;
    }
    if (upper == "COLPIC") {
        format = GCodeThumbnailsFormat::ColPic;
        return true;
    }
    return false;
}

static std::string normalize_thumbnail_item(std::string item)
{
    boost::trim(item);
    item.erase(std::remove(item.begin(), item.end(), '['), item.end());
    item.erase(std::remove(item.begin(), item.end(), ']'), item.end());
    item.erase(std::remove(item.begin(), item.end(), '"'), item.end());
    boost::trim(item);
    return item;
}

std::string get_error_string(const ThumbnailErrors &errors)
{
    std::string error;
    if (errors.has(ThumbnailError::InvalidVal))
        error += "\n - Invalid input format. Expected XxY/EXT, XxY/EXT, ...";
    if (errors.has(ThumbnailError::OutOfRange))
        error += "\n - Input value is out of range";
    if (errors.has(ThumbnailError::InvalidExt))
        error += "\n - Some extension in the input is invalid";
    return error;
}

std::pair<GCodeThumbnailDefinitionsList, ThumbnailErrors>
make_and_check_thumbnail_list(const std::string &thumbnails_string,
                              const std::string_view def_ext)
{
    if (thumbnails_string.empty())
        return {};

    std::istringstream input(thumbnails_string);
    std::string item;
    ThumbnailErrors errors;
    GCodeThumbnailDefinitionsList thumbnails;
    while (std::getline(input, item, ',')) {
        item = normalize_thumbnail_item(item);
        if (item.empty()) {
            continue;
        }
        Vec2d size(Vec2d::Zero());
        std::istringstream item_input(item);
        std::string value;
        if (std::getline(item_input, value, 'x') && !value.empty()) {
            std::istringstream(value) >> size(0);
            if (std::getline(item_input, value, '/') && !value.empty()) {
                std::istringstream(value) >> size(1);
                if (0 < size(0) && size(0) < 1000 && 0 < size(1) && size(1) < 1000) {
                    std::string ext;
                    std::getline(item_input, ext, '/');
                    if (ext.empty()) {
                        ext = def_ext.empty() ? "PNG" : std::string(def_ext);
                    }
                    GCodeThumbnailsFormat format = GCodeThumbnailsFormat::PNG;
                    if (!parse_thumbnail_format(ext, format)) {
                        errors = enum_bitmask(errors | ThumbnailError::InvalidExt);
                        format = GCodeThumbnailsFormat::PNG;
                    }
                    thumbnails.emplace_back(format, size);
                } else {
                    errors = enum_bitmask(errors | ThumbnailError::OutOfRange);
                }
                continue;
            }
        }
        errors = enum_bitmask(errors | ThumbnailError::InvalidVal);
    }
    return {std::move(thumbnails), errors};
}

} // namespace GCodeThumbnails
} // namespace Slic3r

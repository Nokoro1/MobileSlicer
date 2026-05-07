#include "libslic3r/BuildVolume.hpp"
#include "libslic3r/CustomGCode.hpp"
#include "libslic3r/FaceDetector.hpp"
#include "libslic3r/Format/3mf.hpp"
#include "libslic3r/Format/AMF.hpp"
#include "libslic3r/Format/bbs_3mf.hpp"
#include "libslic3r/Format/DRC.hpp"
#include "libslic3r/Format/OBJ.hpp"
#include "libslic3r/Format/svg.hpp"
#include "libslic3r/I18N.hpp"
#include "libslic3r/MeshBoolean.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/TriangleMeshSlicer.hpp"
#include "libslic3r/Utils.hpp"

#include <string>
#include <utility>

namespace Slic3r {

namespace {
std::string g_temporary_dir = "/tmp";
}

extern "C" void orca_android_set_temporary_dir(const char* path)
{
    if (path != nullptr && path[0] != '\0') {
        g_temporary_dir = path;
    }
}

namespace I18N {
translate_fn_type translate_fn = nullptr;
} // namespace I18N

std::string Preset::get_type_string(Preset::Type type)
{
    switch (type) {
    case Preset::Type::TYPE_FILAMENT:
        return "filament";
    case Preset::Type::TYPE_PRINT:
        return "process";
    case Preset::Type::TYPE_PRINTER:
        return "machine";
    case Preset::Type::TYPE_PHYSICAL_PRINTER:
        return "physical_printer";
    case Preset::Type::TYPE_INVALID:
    default:
        return "invalid";
    }
}

bool load_obj(const char * /* path */, TriangleMesh * /* mesh */, ObjInfo & /* vertex_colors */, std::string &message)
{
    message = "OBJ import is not included in the Android STL model-load probe";
    return false;
}

bool load_obj(const char * /* path */, Model * /* model */, ObjInfo & /* vertex_colors */, std::string &message, const char * /* object_name */)
{
    message = "OBJ import is not included in the Android STL model-load probe";
    return false;
}

bool load_svg(const char * /* path */, Model * /* model */, std::string &message)
{
    message = "SVG import is not included in the Android STL model-load probe";
    return false;
}

bool load_drc(const char * /* path */, TriangleMesh * /* meshptr */)
{
    return false;
}

bool load_drc(const char * /* path */, Model * /* model */, const char * /* object_name */)
{
    return false;
}

bool load_amf(const char * /* path */, DynamicPrintConfig * /* config */, ConfigSubstitutionContext * /* config_substitutions */, Model * /* model */, bool * /* use_inches */)
{
    return false;
}

bool load_3mf(const char * /* path */, DynamicPrintConfig & /* config */, ConfigSubstitutionContext & /* config_substitutions */, Model * /* model */, bool /* check_version */)
{
    return false;
}

bool PrusaFileParser::check_3mf_from_prusa(const std::string /* filename */)
{
    return false;
}

void PrusaFileParser::_start_element_handler(const char * /* name */, const char ** /* attributes */) {}

void PrusaFileParser::_characters_handler(const char * /* s */, int /* len */) {}

#ifndef ORCA_ANDROID_REAL_BBS_3MF
bool load_bbs_3mf(const char * /* path */, DynamicPrintConfig * /* config */, ConfigSubstitutionContext * /* config_substitutions */, Model * /* model */, PlateDataPtrs * /* plate_data_list */, std::vector<Preset *> * /* project_presets */, bool * /* is_bbl_3mf */, Semver * /* file_version */, Import3mfProgressFn /* proFn */, LoadStrategy /* strategy */, BBLProject * /* project */, int /* plate_id */)
{
    return false;
}

void delete_object_mesh(ModelObject & /* object */) {}

void save_object_mesh(ModelObject & /* object */) {}

void remove_backup(Model & /* model */, bool /* removeAll */) {}
#endif

unsigned get_current_pid()
{
    return 0;
}

const std::string &temporary_dir()
{
    return g_temporary_dir;
}

#ifndef ORCA_ANDROID_REAL_BBS_3MF
void save_string_file(const boost::filesystem::path & /* p */, const std::string & /* str */) {}
#endif

Step::Step(std::string /* path */, ImportStepProgressFn /* stepFn */, StepIsUtf8Fn /* isUtf8Fn */) {}

Step::~Step() = default;

Step::Step_Status Step::load()
{
    return Step::Step_Status::LOAD_ERROR;
}

unsigned int Step::get_triangle_num(double /* linear_defletion */, double /* angle_defletion */)
{
    return 0;
}

unsigned int Step::get_triangle_num_tbb(double /* linear_defletion */, double /* angle_defletion */)
{
    return 0;
}

void Step::clean_mesh_data() {}

Step::Step_Status Step::mesh(
    Model * /* model */,
    bool &is_cancel,
    bool /* isSplitCompound */,
    double /* linear_defletion */,
    double /* angle_defletion */)
{
    is_cancel = false;
    return Step::Step_Status::MESH_ERROR;
}

void Step::update_process(int /* load_stage */, int /* current */, int /* total */, bool &cancel)
{
    cancel = false;
}

void FaceDetector::detect_exterior_face() {}

} // namespace Slic3r

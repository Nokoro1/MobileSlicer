#include "orca_wrapper.h"

extern "C" void orca_android_set_resources_dir(const char* path);
extern "C" void orca_android_set_temporary_dir(const char* path);

extern "C" void orca_set_runtime_paths(const char* resources_dir, const char* temporary_dir)
{
    orca_android_set_resources_dir(resources_dir);
    orca_android_set_temporary_dir(temporary_dir);
}

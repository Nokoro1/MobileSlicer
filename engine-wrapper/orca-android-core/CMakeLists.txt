cmake_minimum_required(VERSION 3.22.1)

project(orca_android_core LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(orca_core_android SHARED
    orca_android_core.cpp
    shims/libslic3r/LocalesUtils.cpp
    ../../vendor/orcaslicer/deps_src/admesh/connect.cpp
    ../../vendor/orcaslicer/deps_src/admesh/normals.cpp
    ../../vendor/orcaslicer/deps_src/admesh/shared.cpp
    ../../vendor/orcaslicer/deps_src/admesh/stl_io.cpp
    ../../vendor/orcaslicer/deps_src/admesh/stlinit.cpp
    ../../vendor/orcaslicer/deps_src/admesh/util.cpp
)

target_include_directories(orca_core_android
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/shims
        ${CMAKE_CURRENT_SOURCE_DIR}/../../vendor/orcaslicer/deps_src
        ${CMAKE_CURRENT_SOURCE_DIR}/../../vendor/orcaslicer/deps_src/eigen
)

target_compile_definitions(orca_core_android PRIVATE
    SLIC3R_GUI=0
    SLIC3R_DESKTOP_INTEGRATION=0
)

target_compile_options(orca_core_android PRIVATE
    $<$<COMPILE_LANGUAGE:CXX>:-w>
    $<$<COMPILE_LANGUAGE:C>:-w>
)

set_target_properties(orca_core_android PROPERTIES
    OUTPUT_NAME "orca_core_android"
)

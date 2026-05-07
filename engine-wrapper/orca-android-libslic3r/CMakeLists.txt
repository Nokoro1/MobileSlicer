cmake_minimum_required(VERSION 3.22.1)

project(orca_android_libslic3r LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(ORCA_ANDROID_LIBSLIC3R_ENABLE "Enable the experimental Android libslic3r port path" OFF)
option(ORCA_ANDROID_LIBSLIC3R_BUILD_EXPERIMENTAL_TOOLS "Build experimental CLI probe executables and helper tooling" ON)

if (NOT ORCA_ANDROID_LIBSLIC3R_ENABLE)
    message(STATUS "orca_android_libslic3r: disabled. Enable with -DORCA_ANDROID_LIBSLIC3R_ENABLE=ON for Phase 2 port work.")
    return()
endif()

include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/Dependencies.cmake)

set(ORCA_VENDOR_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../vendor/orcaslicer)
set(ORCA_LIBSLIC3R_ROOT ${ORCA_VENDOR_ROOT}/src/libslic3r)
set(ORCA_DEPS_SRC_ROOT ${ORCA_VENDOR_ROOT}/deps_src)
set(ORCA_ANDROID_LOCAL_SHIMS_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/shims)
set(ORCA_ANDROID_SHIMS_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../orca-android-core/shims)

if (NOT DEFINED ORCA_CHECK_GCODE_PLACEHOLDERS)
    set(ORCA_CHECK_GCODE_PLACEHOLDERS 0)
endif()

set(ORCA_ANDROID_LIBSLIC3R_VERSION "android-dependency-subset")
configure_file(
    ${ORCA_LIBSLIC3R_ROOT}/libslic3r_version.h.in
    ${CMAKE_CURRENT_BINARY_DIR}/generated/libslic3r_version.h
    @ONLY
)

set(ORCA_ANDROID_BOOST_PREFIX "" CACHE PATH "Installed Android Boost prefix")
set(ORCA_ANDROID_BOOST_HEADERS_DIR "" CACHE PATH "Android Boost headers root; use full Boost source tree when the installed prefix is header-filtered")
set(ORCA_ANDROID_BOOST_EXTRA_INCLUDE_DIRS "" CACHE STRING "Additional Boost header-only include roots required by CGAL/libslic3r when the install prefix is component-filtered")
set(ORCA_ANDROID_CEREAL_PREFIX "${CMAKE_CURRENT_SOURCE_DIR}/third_party/cereal-1.3.0" CACHE PATH "Header-only cereal root for Android libslic3r experiments")
set(ORCA_ANDROID_OPENSSL_INCLUDE_DIR "" CACHE PATH "OpenSSL include root required by libslic3r config implementation units")
set(ORCA_ANDROID_OPENSSL_CRYPTO_LIBRARY "" CACHE FILEPATH "Android libcrypto.a for config implementation units")
set(ORCA_ANDROID_OPENSSL_SSL_LIBRARY "" CACHE FILEPATH "Android libssl.a for config implementation units")
set(ORCA_ANDROID_TBB_PREFIX "" CACHE PATH "Installed Android oneTBB prefix")
set(ORCA_ANDROID_NLOPT_PREFIX "" CACHE PATH "Installed Android NLopt prefix")
set(ORCA_ANDROID_GMP_PREFIX "" CACHE PATH "Installed Android GMP prefix")
set(ORCA_ANDROID_MPFR_PREFIX "" CACHE PATH "Installed Android MPFR prefix")
set(ORCA_ANDROID_CGAL_PREFIX "" CACHE PATH "Installed Android CGAL prefix")
set(ORCA_ANDROID_LIBNOISE_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../third_party_stub" CACHE PATH "Android libnoise-compatible include root")

if (ORCA_ANDROID_GMP_PREFIX)
    set(GMP_INCLUDE_DIR "${ORCA_ANDROID_GMP_PREFIX}/include" CACHE PATH "" FORCE)
    set(GMP_LIBRARIES "${ORCA_ANDROID_GMP_PREFIX}/lib/libgmp.a" CACHE FILEPATH "" FORCE)
    set(GMPXX_LIBRARIES "${ORCA_ANDROID_GMP_PREFIX}/lib/libgmpxx.a" CACHE FILEPATH "" FORCE)
endif()

if (ORCA_ANDROID_MPFR_PREFIX)
    set(MPFR_INCLUDE_DIR "${ORCA_ANDROID_MPFR_PREFIX}/include" CACHE PATH "" FORCE)
    set(MPFR_LIBRARIES "${ORCA_ANDROID_MPFR_PREFIX}/lib/libmpfr.a" CACHE FILEPATH "" FORCE)
endif()

if (ORCA_ANDROID_BOOST_HEADERS_DIR)
    set(ORCA_ANDROID_BOOST_INCLUDE_DIR "${ORCA_ANDROID_BOOST_HEADERS_DIR}")
else()
    set(ORCA_ANDROID_BOOST_INCLUDE_DIR "${ORCA_ANDROID_BOOST_PREFIX}/include")
endif()
set(ORCA_ANDROID_BOOST_FILESYSTEM_LIBRARY "${ORCA_ANDROID_BOOST_PREFIX}/lib/libboost_filesystem.a")

set(ORCA_ANDROID_TBB_INCLUDE_DIR "${ORCA_ANDROID_TBB_PREFIX}/include")
set(ORCA_ANDROID_TBB_LIBRARY "${ORCA_ANDROID_TBB_PREFIX}/lib/libtbb.a")
set(ORCA_ANDROID_TBBMALLOC_LIBRARY "${ORCA_ANDROID_TBB_PREFIX}/lib/libtbbmalloc.a")
set(ORCA_ANDROID_NLOPT_INCLUDE_DIR "${ORCA_ANDROID_NLOPT_PREFIX}/include")
set(ORCA_ANDROID_NLOPT_LIBRARY "${ORCA_ANDROID_NLOPT_PREFIX}/lib/libnlopt.a")

set(ORCA_ANDROID_CEREAL_INCLUDE_DIR "${ORCA_ANDROID_CEREAL_PREFIX}/include")
set(ORCA_ANDROID_CGAL_INCLUDE_DIR "${ORCA_ANDROID_CGAL_PREFIX}/include")

set(ORCA_ANDROID_BOOST_SRC_ROOT "" CACHE PATH "Boost source root for compiling individual modules")
if (ORCA_ANDROID_BOOST_SRC_ROOT)
    set(ORCA_ANDROID_BOOST_NOWIDE_CSTDLIB_SRC "${ORCA_ANDROID_BOOST_SRC_ROOT}/libs/nowide/src/cstdlib.cpp")
else()
    set(ORCA_ANDROID_BOOST_NOWIDE_CSTDLIB_SRC "")
endif()

add_library(orca_android_libslic3r_port_config INTERFACE)
target_include_directories(orca_android_libslic3r_port_config INTERFACE
    ${CMAKE_CURRENT_BINARY_DIR}/generated
    ${ORCA_ANDROID_LOCAL_SHIMS_ROOT}
    ${ORCA_ANDROID_SHIMS_ROOT}
    ${ORCA_VENDOR_ROOT}/src
    ${ORCA_LIBSLIC3R_ROOT}
    ${ORCA_DEPS_SRC_ROOT}
    ${ORCA_DEPS_SRC_ROOT}/mcut/include
    ${ORCA_DEPS_SRC_ROOT}/libnest2d/include
    ${ORCA_DEPS_SRC_ROOT}/clipper2/Clipper2Lib/include
    ${ORCA_DEPS_SRC_ROOT}/libigl
    ${ORCA_DEPS_SRC_ROOT}/miniz
    ${ORCA_DEPS_SRC_ROOT}/expat
    ${ORCA_DEPS_SRC_ROOT}/eigen
    ${ORCA_DEPS_SRC_ROOT}/qhull/src
    ${ORCA_ANDROID_NLOPT_INCLUDE_DIR}
)
target_compile_definitions(orca_android_libslic3r_port_config INTERFACE
    SLIC3R_GUI=0
    SLIC3R_DESKTOP_INTEGRATION=0
    ORCA_ANDROID_HEADLESS=1
    ORCA_ANDROID_LIBSLIC3R_EXPERIMENTAL=1
    NDEBUG
)
target_compile_options(orca_android_libslic3r_port_config INTERFACE
    $<$<COMPILE_LANGUAGE:CXX>:-O3>
    $<$<COMPILE_LANGUAGE:CXX>:-fomit-frame-pointer>
)

find_package(Threads REQUIRED)

orca_android_require_directory("Android Boost headers" "${ORCA_ANDROID_BOOST_INCLUDE_DIR}")
orca_android_require_file("Android Boost filesystem static library" "${ORCA_ANDROID_BOOST_FILESYSTEM_LIBRARY}")
orca_android_require_directory("Android TBB headers" "${ORCA_ANDROID_TBB_INCLUDE_DIR}")
orca_android_require_file("Android TBB static library" "${ORCA_ANDROID_TBB_LIBRARY}")
orca_android_require_file("Android TBB malloc static library" "${ORCA_ANDROID_TBBMALLOC_LIBRARY}")
orca_android_require_directory("Android NLopt headers" "${ORCA_ANDROID_NLOPT_INCLUDE_DIR}")
orca_android_require_file("Android NLopt static library" "${ORCA_ANDROID_NLOPT_LIBRARY}")
orca_android_require_directory("Android cereal headers" "${ORCA_ANDROID_CEREAL_INCLUDE_DIR}")
orca_android_require_file("Android cereal core header" "${ORCA_ANDROID_CEREAL_INCLUDE_DIR}/cereal/cereal.hpp")
orca_android_require_file("Android libnoise noise.h" "${ORCA_ANDROID_LIBNOISE_INCLUDE_DIR}/libnoise/noise.h")
orca_android_require_directory("Android CGAL headers" "${ORCA_ANDROID_CGAL_INCLUDE_DIR}")
orca_android_require_file("Android GMP static library" "${GMP_LIBRARIES}")
orca_android_require_file("Android GMPXX static library" "${GMPXX_LIBRARIES}")
orca_android_require_file("Android MPFR static library" "${MPFR_LIBRARIES}")
orca_android_require_directory("Android OpenSSL headers" "${ORCA_ANDROID_OPENSSL_INCLUDE_DIR}")
orca_android_require_file("Android OpenSSL MD5 header" "${ORCA_ANDROID_OPENSSL_INCLUDE_DIR}/openssl/md5.h")
orca_android_require_file("Android OpenSSL crypto library" "${ORCA_ANDROID_OPENSSL_CRYPTO_LIBRARY}")

add_library(orca_android_boost_headers INTERFACE)
target_include_directories(orca_android_boost_headers INTERFACE "${ORCA_ANDROID_BOOST_INCLUDE_DIR}")
if (ORCA_ANDROID_BOOST_EXTRA_INCLUDE_DIRS)
    target_include_directories(orca_android_boost_headers INTERFACE ${ORCA_ANDROID_BOOST_EXTRA_INCLUDE_DIRS})
endif()

add_library(orca_android_boost_system INTERFACE)
target_include_directories(orca_android_boost_system INTERFACE "${ORCA_ANDROID_BOOST_INCLUDE_DIR}")

add_library(orca_android_boost_filesystem STATIC IMPORTED GLOBAL)
set_target_properties(orca_android_boost_filesystem PROPERTIES
    IMPORTED_LOCATION "${ORCA_ANDROID_BOOST_FILESYSTEM_LIBRARY}"
    INTERFACE_INCLUDE_DIRECTORIES "${ORCA_ANDROID_BOOST_INCLUDE_DIR}"
    INTERFACE_LINK_LIBRARIES "orca_android_boost_system"
)

add_library(orca_android_tbb STATIC IMPORTED GLOBAL)
set_target_properties(orca_android_tbb PROPERTIES
    IMPORTED_LOCATION "${ORCA_ANDROID_TBB_LIBRARY}"
    INTERFACE_INCLUDE_DIRECTORIES "${ORCA_ANDROID_TBB_INCLUDE_DIR}"
    INTERFACE_LINK_LIBRARIES "Threads::Threads;dl"
)

add_library(orca_android_tbbmalloc STATIC IMPORTED GLOBAL)
set_target_properties(orca_android_tbbmalloc PROPERTIES
    IMPORTED_LOCATION "${ORCA_ANDROID_TBBMALLOC_LIBRARY}"
    INTERFACE_INCLUDE_DIRECTORIES "${ORCA_ANDROID_TBB_INCLUDE_DIR}"
    INTERFACE_LINK_LIBRARIES "Threads::Threads;dl"
)

add_library(orca_android_nlopt STATIC IMPORTED GLOBAL)
set_target_properties(orca_android_nlopt PROPERTIES
    IMPORTED_LOCATION "${ORCA_ANDROID_NLOPT_LIBRARY}"
    INTERFACE_INCLUDE_DIRECTORIES "${ORCA_ANDROID_NLOPT_INCLUDE_DIR}"
    INTERFACE_LINK_LIBRARIES "m"
)

add_library(orca_android_cereal_headers INTERFACE)
target_include_directories(orca_android_cereal_headers SYSTEM INTERFACE
    "${ORCA_ANDROID_CEREAL_INCLUDE_DIR}"
)

add_library(orca_android_libnoise_headers INTERFACE)
target_include_directories(orca_android_libnoise_headers SYSTEM INTERFACE
    "${ORCA_ANDROID_LIBNOISE_INCLUDE_DIR}"
)

add_library(orca_android_cgal_headers INTERFACE)
target_include_directories(orca_android_cgal_headers SYSTEM INTERFACE
    "${ORCA_ANDROID_CGAL_INCLUDE_DIR}"
    "${GMP_INCLUDE_DIR}"
    "${MPFR_INCLUDE_DIR}"
)
target_link_libraries(orca_android_cgal_headers INTERFACE
    "${GMPXX_LIBRARIES}"
    "${GMP_LIBRARIES}"
    "${MPFR_LIBRARIES}"
)

add_library(orca_android_openssl_crypto STATIC IMPORTED GLOBAL)
set_target_properties(orca_android_openssl_crypto PROPERTIES
    IMPORTED_LOCATION "${ORCA_ANDROID_OPENSSL_CRYPTO_LIBRARY}"
    INTERFACE_INCLUDE_DIRECTORIES "${ORCA_ANDROID_OPENSSL_INCLUDE_DIR}"
)
if (ORCA_ANDROID_OPENSSL_SSL_LIBRARY AND EXISTS "${ORCA_ANDROID_OPENSSL_SSL_LIBRARY}")
    add_library(orca_android_openssl_ssl STATIC IMPORTED GLOBAL)
    set_target_properties(orca_android_openssl_ssl PROPERTIES
        IMPORTED_LOCATION "${ORCA_ANDROID_OPENSSL_SSL_LIBRARY}"
        INTERFACE_INCLUDE_DIRECTORIES "${ORCA_ANDROID_OPENSSL_INCLUDE_DIR}"
        INTERFACE_LINK_LIBRARIES "orca_android_openssl_crypto"
    )
endif()

add_library(orca_android_qhull STATIC
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/global_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/stat_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/geom2_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/poly2_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/merge_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/libqhull_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/geom_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/poly_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/qset_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/mem_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/random_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/usermem_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/io_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/user_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhull_r/rboxlib_r.c
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/Coordinates.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/PointCoordinates.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/Qhull.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/QhullFacet.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/QhullFacetList.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/QhullFacetSet.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/QhullHyperplane.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/QhullPoint.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/QhullPointSet.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/QhullPoints.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/QhullQh.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/QhullRidge.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/QhullSet.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/QhullStat.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/QhullVertex.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/QhullVertexSet.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/RboxPoints.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/RoadError.cpp
    ${ORCA_DEPS_SRC_ROOT}/qhull/src/libqhullcpp/RoadLogEvent.cpp
)
target_link_libraries(orca_android_qhull PRIVATE m)
target_include_directories(orca_android_qhull SYSTEM BEFORE PUBLIC
    ${ORCA_DEPS_SRC_ROOT}/qhull/src
)

add_library(orca_android_glu_libtess STATIC
    ${ORCA_DEPS_SRC_ROOT}/glu-libtess/src/dict.c
    ${ORCA_DEPS_SRC_ROOT}/glu-libtess/src/geom.c
    ${ORCA_DEPS_SRC_ROOT}/glu-libtess/src/memalloc.c
    ${ORCA_DEPS_SRC_ROOT}/glu-libtess/src/mesh.c
    ${ORCA_DEPS_SRC_ROOT}/glu-libtess/src/normal.c
    ${ORCA_DEPS_SRC_ROOT}/glu-libtess/src/priorityq.c
    ${ORCA_DEPS_SRC_ROOT}/glu-libtess/src/render.c
    ${ORCA_DEPS_SRC_ROOT}/glu-libtess/src/sweep.c
    ${ORCA_DEPS_SRC_ROOT}/glu-libtess/src/tess.c
    ${ORCA_DEPS_SRC_ROOT}/glu-libtess/src/tessmono.c
)
target_link_libraries(orca_android_glu_libtess PRIVATE m)
target_include_directories(orca_android_glu_libtess SYSTEM PUBLIC
    ${ORCA_DEPS_SRC_ROOT}/glu-libtess/include
)
target_include_directories(orca_android_glu_libtess SYSTEM PRIVATE
    ${ORCA_DEPS_SRC_ROOT}/glu-libtess
)

add_library(orca_android_libslic3r_dependency_subset STATIC
    orca_android_libslic3r_dependency_subset.cpp
)
target_link_libraries(orca_android_libslic3r_dependency_subset PRIVATE
    orca_android_libslic3r_port_config
    orca_android_boost_headers
    orca_android_boost_filesystem
    orca_android_tbb
    orca_android_tbbmalloc
    orca_android_nlopt
    orca_android_cgal_headers
)

add_library(orca_android_libslic3r_config_subset STATIC
    orca_android_libslic3r_config_subset.cpp
)
target_link_libraries(orca_android_libslic3r_config_subset PRIVATE
    orca_android_libslic3r_port_config
    orca_android_boost_headers
    orca_android_cereal_headers
    orca_android_tbb
    orca_android_cgal_headers
)

add_library(orca_android_libslic3r_printconfig_impl_subset STATIC
    orca_android_libslic3r_printconfig_impl_subset.cpp
    ${ORCA_LIBSLIC3R_ROOT}/PrintConfig.cpp
)
target_link_libraries(orca_android_libslic3r_printconfig_impl_subset PRIVATE
    orca_android_libslic3r_port_config
    orca_android_boost_headers
    orca_android_boost_filesystem
    orca_android_cereal_headers
    orca_android_tbb
    orca_android_cgal_headers
    orca_android_openssl_crypto
)

add_library(orca_android_libslic3r_config_impl_subset SHARED
    orca_android_libslic3r_config_impl_subset.cpp
    # Config core
    ${ORCA_LIBSLIC3R_ROOT}/Config.cpp
    ${ORCA_LIBSLIC3R_ROOT}/PrintConfig.cpp
    ${ORCA_LIBSLIC3R_ROOT}/libslic3r.cpp
    ${ORCA_ANDROID_SHIMS_ROOT}/libslic3r/LocalesUtils.cpp
    ${ORCA_LIBSLIC3R_ROOT}/MaterialType.cpp
    # Geometry
    ${ORCA_LIBSLIC3R_ROOT}/Polygon.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Polyline.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ExPolygon.cpp
    ${ORCA_LIBSLIC3R_ROOT}/MultiPoint.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Line.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Point.cpp
    ${ORCA_LIBSLIC3R_ROOT}/MutablePolygon.cpp
    ${ORCA_LIBSLIC3R_ROOT}/BoundingBox.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ClipperUtils.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Clipper2Utils.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ExtrusionEntity.cpp
    ${ORCA_LIBSLIC3R_ROOT}/clipper.cpp
    ${ORCA_DEPS_SRC_ROOT}/clipper/clipper_z.cpp
    ${ORCA_DEPS_SRC_ROOT}/clipper2/Clipper2Lib/src/clipper.engine.cpp
    ${ORCA_DEPS_SRC_ROOT}/clipper2/Clipper2Lib/src/clipper.offset.cpp
    ${ORCA_DEPS_SRC_ROOT}/clipper2/Clipper2Lib/src/clipper.rectclip.cpp
    ${ORCA_DEPS_SRC_ROOT}/clipper2/Clipper2Lib/src/clipper2_z.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Geometry.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ShortestPath.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Circle.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/orca_android_libslic3r_config_impl_flow_subset.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ArcFitter.cpp
    # Boost.Nowide compiled source (setenv)
    ${ORCA_ANDROID_BOOST_NOWIDE_CSTDLIB_SRC}
    # Stubs for symbols referenced but not exercised in config subset
    ${CMAKE_CURRENT_SOURCE_DIR}/android_libslic3r_stubs.cpp
)
target_link_libraries(orca_android_libslic3r_config_impl_subset PRIVATE
    orca_android_libslic3r_port_config
    orca_android_boost_headers
    orca_android_boost_filesystem
    orca_android_cereal_headers
    orca_android_tbb
    orca_android_tbbmalloc
    orca_android_cgal_headers
    orca_android_openssl_crypto
)
target_compile_definitions(orca_android_libslic3r_config_impl_subset PRIVATE
    ORCA_ANDROID_CONFIG_IMPL_SUBSET_NO_PRINT
)

if (ORCA_ANDROID_LIBSLIC3R_BUILD_EXPERIMENTAL_TOOLS)
    add_executable(orca_android_libslic3r_model_stl_probe
        orca_android_libslic3r_model_stl_probe.cpp
        # Config core reused by Model.hpp / Model.cpp.
        ${ORCA_LIBSLIC3R_ROOT}/Config.cpp
        ${ORCA_LIBSLIC3R_ROOT}/PrintConfig.cpp
        ${ORCA_LIBSLIC3R_ROOT}/libslic3r.cpp
        ${ORCA_ANDROID_SHIMS_ROOT}/libslic3r/LocalesUtils.cpp
        ${ORCA_LIBSLIC3R_ROOT}/MaterialType.cpp
        ${ORCA_LIBSLIC3R_ROOT}/GCodeReader.cpp
        # Geometry already proven by config implementation linkage.
        ${ORCA_LIBSLIC3R_ROOT}/Polygon.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Polyline.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ExPolygon.cpp
        ${ORCA_LIBSLIC3R_ROOT}/MultiPoint.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Line.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Point.cpp
        ${ORCA_LIBSLIC3R_ROOT}/BoundingBox.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ClipperUtils.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Clipper2Utils.cpp
        ${ORCA_LIBSLIC3R_ROOT}/clipper.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Geometry.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Circle.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ArcFitter.cpp
        # Model-loading implementation units under evaluation.
        ${ORCA_LIBSLIC3R_ROOT}/ObjectID.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Geometry/ConvexHull.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ModelArrange.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Arrange.cpp
        ${ORCA_LIBSLIC3R_ROOT}/TriangleMesh.cpp
        ${ORCA_LIBSLIC3R_ROOT}/TriangleMeshSlicer.cpp
        ${ORCA_LIBSLIC3R_ROOT}/TriangleSelector.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Model.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Format/STL.cpp
        ${ORCA_DEPS_SRC_ROOT}/semver/semver.c
        # Admesh STL reader required by TriangleMesh::ReadSTLFile.
        ${ORCA_DEPS_SRC_ROOT}/admesh/connect.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/normals.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/shared.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/stl_io.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/stlinit.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/util.cpp
        # Boost.Nowide compiled source (setenv).
        ${ORCA_ANDROID_BOOST_NOWIDE_CSTDLIB_SRC}
        # Existing linker stubs retained unless model loading proves they are required.
        ${CMAKE_CURRENT_SOURCE_DIR}/android_libslic3r_stubs.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/android_libslic3r_model_stubs.cpp
    )
    target_link_libraries(orca_android_libslic3r_model_stl_probe PRIVATE
        orca_android_libslic3r_port_config
        orca_android_boost_headers
        orca_android_boost_filesystem
        orca_android_cereal_headers
        orca_android_libnoise_headers
        orca_android_tbb
        orca_android_tbbmalloc
        orca_android_nlopt
        orca_android_cgal_headers
        orca_android_qhull
        orca_android_openssl_crypto
    )
    target_compile_options(orca_android_libslic3r_model_stl_probe PRIVATE
        $<$<COMPILE_LANGUAGE:CXX>:-w>
        $<$<COMPILE_LANGUAGE:CXX>:-include${CMAKE_CURRENT_SOURCE_DIR}/shims/orca_android_libslic3r_model_preinclude.hpp>
    )

    add_executable(orca_android_libslic3r_triangle_selector_probe
        orca_android_libslic3r_triangle_selector_probe.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/../orca_native_paint.cpp
        # Config core reused by Model.hpp / Model.cpp.
        ${ORCA_LIBSLIC3R_ROOT}/Config.cpp
        ${ORCA_LIBSLIC3R_ROOT}/PrintConfig.cpp
        ${ORCA_LIBSLIC3R_ROOT}/libslic3r.cpp
        ${ORCA_ANDROID_SHIMS_ROOT}/libslic3r/LocalesUtils.cpp
        ${ORCA_LIBSLIC3R_ROOT}/MaterialType.cpp
        ${ORCA_LIBSLIC3R_ROOT}/GCodeReader.cpp
        # Geometry and model implementation used by TriangleSelector and FacetsAnnotation.
        ${ORCA_LIBSLIC3R_ROOT}/Polygon.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Polyline.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ExPolygon.cpp
        ${ORCA_LIBSLIC3R_ROOT}/MultiPoint.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Line.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Point.cpp
        ${ORCA_LIBSLIC3R_ROOT}/BoundingBox.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ClipperUtils.cpp
        ${ORCA_LIBSLIC3R_ROOT}/clipper.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Geometry.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Circle.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ArcFitter.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ObjectID.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Geometry/ConvexHull.cpp
        ${ORCA_LIBSLIC3R_ROOT}/TriangleMesh.cpp
        ${ORCA_LIBSLIC3R_ROOT}/TriangleSelector.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Model.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Format/STL.cpp
        ${ORCA_DEPS_SRC_ROOT}/semver/semver.c
        ${ORCA_DEPS_SRC_ROOT}/admesh/connect.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/normals.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/shared.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/stl_io.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/stlinit.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/util.cpp
        ${ORCA_ANDROID_BOOST_NOWIDE_CSTDLIB_SRC}
        ${CMAKE_CURRENT_SOURCE_DIR}/android_libslic3r_stubs.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/android_libslic3r_model_stubs.cpp
    )
    target_link_libraries(orca_android_libslic3r_triangle_selector_probe PRIVATE
        orca_android_libslic3r_port_config
        orca_android_boost_headers
        orca_android_boost_filesystem
        orca_android_cereal_headers
        orca_android_libnoise_headers
        orca_android_tbb
        orca_android_tbbmalloc
        orca_android_cgal_headers
        orca_android_qhull
        orca_android_glu_libtess
        orca_android_openssl_crypto
    )
    target_include_directories(orca_android_libslic3r_triangle_selector_probe PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/..
    )
    target_compile_options(orca_android_libslic3r_triangle_selector_probe PRIVATE
        $<$<COMPILE_LANGUAGE:CXX>:-w>
        $<$<COMPILE_LANGUAGE:CXX>:-include${CMAKE_CURRENT_SOURCE_DIR}/shims/orca_android_libslic3r_model_preinclude.hpp>
    )
    target_link_options(orca_android_libslic3r_triangle_selector_probe PRIVATE -Wl,--gc-sections)

    add_executable(orca_android_libslic3r_cut_probe
        orca_android_libslic3r_cut_probe.cpp
        # Config core reused by Model.hpp / Model.cpp.
        ${ORCA_LIBSLIC3R_ROOT}/Config.cpp
        ${ORCA_LIBSLIC3R_ROOT}/PrintConfig.cpp
        ${ORCA_LIBSLIC3R_ROOT}/libslic3r.cpp
        ${ORCA_ANDROID_SHIMS_ROOT}/libslic3r/LocalesUtils.cpp
        ${ORCA_LIBSLIC3R_ROOT}/MaterialType.cpp
        ${ORCA_LIBSLIC3R_ROOT}/GCodeReader.cpp
        # Geometry and model implementation used by CutUtils.
        ${ORCA_LIBSLIC3R_ROOT}/Polygon.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Polyline.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ExPolygon.cpp
        ${ORCA_LIBSLIC3R_ROOT}/MultiPoint.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Line.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Point.cpp
        ${ORCA_LIBSLIC3R_ROOT}/BoundingBox.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ClipperUtils.cpp
        ${ORCA_LIBSLIC3R_ROOT}/clipper.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Geometry.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Circle.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ArcFitter.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ObjectID.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Geometry/ConvexHull.cpp
        ${ORCA_LIBSLIC3R_ROOT}/TriangleMesh.cpp
        ${ORCA_LIBSLIC3R_ROOT}/TriangleMeshSlicer.cpp
        ${ORCA_LIBSLIC3R_ROOT}/TriangleSelector.cpp
        ${ORCA_LIBSLIC3R_ROOT}/CutUtils.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Triangulation.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Tesselate.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Model.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Format/STL.cpp
        ${ORCA_DEPS_SRC_ROOT}/semver/semver.c
        ${ORCA_DEPS_SRC_ROOT}/admesh/connect.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/normals.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/shared.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/stl_io.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/stlinit.cpp
        ${ORCA_DEPS_SRC_ROOT}/admesh/util.cpp
        ${ORCA_ANDROID_BOOST_NOWIDE_CSTDLIB_SRC}
        ${CMAKE_CURRENT_SOURCE_DIR}/android_libslic3r_stubs.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/android_libslic3r_model_stubs.cpp
    )
    target_link_libraries(orca_android_libslic3r_cut_probe PRIVATE
        orca_android_libslic3r_port_config
        orca_android_boost_headers
        orca_android_boost_filesystem
        orca_android_cereal_headers
        orca_android_libnoise_headers
        orca_android_tbb
        orca_android_tbbmalloc
        orca_android_cgal_headers
        orca_android_qhull
        orca_android_glu_libtess
        orca_android_openssl_crypto
    )
    target_compile_options(orca_android_libslic3r_cut_probe PRIVATE
        $<$<COMPILE_LANGUAGE:CXX>:-w>
        $<$<COMPILE_LANGUAGE:CXX>:-include${CMAKE_CURRENT_SOURCE_DIR}/shims/orca_android_libslic3r_model_preinclude.hpp>
    )
    target_link_options(orca_android_libslic3r_cut_probe PRIVATE -Wl,--gc-sections)

    add_executable(orca_android_libslic3r_config_json_probe
        orca_android_libslic3r_config_json_probe.cpp
        # Config core reused from the completed config implementation milestone.
        ${ORCA_LIBSLIC3R_ROOT}/Config.cpp
        ${ORCA_LIBSLIC3R_ROOT}/PrintConfig.cpp
        ${ORCA_LIBSLIC3R_ROOT}/libslic3r.cpp
        ${ORCA_ANDROID_SHIMS_ROOT}/libslic3r/LocalesUtils.cpp
        ${ORCA_LIBSLIC3R_ROOT}/MaterialType.cpp
        # Geometry required by config definitions and validation.
        ${ORCA_LIBSLIC3R_ROOT}/Polygon.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Polyline.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ExPolygon.cpp
        ${ORCA_LIBSLIC3R_ROOT}/MultiPoint.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Line.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Point.cpp
        ${ORCA_LIBSLIC3R_ROOT}/BoundingBox.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ClipperUtils.cpp
        ${ORCA_LIBSLIC3R_ROOT}/clipper.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Geometry.cpp
        ${ORCA_LIBSLIC3R_ROOT}/Circle.cpp
        ${ORCA_LIBSLIC3R_ROOT}/ArcFitter.cpp
        # Boost.Nowide compiled source (setenv).
        ${ORCA_ANDROID_BOOST_NOWIDE_CSTDLIB_SRC}
        # Existing linker stubs retained for config-linked code not exercised here.
        ${CMAKE_CURRENT_SOURCE_DIR}/android_libslic3r_stubs.cpp
    )
    target_link_libraries(orca_android_libslic3r_config_json_probe PRIVATE
        orca_android_libslic3r_port_config
        orca_android_boost_headers
        orca_android_boost_filesystem
        orca_android_cereal_headers
        orca_android_libnoise_headers
        orca_android_tbb
        orca_android_tbbmalloc
        orca_android_cgal_headers
        orca_android_openssl_crypto
    )
endif()

set(ORCA_ANDROID_LIBSLIC3R_PRINT_GCODE_ENGINE_SOURCES
    # Config core reused from the completed config implementation milestone.
    ${ORCA_LIBSLIC3R_ROOT}/Config.cpp
    ${ORCA_LIBSLIC3R_ROOT}/PrintConfig.cpp
    ${ORCA_LIBSLIC3R_ROOT}/libslic3r.cpp
    ${ORCA_ANDROID_SHIMS_ROOT}/libslic3r/LocalesUtils.cpp
    ${ORCA_LIBSLIC3R_ROOT}/MaterialType.cpp
    ${ORCA_LIBSLIC3R_ROOT}/NormalUtils.cpp
    # Geometry already proven by config implementation linkage.
    ${ORCA_LIBSLIC3R_ROOT}/Polygon.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Polyline.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ExPolygon.cpp
    ${ORCA_LIBSLIC3R_ROOT}/MultiPoint.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Line.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Point.cpp
    ${ORCA_LIBSLIC3R_ROOT}/MutablePolygon.cpp
    ${ORCA_LIBSLIC3R_ROOT}/BoundingBox.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ClipperUtils.cpp
    ${ORCA_LIBSLIC3R_ROOT}/clipper.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Geometry.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Circle.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ArcFitter.cpp
    # Model-loading implementation units already proven by STL probe.
    ${ORCA_LIBSLIC3R_ROOT}/ObjectID.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Geometry/ConvexHull.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arrange.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Orient.cpp
    ${ORCA_LIBSLIC3R_ROOT}/TriangleMesh.cpp
    ${ORCA_LIBSLIC3R_ROOT}/TriangleMeshSlicer.cpp
    ${ORCA_LIBSLIC3R_ROOT}/TriangleSelector.cpp
    ${ORCA_LIBSLIC3R_ROOT}/MeshBoolean.cpp
    ${ORCA_LIBSLIC3R_ROOT}/CutUtils.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Triangulation.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Tesselate.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Model.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Format/STL.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Format/bbs_3mf.cpp
    ${ORCA_LIBSLIC3R_ROOT}/miniz_extension.cpp
    ${ORCA_DEPS_SRC_ROOT}/semver/semver.c
    ${ORCA_DEPS_SRC_ROOT}/admesh/connect.cpp
    ${ORCA_DEPS_SRC_ROOT}/admesh/normals.cpp
    ${ORCA_DEPS_SRC_ROOT}/admesh/shared.cpp
    ${ORCA_DEPS_SRC_ROOT}/admesh/stl_io.cpp
    ${ORCA_DEPS_SRC_ROOT}/admesh/stlinit.cpp
    ${ORCA_DEPS_SRC_ROOT}/admesh/util.cpp
    ${ORCA_DEPS_SRC_ROOT}/clipper/clipper_z.cpp
    ${ORCA_DEPS_SRC_ROOT}/clipper2/Clipper2Lib/src/clipper.engine.cpp
    ${ORCA_DEPS_SRC_ROOT}/clipper2/Clipper2Lib/src/clipper.offset.cpp
    ${ORCA_DEPS_SRC_ROOT}/clipper2/Clipper2Lib/src/clipper.rectclip.cpp
    ${ORCA_DEPS_SRC_ROOT}/clipper2/Clipper2Lib/src/clipper2_z.cpp
    ${ORCA_DEPS_SRC_ROOT}/miniz/miniz.c
    ${ORCA_DEPS_SRC_ROOT}/expat/xmlparse.c
    ${ORCA_DEPS_SRC_ROOT}/expat/xmlrole.c
    ${ORCA_DEPS_SRC_ROOT}/expat/xmltok.c
    ${ORCA_DEPS_SRC_ROOT}/mcut/source/bvh.cpp
    ${ORCA_DEPS_SRC_ROOT}/mcut/source/frontend.cpp
    ${ORCA_DEPS_SRC_ROOT}/mcut/source/hmesh.cpp
    ${ORCA_DEPS_SRC_ROOT}/mcut/source/kernel.cpp
    ${ORCA_DEPS_SRC_ROOT}/mcut/source/math.cpp
    ${ORCA_DEPS_SRC_ROOT}/mcut/source/mcut.cpp
    ${ORCA_DEPS_SRC_ROOT}/mcut/source/preproc.cpp
    ${ORCA_DEPS_SRC_ROOT}/mcut/source/shewchuk.c
    # First print/gcode root units under evaluation.
    ${ORCA_LIBSLIC3R_ROOT}/PrintBase.cpp
    ${ORCA_LIBSLIC3R_ROOT}/PrintApply.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Print.cpp
    ${ORCA_LIBSLIC3R_ROOT}/PrintObject.cpp
    ${ORCA_LIBSLIC3R_ROOT}/PrintObjectSlice.cpp
    ${ORCA_LIBSLIC3R_ROOT}/MultiMaterialSegmentation.cpp
    ${ORCA_LIBSLIC3R_ROOT}/PrintRegion.cpp
    ${ORCA_LIBSLIC3R_ROOT}/PlaceholderParser.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Layer.cpp
    ${ORCA_LIBSLIC3R_ROOT}/LayerRegion.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Surface.cpp
    ${ORCA_LIBSLIC3R_ROOT}/SurfaceCollection.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Slicing.cpp
    ${ORCA_LIBSLIC3R_ROOT}/SlicingAdaptive.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Flow.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ElephantFootCompensation.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Brim.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/Fill3DHoneycomb.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/FillAdaptive.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/FillBase.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/FillRectilinear.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/FillConcentric.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/FillConcentricInternal.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/FillCrossHatch.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/FillGyroid.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/FillHoneycomb.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/FillLightning.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/FillLine.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/FillPlanePath.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/FillTpmsD.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/FillTpmsFK.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/Lightning/DistanceField.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/Lightning/Generator.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/Lightning/Layer.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/Lightning/TreeNode.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Algorithm/LineSplit.cpp
    ${ORCA_LIBSLIC3R_ROOT}/PerimeterGenerator.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Feature/FuzzySkin/FuzzySkin.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ExtrusionEntity.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Extruder.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ExtrusionEntityCollection.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Fill/Fill.cpp
    ${ORCA_LIBSLIC3R_ROOT}/PrincipalComponents2D.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ShortestPath.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Algorithm/RegionExpansion.cpp
    ${ORCA_LIBSLIC3R_ROOT}/BridgeDetector.cpp
    ${ORCA_LIBSLIC3R_ROOT}/VariableWidth.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arachne/WallToolPaths.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arachne/SkeletalTrapezoidation.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arachne/SkeletalTrapezoidationGraph.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arachne/BeadingStrategy/BeadingStrategy.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arachne/BeadingStrategy/BeadingStrategyFactory.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arachne/BeadingStrategy/DistributedBeadingStrategy.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arachne/BeadingStrategy/LimitedBeadingStrategy.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arachne/BeadingStrategy/OuterWallInsetBeadingStrategy.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arachne/BeadingStrategy/RedistributeBeadingStrategy.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arachne/BeadingStrategy/WideningBeadingStrategy.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arachne/utils/ExtrusionLine.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arachne/utils/PolylineStitcher.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Arachne/utils/SquareGrid.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Geometry/Voronoi.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Geometry/MedialAxis.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Geometry/VoronoiOffset.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Geometry/VoronoiUtils.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Geometry/VoronoiUtilsCgal.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Geometry/Circle.cpp
    ${ORCA_LIBSLIC3R_ROOT}/BuildVolume.cpp
    ${ORCA_LIBSLIC3R_ROOT}/MinimumSpanningTree.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Support/SupportCommon.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Support/SupportMaterial.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Support/SupportSpotsGenerator.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Support/TreeModelVolumes.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Support/TreeSupport3D.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Support/TreeSupport.cpp
    ${ORCA_LIBSLIC3R_ROOT}/CustomGCode.cpp
    ${ORCA_LIBSLIC3R_ROOT}/calib.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCodeReader.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCodeWriter.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/AdaptivePAInterpolator.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/AdaptivePAProcessor.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/AvoidCrossingPerimeters.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/CoolingBuffer.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/GCodeProcessor.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/PchipInterpolatorHelper.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/PrintExtents.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/PressureEqualizer.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/RetractWhenCrossingPerimeters.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/SeamPlacer.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/SmallAreaInfillFlowCompensator.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/SpiralVase.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/ToolOrdering.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/ToolOrderUtils.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/WipeTower.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/WipeTower2.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ShortEdgeCollapse.cpp
    ${ORCA_LIBSLIC3R_ROOT}/EdgeGrid.cpp
    ${ORCA_LIBSLIC3R_ROOT}/FilamentGroup.cpp
    ${ORCA_LIBSLIC3R_ROOT}/FilamentGroupUtils.cpp
    ${ORCA_LIBSLIC3R_ROOT}/ParameterUtils.cpp
    ${ORCA_LIBSLIC3R_ROOT}/Time.cpp
    ${ORCA_LIBSLIC3R_ROOT}/TriangleSetSampling.cpp
    ${ORCA_LIBSLIC3R_ROOT}/GCode/ThumbnailData.cpp
    # Boost.Nowide compiled source (setenv).
    ${ORCA_ANDROID_BOOST_NOWIDE_CSTDLIB_SRC}
    ${CMAKE_CURRENT_SOURCE_DIR}/android_real_gcodeprocessor_guard.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/android_bbs_3mf_export_support.cpp
    # Remaining Android linker/platform stubs. GCodeProcessor stubs in this file
    # are compiled out when ORCA_ANDROID_REAL_GCODE_PROCESSOR is defined.
    ${CMAKE_CURRENT_SOURCE_DIR}/android_libslic3r_stubs.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/android_libslic3r_model_stubs.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/android_libslic3r_remaining_print_stubs.cpp
)

add_library(orca_android_libslic3r_print_gcode_lib STATIC
    ${ORCA_ANDROID_LIBSLIC3R_PRINT_GCODE_ENGINE_SOURCES}
)
target_link_libraries(orca_android_libslic3r_print_gcode_lib PUBLIC
    orca_android_libslic3r_port_config
    orca_android_boost_headers
    orca_android_boost_filesystem
    orca_android_cereal_headers
    orca_android_libnoise_headers
    orca_android_tbb
    orca_android_tbbmalloc
    orca_android_nlopt
    orca_android_cgal_headers
    orca_android_qhull
    orca_android_glu_libtess
    orca_android_openssl_crypto
    log
)
target_compile_options(orca_android_libslic3r_print_gcode_lib PRIVATE
    $<$<COMPILE_LANGUAGE:CXX>:-w>
    $<$<COMPILE_LANGUAGE:CXX>:-include${CMAKE_CURRENT_SOURCE_DIR}/shims/orca_android_libslic3r_model_preinclude.hpp>
)
target_compile_definitions(orca_android_libslic3r_print_gcode_lib PRIVATE
    ORCA_ANDROID_REAL_CONCENTRIC_INTERNAL
    ORCA_ANDROID_REAL_FUZZY_SKIN
    ORCA_ANDROID_REAL_GCODE_PROCESSOR
    ORCA_ANDROID_REAL_MEDIAL_AXIS
    ORCA_ANDROID_REAL_BBS_3MF
    XML_STATIC
)

if (ORCA_ANDROID_LIBSLIC3R_BUILD_EXPERIMENTAL_TOOLS)
    add_executable(orca_android_libslic3r_print_gcode_probe
        orca_android_libslic3r_print_gcode_probe.cpp
    )
    target_link_libraries(orca_android_libslic3r_print_gcode_probe PRIVATE
        orca_android_libslic3r_print_gcode_lib
        orca_android_libslic3r_port_config
        orca_android_boost_headers
        orca_android_boost_filesystem
        orca_android_cereal_headers
        orca_android_tbb
        orca_android_tbbmalloc
        orca_android_cgal_headers
        orca_android_qhull
        orca_android_openssl_crypto
        log
    )
    target_compile_options(orca_android_libslic3r_print_gcode_probe PRIVATE
        $<$<COMPILE_LANGUAGE:CXX>:-w>
        $<$<COMPILE_LANGUAGE:CXX>:-include${CMAKE_CURRENT_SOURCE_DIR}/shims/orca_android_libslic3r_model_preinclude.hpp>
    )
    target_compile_definitions(orca_android_libslic3r_print_gcode_probe PRIVATE
        ORCA_ANDROID_REAL_CONCENTRIC_INTERNAL
        ORCA_ANDROID_REAL_FUZZY_SKIN
        ORCA_ANDROID_REAL_GCODE_PROCESSOR
        ORCA_ANDROID_REAL_MEDIAL_AXIS
    )
endif()

set_target_properties(orca_android_libslic3r_dependency_subset PROPERTIES
    OUTPUT_NAME "orca_android_libslic3r_dependency_subset"
)

set_target_properties(orca_android_libslic3r_config_subset PROPERTIES
    OUTPUT_NAME "orca_android_libslic3r_config_subset"
)

set_target_properties(orca_android_libslic3r_printconfig_impl_subset PROPERTIES
    OUTPUT_NAME "orca_android_libslic3r_printconfig_impl_subset"
)

set_target_properties(orca_android_libslic3r_config_impl_subset PROPERTIES
    OUTPUT_NAME "orca_android_libslic3r_config_impl_subset"
)

if (ORCA_ANDROID_LIBSLIC3R_BUILD_EXPERIMENTAL_TOOLS)
    set_target_properties(orca_android_libslic3r_model_stl_probe PROPERTIES
        OUTPUT_NAME "orca_android_libslic3r_model_stl_probe"
    )

    set_target_properties(orca_android_libslic3r_config_json_probe PROPERTIES
        OUTPUT_NAME "orca_android_libslic3r_config_json_probe"
    )
endif()

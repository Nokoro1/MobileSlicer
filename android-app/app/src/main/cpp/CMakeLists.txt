cmake_minimum_required(VERSION 3.22.1)
project(orca_engine LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_subdirectory(
    ../../../../../engine-wrapper/orca-android-core
    ${CMAKE_CURRENT_BINARY_DIR}/orca-android-core
)

set(SLIC3R_OPENGL_ES ON CACHE BOOL "Build libvgcode for OpenGL ES" FORCE)
add_subdirectory(
    ../../../../../vendor/orcaslicer/src/libvgcode
    ${CMAKE_CURRENT_BINARY_DIR}/libvgcode
)
target_compile_definitions(libvgcode PUBLIC GLAD_GLES2_USE_SYSTEM_EGL)
target_compile_options(libvgcode PRIVATE
    $<$<COMPILE_LANGUAGE:CXX>:-O3>
    $<$<COMPILE_LANGUAGE:CXX>:-fomit-frame-pointer>
)

set(ORCA_SHIPPING_REAL_WRAPPER "OFF")
set(ORCA_ENGINE_WRAPPER_SOURCE "")
set(ORCA_ENGINE_WRAPPER_EXTRA_SOURCES)

if (NOT DEFINED ORCA_SHIPPING_USE_REAL_LIBSLIC3R)
    option(ORCA_SHIPPING_USE_REAL_LIBSLIC3R "Use the real libslic3r wrapper in shipping JNI library" ON)
endif()
option(ORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R "Fail the build if the real libslic3r wrapper is unavailable" ON)
option(ORCA_SHIPPING_ALLOW_REDUCED_WRAPPER "Allow the reduced wrapper for explicit non-shipping development probes" OFF)
set(ORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R_ABIS "" CACHE STRING "Android ABI list that must use the real libslic3r wrapper")
set(ORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R_FOR_ABI "${ORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R}")

if (ORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R_ABIS)
    list(FIND ORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R_ABIS "${ANDROID_ABI}" ORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R_ABI_INDEX)
    if (NOT ORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R_ABI_INDEX EQUAL -1)
        set(ORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R_FOR_ABI ON)
    endif()
endif()

if (ORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R_FOR_ABI AND NOT ORCA_SHIPPING_USE_REAL_LIBSLIC3R)
    message(FATAL_ERROR "The real libslic3r wrapper is required for ${ANDROID_ABI}, but ORCA_SHIPPING_USE_REAL_LIBSLIC3R is OFF.")
endif()

if (ORCA_SHIPPING_USE_REAL_LIBSLIC3R)
    if (NOT ORCA_ANDROID_ABI_SUFFIX)
        set(ORCA_ANDROID_ABI_SUFFIX "x86_64")
        if (ANDROID_ABI STREQUAL "arm64-v8a")
            set(ORCA_ANDROID_ABI_SUFFIX "arm64")
        elseif (ANDROID_ABI STREQUAL "x86_64")
            set(ORCA_ANDROID_ABI_SUFFIX "x86_64")
        endif()
    endif()

    set(ORCA_ANDROID_DEPS_ROOT "/tmp/orca-deps-install" CACHE PATH "Root directory for staged Android native dependencies")
    set(ORCA_ANDROID_DEPS_SRC_ROOT "/tmp/orca-deps-src" CACHE PATH "Root directory for staged Android native dependency sources")
    set(ORCA_ANDROID_LIBSLIC3R_DEPS_PREFIX "${ORCA_ANDROID_DEPS_ROOT}/${ORCA_ANDROID_ABI_SUFFIX}")
    set(ORCA_ANDROID_BOOST_PREFIX "${ORCA_ANDROID_LIBSLIC3R_DEPS_PREFIX}-boost-cgal" CACHE PATH "Android Boost binary prefix")
    set(ORCA_ANDROID_BOOST_HEADERS_DIR "${ORCA_ANDROID_LIBSLIC3R_DEPS_PREFIX}-boost-headers/include" CACHE PATH "Android Boost headers root")
    set(ORCA_ANDROID_TBB_PREFIX "${ORCA_ANDROID_LIBSLIC3R_DEPS_PREFIX}-tbb-static2" CACHE PATH "Android TBB prefix")
    set(ORCA_ANDROID_NLOPT_PREFIX "${ORCA_ANDROID_LIBSLIC3R_DEPS_PREFIX}-nlopt" CACHE PATH "Android NLopt prefix")
    set(ORCA_ANDROID_GMP_PREFIX "${ORCA_ANDROID_LIBSLIC3R_DEPS_PREFIX}-gmp" CACHE PATH "Android GMP prefix")
    set(ORCA_ANDROID_MPFR_PREFIX "${ORCA_ANDROID_LIBSLIC3R_DEPS_PREFIX}-mpfr" CACHE PATH "Android MPFR prefix")
    set(ORCA_ANDROID_CGAL_PREFIX "${ORCA_ANDROID_DEPS_ROOT}/${ORCA_ANDROID_ABI_SUFFIX}-cgal" CACHE PATH "Android CGAL prefix")
    set(ORCA_ANDROID_JPEG_PREFIX "${ORCA_ANDROID_DEPS_ROOT}/${ORCA_ANDROID_ABI_SUFFIX}-jpeg" CACHE PATH "Android libjpeg-turbo prefix")
    set(ORCA_ANDROID_OCCT_PREFIX "" CACHE PATH "Android OCCT prefix for STEP import")
    set(ORCA_ANDROID_DEFAULT_OCCT_PREFIX "${ORCA_ANDROID_DEPS_ROOT}/${ORCA_ANDROID_ABI_SUFFIX}-occt")
    if (NOT ORCA_ANDROID_OCCT_PREFIX AND EXISTS "${ORCA_ANDROID_DEFAULT_OCCT_PREFIX}/lib/cmake/occt")
        set(ORCA_ANDROID_OCCT_PREFIX "${ORCA_ANDROID_DEFAULT_OCCT_PREFIX}" CACHE PATH "Android OCCT prefix for STEP import" FORCE)
    endif()
    set(ORCA_ANDROID_OPENSSL_INCLUDE_DIR "${ORCA_ANDROID_DEPS_ROOT}/${ORCA_ANDROID_ABI_SUFFIX}-openssl/include" CACHE PATH "OpenSSL include root")
    set(ORCA_ANDROID_OPENSSL_CRYPTO_LIBRARY "${ORCA_ANDROID_DEPS_ROOT}/${ORCA_ANDROID_ABI_SUFFIX}-openssl/lib/libcrypto.a" CACHE FILEPATH "OpenSSL crypto library")
    set(ORCA_ANDROID_OPENSSL_SSL_LIBRARY "${ORCA_ANDROID_DEPS_ROOT}/${ORCA_ANDROID_ABI_SUFFIX}-openssl/lib/libssl.a" CACHE FILEPATH "OpenSSL ssl library")
    set(ORCA_ANDROID_BOOST_SRC_ROOT "${ORCA_ANDROID_DEPS_SRC_ROOT}/boost-1.84.0" CACHE PATH "Boost source root")

    if (EXISTS "${ORCA_ANDROID_BOOST_PREFIX}/lib/libboost_filesystem.a"
            AND EXISTS "${ORCA_ANDROID_TBB_PREFIX}/lib/libtbb.a"
            AND EXISTS "${ORCA_ANDROID_NLOPT_PREFIX}/lib/libnlopt.a"
            AND EXISTS "${ORCA_ANDROID_OPENSSL_CRYPTO_LIBRARY}"
            AND EXISTS "${ORCA_ANDROID_OPENSSL_SSL_LIBRARY}"
            AND EXISTS "${ORCA_ANDROID_CGAL_PREFIX}/include"
            AND EXISTS "${ORCA_ANDROID_JPEG_PREFIX}/lib/libjpeg.a"
            AND EXISTS "${ORCA_ANDROID_BOOST_SRC_ROOT}")
        set(ORCA_ANDROID_LIBSLIC3R_ENABLE ON CACHE BOOL "Enable the experimental Android libslic3r path" FORCE)
        set(ORCA_ANDROID_BOOST_PREFIX "${ORCA_ANDROID_BOOST_PREFIX}" CACHE PATH "Android Boost prefix" FORCE)
        set(ORCA_ANDROID_BOOST_HEADERS_DIR "${ORCA_ANDROID_BOOST_HEADERS_DIR}" CACHE PATH "Android Boost headers root" FORCE)
        set(ORCA_ANDROID_TBB_PREFIX "${ORCA_ANDROID_TBB_PREFIX}" CACHE PATH "Android TBB prefix" FORCE)
        set(ORCA_ANDROID_NLOPT_PREFIX "${ORCA_ANDROID_NLOPT_PREFIX}" CACHE PATH "Android NLopt prefix" FORCE)
        set(ORCA_ANDROID_GMP_PREFIX "${ORCA_ANDROID_GMP_PREFIX}" CACHE PATH "Android GMP prefix" FORCE)
        set(ORCA_ANDROID_MPFR_PREFIX "${ORCA_ANDROID_MPFR_PREFIX}" CACHE PATH "Android MPFR prefix" FORCE)
        set(ORCA_ANDROID_CGAL_PREFIX "${ORCA_ANDROID_CGAL_PREFIX}" CACHE PATH "Android CGAL prefix" FORCE)
        set(ORCA_ANDROID_JPEG_PREFIX "${ORCA_ANDROID_JPEG_PREFIX}" CACHE PATH "Android libjpeg-turbo prefix" FORCE)
        set(ORCA_ANDROID_OPENSSL_INCLUDE_DIR "${ORCA_ANDROID_OPENSSL_INCLUDE_DIR}" CACHE PATH "OpenSSL include root" FORCE)
        set(ORCA_ANDROID_OPENSSL_CRYPTO_LIBRARY "${ORCA_ANDROID_OPENSSL_CRYPTO_LIBRARY}" CACHE FILEPATH "OpenSSL crypto library" FORCE)
        set(ORCA_ANDROID_OPENSSL_SSL_LIBRARY "${ORCA_ANDROID_OPENSSL_SSL_LIBRARY}" CACHE FILEPATH "OpenSSL ssl library" FORCE)
        set(ORCA_ANDROID_BOOST_SRC_ROOT "${ORCA_ANDROID_BOOST_SRC_ROOT}" CACHE PATH "Boost source root" FORCE)
        set(ORCA_ANDROID_LIBSLIC3R_BUILD_EXPERIMENTAL_TOOLS OFF CACHE BOOL "Skip experimental probe/lib helper targets for shipping integration boundary" FORCE)

        add_subdirectory(
            ../../../../../engine-wrapper/orca-android-libslic3r
            ${CMAKE_CURRENT_BINARY_DIR}/orca-android-libslic3r
        )

        if (TARGET orca_android_libslic3r_print_gcode_lib)
            set(ORCA_ENGINE_WRAPPER_SOURCE ../../../../../engine-wrapper/orca_wrapper.cpp)
            set(ORCA_ENGINE_WRAPPER_EXTRA_SOURCES
                ../../../../../engine-wrapper/orca_wrapper_calibration.cpp
                ../../../../../engine-wrapper/orca_wrapper_gcode.cpp
                ../../../../../engine-wrapper/orca_wrapper_model_overrides.cpp
                ../../../../../engine-wrapper/orca_native_paint.cpp
                ../../../../../engine-wrapper/orca_wrapper_internal.cpp
                ../../../../../engine-wrapper/orca_wrapper_paint_cut_api.cpp
                ../../../../../engine-wrapper/orca_wrapper_planning_api.cpp
                ../../../../../engine-wrapper/orca_wrapper_preview_api.cpp
                ../../../../../engine-wrapper/orca_wrapper_printable_validation.cpp
                ../../../../../engine-wrapper/orca_wrapper_runtime.cpp
                ../../../../../engine-wrapper/orca_wrapper_slice_export_api.cpp
                ../../../../../engine-wrapper/orca_wrapper_utils.cpp
            )
            set(ORCA_SHIPPING_REAL_WRAPPER "ON")
            option(MOBILE_SLICER_SUPPRESS_VENDOR_WARNING_NOISE "Suppress known third-party Eigen/admesh/libslic3r warning noise from the Orca wrapper build" ON)
            if (MOBILE_SLICER_SUPPRESS_VENDOR_WARNING_NOISE)
                target_compile_options(orca_android_libslic3r_print_gcode_lib PRIVATE
                    $<$<COMPILE_LANGUAGE:CXX>:-Wno-deprecated-declarations>
                    $<$<COMPILE_LANGUAGE:CXX>:-Wno-defaulted-function-deleted>
                    $<$<COMPILE_LANGUAGE:CXX>:-Wno-inconsistent-missing-override>
                )
            endif()
        else()
            if (ORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R_FOR_ABI)
                message(FATAL_ERROR "Real libslic3r wrapper target unavailable for ${ANDROID_ABI}.")
            else()
                message(STATUS "Real libslic3r wrapper target unavailable; falling back to reduced backend wrapper.")
            endif()
        endif()
    else()
        if (ORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R_FOR_ABI)
            message(FATAL_ERROR "Missing staged Android experimental libs required for the real libslic3r wrapper on ${ANDROID_ABI}. Run engine-wrapper/orca-android-libslic3r/build-android-deps.sh or provide ORCA_ANDROID_DEPS_ROOT/ORCA_ANDROID_DEPS_SRC_ROOT.")
        else()
            message(STATUS "Missing staged Android experimental libs; falling back to reduced backend wrapper.")
        endif()
    endif()
endif()

if (NOT ORCA_SHIPPING_REAL_WRAPPER)
    if (ORCA_SHIPPING_ALLOW_REDUCED_WRAPPER AND NOT ORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R_FOR_ABI)
        message(WARNING "Building ${ANDROID_ABI} with the reduced backend wrapper. This is only allowed for explicit non-shipping development probes.")
        set(ORCA_ENGINE_WRAPPER_SOURCE ../../../../../engine-wrapper/orca_wrapper_android.cpp)
    else()
        message(FATAL_ERROR "Refusing to build ${ANDROID_ABI} with the reduced backend wrapper. Stage real Android libslic3r dependencies or pass -DORCA_SHIPPING_ALLOW_REDUCED_WRAPPER=ON with -DORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R=OFF for an explicit non-shipping probe.")
    endif()
endif()

add_library(orca_engine SHARED
    jni_bridge.cpp
    ${ORCA_ENGINE_WRAPPER_SOURCE}
    ${ORCA_ENGINE_WRAPPER_EXTRA_SOURCES}
)

if (ORCA_SHIPPING_REAL_WRAPPER)
    target_sources(orca_engine PRIVATE
        getrandom_compat.cpp
    )
    target_compile_definitions(orca_engine PRIVATE MOBILE_SLICER_ENABLE_VGCODE)
    target_compile_options(orca_engine PRIVATE
        $<$<COMPILE_LANGUAGE:CXX>:-O3>
        $<$<COMPILE_LANGUAGE:CXX>:-fomit-frame-pointer>
        $<$<COMPILE_LANGUAGE:CXX>:-include${CMAKE_CURRENT_LIST_DIR}/../../../../../engine-wrapper/orca-android-libslic3r/shims/orca_android_libslic3r_model_preinclude.hpp>
    )
    target_link_options(orca_engine PRIVATE -Wl,--gc-sections)
    if (MOBILE_SLICER_SUPPRESS_VENDOR_WARNING_NOISE)
        target_compile_options(orca_engine PRIVATE
            $<$<COMPILE_LANGUAGE:CXX>:-Wno-deprecated-declarations>
            $<$<COMPILE_LANGUAGE:CXX>:-Wno-deprecated>
            $<$<COMPILE_LANGUAGE:CXX>:-Wno-defaulted-function-deleted>
            $<$<COMPILE_LANGUAGE:CXX>:-Wno-inconsistent-missing-override>
        )
    endif()
    target_include_directories(orca_engine PRIVATE
        ../../../../../vendor/orcaslicer/deps_src/libnest2d/include
    )
endif()

target_include_directories(orca_engine PRIVATE
    ../../../../../engine-wrapper
    ../../../../../engine-wrapper/orca-android-libslic3r
    ../../../../../engine-wrapper/orca-android-libslic3r/shims
    ../../../../../vendor/orcaslicer/src
    ../../../../../vendor/orcaslicer/deps_src
    ../../../../../engine-wrapper/orca-android-libslic3r/../..//vendor/orcaslicer/deps_src/qhull/src
)

find_library(log-lib log)
find_library(gles-lib GLESv3)
find_library(egl-lib EGL)

target_link_libraries(orca_engine
    orca_core_android
    $<$<BOOL:${ORCA_SHIPPING_REAL_WRAPPER}>:orca_android_libslic3r_print_gcode_lib>
    $<$<BOOL:${ORCA_SHIPPING_REAL_WRAPPER}>:libvgcode>
    ${gles-lib}
    ${egl-lib}
    ${log-lib}
)

file(GLOB APRILTAG_COMMON_SOURCES
    third_party/apriltag/common/*.c
)

add_library(scanner_apriltag_core STATIC
    third_party/apriltag/apriltag.c
    third_party/apriltag/apriltag_pose.c
    third_party/apriltag/apriltag_quad_thresh.c
    third_party/apriltag/tag36h11.c
    ${APRILTAG_COMMON_SOURCES}
)

target_include_directories(scanner_apriltag_core PUBLIC
    third_party/apriltag
)

set_property(TARGET scanner_apriltag_core PROPERTY POSITION_INDEPENDENT_CODE ON)

add_library(scanner_apriltag SHARED
    scanner_apriltag_stub.cpp
)

target_include_directories(scanner_apriltag PRIVATE
    third_party/apriltag
)

target_link_libraries(scanner_apriltag
    scanner_apriltag_core
    ${log-lib}
)

add_library(scanner_pose_solver SHARED
    scanner_pose_solver_stub.cpp
)

target_link_libraries(scanner_pose_solver
    ${log-lib}
)

add_library(scanner_pose_optimizer SHARED
    scanner_pose_optimizer_stub.cpp
)

target_include_directories(scanner_pose_optimizer PRIVATE
    ../../../../../vendor/orcaslicer/deps_src
    ../../../../../vendor/orcaslicer/deps_src/eigen
)

target_compile_options(scanner_pose_optimizer PRIVATE
    $<$<COMPILE_LANGUAGE:CXX>:-Wno-deprecated-declarations>
)

target_link_libraries(scanner_pose_optimizer
    ${log-lib}
)

option(MOBILE_SLICER_BUILD_NATIVE_PAINT_PROBES "Build native paint replay validation executables" OFF)
if (ORCA_SHIPPING_REAL_WRAPPER AND MOBILE_SLICER_BUILD_NATIVE_PAINT_PROBES)
    add_executable(orca_engine_v2_paint_replay_probe
        ../../../../../engine-wrapper/orca_android_v2_paint_replay_probe.cpp
        ${ORCA_ENGINE_WRAPPER_SOURCE}
        ${ORCA_ENGINE_WRAPPER_EXTRA_SOURCES}
        getrandom_compat.cpp
    )
    target_include_directories(orca_engine_v2_paint_replay_probe PRIVATE
        ../../../../../engine-wrapper
        ../../../../../engine-wrapper/orca-android-libslic3r
        ../../../../../engine-wrapper/orca-android-libslic3r/shims
        ../../../../../vendor/orcaslicer/src
        ../../../../../vendor/orcaslicer/deps_src
        ../../../../../vendor/orcaslicer/deps_src/qhull/src
        ../../../../../vendor/orcaslicer/deps_src/libnest2d/include
    )
    target_compile_definitions(orca_engine_v2_paint_replay_probe PRIVATE MOBILE_SLICER_ENABLE_VGCODE)
    target_compile_options(orca_engine_v2_paint_replay_probe PRIVATE
        $<$<COMPILE_LANGUAGE:CXX>:-O3>
        $<$<COMPILE_LANGUAGE:CXX>:-fomit-frame-pointer>
        $<$<COMPILE_LANGUAGE:CXX>:-Wno-deprecated-declarations>
        $<$<COMPILE_LANGUAGE:CXX>:-Wno-deprecated>
        $<$<COMPILE_LANGUAGE:CXX>:-Wno-defaulted-function-deleted>
        $<$<COMPILE_LANGUAGE:CXX>:-Wno-inconsistent-missing-override>
        $<$<COMPILE_LANGUAGE:CXX>:-include${CMAKE_CURRENT_LIST_DIR}/../../../../../engine-wrapper/orca-android-libslic3r/shims/orca_android_libslic3r_model_preinclude.hpp>
    )
    target_link_libraries(orca_engine_v2_paint_replay_probe PRIVATE
        orca_core_android
        orca_android_libslic3r_print_gcode_lib
        libvgcode
        ${gles-lib}
        ${egl-lib}
        ${log-lib}
    )

    add_executable(orca_engine_paint_object_bounds_probe
        ../../../../../engine-wrapper/orca_android_paint_object_bounds_probe.cpp
        ${ORCA_ENGINE_WRAPPER_SOURCE}
        ${ORCA_ENGINE_WRAPPER_EXTRA_SOURCES}
        getrandom_compat.cpp
    )
    target_include_directories(orca_engine_paint_object_bounds_probe PRIVATE
        ../../../../../engine-wrapper
        ../../../../../engine-wrapper/orca-android-libslic3r
        ../../../../../engine-wrapper/orca-android-libslic3r/shims
        ../../../../../vendor/orcaslicer/src
        ../../../../../vendor/orcaslicer/deps_src
        ../../../../../vendor/orcaslicer/deps_src/qhull/src
        ../../../../../vendor/orcaslicer/deps_src/libnest2d/include
    )
    target_compile_definitions(orca_engine_paint_object_bounds_probe PRIVATE MOBILE_SLICER_ENABLE_VGCODE)
    target_compile_options(orca_engine_paint_object_bounds_probe PRIVATE
        $<$<COMPILE_LANGUAGE:CXX>:-O3>
        $<$<COMPILE_LANGUAGE:CXX>:-fomit-frame-pointer>
        $<$<COMPILE_LANGUAGE:CXX>:-Wno-deprecated-declarations>
        $<$<COMPILE_LANGUAGE:CXX>:-Wno-deprecated>
        $<$<COMPILE_LANGUAGE:CXX>:-Wno-defaulted-function-deleted>
        $<$<COMPILE_LANGUAGE:CXX>:-Wno-inconsistent-missing-override>
        $<$<COMPILE_LANGUAGE:CXX>:-include${CMAKE_CURRENT_LIST_DIR}/../../../../../engine-wrapper/orca-android-libslic3r/shims/orca_android_libslic3r_model_preinclude.hpp>
    )
    target_link_libraries(orca_engine_paint_object_bounds_probe PRIVATE
        orca_core_android
        orca_android_libslic3r_print_gcode_lib
        libvgcode
        ${gles-lib}
        ${egl-lib}
        ${log-lib}
    )
endif()

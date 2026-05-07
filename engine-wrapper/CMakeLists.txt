add_library(orca_wrapper STATIC
    orca_wrapper.cpp
    orca_wrapper_calibration.cpp
    orca_wrapper_gcode.cpp
    orca_wrapper_model_overrides.cpp
    orca_native_paint.cpp
    orca_wrapper_internal.cpp
    orca_wrapper_paint_cut_api.cpp
    orca_wrapper_planning_api.cpp
    orca_wrapper_preview_api.cpp
    orca_wrapper_printable_validation.cpp
    orca_wrapper_runtime.cpp
    orca_wrapper_slice_export_api.cpp
    orca_wrapper_utils.cpp
)

target_include_directories(orca_wrapper
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
    PRIVATE
        ${CMAKE_SOURCE_DIR}/vendor/orcaslicer/src
)

target_link_libraries(orca_wrapper
    PRIVATE
        libslic3r
)

add_executable(orca_wrapper_test
    test_main.cpp
)

target_link_libraries(orca_wrapper_test
    PRIVATE
        orca_wrapper
)

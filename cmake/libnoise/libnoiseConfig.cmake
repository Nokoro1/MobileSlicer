if(NOT TARGET noise::noise)
    add_library(noise::noise INTERFACE IMPORTED)
    set_target_properties(noise::noise PROPERTIES
        INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_CURRENT_LIST_DIR}/../../third_party/libnoise_compat"
    )
endif()

set(libnoise_FOUND TRUE)
set(libnoise_LIB_FOUND TRUE)
set(LIBNOISE_INCLUDE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../third_party/libnoise_compat")

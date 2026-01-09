cmake_minimum_required(VERSION 3.22.1)

project("nekospeak")

# Log library
find_library(
        log-lib
        log
)

include_directories(include)

add_library(
        nekospeak
        SHARED
        espeak_wrapper.c
)



target_link_libraries(
        nekospeak
        # espeak-ng # Can we link directly if it's in jniLibs?
        # Usually need import target or just pass -lespeak-ng
        ${log-lib}
)

# For now, we will use dlopen in wrapper or try to link at build time if .so is available
# Preferred: Link at build time. We need to Import libttsespeak.so (which contains espeak-ng)
add_library(libespeak-ng SHARED IMPORTED)
set_target_properties(libespeak-ng PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libttsespeak.so)

target_link_libraries(
        nekospeak
        libespeak-ng
        ${log-lib}
)

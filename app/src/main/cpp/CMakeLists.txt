cmake_minimum_required(VERSION 3.22.1)

project("dualdex_native" C)

set(NATIVE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../native")

add_definitions(
    -D_GNU_SOURCE
    -DCONFIG_VERSION="2024-01-13"
    -DCONFIG_BIGNUM
)

include_directories(
    "${NATIVE_ROOT}/include"
    "${NATIVE_ROOT}/src"
    "${NATIVE_ROOT}/quickjs"
)

set(RCHEEVOS_ROOT "${NATIVE_ROOT}/rcheevos")
file(GLOB RCHEEVOS_SOURCES CONFIGURE_DEPENDS
    "${RCHEEVOS_ROOT}/src/*.c"
    "${RCHEEVOS_ROOT}/src/rapi/*.c"
    "${RCHEEVOS_ROOT}/src/rcheevos/*.c"
    "${RCHEEVOS_ROOT}/src/rhash/*.c"
)
add_library(rcheevos STATIC ${RCHEEVOS_SOURCES})
target_include_directories(rcheevos PUBLIC "${RCHEEVOS_ROOT}/include")
target_compile_definitions(rcheevos PRIVATE RC_CLIENT_SUPPORTS_HASH)
target_compile_options(rcheevos PRIVATE -Wno-unused-parameter)
set_target_properties(rcheevos PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(
    dualdex_native
    SHARED
    dualdex_jni.c
    "${NATIVE_ROOT}/src/pokemon_reader.c"
    "${NATIVE_ROOT}/src/pokemon_text.c"
    "${NATIVE_ROOT}/src/gba_memory_map.c"
    "${NATIVE_ROOT}/src/libretro_host.c"
    "${NATIVE_ROOT}/src/ra_bridge.c"
    "${NATIVE_ROOT}/src/js_calc_engine.c"
    "${NATIVE_ROOT}/quickjs/quickjs.c"
    "${NATIVE_ROOT}/quickjs/libregexp.c"
    "${NATIVE_ROOT}/quickjs/libunicode.c"
    "${NATIVE_ROOT}/quickjs/dtoa.c"
)

target_compile_definitions(
    dualdex_native
    PRIVATE
    DUALDEX_WITH_RCHEEVOS
)

target_compile_options(
    dualdex_native
    PRIVATE
    -fno-strict-aliasing
)

find_library(log-lib log)

target_link_libraries(
    dualdex_native
    rcheevos
    ${log-lib}
    dl
    m
)

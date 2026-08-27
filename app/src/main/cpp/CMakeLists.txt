cmake_minimum_required(VERSION 3.22)
project(ovpncli_android CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT OPENVPN3_ROOT)
    message(FATAL_ERROR "OPENVPN3_ROOT is required")
endif()
if(NOT DEPS_PREFIX)
    message(FATAL_ERROR "DEPS_PREFIX is required")
endif()
if(NOT ASIO_INCLUDE_DIR)
    message(FATAL_ERROR "ASIO_INCLUDE_DIR is required")
endif()

add_library(ovpncli SHARED
    ovpncli_jni.cpp
    ${OPENVPN3_ROOT}/client/ovpncli.cpp
    ${OPENVPN3_ROOT}/openvpn/crypto/data_epoch.cpp
)

target_include_directories(ovpncli PRIVATE
    ${OPENVPN3_ROOT}
    ${OPENVPN3_ROOT}/client
    ${ASIO_INCLUDE_DIR}
    ${DEPS_PREFIX}/include
)

target_compile_definitions(ovpncli PRIVATE
    ASIO_STANDALONE
    USE_ASIO
    USE_OPENSSL
    HAVE_LZ4
)

find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(ovpncli
    ${DEPS_PREFIX}/lib/libssl.a
    ${DEPS_PREFIX}/lib/libcrypto.a
    ${DEPS_PREFIX}/lib/liblz4.a
    ${DEPS_PREFIX}/lib/libfmt.a
    ${log-lib}
    ${dl-lib}
)

target_compile_options(ovpncli PRIVATE
    -fvisibility=hidden
    -fexceptions
    -frtti
    -Wall
    -Wno-unused-parameter
    -Wno-sign-compare
)

set_target_properties(ovpncli PROPERTIES
    OUTPUT_NAME ovpncli
)

cmake_minimum_required(VERSION 4.1.2)
project(whisper_jni LANGUAGES C CXX)
include(FetchContent)

if (NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif ()

if (${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    set(IS_DEBUG TRUE)
    set(IS_RELEASE FALSE)
else ()
    set(IS_DEBUG FALSE)
    set(IS_RELEASE TRUE)
endif ()

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS ${IS_DEBUG} CACHE BOOL "" FORCE)

set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_STATIC ${IS_RELEASE} CACHE BOOL "" FORCE)
set(GGML_LTO ${IS_RELEASE} CACHE BOOL "" FORCE)

if (CMAKE_ANDROID_ARCH_ABI MATCHES "arm64")
    set(GGML_VULKAN ON CACHE BOOL "Enable Vulkan" FORCE)
    FetchContent_Declare(vk_hpp GIT_REPOSITORY https://github.com/KhronosGroup/Vulkan-Hpp
            GIT_TAG v1.3.275
    )
    FetchContent_MakeAvailable(vk_hpp)
    include_directories(${vk_hpp_SOURCE_DIR})
    set(Vulkan_INCLUDE_DIR "${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include")
    set(Vulkan_GLSLC_EXECUTABLE "${ANDROID_NDK}/shader-tools/linux-x86_64/glslc")
    find_library(Vulkan_LIBRARY vulkan)
endif ()

find_library(LOG_LIB log)

FetchContent_Declare(
        whispercpp
        GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
        GIT_TAG master # v1.8.2
)
FetchContent_MakeAvailable(whispercpp)

add_library(whisper_jni SHARED whisper_jni.cpp)
target_link_libraries(whisper_jni PRIVATE whisper ${LOG_LIB})
target_include_directories(whisper_jni PRIVATE ${whispercpp_SOURCE_DIR})

if (CMAKE_ANDROID_ARCH_ABI MATCHES "arm64")
    target_compile_options(whisper_jni PRIVATE -march=armv8.2-a+fp16+simd+dotprod)
    target_link_libraries(whisper_jni PRIVATE ${VULKAN_LIB})
elseif (CMAKE_ANDROID_ARCH_ABI MATCHES "x86")
    set(GGML_NATIVE ON CACHE BOOL "" FORCE)
endif ()

if (IS_DEBUG)
    target_compile_options(whisper_jni PRIVATE -g -O0 -fno-omit-frame-pointer)
else ()
    target_compile_options(whisper_jni PRIVATE -O3 -ffast-math -flto)
    target_compile_options(whisper_jni PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
    target_compile_options(whisper_jni PRIVATE -ffunction-sections -fdata-sections)

    target_link_options(whisper_jni PRIVATE -flto)
    target_link_options(whisper_jni PRIVATE -Wl,--gc-sections)
    target_link_options(whisper_jni PRIVATE -Wl,--exclude-libs,ALL)
endif ()

cmake_minimum_required(VERSION 4.1.2)
project(whisper_jni LANGUAGES C CXX)

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

include(FetchContent)
find_library(LOG_LIB log)

FetchContent_Declare(
        whispercpp
        GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
        GIT_TAG master # v1.8.2
)
FetchContent_MakeAvailable(whispercpp)

add_library(whisper_jni SHARED whisper_jni.cpp)

if (CMAKE_ANDROID_ARCH_ABI MATCHES "arm64")
    target_compile_options(whisper_jni PRIVATE -march=armv8.2-a+fp16)
elseif (CMAKE_ANDROID_ARCH_ABI MATCHES "x86")
    set(GGML_NATIVE ON CACHE BOOL "" FORCE)
endif ()

target_link_libraries(whisper_jni PRIVATE whisper ${LOG_LIB})
target_include_directories(whisper_jni PRIVATE ${whispercpp_SOURCE_DIR})

if (IS_DEBUG)
    target_compile_options(whisper_jni PRIVATE -g -O0 -fno-omit-frame-pointer)
else ()
    target_compile_options(whisper_jni PRIVATE -O3 -flto)
    target_compile_options(whisper_jni PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
    target_compile_options(whisper_jni PRIVATE -ffunction-sections -fdata-sections)

    target_link_options(whisper_jni PRIVATE -flto)
    target_link_options(whisper_jni PRIVATE -Wl,--gc-sections)
    target_link_options(whisper_jni PRIVATE -Wl,--exclude-libs,ALL)
endif ()

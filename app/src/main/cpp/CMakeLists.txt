cmake_minimum_required(VERSION 3.22.1)
project("adaptiq_native" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Fix 16KB page size crash on Android 15+ for all shared libraries (including MNN)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# ─── MNN Source Root ──────────────────────────────────────────────────
set(MNN_SOURCE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../MNN")

# ─── MNN Build Configuration ─────────────────────────────────────────
# Monolithic build: all MNN code compiled into a single libMNN.so.
# Flags mirror MNN's official project/android/build_64.sh.
set(MNN_SEP_BUILD OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(MNN_LOW_MEMORY ON CACHE BOOL "" FORCE)
set(MNN_SUPPORT_TRANSFORMER_FUSE ON CACHE BOOL "" FORCE)
set(MNN_BUILD_LLM ON CACHE BOOL "" FORCE)
set(MNN_USE_LOGCAT ON CACHE BOOL "" FORCE)

# CRITICAL: Must be ON for Android non-command builds.
# Skips POST_BUILD add_custom_command on OBJECT library targets
# (llm, MNNOpenCV) which CMake forbids for OBJECT libs.
set(MNN_BUILD_FOR_ANDROID_COMMAND ON CACHE BOOL "" FORCE)

# Disable features we don't need for a text-only LLM tutor
set(MNN_BUILD_OPENCV OFF CACHE BOOL "" FORCE)
set(MNN_IMGCODECS OFF CACHE BOOL "" FORCE)
set(MNN_OPENCL OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TEST OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_DEMO OFF CACHE BOOL "" FORCE)
set(MNN_LLM_BUILD_DEMO OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_CONVERTER OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_QUANTTOOLS OFF CACHE BOOL "" FORCE)
set(MNN_EVALUATION OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TRAIN OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_AUDIO OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_DIFFUSION OFF CACHE BOOL "" FORCE)

# Include MNN's root CMakeLists.txt
add_subdirectory(${MNN_SOURCE_ROOT} ${CMAKE_BINARY_DIR}/mnn_build)

# ─── Include Directories ─────────────────────────────────────────────
include_directories("${MNN_SOURCE_ROOT}/include/")
include_directories("${MNN_SOURCE_ROOT}/transformers/llm/engine/include/")

# ─── AdaptIQ JNI Bridge ──────────────────────────────────────────────
add_library(adaptiq_jni SHARED llm_jni.cpp)

target_link_options(adaptiq_jni PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(adaptiq_jni
    MNN
    ${log-lib}
    ${android-lib}
)

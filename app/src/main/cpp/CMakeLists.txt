cmake_minimum_required(VERSION 3.22.1)
project("themeconfig")

# ── 可选编译优化 ──
# set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mllvm -fla -mllvm -sub -mllvm -bcf -mllvm -sobf")

set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O2 -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections -DNDEBUG")
set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -Wl,--gc-sections -Wl,--strip-all")

add_library(
    themeconfig
    SHARED
    native-lib.cpp
)

target_compile_options(themeconfig PRIVATE
    -Wall
    -Werror
    -fno-exceptions
    -fno-rtti
    -fomit-frame-pointer
)

find_library(log-lib log)
target_link_libraries(themeconfig ${log-lib})

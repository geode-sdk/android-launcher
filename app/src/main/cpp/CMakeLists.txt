cmake_minimum_required(VERSION 4.1.2)

project(launcherfix)

add_library(launcherfix SHARED launcher-fix.cpp patcher.cpp)

target_compile_features(launcherfix PUBLIC cxx_std_20)
set_target_properties(launcherfix PROPERTIES CXX_EXTENSIONS OFF)

set_target_properties(launcherfix PROPERTIES
    INTERPROCEDURAL_OPTIMIZATION ON
    UNITY_BUILD ON
    C_VISIBILITY_PRESET hidden
    CXX_VISIBILITY_PRESET hidden
)

target_link_libraries(launcherfix PRIVATE log)

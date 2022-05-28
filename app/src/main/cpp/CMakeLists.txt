cmake_minimum_required(VERSION 3.18.1)

project(launcherfix)

add_library(launcherfix SHARED launcher-fix.cpp)

find_library(log-lib log)
target_link_libraries(launcherfix ${log-lib})

include(FetchContent)

FetchContent_Declare(dobby
        GIT_REPOSITORY https://github.com/jmpews/Dobby
        GIT_TAG 223aabced0431525c7d45196f9409fc505d58ac8)

FetchContent_GetProperties(dobby)
if(NOT dobby_POPULATED)
    FetchContent_Populate(dobby)

    set(DOBBY_DEBUG OFF CACHE INTERNAL "" FORCE)
    set(DOBBY_GENERATE_SHARED OFF CACHE INTERNAL "" FORCE)

    add_subdirectory("${dobby_SOURCE_DIR}" ${dobby_BINARY_DIR} EXCLUDE_FROM_ALL)
    target_include_directories(launcherfix PRIVATE ${dobby_SOURCE_DIR}/include)
    target_link_libraries(launcherfix dobby)
endif()
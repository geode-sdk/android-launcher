cmake_minimum_required(VERSION 3.18.1)

project(launcherfix)

add_library(launcherfix SHARED launcher-fix.cpp)

include(FetchContent)

FetchContent_Declare(dobby
        GIT_REPOSITORY https://github.com/jmpews/Dobby
        GIT_TAG 6a80a61ea062424d1595613058a174c64454fa57)

FetchContent_GetProperties(dobby)
if(NOT dobby_POPULATED)
    FetchContent_Populate(dobby)

    # fixes Dobby build
    # add_compile_definitions(typeof=__typeof__)
    set(DOBBY_DEBUG OFF CACHE INTERNAL "" FORCE)
    set(DOBBY_GENERATE_SHARED OFF CACHE INTERNAL "" FORCE)

    add_subdirectory("${dobby_SOURCE_DIR}" ${dobby_BINARY_DIR} EXCLUDE_FROM_ALL)
    target_include_directories(launcherfix PRIVATE ${dobby_SOURCE_DIR}/include)
    target_link_libraries(launcherfix dobby)
endif()
cmake_minimum_required(VERSION 3.22.1)

project("f6x_udp_engine")

add_library(
    f6x_udp
    SHARED
    udp_engine.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    f6x_udp
    ${log-lib}
)

cmake_minimum_required(VERSION 3.10)
project(capri_stockfish CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

file(GLOB SF_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/stockfish/*.cpp ${CMAKE_CURRENT_SOURCE_DIR}/stockfish/syzygy/*.cpp)

add_library(stockfish SHARED ${SF_SOURCES} sf_jni.cpp)
target_compile_options(stockfish PRIVATE -O3)
target_compile_definitions(stockfish PRIVATE NDEBUG)
if(CMAKE_SIZEOF_VOID_P EQUAL 8)
    target_compile_definitions(stockfish PRIVATE IS_64BIT USE_POPCNT)
endif()
target_link_libraries(stockfish log)

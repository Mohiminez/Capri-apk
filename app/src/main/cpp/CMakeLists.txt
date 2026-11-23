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

# Stockfish 16 with the neural network, 64-bit ARM only (the net needs fast integer SIMD). Other phones keep Stockfish 11.
if(ANDROID_ABI STREQUAL "arm64-v8a" OR NOT ANDROID)
    file(GLOB SF16_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/stockfish16/*.cpp ${CMAKE_CURRENT_SOURCE_DIR}/stockfish16/nnue/*.cpp
         ${CMAKE_CURRENT_SOURCE_DIR}/stockfish16/nnue/features/*.cpp ${CMAKE_CURRENT_SOURCE_DIR}/stockfish16/syzygy/*.cpp)
    add_library(stockfish16 SHARED ${SF16_SOURCES} sf16_jni.cpp)
    target_include_directories(stockfish16 PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
    target_compile_features(stockfish16 PRIVATE cxx_std_17)
    set_target_properties(stockfish16 PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
    target_compile_options(stockfish16 PRIVATE -O3)
    target_compile_definitions(stockfish16 PRIVATE NDEBUG NNUE_EMBEDDING_OFF IS_64BIT USE_POPCNT)
    if(ANDROID)
        target_compile_definitions(stockfish16 PRIVATE USE_NEON=8)
    endif()
    target_link_libraries(stockfish16 log)
endif()

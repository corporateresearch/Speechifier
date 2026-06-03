cmake_minimum_required(VERSION 3.22.1)
project("speechify_g2p")

# Native G2P engines, one shared library with three JNI bridges:
#   Tier A: espeak-ng         -> languages a b e f h i p   (native_g2p.cpp)
#   Tier B: OpenJTalk + UniDic -> language j               (japanese_g2p.cpp)
#   Tier C: cppjieba + pinyin  -> language z               (chinese_g2p.cpp)
#
# The JNI bridges are wired; the actual engines are vendored and linked per the
# TODOs in each .cpp. Until then the bridges report not-ready and the Kotlin
# phonemizers fall back cleanly.

add_library(speechify_g2p SHARED
        native_g2p.cpp       # Tier A — espeak-ng
        japanese_g2p.cpp     # Tier B — OpenJTalk
        chinese_g2p.cpp)     # Tier C — cppjieba

# When vendoring engines, add their sources/includes here, e.g.:
#   add_subdirectory(espeak-ng)   ; target_link_libraries(speechify_g2p espeak-ng)
#   add_subdirectory(openjtalk)   ; target_link_libraries(speechify_g2p openjtalk)
#   target_include_directories(speechify_g2p PRIVATE cppjieba/include)

find_library(log-lib log)
target_link_libraries(speechify_g2p ${log-lib})

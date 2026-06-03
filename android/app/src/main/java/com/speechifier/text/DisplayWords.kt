package com.speechifier.text

/**
 * Split a sentence into display words for layout and tap-to-seek. Used wherever a
 * sentence must be shown before (or instead of) real Kokoro word timings —
 * keeping rendering and timing word counts aligned.
 */
fun String.toDisplayWords(): List<String> =
    trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

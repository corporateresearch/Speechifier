package com.speechifier.tts

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A Kokoro voice's style vectors. Each voice ships as a binary blob of
 * little-endian float32 laid out as [rows, [STYLE_DIM]] — Kokoro selects the row
 * by the token-sequence length (longer inputs use a different style row).
 *
 * VERIFY against the deployed voice files: row count, the [STYLE_DIM] width, and
 * whether the index is `tokenCount` or `tokenCount - 1`.
 */
class VoicePack private constructor(
    private val rows: Array<FloatArray>,
) {
    /** Style row for a phoneme-token count, clamped to the available rows. */
    fun styleFor(tokenCount: Int): FloatArray {
        val idx = tokenCount.coerceIn(0, rows.size - 1)
        return rows[idx]
    }

    companion object {
        const val STYLE_DIM = 256

        /** Load a voice `.bin` (flat little-endian float32) and reshape to rows of [STYLE_DIM]. */
        fun fromFile(file: File): VoicePack {
            val bytes = file.readBytes()
            val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            val total = fb.limit()
            require(total % STYLE_DIM == 0) {
                "Voice file ${file.name}: float count $total not divisible by $STYLE_DIM"
            }
            val rowCount = total / STYLE_DIM
            val rows = Array(rowCount) { r ->
                FloatArray(STYLE_DIM).also { row -> fb.position(r * STYLE_DIM); fb.get(row) }
            }
            return VoicePack(rows)
        }
    }
}

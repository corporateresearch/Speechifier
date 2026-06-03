package com.speechifier.text

/**
 * Port of the server's `backend/pdf_utils.py` segmentation.
 *
 * The reader shows a *reflowed* view: text is pulled from the PDF, grouped into
 * paragraphs (one per layout block) and split into sentences. Sentences are the
 * unit of TTS streaming, so they are capped in length to keep synthesis chunks
 * small and responsive.
 *
 * This is a 1:1 behavioural port — keep it in sync with `pdf_utils.py`.
 */
object Segmenter {

    /**
     * Sentences longer than this are hard-wrapped at a word boundary so a single
     * synthesis call never blocks playback for too long.
     */
    const val MAX_SENTENCE_CHARS = 280

    /** Collapse runs of whitespace. */
    private val WS = Regex("\\s+")

    /**
     * Split after sentence-ending punctuation followed by whitespace and an
     * opening quote/bracket + capital/digit. Kotlin uses lookbehind/lookahead
     * identically to the Python `re` pattern.
     */
    private val SENT_SPLIT = Regex("(?<=[.!?])\\s+(?=[\"'(\\[]?[A-Z0-9])")

    /**
     * Strip characters that render as invisible squares:
     *  - C0 control chars (except newline/tab/space)
     *  - Private Use Area (U+E000–U+F8FF) and Supplementary PUA
     *  - Replacement char, geometric shapes, misc symbols, dingbats
     */
    private val JUNK_CHARS = Regex(
        "[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f" +
            "\\uE000-\\uF8FF" +
            "\\uFFFD" +
            "\\u25A0-\\u25FF" +
            "\\u2600-\\u26FF" +
            "\\u2700-\\u27BF" +
            "\\x{F0000}-\\x{10FFFF}" +
            "]+"
    )

    /** True if the cleaned string has any actual readable content. */
    private val HAS_ALNUM = Regex("[a-zA-Z0-9]")

    /** Join hyphenated line breaks: "exam-\nple" -> "example". */
    private val HYPHEN_BREAK = Regex("-\\n(?=[a-z])")

    /**
     * A reflowed document. [paragraphs] holds, for each paragraph, the indices
     * into [sentences] in reading order — matching the server's response shape.
     */
    data class Document(
        val title: String,
        val sentences: List<String>,
        val paragraphs: List<List<Int>>,
    )

    private fun clean(text: String): String {
        var t = JUNK_CHARS.replace(text, "")
        t = HYPHEN_BREAK.replace(t, "")
        return WS.replace(t, " ").trim()
    }

    /** Split an over-long sentence into <= [MAX_SENTENCE_CHARS] chunks at word boundaries. */
    private fun hardWrap(sentence: String): List<String> {
        if (sentence.length <= MAX_SENTENCE_CHARS) return listOf(sentence)
        val chunks = mutableListOf<String>()
        var cur = StringBuilder()
        for (w in sentence.split(" ")) {
            if (cur.isNotEmpty() && cur.length + 1 + w.length > MAX_SENTENCE_CHARS) {
                chunks.add(cur.toString())
                cur = StringBuilder(w)
            } else {
                if (cur.isEmpty()) cur.append(w) else cur.append(' ').append(w)
            }
        }
        if (cur.isNotEmpty()) chunks.add(cur.toString())
        return chunks
    }

    /** Clean a paragraph/block and split it into capped sentences. */
    fun splitSentences(paragraph: String): List<String> {
        val cleaned = clean(paragraph)
        if (cleaned.isEmpty() || !HAS_ALNUM.containsMatchIn(cleaned)) return emptyList()
        val out = mutableListOf<String>()
        for (part in SENT_SPLIT.split(cleaned)) {
            val p = part.trim()
            if (p.isNotEmpty()) out.addAll(hardWrap(p))
        }
        return out
    }

    /**
     * Build a [Document] from ordered text blocks (one block ≈ one paragraph),
     * as produced by [com.speechifier.pdf.PdfExtractor]. Mirrors
     * `extract_document` in `pdf_utils.py`.
     */
    fun buildDocument(title: String, blocks: List<String>): Document {
        val sentences = mutableListOf<String>()
        val paragraphs = mutableListOf<List<Int>>()
        for (block in blocks) {
            val para = splitSentences(block)
            if (para.isEmpty()) continue
            val start = sentences.size
            sentences.addAll(para)
            paragraphs.add((start until sentences.size).toList())
        }
        return Document(title = title.ifBlank { "Document" }, sentences = sentences, paragraphs = paragraphs)
    }

    /** Derive a display title from a filename when PDF metadata has none. */
    fun deriveTitle(filename: String): String {
        val name = filename
            .replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
        return name.ifEmpty { "Document" }
    }
}

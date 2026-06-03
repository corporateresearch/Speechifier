package com.speechifier.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.speechifier.text.Segmenter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real [PdfReader] backed by PdfBox-Android. On-device equivalent of the server's
 * `extract_document` (PyMuPDF) in `backend/pdf_utils.py`.
 *
 * PdfBox has no direct "layout blocks" API like PyMuPDF, so we extract text
 * per page (sorted by position) and split it into blocks on blank lines — each
 * block ≈ one paragraph, then handed to [Segmenter] for cleaning/sentence
 * splitting. Error messages mirror the server's 400/422 responses.
 */
class PdfExtractor(context: Context) : PdfReader {

    private val appContext = context.applicationContext

    override suspend fun extract(uri: Uri): Segmenter.Document = withContext(Dispatchers.IO) {
        val filename = displayName(uri) ?: "document.pdf"

        val document = try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input)
            } ?: throw PdfException("The uploaded file is empty.")
        } catch (e: PdfException) {
            throw e
        } catch (e: Exception) {
            throw PdfException("Could not read PDF: ${e.message}")
        }

        try {
            val metaTitle = document.documentInformation?.title?.trim().orEmpty()

            val blocks = mutableListOf<String>()
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            for (page in 1..document.numberOfPages) {
                stripper.startPage = page
                stripper.endPage = page
                val pageText = stripper.getText(document)
                // Blank line(s) delimit blocks; keep newlines within a block so the
                // Segmenter's hyphen-at-linebreak join still works.
                for (block in pageText.split(BLOCK_DELIMITER)) {
                    if (block.isNotBlank()) blocks.add(block)
                }
            }

            val title = metaTitle.ifBlank { Segmenter.deriveTitle(filename) }
            val doc = Segmenter.buildDocument(title = title, blocks = blocks)

            if (doc.sentences.isEmpty()) {
                throw PdfException("No extractable text found (the PDF may be scanned images).")
            }
            doc
        } finally {
            document.close()
        }
    }

    private fun displayName(uri: Uri): String? =
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private companion object {
        // One or more blank lines (optionally whitespace-only) separate blocks.
        val BLOCK_DELIMITER = Regex("\\n[ \\t]*\\n")
    }
}

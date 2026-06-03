package com.speechifier

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Application entry point. Initialises PdfBox-Android's resource loader once so
 * font/encoding resources are available for text extraction (see pdf/PdfExtractor).
 */
class SpeechifierApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}

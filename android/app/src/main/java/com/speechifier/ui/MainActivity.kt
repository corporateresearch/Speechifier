package com.speechifier.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

/**
 * Single-activity host. The reader screen (built in the UI milestone) is mounted
 * here; for now it shows the placeholder until ReaderScreen lands.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SpeechifierTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReaderScreen()
                }
            }
        }
    }
}

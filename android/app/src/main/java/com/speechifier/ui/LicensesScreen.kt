package com.speechifier.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One third-party component's attribution. */
private data class Attribution(val name: String, val license: String, val note: String)

private val ATTRIBUTIONS = listOf(
    Attribution("Kokoro-82M", "Apache-2.0", "The on-device text-to-speech model."),
    Attribution("espeak-ng", "GPL-3.0", "Grapheme-to-phoneme for English, Spanish, French, Hindi, Italian, Portuguese. Its copyleft terms apply to this app."),
    Attribution("OpenJTalk + UniDic", "Modified BSD / UniDic terms", "Japanese phonemization and dictionary."),
    Attribution("jieba", "MIT", "Chinese word segmentation."),
    Attribution("pypinyin data", "MIT", "Chinese pinyin lexicon."),
    Attribution("ONNX Runtime", "MIT", "Model inference."),
    Attribution("PdfBox-Android", "Apache-2.0", "PDF text extraction."),
)

/**
 * Attribution / licenses screen. Surfaces the obligations flagged in the plan —
 * notably espeak-ng's GPL — and is reachable from the reader's top bar.
 */
@Composable
fun LicensesScreen(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpeechifierColors.Background)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Open-source licenses",
                color = SpeechifierColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, "Close", tint = SpeechifierColors.TextPrimary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ATTRIBUTIONS.forEach { a ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SpeechifierColors.Surface, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(a.name, color = SpeechifierColors.TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(a.license, color = SpeechifierColors.Accent, fontSize = 13.sp)
                    }
                    Text(a.note, color = SpeechifierColors.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

package com.speechifier.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.speechifier.player.ReaderPhase
import com.speechifier.player.ReaderUiState
import com.speechifier.player.ReaderViewModel
import com.speechifier.player.ReaderViewModelFactory
import com.speechifier.text.toDisplayWords
import com.speechifier.tts.Voices

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val ui by viewModel.ui.collectAsState()
    var showLicenses by remember { mutableStateOf(false) }

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::loadPdf) }

    if (showLicenses) {
        LicensesScreen(onClose = { showLicenses = false })
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(SpeechifierColors.Background)) {
        TopBar(title = ui.title.ifBlank { "Speechifier" }, onInfo = { showLicenses = true })

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (ui.phase) {
                ReaderPhase.Empty -> EmptyState { pickPdf.launch(arrayOf("application/pdf")) }
                ReaderPhase.Loading -> CenterSpinner()
                ReaderPhase.Error -> ErrorState(ui.error) { pickPdf.launch(arrayOf("application/pdf")) }
                ReaderPhase.Ready -> DocumentView(ui, viewModel)
            }
        }

        if (ui.phase == ReaderPhase.Ready) {
            ControlsBar(ui, viewModel) { pickPdf.launch(arrayOf("application/pdf")) }
        }
    }
}

@Composable
private fun TopBar(title: String, onInfo: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
    ) {
        Text(
            text = title,
            color = SpeechifierColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onInfo) {
            Icon(Icons.Filled.Info, "Licenses", tint = SpeechifierColors.TextMuted)
        }
    }
}

@Composable
private fun CenterSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SpeechifierColors.Accent)
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Description, null, tint = SpeechifierColors.Accent, modifier = Modifier.height(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Read any PDF aloud", color = SpeechifierColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Words highlight as they're spoken — fully offline.",
            color = SpeechifierColors.TextMuted,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(onClick = onPick) { Text("Open a PDF") }
    }
}

@Composable
private fun ErrorState(message: String?, onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message ?: "Couldn't read that PDF.",
            color = SpeechifierColors.TextPrimary,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        Button(onClick = onPick) { Text("Try another PDF") }
    }
}

@Composable
private fun DocumentView(ui: ReaderUiState, viewModel: ReaderViewModel) {
    val listState = rememberLazyListState()
    // Sentence indices that begin a paragraph, for extra top spacing.
    val paragraphStarts = remember(ui.paragraphs) {
        ui.paragraphs.mapNotNull { it.firstOrNull() }.toSet()
    }

    // Auto-scroll the current sentence toward the viewport center. (Word-level
    // centering is approximated by sentence-level scrolling.)
    LaunchedEffect(ui.currentSentence) {
        if (ui.currentSentence >= 0) listState.animateScrollToItem(ui.currentSentence)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
    ) {
        itemsIndexed(ui.sentences) { index, sentence ->
            if (index in paragraphStarts && index != 0) Spacer(Modifier.height(16.dp))
            SentenceRow(
                index = index,
                sentence = sentence,
                isCurrent = index == ui.currentSentence,
                activeWordIndex = if (index == ui.currentSentence) ui.activeWordIndex else -1,
                wordTexts = if (index == ui.currentSentence && ui.activeWords.isNotEmpty()) {
                    ui.activeWords.map { it.text }
                } else {
                    sentence.toDisplayWords()
                },
                onWordTap = { wordIdx ->
                    if (index == ui.currentSentence) viewModel.seekToWord(wordIdx)
                    else viewModel.jumpToSentence(index)
                },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SentenceRow(
    index: Int,
    sentence: String,
    isCurrent: Boolean,
    activeWordIndex: Int,
    wordTexts: List<String>,
    onWordTap: (Int) -> Unit,
) {
    val rowBg = if (isCurrent) SpeechifierColors.ActiveSentenceBg else androidx.compose.ui.graphics.Color.Transparent
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg, RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        wordTexts.forEachIndexed { wordIdx, word ->
            val highlighted = isCurrent && wordIdx == activeWordIndex
            Text(
                text = word,
                color = if (highlighted) SpeechifierColors.HighlightText else SpeechifierColors.TextPrimary,
                fontSize = 19.sp,
                modifier = Modifier
                    .clickable { onWordTap(wordIdx) }
                    .background(
                        if (highlighted) SpeechifierColors.HighlightBg else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(5.dp),
                    )
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun ControlsBar(ui: ReaderUiState, viewModel: ReaderViewModel, onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpeechifierColors.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            VoicePicker(ui.voiceId, onSelect = viewModel::setVoice)
            Spacer(Modifier.weight(1f))
            SpeedControl(ui.speed, onChange = viewModel::setSpeed)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::previous) {
                Icon(Icons.Filled.SkipPrevious, "Previous", tint = SpeechifierColors.TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = viewModel::togglePlayPause) {
                Icon(
                    if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (ui.isPlaying) "Pause" else "Play",
                    tint = SpeechifierColors.Accent,
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = viewModel::next) {
                Icon(Icons.Filled.SkipNext, "Next", tint = SpeechifierColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun VoicePicker(currentVoiceId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = Voices.resolve(currentVoiceId)
    Box {
        Button(onClick = { expanded = true }) { Text(current.label, maxLines = 1) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Voices.grouped().forEach { (language, voices) ->
                Text(
                    language,
                    color = SpeechifierColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice.label) },
                        leadingIcon = {
                            if (voice.id == currentVoiceId) Icon(Icons.Filled.Check, null, tint = SpeechifierColors.Accent)
                        },
                        onClick = { onSelect(voice.id); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedControl(speed: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("%.2f×".format(speed), color = SpeechifierColors.TextMuted, fontSize = 13.sp)
        Slider(
            value = speed,
            onValueChange = onChange,
            valueRange = 0.5f..2.0f,
            steps = 5, // 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0
            modifier = Modifier.width(140.dp).padding(start = 8.dp),
        )
    }
}

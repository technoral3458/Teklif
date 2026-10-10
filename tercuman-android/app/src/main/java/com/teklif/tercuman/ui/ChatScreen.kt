package com.teklif.tercuman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teklif.tercuman.R
import com.teklif.tercuman.translate.Lang

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: UiState,
    snackbar: SnackbarHostState,
    vm: MainViewModel,
    onMicPress: (Lang?) -> Unit,
    onMicRelease: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.utterances.size, state.utterances.lastOrNull()?.pending) {
        if (state.utterances.isNotEmpty()) listState.animateScrollToItem(state.utterances.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tercüman  TR ⇄ 中文") },
                actions = {
                    IconButton(onClick = { vm.navigate(Screen.FACE_TO_FACE) }) {
                        Icon(painterResource(R.drawable.ic_face_to_face), contentDescription = "Yüz yüze mod")
                    }
                    IconButton(onClick = vm::clearConversation) {
                        Icon(Icons.Default.Delete, contentDescription = "Konuşmayı temizle")
                    }
                    IconButton(onClick = { vm.navigate(Screen.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.utterances, key = { it.id }) { u ->
                        UtteranceCard(u, onSpeak = { vm.speak(u) }, onRetry = { vm.retry(u) })
                    }
                }
            }

            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Tanınan konuşma canlı görünür; başka açıklama yazısı yok.
                    Text(
                        text = state.partial,
                        modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 28.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 4,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LangMic(Lang.TR, state, onMicPress, onMicRelease)
                        MicButton(
                            listening = state.listening && state.listeningFor == null,
                            level = state.level,
                            size = 44.dp,
                            color = MaterialTheme.colorScheme.primary,
                            onPress = { onMicPress(null) },
                            onRelease = onMicRelease,
                        )
                        LangMic(Lang.ZH, state, onMicPress, onMicRelease)
                    }
                }
            }
        }
    }
}

/** Dil tuşu: başka bir tuş dinlerken soluklaşır. */
@Composable
private fun LangMic(
    lang: Lang,
    state: UiState,
    onMicPress: (Lang?) -> Unit,
    onMicRelease: (Boolean) -> Unit,
) {
    val listeningHere = state.listening && state.listeningFor == lang
    val dimmed = state.listening && !listeningHere
    MicButton(
        listening = listeningHere,
        level = state.level,
        size = 96.dp,
        color = if (dimmed) lang.color().copy(alpha = 0.35f) else lang.color(),
        contentColor = lang.onColor(),
        label = if (lang == Lang.TR) "Türkçe" else "中文",
        onPress = { onMicPress(lang) },
        onRelease = onMicRelease,
    )
}

package com.teklif.tercuman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
    onMic: () -> Unit,
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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Direction.entries.forEach { d ->
                    FilterChip(
                        selected = state.direction == d,
                        onClick = { vm.setDirection(d) },
                        label = { Text(d.label) },
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.utterances.isEmpty()) {
                    Text(
                        text = "Mikrofona dokunun ve konuşun.\n点击麦克风开始说话。\n\n" +
                            "Otomatik modda Türkçe mi Çince mi konuşulduğu kendiliğinden algılanır.",
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    Text(
                        text = when {
                            state.partial.isNotBlank() -> state.partial
                            state.listening -> listeningLabel(state)
                            else -> "Dokun ve konuş · 点击说话"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 3,
                    )
                    MicButton(
                        listening = state.listening,
                        level = state.level,
                        size = 84.dp,
                        color = state.direction.source?.color() ?: MaterialTheme.colorScheme.primary,
                        onClick = onMic,
                    )
                }
            }
        }
    }
}

fun listeningLabel(state: UiState): String = when (state.listeningFor) {
    null -> "Dinliyorum… (Türkçe / 中文 otomatik)"
    Lang.TR -> "Dinliyorum… (Türkçe)"
    Lang.ZH -> "正在听…（中文）"
}

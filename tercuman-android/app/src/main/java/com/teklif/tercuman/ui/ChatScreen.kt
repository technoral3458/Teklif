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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
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
    val t = state.settings.uiLang.strings()
    val listState = rememberLazyListState()
    LaunchedEffect(state.utterances.size, state.utterances.lastOrNull()?.pending) {
        if (state.utterances.isNotEmpty()) listState.animateScrollToItem(state.utterances.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${t.appTitle}  ${state.settings.pair.a.flag} ⇄ ${state.settings.pair.b.flag}") },
                actions = {
                    IconButton(onClick = { vm.navigate(Screen.FACE_TO_FACE) }) {
                        Icon(painterResource(R.drawable.ic_face_to_face), contentDescription = t.faceToFace)
                    }
                    IconButton(onClick = vm::clearConversation) {
                        Icon(Icons.Default.Delete, contentDescription = t.clearChat)
                    }
                    IconButton(onClick = { vm.navigate(Screen.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = t.settings)
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
                        UtteranceCard(
                            utterance = u,
                            pair = state.settings.pair,
                            onSpeak = { vm.speak(u) },
                            onRetry = { vm.retry(u) },
                            onEdit = { text -> vm.editUtterance(u.id, text) },
                        )
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
                        LangMic(Side.A, state, vm, onMicPress, onMicRelease)
                        MicButton(
                            listening = state.listening && state.listeningFor == null,
                            level = state.level,
                            size = 44.dp,
                            color = MaterialTheme.colorScheme.primary,
                            onPress = { onMicPress(null) },
                            onRelease = onMicRelease,
                        )
                        LangMic(Side.B, state, vm, onMicPress, onMicRelease)
                    }
                }
            }
        }
    }
}

/** Dil tuşu ve üstünde bayraklı dil seçici. Başka bir tuş dinlerken soluklaşır. */
@Composable
private fun LangMic(
    side: Side,
    state: UiState,
    vm: MainViewModel,
    onMicPress: (Lang?) -> Unit,
    onMicRelease: (Boolean) -> Unit,
) {
    val pair = state.settings.pair
    val lang = if (side == Side.A) pair.a else pair.b
    val listeningHere = state.listening && state.listeningFor == lang
    val dimmed = state.listening && !listeningHere
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FlagPicker(current = lang, onPick = { vm.setLanguage(side, it) })
        MicButton(
            listening = listeningHere,
            level = state.level,
            size = 96.dp,
            color = if (dimmed) lang.color().copy(alpha = 0.35f) else lang.color(),
            contentColor = lang.onColor(),
            label = lang.displayName,
            onPress = { onMicPress(lang) },
            onRelease = onMicRelease,
        )
    }
}

/** Bayrağa dokununca dört dilin bayraklı listesi açılır. */
@Composable
fun FlagPicker(current: Lang, onPick: (Lang) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            text = "${current.flag} ▾",
            fontSize = 26.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 2.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Lang.entries.forEach { l ->
                DropdownMenuItem(
                    text = { Text("${l.flag}  ${l.displayName}", fontSize = 18.sp) },
                    onClick = {
                        open = false
                        onPick(l)
                    },
                )
            }
        }
    }
}

package com.teklif.tercuman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teklif.tercuman.translate.Lang

/**
 * Yüz yüze mod: telefon masaya konur. Üst yarı Çinli misafire dönük (180° çevrili),
 * alt yarı Türkçe konuşana. Her kişinin kendi mikrofon tuşu var; ortadaki tuş otomatik algılar.
 */
@Composable
fun FaceToFaceScreen(
    state: UiState,
    snackbar: SnackbarHostState,
    vm: MainViewModel,
    onMic: (Lang?) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            PersonPane(
                lang = Lang.ZH,
                state = state,
                onMic = { onMic(Lang.ZH) },
                modifier = Modifier.weight(1f).fillMaxWidth().rotate(180f),
            )
            Surface(tonalElevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { vm.navigate(Screen.CHAT) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Sohbete dön")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MicButton(
                            listening = state.listening && state.listeningFor == null,
                            level = state.level,
                            size = 48.dp,
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { onMic(null) },
                        )
                    }
                    Text(
                        "Otomatik\n自动",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
            PersonPane(
                lang = Lang.TR,
                state = state,
                onMic = { onMic(Lang.TR) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun PersonPane(
    lang: Lang,
    state: UiState,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.utterances.size, state.utterances.lastOrNull()?.pending) {
        if (state.utterances.isNotEmpty()) listState.animateScrollToItem(state.utterances.lastIndex)
    }
    val listeningHere = state.listening && state.listeningFor == lang
    val listeningAuto = state.listening && state.listeningFor == null

    Column(modifier.background(lang.color().copy(alpha = 0.06f))) {
        Text(
            text = if (lang == Lang.TR) "🇹🇷 Türkçe" else "🇨🇳 中文",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = lang.color(),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.utterances, key = { it.id }) { u ->
                val spokenByThisSide = u.source == lang
                val text = u.textIn(lang)
                when {
                    text != null -> Text(
                        text = text,
                        // Karşı tarafın sözünün çevirisi büyük; kendi söylediği soluk.
                        fontSize = if (spokenByThisSide) 16.sp else 24.sp,
                        lineHeight = if (spokenByThisSide) 22.sp else 32.sp,
                        fontWeight = if (spokenByThisSide) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (spokenByThisSide) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        textAlign = if (spokenByThisSide) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    u.error != null -> Text(
                        if (lang == Lang.TR) "Çeviri hatası" else "翻译失败",
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Text(
                        if (lang == Lang.TR) "Çevriliyor…" else "翻译中…",
                        color = Color.Gray,
                    )
                }
            }
        }
        val hint = when {
            listeningHere || (listeningAuto && state.partial.isNotBlank()) -> state.partial.ifBlank {
                if (lang == Lang.TR) "Dinliyorum… bitirmek için tekrar dokunun" else "正在听… 说完后再点一下"
            }
            lang == Lang.TR -> "Konuşmak için dokunun, bitirince tekrar dokunun"
            else -> "点击说话，说完后再点一下"
        }
        Text(
            text = hint,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
            maxLines = 2,
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MicButton(
                listening = listeningHere,
                level = state.level,
                size = 72.dp,
                color = lang.color(),
                contentColor = lang.onColor(),
                label = if (lang == Lang.TR) "Türkçe" else "中文",
                onClick = onMic,
            )
        }
    }
}

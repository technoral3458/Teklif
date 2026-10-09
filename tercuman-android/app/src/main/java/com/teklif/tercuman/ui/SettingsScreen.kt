package com.teklif.tercuman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private val EFFORTS = listOf("low" to "Hızlı", "medium" to "Dengeli", "high" to "En iyi")
private val SILENCES = listOf(1200L to "Kısa (1,2 sn)", 2000L to "Normal (2 sn)", 3000L to "Uzun (3 sn)")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel) {
    val s = state.settings
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = { vm.navigate(Screen.CHAT) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = s.apiKey,
                onValueChange = { v -> vm.updateSettings { it.copy(apiKey = v.trim()) } },
                label = { Text("Claude API anahtarı") },
                placeholder = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text("console.anthropic.com → API Keys. Sadece bu telefonda saklanır.") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Ses tanıma ve otomatik dil algılama", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = s.openAiKey,
                onValueChange = { v -> vm.updateSettings { it.copy(openAiKey = v.trim()) } },
                label = { Text("OpenAI API anahtarı (isteğe bağlı, önerilir)") },
                placeholder = { Text("sk-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = {
                    Text(
                        if (s.openAiKey.isBlank()) {
                            "Boşsa telefonun kendi ses tanıması kullanılır. Doldurursanız Whisper konuşulan dili " +
                                "sesten algılar: aynı kişi art arda konuşsa da doğru dil seçilir. " +
                                "platform.openai.com → API keys."
                        } else {
                            "Whisper etkin: konuşulan dil sesten otomatik algılanır."
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Konuşma bitti sayılacak sessizlik", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SILENCES.forEach { (value, label) ->
                    FilterChip(
                        selected = s.silenceMs == value,
                        onClick = { vm.updateSettings { it.copy(silenceMs = value) } },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                "Mikrofon, konuşma başlayana kadar (en fazla 90 sn) sessizce bekler. Cümle arasında duraklıyorsanız Uzun'u seçin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Çeviri kalitesi / hız", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EFFORTS.forEach { (value, label) ->
                    FilterChip(
                        selected = s.effort == value,
                        onClick = { vm.updateSettings { it.copy(effort = value) } },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                "Hızlı: yüz yüze sohbet için önerilir. En iyi: daha yavaş ama daha özenli.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = s.topic,
                onValueChange = { v -> vm.updateSettings { it.copy(topic = v) } },
                label = { Text("Görüşmenin konusu (anlam yükleme)") },
                placeholder = { Text("Örn: CNC membran kapak üretimi, fiyat teklifi ve teslim süresi görüşmesi") },
                minLines = 2,
                supportingText = { Text("Yapay zekâ terimleri bu bağlama göre seçer.") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = s.glossary,
                onValueChange = { v -> vm.updateSettings { it.copy(glossary = v) } },
                label = { Text("Terim sözlüğü") },
                placeholder = { Text("membran kapak = membrane cover = 薄膜盖板\nteklif = quotation = 报价") },
                minLines = 3,
                supportingText = { Text("Her satıra bir terim: Türkçe = English = 中文") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Çeviriyi sesli oku", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Her çeviri karşı tarafın dilinde otomatik okunur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.autoSpeak, onCheckedChange = { v -> vm.updateSettings { it.copy(autoSpeak = v) } })
            }

            OutlinedTextField(
                value = s.model,
                onValueChange = { v -> vm.updateSettings { it.copy(model = v.trim()) } },
                label = { Text("Model") },
                singleLine = true,
                supportingText = { Text("Varsayılan: claude-opus-5-5") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

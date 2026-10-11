package com.teklif.tercuman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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

private val SILENCES = listOf(0L, 1000L, 2000L, 3000L, 4000L, 5000L)
private val MODELS = listOf(
    "claude-haiku-5-5" to "Haiku",
    "claude-sonnet-5-5" to "Sonnet",
    "claude-opus-5-5" to "Opus",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel) {
    val s = state.settings
    val t = s.uiLang.strings()
    val efforts = listOf("low" to t.qualityFast, "medium" to t.qualityBalanced, "high" to t.qualityBest)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t.settings) },
                navigationIcon = {
                    IconButton(onClick = { vm.navigate(Screen.CHAT) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t.back)
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
            Text(t.uiLangTitle, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UiLang.entries.forEach { ul ->
                    FilterChip(
                        selected = s.uiLang == ul,
                        onClick = { vm.updateSettings { it.copy(uiLang = ul) } },
                        label = { Text("${ul.flag} ${ul.label}") },
                    )
                }
            }

            OutlinedTextField(
                value = s.apiKey,
                onValueChange = { v -> vm.updateSettings { it.copy(apiKey = v.trim()) } },
                label = { Text(t.apiKeyLabel) },
                placeholder = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text(t.apiKeyHelp) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(t.speechSection, style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = s.openAiKey,
                onValueChange = { v -> vm.updateSettings { it.copy(openAiKey = v.trim()) } },
                label = { Text(t.openAiLabel) },
                placeholder = { Text("sk-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text(if (s.openAiKey.isBlank()) t.openAiHelpEmpty else t.openAiHelpSet) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(t.micCloseTitle, style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SILENCES.forEach { value ->
                    FilterChip(
                        selected = s.silenceMs == value,
                        onClick = { vm.updateSettings { it.copy(silenceMs = value) } },
                        label = { Text(if (value == 0L) t.onTap else t.seconds((value / 1000).toInt())) },
                    )
                }
            }
            Help(t.micCloseHelp)

            Text(t.qualityTitle, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                efforts.forEach { (value, label) ->
                    FilterChip(
                        selected = s.effort == value,
                        onClick = { vm.updateSettings { it.copy(effort = value) } },
                        label = { Text(label) },
                    )
                }
            }
            Help(t.qualityHelp)

            OutlinedTextField(
                value = s.topic,
                onValueChange = { v -> vm.updateSettings { it.copy(topic = v) } },
                label = { Text(t.topicLabel) },
                placeholder = { Text(t.topicPlaceholder) },
                minLines = 2,
                supportingText = { Text(t.topicHelp) },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = s.glossary,
                onValueChange = { v -> vm.updateSettings { it.copy(glossary = v) } },
                label = { Text(t.glossaryLabel) },
                placeholder = { Text(t.glossaryPlaceholder) },
                minLines = 3,
                supportingText = { Text(t.glossaryHelp) },
                modifier = Modifier.fillMaxWidth(),
            )

            SwitchRow(t.autoSpeakTitle, t.autoSpeakHelp, s.autoSpeak) { v -> vm.updateSettings { it.copy(autoSpeak = v) } }
            SwitchRow(t.pinyinTitle, t.pinyinHelp, s.pinyin) { v -> vm.updateSettings { it.copy(pinyin = v) } }
            SwitchRow(t.economyTitle, t.economyHelp, s.economy) { v -> vm.updateSettings { it.copy(economy = v) } }

            Text(t.modelTitle, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MODELS.forEach { (value, label) ->
                    FilterChip(
                        selected = s.model == value,
                        onClick = { vm.updateSettings { it.copy(model = value) } },
                        label = { Text(label) },
                    )
                }
            }
            Help(t.modelHelp)
        }
    }
}

@Composable
private fun Help(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Help(help)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

package com.teknoral.parametrik.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknoral.parametrik.data.local.ThemeMode
import com.teknoral.parametrik.ui.common.CollapsibleSection
import com.teknoral.parametrik.ui.common.ErrorBox
import com.teknoral.parametrik.ui.common.SegmentedChoice
import com.teknoral.parametrik.ui.common.SwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onContinue: (sessionOpen: Boolean) -> Unit,
    onBack: (() -> Unit)? = null,
    onLoggedOut: () -> Unit = {},
    viewModel: ServerSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sunucu ve Ayarlar") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CollapsibleSection(
                title = "Sunucu adresi",
                subtitle = "Örnek: https://panel.firma.com"
            ) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::onUrlChange,
                    label = { Text("Adres") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                state.error?.let { ErrorBox(it) }
                state.info?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = viewModel::testConnection,
                        enabled = !state.testing && state.url.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text(if (state.testing) "Deneniyor…" else "Bağlantıyı test et") }

                    Button(
                        onClick = { viewModel.save(onContinue) },
                        enabled = !state.testing && state.url.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Kaydet") }
                }
            }

            CollapsibleSection(title = "Görünüm", initiallyExpanded = false) {
                SegmentedChoice(
                    label = "Tema",
                    options = listOf(
                        ThemeMode.DARK to "Koyu",
                        ThemeMode.LIGHT to "Açık",
                        ThemeMode.SYSTEM to "Sistem"
                    ),
                    selected = themeMode,
                    onSelected = viewModel::setThemeMode
                )
            }

            CollapsibleSection(title = "Güvenlik", initiallyExpanded = false) {
                SwitchRow(
                    label = "Biyometrik kilit",
                    description = "Uygulama açılırken parmak izi veya ekran kilidi sorulsun.",
                    checked = biometricEnabled,
                    onCheckedChange = viewModel::setBiometricEnabled
                )
                TextButton(onClick = { viewModel.logout(onLoggedOut) }) {
                    Text("Oturumu kapat")
                }
            }
        }
    }
}

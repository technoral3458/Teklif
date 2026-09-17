package com.technoral.servis.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.technoral.servis.data.MailSettings
import com.technoral.servis.ui.AppViewModel
import com.technoral.servis.ui.Navigator
import com.technoral.servis.ui.components.AppTextField
import com.technoral.servis.ui.components.AppTopBar
import com.technoral.servis.ui.components.ChoiceChipRow
import com.technoral.servis.ui.components.FlowRowCompat
import com.technoral.servis.ui.components.SectionCard

private data class MailPreset(
    val name: String,
    val host: String,
    val port: Int,
    val security: String,
    val hint: String,
)

private val presets = listOf(
    MailPreset("Gmail", "smtp.gmail.com", 587, "STARTTLS", "Google hesabınızda 2 adımlı doğrulama açıp \"uygulama parolası\" üretmelisiniz."),
    MailPreset("Outlook", "smtp-mail.outlook.com", 587, "STARTTLS", "Microsoft hesabı için uygulama parolası gerekebilir."),
    MailPreset("Yandex", "smtp.yandex.com.tr", 465, "SSL", "Yandex'te \"uygulama parolaları\" bölümünden parola üretin."),
    MailPreset("Yaygın (cPanel)", "mail.firmaniz.com", 465, "SSL", "Hosting firmanızın verdiği mail sunucu adını yazın."),
)

@Composable
fun MailSettingsScreen(vm: AppViewModel, nav: Navigator) {
    val settings by vm.settings.collectAsState()
    val mail = settings.mail

    var showPassword by remember { mutableStateOf(false) }
    var testDialog by remember { mutableStateOf(false) }
    var presetHint by remember { mutableStateOf<String?>(null) }

    fun update(block: (MailSettings) -> MailSettings) {
        vm.saveSettings(settings.copy(mail = block(mail)))
    }

    if (testDialog) {
        TestMailDialog(vm = vm, defaultTo = mail.defaultTo.ifBlank { mail.fromAddress }) {
            testDialog = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Mail Ayarları", subtitle = "Raporlar bu hesaptan gönderilir", onBack = { nav.back() }) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard(
                    title = "Hazır ayarlar",
                    subtitle = "Sağlayıcınızı seçerek sunucu ve portu otomatik doldurun",
                ) {
                    FlowRowCompat {
                        presets.forEach { preset ->
                            AssistChip(
                                onClick = {
                                    update {
                                        it.copy(
                                            host = preset.host,
                                            port = preset.port,
                                            security = preset.security,
                                        )
                                    }
                                    presetHint = preset.hint
                                },
                                label = { Text(preset.name) },
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                    }
                    presetHint?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Sunucu") {
                    AppTextField(mail.host, { v -> update { it.copy(host = v.trim()) } }, "SMTP sunucusu", placeholder = "smtp.firmaniz.com")
                    AppTextField(
                        if (mail.port == 0) "" else mail.port.toString(),
                        { v -> update { it.copy(port = v.filter { c -> c.isDigit() }.toIntOrNull() ?: 0) } },
                        "Port",
                        keyboardType = KeyboardType.Number,
                        supportingText = "465 → SSL, 587 → STARTTLS, 25 → şifresiz",
                    )
                    ChoiceChipRow(
                        label = "Şifreleme",
                        options = listOf("STARTTLS", "SSL", "NONE"),
                        selected = mail.security,
                        optionLabel = { if (it == "NONE") "Yok" else it },
                        onSelect = { v -> update { it.copy(security = v) } },
                    )
                }
            }

            item {
                SectionCard(title = "Hesap") {
                    AppTextField(
                        mail.username, { v -> update { it.copy(username = v.trim()) } },
                        "Kullanıcı adı", placeholder = "genelde e-posta adresiniz",
                        keyboardType = KeyboardType.Email,
                    )
                    AppTextField(
                        value = mail.password,
                        onValueChange = { v -> update { it.copy(password = v) } },
                        label = "Parola",
                        keyboardType = if (showPassword) KeyboardType.Text else KeyboardType.Password,
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    "Parolayı göster",
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    )
                    AppTextField(
                        mail.fromAddress, { v -> update { it.copy(fromAddress = v.trim()) } },
                        "Gönderen adresi", placeholder = "servis@firmaniz.com",
                        keyboardType = KeyboardType.Email,
                        supportingText = "Çoğu sunucu, hesapla aynı adresten gönderime izin verir",
                    )
                    AppTextField(
                        mail.fromName, { v -> update { it.copy(fromName = v) } },
                        "Gönderen adı", placeholder = "Firmanız Teknik Servis",
                    )
                }
            }

            item {
                SectionCard(title = "Varsayılanlar") {
                    AppTextField(
                        mail.defaultTo, { v -> update { it.copy(defaultTo = v) } },
                        "Her rapora eklenecek alıcı",
                        placeholder = "arsiv@firmaniz.com",
                        keyboardType = KeyboardType.Email,
                        supportingText = "Müşterinin adresine ek olarak gönderilir",
                    )
                    AppTextField(
                        mail.defaultCc, { v -> update { it.copy(defaultCc = v) } },
                        "Bilgi (CC)", keyboardType = KeyboardType.Email,
                    )
                    AppTextField(
                        mail.subjectTemplate, { v -> update { it.copy(subjectTemplate = v) } },
                        "Konu şablonu",
                        supportingText = "{rapor_no} {musteri} {makine} {tarih} {durum} yer tutucularını kullanabilirsiniz",
                    )
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            update { it.copy(attachPhotos = !it.attachPhotos) }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = mail.attachPhotos,
                            onCheckedChange = { v -> update { it.copy(attachPhotos = v) } },
                        )
                        Text("Fotoğrafları da varsayılan olarak ekle", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Button(
                    onClick = { testDialog = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = mail.isConfigured,
                ) {
                    Icon(Icons.Default.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Test maili gönder")
                }
            }

            item {
                Text(
                    "Parolanız yalnızca bu cihazda saklanır ve mail gönderirken doğrudan sunucunuza iletilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TestMailDialog(vm: AppViewModel, defaultTo: String, onDismiss: () -> Unit) {
    var to by remember { mutableStateOf(defaultTo) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test maili") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(to, { to = it }, "Alıcı", keyboardType = KeyboardType.Email)
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = to.isNotBlank(),
                onClick = {
                    vm.sendTestMail(to.trim()) { result ->
                        if (result.success) {
                            vm.notify(result.message)
                            onDismiss()
                        } else {
                            error = result.message
                        }
                    }
                },
            ) { Text("Gönder") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

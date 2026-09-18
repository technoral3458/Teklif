package com.technoral.servis.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.technoral.servis.ui.AppViewModel
import com.technoral.servis.ui.Navigator
import com.technoral.servis.ui.Screen
import com.technoral.servis.ui.components.AppTextField
import com.technoral.servis.ui.components.AppTopBar
import com.technoral.servis.ui.components.ChoiceChipRow
import com.technoral.servis.ui.components.SectionCard
import com.technoral.servis.util.asDate
import com.technoral.servis.util.asDateTime
import com.technoral.servis.util.money
import com.technoral.servis.util.asNumber
import com.technoral.servis.util.shareUri
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, nav: Navigator) {
    val settings by vm.settings.collectAsState()
    val reports by vm.reports.collectAsState()
    val ratesLoading by vm.ratesLoading.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.importBackup(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppTopBar(title = "Ayarlar", subtitle = settings.company.name.ifBlank { "TeknoServis" }) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard {
                    SettingsRow(
                        icon = Icons.Default.Business,
                        title = "Firma Bilgileri",
                        subtitle = settings.company.name.ifBlank { "Rapor başlığında görünen bilgiler" },
                        onClick = { nav.go(Screen.CompanySettings) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    SettingsRow(
                        icon = Icons.Default.Mail,
                        title = "Mail Ayarları (SMTP)",
                        subtitle = if (settings.mail.isConfigured)
                            "${settings.mail.host}:${settings.mail.port} • ${settings.mail.fromAddress}"
                        else "Gönderen sunucu tanımlı değil",
                        onClick = { nav.go(Screen.MailSettings) },
                    )
                }
            }

            item {
                SectionCard(title = "Teknisyen") {
                    AppTextField(
                        settings.technicianName,
                        { vm.saveSettings(settings.copy(technicianName = it)) },
                        "Ad soyad",
                    )
                    AppTextField(
                        settings.technicianPhone,
                        { vm.saveSettings(settings.copy(technicianPhone = it)) },
                        "Telefon",
                        keyboardType = KeyboardType.Phone,
                    )
                }
            }

            item {
                SectionCard(title = "Rapor") {
                    AppTextField(
                        settings.reportPrefix,
                        { vm.saveSettings(settings.copy(reportPrefix = it.uppercase().take(6))) },
                        "Rapor no ön eki",
                        supportingText = "Örnek: ${settings.reportPrefix.ifBlank { "SRV" }}-202609-0001",
                    )
                    ChoiceChipRow(
                        label = "Görünüm",
                        options = listOf<Boolean?>(null, false, true),
                        selected = settings.darkTheme,
                        optionLabel = {
                            when (it) {
                                null -> "Sistem"
                                false -> "Açık"
                                else -> "Koyu"
                            }
                        },
                        onSelect = { vm.saveSettings(settings.copy(darkTheme = it)) },
                    )
                }
            }

            item {
                SectionCard(
                    title = "Kur ve Cari",
                    subtitle = "Kurlar otomatik güncellenir; her hareket kendi kurunu saklar",
                    trailing = {
                        if (ratesLoading) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { vm.refreshRates(force = true) }) {
                                Icon(Icons.Default.Refresh, "Kurları güncelle")
                            }
                        }
                    },
                ) {
                    val rateInfo = listOf(settings.rateSource, settings.rateDateLabel)
                        .filter { it.isNotBlank() }.joinToString(" • ")
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                if (settings.usdRate > 0 && settings.eurRate > 0)
                                    "1 USD = ${money(settings.usdRate)}   •   1 EUR = ${money(settings.eurRate)}"
                                else "Kur henüz alınmadı — sağdaki yenile düğmesine basın",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (rateInfo.isNotBlank()) {
                                Text(
                                    "Kaynak: $rateInfo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    AppTextField(
                        if (settings.usdRate == 0.0) "" else settings.usdRate.asNumber(),
                        { value ->
                            vm.saveSettings(
                                settings.copy(
                                    usdRate = value.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                    ratesUpdatedAt = System.currentTimeMillis(),
                                )
                            )
                        },
                        "1 USD kaç ₺",
                        keyboardType = KeyboardType.Decimal,
                    )
                    AppTextField(
                        if (settings.eurRate == 0.0) "" else settings.eurRate.asNumber(),
                        { value ->
                            vm.saveSettings(
                                settings.copy(
                                    eurRate = value.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                    ratesUpdatedAt = System.currentTimeMillis(),
                                )
                            )
                        },
                        "1 EUR kaç ₺",
                        keyboardType = KeyboardType.Decimal,
                        supportingText = settings.ratesUpdatedAt?.let { "Son güncelleme: ${it.asDateTime()}" },
                    )
                    AppTextField(
                        settings.overdueGraceDays.toString(),
                        { value ->
                            vm.saveSettings(
                                settings.copy(overdueGraceDays = value.filter { it.isDigit() }.toIntOrNull() ?: 0)
                            )
                        },
                        "Gecikme uyarısı toleransı (gün)",
                        keyboardType = KeyboardType.Number,
                        supportingText = "Vade geçtikten kaç gün sonra uyarı verilsin",
                    )
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            vm.saveSettings(settings.copy(showChargeOnPdf = !settings.showChargeOnPdf))
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = settings.showChargeOnPdf,
                            onCheckedChange = { vm.saveSettings(settings.copy(showChargeOnPdf = it)) },
                        )
                        Column {
                            Text("Servis bedeli PDF raporda görünsün", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Müşteriye giden rapora ücret satırı eklenir",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = "Yedekleme",
                    subtitle = "${reports.size} rapor cihazınızda saklanıyor",
                ) {
                    SettingsRow(
                        icon = Icons.Default.CloudUpload,
                        title = "Yedeği dışa aktar",
                        subtitle = "Tüm kayıtları JSON olarak paylaş",
                        onClick = {
                            scope.launch {
                                val file = vm.exportBackup()
                                if (file == null) {
                                    vm.notify("Yedek oluşturulamadı.")
                                } else {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, shareUri(context, file))
                                        putExtra(Intent.EXTRA_SUBJECT, "TeknoServis yedek")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Yedeği paylaş"))
                                }
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    SettingsRow(
                        icon = Icons.Default.CloudDownload,
                        title = "Yedeği geri yükle",
                        subtitle = "Daha önce aldığınız JSON dosyasını seçin",
                        onClick = { importLauncher.launch("*/*") },
                    )
                }
            }

            item {
                SectionCard(title = "Hakkında") {
                    Text(
                        "TeknoServis — makine servis raporlama uygulaması. Tüm kayıtlar cihazınızda tutulur, " +
                            "internet bağlantısı yalnızca mail gönderirken gerekir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CompanySettingsScreen(vm: AppViewModel, nav: Navigator) {
    val settings by vm.settings.collectAsState()
    val company = settings.company

    fun update(block: (com.technoral.servis.data.CompanyInfo) -> com.technoral.servis.data.CompanyInfo) {
        vm.saveSettings(settings.copy(company = block(company)))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppTopBar(title = "Firma Bilgileri", onBack = { nav.back() }) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard(
                    title = "Künye",
                    subtitle = "Bu bilgiler PDF raporun üst kısmında ve mail imzasında görünür",
                ) {
                    AppTextField(company.name, { v -> update { it.copy(name = v) } }, "Firma adı")
                    AppTextField(
                        company.address, { v -> update { it.copy(address = v) } }, "Adres",
                        singleLine = false, minLines = 2,
                    )
                    AppTextField(
                        company.phone, { v -> update { it.copy(phone = v) } }, "Telefon",
                        keyboardType = KeyboardType.Phone,
                    )
                    AppTextField(
                        company.email, { v -> update { it.copy(email = v) } }, "E-posta",
                        keyboardType = KeyboardType.Email,
                    )
                    AppTextField(company.web, { v -> update { it.copy(web = v) } }, "Web sitesi")
                    AppTextField(
                        company.taxInfo, { v -> update { it.copy(taxInfo = v) } },
                        "Vergi dairesi / no",
                    )
                }
            }
            item {
                SectionCard(title = "Logo") {
                    LogoPicker(
                        currentPath = company.logoPath,
                        onPicked = { path -> update { it.copy(logoPath = path) } },
                        onCleared = { update { it.copy(logoPath = null) } },
                        vm = vm,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogoPicker(
    currentPath: String?,
    onPicked: (String) -> Unit,
    onCleared: () -> Unit,
    vm: AppViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.technoral.servis.util.importImage(context, uri, vm.documentsDir())
            }
            if (file != null) onPicked(file.absolutePath) else vm.notify("Logo eklenemedi.")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (currentPath != null) {
            coil.compose.AsyncImage(
                model = java.io.File(currentPath),
                contentDescription = "Logo",
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.OutlinedButton(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text(if (currentPath == null) "Logo seç" else "Değiştir") }
            if (currentPath != null) {
                androidx.compose.material3.TextButton(onClick = onCleared) { Text("Kaldır") }
            }
        }
    }
}

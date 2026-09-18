package com.technoral.servis.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.technoral.servis.data.LedgerType
import com.technoral.servis.data.ServiceReport
import com.technoral.servis.ui.AppViewModel
import com.technoral.servis.ui.Navigator
import com.technoral.servis.ui.Screen
import com.technoral.servis.ui.components.AppTextField
import com.technoral.servis.ui.components.AppTopBar
import com.technoral.servis.ui.components.ConfirmDialog
import com.technoral.servis.ui.components.InfoRow
import com.technoral.servis.ui.components.SectionCard
import com.technoral.servis.ui.components.StatusBadge
import com.technoral.servis.ui.components.statusPalette
import com.technoral.servis.util.asDate
import com.technoral.servis.util.asNumber
import com.technoral.servis.util.money
import com.technoral.servis.util.asTime
import com.technoral.servis.util.minutesAsDuration
import com.technoral.servis.util.shareUri
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ReportDetailScreen(vm: AppViewModel, nav: Navigator, reportId: String) {
    val reports by vm.reports.collectAsState()
    val report = reports.firstOrNull { it.id == reportId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showMailDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewerPhoto by remember { mutableStateOf<String?>(null) }

    if (report == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Rapor bulunamadı.")
        }
        return
    }

    val ledger by vm.ledger.collectAsState()
    val allExpenses by vm.expenses.collectAsState()
    val charge = ledger.firstOrNull { it.reportId == report.id && it.type == LedgerType.BORC }
    val reportExpenses = allExpenses.filter { it.reportId == report.id }
    val customer = vm.customer(report.customerId)
    val machine = vm.machine(report.machineId)
    val (statusColor, statusBg) = statusPalette(report.status)

    fun sharePdf() {
        scope.launch {
            val pdf = vm.buildPdf(report)
            if (pdf == null) {
                vm.notify("PDF oluşturulamadı.")
                return@launch
            }
            val uri = shareUri(context, pdf)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Servis Raporu ${report.reportNo}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Servis raporunu paylaş"))
        }
    }

    if (showMailDialog) {
        MailDialog(
            vm = vm,
            report = report,
            onDismiss = { showMailDialog = false },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Rapor silinsin mi?",
            message = "${report.reportNo} numaralı rapor ve fotoğrafları kalıcı olarak silinecek.",
            confirmLabel = "Sil",
            onConfirm = {
                vm.deleteReport(report.id)
                nav.back()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    viewerPhoto?.let { path ->
        AlertDialog(
            onDismissRequest = { viewerPhoto = null },
            confirmButton = { TextButton(onClick = { viewerPhoto = null }) { Text("Kapat") } },
            text = {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(380.dp),
                    contentScale = ContentScale.Fit,
                )
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = report.reportNo,
                subtitle = customer?.name ?: "Servis raporu",
                onBack = { nav.back() },
                actions = {
                    IconButton(onClick = {
                        vm.editReport(report.id)
                        nav.go(Screen.ReportEdit)
                    }) { Icon(Icons.Default.Edit, "Düzenle") }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, "Sil", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { sharePdf() },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("PDF")
                    }
                    Button(
                        onClick = { showMailDialog = true },
                        modifier = Modifier.weight(1.3f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Mail, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mail Gönder")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(report.status.label, statusColor, statusBg)
                            Spacer(Modifier.width(8.dp))
                            StatusBadge(
                                report.type.label,
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                report.serviceDate.asDate(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (report.mailedAt != null) {
                            Text(
                                "Mail gönderildi: ${report.mailedTo} (${report.mailedAt.asDate()})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        InfoRow("Müşteri", customer?.name.orEmpty())
                        InfoRow("Yetkili", customer?.contactName.orEmpty())
                        InfoRow("Telefon", customer?.phone.orEmpty())
                        InfoRow("Makine", machine?.displayName.orEmpty())
                        InfoRow("Seri No", machine?.serialNo.orEmpty())
                        InfoRow("Konum", machine?.location.orEmpty())
                    }
                }
            }

            item {
                SectionCard(title = "Servis Bilgileri") {
                    InfoRow("Öncelik", report.priority.label)
                    InfoRow("Başlangıç", report.startTime.asTime())
                    InfoRow("Bitiş", report.endTime.asTime())
                    InfoRow("Süre", minutesAsDuration(report.durationMinutes))
                    InfoRow("Yol", "${report.travelKm.asNumber()} km")
                    InfoRow("Teknisyen", report.technician)
                    InfoRow("Sonraki Bakım", report.nextMaintenance.asDate())
                }
            }

            if (report.departments.isNotEmpty()) {
                item {
                    SectionCard(title = "Çalışılan Bölümler") {
                        report.departments.forEach { dw ->
                            Column {
                                Text(
                                    dw.department.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (dw.work.isNotBlank()) {
                                    Text(dw.work, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            val narrative = listOf(
                "Arıza / Talep Tanımı" to report.faultDescription,
                "Arıza Nedeni" to report.faultCause,
                "Yapılan İşlem / Çözüm" to report.workDone,
                "Öneriler" to report.recommendations,
            ).filter { it.second.isNotBlank() }

            if (narrative.isNotEmpty()) {
                item {
                    SectionCard(title = "Açıklamalar") {
                        narrative.forEach { (title, body) ->
                            Column {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            if (report.parts.isNotEmpty()) {
                item {
                    SectionCard(title = "Yedek Parçalar") {
                        report.parts.forEachIndexed { index, part ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(part.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        listOfNotNull(
                                            part.code.takeIf { it.isNotBlank() },
                                            part.note.takeIf { it.isNotBlank() },
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "${part.quantity.asNumber()} ${part.unit}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        part.status.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (charge != null || reportExpenses.isNotEmpty()) {
                item {
                    SectionCard(
                        title = "Ücret ve Masraf",
                        subtitle = charge?.let { "Cari hesaba işlendi" },
                    ) {
                        if (charge != null) {
                            InfoRow("Servis bedeli", money(charge.amount, charge.currency.symbol))
                            if (charge.currency.code != "TRY") {
                                InfoRow("TL karşılığı", money(charge.tryAmount))
                            }
                            charge.dueDate?.let { InfoRow("Vade", it.asDate()) }
                        }
                        if (reportExpenses.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            reportExpenses.forEach { expense ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(expense.category.label, style = MaterialTheme.typography.bodyMedium)
                                        if (expense.description.isNotBlank()) {
                                            Text(
                                                expense.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Text(
                                        money(expense.amount, expense.currency.symbol),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            InfoRow("Masraf toplamı", money(reportExpenses.sumOf { it.tryAmount }))
                            if (charge != null) {
                                InfoRow(
                                    "Servis kârı",
                                    money(charge.tryAmount - reportExpenses.sumOf { it.tryAmount }),
                                )
                            }
                        }
                    }
                }
            }

            if (report.photos.isNotEmpty()) {
                item {
                    SectionCard(title = "Fotoğraflar", subtitle = "${report.photos.size} görsel") {
                        report.photos.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { photo ->
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewerPhoto = photo.path },
                                    ) {
                                        AsyncImage(
                                            model = File(photo.path),
                                            contentDescription = photo.caption,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.55f),
                                            contentColor = Color.White,
                                            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                                        ) {
                                            Text(
                                                photo.tag.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }

            if (report.customerRep.isNotBlank() || report.signaturePath != null) {
                item {
                    SectionCard(title = "Teslim") {
                        InfoRow("Müşteri Yetkilisi", report.customerRep)
                        report.signaturePath?.let { path ->
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                            ) {
                                AsyncImage(
                                    model = File(path),
                                    contentDescription = "İmza",
                                    modifier = Modifier.fillMaxWidth().height(110.dp).padding(8.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MailDialog(vm: AppViewModel, report: ServiceReport, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    var to by remember { mutableStateOf(vm.defaultRecipients(report)) }
    var cc by remember { mutableStateOf(settings.mail.defaultCc) }
    var note by remember { mutableStateOf("") }
    var includePhotos by remember { mutableStateOf(settings.mail.attachPhotos) }
    var result by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Raporu Mail Gönder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!settings.mail.isConfigured) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            "Mail ayarları eksik. Ayarlar > Mail Ayarları bölümünden SMTP sunucunuzu tanımlayın.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                AppTextField(
                    to, { to = it }, "Alıcı(lar)",
                    placeholder = "ornek@firma.com, ikinci@firma.com",
                    keyboardType = KeyboardType.Email,
                    supportingText = "Birden çok adresi virgülle ayırın",
                )
                AppTextField(cc, { cc = it }, "Bilgi (CC)", keyboardType = KeyboardType.Email)
                AppTextField(note, { note = it }, "Mail notu", singleLine = false, minLines = 3)
                Row(
                    Modifier.fillMaxWidth().clickable { includePhotos = !includePhotos },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includePhotos, onCheckedChange = { includePhotos = it })
                    Text("Fotoğrafları da ekle (${report.photos.size})", style = MaterialTheme.typography.bodyMedium)
                }
                result?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = to.isNotBlank() && settings.mail.isConfigured,
                onClick = {
                    vm.sendReportMail(report, to.trim(), cc.trim(), note.trim(), includePhotos) { r ->
                        if (r.success) {
                            vm.notify(r.message)
                            onDismiss()
                        } else {
                            result = r.message
                        }
                    }
                },
            ) {
                Icon(Icons.Default.PictureAsPdf, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Gönder")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

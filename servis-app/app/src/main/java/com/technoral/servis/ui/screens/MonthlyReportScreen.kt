package com.technoral.servis.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.technoral.servis.data.Finance
import com.technoral.servis.data.currentYearMonth
import com.technoral.servis.data.monthNames
import com.technoral.servis.ui.AppViewModel
import com.technoral.servis.ui.Navigator
import com.technoral.servis.ui.components.AppTextField
import com.technoral.servis.ui.components.AppTopBar
import com.technoral.servis.ui.components.SectionCard
import com.technoral.servis.ui.theme.StatusColors
import com.technoral.servis.util.asDate
import com.technoral.servis.util.asNumber
import com.technoral.servis.util.money
import com.technoral.servis.util.shareUri
import kotlinx.coroutines.launch

@Composable
fun MonthlyReportScreen(vm: AppViewModel, nav: Navigator) {
    val ledger by vm.ledger.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val reports by vm.reports.collectAsState()
    val customers by vm.customers.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val (startYear, startMonth) = currentYearMonth()
    var year by remember { mutableIntStateOf(startYear) }
    var month by remember { mutableIntStateOf(startMonth) }
    var mailOpen by remember { mutableStateOf(false) }

    val summary = remember(year, month, ledger, expenses, reports) { vm.monthlySummary(year, month) }
    val settings by vm.settings.collectAsState()
    val overdue = remember(customers, ledger, settings) {
        Finance.overdueDebts(customers, ledger, settings.overdueGraceDays)
    }

    fun shift(delta: Int) {
        var m = month + delta
        var y = year
        while (m < 0) { m += 12; y -= 1 }
        while (m > 11) { m -= 12; y += 1 }
        month = m
        year = y
    }

    fun sharePdf() {
        scope.launch {
            val pdf = vm.buildFinancePdf(summary)
            if (pdf == null) {
                vm.notify("Rapor oluşturulamadı.")
                return@launch
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, shareUri(context, pdf))
                putExtra(Intent.EXTRA_SUBJECT, "${summary.label} finans raporu")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Raporu paylaş"))
        }
    }

    if (mailOpen) {
        FinanceMailDialog(vm = vm, summaryLabel = summary.label, onSend = { to, note, done ->
            vm.sendFinanceMail(summary, to, note) { result ->
                if (result.success) {
                    vm.notify(result.message)
                    mailOpen = false
                } else done(result.message)
            }
        }, onDismiss = { mailOpen = false })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = "Aylık Rapor",
                subtitle = summary.label,
                onBack = { nav.back() },
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
                        onClick = { mailOpen = true },
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
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = { shift(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Önceki ay")
                        }
                        Text(
                            "${monthNames[month]} $year",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = { shift(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Sonraki ay")
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryTile(Modifier.weight(1f), "Hakediş", money(summary.incomeTry), MaterialTheme.colorScheme.primary)
                    SummaryTile(Modifier.weight(1f), "Tahsilat", money(summary.collectedTry), StatusColors.success)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryTile(Modifier.weight(1f), "Masraf", money(summary.expenseTry), StatusColors.warning)
                    SummaryTile(
                        Modifier.weight(1f), "Net kâr", money(summary.netTry),
                        if (summary.netTry >= 0) StatusColors.success else StatusColors.danger,
                    )
                }
            }

            item {
                SectionCard(title = "Dönem özeti") {
                    SummaryRow("Kasa akışı (tahsilat - masraf)", money(summary.cashFlowTry))
                    SummaryRow("Servis sayısı", summary.serviceCount.toString())
                    if (summary.fuelLiters > 0) {
                        SummaryRow("Yakıt", "${summary.fuelLiters.asNumber()} litre")
                    }
                    SummaryRow("Müşteriye yansıtılacak masraf", money(summary.billableExpenseTry))
                    if (summary.incomeByCurrency.size > 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        summary.incomeByCurrency.forEach { (currency, amount) ->
                            SummaryRow(
                                "${currency.label} hakediş",
                                money(amount, currency.symbol),
                            )
                        }
                    }
                }
            }

            if (summary.expenseByCategory.isNotEmpty()) {
                item {
                    SectionCard(
                        title = "Masraf dağılımı",
                        subtitle = "Toplam ${money(summary.expenseTry)}",
                    ) {
                        val maxValue = summary.expenseByCategory.maxOf { it.second }.coerceAtLeast(0.01)
                        summary.expenseByCategory.forEach { (category, amount) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        expenseIcon(category),
                                        null,
                                        Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(
                                        money(amount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                    ) {}
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth((amount / maxValue).toFloat().coerceIn(0.02f, 1f))
                                            .height(6.dp),
                                    ) {}
                                }
                            }
                        }
                    }
                }
            }

            if (summary.topCustomers.isNotEmpty()) {
                item {
                    SectionCard(title = "Müşteri bazında") {
                        summary.topCustomers.forEach { (customerId, income, collected) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    vm.customer(customerId)?.name.orEmpty().ifBlank { "-" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(money(income), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "tahsil: ${money(collected)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusColors.success,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (overdue.isNotEmpty()) {
                item {
                    SectionCard(
                        title = "Ödemesi geciken alacaklar",
                        subtitle = "Toplam ${money(overdue.sumOf { it.second.openTry })}",
                    ) {
                        overdue.forEach { (customerId, debt) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        vm.customer(customerId)?.name.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        (if (debt.brokenPromise) "Söz: " else "Vade: ") +
                                            (debt.entry.promisedDate ?: debt.entry.dueDate).asDate() +
                                            " • ${debt.daysLate} gün",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StatusColors.danger,
                                    )
                                }
                                Text(
                                    money(debt.openTry),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusColors.danger,
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
private fun SummaryTile(modifier: Modifier, label: String, value: String, tint: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = tint, maxLines = 1)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FinanceMailDialog(
    vm: AppViewModel,
    summaryLabel: String,
    onSend: (to: String, note: String, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    var to by remember { mutableStateOf(settings.mail.defaultTo.ifBlank { settings.mail.fromAddress }) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$summaryLabel raporunu gönder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(to, { to = it }, "Alıcı(lar)", keyboardType = KeyboardType.Email)
                AppTextField(note, { note = it }, "Not", singleLine = false, minLines = 3)
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = to.isNotBlank() && settings.mail.isConfigured,
                onClick = { onSend(to.trim(), note.trim()) { message -> error = message } },
            ) { Text("Gönder") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

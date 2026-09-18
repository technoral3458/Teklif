package com.technoral.servis.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.technoral.servis.data.Currency
import com.technoral.servis.data.Finance
import com.technoral.servis.data.LedgerEntry
import com.technoral.servis.data.LedgerType
import com.technoral.servis.ui.AppViewModel
import com.technoral.servis.ui.Navigator
import com.technoral.servis.ui.components.AppTopBar
import com.technoral.servis.ui.components.LedgerEntryDialog
import com.technoral.servis.ui.components.SectionCard
import com.technoral.servis.ui.theme.StatusColors
import com.technoral.servis.util.asDate
import com.technoral.servis.util.money

/** Bir müşterinin cari ekstresi: bakiye, açık alacaklar ve tüm hareketler. */
@Composable
fun CustomerAccountScreen(vm: AppViewModel, nav: Navigator, customerId: String) {
    val customers by vm.customers.collectAsState()
    val ledger by vm.ledger.collectAsState()
    val settings by vm.settings.collectAsState()
    val customer = customers.firstOrNull { it.id == customerId }

    var newEntryType by remember { mutableStateOf<LedgerType?>(null) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }

    if (customer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Müşteri bulunamadı.") }
        return
    }

    val account = remember(ledger, customerId, settings) {
        Finance.accountOf(customerId, ledger, settings.overdueGraceDays)
    }
    val entries = remember(ledger, customerId) { vm.ledgerOfCustomer(customerId) }

    // Para birimi bazında net bakiye — dolarlı işlerin takibi için
    val perCurrency = remember(entries) {
        Currency.entries.mapNotNull { currency ->
            val net = entries.filter { it.currency == currency }
                .sumOf { if (it.type == LedgerType.BORC) it.amount else -it.amount }
            if (entries.any { it.currency == currency }) currency to net else null
        }
    }

    newEntryType?.let { type ->
        LedgerEntryDialog(
            initial = LedgerEntry(customerId = customerId, type = type),
            settings = settings,
            customerName = customer.name,
            onSave = { vm.saveLedgerEntry(it); newEntryType = null },
            onDismiss = { newEntryType = null },
        )
    }

    editingEntry?.let { entry ->
        LedgerEntryDialog(
            initial = entry,
            settings = settings,
            customerName = customer.name,
            onSave = { vm.saveLedgerEntry(it); editingEntry = null },
            onDismiss = { editingEntry = null },
            onDelete = { vm.deleteLedgerEntry(entry.id); editingEntry = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = customer.name,
                subtitle = "Cari ekstre",
                onBack = { nav.back() },
                actions = {
                    IconButton(onClick = { newEntryType = LedgerType.BORC }) {
                        Icon(Icons.Default.Add, "Borç ekle")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { newEntryType = LedgerType.TAHSILAT },
                icon = { Icon(Icons.Default.Payments, null) },
                text = { Text("Tahsilat") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Bakiye",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            money(account.balanceTry),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                account.balanceTry > 0.005 -> StatusColors.warning
                                account.balanceTry < -0.005 -> StatusColors.info
                                else -> StatusColors.success
                            },
                        )
                        Text(
                            when {
                                account.balanceTry > 0.005 -> "Müşteriden alacağımız"
                                account.balanceTry < -0.005 -> "Müşteriye borcumuz / fazla tahsilat"
                                else -> "Hesap kapalı"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Toplam hakediş",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(money(account.debitTry), style = MaterialTheme.typography.titleSmall)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Toplam tahsilat",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(money(account.creditTry), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        if (perCurrency.size > 1) {
                            Text(
                                perCurrency.joinToString("   ") { (currency, net) ->
                                    "${currency.code}: ${money(net, currency.symbol)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (account.openDebts.isNotEmpty()) {
                item {
                    SectionCard(
                        title = "Açık alacaklar",
                        subtitle = "${account.openDebts.size} kalem • ${money(account.openDebts.sumOf { it.openTry })}",
                    ) {
                        account.openDebts.forEach { debt ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (debt.isOverdue) {
                                    Icon(
                                        Icons.Default.Warning,
                                        null,
                                        Modifier.size(16.dp),
                                        tint = StatusColors.danger,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        debt.entry.description.ifBlank { "Hakediş" },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        buildString {
                                            append(debt.entry.date.asDate())
                                            debt.entry.dueDate?.let { append(" • Vade: ${it.asDate()}") }
                                            debt.entry.promisedDate?.let { append(" • Söz: ${it.asDate()}") }
                                            if (debt.isOverdue) append(" • ${debt.daysLate} gün gecikme")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (debt.isOverdue) StatusColors.danger
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    money(debt.openTry),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Hareketler (${entries.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "Bu müşteri için henüz cari hareket yok. Servis raporuna bedel yazdığınızda " +
                            "otomatik olarak buraya düşer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    LedgerRow(
                        entry = entry,
                        customerName = entry.description.ifBlank { entry.type.label },
                        onClick = { editingEntry = entry },
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

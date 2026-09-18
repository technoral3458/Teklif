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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.technoral.servis.data.Currency
import com.technoral.servis.data.Expense
import com.technoral.servis.data.ExpenseCategory
import com.technoral.servis.data.Finance
import com.technoral.servis.data.LedgerEntry
import com.technoral.servis.data.LedgerType
import com.technoral.servis.data.currentYearMonth
import com.technoral.servis.ui.AppViewModel
import com.technoral.servis.ui.Navigator
import com.technoral.servis.ui.Screen
import com.technoral.servis.ui.components.AppTopBar
import com.technoral.servis.ui.components.EmptyState
import com.technoral.servis.ui.components.ExpenseDialog
import com.technoral.servis.ui.components.LedgerEntryDialog
import com.technoral.servis.ui.components.SelectDialog
import com.technoral.servis.ui.components.StatusBadge
import com.technoral.servis.ui.theme.StatusColors
import com.technoral.servis.util.asDate
import com.technoral.servis.util.asNumber
import com.technoral.servis.util.money
import com.technoral.servis.util.moneyShort

fun expenseIcon(category: ExpenseCategory): ImageVector = when (category) {
    ExpenseCategory.YAKIT -> Icons.Default.LocalGasStation
    ExpenseCategory.KONAKLAMA -> Icons.Default.Hotel
    ExpenseCategory.YEMEK -> Icons.Default.Restaurant
    ExpenseCategory.YOL -> Icons.Default.Toll
    ExpenseCategory.OTOPARK -> Icons.Default.LocalParking
    ExpenseCategory.ULASIM -> Icons.Default.Flight
    ExpenseCategory.MALZEME -> Icons.Default.Build
    ExpenseCategory.KARGO -> Icons.Default.LocalShipping
    ExpenseCategory.ARAC -> Icons.Default.DirectionsCar
    ExpenseCategory.DIGER -> Icons.Default.MoreHoriz
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(vm: AppViewModel, nav: Navigator) {
    val customers by vm.customers.collectAsState()
    val ledger by vm.ledger.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val reports by vm.reports.collectAsState()
    val settings by vm.settings.collectAsState()

    var tab by remember { mutableStateOf(0) }
    var newEntryFor by remember { mutableStateOf<String?>(null) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var pickCustomer by remember { mutableStateOf(false) }
    var newExpense by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    val (year, month) = currentYearMonth()
    val summary = remember(ledger, expenses, reports) { vm.monthlySummary(year, month) }
    val accounts = remember(customers, ledger) { Finance.accounts(customers, ledger) }
    val overdue = remember(customers, ledger) { Finance.overdueDebts(customers, ledger) }
    val receivable = accounts.sumOf { it.balanceTry.coerceAtLeast(0.0) }
    val overdueTotal = overdue.sumOf { it.second.openTry }

    // ------------------------------------------------------------- pencereler

    if (pickCustomer) {
        SelectDialog(
            title = "Müşteri seç",
            items = customers,
            itemTitle = { it.name },
            itemSubtitle = {
                val account = Finance.accountOf(it.id, ledger)
                if (account.balanceTry > 0) "Bakiye: ${money(account.balanceTry)}" else it.city
            },
            onPick = { newEntryFor = it.id; pickCustomer = false },
            onDismiss = { pickCustomer = false },
        )
    }

    newEntryFor?.let { customerId ->
        LedgerEntryDialog(
            initial = LedgerEntry(customerId = customerId, type = LedgerType.TAHSILAT),
            settings = settings,
            customerName = vm.customer(customerId)?.name.orEmpty(),
            onSave = { vm.saveLedgerEntry(it); newEntryFor = null },
            onDismiss = { newEntryFor = null },
        )
    }

    editingEntry?.let { entry ->
        LedgerEntryDialog(
            initial = entry,
            settings = settings,
            customerName = vm.customer(entry.customerId)?.name.orEmpty(),
            onSave = { vm.saveLedgerEntry(it); editingEntry = null },
            onDismiss = { editingEntry = null },
            onDelete = { vm.deleteLedgerEntry(entry.id); editingEntry = null },
        )
    }

    if (newExpense) {
        ExpenseDialog(
            initial = Expense(),
            settings = settings,
            onPickReceipt = null,
            onSave = { vm.saveExpense(it); newExpense = false },
            onDismiss = { newExpense = false },
        )
    }

    editingExpense?.let { expense ->
        ExpenseDialog(
            initial = expense,
            settings = settings,
            onPickReceipt = null,
            onSave = { vm.saveExpense(it); editingExpense = null },
            onDismiss = { editingExpense = null },
            onDelete = { vm.deleteExpense(expense.id); editingExpense = null },
        )
    }

    // ------------------------------------------------------------------ ekran

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = "Cari Takip",
                subtitle = "Alacak, tahsilat ve masraflar",
                actions = {
                    IconButton(onClick = { nav.go(Screen.MonthlyReport) }) {
                        Icon(Icons.Default.InsertChart, "Aylık rapor")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (tab == 2) newExpense = true else pickCustomer = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(if (tab == 2) "Masraf Ekle" else "Tahsilat Ekle") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Hesaplar", "Hareketler", "Masraflar").forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniStat(
                            Modifier.weight(1f), "Toplam alacak",
                            moneyShort(receivable), MaterialTheme.colorScheme.primary,
                        )
                        MiniStat(
                            Modifier.weight(1f), "Vadesi geçen",
                            moneyShort(overdueTotal),
                            if (overdueTotal > 0) StatusColors.danger else StatusColors.neutral,
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniStat(
                            Modifier.weight(1f), "Bu ay hakediş",
                            moneyShort(summary.incomeTry), StatusColors.success,
                        )
                        MiniStat(
                            Modifier.weight(1f), "Bu ay masraf",
                            moneyShort(summary.expenseTry), StatusColors.warning,
                        )
                    }
                }

                if (overdue.isNotEmpty()) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = StatusColors.dangerBg,
                                contentColor = StatusColors.danger,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Ödemesi geciken ${overdue.size} alacak",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                overdue.take(4).forEach { (customerId, debt) ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                vm.customer(customerId)?.name.orEmpty(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                if (debt.brokenPromise)
                                                    "Söz verilen tarih: ${debt.entry.promisedDate.asDate()} • ${debt.daysLate} gün geçti"
                                                else
                                                    "Vade: ${debt.entry.dueDate.asDate()} • ${debt.daysLate} gün geçti",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                        Text(
                                            money(debt.openTry),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                if (overdue.size > 4) {
                                    Text(
                                        "ve ${overdue.size - 4} kayıt daha…",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }

                when (tab) {
                    0 -> {
                        val sorted = accounts.sortedByDescending { it.balanceTry }
                        if (sorted.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Default.MoreHoriz,
                                    title = "Müşteri yok",
                                    description = "Cari takip için önce Kayıtlar bölümünden müşteri ekleyin.",
                                )
                            }
                        }
                        items(sorted, key = { "acc_" + it.customerId }) { account ->
                            val customer = vm.customer(account.customerId) ?: return@items
                            Card(
                                onClick = { nav.go(Screen.CustomerAccount(account.customerId)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {
                                Row(
                                    Modifier.padding(14.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            customer.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "Hakediş ${money(account.debitTry)} • Tahsilat ${money(account.creditTry)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (account.hasOverdue) {
                                            Spacer(Modifier.height(4.dp))
                                            StatusBadge(
                                                "${money(account.overdueTry)} gecikmiş",
                                                StatusColors.danger,
                                                StatusColors.dangerBg,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "Bakiye",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            money(account.balanceTry),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                account.balanceTry > 0.005 -> StatusColors.warning
                                                account.balanceTry < -0.005 -> StatusColors.info
                                                else -> StatusColors.success
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (ledger.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Default.MoreHoriz,
                                    title = "Cari hareket yok",
                                    description = "Servis raporuna bedel yazdığınızda ya da tahsilat girdiğinizde " +
                                        "hareketler burada listelenir.",
                                )
                            }
                        }
                        items(ledger, key = { "led_" + it.id }) { entry ->
                            LedgerRow(
                                entry = entry,
                                customerName = vm.customer(entry.customerId)?.name.orEmpty(),
                                onClick = { editingEntry = entry },
                            )
                        }
                    }

                    else -> {
                        if (expenses.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Default.LocalGasStation,
                                    title = "Masraf kaydı yok",
                                    description = "Yakıt, otel, yemek, otoyol… servis sırasındaki tüm harcamaları " +
                                        "buraya girin; aylık raporda kategori kategori dökülür.",
                                )
                            }
                        }
                        items(expenses, key = { "exp_" + it.id }) { expense ->
                            ExpenseRow(
                                expense = expense,
                                customerName = expense.customerId?.let { vm.customer(it)?.name }.orEmpty(),
                                onClick = { editingExpense = expense },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, label: String, value: String, tint: Color) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerRow(entry: LedgerEntry, customerName: String, onClick: () -> Unit) {
    val isDebit = entry.type == LedgerType.BORC
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = if (isDebit) StatusColors.warningBg else StatusColors.successBg,
                shape = RoundedCornerShape(10.dp),
            ) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (isDebit) "B" else "T",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDebit) StatusColors.warning else StatusColors.success,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    customerName.ifBlank { entry.type.label },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(
                        entry.date.asDate(),
                        entry.description.takeIf { it.isNotBlank() },
                        entry.documentNo.takeIf { it.isNotBlank() },
                    ).filterNotNull().joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (isDebit) "+" else "−") + money(entry.amount, entry.currency.symbol),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDebit) StatusColors.warning else StatusColors.success,
                )
                if (entry.currency != Currency.TRY) {
                    Text(
                        money(entry.tryAmount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseRow(expense: Expense, customerName: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = StatusColors.infoBg, shape = RoundedCornerShape(10.dp)) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Icon(expenseIcon(expense.category), null, Modifier.size(19.dp), tint = StatusColors.info)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(expense.category.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOf(
                        expense.date.asDate(),
                        expense.description.takeIf { it.isNotBlank() },
                        customerName.takeIf { it.isNotBlank() },
                        if (expense.category == ExpenseCategory.YAKIT && expense.quantity > 0)
                            "${expense.quantity.asNumber()} lt" else null,
                    ).filterNotNull().joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    money(expense.amount, expense.currency.symbol),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (expense.billable) {
                    StatusBadge("Yansıtılacak", StatusColors.info, StatusColors.infoBg)
                }
            }
        }
    }
}

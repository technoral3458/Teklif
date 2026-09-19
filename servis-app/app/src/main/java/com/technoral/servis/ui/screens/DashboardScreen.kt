package com.technoral.servis.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.technoral.servis.data.Finance
import com.technoral.servis.data.PartStatus
import com.technoral.servis.data.currentYearMonth
import com.technoral.servis.data.ServiceStatus
import com.technoral.servis.ui.AppViewModel
import com.technoral.servis.ui.Navigator
import com.technoral.servis.ui.Screen
import com.technoral.servis.ui.components.AppTopBar
import com.technoral.servis.ui.components.EmptyState
import com.technoral.servis.ui.components.ReportListItem
import com.technoral.servis.ui.components.SectionCard
import com.technoral.servis.ui.theme.StatusColors
import com.technoral.servis.util.asDate
import com.technoral.servis.util.daysBetween
import com.technoral.servis.util.money
import com.technoral.servis.util.moneyShort
import com.technoral.servis.util.startOfMonth

@Composable
fun DashboardScreen(vm: AppViewModel, nav: Navigator) {
    val reports by vm.reports.collectAsState()
    val machines by vm.machines.collectAsState()
    val customers by vm.customers.collectAsState()
    val ledger by vm.ledger.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val settings by vm.settings.collectAsState()

    val now = System.currentTimeMillis()
    val monthStart = startOfMonth()

    val monthlyCount = reports.count { it.serviceDate >= monthStart }
    val openCount = reports.count { it.status == ServiceStatus.ACIK || it.status == ServiceStatus.TASLAK }
    val waitingParts = reports.count { r ->
        r.status == ServiceStatus.PARCA_BEKLIYOR || r.parts.any { it.status != PartStatus.TAKILDI }
    }
    val followUps = reports.filter { it.needsFollowUp }.take(4)

    val (finYear, finMonth) = currentYearMonth()
    val finance = remember(ledger, expenses, reports) { vm.monthlySummary(finYear, finMonth) }
    val accounts = remember(customers, ledger, settings) { Finance.accounts(customers, ledger, settings.overdueGraceDays) }
    val overdue = remember(customers, ledger, settings) { Finance.overdueDebts(customers, ledger, settings.overdueGraceDays) }
    val receivable = accounts.sumOf { it.balanceTry.coerceAtLeast(0.0) }

    val upcoming = reports
        .mapNotNull { r -> r.nextMaintenance?.let { date -> r to date } }
        .filter { (_, date) -> daysBetween(now, date) in -7..45 }
        .sortedBy { it.second }
        .take(4)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = settings.company.name.ifBlank { "Deli Kadir App" },
                subtitle = settings.technicianName.ifBlank { "Servis takip paneli" },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    vm.startNewReport()
                    nav.go(Screen.ReportEdit)
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Yeni Servis") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        Modifier.weight(1f),
                        "Bu ay",
                        monthlyCount.toString(),
                        "servis",
                        Icons.AutoMirrored.Filled.Assignment,
                        MaterialTheme.colorScheme.primary,
                    )
                    StatCard(
                        Modifier.weight(1f),
                        "Açık",
                        openCount.toString(),
                        "kayıt",
                        Icons.Default.PendingActions,
                        StatusColors.warning,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        Modifier.weight(1f),
                        "Parça",
                        waitingParts.toString(),
                        "bekleyen",
                        Icons.Default.Inventory2,
                        StatusColors.danger,
                    )
                    StatCard(
                        Modifier.weight(1f),
                        "Makine",
                        machines.size.toString(),
                        "kayıtlı",
                        Icons.Default.PrecisionManufacturing,
                        StatusColors.info,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        Modifier.weight(1f),
                        "Alacak",
                        moneyShort(receivable),
                        "tahsil edilecek",
                        Icons.Default.AccountBalanceWallet,
                        StatusColors.warning,
                    )
                    StatCard(
                        Modifier.weight(1f),
                        "Bu ay net",
                        moneyShort(finance.netTry),
                        "hakediş - masraf",
                        Icons.AutoMirrored.Filled.Assignment,
                        if (finance.netTry >= 0) StatusColors.success else StatusColors.danger,
                    )
                }
            }

            if (overdue.isNotEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = StatusColors.dangerBg,
                            contentColor = StatusColors.danger,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().clickable { nav.switchRoot(Screen.Finance) }.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Ödemesi geciken ${overdue.size} alacak",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                "Toplam ${money(overdue.sumOf { it.second.openTry })} • " +
                                    "en uzun gecikme ${overdue.first().second.daysLate} gün",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Ayrıntı için dokunun",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            if (upcoming.isNotEmpty()) {
                item {
                    SectionCard(title = "Yaklaşan bakımlar") {
                        upcoming.forEach { (report, date) ->
                            val machine = vm.machine(report.machineId)
                            val customer = vm.customer(report.customerId)
                            val days = daysBetween(now, date)
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.EventRepeat,
                                    null,
                                    Modifier.size(18.dp),
                                    tint = if (days < 0) StatusColors.danger else StatusColors.warning,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        machine?.displayName ?: (customer?.name ?: "-"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        customer?.name.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(date.asDate(), style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        when {
                                            days < 0 -> "${-days} gün gecikti"
                                            days == 0L -> "Bugün"
                                            else -> "$days gün kaldı"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (days < 0) StatusColors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (followUps.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Takip gerektirenler", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { nav.switchRoot(Screen.Reports) }) { Text("Tümü") }
                    }
                }
                items(followUps, key = { "f_" + it.id }) { report ->
                    ReportListItem(
                        report = report,
                        customerName = vm.customer(report.customerId)?.name.orEmpty(),
                        machineName = vm.machine(report.machineId)?.displayName.orEmpty(),
                        onClick = { nav.go(Screen.ReportDetail(report.id)) },
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Son raporlar", style = MaterialTheme.typography.titleMedium)
                    if (reports.isNotEmpty()) {
                        TextButton(onClick = { nav.switchRoot(Screen.Reports) }) { Text("Tümü") }
                    }
                }
            }

            if (reports.isEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        EmptyState(
                            icon = Icons.Default.Build,
                            title = "Henüz servis raporu yok",
                            description = "İlk raporunuzu oluşturun; makine, yapılan işlemler, fotoğraflar ve " +
                                "yedek parçalar tek kayıtta toplansın.",
                            actionLabel = "Yeni servis raporu",
                            onAction = {
                                vm.startNewReport()
                                nav.go(Screen.ReportEdit)
                            },
                        )
                    }
                }
            } else {
                items(reports.take(6), key = { it.id }) { report ->
                    ReportListItem(
                        report = report,
                        customerName = vm.customer(report.customerId)?.name.orEmpty(),
                        machineName = vm.machine(report.machineId)?.displayName.orEmpty(),
                        onClick = { nav.go(Screen.ReportDetail(report.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    tint: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(9.dp)) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(16.dp), tint = tint)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

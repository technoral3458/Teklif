package com.technoral.servis.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.technoral.servis.data.ServiceStatus
import com.technoral.servis.ui.AppViewModel
import com.technoral.servis.ui.Navigator
import com.technoral.servis.ui.Screen
import com.technoral.servis.ui.components.AppTopBar
import com.technoral.servis.ui.components.ChoiceChipRow
import com.technoral.servis.ui.components.EmptyState
import com.technoral.servis.ui.components.ReportListItem
import com.technoral.servis.util.asDate

@Composable
fun ReportsScreen(vm: AppViewModel, nav: Navigator) {
    val reports by vm.reports.collectAsState()
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<ServiceStatus?>(null) }

    val filtered = remember(reports, query, statusFilter) {
        reports.filter { report ->
            val matchesStatus = statusFilter == null || report.status == statusFilter
            if (!matchesStatus) return@filter false
            if (query.isBlank()) return@filter true
            val haystack = buildString {
                append(report.reportNo).append(' ')
                append(vm.customer(report.customerId)?.name.orEmpty()).append(' ')
                append(vm.machine(report.machineId)?.displayName.orEmpty()).append(' ')
                append(vm.machine(report.machineId)?.serialNo.orEmpty()).append(' ')
                append(report.faultDescription).append(' ')
                append(report.workDone).append(' ')
                append(report.technician).append(' ')
                append(report.serviceDate.asDate())
                report.parts.forEach { append(' ').append(it.name).append(' ').append(it.code) }
            }
            haystack.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = "Servis Raporları",
                subtitle = "${filtered.size} kayıt",
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Rapor no, müşteri, makine, arıza…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                ChoiceChipRow(
                    label = null,
                    options = listOf<ServiceStatus?>(null) + ServiceStatus.entries,
                    selected = statusFilter,
                    optionLabel = { it?.label ?: "Tümü" },
                    onSelect = { statusFilter = it },
                )
            }

            if (filtered.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    title = if (reports.isEmpty()) "Rapor yok" else "Sonuç bulunamadı",
                    description = if (reports.isEmpty())
                        "Sağ alttaki butondan ilk servis raporunuzu oluşturabilirsiniz."
                    else "Arama veya filtreyi değiştirmeyi deneyin.",
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { report ->
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
}

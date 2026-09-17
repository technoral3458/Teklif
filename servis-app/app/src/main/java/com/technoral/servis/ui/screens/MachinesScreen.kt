package com.technoral.servis.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.technoral.servis.data.Machine
import com.technoral.servis.ui.AppViewModel
import com.technoral.servis.ui.Navigator
import com.technoral.servis.ui.Screen
import com.technoral.servis.ui.components.AppTopBar
import com.technoral.servis.ui.components.ConfirmDialog
import com.technoral.servis.ui.components.EmptyState
import com.technoral.servis.ui.components.InfoRow
import com.technoral.servis.ui.components.MachineEditorDialog
import com.technoral.servis.ui.components.ReportListItem
import com.technoral.servis.ui.components.SectionCard
import com.technoral.servis.ui.components.StatusBadge
import com.technoral.servis.ui.theme.StatusColors
import com.technoral.servis.util.asDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachinesScreen(vm: AppViewModel, nav: Navigator) {
    val machines by vm.machines.collectAsState()
    val customers by vm.customers.collectAsState()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Machine?>(null) }

    val filtered = remember(machines, query) {
        if (query.isBlank()) machines
        else machines.filter {
            listOf(it.displayName, it.serialNo, it.model, it.brand, it.location,
                vm.customer(it.customerId)?.name.orEmpty())
                .joinToString(" ").contains(query, ignoreCase = true)
        }
    }

    editing?.let { machine ->
        MachineEditorDialog(
            initial = machine,
            customers = customers,
            onSave = { vm.saveMachine(it); editing = null },
            onDismiss = { editing = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppTopBar(title = "Makineler", subtitle = "${machines.size} kayıt") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Machine() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Makine Ekle") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Makine, seri no, müşteri…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (filtered.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.PrecisionManufacturing,
                    title = if (machines.isEmpty()) "Makine kaydı yok" else "Sonuç bulunamadı",
                    description = "Servis verdiğiniz makineleri kaydedin; her makinenin servis geçmişini " +
                        "tek ekranda görebilirsiniz.",
                    actionLabel = if (machines.isEmpty()) "Makine ekle" else null,
                    onAction = if (machines.isEmpty()) ({ editing = Machine() }) else null,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { machine ->
                        val reportCount = vm.reportsOfMachine(machine.id).size
                        Card(
                            onClick = { nav.go(Screen.MachineDetail(machine.id)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Row(
                                Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        machine.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        vm.customer(machine.customerId)?.name.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    Text(
                                        listOf(
                                            machine.serialNo.takeIf { it.isNotBlank() }?.let { "Seri: $it" },
                                            machine.location.takeIf { it.isNotBlank() },
                                        ).filterNotNull().joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                StatusBadge(
                                    "$reportCount servis",
                                    StatusColors.info,
                                    StatusColors.infoBg,
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
fun MachineDetailScreen(vm: AppViewModel, nav: Navigator, machineId: String) {
    val machines by vm.machines.collectAsState()
    val customers by vm.customers.collectAsState()
    val reports by vm.reports.collectAsState()
    val machine = machines.firstOrNull { it.id == machineId }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (machine == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Makine bulunamadı.") }
        return
    }

    val history = reports.filter { it.machineId == machineId }
    val customer = vm.customer(machine.customerId)

    if (editing) {
        MachineEditorDialog(
            initial = machine,
            customers = customers,
            onSave = { vm.saveMachine(it); editing = false },
            onDismiss = { editing = false },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Makine silinsin mi?",
            message = "Makine kaydı silinecek. Servis raporları kalır ancak makine bilgisi görünmez.",
            confirmLabel = "Sil",
            onConfirm = { vm.deleteMachine(machineId); nav.back() },
            onDismiss = { confirmDelete = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = machine.displayName,
                subtitle = customer?.name ?: "Makine",
                onBack = { nav.back() },
                actions = {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Düzenle") }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, "Sil", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    vm.startNewReport(machineId = machineId, customerId = machine.customerId)
                    nav.go(Screen.ReportEdit)
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Servis Aç") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard(title = "Makine Künyesi") {
                    InfoRow("Müşteri", customer?.name.orEmpty())
                    InfoRow("Marka", machine.brand)
                    InfoRow("Model", machine.model)
                    InfoRow("Seri No", machine.serialNo)
                    InfoRow("Üretim Yılı", machine.year)
                    InfoRow("Konum / Hat", machine.location)
                    InfoRow("Kurulum", machine.installDate.asDate())
                    InfoRow("Garanti Bitişi", machine.warrantyEnd.asDate())
                    if (machine.notes.isNotBlank()) InfoRow("Not", machine.notes)
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Servis geçmişi", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${history.size} kayıt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        "Bu makine için henüz servis kaydı yok.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(history, key = { it.id }) { report ->
                    ReportListItem(
                        report = report,
                        customerName = customer?.name.orEmpty(),
                        machineName = machine.displayName,
                        onClick = { nav.go(Screen.ReportDetail(report.id)) },
                    )
                }
            }
        }
    }
}

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.technoral.servis.data.Customer
import com.technoral.servis.ui.AppViewModel
import com.technoral.servis.ui.Navigator
import com.technoral.servis.ui.Screen
import com.technoral.servis.ui.components.AppTopBar
import com.technoral.servis.ui.components.ConfirmDialog
import com.technoral.servis.ui.components.CustomerEditorDialog
import com.technoral.servis.ui.components.EmptyState
import com.technoral.servis.ui.components.InfoRow
import com.technoral.servis.ui.components.ReportListItem
import com.technoral.servis.ui.components.SectionCard
import com.technoral.servis.ui.components.StatusBadge
import com.technoral.servis.ui.theme.StatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(vm: AppViewModel, nav: Navigator) {
    val customers by vm.customers.collectAsState()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Customer?>(null) }

    val filtered = remember(customers, query) {
        if (query.isBlank()) customers
        else customers.filter {
            listOf(it.name, it.contactName, it.phone, it.city, it.email)
                .joinToString(" ").contains(query, ignoreCase = true)
        }
    }

    editing?.let { customer ->
        CustomerEditorDialog(
            initial = customer,
            onSave = { vm.saveCustomer(it); editing = null },
            onDismiss = { editing = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppTopBar(title = "Müşteriler", subtitle = "${customers.size} kayıt") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Customer() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Müşteri Ekle") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Firma, yetkili, telefon…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (filtered.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Business,
                    title = if (customers.isEmpty()) "Müşteri kaydı yok" else "Sonuç bulunamadı",
                    description = "Müşterilerinizi kaydedin; raporlar ve mail adresleri otomatik dolsun.",
                    actionLabel = if (customers.isEmpty()) "Müşteri ekle" else null,
                    onAction = if (customers.isEmpty()) ({ editing = Customer() }) else null,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { customer ->
                        val machineCount = vm.machinesOf(customer.id).size
                        Card(
                            onClick = { nav.go(Screen.CustomerDetail(customer.id)) },
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
                                        customer.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        listOf(customer.contactName, customer.phone)
                                            .filter { it.isNotBlank() }.joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    if (customer.city.isNotBlank()) {
                                        Text(
                                            customer.city,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                StatusBadge("$machineCount makine", StatusColors.info, StatusColors.infoBg)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDetailScreen(vm: AppViewModel, nav: Navigator, customerId: String) {
    val customers by vm.customers.collectAsState()
    val machines by vm.machines.collectAsState()
    val reports by vm.reports.collectAsState()
    val customer = customers.firstOrNull { it.id == customerId }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (customer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Müşteri bulunamadı.") }
        return
    }

    val customerMachines = machines.filter { it.customerId == customerId }
    val history = reports.filter { it.customerId == customerId }

    if (editing) {
        CustomerEditorDialog(
            initial = customer,
            onSave = { vm.saveCustomer(it); editing = false },
            onDismiss = { editing = false },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Müşteri silinsin mi?",
            message = "Müşteri kaydı silinecek. Makine ve raporlar kalır.",
            confirmLabel = "Sil",
            onConfirm = { vm.deleteCustomer(customerId); nav.back() },
            onDismiss = { confirmDelete = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = customer.name,
                subtitle = customer.contactName.ifBlank { "Müşteri" },
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
                    vm.startNewReport(customerId = customerId)
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
                SectionCard(title = "İletişim") {
                    InfoRow("Yetkili", customer.contactName)
                    InfoRow("Telefon", customer.phone)
                    InfoRow("E-posta", customer.email)
                    InfoRow("Adres", customer.address)
                    InfoRow("Şehir", customer.city)
                    if (customer.notes.isNotBlank()) InfoRow("Not", customer.notes)
                }
            }
            if (customerMachines.isNotEmpty()) {
                item {
                    SectionCard(title = "Makineler", subtitle = "${customerMachines.size} kayıt") {
                        customerMachines.forEach { machine ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(machine.displayName, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        listOf(
                                            machine.serialNo.takeIf { it.isNotBlank() }?.let { "Seri: $it" },
                                            machine.location.takeIf { it.isNotBlank() },
                                        ).filterNotNull().joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { nav.go(Screen.MachineDetail(machine.id)) }) {
                                    Icon(Icons.Default.Search, "Aç")
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("Servis geçmişi (${history.size})", style = MaterialTheme.typography.titleMedium)
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        "Bu müşteri için henüz servis kaydı yok.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(history, key = { it.id }) { report ->
                    ReportListItem(
                        report = report,
                        customerName = customer.name,
                        machineName = vm.machine(report.machineId)?.displayName.orEmpty(),
                        onClick = { nav.go(Screen.ReportDetail(report.id)) },
                    )
                }
            }
        }
    }
}

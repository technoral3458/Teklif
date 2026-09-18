package com.technoral.servis.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Makineler ve Müşteriler listeleri arasında geçiş sekmesi. */
@Composable
fun RecordsTabs(
    selectedIndex: Int,
    onMachines: () -> Unit,
    onCustomers: () -> Unit,
) {
    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Tab(
            selected = selectedIndex == 0,
            onClick = onMachines,
            text = { Text("Makineler") },
        )
        Tab(
            selected = selectedIndex == 1,
            onClick = onCustomers,
            text = { Text("Müşteriler") },
        )
    }
}

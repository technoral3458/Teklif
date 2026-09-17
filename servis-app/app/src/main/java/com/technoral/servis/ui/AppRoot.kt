package com.technoral.servis.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import com.technoral.servis.ui.screens.CustomerDetailScreen
import com.technoral.servis.ui.screens.CustomersScreen
import com.technoral.servis.ui.screens.DashboardScreen
import com.technoral.servis.ui.screens.MachineDetailScreen
import com.technoral.servis.ui.screens.MachinesScreen
import com.technoral.servis.ui.screens.MailSettingsScreen
import com.technoral.servis.ui.screens.CompanySettingsScreen
import com.technoral.servis.ui.screens.ReportDetailScreen
import com.technoral.servis.ui.screens.ReportEditScreen
import com.technoral.servis.ui.screens.ReportsScreen
import com.technoral.servis.ui.screens.SettingsScreen
import com.technoral.servis.ui.screens.SetupScreen

private data class NavItem(val screen: Screen, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem(Screen.Dashboard, "Özet", Icons.Default.Dashboard),
    NavItem(Screen.Reports, "Raporlar", Icons.AutoMirrored.Filled.Assignment),
    NavItem(Screen.Machines, "Makineler", Icons.Default.PrecisionManufacturing),
    NavItem(Screen.Customers, "Müşteriler", Icons.Default.Business),
    NavItem(Screen.Settings, "Ayarlar", Icons.Default.Settings),
)

@Composable
fun AppRoot(vm: AppViewModel) {
    val ready by vm.ready.collectAsState()
    val settings by vm.settings.collectAsState()
    val toast by vm.toast.collectAsState()
    val busy by vm.busy.collectAsState()

    val nav = rememberNavigator(Screen.Dashboard)
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ready, settings.setupDone) {
        if (ready && !settings.setupDone && nav.current != Screen.Setup) {
            nav.switchRoot(Screen.Setup)
        }
    }

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            vm.clearToast()
        }
    }

    BackHandler(enabled = nav.stack.size > 1) { nav.back() }

    if (!ready) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val showBottomBar = nav.current in rootScreens

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(visible = showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            selected = nav.current == item.screen,
                            onClick = { nav.switchRoot(item.screen) },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val screen = nav.current) {
                Screen.Setup -> SetupScreen(vm) { nav.switchRoot(Screen.Dashboard) }
                Screen.Dashboard -> DashboardScreen(vm, nav)
                Screen.Reports -> ReportsScreen(vm, nav)
                Screen.Machines -> MachinesScreen(vm, nav)
                Screen.Customers -> CustomersScreen(vm, nav)
                Screen.Settings -> SettingsScreen(vm, nav)
                Screen.ReportEdit -> ReportEditScreen(vm, nav)
                is Screen.ReportDetail -> ReportDetailScreen(vm, nav, screen.reportId)
                is Screen.MachineDetail -> MachineDetailScreen(vm, nav, screen.machineId)
                is Screen.CustomerDetail -> CustomerDetailScreen(vm, nav, screen.customerId)
                Screen.MailSettings -> MailSettingsScreen(vm, nav)
                Screen.CompanySettings -> CompanySettingsScreen(vm, nav)
            }

            busy?.let { message ->
                BusyOverlay(message)
            }
        }
    }
}

@Composable
private fun BusyOverlay(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(Modifier.size(34.dp))
                Spacer(Modifier.height(12.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

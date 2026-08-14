package com.technoral.ucusbul.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Tab(val title: String) {
    SEARCH("Ara"),
    WATCH("Takip"),
    SETTINGS("Ayarlar")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val vm: MainViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.SEARCH) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            Tab.SEARCH -> "UçuşBul"
                            Tab.WATCH -> "Fiyat takibi"
                            Tab.SETTINGS -> "Ayarlar"
                        }
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.SEARCH,
                    onClick = { tab = Tab.SEARCH },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(Tab.SEARCH.title) }
                )
                NavigationBarItem(
                    selected = tab == Tab.WATCH,
                    onClick = { tab = Tab.WATCH; vm.reloadWatches() },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    label = { Text(Tab.WATCH.title) }
                )
                NavigationBarItem(
                    selected = tab == Tab.SETTINGS,
                    onClick = { tab = Tab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(Tab.SETTINGS.title) }
                )
            }
        }
    ) { padding ->
        when (tab) {
            Tab.SEARCH -> SearchScreen(vm, state, Modifier.padding(padding))
            Tab.WATCH -> WatchScreen(vm, state, Modifier.padding(padding))
            Tab.SETTINGS -> SettingsScreen(vm, Modifier.padding(padding))
        }
    }
}

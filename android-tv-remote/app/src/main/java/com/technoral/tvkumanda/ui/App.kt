package com.technoral.tvkumanda.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.technoral.tvkumanda.ConnectionState
import com.technoral.tvkumanda.RemoteViewModel

@Composable
fun TvKumandaApp(viewModel: RemoteViewModel) {
    val connection by viewModel.connection.collectAsState()
    val discovered by viewModel.discovered.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val currentApp by viewModel.currentApp.collectAsState()

    val onRemoteScreen = connection is ConnectionState.Connected

    // Kumanda ekranindayken mDNS aramasi bosuna pil harcar.
    LaunchedEffect(onRemoteScreen) {
        if (onRemoteScreen) viewModel.stopDiscovery() else viewModel.startDiscovery()
    }

    TvKumandaTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                when (val state = connection) {
                    is ConnectionState.Connected -> RemoteScreen(
                        deviceName = state.deviceName,
                        connected = true,
                        volume = volume,
                        currentApp = currentApp,
                        onKey = viewModel::sendKey,
                        onApp = viewModel::launchApp,
                        onText = viewModel::sendText,
                        onDisconnect = viewModel::disconnect,
                    )

                    else -> ConnectScreen(
                        state = state,
                        discovered = discovered,
                        saved = saved,
                        onConnect = viewModel::connect,
                        onForget = viewModel::forget,
                        onRefresh = viewModel::refreshDiscovery,
                        onSubmitCode = viewModel::submitCode,
                        onCancelPairing = viewModel::cancelPairing,
                    )
                }
            }
        }
    }
}

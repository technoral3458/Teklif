package com.teknoral.parametrik.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.CircularProgressIndicator
import com.teknoral.parametrik.ui.login.BiometricLock
import com.teknoral.parametrik.ui.nav.AppNavHost
import com.teknoral.parametrik.ui.theme.ParametrikTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val rootViewModel: RootViewModel = hiltViewModel()
            val themeMode by rootViewModel.themeMode.collectAsStateWithLifecycle()
            val biometricEnabled by rootViewModel.biometricEnabled.collectAsStateWithLifecycle()
            val start by rootViewModel.startDestination.collectAsStateWithLifecycle()

            var unlocked by remember { mutableStateOf(false) }

            ParametrikTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val destination = start
                    when {
                        destination == null -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }

                        biometricEnabled && destination == StartDestination.JOBS && !unlocked ->
                            BiometricLock(
                                activity = this,
                                onUnlocked = { unlocked = true }
                            )

                        else -> AppNavHost(start = destination)
                    }
                }
            }
        }
    }
}

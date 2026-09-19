package com.technoral.servis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.technoral.servis.ui.AppRoot
import com.technoral.servis.ui.AppViewModel
import com.technoral.servis.ui.screens.SplashScreen
import com.technoral.servis.ui.theme.TeknoServisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsState()

            // Açılış animasyonu yalnızca uygulama ilk açıldığında oynar;
            // ekran döndürmede tekrar başlamaması için durum saklanıyor.
            var splashShown by rememberSaveable { mutableStateOf(false) }

            TeknoServisTheme(darkTheme = settings.darkTheme) {
                if (splashShown) {
                    AppRoot(vm)
                } else {
                    SplashScreen(onFinish = { splashShown = true })
                }
            }
        }
    }
}

package com.teklif.tercuman

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teklif.tercuman.translate.Lang
import com.teklif.tercuman.ui.ChatScreen
import com.teklif.tercuman.ui.FaceToFaceScreen
import com.teklif.tercuman.ui.MainViewModel
import com.teklif.tercuman.ui.Screen
import com.teklif.tercuman.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Görüşme sırasında ekran kararmasın.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            TercumanTheme { App(vm) }
        }
    }
}

@Composable
private fun App(vm: MainViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Mikrofon izni verildikten sonra hangi tuşa basıldığını hatırlamak için.
    var pendingMic by remember { mutableStateOf<Lang?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            vm.toggleListening(pendingMic)
        }
    }
    val hasMicPermission = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val onMic: (Lang?) -> Unit = { lang ->
        if (hasMicPermission()) {
            vm.toggleListening(lang)
        } else {
            pendingMic = lang
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    BackHandler(enabled = state.screen != Screen.CHAT) { vm.navigate(Screen.CHAT) }

    when (state.screen) {
        Screen.CHAT -> ChatScreen(state, snackbar, vm, onMic = { onMic(state.direction.source) })
        Screen.FACE_TO_FACE -> FaceToFaceScreen(state, snackbar, vm, onMic)
        Screen.SETTINGS -> SettingsScreen(state, vm)
    }
}

@Composable
private fun TercumanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

package com.technoral.tvkumanda

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.technoral.tvkumanda.protocol.TvKeys
import com.technoral.tvkumanda.ui.TvKumandaApp

class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Kumanda kullanilirken ekranin kararmasi can sikici; uygulama on planda
        // oldugu surece acik tutuyoruz.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent { TvKumandaApp(viewModel) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reconnectIfNeeded()
    }

    /**
     * Telefonun fiziksel ses tuslari TV'nin sesini ayarlasin - gercek kumanda
     * hissi icin en cok fark yaratan detay.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> TvKeys.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> TvKeys.VOLUME_DOWN
            else -> return super.dispatchKeyEvent(event)
        }
        if (!viewModel.isConnected) return super.dispatchKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) viewModel.sendKey(keyCode)
        return true
    }
}

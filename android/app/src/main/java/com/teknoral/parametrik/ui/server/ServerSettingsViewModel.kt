package com.teknoral.parametrik.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teknoral.parametrik.core.userMessage
import com.teknoral.parametrik.data.local.SettingsStore
import com.teknoral.parametrik.data.local.ThemeMode
import com.teknoral.parametrik.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerUiState(
    val url: String = "",
    val testing: Boolean = false,
    val info: String? = null,
    val error: String? = null,
    val sessionOpen: Boolean = false,
    val saved: Boolean = false
)

@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ServerUiState())
    val state: StateFlow<ServerUiState> = _state.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.DARK)

    val biometricEnabled: StateFlow<Boolean> = settings.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(url = settings.baseUrl.first().orEmpty())
        }
    }

    fun onUrlChange(value: String) {
        _state.value = _state.value.copy(url = value, info = null, error = null, saved = false)
    }

    fun testConnection() {
        val url = _state.value.url
        _state.value = _state.value.copy(testing = true, info = null, error = null)
        viewModelScope.launch {
            auth.probeServer(url)
                .onSuccess { sessionOpen ->
                    _state.value = _state.value.copy(
                        testing = false,
                        sessionOpen = sessionOpen,
                        info = if (sessionOpen) "Sunucuya bağlanıldı, oturum açık."
                        else "Sunucuya bağlanıldı. Giriş yapmanız gerekiyor."
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(testing = false, error = error.userMessage())
                }
        }
    }

    fun save(onSaved: (sessionOpen: Boolean) -> Unit) {
        val url = _state.value.url
        _state.value = _state.value.copy(testing = true, error = null)
        viewModelScope.launch {
            auth.saveServer(url)
                .onSuccess {
                    val sessionOpen = auth.hasValidSession().getOrDefault(false)
                    _state.value = _state.value.copy(testing = false, saved = true, sessionOpen = sessionOpen)
                    onSaved(sessionOpen)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(testing = false, error = error.userMessage())
                }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setBiometricEnabled(enabled) }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            auth.logout()
            onDone()
        }
    }
}

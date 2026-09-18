package com.teknoral.parametrik.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

enum class StartDestination { SERVER, LOGIN, JOBS }

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val auth: AuthRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    val biometricEnabled: StateFlow<Boolean> = settings.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _startDestination = MutableStateFlow<StartDestination?>(null)
    val startDestination: StateFlow<StartDestination?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val baseUrl = settings.baseUrl.first()
            _startDestination.value = when {
                baseUrl.isNullOrBlank() -> StartDestination.SERVER
                auth.hasValidSession().getOrDefault(false) -> StartDestination.JOBS
                else -> StartDestination.LOGIN
            }
        }
    }
}

package com.teknoral.parametrik.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teknoral.parametrik.core.userMessage
import com.teknoral.parametrik.data.local.SettingsStore
import com.teknoral.parametrik.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null
) {
    val canSubmit: Boolean get() = !loading && email.isNotBlank() && password.isNotBlank()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(email = settings.lastEmail.first())
        }
    }

    fun onEmailChange(value: String) {
        _state.value = _state.value.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun login(onSuccess: () -> Unit) {
        val current = _state.value
        if (!current.canSubmit) return
        _state.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            auth.login(current.email, current.password)
                .onSuccess {
                    _state.value = _state.value.copy(loading = false, password = "")
                    onSuccess()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(loading = false, error = error.userMessage())
                }
        }
    }
}

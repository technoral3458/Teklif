package com.teknoral.parametrik.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ayarlar")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store: DataStore<Preferences> get() = context.settingsDataStore

    val baseUrl: Flow<String?> = store.data.map { it[KEY_BASE_URL] }
    val lastEmail: Flow<String> = store.data.map { it[KEY_LAST_EMAIL] ?: "" }
    val biometricEnabled: Flow<Boolean> = store.data.map { it[KEY_BIOMETRIC] ?: false }
    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.DARK.name) }
            .getOrDefault(ThemeMode.DARK)
    }

    suspend fun setBaseUrl(raw: String) {
        store.edit { it[KEY_BASE_URL] = normalizeBaseUrl(raw) }
    }

    suspend fun setLastEmail(email: String) {
        store.edit { it[KEY_LAST_EMAIL] = email }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        store.edit { it[KEY_BIOMETRIC] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_LAST_EMAIL = stringPreferencesKey("last_email")
        private val KEY_BIOMETRIC = booleanPreferencesKey("biometric")
        private val KEY_THEME = stringPreferencesKey("theme_mode")

        /** "panel.firma.com" -> "https://panel.firma.com/" */
        fun normalizeBaseUrl(raw: String): String {
            var value = raw.trim()
            if (value.isEmpty()) return value
            if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
                value = "https://$value"
            }
            if (!value.endsWith("/")) value = "$value/"
            return value
        }
    }
}

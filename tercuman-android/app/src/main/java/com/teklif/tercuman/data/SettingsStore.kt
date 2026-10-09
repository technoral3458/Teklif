package com.teklif.tercuman.data

import android.content.Context
import com.teklif.tercuman.translate.EngineConfig

data class AppSettings(
    val apiKey: String = "",
    val model: String = EngineConfig.DEFAULT_MODEL,
    /** low = Hızlı, medium = Dengeli, high = En iyi */
    val effort: String = "low",
    val topic: String = "",
    val glossary: String = "",
    val autoSpeak: Boolean = true,
) {
    fun toEngineConfig() = EngineConfig(
        apiKey = apiKey.trim(),
        model = model.trim().ifBlank { EngineConfig.DEFAULT_MODEL },
        effort = effort,
        topic = topic,
        glossary = glossary,
    )
}

/** Ayarları telefonda uygulamaya özel alanda saklar. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tercuman_settings", Context.MODE_PRIVATE)

    fun load() = AppSettings(
        apiKey = prefs.getString(KEY_API, "") ?: "",
        model = prefs.getString(KEY_MODEL, EngineConfig.DEFAULT_MODEL) ?: EngineConfig.DEFAULT_MODEL,
        effort = prefs.getString(KEY_EFFORT, "low") ?: "low",
        topic = prefs.getString(KEY_TOPIC, "") ?: "",
        glossary = prefs.getString(KEY_GLOSSARY, "") ?: "",
        autoSpeak = prefs.getBoolean(KEY_AUTO_SPEAK, true),
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putString(KEY_API, s.apiKey)
            .putString(KEY_MODEL, s.model)
            .putString(KEY_EFFORT, s.effort)
            .putString(KEY_TOPIC, s.topic)
            .putString(KEY_GLOSSARY, s.glossary)
            .putBoolean(KEY_AUTO_SPEAK, s.autoSpeak)
            .apply()
    }

    private companion object {
        const val KEY_API = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_EFFORT = "effort"
        const val KEY_TOPIC = "topic"
        const val KEY_GLOSSARY = "glossary"
        const val KEY_AUTO_SPEAK = "auto_speak"
    }
}

package com.teklif.tercuman.data

import android.content.Context
import com.teklif.tercuman.translate.EngineConfig
import com.teklif.tercuman.translate.Lang
import com.teklif.tercuman.translate.LangPair

data class AppSettings(
    val apiKey: String = "",
    val model: String = EngineConfig.DEFAULT_MODEL,
    /** low = Hızlı, medium = Dengeli, high = En iyi */
    val effort: String = "low",
    val topic: String = "",
    val glossary: String = "",
    val autoSpeak: Boolean = true,
    /** Doluysa ses tanıma OpenAI Whisper ile yapılır (dil sesten algılanır). */
    val openAiKey: String = "",
    /** Konuşmanın bittiği sayılacak sessizlik süresi; 0 = sadece dokununca biter. */
    val silenceMs: Long = 0L,
    /** Çince çevirinin altında pinyin okunuşu (kapalıyken çeviri daha hızlı biter). */
    val pinyin: Boolean = false,
    /** Ekonomik mod: yalnızca çeviri üretilir, az geçmiş gönderilir; token ~yarıya iner. */
    val economy: Boolean = true,
    /** Soldaki (kullanıcı) ve sağdaki (misafir) tuşların dilleri. */
    val pair: LangPair = LangPair.DEFAULT,
) {
    fun toEngineConfig() = EngineConfig(
        apiKey = apiKey.trim(),
        model = model.trim().ifBlank { EngineConfig.DEFAULT_MODEL },
        effort = effort,
        topic = topic,
        glossary = glossary,
        pinyin = pinyin,
        economy = economy,
    )
}

/** Ayarları telefonda uygulamaya özel alanda saklar. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tercuman_settings", Context.MODE_PRIVATE)

    fun load() = AppSettings(
        apiKey = prefs.getString(KEY_API, "") ?: "",
        model = loadModel(),
        effort = prefs.getString(KEY_EFFORT, "low") ?: "low",
        topic = prefs.getString(KEY_TOPIC, "") ?: "",
        glossary = prefs.getString(KEY_GLOSSARY, "") ?: "",
        autoSpeak = prefs.getBoolean(KEY_AUTO_SPEAK, true),
        openAiKey = prefs.getString(KEY_OPENAI, "") ?: "",
        // Eski sürümlerden kalan ara değerler (1,5 / 2,5 sn) 1-5 sn seçeneklerine yuvarlanır.
        silenceMs = prefs.getLong(KEY_SILENCE, 0L).let { if (it <= 0) 0L else ((it + 500) / 1000).coerceIn(1, 5) * 1000 },
        pinyin = prefs.getBoolean(KEY_PINYIN, false),
        economy = prefs.getBoolean(KEY_ECONOMY, true),
        pair = LangPair(
            a = Lang.fromCode(prefs.getString(KEY_LANG_A, null)) ?: LangPair.DEFAULT.a,
            b = Lang.fromCode(prefs.getString(KEY_LANG_B, null)) ?: LangPair.DEFAULT.b,
        ),
    )

    /**
     * Eski sürümler Opus'u varsayılan olarak kaydediyordu; maliyeti yüzünden bir kez Haiku'ya
     * geçirilir. Kullanıcı sonra Opus'u yeniden seçerse bir daha dokunulmaz.
     */
    private fun loadModel(): String {
        val stored = prefs.getString(KEY_MODEL, EngineConfig.DEFAULT_MODEL) ?: EngineConfig.DEFAULT_MODEL
        if (prefs.getBoolean(KEY_MIGRATED_HAIKU, false)) return stored
        prefs.edit().putBoolean(KEY_MIGRATED_HAIKU, true).apply()
        if (stored == "claude-opus-5-5") {
            prefs.edit().putString(KEY_MODEL, EngineConfig.DEFAULT_MODEL).apply()
            return EngineConfig.DEFAULT_MODEL
        }
        return stored
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putString(KEY_API, s.apiKey)
            .putString(KEY_MODEL, s.model)
            .putString(KEY_EFFORT, s.effort)
            .putString(KEY_TOPIC, s.topic)
            .putString(KEY_GLOSSARY, s.glossary)
            .putBoolean(KEY_AUTO_SPEAK, s.autoSpeak)
            .putString(KEY_OPENAI, s.openAiKey)
            .putLong(KEY_SILENCE, s.silenceMs)
            .putBoolean(KEY_PINYIN, s.pinyin)
            .putBoolean(KEY_ECONOMY, s.economy)
            .putString(KEY_LANG_A, s.pair.a.code)
            .putString(KEY_LANG_B, s.pair.b.code)
            .apply()
    }

    private companion object {
        const val KEY_API = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_EFFORT = "effort"
        const val KEY_TOPIC = "topic"
        const val KEY_GLOSSARY = "glossary"
        const val KEY_AUTO_SPEAK = "auto_speak"
        const val KEY_OPENAI = "openai_key"
        const val KEY_SILENCE = "silence_ms"
        const val KEY_PINYIN = "pinyin"
        const val KEY_ECONOMY = "economy"
        const val KEY_LANG_A = "lang_a"
        const val KEY_LANG_B = "lang_b"
        const val KEY_MIGRATED_HAIKU = "migrated_haiku_default"
    }
}

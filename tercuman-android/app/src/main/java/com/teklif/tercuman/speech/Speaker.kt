package com.teklif.tercuman.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.teklif.tercuman.translate.Lang

/** Çeviriyi sesli okur (telefonun metin okuma motoru). */
class Speaker(context: Context, private val onProblem: (String) -> Unit) {

    private var ready = false
    private var queued: Pair<String, Lang>? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (!ready) {
            onProblem("Sesli okuma motoru başlatılamadı.")
        } else {
            queued?.let { (text, lang) -> speak(text, lang) }
        }
        queued = null
    }

    fun speak(text: String, lang: Lang) {
        if (text.isBlank()) return
        if (!ready) {
            queued = text to lang
            return
        }
        val result = tts.setLanguage(lang.locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            onProblem(
                "Telefonda ${lang.displayName} ses paketi yok. Ayarlar > Metin okuma çıkışı bölümünden indirin."
            )
            return
        }
        bestVoice(lang)?.let { tts.voice = it }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tercuman-${System.nanoTime()}")
    }

    /**
     * Metnin diline uygun, kurulu en iyi sesi seçer. Çince için Çin anakarası (zh-CN)
     * sesi, Kantonca/Tayvan seslerinden önce gelir.
     */
    private fun bestVoice(lang: Lang): Voice? {
        val voices = runCatching { tts.voices }.getOrNull() ?: return null
        val wantLanguage = lang.locale.language
        val wantCountry = lang.locale.country
        return voices
            .filter { v ->
                v.locale.language == wantLanguage &&
                    !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
            .sortedWith(
                compareBy<Voice>(
                    { it.locale.country != wantCountry },
                    { it.isNetworkConnectionRequired },
                    { -it.quality },
                )
            )
            .firstOrNull()
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

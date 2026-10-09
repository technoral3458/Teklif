package com.teklif.tercuman.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.teklif.tercuman.translate.Lang

/**
 * Telefonun konuşma tanıma servisini (Google) saran sınıf. Ana iş parçacığından çağrılmalı.
 *
 * Otomatik modda tanıyıcıya "birincil dil + ek dil" verilir; Android 14+ cihazlarda dil
 * algılama da açılır. Sonuç metnin yazı sistemine (Çince karakter / Latin harf) bakılarak
 * kesinleştirilir.
 */
class SpeechInput(context: Context, private val listener: Listener) {

    interface Listener {
        fun onListening()
        fun onPartial(text: String)
        fun onLevel(level: Float)
        fun onFinal(text: String, detected: Lang?)
        fun onSpeechError(message: String)
    }

    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }

    private var detectedByService: Lang? = null
    private var lastPartial = ""
    private var languageDetectionSupported = true
    private var pendingRetry: (() -> Unit)? = null

    val isAvailable: Boolean get() = recognizer != null

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.onListening()
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) = listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults.firstResult()
                if (!text.isNullOrBlank()) {
                    lastPartial = text
                    listener.onPartial(text)
                }
            }

            override fun onResults(results: Bundle?) {
                val text = results.firstResult()?.takeIf { it.isNotBlank() } ?: lastPartial
                if (text.isBlank()) {
                    listener.onSpeechError("Ses anlaşılamadı, tekrar deneyin.")
                } else {
                    listener.onFinal(text, detectedByService)
                }
            }

            override fun onLanguageDetection(results: Bundle) {
                val tag = results.getString(SpeechRecognizer.DETECTED_LANGUAGE) ?: return
                detectedByService = when {
                    tag.startsWith("zh", ignoreCase = true) || tag.startsWith("cmn", ignoreCase = true) -> Lang.ZH
                    tag.startsWith("tr", ignoreCase = true) -> Lang.TR
                    else -> null
                }
            }

            override fun onError(error: Int) {
                val retry = pendingRetry
                pendingRetry = null
                if (retry != null && (error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE)) {
                    // Bu cihaz dil algılamayı desteklemiyor: algılamasız tekrar dene.
                    languageDetectionSupported = false
                    retry()
                    return
                }
                if (error == SpeechRecognizer.ERROR_NO_MATCH && lastPartial.isNotBlank()) {
                    listener.onFinal(lastPartial, detectedByService)
                    return
                }
                listener.onSpeechError(errorText(error))
            }
        })
    }

    /**
     * @param primary tanıyıcının öncelikli dili
     * @param alternative otomatik modda ikinci dil; sabit yönde null
     */
    fun start(primary: Lang, alternative: Lang?) {
        val rec = recognizer ?: run {
            listener.onSpeechError("Bu telefonda konuşma tanıma servisi yok. Google uygulamasını yükleyin/güncelleyin.")
            return
        }
        detectedByService = null
        lastPartial = ""
        rec.cancel()

        val useDetection = alternative != null && languageDetectionSupported && Build.VERSION.SDK_INT >= 34
        pendingRetry = if (useDetection) ({ start(primary, alternative) }) else null
        rec.startListening(buildIntent(primary, alternative, useDetection))
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        pendingRetry = null
        recognizer?.cancel()
    }

    fun destroy() {
        recognizer?.destroy()
    }

    private fun buildIntent(primary: Lang, alternative: Lang?, useDetection: Boolean) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primary.speechTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primary.speechTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Konuşmacı kısa duraklamalarda kesilmesin.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            if (alternative != null) {
                // Google tanıyıcısının çok dilli tanıma desteği.
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(alternative.speechTag))
                if (useDetection && Build.VERSION.SDK_INT >= 34) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    putStringArrayListExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                        arrayListOf(primary.speechTag, alternative.speechTag),
                    )
                }
            }
        }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun errorText(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ses algılanamadı, tekrar deneyin."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Konuşma tanıma için internet gerekli."
        SpeechRecognizer.ERROR_AUDIO -> "Mikrofon kullanılamıyor."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni verilmedi."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Tanıyıcı meşgul, tekrar deneyin."
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE ->
            "Bu dil telefonda desteklenmiyor. Google uygulamasında Çince/Türkçe dil paketini indirin."
        else -> "Konuşma tanıma hatası ($error)."
    }

    private companion object {
        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31)
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}

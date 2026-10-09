package com.teklif.tercuman.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.teklif.tercuman.translate.Lang

/**
 * Telefonun konuşma tanıma servisini (Google) saran sınıf. Ana iş parçacığından çağrılmalı.
 *
 * Otomatik modda tanıyıcıya Türkçe + Çince birlikte verilir; Android 14+ cihazlarda dil
 * algılama ve konuşma sırasında dil değiştirme de açılır. Sonuç metnin yazı sistemine
 * (Çince karakter / Latin harf) bakılarak kesinleştirilir.
 *
 * Tanıyıcı birkaç saniye sessizlikte kendini kapatır; hiç konuşma olmadıysa sessizce
 * yeniden başlatılır. Konuşma duyulduğu halde metin çıkmadıysa (örn. Çince, Türkçe sanılarak
 * dinlendi) bir kez diğer dilde tekrar dinlenir ve kişiden tekrar söylemesi istenir.
 */
class PhoneSpeechInput(
    context: Context,
    private val listener: VoiceInput.Listener,
    private val silenceMs: () -> Long,
) : VoiceInput {

    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }

    private var forced: Lang? = null
    private var active = false
    private var userStopped = false
    private var deadline = 0L
    private var detectedByService: Lang? = null
    private var lastPartial = ""
    private var languageDetectionSupported = true
    private var usedDetection = false
    /** Bu oturumda konuşma sesi duyuldu mu (sessizlikten ayırmak için). */
    private var speechHeard = false
    /** Otomatik modda tanınamayan konuşmadan sonra bir kez diğer dil denenir. */
    private var primaryOverride: Lang? = null

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.onListening()
            override fun onBeginningOfSpeech() {
                speechHeard = true
            }
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
                if (!active) return
                val text = results.firstResult()?.takeIf { it.isNotBlank() } ?: lastPartial
                when {
                    text.isNotBlank() -> finish { listener.onFinal(text, detectedByService) }
                    speechHeard -> notUnderstood()
                    keepWaiting() -> begin()
                    userStopped -> finish { listener.onIdle() }
                    else -> finish { listener.onSpeechError("Uzun süre ses gelmedi. Tekrar dokunun.") }
                }
            }

            override fun onLanguageDetection(results: Bundle) {
                val tag = results.getString(SpeechRecognizer.DETECTED_LANGUAGE) ?: return
                detectedByService = when {
                    tag.startsWith("zh", ignoreCase = true) || tag.startsWith("cmn", ignoreCase = true) -> Lang.ZH
                    tag.startsWith("tr", ignoreCase = true) -> Lang.TR
                    else -> detectedByService
                }
            }

            override fun onError(error: Int) {
                if (!active) return
                if (usedDetection && (error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE)) {
                    // Bu cihaz dil algılamayı desteklemiyor: algılamasız devam et.
                    languageDetectionSupported = false
                    begin()
                    return
                }
                val silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                when {
                    silence && lastPartial.isNotBlank() -> finish { listener.onFinal(lastPartial, detectedByService) }
                    silence && speechHeard -> notUnderstood()
                    silence && keepWaiting() -> begin()
                    silence && userStopped -> finish { listener.onIdle() }
                    silence -> finish { listener.onSpeechError("Uzun süre ses gelmedi. Tekrar dokunun.") }
                    else -> finish { listener.onSpeechError(errorText(error)) }
                }
            }
        })
    }

    override fun start(forced: Lang?) {
        if (recognizer == null) {
            listener.onSpeechError("Bu telefonda konuşma tanıma servisi yok. Google uygulamasını yükleyin/güncelleyin.")
            return
        }
        this.forced = forced
        primaryOverride = null
        active = true
        userStopped = false
        deadline = SystemClock.elapsedRealtime() + VoiceInput.MAX_WAIT_FOR_SPEECH_MS
        begin()
    }

    override fun stop() {
        userStopped = true
        recognizer?.stopListening()
    }

    override fun cancel() {
        active = false
        recognizer?.cancel()
    }

    override fun destroy() {
        active = false
        recognizer?.destroy()
    }

    private fun keepWaiting() = !userStopped && SystemClock.elapsedRealtime() < deadline

    /** Konuşma duyuldu ama yazıya dökülemedi. */
    private fun notUnderstood() {
        // Dil algılama bayrakları bazı telefonlarda tanımayı bozuyor: bir daha kullanma.
        if (usedDetection) languageDetectionSupported = false
        if (forced == null && primaryOverride == null && !userStopped) {
            // Otomatikte birincil dil yanlış olabilir: diğer dilde bir kez daha dinle.
            primaryOverride = Lang.ZH
            listener.onPartial("Anlaşılamadı, tekrar söyleyin · 请再说一遍")
            begin()
        } else {
            finish { listener.onSpeechError("Anlaşılamadı. Dili sabitleyip tekrar deneyin · 请再说一遍") }
        }
    }

    private fun finish(report: () -> Unit) {
        active = false
        report()
    }

    private fun begin() {
        val rec = recognizer ?: return
        detectedByService = null
        lastPartial = ""
        speechHeard = false
        rec.cancel()
        // Otomatikte birincil dil Türkçe, Çince ek dil olarak verilir; sıra tahmini yapılmaz.
        val primary = forced ?: primaryOverride ?: Lang.TR
        val alternative = if (forced == null) primary.other else null
        usedDetection = alternative != null && languageDetectionSupported && Build.VERSION.SDK_INT >= 34
        rec.startListening(buildIntent(primary, alternative, usedDetection))
    }

    private fun buildIntent(primary: Lang, alternative: Lang?, useDetection: Boolean) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primary.speechTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primary.speechTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Cümle arasındaki kısa duraklamalarda kesilmesin.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs())
            if (alternative != null) {
                // Google tanıyıcısının çok dilli tanıma desteği.
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(alternative.speechTag))
                if (useDetection && Build.VERSION.SDK_INT >= 34) {
                    val both = arrayListOf(primary.speechTag, alternative.speechTag)
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, both)
                    // Konuşulan dil algılanınca tanıma o dile geçsin.
                    putExtra(
                        RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                        RecognizerIntent.LANGUAGE_SWITCH_HIGH_PRECISION,
                    )
                    putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, both)
                }
            }
        }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun errorText(error: Int): String = when (error) {
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

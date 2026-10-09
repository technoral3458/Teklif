package com.teklif.tercuman.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.teklif.tercuman.translate.Lang

/**
 * Telefonun konuşma tanıma servisini (Google) saran sınıf. Ana iş parçacığından çağrılmalı.
 *
 * Servis her 1-2 saniyelik duraklamada kendini kapatır ve o ana kadarki metni verir. Burada
 * bu parçalar birleştirilir ve dinleme hemen yeniden başlatılır; böylece kullanıcı tekrar
 * dokununcaya (veya ayarlanan uzun sessizliğe) kadar istediği uzunlukta konuşabilir.
 *
 * Otomatik modda Türkçe + Çince birlikte verilir; Android 14+ cihazlarda dil algılama açılır.
 * Bir parça konuşma duyulup metin çıkmadıysa bir sonraki parça diğer dilde dinlenir.
 */
class PhoneSpeechInput(
    context: Context,
    private val listener: VoiceInput.Listener,
    /** 0 = sadece kullanıcı dokununca biter. */
    private val silenceMs: () -> Long,
) : VoiceInput {

    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    private val handler = Handler(Looper.getMainLooper())

    private var forced: Lang? = null
    private var active = false
    private var userStopped = false
    private var startedAt = 0L
    private var lastTextAt = 0L

    /** Bitmiş parçaların birleşimi. */
    private val segments = StringBuilder()
    private var lastPartial = ""
    private var detectedOverall: Lang? = null
    private var detectedInSegment: Lang? = null
    private var speechHeard = false
    private var speechHeardAny = false
    private var languageDetectionSupported = true
    private var usedDetection = false
    private var primaryOverride: Lang? = null

    private val stopSafety = Runnable { if (active) finishWithWhatWeHave() }

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (segments.isEmpty()) listener.onListening()
            }

            override fun onBeginningOfSpeech() {
                speechHeard = true
                speechHeardAny = true
            }

            override fun onRmsChanged(rmsdB: Float) = listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults.firstResult()
                if (!text.isNullOrBlank()) {
                    lastPartial = text
                    listener.onPartial(accumulated())
                }
            }

            override fun onResults(results: Bundle?) {
                if (!active) return
                val text = results.firstResult()?.takeIf { it.isNotBlank() } ?: lastPartial
                segmentEnded(text)
            }

            override fun onLanguageDetection(results: Bundle) {
                val tag = results.getString(SpeechRecognizer.DETECTED_LANGUAGE) ?: return
                detectedInSegment = when {
                    tag.startsWith("zh", ignoreCase = true) || tag.startsWith("cmn", ignoreCase = true) -> Lang.ZH
                    tag.startsWith("tr", ignoreCase = true) -> Lang.TR
                    else -> detectedInSegment
                }
            }

            override fun onError(error: Int) {
                if (!active) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> segmentEnded(lastPartial)
                    ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> {
                        if (usedDetection) {
                            // Bu cihaz dil algılamayı desteklemiyor: algılamasız devam et.
                            languageDetectionSupported = false
                            begin()
                        } else {
                            finish { listener.onSpeechError(errorText(error)) }
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT ->
                        // Hızlı yeniden başlatmada ara sıra olur; kısa bekleyip sürdür.
                        handler.postDelayed({ if (active) begin() }, 300)
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
        segments.setLength(0)
        detectedOverall = null
        speechHeardAny = false
        active = true
        userStopped = false
        startedAt = SystemClock.elapsedRealtime()
        lastTextAt = 0L
        begin()
    }

    override fun stop() {
        if (!active) return
        userStopped = true
        recognizer?.stopListening()
        // Servis bazen son sonucu vermez; kısa süre sonra eldekiyle bitir.
        handler.postDelayed(stopSafety, 2500)
    }

    override fun cancel() {
        active = false
        handler.removeCallbacks(stopSafety)
        recognizer?.cancel()
    }

    override fun destroy() {
        cancel()
        recognizer?.destroy()
    }

    /** Bir tanıma parçası bitti: metni ekle, sonra bitir ya da dinlemeyi sürdür. */
    private fun segmentEnded(text: String) {
        val now = SystemClock.elapsedRealtime()
        if (text.isNotBlank()) {
            segments.append(text.trim()).append(' ')
            lastTextAt = now
            if (detectedOverall == null) detectedOverall = detectedInSegment
            listener.onPartial(accumulated())
        } else if (speechHeard) {
            // Konuşma duyuldu ama yazıya dökülemedi.
            if (usedDetection) languageDetectionSupported = false
            if (forced == null) primaryOverride = (primaryOverride ?: Lang.TR).other
        }
        lastPartial = ""

        val silence = silenceMs()
        when {
            userStopped -> finishWithWhatWeHave()
            silence > 0 && segments.isNotEmpty() && now - lastTextAt >= silence -> finishWithWhatWeHave()
            segments.isEmpty() && !speechHeardAny && now - startedAt > VoiceInput.MAX_WAIT_FOR_SPEECH_MS ->
                finish { listener.onSpeechError("Uzun süre ses gelmedi. Tekrar dokunun.") }
            now - startedAt > MAX_SESSION_MS -> finishWithWhatWeHave()
            else -> begin()
        }
    }

    private fun finishWithWhatWeHave() {
        val text = accumulated()
        when {
            text.isNotBlank() -> finish { listener.onFinal(text, detectedOverall) }
            speechHeardAny -> finish { listener.onSpeechError("Anlaşılamadı. Dil tuşuyla tekrar deneyin · 请再说一遍") }
            else -> finish { listener.onIdle() }
        }
    }

    private fun accumulated(): String = (segments.toString() + lastPartial).trim()

    private fun finish(report: () -> Unit) {
        active = false
        handler.removeCallbacks(stopSafety)
        report()
    }

    private fun begin() {
        val rec = recognizer ?: return
        detectedInSegment = null
        lastPartial = ""
        speechHeard = false
        rec.cancel()
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
            // Servis bunlara çoğu zaman uymaz; parçalar burada birleştirildiği için önemi az.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            if (alternative != null) {
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(alternative.speechTag))
                if (useDetection && Build.VERSION.SDK_INT >= 34) {
                    val both = arrayListOf(primary.speechTag, alternative.speechTag)
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, both)
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
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE ->
            "Bu dil telefonda desteklenmiyor. Google uygulamasında Çince/Türkçe dil paketini indirin."
        else -> "Konuşma tanıma hatası ($error)."
    }

    private companion object {
        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31)
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
        /** Tek konuşma için üst sınır. */
        const val MAX_SESSION_MS = 3 * 60_000L
    }
}

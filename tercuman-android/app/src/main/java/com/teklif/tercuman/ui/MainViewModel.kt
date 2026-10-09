package com.teklif.tercuman.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teklif.tercuman.data.AppSettings
import com.teklif.tercuman.data.SettingsStore
import com.teklif.tercuman.speech.PhoneSpeechInput
import com.teklif.tercuman.speech.Speaker
import com.teklif.tercuman.speech.VoiceInput
import com.teklif.tercuman.speech.WhisperSpeechInput
import com.teklif.tercuman.translate.EngineConfig
import com.teklif.tercuman.translate.HistoryTurn
import com.teklif.tercuman.translate.Lang
import com.teklif.tercuman.translate.TranslationEngine
import com.teklif.tercuman.translate.TranslationException
import com.teklif.tercuman.translate.TranslationProgress
import com.teklif.tercuman.translate.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { CHAT, FACE_TO_FACE, SETTINGS }

data class Utterance(
    val id: Long,
    /** Kesinleşmemiş tahmini kaynak dil (çeviri gelince result.source geçerli olur). */
    val guessedSource: Lang?,
    val rawText: String,
    /** Kullanıcı dili sabitlediyse (yön seçimi / yüz yüze tuşu) o dil. */
    val forced: Lang? = null,
    val result: TranslationResult? = null,
    val error: String? = null,
    /** Akışla gelmekte olan çeviri (sonuç gelene kadar gösterilir). */
    val liveTranslation: String = "",
    val liveSource: Lang? = null,
) {
    val pending: Boolean get() = result == null && error == null
    val source: Lang? get() = result?.source ?: liveSource ?: guessedSource

    /** Bu satırın verilen dildeki hali (yüz yüze modda her yarı kendi dilini gösterir). */
    fun textIn(lang: Lang): String? {
        val r = result
        if (r != null) return if (r.source == lang) r.cleanedSource else r.translation
        val src = source
        return when {
            src == lang -> rawText
            src != null && liveTranslation.isNotBlank() -> liveTranslation
            else -> null
        }
    }
}

data class UiState(
    val screen: Screen = Screen.CHAT,
    val utterances: List<Utterance> = emptyList(),
    /** Şu an dinlenen dil; AUTO'da null olabilir. */
    val listening: Boolean = false,
    val listeningFor: Lang? = null,
    val partial: String = "",
    val level: Float = 0f,
    val message: String? = null,
    val settings: AppSettings = AppSettings(),
)

class MainViewModel(app: Application) : AndroidViewModel(app), VoiceInput.Listener {

    private val store = SettingsStore(app)
    private val _state = MutableStateFlow(UiState(settings = store.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val phoneInput = PhoneSpeechInput(app, this) { _state.value.settings.silenceMs }
    private val whisperInput = WhisperSpeechInput(
        scope = viewModelScope,
        listener = this,
        apiKey = { _state.value.settings.openAiKey },
        silenceMs = { _state.value.settings.silenceMs },
    )
    /** OpenAI anahtarı girildiyse Whisper (dil sesten algılanır), yoksa telefonun tanıyıcısı. */
    private var activeInput: VoiceInput = phoneInput
    private val speaker = Speaker(app) { problem -> showMessage(problem) }

    private var engine: TranslationEngine? = null
    private var engineConfig: EngineConfig? = null
    private var nextId = 1L

    /** Bu dinleme oturumunda sabit dil seçildiyse o dil; otomatikse null. */
    private var forcedForSession: Lang? = null

    // ---- Kullanıcı eylemleri ----

    fun navigate(screen: Screen) {
        if (_state.value.listening) cancelListening()
        _state.update { it.copy(screen = screen) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_state.value.settings)
        store.save(updated)
        _state.update { it.copy(settings = updated) }
    }

    fun clearConversation() {
        speaker.stop()
        _state.update { it.copy(utterances = emptyList()) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /**
     * Dokun-başlat, tekrar dokun-bitir. Dinlerken hangi tuşa basılırsa basılsın konuşma biter.
     * @param fixed null ise otomatik algılama, değilse o dilde dinler.
     */
    fun toggleListening(fixed: Lang?) {
        if (_state.value.listening) {
            activeInput.stop()
            _state.update { it.copy(partial = it.partial.ifBlank { "Tamamlanıyor…" }) }
            return
        }
        if (_state.value.settings.apiKey.isBlank()) {
            showMessage("Önce Ayarlar'dan Claude API anahtarını girin.")
            _state.update { it.copy(screen = Screen.SETTINGS) }
            return
        }
        speaker.stop()
        forcedForSession = fixed
        activeInput = if (_state.value.settings.openAiKey.isNotBlank()) whisperInput else phoneInput
        _state.update { it.copy(listening = true, listeningFor = fixed, partial = "", level = 0f) }
        activeInput.start(fixed)
    }

    fun cancelListening() {
        activeInput.cancel()
        _state.update { it.copy(listening = false, partial = "", level = 0f) }
    }

    fun speak(utterance: Utterance) {
        val r = utterance.result ?: return
        speaker.speak(r.translation, r.target)
    }

    fun retry(utterance: Utterance) {
        replace(utterance.id) { it.copy(error = null, result = null, liveTranslation = "") }
        translate(utterance.id, utterance.rawText, utterance.forced, utterance.guessedSource)
    }

    // ---- VoiceInput.Listener ----

    override fun onListening() {
        _state.update { it.copy(listening = true) }
    }

    override fun onPartial(text: String) {
        _state.update { it.copy(partial = text) }
    }

    override fun onLevel(level: Float) {
        _state.update { it.copy(level = level) }
    }

    override fun onFinal(text: String, detected: Lang?) {
        _state.update { it.copy(listening = false, partial = "", level = 0f) }
        val forced = forcedForSession
        // Whisper dili sesten algılar; telefonda ise yazı sistemi (Çince karakter / Latin harf)
        // servisin tahmininden daha güvenilir.
        val hint = forced
            ?: (if (activeInput === whisperInput) detected else null)
            ?: Lang.guessFromScript(text)
            ?: detected
        val id = nextId++
        _state.update { it.copy(utterances = it.utterances + Utterance(id, hint, text, forced)) }
        translate(id, text, forced, hint)
    }

    override fun onSpeechError(message: String) {
        _state.update { it.copy(listening = false, partial = "", level = 0f) }
        showMessage(message)
    }

    override fun onIdle() {
        _state.update { it.copy(listening = false, partial = "", level = 0f) }
    }

    // ---- İç işler ----

    private fun translate(id: Long, text: String, forced: Lang?, scriptHint: Lang?) {
        val settings = _state.value.settings
        val history = _state.value.utterances
            .filter { it.id < id }
            .mapNotNull { u -> u.result?.let { HistoryTurn(it.source, it.cleanedSource, it.translation) } }
        // Çeviri metni biter bitmez seslendirilir; kalan alanlar (düzeltilmiş metin vb.) beklenmez.
        var spoken = false
        val onProgress: (TranslationProgress) -> Unit = { p ->
            viewModelScope.launch {
                val source = p.source ?: forced ?: scriptHint
                replace(id) { it.copy(liveTranslation = p.translation, liveSource = source ?: it.liveSource) }
                if (p.complete && source != null && !spoken && _state.value.settings.autoSpeak) {
                    spoken = true
                    speaker.speak(p.translation, source.other)
                }
            }
        }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { engineFor(settings).translate(text, forced, scriptHint, history, onProgress) }
            }
            outcome
                .onSuccess { result ->
                    replace(id) { it.copy(result = result, error = null) }
                    if (!spoken && _state.value.settings.autoSpeak) speaker.speak(result.translation, result.target)
                    spoken = true
                }
                .onFailure { e ->
                    val msg = (e as? TranslationException)?.message ?: "Çeviri başarısız: ${e.message}"
                    replace(id) { it.copy(error = msg) }
                }
        }
    }

    @Synchronized
    private fun engineFor(settings: AppSettings): TranslationEngine {
        val config = settings.toEngineConfig()
        val current = engine
        if (current != null && config == engineConfig) return current
        return TranslationEngine(config).also {
            engine = it
            engineConfig = config
        }
    }

    private fun replace(id: Long, transform: (Utterance) -> Utterance) {
        _state.update { s -> s.copy(utterances = s.utterances.map { if (it.id == id) transform(it) else it }) }
    }

    private fun showMessage(text: String) = _state.update { it.copy(message = text) }

    override fun onCleared() {
        phoneInput.destroy()
        whisperInput.destroy()
        speaker.shutdown()
    }
}

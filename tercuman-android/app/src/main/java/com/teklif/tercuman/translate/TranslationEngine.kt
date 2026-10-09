package com.teklif.tercuman.translate

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.fasterxml.jackson.databind.ObjectMapper

/** Çeviri ayarları (Ayarlar ekranından gelir). */
data class EngineConfig(
    val apiKey: String,
    val model: String = DEFAULT_MODEL,
    val effort: String = "low",
    /** Konuşmanın konusu, örn. "CNC membran kapak üretimi, fiyat teklifi görüşmesi". */
    val topic: String = "",
    /** Kullanıcının sabitlediği terimler, her satır: "Türkçe = English = 中文". */
    val glossary: String = "",
    /** Çince çevirinin altına pinyin yazılsın mı (kapalıyken çeviri daha hızlı biter). */
    val pinyin: Boolean = false,
) {
    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
    }
}

/** Önceki konuşma satırı; anlamı korumak için modele bağlam olarak gönderilir. */
data class HistoryTurn(val source: Lang, val sourceText: String, val translation: String)

data class TranslationResult(
    val source: Lang,
    val target: Lang,
    /** Konuşma tanıma hataları düzeltilmiş kaynak metin. */
    val cleanedSource: String,
    /** Anlamı taşıyan İngilizce köprü metin. */
    val english: String,
    val translation: String,
    /** Hedef Çince ise pinyin okunuşu, değilse boş. */
    val pinyin: String,
    /** Belirsizlik/terim notu (Türkçe), yoksa boş. */
    val note: String,
)

/**
 * Akış sırasında çevirinin gelen kısmı.
 * @param source modelin algıladığı kaynak dil (henüz yoksa null)
 * @param complete çeviri metni bitti; seslendirme hemen başlayabilir
 */
data class TranslationProgress(val source: Lang?, val translation: String, val complete: Boolean)

class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Türkçe <-> Çince çeviriyi İngilizce köprü üzerinden yapar:
 * kaynak -> anlamı açıklanmış İngilizce -> hedef dil. Tek istekte iki adım da yapılır,
 * İngilizce ara metin ekranda gösterilmek üzere geri döner. Yanıt akışla (streaming)
 * gelir; çeviri metni biter bitmez [TranslationProgress] ile bildirilir.
 */
class TranslationEngine(private val config: EngineConfig) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(config.apiKey)
        .build()

    private val mapper = ObjectMapper()

    /**
     * @param text konuşma tanımadan gelen metin
     * @param forcedSource kullanıcı yönü sabitlediyse kaynak dil; otomatikte null
     * @param scriptHint ses tanımanın tahmin ettiği dil (otomatik modda ipucu)
     * @param onProgress çeviri metni geldikçe çağrılır (arka plan iş parçacığından)
     */
    fun translate(
        text: String,
        forcedSource: Lang?,
        scriptHint: Lang?,
        history: List<HistoryTurn>,
        onProgress: (TranslationProgress) -> Unit = {},
    ): TranslationResult {
        val response = try {
            try {
                stream(buildParams(text, forcedSource, scriptHint, history, withFallback = true), forcedSource, onProgress)
            } catch (e: BadRequestException) {
                // Yedek model parametresi kabul edilmezse onsuz bir kez daha dene.
                stream(buildParams(text, forcedSource, scriptHint, history, withFallback = false), forcedSource, onProgress)
            }
        } catch (e: UnauthorizedException) {
            throw TranslationException("API anahtarı geçersiz. Ayarlardan kontrol edin.", e)
        } catch (e: RateLimitException) {
            throw TranslationException("Çok fazla istek. Birkaç saniye sonra tekrar deneyin.", e)
        } catch (e: AnthropicServiceException) {
            throw TranslationException("Çeviri servisi hatası (${e.statusCode()}): ${e.message}", e)
        } catch (e: AnthropicIoException) {
            throw TranslationException("İnternet bağlantısı yok veya zayıf.", e)
        }
        return parse(response, text, forcedSource, scriptHint)
    }

    private fun stream(
        params: MessageCreateParams,
        forcedSource: Lang?,
        onProgress: (TranslationProgress) -> Unit,
    ): Message {
        val accumulator = MessageAccumulator.create()
        val json = StringBuilder()
        var sent = ""
        var finished = false
        client.messages().createStreaming(params).use { response ->
            response.stream().forEach { event ->
                accumulator.accumulate(event)
                val delta = event.contentBlockDelta().flatMap { it.delta().text() }.orElse(null) ?: return@forEach
                json.append(delta.text())
                if (finished) return@forEach
                val (translation, complete) = PartialJson.stringField(json, "translation") ?: return@forEach
                if (translation == sent && !complete) return@forEach
                sent = translation
                finished = complete
                val source = forcedSource
                    ?: PartialJson.stringField(json, "source_language")?.takeIf { it.second }?.let { Lang.fromCode(it.first) }
                onProgress(TranslationProgress(source, translation, complete))
            }
        }
        return accumulator.message()
    }

    private fun buildParams(
        text: String,
        forcedSource: Lang?,
        scriptHint: Lang?,
        history: List<HistoryTurn>,
        withFallback: Boolean,
    ): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(config.model)
            .maxTokens(16000L)
            .system(buildSystemPrompt())
            .addUserMessage(buildUserMessage(text, forcedSource, scriptHint, history))
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.of(config.effort))
                    .format(
                        JsonOutputFormat.builder()
                            .schema(OUTPUT_SCHEMA)
                            .build()
                    )
                    .build()
            )
        if (withFallback) {
            // Güvenlik sınıflandırıcısı nadiren yanlışlıkla reddederse sunucu tarafında
            // uygun yedek modele otomatik geçiş.
            builder
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        return builder.build()
    }

    private fun parse(response: Message, text: String, forcedSource: Lang?, scriptHint: Lang?): TranslationResult {
        if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw TranslationException("Bu cümle çevrilemedi. Lütfen farklı ifade edin.")
        }

        val json = response.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("")
        if (json.isBlank()) throw TranslationException("Boş yanıt alındı, tekrar deneyin.")

        val node = try {
            mapper.readTree(json)
        } catch (e: Exception) {
            throw TranslationException("Yanıt okunamadı, tekrar deneyin.", e)
        }

        val source = forcedSource
            ?: Lang.fromCode(node.path("source_language").asText())
            ?: scriptHint
            ?: Lang.TR
        val target = source.other
        return TranslationResult(
            source = source,
            target = target,
            cleanedSource = node.path("cleaned_source").asText(text).ifBlank { text },
            english = node.path("english").asText(""),
            translation = node.path("translation").asText(""),
            pinyin = if (target == Lang.ZH) node.path("pinyin").asText("") else "",
            note = node.path("note").asText(""),
        )
    }

    private fun buildSystemPrompt(): String = buildString {
        append(
            """
            You are a professional live interpreter between Turkish and Mandarin Chinese.
            Two people are talking face to face: a Turkish speaker and a Chinese guest.
            Every input is one utterance captured by phone speech recognition. Speed
            matters: they are waiting for you, so keep every field short and direct.

            Fill the fields in this order:
            source_language: "tr" or "zh" (given to you when known). If the transcript
               looks like Chinese speech written phonetically in Latin letters
               (pinyin-like), it is Chinese.
            english: the meaning in clear English. Speech recognition makes mistakes
               (wrong homophones, missing punctuation, split or merged words), so first
               work out what the speaker most plausibly meant from the conversation so
               far. Translate meaning, not words: resolve idioms, implied subjects,
               politeness and domain terms.
            translation: from the English meaning, what a native speaker of the target
               language would naturally say here. Target is the other language
               (Turkish -> Simplified Chinese, Chinese -> Turkish). Keep the tone and
               politeness level, use standard industry terms, keep numbers, units,
               prices, dates and names exact. No explanations inside the translation.
            cleaned_source: the utterance in its original language with recognition
               errors fixed (Chinese always in Simplified characters). Copy it as is if
               nothing needs fixing.
            """.trimIndent()
        )
        append('\n')
        if (config.pinyin) {
            append("pinyin: if the target is Chinese, Hanyu Pinyin with tone marks for the translation; otherwise \"\".\n")
        } else {
            append("pinyin: always \"\".\n")
        }
        append("note: \"\" unless something was genuinely ambiguous; then one short sentence in Turkish.")
        if (config.topic.isNotBlank()) {
            append("\n\nConversation topic / setting (use it to choose terms):\n")
            append(config.topic.trim())
        }
        if (config.glossary.isNotBlank()) {
            append("\n\nGlossary. Always use these fixed term pairs:\n")
            append(config.glossary.trim())
        }
    }

    private fun buildUserMessage(
        text: String,
        forcedSource: Lang?,
        scriptHint: Lang?,
        history: List<HistoryTurn>,
    ): String = buildString {
        if (history.isNotEmpty()) {
            append("<conversation_so_far>\n")
            for (turn in history.takeLast(MAX_HISTORY)) {
                append("[").append(turn.source.code).append("] ").append(turn.sourceText)
                append("  =>  ").append(turn.translation).append('\n')
            }
            append("</conversation_so_far>\n\n")
        }
        when {
            forcedSource != null ->
                append("The utterance is in ${forcedSource.englishName}; translate it into ${forcedSource.other.englishName}.\n")
            scriptHint != null ->
                append("Detect the language. The speech recognizer suggests ${scriptHint.englishName}, but verify from the text.\n")
            else -> append("Detect the language.\n")
        }
        append("<utterance>\n").append(text.trim()).append("\n</utterance>")
    }

    companion object {
        private const val MAX_HISTORY = 8

        /** Alan sırası önemli: çeviri erken gelsin ki seslendirme hemen başlasın. */
        private val OUTPUT_SCHEMA: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    linkedMapOf(
                        "source_language" to mapOf("type" to "string", "enum" to listOf("tr", "zh")),
                        "english" to mapOf("type" to "string"),
                        "translation" to mapOf("type" to "string"),
                        "cleaned_source" to mapOf("type" to "string"),
                        "pinyin" to mapOf("type" to "string"),
                        "note" to mapOf("type" to "string"),
                    )
                )
            )
            .putAdditionalProperty(
                "required",
                JsonValue.from(
                    listOf("source_language", "english", "translation", "cleaned_source", "pinyin", "note")
                )
            )
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }
}

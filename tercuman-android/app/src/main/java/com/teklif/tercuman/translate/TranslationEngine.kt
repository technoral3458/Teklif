package com.teklif.tercuman.translate

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.JsonOutputFormat
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

class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Türkçe <-> Çince çeviriyi İngilizce köprü üzerinden yapar:
 * kaynak -> anlamı açıklanmış İngilizce -> hedef dil. Tek istekte iki adım da yapılır,
 * İngilizce ara metin ekranda gösterilmek üzere geri döner.
 */
class TranslationEngine(private val config: EngineConfig) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(config.apiKey)
        .build()

    private val mapper = ObjectMapper()

    /**
     * @param text konuşma tanımadan gelen metin
     * @param forcedSource kullanıcı yönü sabitlediyse kaynak dil; otomatikte null
     * @param scriptHint yazı sistemine göre tahmin edilen dil (otomatik modda ipucu)
     */
    fun translate(
        text: String,
        forcedSource: Lang?,
        scriptHint: Lang?,
        history: List<HistoryTurn>,
    ): TranslationResult {
        val params = MessageCreateParams.builder()
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
            // Güvenlik sınıflandırıcısı nadiren yanlışlıkla reddederse sunucu tarafında
            // uygun yedek modele otomatik geçiş.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()

        val response = try {
            client.messages().create(params)
        } catch (e: UnauthorizedException) {
            throw TranslationException("API anahtarı geçersiz. Ayarlardan kontrol edin.", e)
        } catch (e: RateLimitException) {
            throw TranslationException("Çok fazla istek. Birkaç saniye sonra tekrar deneyin.", e)
        } catch (e: AnthropicServiceException) {
            throw TranslationException("Çeviri servisi hatası (${e.statusCode()}): ${e.message}", e)
        } catch (e: AnthropicIoException) {
            throw TranslationException("İnternet bağlantısı yok veya zayıf.", e)
        }

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
            Every input is one utterance captured by phone speech recognition.

            Work in three steps and return them all:
            1. Source: decide whether the utterance is Turkish ("tr") or Chinese ("zh"),
               unless the language is given. Speech recognition makes mistakes: wrong
               homophones, missing punctuation, split or merged words, numbers written
               as words. Reconstruct what the speaker most plausibly meant, using the
               conversation so far, and put that in cleaned_source (same language as
               spoken). If the transcript looks like Chinese speech written phonetically
               in Latin letters (pinyin-like), treat it as Chinese and write it in
               Chinese characters.
            2. English bridge: write the meaning in clear English. Translate meaning,
               not words: resolve idioms, implied subjects, politeness, and domain
               terms so the English says exactly what the speaker intends.
            3. Translation: from the English meaning, write what a native speaker of
               the target language would naturally say in this situation. Target is
               the other language (Turkish -> Simplified Chinese, Chinese -> Turkish).
               Keep the tone and politeness level. Use the standard industry term for
               technical words. Keep numbers, units, prices, dates and names exact.
               Do not add explanations to the translation itself.

            pinyin: if the target is Chinese, give Hanyu Pinyin with tone marks for the
            translation; otherwise an empty string.
            note: only if something was genuinely ambiguous or a term choice matters,
            one short sentence in Turkish for the Turkish user; otherwise empty.
            """.trimIndent()
        )
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
                append("Detect the language. The recognizer's script suggests ${scriptHint.englishName}, but verify.\n")
            else -> append("Detect the language.\n")
        }
        append("<utterance>\n").append(text.trim()).append("\n</utterance>")
    }

    companion object {
        private const val MAX_HISTORY = 8

        private val OUTPUT_SCHEMA: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "source_language" to mapOf("type" to "string", "enum" to listOf("tr", "zh")),
                        "cleaned_source" to mapOf("type" to "string"),
                        "english" to mapOf("type" to "string"),
                        "translation" to mapOf("type" to "string"),
                        "pinyin" to mapOf("type" to "string"),
                        "note" to mapOf("type" to "string"),
                    )
                )
            )
            .putAdditionalProperty(
                "required",
                JsonValue.from(
                    listOf("source_language", "cleaned_source", "english", "translation", "pinyin", "note")
                )
            )
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }
}

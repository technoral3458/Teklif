package com.teklif.tercuman.translate

import com.teklif.tercuman.ui.L
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
    /**
     * Ekonomik mod: model yalnızca dili ve çeviriyi yazar (İngilizce köprü, düzeltilmiş metin,
     * pinyin ve not üretilmez), geçmişten daha az satır gönderilir. Token kullanımı yaklaşık yarıya iner.
     */
    val economy: Boolean = true,
) {
    companion object {
        const val DEFAULT_MODEL = "claude-haiku-5-5"
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
 * Seçili dil çifti arasındaki çeviriyi İngilizce köprü üzerinden yapar:
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
        pair: LangPair,
        forcedSource: Lang?,
        scriptHint: Lang?,
        history: List<HistoryTurn>,
        onProgress: (TranslationProgress) -> Unit = {},
    ): TranslationResult {
        val response = try {
            try {
                stream(buildParams(text, pair, forcedSource, scriptHint, history, withFallback = true), forcedSource, onProgress)
            } catch (e: BadRequestException) {
                // Yedek model parametresi kabul edilmezse onsuz bir kez daha dene.
                stream(buildParams(text, pair, forcedSource, scriptHint, history, withFallback = false), forcedSource, onProgress)
            }
        } catch (e: UnauthorizedException) {
            throw TranslationException(L.current.errApiKey, e)
        } catch (e: RateLimitException) {
            throw TranslationException(L.current.errRateLimit, e)
        } catch (e: AnthropicServiceException) {
            throw TranslationException(L.current.errService(e.statusCode(), e.message), e)
        } catch (e: AnthropicIoException) {
            throw TranslationException(L.current.errNetwork, e)
        }
        return parse(response, text, pair, forcedSource, scriptHint)
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
        pair: LangPair,
        forcedSource: Lang?,
        scriptHint: Lang?,
        history: List<HistoryTurn>,
        withFallback: Boolean,
    ): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(config.model)
            .maxTokens(16000L)
            .system(buildSystemPrompt(pair))
            .addUserMessage(buildUserMessage(text, pair, forcedSource, scriptHint, history))
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.of(config.effort))
                    .format(
                        JsonOutputFormat.builder()
                            .schema(schemaFor(pair, economy = config.economy))
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

    private fun parse(
        response: Message,
        text: String,
        pair: LangPair,
        forcedSource: Lang?,
        scriptHint: Lang?,
    ): TranslationResult {
        if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw TranslationException(L.current.errRefused)
        }

        val json = response.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("")
        if (json.isBlank()) throw TranslationException(L.current.errEmpty)

        val node = try {
            mapper.readTree(json)
        } catch (e: Exception) {
            throw TranslationException(L.current.errParse, e)
        }

        val source = forcedSource
            ?: Lang.fromCode(node.path("source_language").asText())?.takeIf { it in pair }
            ?: scriptHint
            ?: pair.a
        val target = pair.other(source)
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

    private fun buildSystemPrompt(pair: LangPair): String =
        if (config.economy) buildEconomyPrompt(pair) else buildFullPrompt(pair)

    private fun LangPair.names() = "${a.englishName} and ${b.englishName}"
    private fun LangPair.codes() = "\"${a.code}\" or \"${b.code}\""
    private fun LangPair.directions() = "${a.englishName} -> ${b.englishName}, ${b.englishName} -> ${a.englishName}"
    private fun LangPair.chineseRule() = if (Lang.ZH in this) " Always write Chinese in Simplified characters." else ""

    /** Ekonomik mod: kısa talimat, iki alanlı çıktı. Anlam köprüsü yine kurulur ama yazılmaz. */
    private fun buildEconomyPrompt(pair: LangPair): String = buildString {
        append(
            """
            You are a live interpreter between ${pair.names()} for a face-to-face
            business conversation. Each input is one utterance from phone speech recognition,
            which may contain recognition errors; infer what was meant.
            source_language: ${pair.codes()} (given when known).
            translation: what a native speaker of the other language would naturally say
            (${pair.directions()}). Translate the meaning, not
            the words; keep tone, politeness, numbers, prices and names exact. Standard
            industry terms. Nothing but the translation.${pair.chineseRule()}
            """.trimIndent()
        )
        appendContext()
    }

    private fun StringBuilder.appendContext() {
        if (config.topic.isNotBlank()) {
            append("\n\nConversation topic / setting (use it to choose terms):\n")
            append(config.topic.trim())
        }
        if (config.glossary.isNotBlank()) {
            append("\n\nGlossary. Always use these fixed term pairs:\n")
            append(config.glossary.trim())
        }
    }

    private fun buildFullPrompt(pair: LangPair): String = buildString {
        append(
            """
            You are a professional live interpreter between ${pair.names()}.
            Two people are talking face to face, one speaking each language.
            Every input is one utterance captured by phone speech recognition. Speed
            matters: they are waiting for you, so keep every field short and direct.

            Fill the fields in this order:
            source_language: ${pair.codes()} (given to you when known).
            english: the meaning in clear English. Speech recognition makes mistakes
               (wrong homophones, missing punctuation, split or merged words), so first
               work out what the speaker most plausibly meant from the conversation so
               far. Translate meaning, not words: resolve idioms, implied subjects,
               politeness and domain terms.
            translation: from the English meaning, what a native speaker of the target
               language would naturally say here. Target is the other language
               (${pair.directions()}). Keep the tone and
               politeness level, use standard industry terms, keep numbers, units,
               prices, dates and names exact. No explanations inside the translation.
            cleaned_source: the utterance in its original language with recognition
               errors fixed.${pair.chineseRule()} Copy it as is if nothing needs fixing.
            """.trimIndent()
        )
        append('\n')
        if (config.pinyin) {
            append("pinyin: if the target is Chinese, Hanyu Pinyin with tone marks for the translation; otherwise \"\".\n")
        } else {
            append("pinyin: always \"\".\n")
        }
        append("note: \"\" unless something was genuinely ambiguous; then one short sentence in Turkish.")
        appendContext()
    }

    private fun buildUserMessage(
        text: String,
        pair: LangPair,
        forcedSource: Lang?,
        scriptHint: Lang?,
        history: List<HistoryTurn>,
    ): String = buildString {
        if (history.isNotEmpty()) {
            append("<conversation_so_far>\n")
            for (turn in history.takeLast(if (config.economy) ECONOMY_HISTORY else MAX_HISTORY)) {
                append("[").append(turn.source.code).append("] ").append(turn.sourceText)
                append("  =>  ").append(turn.translation).append('\n')
            }
            append("</conversation_so_far>\n\n")
        }
        when {
            forcedSource != null ->
                append("The utterance is in ${forcedSource.englishName}; translate it into ${pair.other(forcedSource).englishName}.\n")
            scriptHint != null ->
                append("The utterance is in ${pair.a.englishName} or ${pair.b.englishName}; the speech recognizer suggests ${scriptHint.englishName}, but verify from the text. Translate into the other one.\n")
            else -> append("The utterance is in ${pair.a.englishName} or ${pair.b.englishName}; detect which, and translate into the other one.\n")
        }
        append("<utterance>\n").append(text.trim()).append("\n</utterance>")
    }

    companion object {
        private const val MAX_HISTORY = 8
        private const val ECONOMY_HISTORY = 3


        /** Alan sırası önemli: çeviri erken gelsin ki seslendirme hemen başlasın. */
        private fun schemaFor(pair: LangPair, economy: Boolean): JsonOutputFormat.Schema {
            val str = mapOf("type" to "string")
            val fields = linkedMapOf<String, Any>(
                "source_language" to mapOf("type" to "string", "enum" to listOf(pair.a.code, pair.b.code)),
            )
            if (!economy) fields["english"] = str
            fields["translation"] = str
            if (!economy) {
                fields["cleaned_source"] = str
                fields["pinyin"] = str
                fields["note"] = str
            }
            return JsonOutputFormat.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(fields))
                .putAdditionalProperty("required", JsonValue.from(fields.keys.toList()))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build()
        }
    }
}

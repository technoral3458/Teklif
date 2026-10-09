package com.teklif.tercuman.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.fasterxml.jackson.databind.ObjectMapper
import com.teklif.tercuman.translate.Lang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Sesi kendisi kaydeder, konuşma bitince (sessizlik) OpenAI Whisper'a gönderir.
 * Whisper konuşulan dili sesten algılar, bu yüzden Türkçe/Çince ayrımı telefondan bağımsız
 * ve güvenilirdir. Konuşma başlayana kadar uzun süre sessizce bekler.
 */
class WhisperSpeechInput(
    private val scope: CoroutineScope,
    private val listener: VoiceInput.Listener,
    private val apiKey: () -> String,
    private val silenceMs: () -> Long,
) : VoiceInput {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val mapper = ObjectMapper()

    private var job: Job? = null
    @Volatile private var stopRequested = false

    override fun start(forced: Lang?) {
        job?.cancel()
        stopRequested = false
        job = scope.launch {
            val audio = try {
                withContext(Dispatchers.IO) { record() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                listener.onSpeechError("Mikrofon izni verilmedi.")
                return@launch
            } catch (e: Exception) {
                listener.onSpeechError("Mikrofon kullanılamıyor.")
                return@launch
            }
            when (audio) {
                null -> if (stopRequested) listener.onIdle()
                else listener.onSpeechError("Uzun süre ses gelmedi. Tekrar dokunun.")
                else -> {
                    listener.onPartial("Yazıya çevriliyor… / 识别中…")
                    val outcome = withContext(Dispatchers.IO) { runCatching { transcribe(audio, forced) } }
                    outcome
                        .onSuccess { (text, lang) ->
                            if (text.isBlank() || isHallucination(text)) {
                                listener.onSpeechError("Ses anlaşılamadı, tekrar deneyin.")
                            } else {
                                listener.onFinal(text, forced ?: lang)
                            }
                        }
                        .onFailure { e -> listener.onSpeechError(e.message ?: "Ses tanıma hatası.") }
                }
            }
        }
    }

    override fun stop() {
        stopRequested = true
    }

    override fun cancel() {
        job?.cancel()
        job = null
    }

    override fun destroy() = cancel()

    /**
     * Konuşma başlayana kadar bekler, konuşma bitince (ayarlanan sessizlik süresi) durur.
     * @return 16 kHz mono PCM; hiç konuşma olmadıysa null
     */
    @SuppressLint("MissingPermission")
    private suspend fun record(): ShortArray? {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuf, FRAME * 8 * 2),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord başlatılamadı" }

        val frame = ShortArray(FRAME)
        val preRoll = ArrayDeque<ShortArray>()
        val speech = ArrayList<ShortArray>()
        var noiseFloor = 0.0
        var framesSeen = 0
        var loudRun = 0
        var silentMs = 0L
        var speechLevel = 0.0
        var speaking = false
        var spokenMs = 0L
        val waitDeadline = System.currentTimeMillis() + VoiceInput.MAX_WAIT_FOR_SPEECH_MS
        val endSilence = silenceMs()

        recorder.startRecording()
        try {
            withContext(Dispatchers.Main) { listener.onListening() }
            while (coroutineContext.isActive) {
                val read = recorder.read(frame, 0, FRAME)
                if (read <= 0) continue
                val chunk = frame.copyOf(read)
                val rms = rms(chunk)
                framesSeen++

                // İlk ~0,3 sn ortam gürültüsünü öğren, sonra yavaşça güncelle.
                if (!speaking) {
                    noiseFloor = if (framesSeen <= 10) max(noiseFloor, rms) else noiseFloor * 0.95 + rms * 0.05
                }
                // Konuşma başladıktan sonra eşik, konuşmanın kendi seviyesine göre de yükselir:
                // ortam gürültüsü sonradan artsa (klima, kalabalık) bile sessizlik yakalanır.
                val threshold = if (speaking) {
                    max(max(noiseFloor * 2.5, MIN_SPEECH_RMS), speechLevel * 0.3)
                } else {
                    max(noiseFloor * 2.5, MIN_SPEECH_RMS)
                }
                val loud = rms > threshold
                if (loud) speechLevel = if (speechLevel == 0.0) rms else speechLevel * 0.9 + rms * 0.1

                if (framesSeen % 3 == 0) {
                    val level = ((20 * log10(max(rms, 1.0)) - 30) / 45).coerceIn(0.0, 1.0).toFloat()
                    withContext(Dispatchers.Main) { listener.onLevel(level) }
                }

                if (!speaking) {
                    preRoll.addLast(chunk)
                    if (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                    loudRun = if (loud) loudRun + 1 else 0
                    if (loudRun >= 3) {
                        speaking = true
                        speech.addAll(preRoll)
                        preRoll.clear()
                        withContext(Dispatchers.Main) { listener.onPartial("Dinliyorum… / 正在听…") }
                    } else if (stopRequested || System.currentTimeMillis() > waitDeadline) {
                        return null
                    }
                } else {
                    speech.add(chunk)
                    spokenMs += FRAME_MS
                    silentMs = if (loud) 0 else silentMs + FRAME_MS
                    if (silentMs >= endSilence || stopRequested || spokenMs >= MAX_UTTERANCE_MS) break
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
        if (!speaking) return null
        val total = speech.sumOf { it.size }
        val out = ShortArray(total)
        var pos = 0
        for (c in speech) {
            c.copyInto(out, pos)
            pos += c.size
        }
        return out
    }

    /** @return (metin, algılanan dil) */
    private fun transcribe(pcm: ShortArray, forced: Lang?): Pair<String, Lang?> {
        val key = apiKey().trim()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("temperature", "0")
            .apply {
                if (forced != null) addFormDataPart("language", forced.code)
                // Bağlam ipucu: iki dilli iş görüşmesi, Çince basitleştirilmiş yazılsın.
                else addFormDataPart("prompt", "Türkçe ve Çince iş görüşmesi. 你好，我们谈一下价格。")
            }
            .addFormDataPart("file", "speech.wav", wav(pcm).toRequestBody("audio/wav".toMediaType()))
            .build()
        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("Ses tanıma için internet bağlantısı gerekli.", e)
        }
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                throw IOException(
                    when (r.code) {
                        401 -> "OpenAI anahtarı geçersiz. Ayarlardan kontrol edin."
                        429 -> "OpenAI kotası/bakiyesi yetersiz veya çok fazla istek."
                        else -> "Ses tanıma hatası (${r.code})."
                    }
                )
            }
            val node = mapper.readTree(text)
            val transcript = node.path("text").asText("").trim()
            val lang = when (node.path("language").asText("").lowercase()) {
                "turkish", "tr" -> Lang.TR
                "chinese", "zh", "mandarin" -> Lang.ZH
                else -> Lang.guessFromScript(transcript)
            }
            return transcript to lang
        }
    }

    private fun rms(chunk: ShortArray): Double {
        var sum = 0.0
        for (s in chunk) sum += s.toDouble() * s
        return sqrt(sum / chunk.size)
    }

    private fun wav(pcm: ShortArray): ByteArray {
        val dataLen = pcm.size * 2
        val out = ByteArrayOutputStream(44 + dataLen)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataLen); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataLen)
        }
        out.write(header.array())
        val data = ByteBuffer.allocate(dataLen).order(ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) data.putShort(s)
        out.write(data.array())
        return out.toByteArray()
    }

    /** Whisper'ın sessiz/gürültülü seste uydurduğu bilinen kalıplar. */
    private fun isHallucination(text: String): Boolean {
        val t = text.lowercase()
        return HALLUCINATIONS.any { t.contains(it) }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_MS = 30L
        const val FRAME = (SAMPLE_RATE * FRAME_MS / 1000).toInt()
        const val PRE_ROLL_FRAMES = 12
        const val MIN_SPEECH_RMS = 350.0
        const val MAX_UTTERANCE_MS = 30_000L

        val HALLUCINATIONS = listOf(
            "altyazı m.k",
            "abone olmayı unutmayın",
            "izlediğiniz için teşekkürler",
            "字幕由amara.org",
            "请不吝点赞",
            "订阅我的频道",
        )
    }
}

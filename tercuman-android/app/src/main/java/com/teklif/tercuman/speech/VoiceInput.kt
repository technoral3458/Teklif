package com.teklif.tercuman.speech

import com.teklif.tercuman.translate.Lang
import com.teklif.tercuman.translate.LangPair

/** Mikrofondan konuşmayı yazıya çeviren kaynak (telefonun tanıyıcısı veya Whisper). */
interface VoiceInput {

    /** Tüm geri çağrılar ana iş parçacığında gelir. */
    interface Listener {
        fun onListening()
        fun onPartial(text: String)
        fun onLevel(level: Float)
        fun onFinal(text: String, detected: Lang?)
        fun onSpeechError(message: String)
        /** Kullanıcı durdurdu ve hiç konuşma yoktu: sessizce kapan. */
        fun onIdle()
    }

    /**
     * @param forced null ise dil [pair] içinden otomatik algılanır
     * @param pair konuşan iki tarafın dilleri
     */
    fun start(forced: Lang?, pair: LangPair)

    /** Dinlemeyi bitir; o ana kadar konuşma varsa yazıya çevrilir. */
    fun stop()

    /** Dinlemeyi iptal et, sonuç verme. */
    fun cancel()

    fun destroy()

    companion object {
        /** Konuşma başlamazsa en fazla bu kadar beklenir. */
        const val MAX_WAIT_FOR_SPEECH_MS = 90_000L
    }
}

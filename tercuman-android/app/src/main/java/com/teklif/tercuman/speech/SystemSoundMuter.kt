package com.teklif.tercuman.speech

import android.content.Context
import android.media.AudioManager

/**
 * Telefonun ses tanıması her başlayış/bitişte sistem "bip"i çalar; sürekli dinlemede bu
 * rahatsız edici olur. Dinleme süresince ilgili ses kanalları susturulur, bitince geri açılır.
 * Zaten sessiz olan kanallara dokunulmaz; bazı cihazlarda (Rahatsız Etmeyin modu) izin
 * gerektiren kanallar sessizce atlanır.
 */
class SystemSoundMuter(context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mutedByUs = mutableSetOf<Int>()

    fun mute() {
        for (stream in STREAMS) {
            if (stream in mutedByUs) continue
            try {
                if (audio.isStreamMute(stream)) continue
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                mutedByUs += stream
            } catch (e: SecurityException) {
                // Bu kanalı değiştirme izni yok; olduğu gibi bırak.
            } catch (e: RuntimeException) {
            }
        }
    }

    fun unmute() {
        for (stream in mutedByUs.toList()) {
            try {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
            } catch (e: RuntimeException) {
            }
            mutedByUs -= stream
        }
    }

    private companion object {
        // Bip sesi cihaza göre bu kanallardan birinde çalar.
        val STREAMS = listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)
    }
}

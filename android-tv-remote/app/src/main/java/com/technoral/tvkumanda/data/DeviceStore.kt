package com.technoral.tvkumanda.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Daha once eslesilmis bir TV. */
data class SavedTv(
    val name: String,
    val host: String,
    val port: Int,
    /** Eslesme aninda gorulen TV sertifikasinin SHA-256 parmak izi. */
    val fingerprint: String,
)

/**
 * Eslesilmis TV'leri saklar. Istemci sertifikasi ayri bir PKCS#12 dosyasinda
 * tutulur; buradaki kayitlar yalnizca adres ve dogrulama bilgisidir.
 */
class DeviceStore(context: Context) {

    private val prefs = context.getSharedPreferences("tvkumanda", Context.MODE_PRIVATE)

    fun all(): List<SavedTv> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                SavedTv(
                    name = item.getString("name"),
                    host = item.getString("host"),
                    port = item.optInt("port", 6466),
                    fingerprint = item.getString("fingerprint"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun find(host: String): SavedTv? = all().firstOrNull { it.host == host }

    fun save(device: SavedTv) {
        val updated = all().filterNot { it.host == device.host } + device
        write(updated)
    }

    fun remove(host: String) = write(all().filterNot { it.host == host })

    var lastHost: String?
        get() = prefs.getString(KEY_LAST_HOST, null)
        set(value) = prefs.edit().putString(KEY_LAST_HOST, value).apply()

    private fun write(devices: List<SavedTv>) {
        val array = JSONArray()
        devices.forEach { device ->
            array.put(
                JSONObject()
                    .put("name", device.name)
                    .put("host", device.host)
                    .put("port", device.port)
                    .put("fingerprint", device.fingerprint),
            )
        }
        prefs.edit().putString(KEY_DEVICES, array.toString()).apply()
    }

    private companion object {
        const val KEY_DEVICES = "devices"
        const val KEY_LAST_HOST = "last_host"
    }
}

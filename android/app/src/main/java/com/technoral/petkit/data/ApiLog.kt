package com.technoral.petkit.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tek bir API çağrısının kaydı. Sorun ayıklama ekranında gösterilir. */
data class ApiLogEntry(
    val zaman: String,
    val yol: String,
    val istek: String,
    val yanit: String,
    val basarili: Boolean,
    val sureMs: Long
)

/**
 * Uygulama içi API günlüğü. Petkit'in resmi olmayan API'sinde alan adları
 * cihaz yazılımına göre değişebildiği için her istek/yanıt burada tutulur.
 */
object ApiLog {
    private const val SINIR = 200
    private val bicim = SimpleDateFormat("HH:mm:ss.SSS", Locale("tr", "TR"))

    private val _kayitlar = MutableStateFlow<List<ApiLogEntry>>(emptyList())
    val kayitlar: StateFlow<List<ApiLogEntry>> = _kayitlar

    fun ekle(yol: String, istek: String, yanit: String, basarili: Boolean, sureMs: Long) {
        val e = ApiLogEntry(bicim.format(Date()), yol, istek, yanit, basarili, sureMs)
        _kayitlar.value = (listOf(e) + _kayitlar.value).take(SINIR)
    }

    fun temizle() {
        _kayitlar.value = emptyList()
    }

    fun metinOlarak(): String = buildString {
        appendLine("Petkit Türkçe - API günlüğü")
        _kayitlar.value.asReversed().forEach { k ->
            appendLine("--------------------------------")
            appendLine("[${k.zaman}] ${k.yol}  (${k.sureMs} ms, ${if (k.basarili) "OK" else "HATA"})")
            appendLine("İstek : ${k.istek}")
            appendLine("Yanıt : ${k.yanit}")
        }
    }
}

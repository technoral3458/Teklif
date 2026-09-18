package com.teknoral.parametrik.core

import java.util.Locale

/**
 * Arayüzde ondalık ayırıcı virgüldür, sunucuya her zaman nokta ile gider.
 */
object Numbers {

    private val TR: Locale = Locale("tr", "TR")

    /** "12,5" / "12.5" / " 12 , 5 " → 12.5 ; boş veya geçersizse null. */
    fun parse(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        val cleaned = text.trim().replace(" ", "").replace(',', '.')
        if (cleaned == "." || cleaned == "-") return null
        return cleaned.toDoubleOrNull()
    }

    /** Sabit ondalıklı, virgüllü gösterim: 24.0 → "24,0" */
    fun format(value: Double, decimals: Int = 1): String =
        String.format(TR, "%.${decimals}f", value)

    /** Tam sayıysa ondalık gösterme: 24.0 → "24", 24.5 → "24,5" */
    fun compact(value: Double, decimals: Int = 2): String {
        val rounded = String.format(TR, "%.${decimals}f", value)
        return rounded.trimEnd('0').trimEnd(',').ifEmpty { "0" }
    }

    /** Sunucu form alanı için: her zaman nokta, bilimsel gösterim yok. */
    fun toServer(value: Double): String {
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) asLong.toString()
        else String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
    }

    /** Kullanıcıya gösterilecek metin alanı değeri. */
    fun toField(value: Double): String = compact(value, 3)
}

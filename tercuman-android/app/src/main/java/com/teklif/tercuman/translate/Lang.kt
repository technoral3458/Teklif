package com.teklif.tercuman.translate

import java.util.Locale

/** Uygulamanın desteklediği iki konuşma dili. */
enum class Lang(
    val code: String,
    val speechTag: String,
    val displayName: String,
    val englishName: String,
    val locale: Locale,
) {
    TR("tr", "tr-TR", "Türkçe", "Turkish", Locale("tr", "TR")),
    ZH("zh", "zh-CN", "中文", "Mandarin Chinese (Simplified)", Locale.SIMPLIFIED_CHINESE);

    val other: Lang get() = if (this == TR) ZH else TR

    companion object {
        fun fromCode(code: String?): Lang? = entries.firstOrNull { it.code == code }

        /**
         * Metnin yazı sistemine bakarak dili tahmin eder. Çince karakter (Han) oranı
         * yüksekse ZH, Latin harf ağırlıklıysa TR döner. Boş metinde null.
         */
        fun guessFromScript(text: String): Lang? {
            var han = 0
            var latin = 0
            for (ch in text) {
                when {
                    Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN -> han++
                    ch.isLetter() -> latin++
                }
            }
            if (han == 0 && latin == 0) return null
            return if (han >= latin) ZH else TR
        }
    }
}

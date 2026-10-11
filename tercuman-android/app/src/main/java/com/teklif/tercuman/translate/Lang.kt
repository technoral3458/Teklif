package com.teklif.tercuman.translate

import java.util.Locale

/** Yazı sistemi: tanınan metnin dilini kabaca ayırmak için. */
enum class Script { LATIN, HAN, CYRILLIC }

/** Uygulamanın desteklediği konuşma dilleri. */
enum class Lang(
    val code: String,
    val speechTag: String,
    val displayName: String,
    val englishName: String,
    val flag: String,
    val script: Script,
    val locale: Locale,
) {
    TR("tr", "tr-TR", "Türkçe", "Turkish", "🇹🇷", Script.LATIN, Locale("tr", "TR")),
    ZH("zh", "zh-CN", "中文", "Mandarin Chinese (Simplified)", "🇨🇳", Script.HAN, Locale.SIMPLIFIED_CHINESE),
    EN("en", "en-US", "English", "English", "🇬🇧", Script.LATIN, Locale.US),
    RU("ru", "ru-RU", "Русский", "Russian", "🇷🇺", Script.CYRILLIC, Locale("ru", "RU"));

    companion object {
        fun fromCode(code: String?): Lang? = entries.firstOrNull { it.code == code }

        /** Whisper'ın döndürdüğü dil adı ("turkish", "chinese", "zh" ...). */
        fun fromWhisperName(name: String): Lang? = when (name.lowercase()) {
            "turkish", "tr" -> TR
            "chinese", "zh", "mandarin" -> ZH
            "english", "en" -> EN
            "russian", "ru" -> RU
            else -> null
        }

        /**
         * Metnin yazı sistemine bakarak, adaylar arasından dili tahmin eder. Yazı sistemi
         * birden fazla adayı karşılıyorsa (örn. Türkçe ve İngilizce ikisi de Latin) null.
         */
        fun guessFromScript(text: String, candidates: Collection<Lang>): Lang? {
            var han = 0
            var cyrillic = 0
            var latin = 0
            for (ch in text) {
                when (Character.UnicodeScript.of(ch.code)) {
                    Character.UnicodeScript.HAN -> han++
                    Character.UnicodeScript.CYRILLIC -> cyrillic++
                    Character.UnicodeScript.LATIN -> latin++
                    else -> {}
                }
            }
            if (han == 0 && cyrillic == 0 && latin == 0) return null
            val script = when {
                han >= cyrillic && han >= latin -> Script.HAN
                cyrillic >= latin -> Script.CYRILLIC
                else -> Script.LATIN
            }
            return candidates.filter { it.script == script }.singleOrNull()
        }
    }
}

/** Konuşan iki taraf: [a] soldaki/alttaki (genelde kullanıcı), [b] sağdaki/üstteki (misafir). */
data class LangPair(val a: Lang, val b: Lang) {
    val both: List<Lang> get() = listOf(a, b)
    operator fun contains(lang: Lang) = lang == a || lang == b

    /** Verilen dilin karşısındaki dil. Çiftte yoksa [a] ya çevrilir. */
    fun other(lang: Lang): Lang = when (lang) {
        a -> b
        b -> a
        else -> a
    }

    companion object {
        val DEFAULT = LangPair(Lang.TR, Lang.ZH)
    }
}

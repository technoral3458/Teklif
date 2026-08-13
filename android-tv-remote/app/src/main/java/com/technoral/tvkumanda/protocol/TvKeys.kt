package com.technoral.tvkumanda.protocol

/**
 * TV'ye gonderilen tus kodlari. Degerler Android'in `KeyEvent` sabitleriyle
 * aynidir - protokol dogrudan Android tus kodu bekler.
 */
object TvKeys {

    const val POWER = 26
    const val TV_POWER = 177
    const val SLEEP = 223
    const val WAKEUP = 224

    const val HOME = 3
    const val BACK = 4
    const val MENU = 82
    const val SEARCH = 84
    const val ASSIST = 219
    const val SETTINGS = 176
    const val GUIDE = 172
    const val INFO = 165
    const val CAPTIONS = 175

    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23

    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val VOLUME_MUTE = 164

    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167

    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_STOP = 86
    const val MEDIA_NEXT = 87
    const val MEDIA_PREVIOUS = 88
    const val MEDIA_REWIND = 89
    const val MEDIA_FAST_FORWARD = 90

    /** Kaynak/giris secim ekranini acar. */
    const val TV_INPUT = 178
    const val TV = 170

    const val TV_INPUT_HDMI_1 = 243
    const val TV_INPUT_HDMI_2 = 244
    const val TV_INPUT_HDMI_3 = 245
    const val TV_INPUT_HDMI_4 = 246
    const val TV_INPUT_COMPOSITE_1 = 247
    const val TV_INPUT_COMPONENT_1 = 249
    const val TV_INPUT_VGA_1 = 251

    const val PROG_RED = 183
    const val PROG_GREEN = 184
    const val PROG_YELLOW = 185
    const val PROG_BLUE = 186

    const val DIGIT_0 = 7
    const val ENTER = 66
    const val DEL = 67
    const val SPACE = 62

    private const val KEY_A = 29

    fun digit(value: Int): Int {
        require(value in 0..9) { "Rakam 0-9 arasinda olmali" }
        return DIGIT_0 + value
    }

    /**
     * Yazi alanina karakter gonderebilmek icin harf/rakam esleme.
     * Turkce'ye ozgu harfler donanim klavyesi tus kodlariyla ifade edilemez,
     * bu yuzden en yakin ASCII karsiligina indirgenir.
     */
    fun keyCodeForChar(char: Char): Int? {
        val normalized = when (char) {
            'ı' -> 'i'; 'İ' -> 'i'
            'ş' -> 's'; 'Ş' -> 's'
            'ğ' -> 'g'; 'Ğ' -> 'g'
            'ü' -> 'u'; 'Ü' -> 'u'
            'ö' -> 'o'; 'Ö' -> 'o'
            'ç' -> 'c'; 'Ç' -> 'c'
            else -> char.lowercaseChar()
        }
        return when (normalized) {
            in 'a'..'z' -> KEY_A + (normalized - 'a')
            in '0'..'9' -> DIGIT_0 + (normalized - '0')
            ' ' -> SPACE
            else -> null
        }
    }
}

/** TV'de uygulama acmak icin kullanilan derin baglantilar. */
data class TvApp(
    val label: String,
    val link: String,
)

object TvApps {
    /**
     * `market://launch?id=<paket>` Android TV'de kurulu uygulamayi baslatir.
     * Kurulu degilse TV Play Store sayfasini acar.
     *
     * Baska bir uygulama eklemek icin listeye paket adiyla yeni satir eklemek yeterli.
     */
    val defaults = listOf(
        TvApp("YouTube", "https://www.youtube.com"),
        TvApp("Netflix", "market://launch?id=com.netflix.ninja"),
        TvApp("Prime Video", "market://launch?id=com.amazon.amazonvideo.livingroom"),
        TvApp("Disney+", "market://launch?id=com.disney.disneyplus"),
        TvApp("Spotify", "market://launch?id=com.spotify.tv.android"),
        TvApp("Exxen", "market://launch?id=com.exxen.android"),
        TvApp("BluTV", "market://launch?id=tv.blutv.androidtv"),
        TvApp("Play Store", "market://launch?id=com.android.vending"),
    )

    /** YouTube'da dogrudan arama sonucunu acar. */
    fun youtubeSearch(query: String): String =
        "https://www.youtube.com/results?search_query=" + urlEncode(query)

    private fun urlEncode(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val c = byte.toInt().toChar()
            when {
                c.isLetterOrDigit() && c.code < 128 -> append(c)
                c == '-' || c == '_' || c == '.' || c == '~' -> append(c)
                c == ' ' -> append('+')
                else -> append("%%%02X".format(byte))
            }
        }
    }
}

package com.teklif.tercuman.translate

/** Akış (streaming) sırasında yarım gelen JSON'dan tek bir metin alanını okur. */
internal object PartialJson {

    /**
     * `"key": "..."` değerini döndürür; değer henüz bitmemiş olabilir.
     * @return (şu ana kadarki değer, tamamlandı mı) ya da alan henüz başlamadıysa null
     */
    fun stringField(json: CharSequence, key: String): Pair<String, Boolean>? {
        val match = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"").find(json) ?: return null
        val sb = StringBuilder()
        var i = match.range.last + 1
        while (i < json.length) {
            val c = json[i]
            when (c) {
                '"' -> return sb.toString() to true
                '\\' -> {
                    if (i + 1 >= json.length) return sb.toString() to false
                    when (val n = json[i + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r', 'b', 'f' -> {}
                        'u' -> {
                            if (i + 5 >= json.length) return sb.toString() to false
                            val code = json.substring(i + 2, i + 6).toIntOrNull(16)
                            if (code != null) sb.append(code.toChar())
                            i += 4
                        }
                        else -> sb.append(n)
                    }
                    i += 2
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString() to false
    }
}

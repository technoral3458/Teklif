package com.technoral.petkit.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Oturum ve tercih deposu.
 *
 * Not: Veriler uygulamanın kendi özel alanında (app-private) tutulur; başka
 * uygulamalar okuyamaz. Şifre düz metin olarak DEĞİL, Petkit'in beklediği
 * MD5 özeti olarak saklanır (oturum düştüğünde otomatik yeniden giriş için).
 */
class Prefs(context: Context) {

    private val p: SharedPreferences =
        context.getSharedPreferences("petkit_tr", Context.MODE_PRIVATE)

    var session: String?
        get() = p.getString("session", null)
        set(v) = p.edit().putString("session", v).apply()

    var kullanici: String?
        get() = p.getString("kullanici", null)
        set(v) = p.edit().putString("kullanici", v).apply()

    var sifreMd5: String?
        get() = p.getString("sifre_md5", null)
        set(v) = p.edit().putString("sifre_md5", v).apply()

    var bolgeKodu: String
        get() = p.getString("bolge", "TR") ?: "TR"
        set(v) = p.edit().putString("bolge", v).apply()

    var sunucuUrl: String
        get() = p.getString("sunucu", PetkitBolge.VARSAYILAN.taban) ?: PetkitBolge.VARSAYILAN.taban
        set(v) = p.edit().putString("sunucu", v).apply()

    var kullaniciAdiGoster: String?
        get() = p.getString("ad", null)
        set(v) = p.edit().putString("ad", v).apply()

    /** Bir porsiyonun kaç grama karşılık geldiği (kullanıcı ayarlayabilir). */
    var porsiyonGram: Int
        get() = p.getInt("porsiyon_gram", 10)
        set(v) = p.edit().putInt("porsiyon_gram", v).apply()

    val girisYapildi: Boolean get() = !session.isNullOrBlank()

    fun oturumuTemizle() {
        p.edit().remove("session").apply()
    }

    fun hepsiniTemizle() {
        p.edit().clear().apply()
    }
}

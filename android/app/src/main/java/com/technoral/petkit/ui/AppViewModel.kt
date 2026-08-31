package com.technoral.petkit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.technoral.petkit.PetkitApp
import com.technoral.petkit.data.BesleyiciDurum
import com.technoral.petkit.data.Cihaz
import com.technoral.petkit.data.KayitSatiri
import com.technoral.petkit.data.PetkitBolge
import com.technoral.petkit.data.PetkitOturumHatasi
import com.technoral.petkit.data.PlanOgun
import com.technoral.petkit.data.alan
import com.technoral.petkit.data.nesne
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

enum class Ekran { GIRIS, CIHAZLAR, CIHAZ, KONSOL }

data class UiDurum(
    val ekran: Ekran = Ekran.GIRIS,
    val yukleniyor: Boolean = false,
    val hata: String? = null,
    val bilgi: String? = null,
    val cihazlar: List<Cihaz> = emptyList(),
    val seciliCihaz: Cihaz? = null,
    val detay: JsonObject? = null,
    val durum: BesleyiciDurum? = null,
    val planlar: List<PlanOgun> = emptyList(),
    val ayarlar: JsonObject? = null,
    val kayitlar: List<KayitSatiri> = emptyList(),
    val kayitGunFarki: Int = 0,
    val sonBeslemeId: String? = null,
    val konsolSonuc: String = "",
    val otomatikYenile: Boolean = true,
    val porsiyonGram: Int = 10,
    val kullaniciAdi: String? = null
)

class AppViewModel(uygulama: Application) : AndroidViewModel(uygulama) {

    private val app = uygulama as PetkitApp
    private val depo get() = app.depo
    private val prefs get() = app.prefs

    private val _durum = MutableStateFlow(UiDurum())
    val durum: StateFlow<UiDurum> = _durum.asStateFlow()

    private var yenilemeIsi: Job? = null

    init {
        _durum.update {
            it.copy(
                ekran = if (prefs.girisYapildi) Ekran.CIHAZLAR else Ekran.GIRIS,
                porsiyonGram = prefs.porsiyonGram,
                kullaniciAdi = prefs.kullaniciAdiGoster ?: prefs.kullanici
            )
        }
        if (prefs.girisYapildi) cihazlariYukle()
    }

    // ------------------------------------------------------------ genel

    fun mesajlariKapat() = _durum.update { it.copy(hata = null, bilgi = null) }

    fun ekranaGit(e: Ekran) = _durum.update { it.copy(ekran = e) }

    private fun hataMetni(e: Throwable): String =
        e.message?.takeIf { it.isNotBlank() } ?: "Beklenmeyen hata: ${e.javaClass.simpleName}"

    /**
     * Oturum düştüyse (ve şifre saklanmadığı için sessiz yeniden giriş
     * yapılamadıysa) kullanıcıyı giriş ekranına döndürür.
     */
    private fun oturumDustuMu(e: Throwable): Boolean {
        if (e !is PetkitOturumHatasi) return false
        yenilemeIsi?.cancel()
        prefs.oturumuTemizle()
        _durum.update {
            it.copy(
                ekran = Ekran.GIRIS,
                yukleniyor = false,
                seciliCihaz = null,
                cihazlar = emptyList(),
                hata = "Oturum süresi doldu, lütfen yeniden giriş yapın."
            )
        }
        return true
    }

    /** Şifre bu telefonda saklı mı? */
    fun sifreKayitliMi(): Boolean = prefs.sifreKayitli

    fun kayitliSifreyiSil() {
        prefs.sifreyiSakla = false
        _durum.update { it.copy(bilgi = "Kayıtlı şifre telefondan silindi.") }
    }

    private fun <T> calistir(
        basariMesaji: String? = null,
        sonrasindaYenile: Boolean = false,
        blok: suspend () -> T
    ) {
        viewModelScope.launch {
            _durum.update { it.copy(yukleniyor = true, hata = null, bilgi = null) }
            try {
                blok()
                _durum.update { it.copy(yukleniyor = false, bilgi = basariMesaji) }
                if (sonrasindaYenile) {
                    delay(1200)
                    cihazYenile(sessiz = true)
                }
            } catch (e: Exception) {
                if (!oturumDustuMu(e)) {
                    _durum.update { it.copy(yukleniyor = false, hata = hataMetni(e)) }
                }
            }
        }
    }

    // ------------------------------------------------------------ oturum

    fun girisYap(kullanici: String, sifre: String, bolge: PetkitBolge, sifreyiSakla: Boolean = true) {
        if (kullanici.isBlank() || sifre.isBlank()) {
            _durum.update { it.copy(hata = "E-posta/telefon ve şifre boş olamaz.") }
            return
        }
        viewModelScope.launch {
            _durum.update { it.copy(yukleniyor = true, hata = null, bilgi = null) }
            try {
                depo.girisYap(kullanici, sifre, bolge, sifreyiSakla)
                _durum.update {
                    it.copy(
                        yukleniyor = false,
                        ekran = Ekran.CIHAZLAR,
                        bilgi = "Giriş başarılı.",
                        kullaniciAdi = prefs.kullaniciAdiGoster ?: prefs.kullanici
                    )
                }
                cihazlariYukle()
            } catch (e: Exception) {
                // Giriş sırasındaki hata "oturum düştü" değildir; gerçek sebebi göster.
                _durum.update { it.copy(yukleniyor = false, hata = hataMetni(e)) }
            }
        }
    }

    fun cikisYap() {
        yenilemeIsi?.cancel()
        depo.cikisYap()
        _durum.value = UiDurum(ekran = Ekran.GIRIS)
    }

    // ------------------------------------------------------------ cihazlar

    fun cihazlariYukle() {
        viewModelScope.launch {
            _durum.update { it.copy(yukleniyor = true, hata = null) }
            try {
                val liste = depo.cihazlar()
                _durum.update { it.copy(yukleniyor = false, cihazlar = liste) }
                if (liste.isEmpty()) {
                    _durum.update { it.copy(hata = "Hesapta cihaz bulunamadı. Bölge/sunucu seçimini kontrol edin.") }
                }
            } catch (e: Exception) {
                if (!oturumDustuMu(e)) {
                    _durum.update { it.copy(yukleniyor = false, hata = hataMetni(e)) }
                }
            }
        }
    }

    fun cihazSec(cihaz: Cihaz) {
        _durum.update {
            it.copy(
                ekran = Ekran.CIHAZ,
                seciliCihaz = cihaz,
                detay = null,
                durum = null,
                planlar = emptyList(),
                kayitlar = emptyList(),
                kayitGunFarki = 0
            )
        }
        cihazYenile()
        kayitlariYukle()
        otomatikYenilemeBaslat()
    }

    fun cihazdanCik() {
        yenilemeIsi?.cancel()
        _durum.update { it.copy(ekran = Ekran.CIHAZLAR, seciliCihaz = null) }
    }

    fun cihazYenile(sessiz: Boolean = false) {
        val cihaz = _durum.value.seciliCihaz ?: return
        viewModelScope.launch {
            if (!sessiz) _durum.update { it.copy(yukleniyor = true, hata = null) }
            try {
                val detay = depo.detay(cihaz)
                _durum.update {
                    it.copy(
                        yukleniyor = false,
                        detay = detay,
                        durum = BesleyiciDurum.ayikla(cihaz.ad, detay),
                        planlar = depo.planlar(detay),
                        ayarlar = detay.alan("settings").nesne()
                    )
                }
            } catch (e: Exception) {
                if (!oturumDustuMu(e)) {
                    _durum.update {
                        it.copy(yukleniyor = false, hata = if (sessiz) it.hata else hataMetni(e))
                    }
                }
            }
        }
    }

    fun otomatikYenilemeDegistir(acik: Boolean) {
        _durum.update { it.copy(otomatikYenile = acik) }
        if (acik) otomatikYenilemeBaslat() else yenilemeIsi?.cancel()
    }

    private fun otomatikYenilemeBaslat() {
        yenilemeIsi?.cancel()
        yenilemeIsi = viewModelScope.launch {
            while (true) {
                delay(30_000)
                if (!_durum.value.otomatikYenile) break
                if (_durum.value.ekran != Ekran.CIHAZ) break
                cihazYenile(sessiz = true)
            }
        }
    }

    // ------------------------------------------------------------ besleme

    fun besle(porsiyon1: Int, porsiyon2: Int) {
        val cihaz = _durum.value.seciliCihaz ?: return
        if (porsiyon1 <= 0 && porsiyon2 <= 0) {
            _durum.update { it.copy(hata = "En az bir hazne için porsiyon girin.") }
            return
        }
        val g = prefs.porsiyonGram
        viewModelScope.launch {
            _durum.update { it.copy(yukleniyor = true, hata = null, bilgi = null) }
            try {
                val id = depo.elleBesle(cihaz, porsiyon1, porsiyon2)
                val ozet = buildList {
                    if (porsiyon1 > 0) add("Hazne 1: $porsiyon1 porsiyon (~${porsiyon1 * g} g)")
                    if (porsiyon2 > 0) add("Hazne 2: $porsiyon2 porsiyon (~${porsiyon2 * g} g)")
                }.joinToString(", ")
                _durum.update {
                    it.copy(yukleniyor = false, bilgi = "Besleme gönderildi → $ozet", sonBeslemeId = id)
                }
                delay(1500)
                cihazYenile(sessiz = true)
                kayitlariYukle(sessiz = true)
            } catch (e: Exception) {
                if (!oturumDustuMu(e)) {
                    _durum.update { it.copy(yukleniyor = false, hata = hataMetni(e)) }
                }
            }
        }
    }

    fun beslemeIptal() {
        val cihaz = _durum.value.seciliCihaz ?: return
        val id = _durum.value.sonBeslemeId
        if (id.isNullOrBlank()) {
            _durum.update { it.copy(hata = "İptal edilecek besleme kimliği yok.") }
            return
        }
        calistir("Besleme iptal isteği gönderildi.", sonrasindaYenile = true) {
            depo.beslemeIptal(cihaz, id)
        }
    }

    // ------------------------------------------------------------ plan

    fun planKaydet(saat: Int, dakika: Int, p1: Int, p2: Int, gunler: Set<Int>, ad: String?) {
        val cihaz = _durum.value.seciliCihaz ?: return
        calistir("Öğün kaydedildi.", sonrasindaYenile = true) {
            depo.planKaydet(cihaz, saat, dakika, p1, p2, gunler, ad)
        }
    }

    fun planSil(ogun: PlanOgun) {
        val cihaz = _durum.value.seciliCihaz ?: return
        calistir("Öğün silindi.", sonrasindaYenile = true) { depo.planSil(cihaz, ogun) }
    }

    fun planGeriAl(ogun: PlanOgun) {
        val cihaz = _durum.value.seciliCihaz ?: return
        calistir("Öğün geri alındı.", sonrasindaYenile = true) { depo.planGeriAl(cihaz, ogun) }
    }

    // ------------------------------------------------------------ kayıtlar

    fun kayitGunuDegistir(fark: Int) {
        _durum.update { it.copy(kayitGunFarki = fark.coerceIn(-30, 0)) }
        kayitlariYukle()
    }

    fun kayitlariYukle(sessiz: Boolean = false) {
        val cihaz = _durum.value.seciliCihaz ?: return
        val gun = depo.gunKodu(_durum.value.kayitGunFarki)
        viewModelScope.launch {
            if (!sessiz) _durum.update { it.copy(yukleniyor = true) }
            try {
                val liste = depo.kayitlar(cihaz, gun)
                _durum.update { it.copy(yukleniyor = false, kayitlar = liste) }
            } catch (e: Exception) {
                if (!oturumDustuMu(e)) {
                    _durum.update {
                        it.copy(yukleniyor = false, hata = if (sessiz) it.hata else hataMetni(e))
                    }
                }
            }
        }
    }

    fun gunEtiketi(): String = depo.gunEtiketi(depo.gunKodu(_durum.value.kayitGunFarki))

    // ------------------------------------------------------------ ayarlar

    fun ayarDegistir(anahtar: String, deger: Any, etiket: String) {
        val cihaz = _durum.value.seciliCihaz ?: return
        calistir("$etiket güncellendi.", sonrasindaYenile = true) {
            depo.ayarGuncelle(cihaz, mapOf(anahtar to deger))
        }
    }

    fun ayarHamGonder(kvJson: String) {
        val cihaz = _durum.value.seciliCihaz ?: return
        if (kvJson.isBlank()) {
            _durum.update { it.copy(hata = "Gönderilecek JSON boş.") }
            return
        }
        calistir("Ayar gönderildi.", sonrasindaYenile = true) {
            depo.ayarHamGuncelle(cihaz, kvJson.trim())
        }
    }

    fun nemAliciSifirla() {
        val cihaz = _durum.value.seciliCihaz ?: return
        calistir("Nem alıcı sayacı sıfırlandı.", sonrasindaYenile = true) {
            depo.nemAliciSifirla(cihaz)
        }
    }

    fun komutGonder(tip: String, kvJson: String, etiket: String) {
        val cihaz = _durum.value.seciliCihaz ?: return
        calistir("$etiket gönderildi.", sonrasindaYenile = true) {
            depo.komutGonder(cihaz, tip, kvJson)
        }
    }

    fun adDegistir(yeniAd: String) {
        val cihaz = _durum.value.seciliCihaz ?: return
        if (yeniAd.isBlank()) return
        calistir("Cihaz adı değiştirildi.", sonrasindaYenile = true) {
            depo.adDegistir(cihaz, yeniAd.trim())
        }
    }

    fun porsiyonGramAyarla(gram: Int) {
        prefs.porsiyonGram = gram.coerceIn(1, 100)
        _durum.update { it.copy(porsiyonGram = prefs.porsiyonGram) }
    }

    // ------------------------------------------------------------ konsol

    fun hamCagri(yol: String, parametreMetni: String) {
        if (yol.isBlank()) {
            _durum.update { it.copy(hata = "Uç nokta (yol) boş olamaz.") }
            return
        }
        val parametreler = parametreMetni.split("\n", "&")
            .mapNotNull { satir ->
                val t = satir.trim()
                if (t.isEmpty()) null
                else {
                    val i = t.indexOf('=')
                    if (i <= 0) null else t.substring(0, i).trim() to t.substring(i + 1).trim()
                }
            }.toMap()
        viewModelScope.launch {
            _durum.update { it.copy(yukleniyor = true, hata = null, konsolSonuc = "Gönderiliyor...") }
            try {
                val sonuc = depo.hamCagri(yol.trim(), parametreler)
                _durum.update { it.copy(yukleniyor = false, konsolSonuc = sonuc) }
            } catch (e: Exception) {
                _durum.update {
                    it.copy(yukleniyor = false, konsolSonuc = "HATA: ${hataMetni(e)}")
                }
            }
        }
    }

    fun seciliCihazYolu(): String = _durum.value.seciliCihaz?.tip ?: "d4sh"
    fun seciliCihazId(): String = _durum.value.seciliCihaz?.id ?: ""
    fun bugunKodu(): String = depo.bugunKodu()
    fun hamDetayJson(): String = depo.api.guzelJson(_durum.value.detay)
}

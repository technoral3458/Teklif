package com.technoral.ucusbul.domain

/** IATA taşıyıcı kodu -> okunabilir ad ve havayolunun kendi satış sitesi. */
object Airlines {

    private data class Info(val name: String, val site: String)

    private val map: Map<String, Info> = mapOf(
        // Türkiye
        "TK" to Info("Turkish Airlines", "https://www.turkishairlines.com"),
        "PC" to Info("Pegasus", "https://www.flypgs.com"),
        "XQ" to Info("SunExpress", "https://www.sunexpress.com"),
        "VF" to Info("AJet", "https://www.ajet.com"),
        "8Q" to Info("Onur Air", "https://www.onurair.com"),
        // Körfez / Orta Doğu
        "QR" to Info("Qatar Airways", "https://www.qatarairways.com"),
        "EK" to Info("Emirates", "https://www.emirates.com"),
        "EY" to Info("Etihad", "https://www.etihad.com"),
        "SV" to Info("Saudia", "https://www.saudia.com"),
        "GF" to Info("Gulf Air", "https://www.gulfair.com"),
        "WY" to Info("Oman Air", "https://www.omanair.com"),
        "KU" to Info("Kuwait Airways", "https://www.kuwaitairways.com"),
        "MS" to Info("EgyptAir", "https://www.egyptair.com"),
        "RJ" to Info("Royal Jordanian", "https://www.rj.com"),
        "FZ" to Info("flydubai", "https://www.flydubai.com"),
        "G9" to Info("Air Arabia", "https://www.airarabia.com"),
        "IR" to Info("Iran Air", "https://www.iranair.com"),
        // Çin ve Doğu Asya
        "CA" to Info("Air China", "https://www.airchina.com"),
        "MU" to Info("China Eastern", "https://www.ceair.com"),
        "CZ" to Info("China Southern", "https://www.csair.com"),
        "HU" to Info("Hainan Airlines", "https://www.hainanairlines.com"),
        "ZH" to Info("Shenzhen Airlines", "https://www.shenzhenair.com"),
        "3U" to Info("Sichuan Airlines", "https://www.sichuanair.com"),
        "MF" to Info("Xiamen Air", "https://www.xiamenair.com"),
        "9C" to Info("Spring Airlines", "https://en.ch.com"),
        "SC" to Info("Shandong Airlines", "https://www.sda.cn"),
        "GS" to Info("Tianjin Airlines", "https://www.tianjin-air.com"),
        "KN" to Info("China United", "https://www.flycua.com"),
        "FM" to Info("Shanghai Airlines", "https://www.ceair.com"),
        "CX" to Info("Cathay Pacific", "https://www.cathaypacific.com"),
        "KA" to Info("Cathay Dragon", "https://www.cathaypacific.com"),
        "NH" to Info("ANA", "https://www.ana.co.jp"),
        "JL" to Info("Japan Airlines", "https://www.jal.co.jp"),
        "KE" to Info("Korean Air", "https://www.koreanair.com"),
        "OZ" to Info("Asiana", "https://flyasiana.com"),
        "SQ" to Info("Singapore Airlines", "https://www.singaporeair.com"),
        "TG" to Info("Thai Airways", "https://www.thaiairways.com"),
        "MH" to Info("Malaysia Airlines", "https://www.malaysiaairlines.com"),
        "VN" to Info("Vietnam Airlines", "https://www.vietnamairlines.com"),
        "AK" to Info("AirAsia", "https://www.airasia.com"),
        // Avrupa
        "LH" to Info("Lufthansa", "https://www.lufthansa.com"),
        "AF" to Info("Air France", "https://www.airfrance.com"),
        "KL" to Info("KLM", "https://www.klm.com"),
        "BA" to Info("British Airways", "https://www.britishairways.com"),
        "IB" to Info("Iberia", "https://www.iberia.com"),
        "AZ" to Info("ITA Airways", "https://www.ita-airways.com"),
        "LX" to Info("SWISS", "https://www.swiss.com"),
        "OS" to Info("Austrian", "https://www.austrian.com"),
        "SN" to Info("Brussels Airlines", "https://www.brusselsairlines.com"),
        "SK" to Info("SAS", "https://www.flysas.com"),
        "AY" to Info("Finnair", "https://www.finnair.com"),
        "LO" to Info("LOT", "https://www.lot.com"),
        "OK" to Info("Czech Airlines", "https://www.csa.cz"),
        "RO" to Info("TAROM", "https://www.tarom.ro"),
        "JU" to Info("Air Serbia", "https://www.airserbia.com"),
        "A3" to Info("Aegean", "https://www.aegeanair.com"),
        "TP" to Info("TAP Portugal", "https://www.flytap.com"),
        "FR" to Info("Ryanair", "https://www.ryanair.com"),
        "U2" to Info("easyJet", "https://www.easyjet.com"),
        "W6" to Info("Wizz Air", "https://wizzair.com"),
        "VY" to Info("Vueling", "https://www.vueling.com"),
        "EW" to Info("Eurowings", "https://www.eurowings.com"),
        "SU" to Info("Aeroflot", "https://www.aeroflot.ru"),
        "S7" to Info("S7 Airlines", "https://www.s7.ru"),
        "UX" to Info("Air Europa", "https://www.aireuropa.com"),
        // Orta Asya / Kafkasya
        "KC" to Info("Air Astana", "https://airastana.com"),
        "J2" to Info("Azerbaijan Airlines", "https://www.azal.az"),
        "HY" to Info("Uzbekistan Airways", "https://www.uzairways.com"),
        "QH" to Info("Kyrgyzstan", "https://www.airkg.com"),
        "T5" to Info("Turkmenistan Airlines", "https://turkmenistanairlines.tm"),
        // Amerika
        "AA" to Info("American Airlines", "https://www.aa.com"),
        "UA" to Info("United", "https://www.united.com"),
        "DL" to Info("Delta", "https://www.delta.com"),
        "AC" to Info("Air Canada", "https://www.aircanada.com")
    )

    fun name(code: String): String = map[code.uppercase()]?.name ?: code.uppercase()

    fun site(code: String): String? = map[code.uppercase()]?.site

    fun label(codes: List<String>): String {
        if (codes.isEmpty()) return "Havayolu bilgisi yok"
        return codes.distinct().joinToString(" + ") { name(it) }
    }
}

package com.teklif.yolcu.geo

/** Bilinen bir kara sınır kapısı. [a] ve [b] iki taraftaki ülke kodları. */
data class BorderCrossing(
    val name: String,
    val a: String,
    val b: String,
    val lat: Double,
    val lon: Double,
    /** Ana güzergâh (otoyol / E-yolu) ise. */
    val major: Boolean = true,
) {
    val position get() = LatLon(lat, lon)
    fun joins(x: String, y: String) = (a == x && b == y) || (a == y && b == x)
}

/**
 * Avrupa'daki başlıca kara sınır kapıları. Konumlar yaklaşık (birkaç yüz metre); rota üzerinde
 * bulunan sınır geçiş noktasına en yakın kapının adını göstermek için kullanılır.
 */
object BorderCrossings {
    val all: List<BorderCrossing> = listOf(
        // Türkiye
        BorderCrossing("Kapıkule – Kapitan Andreevo", "TR", "BG", 41.7194, 26.3556),
        BorderCrossing("Hamzabeyli – Lesovo", "TR", "BG", 41.9472, 26.5983, false),
        BorderCrossing("Dereköy – Malko Tarnovo", "TR", "BG", 41.9853, 27.4486, false),
        BorderCrossing("İpsala – Kipi", "TR", "GR", 40.9325, 26.3078),
        BorderCrossing("Pazarkule – Kastanies", "TR", "GR", 41.6758, 26.5083, false),
        BorderCrossing("Sarp – Sarpi", "TR", "GE", 41.5211, 41.5464),
        BorderCrossing("Türkgözü – Vale", "TR", "GE", 41.5789, 42.8486, false),
        BorderCrossing("Dilucu – Sadarak", "TR", "AZ", 39.6539, 44.8122, false),
        BorderCrossing("Gürbulak – Bazargan", "TR", "IR", 39.4397, 44.3728),
        BorderCrossing("Habur – İbrahim Halil", "TR", "IQ", 37.1261, 42.5875),
        BorderCrossing("Cilvegözü – Bab al-Hawa", "TR", "SY", 36.2389, 36.6894, false),
        // Bulgaristan
        BorderCrossing("Kalotina – Gradina", "BG", "RS", 42.9956, 22.8697),
        BorderCrossing("Kulata – Promachonas", "BG", "GR", 41.3844, 23.3597),
        BorderCrossing("Ilinden – Exochi", "BG", "GR", 41.4058, 23.8653, false),
        BorderCrossing("Ruse – Giurgiu (Tuna Köprüsü)", "BG", "RO", 43.8742, 25.9594),
        BorderCrossing("Vidin – Calafat (Tuna Köprüsü 2)", "BG", "RO", 43.9897, 22.9211),
        BorderCrossing("Gyueshevo – Deve Bair", "BG", "MK", 42.2503, 22.4756),
        BorderCrossing("Durankulak – Vama Veche", "BG", "RO", 43.7297, 28.5708, false),
        // Yunanistan
        BorderCrossing("Evzoni – Bogorodica", "GR", "MK", 41.0994, 22.5614),
        BorderCrossing("Kakavia – Kakavijë", "GR", "AL", 39.9058, 20.3317),
        // Kuzey Makedonya – Sırbistan – Kosova
        BorderCrossing("Tabanovce – Preševo", "MK", "RS", 42.2625, 21.7069),
        BorderCrossing("Blace – Hani i Elezit", "MK", "XK", 42.1533, 21.2942, false),
        BorderCrossing("Merdare", "RS", "XK", 42.9319, 21.0975, false),
        // Sırbistan
        BorderCrossing("Horgoš – Röszke", "RS", "HU", 46.1792, 19.9914),
        BorderCrossing("Kelebija – Tompa", "RS", "HU", 46.1569, 19.5853, false),
        BorderCrossing("Batrovci – Bajakovo", "RS", "HR", 45.0458, 19.1086),
        BorderCrossing("Sremska Rača – Rača", "RS", "BA", 44.9122, 19.3022, false),
        BorderCrossing("Vatin – Moravița", "RS", "RO", 45.2842, 21.2742, false),
        // Romanya
        BorderCrossing("Nădlac II – Csanádpalota (A1/M43)", "RO", "HU", 46.1728, 20.6628),
        BorderCrossing("Nădlac – Nagylak", "RO", "HU", 46.1756, 20.7128, false),
        BorderCrossing("Borș II – Nagykereki (A3/M4)", "RO", "HU", 47.1328, 21.7739),
        BorderCrossing("Borș – Ártánd", "RO", "HU", 47.1211, 21.8094, false),
        BorderCrossing("Petea – Csengersima", "RO", "HU", 47.8497, 22.6431, false),
        BorderCrossing("Siret – Porubne", "RO", "UA", 47.9994, 26.0553, false),
        BorderCrossing("Albița – Leușeni", "RO", "MD", 46.8103, 28.1414, false),
        BorderCrossing("Giurgiulești – Galați", "MD", "RO", 45.4747, 28.2039, false),
        // Hırvatistan
        BorderCrossing("Bregana – Obrežje (A3/A2)", "HR", "SI", 45.8481, 15.6897),
        BorderCrossing("Macelj – Gruškovje (A2/A4)", "HR", "SI", 46.2733, 15.8631),
        BorderCrossing("Goričan – Letenye (A4/M7)", "HR", "HU", 46.4414, 16.6758),
        BorderCrossing("Rupa – Jelšane (A7/H4)", "HR", "SI", 45.4922, 14.2639),
        BorderCrossing("Stara Gradiška – Gradiška", "HR", "BA", 45.1511, 17.2519, false),
        BorderCrossing("Slavonski Brod – Brod", "HR", "BA", 45.1511, 17.9997, false),
        BorderCrossing("Nova Sela – Bijača (A1/A1)", "HR", "BA", 43.0769, 17.5731, false),
        BorderCrossing("Karasovići – Debeli Brijeg", "HR", "ME", 42.4919, 18.4950, false),
        // Slovenya – Avusturya / İtalya / Macaristan
        BorderCrossing("Šentilj – Spielfeld (A1/A9)", "SI", "AT", 46.6886, 15.6436),
        BorderCrossing("Karavanke Tüneli (A2/A11)", "SI", "AT", 46.4575, 14.0319),
        BorderCrossing("Fernetiči – Fernetti (A3/A4)", "SI", "IT", 45.7036, 13.8350),
        BorderCrossing("Vrtojba – Sant'Andrea", "SI", "IT", 45.9114, 13.6283, false),
        BorderCrossing("Pince – Tornyiszentmiklós (A5/M70)", "SI", "HU", 46.5428, 16.5203),
        // Macaristan – Avusturya / Slovakya / Ukrayna
        BorderCrossing("Hegyeshalom – Nickelsdorf (M1/A4)", "HU", "AT", 47.9306, 17.1181),
        BorderCrossing("Rajka – Čunovo (M15/D4)", "HU", "SK", 48.0353, 17.2264),
        BorderCrossing("Parassapuszta – Šahy", "HU", "SK", 48.0592, 18.9586, false),
        BorderCrossing("Záhony – Çop", "HU", "UA", 48.4064, 22.1739, false),
        // Avusturya – Almanya / İtalya / Çekya / İsviçre
        BorderCrossing("Walserberg (A1/A8)", "AT", "DE", 47.7917, 12.9397),
        BorderCrossing("Suben (A8/A3)", "AT", "DE", 48.4139, 13.4406),
        BorderCrossing("Kufstein – Kiefersfelden (A12/A93)", "AT", "DE", 47.6117, 12.1806),
        BorderCrossing("Brenner / Brennero (A13/A22)", "AT", "IT", 47.0056, 11.5069),
        BorderCrossing("Arnoldstein – Tarvisio (A2/A23)", "AT", "IT", 46.5472, 13.6342),
        BorderCrossing("Drasenhofen – Mikulov (A5/52)", "AT", "CZ", 48.7808, 16.6450),
        BorderCrossing("Kleinhaugsdorf – Hatě", "AT", "CZ", 48.7897, 16.0308, false),
        BorderCrossing("Hörbranz – Lindau (A14/A96)", "AT", "DE", 47.5547, 9.7511),
        BorderCrossing("Hohenems – Diepoldsau", "AT", "CH", 47.3850, 9.6583, false),
        // Almanya
        BorderCrossing("Kehl – Strasbourg (Europabrücke)", "DE", "FR", 48.5778, 7.7989),
        BorderCrossing("Saarbrücken – Forbach (A6/A320)", "DE", "FR", 49.2108, 6.9119),
        BorderCrossing("Aachen – Lichtenbusch (A44/A3)", "DE", "BE", 50.7150, 6.1394),
        BorderCrossing("Aachen – Vaals/Bocholtz (A4/A76)", "DE", "NL", 50.8411, 6.0239),
        BorderCrossing("Venlo (A40/A67)", "DE", "NL", 51.3683, 6.2003),
        BorderCrossing("Elten – Zevenaar (A3/A12)", "DE", "NL", 51.8694, 6.1528),
        BorderCrossing("Weil am Rhein – Basel (A5/A2)", "DE", "CH", 47.5914, 7.6097),
        BorderCrossing("Waidhaus – Rozvadov (A6/D5)", "DE", "CZ", 49.6383, 12.5472),
        BorderCrossing("Schirnding – Pomezí", "DE", "CZ", 50.0806, 12.2428, false),
        BorderCrossing("Breitenau – Petrovice (A17/D8)", "DE", "CZ", 50.7842, 13.9311),
        BorderCrossing("Frankfurt (Oder) – Świecko (A12/A2)", "DE", "PL", 52.3078, 14.5725),
        BorderCrossing("Ludwigsdorf – Jędrzychowice (A4/A4)", "DE", "PL", 51.2072, 15.0383),
        BorderCrossing("Pomellen – Kołbaskowo (A11/A6)", "DE", "PL", 53.3442, 14.4231, false),
        BorderCrossing("Flensburg – Padborg (A7/E45)", "DE", "DK", 54.8181, 9.3614),
        BorderCrossing("Perl – Schengen (A8/A13)", "DE", "LU", 49.4703, 6.3683, false),
        // Çekya – Slovakya – Polonya
        BorderCrossing("Břeclav – Kúty (D2/D2)", "CZ", "SK", 48.7322, 17.0208),
        BorderCrossing("Starý Hrozenkov – Drietoma", "CZ", "SK", 48.9950, 17.8747, false),
        BorderCrossing("Bohumín – Gorzyczki (D1/A1)", "CZ", "PL", 49.9372, 18.3550),
        BorderCrossing("Český Těšín – Cieszyn", "CZ", "PL", 49.7450, 18.6250, false),
        BorderCrossing("Zwardoń – Skalité (S1/D3)", "PL", "SK", 49.5061, 18.9839, false),
        BorderCrossing("Chyżne – Trstená", "PL", "SK", 49.4208, 19.7294, false),
        BorderCrossing("Medyka – Şehıni", "PL", "UA", 49.8047, 22.9356),
        BorderCrossing("Korczowa – Krakovets (A4)", "PL", "UA", 49.9542, 23.1261),
        BorderCrossing("Dorohusk – Yagodyn", "PL", "UA", 51.1706, 23.8250, false),
        BorderCrossing("Terespol – Brest", "PL", "BY", 52.0742, 23.6194, false),
        BorderCrossing("Budzisko – Kalvarija (S61/A5)", "PL", "LT", 54.3606, 23.0564),
        // Baltık – Finlandiya
        BorderCrossing("Saločiai – Grenctāle (A10/A7)", "LT", "LV", 56.4350, 24.3914),
        BorderCrossing("Ainaži – Ikla (A1/4)", "LV", "EE", 57.8700, 24.3678),
        BorderCrossing("Narva – Ivangorod", "EE", "RU", 59.3772, 28.1983, false),
        BorderCrossing("Vaalimaa – Torfyanovka", "FI", "RU", 60.5881, 27.8389, false),
        // Fransa – İtalya – İspanya – Belçika – İsviçre
        BorderCrossing("Menton – Ventimiglia (A8/A10)", "FR", "IT", 43.7894, 7.5361),
        BorderCrossing("Fréjus Tüneli (A43/A32)", "FR", "IT", 45.1272, 6.7044),
        BorderCrossing("Mont Blanc Tüneli (A40/A5)", "FR", "IT", 45.8450, 6.9344),
        BorderCrossing("Le Perthus – La Jonquera (A9/AP-7)", "FR", "ES", 42.4642, 2.8664),
        BorderCrossing("Hendaye – Irun (A63/AP-8)", "FR", "ES", 43.3444, -1.7703),
        BorderCrossing("Lille – Rekkem (A22/A17)", "FR", "BE", 50.7444, 3.1831),
        BorderCrossing("Valenciennes – Hensies (A2/E19)", "FR", "BE", 50.4425, 3.6556, false),
        BorderCrossing("Bâle – Saint-Louis (A35/A3)", "FR", "CH", 47.5850, 7.5600),
        BorderCrossing("Genève – Bardonnex (A1/A41)", "CH", "FR", 46.1403, 6.1125),
        BorderCrossing("Calais – Folkestone (Eurotünel)", "FR", "GB", 50.9228, 1.8117),
        // İsviçre – İtalya / Almanya / Avusturya
        BorderCrossing("Chiasso – Como (A2/A9)", "CH", "IT", 45.8339, 9.0319),
        BorderCrossing("Gran San Bernardo Tüneli", "CH", "IT", 45.8689, 7.1706, false),
        BorderCrossing("Thayngen – Bietingen (A4/A81)", "CH", "DE", 47.7536, 8.7367, false),
        BorderCrossing("St. Margrethen – Höchst (A1/A14)", "CH", "AT", 47.4544, 9.6456),
        // Belçika – Hollanda – Lüksemburg
        BorderCrossing("Hazeldonk – Meer (A16/E19)", "NL", "BE", 51.4828, 4.7206),
        BorderCrossing("Eijsden – Visé (A2/E25)", "NL", "BE", 50.7556, 5.6986, false),
        BorderCrossing("Sterpenich – Steinfort (E411/A6)", "BE", "LU", 49.6711, 5.8653),
        // İspanya – Portekiz
        BorderCrossing("Vilar Formoso – Fuentes de Oñoro (A25/A62)", "PT", "ES", 40.6094, -6.8156),
        BorderCrossing("Elvas – Badajoz (A6/A5)", "PT", "ES", 38.8781, -7.0294),
        BorderCrossing("Valença – Tui (A3/A55)", "PT", "ES", 42.0306, -8.6456),
        BorderCrossing("Vila Real de Santo António – Ayamonte (A22/A49)", "PT", "ES", 37.2147, -7.4281),
        // Danimarka – İsveç – Norveç
        BorderCrossing("Öresund Köprüsü (E20)", "DK", "SE", 55.6083, 12.8333),
        BorderCrossing("Svinesund (E6)", "SE", "NO", 59.0983, 11.2531),
        // Kafkasya
        BorderCrossing("Sadakhlo – Bagratashen", "GE", "AM", 41.2264, 44.8375, false),
        BorderCrossing("Red Bridge – Tsiteli Khidi", "GE", "AZ", 41.3436, 45.0944, false),
        BorderCrossing("Kazbegi – Verkhny Lars", "GE", "RU", 42.7408, 44.6289, false),
    )

    /** İki ülke arasındaki, verilen noktaya en yakın kapı (en fazla [maxKm] km uzakta). */
    fun nearest(p: LatLon, a: String, b: String, maxKm: Double = 25.0): BorderCrossing? =
        all.filter { it.joins(a, b) }
            .map { it to Geo.distance(p, it.position) }
            .filter { it.second <= maxKm * 1000 }
            .minByOrNull { it.second }?.first

    /** Bir ülkenin tüm kapıları, verilen noktaya uzaklığa göre. */
    fun nearestFrom(p: LatLon, country: String, limit: Int = 5): List<Pair<BorderCrossing, Double>> =
        all.filter { it.a == country || it.b == country }
            .map { it to Geo.distance(p, it.position) }
            .sortedBy { it.second }
            .take(limit)
}

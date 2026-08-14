package com.technoral.ucusbul.data.provider

import com.technoral.ucusbul.core.Prefs

/**
 * Etkin sağlayıcıların kaydı. Amadeus ve Travelpayouts anahtarı girilmişse ikisi de
 * paralel taranır ve sonuçlar birleştirilir; hiçbiri yoksa demo verisine düşülür.
 */
object Providers {

    @Volatile private var amadeus: AmadeusProvider? = null
    @Volatile private var travelpayouts: TravelpayoutsProvider? = null
    private val demo = DemoProvider()

    fun active(prefs: Prefs): List<FlightProvider> {
        val list = ArrayList<FlightProvider>(2)

        val a = amadeus ?: AmadeusProvider(prefs).also { amadeus = it }
        if (a.isConfigured()) list.add(a)

        val t = travelpayouts ?: TravelpayoutsProvider(prefs).also { travelpayouts = it }
        if (t.isConfigured()) list.add(t)

        if (list.isEmpty() && prefs.demoFallback) list.add(demo)
        return list
    }

    /** Ayarlarda anahtar değişince önbelleklenen token'ları at. */
    fun reset() {
        amadeus = null
        travelpayouts = null
    }
}

package com.technoral.petkit

import android.app.Application
import com.technoral.petkit.data.PetkitApi
import com.technoral.petkit.data.PetkitRepository
import com.technoral.petkit.data.Prefs

class PetkitApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var depo: PetkitRepository
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        depo = PetkitRepository(PetkitApi(prefs), prefs)
        ornek = this
    }

    companion object {
        lateinit var ornek: PetkitApp
            private set
    }
}

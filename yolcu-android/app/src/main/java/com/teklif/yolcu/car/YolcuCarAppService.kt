package com.teklif.yolcu.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.teklif.yolcu.nav.Nav

/** Android Auto giriş noktası. */
class YolcuCarAppService : CarAppService() {

    // Play Store dışından kurulduğu için tüm Android Auto ana bilgisayarlarına izin verilir.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = NavSession()
}

class NavSession : Session() {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                Nav.init(carContext)
                Nav.setCarVisible(true)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                Nav.setCarVisible(false)
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen = NavMapScreen(carContext)
}

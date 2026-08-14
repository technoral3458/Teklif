package com.technoral.ucusbul

import android.app.Application
import com.technoral.ucusbul.work.Notifications

class UcusBulApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
    }
}

package com.adblocker.ytmusic

import android.app.Application
import com.adblocker.ytmusic.service.FilterNotifications

class AdBlockerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FilterNotifications.ensureChannel(this)
    }
}

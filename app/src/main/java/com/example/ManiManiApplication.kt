package com.example

import android.app.Application
import com.example.service.PushNotificationHelper

class ManiManiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PushNotificationHelper.createNotificationChannel(this)
    }
}

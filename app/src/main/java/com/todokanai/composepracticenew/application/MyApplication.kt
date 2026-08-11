package com.todokanai.composepracticenew.application

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.todokanai.composepracticenew.service.FtpForegroundService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            FtpForegroundService.CHANNEL_ID,
            "FTP 연결",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

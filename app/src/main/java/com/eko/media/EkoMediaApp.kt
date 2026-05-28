package com.eko.media

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class EkoMediaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOAD,
                "EKO Download Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active download progress"
            }

            val notifManager = getSystemService(NotificationManager::class.java)
            notifManager.createNotificationChannel(downloadChannel)
        }
    }

    companion object {
        const val CHANNEL_DOWNLOAD = "eko_download_channel"
    }
}

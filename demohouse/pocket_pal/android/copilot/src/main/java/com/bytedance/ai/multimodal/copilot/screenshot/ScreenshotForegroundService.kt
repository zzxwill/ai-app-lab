package com.bytedance.ai.multimodal.copilot.screenshot

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bytedance.ai.multimodal.common.log.FLogger

class ScreenshotForegroundService: Service() {

    companion object{
        private const val TAG = "ScreenshotForegroundService"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "media_projection_channel"
    }

    private var targetClassName: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, createNotification(), FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or FOREGROUND_SERVICE_TYPE_MICROPHONE or FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            startForeground(NOTIFICATION_ID, createNotification(), FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        FLogger.d(TAG, "====CaptureScreenService onCreate======")
    }

    override fun onDestroy() {
        FLogger.d(TAG, "=====CaptureScreenService onDestroy=========")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Projection Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent().apply {
            setClassName(this@ScreenshotForegroundService.application.packageName, targetClassName)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Visual assistant service")
            .setContentText("Running...")
            .setContentIntent(pendingIntent)
            .build()
    }

}
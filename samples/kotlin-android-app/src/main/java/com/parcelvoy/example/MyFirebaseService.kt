package com.parcelvoy.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.e(LOG_TAG, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("Parcelvoy", "push notification received")
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val title = data["title"]
        val body = data["content"]

        MainApplication.analytics.track(
            "Push Notification Received", mapOf(
                "campaign" to mapOf(
                    "medium" to "Push",
                    "source" to "FCM",
                    "name" to title,
                    "content" to body
                )
            )
        )

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("push_notification", true)
        }
        val pi = PendingIntent.getActivity(applicationContext, 101, intent, 0)
        val nm = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("222", "my_channel", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(applicationContext, "222")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(body)
            .setAutoCancel(true)
            .setContentText(title)
            .setContentIntent(pi)
            .setDeleteIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        nm.notify(123456789, builder.build())
    }

    companion object {
        private const val LOG_TAG = "MyFirebaseService"
    }
}
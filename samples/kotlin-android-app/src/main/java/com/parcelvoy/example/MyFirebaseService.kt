package com.parcelvoy.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.os.bundleOf
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.parcelvoy.android.Parcelvoy

class MyFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.e(LOG_TAG, token)

        MainApplication.analytics.register(
            token = token,
            appBuild = BuildConfig.VERSION_CODE,
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val bundle =  bundleOf(*remoteMessage.data.toList().toTypedArray())
        Log.d(LOG_TAG, "push notification received: ${bundle.print()}")

        val title = bundle.getString("title")
        val body = bundle.getString("content")
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

        MainApplication.analytics.pushReceived(bundle)

        if (Parcelvoy.isCheckMessagePush(bundle)) {
            // Do not show a notification if it's a InApp Message push
            return
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("push_notification", true)
        }
        val pi = PendingIntent.getActivity(applicationContext, 101, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
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

    fun Bundle.print(): String = StringBuilder().apply {
        append("Bundle:")
        val iterator = keySet().iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()
            append("\n")
            append(key)
            append(": ")
            append(get(key) ?: "N/A")
        }
    }.toString()

    companion object {
        private const val LOG_TAG = "MyFirebaseService"
    }
}
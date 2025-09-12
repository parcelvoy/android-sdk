package com.parcelvoy.example

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

    private val analytics = MainApplication.analytics

    private var onActivityResult: (isGranted: Boolean) -> Unit = {}
    private val activityResult = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onActivityResult(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)

        lifecycleScope.launch {
            analytics.identify(
                id = UUID.randomUUID().toString(),
                email = "email@example.app",
                traits = mapOf(
                    "first_name" to "John",
                    "last_name" to "Doe",
                )
            )
        }

        lifecycleScope.launch {
            analytics.getNotifications()
        }

        analytics.track(
            event = "Application Opened",
            properties = mapOf("property" to true)
        )

        findViewById<Button>(R.id.notifications_button).setOnClickListener {
            val areNotificationsEnabled =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getSystemService<NotificationManager>()?.areNotificationsEnabled() ?: false
                } else {
                    true
                }
            if (areNotificationsEnabled) {
                registerPushToken()
            } else {
                onActivityResult = { isGranted: Boolean ->
                    if (isGranted) registerPushToken()
                }
                activityResult.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun registerPushToken() {
        Log.w(LOG_TAG, "registerPushToken()")
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(LOG_TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            val token = task.result
            Log.w(LOG_TAG, "registerPushToken() Token: $token")

            if (token != null) {
                // Register new FCM registration token
                analytics.register(
                    token = token,
                    appBuild = BuildConfig.VERSION_CODE,
                    appVersion = BuildConfig.VERSION_NAME
                )
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val uri = intent.data
        Log.e(LOG_TAG, "onNewIntent | uri: $uri")
        if (uri != null) {
            val redirect = analytics.getUriRedirect(uri)
            Log.e(LOG_TAG, "redirect: $redirect")
        }
    }

    companion object {
        private const val LOG_TAG = "Parcelvoy"
    }
}
package com.parcelvoy.example

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.parcelvoy.android.InAppAction
import com.parcelvoy.android.InAppDelegate
import com.parcelvoy.android.InAppDisplayState
import com.parcelvoy.android.Parcelvoy
import com.parcelvoy.android.ParcelvoyNotification

class MainApplication : Application(), InAppDelegate {

    override fun onCreate() {
        super.onCreate()

        // TODO: Enter API Key and URL
        val apiKey = "" // like: pk_fdfbi282ec65-4a4f-b9ef-6f6979905523
        val urlEndpoint = "" // like: https://parcelvoy.company.com/api
        analytics = Parcelvoy.initialize(
            app = this,
            apiKey = apiKey,
            urlEndpoint = urlEndpoint,
            isDebug = true,
            inAppDelegate = this
        )
    }

    override val autoShow: Boolean = true

    override fun onNew(notification: ParcelvoyNotification): InAppDisplayState {
        Log.d(LOG_TAG, "onNew: $notification")
        return InAppDisplayState.SHOW
    }

    override fun handle(
        action: InAppAction,
        context: Map<String, Any>,
        notification: ParcelvoyNotification
    ) {
        Log.d(LOG_TAG, "handle: $action, context: $context, notification: $notification")
        Toast.makeText(this, "Action: $action", Toast.LENGTH_SHORT).show()
    }

    override fun onError(error: Throwable) {
        Log.e(LOG_TAG, "onError: $error")
        super.onError(error)
    }

    companion object {
        private const val LOG_TAG = "MainApplication"
        
        lateinit var analytics: Parcelvoy
    }
}
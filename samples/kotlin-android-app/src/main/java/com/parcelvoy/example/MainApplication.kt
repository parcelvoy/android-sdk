package com.parcelvoy.example

import android.app.Application
import com.parcelvoy.android.Config
import com.parcelvoy.android.Parcelvoy

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // TODO: Enter API Key and URL
        val apiKey = "" // like: pk_fdfbi282ec65-4a4f-b9ef-6f6979905523
        val urlEndpoint = "" // like: https://parcelvoy.company.com/api

        analytics = Parcelvoy.initialize(this, apiKey, urlEndpoint)
    }

    companion object {
        lateinit var analytics: Parcelvoy
    }
}
package com.parcelvoy.android

import java.text.SimpleDateFormat
import java.util.*

class Constants {
    companion object {
        const val PARCELVOY_KEY: String = "parcelvoy"
        const val IN_APP_CHECK_MESSAGE_KEY: String = "check_in_app_messages"

        val iso8601DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
package com.parcelvoy.android

import java.text.SimpleDateFormat
import java.util.*

class Constants {

    companion object {
        val iso8601DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
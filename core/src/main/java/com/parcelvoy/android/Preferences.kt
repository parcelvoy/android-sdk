package com.parcelvoy.android

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class Preferences(context: Context) {

    var anonymousId: String?
        get() = getString("PARCELVOY_ANONYMOUS_ID")
        set(value) {
            setString("PARCELVOY_ANONYMOUS_ID", value)
        }

    var deviceUuid: String?
        get() = getString("PARCELVOY_DEVICE_UUID")
        set(value) {
            setString("PARCELVOY_DEVICE_UUID", value)
        }

    private val manager: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private fun getString(key: String, default: String? = null): String? =
        manager.getString(key, default)

    private fun setString(key: String, value: String?) {
        manager.edit().putString(key, value).apply()
    }
}
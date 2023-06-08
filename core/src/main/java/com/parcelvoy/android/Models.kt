package com.parcelvoy.android

import android.os.Build
import com.google.gson.annotations.SerializedName

data class Config(
    val apiKey: String,
    val urlEndpoint: String,
    val isDebug: Boolean = false
)

data class Identity(
    val anonymousId: String,
    val externalId: String?,
    val phone: String?,
    val email: String?,
    @SerializedName("data")
    val traits: Map<String, Any>
)

data class Alias(
    val anonymousId: String,
    val externalId: String?
)

data class Event(
    val name: String,
    val anonymousId: String,
    val externalId: String?,
    val properties: Map<String, Any>
)

data class Device(
    val anonymousId: String,
    val externalId: String?,
    val deviceId: String?,
    val token: String?,
    val os: String,
    val osVersion: String,
    val model: String,
    val appBuild: String,
    val appVersion: String
) {

    constructor(
        anonymousId: String,
        externalId: String?,
        token: String?,
        deviceId: String,
        appBuild: Int,
        appVersion: String
    ) : this(
        anonymousId = anonymousId,
        externalId = externalId,
        token = token,
        deviceId = deviceId,
        os = "Android",
        osVersion = Build.VERSION.RELEASE,
        model = Build.MODEL,
        appBuild = appBuild.toString(),
        appVersion = appVersion
    )
}

package com.parcelvoy.android

import android.os.Build
import android.os.Parcelable
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.parcelvoy.android.network.NotificationContentDeserializer
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

data class Config(
    val apiKey: String,
    val urlEndpoint: String,
    val inAppDelegate: InAppDelegate? = null,
    val isDebug: Boolean = false,
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
    val appVersion: String,
    val sdkVersion: String,
) {

    constructor(
        anonymousId: String,
        externalId: String?,
        token: String?,
        deviceId: String,
        appBuild: Int,
        appVersion: String,
    ) : this(
        anonymousId = anonymousId,
        externalId = externalId,
        token = token,
        deviceId = deviceId,
        os = "Android",
        osVersion = Build.VERSION.RELEASE,
        model = Build.MODEL,
        appBuild = appBuild.toString(),
        appVersion = appVersion,
        sdkVersion = "1.0.0",
    )
}

data class Page<T>(
    val results: List<T>,
    val nextCursor: String?
)

enum class NotificationType {
    @SerializedName("banner")
    BANNER,

    @SerializedName("alert")
    ALERT,

    @SerializedName("html")
    HTML
}

@JsonAdapter(NotificationContentDeserializer::class)
interface NotificationContent : Parcelable {
    val title: String
    val body: String
    val readOnShow: Boolean?
    val custom: Map<String, Any>?
    val context: Map<String, String>?
}

@Parcelize
data class BannerNotification(
    override val title: String,
    override val body: String,
    override val readOnShow: Boolean? = null,
    override val custom: Map<String, String>? = null,
    override val context: Map<String, String>? = null,
) : NotificationContent

@Parcelize
data class AlertNotification(
    override val title: String,
    override val body: String,
    val image: String?,
    override val readOnShow: Boolean? = null,
    override val custom: Map<String, String>? = null,
    override val context: Map<String, String>? = null,
) : NotificationContent

@Parcelize
data class HtmlNotification(
    override val title: String,
    override val body: String,
    val html: String,
    override val readOnShow: Boolean? = null,
    override val custom: Map<String, String>? = null,
    override val context: Map<String, String>? = null
) : NotificationContent

@Parcelize
data class ParcelvoyNotification(
    val id: Long,
    val contentType: NotificationType,
    val content: NotificationContent,
    val readAt: Date?,
    val expiresAt: Date?
) : Parcelable

enum class InAppAction {
    DISMISS,
    CUSTOM,
}

class ParcelvoyAction(
    val config: JSONObject
) {

    var userInput: String? = null

    val type: String?
        get() = config.optString("type")

    val data: String?
        get() = config.optString("data")

    fun isOfType(type: String): Boolean {
        return this.type != null && this.type == type
    }

    companion object {
        const val ACTION_TYPE_OPEN_URL: String = "openUrl"

        fun from(config: JSONObject?): ParcelvoyAction? = config?.let { ParcelvoyAction(it) }

        fun actionOpenUrl(url: String?): ParcelvoyAction? =
            url?.let {
                val config = JSONObject()
                config.put("type", "openUrl")
                config.put("data", url)
                ParcelvoyAction(config)
            }

        fun actionCustomAction(customActionName: String): ParcelvoyAction? {
            val config = JSONObject()
            config.put("type", customActionName)
            return ParcelvoyAction(config)
        }
    }
}

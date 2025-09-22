package com.parcelvoy.android.network

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.parcelvoy.android.AlertNotification
import com.parcelvoy.android.BannerNotification
import com.parcelvoy.android.HtmlNotification
import com.parcelvoy.android.NotificationContent // Assuming this is a sealed interface or common base class
import com.parcelvoy.android.NotificationType
import com.parcelvoy.android.ParcelvoyNotification
import java.lang.reflect.Type
import java.util.Date

class ParcelvoyNotificationDeserializer : JsonDeserializer<ParcelvoyNotification> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ParcelvoyNotification {
        val jsonObject = json.asJsonObject
        val contentTypeString = jsonObject.get("content_type").asString
        val contentType = try {
            NotificationType.valueOf(contentTypeString.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            throw JsonParseException("Unknown notification content type: $contentTypeString", e)
        }

        val contentJson = jsonObject.getAsJsonObject("content")
        val notificationContent: NotificationContent = when (contentType) {
            NotificationType.BANNER -> context.deserialize(contentJson, BannerNotification::class.java)
            NotificationType.ALERT -> context.deserialize(contentJson, AlertNotification::class.java)
            NotificationType.HTML -> context.deserialize(contentJson, HtmlNotification::class.java)
        }
        return ParcelvoyNotification(
            id = jsonObject.get("id")?.asLong ?: 0L,
            contentType = contentType,
            content = notificationContent,
            readAt = context.deserialize<Date>(jsonObject.get("read_at"), Date::class.java),
            expiresAt = context.deserialize<Date>(jsonObject.get("expires_at"), Date::class.java),
        )
    }
}

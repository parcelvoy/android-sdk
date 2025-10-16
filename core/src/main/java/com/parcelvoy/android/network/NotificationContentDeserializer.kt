package com.parcelvoy.android.network

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.parcelvoy.android.AlertNotification
import com.parcelvoy.android.BannerNotification
import com.parcelvoy.android.HtmlNotification
import com.parcelvoy.android.NotificationContent
import java.lang.reflect.Type

class NotificationContentDeserializer : JsonDeserializer<NotificationContent> {

    companion object {
        private const val KEY_CUSTOM = "custom"
        private const val KEY_CONTEXT = "context"
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): NotificationContent {
        val contentJson = json.asJsonObject

        val customJsonObject = contentJson.getAsJsonObject(KEY_CUSTOM)
        var contextJsonObject: JsonObject? = null
        if (customJsonObject != null && customJsonObject.has(KEY_CONTEXT)) {
            contextJsonObject = customJsonObject.remove(KEY_CONTEXT)?.asJsonObject
        }

        if (contextJsonObject != null) {
            contentJson.add(KEY_CONTEXT, contextJsonObject)
        }

        val concreteType: Class<out NotificationContent> = when {
            contentJson.has("html") -> HtmlNotification::class.java
            contentJson.has("image") -> AlertNotification::class.java
            else -> BannerNotification::class.java
        }

        return context.deserialize(contentJson, concreteType)
    }
}
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

        if (contentJson.has(KEY_CUSTOM) && contentJson.get(KEY_CUSTOM).isJsonObject) {
            val originalCustom = contentJson.getAsJsonObject(KEY_CUSTOM)
            val flattenedCustom = originalCustom.flatten()
            contentJson.add(KEY_CUSTOM, flattenedCustom)
        }

        if (contextJsonObject != null) {
            contentJson.add(KEY_CONTEXT, contextJsonObject.flatten())
        }

        val concreteType: Class<out NotificationContent> = when {
            contentJson.has("html") -> HtmlNotification::class.java
            contentJson.has("image") -> AlertNotification::class.java
            else -> BannerNotification::class.java
        }

        return context.deserialize(contentJson, concreteType)
    }
}

// Extension function to flatten nested JSON objects
private fun JsonObject.flatten(): JsonObject {
    val flattenedCustom = JsonObject()

    for ((key, value) in this.entrySet()) {
        if (value.isJsonPrimitive) {
            flattenedCustom.add(key, value)
        } else {
            flattenedCustom.addProperty(key, value.toString())
        }
    }
    return flattenedCustom
}
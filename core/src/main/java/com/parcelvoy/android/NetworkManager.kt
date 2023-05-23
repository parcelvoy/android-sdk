package com.parcelvoy.android

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.URL
import java.util.*

class NetworkManager(
    private val config: Config
) {

    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(Date::class.java, DateAdapter())
        .create()

    private val httpLoggingInterceptor: HttpLoggingInterceptor
        get() {
            val interceptor = HttpLoggingInterceptor()
            interceptor.level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            return interceptor
        }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(httpLoggingInterceptor)
        .build()

    fun get(
        path: String,
        useBaseUri: Boolean = true,
        handler: ((IOException?) -> Unit)? = null
    ) {
        val url = if (useBaseUri) URL("${config.urlEndpoint}/api/client/$path") else URL(path)
        val request = Request.Builder().url(url).get().build()
        execute(request, handler)
    }

    fun post(
        path: String,
        body: Any,
        useBaseUri: Boolean = true,
        handler: ((IOException?) -> Unit)? = null
    ) {
        val url = if (useBaseUri) URL("${config.urlEndpoint}/api/client/$path") else URL(path)
        val requestBody = gson.toJson(body).toRequestBody()
        val request = Request.Builder().url(url).post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()
        execute(request, handler)
    }

    private fun execute(
        request: Request,
        handler: ((IOException?) -> Unit)? = null
    ) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler?.invoke(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val isValid = (200 until 299).contains(response.code)

                if (!isValid) {
                    print("PV | statusCode should be 2xx, but is ${response.code}")
                    print("PV | response = $response")
                    return
                }
                handler?.invoke(null)
            }
        })
    }
}

package com.parcelvoy.android

import android.content.Context
import android.net.Uri
import android.util.Log
import java.util.*

open class Parcelvoy protected constructor(
    context: Context,
    config: Config
) {

    private var externalId: String? = null
    private var network: NetworkManager = NetworkManager(config)
    private val preferences: Preferences = Preferences(context)

    /**
     * Identify a given user
     *
     * This can be used either for anonymous or known users. When a user transitions from
     * anonymous to known, call identify again to automatically alias ther users together.
     *
     * Call identify whenever user traits (attributes) change to make sure they are updated.
     *
     * @param id An optional known user identifier
     * @param email Optional email address of the user
     * @param phone Optional phone number of the user
     * @param traits Attributes of the user
     */
    fun identify(
        id: String?,
        email: String? = null,
        phone: String? = null,
        traits: Map<String, Any> = emptyMap()
    ) {
        identify(
            identity = Identity(
                anonymousId = getOrAndOrSetAnonymousId(),
                externalId = id,
                phone = phone,
                email = email,
                traits = traits
            )
        )
    }

    /**
     * Identify a given user
     *
     * This can be used either for anonymous or known users. When a user transitions from
     * anonymous to known, call identify again to automatically alias ther users together.
     *
     * Call identify whenever user traits (attributes) change to make sure they are updated.
     *
     * @param identity An object representing a Parcelvoy user identity
     */
    fun identify(identity: Identity) {
        if (externalId == null) {
            identity.externalId?.let {
                alias(anonymousId = getOrAndOrSetAnonymousId(), externalId = it)
            }
        }

        externalId = identity.externalId
        network.post(path = "identify", body = identity)
    }

    /**
     * Alias an anonymous user to a known user
     *
     * Calling alias will only work once, repeated calls will do nothing.
     *
     * **This method is automatically called by `identify` and should not need to be manually called**
     *
     * @param anonymousId The internal anonymous identifier of the user
     * @param externalId The known user identifier
     */
    fun alias(anonymousId: String, externalId: String) {
        this.externalId = externalId
        network.post(
            path = "alias",
            body = Alias(
                anonymousId = anonymousId,
                externalId = externalId
            )
        )
    }

    /**
     * Clears out the externalId and stored anonymousId
     *
     * It is suggested that you call this when a user logs out of your app
     */
    fun reset() {
        this.externalId = null
        preferences.anonymousId = null
    }

    /**
     * Track an event
     *
     * Send events for both anonymous and identified users to Parcelvoy to
     * trigger journeys or lists.
     *
     * @param event A string name of the event
     * @param properties A dictionary of attributes associated to the event
     */
    fun track(event: String, properties: Map<String, Any> = emptyMap()) {
        postEvent(
            events = listOf(
                Event(
                    name = event,
                    anonymousId = getOrAndOrSetAnonymousId(),
                    externalId = externalId,
                    properties = properties
                )
            )
        )
    }

    /**
     * Register device and push notifications
     *
     * This method registers the current device. It is intended to send up the
     * push notification token, but can also be used to know what device the
     * user is using.
     *
     * @param token An optional push notification token
     */
    fun register(
        token: String,
        appBuild: Int,
        appVersion: String
    ) {
        val deviceId = preferences.deviceUuid ?: UUID.randomUUID().toString().apply {
            preferences.deviceUuid = this
        }

        val device = Device(
            anonymousId = getOrAndOrSetAnonymousId(),
            externalId = externalId,
            token = token,
            deviceId = deviceId,
            appBuild = appBuild,
            appVersion = appVersion
        )
        network.post(path = "devices", body = device)
    }

    /**
     * Handle deeplink navigation
     *
     * To allow for click tracking, all emails are click-wrapped in a Parcelvoy url
     * that then needs to be unwrapped for navigation purposes. This method
     * checks to see if a given URL is a Parcelvoy URL and if so, unwraps the url,
     * triggers the unwrapped URL and calls the Parcelvoy API to register that the
     * URL was executed.
     *
     * @param universalLink The URL that the app is trying to open
     */
    fun getUriRedirect(universalLink: Uri): Uri? {
        if (!isParcelvoyDeepLink(universalLink)) return null
        val redirect = universalLink.getQueryParameter("r") ?: return null

        /// Run the URL so that the redirect events get triggered at API
        runCatching {
            network.get(path = universalLink.toString(), useBaseUri = false)
        }

        /// Return the URI included in the parameter
        return Uri.parse(redirect)
    }

    fun isParcelvoyDeepLink(uri: Uri): Boolean {
        uri.getQueryParameter("r") ?: return false
        return uri.path?.endsWith("/c") == true || uri.path?.contains("/c/") == true
    }

    private fun postEvent(events: List<Event>, retries: Int = 3) {
        network.post(path = "events", body = events) { error ->
            if (error != null) {
                if (retries <= 0) {
                    return@post
                }
                postEvent(events, retries = retries - 1)
            }
        }
    }

    private fun getOrAndOrSetAnonymousId(): String =
        preferences.anonymousId ?: UUID.randomUUID().toString().apply {
            preferences.anonymousId = this
        }

    companion object {

        /**
         * Initialize the library with the required API key and URL endpoint
         * **This must be called before any other methods**
         *
         * @param apiKey A generated public API key
         * @param urlEndpoint The based domain of the hosted Parcelvoy instance
         *
         */
        fun initialize(
            context: Context,
            apiKey: String,
            urlEndpoint: String,
            isDebug: Boolean = false
        ): Parcelvoy = initialize(context, Config(apiKey, urlEndpoint, isDebug))

        /**
         * Initialize the library with a config
         * **This must be called before any other methods**
         *
         * @param config An initialized <code>Config</code>
         *
         */
        fun initialize(context: Context, config: Config): Parcelvoy = Parcelvoy(context, config)
    }
}

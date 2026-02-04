package com.parcelvoy.android

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.parcelvoy.android.network.NetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.*

open class Parcelvoy protected constructor(
    app: Application,
    val config: Config
) {

    private var externalId: String? = null
    private var network: NetworkManager = NetworkManager(config)
    private val preferences: Preferences = Preferences(app)
    private val libraryScope: CoroutineScope = ProcessLifecycleOwner.get().lifecycleScope

    private var currentActivity: WeakReference<AppCompatActivity?> = WeakReference(null)
    private var hasAutoShown: Boolean = false
    private val skipNotificationSet: MutableSet<Long> = mutableSetOf()

    private val inAppDelegate: InAppDelegate?
        get() = config.inAppDelegate

    init {
        app.registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(p0: Activity, p1: Bundle?) = Unit
                override fun onActivityDestroyed(p0: Activity) = Unit
                override fun onActivityPaused(p0: Activity) = Unit
                override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) = Unit
                override fun onActivityStarted(p0: Activity) = Unit
                override fun onActivityStopped(p0: Activity) = Unit

                override fun onActivityResumed(p0: Activity) {
                    if (p0 is AppCompatActivity) {
                        currentActivity = WeakReference(p0)
                        if (inAppDelegate?.autoShow == true && !hasAutoShown) {
                            hasAutoShown = true
                            showLatestNotification()
                        }
                    }
                }
            }
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
     * @param id An optional known user identifier
     * @param email Optional email address of the user
     * @param phone Optional phone number of the user
     * @param traits Attributes of the user
     */
    suspend fun identify(
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
    suspend fun identify(identity: Identity) {
        if (externalId == null) {
            identity.externalId?.let {
                alias(anonymousId = getOrAndOrSetAnonymousId(), externalId = it)
            }
        }

        externalId = identity.externalId
        network.post<Unit>(path = "identify", body = identity)
    }

    /**
     * Manually set the external identifier of the user
     *
     * This should be used if the app manages user identities outside of the identify/alias flow.
     *
     * @param id A string representing the external identifier of the user
     */
    fun setExternalId(id: String) {
        externalId = id
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
    suspend fun alias(anonymousId: String, externalId: String) {
        this.externalId = externalId
        network.post<Unit>(
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
        val event = Event(
            name = event,
            anonymousId = getOrAndOrSetAnonymousId(),
            externalId = externalId,
            properties = properties
        )
        libraryScope.launch {
            postEvent(listOf(event))
        }
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
            appVersion = appVersion,
        )
        libraryScope.launch {
            network.post<Unit>(path = "devices", body = device)
        }
    }

    /**
     * Returns a page of notifications
     */
    suspend fun getNotifications(): Result<Page<ParcelvoyNotification>> =
        network.get<Page<ParcelvoyNotification>>(
            path = "notifications" ,
            user = Alias(
                anonymousId = getOrAndOrSetAnonymousId(),
                externalId = externalId
            ),
        )

    /**
     * Fetches the latest notifications and processes them based on the InAppDelegate's response.
     */
    fun showLatestNotification() {
        libraryScope.launch {
            try {
                val firstNotification = getNotifications().getOrThrow().results.firstOrNull {
                    !skipNotificationSet.contains(it.id)
                } ?: return@launch
                when (inAppDelegate?.onNew(firstNotification)) {
                    InAppDisplayState.SHOW -> show(firstNotification)
                    InAppDisplayState.CONSUME -> consume(firstNotification)
                    InAppDisplayState.SKIP -> {
                        skipNotificationSet.add(firstNotification.id)
                        showLatestNotification()
                    }
                    null -> Log.d(LOG_TAG, "No InAppDelegate to decide on notification: ${firstNotification.id}")
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "An unexpected error occurred in showLatestNotification", e)
                inAppDelegate?.onError(e)
            }
        }
    }

    /**
     * Shows an in-app notification as a DialogFragment.
     */
    suspend fun show(
        notification: ParcelvoyNotification,
    ) {
        withContext(Dispatchers.Main) {
            val fragmentManager = currentActivity.get()?.supportFragmentManager
            if (fragmentManager == null) {
                val state = IllegalStateException("No fragment manager available to show in-app notification.")
                Log.e(LOG_TAG, "Exception", state)
                inAppDelegate?.onError(state)
                return@withContext
            }

            val existingDialog = fragmentManager.findFragmentByTag(InAppDialogFragment.DIALOG_TAG) as? DialogFragment
            if (existingDialog != null) {
                Log.d(LOG_TAG, "Dismissing existing InAppDialogFragment.")
                existingDialog.dismissAllowingStateLoss()
            }

            InAppDialogFragment.newInstance(
                notification = notification,
                delegate = object : InAppDelegate {

                    override val autoShow: Boolean
                        get() = inAppDelegate?.autoShow == true

                    override val useDarkMode: Boolean
                        get() = inAppDelegate?.useDarkMode == true

                    override fun handle(
                        action: InAppAction,
                        context: Map<String, Any>,
                        notification: ParcelvoyNotification
                    ) {
                        if (action == InAppAction.DISMISS) {
                            libraryScope.launch {
                                consume(notification)
                            }
                        }
                        inAppDelegate?.handle(action, context, notification)
                    }

                    override fun onError(error: Throwable) {
                        inAppDelegate?.onError(error)
                    }

                    override fun onNotificationShown(notification: ParcelvoyNotification) {
                        inAppDelegate?.onNotificationShown(notification)
                    }
                }
            ).show(fragmentManager, InAppDialogFragment.DIALOG_TAG)
            Log.i(LOG_TAG, "Showing in-app notification dialog: ${notification.id}")
        }

        if (notification.content.readOnShow == true) consume(notification)
    }

    /**
     * Marks a notification as consumed/read on the backend.
     * (consume function remains largely the same as before)
     */
    suspend fun consume(
        notification: ParcelvoyNotification,
        thenShowNext: Boolean = true,
    ) {
        network.put<Unit>(
            path = "notifications/${notification.id}",
            body = Alias(
                anonymousId = getOrAndOrSetAnonymousId(),
                externalId = externalId,
            )
        ).onSuccess {
            Log.i(LOG_TAG, "Notification ${notification.id} consumed successfully (via DialogFragment flow).")
            if (thenShowNext) showLatestNotification()
        }.onFailure { error ->
            Log.e(LOG_TAG, "Failed to consume notification ${notification.id}", error)
            inAppDelegate?.onError(error)
        }
    }

    /**
     * Dismisses the currently shown in-app notification dialog.
     * This might be called programmatically by the app.
     */
    suspend fun dismiss(
        fragmentManager: FragmentManager,
        notification: ParcelvoyNotification,
    ) {
        withContext(Dispatchers.Main) {
            val dialog = fragmentManager.findFragmentByTag(InAppDialogFragment.DIALOG_TAG) as? InAppDialogFragment
            dialog?.dismissAllowingStateLoss()
            Log.i(LOG_TAG, "Dismissed InAppDialogFragment for: ${notification.id}")
        }
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
     * @param context The Android Context.
     * @param universalLink The URL that the app is trying to open.
     * @return True if the link was handled, false otherwise.
     */
    fun handle(universalLink: Uri): Boolean {
        if (!isParcelvoyDeepLink(universalLink)) {
            return false
        }
        val redirectUrl = universalLink.getQueryParameter("r")?.toUri() ?: return false

        // Run the URL so that the redirect events get triggered at API
        libraryScope.launch {
            // Assuming 'network.get' is a suspend function for making GET requests.
            // Replace with your actual network call implementation.
            network.get<Unit>(
                path = universalLink.toString(),
                useBaseUri = false, // Assuming the universalLink is a full URL
                user = Alias( // Replace with your actual User/Alias structure
                    anonymousId = getOrAndOrSetAnonymousId(),
                    externalId = externalId
                )
            )
        }

        // Manually redirect to the URL included in the parameter
        openUrl(redirectUrl)
        return true
    }

    /**
     * Handle push notification message refresh.
     *
     * Push notifications may come with an internal command to
     * check for in-app messages. This method should be called
     * from your push notification handler to allow the library
     * to process the command and show any in-app messages if needed.
     *
     * @param bundle The payload from the push notification.
     */
    fun pushReceived(bundle: Bundle) {
        // Handle silent notifications that should only trigger in-app messages
        if (isCheckMessagePush(bundle)) showLatestNotification()
    }

    /**
     * Helper function to open a URL using an Intent.
     */
    private fun openUrl(url: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url).apply {
                // Add FLAG_ACTIVITY_NEW_TASK if you are calling this from a non-Activity context.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            currentActivity.get()!!.startActivity(intent)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error opening URL: $url", e)
            inAppDelegate?.onError(e)
        }
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
        libraryScope.launch {
            network.get<Unit>(
                path = universalLink.toString(),
                useBaseUri = false,
                user = Alias(
                    anonymousId = getOrAndOrSetAnonymousId(),
                    externalId = externalId
                ),
            )
        }

        /// Return the URI included in the parameter
        return redirect.toUri()
    }

    fun isParcelvoyDeepLink(uri: Uri): Boolean {
        uri.getQueryParameter("r") ?: return false
        return uri.path?.endsWith("/c") == true || uri.path?.contains("/c/") == true
    }

    private suspend fun postEvent(events: List<Event>, retries: Int = 3) {
        network.post<Unit>(path = "events", body = events).onFailure { error ->
            if (retries <= 0) {
                return
            }
            postEvent(events, retries = retries - 1)
        }
    }

    private fun getOrAndOrSetAnonymousId(): String =
        preferences.anonymousId ?: UUID.randomUUID().toString().apply {
            preferences.anonymousId = this
        }

    companion object {
        private const val LOG_TAG = "Parcelvoy"

        /**
         * Initialize the library with the required API key and URL endpoint
         * **This must be called before any other methods**
         *
         * @param apiKey A generated public API key
         * @param urlEndpoint The based domain of the hosted Parcelvoy instance
         *
         */
        fun initialize(
            app: Application,
            apiKey: String,
            urlEndpoint: String,
            inAppDelegate: InAppDelegate? = null,
            isDebug: Boolean = false
        ): Parcelvoy {
            require(apiKey.isNotEmpty())
            require(urlEndpoint.isNotEmpty())
            return initialize(app, Config(apiKey, urlEndpoint, inAppDelegate, isDebug))
        }

        /**
         * Initialize the library with a config
         * **This must be called before any other methods**
         *
         * @param config An initialized <code>Config</code>
         *
         */
        fun initialize(app: Application, config: Config): Parcelvoy = Parcelvoy(app, config)

        fun isParcelvoyPush(extras: Bundle?): Boolean =
            extras?.getBoolean(Constants.PARCELVOY_KEY) == true || extras?.getString(Constants.PARCELVOY_KEY).toBoolean()

        fun isCheckMessagePush(extras: Bundle?): Boolean =
            isParcelvoyPush(extras) &&
                    extras?.getBoolean(Constants.IN_APP_CHECK_MESSAGE_KEY) == true ||
                    extras?.getString(Constants.IN_APP_CHECK_MESSAGE_KEY).toBoolean()
    }
}

package com.parcelvoy.android

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InAppDialogFragment : DialogFragment() {

    private var webView: WebView? = null
    private val gson = Gson()

    private val webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url ?: return false
            Log.d(DIALOG_TAG, "WebView trying to load URL: $url")

            if (url.scheme == Constants.PARCELVOY_KEY) {
                when (url.host) { // e.g., parcelvoy://dismiss, parcelvoy://custom
                    "dismiss" -> processAction(InAppAction.DISMISS)
                    "custom" -> {
                        val params = mutableMapOf<String, Any>("url" to url.toString())
                        url.queryParameterNames.forEach { key ->
                            url.getQueryParameter(key)?.let { value -> params[key] = value }
                        }
                        processAction(InAppAction.CUSTOM, params)
                    }
                    else -> processAction(InAppAction.CUSTOM, mapOf("url" to url.toString()))
                }
                return true
            }

            // For any other URLs, open them in an external browser
            // This prevents the WebView from navigating away from your in-app message content.
            try {
                val intent = Intent(Intent.ACTION_VIEW, url)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return true
            } catch (e: Exception) {
                delegate?.onError(e)
                return false
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)

            val dismissScript = "window.dismiss = function() { ParcelvoyJSBridge.postMessage('dismiss', ''); };"
            val triggerScript = "window.trigger = function(obj) { ParcelvoyJSBridge.postMessage('custom', JSON.stringify(obj)); };"

            view.evaluateJavascript(dismissScript, null)
            view.evaluateJavascript(triggerScript, null)
            setThemeJs()

            notification?.let { delegate?.onNotificationShown(it) }
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            super.onReceivedError(view, request, error)
            if (error != null) {
                delegate?.onError(Exception("WebView error: ${error.description} on URL ${request?.url}"))
            }
        }
    }

    private var notification: ParcelvoyNotification? = null
    private var delegate: InAppDelegate? = null

    override fun onStart() {
        super.onStart()

        setWindowTransitions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notification = arguments?.parcelable(ARG_NOTIFICATION)
        if (notification == null) {
            dismissAllowingStateLoss()
            return
        }

        setStyle(STYLE_NORMAL, R.style.FullScreenDialogTheme)
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val rootView = inflater.inflate(R.layout.dialog_in_app_container, container, false)
        val webViewContainer = rootView.findViewById<ViewGroup>(R.id.webview_container)
        webView = WebView(requireContext()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
            addJavascriptInterface(WebAppInterface(), "ParcelvoyJSBridge")
        }

        webViewContainer.addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        webView?.webViewClient = webViewClient
        when (val content = notification?.content) {
            is HtmlNotification -> {
                // baseURL can be null, or set to a dummy file:// URL if you have local assets to resolve
                webView?.loadDataWithBaseURL(null, content.html, "text/html", "UTF-8", null)
            }
            else -> {
                Log.e(DIALOG_TAG, "Notification content is not HTML. Cannot display in WebView.")
                // Potentially dismiss or show a fallback UI
                dismissAllowingStateLoss()
            }
        }
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.apply {
            setCanceledOnTouchOutside(false)
            setCancelable(false)
            window?.apply {
                setGravity(Gravity.BOTTOM)
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

                lifecycleScope.launch {
                    delay(10)
                    setWindowTransitions()
                }
            }
        }
    }

    private fun setWindowTransitions() {
        dialog?.window?.apply {
            val flagsToUpdate = FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_INSET_DECOR
            setFlags(flagsToUpdate, flagsToUpdate)
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun setThemeJs() {
        val useDarkMode = delegate?.useDarkMode == true
        val function = if (useDarkMode) {
            "document.documentElement.classList.add('darkMode');"
        } else {
            "document.documentElement.classList.remove('darkMode');"
        }
        webView?.evaluateJavascript("javascript: $function", null)
    }

    /**
     * JavaScript Interface object
     * Methods annotated with @JavascriptInterface are callable from JavaScript.
     */
    inner class WebAppInterface {
        @JavascriptInterface
        fun postMessage(actionName: String, jsonPayload: String?) {
            Log.d(DIALOG_TAG, "JSBridge received: $actionName with payload: $jsonPayload")

            val action = InAppAction.entries.find { it.name.equals(actionName, ignoreCase = true) }
            if (action == null) {
                Log.w(DIALOG_TAG, "Unknown action from JSBridge: $actionName")
                // Default to custom if the name isn't 'dismiss' and not found,
                // or handle as an error.
                if (actionName.equals("custom", ignoreCase = true)) {
                    val body: Map<String, Any> = if (!jsonPayload.isNullOrEmpty()) {
                        try {
                            val type = object : TypeToken<Map<String, Any>>() {}.type
                            gson.fromJson(jsonPayload, type)
                        } catch (e: Exception) {
                            Log.e(DIALOG_TAG, "Error parsing JSON payload for custom action", e)
                            mapOf("payload" to jsonPayload) // Fallback
                        }
                    } else {
                        emptyMap()
                    }

                    activity?.runOnUiThread {
                        processAction(InAppAction.CUSTOM, body)
                    }
                }
                return
            }

            when (action) {
                InAppAction.DISMISS -> activity?.runOnUiThread {
                    processAction(InAppAction.DISMISS)
                }
                InAppAction.CUSTOM -> activity?.runOnUiThread {
                    val body: Map<String, Any> = if (!jsonPayload.isNullOrEmpty()) {
                        try {
                            val type = object : TypeToken<Map<String, Any>>() {}.type
                            gson.fromJson(jsonPayload, type)
                        } catch (e: Exception) {
                            Log.e(DIALOG_TAG, "Error parsing JSON payload for custom action", e)
                            mapOf("payload" to jsonPayload) // Fallback: pass raw string if not JSON
                        }
                    } else {
                        emptyMap()
                    }
                    processAction(InAppAction.CUSTOM, body)
                }
            }
        }
    }

    private fun processAction(action: InAppAction, body: Map<String, Any> = emptyMap()) {
        Log.d(DIALOG_TAG, "Processing action: $action with body: $body")
        val notification = notification
        if (notification != null) {
            delegate?.handle(action, body, notification)
        }
        dismissAllowingStateLoss()
    }

    override fun onDestroyView() {
        // Important to null out WebView to prevent leaks
        webView?.destroy() // Use destroy to release resources
        webView = null
        super.onDestroyView()
    }

    companion object {
        const val DIALOG_TAG = "ParcelvoyInAppDialog"
        private const val ARG_NOTIFICATION = "arg_notification"

        fun newInstance(
            notification: ParcelvoyNotification,
            delegate: InAppDelegate
        ): InAppDialogFragment = InAppDialogFragment().apply {
            arguments = bundleOf(ARG_NOTIFICATION to notification)
            this.delegate = delegate
        }
    }
}

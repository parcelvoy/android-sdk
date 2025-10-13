package com.parcelvoy.android

enum class InAppDisplayState {
    SHOW,
    SKIP,
    CONSUME
}

interface InAppDelegate {

    val autoShow: Boolean
        get() = true

    val useDarkMode: Boolean
        get() = false

    fun onNew(notification: ParcelvoyNotification): InAppDisplayState {
        return InAppDisplayState.SHOW
    }

    fun handle(action: InAppAction, context: Map<String, Any>, notification: ParcelvoyNotification)

    fun onError(error: Throwable) {
        // Default empty implementation
    }

    fun onDialogDisplayed(notification: ParcelvoyNotification) {
        // Default empty implementation
    }
}

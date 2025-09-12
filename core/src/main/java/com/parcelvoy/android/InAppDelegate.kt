package com.parcelvoy.android

enum class InAppDisplayState {
    SHOW,
    SKIP,
    CONSUME
}

interface InAppDelegate {

    val autoShow: Boolean
        get() = true

    fun onNew(notification: ParcelvoyNotification): InAppDisplayState {
        return InAppDisplayState.SHOW
    }

    fun handle(action: InAppAction, context: Map<String, Any>, notification: ParcelvoyNotification)

    fun onError(error: Throwable) {
        // Default empty implementation
    }
}

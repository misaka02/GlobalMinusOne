package com.example.globalminusone.system

import android.content.Context

/** Requests only; the application adapter chooses the panel window implementation. */
interface PanelVisibilityRequests {
    fun open(context: Context)
    fun close(context: Context)
}

interface PanelVisibilityRequestsOwner {
    val panelVisibilityRequests: PanelVisibilityRequests
}

/** Resolve ownership without retaining the caller's Activity or Service context. */
internal fun Context.panelVisibilityRequests(): PanelVisibilityRequests =
    checkNotNull(applicationContext as? PanelVisibilityRequestsOwner) {
        "Application must implement PanelVisibilityRequestsOwner"
    }.panelVisibilityRequests

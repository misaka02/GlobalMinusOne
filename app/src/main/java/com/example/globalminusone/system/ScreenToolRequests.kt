package com.example.globalminusone.system

import android.content.Context

/** Requests only; permission checks remain with the existing callers. */
interface ScreenToolRequests {
    fun regionScreenshot(context: Context): Boolean
    fun screenAssistant(context: Context): Boolean
    fun breenoTouch(context: Context)
    fun supportsPrivilegedScreenCapture(context: Context): Boolean = false
}

interface ScreenToolRequestsOwner {
    val screenToolRequests: ScreenToolRequests
}

internal fun Context.screenToolRequests(): ScreenToolRequests =
    checkNotNull(applicationContext as? ScreenToolRequestsOwner) {
        "Application must implement ScreenToolRequestsOwner"
    }.screenToolRequests

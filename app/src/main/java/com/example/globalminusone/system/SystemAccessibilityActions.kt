package com.example.globalminusone.system

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo

/** Existing synchronous system actions; availability is resolved for each caller invocation. */
interface SystemAccessibilityActions {
    fun performBack(): Boolean
    fun performHome(): Boolean
    fun performRecents(): Boolean
    fun performLockScreen(): Boolean
    fun performScreenshot(): Boolean
    fun performNotifications(): Boolean
    fun performQuickSettings(): Boolean
    fun performPowerDialog(): Boolean
    fun performSplitScreen(): Boolean
    fun performLastApp(): Boolean
    fun activeWindowRoot(): AccessibilityNodeInfo? = null
}

interface SystemAccessibilityActionsOwner {
    fun currentSystemAccessibilityActions(): SystemAccessibilityActions?
}

internal fun Context.currentSystemAccessibilityActions(): SystemAccessibilityActions? =
    checkNotNull(applicationContext as? SystemAccessibilityActionsOwner) {
        "Application must implement SystemAccessibilityActionsOwner"
    }.currentSystemAccessibilityActions()

internal fun Context.findSystemAccessibilityActions(): SystemAccessibilityActions? =
    (applicationContext as? SystemAccessibilityActionsOwner)?.currentSystemAccessibilityActions()

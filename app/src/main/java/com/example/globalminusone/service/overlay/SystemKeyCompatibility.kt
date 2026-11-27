package com.example.globalminusone.service.overlay

/** API31's PhoneFallbackEventHandler attempts a restricted CLOSE_SYSTEM_DIALOGS
 * operation after an unhandled CALL key. Let children handle the key first;
 * consume only that unsupported fallback, never BACK, headset or media keys. */
internal fun isAndroid12CallFallback(sdk: Int, keyCode: Int, action: Int): Boolean =
    sdk in 31..32 && keyCode == 5 && action in 0..1

internal inline fun dispatchWithCallFallbackGuard(
    sdk: Int, keyCode: Int, action: Int, dispatch: () -> Boolean
): Boolean {
    val affected = isAndroid12CallFallback(sdk, keyCode, action)
    val handled = try { dispatch() } catch (failure: SecurityException) {
        if (affected && isCloseDialogsFallbackFailure(failure)) true else throw failure
    }
    // ViewRootImpl's fallback is outside the child's dispatch stack. Returning true
    // for this one unhandled key prevents that later framework exception as well.
    return handled || affected
}

internal fun isCloseDialogsFallbackFailure(failure: SecurityException): Boolean =
    failure.message.orEmpty().contains("CLOSE_SYSTEM_DIALOGS") && failure.stackTrace.any {
        (it.className == "com.android.internal.policy.PhoneFallbackEventHandler" &&
            it.methodName in setOf("sendCloseSystemWindows", "startCallActivity", "onKeyUp")) ||
            (it.className == "com.android.internal.policy.PhoneWindow" && it.methodName == "sendCloseSystemWindows")
    }

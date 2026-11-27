package com.example.globalminusone.service.embedded

import android.view.WindowManager.LayoutParams

/** Remote editors own focus; their normal system IME must appear above the output overlay. */
internal fun embeddedHostFocusFlags(current: Int, remoteInput: Boolean): Int {
    val cleared = current and LayoutParams.FLAG_NOT_FOCUSABLE.inv() and LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
    return if (remoteInput) cleared or LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_ALT_FOCUSABLE_IM else cleared
}

internal fun applicationWindowInputFlags(current: Int, hidden: Boolean, remoteInput: Boolean): Int {
    val cleared = current and LayoutParams.FLAG_NOT_TOUCHABLE.inv()
    return if (hidden) (cleared and LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()) or
        LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCHABLE
    else embeddedHostFocusFlags(cleared, remoteInput)
}

/**
 * WindowManager focus & outside-touch flags for standalone floating application windows (`小窗`):
 * - `hidden == true`: invisible, non-touchable, non-focusable (`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`).
 * - `windowFocused == false` (user touched outside or switched to another `小窗`):
 *   touchable so a tap inside reactivates it, but `FLAG_NOT_FOCUSABLE` (with `FLAG_ALT_FOCUSABLE_IM` cleared)
 *   so 100% of system Back/navigation gestures and key events go to the outside app or the active `小窗`.
 * - `windowFocused == true` (currently operated `小窗`):
 *   - `FLAG_WATCH_OUTSIDE_TOUCH` is set to detect touches outside this `小窗`.
 *   - `FLAG_NOT_FOCUSABLE` is CLEARED so Android/ColorOS routes system edge-swipe Back gestures
 *     (`OnBackInvokedCallback` & `KEYCODE_BACK`) directly to this `小窗`.
 *   - When `remoteInput == true`, `FLAG_ALT_FOCUSABLE_IM` is SET alone (`NOT_FOCUSABLE=0, ALT_FOCUSABLE_IM=1`),
 *     for which AOSP `LayoutParams.mayUseInputMethod(flags) == false` — meaning the host frame receives
 *     system Back gestures WITHOUT stealing IME focus from the remote VirtualDisplay's soft keyboard!
 */
internal fun applicationWindowFocusFlags(
    current: Int,
    hidden: Boolean,
    remoteInput: Boolean,
    windowFocused: Boolean
): Int {
    val cleared = current and
        LayoutParams.FLAG_NOT_TOUCHABLE.inv() and
        LayoutParams.FLAG_NOT_FOCUSABLE.inv() and
        LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv() and
        LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
    if (hidden) {
        return cleared or LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCHABLE
    }
    if (!windowFocused) {
        return cleared or LayoutParams.FLAG_NOT_FOCUSABLE
    }
    val watched = cleared or LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    return if (remoteInput) {
        watched or LayoutParams.FLAG_ALT_FOCUSABLE_IM
    } else {
        watched
    }
}

/**
 * Determines whether an `ACTION_OUTSIDE` touch at `(rawX, rawY)` in full display coordinates
 * represents a genuine user tap on the outside screen (which should defocus the active `小窗`),
 * as opposed to:
 * 1. Starting inside a system navigation edge-gesture zone (left/right Back swipe, top status bar, bottom home bar),
 *    which MUST keep the active `小窗` focused so the edge-swipe Back gesture is delivered to it;
 * 2. Tapping inside a currently visible soft keyboard (`imeBottomPx > 0`);
 * 3. Tapping inside another visible `小窗` (whose own `ACTION_DOWN` claims active focus directly).
 */
internal fun shouldOutsideTouchDefocusWindow(
    rawX: Float,
    rawY: Float,
    fullDisplayWidth: Int,
    fullDisplayHeight: Int,
    leftGestureEdgePx: Int,
    rightGestureEdgePx: Int,
    topSystemEdgePx: Int,
    bottomGestureEdgePx: Int,
    imeBottomPx: Int,
    insideAnotherVisibleWindow: Boolean = false
): Boolean {
    if (insideAnotherVisibleWindow) return false
    if (fullDisplayWidth <= 0 || fullDisplayHeight <= 0) return false
    if (rawX < leftGestureEdgePx || rawX > fullDisplayWidth - rightGestureEdgePx) return false
    if (rawY < topSystemEdgePx || rawY > fullDisplayHeight - bottomGestureEdgePx) return false
    if (imeBottomPx > 0 && rawY >= fullDisplayHeight - imeBottomPx) return false
    return true
}

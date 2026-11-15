package com.example.globalminusone.split

import android.content.Intent

/** Archived public-route policy kept for compatibility; the current default uses explicit task pairing. */
internal fun canRequestAdjacentSplit(sdk: Int, sourceInPip: Boolean) = sdk >= 32 && !sourceInPip
internal fun adjacentSplitLaunchFlags() = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
internal fun canReplaceSplitSource(launchMode: Int) = launchMode == 0 || launchMode == 1
internal enum class SplitLaunchOutcome { WAITING, MULTI_WINDOW_REPORTED, UNCONFIRMED, FAILED }

/** A launch return code is deliberately not success. Observed source state reports multi-window, which also includes freeform; it cannot prove split-screen. */
internal class SplitLaunchTracker(private val timeoutMs: Long = 5000) {
    private var generation = 0L
    private var current: Long? = null
    private var deadline = 0L
    private var startedAt = 0L
    fun begin(now: Long): Long { startedAt = now; deadline = now + timeoutMs; return (++generation).also { current = it } }
    fun observe(token: Long, sourceInMultiWindow: Boolean, now: Long): SplitLaunchOutcome? {
        if (token != current) return null
        return when {
            sourceInMultiWindow && now - startedAt >= 750 -> SplitLaunchOutcome.MULTI_WINDOW_REPORTED
            now >= deadline -> SplitLaunchOutcome.UNCONFIRMED
            else -> SplitLaunchOutcome.WAITING
        }.also { if (it != SplitLaunchOutcome.WAITING) current = null }
    }
    fun fail(token: Long): SplitLaunchOutcome? = if (token == current) {
        current = null; SplitLaunchOutcome.FAILED
    } else null
    fun cancel() { current = null; generation++ }
}

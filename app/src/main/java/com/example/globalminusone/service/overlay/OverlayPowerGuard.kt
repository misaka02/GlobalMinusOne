package com.example.globalminusone.service.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.example.globalminusone.service.WidgetHostManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coordinates overlay power-saving across panel expand/collapse and screen on/off:
 * pauses AppWidgetHost binder updates when idle or screen-off (keeping cached views intact)
 * and suspends decorative animators while the display is off.
 */
internal class OverlayPowerGuard(
    private val context: Context,
    private val scope: CoroutineScope,
    private val widgetHostManager: WidgetHostManager,
    private val hasWidgets: () -> Boolean,
    private val onScreenInteractiveChanged: (Boolean) -> Unit
) {
    private var panelExpanded = false
    private var screenInteractive = true
    private var receiverRegistered = false
    private var idlePauseJob: Job? = null
    private var resumeWidgetsJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
                    idlePauseJob?.cancel()
                    idlePauseJob = null
                    resumeWidgetsJob?.cancel()
                    resumeWidgetsJob = null
                    widgetHostManager.pauseListening()
                    onScreenInteractiveChanged(false)
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenInteractive = true
                    onScreenInteractiveChanged(true)
                    if (panelExpanded && hasWidgets()) {
                        scheduleWidgetResume()
                    }
                }
            }
        }
    }

    fun register() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        receiverRegistered = runCatching {
            ContextCompat.registerReceiver(context, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            true
        }.getOrDefault(false)
    }

    fun onPanelExpandedChanged(expanded: Boolean) {
        panelExpanded = expanded
        idlePauseJob?.cancel()
        idlePauseJob = null
        if (expanded) {
            if (screenInteractive && hasWidgets()) {
                scheduleWidgetResume()
            }
        } else {
            resumeWidgetsJob?.cancel()
            resumeWidgetsJob = null
            idlePauseJob = scope.launch {
                delay(IDLE_WIDGET_PAUSE_DELAY_MS)
                if (!panelExpanded) widgetHostManager.pauseListening()
            }
        }
    }

    fun syncWidgetPresence() {
        if (!hasWidgets()) {
            idlePauseJob?.cancel()
            idlePauseJob = null
            resumeWidgetsJob?.cancel()
            resumeWidgetsJob = null
            widgetHostManager.pauseListening()
        } else if (panelExpanded && screenInteractive) {
            scheduleWidgetResume()
        }
    }

    private fun scheduleWidgetResume() {
        resumeWidgetsJob?.cancel()
        resumeWidgetsJob = scope.launch {
            delay(RESUME_WIDGET_DELAY_MS)
            if (panelExpanded && screenInteractive && hasWidgets()) {
                widgetHostManager.startListening()
            }
        }
    }

    fun destroy() {
        idlePauseJob?.cancel()
        idlePauseJob = null
        resumeWidgetsJob?.cancel()
        resumeWidgetsJob = null
        if (receiverRegistered) {
            receiverRegistered = false
            runCatching { context.unregisterReceiver(screenReceiver) }
        }
    }

    private companion object {
        const val IDLE_WIDGET_PAUSE_DELAY_MS = 15_000L
        const val RESUME_WIDGET_DELAY_MS = 120L
    }
}

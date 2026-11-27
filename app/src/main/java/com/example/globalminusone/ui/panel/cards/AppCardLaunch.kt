package com.example.globalminusone.ui.panel.cards

import android.content.Context
import com.example.globalminusone.browser.EmbeddedAppTargets
import com.example.globalminusone.data.AppLaunchMode
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.globalminusone.ui.panel.EmbeddedApplicationTarget

internal fun applicationCard(packageName: String, label: String, mode: AppLaunchMode) = ComponentItem(
    type = ComponentType.APP_ICON, title = label, packageName = packageName, appLaunchMode = mode)

/** A floating-only card never falls back to a full-screen application on failure. */
internal suspend fun resolveFloatingApplication(context: Context, packageName: String, label: String): EmbeddedApplicationTarget? =
    withContext(Dispatchers.IO) {
        val launch = runCatching { context.packageManager.getLaunchIntentForPackage(packageName)?.component }.getOrNull()
            ?: return@withContext null
        EmbeddedAppTargets.resolve(context, launch)?.let { EmbeddedApplicationTarget(it, label) }
    }

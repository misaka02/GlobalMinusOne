package com.example.globalminusone.browser

import android.content.Context
import android.webkit.WebView
import androidx.annotation.UiThread
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID

/** UI-thread only. A private WebView must be destroyed before its profile is deleted. */
internal class PrivateWebProfile(context: Context) {
    private val appContext = context.applicationContext
    private var lifecycle = PrivateProfileLifecycle<WebView>()
    var activeName: String? = null
        private set

    fun supported(): Boolean = runCatching {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    }.getOrDefault(false)

    @UiThread fun start(): Boolean {
        if (activeName != null || !cleanupStaleOnStartup(appContext)) return false
        if (!supported()) return false
        val name = PREFIX + UUID.randomUUID().toString()
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Register first so a crash between profile creation and attachment remains recoverable.
        if (!prefs.edit().putStringSet(KEY_PENDING, pendingNames(appContext) + name).commit()) return false
        return runCatching {
            ProfileStore.getInstance().getOrCreateProfile(name)
            lifecycle = PrivateProfileLifecycle()
            activeNames.add(name)
            activeName = name
            true
        }.getOrDefault(false)
    }

    @UiThread fun bind(view: WebView) {
        WebViewCompat.setProfile(view, checkNotNull(activeName))
        lifecycle.bind(view)
    }

    /** Null means a bound WebView still needs to be destroyed. */
    @UiThread fun finishIfUnbound(): Boolean? = if (activeName == null) null else
        lifecycle.finishIfUnbound(::deleteActive)

    /** Must be called after view.destroy(). Other WebViews cannot release this profile. */
    @UiThread fun onViewDestroyed(view: WebView): Boolean? = if (activeName == null) null else
        lifecycle.onViewDestroyed(view, ::deleteActive)

    /** Returns true only after the provider confirms deletion. Never touches the default profile. */
    private fun deleteActive(): Boolean {
        val name = activeName ?: return false
        val deleted = runCatching {
            supported() && (ProfileStore.getInstance().getProfile(name) == null ||
                ProfileStore.getInstance().deleteProfile(name))
        }.getOrDefault(false)
        activeNames.remove(name)
        activeName = null
        if (deleted) removePending(appContext, name)
        return deleted
    }

    companion object {
        private const val PREFS = "private_web_profiles"
        private const val KEY_PENDING = "pending_profile_names"
        private const val PREFIX = "globalminusone_ephemeral_"
        private val activeNames = mutableSetOf<String>()

        private fun pendingNames(context: Context): Set<String> = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PENDING, emptySet()).orEmpty()
            .filterTo(mutableSetOf()) { it.startsWith(PREFIX) }

        private fun removePending(context: Context, name: String) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(KEY_PENDING, pendingNames(context) - name).commit()
        }

        /** Reads only a small preference when there is no private-profile residue. */
        @UiThread fun cleanupStaleOnStartup(context: Context): Boolean {
            val pending = pendingNames(context)
            if (pending.isEmpty()) return true
            if (!runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)) return false
            val store = runCatching { ProfileStore.getInstance() }.getOrNull() ?: return false
            for (name in pending) {
                if (name in activeNames) continue
                val deleted = runCatching { store.getProfile(name) == null || store.deleteProfile(name) }.getOrDefault(false)
                if (deleted) removePending(context, name)
            }
            return pendingNames(context).all { it in activeNames }
        }
    }
}

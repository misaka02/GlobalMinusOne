package com.example.globalminusone.browser

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/** Public PackageManager + manifest preflight for menu visibility. The privileged engine checks again. */
object EmbeddedAppTargets {
    private val chrome = setOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary")
    private const val CHROME_TABBED = "org.chromium.chrome.browser.ChromeTabbedActivity"
    private const val ANDROID = "http://schemas.android.com/apk/res/android"

    /** All Chrome launcher aliases share one app-window identity and safe native route. */
    fun canonical(component: ComponentName): ComponentName =
        if (component.packageName in chrome) ComponentName(component.packageName, CHROME_TABBED) else component

    /** Performs package/resource I/O off the UI thread; null means no supported in-panel route. */
    suspend fun resolve(context: Context, component: ComponentName): ComponentName? = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            val target = canonical(component)
            val info = pm.getActivityInfo(target, 0)
            check(info.enabled && info.applicationInfo.enabled && info.exported)
            check(pm.getComponentEnabledSetting(target) in listOf(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.COMPONENT_ENABLED_STATE_ENABLED))
            check(pm.getApplicationEnabledSetting(target.packageName) in listOf(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.COMPONENT_ENABLED_STATE_ENABLED))
            val resizable = manifestResizable(pm, info)
            if (component.packageName in chrome) {
                val major = EmbeddedBrowserVersion.major(pm.getPackageInfo(target.packageName, 0).versionName)
                ChromeLaunchPlan.requireBrowser(Build.VERSION.SDK_INT, major, info.launchMode, true, resizable)
            } else EmbeddedAppLaunchPlan.flags(Build.VERSION.SDK_INT, info.launchMode, info.documentLaunchMode, true, resizable)
            target
        }.getOrElse { failure ->
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            null
        }
    }

    // ActivityInfo.resizeMode is hidden. Read the installed manifest through public Resources APIs
    // rather than granting hidden-API exemptions to the unprivileged application process.
    private fun manifestResizable(pm: PackageManager, info: ActivityInfo): Boolean {
        val resources = pm.getResourcesForApplication(info.applicationInfo)
        val actualActivity = info.targetActivity ?: info.name
        fun fullName(name: String) = when {
            name.startsWith('.') -> info.packageName + name
            '.' !in name -> "${info.packageName}.$name"
            else -> name
        }
        var appResize: Boolean? = null
        var activityResize: Boolean? = null
        var found = false
        var baseSeen = false
        // The public cookie overload avoids accidentally reading only the last config split's
        // manifest. Android's resource path order puts the base before its activity splits.
        for (cookie in 1..64) {
            val parser = try { resources.assets.openXmlResourceParser(cookie, "AndroidManifest.xml") }
                catch (_: java.io.IOException) { continue }
            parser.use {
                var matching = false
                var base = false
                fun resizeAttribute(): Boolean? {
                    val raw = parser.getAttributeValue(ANDROID, "resizeableActivity") ?: return null
                    val resource = parser.getAttributeResourceValue(ANDROID, "resizeableActivity", 0)
                    return if (resource != 0) resources.getBoolean(resource) else raw.toBooleanStrict()
                }
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                        "manifest" -> {
                            matching = parser.getAttributeValue(null, "package") == info.packageName
                            base = parser.getAttributeValue(null, "split").isNullOrBlank()
                            if (matching && base) baseSeen = true
                        }
                        "application" -> if (matching && base) appResize = resizeAttribute()
                        "activity" -> if (matching && parser.getAttributeValue(ANDROID, "name")?.let(::fullName) == fullName(actualActivity)) {
                            found = true; activityResize = resizeAttribute()
                        }
                    }
                    parser.next()
                }
            }
            if (baseSeen && found) return EmbeddedResizePolicy.resizable(info.applicationInfo.targetSdkVersion, appResize, activityResize)
        }
        error("Cannot identify the installed activity manifest")
    }
}

internal object EmbeddedResizePolicy {
    fun resizable(targetSdk: Int, application: Boolean?, activity: Boolean?): Boolean =
        activity ?: application ?: (targetSdk >= 24)
}

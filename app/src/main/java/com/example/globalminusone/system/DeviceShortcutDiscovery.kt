package com.example.globalminusone.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.Log
import com.example.globalminusone.data.QuickActionItem
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Describes a real device activity advertising `Intent.ACTION_CREATE_SHORTCUT`. */
data class DeviceShortcutProviderInfo(
    val packageName: String,
    val activityName: String,
    val appLabel: String,
    val shortcutLabel: String
) {
    val componentName: ComponentName
        get() = ComponentName(packageName, activityName)
}

/** Snapshot of real shortcuts discoverable on the current device. */
data class DeviceShortcutsSnapshot(
    val createProviders: List<DeviceShortcutProviderInfo>,
    val hostPinnedShortcuts: List<QuickActionItem>,
    val hasShortcutHostPermission: Boolean,
    val installedPackageNames: Set<String>
)

/**
 * Pure filtering & URI helper for device-discovered shortcuts and installed preset matching.
 */
internal object ShortcutDiscoveryPolicy {
    fun filterBuiltInPresets(
        presets: List<QuickActionItem>,
        installedPackages: Set<String>,
        includeUninstalled: Boolean
    ): Pair<List<QuickActionItem>, Int> {
        val installedCount = presets.count { it.packageName.isBlank() || it.packageName in installedPackages }
        val filtered = if (includeUninstalled) {
            presets
        } else {
            presets.filter { it.packageName.isBlank() || it.packageName in installedPackages }
        }
        return filtered to installedCount
    }

    fun isBuiltInToolPreset(item: QuickActionItem): Boolean = item.packageName.isBlank()

    fun buildAppShortcutUri(packageName: String, shortcutId: String): String =
        "appshortcut://${packageName.trim()}/${shortcutId.trim()}"

    fun parseAppShortcutUri(uri: String): Pair<String, String>? {
        if (!uri.startsWith("appshortcut://")) return null
        val rest = uri.removePrefix("appshortcut://")
        val slash = rest.indexOf('/')
        if (slash <= 0 || slash >= rest.lastIndex) return null
        val pkg = rest.substring(0, slash).trim()
        val id = rest.substring(slash + 1).trim()
        return if (pkg.isNotEmpty() && id.isNotEmpty()) pkg to id else null
    }
}

/**
 * Discovers real shortcuts on the device (`ACTION_CREATE_SHORTCUT` providers, installed packages,
 * and dynamic/pinned `LauncherApps` shortcuts when host permission is granted by the OS).
 */
internal object DeviceShortcutDiscovery {
    private const val TAG = "DeviceShortcutDiscovery"

    fun scan(context: Context): DeviceShortcutsSnapshot {
        val pm = context.packageManager
        val installedPkgs = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0).mapNotNull { it.packageName }.toSet()
        }.getOrDefault(emptySet())

        val providers = queryCreateShortcutProviders(pm)
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val hostGranted = runCatching { launcherApps?.hasShortcutHostPermission() == true }.getOrDefault(false)

        val hostShortcuts = if (hostGranted && launcherApps != null) {
            queryHostShortcuts(pm, launcherApps)
        } else {
            emptyList()
        }

        return DeviceShortcutsSnapshot(
            createProviders = providers,
            hostPinnedShortcuts = hostShortcuts,
            hasShortcutHostPermission = hostGranted,
            installedPackageNames = installedPkgs
        )
    }

    fun queryCreateShortcutProviders(pm: PackageManager): List<DeviceShortcutProviderInfo> {
        val intent = Intent(Intent.ACTION_CREATE_SHORTCUT)
        val resolved = runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }.getOrDefault(emptyList())

        return resolved.mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            if (!ai.enabled || !ai.exported) return@mapNotNull null
            val shortcutLabel = runCatching { ri.loadLabel(pm).toString().trim() }.getOrDefault("")
            val appLabel = runCatching { ai.applicationInfo.loadLabel(pm).toString().trim() }.getOrDefault("")
            DeviceShortcutProviderInfo(
                packageName = ai.packageName,
                activityName = ai.name,
                appLabel = appLabel.ifBlank { ai.packageName },
                shortcutLabel = shortcutLabel.ifBlank { appLabel.ifBlank { ai.name } }
            )
        }.distinctBy { "${it.packageName}/${it.activityName}" }
            .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.shortcutLabel.lowercase() }))
    }

    private fun queryHostShortcuts(pm: PackageManager, launcherApps: LauncherApps): List<QuickActionItem> {
        val query = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
            )
        }
        val list: List<ShortcutInfo> = runCatching {
            launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
        }.getOrElse { failure ->
            Log.w(TAG, "LauncherApps.getShortcuts failed", failure)
            emptyList()
        }

        return list.filter { it.isEnabled }.map { info ->
            val appName = runCatching {
                @Suppress("DEPRECATION")
                pm.getApplicationLabel(pm.getApplicationInfo(info.`package`, 0)).toString()
            }.getOrDefault(info.`package`)
            val label = (info.shortLabel ?: info.longLabel ?: info.id).toString().trim()
            QuickActionItem(
                id = "host_sc_${info.`package`}_${info.id}",
                title = label,
                subtitle = "$appName · 系统快捷方式",
                iconName = "bolt",
                colorHex = "#3F51B5",
                intentUri = ShortcutDiscoveryPolicy.buildAppShortcutUri(info.`package`, info.id),
                packageName = info.`package`
            )
        }.sortedBy { it.title.lowercase() }
    }

    /** Persists a shortcut icon bitmap under `filesDir/shortcut_icons/` and returns a `file://` URI string. */
    fun saveShortcutIconBitmap(context: Context, bitmap: Bitmap): String? {
        return try {
            val dir = File(context.filesDir, "shortcut_icons").apply { mkdirs() }
            val file = File(dir, "sc_${UUID.randomUUID()}.png")
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return null
            }
            file.toURI().toString()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to persist shortcut icon bitmap", t)
            null
        }
    }

    fun drawableToBitmap(drawable: Drawable?, sizePx: Int = 128): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
        return runCatching {
            val bmp = Bitmap.createBitmap(width.coerceIn(32, 256), height.coerceIn(32, 256), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }.getOrNull()
    }
}

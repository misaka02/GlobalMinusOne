package com.example.globalminusone.system

import android.os.Build
import java.util.Locale

/** No private ColorOS intents or hidden windowing APIs are used here.
 * Official user paths: https://www.oppo.com/cn/newsroom/stories/468/
 * https://www.oneplus.com/cn/coloros
 * Android LAUNCH_ADJACENT is a best-effort foreground Activity API, not a ColorOS small-window API.
 * https://developer.android.com/develop/ui/views/layout/support-multi-window-mode
 * Manufacturer hints are conservative user guidance, not OEM launch APIs:
 * https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1593
 * https://www.vivo.com.cn/originos
 * https://consumer.huawei.com/cn/support/content/zh-cn15985857/
 */
internal object OemWindowLauncher {
    fun isColorOsFamily(manufacturer: String = Build.MANUFACTURER): Boolean =
        manufacturer.lowercase(Locale.ROOT) in setOf("oppo", "oneplus", "realme")

    fun guidanceForManufacturer(smallWindow: Boolean, manufacturer: String = Build.MANUFACTURER): String {
        val family = manufacturer.trim().lowercase(Locale.ROOT)
        if (isColorOsFamily(family)) return guidance(smallWindow, true)
        val systemName = when (family) {
            "xiaomi", "redmi" -> "小米 / Redmi 的 HyperOS"
            "vivo", "iqoo" -> "vivo / iQOO 的 OriginOS"
            "huawei" -> "华为 EMUI / HarmonyOS 安卓兼容版"
            else -> "当前手机系统"
        }
        val option = if (smallWindow) "小窗 / 浮窗" else "分屏"
        val extra = if (family == "huawei") "也可查找智慧多窗。此 Android APK 不代表支持原生鸿蒙应用系统。" else ""
        return "将先正常打开应用；已有无障碍服务时，确认应用进入前台后协助打开最近任务。仍须您在系统任务菜单选择$option；这不是强制小窗或分屏。\n\n请在$systemName 的任务菜单中查找$option。具体入口、系统版本和应用支持范围可能不同。$extra"
    }

    fun guidance(smallWindow: Boolean, colorOs: Boolean): String {
        val limitation = "当前面板尚不支持直接以系统${if (smallWindow) "小窗" else "分屏"}启动应用。将先正常打开应用；已有无障碍服务时，确认应用进入前台后协助打开最近任务。仍须您在任务菜单选择浮窗或分屏；这是系统操作指引，不是强制一键启动。"
        val steps = if (colorOs) {
            if (smallWindow) "先正常打开应用，再进入系统最近任务，打开该任务的菜单并查找“浮窗”；也可使用 ColorOS 智能侧边栏。ColorOS 16 的具体入口和可用应用以手机显示为准。"
            else "先正常打开应用，再进入系统最近任务，打开该任务菜单并查找“分屏”；也可从 ColorOS 智能侧边栏拖出支持的应用。是否支持由系统和目标应用决定。"
        } else "先正常打开应用，再进入系统最近任务，在任务菜单中选择${if (smallWindow) "小窗（若提供）" else "分屏"}。入口和支持范围由手机系统决定。"
        return "$limitation\n\n$steps"
    }
}

package com.example.globalminusone.data

/** Native window layer currently owning an overlay window or slot. */
enum class OverlayWindowLayer {
    ACCESSIBILITY,
    APPLICATION,
    UNREGISTERED,
    NONE;

    fun label(): String = when (this) {
        ACCESSIBILITY -> "无障碍层"
        APPLICATION -> "普通层"
        UNREGISTERED -> "未挂载"
        NONE -> "未启用"
    }
}

/** Live overlay window placement state observed by settings UI. */
data class OverlayLayerStatus(
    val requestedAccessibilityLayer: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val panelLayer: OverlayWindowLayer = OverlayWindowLayer.UNREGISTERED,
    val triggerLayer: OverlayWindowLayer = OverlayWindowLayer.NONE,
    val lastError: String? = null
) {
    fun summaryText(): String = when {
        !requestedAccessibilityLayer ->
            "当前：普通悬浮层（电话本、系统设置等防遮挡界面会被系统隐藏悬浮窗）"
        !accessibilityConnected ->
            "当前：未生效（无障碍服务未开启，已回退普通悬浮层，电话本等受保护界面无法覆盖）"
        panelLayer == OverlayWindowLayer.ACCESSIBILITY &&
            (triggerLayer == OverlayWindowLayer.ACCESSIBILITY || triggerLayer == OverlayWindowLayer.NONE) ->
            "当前：无障碍悬浮层已生效（面板与触发区均已切换，可在电话本等受保护界面呼出）"
        !lastError.isNullOrBlank() ->
            "当前：无障碍层挂载失败，已自动回退普通悬浮层（$lastError）"
        else ->
            "当前：部分窗口回退普通悬浮层（面板=${panelLayer.label()}，触发区=${triggerLayer.label()}）"
    }
}

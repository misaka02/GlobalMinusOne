package com.example.globalminusone.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Sentinel for per-module durations that inherit [ThemeConfig.animationDurationMs]. */
const val FOLLOW_GLOBAL_ANIMATION_MS = -1

@Serializable
enum class SubPanelTransitionStyle(val label: String) {
    PORTAL_EXPAND("入口展开"),
    FADE_SCALE("缩放淡入"),
    SLIDE("横向滑入"),
    NONE("无动画")
}

@Serializable
enum class CardReorderMotionStyle(val label: String) {
    SMOOTH("柔和跟手"),
    SNAPPY("利落回弹"),
    BOUNCY("灵动弹性"),
    NONE("无动画")
}

/**
 * Per-module animation overrides layered over [ThemeConfig.animationDurationMs].
 * Any field set to [FOLLOW_GLOBAL_ANIMATION_MS] dynamically tracks the global duration.
 */
@Immutable
@Serializable
data class ModuleAnimationConfig(
    val panelOpenCloseDurationMs: Int = FOLLOW_GLOBAL_ANIMATION_MS,
    val pageSwitchDurationMs: Int = FOLLOW_GLOBAL_ANIMATION_MS,
    val subPanelDurationMs: Int = FOLLOW_GLOBAL_ANIMATION_MS,
    val subPanelTransitionStyle: SubPanelTransitionStyle = SubPanelTransitionStyle.PORTAL_EXPAND,
    val cardReorderDurationMs: Int = FOLLOW_GLOBAL_ANIMATION_MS,
    val cardReorderStyle: CardReorderMotionStyle = CardReorderMotionStyle.SMOOTH,
    val containerPopupDurationMs: Int = FOLLOW_GLOBAL_ANIMATION_MS,
    val modalMenuDurationMs: Int = FOLLOW_GLOBAL_ANIMATION_MS
) {
    fun hasCustomOverrides(): Boolean =
        panelOpenCloseDurationMs != FOLLOW_GLOBAL_ANIMATION_MS ||
            pageSwitchDurationMs != FOLLOW_GLOBAL_ANIMATION_MS ||
            subPanelDurationMs != FOLLOW_GLOBAL_ANIMATION_MS ||
            subPanelTransitionStyle != SubPanelTransitionStyle.PORTAL_EXPAND ||
            cardReorderDurationMs != FOLLOW_GLOBAL_ANIMATION_MS ||
            cardReorderStyle != CardReorderMotionStyle.SMOOTH ||
            containerPopupDurationMs != FOLLOW_GLOBAL_ANIMATION_MS ||
            modalMenuDurationMs != FOLLOW_GLOBAL_ANIMATION_MS
}

fun resolveModuleAnimationMs(overrideMs: Int, globalMs: Int): Int {
    val safeGlobal = globalMs.coerceIn(0, 800)
    return if (overrideMs < 0) safeGlobal else overrideMs.coerceIn(0, 800)
}

fun formatModuleAnimationSummary(overrideMs: Int, globalMs: Int): String = when {
    overrideMs < 0 -> "跟随全局 (${globalMs.coerceIn(0, 800)}ms)"
    overrideMs == 0 -> "0ms (已关闭)"
    else -> "${overrideMs.coerceIn(0, 800)}ms (独立)"
}

fun ThemeConfig.resolvedPanelOpenCloseMs(): Int =
    resolveModuleAnimationMs(moduleAnimations.panelOpenCloseDurationMs, animationDurationMs)

fun ThemeConfig.resolvedPageSwitchMs(): Int =
    resolveModuleAnimationMs(moduleAnimations.pageSwitchDurationMs, animationDurationMs)

fun ThemeConfig.resolvedSubPanelMs(): Int =
    if (moduleAnimations.subPanelTransitionStyle == SubPanelTransitionStyle.NONE) 0
    else resolveModuleAnimationMs(moduleAnimations.subPanelDurationMs, animationDurationMs)

fun ThemeConfig.resolvedCardReorderMs(): Int =
    if (moduleAnimations.cardReorderStyle == CardReorderMotionStyle.NONE) 0
    else resolveModuleAnimationMs(moduleAnimations.cardReorderDurationMs, animationDurationMs)

fun ThemeConfig.resolvedContainerPopupMs(): Int =
    resolveModuleAnimationMs(moduleAnimations.containerPopupDurationMs, animationDurationMs)

fun ThemeConfig.resolvedModalMenuMs(): Int =
    resolveModuleAnimationMs(moduleAnimations.modalMenuDurationMs, animationDurationMs)

package com.example.globalminusone

import androidx.compose.ui.geometry.Rect
import com.example.globalminusone.data.CardReorderMotionStyle
import com.example.globalminusone.data.FOLLOW_GLOBAL_ANIMATION_MS
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.ModuleAnimationConfig
import com.example.globalminusone.data.SubPanelTransitionStyle
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.resolvedCardReorderMs
import com.example.globalminusone.data.resolvedContainerPopupMs
import com.example.globalminusone.data.resolvedModalMenuMs
import com.example.globalminusone.data.resolvedPageSwitchMs
import com.example.globalminusone.data.resolvedPanelOpenCloseMs
import com.example.globalminusone.data.resolvedSubPanelMs
import com.example.globalminusone.data.storage.ConfigurationCodec
import com.example.globalminusone.ui.motion.MotionTokens
import com.example.globalminusone.ui.motion.SubPanelTransitionDirection
import com.example.globalminusone.ui.motion.computeSubPanelPortalTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationConfigTest {
    @Test
    fun legacyV5JsonWithoutModuleAnimations_decodesWithFollowGlobalDefaults() {
        val legacyJson = """{"themeConfig":{"animationDurationMs":320}}"""
        val decoded = ConfigurationCodec.json.decodeFromString(GlobalMinusOneState.serializer(), legacyJson)
        val theme = decoded.themeConfig
        assertEquals(320, theme.animationDurationMs)
        assertEquals(FOLLOW_GLOBAL_ANIMATION_MS, theme.moduleAnimations.panelOpenCloseDurationMs)
        assertEquals(SubPanelTransitionStyle.PORTAL_EXPAND, theme.moduleAnimations.subPanelTransitionStyle)
        assertEquals(CardReorderMotionStyle.SMOOTH, theme.moduleAnimations.cardReorderStyle)
        assertFalse(theme.moduleAnimations.hasCustomOverrides())
        assertEquals(320, theme.resolvedPanelOpenCloseMs())
        assertEquals(320, theme.resolvedPageSwitchMs())
        assertEquals(320, theme.resolvedSubPanelMs())
        assertEquals(320, theme.resolvedCardReorderMs())
        assertEquals(320, theme.resolvedContainerPopupMs())
        assertEquals(320, theme.resolvedModalMenuMs())
    }

    @Test
    fun moduleAnimationOverrides_applyIndependentlyAndHonorStyleNone() {
        val theme = ThemeConfig(
            animationDurationMs = 260,
            moduleAnimations = ModuleAnimationConfig(
                panelOpenCloseDurationMs = 140,
                pageSwitchDurationMs = 0,
                subPanelDurationMs = 300,
                subPanelTransitionStyle = SubPanelTransitionStyle.NONE,
                cardReorderDurationMs = 180,
                cardReorderStyle = CardReorderMotionStyle.BOUNCY,
                containerPopupDurationMs = FOLLOW_GLOBAL_ANIMATION_MS,
                modalMenuDurationMs = 420
            )
        )
        assertTrue(theme.moduleAnimations.hasCustomOverrides())
        assertEquals(140, theme.resolvedPanelOpenCloseMs())
        assertEquals(0, theme.resolvedPageSwitchMs())
        assertEquals(0, theme.resolvedSubPanelMs())
        assertEquals(180, theme.resolvedCardReorderMs())
        assertEquals(260, theme.resolvedContainerPopupMs())
        assertEquals(420, theme.resolvedModalMenuMs())
    }

    @Test
    fun subPanelPortalTransform_supportsAllFourStyles() {
        val panel = Rect(0f, 0f, 300f, 600f)
        val anchor = Rect(20f, 80f, 90f, 150f)

        val portal = computeSubPanelPortalTransform(0f, SubPanelTransitionDirection.ENTER_FROM_CARD, anchor, panel, SubPanelTransitionStyle.PORTAL_EXPAND)
        assertTrue(portal.scale < 0.75f)
        assertTrue(portal.translationX < 0f)
        assertTrue(portal.portalRimAlpha > 0.1f)

        val fade = computeSubPanelPortalTransform(0f, SubPanelTransitionDirection.ENTER_FROM_CARD, anchor, panel, SubPanelTransitionStyle.FADE_SCALE)
        assertEquals(0.90f, fade.scale, 0.001f)
        assertEquals(0f, fade.translationX, 0.001f)
        assertEquals(0f, fade.portalRimAlpha, 0.001f)

        val slide = computeSubPanelPortalTransform(0f, SubPanelTransitionDirection.ENTER_FROM_CARD, anchor, panel, SubPanelTransitionStyle.SLIDE)
        assertEquals(1f, slide.scale, 0.001f)
        assertTrue(slide.translationX > 10f)

        val none = computeSubPanelPortalTransform(0f, SubPanelTransitionDirection.ENTER_FROM_CARD, anchor, panel, SubPanelTransitionStyle.NONE)
        assertEquals(1f, none.scale, 0.001f)
        assertEquals(1f, none.alpha, 0.001f)
        assertEquals(0f, none.translationX, 0.001f)
    }

    @Test
    fun cardReorderSprings_buildValidSpecsAcrossStyles() {
        CardReorderMotionStyle.values().forEach { style ->
            MotionTokens.cardSettleSpring(220, style)
            MotionTokens.cardOffsetSpring(220, style)
        }
    }
}

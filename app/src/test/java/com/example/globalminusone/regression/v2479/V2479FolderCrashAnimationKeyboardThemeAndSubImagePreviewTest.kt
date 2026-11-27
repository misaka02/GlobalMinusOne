package com.example.globalminusone.regression.v2479

import com.example.globalminusone.data.policy.CONTAINER_VIEWPORT_MAX_DP
import com.example.globalminusone.data.policy.contrastRatio
import com.example.globalminusone.data.policy.relativeLuminance
import com.example.globalminusone.data.policy.resolveContainerViewportHeightPx
import com.example.globalminusone.data.policy.resolveEffectiveAnimationMs
import com.example.globalminusone.data.policy.resolveKeyboardCardColors
import com.example.globalminusone.service.ScreenAssistantMode
import com.example.globalminusone.service.screenassistant.ScreenAssistantPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2479FolderCrashAnimationKeyboardThemeAndSubImagePreviewTest {

    @Test
    fun folderViewportHeightStaysFiniteUnderUnboundedPopupConstraints() {
        val fallback480Px = CONTAINER_VIEWPORT_MAX_DP * 3f // 1440px at 3x density
        val screenHeightPx = 900f * 3f // 2700px

        // Inside own popup whose parent has verticalScroll -> hasBoundedHeight == false.
        val popupViewport = resolveContainerViewportHeightPx(
            hasBoundedHeight = false,
            maxConstraintHeightPx = Float.MAX_VALUE,
            insideOwnPopup = true,
            screenHeightPx = screenHeightPx,
            maxFallbackPx = fallback480Px
        )
        assertTrue(popupViewport.isFinite())
        assertTrue("Popup viewport should not be Float.MAX_VALUE", popupViewport < fallback480Px * 6f)
        assertEquals(screenHeightPx, popupViewport, 0.5f)

        // Supports tall popups (> 480dp) when bounded by explicit containerPopupHeightDp > 480dp.
        val tallBoundedPopup = resolveContainerViewportHeightPx(
            hasBoundedHeight = true,
            maxConstraintHeightPx = 620f * 3f,
            insideOwnPopup = true,
            screenHeightPx = screenHeightPx,
            maxFallbackPx = fallback480Px
        )
        assertEquals(620f * 3f, tallBoundedPopup, 0.5f)

        // Nested child container outside its own popup stays capped at CONTAINER_VIEWPORT_MAX_DP.
        val nestedInlineViewport = resolveContainerViewportHeightPx(
            hasBoundedHeight = false,
            maxConstraintHeightPx = 4000f,
            insideOwnPopup = false,
            screenHeightPx = screenHeightPx,
            maxFallbackPx = fallback480Px
        )
        assertEquals(fallback480Px, nestedInlineViewport, 0.5f)
    }

    @Test
    fun effectiveAnimationDurationHonorsZeroSnapAndEliminatesHighMinFloor() {
        assertEquals(0, resolveEffectiveAnimationMs(0))
        assertEquals(0, resolveEffectiveAnimationMs(0, scale = 0.72f))
        assertEquals(50, resolveEffectiveAnimationMs(50, scale = 1f))
        assertEquals(20, resolveEffectiveAnimationMs(20, scale = 1f))
        assertEquals(16, resolveEffectiveAnimationMs(10, scale = 1f))
        assertEquals(36, resolveEffectiveAnimationMs(50, scale = 0.72f))
        assertEquals(400, resolveEffectiveAnimationMs(1000, scale = 1f, maxMs = 400))
    }

    @Test
    fun keyboardCardColorsMaintainHighContrastWithoutWhiteKeycapGlareOnDarkCards() {
        val darkTokens = resolveKeyboardCardColors(
            textPrimaryArgb = 0xFFFFFFFF.toInt(),
            accentArgb = 0xFF2196F3.toInt(),
            fallbackDark = true
        )
        assertTrue(darkTokens.isDark)
        assertTrue("Dark card keycap must stay dark (not glaring bright white)", relativeLuminance(darkTokens.keyBg) < 0.30f)
        assertTrue(
            "Dark keycap contrast ratio must be >= 4.5:1",
            contrastRatio(darkTokens.keyText, darkTokens.keyBg) >= 4.5f
        )
        assertEquals(0xFFFFFFFF.toInt(), darkTokens.accentOnText)

        // Light card with dark text
        val lightTokens = resolveKeyboardCardColors(
            textPrimaryArgb = 0xFF1F2329.toInt(),
            accentArgb = 0xFFFFEB3B.toInt(),
            fallbackDark = false
        )
        assertFalse(lightTokens.isDark)
        assertTrue(relativeLuminance(lightTokens.keyBg) > 0.85f)
        assertTrue(
            "Light keycap contrast ratio must be >= 4.5:1",
            contrastRatio(lightTokens.keyText, lightTokens.keyBg) >= 4.5f
        )
        // Bright yellow accent automatically picks dark text for readability
        assertEquals(0xFF121620.toInt(), lightTokens.accentOnText)
    }

    @Test
    fun subImagePreviewStateResetsAcrossModeSwitchesAndExtractionRestarts() {
        val presentation = ScreenAssistantPresentation(ScreenAssistantMode.SUB_IMAGES)
        assertFalse(presentation.previewingActiveSubImage)
        assertFalse(presentation.adjustingSubImageCrop)
        presentation.previewingActiveSubImage = true
        presentation.adjustingSubImageCrop = true

        presentation.switchMode(ScreenAssistantMode.TEXT_SELECT)
        assertFalse(presentation.previewingActiveSubImage)
        assertFalse(presentation.adjustingSubImageCrop)

        presentation.switchMode(ScreenAssistantMode.SUB_IMAGES)
        presentation.previewingActiveSubImage = true
        presentation.adjustingSubImageCrop = true
        presentation.begin(ScreenAssistantPresentation.Work.EXTRACTION)
        assertFalse(presentation.previewingActiveSubImage)
        assertFalse(presentation.adjustingSubImageCrop)
    }
}

package com.example.globalminusone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingPodStyleSpecTest {

    @Test
    fun `round trips pod size, colors, alpha and corner across all launcher URI formats`() {
        val style = FloatingPodStyleSpec(
            widthDp = 336,
            heightDp = 440,
            bgColorHex = 0xFF1E3A5FL,
            bgAlphaPercent = 88,
            textColorHex = 0xFFFFFFFFL,
            cornerRadiusDp = 24
        )
        val targets = listOf(
            ComponentItem(
                type = ComponentType.APP_KEYBOARD_LAUNCHER,
                intentUri = AppKeyboardLauncherSpec(
                    layout = KeyboardCardLayout.T9,
                    showNumberRow = false,
                    maxCandidates = 10,
                    presentationMode = LauncherCardPresentationMode.POPUP_ICON
                ).encodeToIntentUri()
            ),
            ComponentItem(
                type = ComponentType.APP_DRAWER_CARD,
                intentUri = AppDrawerCardSpec(LauncherCardPresentationMode.POPUP_ICON).encodeToIntentUri()
            ),
            ComponentItem(
                type = ComponentType.CLIPBOARD_CARD,
                intentUri = "${GENERIC_CARD_POD_URI_PREFIX}mode=${LauncherCardPresentationMode.POPUP_ICON.name}"
            ),
            ComponentItem(
                type = ComponentType.BRIGHTNESS_BAR,
                intentUri = ""
            )
        )

        for (item in targets) {
            val saved = withFloatingPodStyle(item, style)
            val parsed = parseFloatingPodStyle(saved.intentUri)
            assertEquals("Width mismatch for ${item.type}", 336.coerceAtMost(floatingPodSizeBoundsFor(item.type).maxWidthDp), parsed.widthDp)
            assertEquals("Height mismatch for ${item.type}", 440.coerceAtMost(floatingPodSizeBoundsFor(item.type).maxHeightDp), parsed.heightDp)
            assertEquals(0xFF1E3A5FL, parsed.bgColorHex)
            assertEquals(88, parsed.bgAlphaPercent)
            assertEquals(0xFFFFFFFFL, parsed.textColorHex)
            assertEquals(24, parsed.cornerRadiusDp)
        }

        val kbdSaved = withFloatingPodStyle(targets[0], style)
        val kbdSpec = AppKeyboardLauncherSpec.fromComponent(kbdSaved)
        assertEquals(KeyboardCardLayout.T9, kbdSpec.layout)
        assertFalse(kbdSpec.showNumberRow)
        assertEquals(10, kbdSpec.maxCandidates)
        assertEquals(LauncherCardPresentationMode.POPUP_ICON, kbdSpec.presentationMode)
    }

    @Test
    fun `toggling presentation mode and updating keyboard spec preserve custom pod styles`() {
        val original = ComponentItem(
            type = ComponentType.APP_KEYBOARD_LAUNCHER,
            intentUri = AppKeyboardLauncherSpec(
                layout = KeyboardCardLayout.QWERTY,
                presentationMode = LauncherCardPresentationMode.POPUP_ICON
            ).encodeToIntentUri()
        )
        val styled = withFloatingPodStyle(
            original,
            FloatingPodStyleSpec(widthDp = 320, heightDp = 310, bgColorHex = 0xFF000000L, bgAlphaPercent = 90, cornerRadiusDp = 18)
        )

        val switchedInline = withCardPresentationMode(styled, LauncherCardPresentationMode.INLINE_CARD)
        assertEquals(LauncherCardPresentationMode.INLINE_CARD, parseCardPresentationMode(switchedInline))
        assertEquals(320, parseFloatingPodStyle(switchedInline.intentUri).widthDp)
        assertEquals(0xFF000000L, parseFloatingPodStyle(switchedInline.intentUri).bgColorHex)

        val updatedKbdUri = mergeFloatingPodStyleParams(
            AppKeyboardLauncherSpec(layout = KeyboardCardLayout.T9, presentationMode = LauncherCardPresentationMode.POPUP_ICON).encodeToIntentUri(),
            switchedInline.intentUri
        )
        val restoredStyle = parseFloatingPodStyle(updatedKbdUri)
        assertEquals(320, restoredStyle.widthDp)
        assertEquals(310, restoredStyle.heightDp)
        assertEquals(0xFF000000L, restoredStyle.bgColorHex)
        assertEquals(90, restoredStyle.bgAlphaPercent)
        assertEquals(18, restoredStyle.cornerRadiusDp)
    }

    @Test
    fun `resets pod styles cleanly without dropping popup mode or keyboard flags`() {
        val kbd = withFloatingPodStyle(
            ComponentItem(
                type = ComponentType.APP_KEYBOARD_LAUNCHER,
                intentUri = AppKeyboardLauncherSpec(presentationMode = LauncherCardPresentationMode.POPUP_ICON).encodeToIntentUri()
            ),
            FloatingPodStyleSpec(widthDp = 340, bgColorHex = 0xFFFFFFFFL, cornerRadiusDp = 12)
        )
        val resetKbd = withFloatingPodStyle(kbd, FloatingPodStyleSpec())
        assertTrue(parseFloatingPodStyle(resetKbd.intentUri).isDefault)
        assertTrue(isSingleIconLauncherItem(resetKbd))

        val slider = withFloatingPodStyle(
            ComponentItem(type = ComponentType.BRIGHTNESS_BAR, intentUri = ""),
            FloatingPodStyleSpec(widthDp = 96, heightDp = 280)
        )
        val resetSlider = withFloatingPodStyle(slider, FloatingPodStyleSpec())
        assertEquals("", resetSlider.intentUri)
    }

    @Test
    fun `clamps pod dimensions safely across slider defaults and tiny hosts`() {
        val sliderBounds = floatingPodSizeBoundsFor(ComponentType.VOLUME_BAR)
        assertEquals(76, sliderBounds.defaultWidthDp)
        assertEquals(228, sliderBounds.defaultHeightDp)

        val (drawerW, drawerH) = resolveEffectivePodSizeDp(
            type = ComponentType.APP_DRAWER_CARD,
            style = FloatingPodStyleSpec(),
            defaultWidthDp = -1f,
            defaultHeightDp = -1f,
            hostWidthDp = 360f,
            hostHeightDp = 760f
        )
        assertTrue(drawerW in 280f..340f)
        assertTrue(drawerH >= 300f)

        val (tightW, tightH) = resolveEffectivePodSizeDp(
            type = ComponentType.APP_DRAWER_CARD,
            style = FloatingPodStyleSpec(widthDp = 420, heightDp = 620),
            defaultWidthDp = -1f,
            defaultHeightDp = -1f,
            hostWidthDp = 190f,
            hostHeightDp = 210f
        )
        assertEquals(174f, tightW, 0.01f)
        assertEquals(186f, tightH, 0.01f)

        val customKbdDims = resolveKeyboardPodDimensions(
            layout = KeyboardCardLayout.QWERTY,
            hostWidthDp = 390f,
            cardWidthDp = 48f,
            cardHeightDp = 48f,
            insideContainer = false,
            customPodWidthDp = 340
        )
        assertEquals(340f, customKbdDims.podWidthDp, 0.01f)

        // Ensure ultra-narrow hosts never crash with empty coercion range.
        val tinyHostDims = resolveKeyboardPodDimensions(
            layout = KeyboardCardLayout.QWERTY,
            hostWidthDp = 220f,
            cardWidthDp = 48f,
            cardHeightDp = 48f,
            insideContainer = false,
            customPodWidthDp = 320
        )
        assertEquals(204f, tinyHostDims.podWidthDp, 0.01f)
    }

    @Test
    fun `auto adapts text color for custom light or dark pod background and parses hex safely`() {
        assertEquals(0xFF1A2B3CL, parseRgbHexColor("#1a2b3c"))
        assertEquals(0xFF00FF11L, parseRgbHexColor("00ff11"))
        assertNull(parseRgbHexColor("#12345"))
        assertNull(parseRgbHexColor("GGGGGG"))

        val lightPod = FloatingPodStyleSpec(bgColorHex = 0xFFFFFFFFL, bgAlphaPercent = 95)
        val lightSurface = resolveFloatingPodSurfaceArgb(lightPod, 0xFF1E222B.toInt())
        assertFalse(isFloatingPodDarkSurface(lightPod, lightSurface, fallbackDark = true))
        assertEquals(0xFF1F2329.toInt(), resolveFloatingPodTextArgb(lightPod, lightSurface, 0xFFFFFFFF.toInt(), fallbackDark = true))

        val darkPodWithAccentText = FloatingPodStyleSpec(bgColorHex = 0xFF1E222BL, textColorHex = 0xFFF59E0BL)
        val darkSurface = resolveFloatingPodSurfaceArgb(darkPodWithAccentText, 0xFFFFFFFF.toInt())
        assertTrue(isFloatingPodDarkSurface(darkPodWithAccentText, darkSurface, fallbackDark = false))
        assertEquals(0xFFF59E0B.toInt(), resolveFloatingPodTextArgb(darkPodWithAccentText, darkSurface, 0xFF1F2329.toInt(), fallbackDark = false))
    }
}

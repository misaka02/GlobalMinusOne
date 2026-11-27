package com.example.globalminusone.regression.v2418

import android.content.SharedPreferences
import android.view.WindowManager
import com.example.globalminusone.browser.BrowserDomStyles
import com.example.globalminusone.browser.BrowserNightModeEngine
import com.example.globalminusone.browser.ChromeViewportPolicy
import com.example.globalminusone.data.TriggerConfig
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.service.embedded.ApplicationWindowGeometryStore
import com.example.globalminusone.service.embedded.ApplicationWindowPlacement
import com.example.globalminusone.service.embedded.ApplicationWindowRect
import com.example.globalminusone.service.capture.CropBox
import com.example.globalminusone.service.capture.CropDragHandle
import com.example.globalminusone.service.capture.RegionCropGeometry
import com.example.globalminusone.service.embedded.SavedApplicationWindowState
import com.example.globalminusone.service.overlay.SidePullTriggerPolicy
import com.example.globalminusone.service.embedded.applicationWindowFocusFlags
import com.example.globalminusone.service.embedded.shouldOutsideTouchDefocusWindow
import com.example.globalminusone.ui.panel.IndependentBrowserSession
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.math.abs

class V2418RealDeviceFixesTest {

    // --- 1. Browser Night Mode (ColorOS 16 / Android 16 targetSdk=36 deterministic DOM + CSP probe) ---

    @Test
    fun browserNightModeScriptAlwaysInjectsViaDarkStylesheetAndNeverShortCircuitsOnNativeDark() {
        val enableScript = BrowserDomStyles.nightModeDomSyncScript(enabled = true)
        assertTrue(enableScript.contains("gmo-via-night-style"))
        assertTrue(enableScript.contains("color-scheme: dark !important"))
        assertTrue(enableScript.contains("background-color: #13161c !important"))
        assertTrue(enableScript.contains("MutationObserver"))
        assertTrue(enableScript.contains("parseBgLum"))
        assertTrue(enableScript.contains("css_applied"))
        assertTrue(enableScript.contains("csp_blocked"))
        // Must NEVER inject 'color-scheme: dark light' (which disabled Blink AutoDark) or skip CSS when matchMedia is dark
        assertFalse(enableScript.contains("color-scheme: dark light !important"))
        assertFalse(enableScript.contains("return 'blink_native_dark'"))

        // Single-pass GPU HUE_PRESERVING_DARK_MATRIX (with Blink AutoDark disabled on CSP fallback) turns white -> dark (#181A20)
        val (singleR, singleG, singleB) = BrowserNightModeEngine.transformRgb(255, 255, 255)
        assertTrue("Single-pass white must be dark (<36), got ($singleR,$singleG,$singleB)", singleR < 36 && singleG < 36 && singleB < 36)
        // Whereas double-inverting Blink AutoDark's #121212 (18,18,18) would turn it back into light gray (>200)
        val (doubleR, doubleG, doubleB) = BrowserNightModeEngine.transformRgb(18, 18, 18)
        assertTrue("Double-inverted dark becomes light (>200), proving mutual exclusion is required", doubleR > 200 && doubleG > 200 && doubleB > 200)

        val disableScript = BrowserDomStyles.nightModeDomSyncScript(enabled = false)
        assertTrue(disableScript.contains("gmo-via-night-style"))
        assertTrue(disableScript.contains("__gmoNightObs"))
        assertTrue(disableScript.contains("light_restored"))
    }

    // --- 2. Floating Window (`小窗`) Gesture Focus, Outside-Touch Defocus & Multi-Window Support ---

    @Test
    fun focusedAppWindowReceivesBackGesturesWithoutStealingRemoteImeWhileUnfocusedWindowYieldsAllGestures() {
        val base = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        // Focused remote-input window: FLAG_NOT_FOCUSABLE == 0 (receives Back/OnBackInvokedCallback),
        // FLAG_ALT_FOCUSABLE_IM == 1 (AOSP LayoutParams.mayUseInputMethod == false -> remote VirtualDisplay keeps IME!),
        // FLAG_WATCH_OUTSIDE_TOUCH == 1 (detects touches outside all floating windows).
        val focusedRemote = applicationWindowFocusFlags(base, hidden = false, remoteInput = true, windowFocused = true)
        assertEquals(0, focusedRemote and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        assertNotEquals(0, focusedRemote and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        assertNotEquals(0, focusedRemote and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        assertFalse(aospMayUseInputMethod(focusedRemote))

        // After user touches outside: windowFocused == false -> FLAG_NOT_FOCUSABLE == 1, ALT_FOCUSABLE_IM == 0,
        // so 100% of system Back/edge gestures operate on the outside foreground app!
        val unfocusedOutside = applicationWindowFocusFlags(focusedRemote, hidden = false, remoteInput = true, windowFocused = false)
        assertNotEquals(0, unfocusedOutside and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        assertEquals(0, unfocusedOutside and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        assertEquals(0, unfocusedOutside and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        assertFalse(aospMayUseInputMethod(unfocusedOutside))

        // Local host input (e.g. searching/connecting before remote display attaches): focusable AND may use IME
        val focusedLocal = applicationWindowFocusFlags(base, hidden = false, remoteInput = false, windowFocused = true)
        assertTrue(aospMayUseInputMethod(focusedLocal))

        // Minimized/hidden window: both NOT_FOCUSABLE and NOT_TOUCHABLE
        val hiddenFlags = applicationWindowFocusFlags(focusedRemote, hidden = true, remoteInput = true, windowFocused = true)
        assertNotEquals(0, hiddenFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        assertNotEquals(0, hiddenFlags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    /** Exact AOSP `WindowManager.LayoutParams.mayUseInputMethod(int flags)` formula without android.jar stub exception. */
    private fun aospMayUseInputMethod(flags: Int): Boolean {
        val mask = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        return when (flags and mask) {
            0, mask -> true
            else -> false
        }
    }

    @Test
    fun outsideTouchDefocusProtectsEdgeBackGesturesImeKeyboardAndSiblingFloatingWindows() {
        // Left & right system edge-swipe Back gesture initiation zones must NOT defocus active floating window!
        assertFalse(
            shouldOutsideTouchDefocusWindow(
                rawX = 18f,
                rawY = 900f,
                fullDisplayWidth = 1080,
                fullDisplayHeight = 2400,
                leftGestureEdgePx = 36,
                rightGestureEdgePx = 36,
                topSystemEdgePx = 48,
                bottomGestureEdgePx = 36,
                imeBottomPx = 0,
                insideAnotherVisibleWindow = false
            )
        )
        assertFalse(
            shouldOutsideTouchDefocusWindow(
                rawX = 1068f,
                rawY = 900f,
                fullDisplayWidth = 1080,
                fullDisplayHeight = 2400,
                leftGestureEdgePx = 36,
                rightGestureEdgePx = 36,
                topSystemEdgePx = 48,
                bottomGestureEdgePx = 36,
                imeBottomPx = 0,
                insideAnotherVisibleWindow = false
            )
        )

        // Touching soft keyboard at bottom (imeBottomPx = 860 -> y >= 1540) must NOT defocus active floating window!
        assertFalse(
            shouldOutsideTouchDefocusWindow(
                rawX = 540f,
                rawY = 1900f,
                fullDisplayWidth = 1080,
                fullDisplayHeight = 2400,
                leftGestureEdgePx = 36,
                rightGestureEdgePx = 36,
                topSystemEdgePx = 48,
                bottomGestureEdgePx = 36,
                imeBottomPx = 860,
                insideAnotherVisibleWindow = false
            )
        )

        // Touching sibling floating window B must NOT trigger global outside-defocus (window B claims focus itself)
        assertFalse(
            shouldOutsideTouchDefocusWindow(
                rawX = 700f,
                rawY = 1100f,
                fullDisplayWidth = 1080,
                fullDisplayHeight = 2400,
                leftGestureEdgePx = 36,
                rightGestureEdgePx = 36,
                topSystemEdgePx = 48,
                bottomGestureEdgePx = 36,
                imeBottomPx = 0,
                insideAnotherVisibleWindow = true
            )
        )

        // Touching genuine outside app area (e.g. top/center background outside all windows & gesture edges) MUST defocus!
        assertTrue(
            shouldOutsideTouchDefocusWindow(
                rawX = 540f,
                rawY = 160f,
                fullDisplayWidth = 1080,
                fullDisplayHeight = 2400,
                leftGestureEdgePx = 36,
                rightGestureEdgePx = 36,
                topSystemEdgePx = 48,
                bottomGestureEdgePx = 36,
                imeBottomPx = 0,
                insideAnotherVisibleWindow = false
            )
        )
    }

    // --- 3 & 4. Floating Window Compact Default, Smooth Diagonal Resize & Persistent Geometry ---

    @Test
    fun defaultFloatingWindowSizeIsCompactAndSmoothlyShrinkableOnHighDensityPhones() {
        val placement = ApplicationWindowPlacement(1080, 2400, 3f)
        // Readable new-window default; persisted user bounds still override it.
        assertEquals(756, placement.window.width)
        assertEquals(1440, placement.window.height)
        assertTrue(placement.window.width < (1080 * 0.80f).toInt())
        assertTrue(placement.window.height < (2400 * 0.70f).toInt())

        // High-density 360dp phone can shrink smoothly down to 42% width (454px) × 25% height (600px), not stuck at 720px!
        placement.resizeWindow(placement.window, -9999, -9999)
        assertEquals(454, placement.window.width)
        assertEquals(600, placement.window.height)
    }

    @Test
    fun lockedAspectRatioResizeIsStrictlyContinuousWithZeroSuddenSizeJumpsAcrossDiagonalBoundary() {
        val placement = ApplicationWindowPlacement(1080, 2400, 3f)
        placement.lockContentAspectRatio(canvasWidth = 1080, canvasHeight = 1920, chromeW = 6, chromeH = 114)
        val start = placement.window

        // Initial area-preserving snap must keep area close to compact default without jumping to full screen
        assertTrue(start.height in 900..1550)

        // Sweep across the old discontinuity line |dx| == |dy| with dx fixed at -80 and dy varying from -120 to -40 by 1px:
        var prevW = -1
        var prevH = -1
        for (dy in -120..-40) {
            placement.resizeWindow(start, dx = -80, dy = dy)
            val cur = placement.window
            // Top-left anchor must NEVER shift unexpectedly during bottom-right resize handle drag
            assertEquals(start.x, cur.x)
            assertEquals(start.y, cur.y)
            if (prevW >= 0) {
                assertTrue("Width jumped from $prevW to ${cur.width} at dy=$dy", abs(cur.width - prevW) <= 2)
                assertTrue("Height jumped from $prevH to ${cur.height} at dy=$dy", abs(cur.height - prevH) <= 2)
            }
            prevW = cur.width
            prevH = cur.height
        }
    }

    @Test
    fun geometryStorePersistsAndRestoresPerPackageAndGlobalLastWindowSizeAndPosition() {
        val prefs = inMemorySharedPreferences()
        val writerStore = ApplicationWindowGeometryStore(prefs)

        writerStore.save(
            packageName = "com.tencent.mm",
            state = SavedApplicationWindowState.fromRect(
                rect = ApplicationWindowRect(x = 140, y = 260, width = 620, height = 1080),
                screenWidth = 1080,
                screenHeight = 2400,
                userUnlockedAspectRatio = false
            )
        )

        // Fresh reader instance verifies genuine SharedPreferences round-trip serialization
        val readerStore = ApplicationWindowGeometryStore(prefs)
        val loadedPkg = readerStore.load("com.tencent.mm")
        assertNotNull(loadedPkg)
        val restoredRect = loadedPkg!!.toRect(1080, 2400)
        assertEquals(140, restoredRect.x)
        assertEquals(260, restoredRect.y)
        assertEquals(620, restoredRect.width)
        assertEquals(1080, restoredRect.height)
        assertFalse(loadedPkg.userUnlockedAspectRatio)

        // Newly opened package falls back to user's last preferred floating window size & position
        val fallbackForNewApp = readerStore.load("com.coolapk.market")
        assertNotNull(fallbackForNewApp)
        assertEquals(620, fallbackForNewApp!!.toRect(1080, 2400).width)

        // Multi-window cascade offsets overlapping second window cleanly
        val second = ApplicationWindowPlacement(1080, 2400, 3f)
        second.restoreSavedWindow(restoredRect)
        second.cascadeIfOverlapping(listOf(restoredRect), stepPx = 72)
        assertNotEquals(restoredRect.x, second.window.x)
        assertNotEquals(restoredRect.y, second.window.y)
    }

    // --- 5. Interactive Region Screenshot (`区域截屏`) Crop Math & Handle Hit-Testing ---

    @Test
    fun regionCropGeometrySupportsAllEightHandlesMoveNewDragAndPixelAccurateBitmapMapping() {
        val initial = RegionCropGeometry.defaultInitialSelection(1080f, 2400f)
        assertTrue(initial.width in 600f..800f)
        assertTrue(initial.height in 900f..1300f)

        // Hit-test corners, edges, inside, and outside
        assertEquals(CropDragHandle.TOP_LEFT, RegionCropGeometry.hitTestHandle(initial, initial.left + 4f, initial.top - 4f, 36f))
        assertEquals(CropDragHandle.BOTTOM_RIGHT, RegionCropGeometry.hitTestHandle(initial, initial.right - 3f, initial.bottom + 5f, 36f))
        assertEquals(CropDragHandle.RIGHT, RegionCropGeometry.hitTestHandle(initial, initial.right + 2f, initial.centerY, 36f))
        assertEquals(CropDragHandle.INSIDE_MOVE, RegionCropGeometry.hitTestHandle(initial, initial.centerX, initial.centerY, 36f))
        assertEquals(CropDragHandle.NEW_RECT, RegionCropGeometry.hitTestHandle(initial, 30f, 40f, 36f))

        // Dragging bottom-right corner resizes selection while clamping minimum size
        val shrunk = RegionCropGeometry.applyDrag(
            startRect = CropBox(200f, 300f, 800f, 1100f),
            handle = CropDragHandle.BOTTOM_RIGHT,
            downX = 800f,
            downY = 1100f,
            currentX = 500f,
            currentY = 760f,
            viewWidth = 1080f,
            viewHeight = 2400f,
            minSizePx = 96f
        )
        assertEquals(200f, shrunk.left, 0.01f)
        assertEquals(300f, shrunk.top, 0.01f)
        assertEquals(500f, shrunk.right, 0.01f)
        assertEquals(760f, shrunk.bottom, 0.01f)

        // Pixel mapping to captured screen bitmap is exact and strictly bounded
        val pixelRect = RegionCropGeometry.viewRectToBitmapRect(shrunk, 1080, 2400, 1080, 2400)
        assertEquals(200, pixelRect.left)
        assertEquals(300, pixelRect.top)
        assertEquals(300, pixelRect.width)
        assertEquals(460, pixelRect.height)

        // Toolbar positions below selection normally, and flips above when selection reaches bottom edge
        val (_, belowY) = RegionCropGeometry.computeToolbarPosition(shrunk, 520, 110, 1080, 2400, 24)
        assertTrue(belowY > shrunk.bottom)
        val nearBottom = CropBox(100f, 1500f, 900f, 2360f)
        val (_, aboveY) = RegionCropGeometry.computeToolbarPosition(nearBottom, 520, 110, 1080, 2400, 24)
        assertTrue(aboveY < nearBottom.top)
    }

    // --- 6. Built-in Browser Window Geometry Persistence Across App / Service Restarts ---

    @Test
    fun builtInBrowserSessionPersistsAndRestoresWindowSizeAndPositionAcrossRestarts() {
        val prefs = inMemorySharedPreferences()
        val firstSession = IndependentBrowserSession(context = null, sharedPrefsOverride = prefs)
        assertEquals(IndependentBrowserSession.DEFAULT_WINDOW_WIDTH_FRACTION, firstSession.windowWidthFraction, 0.001f)
        assertEquals(IndependentBrowserSession.DEFAULT_WINDOW_HEIGHT_FRACTION, firstSession.windowHeightFraction, 0.001f)

        // User resizes and drags the built-in browser window
        firstSession.updateWindowGeometry(
            widthFraction = 0.76f,
            heightFraction = 0.61f,
            xFraction = 0.14f,
            yFraction = 0.22f
        )

        // Simulate full app / OverlayService restart creating a brand-new IndependentBrowserSession
        val restartedSession = IndependentBrowserSession(context = null, sharedPrefsOverride = prefs)
        assertEquals(0.76f, restartedSession.windowWidthFraction, 0.0001f)
        assertEquals(0.61f, restartedSession.windowHeightFraction, 0.0001f)
        assertEquals(0.14f, restartedSession.windowXFraction, 0.0001f)
        assertEquals(0.22f, restartedSession.windowYFraction, 0.0001f)
    }

    // --- 7. Side-Pulldown Trigger Bar Left/Right Switching & Horizontal Offset Math ---

    @Test
    fun sidePulldownTriggerSupportsDeterministicLeftRightSwitchingAndCustomHorizontalOffset() {
        val base = TriggerConfig()
        val rightCfg = base.withPosition(TriggerPosition.RIGHT)
        assertEquals(TriggerPosition.RIGHT, rightCfg.triggerPosition)
        assertEquals(TriggerSide.RIGHT, rightCfg.side)

        val bothCfg = rightCfg.withPosition(TriggerPosition.BOTH)
        assertEquals(TriggerPosition.BOTH, bothCfg.triggerPosition)
        assertEquals(TriggerSide.BOTH, bothCfg.side)

        // Auto gesture-avoidance inset (-1) places LEFT near left edge and RIGHT deterministically near right edge
        val leftAutoX = SidePullTriggerPolicy.resolveTriggerBarLeftX(
            position = TriggerPosition.LEFT,
            screenWidth = 1080,
            barWidthPx = 32,
            sidePull = true,
            leftInsetPx = 72,
            rightInsetPx = 72,
            customOffsetDp = -1,
            density = 3f
        )
        val rightAutoX = SidePullTriggerPolicy.resolveTriggerBarLeftX(
            position = TriggerPosition.RIGHT,
            screenWidth = 1080,
            barWidthPx = 32,
            sidePull = true,
            leftInsetPx = 72,
            rightInsetPx = 72,
            customOffsetDp = -1,
            density = 3f
        )
        assertEquals(96, leftAutoX) // 72 + 8*3 = 96px from left edge
        assertEquals(1080 - 32 - 96, rightAutoX) // 952px in Gravity.LEFT physical coordinates

        // Custom horizontal offset (0dp = flush against edge, 40dp = 120px inward)
        val rightFlushX = SidePullTriggerPolicy.resolveTriggerBarLeftX(
            position = TriggerPosition.RIGHT,
            screenWidth = 1080,
            barWidthPx = 32,
            sidePull = true,
            leftInsetPx = 72,
            rightInsetPx = 72,
            customOffsetDp = 0,
            density = 3f
        )
        assertEquals(1080 - 32, rightFlushX)

        val leftCustomX = SidePullTriggerPolicy.resolveTriggerBarLeftX(
            position = TriggerPosition.LEFT,
            screenWidth = 1080,
            barWidthPx = 32,
            sidePull = true,
            leftInsetPx = 72,
            rightInsetPx = 72,
            customOffsetDp = 40,
            density = 3f
        )
        assertEquals(120, leftCustomX)
    }

    // --- 8. App Window Viewport Density Prevents sw360dp App Toolbar/Button Overflow ---

    @Test
    fun appWindowViewportDensityGuaranteesAtLeast376DpLogicalWidthOnColorOs480DpiScreens() {
        // 62% wide floating window inner canvas on 1080x2400 @ 480dpi ColorOS phone (~652 x 1100 px)
        val dpi = ChromeViewportPolicy.appWindowDensityDpi(widthPx = 652, heightPx = 1100, hostDensityDpi = 480)
        val logicalWidthDp = 652f * 160f / dpi
        assertTrue("Expected >= 376dp logical width so sw360dp app toolbars never overflow, got $logicalWidthDp", logicalWidthDp >= 376f)
    }

    private fun inMemorySharedPreferences(): SharedPreferences {
        val map = HashMap<String, Any?>()
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putString", "putInt", "putLong", "putFloat", "putBoolean" -> {
                    map[args[0] as String] = args[1]
                    proxy
                }
                "remove" -> {
                    map.remove(args[0] as String)
                    proxy
                }
                "clear" -> {
                    map.clear()
                    proxy
                }
                "commit" -> true
                "apply" -> null
                else -> proxy
            }
        } as SharedPreferences.Editor

        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "edit" -> editor
                "contains" -> map.containsKey(args[0] as String)
                "getString" -> (map[args[0] as String] as? String) ?: (args[1] as? String)
                "getFloat" -> (map[args[0] as String] as? Float) ?: (args[1] as Float)
                "getBoolean" -> (map[args[0] as String] as? Boolean) ?: (args[1] as Boolean)
                "getInt" -> (map[args[0] as String] as? Int) ?: (args[1] as Int)
                "getLong" -> (map[args[0] as String] as? Long) ?: (args[1] as Long)
                "getAll" -> HashMap(map)
                else -> null
            }
        } as SharedPreferences
    }
}

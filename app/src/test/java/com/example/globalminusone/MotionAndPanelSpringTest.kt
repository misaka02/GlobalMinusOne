package com.example.globalminusone

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.FloatingPodStyleSpec
import com.example.globalminusone.data.LauncherCardPresentationMode
import com.example.globalminusone.data.POPOUT_CAPABLE_CARD_TYPES
import com.example.globalminusone.data.cardImageCacheKey
import com.example.globalminusone.data.floatingPodSizeBoundsFor
import com.example.globalminusone.data.isSingleIconLauncherItem
import com.example.globalminusone.data.parseFloatingPodStyle
import com.example.globalminusone.data.withCardPresentationMode
import com.example.globalminusone.data.withFloatingPodStyle
import com.example.globalminusone.service.OverlayVisibilityPolicy
import com.example.globalminusone.service.TriggerGesturePolicy
import com.example.globalminusone.service.TriggerSwipeDirection
import com.example.globalminusone.service.resolveWidgetTouchScrollAction
import com.example.globalminusone.ui.motion.MotionTokens
import com.example.globalminusone.ui.motion.SpringPhysics1D
import com.example.globalminusone.ui.motion.panelSpringAngularFrequency
import com.example.globalminusone.ui.motion.panelSpringMaxSettleSeconds
import com.example.globalminusone.ui.motion.settingsScreenDepth
import com.example.globalminusone.ui.panel.ContainerPresentation
import com.example.globalminusone.ui.panel.PanelFlingCalibration
import com.example.globalminusone.ui.panel.PanelOverlayPortalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionAndPanelSpringTest {
    @Test
    fun springPhysics_convergesWithoutOvershootAndCarriesInitialVelocity() {
        val omega = panelSpringAngularFrequency(260, closing = false)
        assertTrue(omega > 0f)
        assertEquals(0f, panelSpringAngularFrequency(0, closing = false), 0f)

        val spring = SpringPhysics1D(omega)
        val noVel = spring.sample(startPosition = 0.2f, targetPosition = 1f, initialVelocity = 0f, elapsedSeconds = 0.08f)
        val withVel = spring.sample(startPosition = 0.2f, targetPosition = 1f, initialVelocity = 3.5f, elapsedSeconds = 0.08f)
        assertTrue(withVel.position > noVel.position)
        assertTrue(withVel.position <= 1.0005f)

        val settled = spring.sample(startPosition = 0.2f, targetPosition = 1f, initialVelocity = 3.5f, elapsedSeconds = 0.8f)
        assertTrue(settled.settled)
        assertEquals(1f, settled.position, 0f)
        assertEquals(0f, settled.velocity, 0f)

        val reversed = spring.sample(startPosition = 0.6f, targetPosition = 0f, initialVelocity = 2.5f, elapsedSeconds = 0.9f)
        assertTrue(reversed.settled)
        assertEquals(0f, reversed.position, 0f)
        assertTrue(spring.sample(0.6f, 0f, 2.5f, 0.2f).position >= -0.0005f)
    }

    @Test
    fun motionTokens_returnSnapForZeroAndValidSpringsOtherwise() {
        assertTrue(MotionTokens.pagerSnapSpring(0) is SnapSpec<*>)
        assertTrue(MotionTokens.cardSettleSpring(0) is SnapSpec<*>)
        assertTrue(MotionTokens.cardOffsetSpring(0) is SnapSpec<*>)
        assertTrue(MotionTokens.overlayEnterSpring(0) is SnapSpec<*>)
        assertTrue(MotionTokens.overlayExitSpring(0) is SnapSpec<*>)
        assertTrue(MotionTokens.chromeFadeSpec(0) is SnapSpec<*>)

        val specs = listOf(
            MotionTokens.pagerSnapSpring(260),
            MotionTokens.cardSettleSpring(260),
            MotionTokens.cardOffsetSpring(260),
            MotionTokens.overlayEnterSpring(260),
            MotionTokens.overlayExitSpring(260),
            MotionTokens.chromeFadeSpec(260)
        )
        for (spec in specs) {
            assertTrue(spec is SpringSpec<*>)
            val springSpec = spec as SpringSpec<*>
            assertTrue(springSpec.stiffness > 0f)
            assertTrue(springSpec.dampingRatio in 0.8f..1.0f)
        }
    }

    @Test
    fun triggerGesturePolicy_zeroJumpFollowAndVelocityAwareSettle() {
        assertEquals(
            0f,
            TriggerGesturePolicy.followProgressFromOffset(
                TriggerSwipeDirection.RIGHT,
                dx = 30f,
                dy = 0f,
                startProjectedPx = 30f,
                panelWidth = 600f,
                screenHeight = 1000f
            ),
            0f
        )
        assertEquals(
            0.5f,
            TriggerGesturePolicy.followProgressFromOffset(
                TriggerSwipeDirection.RIGHT,
                dx = 330f,
                dy = 0f,
                startProjectedPx = 30f,
                panelWidth = 600f,
                screenHeight = 1000f
            ),
            0.0001f
        )
        assertEquals(
            2.0f,
            TriggerGesturePolicy.velocityToProgressPerSec(
                TriggerSwipeDirection.RIGHT,
                vxPxPerSec = 1200f,
                vyPxPerSec = 0f,
                panelWidth = 600f,
                screenHeight = 1000f
            ),
            0.0001f
        )

        // Forward flick opens even with small distance; backward flick closes even far open.
        assertTrue(TriggerGesturePolicy.shouldSettleOpen(0.08f, 1.5f, 20f, 48, 2f))
        assertFalse(TriggerGesturePolicy.shouldSettleOpen(0.8f, -1.5f, 480f, 48, 2f))
        // Slow drag obeys position or distance threshold.
        assertTrue(TriggerGesturePolicy.shouldSettleOpen(0.25f, 0.1f, 150f, 48, 2f))
        assertFalse(TriggerGesturePolicy.shouldSettleOpen(0.1f, 0.1f, 50f, 48, 2f))
    }

    @Test
    fun panelFlingCalibration_preservesFastTwoSampleFlickAnd50msPauseWhileZeroing60msStop() {
        val fast = PanelFlingCalibration { 20L }
        fast.begin(0L, 500f)
        fast.end(20L, 380f)
        assertEquals(6000f, fast.limit(8000f), 10f)

        val paused50ms = PanelFlingCalibration { 90L }
        paused50ms.begin(0L, 500f)
        paused50ms.sample(20L, 460f)
        paused50ms.sample(40L, 420f)
        paused50ms.end(90L, 420f)
        assertTrue(paused50ms.limit(6000f) > 500f)

        val stopped60ms = PanelFlingCalibration { 80L }
        stopped60ms.begin(0L, 500f)
        stopped60ms.sample(20L, 450f)
        stopped60ms.end(80L, 450f)
        assertEquals(0f, stopped60ms.limit(6000f), 0f)
    }

    @Test
    fun portalState_preservesStackOrderAndInPlaceUpdates() {
        val portal = PanelOverlayPortalState()
        assertFalse(portal.hasEntries)
        val firstKey = Any()
        val secondKey = Any()
        val firstContent: @androidx.compose.runtime.Composable () -> Unit = {}
        val updatedFirst: @androidx.compose.runtime.Composable () -> Unit = {}
        val secondContent: @androidx.compose.runtime.Composable () -> Unit = {}

        portal.upsert(firstKey, firstContent)
        portal.upsert(secondKey, secondContent)
        assertTrue(portal.hasEntries)
        assertEquals(listOf(firstKey, secondKey), portal.entries.map { it.key })

        portal.upsert(firstKey, updatedFirst)
        assertEquals(listOf(firstKey, secondKey), portal.entries.map { it.key })
        assertEquals(updatedFirst, portal.entries[0].content)

        portal.remove(firstKey)
        assertEquals(listOf(secondKey), portal.entries.map { it.key })
        portal.clear()
        assertFalse(portal.hasEntries)
    }

    @Test
    fun settingsScreenDepth_ordersMainSubpagesAndBallSettings() {
        assertEquals(0, settingsScreenDepth("MAIN"))
        for (sub in listOf("OPERATION", "THEME", "ADVANCE", "EXTEND", "SCREEN_ASSISTANT", "CONFIG", "ABOUT")) {
            assertEquals(1, settingsScreenDepth(sub))
        }
        assertEquals(2, settingsScreenDepth("SB_SETTINGS"))
        assertTrue(settingsScreenDepth("OPERATION") > settingsScreenDepth("MAIN"))
        assertTrue(settingsScreenDepth("SB_SETTINGS") > settingsScreenDepth("OPERATION"))
    }

    @Test
    fun containerPresentation_fractionsForScopesOnlyMatchingActiveCurtains() {
        val presentation = ContainerPresentation()
        presentation.curtain("c-a").expanded = true
        kotlinx.coroutines.runBlocking {
            presentation.curtain("c-a").progress.snapTo(0.65f)
            presentation.curtain("c-b").progress.snapTo(0f)
        }
        assertEquals(emptyMap<String, Float>(), presentation.fractionsFor(emptySet()))
        assertEquals(emptyMap<String, Float>(), presentation.fractionsFor(setOf("c-b", "c-missing")))
        assertEquals(mapOf("c-a" to 0.65f), presentation.fractionsFor(setOf("c-a", "c-b")))
    }

    @Test
    fun suspensionBallSnap_respectsThrowVelocityAndMidpointFallback() {
        // Rightward throw from left half snaps to right edge; leftward throw from right half snaps left.
        assertEquals(
            0.98f,
            OverlayVisibilityPolicy.snapSuspensionBallXPercentWithVelocity(0.25f, 1.4f, true),
            0.0001f
        )
        assertEquals(
            0.02f,
            OverlayVisibilityPolicy.snapSuspensionBallXPercentWithVelocity(0.75f, -1.4f, true),
            0.0001f
        )
        // Slow drag obeys midpoint fallback.
        assertEquals(
            0.02f,
            OverlayVisibilityPolicy.snapSuspensionBallXPercentWithVelocity(0.35f, 0.2f, true),
            0.0001f
        )
        assertEquals(
            0.98f,
            OverlayVisibilityPolicy.snapSuspensionBallXPercentWithVelocity(0.65f, -0.2f, true),
            0.0001f
        )
        // Edge-snap disabled preserves clamped position regardless of velocity.
        assertEquals(
            0.42f,
            OverlayVisibilityPolicy.snapSuspensionBallXPercentWithVelocity(0.42f, 3.0f, false),
            0.0001f
        )
        assertEquals(
            0.98f,
            OverlayVisibilityPolicy.snapSuspensionBallXPercentWithVelocity(1.2f, -3.0f, false),
            0.0001f
        )
    }

    @Test
    fun cardImageCacheKey_isolatesNormalizedUriEdgeAndRevision() {
        val base = cardImageCacheKey("  content://img/1  ", 1440, 0L)
        assertEquals(base, cardImageCacheKey("content://img/1", 1440, 0L))
        assertFalse(base == cardImageCacheKey("content://img/1", 1024, 0L))
        assertFalse(base == cardImageCacheKey("content://img/1", 1440, 1L))
        assertFalse(base == cardImageCacheKey("content://img/2", 1440, 0L))
    }

    @Test
    fun popOutCards_supportWidgetAndExtendedCardRoundTripAndStyleBounds() {
        val newlyEnabled = listOf(
            ComponentType.APP_WIDGET,
            ComponentType.STAT_BATTERY_DETAIL,
            ComponentType.SINGLE_IMAGE,
            ComponentType.PHOTO_CUBE,
            ComponentType.HITOKOTO_CARD
        )
        newlyEnabled.forEach { type ->
            assertTrue("$type should be pop-out capable", type in POPOUT_CAPABLE_CARD_TYPES)
            val bounds = floatingPodSizeBoundsFor(type)
            assertTrue(bounds.defaultWidthDp in bounds.minWidthDp..bounds.maxWidthDp)
            assertTrue(bounds.defaultHeightDp in bounds.minHeightDp..bounds.maxHeightDp)
            val base = ComponentItem(id = "card-$type", type = type, title = type.name)
            val popOut = withCardPresentationMode(base, LauncherCardPresentationMode.POPUP_ICON)
            assertTrue(isSingleIconLauncherItem(popOut))
            assertEquals(1, popOut.gridSpanX)
            assertEquals(1, popOut.gridSpanY)
            val styled = withFloatingPodStyle(
                popOut,
                FloatingPodStyleSpec(widthDp = bounds.maxWidthDp, heightDp = bounds.minHeightDp)
            )
            val restored = withCardPresentationMode(styled, LauncherCardPresentationMode.INLINE_CARD)
            assertFalse(isSingleIconLauncherItem(restored))
            assertTrue(restored.gridSpanX > 1 || restored.gridSpanY > 1)
            val parsedStyle = parseFloatingPodStyle(restored.intentUri)
            assertEquals(bounds.maxWidthDp, parsedStyle.widthDp)
            assertEquals(bounds.minHeightDp, parsedStyle.heightDp)
        }
        // WEBSITE_LINK stores its URL in intentUri and CONTAINER_BOX/FOLDER use container popups.
        assertFalse(ComponentType.WEBSITE_LINK in POPOUT_CAPABLE_CARD_TYPES)
        assertFalse(ComponentType.CONTAINER_BOX in POPOUT_CAPABLE_CARD_TYPES)
    }

    @Test
    fun widgetTouchScrollRouting_yieldsOnStaticOrBoundaryAndKeepsLiveVerticalList() {
        // Static widgets without a vertically scrollable child immediately yield to outer containers.
        assertEquals(false, resolveWidgetTouchScrollAction(false, 0f, -40f, 16f, true, true))

        // Within touchSlop, keep waiting (null) so list scroll/click isn't prematurely cancelled.
        assertEquals(null, resolveWidgetTouchScrollAction(true, 4f, -12f, 16f, true, true))

        // Horizontal drags always yield to outer HorizontalPager.
        assertEquals(false, resolveWidgetTouchScrollAction(true, 40f, -10f, 16f, true, true))

        // Vertical drag inside scrollable widget claims touch only when content can still scroll in that direction.
        assertEquals(true, resolveWidgetTouchScrollAction(true, 4f, -36f, 16f, canScrollUpContent = false, canScrollDownContent = true))
        assertEquals(false, resolveWidgetTouchScrollAction(true, 4f, -36f, 16f, canScrollUpContent = true, canScrollDownContent = false))
        assertEquals(true, resolveWidgetTouchScrollAction(true, 4f, 36f, 16f, canScrollUpContent = true, canScrollDownContent = false))
        assertEquals(false, resolveWidgetTouchScrollAction(true, 4f, 36f, 16f, canScrollUpContent = false, canScrollDownContent = true))
    }

    @Test
    fun springPhysics_snapsPromptlyAtLowAnimationDurations() {
        val omega50 = panelSpringAngularFrequency(50, closing = false)
        assertTrue("Expected crisp angular frequency at 50ms, got $omega50", omega50 > 100f)
        val spring50 = SpringPhysics1D(omega50)
        assertTrue(spring50.sample(0f, 1f, 0f, 0.05f).position >= 0.99f)
        assertTrue(spring50.sample(0f, 1f, 0f, 0.08f).settled)

        val omegaMin = panelSpringAngularFrequency(16, closing = false)
        assertTrue("Expected ultra-fast angular frequency at 16ms, got $omegaMin", omegaMin > 300f)
        val springMin = SpringPhysics1D(omegaMin)
        assertTrue(springMin.sample(0f, 1f, 0f, 0.016f).position >= 0.99f)
        assertTrue(springMin.sample(0f, 1f, 0f, 0.032f).settled)

        val cap50 = panelSpringMaxSettleSeconds(50, closing = false)
        assertTrue("Expected tight settle cap at 50ms, got $cap50", cap50 in 0.05f..0.08f)
        assertEquals(0f, panelSpringMaxSettleSeconds(0, closing = false), 0f)
    }
}

package com.example.globalminusone.ui.panel

import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.ui.panel.gesture.PanelGestureOwnership
import com.example.globalminusone.ui.panel.gesture.StationaryHoldPolicy
import com.example.globalminusone.ui.panel.gesture.canCommitLayout
import com.example.globalminusone.ui.panel.gesture.commitCardGeometry
import com.example.globalminusone.ui.panel.gesture.commitLinearPreview
import com.example.globalminusone.ui.panel.gesture.isSuccessfulDragRelease
import com.example.globalminusone.ui.panel.gesture.ownsVerticalScroll
import com.example.globalminusone.ui.panel.editor.parseExactInteger
import com.example.globalminusone.ui.panel.gesture.preserveViewportScroll
import com.example.globalminusone.ui.panel.gesture.reorderLinearDrag
import com.example.globalminusone.ui.panel.gesture.resizedLinearHeight
import com.example.globalminusone.ui.panel.gesture.restoreDefaultCardSize
import com.example.globalminusone.ui.panel.gesture.stableContentExtent

class PanelInteractionPolicyTest {
    @Test fun visibleCardBoundsExcludeBackgroundHoldsAndAreRemovedWhenTheCardLeaves() {
        val ownership = PanelGestureOwnership()
        val card = Any()
        ownership.recordCard(card, androidx.compose.ui.geometry.Rect(20f, 30f, 120f, 230f))
        assertTrue(ownership.hitsCard(androidx.compose.ui.geometry.Offset(50f, 100f)))
        assertFalse(ownership.hitsCard(androidx.compose.ui.geometry.Offset(10f, 100f)))
        ownership.removeCard(card)
        assertFalse(ownership.hitsCard(androidx.compose.ui.geometry.Offset(50f, 100f)))
    }
    @Test fun disabledDescendantNeverReservesAnEnabledParentsHold() {
        val ownership = PanelGestureOwnership()
        val disabledChild = Any(); val parent = Any()
        assertFalse(ownership.claimHold(100L, disabledChild, false))
        assertTrue(ownership.claimHold(100L, parent, true))
        ownership.releaseHold(parent)
        assertTrue(ownership.claimHold(200L, disabledChild, true))
        assertFalse(ownership.claimHold(200L, parent, true))
    }
    @Test fun linearHeightPreviewSnapsAndClampsWithoutChangingOtherGeometry() {
        val original = ComponentItem(type = ComponentType.NOTE_CARD, x = 5, y = 7, w = 20, h = 10)
        val height = resizedLinearHeight(original.h, 36f, 18f, 40)
        val changed = original.copy(h = height, autoHeight = false)
        assertEquals(12, height)
        assertEquals(original.copy(h = 12, autoHeight = false), changed)
        assertEquals(1, resizedLinearHeight(10, -10000f, 18f, 40))
        assertEquals(40, resizedLinearHeight(10, 10000f, 18f, 40))
        assertEquals(10, original.h)
    }

    @Test fun releasingLastCardCannotClampScrollAwayFromTheDisplayedDrop() {
        assertEquals(1100f, stableContentExtent(1064f, 400, 700f), .01f)
        assertEquals(1064f, stableContentExtent(1064f, 200, 700f), .01f)
        assertEquals(700f, stableContentExtent(100f, 0, 700f), .01f)
    }

    @Test fun onlyScrollableDominantVerticalMotionOwnsTheOuterPage() {
        assertFalse(ownsVerticalScroll(80f, 5f, 8f, true))
        assertFalse(ownsVerticalScroll(2f, 80f, 8f, false))
        assertTrue(ownsVerticalScroll(2f, -80f, 8f, true))
        assertFalse(ownsVerticalScroll(2f, 3f, 8f, true))
    }
    @Test fun exactIntegerInputRejectsBlankFractionsOverflowAndOutOfRange() {
        assertEquals(37, parseExactInteger("37", 1, 64))
        for (bad in listOf("", "-1", "1.2", "65", "2147483648", "x")) {
            assertNull(parseExactInteger(bad, 1, 64))
        }
    }
    @Test fun previewCommitKeepsAllLiveObjectsAndRejectsAStaleTree() {
        val first = ComponentItem(id = "first", type = ComponentType.NOTE_CARD, h = 19, x = 5)
        val second = ComponentItem(id = "second", type = ComponentType.APP_DRAWER_CARD, h = 44, y = 23)
        val page = PageConfig(name = "Preview", layoutType = PageLayoutType.LINEAR, components = listOf(first, second))
        val preview = reorderLinearDrag(page, first.id, 250f, 300f)
        val committed = commitLinearPreview(page, preview.components.map { it.id })
        assertEquals(preview, committed)
        assertSame(second, committed.components[0]); assertSame(first, committed.components[1])
        assertEquals(page, commitLinearPreview(page, listOf("first", "deleted")))
        assertEquals(page, commitLinearPreview(page, null)) // cancelled preview
    }

    @Test fun consumedCancelReleaseNeverCommitsAnActiveDrag() {
        assertTrue(isSuccessfulDragRelease(true, false, false))
        assertFalse(isSuccessfulDragRelease(true, false, true))
        assertFalse(isSuccessfulDragRelease(true, true, false))
        assertFalse(isSuccessfulDragRelease(false, false, false))
    }
    @Test fun toolbarViewportChangePreservesScrolledScreenPositionWithoutMovingTop() {
        assertEquals(700, preserveViewportScroll(350, 1000, 650))
        assertEquals(350, preserveViewportScroll(700, 650, 1000))
        assertEquals(0, preserveViewportScroll(0, 1000, 650))
        assertEquals(0, preserveViewportScroll(100, 650, 1000))
    }
    @Test fun outAndBackMotionCannotBecomeAStationaryHold() {
        val hold = StationaryHoldPolicy(1f, 2f)
        repeat(4) { hold.observe(.6f, 0f, 1, true, false); hold.observe(-.6f, 0f, 1, true, false) }
        assertTrue(hold.cancelled)
        assertFalse(hold.observe(0f, 0f, 1, true, false))
    }
    @Test fun driftSecondPointerReleaseAndConsumedScrollCancelPermanently() {
        val samples = listOf(
            { p: StationaryHoldPolicy -> p.observe(1.01f, 0f, 1, true, false) },
            { p: StationaryHoldPolicy -> p.observe(0f, 0f, 2, true, false) },
            { p: StationaryHoldPolicy -> p.observe(0f, 0f, 1, false, false) },
            { p: StationaryHoldPolicy -> p.observe(.1f, 0f, 1, true, true) }
        )
        samples.forEach { sample -> val hold = StationaryHoldPolicy(1f, 2f); assertFalse(sample(hold)); assertTrue(hold.cancelled) }
    }
    @Test fun smallSensorJitterAndTrueStillnessRemainEligible() {
        val hold = StationaryHoldPolicy(1f, 2f)
        assertTrue(hold.observe(.1f, .1f, 1, true, false))
        assertTrue(hold.observe(-.1f, -.1f, 1, true, false))
        assertTrue(hold.observe(0f, 0f, 1, true, false))
    }
    @Test fun linearReorderingPreservesAllObjectsGeometryAndNestedTrees() {
        val note = ComponentItem(id="note", type=ComponentType.NOTE_CARD, x=3, y=7, w=18, h=12)
        val drawer = ComponentItem(id="drawer", type=ComponentType.APP_DRAWER_CARD, x=1, y=25, h=44)
        val container = ComponentItem(id="container", type=ComponentType.CONTAINER_BOX, containerComponents=listOf(note.copy(id="child")))
        val page = PageConfig(name="Test", layoutType=PageLayoutType.LINEAR, components=listOf(note, drawer, container))
        val moved = reorderLinearDrag(page, "note", 500f, 200f)
        assertEquals(PageLayoutType.LINEAR, moved.layoutType)
        assertEquals(listOf("drawer","container","note"), moved.components.map { it.id })
        page.components.forEach { before -> assertSame(before, moved.components.first { it.id == before.id }) }
        assertSame(page, reorderLinearDrag(page,"missing",100f,20f))
    }
    @Test fun draggingEstimatedAutoHeightNeverWritesEstimatedDimensions() {
        val child = ComponentItem(id="child", type=ComponentType.NOTE_CARD)
        val item = ComponentItem(type=ComponentType.CONTAINER_BOX,h=12,containerComponents=listOf(child))
        val moved = commitCardGeometry(item,2,3,28,90,false,false)
        assertEquals(item.copy(x=2,y=3),moved)
        val wider = commitCardGeometry(item,0,0,28,90,true,false)
        assertEquals(12,wider.h); assertTrue(wider.autoHeight)
        val taller = commitCardGeometry(item,0,0,32,20,false,true)
        assertEquals(20,taller.h); assertFalse(taller.autoHeight)
        assertSame(child,taller.containerComponents.single())
    }
    @Test fun nestedGestureOwnersKeepOuterScrollingLockedUntilEveryOwnerReleases() {
        val ownership = PanelGestureOwnership()
        val parent = Any(); val child = Any()
        assertFalse(ownership.active)
        ownership.acquire(parent); ownership.acquire(child)
        assertTrue(ownership.active)
        ownership.release(child)
        assertTrue(ownership.active)
        ownership.release(parent)
        assertFalse(ownership.active)
        ownership.release(child)
        assertFalse(ownership.active)
    }
    @Test fun pendingGestureCannotCommitAfterLeavingEditOrLocking() {
        assertTrue(canCommitLayout(true, false))
        assertFalse(canCommitLayout(false, false))
        assertFalse(canCommitLayout(true, true))
        assertFalse(canCommitLayout(false, true))
    }
    @Test fun explicitDefaultSizeRecoveryPreservesContentLocationAndChildren() {
        val child=ComponentItem(type=ComponentType.TEXT_LABEL)
        val broken=ComponentItem(type=ComponentType.CONTAINER_BOX,x=4,y=9,w=20,h=96,title="Keep",textContent="Unchanged",intentUri="keep://launch",iconUri="content://icons/keep",
            cornerRadiusDp=-1,bgAlphaPercent=-1,textAlphaPercent=-1,containerComponents=listOf(child))
        val recovered=restoreDefaultCardSize(broken,32)
        assertEquals(28,recovered.w);assertEquals(broken.type.defaultH,recovered.h)
        assertEquals(broken.copy(w=28,h=broken.type.defaultH,autoHeight=false),recovered)
        assertSame(child,recovered.containerComponents.single())
    }
}

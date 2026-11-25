package com.example.globalminusone

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import com.example.globalminusone.ui.panel.LoopPagerDecision
import com.example.globalminusone.ui.panel.LoopPagerNavigation
import com.example.globalminusone.ui.panel.LoopPagerPolicy
import com.example.globalminusone.ui.panel.LoopPagerReconciler
import com.example.globalminusone.ui.panel.LoopPagerSnapshot
import com.example.globalminusone.ui.panel.resolvePagerSnapAnimationSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopPagerPolicyTest {
    private val policy = LoopPagerPolicy(listOf("a", "b", "c"))

    @Test fun bothBoundariesAreAdjacentNativePagerSlots() {
        val first = policy.initialVirtualPage(0)
        assertEquals(2, policy.realIndex(first - 1))
        assertEquals(0, policy.realIndex(first + 3))
        assertEquals(first - 1, policy.targetVirtualPage(first, 2, -1))
        assertEquals(first + 3, policy.targetVirtualPage(first + 2, 0, 1))
        assertTrue(first > 100_000 && first < policy.virtualPageCount - 100_000)
        var virtual = first
        repeat(2_000) {
            val next = (policy.realIndex(virtual) + 1) % policy.realPageCount
            val target = policy.targetVirtualPage(virtual, next, 1)
            assertEquals(virtual + 1, target)
            virtual = target
        }
    }

    @Test fun twoPageButtonsRetainRequestedDirectionAndDirectNavigationUsesNearestSlot() {
        val two = LoopPagerPolicy(listOf("a", "b"))
        val first = two.initialVirtualPage(0)
        assertEquals(first + 1, two.targetVirtualPage(first, 1, 1))
        assertEquals(first - 1, two.targetVirtualPage(first, 1, -1))
        val base = policy.initialVirtualPage(0)
        assertEquals(base - 1, policy.targetVirtualPage(base, 2))
        assertEquals(base + 1, policy.targetVirtualPage(base, 1))
    }

    @Test fun singleAndTemporarilyEmptyListsHaveNoDuplicateSlotsOrNavigation() {
        for (ids in listOf(emptyList(), listOf("only"))) {
            val single = LoopPagerPolicy(ids)
            assertEquals(1, single.virtualPageCount)
            assertEquals(0, single.initialVirtualPage(99))
            assertEquals(0, single.targetVirtualPage(0, 1, 1))
            assertNull(LoopPagerReconciler(single, 0).reconcile(
                LoopPagerSnapshot(0, 0, 0, false, LoopPagerNavigation(1, 0, 1))))
        }
    }

    @Test fun virtualKeysAreUniqueButPreservePageIdentityAcrossReorder() {
        val base = policy.initialVirtualPage(0)
        assertNotEquals(policy.key(base), policy.key(base + 3))
        val swapped = LoopPagerPolicy(listOf("b", "a", "c"))
        assertEquals(policy.key(base), swapped.key(base + 1))
        // Each rebuilt policy anchors the repository's real index, after add/delete/swap.
        for (ids in listOf(listOf("b", "a", "c"), listOf("b", "a"), listOf("x", "b", "a"))) {
            val updated = LoopPagerPolicy(ids)
            val indexOfA = ids.indexOf("a")
            assertEquals("a", ids[updated.realIndex(updated.initialVirtualPage(indexOfA))])
        }
    }

    @Test fun physicalEndsRemainInBoundsEvenAfterExtremeNavigation() {
        assertEquals(2, policy.targetVirtualPage(0, 2, -1))
        val end = policy.virtualPageCount - 1
        val nextReal = (policy.realIndex(end) + 1) % policy.realPageCount
        val target = policy.targetVirtualPage(end, nextReal, 1)
        assertTrue(target in 0 until policy.virtualPageCount)
        assertEquals(nextReal, policy.realIndex(target))
    }

    @Test fun repositoryRequestWinsOverOldSettledPageAndDoesNotEcho() {
        val start = policy.initialVirtualPage(0)
        val sync = LoopPagerReconciler(policy, 0)
        assertEquals(LoopPagerDecision.Navigate(start + 1), sync.reconcile(
            LoopPagerSnapshot(1, start, start, false, null)))
        assertNull(sync.reconcile(LoopPagerSnapshot(1, start + 1, start + 1, false, null)))
        // A second external request arriving during animation must survive its old settle.
        assertEquals(LoopPagerDecision.Navigate(start + 2), sync.reconcile(
            LoopPagerSnapshot(2, start + 1, start + 1, false, null)))
    }

    @Test fun swipePersistsOnlyAfterSettleAndPersistsOnlyRealIndex() {
        val first = policy.initialVirtualPage(0)
        val sync = LoopPagerReconciler(policy, 0)
        assertNull(sync.reconcile(LoopPagerSnapshot(0, first - 1, first, true, null)))
        assertEquals(LoopPagerDecision.Persist(2), sync.reconcile(
            LoopPagerSnapshot(0, first - 1, first - 1, false, null)))
        assertNull(sync.reconcile(LoopPagerSnapshot(2, first - 1, first - 1, false, null)))
    }

    @Test fun interruptedProgramAnimationYieldsToUserAndNewRequestsAreNotLost() {
        val first = policy.initialVirtualPage(0)
        val sync = LoopPagerReconciler(policy, 0)
        val previous = LoopPagerNavigation(1, 2, -1)
        assertEquals(LoopPagerDecision.Navigate(first - 1), sync.reconcile(
            LoopPagerSnapshot(2, first, first, false, previous)))
        assertNull(sync.reconcile(LoopPagerSnapshot(2, first + 1, first, true, previous)))
        assertEquals(LoopPagerDecision.Persist(1), sync.reconcile(
            LoopPagerSnapshot(2, first + 1, first + 1, false, previous)))
        val next = LoopPagerNavigation(2, 2, 1)
        assertEquals(LoopPagerDecision.Navigate(first + 2), sync.reconcile(
            LoopPagerSnapshot(2, first + 1, first + 1, false, next)))
    }

    @Test fun obsoleteButtonDirectionCannotOverrideNewExternalRequest() {
        val first = policy.initialVirtualPage(0)
        val sync = LoopPagerReconciler(policy, 0)
        assertEquals(LoopPagerDecision.Navigate(first + 1), sync.reconcile(
            LoopPagerSnapshot(1, first, first, false, LoopPagerNavigation(1, 2, -1))))
    }

    @Test fun pagerSnapAnimationSpecUsesSnapForZeroAndVelocitySpringOtherwise() {
        assertTrue(resolvePagerSnapAnimationSpec(0) is SnapSpec<*>)
        val fast = resolvePagerSnapAnimationSpec(120) as SpringSpec<*>
        val normal = resolvePagerSnapAnimationSpec(240) as SpringSpec<*>
        val slow = resolvePagerSnapAnimationSpec(600) as SpringSpec<*>
        for (spec in listOf(fast, normal, slow)) {
            assertEquals(Spring.DampingRatioNoBouncy, spec.dampingRatio, 0.0001f)
            assertTrue(spec.stiffness in 140f..3200f)
        }
        assertTrue(fast.stiffness > normal.stiffness)
        assertTrue(normal.stiffness > slow.stiffness)
    }
}

package com.example.globalminusone

import com.example.globalminusone.ui.panel.*
import org.junit.Assert.*
import org.junit.Test

class AppIndexPolicyTest {
    @Test fun labelsNormalizeAccentsAndKeepNonLettersInHash() {
        assertEquals("E", appIndexLetter(" Éclair"))
        assertEquals("#", appIndexLetter("123工具"))
        assertEquals("#", appIndexLetter("★收藏"))
        assertEquals("#", appIndexLetter(""))
        assertEquals("W", appIndexLetter("微信") { if (it == "微信") "wei xin" else it })
    }
    @Test fun filteredGroupsHaveOnlyAvailableLettersAndStableHeadingOffsets() {
        val groups = groupAppsByLetter(listOf("Zulu", "123", "Alpha", "Atom"), { it }, { it })
        assertEquals(listOf("A", "Z", "#"), groups.map { it.letter })
        assertEquals(listOf(0, 3, 5), appGroupOffsets(groups.map { it.apps.size }))
        val filtered = groupAppsByLetter(listOf("Zulu"), { it }, { it })
        assertEquals(listOf("Z"), filtered.map { it.letter })
    }
    @Test fun dragIndexClampsEdgesAndRejectsEmptyOrUnmeasuredBars() {
        assertEquals(0, appIndexAtPosition(-4f, 100f, 3))
        assertEquals(1, appIndexAtPosition(50f, 100f, 3))
        assertEquals(2, appIndexAtPosition(100f, 100f, 3))
        assertNull(appIndexAtPosition(0f, 0f, 3))
        assertNull(appIndexAtPosition(0f, 100f, 0))
    }
    @Test fun searchFiltersPreparedGroupsWithoutReclassifyingOrChangingOrder() {
        var classifications = 0
        val prepared = groupAppsByLetter(listOf("Alpha", "Atom", "Beta"), { it }, { classifications++; it })
        val calls = classifications
        val result = filterAppGroups(prepared) { it.contains("tom") }
        assertEquals(calls, classifications)
        assertEquals(listOf("A"), result.map { it.letter })
        assertEquals(listOf("Atom"), result.single().apps)
        assertEquals(listOf("A", "B"), prepared.map { it.letter })
    }

    @Test fun visibleGroupSkipsClippedPredecessorAndHandlesOversizedOrEmptyViewport() {
        // T has only its blank bottom edge left; V is the first complete visible heading.
        val offsets = listOf(0, 3, 6)
        assertEquals(1, visibleAppGroupIndex(offsets, listOf(
            AppViewportItem(2, -90, 100), AppViewportItem(3, 18, 30), AppViewportItem(4, 48, 90)
        ), 0, 140, 2))
        // A tall row may cover the entire viewport: retain its group instead of inventing one.
        assertEquals(1, visibleAppGroupIndex(offsets, listOf(AppViewportItem(4, -40, 250)), 0, 100, 6))
        assertEquals(0, visibleAppGroupIndex(offsets, emptyList(), 0, 0, -1))
        assertEquals(2, visibleAppGroupIndex(offsets, emptyList(), 0, 100, 99))
        assertNull(visibleAppGroupIndex(emptyList(), emptyList(), 0, 100, 0))
    }

    @Test fun liveViewportAdapterKeepsFullyVisibleAndFallbackGroupRules() {
        val offsets = listOf(0, 3, 6)
        val viewport = listOf(Triple(2, -90, 100), Triple(3, 18, 30), Triple(4, 48, 90))
        assertEquals(1, visibleAppGroupIndex(offsets, viewport, 0, 140, 2,
            { it.first }, { it.second }, { it.third }))
        assertEquals(1, visibleAppGroupIndex(offsets, listOf(Triple(4, -40, 250)), 0, 100, 6,
            { it.first }, { it.second }, { it.third }))
        assertEquals(2, visibleAppGroupIndex(offsets, emptyList<Triple<Int, Int, Int>>(), 0, 100, 99,
            { it.first }, { it.second }, { it.third }))
    }

}

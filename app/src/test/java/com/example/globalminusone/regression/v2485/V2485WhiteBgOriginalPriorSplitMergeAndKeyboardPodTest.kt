package com.example.globalminusone.regression.v2485

import com.example.globalminusone.data.KeyboardCardLayout
import com.example.globalminusone.data.resolveKeyboardPodDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2485WhiteBgOriginalPriorSplitMergeAndKeyboardPodTest {

    @Test
    fun keyboardPodDimensionsFixFiveToOneKeyDistortionAndConstrainSingleHandPod() {
        // Case 1: Tall 4-row QWERTY card on a 412dp screen (previously stretched keys ~5:1 & stretched strip ~396dp).
        val tallCard = resolveKeyboardPodDimensions(
            layout = KeyboardCardLayout.QWERTY,
            hostWidthDp = 412f,
            cardWidthDp = 340f,
            cardHeightDp = 320f,
            insideContainer = false,
            showNumberRow = false
        )
        assertTrue("podWidthDp must fit single-hand range 268..304dp, got ${tallCard.podWidthDp}", tallCard.podWidthDp in 268f..304f)
        assertEquals(tallCard.podWidthDp, tallCard.candidateMaxStripWidthDp, 0.01f)
        assertTrue("Floating candidate strip must not span full 396dp screen: ${tallCard.candidateMaxStripWidthDp}", tallCard.candidateMaxStripWidthDp <= 304f)
        assertTrue("QWERTY key height must stay in 28..40dp, got ${tallCard.qwertyKeyHeightDp}", tallCard.qwertyKeyHeightDp in 28f..40f)
        val approxKeyW = (tallCard.podWidthDp - 14f - 22.5f) / 10.6f
        val aspectRatio = tallCard.qwertyKeyHeightDp / approxKeyW
        assertTrue("Key height/width aspect must never distort near 5:1 (got $aspectRatio)", aspectRatio in 1.1f..1.85f)
        assertTrue("Tall card should surface inline thumb-priority candidates above keypad", tallCard.canShowInlineCandidates)
        assertFalse(tallCard.shouldUsePopOutPod)

        // Case 2: Cramped folder container card auto-triggers ergonomic pop-out single-hand pod.
        val folderCard = resolveKeyboardPodDimensions(
            layout = KeyboardCardLayout.T9,
            hostWidthDp = 412f,
            cardWidthDp = 160f,
            cardHeightDp = 160f,
            insideContainer = true
        )
        assertTrue("Folder card must trigger pop-out single-hand pod", folderCard.shouldUsePopOutPod)
        assertFalse(folderCard.canShowInlineCandidates)
        assertTrue("Pod width stays <= 304dp", folderCard.podWidthDp in 268f..304f)
        assertTrue("T9 key height stays in 30..38dp, got ${folderCard.t9KeyHeightDp}", folderCard.t9KeyHeightDp in 30f..38f)

        // Case 3: Narrow host popup width (< 284dp) clamps pod width strictly inside host margin.
        val narrowHost = resolveKeyboardPodDimensions(
            layout = KeyboardCardLayout.QWERTY,
            hostWidthDp = 260f,
            cardWidthDp = 240f,
            cardHeightDp = 180f,
            insideContainer = true
        )
        assertTrue(narrowHost.podWidthDp <= 244f)
        assertTrue(narrowHost.shouldUsePopOutPod)
    }
}

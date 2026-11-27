package com.example.globalminusone.regression.v2485

import com.example.globalminusone.data.policy.KeyboardCardLayout
import com.example.globalminusone.data.policy.resolveKeyboardPodDimensions
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
        assertTrue("podWidthDp must fit enlarged single-hand range 308..368dp, got ${tallCard.podWidthDp}", tallCard.podWidthDp in 308f..368f)
        assertTrue("Detached candidate area constrains to comfortable single-hand width: ${tallCard.candidateMaxStripWidthDp}", tallCard.candidateMaxStripWidthDp in 248f..296f)
        assertTrue("QWERTY key height must stay in 34..48dp, got ${tallCard.qwertyKeyHeightDp}", tallCard.qwertyKeyHeightDp in 34f..48f)
        val approxKeyW = (tallCard.podWidthDp - 10f - 22.5f) / 10.2f
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
        assertTrue("T9 pod preserves original compact width 268..304dp, got ${folderCard.podWidthDp}", folderCard.podWidthDp in 268f..304f)
        assertTrue("T9 key height preserves original compact range 30..38dp, got ${folderCard.t9KeyHeightDp}", folderCard.t9KeyHeightDp in 30f..38f)

        // Case 3: Narrow host popup width (< 284dp) clamps pod width strictly inside host bounds.
        val narrowHost = resolveKeyboardPodDimensions(
            layout = KeyboardCardLayout.QWERTY,
            hostWidthDp = 260f,
            cardWidthDp = 240f,
            cardHeightDp = 180f,
            insideContainer = true
        )
        assertTrue(narrowHost.podWidthDp <= 256f)
        assertTrue(narrowHost.shouldUsePopOutPod)
    }
}

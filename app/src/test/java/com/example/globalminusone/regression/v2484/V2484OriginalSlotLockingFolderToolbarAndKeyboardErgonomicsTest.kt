package com.example.globalminusone.regression.v2484

import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.resolveEffectiveKeyTap
import com.example.globalminusone.data.resolveKeyboardSideErgonomics
import com.example.globalminusone.ui.panel.folderToolbarDensity
import com.example.globalminusone.ui.panel.shouldShowFolderHeaderDualActionRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V2484OriginalSlotLockingFolderToolbarAndKeyboardErgonomicsTest {

    @Test
    fun folderToolbarHidesPermanentDualActionRowAndKeepsThreeDotMenuEntry() {
        assertFalse(shouldShowFolderHeaderDualActionRow(editing = false, locked = false))
        assertFalse(shouldShowFolderHeaderDualActionRow(editing = true, locked = false))
        assertFalse(shouldShowFolderHeaderDualActionRow(editing = true, locked = true))
        val normalDensity = folderToolbarDensity(contentWidthDp = 300f)
        assertFalse(normalDensity.compactHeader)
        assertFalse(normalDensity.compactMenu)
        assertTrue(folderToolbarDensity(contentWidthDp = 180f).compactHeader)
        assertTrue(folderToolbarDensity(contentWidthDp = 110f).ultraCompactHeader)
    }

    @Test
    fun keyboardSideErgonomicsRemovesLaunchKeyAdaptsToLeftRightAndSnapsFatFingerToReachableNeighbor() {
        val rightErg = resolveKeyboardSideErgonomics(TriggerSide.RIGHT)
        assertFalse(rightErg.hasLaunchButton)
        assertFalse(rightErg.backspaceOnLeft)
        assertFalse(rightErg.headerControlsOnLeft)
        assertTrue(rightErg.row2LeadingSpacerWeight > rightErg.row2TrailingSpacerWeight)
        assertTrue(rightErg.row3LeadingSpacerWeight > rightErg.row3TrailingSpacerWeight)

        val leftErg = resolveKeyboardSideErgonomics(TriggerSide.LEFT)
        assertFalse(leftErg.hasLaunchButton)
        assertTrue(leftErg.backspaceOnLeft)
        assertTrue(leftErg.headerControlsOnLeft)
        assertTrue(leftErg.row2TrailingSpacerWeight > leftErg.row2LeadingSpacerWeight)
        assertTrue(leftErg.row3TrailingSpacerWeight > leftErg.row3LeadingSpacerWeight)

        val row2 = listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l')
        // Already reachable key passes through untouched
        assertEquals('s', resolveEffectiveKeyTap(row2, 's', setOf('s', 'd'), TriggerSide.RIGHT))
        // Tapping dimmed 's' when only right neighbor 'd' is reachable snaps to 'd'
        assertEquals('d', resolveEffectiveKeyTap(row2, 's', setOf('d'), TriggerSide.RIGHT))
        // When both neighbors ('a' and 'd') are reachable, tie breaks toward thumb side
        assertEquals('d', resolveEffectiveKeyTap(row2, 's', setOf('a', 'd'), TriggerSide.RIGHT))
        assertEquals('a', resolveEffectiveKeyTap(row2, 's', setOf('a', 'd'), TriggerSide.LEFT))
        // When no immediate neighbor is reachable, keep original tapped key
        assertEquals('s', resolveEffectiveKeyTap(row2, 's', setOf('k'), TriggerSide.RIGHT))
    }
}

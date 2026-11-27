package com.example.globalminusone.regression.v2438

import com.example.globalminusone.ui.panel.file.fileBrowserControlsHeightDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBrowserEditorBudgetTest {
    @Test fun activeEditorIsDeductedBeforeReservingFileRows() {
        val window = 400f
        val editor = 100f
        val tools = fileBrowserControlsHeightDp(window, false, 44f, editor)
        assertTrue(window - editor - tools >= 3f * 44f + 16f)
        assertTrue(tools < fileBrowserControlsHeightDp(window, false, 44f))
    }

    @Test fun editorLargerThanViewportDoesNotCreateNegativeConstraints() {
        assertEquals(0f, fileBrowserControlsHeightDp(100f, false, 44f, 120f), 0f)
    }

    @Test fun editorFreeViewportRetainsExistingBudget() {
        assertEquals(fileBrowserControlsHeightDp(400f, false, 44f),
            fileBrowserControlsHeightDp(400f, false, 44f, 0f), 0f)
    }
}

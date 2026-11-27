package com.example.globalminusone.regression.v2414

import com.example.globalminusone.ui.panel.*
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.ui.panel.editor.EditToolsAnchor
import com.example.globalminusone.ui.panel.editor.EditToolsPosition
import com.example.globalminusone.ui.panel.editor.editToolsAnchor
import com.example.globalminusone.ui.panel.editor.editToolsPosition

class EditToolsAnchorTest {
    @Test fun savedAnchorReturnsToSamePositionWhenEditorIsRecreated() {
        val position = EditToolsPosition(120f, 900f)
        val saved = editToolsAnchor(position, EditToolsAnchor(), 360f, 48f, 800f, 1600f)
        val restored = editToolsPosition(saved, 360f, 48f, 800f, 1600f)
        assertEquals(position.x, restored.x, .01f)
        assertEquals(position.y, restored.y, .01f)
    }
    @Test fun rotationKeepsRelativePlacementAndClampsToTheNewScreen() {
        val saved = editToolsAnchor(EditToolsPosition(400f, 1400f), EditToolsAnchor(), 360f, 48f, 800f, 1600f)
        val rotated = editToolsPosition(saved, 360f, 48f, 1600f, 800f)
        assertTrue(rotated.y > 0)
        assertTrue(rotated.x <= 1240f && rotated.y <= 752f)
        assertEquals(EditToolsPosition(0f, 0f), editToolsPosition(saved, 360f, 48f, 200f, 40f))
        val unchanged = editToolsAnchor(EditToolsPosition(0f, 0f), saved, 360f, 48f, 200f, 40f)
        assertEquals(saved, unchanged)
    }
    @Test fun malformedStoredCoordinatesCannotPositionToolsOffScreen() {
        assertEquals(EditToolsPosition(0f, 0f), editToolsPosition(EditToolsAnchor(Float.NaN, Float.POSITIVE_INFINITY), 360f, 48f, 800f, 1600f))
        assertEquals(EditToolsPosition(0f, 1552f), editToolsPosition(EditToolsAnchor(-1f, 2f), 360f, 48f, 800f, 1600f))
    }
}

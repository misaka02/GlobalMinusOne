package com.example.globalminusone.regression.v2411

import com.example.globalminusone.data.*
import com.example.globalminusone.ui.panel.*
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.ClipboardRecord
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.ui.panel.editor.CardDestructiveAction
import com.example.globalminusone.ui.panel.editor.CardDestructiveRequest
import com.example.globalminusone.ui.panel.editor.clipboardClearStillMatches
import com.example.globalminusone.ui.panel.editor.resolveCardDestructiveTarget

class CardDestructiveActionsTest {
    private val child = ComponentItem(id = "child", type = ComponentType.NOTE_CARD)
    private val container = ComponentItem(id = "box", type = ComponentType.CONTAINER_BOX, containerComponents = listOf(child))
    private val page = PageConfig(id = "page", name = "页面", components = listOf(container))
    private fun request(id: String = "child", action: CardDestructiveAction = CardDestructiveAction.DELETE) =
        CardDestructiveRequest(page.id, id, "测试", action)
    private fun state() = GlobalMinusOneState(pages = listOf(page))

    @Test fun nestedDeletionResolvesAfterPageReorder() {
        val state = state().copy(pages = listOf(PageConfig(id = "other", name = "其它"), page))
        val target = resolveCardDestructiveTarget(state, request())!!
        assertEquals(1, target.pageIndex)
        assertEquals(child, target.item)
    }
    @Test fun missingPageOrCardNeverFallsBackToAnotherTarget() {
        assertNull(resolveCardDestructiveTarget(state().copy(pages = emptyList()), request()))
        assertNull(resolveCardDestructiveTarget(state(), request("gone")))
    }
    @Test fun layoutLockAppliedWhileConfirmationIsOpenRejectsMutation() {
        val state = state()
        assertNull(resolveCardDestructiveTarget(state.copy(advancedConfig = state.advancedConfig.copy(layoutLocked = true)), request()))
    }
    @Test fun clearRequiresCurrentContainerTypeIncludingFolders() {
        assertNull(resolveCardDestructiveTarget(state(), request("child", CardDestructiveAction.CLEAR_CHILDREN)))
        for (type in listOf(ComponentType.CONTAINER_BOX, ComponentType.FOLDER)) {
            val state = state().copy(pages = listOf(page.copy(components = listOf(container.copy(type = type)))))
            assertNotNull(resolveCardDestructiveTarget(state, request("box", CardDestructiveAction.CLEAR_CHILDREN)))
        }
    }
    @Test fun clipboardNewRecordsAndPinChangesRequireFreshConfirmation() {
        val a = ClipboardRecord(id = "a", text = "a")
        val pinned = ClipboardRecord(id = "p", text = "p", pinned = true)
        assertTrue(clipboardClearStillMatches(listOf(a, pinned), setOf("a")))
        assertFalse(clipboardClearStillMatches(listOf(a, pinned, ClipboardRecord(id = "b", text = "b")), setOf("a")))
        assertFalse(clipboardClearStillMatches(listOf(a.copy(pinned = true), pinned), setOf("a")))
        assertFalse(clipboardClearStillMatches(listOf(pinned), emptySet()))
    }
}

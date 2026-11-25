package com.example.globalminusone.ui.panel

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.findComponent
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.QuickActionItem
import org.junit.Assert.*
import org.junit.Test

class CardLayoutCapabilityTargetTest {
    private val child = ComponentItem(id = "child", type = ComponentType.CLIPBOARD_CARD)
    private val legacy = QuickActionItem(id = "legacy", title = "old", intentUri = "intent://synthetic")
    private val folder = ComponentItem(id = "folder", type = ComponentType.FOLDER,
        containerComponents = listOf(child), containerActions = listOf(legacy))
    private val page = PageConfig(id = "page", name = "page", components = listOf(folder))
    private class Mutations(var state: CardLayoutSnapshot) : AppCardMutationAccess {
        val calls = mutableListOf<String>()
        var accepted = true
        var failWith: RuntimeException? = null
        var edited: ComponentItem? = null
        override val currentLayout: CardLayoutSnapshot get() { calls += "read"; return state }
        override fun updateComponent(pageIndex: Int, componentId: String, transform: (ComponentItem) -> ComponentItem): Boolean {
            calls += "update:$pageIndex:$componentId"
            failWith?.let { throw it }
            val item = state.pages[pageIndex].components.findComponent(componentId)
            edited = transform(checkNotNull(item))
            return accepted
        }
    }
    private fun state() = GlobalMinusOneState(pages = listOf(page)).cardLayoutSnapshot()

    @Test fun cardMutationResolvesNestedIdentityAgainstLatestReorderedPages() {
        val actions = Mutations(state())
        actions.state = actions.state.copy(pages = listOf(PageConfig(id = "other", name = "other"), page))
        assertTrue(mutateAppMenuCard(actions, page.id, child) { it.copy(title = "renamed") })
        assertEquals(listOf("read", "update:1:child"), actions.calls)
        assertEquals("renamed", actions.edited?.title)
    }
    @Test fun removedOrLockedTargetNeverMutatesAnotherPageOrParent() {
        for (current in listOf(state().copy(layoutLocked = true), state().copy(pages = emptyList()), state().copy(pages = listOf(page.copy(components = emptyList()))))) {
            val actions = Mutations(current)
            assertFalse(mutateAppMenuCard(actions, page.id, child) { error("invalid target") })
            assertEquals(listOf("read"), actions.calls)
        }
        assertFalse(mutateAppMenuCard(null, page.id, child) { it })
    }
    @Test fun legacyMutationRequiresTheOriginalParentAndActionAndPreservesItsId() {
        val actions = Mutations(state())
        assertTrue(mutateAppMenuLegacy(actions, page.id, folder.id, legacy) { it.copy(title = "new") })
        assertEquals(listOf("read", "update:0:folder"), actions.calls)
        assertEquals(legacy.id, actions.edited?.containerActions?.single()?.id)
        assertEquals("new", actions.edited?.containerActions?.single()?.title)
        assertEquals(listOf(child), actions.edited?.containerComponents)
    }
    @Test fun deletedLegacyActionOrParentNeverFallsBackToTopLevel() {
        val actions = Mutations(state().copy(pages = listOf(page.copy(components = listOf(folder.copy(containerActions = emptyList()))))))
        assertFalse(mutateAppMenuLegacy(actions, page.id, folder.id, legacy) { error("missing action") })
        assertEquals(listOf("read"), actions.calls)
        actions.calls.clear()
        assertFalse(mutateAppMenuLegacy(actions, page.id, "gone", legacy) { error("missing parent") })
        assertEquals(listOf("read"), actions.calls)
    }
    @Test fun declinedWriteReturnsFalseForBothCardAndLegacyAction() {
        val actions = Mutations(state()); actions.accepted = false
        assertFalse(mutateAppMenuCard(actions, page.id, child) { it })
        assertFalse(mutateAppMenuLegacy(actions, page.id, folder.id, legacy) { it })
        assertEquals(listOf("read", "update:0:child", "read", "update:0:folder"), actions.calls)
    }
    @Test fun declinedDraftCanRetryAndDisappearingTargetNeverFallsBack() {
        val actions = Mutations(state()); actions.accepted = false
        val draft = "unsaved input"
        assertFalse(mutateAppMenuCard(actions, page.id, child) { it.copy(title = draft) })
        actions.accepted = true
        assertTrue(mutateAppMenuCard(actions, page.id, child) { it.copy(title = draft) })
        assertEquals(draft, actions.edited?.title)
        actions.state = actions.state.copy(pages = listOf(page.copy(components = listOf(folder.copy(containerComponents = emptyList())))))
        assertFalse(mutateAppMenuCard(actions, page.id, child) { error("must not target a parent or root") })
        assertEquals(listOf("read", "update:0:child", "read", "update:0:child", "read"), actions.calls)
    }
    @Test fun mutationFailurePropagatesAfterSingleTargetReadWithoutRetry() {
        val actions = Mutations(state()); val failure = IllegalStateException("save")
        actions.failWith = failure
        try { mutateAppMenuLegacy(actions, page.id, folder.id, legacy) { it }; fail("must propagate") }
        catch (caught: IllegalStateException) { assertSame(failure, caught) }
        assertEquals(listOf("read", "update:0:folder"), actions.calls)
    }
}

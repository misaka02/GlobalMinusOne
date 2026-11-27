package com.example.globalminusone.ui.panel

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.ClipboardClearResult
import com.example.globalminusone.data.ClipboardRecord
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.ui.panel.editor.CardDestructiveAction
import com.example.globalminusone.ui.panel.editor.CardDestructiveRequest
import com.example.globalminusone.ui.panel.cards.ClipboardHistoryActions
import com.example.globalminusone.ui.panel.editor.ConfirmationWriteResult
import com.example.globalminusone.ui.panel.cards.cardLayoutActions
import com.example.globalminusone.ui.panel.cards.cardLayoutSnapshot
import com.example.globalminusone.ui.panel.cards.clearClipboardSelection
import com.example.globalminusone.ui.panel.cards.clipboardHistoryActions
import com.example.globalminusone.ui.panel.editor.confirmCardDestructiveAction
import com.example.globalminusone.ui.panel.editor.resolveCardDestructiveTarget
import com.example.globalminusone.ui.panel.container.saveLegacyContainerAction

class CardWriteAcceptanceTest {
    private class FaultPreferences(private val delegate: MemoryPreferences = MemoryPreferences()) : SharedPreferences by delegate {
        var rejectNext = false
        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                override fun remove(key: String?): SharedPreferences.Editor { editor.remove(key); return this }
                override fun apply() {
                    if (rejectNext) { rejectNext = false; error("synthetic write rejection") }
                    editor.apply()
                }
            }
        }
    }
    private val legacy = QuickActionItem(id = "old", title = "old", intentUri = "test:")
    private val child = ComponentItem(id = "child", type = ComponentType.NOTE_CARD)
    private val box = ComponentItem(id = "box", type = ComponentType.FOLDER,
        containerActions = listOf(legacy), containerComponents = listOf(child))
    private fun repository(prefs: FaultPreferences): MinusOneRepository = MinusOneRepository(prefs).also {
        it.updatePageConfig(0) { page -> page.copy(components = listOf(box)) }
    }
    private fun request(repo: MinusOneRepository, id: String = "box", action: CardDestructiveAction = CardDestructiveAction.CLEAR_CHILDREN) =
        CardDestructiveRequest(repo.state.value.pages[0].id, id, "test", action)

    @Test fun legacyDraftCanRetryAfterActualRepositoryRejection() {
        val prefs = FaultPreferences(); val repo = repository(prefs)
        val before = repo.state.value
        // The same draft/geometry is offered again after rejection, as the dialog remains open.
        val draft: (QuickActionItem) -> QuickActionItem = { it.copy(id = "must-not-replace-identity", title = "new", xDp = 27, wDp = 99) }
        prefs.rejectNext = true
        assertEquals(ConfirmationWriteResult.WRITE_REJECTED, saveLegacyContainerAction(repo, 0, "box", "old", draft))
        assertEquals(before, repo.state.value)
        assertNotNull(repo.persistenceError.value)
        assertEquals(ConfirmationWriteResult.APPLIED, saveLegacyContainerAction(repo, 0, "box", "old", draft))
        assertEquals(draft(legacy).copy(id = legacy.id), repo.state.value.pages[0].components.single().containerActions.single())
        repo.setLayoutLocked(true)
        assertEquals(ConfirmationWriteResult.TARGET_CHANGED, saveLegacyContainerAction(repo, 0, "box", "old", draft))
        repo.setLayoutLocked(false)
        repo.removeComponent(0, "box")
        assertEquals(ConfirmationWriteResult.TARGET_CHANGED, saveLegacyContainerAction(repo, 0, "box", "old", draft))
    }

    @Test fun nestedDeleteAndClearStayOpenOnRejectionAndRetrySuccessfully() {
        for ((id, action) in listOf("box" to CardDestructiveAction.CLEAR_CHILDREN, "child" to CardDestructiveAction.DELETE)) {
            val prefs = FaultPreferences(); val repo = repository(prefs)
            val request = request(repo, id, action); val before = repo.state.value
            val actions = cardLayoutActions(repo)
            prefs.rejectNext = true
            assertEquals(ConfirmationWriteResult.WRITE_REJECTED, confirmCardDestructiveAction(actions, request))
            assertEquals(before, repo.state.value)
            assertEquals(ConfirmationWriteResult.APPLIED, confirmCardDestructiveAction(actions, request))
            val current = repo.state.value.pages[0].components.single()
            assertTrue(current.containerComponents.isEmpty())
            if (action == CardDestructiveAction.CLEAR_CHILDREN) assertTrue(current.containerActions.isEmpty())
            repo.setLayoutLocked(true)
            assertEquals(ConfirmationWriteResult.TARGET_CHANGED, confirmCardDestructiveAction(actions, request))
        }
    }

    @Test fun clipboardRejectionIsDistinctFromConfirmationRangeChanges() {
        val prefs = FaultPreferences(); val repo = repository(prefs)
        repo.recordClipboardText("one")
        val expected = repo.state.value.clipboardHistory.filterNot { it.pinned }.map { it.id }.toSet()
        val history = clipboardHistoryActions(repo); val before = repo.state.value
        prefs.rejectNext = true
        assertEquals(ClipboardClearResult.SAVE_REJECTED, clearClipboardSelection(history, expected))
        assertEquals(before, repo.state.value)
        assertEquals(ClipboardClearResult.ACCEPTED, clearClipboardSelection(history, expected))
        repo.recordClipboardText("two")
        assertEquals(ClipboardClearResult.SELECTION_CHANGED, clearClipboardSelection(history, expected))
        assertEquals(listOf("two"), repo.state.value.clipboardHistory.filterNot { it.pinned }.map { it.text })
        assertEquals(before.clipboardHistory.filter { it.pinned }, repo.state.value.clipboardHistory.filter { it.pinned })
    }

    @Test fun staleClipboardSnapshotCannotClearRecordsAddedBeforeExecution() {
        val prefs = FaultPreferences(); val repo = repository(prefs)
        repo.recordClipboardText("confirmed")
        val snapshot = repo.state.value.clipboardHistory
        val expected = snapshot.filterNot { it.pinned }.map { it.id }.toSet()
        val realActions = clipboardHistoryActions(repo)
        val staleReader = object : ClipboardHistoryActions by realActions {
            override val currentRecords: List<ClipboardRecord> get() = snapshot
        }
        repo.recordClipboardText("arrived later")
        assertEquals(ClipboardClearResult.SELECTION_CHANGED, clearClipboardSelection(staleReader, expected))
        assertEquals(snapshot.size + 1, repo.state.value.clipboardHistory.size)
        assertTrue(repo.state.value.clipboardHistory.any { it.text == "arrived later" })
    }

    @Test fun containerCommandsRejectMissingActionAndNonContainerWithoutFalseSuccess() {
        val prefs = FaultPreferences(); val repo = repository(prefs)
        assertFalse(repo.tryRemoveActionFromContainer(0, "box", "missing"))
        assertFalse(repo.tryUpdateContainerAction(0, "box", "missing") { it.copy(title = "wrong") })
        assertFalse(repo.tryClearContainerActions(0, "child"))
        assertFalse(repo.tryUpdateContainerAction(0, "child", "old") { it.copy(title = "wrong") })
        assertFalse(repo.tryRemoveActionFromContainer(0, "child", "old"))
        repo.setLayoutLocked(true)
        assertFalse(repo.tryClearContainerActions(0, "box"))
        assertFalse(repo.tryRemoveActionFromContainer(0, "box", "old"))
    }

    @Test fun legacyRemovalAlsoReturnsRealAcceptance() {
        val prefs = FaultPreferences(); val repo = repository(prefs); val before = repo.state.value
        prefs.rejectNext = true
        assertFalse(repo.tryRemoveActionFromContainer(0, "box", "old"))
        assertEquals(before, repo.state.value)
        assertTrue(repo.tryRemoveActionFromContainer(0, "box", "old"))
        assertTrue(repo.state.value.pages[0].components.single().containerActions.isEmpty())
    }

    @Test fun ordinaryAndSnapshotResolutionShareReorderDeletionAndLockRules() {
        val page = PageConfig(id = "original", name = "original", components = listOf(box))
        val request = CardDestructiveRequest(page.id, child.id, "child", CardDestructiveAction.DELETE)
        val original = GlobalMinusOneState(pages = listOf(page))
        val reordered = original.copy(pages = listOf(PageConfig(id = "other", name = "other"), page))
        val states = listOf(original, reordered, original.copy(pages = emptyList()),
            original.copy(pages = listOf(page.copy(components = emptyList()))),
            original.copy(advancedConfig = original.advancedConfig.copy(layoutLocked = true)))
        for (state in states) assertEquals(resolveCardDestructiveTarget(state, request), resolveCardDestructiveTarget(state.cardLayoutSnapshot(), request))
        assertEquals(1, resolveCardDestructiveTarget(reordered, request)!!.pageIndex)
        assertEquals(child.id, resolveCardDestructiveTarget(reordered, request)!!.item.id)
        for (state in states.drop(2)) assertNull(resolveCardDestructiveTarget(state, request))
    }
}

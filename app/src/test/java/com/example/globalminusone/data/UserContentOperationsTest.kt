package com.example.globalminusone.data

import com.example.globalminusone.MemoryPreferences
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.operations.UserContentOperations

class UserContentOperationsTest {
    @Test fun refreshKeepsFirstTextMatchIdentityAndPinWithoutCreatingOrApplyingNewRecordLimit() {
        val matched = ClipboardRecord(id = "kept", text = "same", pinned = true, timestamp = 1)
        val history = listOf(matched, matched.copy(text = "duplicate-id")) +
            (0..26).map { ClipboardRecord(id = "other-$it", text = "other-$it", timestamp = 10) }
        var clockCalls = 0
        var factoryCalls = 0
        val updated = UserContentOperations.recordClipboard(history, "same", { clockCalls++; 99L }, {
            factoryCalls++; ClipboardRecord(id = "wrong-new-record", text = it, timestamp = 100)
        })
        assertEquals(1, clockCalls)
        assertEquals(0, factoryCalls)
        assertEquals(28, updated.size)
        assertEquals(matched.copy(timestamp = 99), updated.first())
        assertEquals(1, updated.count { it.id == "kept" })
        assertFalse(updated.any { it.text == "duplicate-id" })
        assertEquals(history.drop(2), updated.drop(1))
    }

    @Test fun newClipboardRecordIsCreatedOnceThenTakes25BeforeStablePinAndTimestampSort() {
        val history = (0..25).map { ClipboardRecord(id = "old-$it", text = "old-$it", pinned = it == 25, timestamp = 10) }
        var factoryCalls = 0
        val updated = UserContentOperations.recordClipboard(history, "new", { error("Refresh clock must not run for new content") }, {
            factoryCalls++; ClipboardRecord(id = "new", text = it, timestamp = 10)
        })
        assertEquals(1, factoryCalls)
        assertEquals(25, updated.size)
        assertEquals(listOf("new") + (0..23).map { "old-$it" }, updated.map { it.id })
        assertFalse(updated.any { it.id == "old-25" })
    }

    @Test fun todoAndClipboardTransformsRetainDuplicateIdAndStableOrderingSemantics() {
        val todos = listOf(TodoItem("same", "one", false, 1), TodoItem("same", "two", true, 2))
        val added = TodoItem("new", "three", false, 3)
        assertEquals(listOf(added) + todos, UserContentOperations.addTodo(todos, added))
        assertEquals(listOf(true, false), UserContentOperations.toggleTodo(todos, "same").map { it.done })
        assertTrue(UserContentOperations.deleteTodo(todos, "same").isEmpty())
        val first = ClipboardRecord("same", "one", false, 10)
        val second = ClipboardRecord("same", "two", false, 10)
        val pinned = ClipboardRecord("pinned", "three", true, 20)
        val history = listOf(first, second, pinned)
        assertEquals(listOf(pinned, first.copy(pinned = true), second.copy(pinned = true)), UserContentOperations.togglePinClipboard(history, "same"))
        assertEquals(listOf(pinned), UserContentOperations.deleteClipboard(history, "same"))
        assertEquals(listOf(pinned), UserContentOperations.clearUnpinnedClipboard(history))
    }

    @Test fun blankContentReturnsBeforeRepositoryWriteAcceptance() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        val initial = repository.state.value
        repository.addTodo(" \n\t ")
        repository.recordClipboardText(" \n\t ")
        assertEquals(0, prefs.writeCount)
        assertEquals(initial, repository.state.value)
    }

    @Test fun discardingPanelEditPreservesLiveTodosAndPinnedClipboardAcrossReload() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        val originalPages = repository.state.value.pages
        assertTrue(repository.setEditMode(true))
        repository.updatePageConfig(0) { it.copy(name = "discarded layout draft") }
        repository.addTodo("  live todo  ")
        repository.recordClipboardText("  live clipboard  ")
        val record = repository.state.value.clipboardHistory.single { it.text == "live clipboard" }
        repository.togglePinClipboard(record.id)
        assertTrue(repository.finishPanelEdit(save = false))
        val reloaded = MinusOneRepository(prefs).state.value
        assertEquals(originalPages, reloaded.pages)
        assertTrue(reloaded.todos.any { it.content == "live todo" })
        assertEquals(record.copy(pinned = true), reloaded.clipboardHistory.single { it.id == record.id })
        assertFalse(reloaded.editMode)
    }
}

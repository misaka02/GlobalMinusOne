package com.example.globalminusone.ui.panel

import com.example.globalminusone.data.ClipboardClearResult
import com.example.globalminusone.data.ClipboardRecord
import org.junit.Assert.*
import org.junit.Test

class CardContentCapabilityTest {
    private class History : ClipboardHistoryActions {
        val calls = mutableListOf<String>()
        var records = listOf(ClipboardRecord(id = "a", text = "one"))
        var clearFailure: RuntimeException? = null
        override val currentRecords: List<ClipboardRecord> get() { calls += "readRecords"; return records }
        override fun recordClipboardText(text: String) { calls += "record:$text" }
        override fun togglePinClipboard(id: String) { calls += "pin:$id" }
        override fun deleteClipboardRecord(id: String) { calls += "delete:$id" }
        override fun clearUnpinnedClipboard(expectedIds: Set<String>): ClipboardClearResult {
            if (!clipboardClearStillMatches(currentRecords, expectedIds)) return ClipboardClearResult.SELECTION_CHANGED
            calls += "clear"; clearFailure?.let { throw it }; return ClipboardClearResult.ACCEPTED
        }
    }
    private fun clipboard(history: History, read: () -> String?) = object : ClipboardTextAccess {
        override fun readCurrentClipboardText(): String? { history.calls += "readClipboard"; return read() }
        override fun copyToClipboard(label: String, text: String) { history.calls += "copy:$label:$text" }
    }
    @Test fun explicitCaptureReadsClipboardBeforeRecordingWithoutEditingTheText() {
        val history = History()
        recordCurrentClipboard(history, clipboard(history) { "  exact text  " })
        assertEquals(listOf("readClipboard", "record:  exact text  "), history.calls)
    }
    @Test fun deniedOrEmptyClipboardReadDoesNotCreateAHistoryRecord() {
        val history = History()
        recordCurrentClipboard(history, clipboard(history) { null })
        assertEquals(listOf("readClipboard"), history.calls)
    }
    @Test fun clipboardReadFailurePropagatesWithoutRecordOrFallback() {
        val history = History(); val failure = IllegalStateException("read")
        try { recordCurrentClipboard(history, clipboard(history) { throw failure }); fail("must propagate") }
        catch (caught: IllegalStateException) { assertSame(failure, caught) }
        assertEquals(listOf("readClipboard"), history.calls)
    }
    @Test fun confirmationRechecksCurrentUnpinnedIdsBeforeClearing() {
        val history = History()
        history.records = history.records + ClipboardRecord(id = "pinned", text = "p", pinned = true)
        assertEquals(ClipboardClearResult.ACCEPTED, clearClipboardSelection(history, setOf("a")))
        assertEquals(listOf("readRecords", "clear"), history.calls)
        history.calls.clear(); history.records = history.records.map { it.copy(pinned = true) }
        assertEquals(ClipboardClearResult.SELECTION_CHANGED, clearClipboardSelection(history, setOf("a")))
        assertEquals(listOf("readRecords"), history.calls)
    }
    @Test fun clearFailureIsNotReportedAsSuccessOrRetried() {
        val history = History(); val failure = IllegalStateException("clear")
        history.clearFailure = failure
        try { clearClipboardSelection(history, setOf("a")); fail("must propagate") }
        catch (caught: IllegalStateException) { assertSame(failure, caught) }
        assertEquals(listOf("readRecords", "clear"), history.calls)
    }
}

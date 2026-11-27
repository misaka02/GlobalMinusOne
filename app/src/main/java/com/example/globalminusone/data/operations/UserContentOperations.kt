package com.example.globalminusone.data.operations

import com.example.globalminusone.data.ClipboardRecord
import com.example.globalminusone.data.TodoItem

/** Live content transformations; the repository owns state publication and persistence. */
internal object UserContentOperations {
    private val clipboardOrder = compareByDescending<ClipboardRecord> { it.pinned }.thenByDescending { it.timestamp }

    fun addTodo(todos: List<TodoItem>, item: TodoItem): List<TodoItem> = listOf(item) + todos

    fun toggleTodo(todos: List<TodoItem>, id: String): List<TodoItem> =
        todos.map { if (it.id == id) it.copy(done = !it.done) else it }

    fun deleteTodo(todos: List<TodoItem>, id: String): List<TodoItem> = todos.filterNot { it.id == id }

    /** The caller validates/trim text; only the branch that needs an identity or time invokes its factory. */
    fun recordClipboard(
        history: List<ClipboardRecord>, text: String,
        refreshTimestamp: () -> Long, createRecord: (String) -> ClipboardRecord,
    ): List<ClipboardRecord> {
        val existing = history.find { it.text == text }
        val updatedList = if (existing != null) {
            val refreshed = existing.copy(timestamp = refreshTimestamp())
            listOf(refreshed) + history.filterNot { it.id == existing.id }
        } else {
            val newRecord = createRecord(text)
            (listOf(newRecord) + history).take(25)
        }
        return updatedList.sortedWith(clipboardOrder)
    }

    fun togglePinClipboard(history: List<ClipboardRecord>, id: String): List<ClipboardRecord> = history.map {
        if (it.id == id) it.copy(pinned = !it.pinned) else it
    }.sortedWith(clipboardOrder)

    fun deleteClipboard(history: List<ClipboardRecord>, id: String): List<ClipboardRecord> = history.filterNot { it.id == id }

    fun clearUnpinnedClipboard(history: List<ClipboardRecord>): List<ClipboardRecord> = history.filter { it.pinned }
}

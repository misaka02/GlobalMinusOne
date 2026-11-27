package com.example.globalminusone.ui.panel.file

import androidx.compose.runtime.mutableStateOf
import com.example.globalminusone.system.file.DuplicateFileGroup
import com.example.globalminusone.system.file.FileCategory
import com.example.globalminusone.system.file.FileScanResult
import com.example.globalminusone.system.file.FileSearchResult

/** Results and selections belong to one directory, independently of inspector/editor drafts. */
internal data class FileManagementSearchOwner(val rootKey: String?, val query: String, val category: FileCategory)

internal class FileManagementAnalyzerState {
    var rootKey: String? = null
        private set
    val statistics = mutableStateOf<FileScanResult?>(null)
    val duplicateGroups = mutableStateOf<List<DuplicateFileGroup>?>(null)
    val results = mutableStateOf<FileSearchResult?>(null)
    val query = mutableStateOf("")
    val category = mutableStateOf(FileCategory.ALL)

    fun updateQuery(value: String) {
        if (query.value == value) return
        query.value = value
        invalidateSearch()
    }

    fun updateCategory(value: FileCategory) {
        if (category.value == value) return
        category.value = value
        invalidateSearch()
    }

    fun searchOwner() = FileManagementSearchOwner(rootKey, query.value.trim(), category.value)

    fun publishSearch(owner: FileManagementSearchOwner, value: FileSearchResult) {
        if (owner == searchOwner()) results.value = value
    }

    fun appendSearch(owner: FileManagementSearchOwner, next: FileSearchResult) {
        if (owner != searchOwner()) return
        val existing = results.value ?: run {
            results.value = next
            return
        }
        val seenKeys = existing.entries.mapTo(HashSet()) { it.location.key }
        val merged = existing.entries + next.entries.filter { seenKeys.add(it.location.key) }
        results.value = next.copy(entries = merged)
    }

    private fun invalidateSearch() {
        results.value = null
        selected.value = emptySet()
        selecting.value = false
    }
    val selected = mutableStateOf<Set<String>>(emptySet())
    val selecting = mutableStateOf(false)

    fun bindRoot(key: String?) {
        if (rootKey == key) return
        rootKey = key
        invalidate()
    }

    fun invalidate() {
        statistics.value = null
        duplicateGroups.value = null
        results.value = null
        selected.value = emptySet()
        selecting.value = false
    }
}

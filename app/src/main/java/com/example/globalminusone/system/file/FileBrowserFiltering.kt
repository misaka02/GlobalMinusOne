package com.example.globalminusone.system.file

/** Continuation adds a batch without discarding matches already published by the same query. */
internal fun mergeFileSearchResults(previous: FileSearchResult?, page: FileSearchResult): FileSearchResult =
    page.copy(entries = (previous?.entries.orEmpty() + page.entries).distinctBy { it.location.key })

/** Navigation keeps directories reachable; a name search includes only matching entries. */
internal fun filterBrowserEntries(
    entries: List<BrowserEntry>, query: String, regex: Boolean, showHidden: Boolean,
    quickFilter: String, category: FileCategory
): List<BrowserEntry> {
    val term = query.trim()
    val matcher = try { FileSearchPattern(term, regex) } catch (_: RuntimeException) { return emptyList() }
    return entries.filter { entry ->
        if (!showHidden && entry.name.startsWith('.')) return@filter false
        if (term.isNotBlank() && !runCatching { matcher.matches(entry.name) }.getOrDefault(false)) return@filter false
        if (term.isBlank() && entry.isDirectory) return@filter true
        matchesQuickFilter(entry.name, quickFilter) && (category == FileCategory.ALL || FileCategory.of(entry) == category)
    }
}

/** Only a non-empty active query may own a result page; clearing it reveals directory rows. */
internal fun activeFileSearchResult(result: FileSearchResult?, searchOpen: Boolean, query: String): FileSearchResult? =
    result.takeIf { searchOpen && query.isNotBlank() }

/** Navigation directories are not file matches when a quick/type filter is active. */
internal fun browserEntryCountLabel(entries: List<BrowserEntry>, query: String, quickFilter: String,
    category: FileCategory, searchResults: Boolean = false): String {
    if (!searchResults && query.isBlank() && (quickFilter.isNotBlank() || category != FileCategory.ALL)) {
        val directories = entries.count { it.isDirectory }
        return "${entries.size - directories} 个匹配文件 · $directories 个目录保留"
    }
    return "${entries.size} 项${if (searchResults || query.isNotBlank()) "匹配" else " · 目录优先"}"
}

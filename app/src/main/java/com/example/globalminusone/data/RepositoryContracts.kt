package com.example.globalminusone.data

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Acceptance of the current unpinned selection, checked inside the repository transaction lock. */
internal enum class ClipboardClearResult { ACCEPTED, SELECTION_CHANGED, SAVE_REJECTED }

/** Unparsed recovery evidence; this is deliberately never presented as a valid panel configuration. */
data class CorruptStateBackup(val preferenceKey: String, val raw: String)

/** APPLIED means a valid command was accepted, including no change; it is not a disk flush guarantee. */
internal enum class LayoutMutationResult { APPLIED, TARGET_CHANGED, WRITE_REJECTED }

internal object WidgetHostContract {
    // Persistent widget binding identity; do not change casually.
    const val HOST_ID = 2026
}

/** Reauthorization replaces in place; choosing an existing root never duplicates or reorders it. */
internal fun mergeGrantedFileRoot(roots: List<FileBrowserRoot>, root: FileBrowserRoot, replaceUri: String?): List<FileBrowserRoot> {
    var inserted = false
    val updated = roots.mapNotNull { saved ->
        if (saved.uri == root.uri || saved.uri == replaceUri) {
            if (inserted) null else root.also { inserted = true }
        } else saved
    }
    return if (inserted) updated else updated + root
}

/** A picker result already received by the host; recreating the host must resume it, not open another picker. */
internal data class PendingDocumentAccess(val uri: String, val flags: Int)

/** An initially false expanded flag is insufficient while the opening animation is still running. */
internal suspend fun awaitFilePickerPanelRelease(
    expanded: StateFlow<Boolean>,
    closeGeneration: StateFlow<Long>,
    requestedAfter: Long,
    timeoutMillis: Long = 10_000L
): Boolean = withTimeoutOrNull(timeoutMillis) {
    combine(expanded, closeGeneration) { open, generation -> !open && generation > requestedAfter }.first { it }
    true
} ?: false

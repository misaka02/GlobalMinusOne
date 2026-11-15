package com.example.globalminusone.data

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** A picker result already received by the host; recreating the host must resume it, not open another picker. */
internal data class PendingDocumentAccess(val uri: String, val flags: Int)

/** An initially false expanded flag is insufficient while the opening animation is still running. */
internal suspend fun awaitFilePickerPanelRelease(expanded: StateFlow<Boolean>, closeGeneration: StateFlow<Long>,
    requestedAfter: Long, timeoutMillis: Long = 10_000L): Boolean = withTimeoutOrNull(timeoutMillis) {
    combine(expanded, closeGeneration) { open, generation -> !open && generation > requestedAfter }.first { it }
    true
} ?: false

package com.example.globalminusone.ui.panel.container

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** A folder owns its editing session; opening a child menu must not enable panel editing. */
internal val LocalFolderEditing = compositionLocalOf { false }
internal val LocalFolderIconSizeDp = compositionLocalOf<Int?> { null }
internal val LocalFolderGridSpacing = compositionLocalOf<com.example.globalminusone.data.policy.ContainerIconVerticalSpacing?> { null }
internal val LocalFolderEditAction = staticCompositionLocalOf<(() -> Unit)?> { null }
internal val LocalFolderOpenProperties = staticCompositionLocalOf<((com.example.globalminusone.data.ComponentItem) -> Unit)?> { null }
internal val LocalInContainerChild = compositionLocalOf { false }

/** Tracks a folder temporarily closed for `AddComponentModal` / external pickers so it reopens automatically. */
internal class FolderReturnState {
    var reopenFolderId by mutableStateOf<String?>(null)
        private set
    var triggerToken by mutableIntStateOf(0)
        private set
    var pendingFolderId: String? = null

    fun markPending(folderId: String?) {
        pendingFolderId = folderId
    }

    fun triggerPendingReopen() {
        val id = pendingFolderId
        pendingFolderId = null
        if (id != null) {
            reopenFolderId = id
            triggerToken++
        }
    }

    fun openNow(folderId: String) {
        pendingFolderId = null
        reopenFolderId = folderId
        triggerToken++
    }

    fun consume(folderId: String): Boolean {
        if (reopenFolderId != folderId) return false
        reopenFolderId = null
        return true
    }

    fun clear() {
        pendingFolderId = null
        reopenFolderId = null
    }
}
internal val LocalFolderReturnState = staticCompositionLocalOf<FolderReturnState?> { null }

/** One controller per item, registered by that item's actual application menu. */
internal class FolderItemInteraction {
    var openMenu: (() -> Unit)? = null
    var finishHoldMenu: (() -> Unit)? = null
    var dismissHeldMenu: (() -> Unit)? = null
    var tap: (() -> Unit)? = null
}
internal val LocalFolderItemInteraction = staticCompositionLocalOf<FolderItemInteraction?> { null }

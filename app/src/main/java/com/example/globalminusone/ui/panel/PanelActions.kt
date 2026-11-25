package com.example.globalminusone.ui.panel

import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.LayoutMutationResult

internal data class EmbeddedApplicationTarget(val component: ComponentName, val label: String)

/** Consolidated panel-level UI actions injected once by MinusOneOverlayContainer. */
@Immutable
internal class PanelActions(
    val openBrowserUrl: (String) -> Unit = {},
    val closePanelForExternalApp: (() -> Unit)? = null,
    val openEmbeddedApplication: ((EmbeddedApplicationTarget) -> Unit)? = null,
    val exitPanelEdit: (() -> Unit)? = null,
    val openNewPageModal: (() -> Unit)? = null,
    val addToContainer: ((String) -> Unit)? = null,
    val openFileBrowser: () -> Unit = {},
    val openFileShortcut: (ComponentItem) -> Unit = {},
    val selectImage: (ComponentItem) -> Unit = {}
)

internal val LocalPanelActions = staticCompositionLocalOf { PanelActions() }

/** Acknowledges acceptance; callers can close/show success only after true. */
internal fun reportLayoutMutation(context: Context, result: LayoutMutationResult, successMessage: String? = null): Boolean {
    val message = when (result) {
        LayoutMutationResult.APPLIED -> successMessage
        LayoutMutationResult.TARGET_CHANGED -> "目标已变化或布局已锁定，请重新操作"
        LayoutMutationResult.WRITE_REJECTED -> "更改未应用，请重试"
    }
    message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    return result == LayoutMutationResult.APPLIED
}

package com.example.globalminusone.ui.panel

import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.globalminusone.data.*

internal enum class CardDestructiveAction { DELETE, CLEAR_CHILDREN }
internal data class CardDestructiveRequest(val pageId: String, val componentId: String,
    val title: String, val action: CardDestructiveAction)
internal data class CardDestructiveTarget(val pageIndex: Int, val item: ComponentItem)

/** Keep existing editor callers on the repository's acceptance contract. */
internal typealias ConfirmationWriteResult = LayoutMutationResult

/** Both read models resolve through the same stable-ID/nested-tree rules. */
internal fun resolveCardDestructiveTarget(state: GlobalMinusOneState, request: CardDestructiveRequest): CardDestructiveTarget? =
    resolveCardDestructiveTarget(state.pages, state.advancedConfig.layoutLocked, request)

private fun resolveCardDestructiveTarget(pages: List<PageConfig>, locked: Boolean, request: CardDestructiveRequest): CardDestructiveTarget? {
    if (locked) return null
    val index = pages.indexOfFirst { it.id == request.pageId }
    val item = pages.getOrNull(index)?.components?.findComponent(request.componentId) ?: return null
    if (request.action == CardDestructiveAction.CLEAR_CHILDREN && !item.type.isContainerType()) return null
    return CardDestructiveTarget(index, item)
}

internal fun confirmCardDestructiveAction(actions: CardLayoutActions, request: CardDestructiveRequest): ConfirmationWriteResult {
    val target = resolveCardDestructiveTarget(actions.current, request) ?: return ConfirmationWriteResult.TARGET_CHANGED
    val accepted = if (request.action == CardDestructiveAction.CLEAR_CHILDREN)
        actions.clearContainerActions(target.pageIndex, target.item.id)
    else actions.removeComponent(target.pageIndex, target.item.id)
    return if (accepted) ConfirmationWriteResult.APPLIED else ConfirmationWriteResult.WRITE_REJECTED
}

internal fun clipboardClearStillMatches(records: List<ClipboardRecord>, expected: Set<String>): Boolean =
    expected.isNotEmpty() && records.filterNot { it.pinned }.map { it.id }.toSet() == expected

@Composable
internal fun CardDestructiveConfirmation(request: CardDestructiveRequest, repository: MinusOneRepository,
    onDismiss: () -> Unit) {
    CardDestructiveConfirmationContent(request, rememberCardLayoutActions(repository), onDismiss)
}

@Composable
internal fun CardDestructiveConfirmationContent(request: CardDestructiveRequest, actions: CardLayoutActions,
    onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clearing = request.action == CardDestructiveAction.CLEAR_CHILDREN
    PanelAlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (clearing) "清空子项？" else "删除卡片？") },
        text = { Text(if (clearing) "将清空“${request.title}”内的全部子卡片和旧版操作，无法撤销。"
            else "将删除“${request.title}”及其内部内容，无法撤销。") },
        confirmButton = { TextButton(onClick = {
            when (confirmCardDestructiveAction(actions, request)) {
                ConfirmationWriteResult.APPLIED -> onDismiss()
                ConfirmationWriteResult.TARGET_CHANGED -> Toast.makeText(context, "目标已变化或布局已锁定，请重新操作", Toast.LENGTH_SHORT).show()
                ConfirmationWriteResult.WRITE_REJECTED -> Toast.makeText(context, "更改未应用，请重试", Toast.LENGTH_SHORT).show()
            }
        }) { Text(if (clearing) "清空子项" else "删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
internal fun ClipboardClearConfirmation(expected: Set<String>, repository: MinusOneRepository, onDismiss: () -> Unit) {
    ClipboardClearConfirmationContent(expected, rememberClipboardHistoryActions(repository), onDismiss)
}

@Composable
internal fun ClipboardClearConfirmationContent(expected: Set<String>, history: ClipboardHistoryActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    PanelAlertDialog(onDismissRequest = onDismiss, title = { Text("清空剪贴板历史？") },
        text = { Text("将删除 ${expected.size} 条未固定记录，保留已固定记录。无法撤销。") },
        confirmButton = { TextButton(onClick = {
            when (clearClipboardSelection(history, expected)) {
                ClipboardClearResult.ACCEPTED -> onDismiss()
                ClipboardClearResult.SELECTION_CHANGED -> Toast.makeText(context, "记录已变化，请重新确认清空范围", Toast.LENGTH_SHORT).show()
                ClipboardClearResult.SAVE_REJECTED -> Toast.makeText(context, "更改未应用，请重试", Toast.LENGTH_SHORT).show()
            }
        }) { Text("清空", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

internal fun resolveCardDestructiveTarget(state: CardLayoutSnapshot, request: CardDestructiveRequest): CardDestructiveTarget? =
    resolveCardDestructiveTarget(state.pages, state.layoutLocked, request)

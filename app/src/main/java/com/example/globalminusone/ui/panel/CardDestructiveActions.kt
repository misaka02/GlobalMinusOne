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

/** Resolve by stable identities again at confirmation, including nested children and page reordering. */
internal fun resolveCardDestructiveTarget(state: GlobalMinusOneState, request: CardDestructiveRequest): CardDestructiveTarget? {
    if (state.advancedConfig.layoutLocked) return null
    val index = state.pages.indexOfFirst { it.id == request.pageId }
    val item = state.pages.getOrNull(index)?.components?.findComponent(request.componentId) ?: return null
    if (request.action == CardDestructiveAction.CLEAR_CHILDREN && !item.type.isContainerType()) return null
    return CardDestructiveTarget(index, item)
}

internal fun clipboardClearStillMatches(records: List<ClipboardRecord>, expected: Set<String>): Boolean =
    expected.isNotEmpty() && records.filterNot { it.pinned }.map { it.id }.toSet() == expected

@Composable
internal fun CardDestructiveConfirmation(request: CardDestructiveRequest, repository: MinusOneRepository,
    onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clearing = request.action == CardDestructiveAction.CLEAR_CHILDREN
    PanelAlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (clearing) "清空子项？" else "删除卡片？") },
        text = { Text(if (clearing) "将清空“${request.title}”内的全部子卡片和旧版操作，无法撤销。"
            else "将删除“${request.title}”及其内部内容，无法撤销。") },
        confirmButton = { TextButton(onClick = {
            val target = resolveCardDestructiveTarget(repository.state.value, request)
            if (target == null) Toast.makeText(context, "目标已变化或布局已锁定，请重新操作", Toast.LENGTH_SHORT).show()
            else if (clearing) repository.clearContainerActions(target.pageIndex, target.item.id)
            else repository.removeComponent(target.pageIndex, target.item.id)
            onDismiss()
        }) { Text(if (clearing) "清空子项" else "删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
internal fun ClipboardClearConfirmation(expected: Set<String>, repository: MinusOneRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    PanelAlertDialog(onDismissRequest = onDismiss, title = { Text("清空剪贴板历史？") },
        text = { Text("将删除 ${expected.size} 条未固定记录，保留已固定记录。无法撤销。") },
        confirmButton = { TextButton(onClick = {
            if (clipboardClearStillMatches(repository.state.value.clipboardHistory, expected)) repository.clearUnpinnedClipboard()
            else Toast.makeText(context, "记录已变化，请重新确认清空范围", Toast.LENGTH_SHORT).show()
            onDismiss()
        }) { Text("清空", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

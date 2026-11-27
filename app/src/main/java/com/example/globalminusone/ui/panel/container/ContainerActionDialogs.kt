package com.example.globalminusone.ui.panel.container

import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.*
import kotlin.math.roundToInt
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.policy.ContainerActionGeometry
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.data.policy.commitContainerActionGeometry
import com.example.globalminusone.data.policy.containerActionGeometry
import com.example.globalminusone.data.policy.containerPopupColumns
import com.example.globalminusone.data.policy.containerPopupWidthDp
import com.example.globalminusone.data.operations.findComponent
import com.example.globalminusone.data.isContainerType
import com.example.globalminusone.ui.panel.editor.ConfirmationWriteResult
import com.example.globalminusone.ui.panel.editor.NumberStepper
import com.example.globalminusone.ui.panel.PanelAlertDialog

internal fun mutableContainerTarget(state: GlobalMinusOneState, page: Int, id: String): ComponentItem? =
    if (state.advancedConfig.layoutLocked) null
    else state.pages.getOrNull(page)?.components?.findComponent(id)?.takeIf { it.type.isContainerType() }

@Composable
internal fun EditContainerActionDialog(repository: MinusOneRepository, page: Int, containerId: String, action: QuickActionItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by repository.state.collectAsState()
    val target = mutableContainerTarget(state, page, containerId)
    val existing = target?.containerActions?.firstOrNull { it.id == action.id }
    var title by remember(action.id) { mutableStateOf(action.title) }
    var uri by remember(action.id) { mutableStateOf(action.intentUri) }
    var section by remember(action.id) { mutableStateOf("") }
    val initial = target?.let { parent -> containerActionGeometry(action,
        parent.containerActions.indexOfFirst { it.id == action.id }.coerceAtLeast(0), containerPopupColumns(parent),
        containerPopupWidthDp(parent, 1200f) - 24f, parent.containerItemSizeDp, parent.containerItemGapDp) }
        ?: ContainerActionGeometry(0f, 0f, 88f, 88f)
    var x by remember(action.id) { mutableIntStateOf(initial.x.roundToInt()) }
    var y by remember(action.id) { mutableIntStateOf(initial.y.roundToInt()) }
    var w by remember(action.id) { mutableIntStateOf(initial.width.roundToInt()) }
    var h by remember(action.id) { mutableIntStateOf(initial.height.roundToInt()) }
    var geometryEdited by remember(action.id) { mutableStateOf(false) }
    var resetGeometry by remember(action.id) { mutableStateOf(false) }
    PanelAlertDialog(onDismissRequest = { if (section.isNotEmpty()) section = "" else onDismiss() }, title = { Text("编辑容器子项") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (existing == null) Text("子项已删除或布局已锁定")
            if (section.isNotEmpty()) TextButton(onClick = { section = "" }) { Text("‹ 子项属性") }
            when (section) {
            "" -> {
                TextButton(onClick = { section = "内容" }) { Text("内容：名称与启动动作") }
                TextButton(onClick = { section = "位置与尺寸" }) { Text("自由布局：位置与尺寸") }
            }
            "内容" -> {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("名称") }, singleLine = true)
            OutlinedTextField(value = uri, onValueChange = { uri = it }, label = { Text("启动 Intent / 链接") })
            }
            "位置与尺寸" -> {
                Text("单位 dp；自由布局使用独立坐标，网格模式保留这些值并自动排序。")
                NumberStepper("横坐标", x, { x = it; geometryEdited = true; resetGeometry = false }, maximum = 100000)
                NumberStepper("纵坐标", y, { y = it; geometryEdited = true; resetGeometry = false }, maximum = 100000)
                NumberStepper("宽度", w, { w = it; geometryEdited = true; resetGeometry = false }, minimum = 24, maximum = 1200)
                NumberStepper("高度", h, { h = it; geometryEdited = true; resetGeometry = false }, minimum = 24, maximum = 1200)
                TextButton(onClick = { resetGeometry = true; geometryEdited = false }) { Text(if (resetGeometry) "保存后恢复自动位置" else "恢复自动位置与尺寸") }
            }
            }
        }
    }, confirmButton = { TextButton(enabled = existing != null && title.isNotBlank() && uri.isNotBlank(), onClick = {
        val result = saveLegacyContainerAction(repository, page, containerId, action.id) {
            val content = it.copy(title = title.trim(), intentUri = uri.trim())
            when {
                resetGeometry -> content.copy(xDp = null, yDp = null, wDp = null, hDp = null)
                geometryEdited -> commitContainerActionGeometry(content, x, y, w, h)
                else -> content
            }
        }
        when (result) {
            ConfirmationWriteResult.APPLIED -> onDismiss()
            ConfirmationWriteResult.TARGET_CHANGED -> Toast.makeText(context, "子项已删除或布局已锁定", Toast.LENGTH_SHORT).show()
            ConfirmationWriteResult.WRITE_REJECTED -> Toast.makeText(context, "更改未应用，请重试", Toast.LENGTH_SHORT).show()
        }
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

/** Resolve again at save time; rejected writes leave the caller's draft intact for retry. */
internal fun saveLegacyContainerAction(repository: MinusOneRepository, page: Int, containerId: String,
    actionId: String, transform: (QuickActionItem) -> QuickActionItem): ConfirmationWriteResult {
    val current = mutableContainerTarget(repository.state.value, page, containerId)
    if (current == null || current.containerActions.none { it.id == actionId }) return ConfirmationWriteResult.TARGET_CHANGED
    val accepted = repository.tryUpdateContainerAction(page, containerId, actionId, transform)
    return if (accepted) ConfirmationWriteResult.APPLIED else ConfirmationWriteResult.WRITE_REJECTED
}

package com.example.globalminusone.ui.panel.editor

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.operations.SubPanelConfig
import com.example.globalminusone.data.operations.SubPanelTemplate
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelAlertDialog
import com.example.globalminusone.ui.panel.PanelPalette

@Composable
internal fun SubPanelLibraryModal(
    repository: MinusOneRepository,
    pickerMode: Boolean,
    onSelectTemplate: ((SubPanelTemplate) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalPanelPalette.current
    val state by repository.state.collectAsState()
    val library = state.subPanelLibrary
    val surface = if (palette.isDark) Color(0xFF1E222B) else Color(0xFFFFFFFF)
    var renamingTemplate by remember { mutableStateOf<SubPanelTemplate?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deletingTemplate by remember { mutableStateOf<SubPanelTemplate?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(min = 200.dp, max = 480.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) }
                },
            shape = RoundedCornerShape(18.dp),
            color = surface,
            border = BorderStroke(1.dp, palette.cardBorder),
            shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (pickerMode) "从子面板仓库选择" else "子面板仓库管理",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary
                        )
                        Text(
                            if (pickerMode) "选中模板后将按模板配置与分页新建子面板并关联"
                            else "共 ${library.size} 个子面板模板，可在子面板配置中随时存入",
                            fontSize = 11.sp, color = palette.textSecondary
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }

                if (library.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "仓库暂无子面板模板。\n请先进入任意子面板右上/左上设置，点击「存入子面板仓库」。",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(library, key = { it.id }) { tpl ->
                            val geomText = if (tpl.config.inheritGeometry) "继承尺寸"
                            else "${tpl.config.panelWidthPercent}%×${tpl.config.panelHeightPercent}%"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = palette.cardBackground.copy(alpha = 0.72f),
                                border = BorderStroke(0.8.dp, palette.cardBorder),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = pickerMode) {
                                    onSelectTemplate?.invoke(tpl)
                                }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            tpl.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = palette.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${tpl.pages.size} 页 · ${tpl.cardCount()} 个组件 · $geomText",
                                            fontSize = 11.sp,
                                            color = palette.textSecondary
                                        )
                                    }
                                    if (pickerMode) {
                                        TextButton(onClick = { onSelectTemplate?.invoke(tpl) }) {
                                            Text("选用", fontSize = 12.sp)
                                        }
                                    }
                                    IconButton(
                                        onClick = { renamingTemplate = tpl; renameText = tpl.name },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Rounded.Edit, "重命名", Modifier.size(16.dp), tint = palette.textSecondary)
                                    }
                                    IconButton(
                                        onClick = { deletingTemplate = tpl },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Rounded.Delete, "删除模板", Modifier.size(16.dp), tint = Color(0xFFE53935))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renamingTemplate?.let { target ->
        PanelAlertDialog(
            onDismissRequest = { renamingTemplate = null },
            title = { Text("重命名子面板模板") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("模板名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = repository.renameSubPanelTemplate(target.id, renameText)
                    if (!ok) Toast.makeText(context, "名称不能为空", Toast.LENGTH_SHORT).show()
                    else renamingTemplate = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renamingTemplate = null }) { Text("取消") } }
        )
    }

    deletingTemplate?.let { target ->
        PanelAlertDialog(
            onDismissRequest = { deletingTemplate = null },
            title = { Text("删除模板「${target.name}」？") },
            text = { Text("仅从子面板仓库移除该模板，已创建的子面板不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteSubPanelTemplate(target.id)
                    deletingTemplate = null
                }) { Text("删除", color = Color(0xFFE53935)) }
            },
            dismissButton = { TextButton(onClick = { deletingTemplate = null }) { Text("取消") } }
        )
    }
}

@Composable
internal fun SubPanelEntryLinkSection(
    draft: ComponentEditDraft,
    repository: MinusOneRepository,
    palette: PanelPalette,
    modalTrack: Color,
    modalTextPrimary: Color,
    modalTextSecondary: Color,
    syncLive: () -> Unit
) {
    val context = LocalContext.current
    val liveState by repository.state.collectAsState()
    val currentSub = liveState.subPanels.firstOrNull { it.id == draft.subPanelId }
    var showLibraryPicker by remember { mutableStateOf(false) }

    Text("关联子面板", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Text(
        currentSub?.let {
            val geom = if (it.inheritGeometry) "继承主面板尺寸" else "${it.panelWidthPercent}%×${it.panelHeightPercent}%"
            "当前指向：${it.name}（$geom）"
        } ?: "未关联子面板，可连接已有子面板、新建空白或从仓库选用。",
        color = modalTextSecondary,
        fontSize = 11.sp
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        liveState.subPanels.forEach { sp ->
            val selected = draft.subPanelId == sp.id
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selected) palette.accent else modalTrack,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    draft.subPanelId = sp.id
                    if (draft.titleText == "子面板" || draft.titleText.isBlank()) draft.titleText = sp.name
                    syncLive()
                }
            ) {
                Text(
                    sp.name,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color.White else modalTextPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = palette.accent.copy(alpha = 0.16f),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                val created = repository.createSubPanel("子面板")
                if (created != null) {
                    draft.subPanelId = created.id
                    if (draft.titleText == "子面板" || draft.titleText.isBlank()) draft.titleText = created.name
                    syncLive()
                } else {
                    Toast.makeText(context, "编辑未解锁或已达子面板上限", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Text(
                "＋新建空白",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.accent,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = palette.accent.copy(alpha = 0.12f),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showLibraryPicker = true }
        ) {
            Text(
                "从仓库选择 (${liveState.subPanelLibrary.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.accent,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }

    if (showLibraryPicker) {
        SubPanelLibraryModal(
            repository = repository,
            pickerMode = true,
            onSelectTemplate = { tpl ->
                val created = repository.createSubPanelFromLibrary(tpl.id)
                if (created != null) {
                    draft.subPanelId = created.id
                    if (draft.titleText == "子面板" || draft.titleText.isBlank()) draft.titleText = created.name
                    syncLive()
                    Toast.makeText(context, "已从仓库创建并关联「${created.name}」", Toast.LENGTH_SHORT).show()
                    showLibraryPicker = false
                } else {
                    Toast.makeText(context, "创建失败，请检查锁定状态或数量上限", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showLibraryPicker = false }
        )
    }
}

@Composable
internal fun SubPanelAddEntrySubPage(
    repository: MinusOneRepository,
    palette: PanelPalette,
    primaryText: Color,
    secondaryText: Color,
    itemBg: Color,
    itemBorderColor: Color,
    onAddEntry: (ComponentItem, String) -> Unit
) {
    val context = LocalContext.current
    val liveState by repository.state.collectAsState()
    var showManageLibrary by remember { mutableStateOf(false) }

    fun createBlank() {
        val created = repository.createSubPanel("子面板")
        if (created != null) {
            onAddEntry(
                ComponentItem(
                    type = ComponentType.SUB_PANEL_ENTRY,
                    title = created.name,
                    subPanelId = created.id,
                    w = ComponentType.SUB_PANEL_ENTRY.defaultW,
                    h = ComponentType.SUB_PANEL_ENTRY.defaultH
                ),
                "已新建并添加子面板「${created.name}」"
            )
        } else {
            Toast.makeText(context, "编辑未解锁或已达子面板上限", Toast.LENGTH_SHORT).show()
        }
    }

    fun linkExisting(sp: SubPanelConfig) {
        onAddEntry(
            ComponentItem(
                type = ComponentType.SUB_PANEL_ENTRY,
                title = sp.name,
                subPanelId = sp.id,
                w = ComponentType.SUB_PANEL_ENTRY.defaultW,
                h = ComponentType.SUB_PANEL_ENTRY.defaultH
            ),
            "已添加入口「${sp.name}」"
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilledTonalButton(onClick = ::createBlank, modifier = Modifier.fillMaxWidth()) {
                Text("＋ 新建空白子面板并添加入口")
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("从子面板仓库选用（${liveState.subPanelLibrary.size}）", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.accent)
                if (liveState.subPanelLibrary.isNotEmpty()) {
                    Text(
                        "管理仓库",
                        fontSize = 11.sp,
                        color = palette.accent,
                        modifier = Modifier.clickable { showManageLibrary = true }.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (liveState.subPanelLibrary.isEmpty()) {
            item {
                Text("仓库暂无模板，可在子面板配置弹窗点击「存入子面板仓库」。", fontSize = 11.sp, color = secondaryText)
            }
        } else {
            items(liveState.subPanelLibrary, key = { "lib_${it.id}" }) { tpl ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        val created = repository.createSubPanelFromLibrary(tpl.id)
                        if (created != null) {
                            onAddEntry(
                                ComponentItem(
                                    type = ComponentType.SUB_PANEL_ENTRY,
                                    title = created.name,
                                    subPanelId = created.id,
                                    w = ComponentType.SUB_PANEL_ENTRY.defaultW,
                                    h = ComponentType.SUB_PANEL_ENTRY.defaultH
                                ),
                                "已从仓库添加子面板「${created.name}」"
                            )
                        } else {
                            Toast.makeText(context, "创建失败，请检查锁定状态或数量上限", Toast.LENGTH_SHORT).show()
                        }
                    },
                    color = itemBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, itemBorderColor)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tpl.name, color = primaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("${tpl.pages.size} 页 · ${tpl.cardCount()} 个组件", color = secondaryText, fontSize = 11.sp)
                        }
                        Text("选用创建", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (liveState.subPanels.isNotEmpty()) {
            item {
                Text(
                    "连接到已有子面板（${liveState.subPanels.size}）",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(liveState.subPanels, key = { "existing_${it.id}" }) { sp ->
                val pageCount = liveState.pages.count { it.subPanelId == sp.id }
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { linkExisting(sp) },
                    color = itemBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, itemBorderColor)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sp.name, color = primaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("已有子面板 · $pageCount 页", color = secondaryText, fontSize = 11.sp)
                        }
                        Text("直接连接", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showManageLibrary) {
        SubPanelLibraryModal(
            repository = repository,
            pickerMode = false,
            onDismiss = { showManageLibrary = false }
        )
    }
}

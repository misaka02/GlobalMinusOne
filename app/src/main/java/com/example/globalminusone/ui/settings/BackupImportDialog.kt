package com.example.globalminusone.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.backup.*

@Composable
internal fun BackupExportOptionsDialog(
    state: GlobalMinusOneState,
    onDismiss: () -> Unit,
    onConfirm: (BackupExportMode, Set<String>) -> Unit
) {
    var mode by remember { mutableStateOf(BackupExportMode.FULL) }
    var selectedPages by remember(state.pages) {
        mutableStateOf(
            setOfNotNull(state.pages.getOrNull(state.currentPageIndex)?.id ?: state.pages.firstOrNull()?.id)
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择导出范围") },
        text = {
            Column(
                Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BackupExportMode.values().forEach { candidate ->
                    Row(
                        Modifier.fillMaxWidth().clickable { mode = candidate }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == candidate, onClick = { mode = candidate })
                        Text(candidate.label, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                if (mode == BackupExportMode.SELECTED_PAGES) {
                    HorizontalDivider()
                    Text(
                        "勾选要导出的页面（含关联子面板与图片资源）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    state.pages.forEach { page ->
                        val checked = page.id in selectedPages
                        val scopeTag = if (page.subPanelId.isEmpty()) "主面板" else "子面板"
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selectedPages = if (checked) selectedPages - page.id else selectedPages + page.id
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on -> selectedPages = if (on) selectedPages + page.id else selectedPages - page.id }
                            )
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(page.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "$scopeTag · ${page.layoutType.label} · ${page.components.size} 卡片",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = mode != BackupExportMode.SELECTED_PAGES || selectedPages.isNotEmpty(),
                onClick = { onConfirm(mode, selectedPages) }
            ) { Text("导出为 ZIP") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
internal fun BackupRestoreOptionsDialog(
    backup: DecodedNativeBackup,
    currentState: GlobalMinusOneState,
    busy: Boolean,
    restoreAllowed: Boolean,
    defaultPageAppendOnly: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (BackupImportSelection) -> Unit
) {
    val incoming = backup.payload.state
    val isSettingsOnly = backup.payload.isSettingsOnlyExport()
    var fullReplace by remember(backup, defaultPageAppendOnly) { mutableStateOf(!defaultPageAppendOnly) }
    var importTheme by remember(backup, defaultPageAppendOnly) { mutableStateOf(!defaultPageAppendOnly) }
    var importTriggers by remember(backup, defaultPageAppendOnly) { mutableStateOf(!defaultPageAppendOnly) }
    var importBrowser by remember(backup, defaultPageAppendOnly) { mutableStateOf(!defaultPageAppendOnly && !backup.payload.configurationOnly) }
    var importProfiles by remember(backup, defaultPageAppendOnly) { mutableStateOf(!defaultPageAppendOnly) }
    var selectedPages by remember(backup, isSettingsOnly) {
        mutableStateOf(if (isSettingsOnly) emptySet() else incoming.pages.map { it.id }.toSet())
    }
    var pageImportMode by remember(backup, defaultPageAppendOnly) {
        mutableStateOf(
            when {
                isSettingsOnly || defaultPageAppendOnly -> PageImportMode.APPEND
                incoming.pages.size == 1 && currentState.pages.size > 1 -> PageImportMode.APPEND
                else -> PageImportMode.REPLACE_ALL
            }
        )
    }
    var applySourceGeometry by remember(backup, defaultPageAppendOnly) { mutableStateOf(!defaultPageAppendOnly) }

    val compatibilityIssues = remember(incoming, currentState, selectedPages, fullReplace, isSettingsOnly) {
        val checkedPages = when {
            isSettingsOnly -> incoming.pages.take(0).map { it.id }.toSet()
            fullReplace -> incoming.pages.map { it.id }.toSet()
            else -> selectedPages
        }
        if (isSettingsOnly && checkedPages.isEmpty()) analyzeBackupCompatibility(incoming, currentState, setOf("__settings_only__"))
        else analyzeBackupCompatibility(incoming, currentState, checkedPages)
    }
    LaunchedEffect(compatibilityIssues) {
        if (compatibilityIssues.isNotEmpty()) applySourceGeometry = false
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("确认导入备份") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(true to "一键完整导入", false to "自定义部分/单页导入").forEach { (isFull, label) ->
                        FilterChip(
                            selected = fullReplace == isFull,
                            onClick = { fullReplace = isFull },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(
                    if (isSettingsOnly) {
                        "此备份仅包含主题外观、手势与方案设置；导入时将完整保留您现有的全部页面与子面板。"
                    } else {
                        "备份内容：${incoming.pages.size} 页、${incoming.profiles.size} 个方案、${backup.assets.size} 个图片文件；备份面板尺寸 ${incoming.themeConfig.panelWidthPercent}%×${incoming.themeConfig.panelHeightPercent}%。"
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                if (compatibilityIssues.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFF4E5),
                        border = BorderStroke(1.dp, Color(0xFFE6A23C))
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("面板尺寸/网格不适配提醒", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8A5300))
                            compatibilityIssues.forEach { issue ->
                                Text("• ${issue.title}：${issue.detail}", fontSize = 11.sp, color = Color(0xFF6E4700))
                            }
                        }
                    }
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { applySourceGeometry = false },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = !applySourceGeometry, onClick = { applySourceGeometry = false })
                            Text("保留当前设备的面板尺寸与位置", fontSize = 12.sp)
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { applySourceGeometry = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = applySourceGeometry, onClick = { applySourceGeometry = true })
                            Text("同步采用备份中的面板尺寸与位置", fontSize = 12.sp)
                        }
                    }
                }

                if (!fullReplace) {
                    HorizontalDivider()
                    Text("导入模块", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    ModuleCheckbox("主题外观与面板风格", importTheme) { importTheme = it }
                    ModuleCheckbox("边缘手势区与悬浮球配置", importTriggers) { importTriggers = it }
                    if (!backup.payload.configurationOnly) {
                        ModuleCheckbox("浏览器书签 / 历史 / 设置", importBrowser) { importBrowser = it }
                    }
                    ModuleCheckbox("配置方案库", importProfiles) { importProfiles = it }

                    if (!isSettingsOnly) {
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "选择导入页面（${selectedPages.size}/${incoming.pages.size}）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row {
                                TextButton(
                                    onClick = { selectedPages = incoming.pages.map { it.id }.toSet() }
                                ) { Text("全选", fontSize = 11.sp) }
                                TextButton(onClick = { selectedPages = emptySet() }) { Text("全不选", fontSize = 11.sp) }
                            }
                        }
                        if (selectedPages.isNotEmpty()) {
                            PageImportMode.values().forEach { m ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { pageImportMode = m },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = pageImportMode == m, onClick = { pageImportMode = m })
                                    Text(m.label, fontSize = 12.sp)
                                }
                            }
                        }
                        incoming.pages.forEach { page ->
                            PageSelectRow(page, page.id in selectedPages) { checked ->
                                selectedPages = if (checked) selectedPages + page.id else selectedPages - page.id
                            }
                        }
                    }
                }

                backup.payload.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            val hasSelectedModules = fullReplace || importTheme || importTriggers ||
                importBrowser || importProfiles || (!isSettingsOnly && selectedPages.isNotEmpty())
            TextButton(
                enabled = !busy && restoreAllowed && hasSelectedModules,
                onClick = {
                    val sel = if (fullReplace) {
                        BackupImportSelection(
                            importThemeAndPanel = true,
                            importTriggersAndBall = true,
                            importBrowserData = !backup.payload.configurationOnly,
                            importProfiles = true,
                            selectedPageIds = if (isSettingsOnly) emptySet() else incoming.pages.map { it.id }.toSet(),
                            pageImportMode = if (isSettingsOnly) PageImportMode.APPEND else PageImportMode.REPLACE_ALL,
                            applySourcePanelGeometry = applySourceGeometry,
                            restoreFullUserData = backup.payload.isFullBackupExport()
                        )
                    } else {
                        BackupImportSelection(
                            importThemeAndPanel = importTheme,
                            importTriggersAndBall = importTriggers,
                            importBrowserData = importBrowser && !backup.payload.configurationOnly,
                            importProfiles = importProfiles,
                            selectedPageIds = if (isSettingsOnly) emptySet() else selectedPages,
                            pageImportMode = if (isSettingsOnly) PageImportMode.APPEND else pageImportMode,
                            applySourcePanelGeometry = applySourceGeometry,
                            restoreFullUserData = false
                        )
                    }
                    onConfirm(sel)
                }
            ) { Text(if (fullReplace) "一键替换" else "导入所选项") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ModuleCheckbox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun PageSelectRow(page: PageConfig, checked: Boolean, onChange: (Boolean) -> Unit) {
    val scope = if (page.subPanelId.isEmpty()) "主面板" else "子面板"
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.padding(start = 4.dp)) {
            Text(page.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("$scope · ${page.layoutType.label} · ${page.components.size} 卡片", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

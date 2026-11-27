package com.example.globalminusone.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.*
import com.example.globalminusone.data.panels.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.operations.findComponent
import com.example.globalminusone.data.operations.flattenComponents
import com.example.globalminusone.data.isContainerType

/** Selection/preview is read-only. Only a confirmed append installs the chosen assets. */
@Composable
internal fun PanelsImportEntry(repository: MinusOneRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    var archive by remember { mutableStateOf<PanelsImportArchive?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; message = null
            try {
                archive = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(PanelsBackupImporter::parse)
                        ?: error("无法读取所选文件")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = failure.message ?: "Panels 配置读取失败" }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("迁移 Panels 布局", style = MaterialTheme.typography.titleMedium)
        Text("选择 .bkp，预览后可将整页导入为独立页面、网格容器或网格文件夹，也可导入单个文件夹。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) { Text(if (busy) "正在读取…" else "选择 Panels 配置文件") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
    archive?.let { data ->
        PanelsImportHostDialog(
            archive = data,
            repository = repository,
            onDismiss = { archive = null },
            onImported = { resultMessage ->
                message = resultMessage
                archive = null
            }
        )
    }
}

@Composable
internal fun PanelsImportHostDialog(
    archive: PanelsImportArchive,
    repository: MinusOneRepository,
    bannerHint: String? = null,
    onDismiss: () -> Unit,
    onImported: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    var busy by remember(archive) { mutableStateOf(false) }
    var dialogError by remember(archive) { mutableStateOf<String?>(null) }
    PanelsImportDialog(
        archive = archive,
        state = state,
        busy = busy,
        bannerHint = bannerHint,
        errorMessage = dialogError,
        onDismiss = { if (!busy) onDismiss() },
        onConfirm = { selection ->
            scope.launch {
                busy = true
                dialogError = null
                try {
                    val imported = withContext(Dispatchers.IO) {
                        PanelsIconStaging(context.filesDir, archive.iconAssets).use { icons ->
                            val columns = repository.state.value.advancedConfig.gridColumns
                            var importedCount = 0
                            val applied = if (selection.folderId != null) {
                                val folder = archive.convertFolder(selection.folderId, iconUriFor = icons::uriFor, gridColumns = columns)
                                importedCount = 1
                                repository.appendImportedFolder(folder, selection.targetPageId, selection.targetContainerId)
                            } else if (selection.pageMode == PanelsPageImportMode.STANDALONE_PAGE) {
                                val pages = archive.pages.filter { it.id in selection.pageIds }.map {
                                    archive.convertPage(it.id, iconUriFor = icons::uriFor, gridColumns = columns)
                                }
                                importedCount = pages.size
                                repository.appendImportedPages(pages)
                            } else {
                                val asFolder = selection.pageMode == PanelsPageImportMode.GRID_FOLDER
                                val containers = archive.pages.filter { it.id in selection.pageIds }.map {
                                    archive.convertPageAsContainer(it.id, asFolder = asFolder, iconUriFor = icons::uriFor, gridColumns = columns)
                                }
                                importedCount = containers.size
                                repository.appendImportedContainers(containers, selection.targetPageId, selection.targetContainerId)
                            }
                            check(applied) { "未导入：布局被锁定、目标已失效或保存失败，原配置保留" }
                            icons.commit()
                            when {
                                selection.folderId != null -> "已添加文件夹，可在目标页面查看"
                                selection.pageMode == PanelsPageImportMode.STANDALONE_PAGE -> "已追加 $importedCount 页，可在面板翻页查看"
                                selection.pageMode == PanelsPageImportMode.GRID_CONTAINER -> "已导入 $importedCount 个网格容器，可在目标页面查看"
                                else -> "已导入 $importedCount 个网格文件夹，可在目标页面查看"
                            }
                        }
                    }
                    Toast.makeText(context, imported, Toast.LENGTH_SHORT).show()
                    onImported(imported)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    dialogError = failure.message ?: "导入未完成，请检查恢复备份与存储空间"
                } finally {
                    busy = false
                }
            }
        }
    )
}

private data class PanelsSelection(
    val pageIds: Set<Int>,
    val folderId: Int?,
    val targetPageId: String,
    val targetContainerId: String?,
    val pageMode: PanelsPageImportMode = PanelsPageImportMode.STANDALONE_PAGE
)

@Composable
private fun PanelsImportDialog(archive: PanelsImportArchive, state: GlobalMinusOneState, busy: Boolean,
    bannerHint: String? = null, errorMessage: String?,
    onDismiss: () -> Unit, onConfirm: (PanelsSelection) -> Unit) {
    var folderMode by remember { mutableStateOf(false) }
    var pageImportMode by remember { mutableStateOf(PanelsPageImportMode.STANDALONE_PAGE) }
    var pages by remember(archive) { mutableStateOf(setOfNotNull(archive.pages.firstOrNull()?.id)) }
    var folderId by remember(archive) { mutableStateOf(archive.folders.firstOrNull()?.id) }
    var targetPageId by remember(archive) {
        mutableStateOf(state.pages.getOrElse(state.currentPageIndex) { state.pages.first() }.id)
    }
    var targetContainerId by remember(archive) { mutableStateOf<String?>(null) }
    var chooseTarget by remember { mutableStateOf(false) }
    var warningsExpanded by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf("") }
    var previewValid by remember { mutableStateOf(false) }
    val target = state.pages.firstOrNull { it.id == targetPageId }
    val container = targetContainerId?.let { target?.components?.findComponent(it) }
    val needsTarget = folderMode || pageImportMode != PanelsPageImportMode.STANDALONE_PAGE
    LaunchedEffect(archive, folderMode, pageImportMode, pages, folderId, target?.name, container?.title, state.advancedConfig.gridColumns) {
        previewValid = false
        try {
            preview = withContext(Dispatchers.Default) {
                val cols = state.advancedConfig.gridColumns
                when {
                    folderMode -> {
                        val roots = listOfNotNull(folderId?.let { archive.convertFolder(it, gridColumns = cols) })
                        require(roots.isNotEmpty()) { "请选择要导入的文件夹" }
                        val all = roots.flattenComponents()
                        "将追加 1 个文件夹、${all.size} 个卡片（含嵌套）。\n预览：${all.take(6).joinToString("、") { it.title }}"
                    }
                    pageImportMode == PanelsPageImportMode.STANDALONE_PAGE -> {
                        val selected = archive.pages.filter { it.id in pages }
                        require(selected.isNotEmpty()) { "请选择要导入的页面" }
                        val roots = selected.flatMap { archive.convertPage(it.id, gridColumns = cols).components }
                        val all = roots.flattenComponents()
                        "将追加 ${selected.size} 页、${all.size} 个卡片（含嵌套）。\n预览：${all.take(6).joinToString("、") { it.title }.ifBlank { "空页面" }}"
                    }
                    else -> {
                        val selected = archive.pages.filter { it.id in pages }
                        require(selected.isNotEmpty()) { "请选择要导入的页面" }
                        val asFolder = pageImportMode == PanelsPageImportMode.GRID_FOLDER
                        val roots = selected.map { archive.convertPageAsContainer(it.id, asFolder = asFolder, gridColumns = cols) }
                        val all = roots.flattenComponents()
                        val kindLabel = if (asFolder) "网格文件夹" else "网格容器"
                        val destLabel = "${target?.name ?: "目标页面"} / ${container?.title ?: "页顶层"}"
                        "将在「$destLabel」追加 ${roots.size} 个$kindLabel（共 ${all.size} 个卡片，含子项）。\n预览：${roots.joinToString("、") { "${it.title}(${it.containerColumns}列)" }}"
                    }
                }
            }
            previewValid = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { preview = failure.message ?: "无法转换所选布局" }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = {
            com.example.globalminusone.ui.Android12WindowKeyGuard()
            Text("导入 Panels 布局")
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                bannerHint?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                Text("${archive.pages.size} 页 · ${archive.folders.size} 个文件夹 · ${archive.itemCount} 个原始条目")
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = !folderMode, onClick = { if (!busy) folderMode = false }, label = { Text("整页（可多选）", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) })
                    FilterChip(selected = folderMode, onClick = { if (!busy) folderMode = true }, label = { Text("单个文件夹", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) })
                }
                if (!folderMode) {
                    Text("整页导入方式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PanelsPageImportMode.entries.forEach { mode ->
                            FilterChip(
                                selected = pageImportMode == mode,
                                onClick = { if (!busy) pageImportMode = mode },
                                label = { Text(mode.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                    TextButton(onClick = { pages = if (pages.size == archive.pages.size) emptySet() else archive.pages.map { it.id }.toSet() }, enabled = !busy) { Text("全选 / 清除") }
                    archive.pages.forEach { page ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { pages = if (page.id in pages) pages - page.id else pages + page.id }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(page.id in pages, onCheckedChange = null)
                            Text("组${page.setId} · ${page.title} #${page.id}\n${page.columns}列 · ${page.itemCount}项${if (page.isAppDrawer) " · 应用抽屉" else ""}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    archive.folders.forEach { folder ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { folderId = folder.id }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(folder.id == folderId, onClick = null)
                            Text("${folder.title} #${folder.id} · ${folder.itemCount}项\n来源页${folder.panelId} · ${folder.columns}列", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (needsTarget) {
                    HorizontalDivider()
                    Text("添加到：${target?.name ?: "目标页面已失效"} / ${container?.title ?: "页顶层"}")
                    TextButton(onClick = { chooseTarget = !chooseTarget }, enabled = !busy) { Text(if (chooseTarget) "收起目标位置" else "选择目标位置") }
                    if (chooseTarget) {
                        state.pages.forEach { page -> TextButton(onClick = { targetPageId = page.id; targetContainerId = null }, enabled = !busy) { Text("${if (page.id == targetPageId) "✓ " else ""}${page.name}") } }
                        target?.let { page ->
                            val topLabel = when {
                                folderMode || pageImportMode == PanelsPageImportMode.GRID_FOLDER -> "添加为页面顶层文件夹"
                                else -> "添加为页面顶层网格容器"
                            }
                            TextButton(onClick = { targetContainerId = null }, enabled = !busy) { Text(topLabel) }
                            page.components.flattenComponents().filter { it.type.isContainerType() }.forEach { parent ->
                                TextButton(onClick = { targetContainerId = parent.id }, enabled = !busy) { Text("${if (parent.id == targetContainerId) "✓ " else ""}容器：${parent.title} · ${parent.id.takeLast(5)}") }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text(preview, style = MaterialTheme.typography.bodySmall)
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (archive.warnings.isNotEmpty()) {
                    TextButton(onClick = { warningsExpanded = !warningsExpanded }) { Text("兼容说明 · ${archive.warnings.size} 项") }
                    if (warningsExpanded) archive.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                Text("缺失应用保留条目；系统小部件需在本应用重新绑定。源应用的触发器、权限和手势不会被执行或覆盖。", style = MaterialTheme.typography.bodySmall)
                if (state.advancedConfig.layoutLocked) Text("当前布局已锁定，请先解锁再导入", color = MaterialTheme.colorScheme.error)
                if (state.editMode) Text("请先保存或取消当前编辑，再导入", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            onConfirm(PanelsSelection(pages, if (folderMode) folderId else null, targetPageId, targetContainerId, pageImportMode))
        },
            enabled = !busy && previewValid && !state.advancedConfig.layoutLocked && !state.editMode &&
                (!folderMode || folderId != null) && (!needsTarget || (target != null && (targetContainerId == null || container != null)))) {
            Text(if (busy) "正在导入…" else "确认追加")
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
}

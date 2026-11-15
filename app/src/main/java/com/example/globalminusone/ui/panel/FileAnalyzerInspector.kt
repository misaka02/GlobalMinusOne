package com.example.globalminusone.ui.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.system.*
import com.example.globalminusone.ui.AppIcons as Icons
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AnalyzerInspectorSheet(
    entry: BrowserEntry,
    root: BrowserLocation,
    properties: FileProperties?,
    checksums: FileChecksums?,
    hashCompareInput: String,
    onHashCompareChange: (String) -> Unit,
    writable: Boolean,
    busy: Boolean,
    externalBusy: Boolean,
    renameMode: Boolean,
    renameInput: String,
    onRenameInputChange: (String) -> Unit,
    onToggleRename: () -> Unit,
    onConfirmRename: () -> Unit,
    deleteConfirmMode: Boolean,
    onToggleDeleteConfirm: () -> Unit,
    onConfirmDelete: () -> Unit,
    onPreview: () -> Unit,
    onOpenParentDirectory: () -> Unit,
    onOpenExternal: (Boolean) -> Unit,
    onFavorite: () -> Unit,
    onSelectForBatch: () -> Unit,
    onComputeChecksums: () -> Unit,
    onCopyNotice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = opaqueFilePalette(LocalPanelPalette.current)
    val clipManager = remember(context) {
        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    }
    fun copyText(label: String, text: String) {
        clipManager?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        onCopyNotice("已复制 $label")
    }
    val fullLocation = properties?.location
        ?: entry.location.path
        ?: entry.location.documentId
        ?: entry.location.singleDocumentUri
        ?: entry.location.title
    val relParent = remember(root.key, entry.location.key, entry.parentLocation?.key) {
        relativeAnalyzerParentLabel(root, entry)
    }
    val knownBytes = properties?.size ?: entry.size
    val modifiedMillis = properties?.modified ?: entry.modified
    val categoryLabel = FileCategory.of(entry).label

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.86f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = palette.cardBackground,
            contentColor = palette.textPrimary,
            shadowElevation = 10.dp
        ) {
            Column {
                // Sheet Header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileWorkspaceEntryGlyph(entry, compact = true)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = entry.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$categoryLabel · ${fileBytes(knownBytes)} · 位于 $relParent",
                            fontSize = 10.5.sp,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Close, "关闭文件详情", Modifier.size(20.dp), tint = palette.textSecondary)
                    }
                }
                HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.4f))

                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: Primary Open / Locate / Share Actions
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InspectorQuickActionButton(
                            title = "内置预览",
                            icon = Icons.Visibility,
                            enabled = !busy,
                            accent = true,
                            modifier = Modifier.weight(1f),
                            onClick = onPreview
                        )
                        InspectorQuickActionButton(
                            title = "所在目录",
                            icon = Icons.Folder,
                            enabled = !busy,
                            accent = true,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenParentDirectory
                        )
                        InspectorQuickActionButton(
                            title = if (entry.name.endsWith(".apk", ignoreCase = true)) "安装/外部" else "外部打开",
                            icon = Icons.OpenInNew,
                            enabled = !busy && !externalBusy && !entry.location.rootAccess,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenExternal(false) }
                        )
                        InspectorQuickActionButton(
                            title = "分享",
                            icon = Icons.Share,
                            enabled = !busy && !externalBusy && !entry.location.rootAccess,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenExternal(true) }
                        )
                    }

                    // Row 2: Management Actions (Rename, Delete, Favorite, Batch Select)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InspectorQuickActionButton(
                            title = "重命名",
                            icon = Icons.Edit,
                            enabled = !busy && writable,
                            active = renameMode,
                            modifier = Modifier.weight(1f),
                            onClick = onToggleRename
                        )
                        InspectorQuickActionButton(
                            title = "删除文件",
                            icon = Icons.Delete,
                            enabled = !busy && writable,
                            destructive = true,
                            active = deleteConfirmMode,
                            modifier = Modifier.weight(1f),
                            onClick = onToggleDeleteConfirm
                        )
                        InspectorQuickActionButton(
                            title = "收藏",
                            icon = Icons.Checklist,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            onClick = onFavorite
                        )
                        InspectorQuickActionButton(
                            title = "加入多选",
                            icon = Icons.Check,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            onClick = onSelectForBatch
                        )
                    }

                    // Inline Rename Box
                    if (renameMode) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = palette.accent.copy(alpha = 0.08f),
                            border = BorderStroke(0.8.dp, palette.accent.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("重命名文件（原目录下直接改名，不重新扫描）", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = renameInput,
                                    onValueChange = onRenameInputChange,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("新文件名", fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { onConfirmRename() })
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = onToggleRename, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消", fontSize = 12.sp) }
                                    FilledTonalButton(
                                        onClick = onConfirmRename,
                                        enabled = !busy && renameInput.isNotBlank() && renameInput.trim() != entry.name,
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) { Text("确认改名", fontSize = 12.sp) }
                                }
                            }
                        }
                    }

                    // Inline Delete Confirmation Box
                    if (deleteConfirmMode) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "确认永久删除「${entry.name}」？",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "位置：$relParent · 释放空间：${fileBytes(knownBytes)}。删除不可撤销，统计数据将立即同步。",
                                    fontSize = 11.sp,
                                    color = palette.textSecondary
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = onToggleDeleteConfirm, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消", fontSize = 12.sp) }
                                    TextButton(onClick = onConfirmDelete, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("确认删除", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    // Detailed Metadata Card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("文件详细信息", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = { copyText("文件路径", fullLocation) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Icon(Icons.ContentCopy, null, Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("复制路径", fontSize = 11.sp)
                                }
                            }
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("名称：${entry.name}", fontSize = 11.5.sp)
                                    Text("类型：$categoryLabel（${properties?.type ?: entry.mimeType}）", fontSize = 11.5.sp, color = palette.textSecondary)
                                    val exactSizeSuffix = knownBytes?.let { "（${String.format(Locale.ROOT, "%,d", it)} 字节）" }.orEmpty()
                                    Text("大小：${fileBytes(knownBytes)}$exactSizeSuffix", fontSize = 11.5.sp, color = palette.textSecondary)
                                    Text(
                                        "修改：${modifiedMillis?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "未知"}",
                                        fontSize = 11.5.sp,
                                        color = palette.textSecondary
                                    )
                                    Text(
                                        "权限：${if (writable) "可读写" else "只读"}",
                                        fontSize = 11.5.sp,
                                        color = palette.textSecondary
                                    )
                                    Text("路径：$fullLocation", fontSize = 11.sp, color = palette.textSecondary)
                                }
                            }
                        }
                    }

                    // Streaming Hash Checksum Section
                    if (!entry.isDirectory) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (checksums == null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Fingerprint, null, Modifier.size(16.dp), tint = palette.accent)
                                        Spacer(Modifier.width(6.dp))
                                        Text("哈希校验 (MD5 / SHA-1 / SHA-256 / CRC32)", fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                                        FilledTonalButton(
                                            onClick = onComputeChecksums,
                                            enabled = !busy,
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                        ) { Text(if (busy) "计算中…" else "立即计算", fontSize = 11.sp) }
                                    }
                                } else {
                                    Text("哈希校验结果（已读取 ${fileBytes(checksums.byteCount)}）", fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                                    listOf(
                                        "MD5" to checksums.md5,
                                        "SHA-1" to checksums.sha1,
                                        "SHA-256" to checksums.sha256,
                                        "CRC32" to checksums.crc32
                                    ).forEach { (algo, hex) ->
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(algo, fontSize = 10.sp, color = palette.textSecondary, fontWeight = FontWeight.Medium)
                                                Text(hex, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                                            }
                                            FileToolButton(onClick = { copyText(algo, hex) }, height = 26.dp) {
                                                Text("复制", fontSize = 10.5.sp)
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = hashCompareInput,
                                        onValueChange = onHashCompareChange,
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("粘贴外部哈希值比对", fontSize = 10.5.sp) },
                                        trailingIcon = {
                                            TextButton(onClick = {
                                                val clipText = clipManager?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                                if (clipText.isNotEmpty()) onHashCompareChange(clipText)
                                            }) { Text("粘贴", fontSize = 10.5.sp) }
                                        }
                                    )
                                    if (hashCompareInput.isNotBlank()) {
                                        val matched = checksums.matchedAlgorithm(hashCompareInput)
                                        Text(
                                            text = if (matched != null) "✓ 校验一致（匹配 $matched）" else "✗ 与当前文件哈希不匹配",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (matched != null) palette.accent else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorQuickActionButton(
    title: String,
    icon: ImageVector,
    enabled: Boolean = true,
    accent: Boolean = false,
    destructive: Boolean = false,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = opaqueFilePalette(LocalPanelPalette.current)
    val fg = when {
        !enabled -> palette.textSecondary.copy(alpha = 0.4f)
        destructive -> MaterialTheme.colorScheme.error
        accent || active -> palette.accent
        else -> palette.textPrimary
    }
    val bg = when {
        active && destructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
        active || accent -> palette.accent.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(0.7.dp, if (active) fg else palette.cardBorder.copy(alpha = 0.4f)),
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = fg)
            Spacer(Modifier.height(3.dp))
            Text(
                text = title,
                fontSize = 10.2.sp,
                color = fg,
                fontWeight = if (accent || active) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun FileToolButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    height: Dp = 36.dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = maxOf(height, 48.dp)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), content = content)
}


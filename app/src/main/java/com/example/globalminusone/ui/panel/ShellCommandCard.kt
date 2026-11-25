package com.example.globalminusone.ui.panel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ShellCommandSpec
import com.example.globalminusone.data.ShellExecutionMode
import com.example.globalminusone.system.AppShellExecutor
import com.example.globalminusone.system.AppShellResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun ShellCommandCard(item: ComponentItem, enabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cardColors = LocalCardColors.current
    val spec = remember(item.intentUri, item.textContent) { ShellCommandSpec.fromComponent(item) }
    val highRisk = remember(spec.command) { ShellCommandSpec.isHighRiskCommand(spec.command) }
    val mustConfirm = spec.requireConfirm || highRisk
    val isCompactTile = LocalContainerGridIconCell.current || spec.preferCompactTile || (item.w <= 10 && item.h <= 8)

    var showOutputDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<AppShellResult?>(null) }
    var lastStatus by remember { mutableStateOf("") }
    var lastOutput by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Job?>(null) }

    fun launchExecution(openDialog: Boolean = spec.showOutputDialog || !isCompactTile) {
        if (spec.command.isBlank()) {
            lastStatus = "尚未配置命令"
            lastOutput = "请在编辑模式下长按此卡片，打开「属性」填写 Shell 命令。"
            showOutputDialog = true
            return
        }
        pending?.cancel()
        if (openDialog) showOutputDialog = true
        lastStatus = "正在以 ${spec.mode.label} 执行…"
        lastOutput = ""
        pending = scope.launch {
            try {
                val res = runCatching {
                    AppShellExecutor.execute(
                        context = context,
                        command = spec.command,
                        mode = spec.mode,
                        timeoutSec = spec.timeoutSec
                    )
                }.getOrElse { err ->
                    AppShellResult(
                        exitCode = null,
                        output = "执行失败或已取消：${err.message.orEmpty()}",
                        timedOut = false,
                        modeUsed = spec.mode
                    )
                }
                lastResult = res
                lastStatus = when {
                    res.timedOut -> "已超时或达到输出上限 (${res.elapsedMs}ms)"
                    res.exitCode == 0 -> "执行成功 · 退出码 0 (${res.elapsedMs}ms)"
                    res.exitCode != null -> "退出码 ${res.exitCode} (${res.elapsedMs}ms)"
                    else -> "未能完成执行"
                }
                lastOutput = res.output.ifBlank { "（无标准输出）" }
                if (!openDialog) {
                    if (res.exitCode == 0 && !res.timedOut) {
                        val firstLine = res.output.lineSequence().firstOrNull { it.isNotBlank() }?.take(60)
                        Toast.makeText(
                            context,
                            if (firstLine != null) "${item.title}: $firstLine" else "${item.title} 执行完成",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showOutputDialog = true
                    }
                }
            } finally {
                pending = null
            }
        }
    }

    fun triggerAction() {
        if (!enabled) return
        if (spec.command.isBlank()) {
            launchExecution(openDialog = true)
        } else if (mustConfirm) {
            showConfirmDialog = true
        } else {
            launchExecution(openDialog = spec.showOutputDialog)
        }
    }

    val modeTint = when (spec.mode) {
        ShellExecutionMode.NORMAL -> Color(0xFF1E88E5)
        ShellExecutionMode.SHIZUKU -> Color(0xFFFF8F00)
        ShellExecutionMode.ROOT -> Color(0xFFD32F2F)
    }

    if (isCompactTile) {
        Box(modifier = Modifier.fillMaxSize()) {
            CustomShortcutComponent(
                item = item,
                enabled = enabled,
                fallbackIcon = Icons.Rounded.Bolt,
                onClick = ::triggerAction
            )
            if (spec.mode != ShellExecutionMode.NORMAL) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = modeTint.copy(alpha = 0.88f)
                ) {
                    Text(
                        text = if (spec.mode == ShellExecutionMode.SHIZUKU) "S" else "R",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = enabled) { triggerAction() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = modeTint.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = spec.mode.badge,
                        color = modeTint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = item.title.ifBlank { "Shell 命令" },
                    color = cardColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (highRisk) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color(0xFFD32F2F).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "高危",
                            color = Color(0xFFD32F2F),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                if (lastOutput.isNotBlank() && enabled) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = cardColors.accent.copy(alpha = 0.14f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText(item.title, lastOutput))
                                Toast.makeText(context, "已复制命令输出", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "复制输出",
                            tint = cardColors.accent,
                            modifier = Modifier.padding(5.dp).size(13.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (pending != null) Color(0xFFD32F2F).copy(alpha = 0.16f) else cardColors.accent.copy(alpha = 0.18f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = enabled) {
                            if (pending != null) {
                                pending?.cancel()
                                pending = null
                                lastStatus = "已取消执行"
                            } else {
                                triggerAction()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = if (pending != null) Icons.Rounded.Close else Icons.Rounded.PlayArrow,
                            contentDescription = if (pending != null) "取消" else "执行",
                            tint = if (pending != null) Color(0xFFD32F2F) else cardColors.accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (pending != null) "停止" else "执行",
                            color = if (pending != null) Color(0xFFD32F2F) else cardColors.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(cardColors.trackColor.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (spec.command.isBlank()) "点击编辑命令内容…" else "$ ${spec.command}",
                    color = if (spec.command.isBlank()) cardColors.textSecondary else cardColors.textPrimary.copy(alpha = 0.9f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (lastStatus.isNotBlank() || lastOutput.isNotBlank()) {
                val ok = lastResult?.exitCode == 0 && lastResult?.timedOut != true
                val preview = lastOutput.lineSequence().firstOrNull { it.isNotBlank() }?.take(72).orEmpty()
                Text(
                    text = if (preview.isNotBlank()) "$lastStatus · $preview" else lastStatus,
                    color = if (lastResult == null) cardColors.textSecondary else if (ok) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = enabled) { showOutputDialog = true }
                )
            }
        }
    }

    if (showConfirmDialog) {
        PanelAlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(if (highRisk) "高危命令执行确认 · ${item.title}" else "确认执行 · ${item.title}")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (highRisk) {
                            "警告：检测到可能修改或破坏系统/数据的敏感关键字！请仔细核对后再执行："
                        } else {
                            "将以「${spec.mode.label}」执行以下脚本（超时 ${spec.timeoutSec}s）："
                        },
                        color = if (highRisk) Color(0xFFD32F2F) else LocalPanelPalette.current.textPrimary,
                        fontSize = 12.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = spec.command,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    launchExecution(openDialog = spec.showOutputDialog || !isCompactTile)
                }) {
                    Text("确认执行", color = if (highRisk) Color(0xFFD32F2F) else LocalPanelPalette.current.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("取消") }
            }
        )
    }

    if (showOutputDialog) {
        PanelAlertDialog(
            onDismissRequest = {
                pending?.cancel()
                showOutputDialog = false
            },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.title.ifBlank { "Shell 命令" })
                    if (lastStatus.isNotBlank()) {
                        Text(
                            text = "${spec.mode.badge} · $lastStatus",
                            fontSize = 11.sp,
                            color = LocalPanelPalette.current.textSecondary
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (spec.command.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "$ ${spec.command}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = lastOutput.ifBlank { if (pending != null) "正在等待命令输出…" else "点击下方执行命令" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (lastOutput.isNotBlank()) {
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText(item.title, lastOutput))
                            Toast.makeText(context, "已复制命令输出", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("复制结果")
                        }
                    }
                    TextButton(
                        enabled = pending == null && spec.command.isNotBlank(),
                        onClick = {
                            if (mustConfirm) {
                                showOutputDialog = false
                                showConfirmDialog = true
                            } else {
                                launchExecution(openDialog = true)
                            }
                        }
                    ) {
                        Text(if (lastOutput.isBlank()) "执行命令" else "重新执行")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pending?.cancel()
                    showOutputDialog = false
                }) {
                    Text(if (pending != null) "取消执行" else "关闭")
                }
            }
        )
    }
}

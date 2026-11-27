package com.example.globalminusone.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.ui.AppIcons

/** Anchored to the actual browser window, with no Activity dialog or full-screen minimum size. */
@Composable
internal fun CompactBrowserPanel(title: String, onDismiss: () -> Unit,
    footer: (@Composable RowScope.() -> Unit)? = null, content: @Composable () -> Unit) {
    val sheetTitle = title.ifEmpty { "工具菜单" }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.36f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
        Surface(Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth()
            .heightIn(max = maxHeight.coerceAtMost(380.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 8.dp) {
            Column {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 2.dp)
                        .width(36.dp).height(4.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sheetTitle, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(AppIcons.Close, "关闭$sheetTitle", Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Box(Modifier.fillMaxWidth().weight(1f, fill = false)) { content() }
                footer?.let { actions ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
    }
}

@Composable
internal fun CompactBrowserToolMenu(
    tools: List<BrowserTool>,
    onToolClick: (BrowserTool) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth < 176.dp -> 2
            maxWidth < 280.dp -> 3
            else -> 4
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BrowserToolGroup.entries.forEach { group ->
                val groupedTools = tools.filter { it.group == group }
                if (groupedTools.isNotEmpty()) {
                    Text(group.label, Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold)
                }
                groupedTools.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { tool ->
                            BrowserNavigationButton(
                                label = tool.label,
                                icon = tool.icon,
                                enabled = tool.enabled,
                                modifier = Modifier.weight(1f).heightIn(min = 62.dp),
                                active = tool.active,
                                destructive = tool.destructive
                            ) { onToolClick(tool) }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

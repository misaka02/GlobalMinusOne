package com.example.globalminusone.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.globalminusone.ui.AppIcons

/** Anchored to the actual browser window, with no Activity dialog or full-screen minimum size. */
@Composable
internal fun CompactBrowserPanel(title: String, onDismiss: () -> Unit,
    footer: (@Composable RowScope.() -> Unit)? = null, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .24f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
        Surface(Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth()
            .heightIn(max = maxHeight.coerceAtMost(360.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 6.dp) {
            Column {
                if (title.isNotEmpty()) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(AppIcons.Close, "关闭$title", Modifier.size(18.dp)) }
                }
                if (title.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
                Box(Modifier.fillMaxWidth().weight(1f, fill = false)) { content() }
                footer?.let { actions ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
    }
}

@Composable
internal fun CompactBrowserToolMenu(
    onDismiss: () -> Unit,
    tools: List<BrowserTool>,
    onToolClick: (BrowserTool) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth < 176.dp -> 2
            maxWidth < 280.dp -> 3
            else -> 4
        }
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BrowserToolGroup.entries.forEach { group ->
                    val groupedTools = tools.filter { it.group == group }
                    if (groupedTools.isNotEmpty()) {
                        Text(group.label, Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp),
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
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(AppIcons.KeyboardArrowDown, "收起菜单", Modifier.size(22.dp))
                }
            }
        }
    }
}

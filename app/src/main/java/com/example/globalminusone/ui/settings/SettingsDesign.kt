package com.example.globalminusone.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.ui.AppIcons as Icons

internal fun toggleExpandedGroup(current: Set<String>, group: String): Set<String> =
    if (group in current) current - group else current + group

@Composable
internal fun rememberExpandedGroups(
    initialGroup: String,
    defaultGroup: String = ""
): MutableState<Set<String>> {
    val initialKey = initialGroup.ifBlank { defaultGroup }
    val rawList = rememberSaveable {
        mutableStateOf(if (initialKey.isNotBlank()) listOf(initialKey) else emptyList())
    }
    return remember(rawList) {
        object : MutableState<Set<String>> {
            override var value: Set<String>
                get() = rawList.value.toSet()
                set(v) {
                    rawList.value = v.toList()
                }

            override fun component1(): Set<String> = value
            override fun component2(): (Set<String>) -> Unit = { value = it }
        }
    }
}

private class BoxedGroupLazyListScope(
    private val delegate: LazyListScope
) : LazyListScope by delegate {
    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        delegate.item(key = key, contentType = contentType) {
            BoxedGroupChildContainer { content() }
        }
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit
    ) {
        delegate.items(count = count, key = key, contentType = contentType) { index ->
            BoxedGroupChildContainer { itemContent(index) }
        }
    }
}

@Composable
private fun BoxedGroupChildContainer(content: @Composable () -> Unit) {
    val bgColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(bgColor)
            .drawBehind {
                val stroke = 0.6.dp.toPx()
                drawLine(borderColor, Offset(stroke / 2f, 0f), Offset(stroke / 2f, size.height), stroke)
                val rx = size.width - stroke / 2f
                drawLine(borderColor, Offset(rx, 0f), Offset(rx, size.height), stroke)
            }
    ) {
        content()
    }
}

/** Multi-expand grouped section with a boxed container around expanded child controls. */
internal fun LazyListScope.settingsGroup(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    item(key = "settings-group-$title") {
        SettingsGroupHeader(title = title, summary = summary, expanded = expanded, onToggle = onToggle)
    }
    if (expanded) {
        BoxedGroupLazyListScope(this).content()
        item(key = "settings-group-footer-$title") {
            SettingsGroupFooterCap()
        }
    }
}

@Composable
private fun SettingsGroupHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val colors = rememberGroupHeaderSpec(expanded)
    Column(
        modifier = Modifier.fillMaxWidth().then(colors.outerPad).clip(colors.shape).background(colors.bg)
            .drawGroupHeaderBorder(expanded, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                .clickable(role = Role.Button, onClickLabel = colors.clickLabel, onClick = onToggle)
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.titleColor)
                Text(summary, fontSize = 12.5.sp, color = colors.subColor)
            }
            Icon(
                Icons.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.titleColor.copy(alpha = 0.78f),
                modifier = Modifier.size(20.dp).rotate(if (expanded) 90f else 0f)
            )
        }
        if (expanded) HorizontalDivider(color = borderColor, thickness = 0.6.dp)
    }
}

private data class GroupHeaderSpec(
    val bg: Color,
    val shape: RoundedCornerShape,
    val outerPad: Modifier,
    val titleColor: Color,
    val subColor: Color,
    val clickLabel: String
)

@Composable
private fun rememberGroupHeaderSpec(expanded: Boolean): GroupHeaderSpec {
    val scheme = MaterialTheme.colorScheme
    return if (expanded) {
        GroupHeaderSpec(
            bg = scheme.secondaryContainer.copy(alpha = 0.55f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            outerPad = Modifier.padding(start = 16.dp, end = 16.dp, top = 5.dp),
            titleColor = scheme.onSecondaryContainer,
            subColor = scheme.onSecondaryContainer.copy(alpha = 0.85f),
            clickLabel = "收起分组"
        )
    } else {
        GroupHeaderSpec(
            bg = scheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            outerPad = Modifier.padding(16.dp, 5.dp),
            titleColor = scheme.onSurface,
            subColor = scheme.onSurfaceVariant,
            clickLabel = "展开分组"
        )
    }
}

private fun Modifier.drawGroupHeaderBorder(expanded: Boolean, borderColor: Color): Modifier =
    drawBehind {
        val stroke = 0.6.dp.toPx()
        val half = stroke / 2f
        val r = 16.dp.toPx()
        val arcSize = Size(r * 2f - stroke, r * 2f - stroke)
        if (!expanded) {
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(half, half),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(r, r),
                style = Stroke(stroke)
            )
        } else {
            val s = Stroke(stroke)
            drawArc(borderColor, 180f, 90f, false, Offset(half, half), arcSize, style = s)
            drawArc(borderColor, 270f, 90f, false, Offset(size.width - r * 2f + half, half), arcSize, style = s)
            drawLine(borderColor, Offset(half, r), Offset(half, size.height), stroke)
            drawLine(borderColor, Offset(size.width - half, r), Offset(size.width - half, size.height), stroke)
            drawLine(borderColor, Offset(r, half), Offset(size.width - r, half), stroke)
        }
    }

@Composable
private fun SettingsGroupFooterCap() {
    val bgColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(bgColor)
                .drawBehind {
                    val stroke = 0.6.dp.toPx()
                    val half = stroke / 2f
                    val r = 14.dp.toPx().coerceAtMost(size.height)
                    val s = Stroke(stroke)
                    val arcSize = Size(r * 2f - stroke, r * 2f - stroke)
                    val topArcY = size.height - r * 2f + half
                    drawArc(borderColor, 90f, 90f, false, Offset(half, topArcY), arcSize, style = s)
                    drawArc(borderColor, 0f, 90f, false, Offset(size.width - r * 2f + half, topArcY), arcSize, style = s)
                    drawLine(borderColor, Offset(r, size.height - half), Offset(size.width - r, size.height - half), stroke)
                }
        )
        Spacer(modifier = Modifier.height(5.dp))
    }
}

@Composable
internal fun SettingsHint(text: String) {
    Text(
        text,
        fontSize = 12.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

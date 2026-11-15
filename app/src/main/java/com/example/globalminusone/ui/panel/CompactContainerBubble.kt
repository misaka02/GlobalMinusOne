package com.example.globalminusone.ui.panel

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.*
import com.example.globalminusone.system.SystemController
import com.example.globalminusone.ui.AppIcons as Icons

/** An in-place curtain, deliberately separate from the Folder popup presentation. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactContainerBubble(
    item: ComponentItem, state: GlobalMinusOneState, repository: MinusOneRepository?,
    systemController: SystemController?, isEditMode: Boolean,
    onRequestClose: () -> Unit, onOpenMainApp: () -> Unit, pageIndex: Int = 0,
    nestedContent: (@Composable () -> Unit)? = null
) {
    val colors = LocalCardColors.current
    val theme = LocalPanelPalette.current.themeConfig
    val locked = LocalLayoutLocked.current
    val folderEdit = LocalFolderEditAction.current
    val presentation = LocalContainerPresentation.current ?: remember { ContainerPresentation() }
    val curtain = remember(presentation, item.id) { presentation.curtain(item.id) }
    val expanded = curtain.expanded
    val animDurationMs = if (theme.animationDurationMs <= 0) 0 else theme.animationDurationMs.coerceIn(100, 420)
    LaunchedEffect(expanded, animDurationMs) {
        if (animDurationMs == 0) curtain.progress.snapTo(if (expanded) 1f else 0f)
        else curtain.progress.animateTo(if (expanded) 1f else 0f, tween(animDurationMs, easing = FastOutSlowInEasing))
    }
    // Parent slots use the very same animation value to make room for the curtain.
    val fraction = curtain.progress.value
    val angle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(animDurationMs),
        label = "curtain-chevron"
    )
    val scroll = rememberScrollState()
    val route = rememberContainerScrollRoute(scroll)
    val add = LocalAddToContainer.current
    var actionToEdit by remember(item.id) { mutableStateOf<QuickActionItem?>(null) }
    actionToEdit?.let { action -> repository?.let { repo ->
        EditContainerActionDialog(repo, pageIndex, item.id, action) { actionToEdit = null }
    } }
    val alpha = if (!theme.cardBackground) 0f else
        (if (item.bgAlphaPercent in 0..100) item.bgAlphaPercent else theme.cardBgAlphaPercent) / 100f
    val background = Color(((item.bgColorHex ?: theme.defaultCardBgColorHex) and 0x00FFFFFFL) or 0xFF000000L).copy(alpha = alpha)
    val radius = if (item.cornerRadiusDp >= 0) item.cornerRadiusDp else theme.cardCornerRadiusDp
    val headerHeight = compactContainerHeaderHeightDp(LocalDensity.current.fontScale).dp
    Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(radius.dp), color = background) {
        Column(Modifier.fillMaxSize().clipToBounds()) {
            Row(Modifier.fillMaxWidth().height(headerHeight).combinedClickable(
                onClick = { if (!isEditMode) curtain.expanded = !curtain.expanded else repository?.selectComponent(item.id) },
                onLongClick = { if (!locked) { if (folderEdit != null) folderEdit() else repository?.setEditMode(true); repository?.selectComponent(item.id) } }
            ).padding(end = if (folderEdit != null && isEditMode) 36.dp else 0.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Widgets, null, Modifier.size(20.dp), tint = colors.textSecondary)
                Text(item.title.ifBlank { "容器" }, Modifier.weight(1f), color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isEditMode && !locked && !(folderEdit != null && isEditMode)) {
                    ContainerQuickLayoutPill(
                        item = item,
                        pageIndex = pageIndex,
                        repository = repository,
                        onAddChild = add?.let { cb -> { cb(item.id) } }
                    )
                }
                val itemCount = item.containerActions.size + item.containerComponents.size
                if (itemCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.trackColor.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = "$itemCount",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                IconButton(onClick = { curtain.expanded = !curtain.expanded }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.KeyboardArrowDown, if (expanded) "收起容器" else "展开容器",
                        Modifier.size(20.dp).rotate(angle), tint = colors.textSecondary)
                }
            }
            if (fraction > 0f) {
                HorizontalDivider(thickness = COMPACT_CONTAINER_DIVIDER_DP.dp, color = colors.trackColor.copy(alpha = .5f))
                CompositionLocalProvider(LocalPanelScrollTarget provides route.target) {
                    Column(Modifier.weight(1f).fillMaxWidth().containerPanelScroll(route, !isEditMode)
                        .ownPanelScrollGestures(!isEditMode).padding(horizontal = COMPACT_CONTAINER_HORIZONTAL_PADDING_DP.dp,
                            vertical = COMPACT_CONTAINER_PADDING_DP.dp),
                        verticalArrangement = Arrangement.spacedBy(COMPACT_CONTAINER_GAP_DP.dp)) {
                        if (item.containerActions.isNotEmpty() || item.containerComponents.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(
                                if (item.containerLayout == ContainerLayoutType.GRID) containerRowGapDp(item).dp else COMPACT_CONTAINER_GAP_DP.dp)) {
                                if (item.containerActions.isNotEmpty() && repository != null)
                                    LegacyContainerActionGrid(item, pageIndex, state, repository, systemController,
                                        isEditMode, onRequestClose, onOpenMainApp, onEdit = { actionToEdit = it })
                                else if (repository == null) item.containerActions.forEach { Text(it.title, color = colors.textPrimary) }
                                if (item.containerComponents.isNotEmpty()) nestedContent?.invoke()
                            }
                        }
                        if (item.containerActions.isEmpty() && item.containerComponents.isEmpty())
                            Text("还没有子卡片", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp)
                        if (!locked && add != null) TextButton(onClick = { add(item.id) },
                            modifier = Modifier.heightIn(min = compactContainerAddRowHeightDp(LocalDensity.current.fontScale).dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("添加子卡片", lineHeight = 20.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

package com.example.globalminusone.ui.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.*
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.SystemController
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.motion.MotionTokens

/** An in-place collapsible container card with compact header controls and zero wasted footer space. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactContainerBubble(
    item: ComponentItem, state: GlobalMinusOneState, repository: MinusOneRepository?,
    systemController: SystemController?, isEditMode: Boolean,
    onRequestClose: () -> Unit, onOpenMainApp: () -> Unit, pageIndex: Int = 0,
    nestedContent: (@Composable () -> Unit)? = null
) {
    CompactContainerContent(
        item = item,
        state = state.cardLayoutSnapshot(),
        layoutActions = repository?.let { rememberCardLayoutActions(it) },
        propertyEditors = repository?.let { rememberCardPropertyEditors(it, systemController) },
        legacyActions = rememberLegacyContainerActions(repository, systemController),
        eyeProtectionEnabled = state.advancedConfig.eyeProtectionEnabled,
        isEditMode = isEditMode,
        onRequestClose = onRequestClose,
        onOpenMainApp = onOpenMainApp,
        pageIndex = pageIndex,
        nestedContent = nestedContent
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactContainerContent(
    item: ComponentItem, state: CardLayoutSnapshot, layoutActions: CardLayoutActions?,
    propertyEditors: CardPropertyEditors?, legacyActions: LegacyContainerActions, eyeProtectionEnabled: Boolean,
    isEditMode: Boolean, onRequestClose: () -> Unit, onOpenMainApp: () -> Unit, pageIndex: Int,
    nestedContent: (@Composable () -> Unit)?
) {
    val colors = LocalCardColors.current
    val theme = LocalPanelPalette.current.themeConfig
    val locked = LocalLayoutLocked.current
    val folderEdit = LocalFolderEditAction.current
    val presentation = LocalContainerPresentation.current ?: remember { ContainerPresentation() }
    val curtain = remember(presentation, item.id) { presentation.curtain(item.id) }
    val expanded = curtain.expanded
    val animMs = theme.animationDurationMs.coerceIn(0, 800)
    LaunchedEffect(expanded, animMs) {
        if (animMs <= 0) curtain.progress.snapTo(if (expanded) 1f else 0f)
        else curtain.progress.animateTo(
            if (expanded) 1f else 0f,
            if (expanded) MotionTokens.overlayEnterSpring(animMs) else MotionTokens.overlayExitSpring(animMs)
        )
    }
    val showCurtainContent by remember(curtain) { derivedStateOf { curtain.progress.value > 0f } }
    val scroll = rememberScrollState()
    val route = rememberContainerScrollRoute(scroll)
    val add = LocalPanelActions.current.addToContainer
    var actionToEdit by remember(item.id) { mutableStateOf<QuickActionItem?>(null) }
    actionToEdit?.let { action ->
        layoutActions?.let {
            propertyEditors?.LegacyAction(pageIndex, item.id, action) { actionToEdit = null }
        }
    }
    val alpha = if (!theme.cardBackground) 0f else
        (if (item.bgAlphaPercent in 0..100) item.bgAlphaPercent else theme.cardBgAlphaPercent) / 100f
    val background = Color(((item.bgColorHex ?: theme.defaultCardBgColorHex) and 0x00FFFFFFL) or 0xFF000000L).copy(alpha = alpha)
    val radius = if (item.cornerRadiusDp >= 0) item.cornerRadiusDp else theme.cardCornerRadiusDp
    val headerHeight = compactContainerHeaderHeightDp(LocalDensity.current.fontScale).dp
    val itemCount = item.containerActions.size + item.containerComponents.size
    val hasChildren = itemCount > 0

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(radius.dp),
        color = background,
        border = BorderStroke(1.dp, colors.trackColor.copy(alpha = 0.24f))
    ) {
        Column(Modifier.fillMaxSize().clipToBounds()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .combinedClickable(
                        onClick = { if (!isEditMode) curtain.expanded = !curtain.expanded else layoutActions?.selectComponent(item.id) },
                        onLongClick = {
                            if (!locked) {
                                if (folderEdit != null) folderEdit() else layoutActions?.setEditMode(true)
                                layoutActions?.selectComponent(item.id)
                            }
                        }
                    )
                    .padding(end = if (folderEdit != null && isEditMode) 36.dp else 0.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.accent.copy(alpha = 0.14f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Widgets,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.accent
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.title.ifBlank { "容器" },
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (hasChildren) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = colors.trackColor.copy(alpha = 0.42f)
                            ) {
                                Text(
                                    text = "$itemCount",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    color = colors.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (!expanded && !isEditMode && hasChildren) {
                    CollapsedChildPreviewStrip(item = item, colors = colors)
                }

                if (isEditMode && !locked && !(folderEdit != null && isEditMode)) {
                    ContainerQuickLayoutContent(
                        item = item,
                        pageIndex = pageIndex,
                        layoutActions = layoutActions,
                        onAddChild = add?.let { cb -> { cb(item.id) } }
                    )
                } else if (expanded && !locked && add != null && hasChildren) {
                    Surface(
                        shape = CircleShape,
                        color = colors.accent.copy(alpha = 0.15f),
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { add(item.id) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Add,
                                contentDescription = "添加子卡片",
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = colors.trackColor.copy(alpha = 0.36f),
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { curtain.expanded = !curtain.expanded }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.KeyboardArrowDown,
                            contentDescription = if (expanded) "收起容器" else "展开容器",
                            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = curtain.progress.value * 180f },
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            if (showCurtainContent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(COMPACT_CONTAINER_DIVIDER_DP.dp)
                        .drawBehind {
                            val a = 0.32f * curtain.progress.value.coerceIn(0f, 1f)
                            if (a > 0f) drawRect(colors.trackColor.copy(alpha = a))
                        }
                )
                CompositionLocalProvider(LocalPanelScrollTarget provides route.target) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .layout { measurable, constraints ->
                                    // Measure children at constant max viewport height rather than
                                    // passing the animating curtain height so inner grids don't re-subcompose 60fps.
                                    val capPx = CONTAINER_VIEWPORT_MAX_DP.dp.roundToPx().coerceAtLeast(1)
                                    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = capPx))
                                    val layoutH = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
                                    layout(constraints.maxWidth, layoutH) {
                                        placeable.placeRelative(0, 0)
                                    }
                                }
                                .graphicsLayer {
                                    this.alpha = ((curtain.progress.value - 0.08f) / 0.92f).coerceIn(0f, 1f)
                                }
                                .containerPanelScroll(route, !isEditMode)
                                .ownPanelScrollGestures(!isEditMode)
                                .padding(
                                    horizontal = COMPACT_CONTAINER_HORIZONTAL_PADDING_DP.dp,
                                    vertical = COMPACT_CONTAINER_PADDING_DP.dp
                                ),
                            verticalArrangement = Arrangement.spacedBy(
                                if (item.containerLayout == ContainerLayoutType.GRID) containerRowGapDp(item).dp else COMPACT_CONTAINER_GAP_DP.dp
                            )
                        ) {
                        if (hasChildren) {
                            if (item.containerActions.isNotEmpty() && layoutActions != null) {
                                LegacyContainerGridContent(
                                    item = item,
                                    pageIndex = pageIndex,
                                    layoutLocked = state.layoutLocked,
                                    eyeProtectionEnabled = eyeProtectionEnabled,
                                    layoutActions = layoutActions,
                                    legacyActions = legacyActions,
                                    editing = isEditMode,
                                    onClose = onRequestClose,
                                    onMain = onOpenMainApp,
                                    onEdit = { actionToEdit = it }
                                )
                            } else if (layoutActions == null) {
                                item.containerActions.forEach { Text(it.title, color = colors.textPrimary) }
                            }
                            if (item.containerComponents.isNotEmpty()) nestedContent?.invoke()
                        } else {
                            val addHeight = compactContainerAddRowHeightDp(LocalDensity.current.fontScale).dp
                            if (!locked && add != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.accent.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = addHeight)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { add(item.id) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Add, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "空容器 · 点击添加子卡片",
                                            color = colors.accent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = addHeight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("还没有子卡片", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
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
private fun CollapsedChildPreviewStrip(
    item: ComponentItem,
    colors: CardColorPalette
) {
    val cacheGen by AppAndIntentLauncher.cacheGeneration.collectAsState()
    val previews = remember(item.containerComponents, item.containerActions, cacheGen) {
        buildList {
            item.containerComponents.take(3).forEach { child ->
                add(Triple(child.packageName, child.iconName, Color(child.colorHex)))
            }
            if (size < 3) {
                item.containerActions.take(3 - size).forEach { act ->
                    add(Triple(act.packageName, act.iconName, colors.accent))
                }
            }
        }
    }
    if (previews.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        previews.forEach { (pkg, iconName, tint) ->
            val appBmp = remember(pkg, cacheGen) {
                if (pkg.isNotBlank()) AppAndIntentLauncher.peekAppIconBitmap(pkg) else null
            }
            if (appBmp != null) {
                Image(
                    bitmap = appBmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getQuickActionIcon(iconName),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

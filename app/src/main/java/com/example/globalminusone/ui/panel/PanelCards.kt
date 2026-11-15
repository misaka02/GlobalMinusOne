package com.example.globalminusone.ui.panel

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.containerRowGapDp
import com.example.globalminusone.data.PageLayoutType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.findComponent
import com.example.globalminusone.data.isContainerType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.service.WidgetHostManager
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.HardwareStatus
import com.example.globalminusone.system.SystemController
import com.example.globalminusone.system.SystemMonitor
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------------------
// 卡片内高对比度配色辅助系统 (针对默认白底卡片优化，彻底告别浅灰字与隐形底圈)
// -----------------------------------------------------------------------------------------

data class CardColorPalette(
    val textPrimary: Color = Color(0xFF1F2329),     // 主文字颜色（含文字不透明度）
    val textSecondary: Color = Color(0xFF4E5969),   // 副文字颜色（含文字不透明度）
    val titleColor: Color = Color(0xFF1F2329),      // 标题颜色（含标题不透明度）
    val textPlaceholder: Color = Color(0xFF86909C), // 占位提示文字
    val iconBackground: Color = Color(0xFFF2F3F5),  // 卡片内图标圆形底色 / 分割线色
    val trackColor: Color = Color(0xFFE5E6EB),      // 进度条/Slider底轨色
    val accent: Color = Color(0xFF2196F3)           // 强调色/激活蓝色
)

val LocalCardColors = staticCompositionLocalOf {
    CardColorPalette()
}

val LocalIsEditMode = compositionLocalOf {
    false
}

internal val LocalLayoutLocked = compositionLocalOf { false }
internal val LocalAddToContainer = staticCompositionLocalOf<((String) -> Unit)?> { null }
internal val LocalCloseContainerPopup = staticCompositionLocalOf<() -> Unit> { {} }
/** Display treatment only. Menus and property editors always receive the saved item. */
internal val LocalContainerGridIconCell = staticCompositionLocalOf { false }
internal val LocalOpenFileBrowser = staticCompositionLocalOf<() -> Unit> { {} }

internal val LocalHardwareStatus = compositionLocalOf<HardwareStatus?> { null }

@Composable
internal fun currentHardwareStatus(): HardwareStatus {
    LocalHardwareStatus.current?.let { return it }
    // Older wrappers outside the overlay still share the same sampler.
    val context = LocalContext.current
    val statuses = remember(context) { SystemMonitor.observe(context) }
    return statuses.collectAsState().value
}

fun resolveCardPalette(
    item: ComponentItem,
    theme: com.example.globalminusone.data.ThemeConfig
): CardColorPalette {
    val rawBgHex = item.bgColorHex ?: theme.defaultCardBgColorHex
    val bgAlphaPct = if (!theme.cardBackground) 0 else if (item.bgAlphaPercent in 0..100) item.bgAlphaPercent else theme.cardBgAlphaPercent
    val baseBgRgb = Color((rawBgHex and 0x00FFFFFFL) or 0xFF000000L)
    val bgAlpha = (bgAlphaPct / 100f).coerceIn(0f, 1f)

    // 判断当前卡片背景（或高透明卡片底下的遮罩）是否为深色基调
    val bgLum = 0.299f * baseBgRgb.red + 0.587f * baseBgRgb.green + 0.114f * baseBgRgb.blue
    val isDarkSurface = if (bgAlpha < 0.25f) {
        theme.isDarkMode || ((theme.maskColorHex and 0x00FFFFFFL) < 0x808080L)
    } else {
        bgLum < 0.48f
    }

    val rawTextHex = item.textColorHex ?: theme.defaultTextColorHex
    val textAlphaPct = if (item.textAlphaPercent in 10..100) item.textAlphaPercent else theme.textAlphaPercent
    val textAlpha = (textAlphaPct / 100f).coerceIn(0.1f, 1f)
    val baseTextRgb = Color((rawTextHex and 0x00FFFFFFL) or 0xFF000000L)
    val primaryText = baseTextRgb.copy(alpha = textAlpha)

    val rawTitleHex = item.textColorHex ?: theme.titleColorHex ?: theme.defaultTextColorHex
    val titleAlphaPct = if (item.textAlphaPercent in 10..100) item.textAlphaPercent else theme.titleAlphaPercent
    val titleAlpha = (titleAlphaPct / 100f).coerceIn(0.1f, 1f)
    val baseTitleRgb = Color((rawTitleHex and 0x00FFFFFFL) or 0xFF000000L)
    val resolvedTitle = baseTitleRgb.copy(alpha = titleAlpha)

    val fallbackAccent = try {
        Color((theme.accentColorHex and 0x00FFFFFFL) or 0xFF000000L)
    } catch (_: Exception) {
        Color(0xFF2196F3)
    }

    val textLum = 0.299f * baseTextRgb.red + 0.587f * baseTextRgb.green + 0.114f * baseTextRgb.blue
    val secondaryBase = if (item.textColorHex != null) {
        baseTextRgb
    } else if (textLum > 0.55f) {
        Color(0xFFD0D5DD)
    } else {
        Color(0xFF4E5969)
    }

    return CardColorPalette(
        textPrimary = primaryText,
        textSecondary = secondaryBase.copy(alpha = (textAlpha * 0.82f).coerceIn(0.1f, 1f)),
        titleColor = resolvedTitle,
        textPlaceholder = secondaryBase.copy(alpha = (textAlpha * 0.60f).coerceIn(0.1f, 1f)),
        iconBackground = if (isDarkSurface) Color.White.copy(alpha = 0.14f) else Color(0xFFF2F3F5).copy(alpha = bgAlpha.coerceAtLeast(0.55f)),
        trackColor = if (isDarkSurface) Color.White.copy(alpha = 0.20f) else Color(0xFFE5E6EB).copy(alpha = bgAlpha.coerceAtLeast(0.60f)),
        accent = fallbackAccent
    )
}

// -----------------------------------------------------------------------------------------
// 快捷图标映射工具函数
// -----------------------------------------------------------------------------------------

fun getQuickActionIcon(iconName: String): ImageVector {
    return when (iconName.lowercase().removePrefix("custom:")) {
        "qr_code_scanner", "qrcode", "scan" -> Icons.Rounded.QrCodeScanner
        "payments", "payment", "pay" -> Icons.Rounded.Payments
        "directions_bus", "bus" -> Icons.Rounded.DirectionsBus
        "center_focus_strong", "wechat_scan" -> Icons.Rounded.CenterFocusStrong
        "chat", "wechat" -> Icons.Rounded.Chat
        "explore", "coolapk" -> Icons.Rounded.Explore
        "settings" -> Icons.Rounded.Settings
        "calculate" -> Icons.Rounded.Calculate
        "bolt", "flash" -> Icons.Rounded.Bolt
        "storage" -> Icons.Rounded.Storage
        "dashboard" -> Icons.Rounded.Dashboard
        "play_arrow" -> Icons.Rounded.PlayArrow
        "mic" -> Icons.Rounded.Mic
        "search" -> Icons.Rounded.Search
        "check_circle" -> Icons.Rounded.CheckCircle
        "access_time" -> Icons.Rounded.AccessTime
        "home" -> Icons.Rounded.Home
        "flashlight_on" -> Icons.Rounded.FlashlightOn
        "lock" -> Icons.Rounded.Lock
        "screenshot" -> Icons.Rounded.Screenshot
        "volume_up" -> Icons.Rounded.VolumeUp
        else -> Icons.Rounded.TouchApp
    }
}

// -----------------------------------------------------------------------------------------
// 常用应用与系统功能精绘彩色矢量图标 (QQ企鹅, 微信, 酷安, 全局负一屏, 快捷方式, 支付, 网站)
// -----------------------------------------------------------------------------------------

@Composable
fun ComponentWrapper(
    item: ComponentItem,
    modifier: Modifier = Modifier,
    pageIndex: Int = 0,
    repository: MinusOneRepository? = null,
    isEditMode: Boolean = false,
    onOpenAddComponent: (() -> Unit)? = null,
    onOpenEditDialog: ((ComponentItem) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val panelPalette = LocalPanelPalette.current
    val theme = panelPalette.themeConfig
    val bareGridIcon = LocalContainerGridIconCell.current && item.type in setOf(
        ComponentType.APP_ICON, ComponentType.CUSTOM_SHORTCUT, ComponentType.STATIC_SHORTCUT, ComponentType.FILE_SHORTCUT, ComponentType.WEBSITE_LINK, ComponentType.FOLDER)
    val gridFolderSpacing = LocalFolderGridSpacing.current.takeIf {
        LocalContainerGridIconCell.current && item.type == ComponentType.FOLDER
    }
    val cornerRadius = if (item.cornerRadiusDp >= 0) item.cornerRadiusDp.dp else theme.cardCornerRadiusDp.dp

    val rawBgHex = item.bgColorHex ?: theme.defaultCardBgColorHex
    val bgAlphaPct = if (!theme.cardBackground) {
        0
    } else if (item.bgAlphaPercent in 0..100) {
        item.bgAlphaPercent
    } else {
        theme.cardBgAlphaPercent
    }
    val baseBgRgb = Color((rawBgHex and 0x00FFFFFFL) or 0xFF000000L)
    val bgColor = baseBgRgb.copy(alpha = (bgAlphaPct / 100f).coerceIn(0f, 1f))

    val shape = RoundedCornerShape(cornerRadius)
    var showMenu by remember { mutableStateOf(false) }
    var destructiveRequest by remember(item.id) { mutableStateOf<CardDestructiveRequest?>(null) }
    fun requestDestructive(action: CardDestructiveAction) {
        val pageId = repository?.state?.value?.pages?.getOrNull(pageIndex)?.id ?: return
        destructiveRequest = CardDestructiveRequest(pageId, item.id, item.title.ifBlank { "卡片" }, action)
    }
    destructiveRequest?.let { request -> repository?.let { repo ->
        CardDestructiveConfirmation(request, repo) { destructiveRequest = null }
    } }
    val addToContainer = LocalAddToContainer.current
    val addAboveComponent = LocalAddAboveComponent.current
    val contentDrawsSurface = bareGridIcon || item.type == ComponentType.FOLDER ||
        (item.type == ComponentType.CONTAINER_BOX && item.containerCompact)
    val wrapperBgColor = if (contentDrawsSurface) Color.Transparent else bgColor

    val isWrap = item.autoHeight && !item.containerCompact && (item.type == ComponentType.CONTAINER_BOX || item.type == ComponentType.OPERATION_BAR)
    val layoutLocked = LocalLayoutLocked.current
    val folderEdit = LocalFolderEditAction.current
    val insideContainerChild = LocalInContainerChild.current
    val ordinaryHoldEditsLayout = !isAppContextMenuCardType(item.type)
    val slimVerticalCard = !bareGridIcon && !isWrap &&
        com.example.globalminusone.data.resolveComponentVertical(item) &&
        com.example.globalminusone.data.isSliderBarType(item.type) &&
        item.gridSpanX <= 1

    val cardPalette = remember(
        item.bgColorHex,
        item.bgAlphaPercent,
        item.textColorHex,
        item.textAlphaPercent,
        theme
    ) {
        resolveCardPalette(item, theme)
    }

    CompositionLocalProvider(
        LocalAppMenuEnvironment provides repository?.let { repo ->
            AppMenuEnvironment(repo, repo.state.value.pages.getOrNull(pageIndex)?.id.orEmpty(), item)
        },
        LocalCardColors provides cardPalette,
        LocalIsEditMode provides isEditMode,
        LocalAppEditAction provides onOpenEditDialog?.let { callback -> { callback(item) } },
        LocalAppEditLabel provides when (item.type) {
            ComponentType.APP_DRAWER_CARD -> "编辑抽屉卡片"
            ComponentType.RECENT_APPS_CARD -> "编辑最近运行卡片"
            else -> "编辑应用卡片"
        }
    ) {
        val baseCardModifier = when {
            slimVerticalCard -> Modifier.widthIn(max = 72.dp).fillMaxSize()
            isWrap -> modifier.wrapContentHeight()
            else -> modifier
        }
        val surfaceCard: @Composable () -> Unit = {
        Surface(
            modifier = baseCardModifier
                .then(if (bareGridIcon) Modifier else Modifier.padding(
                    start = item.marginLeftDp.coerceAtLeast(0).dp,
                    end = item.marginRightDp.coerceAtLeast(0).dp,
                    top = item.marginTopDp.coerceAtLeast(0).dp,
                    bottom = item.marginBottomDp.coerceAtLeast(0).dp
                ))
                .then(
                    if (!contentDrawsSurface && bgColor.alpha in 0.05f..0.97f) {
                        Modifier.border(
                            width = 0.8.dp,
                            color = panelPalette.cardBorder.copy(alpha = 0.32f),
                            shape = shape
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(shape)
                .panelCardBounds(item.id)
                .cardLongPressToEdit(
                    item.id,
                    repository != null && !isEditMode && !layoutLocked && ordinaryHoldEditsLayout && !insideContainerChild,
                    requireIdleOwnership = !com.example.globalminusone.data.isSliderBarType(item.type)
                ) {
                    if (folderEdit != null) folderEdit() else repository?.setEditMode(true)
                    repository?.selectComponent(item.id)
                },
            shape = shape,
            color = wrapperBgColor,
            shadowElevation = if (!contentDrawsSurface && bgColor.alpha >= 0.98f) 2.dp else 0.dp
        ) {
            Column(
                modifier = (if (isWrap) {
                    Modifier.fillMaxWidth().wrapContentHeight()
                } else {
                    Modifier.fillMaxSize()
                }).then(
                    if (contentDrawsSurface) Modifier else Modifier.padding(
                        start = item.paddingLeftDp.coerceIn(0, 48).dp,
                        end = item.paddingRightDp.coerceIn(0, 48).dp,
                        top = item.paddingTopDp.coerceIn(0, 48).dp,
                        bottom = item.paddingBottomDp.coerceIn(0, 48).dp
                    )
                )
            ) {
                if (!bareGridIcon && item.showTitle && item.title.isNotBlank() && !(item.type == ComponentType.CONTAINER_BOX && item.containerCompact)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = gridFolderSpacing?.labelGapDp?.dp ?: 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.title,
                            modifier = Modifier.weight(1f).padding(end = if (folderEdit != null && isEditMode) 36.dp else 6.dp),
                            color = cardPalette.titleColor,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // 右上角菜单（容器排版药丸仅在编辑态显示，日常浏览保持清爽）
                        if (!(folderEdit != null && isEditMode)) Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (item.type.isContainerType() && isEditMode && !LocalLayoutLocked.current) {
                                ContainerQuickLayoutPill(
                                    item = item,
                                    pageIndex = pageIndex,
                                    repository = repository,
                                    onAddChild = addToContainer?.let { cb -> { cb(item.id) } }
                                )
                            }
                            Box {
                            IconButton(
                                onClick = { showMenu = true },
                                enabled = !LocalLayoutLocked.current,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "卡片选项",
                                    tint = cardPalette.textSecondary.copy(alpha = if (isEditMode) 0.9f else 0.65f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            PanelDropdownMenu(
                                expanded = showMenu && !LocalLayoutLocked.current,
                                onDismissRequest = { showMenu = false }
                            ) {
                                com.example.globalminusone.service.Android12WindowKeyGuard()
                                val menuPalette = checkNotNull(LocalPanelMenuPalette.current)
                                val pageLayoutType = repository?.state?.collectAsState()?.value?.pages?.getOrNull(pageIndex)?.layoutType
                                val isFreePage = pageLayoutType == PageLayoutType.FREE
                                val isGridPage = pageLayoutType == PageLayoutType.GRID && folderEdit == null

                                if (isGridPage) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("占格", fontSize = 12.sp, color = menuPalette.secondary)
                                        listOf(
                                            Triple("1×1", 1, 1),
                                            Triple("2×1", 2, 1),
                                            Triple("2×2", 2, 2),
                                            Triple("整行×1", com.example.globalminusone.data.GRID_SPAN_FULL_ROW, 1),
                                            Triple("整行×2", com.example.globalminusone.data.GRID_SPAN_FULL_ROW, 2)
                                        ).forEach { (spanLabel, sx, sy) ->
                                            val selectedSpan = item.gridSpanX == sx && item.gridSpanY == sy
                                            Surface(
                                                onClick = {
                                                    showMenu = false
                                                    repository?.updateComponent(pageIndex, item.id) {
                                                        it.copy(gridSpanX = sx, gridSpanY = sy, autoHeight = false)
                                                    }
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (selectedSpan) menuPalette.selectedChip else menuPalette.chip
                                            ) {
                                                Text(
                                                    spanLabel,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selectedSpan) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedSpan) menuPalette.accent else menuPalette.text
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                }

                                if (item.type.isContainerType()) {
                                    PanelMenuItem(
                                        text = { Text("添加子卡片", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                                        enabled = addToContainer != null && repository != null,
                                        onClick = {
                                            showMenu = false
                                            addToContainer?.invoke(item.id)
                                        }
                                    )
                                    HorizontalDivider()
                                    PanelMenuItem(
                                        text = { Text(if (item.containerCompact) "切换为平铺展开" else "切换为紧凑气泡", fontSize = 13.sp) },
                                        onClick = {
                                            showMenu = false
                                            repository?.updateComponent(pageIndex, item.id) {
                                                it.copy(containerCompact = !it.containerCompact)
                                            }
                                        }
                                    )
                                    PanelMenuItem(
                                        text = {
                                            Text(
                                                if (item.containerLayout == ContainerLayoutType.GRID) "内部排列：切换自由布局" else "内部排列：切换网格布局",
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            repository?.updateComponent(pageIndex, item.id) {
                                                it.copy(
                                                    containerLayout = if (it.containerLayout == ContainerLayoutType.GRID) {
                                                        ContainerLayoutType.FREE
                                                    } else {
                                                        ContainerLayoutType.GRID
                                                    }
                                                )
                                            }
                                        }
                                    )
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("网格", fontSize = 12.sp, color = menuPalette.secondary)
                                        listOf(2, 3, 4, 5, 6).forEach { cols ->
                                            val selectedCols = item.containerLayout == ContainerLayoutType.GRID &&
                                                item.containerColumns.coerceIn(1, 8) == cols
                                            Surface(
                                                onClick = {
                                                    showMenu = false
                                                    repository?.updateComponent(pageIndex, item.id) {
                                                        it.copy(containerLayout = ContainerLayoutType.GRID, containerColumns = cols)
                                                    }
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (selectedCols) menuPalette.selectedChip else menuPalette.chip
                                            ) {
                                                Text(
                                                    "${cols}列",
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selectedCols) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedCols) menuPalette.accent else menuPalette.text
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                }
                                if (addAboveComponent != null && !isFreePage && folderEdit == null) {
                                    PanelMenuItem(
                                        text = { Text("在上方添加卡片", fontSize = 13.sp) },
                                        enabled = !layoutLocked,
                                        onClick = {
                                            showMenu = false
                                            addAboveComponent.invoke(item.id)
                                        }
                                    )
                                }
                                PanelMenuItem(
                                    text = { Text("卡片属性", fontSize = 13.sp) },
                                    onClick = {
                                        showMenu = false
                                        onOpenEditDialog?.invoke(item)
                                    }
                                )
                                if (item.type.isContainerType()) {
                                    HorizontalDivider()
                                    PanelMenuItem(
                                        text = { Text("清空子项…", color = menuPalette.danger, fontSize = 13.sp) },
                                        onClick = {
                                            showMenu = false
                                            requestDestructive(CardDestructiveAction.CLEAR_CHILDREN)
                                        }
                                    )
                                }
                                PanelMenuItem(
                                    text = { Text("删除卡片…", color = menuPalette.danger, fontSize = 13.sp) },
                                    onClick = {
                                        showMenu = false
                                        requestDestructive(CardDestructiveAction.DELETE)
                                    }
                                )
                            }
                            }
                        }
                    }
                }
                Box(
                    modifier = if (isWrap) {
                        Modifier.fillMaxWidth().wrapContentHeight()
                    } else {
                        Modifier.fillMaxWidth().weight(1f, fill = true)
                    },
                    contentAlignment = Alignment.Center
                ) {
                    content()
                    if (isEditMode &&
                        item.type == ComponentType.CONTAINER_BOX &&
                        !item.containerCompact &&
                        (!item.showTitle || item.title.isBlank()) &&
                        !(folderEdit != null && isEditMode) &&
                        !layoutLocked
                    ) {
                        Box(Modifier.align(Alignment.TopEnd).padding(2.dp)) {
                            ContainerQuickLayoutPill(
                                item = item,
                                pageIndex = pageIndex,
                                repository = repository,
                                onAddChild = addToContainer?.let { cb -> { cb(item.id) } }
                            )
                        }
                    }
                }
            }
        }
        }
        if (slimVerticalCard) {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                surfaceCard()
            }
        } else {
            surfaceCard()
        }
    }
}

@Composable
internal fun ContainerQuickLayoutPill(
    item: ComponentItem,
    pageIndex: Int,
    repository: MinusOneRepository?,
    onAddChild: (() -> Unit)? = null
) {
    if (repository == null) return
    val panelPalette = LocalPanelPalette.current
    val cardPalette = LocalCardColors.current
    val locked = LocalLayoutLocked.current
    var expanded by remember(item.id) { mutableStateOf(false) }
    var requestedInput by remember(item.id) { mutableStateOf<Int?>(null) }
    val isGrid = item.containerLayout == ContainerLayoutType.GRID
    val cols = item.containerColumns.coerceIn(1, 8)
    requestedInput?.let { kind ->
        GridDimensionInput(
            kind = kind,
            columnCount = cols,
            iconSizeDp = item.containerIconSizeDp,
            gapDp = item.containerItemGapDp,
            onColumns = { value -> repository.updateComponent(pageIndex, item.id) { it.copy(containerColumns = value) } },
            onIconSize = { value -> repository.updateComponent(pageIndex, item.id) { it.copy(containerIconSizeDp = value) } },
            onGap = { value -> repository.updateComponent(pageIndex, item.id) { it.copy(containerItemGapDp = value) } },
            onDismiss = { requestedInput = null },
            maxColumns = 8,
            allowAutoColumns = false,
            rowGapDp = containerRowGapDp(item),
            onRowGap = { value -> repository.updateComponent(pageIndex, item.id) { it.copy(containerRowGapDp = value) } }
        )
    }
    Box {
        Surface(
            onClick = { if (!locked) expanded = true },
            enabled = !locked,
            shape = RoundedCornerShape(6.dp),
            color = if (isGrid) panelPalette.accent.copy(alpha = 0.16f) else panelPalette.accent.copy(alpha = 0.08f)
        ) {
            Text(
                text = if (isGrid) "${cols}列网格 ▾" else "自由 ▾",
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isGrid) panelPalette.accent else cardPalette.textSecondary,
                maxLines = 1
            )
        }
        PanelDropdownMenu(
            expanded = expanded && !locked,
            onDismissRequest = { expanded = false }
        ) {
            com.example.globalminusone.service.Android12WindowKeyGuard()
            val menuPalette = checkNotNull(LocalPanelMenuPalette.current)
            Text(
                text = "容器内部排版",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = menuPalette.secondary
            )
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    ContainerLayoutType.GRID to "网格布局",
                    ContainerLayoutType.FREE to "自由布局"
                ).forEach { (mode, label) ->
                    val selected = item.containerLayout == mode
                    Surface(
                        onClick = {
                            repository.updateComponent(pageIndex, item.id) { it.copy(containerLayout = mode) }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) menuPalette.selectedChip else menuPalette.chip
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) menuPalette.accent else menuPalette.text
                        )
                    }
                }
            }
            if (isGrid) {
                GridDimensionControls(
                    columnCount = cols,
                    iconSizeDp = item.containerIconSizeDp,
                    gapDp = item.containerItemGapDp,
                    onColumns = { value -> repository.updateComponent(pageIndex, item.id) { it.copy(containerColumns = value) } },
                    onIconSize = { value -> repository.updateComponent(pageIndex, item.id) { it.copy(containerIconSizeDp = value) } },
                    onGap = { value -> repository.updateComponent(pageIndex, item.id) { it.copy(containerItemGapDp = value) } },
                    modifier = Modifier.width(264.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                    maxColumns = 8,
                    allowAutoColumns = false,
                    rowGapDp = containerRowGapDp(item),
                    onRowGap = { value -> repository.updateComponent(pageIndex, item.id) { it.copy(containerRowGapDp = value) } },
                    onRequestInput = { kind ->
                        expanded = false
                        requestedInput = kind
                    }
                )
            }
            if (onAddChild != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                PanelMenuItem(
                    text = { Text("＋ 添加子卡片", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                    onClick = {
                        expanded = false
                        onAddChild()
                    }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 全组件统一渲染器 (RenderComponentItem)
// -----------------------------------------------------------------------------------------

@Composable
fun RenderComponentItem(
    item: ComponentItem,
    pageIndex: Int,
    state: GlobalMinusOneState,
    repository: MinusOneRepository,
    systemController: SystemController,
    widgetHostManager: WidgetHostManager,
    isEditMode: Boolean,
    isSelected: Boolean,
    onBrightnessAdjusting: (Boolean) -> Unit,
    onRequestClose: () -> Unit,
    onOpenMainApp: () -> Unit,
    onOpenEditDialog: (ComponentItem) -> Unit,
    onOpenBottomNineKeyBar: () -> Unit,
    onOpenAddComponent: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openFloatingApplication = LocalOpenEmbeddedApplication.current
    val addToContainer = LocalAddToContainer.current
    val closeContainerPopup = LocalCloseContainerPopup.current
    val folderEdit = LocalFolderEditAction.current
    val folderProperties = LocalFolderOpenProperties.current
    val addAbove = LocalAddAboveComponent.current
    val exitEdit = LocalExitPanelEdit.current
    val openNewPageModal = LocalOpenNewPageModal.current

    ComponentWrapper(
        item = item,
        pageIndex = pageIndex,
        repository = repository,
        isEditMode = isEditMode,
        onOpenAddComponent = onOpenAddComponent,
        onOpenEditDialog = { selected ->
            if (folderProperties != null) folderProperties(selected)
            else { if (folderEdit == null) closeContainerPopup(); onOpenEditDialog(selected) }
        }
    ) {
        when (item.type) {
            // --- 通知栏开关类 ---
            ComponentType.SWITCH_WIFI -> RadioControlTile(com.example.globalminusone.system.RadioKind.WIFI,
                systemController, repository, !isEditMode, onRequestClose)
            ComponentType.SWITCH_BLUETOOTH -> RadioControlTile(com.example.globalminusone.system.RadioKind.BLUETOOTH,
                systemController, repository, !isEditMode, onRequestClose)
            ComponentType.SWITCH_TORCH -> {
                val isTorchOn by systemController.isTorchOn.collectAsState()
                SwitchTileComponent(
                    title = if (isTorchOn) "手电筒·开" else "手电筒",
                    icon = if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    active = isTorchOn,
                    subtitle = if (systemController.canControlTorch()) "点按切换" else "需相机权限（点按申请）",
                    onClick = { if (!isEditMode) {
                        if (systemController.canControlTorch()) systemController.toggleTorch() else systemController.requestTorchPermission()
                    } }
                )
            }
            ComponentType.SWITCH_RINGER -> {
                val ringerLabel by systemController.ringerModeLabel.collectAsState()
                SwitchTileComponent(
                    title = "声音模式",
                    subtitle = ringerLabel,
                    icon = Icons.Rounded.VolumeUp,
                    onClick = { if (!isEditMode) systemController.cycleRingerMode() }
                )
            }
            ComponentType.SWITCH_MUTE -> {
                val label by systemController.ringerModeLabel.collectAsState()
                SwitchTileComponent(active = label == "静音", subtitle = label,
                    title = "静音",
                    icon = Icons.Rounded.VolumeMute,
                    onClick = { if (!isEditMode) systemController.toggleMute() }
                )
            }
            ComponentType.SWITCH_VIBRATE -> {
                val label by systemController.ringerModeLabel.collectAsState()
                SwitchTileComponent(active = label == "震动", subtitle = label,
                    title = "震动",
                    icon = Icons.Rounded.Vibration,
                    onClick = { if (!isEditMode) systemController.toggleVibrate() }
                )
            }
            ComponentType.SWITCH_AUTO_BRIGHTNESS -> {
                var actual by remember { mutableStateOf(systemController.isAutoBrightnessEnabled()) }
                val expanded by repository.isPanelExpanded.collectAsState()
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(expanded, systemController) {
                    if (expanded) actual = systemController.isAutoBrightnessEnabled()
                }
                DisposableEffect(lifecycle, expanded, systemController) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (expanded && event == Lifecycle.Event.ON_RESUME) actual = systemController.isAutoBrightnessEnabled()
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                SwitchTileComponent(active = actual, subtitle = if (systemController.canWriteSettings()) "点按切换" else "需修改设置权限（点按申请）",
                    title = "自动亮度",
                    icon = Icons.Rounded.BrightnessMedium,
                    onClick = {
                        if (!isEditMode) {
                            systemController.toggleAutoBrightness(); actual = systemController.isAutoBrightnessEnabled()
                        }
                    }
                )
            }
            ComponentType.SWITCH_AUTO_ROTATE -> {
                var actual by remember { mutableStateOf(systemController.isAutoRotateEnabled()) }
                val expanded by repository.isPanelExpanded.collectAsState()
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(expanded, systemController) {
                    if (expanded) actual = systemController.isAutoRotateEnabled()
                }
                DisposableEffect(lifecycle, expanded, systemController) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (expanded && event == Lifecycle.Event.ON_RESUME) actual = systemController.isAutoRotateEnabled()
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                SwitchTileComponent(active = actual, subtitle = if (systemController.canWriteSettings()) "点按切换" else "需修改设置权限（点按申请）",
                    title = "自动旋转",
                    icon = Icons.Rounded.ScreenRotation,
                    onClick = {
                        if (!isEditMode) {
                            systemController.toggleAutoRotate(); actual = systemController.isAutoRotateEnabled()
                        }
                    }
                )
            }
            ComponentType.SWITCH_KEEP_AWAKE -> {
                val keepAwake by systemController.keepAwake.collectAsState()
                SwitchTileComponent(title = "面板常亮", subtitle = "仅面板显示期间", active = keepAwake,
                    icon = Icons.Rounded.Nightlight, onClick = { if (!isEditMode) systemController.toggleKeepAwake() })
            }
            ComponentType.SWITCH_LOCATION, ComponentType.SWITCH_HOTSPOT,
            ComponentType.SWITCH_NFC, ComponentType.SWITCH_AIRPLANE -> {
                SwitchTileComponent(title = item.title, subtitle = "暂不支持面板内直接控制",
                    icon = Icons.Rounded.Settings, enabled = false, onClick = {})
            }
            ComponentType.SWITCH_EYE_CARE -> {
                val eyeCare = state.advancedConfig.eyeProtectionEnabled
                SwitchTileComponent(
                    title = "全局护眼",
                    icon = Icons.Rounded.Visibility,
                    active = eyeCare,
                    onClick = {
                        if (!isEditMode) {
                            repository.updateAdvancedConfig { it.copy(eyeProtectionEnabled = !it.eyeProtectionEnabled) }
                        }
                    }
                )
            }

            // --- 系统操作栏与按键类 ---
            ComponentType.ACTION_LOCK -> {
                SwitchTileComponent(
                    title = "锁屏",
                    icon = Icons.Rounded.Lock,
                    onClick = {
                        if (!isEditMode) {
                            systemController.lockScreen()
                            onRequestClose()
                        }
                    }
                )
            }
            ComponentType.ACTION_HOME -> {
                SwitchTileComponent(
                    title = "桌面",
                    icon = Icons.Rounded.Home,
                    onClick = {
                        if (!isEditMode) {
                            systemController.goHome()
                            onRequestClose()
                        }
                    }
                )
            }
            ComponentType.ACTION_BACK -> {
                SwitchTileComponent(
                    title = "返回",
                    icon = Icons.Rounded.ArrowBack,
                    onClick = {
                        if (!isEditMode) {
                            onRequestClose()
                        }
                    }
                )
            }
            ComponentType.ACTION_RECENTS -> {
                SwitchTileComponent(
                    title = "最近任务",
                    icon = Icons.Rounded.History,
                    onClick = {
                        if (!isEditMode) {
                            systemController.openRecentApps()
                            onRequestClose()
                        }
                    }
                )
            }
            ComponentType.ACTION_NOTIFICATIONS -> {
                SwitchTileComponent(
                    title = "通知栏",
                    icon = Icons.Rounded.Notifications,
                    onClick = {
                        if (!isEditMode) {
                            systemController.expandNotifications()
                            onRequestClose()
                        }
                    }
                )
            }
            ComponentType.ACTION_SPLIT_SCREEN -> {
                SwitchTileComponent(
                    title = "分屏暂不可用",
                    icon = Icons.Rounded.ViewAgenda,
                    subtitle = "功能待修复，可编辑或移除此卡片",
                    enabled = false,
                    onClick = {}
                )
            }
            ComponentType.ACTION_SCREENSHOT -> {
                SwitchTileComponent(
                    title = "截屏",
                    icon = Icons.Rounded.Screenshot,
                    onClick = {
                        if (!isEditMode) {
                            if (com.example.globalminusone.service.GlobalAccessibilityService.instance == null) {
                                systemController.takeScreenshot()
                            } else {
                                onRequestClose()
                                Handler(Looper.getMainLooper()).postDelayed({
                                    systemController.takeScreenshot()
                                }, 300L)
                            }
                        }
                    }
                )
            }
            ComponentType.ACTION_VOICE_ASSIST -> {
                SwitchTileComponent(
                    title = "语音助手",
                    icon = Icons.Rounded.Mic,
                    onClick = {
                        if (!isEditMode) {
                            onRequestClose()
                            systemController.launchVoiceAssistant()
                        }
                    }
                )
            }
            ComponentType.ACTION_WECHAT_SCAN -> {
                SwitchTileComponent(
                    title = "微信扫一扫",
                    icon = Icons.Rounded.CenterFocusStrong,
                    onClick = {
                        if (!isEditMode) {
                            AppAndIntentLauncher.launchQuickAction(
                                context,
                                QuickActionItem(
                                    title = "微信扫一扫",
                                    intentUri = "weixin://dl/scan",
                                    packageName = "com.tencent.mm"
                                )
                            )
                            onRequestClose()
                        }
                    }
                )
            }
            ComponentType.OPERATION_BAR -> {
                OperationBarComponent(
                    isEditMode = state.editMode,
                    onToggleEditMode = {
                        val newMode = !state.editMode
                        if (newMode) repository.setEditMode(true) else exitEdit?.invoke() ?: repository.setEditMode(false)
                    },
                    onOpenAddComponent = { if (addAbove != null) addAbove(item.id) else onOpenAddComponent() },
                    onAddPage = {
                        openNewPageModal?.invoke()
                            ?: repository.addPage(com.example.globalminusone.data.PageLayoutType.FREE)
                    },
                    onOpenBottomNineKeyBar = onOpenBottomNineKeyBar,
                    onOpenMainApp = onOpenMainApp
                )
            }

            // --- 快捷方式与应用类 ---
            ComponentType.SHELL_COMMAND -> ShellCommandCard(item, enabled = !isEditMode)
            ComponentType.CUSTOM_SHORTCUT, ComponentType.STATIC_SHORTCUT -> {
                CustomShortcutComponent(
                    item = item,
                    onClick = {
                        if (!isEditMode) {
                            AppAndIntentLauncher.launchQuickAction(
                                context,
                                QuickActionItem(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    iconName = item.iconName,
                                    colorHex = String.format("#%06X", (0xFFFFFF and item.colorHex.toInt())),
                                    intentUri = item.intentUri,
                                    packageName = item.packageName
                                )
                            )
                            if (state.advancedConfig.autoCloseOnWidgetClick) {
                                onRequestClose()
                            }
                        }
                    }
                )
            }
            ComponentType.APP_ICON -> {
                AppIconComponent(
                    packageName = item.packageName,
                    title = item.title,
                    onClick = {
                        if (!isEditMode) {
                            if (item.appLaunchMode == com.example.globalminusone.data.AppLaunchMode.FLOATING) {
                                scope.launch {
                                    val target = resolveFloatingApplication(context, item.packageName, item.title)
                                    if (target != null && openFloatingApplication != null) openFloatingApplication(target)
                                    else Toast.makeText(context, "无法以小窗打开此应用，未启动普通窗口", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                AppAndIntentLauncher.launchApp(context, item.packageName)
                                if (state.advancedConfig.autoCloseOnWidgetClick) onRequestClose()
                            }
                        }
                    }
                )
            }
            ComponentType.CONTAINER_BOX, ComponentType.FOLDER -> {
                ContainerBoxComponent(
                    item = item,
                    pageIndex = pageIndex,
                    state = state,
                    repository = repository,
                    systemController = systemController,
                    isEditMode = isEditMode,
                    onRequestClose = onRequestClose,
                    onOpenMainApp = onOpenMainApp,
                    nestedContent = {
                        NestedContainerComponents(
                            container = item, pageIndex = pageIndex, state = state, repository = repository,
                            onOpenEditDialog = onOpenEditDialog,
                            content = { child -> RenderComponentItem(
                                item = child, pageIndex = pageIndex, state = state, repository = repository,
                                systemController = systemController, widgetHostManager = widgetHostManager,
                                isEditMode = com.example.globalminusone.data.containerEditingMode(state.editMode,
                                    if (LocalFolderEditAction.current != null) LocalFolderEditing.current else null,
                                    state.advancedConfig.layoutLocked), isSelected = state.selectedComponentId == child.id,
                                onBrightnessAdjusting = onBrightnessAdjusting, onRequestClose = onRequestClose,
                                onOpenMainApp = onOpenMainApp, onOpenEditDialog = onOpenEditDialog,
                                onOpenBottomNineKeyBar = onOpenBottomNineKeyBar,
                                onOpenAddComponent = { addToContainer?.invoke(item.id) }
                            ) }
                        )
                    }
                )
            }
            ComponentType.APP_DRAWER_CARD -> {
                AppDrawerComponent(
                    isEditMode = isEditMode,
                    onRequestClose = onRequestClose
                )
            }
            ComponentType.RECENT_APPS_CARD -> {
                RecentAppsComponent(
                    isEditMode = isEditMode,
                    onRequestClose = onRequestClose
                )
            }

            // --- 桌面小部件 ---
            ComponentType.APP_WIDGET -> {
                AppWidgetComponent(
                    item = item,
                    widgetHostManager = widgetHostManager
                )
            }

            // --- 调节滑条类 ---
            ComponentType.BRIGHTNESS_BAR -> {
                BrightnessBarComponent(
                    systemController = systemController,
                    onBrightnessAdjusting = onBrightnessAdjusting,
                    orientation = item.orientation
                )
            }
            ComponentType.VOLUME_BAR -> {
                VolumeBarComponent(
                    systemController = systemController,
                    orientation = item.orientation
                )
            }

            // --- 统计与硬件状态卡片类 ---
            ComponentType.STAT_BATTERY_BAR -> {
                StatBatteryBarComponent(orientation = item.orientation)
            }
            ComponentType.STAT_BATTERY_DETAIL -> {
                StatBatteryDetailComponent()
            }
            ComponentType.STAT_RAM -> {
                StatRamComponent(orientation = item.orientation)
            }
            ComponentType.STAT_ROM -> {
                StatRomComponent(orientation = item.orientation)
            }
            ComponentType.STAT_UPTIME -> {
                StatUptimeComponent()
            }
            ComponentType.STAT_SCREEN_TIME -> {
                StatScreenTimeComponent()
            }
            ComponentType.STAT_APP_COUNT -> {
                RealAppCount()
            }
            ComponentType.STAT_NOTIF_COUNT -> {
                val statistics = remember(context) { com.example.globalminusone.data.ActivityStatisticsStore.get(context) }
                val counts by statistics.counts.collectAsState()
                val statsVisible by repository.isPanelExpanded.collectAsState()
                LaunchedEffect(statistics, statsVisible) {
                    if (!statsVisible) return@LaunchedEffect
                    while (true) {
                        statistics.refresh()
                        kotlinx.coroutines.delay(30_000L)
                    }
                }
                StatCountComponent("${counts.notifications}", "今日通知·已选应用", Icons.Rounded.Notifications)
            }
            ComponentType.STAT_DATA_TRAFFIC -> {
                RealTrafficCount()
            }
            ComponentType.STAT_UNLOCK_COUNT -> {
                val statistics = remember(context) { com.example.globalminusone.data.ActivityStatisticsStore.get(context) }
                val counts by statistics.counts.collectAsState()
                val statsVisible by repository.isPanelExpanded.collectAsState()
                LaunchedEffect(statistics, statsVisible) {
                    if (!statsVisible) return@LaunchedEffect
                    while (true) {
                        statistics.refresh()
                        kotlinx.coroutines.delay(30_000L)
                    }
                }
                StatCountComponent("${counts.unlocks}", "今日解锁·服务期间", Icons.Rounded.Lock)
            }

            // --- 文本、一言、便签、剪贴板、图片、网站 ---
            ComponentType.HITOKOTO_CARD -> {
                HitokotoCardComponent(
                    state = state,
                    repository = repository
                )
            }
            ComponentType.NOTE_CARD -> {
                NoteCardComponent(
                    item = item,
                    pageIndex = pageIndex,
                    repository = repository
                )
            }
            ComponentType.TEXT_LABEL -> {
                TextLabelComponent(
                    item = item,
                    pageIndex = pageIndex,
                    repository = repository
                )
            }
            ComponentType.CLIPBOARD_CARD -> {
                ClipboardCardComponent(
                    state = state,
                    repository = repository,
                    systemController = systemController
                )
            }
            ComponentType.SINGLE_IMAGE -> {
                val selectImage = LocalSelectImage.current
                SingleImageComponent(
                    item = item,
                    onOpenMainApp = { selectImage(item) }
                )
            }
            ComponentType.PHOTO_CUBE -> {
                val panelVisible by repository.isPanelExpanded.collectAsState()
                val visible = panelVisible && LocalPageViewportActive.current
                PhotoCubeCard(item, active = visible, playing = visible && !isEditMode,
                    onChoose = {
                        val latest = repository.state.value
                        val page = latest.pages.firstOrNull { it.components.findComponent(item.id) != null }
                        if (page != null && !latest.advancedConfig.layoutLocked) {
                            com.example.globalminusone.PhotoCubePickerActivity.start(context, page.id, item.id)
                        }
                    },
                    onIntervalChange = { seconds ->
                        repository.updateComponent(pageIndex, item.id) { it.copy(photoCubeIntervalSeconds = seconds.coerceIn(2, 60)) }
                    })
            }
            ComponentType.FILE_SHORTCUT -> {
                val openFileShortcut = LocalOpenFileShortcut.current
                CustomShortcutComponent(
                    item = item, enabled = !isEditMode, fallbackIcon = Icons.Rounded.Folder,
                    onClick = { closeContainerPopup(); openFileShortcut(item) }
                )
            }
            ComponentType.WEBSITE_LINK -> {
                val openBrowserUrl = LocalOpenBrowserUrl.current
                CustomShortcutComponent(item = item, enabled = !isEditMode, fallbackIcon = Icons.Language,
                    onClick = { closeContainerPopup(); openBrowserUrl(item.intentUri) })
            }
            ComponentType.FILE_BROWSER -> {
                val openFiles = LocalOpenFileBrowser.current
                SwitchTileComponent(
                    title = "文件浏览",
                    icon = Icons.Rounded.Folder,
                    subtitle = "手机存储与已授权目录",
                    enabled = !isEditMode,
                    onClick = openFiles
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 单个组件的自适应子项实现
// -----------------------------------------------------------------------------------------

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun SwitchTileComponent(
    title: String,
    icon: ImageVector,
    active: Boolean = false,
    subtitle: String = "",
    enabled: Boolean = !LocalIsEditMode.current,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val cardColors = LocalCardColors.current
    val circleBg = if (active) cardColors.accent else cardColors.iconBackground
    val iconTint = if (active) Color.White else cardColors.textPrimary

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(enabled = enabled, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        val h = maxHeight
        val overrideIconDp = LocalFolderIconSizeDp.current
        val isVeryCompact = h < 46.dp
        val isCompact = h in 46.dp..62.dp

        val circleSize = when {
            overrideIconDp != null -> minOf(overrideIconDp.coerceIn(24, 60).dp, (h - if (isVeryCompact) 6.dp else 18.dp).coerceAtLeast(22.dp))
            isVeryCompact -> 26.dp
            isCompact -> 30.dp
            else -> 34.dp
        }
        val iconSize = when {
            overrideIconDp != null -> (circleSize * 0.56f).coerceIn(14.dp, 34.dp)
            isVeryCompact -> 16.dp
            isCompact -> 17.dp
            else -> 19.dp
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(circleBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
            }
            if (!isVeryCompact) {
                Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 3.dp))
                Text(
                    text = title,
                    color = cardColors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (subtitle.isNotBlank() && !isCompact) {
                    Text(
                        text = subtitle,
                        color = cardColors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun OperationBarComponent(
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    onOpenAddComponent: () -> Unit,
    onAddPage: () -> Unit,
    onOpenBottomNineKeyBar: () -> Unit,
    onOpenMainApp: () -> Unit
) {
    val cardColors = LocalCardColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onOpenBottomNineKeyBar),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(cardColors.trackColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "底栏菜单",
                tint = cardColors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun OperationBarButton(
    title: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    val cardColors = LocalCardColors.current
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (active) cardColors.accent.copy(alpha = 0.25f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (active) cardColors.accent else cardColors.textPrimary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = title,
                color = if (active) cardColors.accent else cardColors.textPrimary,
                fontSize = 10.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun ContainerBoxComponent(
    item: ComponentItem,
    pageIndex: Int = 0,
    state: GlobalMinusOneState = GlobalMinusOneState(),
    repository: MinusOneRepository? = null,
    systemController: SystemController? = null,
    isEditMode: Boolean = LocalIsEditMode.current,
    onRequestClose: () -> Unit = {},
    onOpenMainApp: () -> Unit = {},
    nestedContent: (@Composable () -> Unit)? = null
) {
    val cardColors = LocalCardColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cols = item.containerColumns.coerceIn(2, 6)
    if (item.type == ComponentType.FOLDER) {
        FolderContainerCard(item, pageIndex, state, repository, systemController, isEditMode,
            onRequestClose, onOpenMainApp, nestedContent)
        return
    }
    val actions = item.containerActions
    var editingAction by remember(item.id) { mutableStateOf<QuickActionItem?>(null) }
    val addToContainer = LocalAddToContainer.current
    editingAction?.let { action ->
        if (repository != null) EditContainerActionDialog(repository, pageIndex, item.id, action) { editingAction = null }
    }

    if (item.containerCompact) {
        CompactContainerBubble(
            item, state, repository, systemController, isEditMode, onRequestClose, onOpenMainApp, pageIndex, nestedContent
        )
        return
    }

    if (actions.isEmpty() && item.containerComponents.isEmpty()) {
        val modeLabel = if (item.containerLayout == ContainerLayoutType.GRID) {
            "${item.containerColumns.coerceIn(1, 8)}列网格"
        } else {
            "自由布局"
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !LocalLayoutLocked.current && addToContainer != null) { addToContainer?.invoke(item.id) }
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = if (LocalLayoutLocked.current) "暂无子卡片" else "＋ 添加子卡片",
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (LocalLayoutLocked.current) cardColors.textSecondary else cardColors.accent
            )
            Text(
                text = "当前：$modeLabel",
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = cardColors.textSecondary
            )
        }
        return
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(
        if (item.containerLayout == ContainerLayoutType.GRID) containerRowGapDp(item).coerceAtLeast(0).dp else 8.dp)) {
        if (actions.isNotEmpty() && repository != null) LegacyContainerActionGrid(item, pageIndex, state, repository,
            systemController, isEditMode, onRequestClose, onOpenMainApp, onEdit = { editingAction = it })
        else if (actions.isNotEmpty()) actions.forEach { Text(it.title, color = cardColors.textPrimary) }
        if (item.containerComponents.isNotEmpty()) nestedContent?.invoke()
    }
}

@Composable
fun RenderContainerActionIcon(
    action: QuickActionItem,
    eyeCareActive: Boolean,
    modifier: Modifier = Modifier.size(38.dp)
) {
    val context = LocalContext.current
    val cardColors = LocalCardColors.current
    val title = action.title
    val iconName = action.iconName.lowercase()
    val pkg = action.packageName ?: ""
    val uri = action.intentUri ?: ""
    val effectivePkg = remember(pkg, uri) { AppAndIntentLauncher.resolveEffectivePackageName(pkg, uri) }
    val cacheGen by AppAndIntentLauncher.cacheGeneration.collectAsState()
    val cachedBitmap = remember(effectivePkg, cacheGen) {
        if (effectivePkg.isNotBlank()) AppAndIntentLauncher.peekAppIconBitmap(effectivePkg) else null
    }
    var loadedBitmap by remember(effectivePkg) { mutableStateOf(cachedBitmap) }
    val resolvedBitmap = cachedBitmap ?: loadedBitmap
    LaunchedEffect(context, effectivePkg) {
        if (effectivePkg.isNotBlank() && resolvedBitmap == null &&
            !AppAndIntentLauncher.isKnownMissingPackage(effectivePkg)
        ) {
            AppAndIntentLauncher.getAppInfoCached(context, effectivePkg)
            loadedBitmap = AppAndIntentLauncher.peekAppIconBitmap(effectivePkg)
        }
    }

    when {
        iconName.startsWith("custom:") -> Icon(getQuickActionIcon(iconName), contentDescription = title,
            tint = cardColors.accent, modifier = modifier)
        title == "QQ" || iconName == "qq" || pkg == "com.tencent.mobileqq" -> {
            QqAvatarIcon(modifier = modifier)
        }
        title == "微信" && (pkg == "com.tencent.mm" || uri.startsWith("weixin://")) -> {
            WechatAvatarIcon(modifier = modifier)
        }
        title == "酷安" || iconName == "coolapk" || pkg == "com.coolapk.market" -> {
            CoolapkAvatarIcon(modifier = modifier)
        }
        title == "全局负一屏" || pkg == "com.example.globalminusone" -> {
            GlobalMinusOneAvatarIcon(modifier = modifier)
        }
        title == "区域截屏" || iconName == "crop" -> {
            CropShortcutIcon(modifier = modifier)
        }
        title == "全局护眼" || iconName == "visibility" || iconName == "eye" -> {
            EyeShortcutIcon(active = eyeCareActive, modifier = modifier)
        }
        title == "播放/暂停" || iconName == "play_pause" || iconName == "playarrow" -> {
            PlayPauseShortcutIcon(modifier = modifier)
        }
        title == "语音助手" || iconName == "mic" -> {
            VoiceShortcutIcon(modifier = modifier)
        }
        (title == "扫一扫" || title == "付款码") && (pkg.contains("alipay", ignoreCase = true) || uri.contains("alipay", ignoreCase = true) || iconName.contains("alipay")) -> {
            AlipayPayAvatarIcon(modifier = modifier)
        }
        (title == "扫一扫" || title == "付款码") && (pkg.contains("tencent", ignoreCase = true) || uri.contains("weixin", ignoreCase = true) || iconName.contains("wechat")) -> {
            WechatPayAvatarIcon(modifier = modifier)
        }
        resolvedBitmap != null -> {
            Image(
                bitmap = resolvedBitmap,
                contentDescription = title,
                modifier = modifier.clip(RoundedCornerShape(10.dp))
            )
        }
        title in listOf("百度", "微博", "京东", "淘宝") || uri.startsWith("http://") || uri.startsWith("https://") -> {
            WebLinkAvatarIcon(modifier = modifier)
        }
        else -> {
            Box(
                modifier = modifier
                    .clip(CircleShape)
                    .background(cardColors.iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = title,
                    tint = cardColors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

fun handleContainerActionClick(
    action: QuickActionItem,
    context: Context,
    repository: MinusOneRepository?,
    systemController: SystemController?,
    scope: kotlinx.coroutines.CoroutineScope,
    onRequestClose: () -> Unit,
    onOpenMainApp: () -> Unit
) {
    when (action.intentUri.trim()) {
        "action:com.example.globalminusone.MAIN" -> {
            onRequestClose()
            onOpenMainApp()
        }
        "action:screenshot_region" -> {
            if (systemController != null) {
                systemController.takeRegionScreenshot()
            } else {
                AppAndIntentLauncher.launchQuickAction(context, action)
            }
        }
        "action:eye_protection" -> {
            repository?.updateAdvancedConfig { it.copy(eyeProtectionEnabled = !it.eyeProtectionEnabled) }
            Toast.makeText(context, "已切换护眼模式", Toast.LENGTH_SHORT).show()
        }
        "action:media_play_pause" -> {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                val wasActive = audioManager?.isMusicActive == true
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                systemController?.performTickHaptic()
                if (!wasActive) {
                    Toast.makeText(context, "已发送播放/暂停指令（当前无活跃媒体）", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "媒体控制失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        "action:voice_assist" -> {
            onRequestClose()
            systemController?.launchVoiceAssistant()
        }
        else -> {
            if (AppAndIntentLauncher.launchQuickAction(context, action)) onRequestClose()
        }
    }
}

@Composable
fun AppDrawerComponent(
    isEditMode: Boolean = LocalIsEditMode.current,
    onRequestClose: () -> Unit
) {
    IndexedAppDrawer(isEditMode, onRequestClose)
}

@Composable
fun RecentAppsComponent(
    isEditMode: Boolean = LocalIsEditMode.current,
    onRequestClose: () -> Unit
) {
    RealRecentApps(isEditMode, onRequestClose)
}


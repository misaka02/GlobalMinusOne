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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
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

@Immutable
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
internal val LocalCloseContainerPopup = staticCompositionLocalOf<() -> Unit> { {} }
/** Display treatment only. Menus and property editors always receive the saved item. */
internal val LocalContainerGridIconCell = staticCompositionLocalOf { false }

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
        "crop_free" -> Icons.Rounded.CropFree
        "translate" -> Icons.Rounded.Translate
        "auto_awesome" -> Icons.Rounded.AutoAwesome
        "visibility" -> Icons.Rounded.Visibility
        "person" -> Icons.Rounded.Person
        "photo_library" -> Icons.Rounded.PhotoLibrary
        "content_paste" -> Icons.Rounded.ContentPaste
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
    ComponentWrapperContent(item, modifier, pageIndex, repository?.let { rememberCardLayoutActions(it) },
        repository?.let { rememberAppCardMenuActions(it) }, repository?.let { rememberCardDestinationPicker(it) },
        isEditMode, onOpenAddComponent, onOpenEditDialog, content)
}

@Composable
internal fun ComponentWrapperContent(
    item: ComponentItem, modifier: Modifier = Modifier, pageIndex: Int = 0,
    layoutActions: CardLayoutActions?, menuActions: AppCardMenuActions?, destinationPicker: CardDestinationPicker?,
    isEditMode: Boolean = false, onOpenAddComponent: (() -> Unit)? = null,
    onOpenEditDialog: ((ComponentItem) -> Unit)? = null, content: @Composable () -> Unit
) {
    val panelPalette = LocalPanelPalette.current
    val theme = panelPalette.themeConfig
    val bareGridIcon = LocalContainerGridIconCell.current && (
        item.type in setOf(
            ComponentType.APP_ICON, ComponentType.CUSTOM_SHORTCUT, ComponentType.STATIC_SHORTCUT,
            ComponentType.FILE_SHORTCUT, ComponentType.WEBSITE_LINK, ComponentType.FOLDER
        ) || com.example.globalminusone.data.isSingleIconLauncherItem(item)
        )
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
        val pageId = layoutActions?.current?.pages?.getOrNull(pageIndex)?.id ?: return
        destructiveRequest = CardDestructiveRequest(pageId, item.id, item.title.ifBlank { "卡片" }, action)
    }
    destructiveRequest?.let { request -> layoutActions?.let { repo ->
        CardDestructiveConfirmationContent(request, repo) { destructiveRequest = null }
    } }
    val addToContainer = LocalPanelActions.current.addToContainer
    val addAboveComponent = LocalAddAboveComponent.current
    val contentDrawsSurface = bareGridIcon || item.type == ComponentType.FOLDER ||
        (item.type == ComponentType.CONTAINER_BOX && item.containerCompact)
    val wrapperBgColor = if (contentDrawsSurface) Color.Transparent else bgColor

    val isWrap = item.autoHeight && !item.containerCompact && (item.type == ComponentType.CONTAINER_BOX || item.type == ComponentType.OPERATION_BAR)
    val layoutLocked = LocalLayoutLocked.current
    val folderEdit = LocalFolderEditAction.current
    val insideContainerChild = LocalInContainerChild.current
    val ordinaryHoldEditsLayout = !isAppContextMenuCardType(item.type) &&
        !com.example.globalminusone.data.isSingleIconLauncherItem(item)
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
        LocalAppMenuEnvironment provides menuActions?.let { menu -> destinationPicker?.let { picker ->
            AppMenuEnvironment(menu, picker, layoutActions?.current?.pages?.getOrNull(pageIndex)?.id.orEmpty(), item)
        } },
        LocalCardColors provides cardPalette,
        LocalIsEditMode provides isEditMode,
        LocalAppEditAction provides onOpenEditDialog?.let { callback -> { callback(item) } },
        LocalAppEditLabel provides when (item.type) {
            ComponentType.APP_DRAWER_CARD -> "编辑抽屉卡片"
            ComponentType.APP_KEYBOARD_LAUNCHER -> "编辑键盘快启卡片"
            ComponentType.RECENT_APPS_CARD -> "编辑最近运行卡片"
            ComponentType.BRIGHTNESS_BAR -> "编辑亮度卡片"
            ComponentType.VOLUME_BAR -> "编辑音量卡片"
            ComponentType.CLIPBOARD_CARD -> "编辑剪贴板卡片"
            ComponentType.NOTE_CARD -> "编辑便签卡片"
            ComponentType.APP_WIDGET -> "编辑小部件卡片"
            ComponentType.STAT_BATTERY_DETAIL -> "编辑电池详情卡片"
            ComponentType.SINGLE_IMAGE -> "编辑图片卡片"
            ComponentType.PHOTO_CUBE -> "编辑魔方相册卡片"
            ComponentType.HITOKOTO_CARD -> "编辑一言卡片"
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
                    layoutActions != null && !isEditMode && !layoutLocked && ordinaryHoldEditsLayout && !insideContainerChild,
                    requireIdleOwnership = !com.example.globalminusone.data.isSliderBarType(item.type) &&
                        item.type != ComponentType.APP_WIDGET
                ) {
                    if (folderEdit != null) folderEdit() else layoutActions?.setEditMode(true)
                    layoutActions?.selectComponent(item.id)
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
                                ContainerQuickLayoutContent(
                                    item = item,
                                    pageIndex = pageIndex,
                                    layoutActions = layoutActions,
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
                                com.example.globalminusone.ui.Android12WindowKeyGuard()
                                val menuPalette = checkNotNull(LocalPanelMenuPalette.current)
                                val pageLayoutType = layoutActions?.observe()?.pages?.getOrNull(pageIndex)?.layoutType
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
                                                    layoutActions?.updateComponent(pageIndex, item.id) { cur ->
                                                        if (cur.type in com.example.globalminusone.data.POPOUT_CAPABLE_CARD_TYPES) {
                                                            if (sx == 1 && sy == 1) {
                                                                com.example.globalminusone.data.withCardPresentationMode(
                                                                    cur,
                                                                    com.example.globalminusone.data.LauncherCardPresentationMode.POPUP_ICON
                                                                )
                                                            } else {
                                                                val base = if (com.example.globalminusone.data.isSingleIconLauncherItem(cur)) {
                                                                    com.example.globalminusone.data.withCardPresentationMode(
                                                                        cur,
                                                                        com.example.globalminusone.data.LauncherCardPresentationMode.INLINE_CARD
                                                                    )
                                                                } else cur
                                                                base.copy(gridSpanX = sx, gridSpanY = sy, autoHeight = false)
                                                            }
                                                        } else {
                                                            cur.copy(gridSpanX = sx, gridSpanY = sy, autoHeight = false)
                                                        }
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
                                        enabled = addToContainer != null && layoutActions != null,
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
                                            layoutActions?.updateComponent(pageIndex, item.id) {
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
                                            layoutActions?.updateComponent(pageIndex, item.id) {
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
                                                    layoutActions?.updateComponent(pageIndex, item.id) {
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
                                if (item.type in com.example.globalminusone.data.POPOUT_CAPABLE_CARD_TYPES) {
                                    val isPopOut = com.example.globalminusone.data.isSingleIconLauncherItem(item)
                                    val nextMode = if (isPopOut) {
                                        com.example.globalminusone.data.LauncherCardPresentationMode.INLINE_CARD
                                    } else {
                                        com.example.globalminusone.data.LauncherCardPresentationMode.POPUP_ICON
                                    }
                                    PanelMenuItem(
                                        text = { Text("切换为${nextMode.label}", fontSize = 13.sp) },
                                        enabled = layoutActions != null && !layoutLocked,
                                        onClick = {
                                            showMenu = false
                                            layoutActions?.updateComponent(pageIndex, item.id) {
                                                com.example.globalminusone.data.withCardPresentationMode(it, nextMode)
                                            }
                                        }
                                    )
                                }
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
                            ContainerQuickLayoutContent(
                                item = item,
                                pageIndex = pageIndex,
                                layoutActions = layoutActions,
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


// -----------------------------------------------------------------------------------------
// 兼容组合入口：装配各卡片域的数据和动作，实际 Content 叶节点不接收仓库或系统总控。
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
    val panelActions = LocalPanelActions.current
    val openFloatingApplication = panelActions.openEmbeddedApplication
    val addToContainer = panelActions.addToContainer
    val closeContainerPopup = LocalCloseContainerPopup.current
    val folderEdit = LocalFolderEditAction.current
    val folderProperties = LocalFolderOpenProperties.current
    val addAbove = LocalAddAboveComponent.current
    val exitEdit = panelActions.exitPanelEdit
    val openNewPageModal = panelActions.openNewPageModal

    ComponentWrapperContent(
        item = item,
        pageIndex = pageIndex,
        layoutActions = rememberCardLayoutActions(repository),
        menuActions = rememberAppCardMenuActions(repository),
        destinationPicker = rememberCardDestinationPicker(repository),
        isEditMode = isEditMode,
        onOpenAddComponent = onOpenAddComponent,
        onOpenEditDialog = { selected ->
            if (folderProperties != null) folderProperties(selected)
            else { if (folderEdit == null) closeContainerPopup(); onOpenEditDialog(selected) }
        }
    ) {
        when (item.type) {
            // --- 通知栏开关类 ---
            ComponentType.SWITCH_WIFI -> RadioControlTileContent(com.example.globalminusone.system.RadioKind.WIFI,
                rememberRadioCardControl(systemController), rememberRadioAuthorizationActions(systemController),
                { repository.isPanelExpanded }, !isEditMode, onRequestClose)
            ComponentType.SWITCH_BLUETOOTH -> RadioControlTileContent(com.example.globalminusone.system.RadioKind.BLUETOOTH,
                rememberRadioCardControl(systemController), rememberRadioAuthorizationActions(systemController),
                { repository.isPanelExpanded }, !isEditMode, onRequestClose)
            ComponentType.SWITCH_TORCH, ComponentType.SWITCH_RINGER, ComponentType.SWITCH_MUTE,
            ComponentType.SWITCH_VIBRATE, ComponentType.SWITCH_AUTO_BRIGHTNESS, ComponentType.SWITCH_AUTO_ROTATE,
            ComponentType.SWITCH_KEEP_AWAKE -> {
                SystemSwitchCard(item, isEditMode, rememberSwitchCardControls(systemController)) { repository.isPanelExpanded }
            }
            ComponentType.SWITCH_LOCATION, ComponentType.SWITCH_HOTSPOT,
            ComponentType.SWITCH_NFC, ComponentType.SWITCH_AIRPLANE -> {
                SystemActionCard(item, isEditMode, rememberSystemActionCardCommands(systemController, context), onRequestClose)
            }
            ComponentType.SWITCH_EYE_CARE -> {
                SystemEyeCareCard(state.advancedConfig.eyeProtectionEnabled, isEditMode) {
                    repository.updateAdvancedConfig { it.copy(eyeProtectionEnabled = !it.eyeProtectionEnabled) }
                }
            }

            // --- 系统操作栏与按键类 ---
            ComponentType.ACTION_LOCK, ComponentType.ACTION_HOME, ComponentType.ACTION_BACK,
            ComponentType.ACTION_RECENTS, ComponentType.ACTION_NOTIFICATIONS, ComponentType.ACTION_SPLIT_SCREEN,
            ComponentType.ACTION_SCREENSHOT, ComponentType.ACTION_VOICE_ASSIST, ComponentType.ACTION_WECHAT_SCAN,
            ComponentType.ACTION_REGION_SCREENSHOT, ComponentType.ACTION_SCREEN_ASSISTANT, ComponentType.ACTION_BREENO_TOUCH -> {
                SystemActionCard(item, isEditMode, rememberSystemActionCardCommands(systemController, context), onRequestClose)
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
                ContainerBoxContent(
                    item = item,
                    pageIndex = pageIndex,
                    state = state.cardLayoutSnapshot(),
                    layoutActions = rememberCardLayoutActions(repository),
                    propertyEditors = rememberCardPropertyEditors(repository, systemController),
                    destinationPicker = rememberCardDestinationPicker(repository),
                    legacyActions = rememberLegacyContainerActions(repository, systemController),
                    eyeProtectionEnabled = state.advancedConfig.eyeProtectionEnabled,
                    isEditMode = isEditMode,
                    onRequestClose = onRequestClose,
                    onOpenMainApp = onOpenMainApp,
                    nestedContent = {
                        NestedContainerContent(
                            container = item, pageIndex = pageIndex, state = state.cardLayoutSnapshot(), layoutActions = rememberCardLayoutActions(repository),
                            destinationPicker = rememberCardDestinationPicker(repository),
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
                    item = item,
                    isEditMode = isEditMode,
                    onRequestClose = onRequestClose
                )
            }
            ComponentType.APP_KEYBOARD_LAUNCHER -> {
                AppKeyboardLauncherCard(
                    item = item,
                    isEditMode = isEditMode,
                    onRequestClose = {
                        if (state.advancedConfig.autoCloseOnWidgetClick) onRequestClose()
                    }
                )
            }
            ComponentType.RECENT_APPS_CARD -> {
                FunctionalCardPopOutWrapper(
                    item = item,
                    podKind = FloatingPodKind.RECENT_APPS,
                    defaultTitle = "最近运行",
                    icon = Icons.Rounded.History,
                    isEditMode = isEditMode,
                    horizontalAnchor = com.example.globalminusone.data.FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
                    podWidthDp = 312f,
                    podHeightDp = 300f,
                    showHeader = true,
                    inlineContent = {
                        RecentAppsComponent(
                            isEditMode = isEditMode,
                            onRequestClose = onRequestClose
                        )
                    },
                    podContent = { dismiss ->
                        RecentAppsComponent(
                            isEditMode = false,
                            onRequestClose = {
                                dismiss()
                                onRequestClose()
                            }
                        )
                    }
                )
            }

            // --- 桌面小部件 ---
            ComponentType.APP_WIDGET -> {
                val widgetFactory = rememberWidgetCardViewFactory(widgetHostManager)
                val folderEdit = LocalFolderEditAction.current
                val insideContainerChild = LocalInContainerChild.current
                val onWidgetLongPressEdit: (() -> Unit)? = if (
                    !isEditMode && !state.advancedConfig.layoutLocked && !insideContainerChild
                ) {
                    {
                        if (folderEdit != null) folderEdit() else repository.setEditMode(true)
                        repository.selectComponent(item.id)
                    }
                } else null
                FunctionalCardPopOutWrapper(
                    item = item,
                    podKind = FloatingPodKind.APP_WIDGET,
                    defaultTitle = item.title.ifBlank { "小部件" },
                    icon = Icons.Rounded.Widgets,
                    isEditMode = isEditMode,
                    horizontalAnchor = com.example.globalminusone.data.FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
                    podWidthDp = 312f,
                    podHeightDp = 220f,
                    showHeader = true,
                    compactWidthThresholdDp = 0f,
                    compactHeightThresholdDp = 0f,
                    inlineContent = {
                        AppWidgetCardContent(
                            item = item,
                            factory = widgetFactory,
                            onLongPressEdit = onWidgetLongPressEdit
                        )
                    },
                    podContent = {
                        AppWidgetCardContent(
                            item = item,
                            factory = widgetFactory
                        )
                    }
                )
            }

            // --- 调节滑条类 ---
            ComponentType.BRIGHTNESS_BAR -> {
                val brightnessControl = rememberBrightnessCardControl(systemController)
                val expansionFlow = { source: android.content.Context -> MinusOneRepository.getInstance(source).isPanelExpanded }
                FunctionalCardPopOutWrapper(
                    item = item,
                    podKind = FloatingPodKind.BRIGHTNESS,
                    defaultTitle = "亮度",
                    icon = Icons.Rounded.BrightnessMedium,
                    isEditMode = isEditMode,
                    horizontalAnchor = com.example.globalminusone.data.FloatingPodHorizontalAnchor.ALIGN_TRIGGER_X,
                    podWidthDp = 76f,
                    podHeightDp = 228f,
                    showHeader = false,
                    compactWidthThresholdDp = 96f,
                    compactHeightThresholdDp = 96f,
                    inlineContent = {
                        BrightnessBarContent(
                            control = brightnessControl,
                            panelExpansion = expansionFlow,
                            onBrightnessAdjusting = onBrightnessAdjusting,
                            orientation = item.orientation
                        )
                    },
                    podContent = {
                        BrightnessBarContent(
                            control = brightnessControl,
                            panelExpansion = expansionFlow,
                            onBrightnessAdjusting = onBrightnessAdjusting,
                            orientation = com.example.globalminusone.data.ComponentOrientation.VERTICAL
                        )
                    }
                )
            }
            ComponentType.VOLUME_BAR -> {
                val volumeControl = rememberVolumeCardControl(systemController)
                val expansionFlow = { source: android.content.Context -> MinusOneRepository.getInstance(source).isPanelExpanded }
                FunctionalCardPopOutWrapper(
                    item = item,
                    podKind = FloatingPodKind.VOLUME,
                    defaultTitle = "音量",
                    icon = Icons.Rounded.VolumeUp,
                    isEditMode = isEditMode,
                    horizontalAnchor = com.example.globalminusone.data.FloatingPodHorizontalAnchor.ALIGN_TRIGGER_X,
                    podWidthDp = 76f,
                    podHeightDp = 228f,
                    showHeader = false,
                    compactWidthThresholdDp = 96f,
                    compactHeightThresholdDp = 96f,
                    inlineContent = {
                        VolumeBarContent(
                            control = volumeControl,
                            panelExpansion = expansionFlow,
                            orientation = item.orientation
                        )
                    },
                    podContent = {
                        VolumeBarContent(
                            control = volumeControl,
                            panelExpansion = expansionFlow,
                            orientation = com.example.globalminusone.data.ComponentOrientation.VERTICAL
                        )
                    }
                )
            }

            // --- 统计与硬件状态卡片类 ---
            ComponentType.STAT_BATTERY_BAR -> {
                StatBatteryBarComponent(orientation = item.orientation)
            }
            ComponentType.STAT_BATTERY_DETAIL -> {
                FunctionalCardPopOutWrapper(
                    item = item,
                    podKind = FloatingPodKind.BATTERY_DETAIL,
                    defaultTitle = "电池详情",
                    icon = Icons.Rounded.BatteryStd,
                    isEditMode = isEditMode,
                    horizontalAnchor = com.example.globalminusone.data.FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
                    podWidthDp = 300f,
                    podHeightDp = 200f,
                    showHeader = true,
                    compactWidthThresholdDp = 0f,
                    compactHeightThresholdDp = 0f,
                    inlineContent = { StatBatteryDetailComponent() },
                    podContent = { StatBatteryDetailComponent() }
                )
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
                NotificationCountContent(context) { repository.isPanelExpanded }
            }
            ComponentType.STAT_DATA_TRAFFIC -> {
                RealTrafficCount()
            }
            ComponentType.STAT_UNLOCK_COUNT -> {
                UnlockCountContent(context) { repository.isPanelExpanded }
            }
            // --- 文本、一言、便签、剪贴板、图片、网站 ---
            ComponentType.HITOKOTO_CARD -> {
                val onSaveMotto: (String) -> Unit = { next ->
                    repository.updateAppearanceConfig { it.copy(customMotto = next) }
                }
                FunctionalCardPopOutWrapper(
                    item = item,
                    podKind = FloatingPodKind.HITOKOTO,
                    defaultTitle = "一言",
                    icon = Icons.Rounded.FormatQuote,
                    isEditMode = isEditMode,
                    horizontalAnchor = com.example.globalminusone.data.FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
                    podWidthDp = 300f,
                    podHeightDp = 180f,
                    showHeader = true,
                    compactWidthThresholdDp = 0f,
                    compactHeightThresholdDp = 0f,
                    inlineContent = {
                        HitokotoCardContent(state.appearanceConfig.customMotto, { repository.isPanelExpanded }, onSaveMotto)
                    },
                    podContent = {
                        HitokotoCardContent(state.appearanceConfig.customMotto, { repository.isPanelExpanded }, onSaveMotto)
                    }
                )
            }
            ComponentType.NOTE_CARD -> {
                FunctionalCardPopOutWrapper(
                    item = item,
                    podKind = FloatingPodKind.NOTE,
                    defaultTitle = "便签",
                    icon = Icons.Rounded.EditNote,
                    isEditMode = isEditMode,
                    horizontalAnchor = com.example.globalminusone.data.FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
                    podWidthDp = 312f,
                    podHeightDp = 260f,
                    showHeader = true,
                    inlineContent = {
                        NoteCardContent(item) { repository.updateComponentText(pageIndex, item.id, it) }
                    },
                    podContent = {
                        NoteCardContent(item) { repository.updateComponentText(pageIndex, item.id, it) }
                    }
                )
            }
            ComponentType.TEXT_LABEL -> {
                TextLabelCardContent(item) { repository.updateComponentText(pageIndex, item.id, it) }
            }
            ComponentType.CLIPBOARD_CARD -> {
                val historyActions = rememberClipboardHistoryActions(repository)
                val clipboardAccess = rememberClipboardTextAccess(systemController)
                FunctionalCardPopOutWrapper(
                    item = item,
                    podKind = FloatingPodKind.CLIPBOARD,
                    defaultTitle = "剪贴板",
                    icon = Icons.Rounded.ContentPaste,
                    isEditMode = isEditMode,
                    horizontalAnchor = com.example.globalminusone.data.FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
                    podWidthDp = 312f,
                    podHeightDp = 290f,
                    showHeader = true,
                    inlineContent = {
                        ClipboardCardContent(state.clipboardHistory, historyActions, clipboardAccess)
                    },
                    podContent = {
                        ClipboardCardContent(state.clipboardHistory, historyActions, clipboardAccess)
                    }
                )
            }
            ComponentType.SINGLE_IMAGE -> {
                val selectImage = LocalPanelActions.current.selectImage
                FunctionalCardPopOutWrapper(
                    item = item,
                    podKind = FloatingPodKind.SINGLE_IMAGE,
                    defaultTitle = "图片",
                    icon = Icons.Rounded.Image,
                    isEditMode = isEditMode,
                    horizontalAnchor = com.example.globalminusone.data.FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
                    podWidthDp = 240f,
                    podHeightDp = 240f,
                    showHeader = true,
                    compactWidthThresholdDp = 0f,
                    compactHeightThresholdDp = 0f,
                    inlineContent = {
                        SingleImageCardContent(item, { selectImage(item) }) { source ->
                            MinusOneRepository.getInstance(source).imageAccessRevisions
                        }
                    },
                    podContent = {
                        SingleImageCardContent(item, { selectImage(item) }) { source ->
                            MinusOneRepository.getInstance(source).imageAccessRevisions
                        }
                    }
                )
            }
            ComponentType.PHOTO_CUBE -> {
                val panelVisible by repository.isPanelExpanded.collectAsState()
                val visible = panelVisible && LocalPageViewportActive.current
                val onChoose: () -> Unit = {
                    val latest = repository.state.value
                    val page = latest.pages.firstOrNull { it.components.findComponent(item.id) != null }
                    if (page != null && !latest.advancedConfig.layoutLocked) {
                        com.example.globalminusone.PhotoCubePickerActivity.start(context, page.id, item.id)
                    }
                }
                val onIntervalChange: (Int) -> Unit = { seconds ->
                    repository.updateComponent(pageIndex, item.id) { it.copy(photoCubeIntervalSeconds = seconds.coerceIn(2, 60)) }
                }
                FunctionalCardPopOutWrapper(
                    item = item,
                    podKind = FloatingPodKind.PHOTO_CUBE,
                    defaultTitle = "魔方相册",
                    icon = Icons.Rounded.PhotoLibrary,
                    isEditMode = isEditMode,
                    horizontalAnchor = com.example.globalminusone.data.FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
                    podWidthDp = 240f,
                    podHeightDp = 240f,
                    showHeader = true,
                    compactWidthThresholdDp = 0f,
                    compactHeightThresholdDp = 0f,
                    inlineContent = {
                        PhotoCubeCard(
                            item = item,
                            active = visible,
                            playing = visible && !isEditMode,
                            onChoose = onChoose,
                            onIntervalChange = onIntervalChange
                        )
                    },
                    podContent = {
                        PhotoCubeCard(
                            item = item,
                            active = true,
                            playing = true,
                            onChoose = onChoose,
                            onIntervalChange = onIntervalChange
                        )
                    }
                )
            }
            ComponentType.FILE_SHORTCUT -> {
                val openFileShortcut = LocalPanelActions.current.openFileShortcut
                CustomShortcutComponent(
                    item = item, enabled = !isEditMode, fallbackIcon = Icons.Rounded.Folder,
                    onClick = { closeContainerPopup(); openFileShortcut(item) }
                )
            }
            ComponentType.WEBSITE_LINK -> {
                val openBrowserUrl = LocalPanelActions.current.openBrowserUrl
                CustomShortcutComponent(item = item, enabled = !isEditMode, fallbackIcon = Icons.Language,
                    onClick = { closeContainerPopup(); openBrowserUrl(item.intentUri) })
            }
            ComponentType.FILE_BROWSER -> {
                val openFiles = LocalPanelActions.current.openFileBrowser
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
fun AppDrawerComponent(
    item: ComponentItem? = null,
    isEditMode: Boolean = LocalIsEditMode.current,
    onRequestClose: () -> Unit
) {
    if (item == null) {
        IndexedAppDrawer(isEditMode, onRequestClose)
        return
    }
    FunctionalCardPopOutWrapper(
        item = item,
        podKind = FloatingPodKind.APP_DRAWER,
        defaultTitle = "应用抽屉",
        icon = Icons.Rounded.Apps,
        isEditMode = isEditMode,
        horizontalAnchor = com.example.globalminusone.data.FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
        podWidthDp = -1f,
        podHeightDp = -1f,
        showHeader = true,
        autoPopInsideContainer = true,
        inlineContent = {
            IndexedAppDrawer(isEditMode, onRequestClose)
        },
        podContent = { dismiss ->
            IndexedAppDrawer(
                isEditMode = false,
                onRequestClose = {
                    dismiss()
                    onRequestClose()
                }
            )
        }
    )
}

@Composable
fun RecentAppsComponent(
    isEditMode: Boolean = LocalIsEditMode.current,
    onRequestClose: () -> Unit
) {
    RealRecentApps(isEditMode, onRequestClose)
}

package com.example.globalminusone.ui.panel

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.*
import com.example.globalminusone.system.PanelFileBrowser
import com.example.globalminusone.ui.AppIcons as Icons

@Composable
private fun OrientationSelectorRow(
    selected: ComponentOrientation,
    accent: Color,
    track: Color,
    textPrimary: Color,
    modifier: Modifier = Modifier,
    onSelect: (ComponentOrientation) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ComponentOrientation.entries.forEach { opt ->
            val active = selected == opt
            FilledTonalButton(
                onClick = { onSelect(opt) },
                modifier = Modifier.weight(1f).height(34.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (active) accent else track,
                    contentColor = if (active) Color.White else textPrimary
                )
            ) {
                Text(opt.label, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
fun ComponentEditModal(
    item: ComponentItem,
    pageIndex: Int,
    repository: MinusOneRepository,
    systemController: com.example.globalminusone.system.SystemController? = null,
    initialSection: String = "",
    onDismiss: () -> Unit
) {
    val palette = LocalPanelPalette.current
    val context = LocalContext.current
    val folderReturn = LocalFolderReturnState.current
    val addAbove = LocalAddAboveComponent.current
    val liveState by repository.state.collectAsState()
    val liveChildren = liveState.pages.getOrNull(pageIndex)?.components.orEmpty()
    val parentContainer = liveChildren.componentPath(item.id)?.dropLast(1)?.lastOrNull()
        ?.let { liveChildren.findComponent(it) }
    var section by remember(item.id, initialSection) { mutableStateOf(initialSection) }
    var confirmation by remember(item.id) { mutableStateOf("") }
    var containerLayout by remember(item.id) { mutableStateOf(item.containerLayout) }
    var containerColumns by remember(item.id) { mutableIntStateOf(item.containerColumns) }
    var popupWidth by remember(item.id) { mutableIntStateOf(item.containerPopupWidthDp) }
    var popupHeight by remember(item.id) { mutableIntStateOf(item.containerPopupHeightDp) }
    var itemSize by remember(item.id) { mutableIntStateOf(item.containerItemSizeDp) }
    var iconSize by remember(item.id) { mutableIntStateOf(item.containerIconSizeDp) }
    var itemGap by remember(item.id) { mutableIntStateOf(item.containerItemGapDp) }
    var rowGap by remember(item.id) { mutableIntStateOf(containerRowGapDp(item)) }
    var itemPaddingVertical by remember(item.id) { mutableIntStateOf(item.containerItemPaddingVerticalDp) }
    var iconLabelGap by remember(item.id) { mutableIntStateOf(item.containerIconLabelGapDp) }
    var spacing by remember(item.id) { mutableStateOf(listOf(item.paddingTopDp, item.paddingBottomDp, item.paddingLeftDp, item.paddingRightDp, item.marginTopDp, item.marginBottomDp, item.marginLeftDp, item.marginRightDp)) }
    PropertyModalBack {
        if (confirmation.isNotEmpty()) confirmation = ""
        else previousPropertySection(section, initialSection)?.let { section = it } ?: onDismiss()
    }
    var x by remember(item.id) { mutableIntStateOf(item.x) }
    var y by remember(item.id) { mutableIntStateOf(item.y) }
    var w by remember(item.id) { mutableIntStateOf(item.w) }
    var h by remember(item.id) { mutableIntStateOf(item.h) }
    var gridSpanX by remember(item.id) { mutableIntStateOf(item.gridSpanX) }
    var gridSpanY by remember(item.id) { mutableIntStateOf(item.gridSpanY) }
    var cornerRadius by remember(item.id) { mutableIntStateOf(item.cornerRadiusDp) }
    var rotation by remember(item.id) { mutableIntStateOf(item.rotationDeg) }
    var showTitle by remember(item.id) { mutableStateOf(item.showTitle) }
    var containerCompact by remember(item.id) { mutableStateOf(item.containerCompact) }
    var autoHeight by remember(item.id) { mutableStateOf(item.autoHeight) }
    var titleText by remember(item.id) { mutableStateOf(item.title) }
    var textContent by remember(item.id) { mutableStateOf(item.textContent) }
    var intentUri by remember(item.id) { mutableStateOf(item.intentUri) }
    var appLaunchMode by remember(item.id) { mutableStateOf(item.appLaunchMode) }
    var orientation by remember(item.id) { mutableStateOf(item.orientation) }
    var selectedBgColor by remember(item.id) { mutableStateOf(item.bgColorHex) }
    var bgAlphaPercent by remember(item.id) { mutableIntStateOf(item.bgAlphaPercent) }
    var selectedTextColor by remember(item.id) { mutableStateOf(item.textColorHex) }
    var textAlphaPercent by remember(item.id) { mutableIntStateOf(item.textAlphaPercent) }

    val modalBg = if (palette.isDark) Color(0xFF1F242D) else Color.White
    val modalTextPrimary = if (palette.isDark) Color(0xFFF5F7FA) else Color(0xFF111827)
    val modalTextSecondary = if (palette.isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val modalTrack = if (palette.isDark) Color(0xFF2D3340) else Color(0xFFF1F5F9)
    val modalBorder = if (palette.isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0)

    fun syncLive() {
        repository.updateComponent(pageIndex, item.id) { comp ->
            comp.copy(
                x = x,
                y = y,
                w = w,
                h = h,
                cornerRadiusDp = cornerRadius,
                rotationDeg = rotation,
                showTitle = showTitle,
                containerCompact = containerCompact,
                containerColumns = containerColumns,
                containerLayout = containerLayout,
                containerPopupWidthDp = popupWidth,
                containerPopupHeightDp = popupHeight,
                containerItemSizeDp = itemSize,
                containerIconSizeDp = iconSize,
                containerItemGapDp = itemGap,
                containerRowGapDp = rowGap,
                containerItemPaddingVerticalDp = itemPaddingVertical,
                containerIconLabelGapDp = iconLabelGap,
                gridSpanX = gridSpanX,
                gridSpanY = gridSpanY,
                paddingTopDp = spacing[0], paddingBottomDp = spacing[1], paddingLeftDp = spacing[2], paddingRightDp = spacing[3],
                marginTopDp = spacing[4], marginBottomDp = spacing[5], marginLeftDp = spacing[6], marginRightDp = spacing[7],
                autoHeight = autoHeight,
                title = titleText,
                textContent = textContent,
                intentUri = intentUri,
                appLaunchMode = appLaunchMode,
                orientation = orientation,
                bgColorHex = selectedBgColor,
                bgAlphaPercent = bgAlphaPercent,
                textColorHex = selectedTextColor,
                textAlphaPercent = textAlphaPercent
            )
        }
    }

    fun applyOrientationChoice(choice: ComponentOrientation) {
        val activePage = liveState.pages.getOrNull(pageIndex)
        val inGridPage = parentContainer == null && activePage?.layoutType == PageLayoutType.GRID
        val inGridContainer = parentContainer?.containerLayout == ContainerLayoutType.GRID
        orientation = choice
        if (choice == ComponentOrientation.VERTICAL) {
            if (inGridPage || inGridContainer) {
                val defSpan = defaultGridSpanForOrientation(item.type, choice)
                if (defSpan != null && (gridSpanX == 0 || gridSpanY == 0 || gridSpanX > gridSpanY)) {
                    gridSpanX = defSpan.first
                    gridSpanY = defSpan.second
                    autoHeight = false
                }
            } else {
                if (w > h || h <= item.type.defaultH) {
                    w = minOf(w, 8).coerceAtLeast(5)
                    h = maxOf(h, if (isSliderBarType(item.type)) 16 else 14)
                    autoHeight = false
                }
            }
        } else if (choice == ComponentOrientation.HORIZONTAL) {
            if (inGridPage || inGridContainer) {
                if (gridSpanX == 1 && gridSpanY >= 2) {
                    gridSpanX = 0
                    gridSpanY = 0
                }
            } else if (h > w) {
                w = item.type.defaultW.coerceAtLeast(12)
                h = item.type.defaultH
            }
        }
        syncLive()
    }

    val isLivePreviewSection = section.isNotEmpty() && section != "重置与删除"
    var peekCanvas by remember(section) { mutableStateOf(false) }
    val scrimAlpha = when {
        peekCanvas -> 0.05f
        isLivePreviewSection -> 0.16f
        else -> 0.65f
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = if (isLivePreviewSection) Alignment.BottomCenter else Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = (maxHeight - 24.dp).coerceAtLeast(1.dp).coerceAtMost(580.dp))
                .padding(bottom = if (isLivePreviewSection) 12.dp else 0.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            color = if (peekCanvas) modalBg.copy(alpha = 0.28f) else modalBg,
            shape = RoundedCornerShape(20.dp),
            border = if (peekCanvas) null else BorderStroke(1.dp, modalBorder),
            tonalElevation = 0.dp,
            shadowElevation = if (peekCanvas) 0.dp else 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (section.isNotEmpty()) {
                        Surface(
                            onClick = { previousPropertySection(section, initialSection)?.let { section = it } ?: onDismiss() },
                            shape = CircleShape,
                            color = modalTrack,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.ArrowBack,
                                    contentDescription = "返回",
                                    tint = modalTextPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            text = if (section.isEmpty()) "卡片属性" else section,
                            color = modalTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (titleText.isNotBlank()) {
                            Text(
                                text = titleText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = modalTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (isLivePreviewSection) {
                        Surface(
                            onClick = { peekCanvas = !peekCanvas },
                            shape = RoundedCornerShape(9.dp),
                            color = if (peekCanvas) palette.accent else palette.accent.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    if (peekCanvas) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = null,
                                    tint = if (peekCanvas) Color.White else palette.accent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (peekCanvas) "恢复弹窗" else "透视画布",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (peekCanvas) Color.White else palette.accent
                                )
                            }
                        }
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = modalTrack,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, "关闭", tint = modalTextSecondary, modifier = Modifier.size(17.dp))
                        }
                    }
                }

                if (section.isNotEmpty()) {
                    val quickTabs = buildList {
                        add("内容")
                        add("位置与尺寸")
                        add("外观")
                        add("间距")
                        if (item.type in setOf(ComponentType.SWITCH_WIFI, ComponentType.SWITCH_BLUETOOTH)) {
                            add("无线控制方式")
                        }
                        if (item.type in setOf(ComponentType.CONTAINER_BOX, ComponentType.FOLDER)) {
                            add("容器与行为")
                            if (section in setOf("内部排列", "内部尺寸与间距", "图标与文字留白", "文件夹窗口")) {
                                add("内部排列")
                                add("图标与文字留白")
                                add("文件夹窗口")
                            }
                        }
                        add("重置与删除")
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        shape = RoundedCornerShape(11.dp),
                        color = modalTrack
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            quickTabs.forEach { tab ->
                                val selectedTab = section == tab
                                Surface(
                                    onClick = { section = tab },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedTab) palette.accent else Color.Transparent
                                ) {
                                    Text(
                                        text = tab,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedTab) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selectedTab) Color.White else modalTextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(key(section) { rememberScrollState() }),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (section) {
                        "" -> {
                            val isFreePage = liveState.pages.getOrNull(pageIndex)?.layoutType == PageLayoutType.FREE
                            if (supportsVerticalOrientation(item.type)) {
                                PropertySectionCard(title = "排布方向") {
                                    OrientationSelectorRow(
                                        selected = orientation,
                                        accent = palette.accent,
                                        track = modalTrack,
                                        textPrimary = modalTextPrimary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                        onSelect = ::applyOrientationChoice
                                    )
                                }
                            }
                            PropertySectionCard(title = "内容与几何") {
                                if (addAbove != null && !isFreePage && parentContainer == null) {
                                    PropertyCategoryRow("在上方添加卡片", "在当前卡片之前插入", icon = Icons.Rounded.Add) {
                                        addAbove(item.id); onDismiss()
                                    }
                                }
                                PropertyCategoryRow("内容", "标题、文本、图片与链接", icon = Icons.Rounded.EditNote) { section = "内容" }
                                PropertyCategoryRow("位置与尺寸", "坐标、宽高与自动高度", icon = Icons.Rounded.CenterFocusStrong) { section = "位置与尺寸" }
                            }
                            PropertySectionCard(title = "外观与高级") {
                                PropertyCategoryRow("外观", "背景、文字颜色、透明度与圆角", icon = Icons.Rounded.Palette) { section = "外观" }
                                PropertyCategoryRow("间距", "卡片内边距与外边距", icon = Icons.Rounded.ViewAgenda) { section = "间距" }
                                if (item.type in setOf(ComponentType.SWITCH_WIFI, ComponentType.SWITCH_BLUETOOTH)) {
                                    PropertyCategoryRow("无线控制方式", "选择 Wi-Fi 与蓝牙的控制方式", icon = Icons.Rounded.Wifi) { section = "无线控制方式" }
                                }
                                if (item.type in setOf(ComponentType.CONTAINER_BOX, ComponentType.FOLDER)) {
                                    PropertyCategoryRow("容器与行为", "排列、尺寸与间距", icon = Icons.Rounded.Folder) { section = "容器与行为" }
                                }
                            }
                            PropertyCategoryRow("重置与删除", "恢复默认大小、重置样式或删除卡片", icon = Icons.Rounded.DeleteOutline, destructive = true) { section = "重置与删除" }
                        }
                        "内容" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "显示卡片标题栏", color = modalTextPrimary, fontSize = 12.sp)
                                Switch(
                                    checked = showTitle,
                                    onCheckedChange = { showTitle = it; syncLive() },
                                    colors = SwitchDefaults.colors(checkedThumbColor = palette.accent)
                                )
                            }

                            run {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    color = modalTrack
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        BasicTextField(
                                            value = titleText,
                                            onValueChange = { titleText = it; syncLive() },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                            singleLine = true,
                                            decorationBox = { inner -> Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) { inner() } },
                                            textStyle = TextStyle(color = modalTextPrimary, fontSize = 12.sp),
                                            cursorBrush = SolidColor(palette.accent)
                                        )
                                    }
                                }
                            }

                            if (item.type == ComponentType.NOTE_CARD || item.type == ComponentType.TEXT_LABEL || item.type == ComponentType.SHELL_COMMAND) {
                                Text(text = "文本内容", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(70.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    color = modalTrack
                                ) {
                                    BasicTextField(
                                        value = textContent,
                                        onValueChange = { textContent = it; syncLive() },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp),
                                        textStyle = TextStyle(color = modalTextPrimary, fontSize = 12.sp),
                                        cursorBrush = SolidColor(palette.accent)
                                    )
                                }
                            }

                            if (item.type == ComponentType.APP_ICON) {
                                Text("打开方式", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = appLaunchMode == AppLaunchMode.NORMAL,
                                        onClick = { appLaunchMode = AppLaunchMode.NORMAL; syncLive() }, label = { Text("普通打开") })
                                    FilterChip(selected = appLaunchMode == AppLaunchMode.FLOATING,
                                        onClick = { appLaunchMode = AppLaunchMode.FLOATING; syncLive() }, label = { Text("小窗打开") })
                                }
                            }
                            if (item.type == ComponentType.SINGLE_IMAGE) {
                                OutlinedButton(onClick = {
                                    val latest = repository.state.value
                                    val page = latest.pages.getOrNull(pageIndex)
                                    if (latest.advancedConfig.layoutLocked) Toast.makeText(context, "布局已锁定，请先解锁", Toast.LENGTH_SHORT).show()
                                    else if (page?.components?.findComponent(item.id) != null) {
                                        folderReturn?.clear()
                                        onDismiss()
                                        PanelFileBrowser.requestImage(context, page.id, item.id)
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("选择 / 更换图片", color = palette.accent) }
                            }
                            if (item.type == ComponentType.FILE_SHORTCUT) {
                                Text("文件在面板内预览；权限失效时可重新选择。", color = modalTextSecondary, fontSize = 12.sp)
                                OutlinedButton(onClick = {
                                    val latest = repository.state.value
                                    val page = latest.pages.getOrNull(pageIndex)
                                    if (latest.advancedConfig.layoutLocked) {
                                        Toast.makeText(context, "布局已锁定，请先解锁", Toast.LENGTH_SHORT).show()
                                    } else if (page?.components?.findComponent(item.id) != null) {
                                        folderReturn?.clear()
                                        onDismiss()
                                        PanelFileBrowser.requestFile(context, page.id, item.id)
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("重新选择文件", color = palette.accent) }
                            }
                            if (item.type == ComponentType.WEBSITE_LINK || item.type == ComponentType.CUSTOM_SHORTCUT) {
                                Text(text = "URL / Intent URI", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    color = modalTrack
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        BasicTextField(
                                            value = intentUri,
                                            onValueChange = { intentUri = it; syncLive() },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                            singleLine = true,
                                            decorationBox = { inner -> Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) { inner() } },
                                            textStyle = TextStyle(color = modalTextPrimary, fontSize = 12.sp),
                                            cursorBrush = SolidColor(palette.accent)
                                        )
                                    }
                                }
                            }
                        }
                        "位置与尺寸" -> {
                            val activePage = liveState.pages.getOrNull(pageIndex)
                            if (supportsVerticalOrientation(item.type)) {
                                Text("排布方向样式", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                OrientationSelectorRow(
                                    selected = orientation,
                                    accent = palette.accent,
                                    track = modalTrack,
                                    textPrimary = modalTextPrimary,
                                    onSelect = ::applyOrientationChoice
                                )
                            }
                            val inGridContainer = parentContainer?.containerLayout == ContainerLayoutType.GRID
                            val inGridPage = parentContainer == null && activePage?.layoutType == PageLayoutType.GRID
                            if (inGridPage || inGridContainer) {
                                val maxColsHint = if (inGridContainer) {
                                    parentContainer!!.containerColumns.coerceIn(1, 8)
                                } else {
                                    com.example.globalminusone.data.derivePageGridMetrics(
                                        availableWidthDp = repository.estimatedPageGridWidthDp(),
                                        itemSizeDp = activePage?.gridItemSizeDp ?: 58,
                                        itemGapDp = activePage?.gridItemGapDp ?: 8,
                                        columnCount = activePage?.gridColumnCount ?: 0,
                                        iconSizeDp = activePage?.gridIconSizeDp ?: 0
                                    ).columns
                                }
                                Text(
                                    text = if (inGridPage) {
                                        "当前在网格布局页：可设置卡片占用多个网格单元（列 × 行），也可在编辑模式拖动右下角手柄缩放。"
                                    } else {
                                        "当前在网格容器中（${maxColsHint} 列）：可设置子卡片所占网格列数与行数。"
                                    },
                                    color = modalTextSecondary,
                                    fontSize = 11.sp
                                )
                                Text("多格跨度快捷预设", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        Triple("默认", 0, 0),
                                        Triple("1×1", 1, 1),
                                        Triple("2×1", 2, 1),
                                        Triple("2×2", 2, 2),
                                        Triple("3×2", 3, 2),
                                        Triple("整行×1", com.example.globalminusone.data.GRID_SPAN_FULL_ROW, 1),
                                        Triple("整行×2", com.example.globalminusone.data.GRID_SPAN_FULL_ROW, 2)
                                    ).forEach { (presetLabel, sx, sy) ->
                                        val activeSpan = gridSpanX == sx && gridSpanY == sy
                                        Surface(
                                            onClick = {
                                                gridSpanX = sx
                                                gridSpanY = sy
                                                if (sy > 0) autoHeight = false
                                                if (inGridContainer && sx > 0 && !isContainerGridIcon(item.type)) {
                                                    val hostCols = liveState.advancedConfig.gridColumns.coerceIn(8, 64)
                                                    val effCols = if (sx >= com.example.globalminusone.data.GRID_SPAN_FULL_ROW) maxColsHint else sx
                                                    w = containerGridSpanWidth(effCols, maxColsHint, hostCols, parentContainer!!.containerItemGapDp)
                                                }
                                                syncLive()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (activeSpan) palette.accent else modalTrack
                                        ) {
                                            Text(
                                                presetLabel,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                fontSize = 11.sp,
                                                fontWeight = if (activeSpan) FontWeight.Bold else FontWeight.Normal,
                                                color = if (activeSpan) Color.White else modalTextPrimary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val displaySpanX = if (gridSpanX >= com.example.globalminusone.data.GRID_SPAN_FULL_ROW) maxColsHint else gridSpanX
                                    NumberStepper(
                                        label = "占格列数 (0默认)",
                                        value = displaySpanX,
                                        minimum = 0,
                                        maximum = 12,
                                        onValueChange = { v ->
                                            gridSpanX = v.coerceIn(0, 12)
                                            syncLive()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NumberStepper(
                                        label = "占格行数 (0默认)",
                                        value = gridSpanY,
                                        minimum = 0,
                                        maximum = 12,
                                        onValueChange = { v ->
                                            gridSpanY = v.coerceIn(0, 12)
                                            if (gridSpanY > 0) autoHeight = false
                                            syncLive()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            val freeGeometry = !inGridPage && !inGridContainer
                            if (freeGeometry) Text(text = if (parentContainer == null && activePage?.layoutType == PageLayoutType.LINEAR) "线性卡片宽高" else "自由布局坐标与宽高", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)


                            if (item.type in setOf(ComponentType.FOLDER, ComponentType.CONTAINER_BOX, ComponentType.OPERATION_BAR, ComponentType.APP_ICON, ComponentType.CUSTOM_SHORTCUT, ComponentType.STATIC_SHORTCUT)) {
                                PropertySwitch("按内容自动高度", autoHeight) { autoHeight = it; if (it) gridSpanY = 0; syncLive() }
                            }
                            if (liveState.pages.getOrNull(pageIndex)?.layoutType == PageLayoutType.LINEAR) {
                                Text("线性布局按顺序排列，可调宽高；切换网格或自由布局后可多格摆放与缩放。", color = modalTextSecondary, fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { repository.setPageLayoutType(pageIndex, PageLayoutType.GRID); onDismiss() }) {
                                        Text("切换网格布局", color = palette.accent, fontSize = 12.sp)
                                    }
                                    OutlinedButton(onClick = { repository.setPageLayoutType(pageIndex, PageLayoutType.FREE); onDismiss() }) {
                                        Text("切换自由布局", color = palette.accent, fontSize = 12.sp)
                                    }
                                }
                            }
                            if (freeGeometry && (parentContainer != null || activePage?.layoutType == PageLayoutType.FREE)) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberStepper(label = "X坐标", value = x, maximum = (liveState.advancedConfig.gridColumns - w).coerceAtLeast(0), onValueChange = { x = it.coerceIn(0, (repository.state.value.advancedConfig.gridColumns - w).coerceAtLeast(0)); syncLive() }, modifier = Modifier.weight(1f))
                                NumberStepper(label = "Y坐标", value = y, onValueChange = { y = it.coerceAtLeast(0); syncLive() }, modifier = Modifier.weight(1f))
                            }
                            if (freeGeometry) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberStepper(label = "W (宽)", value = w, minimum = 1, maximum = (liveState.advancedConfig.gridColumns - if (parentContainer == null && activePage?.layoutType == PageLayoutType.LINEAR) 0 else x).coerceAtLeast(1), onValueChange = { w = it.coerceIn(1, (repository.state.value.advancedConfig.gridColumns - if (parentContainer == null && activePage?.layoutType == PageLayoutType.LINEAR) 0 else x).coerceAtLeast(1)); syncLive() }, modifier = Modifier.weight(1f))
                                NumberStepper(label = "H (高)", value = h, minimum = 1, maximum = 120, onValueChange = { h = it.coerceIn(1, 120); autoHeight = false; syncLive() }, modifier = Modifier.weight(1f))
                            }


                        }
                        "外观" -> {
                            Text(text = "单卡片背景与透明度", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            val bgOptions = listOf<Pair<String, Long?>>(
                                "跟随全局" to null,
                                "纯白亮色" to 0xFFFFFFFFL,
                                "柔和暖白" to 0xFFF7F8FAL,
                                "暗黑深灰" to 0xFF1E222BL,
                                "纯黑OLED" to 0xFF000000L,
                                "雾蓝卡片" to 0xFF1E3A5FL
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                bgOptions.take(3).forEach { (label, hex) ->
                                    val selected = selectedBgColor == hex
                                    FilledTonalButton(
                                        onClick = { selectedBgColor = hex; syncLive() },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (selected) palette.accent else modalTrack,
                                            contentColor = if (selected) Color.White else modalTextPrimary
                                        )
                                    ) {
                                        Text(text = label, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                bgOptions.drop(3).forEach { (label, hex) ->
                                    val selected = selectedBgColor == hex
                                    FilledTonalButton(
                                        onClick = { selectedBgColor = hex; syncLive() },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (selected) palette.accent else modalTrack,
                                            contentColor = if (selected) Color.White else modalTextPrimary
                                        )
                                    ) {
                                        Text(text = label, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val displayBgAlpha = if (bgAlphaPercent >= 0) "${bgAlphaPercent}%" else "跟随全局 (${palette.themeConfig.cardBgAlphaPercent}%)"
                                    Text(text = "卡片背景不透明度: $displayBgAlpha", color = modalTextPrimary, fontSize = 12.sp)
                                    if (bgAlphaPercent >= 0) {
                                        Text(
                                            text = "恢复跟随",
                                            color = palette.accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.clickable { bgAlphaPercent = -1; syncLive() }
                                        )
                                    }
                                }
                                Slider(
                                    value = (if (bgAlphaPercent >= 0) bgAlphaPercent else palette.themeConfig.cardBgAlphaPercent).toFloat(),
                                    onValueChange = { bgAlphaPercent = it.toInt(); syncLive() },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                )
                            }

                            Text(text = "单卡片文字/标题颜色与透明度", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val textOptions = listOf<Pair<String, Long?>>(
                                "跟随全局" to null,
                                "深炭黑字" to 0xFF1F2329L,
                                "纯白亮字" to 0xFFFFFFFFL,
                                "柔和灰白" to 0xFFD0D5DDL,
                                "科技蓝字" to 0xFF2196F3L,
                                "琥珀金字" to 0xFFF59E0BL
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                textOptions.take(3).forEach { (label, hex) ->
                                    val selected = selectedTextColor == hex
                                    FilledTonalButton(
                                        onClick = { selectedTextColor = hex; syncLive() },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (selected) palette.accent else modalTrack,
                                            contentColor = if (selected) Color.White else modalTextPrimary
                                        )
                                    ) {
                                        Text(text = label, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                textOptions.drop(3).forEach { (label, hex) ->
                                    val selected = selectedTextColor == hex
                                    FilledTonalButton(
                                        onClick = { selectedTextColor = hex; syncLive() },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (selected) palette.accent else modalTrack,
                                            contentColor = if (selected) Color.White else modalTextPrimary
                                        )
                                    ) {
                                        Text(text = label, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val displayTextAlpha = if (textAlphaPercent >= 0) "${textAlphaPercent}%" else "跟随全局 (${palette.themeConfig.textAlphaPercent}%)"
                                    Text(text = "文字与标题不透明度: $displayTextAlpha", color = modalTextPrimary, fontSize = 12.sp)
                                    if (textAlphaPercent >= 0) {
                                        Text(
                                            text = "恢复跟随",
                                            color = palette.accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.clickable { textAlphaPercent = -1; syncLive() }
                                        )
                                    }
                                }
                                Slider(
                                    value = (if (textAlphaPercent >= 0) textAlphaPercent else palette.themeConfig.textAlphaPercent).toFloat(),
                                    onValueChange = { textAlphaPercent = it.toInt(); syncLive() },
                                    valueRange = 10f..100f,
                                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                )
                            }

                            Text(text = "圆角、旋转与标题栏", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton(onClick = { cornerRadius = -1; syncLive() }) { Text("恢复跟随", fontSize = 11.sp) }
                                    Text(text = if (cornerRadius < 0) "卡片圆角: 跟随全局 (${palette.themeConfig.cardCornerRadiusDp}dp)" else "卡片圆角: ${cornerRadius}dp", color = modalTextPrimary, fontSize = 11.sp)
                                }
                                NumberStepper("圆角 (dp)", if (cornerRadius < 0) palette.themeConfig.cardCornerRadiusDp else cornerRadius,
                                    { cornerRadius = it; syncLive() }, maximum = 50)
                                Slider(
                                    value = (if (cornerRadius < 0) palette.themeConfig.cardCornerRadiusDp else cornerRadius).toFloat(),
                                    onValueChange = { cornerRadius = it.toInt(); syncLive() },
                                    valueRange = 0f..50f,
                                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                )
                            }

                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    NumberStepper("旋转角度 (°)", rotation, { rotation = it; syncLive() }, maximum = 360)
                                }
                                Slider(
                                    value = rotation.toFloat(),
                                    onValueChange = { rotation = it.toInt(); syncLive() },
                                    valueRange = 0f..360f,
                                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                )
                            }


                        }
                        "无线控制方式" -> {
                            if (systemController != null) RadioControlSettings(systemController)
                            else Text("无线控制器暂不可用，请重新打开卡片属性。", color = modalTextSecondary, fontSize = 12.sp)
                        }
                        "间距" -> {
                            PropertySectionCard(title = "内边距 (dp)") {
                                PropertySpacingFields(spacing.take(4)) { index, value -> spacing = spacing.toMutableList().also { it[index] = value }; syncLive() }
                            }
                            PropertySectionCard(title = "外边距 (dp)") {
                                PropertySpacingFields(spacing.drop(4)) { index, value -> spacing = spacing.toMutableList().also { it[index + 4] = value }; syncLive() }
                            }
                        }
                        "容器与行为" -> {
                            PropertyCategoryRow("内部排列", "自动排列或自由摆放、列数与间距", icon = Icons.Rounded.GridView) { section = "内部排列" }
                            PropertyCategoryRow("图标与文字留白", "每项上下内边距、图文间距", icon = Icons.Rounded.Tune) { section = "图标与文字留白" }
                            if (item.type == ComponentType.FOLDER) PropertyCategoryRow("文件夹窗口", "展开后的弹出窗口宽高", icon = Icons.Rounded.OpenInNew) { section = "文件夹窗口" }
                            if (item.type == ComponentType.CONTAINER_BOX) {
                                PropertySwitch("紧凑容器", containerCompact) { containerCompact = it; syncLive() }
                            }
                        }
                        "内部排列", "内部尺寸与间距" -> {
                            PropertySectionCard(title = "排列模式") {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = modalTrack,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(
                                            ContainerLayoutType.GRID to "自动排列",
                                            ContainerLayoutType.FREE to "自由摆放"
                                        ).forEach { (mode, label) ->
                                            val selected = containerLayout == mode
                                            Surface(
                                                onClick = { containerLayout = mode; syncLive() },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (selected) palette.accent else Color.Transparent
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (selected) {
                                                        Icon(
                                                            Icons.Rounded.Check,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp).padding(end = 3.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = label,
                                                        color = if (selected) Color.White else modalTextPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (containerLayout == ContainerLayoutType.GRID) {
                                GridDimensionControls(containerColumns, iconSize, itemGap,
                                    onColumns = { containerColumns = it; syncLive() },
                                    onIconSize = { iconSize = it; itemSize = 0; syncLive() },
                                    onGap = { itemGap = it; syncLive() }, maxColumns = 8, allowAutoColumns = false,
                                    rowGapDp = rowGap.coerceIn(com.example.globalminusone.data.MIN_GRID_GAP_DP, com.example.globalminusone.data.MAX_GRID_GAP_DP),
                                    onRowGap = { rowGap = it; itemSize = 0; syncLive() })
                                val compactDensity = androidx.compose.ui.platform.LocalDensity.current
                                val compactPresentation = LocalContainerPresentation.current
                                val fallbackWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat()
                                Surface(
                                    onClick = {
                                        val liveContainer = liveChildren.findComponent(item.id) ?: item
                                        val availableWidthDp = compactPresentation?.panelBounds?.let { bounds ->
                                            with(compactDensity) { (bounds.right - bounds.left).toDp().value }
                                        } ?: fallbackWidthDp
                                        val compactMetrics = containerCompactTargetMetrics(
                                            item = liveContainer.copy(containerColumns = containerColumns.coerceIn(1, 8)),
                                            availableWidthDp = availableWidthDp,
                                            editingInside = true,
                                            gridColumns = liveState.advancedConfig.gridColumns,
                                            configuredHeightUnitDp = liveState.advancedConfig.heightUnitDp
                                        )
                                        repository.compactContainerGridPlacements(
                                            pageIndex = pageIndex,
                                            containerId = item.id,
                                            widthDp = compactMetrics.widthDp,
                                            stepYDp = compactMetrics.stepYDp,
                                            iconColumns = compactMetrics.columns
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = palette.accent.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Rounded.GridView, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("紧凑对齐子卡片", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            } else {
                                PropertySectionCard {
                                    Text("子卡片按保存坐标与尺寸摆放，进入内部编辑后可长按拖动调整位置。", color = modalTextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                        "图标与文字留白" -> {
                            PropertySectionCard {
                                Text("应用于网格中的每项；上下内边距分别作用于顶部和底部，图文间隔作用于图标与标题之间。", color = modalTextSecondary, fontSize = 12.sp)
                                if (itemSize > 0) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("旧版固定格尺寸 ${itemSize}dp 仍生效", color = modalTextSecondary, fontSize = 12.sp)
                                        Surface(onClick = { itemSize = 0; syncLive() }, shape = RoundedCornerShape(8.dp), color = palette.accent.copy(alpha = 0.12f)) {
                                            Text("改为按内容自动", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                                        }
                                    }
                                }
                                NumberStepper("上下内边距 (dp)", itemPaddingVertical,
                                    { itemPaddingVertical = it; syncLive() }, minimum = 0, maximum = 48)
                                NumberStepper("图文间隔 (dp)", iconLabelGap,
                                    { iconLabelGap = it; syncLive() },
                                    minimum = com.example.globalminusone.data.MIN_ICON_LABEL_GAP_DP,
                                    maximum = com.example.globalminusone.data.MAX_ICON_LABEL_GAP_DP)
                                Surface(
                                    onClick = { itemPaddingVertical = 0; iconLabelGap = 2; itemSize = 0; syncLive() },
                                    shape = RoundedCornerShape(9.dp),
                                    color = modalTrack,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text("恢复默认留白", color = modalTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                        "文件夹窗口" -> {
                            PropertySectionCard {
                                Text(
                                    "设为 0 表示按内容自动适应（1 列单竖条工具可自动收窄至 104dp）；手动宽度支持 92–1200dp，窄窗在浏览态自动将编辑与属性收纳进 ⋮ 菜单。",
                                    color = modalTextSecondary,
                                    fontSize = 12.sp
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(onClick = { popupWidth = 0; syncLive() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(9.dp), color = modalTrack) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                                            Text("宽度自动", color = modalTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    Surface(onClick = { popupWidth = 104; syncLive() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(9.dp), color = modalTrack) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                                            Text("极简 104dp", color = modalTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    Surface(onClick = { popupHeight = 0; syncLive() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(9.dp), color = modalTrack) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                                            Text("高度自动", color = modalTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                                NumberStepper("窗口宽度 (dp)", popupWidth, { popupWidth = it; syncLive() }, minimum = 0, maximum = 1200)
                                NumberStepper("窗口高度 (dp)", popupHeight, { popupHeight = it; syncLive() }, minimum = 0, maximum = 1200)
                            }
                        }
                        "重置与删除" -> {
                            PropertyCategoryRow("恢复默认大小", "仅恢复这张卡片的宽高与自动高度", icon = Icons.Rounded.RestartAlt) { confirmation = "恢复默认大小" }
                            PropertyCategoryRow("重置样式", "恢复颜色、透明度、圆角与间距", icon = Icons.Rounded.FormatPaint) { confirmation = "重置样式" }
                            PropertyCategoryRow("删除卡片", if (item.type in setOf(ComponentType.CONTAINER_BOX, ComponentType.FOLDER)) "容器内的子卡片也会一并删除" else "此操作无法恢复", icon = Icons.Rounded.DeleteOutline, destructive = true) { confirmation = "删除卡片" }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
                ) {
                    Text("完成", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (confirmation.isNotEmpty()) {
        PanelAlertDialog(
            onDismissRequest = { confirmation = "" },
            title = { Text("确认$confirmation？") },
            text = { Text(if (confirmation == "删除卡片") { if (item.type in setOf(ComponentType.CONTAINER_BOX, ComponentType.FOLDER)) "删除后无法恢复，容器内的所有子卡片也会被删除。" else "删除这张卡片后无法恢复。" } else "此操作将覆盖这张卡片的对应设置。") },
            confirmButton = {
                TextButton(onClick = {
                    when (confirmation) {
                        "删除卡片" -> repository.removeComponent(pageIndex, item.id)
                        "重置样式" -> repository.resetComponentStyle(pageIndex, item.id)
                        else -> repository.updateComponent(pageIndex, item.id) { current ->
                            repository.prepareNewComponent(pageIndex,
                                restoreDefaultCardSize(current, repository.state.value.advancedConfig.gridColumns), parentContainer?.id)
                        }
                    }
                    confirmation = ""
                    onDismiss()
                }) {
                    Text(
                        "确认$confirmation",
                        color = if (confirmation == "删除卡片") MaterialTheme.colorScheme.error else Color.Unspecified
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirmation = "" }) { Text("取消") } }
        )
    }
}

@Composable
fun ThemeAppearanceModal(
    currentPageIndex: Int,
    state: GlobalMinusOneState,
    repository: MinusOneRepository,
    initialSection: String = "",
    onDismiss: () -> Unit
) {
    val palette = LocalPanelPalette.current
    val defaultSection = initialSection.ifEmpty { "面板尺寸与布局" }
    var section by remember(initialSection) { mutableStateOf(defaultSection) }
    var peekCanvas by remember { mutableStateOf(false) }
    PropertyModalBack {
        if (section == defaultSection) onDismiss() else section = defaultSection
    }
    val theme = state.themeConfig
    val currentPage = state.pages.getOrNull(currentPageIndex)

    val modalBg = if (palette.isDark) Color(0xFF1F242D) else Color.White
    val modalTextPrimary = if (palette.isDark) Color(0xFFF5F7FA) else Color(0xFF111827)
    val modalTextSecondary = if (palette.isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val modalTrack = if (palette.isDark) Color(0xFF2D3340) else Color(0xFFF1F5F9)
    val modalBorder = if (palette.isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0)
    val scrimAlpha = if (peekCanvas) 0.04f else 0.16f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.95f)
                .heightIn(max = (maxHeight - 24.dp).coerceAtLeast(1.dp).coerceAtMost(540.dp))
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(22.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            color = if (peekCanvas) modalBg.copy(alpha = 0.25f) else modalBg.copy(alpha = 0.98f),
            shape = RoundedCornerShape(22.dp),
            border = if (peekCanvas) null else BorderStroke(1.dp, modalBorder),
            tonalElevation = 0.dp,
            shadowElevation = if (peekCanvas) 0.dp else 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "全局外观与面板",
                        color = modalTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        onClick = { peekCanvas = !peekCanvas },
                        shape = RoundedCornerShape(9.dp),
                        color = if (peekCanvas) palette.accent else palette.accent.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (peekCanvas) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                tint = if (peekCanvas) Color.White else palette.accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (peekCanvas) "恢复弹窗" else "透视画布",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (peekCanvas) Color.White else palette.accent
                            )
                        }
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = modalTrack,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, "关闭", tint = modalTextSecondary, modifier = Modifier.size(17.dp))
                        }
                    }
                }

                val globalTabs = listOf(
                    "面板尺寸与布局",
                    "主题与背景",
                    "卡片外观",
                    "文字与标题",
                    "转场动画",
                    "手势与行为"
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = modalTrack
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        globalTabs.forEach { tab ->
                            val selectedTab = section == tab
                            Surface(
                                onClick = { section = tab },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedTab) palette.accent else Color.Transparent
                            ) {
                                Text(
                                    text = tab,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selectedTab) Color.White else modalTextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(key(section) { rememberScrollState() }),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (section) {
                        "面板尺寸与布局" -> {
                            Text(text = "当前页面排版方式", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val currentLayout = currentPage?.layoutType ?: PageLayoutType.FREE
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    PageLayoutType.LINEAR to "线性布局",
                                    PageLayoutType.GRID to "网格布局",
                                    PageLayoutType.FREE to "自由布局"
                                ).forEach { (mode, label) ->
                                    FilledTonalButton(
                                        onClick = {
                                            if (currentLayout != mode) {
                                                repository.setPageLayoutType(currentPageIndex, mode)
                                            }
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (currentLayout == mode) palette.accent else modalTrack,
                                            contentColor = if (currentLayout == mode) Color.White else modalTextPrimary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Text(text = label, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }

                            if (currentLayout == PageLayoutType.GRID && currentPage != null) {
                                Text("指定列数时按当前面板宽度适配图标与间距；0 为自动列数。", color = modalTextSecondary, fontSize = 11.sp)
                                GridDimensionControls(
                                    currentPage.gridColumnCount,
                                    currentPage.gridIconSizeDp,
                                    currentPage.gridItemGapDp,
                                    onColumns = { repository.fitPageGridColumnCount(currentPageIndex, it, repository.estimatedPageGridWidthDp()) },
                                    onIconSize = { repository.updatePageGridStyle(currentPageIndex, iconSizeDp = it) },
                                    onGap = { repository.updatePageGridStyle(currentPageIndex, itemGapDp = it) },
                                    rowGapDp = com.example.globalminusone.data.derivePageGridMetrics(
                                        repository.estimatedPageGridWidthDp(), currentPage.gridItemSizeDp, currentPage.gridItemGapDp,
                                        columnCount = currentPage.gridColumnCount, iconSizeDp = currentPage.gridIconSizeDp,
                                        rowGapDp = currentPage.gridRowGapDp).rowGapDp,
                                    onRowGap = { repository.updatePageGridStyle(currentPageIndex, rowGapDp = it) }
                                )
                                OutlinedButton(
                                    onClick = { repository.compactPageGridPlacements(currentPageIndex) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("网格紧凑对齐（取消空位重排）", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else if (currentLayout == PageLayoutType.LINEAR) {
                                Column {
                                    NumberStepper(
                                        "线性卡片间距 (dp)",
                                        theme.cardSpacingDp,
                                        { v ->
                                            repository.updateThemeConfig {
                                                it.copy(
                                                    cardSpacingDp = v.coerceIn(
                                                        com.example.globalminusone.data.MIN_CARD_SPACING_DP,
                                                        com.example.globalminusone.data.MAX_CARD_SPACING_DP
                                                    )
                                                )
                                            }
                                        },
                                        minimum = com.example.globalminusone.data.MIN_CARD_SPACING_DP,
                                        maximum = com.example.globalminusone.data.MAX_CARD_SPACING_DP
                                    )
                                    Slider(
                                        value = theme.cardSpacingDp.coerceIn(
                                            com.example.globalminusone.data.MIN_CARD_SPACING_DP,
                                            com.example.globalminusone.data.MAX_CARD_SPACING_DP
                                        ).toFloat(),
                                        onValueChange = { v -> repository.updateThemeConfig { it.copy(cardSpacingDp = v.toInt()) } },
                                        valueRange = com.example.globalminusone.data.MIN_CARD_SPACING_DP.toFloat()..com.example.globalminusone.data.MAX_CARD_SPACING_DP.toFloat(),
                                        colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                    )
                                }
                            }

                            Column {
                                NumberStepper("负一屏宽度 (%)", theme.panelWidthPercent, { v -> repository.updateThemeConfig { it.copy(panelWidthPercent = v) } }, minimum = 30, maximum = 100)
                                Slider(
                                    value = theme.panelWidthPercent.toFloat(),
                                    onValueChange = { v -> repository.updateThemeConfig { it.copy(panelWidthPercent = v.toInt()) } },
                                    valueRange = 30f..100f,
                                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                )
                            }

                            listOf("负一屏高度" to theme.panelHeightPercent, "离边缘位置" to theme.panelHorizontalOffsetPercent,
                                "垂直位置" to theme.panelVerticalPositionPercent).forEachIndexed { index, (label, value) ->
                                Column {
                                    NumberStepper("$label (%)", value, { v -> repository.updateThemeConfig { cfg -> when(index) {
                                        0 -> cfg.copy(panelHeightPercent = v)
                                        1 -> cfg.copy(panelHorizontalOffsetPercent = v)
                                        else -> cfg.copy(panelVerticalPositionPercent = v)
                                    } } }, minimum = if (index == 0) 30 else 0, maximum = 100)
                                    Slider(value = value.toFloat(), valueRange = if (index == 0) 30f..100f else 0f..100f,
                                        onValueChange = { v -> repository.updateThemeConfig { cfg -> when(index) {
                                            0 -> cfg.copy(panelHeightPercent = v.toInt())
                                            1 -> cfg.copy(panelHorizontalOffsetPercent = v.toInt())
                                            else -> cfg.copy(panelVerticalPositionPercent = v.toInt())
                                        } } }, colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent))
                                }
                            }

                            PropertySwitch("覆盖状态栏区域", theme.coverStatusBar) { value ->
                                repository.updateThemeConfig { it.copy(coverStatusBar = value) }
                            }
                        }
                        "主题与背景" -> {
                            PropertySwitch("面板跟随系统深色模式", theme.followSystemDark) { value ->
                                repository.updateThemeConfig { it.copy(followSystemDark = value) }
                            }
                            Text(text = "亮色 / 暗色 / 透明一键预设", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val isPureLight = !theme.isDarkMode && theme.cardBgAlphaPercent >= 95
                                val isPureDark = theme.isDarkMode && theme.cardBgAlphaPercent >= 95
                                FilledTonalButton(
                                    onClick = { repository.applyLightDarkPreset(darkMode = false, cardAlpha = 100, panelAlpha = 0) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isPureLight) palette.accent else modalTrack,
                                        contentColor = if (isPureLight) Color.White else modalTextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "亮色卡片", fontSize = 11.sp, maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = { repository.applyLightDarkPreset(darkMode = true, cardAlpha = 100, panelAlpha = 0) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isPureDark) palette.accent else modalTrack,
                                        contentColor = if (isPureDark) Color.White else modalTextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "暗色卡片", fontSize = 11.sp, maxLines = 1)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val isGlassLight = !theme.isDarkMode && theme.cardBgAlphaPercent in 10..94
                                val isGlassDark = theme.isDarkMode && theme.cardBgAlphaPercent in 10..94
                                val isZeroBg = theme.cardBgAlphaPercent < 10
                                FilledTonalButton(
                                    onClick = { repository.applyLightDarkPreset(darkMode = false, cardAlpha = 75, panelAlpha = 0) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isGlassLight) palette.accent else modalTrack,
                                        contentColor = if (isGlassLight) Color.White else modalTextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "半透亮色", fontSize = 11.sp, maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = { repository.applyLightDarkPreset(darkMode = true, cardAlpha = 72, panelAlpha = 0) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isGlassDark) palette.accent else modalTrack,
                                        contentColor = if (isGlassDark) Color.White else modalTextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "半透暗色", fontSize = 11.sp, maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = { repository.applyLightDarkPreset(darkMode = true, cardAlpha = 0, panelAlpha = 0) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isZeroBg) palette.accent else modalTrack,
                                        contentColor = if (isZeroBg) Color.White else modalTextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "全透明无底", fontSize = 11.sp, maxLines = 1)
                                }
                            }

                            Text(text = "背景遮罩与面板底框", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val isDarkMask = (theme.maskColorHex and 0x00FFFFFFL) < 0x808080L
                                FilledTonalButton(
                                    onClick = { repository.updateThemeConfig { it.copy(maskColorHex = 0xFF000000L) } },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isDarkMask) palette.accent else modalTrack,
                                        contentColor = if (isDarkMask) Color.White else modalTextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "暗色背景遮罩", fontSize = 11.sp, maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = { repository.updateThemeConfig { it.copy(maskColorHex = 0xFFF2F4F8L) } },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (!isDarkMask) palette.accent else modalTrack,
                                        contentColor = if (!isDarkMask) Color.White else modalTextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "亮色雾化遮罩", fontSize = 11.sp, maxLines = 1)
                                }
                            }

                            Column {
                                Text(text = "背景遮罩不透明度: ${theme.maskAlphaPercent}%", color = modalTextPrimary, fontSize = 12.sp)
                                Slider(
                                    value = theme.maskAlphaPercent.toFloat(),
                                    onValueChange = { v -> repository.updateThemeConfig { it.copy(maskAlphaPercent = v.toInt()) } },
                                    valueRange = 0f..95f,
                                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                )
                            }

                            Column {
                                Text(text = "背景高斯模糊半径: ${theme.backgroundBlurRadius}dp", color = modalTextPrimary, fontSize = 12.sp)
                                Slider(
                                    value = theme.backgroundBlurRadius.coerceIn(0, 50).toFloat(),
                                    onValueChange = { v -> repository.updateThemeConfig { it.copy(backgroundBlurRadius = v.toInt()) } },
                                    valueRange = 0f..50f,
                                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val panelRgb = theme.panelBgColorHex and 0x00FFFFFFL
                                val isPanelTrans = theme.panelBgAlphaPercent == 0
                                val isPanelLight = !isPanelTrans && panelRgb > 0x808080L
                                val isPanelDark = !isPanelTrans && panelRgb <= 0x808080L
                                FilledTonalButton(
                                    onClick = { repository.updateThemeConfig { it.copy(panelBgAlphaPercent = 0) } },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isPanelTrans) palette.accent else modalTrack,
                                        contentColor = if (isPanelTrans) Color.White else modalTextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "透明无底框", fontSize = 11.sp, maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        repository.updateThemeConfig {
                                            it.copy(
                                                panelBgColorHex = 0xFFF2F3F5L,
                                                panelBgAlphaPercent = if (it.panelBgAlphaPercent == 0) 85 else it.panelBgAlphaPercent
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isPanelLight) palette.accent else modalTrack,
                                        contentColor = if (isPanelLight) Color.White else modalTextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "亮色面板底框", fontSize = 11.sp, maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        repository.updateThemeConfig {
                                            it.copy(
                                                panelBgColorHex = 0xFF14171DL,
                                                panelBgAlphaPercent = if (it.panelBgAlphaPercent == 0) 85 else it.panelBgAlphaPercent
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isPanelDark) palette.accent else modalTrack,
                                        contentColor = if (isPanelDark) Color.White else modalTextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "暗色面板底框", fontSize = 11.sp, maxLines = 1)
                                }
                            }

                            Column {
                                Text(text = "面板底框不透明度: ${theme.panelBgAlphaPercent}%", color = modalTextPrimary, fontSize = 12.sp)
                                Slider(
                                    value = theme.panelBgAlphaPercent.toFloat(),
                                    onValueChange = { v ->
                                        repository.updateThemeConfig {
                                            val defaultHex = if ((it.panelBgColorHex and 0x00FFFFFFL) == 0L) {
                                                if (it.isDarkMode) 0xFF14171DL else 0xFFF2F3F5L
                                            } else it.panelBgColorHex
                                            it.copy(panelBgColorHex = defaultHex, panelBgAlphaPercent = v.toInt())
                                        }
                                    },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                )
                            }
                        }
                        "卡片外观" -> {
                            Text(text = "全局卡片亮暗色与不透明度", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val cardColors = listOf(
                                "纯白亮色" to 0xFFFFFFFFL,
                                "柔和浅灰" to 0xFFF2F4F7L,
                                "暗黑深灰" to 0xFF1E222BL,
                                "纯黑OLED" to 0xFF000000L
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                cardColors.forEach { (label, hex) ->
                                    val selected = (theme.defaultCardBgColorHex and 0x00FFFFFFL) == (hex and 0x00FFFFFFL)
                                    FilledTonalButton(
                                        onClick = {
                                            val dark = (hex and 0x00FFFFFFL) < 0x808080L
                                            repository.updateThemeConfig {
                                                it.copy(
                                                    isDarkMode = dark,
                                                    followSystemDark = false,
                                                    defaultCardBgColorHex = hex,
                                                    defaultTextColorHex = if (dark) 0xFFF5F7FAL else 0xFF1F2329L,
                                                    titleColorHex = if (dark) 0xFFFFFFFFL else 0xFF1F2329L
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (selected) palette.accent else modalTrack,
                                            contentColor = if (selected) Color.White else modalTextPrimary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = label, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }

                            Column {
                                Text(text = "卡片背景不透明度: ${theme.cardBgAlphaPercent}%", color = modalTextPrimary, fontSize = 11.sp)
                                Slider(
                                    value = theme.cardBgAlphaPercent.toFloat(),
                                    onValueChange = { v -> repository.updateThemeConfig { it.copy(cardBgAlphaPercent = v.toInt()) } },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                )
                            }

                            Column {
                                Text(text = "卡片圆角: ${theme.cardCornerRadiusDp}dp", color = modalTextPrimary, fontSize = 11.sp)
                                Slider(
                                    value = theme.cardCornerRadiusDp.toFloat(),
                                    onValueChange = { v -> repository.updateThemeConfig { it.copy(cardCornerRadiusDp = v.toInt()) } },
                                    valueRange = 0f..36f,
                                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                )
                            }

                            Column {
                                Text(text = "线性卡片间距: ${theme.cardSpacingDp}dp", color = modalTextPrimary, fontSize = 11.sp)
                                Slider(
                                    value = theme.cardSpacingDp.coerceIn(
                                        com.example.globalminusone.data.MIN_CARD_SPACING_DP,
                                        com.example.globalminusone.data.MAX_CARD_SPACING_DP
                                    ).toFloat(),
                                    onValueChange = { v -> repository.updateThemeConfig { it.copy(cardSpacingDp = v.toInt()) } },
                                    valueRange = com.example.globalminusone.data.MIN_CARD_SPACING_DP.toFloat()..com.example.globalminusone.data.MAX_CARD_SPACING_DP.toFloat(),
                                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                )
                            }
                        }
                        "文字与标题" -> {
                            Text(text = "全局文字与标题颜色及不透明度", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val textColors = listOf(
                                "深炭黑字" to 0xFF1F2329L,
                                "纯白亮字" to 0xFFFFFFFFL,
                                "柔和灰白" to 0xFFD0D5DDL,
                                "科技蓝字" to 0xFF2196F3L
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                textColors.forEach { (label, hex) ->
                                    val selected = (theme.defaultTextColorHex and 0x00FFFFFFL) == (hex and 0x00FFFFFFL)
                                    FilledTonalButton(
                                        onClick = {
                                            repository.updateThemeConfig {
                                                it.copy(defaultTextColorHex = hex, titleColorHex = hex)
                                            }
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (selected) palette.accent else modalTrack,
                                            contentColor = if (selected) Color.White else modalTextPrimary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = label, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }

                            Column {
                                Text(text = "文字内容不透明度: ${theme.textAlphaPercent}%", color = modalTextPrimary, fontSize = 12.sp)
                                Slider(
                                    value = theme.textAlphaPercent.toFloat(),
                                    onValueChange = { v -> repository.updateThemeConfig { it.copy(textAlphaPercent = v.toInt()) } },
                                    valueRange = 10f..100f,
                                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                )
                            }

                            Column {
                                Text(text = "卡片标题不透明度: ${theme.titleAlphaPercent}%", color = modalTextPrimary, fontSize = 12.sp)
                                Slider(
                                    value = theme.titleAlphaPercent.toFloat(),
                                    onValueChange = { v -> repository.updateThemeConfig { it.copy(titleAlphaPercent = v.toInt()) } },
                                    valueRange = 10f..100f,
                                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                                )
                            }
                        }
                        "转场动画" -> {
                            Text(text = "翻页转场效果", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                PageTransition.values().toList().chunked(3).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowItems.forEach { t ->
                                            val selected = theme.pageTransition == t
                                            Surface(
                                                onClick = { repository.updateThemeConfig { cfg -> cfg.copy(pageTransition = t) } },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(9.dp),
                                                color = if (selected) palette.accent else modalTrack
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = t.label,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (selected) Color.White else modalTextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }

                            Text(text = "动画时长（0 为关闭动画，滑块/按钮实时生效）", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "关闭 0" to 0,
                                    "极速 140" to 140,
                                    "流畅 220" to 220,
                                    "舒缓 360" to 360
                                ).forEach { (label, ms) ->
                                    val selected = theme.animationDurationMs == ms
                                    Surface(
                                        onClick = { repository.updateThemeConfig { it.copy(animationDurationMs = ms) } },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) palette.accent else modalTrack
                                    ) {
                                        Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) Color.White else modalTextPrimary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            NumberStepper("动画时长 (ms)", theme.animationDurationMs, { v -> repository.updateThemeConfig { it.copy(animationDurationMs = v) } }, minimum = 0, maximum = 800)
                            Slider(
                                value = theme.animationDurationMs.coerceIn(0, 800).toFloat(),
                                onValueChange = { v -> repository.updateThemeConfig { it.copy(animationDurationMs = v.toInt()) } },
                                valueRange = 0f..800f,
                                colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                            )
                        }
                        "手势与行为" -> {
                            NumberStepper("最小滑动距离 (dp)", state.triggerConfig.minimumSwipeDistanceDp, { v -> repository.updateTriggerConfig { it.copy(minimumSwipeDistanceDp = v) } }, minimum = 16, maximum = 160)
                            PropertySwitch("触发时振动", state.triggerConfig.vibrate) { value -> repository.updateTriggerConfig { it.copy(vibrate = value) } }
                            PropertySwitch("避让输入法", state.triggerConfig.avoidInputMethod) { value -> repository.updateTriggerConfig { it.copy(avoidInputMethod = value) } }
                            PropertySwitch("覆盖状态栏区域", theme.coverStatusBar) { value -> repository.updateThemeConfig { it.copy(coverStatusBar = value) } }
                            PropertySwitch("横屏自动关闭", state.advancedConfig.autoCloseInLandscape) { value -> repository.updateAdvancedConfig { it.copy(autoCloseInLandscape = value) } }
                            PropertySwitch("点击小组件自动关闭", state.advancedConfig.autoCloseOnWidgetClick) { value -> repository.updateAdvancedConfig { it.copy(autoCloseOnWidgetClick = value) } }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (state.editMode) Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.accent.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        "更改仅在本次编辑中预览，退出编辑时可统一保存或放弃。",
                        color = modalTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
                ) {
                    Text(text = if (state.editMode) "返回编辑" else "完成", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Restore the service modal handler when the property menu leaves composition. */
@Composable
private fun PropertyModalBack(onBack: () -> Unit) {
    val latestBack by rememberUpdatedState(onBack)
    val host = LocalPanelModalHost.current
    val owner = remember { Any() }
    DisposableEffect(host, owner) {
        PanelModalBackHandler.register(owner, PanelModalBackHandler.PRIORITY_PROPERTY_MODAL) {
            if (!host.dismiss()) latestBack()
            true
        }
        onDispose { PanelModalBackHandler.unregister(owner) }
    }
}

@Composable
private fun PropertySectionCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalPanelPalette.current
    val groupBg = if (palette.isDark) Color(0xFF272C36) else Color(0xFFF6F8FB)
    val groupBorder = if (palette.isDark) Color.White.copy(alpha = 0.07f) else Color(0xFFE6EAF0)
    val labelColor = if (palette.isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = groupBg,
        border = BorderStroke(0.75.dp, groupBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    color = labelColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
        }
    }
}

@Composable
private fun PropertyCategoryRow(
    title: String,
    description: String,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalPanelPalette.current
    val primary = if (palette.isDark) Color(0xFFF5F7FA) else Color(0xFF111827)
    val secondary = if (palette.isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val cardBg = when {
        destructive -> Color(0xFFEF4444).copy(alpha = if (palette.isDark) 0.14f else 0.08f)
        palette.isDark -> Color(0xFF272C36)
        else -> Color(0xFFF6F8FB)
    }
    val borderColor = when {
        destructive -> Color(0xFFEF4444).copy(alpha = 0.28f)
        palette.isDark -> Color.White.copy(alpha = 0.07f)
        else -> Color(0xFFE6EAF0)
    }
    val iconColor = if (destructive) Color(0xFFEF4444) else palette.accent
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = cardBg,
        border = BorderStroke(0.75.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = iconColor.copy(alpha = if (palette.isDark) 0.18f else 0.12f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (destructive) Color(0xFFEF4444) else primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(description, color = secondary, fontSize = 11.sp)
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = secondary.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PropertySpacingFields(values: List<Int>, onChange: (Int, Int) -> Unit) {
    listOf("上", "下", "左", "右").chunked(2).forEachIndexed { row, labels ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { column, label ->
                val index = row * 2 + column
                NumberStepper(label, values[index], { onChange(index, it) }, minimum = 0, maximum = 64, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PropertySwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPanelPalette.current
    val groupBg = if (palette.isDark) Color(0xFF272C36) else Color(0xFFF6F8FB)
    val groupBorder = if (palette.isDark) Color.White.copy(alpha = 0.07f) else Color(0xFFE6EAF0)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = groupBg,
        border = BorderStroke(0.75.dp, groupBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = if (palette.isDark) Color(0xFFF5F7FA) else Color(0xFF111827),
                fontSize = 13.sp
            )
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

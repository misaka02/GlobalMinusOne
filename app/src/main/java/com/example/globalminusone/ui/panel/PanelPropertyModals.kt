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
import com.example.globalminusone.ui.motion.overlayDialogMotion
import com.example.globalminusone.ui.motion.overlayScrim
import com.example.globalminusone.ui.motion.overlaySheetMotion

internal class ComponentEditDraft(item: ComponentItem) {
    var containerLayout by mutableStateOf(item.containerLayout)
    var containerColumns by mutableIntStateOf(item.containerColumns)
    var popupWidth by mutableIntStateOf(item.containerPopupWidthDp)
    var popupHeight by mutableIntStateOf(item.containerPopupHeightDp)
    var itemSize by mutableIntStateOf(item.containerItemSizeDp)
    var iconSize by mutableIntStateOf(item.containerIconSizeDp)
    var itemGap by mutableIntStateOf(item.containerItemGapDp)
    var rowGap by mutableIntStateOf(containerRowGapDp(item))
    var itemPaddingVertical by mutableIntStateOf(item.containerItemPaddingVerticalDp)
    var iconLabelGap by mutableIntStateOf(item.containerIconLabelGapDp)
    var spacing by mutableStateOf(
        listOf(
            item.paddingTopDp, item.paddingBottomDp, item.paddingLeftDp, item.paddingRightDp,
            item.marginTopDp, item.marginBottomDp, item.marginLeftDp, item.marginRightDp
        )
    )
    var x by mutableIntStateOf(item.x)
    var y by mutableIntStateOf(item.y)
    var w by mutableIntStateOf(item.w)
    var h by mutableIntStateOf(item.h)
    var gridSpanX by mutableIntStateOf(item.gridSpanX)
    var gridSpanY by mutableIntStateOf(item.gridSpanY)
    var cornerRadius by mutableIntStateOf(item.cornerRadiusDp)
    var rotation by mutableIntStateOf(item.rotationDeg)
    var showTitle by mutableStateOf(item.showTitle)
    var containerCompact by mutableStateOf(item.containerCompact)
    var autoHeight by mutableStateOf(item.autoHeight)
    var titleText by mutableStateOf(item.title)
    var textContent by mutableStateOf(item.textContent)
    var intentUri by mutableStateOf(item.intentUri)
    var appLaunchMode by mutableStateOf(item.appLaunchMode)
    var orientation by mutableStateOf(item.orientation)
    var selectedBgColor by mutableStateOf(item.bgColorHex)
    var bgAlphaPercent by mutableIntStateOf(item.bgAlphaPercent)
    var selectedTextColor by mutableStateOf(item.textColorHex)
    var textAlphaPercent by mutableIntStateOf(item.textAlphaPercent)

    fun applyTo(comp: ComponentItem): ComponentItem = comp.copy(
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
    val originalPageId = remember(repository, item.id) { repository.state.value.pages.getOrNull(pageIndex)?.id.orEmpty() }
    val resolvedPageIndex = liveState.pages.indexOfFirst { it.id == originalPageId }
    val propertyTarget = remember(originalPageId, item.id) { PropertyCardTarget(originalPageId, item.id) }
    var pendingDraft by remember(repository, item.id) { mutableStateOf(false) }
    var exitConfirmation by remember(repository, item.id) { mutableStateOf(false) }
    fun reportWriteResult(result: LayoutMutationResult) {
        if (result != LayoutMutationResult.APPLIED) {
            Toast.makeText(
                context,
                if (result == LayoutMutationResult.TARGET_CHANGED) "目标已变化或布局已锁定，请重新操作" else "更改未应用，输入已保留，请重试",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    fun requestClose() { if (pendingDraft) exitConfirmation = true else onDismiss() }

    val liveChildren = liveState.pages.getOrNull(resolvedPageIndex)?.components.orEmpty()
    val parentContainer = liveChildren.componentPath(item.id)?.dropLast(1)?.lastOrNull()
        ?.let { liveChildren.findComponent(it) }
    var section by remember(item.id, initialSection) { mutableStateOf(initialSection) }
    var confirmation by remember(item.id) { mutableStateOf("") }
    val draft = remember(item.id) { ComponentEditDraft(item) }

    PropertyModalBack {
        if (exitConfirmation) exitConfirmation = false
        else if (confirmation.isNotEmpty()) confirmation = ""
        else previousPropertySection(section, initialSection)?.let { section = it } ?: requestClose()
    }

    val modalBg = if (palette.isDark) Color(0xFF1F242D) else Color.White
    val modalTextPrimary = if (palette.isDark) Color(0xFFF5F7FA) else Color(0xFF111827)
    val modalTextSecondary = if (palette.isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val modalTrack = if (palette.isDark) Color(0xFF2D3340) else Color(0xFFF1F5F9)
    val modalBorder = if (palette.isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0)

    fun syncLive(): Boolean {
        val result = repository.tryUpdateComponent(originalPageId, item.id, draft::applyTo)
        pendingDraft = result != LayoutMutationResult.APPLIED
        reportWriteResult(result)
        return !pendingDraft
    }

    fun applyOrientationChoice(choice: ComponentOrientation) {
        val activePage = liveState.pages.getOrNull(resolvedPageIndex)
        val inGridPage = parentContainer == null && activePage?.layoutType == PageLayoutType.GRID
        val inGridContainer = parentContainer?.containerLayout == ContainerLayoutType.GRID
        draft.orientation = choice
        if (choice == ComponentOrientation.VERTICAL) {
            if (inGridPage || inGridContainer) {
                val defSpan = defaultGridSpanForOrientation(item.type, choice)
                if (defSpan != null && (draft.gridSpanX == 0 || draft.gridSpanY == 0 || draft.gridSpanX > draft.gridSpanY)) {
                    draft.gridSpanX = defSpan.first
                    draft.gridSpanY = defSpan.second
                    draft.autoHeight = false
                }
            } else if (draft.w > draft.h || draft.h <= item.type.defaultH) {
                draft.w = minOf(draft.w, 8).coerceAtLeast(5)
                draft.h = maxOf(draft.h, if (isSliderBarType(item.type)) 16 else 14)
                draft.autoHeight = false
            }
        } else if (choice == ComponentOrientation.HORIZONTAL) {
            if (inGridPage || inGridContainer) {
                if (draft.gridSpanX == 1 && draft.gridSpanY >= 2) {
                    draft.gridSpanX = 0
                    draft.gridSpanY = 0
                }
            } else if (draft.h > draft.w) {
                draft.w = item.type.defaultW.coerceAtLeast(12)
                draft.h = item.type.defaultH
            }
        }
        syncLive()
    }

    fun applyPresentationModeChoice(mode: LauncherCardPresentationMode) {
        val next = withCardPresentationMode(draft.applyTo(item), mode)
        draft.w = next.w
        draft.h = next.h
        draft.gridSpanX = next.gridSpanX
        draft.gridSpanY = next.gridSpanY
        draft.autoHeight = next.autoHeight
        draft.showTitle = next.showTitle
        draft.intentUri = next.intentUri
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
            .overlayScrim(Color.Black, scrimAlpha)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { requestClose() },
        contentAlignment = if (isLivePreviewSection) Alignment.BottomCenter else Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = (maxHeight - 24.dp).coerceAtLeast(1.dp).coerceAtMost(580.dp))
                .padding(bottom = if (isLivePreviewSection) 12.dp else 0.dp)
                .then(if (isLivePreviewSection) Modifier.overlaySheetMotion() else Modifier.overlayDialogMotion())
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
                ComponentEditHeader(
                    section = section,
                    initialSection = initialSection,
                    titleText = draft.titleText,
                    isLivePreviewSection = isLivePreviewSection,
                    peekCanvas = peekCanvas,
                    accent = palette.accent,
                    modalTrack = modalTrack,
                    modalTextPrimary = modalTextPrimary,
                    modalTextSecondary = modalTextSecondary,
                    onTogglePeek = { peekCanvas = !peekCanvas },
                    onNavigateBack = { previousPropertySection(section, initialSection)?.let { section = it } ?: requestClose() },
                    onClose = ::requestClose
                )

                if (section.isNotEmpty()) {
                    ComponentEditQuickTabs(
                        itemType = item.type,
                        section = section,
                        accent = palette.accent,
                        modalTrack = modalTrack,
                        modalTextSecondary = modalTextSecondary,
                        onSelectSection = { section = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(key(section) { rememberScrollState() }),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (section) {
                        "" -> ComponentEditOverviewSection(
                            item = item,
                            draft = draft,
                            isFreePage = liveState.pages.getOrNull(resolvedPageIndex)?.layoutType == PageLayoutType.FREE,
                            hasParentContainer = parentContainer != null,
                            addAbove = addAbove,
                            pendingDraft = pendingDraft,
                            palette = palette,
                            modalTrack = modalTrack,
                            modalTextPrimary = modalTextPrimary,
                            onSelectOrientation = ::applyOrientationChoice,
                            onSelectPresentationMode = ::applyPresentationModeChoice,
                            onSelectSection = { section = it },
                            onRequireDraftExit = { exitConfirmation = true },
                            onDismiss = onDismiss
                        )
                        "内容" -> ComponentEditContentSection(
                            item = item,
                            draft = draft,
                            originalPageId = originalPageId,
                            pendingDraft = pendingDraft,
                            repository = repository,
                            palette = palette,
                            modalTrack = modalTrack,
                            modalTextPrimary = modalTextPrimary,
                            modalTextSecondary = modalTextSecondary,
                            syncLive = ::syncLive,
                            onSelectPresentationMode = ::applyPresentationModeChoice,
                            onPickFileOrImage = { isImage ->
                                if (pendingDraft) {
                                    exitConfirmation = true
                                } else {
                                    val latest = repository.state.value
                                    val page = latest.pages.firstOrNull { it.id == originalPageId }
                                    if (latest.advancedConfig.layoutLocked) {
                                        Toast.makeText(context, "布局已锁定，请先解锁", Toast.LENGTH_SHORT).show()
                                    } else if (page?.components?.findComponent(item.id) != null) {
                                        folderReturn?.clear()
                                        onDismiss()
                                        if (isImage) PanelFileBrowser.requestImage(context, page.id, item.id)
                                        else PanelFileBrowser.requestFile(context, page.id, item.id)
                                    }
                                }
                            }
                        )
                        "位置与尺寸" -> ComponentEditGeometrySection(
                            item = item,
                            draft = draft,
                            liveState = liveState,
                            resolvedPageIndex = resolvedPageIndex,
                            originalPageId = originalPageId,
                            parentContainer = parentContainer,
                            pendingDraft = pendingDraft,
                            repository = repository,
                            palette = palette,
                            modalTrack = modalTrack,
                            modalTextPrimary = modalTextPrimary,
                            modalTextSecondary = modalTextSecondary,
                            syncLive = ::syncLive,
                            onSelectOrientation = ::applyOrientationChoice,
                            onReportWriteResult = ::reportWriteResult,
                            onRequestClose = ::requestClose
                        )
                        "外观" -> ComponentEditAppearanceSection(
                            draft = draft,
                            palette = palette,
                            modalTrack = modalTrack,
                            modalTextPrimary = modalTextPrimary,
                            syncLive = ::syncLive
                        )
                        "无线控制方式" -> {
                            if (systemController != null) RadioControlSettings(systemController)
                            else Text("无线控制器暂不可用，请重新打开卡片属性。", color = modalTextSecondary, fontSize = 12.sp)
                        }
                        "间距" -> {
                            PropertySectionCard(title = "内边距 (dp)") {
                                PropertySpacingFields(draft.spacing.take(4)) { index, value ->
                                    draft.spacing = draft.spacing.toMutableList().also { it[index] = value }
                                    syncLive()
                                }
                            }
                            PropertySectionCard(title = "外边距 (dp)") {
                                PropertySpacingFields(draft.spacing.drop(4)) { index, value ->
                                    draft.spacing = draft.spacing.toMutableList().also { it[index + 4] = value }
                                    syncLive()
                                }
                            }
                        }
                        "容器与行为", "内部排列", "内部尺寸与间距", "图标与文字留白", "文件夹窗口" ->
                            ComponentEditContainerSection(
                                section = section,
                                item = item,
                                draft = draft,
                                liveState = liveState,
                                resolvedPageIndex = resolvedPageIndex,
                                liveChildren = liveChildren,
                                repository = repository,
                                palette = palette,
                                modalTrack = modalTrack,
                                modalTextPrimary = modalTextPrimary,
                                modalTextSecondary = modalTextSecondary,
                                syncLive = ::syncLive,
                                onSelectSection = { section = it }
                            )
                        "重置与删除" -> {
                            PropertyCategoryRow("恢复默认大小", "仅恢复这张卡片的宽高与自动高度", icon = Icons.Rounded.RestartAlt) { confirmation = "恢复默认大小" }
                            PropertyCategoryRow("重置样式", "恢复颜色、透明度、圆角与间距", icon = Icons.Rounded.FormatPaint) { confirmation = "重置样式" }
                            PropertyCategoryRow(
                                "删除卡片",
                                if (item.type in setOf(ComponentType.CONTAINER_BOX, ComponentType.FOLDER)) "容器内的子卡片也会一并删除" else "此操作无法恢复",
                                icon = Icons.Rounded.DeleteOutline,
                                destructive = true
                            ) { confirmation = "删除卡片" }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (pendingDraft) Text("更改尚未应用，输入已保留。", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Button(
                    onClick = { if (!pendingDraft || syncLive()) onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
                ) {
                    Text(if (pendingDraft) "重试并完成" else "完成", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (confirmation.isNotEmpty()) {
        PanelAlertDialog(
            onDismissRequest = { confirmation = "" },
            title = { Text("确认$confirmation？") },
            text = {
                Text(
                    if (confirmation == "删除卡片") {
                        if (item.type in setOf(ComponentType.CONTAINER_BOX, ComponentType.FOLDER)) {
                            "删除后无法恢复，容器内的所有子卡片也会被删除。"
                        } else {
                            "删除这张卡片后无法恢复。"
                        }
                    } else {
                        "此操作将覆盖这张卡片的对应设置。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pendingDraft && !syncLive()) return@TextButton
                    val action = when (confirmation) {
                        "删除卡片" -> CardPropertyAction.DELETE
                        "重置样式" -> CardPropertyAction.RESET_STYLE
                        else -> CardPropertyAction.RESTORE_SIZE
                    }
                    val result = executeCardPropertyAction(repository, propertyTarget, action)
                    reportWriteResult(result)
                    if (result == LayoutMutationResult.APPLIED) {
                        confirmation = ""
                        onDismiss()
                    }
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
    if (exitConfirmation) {
        PanelAlertDialog(
            onDismissRequest = { exitConfirmation = false },
            title = { Text("仍有未应用的更改") },
            text = { Text("输入已保留。可以重试保存，或放弃尚未应用的输入。此前已应用的更改会保留。") },
            confirmButton = { TextButton(onClick = { if (syncLive()) { exitConfirmation = false; onDismiss() } }) { Text("重试并关闭") } },
            dismissButton = { TextButton(onClick = { exitConfirmation = false; onDismiss() }) { Text("放弃未应用输入") } }
        )
    }
}

@Composable
private fun ComponentEditHeader(
    section: String,
    initialSection: String,
    titleText: String,
    isLivePreviewSection: Boolean,
    peekCanvas: Boolean,
    accent: Color,
    modalTrack: Color,
    modalTextPrimary: Color,
    modalTextSecondary: Color,
    onTogglePeek: () -> Unit,
    onNavigateBack: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (section.isNotEmpty()) {
            Surface(
                onClick = onNavigateBack,
                shape = CircleShape,
                color = modalTrack,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = modalTextPrimary, modifier = Modifier.size(17.dp))
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
                onClick = onTogglePeek,
                shape = RoundedCornerShape(9.dp),
                color = if (peekCanvas) accent else accent.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        if (peekCanvas) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        tint = if (peekCanvas) Color.White else accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (peekCanvas) "恢复弹窗" else "透视画布",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (peekCanvas) Color.White else accent
                    )
                }
            }
        }
        Surface(
            onClick = onClose,
            shape = CircleShape,
            color = modalTrack,
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "关闭", tint = modalTextSecondary, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun ComponentEditQuickTabs(
    itemType: ComponentType,
    section: String,
    accent: Color,
    modalTrack: Color,
    modalTextSecondary: Color,
    onSelectSection: (String) -> Unit
) {
    val quickTabs = buildList {
        add("内容")
        add("位置与尺寸")
        add("外观")
        add("间距")
        if (itemType in setOf(ComponentType.SWITCH_WIFI, ComponentType.SWITCH_BLUETOOTH)) {
            add("无线控制方式")
        }
        if (itemType in setOf(ComponentType.CONTAINER_BOX, ComponentType.FOLDER)) {
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
                    onClick = { onSelectSection(tab) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedTab) accent else Color.Transparent
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


@Composable
private fun ComponentEditOverviewSection(
    item: ComponentItem,
    draft: ComponentEditDraft,
    isFreePage: Boolean,
    hasParentContainer: Boolean,
    addAbove: ((String) -> Unit)?,
    pendingDraft: Boolean,
    palette: PanelPalette,
    modalTrack: Color,
    modalTextPrimary: Color,
    onSelectOrientation: (ComponentOrientation) -> Unit,
    onSelectPresentationMode: (LauncherCardPresentationMode) -> Unit,
    onSelectSection: (String) -> Unit,
    onRequireDraftExit: () -> Unit,
    onDismiss: () -> Unit
) {
    val presentationMode = remember(
        item.type,
        draft.intentUri,
        draft.gridSpanX,
        draft.gridSpanY,
        draft.w,
        draft.h
    ) {
        parseCardPresentationMode(draft.applyTo(item))
    }
    if (presentationMode != null) {
        PropertySectionCard(title = "展现方式") {
            PresentationModeSelectorRow(
                selected = presentationMode,
                accent = palette.accent,
                track = modalTrack,
                textPrimary = modalTextPrimary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                onSelect = onSelectPresentationMode
            )
        }
    }
    if (supportsVerticalOrientation(item.type)) {
        PropertySectionCard(title = "排布方向") {
            OrientationSelectorRow(
                selected = draft.orientation,
                accent = palette.accent,
                track = modalTrack,
                textPrimary = modalTextPrimary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                onSelect = onSelectOrientation
            )
        }
    }
    PropertySectionCard(title = "内容与几何") {
        if (addAbove != null && !isFreePage && !hasParentContainer) {
            PropertyCategoryRow("在上方添加卡片", "在当前卡片之前插入", icon = Icons.Rounded.Add) {
                if (pendingDraft) onRequireDraftExit() else { addAbove(item.id); onDismiss() }
            }
        }
        PropertyCategoryRow("内容", "标题、文本、图片与链接", icon = Icons.Rounded.EditNote) { onSelectSection("内容") }
        PropertyCategoryRow("位置与尺寸", "坐标、宽高与自动高度", icon = Icons.Rounded.CenterFocusStrong) { onSelectSection("位置与尺寸") }
    }
    PropertySectionCard(title = "外观与高级") {
        PropertyCategoryRow("外观", "背景、文字颜色、透明度与圆角", icon = Icons.Rounded.Palette) { onSelectSection("外观") }
        PropertyCategoryRow("间距", "卡片内边距与外边距", icon = Icons.Rounded.ViewAgenda) { onSelectSection("间距") }
        if (item.type in setOf(ComponentType.SWITCH_WIFI, ComponentType.SWITCH_BLUETOOTH)) {
            PropertyCategoryRow("无线控制方式", "选择 Wi-Fi 与蓝牙的控制方式", icon = Icons.Rounded.Wifi) { onSelectSection("无线控制方式") }
        }
        if (item.type in setOf(ComponentType.CONTAINER_BOX, ComponentType.FOLDER)) {
            PropertyCategoryRow("容器与行为", "排列、尺寸与间距", icon = Icons.Rounded.Folder) { onSelectSection("容器与行为") }
        }
    }
    PropertyCategoryRow("重置与删除", "恢复默认大小、重置样式或删除卡片", icon = Icons.Rounded.DeleteOutline, destructive = true) { onSelectSection("重置与删除") }
}


/** Restore the service modal handler when the property menu leaves composition. */
@Composable
internal fun PropertyModalBack(onBack: () -> Unit) {
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
internal fun PropertySectionCard(
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
internal fun PropertyCategoryRow(
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
internal fun PropertySpacingFields(values: List<Int>, onChange: (Int, Int) -> Unit) {
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
internal fun PropertySwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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

/** A direct settings entry owns a boundary, not the invisible parents of the property tree. */
internal fun previousPropertySection(section: String, entrySection: String): String? = when {
    section == entrySection || section.isEmpty() -> null
    section in setOf("内部排列", "内部尺寸与间距", "图标与文字留白", "文件夹窗口") -> "容器与行为"
    else -> ""
}

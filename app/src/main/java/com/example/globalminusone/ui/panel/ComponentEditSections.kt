package com.example.globalminusone.ui.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.*
import com.example.globalminusone.ui.AppIcons as Icons

@Composable
internal fun OrientationSelectorRow(
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
internal fun PresentationModeSelectorRow(
    selected: LauncherCardPresentationMode,
    accent: Color,
    track: Color,
    textPrimary: Color,
    modifier: Modifier = Modifier,
    onSelect: (LauncherCardPresentationMode) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LauncherCardPresentationMode.entries.forEach { mode ->
            val active = selected == mode
            FilledTonalButton(
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f).height(34.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (active) accent else track,
                    contentColor = if (active) Color.White else textPrimary
                )
            ) {
                Text(mode.label, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}
@Composable
internal fun ComponentEditContentSection(
    item: ComponentItem,
    draft: ComponentEditDraft,
    originalPageId: String,
    pendingDraft: Boolean,
    repository: MinusOneRepository,
    palette: PanelPalette,
    modalTrack: Color,
    modalTextPrimary: Color,
    modalTextSecondary: Color,
    syncLive: () -> Boolean,
    onSelectPresentationMode: (LauncherCardPresentationMode) -> Unit,
    onPickFileOrImage: (isImage: Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "显示卡片标题栏", color = modalTextPrimary, fontSize = 12.sp)
        Switch(
            checked = draft.showTitle,
            onCheckedChange = { draft.showTitle = it; syncLive() },
            colors = SwitchDefaults.colors(checkedThumbColor = palette.accent)
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp)),
        color = modalTrack
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = draft.titleText,
                onValueChange = { draft.titleText = it; syncLive() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                singleLine = true,
                decorationBox = { inner -> Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) { inner() } },
                textStyle = TextStyle(color = modalTextPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(palette.accent)
            )
        }
    }

    if (item.type == ComponentType.NOTE_CARD || item.type == ComponentType.TEXT_LABEL) {
        Text(text = "文本内容", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = modalTrack
        ) {
            BasicTextField(
                value = draft.textContent,
                onValueChange = { draft.textContent = it; syncLive() },
                modifier = Modifier.fillMaxSize().padding(8.dp),
                textStyle = TextStyle(color = modalTextPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(palette.accent)
            )
        }
    }

    if (item.type == ComponentType.SHELL_COMMAND) {
        val currentSpec = ShellCommandSpec.fromComponent(
            item.copy(textContent = draft.textContent, intentUri = draft.intentUri)
        )
        val highRisk = ShellCommandSpec.isHighRiskCommand(draft.textContent)
        fun updateShellSpec(updated: ShellCommandSpec) {
            val clean = updated.copy(command = updated.command)
            draft.textContent = clean.command
            draft.intentUri = clean.encodeToIntentUri()
            syncLive()
        }

        Text(text = "Shell 脚本与执行策略", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SHELL_COMMAND_TEMPLATES.forEach { tpl ->
                FilterChip(
                    selected = draft.textContent.trim() == tpl.command.trim() && currentSpec.mode == tpl.mode,
                    onClick = {
                        draft.titleText = tpl.title
                        updateShellSpec(
                            currentSpec.copy(
                                command = tpl.command,
                                mode = tpl.mode,
                                requireConfirm = tpl.requireConfirm,
                                preferCompactTile = tpl.preferCompactTile
                            )
                        )
                    },
                    label = { Text(tpl.title, fontSize = 10.sp) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShellExecutionMode.entries.forEach { m ->
                FilterChip(
                    selected = currentSpec.mode == m,
                    onClick = { updateShellSpec(currentSpec.copy(mode = m)) },
                    label = { Text(m.badge, fontSize = 11.sp) }
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp, max = 140.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = modalTrack
        ) {
            BasicTextField(
                value = draft.textContent,
                onValueChange = { cmd -> updateShellSpec(currentSpec.copy(command = cmd.take(4096))) },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                textStyle = TextStyle(color = modalTextPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(palette.accent)
            )
        }

        if (highRisk) {
            Text(
                text = "检测到敏感关键字：运行时将强制要求二次确认。",
                color = Color(0xFFD32F2F),
                fontSize = 10.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("紧凑 1×1 图标卡展示", color = modalTextPrimary, fontSize = 11.sp)
            Switch(
                checked = currentSpec.preferCompactTile,
                onCheckedChange = { updateShellSpec(currentSpec.copy(preferCompactTile = it)) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("执行前确认弹窗", color = modalTextPrimary, fontSize = 11.sp)
            Switch(
                checked = currentSpec.requireConfirm || highRisk,
                enabled = !highRisk,
                onCheckedChange = { updateShellSpec(currentSpec.copy(requireConfirm = it)) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("超时:", color = modalTextSecondary, fontSize = 10.sp)
            listOf(5, 10, 20, 45).forEach { sec ->
                FilterChip(
                    selected = currentSpec.timeoutSec == sec,
                    onClick = { updateShellSpec(currentSpec.copy(timeoutSec = sec)) },
                    label = { Text("${sec}s", fontSize = 10.sp) }
                )
            }
        }
    }

    var showPodStyleDialog by remember(item.id) { mutableStateOf(false) }
    val draftPreviewItem = remember(
        item,
        draft.intentUri,
        draft.gridSpanX,
        draft.gridSpanY,
        draft.w,
        draft.h,
        draft.selectedBgColor,
        draft.bgAlphaPercent,
        draft.selectedTextColor,
        draft.textAlphaPercent
    ) { draft.applyTo(item) }
    if (showPodStyleDialog) {
        FloatingPodStyleDialog(
            item = draftPreviewItem,
            onDismiss = { showPodStyleDialog = false },
            onSave = { updatedStyle ->
                val mutated = withFloatingPodStyle(draftPreviewItem, updatedStyle)
                draft.intentUri = mutated.intentUri
                val saved = syncLive()
                if (saved) showPodStyleDialog = false
                saved
            }
        )
    }

    val currentMode = remember(draftPreviewItem) {
        parseCardPresentationMode(draftPreviewItem)
    }
    if (currentMode != null && item.type != ComponentType.APP_KEYBOARD_LAUNCHER) {
        Text("展现方式", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        PresentationModeSelectorRow(
            selected = currentMode,
            accent = palette.accent,
            track = modalTrack,
            textPrimary = modalTextPrimary,
            onSelect = onSelectPresentationMode
        )
    }
    if (item.type in POPOUT_CAPABLE_CARD_TYPES) {
        OutlinedButton(
            onClick = { showPodStyleDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("自定义弹出舱尺寸与样式…", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (item.type == ComponentType.APP_KEYBOARD_LAUNCHER) {
        val currentKbdSpec = remember(draft.intentUri, draft.gridSpanX, draft.gridSpanY, draft.w, draft.h) {
            val base = AppKeyboardLauncherSpec.fromIntentUri(draft.intentUri)
            if (!draft.intentUri.contains("mode=") && isSingleIconLauncherItem(draftPreviewItem)) {
                base.copy(presentationMode = LauncherCardPresentationMode.POPUP_ICON)
            } else {
                base
            }
        }
        fun updateKbdSpec(next: AppKeyboardLauncherSpec) {
            draft.intentUri = mergeFloatingPodStyleParams(next.encodeToIntentUri(), draft.intentUri)
            syncLive()
        }
        Text("展现方式", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        PresentationModeSelectorRow(
            selected = currentKbdSpec.presentationMode,
            accent = palette.accent,
            track = modalTrack,
            textPrimary = modalTextPrimary,
            onSelect = onSelectPresentationMode
        )
        Text("键盘快启设置", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyboardCardLayout.entries.forEach { layoutMode ->
                FilterChip(
                    selected = currentKbdSpec.layout == layoutMode,
                    onClick = { updateKbdSpec(currentKbdSpec.copy(layout = layoutMode)) },
                    label = { Text(layoutMode.label) }
                )
            }
        }
        PropertySwitch("全键盘显示数字行 (1-0)", currentKbdSpec.showNumberRow) {
            updateKbdSpec(currentKbdSpec.copy(showNumberRow = it))
        }
        PropertySwitch("启动应用后自动清空输入", currentKbdSpec.clearAfterLaunch) {
            updateKbdSpec(currentKbdSpec.copy(clearAfterLaunch = it))
        }
        PropertySwitch("仅剩唯一匹配时自动启动", currentKbdSpec.autoLaunchSingleMatch) {
            updateKbdSpec(currentKbdSpec.copy(autoLaunchSingleMatch = it))
        }
        PropertySwitch("候选应用显示名称", currentKbdSpec.showAppLabels) {
            updateKbdSpec(currentKbdSpec.copy(showAppLabels = it))
        }
        NumberStepper(
            label = "最多显示候选数量",
            value = currentKbdSpec.maxCandidates,
            minimum = 4,
            maximum = 16,
            onValueChange = { v -> updateKbdSpec(currentKbdSpec.copy(maxCandidates = v.coerceIn(4, 16))) }
        )
    }

    if (item.type == ComponentType.APP_ICON) {
        Text("打开方式", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.appLaunchMode == AppLaunchMode.NORMAL,
                onClick = { draft.appLaunchMode = AppLaunchMode.NORMAL; syncLive() },
                label = { Text("普通打开") }
            )
            FilterChip(
                selected = draft.appLaunchMode == AppLaunchMode.FLOATING,
                onClick = { draft.appLaunchMode = AppLaunchMode.FLOATING; syncLive() },
                label = { Text("小窗打开") }
            )
        }
    }
    if (item.type == ComponentType.SINGLE_IMAGE) {
        OutlinedButton(onClick = { onPickFileOrImage(true) }, modifier = Modifier.fillMaxWidth()) {
            Text("选择 / 更换图片", color = palette.accent)
        }
    }
    if (item.type == ComponentType.FILE_SHORTCUT) {
        Text("文件在面板内预览；权限失效时可重新选择。", color = modalTextSecondary, fontSize = 12.sp)
        OutlinedButton(onClick = { onPickFileOrImage(false) }, modifier = Modifier.fillMaxWidth()) {
            Text("重新选择文件", color = palette.accent)
        }
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
                    value = draft.intentUri,
                    onValueChange = { draft.intentUri = it; syncLive() },
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

@Composable
internal fun ComponentEditGeometrySection(
    item: ComponentItem,
    draft: ComponentEditDraft,
    liveState: GlobalMinusOneState,
    resolvedPageIndex: Int,
    originalPageId: String,
    parentContainer: ComponentItem?,
    pendingDraft: Boolean,
    repository: MinusOneRepository,
    palette: PanelPalette,
    modalTrack: Color,
    modalTextPrimary: Color,
    modalTextSecondary: Color,
    syncLive: () -> Boolean,
    onSelectOrientation: (ComponentOrientation) -> Unit,
    onReportWriteResult: (LayoutMutationResult) -> Unit,
    onRequestClose: () -> Unit
) {
    val activePage = liveState.pages.getOrNull(resolvedPageIndex)
    if (supportsVerticalOrientation(item.type)) {
        Text("排布方向样式", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        OrientationSelectorRow(
            selected = draft.orientation,
            accent = palette.accent,
            track = modalTrack,
            textPrimary = modalTextPrimary,
            onSelect = onSelectOrientation
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
                "当前在网格容器中（${maxColsHint} 列）：可设置子卡片所占网格列数与行数。此处调整的是子卡片自身尺寸；如需调节文件夹窗口大小，请从文件夹属性页进入。"
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
                Triple("整行×2", com.example.globalminusone.data.GRID_SPAN_FULL_ROW, 2),
                Triple("整行×4", com.example.globalminusone.data.GRID_SPAN_FULL_ROW, 4),
                Triple("整行×6", com.example.globalminusone.data.GRID_SPAN_FULL_ROW, 6)
            ).forEach { (presetLabel, sx, sy) ->
                val activeSpan = draft.gridSpanX == sx && draft.gridSpanY == sy
                Surface(
                    onClick = {
                        draft.gridSpanX = sx
                        draft.gridSpanY = sy
                        if (sy > 0) draft.autoHeight = false
                        if (inGridContainer && sx > 0 && !isContainerGridIconItem(item)) {
                            val hostCols = liveState.advancedConfig.gridColumns.coerceIn(8, 64)
                            val effCols = if (sx >= com.example.globalminusone.data.GRID_SPAN_FULL_ROW) maxColsHint else sx
                            draft.w = containerGridSpanWidth(effCols, maxColsHint, hostCols, parentContainer!!.containerItemGapDp)
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
            val displaySpanX = if (draft.gridSpanX >= com.example.globalminusone.data.GRID_SPAN_FULL_ROW) maxColsHint else draft.gridSpanX
            NumberStepper(
                label = "占格列数 (0默认)",
                value = displaySpanX,
                minimum = 0,
                maximum = 12,
                onValueChange = { v ->
                    val clamped = v.coerceIn(0, 12)
                    draft.gridSpanX = clamped
                    if (inGridContainer && clamped > 0 && !isContainerGridIconItem(item)) {
                        val hostCols = repository.state.value.advancedConfig.gridColumns.coerceIn(8, 64)
                        draft.w = containerGridSpanWidth(clamped, maxColsHint, hostCols, parentContainer!!.containerItemGapDp)
                    }
                    syncLive()
                },
                modifier = Modifier.weight(1f)
            )
            NumberStepper(
                label = "占格行数 (0默认)",
                value = draft.gridSpanY,
                minimum = 0,
                maximum = 12,
                onValueChange = { v ->
                    draft.gridSpanY = v.coerceIn(0, 12)
                    if (draft.gridSpanY > 0) draft.autoHeight = false
                    syncLive()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
    val freeGeometry = !inGridPage && !inGridContainer
    if (freeGeometry) {
        Text(
            text = if (parentContainer == null && activePage?.layoutType == PageLayoutType.LINEAR) "线性卡片宽高" else "自由布局坐标与宽高",
            color = palette.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }

    if (item.type in setOf(ComponentType.FOLDER, ComponentType.CONTAINER_BOX, ComponentType.OPERATION_BAR, ComponentType.APP_ICON, ComponentType.CUSTOM_SHORTCUT, ComponentType.STATIC_SHORTCUT)) {
        PropertySwitch("按内容自动高度", draft.autoHeight) {
            draft.autoHeight = it
            if (it) draft.gridSpanY = 0
            syncLive()
        }
    }
    if (liveState.pages.getOrNull(resolvedPageIndex)?.layoutType == PageLayoutType.LINEAR) {
        Text("线性布局按顺序排列，可调宽高；切换网格或自由布局后可多格摆放与缩放。", color = modalTextSecondary, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (pendingDraft && !syncLive()) return@OutlinedButton
                val result = repository.trySetPageLayoutType(originalPageId, PageLayoutType.GRID)
                onReportWriteResult(result)
                if (result == LayoutMutationResult.APPLIED) onRequestClose()
            }) {
                Text("切换网格布局", color = palette.accent, fontSize = 12.sp)
            }
            OutlinedButton(onClick = {
                if (pendingDraft && !syncLive()) return@OutlinedButton
                val result = repository.trySetPageLayoutType(originalPageId, PageLayoutType.FREE)
                onReportWriteResult(result)
                if (result == LayoutMutationResult.APPLIED) onRequestClose()
            }) {
                Text("切换自由布局", color = palette.accent, fontSize = 12.sp)
            }
        }
    }
    if (freeGeometry && (parentContainer != null || activePage?.layoutType == PageLayoutType.FREE)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberStepper(
                label = "X坐标",
                value = draft.x,
                maximum = (liveState.advancedConfig.gridColumns - draft.w).coerceAtLeast(0),
                onValueChange = {
                    draft.x = it.coerceIn(0, (repository.state.value.advancedConfig.gridColumns - draft.w).coerceAtLeast(0))
                    syncLive()
                },
                modifier = Modifier.weight(1f)
            )
            NumberStepper(label = "Y坐标", value = draft.y, onValueChange = { draft.y = it.coerceAtLeast(0); syncLive() }, modifier = Modifier.weight(1f))
        }
    }
    if (freeGeometry) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberStepper(
                label = "W (宽)",
                value = draft.w,
                minimum = 1,
                maximum = (liveState.advancedConfig.gridColumns - if (parentContainer == null && activePage?.layoutType == PageLayoutType.LINEAR) 0 else draft.x).coerceAtLeast(1),
                onValueChange = {
                    draft.w = it.coerceIn(1, (repository.state.value.advancedConfig.gridColumns - if (parentContainer == null && activePage?.layoutType == PageLayoutType.LINEAR) 0 else draft.x).coerceAtLeast(1))
                    syncLive()
                },
                modifier = Modifier.weight(1f)
            )
            NumberStepper(
                label = "H (高)",
                value = draft.h,
                minimum = 1,
                maximum = 120,
                onValueChange = { draft.h = it.coerceIn(1, 120); draft.autoHeight = false; syncLive() },
                modifier = Modifier.weight(1f)
            )
        }
    }
    if (inGridContainer && !isContainerGridIconItem(item)) {
        Text("精细宽高微调 (清除对应跨格设置)", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberStepper(
                label = "W (宽)",
                value = draft.w,
                minimum = 1,
                maximum = liveState.advancedConfig.gridColumns.coerceIn(8, 64),
                onValueChange = { v ->
                    draft.w = v.coerceIn(1, repository.state.value.advancedConfig.gridColumns.coerceIn(8, 64))
                    draft.gridSpanX = 0
                    syncLive()
                },
                modifier = Modifier.weight(1f)
            )
            NumberStepper(
                label = "H (高)",
                value = draft.h,
                minimum = 1,
                maximum = 120,
                onValueChange = { v ->
                    draft.h = v.coerceIn(1, 120)
                    draft.gridSpanY = 0
                    draft.autoHeight = false
                    syncLive()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ComponentEditAppearanceSection(
    draft: ComponentEditDraft,
    palette: PanelPalette,
    modalTrack: Color,
    modalTextPrimary: Color,
    syncLive: () -> Boolean
) {
    Text(text = "单卡片背景与透明度", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val bgOptions = listOf<Pair<String, Long?>>(
        "跟随全局" to null,
        "纯白亮色" to 0xFFFFFFFFL,
        "柔和暖白" to 0xFFF7F8FAL,
        "暗黑深灰" to 0xFF1E222BL,
        "纯黑OLED" to 0xFF000000L,
        "雾蓝卡片" to 0xFF1E3A5FL
    )
    bgOptions.chunked(3).forEach { rowItems ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowItems.forEach { (label, hex) ->
                val selected = draft.selectedBgColor == hex
                FilledTonalButton(
                    onClick = { draft.selectedBgColor = hex; syncLive() },
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
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayBgAlpha = if (draft.bgAlphaPercent >= 0) "${draft.bgAlphaPercent}%" else "跟随全局 (${palette.themeConfig.cardBgAlphaPercent}%)"
            Text(text = "卡片背景不透明度: $displayBgAlpha", color = modalTextPrimary, fontSize = 12.sp)
            if (draft.bgAlphaPercent >= 0) {
                Text(
                    text = "恢复跟随",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { draft.bgAlphaPercent = -1; syncLive() }
                )
            }
        }
        Slider(
            value = (if (draft.bgAlphaPercent >= 0) draft.bgAlphaPercent else palette.themeConfig.cardBgAlphaPercent).toFloat(),
            onValueChange = { draft.bgAlphaPercent = it.toInt(); syncLive() },
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
    textOptions.chunked(3).forEach { rowItems ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowItems.forEach { (label, hex) ->
                val selected = draft.selectedTextColor == hex
                FilledTonalButton(
                    onClick = { draft.selectedTextColor = hex; syncLive() },
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
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayTextAlpha = if (draft.textAlphaPercent >= 0) "${draft.textAlphaPercent}%" else "跟随全局 (${palette.themeConfig.textAlphaPercent}%)"
            Text(text = "文字与标题不透明度: $displayTextAlpha", color = modalTextPrimary, fontSize = 12.sp)
            if (draft.textAlphaPercent >= 0) {
                Text(
                    text = "恢复跟随",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { draft.textAlphaPercent = -1; syncLive() }
                )
            }
        }
        Slider(
            value = (if (draft.textAlphaPercent >= 0) draft.textAlphaPercent else palette.themeConfig.textAlphaPercent).toFloat(),
            onValueChange = { draft.textAlphaPercent = it.toInt(); syncLive() },
            valueRange = 10f..100f,
            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
        )
    }

    Text(text = "圆角、旋转与标题栏", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { draft.cornerRadius = -1; syncLive() }) { Text("恢复跟随", fontSize = 11.sp) }
            Text(
                text = if (draft.cornerRadius < 0) "卡片圆角: 跟随全局 (${palette.themeConfig.cardCornerRadiusDp}dp)" else "卡片圆角: ${draft.cornerRadius}dp",
                color = modalTextPrimary,
                fontSize = 11.sp
            )
        }
        NumberStepper(
            "圆角 (dp)",
            if (draft.cornerRadius < 0) palette.themeConfig.cardCornerRadiusDp else draft.cornerRadius,
            { draft.cornerRadius = it; syncLive() },
            maximum = 50
        )
        Slider(
            value = (if (draft.cornerRadius < 0) palette.themeConfig.cardCornerRadiusDp else draft.cornerRadius).toFloat(),
            onValueChange = { draft.cornerRadius = it.toInt(); syncLive() },
            valueRange = 0f..50f,
            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
        )
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            NumberStepper("旋转角度 (°)", draft.rotation, { draft.rotation = it; syncLive() }, maximum = 360)
        }
        Slider(
            value = draft.rotation.toFloat(),
            onValueChange = { draft.rotation = it.toInt(); syncLive() },
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
        )
    }
}

@Composable
internal fun ComponentEditContainerSection(
    section: String,
    item: ComponentItem,
    draft: ComponentEditDraft,
    liveState: GlobalMinusOneState,
    resolvedPageIndex: Int,
    liveChildren: List<ComponentItem>,
    repository: MinusOneRepository,
    palette: PanelPalette,
    modalTrack: Color,
    modalTextPrimary: Color,
    modalTextSecondary: Color,
    syncLive: () -> Boolean,
    onSelectSection: (String) -> Unit
) {
    when (section) {
        "容器与行为" -> {
            PropertyCategoryRow("内部排列", "自动排列或自由摆放、列数与间距", icon = Icons.Rounded.GridView) { onSelectSection("内部排列") }
            PropertyCategoryRow("图标与文字留白", "每项上下内边距、图文间距", icon = Icons.Rounded.Tune) { onSelectSection("图标与文字留白") }
            if (item.type == ComponentType.FOLDER) {
                PropertyCategoryRow("文件夹窗口", "展开后的弹出窗口宽高", icon = Icons.Rounded.OpenInNew) { onSelectSection("文件夹窗口") }
            }
            if (item.type == ComponentType.CONTAINER_BOX) {
                PropertySwitch("紧凑容器", draft.containerCompact) { draft.containerCompact = it; syncLive() }
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
                            val selected = draft.containerLayout == mode
                            Surface(
                                onClick = { draft.containerLayout = mode; syncLive() },
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
            if (draft.containerLayout == ContainerLayoutType.GRID) {
                GridDimensionControls(
                    draft.containerColumns,
                    draft.iconSize,
                    draft.itemGap,
                    onColumns = { draft.containerColumns = it; syncLive() },
                    onIconSize = { draft.iconSize = it; draft.itemSize = 0; syncLive() },
                    onGap = { draft.itemGap = it; syncLive() },
                    maxColumns = 8,
                    allowAutoColumns = false,
                    rowGapDp = draft.rowGap.coerceIn(com.example.globalminusone.data.MIN_GRID_GAP_DP, com.example.globalminusone.data.MAX_GRID_GAP_DP),
                    onRowGap = { draft.rowGap = it; draft.itemSize = 0; syncLive() }
                )
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
                            item = liveContainer.copy(containerColumns = draft.containerColumns.coerceIn(1, 8)),
                            availableWidthDp = availableWidthDp,
                            editingInside = true,
                            gridColumns = liveState.advancedConfig.gridColumns,
                            configuredHeightUnitDp = liveState.advancedConfig.heightUnitDp
                        )
                        repository.compactContainerGridPlacements(
                            pageIndex = resolvedPageIndex,
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
                if (draft.itemSize > 0) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("旧版固定格尺寸 ${draft.itemSize}dp 仍生效", color = modalTextSecondary, fontSize = 12.sp)
                        Surface(onClick = { draft.itemSize = 0; syncLive() }, shape = RoundedCornerShape(8.dp), color = palette.accent.copy(alpha = 0.12f)) {
                            Text("改为按内容自动", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                        }
                    }
                }
                NumberStepper("上下内边距 (dp)", draft.itemPaddingVertical, { draft.itemPaddingVertical = it; syncLive() }, minimum = 0, maximum = 48)
                NumberStepper(
                    "图文间隔 (dp)",
                    draft.iconLabelGap,
                    { draft.iconLabelGap = it; syncLive() },
                    minimum = com.example.globalminusone.data.MIN_ICON_LABEL_GAP_DP,
                    maximum = com.example.globalminusone.data.MAX_ICON_LABEL_GAP_DP
                )
                Surface(
                    onClick = { draft.itemPaddingVertical = 0; draft.iconLabelGap = 2; draft.itemSize = 0; syncLive() },
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
                    Surface(onClick = { draft.popupWidth = 0; syncLive() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(9.dp), color = modalTrack) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                            Text("宽度自动", color = modalTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Surface(onClick = { draft.popupWidth = 104; syncLive() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(9.dp), color = modalTrack) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                            Text("极简 104dp", color = modalTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Surface(onClick = { draft.popupHeight = 0; syncLive() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(9.dp), color = modalTrack) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                            Text("高度自动", color = modalTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                NumberStepper("窗口宽度 (dp)", draft.popupWidth, { draft.popupWidth = it; syncLive() }, minimum = 0, maximum = 1200)
                NumberStepper("窗口高度 (dp)", draft.popupHeight, { draft.popupHeight = it; syncLive() }, minimum = 0, maximum = 1200)
            }
        }
    }
}

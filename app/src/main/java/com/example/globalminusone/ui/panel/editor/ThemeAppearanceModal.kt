package com.example.globalminusone.ui.panel.editor

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.*
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.panel.PanelOverlayRadii
import com.example.globalminusone.ui.panel.panelOverlayTokens
import com.example.globalminusone.ui.motion.overlayScrim
import com.example.globalminusone.ui.motion.overlaySheetMotion
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.LayoutMutationResult
import com.example.globalminusone.data.MAX_CARD_SPACING_DP
import com.example.globalminusone.data.MIN_CARD_SPACING_DP
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.PageTransition
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.policy.derivePageGridMetrics
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelPalette

@Composable
fun ThemeAppearanceModal(
    currentPageIndex: Int,
    state: GlobalMinusOneState,
    repository: MinusOneRepository,
    initialSection: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val originalPageId = remember(repository) { repository.state.value.pages.getOrNull(currentPageIndex)?.id.orEmpty() }
    val resolvedPageIndex = state.pages.indexOfFirst { it.id == originalPageId }
    fun reportLayoutResult(result: LayoutMutationResult) {
        if (result != LayoutMutationResult.APPLIED) {
            Toast.makeText(
                context,
                if (result == LayoutMutationResult.TARGET_CHANGED) "页面已变化或布局已锁定" else "更改未应用，请重试",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val palette = LocalPanelPalette.current
    val defaultSection = remember(initialSection) { normalizeThemeAppearanceTab(initialSection) }
    var section by remember(initialSection) { mutableStateOf(defaultSection) }
    var peekCanvas by remember { mutableStateOf(false) }
    PropertyModalBack {
        onDismiss()
    }

    val theme = state.themeConfig
    val currentPage = state.pages.getOrNull(resolvedPageIndex)
    val tokens = panelOverlayTokens(palette)
    val modalBg = tokens.elevatedSurface
    val modalTextPrimary = tokens.textPrimary
    val modalTextSecondary = tokens.textSecondary
    val modalTrack = tokens.chipBg
    val modalBorder = tokens.border
    val scrimAlpha = if (peekCanvas) 0f else 0.20f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .overlayScrim(Color.Black, scrimAlpha)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { if (peekCanvas) peekCanvas = false else onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        if (peekCanvas) {
            com.example.globalminusone.ui.panel.PeekCanvasOverlayBar(
                label = "正在预览面板外观 · 点击恢复编辑",
                accent = palette.accent,
                tokens = tokens,
                onRestore = { peekCanvas = false }
            )
        } else {
        Surface(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.95f)
                .heightIn(max = (maxHeight - 24.dp).coerceAtLeast(1.dp).coerceAtMost(540.dp))
                .padding(bottom = 10.dp)
                .overlaySheetMotion()
                .clip(RoundedCornerShape(PanelOverlayRadii.Sheet))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            color = modalBg.copy(alpha = 0.98f),
            shape = RoundedCornerShape(PanelOverlayRadii.Sheet),
            border = BorderStroke(1.dp, modalBorder),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp
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

                val globalTabs = listOf("面板", "背景", "卡片", "动效")
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = modalTrack
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        globalTabs.forEach { tab ->
                            val selectedTab = section == tab
                            Surface(
                                onClick = { section = tab },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedTab) palette.accent else Color.Transparent
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                    Text(
                                        text = tab,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (selectedTab) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selectedTab) Color.White else modalTextSecondary,
                                        maxLines = 1
                                    )
                                }
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
                        "面板" -> {
                            ThemePanelLayoutSection(
                                currentPage = currentPage,
                                originalPageId = originalPageId,
                                resolvedPageIndex = resolvedPageIndex,
                                theme = theme,
                                repository = repository,
                                palette = palette,
                                modalTrack = modalTrack,
                                modalTextPrimary = modalTextPrimary,
                                modalTextSecondary = modalTextSecondary,
                                onReportLayoutResult = ::reportLayoutResult
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = modalTrack,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "触发距离、振动与自动收起请在「应用设置 → 触发与手势」中调整",
                                    color = modalTextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                        "背景" -> ThemeBackgroundSection(theme, repository, palette, modalTrack, modalTextPrimary)
                        "卡片" -> {
                            ThemeCardAppearanceSection(theme, repository, palette, modalTrack, modalTextPrimary)
                            HorizontalDivider(color = tokens.divider, thickness = 0.6.dp)
                            ThemeTypographySection(theme, repository, palette, modalTrack, modalTextPrimary)
                        }
                        "动效" -> ThemeTransitionSection(theme, repository, palette, modalTrack, modalTextPrimary)
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
}

@Composable
private fun ThemePanelLayoutSection(
    currentPage: PageConfig?,
    originalPageId: String,
    resolvedPageIndex: Int,
    theme: ThemeConfig,
    repository: MinusOneRepository,
    palette: PanelPalette,
    modalTrack: Color,
    modalTextPrimary: Color,
    modalTextSecondary: Color,
    onReportLayoutResult: (LayoutMutationResult) -> Unit
) {
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
                        onReportLayoutResult(repository.trySetPageLayoutType(originalPageId, mode))
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
            onColumns = { onReportLayoutResult(repository.tryFitPageGridColumnCount(originalPageId, it, repository.estimatedPageGridWidthDp())) },
            onIconSize = { repository.updatePageGridStyle(resolvedPageIndex, iconSizeDp = it) },
            onGap = { repository.updatePageGridStyle(resolvedPageIndex, itemGapDp = it) },
            rowGapDp = com.example.globalminusone.data.policy.derivePageGridMetrics(
                repository.estimatedPageGridWidthDp(), currentPage.gridItemSizeDp, currentPage.gridItemGapDp,
                columnCount = currentPage.gridColumnCount, iconSizeDp = currentPage.gridIconSizeDp,
                rowGapDp = currentPage.gridRowGapDp
            ).rowGapDp,
            onRowGap = { repository.updatePageGridStyle(resolvedPageIndex, rowGapDp = it) },
            rowCount = currentPage.gridRowCount,
            autoRows = currentPage.gridAutoRows,
            onRowCount = { count -> repository.updatePageGridStyle(resolvedPageIndex, rowCount = count, autoRows = count <= 0) },
            onAutoRows = { auto -> repository.updatePageGridStyle(resolvedPageIndex, rowCount = if (auto) 0 else 4, autoRows = auto) }
        )
        OutlinedButton(
            onClick = { repository.compactPageGridPlacements(resolvedPageIndex) },
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

    listOf(
        "负一屏高度" to theme.panelHeightPercent,
        "离边缘位置" to theme.panelHorizontalOffsetPercent,
        "垂直位置" to theme.panelVerticalPositionPercent
    ).forEachIndexed { index, (label, value) ->
        Column {
            NumberStepper("$label (%)", value, { v ->
                repository.updateThemeConfig { cfg ->
                    when (index) {
                        0 -> cfg.copy(panelHeightPercent = v)
                        1 -> cfg.copy(panelHorizontalOffsetPercent = v)
                        else -> cfg.copy(panelVerticalPositionPercent = v)
                    }
                }
            }, minimum = if (index == 0) 30 else 0, maximum = 100)
            Slider(
                value = value.toFloat(),
                valueRange = if (index == 0) 30f..100f else 0f..100f,
                onValueChange = { v ->
                    repository.updateThemeConfig { cfg ->
                        when (index) {
                            0 -> cfg.copy(panelHeightPercent = v.toInt())
                            1 -> cfg.copy(panelHorizontalOffsetPercent = v.toInt())
                            else -> cfg.copy(panelVerticalPositionPercent = v.toInt())
                        }
                    }
                },
                colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
            )
        }
    }

    PropertySwitch("覆盖状态栏区域", theme.coverStatusBar) { value ->
        repository.updateThemeConfig { it.copy(coverStatusBar = value) }
    }
}

@Composable
private fun ThemeBackgroundSection(
    theme: ThemeConfig,
    repository: MinusOneRepository,
    palette: PanelPalette,
    modalTrack: Color,
    modalTextPrimary: Color
) {
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

@Composable
private fun ThemeCardAppearanceSection(
    theme: ThemeConfig,
    repository: MinusOneRepository,
    palette: PanelPalette,
    modalTrack: Color,
    modalTextPrimary: Color
) {
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

@Composable
private fun ThemeTypographySection(
    theme: ThemeConfig,
    repository: MinusOneRepository,
    palette: PanelPalette,
    modalTrack: Color,
    modalTextPrimary: Color
) {
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

@Composable
private fun ThemeTransitionSection(
    theme: ThemeConfig,
    repository: MinusOneRepository,
    palette: PanelPalette,
    modalTrack: Color,
    modalTextPrimary: Color
) {
    PanelAnimationSettingsSection(theme, repository, palette, modalTrack, modalTextPrimary)
}

internal fun normalizeThemeAppearanceTab(initialSection: String): String = when (initialSection) {
    "主题与背景", "背景" -> "背景"
    "卡片外观", "文字与标题", "卡片" -> "卡片"
    "转场动画", "动效" -> "动效"
    else -> "面板"
}

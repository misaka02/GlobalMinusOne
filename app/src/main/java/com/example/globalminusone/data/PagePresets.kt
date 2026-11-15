package com.example.globalminusone.data

import java.util.UUID

/**
 * Curated ready-to-use layout presets offered alongside blank Grid/Free/Linear pages
 * when creating a new page.
 */
enum class PagePresetOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val layoutType: PageLayoutType,
    val badge: String,
    val isPreset: Boolean = true
) {
    CONTROL_CENTER(
        id = "control_center",
        title = "控制中心",
        subtitle = "竖排亮度/音量调节 · Wi-Fi/蓝牙/手电筒/静音",
        layoutType = PageLayoutType.GRID,
        badge = "4列网格",
        isPreset = true
    ),
    APP_FOLDERS(
        id = "app_folders",
        title = "应用与文件夹",
        subtitle = "应用抽屉 · 最近使用 · 预置分类文件夹",
        layoutType = PageLayoutType.GRID,
        badge = "4列网格",
        isPreset = true
    ),
    PRODUCTIVITY(
        id = "productivity",
        title = "效率工作台",
        subtitle = "便签 · 剪贴板 · 文件浏览 · 常用亮度/音量",
        layoutType = PageLayoutType.LINEAR,
        badge = "线性流式",
        isPreset = true
    ),
    SYSTEM_MONITOR(
        id = "system_monitor",
        title = "硬件状态看板",
        subtitle = "电池详情 · 竖排内存/存储监控 · 开机时间",
        layoutType = PageLayoutType.GRID,
        badge = "4列网格",
        isPreset = true
    ),
    BLANK_GRID(
        id = "blank_grid",
        title = "空白网格页",
        subtitle = "可指定列数 · 支持多格卡片与自由摆位",
        layoutType = PageLayoutType.GRID,
        badge = "网格布局",
        isPreset = false
    ),
    BLANK_FREE(
        id = "blank_free",
        title = "空白自由页",
        subtitle = "使用自定义点阵 · 任意坐标与尺寸缩放",
        layoutType = PageLayoutType.FREE,
        badge = "自由布局",
        isPreset = false
    ),
    BLANK_LINEAR(
        id = "blank_linear",
        title = "空白线性页",
        subtitle = "纵向流式卡片 · 自动适应高度排序",
        layoutType = PageLayoutType.LINEAR,
        badge = "线性布局",
        isPreset = false
    )
}

private fun presetUid(): String = UUID.randomUUID().toString()

fun buildPagePresetConfig(
    preset: PagePresetOption,
    customName: String = "",
    estimatedGridWidthDp: Float = 268f
): PageConfig {
    val cleanName = customName.trim()
    val pageId = "page_${preset.id}_${presetUid().take(8)}"
    val fitted4 = fitPageGridColumns(estimatedGridWidthDp, 4)
    return when (preset) {
        PagePresetOption.CONTROL_CENTER -> PageConfig(
            id = pageId,
            name = cleanName.ifEmpty { preset.title },
            layoutType = PageLayoutType.GRID,
            gridColumnCount = fitted4.columnCount,
            gridItemSizeDp = fitted4.itemSizeDp,
            gridItemGapDp = fitted4.itemGapDp,
            gridIconSizeDp = fitted4.iconSizeDp,
            components = listOf(
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.BRIGHTNESS_BAR,
                    title = "亮度调节",
                    showTitle = false,
                    orientation = ComponentOrientation.VERTICAL,
                    w = 6,
                    h = 16,
                    gridSpanX = 1,
                    gridSpanY = 3,
                    gridCol = 0,
                    gridRow = 0
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.VOLUME_BAR,
                    title = "媒体音量",
                    showTitle = false,
                    orientation = ComponentOrientation.VERTICAL,
                    w = 6,
                    h = 16,
                    gridSpanX = 1,
                    gridSpanY = 3,
                    gridCol = 1,
                    gridRow = 0
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.SWITCH_WIFI,
                    title = "WiFi",
                    showTitle = false,
                    gridSpanX = 1,
                    gridSpanY = 1,
                    gridCol = 2,
                    gridRow = 0
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.SWITCH_BLUETOOTH,
                    title = "蓝牙",
                    showTitle = false,
                    gridSpanX = 1,
                    gridSpanY = 1,
                    gridCol = 3,
                    gridRow = 0
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.SWITCH_TORCH,
                    title = "手电筒",
                    showTitle = false,
                    gridSpanX = 1,
                    gridSpanY = 1,
                    gridCol = 2,
                    gridRow = 1
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.SWITCH_MUTE,
                    title = "静音",
                    showTitle = false,
                    gridSpanX = 1,
                    gridSpanY = 1,
                    gridCol = 3,
                    gridRow = 1
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.ACTION_LOCK,
                    title = "锁屏",
                    showTitle = false,
                    gridSpanX = 1,
                    gridSpanY = 1,
                    gridCol = 2,
                    gridRow = 2
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.ACTION_SCREENSHOT,
                    title = "截屏",
                    showTitle = false,
                    gridSpanX = 1,
                    gridSpanY = 1,
                    gridCol = 3,
                    gridRow = 2
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.OPERATION_BAR,
                    title = "操作栏",
                    showTitle = false,
                    gridSpanX = GRID_SPAN_FULL_ROW,
                    gridSpanY = 1,
                    gridCol = 0,
                    gridRow = 3
                )
            )
        )
        PagePresetOption.APP_FOLDERS -> PageConfig(
            id = pageId,
            name = cleanName.ifEmpty { preset.title },
            layoutType = PageLayoutType.GRID,
            gridColumnCount = fitted4.columnCount,
            gridItemSizeDp = fitted4.itemSizeDp,
            gridItemGapDp = fitted4.itemGapDp,
            gridIconSizeDp = fitted4.iconSizeDp,
            components = listOf(
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.RECENT_APPS_CARD,
                    title = "最近运行",
                    showTitle = true,
                    gridSpanX = GRID_SPAN_FULL_ROW,
                    gridSpanY = 2,
                    gridCol = 0,
                    gridRow = 0
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.FOLDER,
                    title = "快捷工具",
                    showTitle = false,
                    containerColumns = 4,
                    containerLayout = ContainerLayoutType.GRID,
                    gridSpanX = 1,
                    gridSpanY = 1,
                    gridCol = 0,
                    gridRow = 2,
                    containerComponents = listOf(
                        ComponentItem(
                            id = presetUid(),
                            type = ComponentType.ACTION_WECHAT_SCAN,
                            title = "扫一扫",
                            showTitle = false
                        ),
                        ComponentItem(
                            id = presetUid(),
                            type = ComponentType.ACTION_SCREENSHOT,
                            title = "截屏",
                            showTitle = false
                        ),
                        ComponentItem(
                            id = presetUid(),
                            type = ComponentType.ACTION_LOCK,
                            title = "锁屏",
                            showTitle = false
                        )
                    )
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.FOLDER,
                    title = "便签与文件",
                    showTitle = false,
                    containerColumns = 4,
                    containerLayout = ContainerLayoutType.GRID,
                    gridSpanX = 1,
                    gridSpanY = 1,
                    gridCol = 1,
                    gridRow = 2,
                    containerComponents = listOf(
                        ComponentItem(
                            id = presetUid(),
                            type = ComponentType.FILE_BROWSER,
                            title = "文件浏览",
                            showTitle = false
                        )
                    )
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.FILE_BROWSER,
                    title = "文件管理",
                    showTitle = true,
                    gridSpanX = 2,
                    gridSpanY = 1,
                    gridCol = 2,
                    gridRow = 2
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.APP_DRAWER_CARD,
                    title = "应用抽屉",
                    showTitle = true,
                    gridSpanX = GRID_SPAN_FULL_ROW,
                    gridSpanY = 4,
                    gridCol = 0,
                    gridRow = 3
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.OPERATION_BAR,
                    title = "操作栏",
                    showTitle = false,
                    gridSpanX = GRID_SPAN_FULL_ROW,
                    gridSpanY = 1,
                    gridCol = 0,
                    gridRow = 7
                )
            )
        )
        PagePresetOption.PRODUCTIVITY -> PageConfig(
            id = pageId,
            name = cleanName.ifEmpty { preset.title },
            layoutType = PageLayoutType.LINEAR,
            components = listOf(
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.NOTE_CARD,
                    title = "随手便签",
                    textContent = "在此快速记录待办与灵感...",
                    showTitle = true,
                    w = 32,
                    h = 12
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.CLIPBOARD_CARD,
                    title = "剪贴板",
                    showTitle = true,
                    w = 32,
                    h = 11
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.FILE_BROWSER,
                    title = "面板文件浏览",
                    showTitle = true,
                    w = 32,
                    h = 8
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.BRIGHTNESS_BAR,
                    title = "屏幕亮度",
                    showTitle = false,
                    orientation = ComponentOrientation.HORIZONTAL,
                    w = 32,
                    h = 5
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.VOLUME_BAR,
                    title = "媒体音量",
                    showTitle = false,
                    orientation = ComponentOrientation.HORIZONTAL,
                    w = 32,
                    h = 5
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.OPERATION_BAR,
                    title = "操作栏",
                    showTitle = false,
                    w = 32,
                    h = 5
                )
            )
        )
        PagePresetOption.SYSTEM_MONITOR -> PageConfig(
            id = pageId,
            name = cleanName.ifEmpty { preset.title },
            layoutType = PageLayoutType.GRID,
            gridColumnCount = fitted4.columnCount,
            gridItemSizeDp = fitted4.itemSizeDp,
            gridItemGapDp = fitted4.itemGapDp,
            gridIconSizeDp = fitted4.iconSizeDp,
            components = listOf(
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.STAT_BATTERY_DETAIL,
                    title = "电量详情",
                    showTitle = true,
                    gridSpanX = 2,
                    gridSpanY = 2,
                    gridCol = 0,
                    gridRow = 0
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.STAT_RAM,
                    title = "内存占用",
                    showTitle = false,
                    orientation = ComponentOrientation.VERTICAL,
                    w = 6,
                    h = 14,
                    gridSpanX = 1,
                    gridSpanY = 2,
                    gridCol = 2,
                    gridRow = 0
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.STAT_ROM,
                    title = "存储占用",
                    showTitle = false,
                    orientation = ComponentOrientation.VERTICAL,
                    w = 6,
                    h = 14,
                    gridSpanX = 1,
                    gridSpanY = 2,
                    gridCol = 3,
                    gridRow = 0
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.STAT_UPTIME,
                    title = "开机时间",
                    showTitle = false,
                    gridSpanX = 2,
                    gridSpanY = 1,
                    gridCol = 0,
                    gridRow = 2
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.STAT_BATTERY_BAR,
                    title = "剩余电量",
                    showTitle = false,
                    gridSpanX = 2,
                    gridSpanY = 1,
                    gridCol = 2,
                    gridRow = 2
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.HITOKOTO_CARD,
                    title = "一言",
                    showTitle = true,
                    gridSpanX = GRID_SPAN_FULL_ROW,
                    gridSpanY = 1,
                    gridCol = 0,
                    gridRow = 3
                ),
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.OPERATION_BAR,
                    title = "操作栏",
                    showTitle = false,
                    gridSpanX = GRID_SPAN_FULL_ROW,
                    gridSpanY = 1,
                    gridCol = 0,
                    gridRow = 4
                )
            )
        )
        PagePresetOption.BLANK_GRID -> PageConfig(
            id = pageId,
            name = cleanName,
            layoutType = PageLayoutType.GRID,
            components = listOf(
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.OPERATION_BAR,
                    title = "操作栏",
                    showTitle = false,
                    w = 32,
                    h = 4,
                    gridSpanX = GRID_SPAN_FULL_ROW,
                    gridSpanY = 1
                )
            )
        )
        PagePresetOption.BLANK_FREE -> PageConfig(
            id = pageId,
            name = cleanName,
            layoutType = PageLayoutType.FREE,
            components = listOf(
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.OPERATION_BAR,
                    title = "操作栏",
                    x = 1,
                    y = 44,
                    w = 30,
                    h = 4,
                    showTitle = false
                )
            )
        )
        PagePresetOption.BLANK_LINEAR -> PageConfig(
            id = pageId,
            name = cleanName,
            layoutType = PageLayoutType.LINEAR,
            components = listOf(
                ComponentItem(
                    id = presetUid(),
                    type = ComponentType.OPERATION_BAR,
                    title = "操作栏",
                    x = 0,
                    y = 2,
                    w = 32,
                    h = 4,
                    showTitle = false
                )
            )
        )
    }
}

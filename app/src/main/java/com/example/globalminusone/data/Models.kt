package com.example.globalminusone.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** Sentinel meaning row gap inherits the horizontal item gap. */
const val ROW_GAP_INHERIT = -999
const val CURRENT_STATE_SCHEMA_VERSION = 2
const val MIN_GRID_GAP_DP = -24
const val MAX_GRID_GAP_DP = 48
const val MIN_CARD_SPACING_DP = -24
const val MAX_CARD_SPACING_DP = 32
const val MIN_ICON_LABEL_GAP_DP = -16
const val MAX_ICON_LABEL_GAP_DP = 48

@Serializable
data class NotificationPreferencesConfig(
    val reminderEnabled: Boolean = false,
    val vibrate: Boolean = false,
    val autoJumpEnabled: Boolean = false,
    val allowedPackages: Set<String> = emptySet()
)

/**
 * 触发方式
 */
@Serializable
enum class TriggerWay(val label: String) {
    SLIDE("直接滑动"),
    SECOND_SLIDE("二次滑动"),
    SIDE_PULLDOWN("侧边下滑"),
    SUSPENSION_BALL("悬浮球"),
    NONE("无")
}

/**
 * 触发位置
 */
@Serializable
enum class TriggerPosition(val label: String) {
    LEFT("左侧"),
    RIGHT("右侧"),
    BOTH("左和右"),
    TOP("顶部"),
    BOTTOM("底部")
}

/**
 * 呼出方向
 */
@Serializable
enum class ShowSideMode(val label: String) {
    FOLLOW_TRIGGER("跟随触发位置"),
    LEFT("总是从左边呼出"),
    RIGHT("总是从右边呼出")
}

/**
 * 手势与按键动作定义
 */
@Serializable
enum class GestureAction(val label: String) {
    NONE("无"),
    LEFT_OPEN_NOS("从左边呼出负一屏"),
    RIGHT_OPEN_NOS("从右边呼出负一屏"),
    BACK("返回键"),
    HOME("返回桌面"),
    RECENT("最近任务"),
    LAST_APP("切换到上一个应用"),
    NOTIFICATION("下拉通知栏"),
    QUICK_SETTINGS("快捷设置面板"),
    LOCK("锁屏"),
    POWER("电源菜单"),
    SCREENSHOT("截屏")
}

/**
 * 兼容旧版的触发侧枚举
 */
@Serializable
enum class TriggerSide(val label: String) {
    LEFT("左侧"),
    RIGHT("右侧"),
    BOTH("左和右")
}

/**
 * 兼容旧版的触发样式枚举
 */
@Serializable
enum class TriggerStyle(val label: String) {
    EDGE_BAR("手势触发条"),
    FLOATING_BALL("悬浮球")
}

/**
 * 触发设置配置模型
 */
@Serializable
data class TriggerConfig(
    val serviceEnabled: Boolean = true,
    val triggerWay: TriggerWay = TriggerWay.SLIDE,
    val triggerPosition: TriggerPosition = TriggerPosition.LEFT,
    val defaultShowSide: ShowSideMode = ShowSideMode.FOLLOW_TRIGGER,
    val touchWidthPercent: Int = 3,
    val touchHeightPercent: Int = 15,
    val touchOffsetPercent: Int = 70,
    val touchVisible: Boolean = true,
    val touchAlpha: Float = 0.45f,
    val touchColorHex: Long = 0xFFDFDFDD,
    val vibrate: Boolean = true,
    val useSystemVibrate: Boolean = false,
    val avoidInputMethod: Boolean = true,
    val minimumSwipeDistanceDp: Int = 48,
    val secondSlideTimeoutMs: Int = 1200,
    // 侧边下滑手势条距屏幕左/右边缘的自定义水平偏移（dp）；-1 表示自动避让系统返回区域
    val sidePullHorizontalOffsetDp: Int = -1,

    // 各方向与按压手势独立动作配置
    val singleTapAction: GestureAction = GestureAction.NONE,
    val doubleTapAction: GestureAction = GestureAction.NONE,
    val longPressAction: GestureAction = GestureAction.NONE,
    val swipeUpAction: GestureAction = GestureAction.NONE,
    val swipeDownAction: GestureAction = GestureAction.NONE,
    val swipeLeftAction: GestureAction = GestureAction.RIGHT_OPEN_NOS,
    val swipeRightAction: GestureAction = GestureAction.LEFT_OPEN_NOS,

    // 临时关闭截止时间戳（毫秒）
    val tempDisabledUntilMs: Long = 0L,

    // 兼容旧版调用字段
    val enabled: Boolean = serviceEnabled,
    val side: TriggerSide = when (triggerPosition) {
        TriggerPosition.LEFT -> TriggerSide.LEFT
        TriggerPosition.RIGHT -> TriggerSide.RIGHT
        TriggerPosition.BOTH -> TriggerSide.BOTH
        TriggerPosition.TOP -> TriggerSide.LEFT
        TriggerPosition.BOTTOM -> TriggerSide.LEFT
    },
    val style: TriggerStyle = when (triggerWay) {
        TriggerWay.SUSPENSION_BALL -> TriggerStyle.FLOATING_BALL
        else -> TriggerStyle.EDGE_BAR
    },
    val verticalPositionPercent: Float = touchOffsetPercent / 100f,
    val heightDp: Int = 140,
    val widthDp: Int = 10,
    val indicatorAlpha: Float = touchAlpha,
    val indicatorColorHex: String = "#DFDFDD",
    val vibrateOnTrigger: Boolean = vibrate,
    val hideInLandscape: Boolean = true,
    val autoStartOnBoot: Boolean = true,
    val blacklistPackages: List<String> = emptyList()
) {
    fun isTempDisabled(now: Long = System.currentTimeMillis()): Boolean = tempDisabledUntilMs > now

    fun withPosition(newPosition: TriggerPosition): TriggerConfig = copy(
        triggerPosition = newPosition,
        side = when (newPosition) {
            TriggerPosition.LEFT -> TriggerSide.LEFT
            TriggerPosition.RIGHT -> TriggerSide.RIGHT
            TriggerPosition.BOTH -> TriggerSide.BOTH
            TriggerPosition.TOP -> TriggerSide.LEFT
            TriggerPosition.BOTTOM -> TriggerSide.LEFT
        }
    )

    fun withTouchGeometry(
        offsetPercent: Int = touchOffsetPercent,
        heightPercent: Int = touchHeightPercent,
        widthPercent: Int = touchWidthPercent
    ): TriggerConfig {
        val clampedOffset = offsetPercent.coerceIn(0, 100)
        val clampedHeight = heightPercent.coerceIn(5, 100)
        val clampedWidth = widthPercent.coerceIn(1, 20)
        return copy(
            touchOffsetPercent = clampedOffset,
            verticalPositionPercent = clampedOffset / 100f,
            touchHeightPercent = clampedHeight,
            touchWidthPercent = clampedWidth
        )
    }
}

/**
 * 悬浮球设置配置模型
 */
@Serializable
data class SuspensionBallConfig(
    val radiusDp: Int = 22,
    val alpha: Float = 0.75f,
    val breatheMode: Boolean = false,
    val autoFadeSeconds: Int = 3,
    val mouseMode: Boolean = false,
    val posXPercent: Float = 0.92f,
    val posYPercent: Float = 0.65f,
    val singleTapAction: GestureAction = GestureAction.LEFT_OPEN_NOS,
    val doubleTapAction: GestureAction = GestureAction.RECENT,
    val longPressAction: GestureAction = GestureAction.LOCK,
    val swipeUpAction: GestureAction = GestureAction.HOME,
    val swipeDownAction: GestureAction = GestureAction.NOTIFICATION,
    val swipeLeftAction: GestureAction = GestureAction.BACK,
    val swipeRightAction: GestureAction = GestureAction.LEFT_OPEN_NOS,
    val snapToEdge: Boolean = false,
    val showIcon: Boolean = false,
    val breatheFrequencySeconds: Int = 2,
    val mouseSpeed: String = "一般",
    val mouseRange: Int = 40
)

/**
 * 12 种页面转场动画
 */
@Serializable
enum class PageTransition(val label: String) {
    TRANSLATION("平移"),
    STACK("堆叠"),
    STACK_SCALE("堆叠缩放"),
    SCALE("缩放"),
    PAGE("翻页"),
    TURN_TABLE("转盘"),
    ROTATION("旋转"),
    COMPRESS("压缩"),
    UP("上移"),
    DOWN("下移"),
    OUT_CUBE("外魔方"),
    IN_CUBE("内魔方")
}

/**
 * 主题外观配置模型
 */
@Serializable
data class ThemeConfig(
    val isDarkMode: Boolean = false,
    val followSystemDark: Boolean = false,
    val panelWidthPercent: Int = 70,
    val panelHeightPercent: Int = 100,
    val panelHorizontalOffsetPercent: Int = 0,
    val panelVerticalPositionPercent: Int = 50,
    val edgeMarginPercent: Int = 2,
    val maskColorHex: Long = 0xFF000000,
    val maskAlphaPercent: Int = 20,
    val pageTransition: PageTransition = PageTransition.TRANSLATION,
    val animationDurationMs: Int = 260,
    val coverStatusBar: Boolean = true,
    val backgroundBlurRadius: Int = 25,
    val cardBackground: Boolean = true,
    val cardCornerRadiusDp: Int = 8,
    val cardSpacingDp: Int = 8,
    val panelBgColorHex: Long = 0x00000000,
    val panelBgAlphaPercent: Int = 0,
    val defaultCardBgColorHex: Long = 0xFFFFFFFF,
    val cardBgAlphaPercent: Int = 100,
    val defaultTextColorHex: Long = 0xFF1F2329,
    val textAlphaPercent: Int = 100,
    val titleColorHex: Long? = null,
    val titleAlphaPercent: Int = 100,
    val accentColorHex: Long = 0xFF2196F3,
    val bgImageUri: String = "",
    val bgImageAlpha: Float = 0.85f
)

/**
 * 兼容旧版预设主题
 */
@Serializable
enum class ThemePreset(val label: String, val description: String) {
    DARK_GLASS("深色磨砂", "深色毛玻璃质感"),
    LIGHT_GLASS("浅色磨砂", "浅色毛玻璃卡片"),
    PURE_BLACK("纯黑底色", "深色纯黑背景"),
    CYBER_NEON("深蓝配色", "经典暗夜蓝紫配色")
}

/**
 * 兼容旧版外观配置模型
 */
@Serializable
data class AppearanceConfig(
    val themePreset: ThemePreset = ThemePreset.DARK_GLASS,
    val panelWidthFraction: Float = 0.78f,
    val cardCornerRadiusDp: Int = 12,
    val cardAlpha: Float = 0.85f,
    val cardSpacingDp: Int = 8,
    val backgroundBlurRadius: Int = 25,
    val backgroundDimAlpha: Float = 0.45f,
    val accentColorHex: String = "#2196F3",
    val appGridColumns: Int = 4,
    val showAppLabels: Boolean = true,
    val customMotto: String = "全局负一屏",
    val searchEngineName: String = "Baidu",
    val searchEngineUrl: String = "https://www.baidu.com/s?wd=%s"
)

/**
 * 图标点击行为
 */
@Serializable
enum class IconClickBehavior(val label: String) {
    OPEN_SETTINGS("进入设置界面"),
    OPEN_NOS("直接呼出负一屏")
}

/**
 * 情景模式单项应用配置
 */
@Serializable
data class ScenarioModeItem(
    val packageName: String,
    val appLabel: String,
    val volumePercent: Int = -1,
    val brightnessPercent: Int = -1,
    val enabled: Boolean = true
)

/**
 * 高级设置与扩展功能配置模型
 */
@Serializable
data class AdvancedConfig(
    val notificationPreferences: NotificationPreferencesConfig = NotificationPreferencesConfig(),
    val iconClickBehavior: IconClickBehavior = IconClickBehavior.OPEN_SETTINGS,
    val autoCloseInLandscape: Boolean = true,
    val autoCloseOnWidgetClick: Boolean = true,
    val autoStartOnBoot: Boolean = true,
    val gridColumns: Int = 32,
    val heightUnitDp: Int = 0,
    val layoutLocked: Boolean = false,
    val settingsDarkMode: Boolean = false,
    val settingsFollowSystemTheme: Boolean = false,
    val rootModeEnabled: Boolean = false,
    val autoJumpAccessibility: Boolean = true,
    val highestOverlayLevel: Boolean = false,
    val blacklistPackages: Set<String> = emptySet(),
    val eyeProtectionEnabled: Boolean = false,
    val eyeProtectionColorHex: Long = 0x26FFB100,
    val autoCopyVerificationCode: Boolean = false,
    val scenarioModes: List<ScenarioModeItem> = emptyList()
)

/**
 * 页面布局类型（0: 线性布局，1: 自由布局，2: 网格布局）
 */
@Serializable
enum class PageLayoutType(val code: Int, val label: String) {
    LINEAR(0, "线性布局"),
    FREE(1, "自由布局"),
    GRID(2, "网格布局")
}

/**
 * 组件添加分类
 */
@Serializable
enum class ComponentCategory(val label: String) {
    SWITCH("通知栏开关"),
    SYSTEM_ACTION("操作栏与按键"),
    SHORTCUT("快捷方式"),
    APP("应用"),
    CONTAINER("容器"),
    APP_DRAWER("应用抽屉"),
    RECENT_APPS("最近运行"),
    WIDGET("小部件"),
    SLIDER("亮度与音量条"),
    STATISTICS("电量与使用统计"),
    HITOKOTO("一言"),
    NOTE("便签与文字"),
    IMAGE("图片与魔方"),
    WEBSITE("网站与搜索"),
    FILES("文件浏览")
}

/**
 * 组件类型枚举（涵盖系统与桌面核心组件及布局参数）
 */
@Serializable
enum class ComponentType(
    val cnum: Int,
    val label: String,
    val category: ComponentCategory,
    val defaultW: Int,
    val defaultH: Int
) {
    // 通知栏开关类
    SWITCH_WIFI(18, "WiFi", ComponentCategory.SWITCH, 8, 6),
    SWITCH_BLUETOOTH(3, "蓝牙", ComponentCategory.SWITCH, 8, 6),
    SWITCH_TORCH(15, "手电筒", ComponentCategory.SWITCH, 8, 6),
    SWITCH_RINGER(17, "声音模式", ComponentCategory.SWITCH, 8, 6),
    SWITCH_MUTE(14, "静音", ComponentCategory.SWITCH, 8, 6),
    SWITCH_VIBRATE(11, "震动", ComponentCategory.SWITCH, 8, 6),
    SWITCH_AUTO_BRIGHTNESS(2, "自动亮度", ComponentCategory.SWITCH, 8, 6),
    SWITCH_AUTO_ROTATE(4, "自动旋转", ComponentCategory.SWITCH, 8, 6),
    SWITCH_KEEP_AWAKE(5, "屏幕常亮", ComponentCategory.SWITCH, 8, 6),
    SWITCH_LOCATION(7, "位置信息", ComponentCategory.SWITCH, 8, 6),
    SWITCH_HOTSPOT(8, "便携热点", ComponentCategory.SWITCH, 8, 6),
    SWITCH_NFC(13, "NFC", ComponentCategory.SWITCH, 8, 6),
    SWITCH_AIRPLANE(1, "飞行模式", ComponentCategory.SWITCH, 8, 6),
    SWITCH_EYE_CARE(6, "全局护眼", ComponentCategory.SWITCH, 8, 6),

    // 系统操作栏与按键类
    ACTION_LOCK(19, "锁屏", ComponentCategory.SYSTEM_ACTION, 8, 6),
    ACTION_HOME(20, "桌面", ComponentCategory.SYSTEM_ACTION, 8, 6),
    ACTION_BACK(21, "返回", ComponentCategory.SYSTEM_ACTION, 8, 6),
    ACTION_RECENTS(22, "最近任务", ComponentCategory.SYSTEM_ACTION, 8, 6),
    ACTION_NOTIFICATIONS(23, "通知栏", ComponentCategory.SYSTEM_ACTION, 8, 6),
    ACTION_SPLIT_SCREEN(27, "分屏", ComponentCategory.SYSTEM_ACTION, 8, 6),
    ACTION_SCREENSHOT(28, "截屏", ComponentCategory.SYSTEM_ACTION, 8, 6),
    ACTION_VOICE_ASSIST(30, "语音助手", ComponentCategory.SYSTEM_ACTION, 8, 6),
    ACTION_WECHAT_SCAN(31, "微信扫一扫", ComponentCategory.SYSTEM_ACTION, 8, 6),
    OPERATION_BAR(60, "操作栏", ComponentCategory.SYSTEM_ACTION, 32, 5),

    // 快捷方式与直达类
    CUSTOM_SHORTCUT(25, "快捷方式", ComponentCategory.SHORTCUT, 8, 6),
    SHELL_COMMAND(24, "Shell命令", ComponentCategory.SHORTCUT, 8, 6),
    STATIC_SHORTCUT(29, "静态快捷方式", ComponentCategory.SHORTCUT, 8, 6),

    // 应用与容器类
    APP_ICON(42, "应用", ComponentCategory.APP, 8, 7),
    CONTAINER_BOX(48, "容器", ComponentCategory.CONTAINER, 32, 12),
    FOLDER(64, "文件夹", ComponentCategory.CONTAINER, 8, 8),
    APP_DRAWER_CARD(43, "应用抽屉", ComponentCategory.APP_DRAWER, 32, 40),
    RECENT_APPS_CARD(54, "最近运行", ComponentCategory.RECENT_APPS, 32, 8),

    // 桌面小部件
    APP_WIDGET(44, "小部件", ComponentCategory.WIDGET, 32, 14),

    // 调节滑条类
    BRIGHTNESS_BAR(46, "亮度条", ComponentCategory.SLIDER, 32, 5),
    VOLUME_BAR(58, "音量条", ComponentCategory.SLIDER, 32, 5),

    // 统计与硬件状态卡片类
    STAT_BATTERY_BAR(34, "剩余电量", ComponentCategory.STATISTICS, 10, 6),
    STAT_BATTERY_DETAIL(45, "电量详情", ComponentCategory.STATISTICS, 32, 9),
    STAT_RAM(36, "内存占用", ComponentCategory.STATISTICS, 11, 6),
    STAT_ROM(37, "存储占用", ComponentCategory.STATISTICS, 11, 6),
    STAT_UPTIME(33, "开机时间", ComponentCategory.STATISTICS, 16, 6),
    STAT_SCREEN_TIME(38, "使用统计", ComponentCategory.STATISTICS, 16, 6),
    STAT_APP_COUNT(32, "可启动应用", ComponentCategory.STATISTICS, 16, 6),
    STAT_NOTIF_COUNT(35, "通知统计", ComponentCategory.STATISTICS, 16, 6),
    STAT_DATA_TRAFFIC(39, "本次开机流量", ComponentCategory.STATISTICS, 16, 6),
    STAT_UNLOCK_COUNT(40, "今日解锁", ComponentCategory.STATISTICS, 16, 6),

    // 文本、一言、便签、图片、网站
    HITOKOTO_CARD(53, "一言", ComponentCategory.HITOKOTO, 32, 7),
    NOTE_CARD(52, "便签", ComponentCategory.NOTE, 32, 12),
    TEXT_LABEL(57, "文字", ComponentCategory.NOTE, 32, 5),
    CLIPBOARD_CARD(61, "剪贴板", ComponentCategory.NOTE, 32, 11),
    SINGLE_IMAGE(41, "图片", ComponentCategory.IMAGE, 16, 12),
    PHOTO_CUBE(50, "图片魔方", ComponentCategory.IMAGE, 16, 12),
    WEBSITE_LINK(59, "网站与搜索", ComponentCategory.WEBSITE, 32, 6),
    FILE_BROWSER(62, "文件浏览", ComponentCategory.FILES, 32, 8),
    FILE_SHORTCUT(63, "文件", ComponentCategory.FILES, 32, 7);

    companion object {
        fun fromCnum(cnum: Int): ComponentType? = values().firstOrNull { it.cnum == cnum }
    }
}

fun ComponentType.isContainerType(): Boolean = this == ComponentType.CONTAINER_BOX || this == ComponentType.FOLDER

@Serializable
enum class ContainerLayoutType { FREE, GRID }

@Serializable
enum class AppLaunchMode { NORMAL, FLOATING }

/**
 * 快捷方式项（通用实体）
 */
@Serializable
data class QuickActionItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String = "",
    val iconName: String = "bolt",
    val colorHex: String = "#2196F3",
    val intentUri: String,
    val packageName: String = "",
    // Optional folder-local dp geometry. Null preserves the original grid fallback.
    val xDp: Int? = null,
    val yDp: Int? = null,
    val wDp: Int? = null,
    val hDp: Int? = null,
    // 2D cell coordinates inside GRID containers (-1 uses sequential fallback).
    val gridCol: Int = -1,
    val gridRow: Int = -1
)

/**
 * 自由布局与线性布局通用组件实体
 */
@Serializable
data class ComponentItem(
    val id: String = UUID.randomUUID().toString(),
    val type: ComponentType,
    val title: String = type.label,
    val subtitle: String = "",
    val showTitle: Boolean = false,

    // 自由布局网格坐标与跨度（默认 32 列网格）
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = type.defaultW,
    val h: Int = type.defaultH,

    // 组件样式
    val bgColorHex: Long? = null,
    val bgAlphaPercent: Int = -1,
    val textColorHex: Long? = null,
    val textAlphaPercent: Int = -1,
    val cornerRadiusDp: Int = -1,
    val rotationDeg: Int = 0,
    val paddingTopDp: Int = 6,
    val paddingBottomDp: Int = 6,
    val paddingLeftDp: Int = 8,
    val paddingRightDp: Int = 8,
    val marginTopDp: Int = 2,
    val marginBottomDp: Int = 2,
    val marginLeftDp: Int = 2,
    val marginRightDp: Int = 2,

    // 组件专用数据字段
    val packageName: String = "",
    val appLaunchMode: AppLaunchMode = AppLaunchMode.NORMAL,
    val orientation: ComponentOrientation = ComponentOrientation.AUTO,
    val intentUri: String = "",
    val iconName: String = "",
    val iconUri: String = "",
    val colorHex: Long = 0xFF2196F3,
    val textContent: String = "",
    val imageUri: String = "",
    val photoCubeUris: List<String> = emptyList(),
    val photoCubeIntervalSeconds: Int = 5,
    val widgetId: Int = -1,
    val containerActions: List<QuickActionItem> = emptyList(),
    // Real child cards. Legacy actions remain readable without a destructive migration.
    val containerComponents: List<ComponentItem> = emptyList(),
    val containerColumns: Int = 4,
    val containerCompact: Boolean = false,
    val containerLayout: ContainerLayoutType = if (type == ComponentType.FOLDER) ContainerLayoutType.GRID else ContainerLayoutType.FREE,
    // Zero preserves automatic sizing for configurations saved before these options existed.
    val containerPopupWidthDp: Int = 0,
    val containerPopupHeightDp: Int = 0,
    val containerItemSizeDp: Int = 0,
    val containerItemGapDp: Int = 0,
    // ROW_GAP_INHERIT (-999) inherits horizontal gap; -24..48 sets explicit vertical row gap in dp.
    val containerRowGapDp: Int = ROW_GAP_INHERIT,
    val containerItemPaddingVerticalDp: Int = 0,
    val containerIconLabelGapDp: Int = 2,
    // 网格布局占格跨度（0 表示按组件类型默认跨度，99 表示占满当前整行）
    val gridSpanX: Int = 0,
    val gridSpanY: Int = 0,
    // 网格布局显式 2D 单元格坐标（-1 表示自动顺位流式排列，>= 0 表示自由摆放到指定列/行）
    val gridCol: Int = -1,
    val gridRow: Int = -1,
    val autoHeight: Boolean = type in setOf(ComponentType.CONTAINER_BOX, ComponentType.OPERATION_BAR,
        ComponentType.APP_ICON, ComponentType.CUSTOM_SHORTCUT, ComponentType.STATIC_SHORTCUT),
    // Actual rendered icon dp. Zero preserves legacy cell-size-derived appearance.
    val containerIconSizeDp: Int = 0
)

/**
 * 屏幕单页配置
 * 网格可自动推导列数，也可固定列数；新字段零值兼容旧配置。
 */
@Serializable
data class PageConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val layoutType: PageLayoutType = PageLayoutType.FREE,
    val bgColorHex: Long? = null,
    val bgImageUri: String = "",
    val bgImageAlpha: Float = 0.85f,
    val gridItemSizeDp: Int = 58,
    val gridItemGapDp: Int = 8,
    // ROW_GAP_INHERIT (-999) preserves effective horizontal gap; -24..48 sets explicit row gap in dp.
    val gridRowGapDp: Int = ROW_GAP_INHERIT,
    val components: List<ComponentItem> = emptyList(),
    val gridColumnCount: Int = 0,
    val gridIconSizeDp: Int = 0,
    // Panels reading order must not be changed by backfilling holes ahead of earlier cards.
    val gridFillGaps: Boolean = true
)

/**
 * 配置文件方案元数据
 */
@Serializable
data class ConfigProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val author: String = "默认",
    val description: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val stateJson: String = ""
)

/**
 * 待办事项
 */
@Serializable
data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 剪贴板记录
 */
@Serializable
data class ClipboardRecord(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val pinned: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 组件方向样式：自动随宽高适配，或固定横排 / 竖排
 */
@Serializable
enum class ComponentOrientation(val label: String) {
    AUTO("自动"),
    HORIZONTAL("横排"),
    VERTICAL("竖排")
}

/**
 * 绑定的小部件信息
 */
@Serializable
data class BoundWidgetInfo(
    val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val label: String,
    val heightDp: Int = 180
)

/**
 * 兼容旧版的卡片类型
 */
@Serializable
enum class CardType(val defaultTitle: String, val description: String) {
    HEADER_GLANCE("时间与搜索", "顶部时间、日期、一言与聚合搜索框"),
    QUICK_ACTIONS("快捷方式", "微信、支付宝等快捷方式与扫码"),
    SYSTEM_CONTROL("控制中心", "音量、屏幕亮度、手电筒、锁屏等快捷开关"),
    APP_GRID("常用应用", "常用应用网格"),
    TODO_NOTES("便签与待办", "记录便签与待办事项"),
    CLIPBOARD("剪贴板", "记录复制历史与常用短语"),
    SYSTEM_MONITOR("系统状态", "运行内存、存储空间与电池信息"),
    APP_WIDGET("桌面小部件", "嵌入桌面小部件"),
    CUSTOM_IMAGE("图片", "添加自定义图片")
}

/**
 * 兼容旧版的卡片配置
 */
@Serializable
data class CardConfig(
    val id: String = UUID.randomUUID().toString(),
    val type: CardType,
    val title: String = type.defaultTitle,
    val enabled: Boolean = true,
    val collapsed: Boolean = false,
    val order: Int = 0,
    val widgetId: Int? = null,
    val customImageUri: String? = null,
    val customImageHeightDp: Int = 200
)

/**
 * 全局状态模型
 */
@Serializable
data class AppAppearanceOverride(val displayName: String = "", val iconUri: String = "")

@Serializable
data class GlobalMinusOneState(
    val schemaVersion: Int = CURRENT_STATE_SCHEMA_VERSION,
    val triggerConfig: TriggerConfig = TriggerConfig(),
    val suspensionBallConfig: SuspensionBallConfig = SuspensionBallConfig(),
    val themeConfig: ThemeConfig = ThemeConfig(),
    val advancedConfig: AdvancedConfig = AdvancedConfig(),
    val pages: List<PageConfig> = DefaultPresets.defaultPages(),
    val currentPageIndex: Int = 0,
    val editMode: Boolean = false,
    val selectedComponentId: String? = null,
    val profiles: List<ConfigProfile> = DefaultPresets.defaultProfiles(),
    val activeProfileId: String = "default",
    val favoriteApps: List<String> = emptyList(),
    val appAppearanceOverrides: Map<String, AppAppearanceOverride> = emptyMap(),
    val todos: List<TodoItem> = DefaultPresets.defaultTodos(),
    val clipboardHistory: List<ClipboardRecord> = DefaultPresets.defaultClipboard(),
    val fileBrowserConfig: FileBrowserConfig = FileBrowserConfig(),
    val boundWidgets: List<BoundWidgetInfo> = emptyList(),
    val noteText: String = "在任意界面从屏幕边缘向内滑动即可呼出负一屏。",

    // 兼容旧版视图字段
    val appearanceConfig: AppearanceConfig = AppearanceConfig(),
    val cards: List<CardConfig> = DefaultPresets.defaultCards(),
    val quickActions: List<QuickActionItem> = DefaultPresets.defaultQuickActions()
)

/**
 * 默认预设数据仓库
 */
object DefaultPresets {

    fun defaultPages(): List<PageConfig> = listOf(
        // 第 1 页：默认线性布局页
        PageConfig(
            id = "page_linear_1",
            name = "线性布局",
            layoutType = PageLayoutType.LINEAR,
            components = listOf(
                // 1.「应用」容器卡片 (4 列白底，右上角带 ⋮ 菜单)
                ComponentItem(
                    id = "p1_container_app",
                    type = ComponentType.CONTAINER_BOX,
                    title = "应用",
                    showTitle = true,
                    containerColumns = 4,
                    containerActions = listOf(
                        QuickActionItem(
                            id = "ca_qq",
                            title = "QQ",
                            subtitle = "腾讯QQ",
                            iconName = "qq",
                            colorHex = "#212121",
                            intentUri = "action:android.intent.action.MAIN",
                            packageName = "com.tencent.mobileqq"
                        ),
                        QuickActionItem(
                            id = "ca_wechat",
                            title = "微信",
                            subtitle = "微信",
                            iconName = "wechat",
                            colorHex = "#07C160",
                            intentUri = "action:android.intent.action.MAIN",
                            packageName = "com.tencent.mm"
                        ),
                        QuickActionItem(
                            id = "ca_coolapk",
                            title = "酷安",
                            subtitle = "应用社区",
                            iconName = "coolapk",
                            colorHex = "#11B95C",
                            intentUri = "coolmarket://",
                            packageName = "com.coolapk.market"
                        ),
                        QuickActionItem(
                            id = "ca_gmo",
                            title = "全局负一屏",
                            subtitle = "设置",
                            iconName = "globalminusone",
                            colorHex = "#2196F3",
                            intentUri = "action:com.example.globalminusone.MAIN",
                            packageName = "com.example.globalminusone"
                        )
                    )
                ),
                // 2.「快捷方式」容器卡片 (4 列白底，右上角带 ⋮ 菜单)
                ComponentItem(
                    id = "p1_container_shortcuts",
                    type = ComponentType.CONTAINER_BOX,
                    title = "快捷方式",
                    showTitle = true,
                    containerColumns = 4,
                    containerActions = listOf(
                        QuickActionItem(
                            id = "ca_crop",
                            title = "区域截屏",
                            subtitle = "截屏",
                            iconName = "crop",
                            colorHex = "#2196F3",
                            intentUri = "action:screenshot_region"
                        ),
                        QuickActionItem(
                            id = "ca_eye",
                            title = "全局护眼",
                            subtitle = "护眼滤镜",
                            iconName = "eye",
                            colorHex = "#9E9E9E",
                            intentUri = "action:eye_protection"
                        ),
                        QuickActionItem(
                            id = "ca_play_pause",
                            title = "播放/暂停",
                            subtitle = "媒体控制",
                            iconName = "play_pause",
                            colorHex = "#FBC02D",
                            intentUri = "action:media_play_pause"
                        ),
                        QuickActionItem(
                            id = "ca_voice",
                            title = "语音助手",
                            subtitle = "系统语音",
                            iconName = "mic",
                            colorHex = "#43A047",
                            intentUri = "action:voice_assist"
                        )
                    )
                ),
                // 3.「支付」容器卡片 (4 列白底，右上角带 ⋮ 菜单)
                ComponentItem(
                    id = "p1_container_pay",
                    type = ComponentType.CONTAINER_BOX,
                    title = "支付",
                    showTitle = true,
                    containerColumns = 4,
                    containerActions = listOf(
                        QuickActionItem(
                            id = "ca_alipay_scan",
                            title = "扫一扫",
                            subtitle = "支付宝扫一扫",
                            iconName = "alipay_scan",
                            colorHex = "#1677FF",
                            intentUri = "alipays://platformapi/startapp?saId=10000007",
                            packageName = "com.eg.android.AlipayGphone"
                        ),
                        QuickActionItem(
                            id = "ca_alipay_code",
                            title = "付款码",
                            subtitle = "支付宝付款码",
                            iconName = "alipay_code",
                            colorHex = "#1677FF",
                            intentUri = "alipays://platformapi/startapp?saId=20000056",
                            packageName = "com.eg.android.AlipayGphone"
                        ),
                        QuickActionItem(
                            id = "ca_wechat_scan",
                            title = "扫一扫",
                            subtitle = "微信扫一扫",
                            iconName = "wechat_scan",
                            colorHex = "#07C160",
                            intentUri = "weixin://dl/scan",
                            packageName = "com.tencent.mm"
                        ),
                        QuickActionItem(
                            id = "ca_wechat_code",
                            title = "付款码",
                            subtitle = "微信付款码",
                            iconName = "wechat_code",
                            colorHex = "#07C160",
                            intentUri = "am start -n com.tencent.mm/com.tencent.mm.plugin.offline.ui.WalletOfflineCoinPurseUI",
                            packageName = "com.tencent.mm"
                        )
                    )
                ),
                // 4.「网站」容器卡片 (4 列白底，右上角带 ⋮ 菜单)
                ComponentItem(
                    id = "p1_container_website",
                    type = ComponentType.CONTAINER_BOX,
                    title = "网站",
                    showTitle = true,
                    containerColumns = 4,
                    containerActions = listOf(
                        QuickActionItem(
                            id = "ca_baidu",
                            title = "百度",
                            subtitle = "百度搜索",
                            iconName = "link",
                            colorHex = "#2196F3",
                            intentUri = "https://www.baidu.com"
                        ),
                        QuickActionItem(
                            id = "ca_weibo",
                            title = "微博",
                            subtitle = "新浪微博",
                            iconName = "link",
                            colorHex = "#2196F3",
                            intentUri = "https://www.weibo.com"
                        ),
                        QuickActionItem(
                            id = "ca_jd",
                            title = "京东",
                            subtitle = "京东商城",
                            iconName = "link",
                            colorHex = "#2196F3",
                            intentUri = "https://www.jd.com"
                        ),
                        QuickActionItem(
                            id = "ca_taobao",
                            title = "淘宝",
                            subtitle = "淘宝网",
                            iconName = "link",
                            colorHex = "#2196F3",
                            intentUri = "https://www.taobao.com"
                        )
                    )
                ),
                // 5. 底部「操作栏」卡片 (无标题，高度 46dp，中央浅灰圆形 + 按钮)
                ComponentItem(
                    id = "p1_op_bar",
                    type = ComponentType.OPERATION_BAR,
                    title = "操作栏",
                    showTitle = false,
                    w = 32,
                    h = 4
                )
            )
        ),
        // 第 2 页：真正的 32 等分二维「自由布局」页（32×48 二维不对称网格画布）
        PageConfig(
            id = "page_free_2",
            name = "自由布局",
            layoutType = PageLayoutType.FREE,
            components = listOf(
                // 左上角 x=1, y=2, w=17, h=8：一言卡片（白底圆角）
                ComponentItem(
                    id = "p2_hitokoto",
                    type = ComponentType.HITOKOTO_CARD,
                    title = "一言",
                    x = 1,
                    y = 2,
                    w = 17,
                    h = 8
                ),
                // 右上角 x=19, y=2, w=12, h=8：剩余电量卡片（大字百分比）
                ComponentItem(
                    id = "p2_battery",
                    type = ComponentType.STAT_BATTERY_BAR,
                    title = "剩余电量",
                    x = 19,
                    y = 2,
                    w = 12,
                    h = 8
                ),
                // 中左 2×2 独立开关矩阵：
                // x=1, y=11, w=7, h=6：WiFi 开关
                ComponentItem(
                    id = "p2_wifi",
                    type = ComponentType.SWITCH_WIFI,
                    title = "WiFi",
                    x = 1,
                    y = 11,
                    w = 7,
                    h = 6
                ),
                // x=9, y=11, w=7, h=6：蓝牙开关
                ComponentItem(
                    id = "p2_bluetooth",
                    type = ComponentType.SWITCH_BLUETOOTH,
                    title = "蓝牙",
                    x = 9,
                    y = 11,
                    w = 7,
                    h = 6
                ),
                // x=1, y=18, w=7, h=6：手电筒开关
                ComponentItem(
                    id = "p2_torch",
                    type = ComponentType.SWITCH_TORCH,
                    title = "手电筒",
                    x = 1,
                    y = 18,
                    w = 7,
                    h = 6
                ),
                // x=9, y=18, w=7, h=6：锁屏按键
                ComponentItem(
                    id = "p2_lock",
                    type = ComponentType.ACTION_LOCK,
                    title = "锁屏",
                    x = 9,
                    y = 18,
                    w = 7,
                    h = 6
                ),
                // 中右大块便签卡片：x=17, y=11, w=14, h=13：便签卡片（跨两行高度，与左侧 4 个小开关左右并排！）
                ComponentItem(
                    id = "p2_note",
                    type = ComponentType.NOTE_CARD,
                    title = "便签",
                    textContent = "随手记便签，记录当下灵感。",
                    x = 17,
                    y = 11,
                    w = 14,
                    h = 13
                ),
                // 下方并排统计卡：x=1, y=25, w=15, h=6（剩余RAM）与 x=17, y=25, w=14, h=6（剩余ROM）
                ComponentItem(
                    id = "p2_ram",
                    type = ComponentType.STAT_RAM,
                    title = "剩余RAM",
                    x = 1,
                    y = 25,
                    w = 15,
                    h = 6
                ),
                ComponentItem(
                    id = "p2_rom",
                    type = ComponentType.STAT_ROM,
                    title = "剩余ROM",
                    x = 17,
                    y = 25,
                    w = 14,
                    h = 6
                ),
                // 亮度条与音量条：x=1, y=32, w=30, h=5（亮度条）、x=1, y=38, w=30, h=5（音量条）
                ComponentItem(
                    id = "p2_brightness",
                    type = ComponentType.BRIGHTNESS_BAR,
                    title = "亮度条",
                    x = 1,
                    y = 32,
                    w = 30,
                    h = 5
                ),
                ComponentItem(
                    id = "p2_volume",
                    type = ComponentType.VOLUME_BAR,
                    title = "音量条",
                    x = 1,
                    y = 38,
                    w = 30,
                    h = 5
                ),
                // 底部操作栏卡片：x=1, y=44, w=30, h=4（+ 操作栏卡片）
                ComponentItem(
                    id = "p2_op_bar",
                    type = ComponentType.OPERATION_BAR,
                    title = "操作栏",
                    showTitle = false,
                    x = 1,
                    y = 44,
                    w = 30,
                    h = 4
                )
            )
        )
    )

    fun defaultProfiles(): List<ConfigProfile> = listOf(
        ConfigProfile(
            id = "default",
            name = "默认",
            author = "全局负一屏",
            description = "全局负一屏默认双页配置"
        )
    )

    /**
     * 内置快捷方式库
     */
    val builtInShortcuts: List<QuickActionItem> = listOf(
        // 支付宝
        QuickActionItem(
            id = "sc_alipay_scan",
            title = "支付宝扫一扫",
            subtitle = "扫码直达",
            iconName = "qr_code_scanner",
            colorHex = "#1677FF",
            intentUri = "alipays://platformapi/startapp?saId=10000007",
            packageName = "com.eg.android.AlipayGphone"
        ),
        QuickActionItem(
            id = "sc_alipay_pay",
            title = "支付宝付款码",
            subtitle = "出示付款",
            iconName = "payments",
            colorHex = "#00A6FF",
            intentUri = "alipays://platformapi/startapp?saId=20000056",
            packageName = "com.eg.android.AlipayGphone"
        ),
        QuickActionItem(
            id = "sc_alipay_collect",
            title = "支付宝收款码",
            subtitle = "收钱直达",
            iconName = "payments",
            colorHex = "#0288D1",
            intentUri = "alipays://platformapi/startapp?saId=20000123",
            packageName = "com.eg.android.AlipayGphone"
        ),
        QuickActionItem(
            id = "sc_alipay_bus",
            title = "支付宝乘车码",
            subtitle = "公交地铁",
            iconName = "directions_bus",
            colorHex = "#00B578",
            intentUri = "alipays://platformapi/startapp?saId=200011235",
            packageName = "com.eg.android.AlipayGphone"
        ),
        QuickActionItem(
            id = "sc_alipay_transfer",
            title = "支付宝转账",
            subtitle = "转账直达",
            iconName = "bolt",
            colorHex = "#1E88E5",
            intentUri = "alipays://platformapi/startapp?saId=20000221",
            packageName = "com.eg.android.AlipayGphone"
        ),
        QuickActionItem(
            id = "sc_alipay_forest",
            title = "蚂蚁森林",
            subtitle = "收能量",
            iconName = "explore",
            colorHex = "#4CAF50",
            intentUri = "alipays://platformapi/startapp?saId=60000002",
            packageName = "com.eg.android.AlipayGphone"
        ),
        QuickActionItem(
            id = "sc_alipay_express",
            title = "查快递",
            subtitle = "物流查询",
            iconName = "storage",
            colorHex = "#FB8C00",
            intentUri = "alipays://platformapi/startapp?saId=20000754",
            packageName = "com.eg.android.AlipayGphone"
        ),
        QuickActionItem(
            id = "sc_alipay_card_pkg",
            title = "支付宝卡包",
            subtitle = "卡券票据",
            iconName = "dashboard",
            colorHex = "#5E35B1",
            intentUri = "alipays://platformapi/startapp?saId=20000227",
            packageName = "com.eg.android.AlipayGphone"
        ),
        QuickActionItem(
            id = "sc_alipay_huabei",
            title = "花呗",
            subtitle = "花呗账单",
            iconName = "payments",
            colorHex = "#1565C0",
            intentUri = "alipays://platformapi/startapp?saId=77700130",
            packageName = "com.eg.android.AlipayGphone"
        ),
        QuickActionItem(
            id = "sc_alipay_yu_e_bao",
            title = "余额宝",
            subtitle = "理财收益",
            iconName = "payments",
            colorHex = "#E53935",
            intentUri = "alipays://platformapi/startapp?saId=20000032",
            packageName = "com.eg.android.AlipayGphone"
        ),

        // 微信
        QuickActionItem(
            id = "sc_wechat_scan",
            title = "微信扫一扫",
            subtitle = "扫码直达",
            iconName = "center_focus_strong",
            colorHex = "#07C160",
            intentUri = "weixin://dl/scan",
            packageName = "com.tencent.mm"
        ),
        QuickActionItem(
            id = "sc_wechat_open",
            title = "微信",
            subtitle = "消息沟通",
            iconName = "chat",
            colorHex = "#10B981",
            intentUri = "weixin://",
            packageName = "com.tencent.mm"
        ),

        // 微博
        QuickActionItem(
            id = "sc_weibo_hot",
            title = "微博热搜",
            subtitle = "热门话题",
            iconName = "search",
            colorHex = "#E6162D",
            intentUri = "sinaweibo://searchall",
            packageName = "com.sina.weibo"
        ),
        QuickActionItem(
            id = "sc_weibo_home",
            title = "微博首页",
            subtitle = "关注动态",
            iconName = "home",
            colorHex = "#FF8200",
            intentUri = "sinaweibo://gotohome",
            packageName = "com.sina.weibo"
        ),

        // 酷安
        QuickActionItem(
            id = "sc_coolapk_home",
            title = "酷安",
            subtitle = "应用社区",
            iconName = "explore",
            colorHex = "#11A668",
            intentUri = "coolmarket://",
            packageName = "com.coolapk.market"
        ),
        QuickActionItem(
            id = "sc_coolapk_search",
            title = "酷安搜索",
            subtitle = "搜索应用与讨论",
            iconName = "search",
            colorHex = "#009688",
            intentUri = "coolmarket://search?q=",
            packageName = "com.coolapk.market"
        ),

        // 网易云音乐
        QuickActionItem(
            id = "sc_netease_daily",
            title = "每日推荐",
            subtitle = "今日推荐歌曲",
            iconName = "play_arrow",
            colorHex = "#C20C0C",
            intentUri = "#Intent;action=android.intent.action.MAIN;package=com.netease.cloudmusic;component=com.netease.cloudmusic/.activity.DailyRcmdMusicActivity;end",
            packageName = "com.netease.cloudmusic"
        ),
        QuickActionItem(
            id = "sc_netease_recognize",
            title = "听歌识曲",
            subtitle = "识别背景音乐",
            iconName = "mic",
            colorHex = "#D32F2F",
            intentUri = "#Intent;action=android.intent.action.MAIN;package=com.netease.cloudmusic;component=com.netease.cloudmusic/.activity.IdentifyActivity;end",
            packageName = "com.netease.cloudmusic"
        ),

        // 淘宝
        QuickActionItem(
            id = "sc_taobao_search",
            title = "淘宝搜索",
            subtitle = "商品搜索",
            iconName = "search",
            colorHex = "#FF5000",
            intentUri = "taobao://s.taobao.com/search?",
            packageName = "com.taobao.taobao"
        ),
        QuickActionItem(
            id = "sc_taobao_cart",
            title = "淘宝购物车",
            subtitle = "购物车列表",
            iconName = "dashboard",
            colorHex = "#FF6F00",
            intentUri = "#Intent;component=com.taobao.taobao/com.taobao.android.trade.cart.CartActivity;end",
            packageName = "com.taobao.taobao"
        ),

        // 钉钉
        QuickActionItem(
            id = "sc_dingtalk_checkin",
            title = "钉钉打卡",
            subtitle = "考勤打卡",
            iconName = "check_circle",
            colorHex = "#0089FF",
            intentUri = "dingtalk://dingtalkclient/page/link?url=https://attend.dingtalk.com/attend/index.html",
            packageName = "com.alibaba.android.rimet"
        ),

        // 百度网盘
        QuickActionItem(
            id = "sc_baidupan_scan",
            title = "百度网盘扫一扫",
            subtitle = "文件扫码",
            iconName = "qr_code_scanner",
            colorHex = "#06A7FF",
            intentUri = "#Intent;component=com.baidu.netdisk/com.baidu.graph.sdk.GraphActivity;end",
            packageName = "com.baidu.netdisk"
        ),

        // 系统工具
        QuickActionItem(
            id = "sc_system_settings",
            title = "系统设置",
            subtitle = "设备设置",
            iconName = "settings",
            colorHex = "#607D8B",
            intentUri = "action:android.settings.SETTINGS"
        ),
        QuickActionItem(
            id = "sc_system_calculator",
            title = "计算器",
            subtitle = "速算工具",
            iconName = "calculate",
            colorHex = "#FF9800",
            intentUri = "category:android.intent.category.APP_CALCULATOR"
        ),
        QuickActionItem(
            id = "sc_system_clock",
            title = "时钟与闹钟",
            subtitle = "闹钟提醒",
            iconName = "access_time",
            colorHex = "#3F51B5",
            intentUri = "action:android.intent.action.SHOW_ALARMS"
        )
    )

    fun defaultCards(): List<CardConfig> = listOf(
        CardConfig(id = "card_header", type = CardType.HEADER_GLANCE, order = 0, enabled = true),
        CardConfig(id = "card_quick_actions", type = CardType.QUICK_ACTIONS, order = 1, enabled = true),
        CardConfig(id = "card_system_control", type = CardType.SYSTEM_CONTROL, order = 2, enabled = true),
        CardConfig(id = "card_app_grid", type = CardType.APP_GRID, order = 3, enabled = true),
        CardConfig(id = "card_todo_notes", type = CardType.TODO_NOTES, order = 4, enabled = true),
        CardConfig(id = "card_clipboard", type = CardType.CLIPBOARD, order = 5, enabled = true),
        CardConfig(id = "card_system_monitor", type = CardType.SYSTEM_MONITOR, order = 6, enabled = true),
        CardConfig(id = "card_custom_image", type = CardType.CUSTOM_IMAGE, order = 7, enabled = false)
    )

    fun defaultQuickActions(): List<QuickActionItem> = builtInShortcuts.take(8)

    fun defaultTodos(): List<TodoItem> = listOf(
        TodoItem(id = "todo_1", content = "长按组件可进入编辑属性并自由拖动尺寸与位置", done = false),
        TodoItem(id = "todo_2", content = "在底部操作栏可随时新建一页或切换自由布局与线性布局", done = false),
        TodoItem(id = "todo_3", content = "支持从屏幕边缘直接滑动或通过悬浮球呼出负一屏", done = true)
    )

    fun defaultClipboard(): List<ClipboardRecord> = listOf(
        ClipboardRecord(
            id = "clip_1",
            text = "全局负一屏",
            pinned = true
        ),
        ClipboardRecord(
            id = "clip_2",
            text = "专注效率，轻盈触达",
            pinned = false
        )
    )

    val builtInMottos = listOf(
        "专注效率，轻盈触达",
        "全局负一屏，随手触达",
        "保持简单，保持高效",
        "记录当下，专注眼前"
    )

    val searchEngines = listOf(
        "Baidu" to "https://www.baidu.com/s?wd=%s",
        "Bing" to "https://www.bing.com/search?q=%s",
        "Google" to "https://www.google.com/search?q=%s",
        "Bilibili" to "https://search.bilibili.com/all?keyword=%s",
        "Coolapk" to "https://www.coolapk.com/search?q=%s"
    )
}

private fun ComponentItem.migrateLegacyRowGaps(): ComponentItem = copy(
    containerRowGapDp = if (containerRowGapDp == -1) ROW_GAP_INHERIT else containerRowGapDp,
    containerComponents = containerComponents.map { it.migrateLegacyRowGaps() }
)

/** Upgrades pre-v2 JSON states where `-1` meant inherit row gap to `ROW_GAP_INHERIT`. */
fun GlobalMinusOneState.migrateLegacyRowGapSentinels(): GlobalMinusOneState = copy(
    schemaVersion = CURRENT_STATE_SCHEMA_VERSION,
    pages = pages.map { page ->
        page.copy(
            gridRowGapDp = if (page.gridRowGapDp == -1) ROW_GAP_INHERIT else page.gridRowGapDp,
            components = page.components.map { it.migrateLegacyRowGaps() }
        )
    }
)

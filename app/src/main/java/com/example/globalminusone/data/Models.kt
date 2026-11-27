package com.example.globalminusone.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.UUID
import com.example.globalminusone.data.policy.DefaultPresets
import com.example.globalminusone.data.operations.SubPanelConfig
import com.example.globalminusone.data.operations.SubPanelTemplate
import com.example.globalminusone.data.policy.containerIconSizeDp
import com.example.globalminusone.data.policy.containerPopupHeightDp
import com.example.globalminusone.data.policy.containerPopupWidthDp
import com.example.globalminusone.data.policy.containerRowGapDp
import com.example.globalminusone.data.policy.PanelOpenPageMode

/** Sentinel meaning row gap inherits the horizontal item gap. */
const val ROW_GAP_INHERIT = -999
const val CURRENT_STATE_SCHEMA_VERSION = 2
const val MIN_GRID_GAP_DP = -24
const val MAX_GRID_GAP_DP = 48
const val MIN_CARD_SPACING_DP = -24
const val MAX_CARD_SPACING_DP = 32
const val MIN_ICON_LABEL_GAP_DP = -16
const val MAX_ICON_LABEL_GAP_DP = 48

@Immutable
@Serializable
data class NotificationPreferencesConfig(
    val reminderEnabled: Boolean = false,
    val vibrate: Boolean = false,
    val autoJumpEnabled: Boolean = false,
    val allowedPackages: Set<String> = emptySet()
)

/** 触发方式 */
@Serializable
enum class TriggerWay(val label: String) {
    SLIDE("直接滑动"),
    SECOND_SLIDE("二次滑动"),
    SIDE_PULLDOWN("侧边下滑"),
    SUSPENSION_BALL("悬浮球"),
    NONE("无")
}

/** 触发位置 */
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
    SCREENSHOT("截屏"),
    REGION_SCREENSHOT("区域截屏"),
    SCREEN_ASSISTANT("全局识屏与翻译"),
    BREENO_TOUCH("小布识屏(ColorOS)")
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
@Immutable
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
@Immutable
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

/** 主题外观配置模型 */
@Immutable
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
    val moduleAnimations: ModuleAnimationConfig = ModuleAnimationConfig(),
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
@Immutable
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
@Immutable
@Serializable
data class ScenarioModeItem(
    val packageName: String,
    val appLabel: String,
    val volumePercent: Int = -1,
    val brightnessPercent: Int = -1,
    val enabled: Boolean = true
)

/**
 * 全局识屏 OCR 识别引擎选项（Shizuku + Root 原生引擎 / 无障碍节点树 / 视觉大模型 / 自定义 HTTP）
 */
@Serializable
enum class ScreenOcrEngine(val label: String, val description: String) {
    AUTO("智能优先 (推荐)", "优先结合无障碍界面树与 Shizuku / Root 本地原生 OCR，按需补充视觉大模型"),
    // Keep legacy enum names so existing state_json_v5 backups decode cleanly.
    MLKIT_UNBUNDLED("已并入本地原生 OCR", "已统一切换至 Shizuku / Root 本地原生 PP-OCR 推理链路"),
    PPOCR_ONNX("百度系 PP-OCR（本地原生加速）", "使用本地下载的 PP-OCRv6 / v5 模型，通过 Root 或 Shizuku 原生引擎高速识别"),
    ACCESSIBILITY_TREE("无障碍界面树 (纯离线零模型)", "无需下载任何模型，直接提取当前界面原生控件文字与精确位置（不适用于图片内文字）"),
    VISION_LLM("视觉大模型 OCR (需配置 API)", "上传屏幕或框选区域给多模态大模型识别文字与边界坐标，适合复杂图片与手写体"),
    CUSTOM_HTTP_OCR("自定义 HTTP OCR 接口", "接入局域网自建的 RapidOCR / Umi-OCR 或云端 HTTP JSON 识别服务"),
    PPOCR_WASM("本地离线原生 OCR（PP-OCR）", "通过 Root 或 Shizuku 原生常驻引擎运行本地 PP-OCRv6 / v5 及自定义 ONNX 模型");

    fun isInstallFreeEngine(): Boolean =
        this == PPOCR_WASM || this == PPOCR_ONNX || this == MLKIT_UNBUNDLED

    companion object {
        val selectableEntries: List<ScreenOcrEngine>
            get() = listOf(AUTO, PPOCR_WASM, ACCESSIBILITY_TREE, VISION_LLM, CUSTOM_HTTP_OCR)
    }
}

/**
 * 识屏翻译引擎选项
 */
@Serializable
enum class ScreenTranslateEngine(val label: String, val description: String) {
    TRADITIONAL_FREE("传统免费翻译引擎", "内置 Bing 网页 / Google 网页 / MyMemory / 自定义 LibreTranslate 等引擎"),
    BILIBILI_INDEX("B站免费 Index-Translate", "哔哩哔哩开放免费神经翻译接口 (Index-Translate-35B-A3B)，无需 API Key"),
    LLM_API("大语言模型 API 翻译", "调用 OpenAI 兼容接口（支持 DeepSeek 等）批量理解上下文并原位替换"),
    OFFLINE_LEXICON("本地离线词库", "使用软件内按需下载或导入的离线词典包进行纯离线快速翻译")
}

/**
 * 传统免费翻译源选项
 */
@Serializable
enum class TraditionalTranslateProvider(val label: String) {
    AUTO("自动切换 (Bing 优先，失败自动回退)"),
    BING_WEB("微软 Bing 翻译"),
    GOOGLE_WEB("Google 网页翻译"),
    MY_MEMORY("MyMemory 公开词句翻译"),
    CUSTOM_LIBRE("自定义 LibreTranslate / HTTP 接口")
}

/**
 * 全局识屏与翻译配置（API 密钥单独保存在本地隐私偏好，不写入可分享配置码）
 */
@Immutable
@Serializable
data class ScreenAssistantConfig(
    val ocrEngine: ScreenOcrEngine = ScreenOcrEngine.AUTO,
    val ppOcrLangPack: String = "ch_v5",
    val translateEngine: ScreenTranslateEngine = ScreenTranslateEngine.TRADITIONAL_FREE,
    val traditionalProvider: TraditionalTranslateProvider = TraditionalTranslateProvider.AUTO,
    val targetLanguage: String = "zh-CN",
    val traditionalCustomUrl: String = "",
    val llmBaseUrl: String = "https://api.openai.com/v1",
    val llmModel: String = "gpt-4o-mini",
    val visionLlmBaseUrl: String = "https://api.openai.com/v1",
    val visionLlmModel: String = "gpt-4o-mini",
    val customOcrUrl: String = "",
    val breenoAutoFallbackToBuiltin: Boolean = false,
    val breenoUsePrivilegedShell: Boolean = false,
    val usePrivilegedCapture: Boolean = false,
    val extractSubImagesEnabled: Boolean = true,
    val lsposedViewCaptureEnabled: Boolean = false,
    val activeLexiconId: String = "",
    val activeLexiconName: String = "",
    val wasmOcrPackId: String = "ch_v5",
    val fabXFraction: Float = 0.88f,
    val fabYFraction: Float = 0.62f
)

/** 高级设置与扩展功能配置模型 */
@Immutable
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
    val scenarioModes: List<ScenarioModeItem> = emptyList(),
    val screenAssistantConfig: ScreenAssistantConfig = ScreenAssistantConfig(),
    val panelOpenPageMode: PanelOpenPageMode = PanelOpenPageMode.LAST_VISITED,
    val lockedOpenPageId: String = ""
)

/** 页面布局类型（0: 线性布局，1: 自由布局，2: 网格布局） */
@Serializable
enum class PageLayoutType(val code: Int, val label: String) {
    LINEAR(0, "线性布局"),
    FREE(1, "自由布局"),
    GRID(2, "网格布局")
}

/** 组件添加分类 */
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
    APP_KEYBOARD_LAUNCHER(68, "键盘快启", ComponentCategory.APP_DRAWER, 32, 34),
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
    FILE_SHORTCUT(63, "文件", ComponentCategory.FILES, 32, 7),
    ACTION_REGION_SCREENSHOT(65, "区域截屏", ComponentCategory.SYSTEM_ACTION, 8, 6),
    ACTION_SCREEN_ASSISTANT(66, "全局识屏", ComponentCategory.SYSTEM_ACTION, 8, 6),
    ACTION_BREENO_TOUCH(67, "小布识屏", ComponentCategory.SYSTEM_ACTION, 8, 6),
    SUB_PANEL_ENTRY(69, "子面板", ComponentCategory.CONTAINER, 8, 8);

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
@Immutable
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
@Immutable
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
    val containerIconSizeDp: Int = 0,
    val subPanelId: String = ""
)

/**
 * 屏幕单页配置
 * 网格可自动推导列数，也可固定列数；新字段零值兼容旧配置。
 */
@Immutable
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
    val gridFillGaps: Boolean = true,
    // Manual grid row count (0 = automatic) & adaptive shrink/grow row toggle.
    val gridRowCount: Int = 0,
    val gridAutoRows: Boolean = true,
    // Empty = belongs to main panel; non-empty = belongs to SubPanelConfig.id.
    val subPanelId: String = ""
)

/**
 * 配置文件方案元数据
 */
@Immutable
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
@Immutable
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
@Immutable
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
@Immutable
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
@Immutable
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
@Immutable
@Serializable
data class AppAppearanceOverride(val displayName: String = "", val iconUri: String = "")

@Immutable
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
    val subPanels: List<SubPanelConfig> = emptyList(),
    val subPanelLibrary: List<SubPanelTemplate> = emptyList(),
    val noteText: String = "在任意界面从屏幕边缘向内滑动即可呼出负一屏。",
    // 兼容旧版视图字段
    val appearanceConfig: AppearanceConfig = AppearanceConfig(),
    val cards: List<CardConfig> = DefaultPresets.defaultCards(),
    val quickActions: List<QuickActionItem> = DefaultPresets.defaultQuickActions()
)

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

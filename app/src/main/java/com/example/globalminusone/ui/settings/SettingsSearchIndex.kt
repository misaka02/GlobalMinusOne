package com.example.globalminusone.ui.settings

import com.example.globalminusone.data.AppQuickSearchMatcher
import com.example.globalminusone.data.PinyinIndexProvider

/** Searchable setting descriptor used by the home settings search bar for direct jump navigation. */
data class SettingsSearchEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val targetScreen: String,
    val keywords: List<String>,
    val expandOcrSection: Boolean = false,
    val targetGroup: String = ""
)

/**
 * Complete catalog and relevance-ranked (Chinese, English, Pinyin, initials & composite query)
 * search filter covering every settings page, collapsible group, and action dialog.
 */
object SettingsSearchIndex {
    private val defaultPinyinProvider = PinyinIndexProvider()

    val entries: List<SettingsSearchEntry> = listOf(
        // =========================================================================
        // 1. Permissions & System Authorizations (Main Home Checklist)
        // =========================================================================
        SettingsSearchEntry(
            id = "perm_accessibility",
            title = "无障碍服务权限",
            subtitle = "开启或重连全局无障碍服务（读取界面节点、模拟返回/锁屏/识屏）",
            category = "权限检查",
            targetScreen = "PERMISSIONS",
            keywords = listOf("无障碍", "辅助服务", "accessibility", "权限", "节点", "重开", "锁屏权限")
        ),
        SettingsSearchEntry(
            id = "perm_overlay",
            title = "悬浮窗权限",
            subtitle = "允许全局负一屏在其他应用上层显示触发条与主面板",
            category = "权限检查",
            targetScreen = "PERMISSIONS",
            keywords = listOf("悬浮窗", "上层显示", "overlay", "权限", "无法弹出")
        ),
        SettingsSearchEntry(
            id = "perm_storage",
            title = "手机存储与管理所有文件权限",
            subtitle = "授权文件浏览卡片及本地备份读写（Android 11+ 所有文件访问）",
            category = "权限检查",
            targetScreen = "PERMISSIONS",
            keywords = listOf("存储", "所有文件", "文件权限", "外部存储", "storage", "目录读取")
        ),
        SettingsSearchEntry(
            id = "perm_write_settings",
            title = "修改系统设置权限",
            subtitle = "用于滑条直接调节系统屏幕亮度与自动旋转开关",
            category = "权限检查",
            targetScreen = "PERMISSIONS",
            keywords = listOf("修改系统设置", "write settings", "亮度权限", "旋转权限")
        ),
        SettingsSearchEntry(
            id = "perm_dnd",
            title = "勿扰模式权限",
            subtitle = "允许开关卡片切换静音/勿扰状态",
            category = "权限检查",
            targetScreen = "PERMISSIONS",
            keywords = listOf("勿扰", "免打扰", "静音权限", "dnd")
        ),
        SettingsSearchEntry(
            id = "perm_usage_stats",
            title = "使用情况访问权限（使用数据）",
            subtitle = "用于“最近运行”与应用使用时长统计卡片读取应用历史",
            category = "权限检查",
            targetScreen = "PERMISSIONS",
            keywords = listOf("使用数据", "使用情况", "使用统计", "最近运行权限", "usage stats")
        ),
        SettingsSearchEntry(
            id = "perm_device_admin",
            title = "设备管理器权限",
            subtitle = "备选系统锁屏通道（无障碍未开启时锁屏）",
            category = "权限检查",
            targetScreen = "PERMISSIONS",
            keywords = listOf("设备管理", "设备管理器", "admin", "一键锁屏")
        ),
        SettingsSearchEntry(
            id = "perm_bg_popup_battery",
            title = "后台弹出界面与电池优化白名单",
            subtitle = "防止 ColorOS/小米等系统后台杀进程，保障边缘触发常驻响应",
            category = "权限检查",
            targetScreen = "PERMISSIONS",
            keywords = listOf("后台弹出", "电池优化", "省电白名单", "杀后台", "保活", "自启动", "coloros")
        ),

        // =========================================================================
        // 2. Operation & Edge Trigger Settings (OPERATION)
        // =========================================================================
        SettingsSearchEntry(
            id = "op_trigger_way",
            title = "触发方式（侧滑 / 二次滑出 / 侧边下滑 / 悬浮球）",
            subtitle = "选择边缘滑出、二次防误触滑出或侧边沿边缘下滑呼出面板",
            category = "触发设置",
            targetScreen = "OPERATION",
            targetGroup = "触发",
            keywords = listOf("触发方式", "侧滑", "二次滑出", "侧边下滑", "呼出方式", "手势", "operation", "trigger")
        ),
        SettingsSearchEntry(
            id = "op_second_slide_distance",
            title = "二次滑出有效时间窗与最小滑动距离",
            subtitle = "调整二次呼出等待毫秒数与滑动灵敏度距离阈值",
            category = "触发设置",
            targetScreen = "OPERATION",
            targetGroup = "触发",
            keywords = listOf("二次滑出", "时间窗", "滑动距离", "灵敏度", "防误触", "灵敏")
        ),
        SettingsSearchEntry(
            id = "op_feedback_ime",
            title = "触发震动反馈与避让输入法",
            subtitle = "开启触摸震动、切换系统震动波形，打字弹出键盘时自动避让边缘条",
            category = "触发设置",
            targetScreen = "OPERATION",
            targetGroup = "反馈与避让",
            keywords = listOf("震动", "系统震动", "触感反馈", "避让输入法", "键盘避让", "ime")
        ),
        SettingsSearchEntry(
            id = "op_trigger_position_side",
            title = "触发位置与负一屏默认显示位置",
            subtitle = "选择左侧 / 右侧 / 底部 / 双侧触发条，以及面板靠左或靠右弹出",
            category = "触发设置",
            targetScreen = "OPERATION",
            targetGroup = "位置",
            keywords = listOf("触发位置", "显示位置", "左侧", "右侧", "双侧", "左边缘", "右边缘", "靠左", "靠右")
        ),
        SettingsSearchEntry(
            id = "op_zone_visibility_size",
            title = "触发区域可见、手势条透明度与宽高位置",
            subtitle = "显示或隐藏侧边手势条，调节透明度、宽度、长度与上下偏移",
            category = "触发设置",
            targetScreen = "OPERATION",
            targetGroup = "区域",
            keywords = listOf("触发区域", "手势条", "透明度", "宽高", "长度", "上下位置", "区域可见", "绿条", "白条")
        ),
        SettingsSearchEntry(
            id = "op_side_pulldown_offset",
            title = "侧边下滑水平位置（距侧边距离 dp）",
            subtitle = "自动避让系统侧滑返回区或自定义 0–160 dp 贴边距离",
            category = "触发设置",
            targetScreen = "OPERATION",
            targetGroup = "区域",
            keywords = listOf("水平位置", "侧边距离", "侧滑返回", "冲突避让", "贴边距离")
        ),
        SettingsSearchEntry(
            id = "op_gesture_bar_actions",
            title = "手势条动作（单击 / 双击 / 长按 / 四向滑动）",
            subtitle = "自定义边缘手势条触发的快捷系统动作、识屏或面板呼出",
            category = "触发设置",
            targetScreen = "OPERATION",
            targetGroup = "手势条动作",
            keywords = listOf("手势条动作", "单击", "双击", "长按", "上滑", "下滑", "左滑", "右滑", "快捷手势")
        ),

        // =========================================================================
        // 3. Suspension Ball Settings (SB_SETTINGS)
        // =========================================================================
        SettingsSearchEntry(
            id = "sb_enable_entry",
            title = "启用悬浮球（与边缘触发联动）",
            subtitle = "开启桌面/应用上层浮动小白点并配置位置与快捷入口",
            category = "触发设置",
            targetScreen = "OPERATION",
            targetGroup = "悬浮球",
            keywords = listOf("悬浮球", "启用悬浮球", "开启悬浮球", "小白点", "ball")
        ),
        SettingsSearchEntry(
            id = "sb_appearance",
            title = "悬浮球外观（图标、自动贴边吸附、半径、透明度）",
            subtitle = "开关贴边吸附或自由停放，调整悬浮球大小半径与不透明度",
            category = "悬浮球设置",
            targetScreen = "SB_SETTINGS",
            targetGroup = "外观",
            keywords = listOf("悬浮球", "贴边", "吸附", "半径", "大小", "图标", "透明度", "小白点", "ball")
        ),
        SettingsSearchEntry(
            id = "sb_breathing",
            title = "悬浮球呼吸灯模式与频率",
            subtitle = "开启悬浮球空闲呼吸闪烁动效并调节呼吸节奏频率",
            category = "悬浮球设置",
            targetScreen = "SB_SETTINGS",
            targetGroup = "呼吸",
            keywords = listOf("悬浮球", "呼吸", "呼吸模式", "呼吸频率", "闪烁")
        ),
        SettingsSearchEntry(
            id = "sb_fade",
            title = "悬浮球自动渐隐与等待时间",
            subtitle = "无操作后自动半透明隐身并配置等待秒数",
            category = "悬浮球设置",
            targetScreen = "SB_SETTINGS",
            targetGroup = "渐隐",
            keywords = listOf("悬浮球", "渐隐", "自动渐隐", "半透明", "隐藏等待")
        ),
        SettingsSearchEntry(
            id = "sb_mouse",
            title = "悬浮球鼠标模式、移动速度与触控行程",
            subtitle = "将悬浮球切为大屏触控板鼠标指针并调节指针灵敏度与映射比例",
            category = "悬浮球设置",
            targetScreen = "SB_SETTINGS",
            targetGroup = "鼠标",
            keywords = listOf("鼠标", "鼠标模式", "移动速度", "触控行程", "光标", "指针", "悬浮球")
        ),
        SettingsSearchEntry(
            id = "sb_actions",
            title = "悬浮球动作（单击 / 双击 / 上下左右滑 / 长按）",
            subtitle = "分别为悬浮球七种手势绑定呼出面板、返回、锁屏或识屏等动作",
            category = "悬浮球设置",
            targetScreen = "SB_SETTINGS",
            targetGroup = "动作",
            keywords = listOf("悬浮球动作", "单击", "双击", "长按", "上滑", "下滑", "左滑", "右滑")
        ),

        // =========================================================================
        // 4. Theme & Panel Geometry Settings (THEME)
        // =========================================================================
        SettingsSearchEntry(
            id = "theme_settings_ui",
            title = "设置页面配色（跟随系统深色 / 手动暗色）",
            subtitle = "单独切换设置界面的日间与夜间深色外观",
            category = "主题外观",
            targetScreen = "THEME",
            targetGroup = "设置页面配色",
            keywords = listOf("设置深色", "设置暗色", "设置主题", "跟随系统", "深浅色")
        ),
        SettingsSearchEntry(
            id = "theme_panel_presets",
            title = "面板配色与一键预设（暗色卡片 / 亮色卡片 / 跟随系统）",
            subtitle = "开启暗色卡片模式、自动跟随系统主题并选用预设视觉方案",
            category = "主题外观",
            targetScreen = "THEME",
            targetGroup = "亮色 / 暗色模式与一键预设",
            keywords = listOf("面板深色", "深色", "暗色", "浅色", "亮色", "跟随系统", "配色", "预设", "theme")
        ),
        SettingsSearchEntry(
            id = "theme_backdrop_blur",
            title = "背景遮罩与高斯模糊（Android 12+）",
            subtitle = "调节后台遮罩深浅与不透明度，设置面板底层实时高斯模糊半径",
            category = "主题外观",
            targetScreen = "THEME",
            targetGroup = "背景遮罩",
            keywords = listOf("背景遮罩", "高斯模糊", "模糊半径", "毛玻璃", "暗色遮罩", "blur", "遮罩透明度")
        ),
        SettingsSearchEntry(
            id = "theme_panel_base",
            title = "面板底框颜色与底框不透明度",
            subtitle = "调整负一屏外框背景色调与半透明穿透程度",
            category = "主题外观",
            targetScreen = "THEME",
            targetGroup = "面板底框",
            keywords = listOf("面板底框", "底框颜色", "底框透明度", "面板背景")
        ),
        SettingsSearchEntry(
            id = "theme_panel_geometry",
            title = "面板尺寸与位置（负一屏宽高、边缘位置、垂直位置、间隔）",
            subtitle = "精确调节负一屏宽度%、高度%、靠边间距与上下偏移",
            category = "主题外观",
            targetScreen = "THEME",
            targetGroup = "面板尺寸与位置",
            keywords = listOf("面板尺寸", "负一屏宽度", "负一屏高度", "宽度", "高度", "位置", "边缘间隔", "面板大小")
        ),
        SettingsSearchEntry(
            id = "theme_cards_style",
            title = "卡片外观、圆角、卡片间距与全局不透明度",
            subtitle = "统一修改所有卡片底色、透明度、圆角 dp 与间距 dp（长按卡片可单独覆盖）",
            category = "主题外观",
            targetScreen = "THEME",
            targetGroup = "卡片外观与透明度 (长按单个卡片可独立微调)",
            keywords = listOf("卡片外观", "卡片圆角", "圆角", "卡片间距", "间距", "卡片透明度", "卡片颜色")
        ),
        SettingsSearchEntry(
            id = "theme_text_style",
            title = "文字与标题颜色及不透明度",
            subtitle = "自定义全局正文颜色、标题文字颜色与文字不透明度",
            category = "主题外观",
            targetScreen = "THEME",
            targetGroup = "文字与标题颜色及不透明度",
            keywords = listOf("文字颜色", "标题颜色", "文字透明度", "字体颜色", "字体透明")
        ),
        SettingsSearchEntry(
            id = "theme_animation_notch",
            title = "呼出动画、动画时间与覆盖异形屏",
            subtitle = "切换面板滑入/缩放动效与毫秒时长，开启挖孔/刘海异形屏全高覆盖",
            category = "主题外观",
            targetScreen = "THEME",
            targetGroup = "动画与其它",
            keywords = listOf("呼出动画", "动画", "动画时间", "速度", "异形屏", "刘海屏", "挖孔屏", "全屏覆盖")
        ),

        // =========================================================================
        // 5. Advance Settings (ADVANCE)
        // =========================================================================
        SettingsSearchEntry(
            id = "adv_layout_lock",
            title = "锁定布局与编辑保护",
            subtitle = "锁定后禁止增删、拖动或误改卡片；导入/切换方案前可在此解锁",
            category = "高级设置",
            targetScreen = "ADVANCE",
            targetGroup = "负一屏",
            keywords = listOf("锁定布局", "布局锁", "锁", "防误删", "编辑保护", "解锁")
        ),
        SettingsSearchEntry(
            id = "adv_auto_a11y",
            title = "自动跳转辅助服务开关",
            subtitle = "触发依赖无障碍的功能时是否自动拉起系统无障碍设置页",
            category = "高级设置",
            targetScreen = "ADVANCE",
            targetGroup = "启动与自动收起",
            keywords = listOf("自动跳转", "辅助服务", "无障碍跳转")
        ),
        SettingsSearchEntry(
            id = "adv_icon_click_behavior",
            title = "点击应用图标行为（自动收起或保持面板）",
            subtitle = "设置从负一屏打开应用后是否自动关闭负一屏浮窗",
            category = "高级设置",
            targetScreen = "ADVANCE",
            targetGroup = "启动与自动收起",
            keywords = listOf("点击应用", "图标行为", "打开后收起", "自动关闭面板")
        ),
        SettingsSearchEntry(
            id = "adv_landscape_close",
            title = "横屏自动关闭",
            subtitle = "玩游戏或横屏看视频时自动禁止或收起负一屏",
            category = "高级设置",
            targetScreen = "ADVANCE",
            targetGroup = "启动与自动收起",
            keywords = listOf("横屏", "横屏关闭", "游戏防误触", "自动关闭")
        ),
        SettingsSearchEntry(
            id = "adv_blacklist",
            title = "应用黑名单（指定应用内禁用触发）",
            subtitle = "在游戏、相机等选定应用中自动屏蔽侧边与悬浮球触发",
            category = "高级设置",
            targetScreen = "ADVANCE",
            targetGroup = "启动与自动收起",
            keywords = listOf("黑名单", "应用屏蔽", "禁触发", "游戏免打扰", "blacklist")
        ),
        SettingsSearchEntry(
            id = "adv_widget_close",
            title = "点击小部件自动收起",
            subtitle = "点击嵌套的桌面 AppWidget 后自动关闭负一屏面板",
            category = "高级设置",
            targetScreen = "ADVANCE",
            targetGroup = "启动与自动收起",
            keywords = listOf("小部件收起", "小部件", "widget", "点击收起")
        ),
        SettingsSearchEntry(
            id = "adv_layer_grid_divs",
            title = "无障碍悬浮层级与自由布局宽度等分",
            subtitle = "启用最高层级（可覆盖系统弹窗）以及调整自由画布逻辑列数（默认 32 列）",
            category = "高级设置",
            targetScreen = "ADVANCE",
            targetGroup = "显示层级与网格",
            keywords = listOf("悬浮层级", "无障碍层级", "最高层级", "自由布局等分", "宽度等分", "逻辑列数", "网格列数")
        ),
        SettingsSearchEntry(
            id = "adv_root_mode",
            title = "Root 模式与特权原画质通道",
            subtitle = "使用 Root (su) 提升原画质截屏、缓存原图直提与系统控制响应速度",
            category = "高级设置",
            targetScreen = "ADVANCE",
            targetGroup = "Root",
            keywords = listOf("root", "su", "root权限", "特权", "底层通道")
        ),

        // =========================================================================
        // 6. Extend Settings (EXTEND)
        // =========================================================================
        SettingsSearchEntry(
            id = "ext_notifications",
            title = "通知轻提示、提醒震动与自动跳转白名单",
            subtitle = "收到指定应用通知时弹出轻提示条与触感提示",
            category = "扩展功能",
            targetScreen = "EXTEND",
            targetGroup = "通知",
            keywords = listOf("通知提醒", "轻提示", "通知震动", "白名单通知", "通知跳转", "notification")
        ),
        SettingsSearchEntry(
            id = "ext_captcha_otp",
            title = "短信验证码自动复制",
            subtitle = "监听通知/短信中的 4–8 位验证码并一键复制到剪贴板",
            category = "扩展功能",
            targetScreen = "EXTEND",
            targetGroup = "其它",
            keywords = listOf("验证码", "短信验证码", "自动复制", "otp")
        ),
        SettingsSearchEntry(
            id = "ext_scenarios",
            title = "应用情景模式（按前台应用自动调节亮度/音量）",
            subtitle = "切换到指定应用时自动应用预设媒体音量或屏幕亮度",
            category = "扩展功能",
            targetScreen = "EXTEND",
            targetGroup = "其它",
            keywords = listOf("情景模式", "自动化", "情景规则", "分应用亮度", "分应用音量")
        ),
        SettingsSearchEntry(
            id = "ext_eye_care",
            title = "全局护眼开关与护眼滤色选择",
            subtitle = "叠加全局防蓝光护眼色温蒙层并切换暖黄/柔和暗绿等预设颜色",
            category = "扩展功能",
            targetScreen = "EXTEND",
            targetGroup = "护眼与滤色",
            keywords = listOf("护眼", "全局护眼", "护眼色", "滤色", "防蓝光", "色温", "偏黄", "eye care")
        ),

        // =========================================================================
        // 7. Screen Assistant, OCR, Translate & Breeno (SCREEN_ASSISTANT)
        // =========================================================================
        SettingsSearchEntry(
            id = "ocr_engine_select",
            title = "OCR 识别引擎切换",
            subtitle = "在 自动 / 免安装离线 OCR (PP-OCR) / 无障碍节点 / 视觉大模型 / HTTP OCR 间切换",
            category = "全局识屏与翻译",
            targetScreen = "SCREEN_ASSISTANT",
            targetGroup = "OCR",
            keywords = listOf("ocr", "识屏", "识字", "识别引擎", "自动引擎", "无障碍文字")
        ),
        SettingsSearchEntry(
            id = "ocr_extension_models",
            title = "免安装离线 OCR 扩展与 PP-OCR / Tesseract 模型管理",
            subtitle = "下载通用 OCR 运行时、多语种模型、导入本地 ONNX 或配置 GitHub 加速镜像前缀",
            category = "全局识屏与翻译",
            targetScreen = "SCREEN_ASSISTANT",
            targetGroup = "OCR",
            expandOcrSection = true,
            keywords = listOf("免安装", "离线ocr", "扩展", "下载模型", "paddle", "ppocr", "tesseract", "onnx", "镜像", "加速前缀")
        ),
        SettingsSearchEntry(
            id = "ocr_vision_llm",
            title = "视觉大模型 OCR 接口 (Vision LLM)",
            subtitle = "配置支持多模态图片识别的接口地址、模型名称与 API Key",
            category = "全局识屏与翻译",
            targetScreen = "SCREEN_ASSISTANT",
            targetGroup = "OCR",
            keywords = listOf("视觉大模型", "vision", "多模态", "vlm", "图片识别模型", "api")
        ),
        SettingsSearchEntry(
            id = "ocr_custom_http",
            title = "自定义 HTTP OCR 接口",
            subtitle = "对接局域网或自建 HTTP 文字识别服务端",
            category = "全局识屏与翻译",
            targetScreen = "SCREEN_ASSISTANT",
            targetGroup = "OCR",
            keywords = listOf("http ocr", "自定义ocr", "自建ocr", "局域网识别")
        ),
        SettingsSearchEntry(
            id = "ocr_subimage_extract",
            title = "同时识别界面缩略图与小图（提图 / 去角标）",
            subtitle = "开启/关闭图片提取、界面选图框及底层缓存原图直提",
            category = "全局识屏与翻译",
            targetScreen = "SCREEN_ASSISTANT",
            targetGroup = "OCR",
            keywords = listOf("提图", "提取图片", "抠图", "缩略图", "原图直提", "去角标")
        ),
        SettingsSearchEntry(
            id = "ocr_shizuku_root_capture",
            title = "主动请求 Shizuku / Root 原画质截屏与深层节点",
            subtitle = "配合 Shizuku 或 Root 直接抽取原生视图树与第三方应用缓存原图",
            category = "全局识屏与翻译",
            targetScreen = "SCREEN_ASSISTANT",
            targetGroup = "OCR",
            keywords = listOf("shizuku", "原画质", "原图提取", "深层节点", "dumpsys", "截屏权限")
        ),
        SettingsSearchEntry(
            id = "ocr_translate_engine",
            title = "翻译引擎方案与目标语言设置",
            subtitle = "切换 B站免费 / 自动 / 离线词库 / 传统接口 / 大模型翻译与目标语种",
            category = "全局识屏与翻译",
            targetScreen = "SCREEN_ASSISTANT",
            targetGroup = "TRANSLATE",
            keywords = listOf("翻译", "原位翻译", "翻译引擎", "目标语言", "英译中", "中译英", "日译中", "bilibili", "deepseek", "openai")
        ),
        SettingsSearchEntry(
            id = "ocr_translate_free_sources",
            title = "传统免费翻译源与自定义 LibreTranslate 地址",
            subtitle = "在自动、MyMemory、Bing 或自建 LibreTranslate 服务器之间切换",
            category = "全局识屏与翻译",
            targetScreen = "SCREEN_ASSISTANT",
            targetGroup = "TRANSLATE",
            keywords = listOf("免费翻译", "传统翻译", "libretranslate", "bing", "mymemory")
        ),
        SettingsSearchEntry(
            id = "ocr_translate_bilibili_llm",
            title = "B站免费 Index-Translate 与大模型 API 翻译（DeepSeek / OpenAI）",
            subtitle = "查看 B站免费接口可用状态或配置兼容 OpenAI 格式的大模型密钥",
            category = "全局识屏与翻译",
            targetScreen = "SCREEN_ASSISTANT",
            targetGroup = "TRANSLATE",
            keywords = listOf("bilibili", "b站", "index-translate", "deepseek", "openai", "大模型", "llm", "api key")
        ),
        SettingsSearchEntry(
            id = "ocr_offline_lexicon",
            title = "离线词库附件管理（URL 下载 / 本地导入词典）",
            subtitle = "下载或导入 .tsv / .json / .gz 双语词典用于离线翻译与本地缓存加速",
            category = "全局识屏与翻译",
            targetScreen = "SCREEN_ASSISTANT",
            targetGroup = "ATTACHMENTS",
            keywords = listOf("离线词库", "词典", "词库", "导入词库", "下载词库", "lexicon")
        ),
        SettingsSearchEntry(
            id = "ocr_breeno_bridge",
            title = "ColorOS 小布识屏联动与特权通道",
            subtitle = "实时探测系统小布组件状态，按需启用 Shizuku/Root 拉起系统小布识屏",
            category = "全局识屏与翻译",
            targetScreen = "SCREEN_ASSISTANT",
            targetGroup = "BREENO",
            keywords = listOf("小布", "小布识屏", "breeno", "coloros", "系统识屏")
        ),

        // =========================================================================
        // 8. Config Profiles, Backups, Panels Import, Tutorial & About
        // =========================================================================
        SettingsSearchEntry(
            id = "cfg_profiles",
            title = "配置方案管理（新建、切换与删除方案）",
            subtitle = "保存多套桌面与工作风格配置并在横滑方案卡间一键切换",
            category = "配置管理",
            targetScreen = "CONFIG",
            keywords = listOf("配置方案", "方案", "多配置", "切换方案", "新建方案")
        ),
        SettingsSearchEntry(
            id = "cfg_share_code",
            title = "配置码导入与导出",
            subtitle = "通过剪贴板分享码导入或导出一整套负一屏设置与组件",
            category = "配置管理",
            targetScreen = "CONFIG",
            keywords = listOf("配置码", "分享码", "口令", "导入配置码", "导出配置码")
        ),
        SettingsSearchEntry(
            id = "cfg_backup_restore",
            title = "完整备份到文件与恢复备份",
            subtitle = "将 state_json_v5 完整导出至本地文件，或从备份文件安全恢复",
            category = "配置管理",
            targetScreen = "CONFIG",
            keywords = listOf("备份", "恢复", "还原", "导出备份", "备份文件", "json")
        ),
        SettingsSearchEntry(
            id = "cfg_panels_import",
            title = "迁移导入 Panels 布局（整页 / 单个文件夹）",
            subtitle = "读取 Panels 备份包并保留原列数网格结构导入到全局负一屏",
            category = "配置管理",
            targetScreen = "CONFIG",
            keywords = listOf("panels", "迁移", "panels导入", "导入布局")
        ),
        SettingsSearchEntry(
            id = "help_tutorial",
            title = "使用教程与常见问题帮助",
            subtitle = "查看边缘呼出、编辑卡片、文件夹与识屏提图操作指南",
            category = "配置与帮助",
            targetScreen = "TUTORIAL",
            keywords = listOf("教程", "使用教程", "帮助", "怎么用", "常见问题", "指南", "tutorial")
        ),
        SettingsSearchEntry(
            id = "about_version",
            title = "关于与版本信息、开发者及开源仓库",
            subtitle = "查看当前版本号、签名证书指纹与 GitHub 项目地址",
            category = "配置与帮助",
            targetScreen = "ABOUT",
            keywords = listOf("关于", "版本号", "github", "开源地址", "开发者", "反馈", "about")
        ),
        SettingsSearchEntry(
            id = "about_licenses_privacy",
            title = "开源许可、第三方声明与隐私政策",
            subtitle = "查阅隐私合规说明及第三方开源组件许可列表",
            category = "配置与帮助",
            targetScreen = "ABOUT",
            keywords = listOf("开源许可", "第三方", "声明", "隐私政策", "隐私", "合规", "license")
        ),
        SettingsSearchEntry(
            id = "about_reset_default",
            title = "恢复初始默认布局",
            subtitle = "清除当前卡片排版与自定义设置，还原为出厂默认页面",
            category = "配置与帮助",
            targetScreen = "ABOUT",
            keywords = listOf("恢复默认", "重置布局", "出厂设置", "清空重来", "reset")
        )
    )

    fun search(query: String): List<SettingsSearchEntry> = search(query, defaultPinyinProvider)

    fun search(query: String, pinyinProvider: PinyinIndexProvider): List<SettingsSearchEntry> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return emptyList()
        val tokens = clean.split(Regex("""[\s/·,，、]+""")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return entries.mapNotNull { entry ->
            val titleLower = entry.title.lowercase()
            val subLower = entry.subtitle.lowercase()
            val catLower = entry.category.lowercase()
            val kwLower = entry.keywords.map { it.lowercase() }
            val titleKeys = pinyinProvider.buildTextSearchKeys(entry.title)
            val kwKeys = kwLower.map { pinyinProvider.buildTextSearchKeys(it) }
            val subKeys = pinyinProvider.buildTextSearchKeys(entry.subtitle)
            val combinedTitleKw = buildString {
                append(titleKeys.compactLower)
                append(pinyinProvider.buildTextSearchKeys(entry.category).compactLower)
                for (kw in kwLower) append(kw)
            }
            val combinedAll = combinedTitleKw + subKeys.compactLower

            var totalScore = 0
            for (tok in tokens) {
                var score = when {
                    titleLower == tok -> 140
                    titleLower.startsWith(tok) -> 110
                    titleLower.contains(tok) -> 90
                    kwLower.any { it == tok } -> 85
                    kwLower.any { it.contains(tok) } -> 68
                    subLower.contains(tok) -> 42
                    catLower.contains(tok) -> 28
                    else -> 0
                }
                if (score == 0) {
                    val titlePinyin = AppQuickSearchMatcher.scoreTextMatch(titleKeys, tok)
                    val kwPinyin = kwKeys.maxOfOrNull { AppQuickSearchMatcher.scoreTextMatch(it, tok) } ?: 0
                    val subPinyin = AppQuickSearchMatcher.scoreTextMatch(subKeys, tok)
                    score = when {
                        titlePinyin >= 800 -> 66
                        kwPinyin >= 800 -> 60
                        titlePinyin >= 600 -> 50
                        kwPinyin >= 600 -> 44
                        subPinyin >= 800 -> 34
                        titlePinyin > 0 || kwPinyin > 0 -> 26
                        else -> 0
                    }
                }
                if (score == 0 && tok.length >= 2) {
                    val tokCompact = AppQuickSearchMatcher.cleanQuery(tok)
                    if (tokCompact.length >= 2) {
                        score = when {
                            AppQuickSearchMatcher.isOrderedSubsequence(combinedTitleKw, tokCompact) -> 30
                            tokCompact.all { it in combinedTitleKw } -> 24
                            tokCompact.all { it in combinedAll } -> 16
                            else -> 0
                        }
                    }
                }
                if (score == 0) return@mapNotNull null
                totalScore += score
            }
            entry to totalScore
        }.sortedByDescending { it.second }.map { it.first }
    }
}

package com.example.globalminusone.data

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
        ),
        QuickActionItem(
            id = "sc_builtin_region_screenshot",
            title = "区域截屏",
            subtitle = "框选截屏与识图",
            iconName = "crop_free",
            colorHex = "#00897B",
            intentUri = "action:screenshot_region"
        ),
        QuickActionItem(
            id = "sc_builtin_screen_assistant",
            title = "全局识屏与翻译",
            subtitle = "原位选字/翻译/提小图",
            iconName = "translate",
            colorHex = "#3949AB",
            intentUri = "action:screen_assistant"
        ),
        QuickActionItem(
            id = "sc_builtin_breeno_touch",
            title = "小布识屏 (ColorOS)",
            subtitle = "唤起系统小布识屏",
            iconName = "auto_awesome",
            colorHex = "#5E35B1",
            intentUri = "action:breeno_touch"
        ),
        QuickActionItem(
            id = "sc_builtin_eye_care",
            title = "全局护眼开关",
            subtitle = "切换护眼滤镜",
            iconName = "visibility",
            colorHex = "#7CB342",
            intentUri = "action:eye_protection"
        ),
        QuickActionItem(
            id = "sc_builtin_media_play_pause",
            title = "媒体播放/暂停",
            subtitle = "切换当前音频状态",
            iconName = "play_arrow",
            colorHex = "#E65100",
            intentUri = "action:media_play_pause"
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


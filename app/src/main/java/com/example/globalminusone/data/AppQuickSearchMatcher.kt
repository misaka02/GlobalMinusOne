package com.example.globalminusone.data

/**
 * Pure-Kotlin Hanzi -> Pinyin, ASCII CamelCase token, and T9 quick-search matcher.
 * Zero Android framework imports so unit tests run directly on the JVM and domain boundaries stay clean.
 */
class PinyinIndexProvider(
    private val fallbackCharToPinyin: ((Char) -> String?)? = null
) {
    private val runtimeCache = HashMap<Char, List<String>>()

    fun charReadings(ch: Char): List<String> {
        val curated = CURATED_HANZI_PINYIN[ch]
        if (fallbackCharToPinyin == null) return curated.orEmpty()
        if (curated != null && curated.isNotEmpty()) {
            val extra = synchronized(runtimeCache) {
                runtimeCache.getOrPut(ch) {
                    val fb = cleanFallbackReading(fallbackCharToPinyin.invoke(ch))
                    if (fb == null || fb in curated) curated else (curated + fb).take(3)
                }
            }
            return extra
        }
        return synchronized(runtimeCache) {
            runtimeCache.getOrPut(ch) {
                val fb = cleanFallbackReading(fallbackCharToPinyin.invoke(ch))
                if (fb != null) listOf(fb) else emptyList()
            }
        }
    }

    private fun cleanFallbackReading(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val firstWord = raw.trim().lowercase().split(' ', '\t', '\'', '-').firstOrNull { it.isNotEmpty() } ?: return null
        val clean = buildString {
            for (c in firstWord) {
                if (c in 'a'..'z') append(c)
            }
        }
        return clean.takeIf { it.isNotEmpty() }
    }

    fun extractSyllableTokens(text: String): List<List<String>> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<List<String>>(text.length.coerceAtMost(24))
        var i = 0
        while (i < text.length && out.size < 24) {
            val c = text[i]
            when {
                c in '\u4E00'..'\u9FFF' -> {
                    val r = charReadings(c)
                    if (r.isNotEmpty()) out += r
                    i++
                }
                c in 'a'..'z' || c in 'A'..'Z' -> {
                    val runStart = i
                    while (i < text.length && (text[i] in 'a'..'z' || text[i] in 'A'..'Z')) i++
                    var partStart = runStart
                    for (j in (runStart + 1) until i) {
                        val prev = text[j - 1]
                        val cur = text[j]
                        val nextLower = j + 1 < i && text[j + 1] in 'a'..'z'
                        if (cur in 'A'..'Z' && (prev in 'a'..'z' || nextLower)) {
                            out += listOf(text.substring(partStart, j).lowercase())
                            partStart = j
                            if (out.size >= 24) break
                        }
                    }
                    if (out.size < 24 && partStart < i) {
                        out += listOf(text.substring(partStart, i).lowercase())
                    }
                }
                c in '0'..'9' -> {
                    val runStart = i
                    while (i < text.length && text[i] in '0'..'9') i++
                    out += listOf(text.substring(runStart, i))
                }
                else -> i++
            }
        }
        return out
    }

    fun buildTextSearchKeys(text: String): TextSearchKeys {
        val rawLower = text.trim().lowercase()
        val compactLower = buildString(rawLower.length) {
            for (ch in rawLower) {
                if (ch.isLetterOrDigit() || ch in '\u4E00'..'\u9FFF') append(ch)
            }
        }
        val digitsOnly = buildString {
            for (ch in rawLower) {
                if (ch in '0'..'9') append(ch)
            }
        }
        val tokens = extractSyllableTokens(text)
        var fullCombos = listOf("")
        var initialCombos = listOf("")
        for (readings in tokens) {
            if (readings.isEmpty()) continue
            val nextFull = LinkedHashSet<String>(8)
            val nextInit = LinkedHashSet<String>(8)
            for (r in readings) {
                val initToken = if (r.isNotEmpty() && r[0] in '0'..'9') r else r.take(1)
                for (prefix in fullCombos) {
                    if (nextFull.size >= 8) break
                    nextFull += prefix + r
                }
                for (prefix in initialCombos) {
                    if (nextInit.size >= 8) break
                    nextInit += prefix + initToken
                }
            }
            if (nextFull.isNotEmpty()) fullCombos = nextFull.toList()
            if (nextInit.isNotEmpty()) initialCombos = nextInit.toList()
        }
        val fullList = if (tokens.isEmpty()) emptyList() else fullCombos.filter { it.isNotEmpty() }.distinct()
        val initList = if (tokens.isEmpty()) emptyList() else initialCombos.filter { it.isNotEmpty() }.distinct()
        val t9Fulls = fullList.map(::toT9String).filter { it.isNotEmpty() }.distinct()
        val t9Initials = initList.map(::toT9String).filter { it.isNotEmpty() }.distinct()
        return TextSearchKeys(
            rawLower = rawLower,
            compactLower = compactLower,
            syllableLists = tokens,
            fullPinyins = fullList,
            initials = initList,
            digitsOnly = digitsOnly,
            t9Fulls = t9Fulls,
            t9Initials = t9Initials
        )
    }

    companion object {
        fun charToT9Digit(c: Char): Char? = when (c.lowercaseChar()) {
            in '0'..'9' -> c
            'a', 'b', 'c' -> '2'
            'd', 'e', 'f' -> '3'
            'g', 'h', 'i' -> '4'
            'j', 'k', 'l' -> '5'
            'm', 'n', 'o' -> '6'
            'p', 'q', 'r', 's' -> '7'
            't', 'u', 'v' -> '8'
            'w', 'x', 'y', 'z' -> '9'
            else -> null
        }

        fun toT9String(text: String): String = buildString(text.length) {
            for (ch in text) {
                charToT9Digit(ch)?.let(::append)
            }
        }

        private val CURATED_HANZI_PINYIN: Map<Char, List<String>> = mapOf(
            // Polyphones common in apps and settings
            '乐' to listOf("yue", "le"), '行' to listOf("hang", "xing"), '长' to listOf("zhang", "chang"),
            '重' to listOf("zhong", "chong"), '调' to listOf("tiao", "diao"), '传' to listOf("chuan", "zhuan"),
            '弹' to listOf("dan", "tan"), '藏' to listOf("cang", "zang"), '率' to listOf("lv", "shuai"),
            '数' to listOf("shu"), '便' to listOf("bian"), '单' to listOf("dan"),
            '校' to listOf("xiao", "jiao"), '秘' to listOf("mi", "bi"), '解' to listOf("jie"),
            '藏' to listOf("cang", "zang"), '差' to listOf("cha"), '和' to listOf("he"),
            '度' to listOf("du"), '省' to listOf("sheng"), '卡' to listOf("ka"),
            '角' to listOf("jiao"), '背' to listOf("bei"), '模' to listOf("mo"),
            '落' to listOf("luo"), '露' to listOf("lu"), '量' to listOf("liang"),
            '降' to listOf("jiang"), '着' to listOf("zhao", "zhe"), '查' to listOf("cha"),
            '藏' to listOf("cang", "zang"), '弹' to listOf("dan", "tan"),
            // Top Chinese apps & services
            '微' to listOf("wei"), '信' to listOf("xin"), '支' to listOf("zhi"), '付' to listOf("fu"),
            '宝' to listOf("bao"), '抖' to listOf("dou"), '音' to listOf("yin"), '哔' to listOf("bi"),
            '哩' to listOf("li"), '淘' to listOf("tao"), '京' to listOf("jing"), '东' to listOf("dong"),
            '高' to listOf("gao"), '德' to listOf("de"), '百' to listOf("bai"), '地' to listOf("di"),
            '图' to listOf("tu"), '网' to listOf("wang"), '易' to listOf("yi"), '云' to listOf("yun"),
            '美' to listOf("mei"), '团' to listOf("tuan"), '拼' to listOf("pin"), '多' to listOf("duo"),
            '小' to listOf("xiao"), '红' to listOf("hong"), '书' to listOf("shu"), '知' to listOf("zhi"),
            '乎' to listOf("hu"), '博' to listOf("bo"), '快' to listOf("kuai"), '手' to listOf("shou"),
            '闲' to listOf("xian"), '鱼' to listOf("yu"), '夸' to listOf("kua"), '克' to listOf("ke"),
            '铁' to listOf("tie"), '路' to listOf("lu"), '银' to listOf("yin"), '建' to listOf("jian"),
            '工' to listOf("gong"), '农' to listOf("nong"), '招' to listOf("zhao"), '商' to listOf("shang"),
            '相' to listOf("xiang"), '册' to listOf("ce"), '机' to listOf("ji"), '设' to listOf("she"),
            '置' to listOf("zhi"), '文' to listOf("wen"), '件' to listOf("jian"), '流' to listOf("liu"),
            '览' to listOf("lan"), '器' to listOf("qi"), '计' to listOf("ji"), '算' to listOf("suan"),
            '日' to listOf("ri"), '历' to listOf("li"), '时' to listOf("shi"), '钟' to listOf("zhong"),
            '闹' to listOf("nao"), '天' to listOf("tian"), '气' to listOf("qi"), '短' to listOf("duan"),
            '电' to listOf("dian"), '话' to listOf("hua"), '通' to listOf("tong"), '讯' to listOf("xun"),
            '录' to listOf("lu"), '备' to listOf("bei"), '忘' to listOf("wang"), '签' to listOf("qian"),
            '笔' to listOf("bi"), '记' to listOf("ji"), '邮' to listOf("you"), '箱' to listOf("xiang"),
            '视' to listOf("shi"), '频' to listOf("pin"), '游' to listOf("you"), '戏' to listOf("xi"),
            '店' to listOf("dian"), '应' to listOf("ying"), '用' to listOf("yong"), '市' to listOf("shi"),
            '场' to listOf("chang"), '管' to listOf("guan"), '家' to listOf("jia"), '安' to listOf("an"),
            '全' to listOf("quan"), '卫' to listOf("wei"), '士' to listOf("shi"), '酷' to listOf("ku"),
            '狗' to listOf("gou"), '我' to listOf("wo"), '豆' to listOf("dou"), '瓣' to listOf("ban"),
            '携' to listOf("xie"), '程' to listOf("cheng"), '飞' to listOf("fei"), '猪' to listOf("zhu"),
            '饿' to listOf("e"), '了' to listOf("le"), '么' to listOf("me"), '滴' to listOf("di"),
            '虎' to listOf("hu"), '扑' to listOf("pu"), '贴' to listOf("tie"), '吧' to listOf("ba"),
            '漫' to listOf("man"), '画' to listOf("hua"), '喜' to listOf("xi"), '马' to listOf("ma"),
            '拉' to listOf("la"), '雅' to listOf("ya"), '爱' to listOf("ai"), '奇' to listOf("qi"),
            '艺' to listOf("yi"), '腾' to listOf("teng"), '优' to listOf("you"), '麦' to listOf("mai"),
            '芒' to listOf("mang"), '果' to listOf("guo"), '斗' to listOf("dou"), '牙' to listOf("ya"),
            '丁' to listOf("ding"), '钉' to listOf("ding"), '书' to listOf("shu"), '飞' to listOf("fei"),
            '企' to listOf("qi"), '业' to listOf("ye"), '金' to listOf("jin"),
            '山' to listOf("shan"), '词' to listOf("ci"), '霸' to listOf("ba"), '有' to listOf("you"),
            '道' to listOf("dao"), '欧' to listOf("ou"), '路' to listOf("lu"), '翻' to listOf("fan"),
            '译' to listOf("yi"), '豆' to listOf("dou"), '包' to listOf("bao"), '元' to listOf("yuan"),
            '千' to listOf("qian"), '问' to listOf("wen"), '星' to listOf("xing"), '火' to listOf("huo"),
            '即' to listOf("ji"), '刻' to listOf("ke"), '买' to listOf("mai"), '菜' to listOf("cai"),
            '盒' to listOf("he"), '叮' to listOf("ding"), '咚' to listOf("dong"), '顺' to listOf("shun"),
            '丰' to listOf("feng"), '鸟' to listOf("niao"), '驿' to listOf("yi"), '站' to listOf("zhan"),
            '花' to listOf("hua"), '生' to listOf("sheng"), '咪' to listOf("mi"), '咕' to listOf("gu"),
            '悦' to listOf("yue"), '读' to listOf("du"), '起' to listOf("qi"), '点' to listOf("dian"),
            '晋' to listOf("jin"), '江' to listOf("jiang"), '番' to listOf("fan"), '茄' to listOf("qie"),
            '极' to listOf("ji"), '速' to listOf("su"), '版' to listOf("ban"), '轻' to listOf("qing"),
            '聊' to listOf("liao"), '天' to listOf("tian"), '导' to listOf("dao"), '航' to listOf("hang"),
            '打' to listOf("da"), '车' to listOf("che"), '外' to listOf("wai"), '卖' to listOf("mai"),
            '购' to listOf("gou"), '物' to listOf("wu"), '社' to listOf("she"), '交' to listOf("jiao"),
            '办' to listOf("ban"), '公' to listOf("gong"), '修' to listOf("xiu"), '剪' to listOf("jian"),
            '映' to listOf("ying"), '美' to listOf("mei"), '颜' to listOf("yan"), '醒' to listOf("xing"),
            '图' to listOf("tu"), '录' to listOf("lu"), '屏' to listOf("ping"), '幕' to listOf("mu"),
            // System, settings & panel terminology
            '负' to listOf("fu"), '一' to listOf("yi"), '全' to listOf("quan"), '局' to listOf("ju"),
            '无' to listOf("wu"), '障' to listOf("zhang"), '碍' to listOf("ai"), '悬' to listOf("xuan"),
            '浮' to listOf("fu"), '窗' to listOf("chuang"), '球' to listOf("qiu"), '权' to listOf("quan"),
            '限' to listOf("xian"), '检' to listOf("jian"), '查' to listOf("cha"), '后' to listOf("hou"),
            '台' to listOf("tai"), '弹' to listOf("tan", "dan"), '出' to listOf("chu"), '界' to listOf("jie"),
            '面' to listOf("mian"), '池' to listOf("chi"), '优' to listOf("you"), '化' to listOf("hua"),
            '白' to listOf("bai"), '名' to listOf("ming"), '单' to listOf("dan"), '黑' to listOf("hei"),
            '存' to listOf("cun"), '储' to listOf("chu"), '勿' to listOf("wu"), '扰' to listOf("rao"),
            '据' to listOf("ju"), '统' to listOf("tong"), '护' to listOf("hu"), '眼' to listOf("yan"),
            '滤' to listOf("lv"), '色' to listOf("se"), '温' to listOf("wen"), '蓝' to listOf("lan"),
            '光' to listOf("guang"), '夜' to listOf("ye"), '间' to listOf("jian"), '主' to listOf("zhu"),
            '题' to listOf("ti"), '深' to listOf("shen"), '暗' to listOf("an"), '浅' to listOf("qian"),
            '亮' to listOf("liang"), '配' to listOf("pei"), '动' to listOf("dong"), '态' to listOf("tai"),
            '取' to listOf("qu"), '莫' to listOf("mo"), '奈' to listOf("nai"), '斯' to listOf("si"),
            '高' to listOf("gao"), '模' to listOf("mo"), '糊' to listOf("hu"), '遮' to listOf("zhe"),
            '罩' to listOf("zhao"), '底' to listOf("di"), '框' to listOf("kuang"), '透' to listOf("tou"),
            '明' to listOf("ming"), '圆' to listOf("yuan"), '角' to listOf("jiao"), '距' to listOf("ju"),
            '离' to listOf("li"), '尺' to listOf("chi"), '寸' to listOf("cun"), '宽' to listOf("kuan"),
            '格' to listOf("ge"), '列' to listOf("lie"), '异' to listOf("yi"), '形' to listOf("xing"),
            '覆' to listOf("fu"), '盖' to listOf("gai"), '挖' to listOf("wa"), '孔' to listOf("kong"),
            '刘' to listOf("liu"), '海' to listOf("hai"), '触' to listOf("chu"), '发' to listOf("fa"),
            '侧' to listOf("ce"), '滑' to listOf("hua"), '边' to listOf("bian"), '缘' to listOf("yuan"),
            '灵' to listOf("ling"), '敏' to listOf("min"), '震' to listOf("zhen"), '避' to listOf("bi"),
            '让' to listOf("rang"), '输' to listOf("shu"), '入' to listOf("ru"), '法' to listOf("fa"),
            '击' to listOf("ji"), '双' to listOf("shuang"), '上' to listOf("shang"), '下' to listOf("xia"),
            '左' to listOf("zuo"), '右' to listOf("you"), '呼' to listOf("hu"), '呼' to listOf("hu"),
            '吸' to listOf("xi"), '频' to listOf("pin"), '渐' to listOf("jian"), '隐' to listOf("yin"),
            '吸' to listOf("xi"), '附' to listOf("fu"), '鼠' to listOf("shu"), '标' to listOf("biao"),
            '移' to listOf("yi"), '速' to listOf("su"), '行' to listOf("xing", "hang"), '半' to listOf("ban"),
            '径' to listOf("jing"), '锁' to listOf("suo"), '定' to listOf("ding"), '布' to listOf("bu"),
            '保' to listOf("bao"), '护' to listOf("hu"), '误' to listOf("wu"), '操' to listOf("cao"),
            '作' to listOf("zuo"), '横' to listOf("heng"), '关' to listOf("guan"), '闭' to listOf("bi"),
            '收' to listOf("shou"), '自' to listOf("zi"), '跳' to listOf("tiao"), '转' to listOf("zhuan"),
            '辅' to listOf("fu"), '助' to listOf("zhu"), '层' to listOf("ceng"), '级' to listOf("ji"),
            '等' to listOf("deng"), '分' to listOf("fen"), '情' to listOf("qing"), '景' to listOf("jing"),
            '式' to listOf("shi"), '则' to listOf("ze"), '规' to listOf("gui"), '验' to listOf("yan"),
            '证' to listOf("zheng"), '码' to listOf("ma"), '复' to listOf("fu"), '制' to listOf("zhi"),
            '醒' to listOf("xing"), '提' to listOf("ti"), '示' to listOf("shi"), '轻' to listOf("qing"),
            '识' to listOf("shi"), '字' to listOf("zi"), '选' to listOf("xuan"), '抠' to listOf("kou"),
            '库' to listOf("ku"), '词' to listOf("ci"), '典' to listOf("dian"), '离' to listOf("li"),
            '线' to listOf("xian"), '在' to listOf("zai"), '免' to listOf("mian"), '装' to listOf("zhuang"),
            '扩' to listOf("kuo"), '展' to listOf("zhan"), '型' to listOf("xing"), '镜' to listOf("jing"),
            '像' to listOf("xiang"), '加' to listOf("jia"), '载' to listOf("zai"), '大' to listOf("da"),
            '语' to listOf("yu"), '言' to listOf("yan"), '密' to listOf("mi"), '钥' to listOf("yue"),
            '接' to listOf("jie"), '口' to listOf("kou"), '小' to listOf("xiao"),
            '布' to listOf("bu"), '联' to listOf("lian"), '桥' to listOf("qiao"), '特' to listOf("te"),
            '备' to listOf("bei"), '份' to listOf("fen"), '导' to listOf("dao"), '还' to listOf("huan"),
            '原' to listOf("yuan"), '迁' to listOf("qian"), '方' to listOf("fang"), '案' to listOf("an"),
            '教' to listOf("jiao"), '帮' to listOf("bang"), '常' to listOf("chang"), '见' to listOf("jian"),
            '开' to listOf("kai"), '源' to listOf("yuan"), '许' to listOf("xu"), '隐' to listOf("yin"),
            '私' to listOf("si"), '政' to listOf("zheng"), '策' to listOf("ce"), '合' to listOf("he"),
            '声' to listOf("sheng"), '明' to listOf("ming"), '版' to listOf("ban"), '本' to listOf("ben"),
            '于' to listOf("yu"), '删' to listOf("shan"), '除' to listOf("chu"), '退' to listOf("tui"),
            '抽' to listOf("chou"), '屉' to listOf("ti"), '近' to listOf("jin"), '运' to listOf("yun"),
            '键' to listOf("jian"), '盘' to listOf("pan"), '启' to listOf("qi"), '快' to listOf("kuai"),
            '九' to listOf("jiu"), '宫' to listOf("gong"), '搜' to listOf("sou"), '索' to listOf("suo"),
            '内' to listOf("nei"), '容' to listOf("rong"), '夹' to listOf("jia"), '桶' to listOf("tong"),
            '筒' to listOf("tong"), '蓝' to listOf("lan"), '静' to listOf("jing"), '响' to listOf("xiang"),
            '铃' to listOf("ling"), '热' to listOf("re"), '位' to listOf("wei"), '锁' to listOf("suo"),
            '桌' to listOf("zhuo"), '返' to listOf("fan"), '回' to listOf("hui"), '截' to listOf("jie"),
            '扫' to listOf("sao"), '贴' to listOf("tie"), '切' to listOf("qie"), '换' to listOf("huan"),
            '壁' to listOf("bi"), '纸' to listOf("zhi"), '体' to listOf("ti"), '缩' to listOf("suo"),
            '放' to listOf("fang"), '顺' to listOf("shun"), '序' to listOf("xu"), '组' to listOf("zu"),
            '件' to listOf("jian"), '随' to listOf("sui"), '二' to listOf("er"), '次' to listOf("ci"),
            '等' to listOf("deng"), '待' to listOf("dai"), '窗' to listOf("chuang"), '最' to listOf("zui"),
            '前' to listOf("qian"), '景' to listOf("jing"), '深' to listOf("shen"), '实' to listOf("shi"),
            '感' to listOf("gan"), '跟' to listOf("gen"), '随' to listOf("sui"), '统' to listOf("tong"),
            '系' to listOf("xi"), '单' to listOf("dan"), '选' to listOf("xuan"), '多' to listOf("duo"),
            '能' to listOf("neng"), '耗' to listOf("hao"), '电' to listOf("dian"), '量' to listOf("liang"),
            '内' to listOf("nei"), '存' to listOf("cun"), '详' to listOf("xiang"), '解' to listOf("jie"),
            '条' to listOf("tiao"), '魔' to listOf("mo"), '赞' to listOf("zan"), '赏' to listOf("shang"),
            '反' to listOf("fan"), '馈' to listOf("kui"), '选' to listOf("xuan"), '项' to listOf("xiang"),
            '启' to listOf("qi"), '动' to listOf("dong"), '开' to listOf("kai"), '机' to listOf("ji")
        ).filterValues { it.isNotEmpty() }
    }
}

data class TextSearchKeys(
    val rawLower: String,
    val compactLower: String,
    val syllableLists: List<List<String>>,
    val fullPinyins: List<String>,
    val initials: List<String>,
    val digitsOnly: String,
    val t9Fulls: List<String>,
    val t9Initials: List<String>
)

data class IndexedSearchApp<T>(
    val item: T,
    val packageName: String,
    val label: String,
    val keys: TextSearchKeys,
    val packageSegments: List<String>,
    val packageCompact: String
)

object AppQuickSearchMatcher {
    fun <T> indexApps(
        apps: List<T>,
        label: (T) -> String,
        packageName: (T) -> String,
        provider: PinyinIndexProvider
    ): List<IndexedSearchApp<T>> = apps.map { app ->
        val lbl = label(app)
        val pkg = packageName(app).trim()
        val pkgLower = pkg.lowercase()
        IndexedSearchApp(
            item = app,
            packageName = pkg,
            label = lbl,
            keys = provider.buildTextSearchKeys(lbl),
            packageSegments = pkgLower.split('.', '_', '-').filter { it.isNotEmpty() },
            packageCompact = pkgLower.filter { it.isLetterOrDigit() }
        )
    }

    fun <T> matchApps(
        indexed: List<IndexedSearchApp<T>>,
        rawQuery: String,
        t9Mode: Boolean = false,
        recentPackages: List<String> = emptyList(),
        maxResults: Int = 24
    ): List<T> {
        val q = cleanQuery(rawQuery)
        val recencyMap = HashMap<String, Int>(recentPackages.size * 2 + 1)
        recentPackages.forEachIndexed { idx, pkg ->
            if (pkg.isNotEmpty() && pkg !in recencyMap) recencyMap[pkg] = recentPackages.size - idx
        }
        if (q.isEmpty()) {
            return indexed.sortedWith(
                compareByDescending<IndexedSearchApp<T>> { recencyMap[it.packageName] ?: 0 }
                    .thenBy { it.label.lowercase() }
            ).take(maxResults).map { it.item }
        }
        val scored = ArrayList<Pair<IndexedSearchApp<T>, Int>>(indexed.size.coerceAtMost(32))
        for (entry in indexed) {
            val s = scoreEntry(entry, q, t9Mode)
            if (s > 0) scored += entry to s
        }
        return scored.sortedWith(
            compareByDescending<Pair<IndexedSearchApp<T>, Int>> { it.second }
                .thenByDescending { recencyMap[it.first.packageName] ?: 0 }
                .thenBy { it.first.label.length }
                .thenBy { it.first.label.lowercase() }
        ).take(maxResults).map { it.first.item }
    }

    fun <T> nextReachableKeys(
        indexed: List<IndexedSearchApp<T>>,
        currentQuery: String,
        candidateKeys: List<Char>,
        t9Mode: Boolean
    ): Set<Char> {
        val q = cleanQuery(currentQuery)
        if (q.isEmpty()) return candidateKeys.toSet()
        val reachable = HashSet<Char>(candidateKeys.size)
        for (c in candidateKeys) {
            val probe = q + c.lowercaseChar()
            if (indexed.any { scoreEntry(it, probe, t9Mode) > 0 }) {
                reachable += c
            }
        }
        return reachable
    }

    fun cleanQuery(raw: String): String = buildString(raw.length) {
        for (ch in raw.trim().lowercase()) {
            if (ch.isLetterOrDigit() || ch in '\u4E00'..'\u9FFF') append(ch)
        }
    }

    fun scoreTextMatch(keys: TextSearchKeys, rawQuery: String, t9Mode: Boolean = false): Int {
        val q = cleanQuery(rawQuery)
        if (q.isEmpty()) return 0
        val base = scoreKeys(keys, q, t9Mode)
        if (base > 0) return base
        if (q.length >= 2 && isOrderedSubsequence(keys.compactLower, q)) return 320
        return 0
    }

    private fun <T> scoreEntry(entry: IndexedSearchApp<T>, q: String, t9Mode: Boolean): Int {
        val k = entry.keys
        val textScore = scoreKeys(k, q, t9Mode)
        if (textScore > 0) return textScore
        if (entry.packageSegments.any { it.startsWith(q) }) return 520
        if (entry.packageCompact.contains(q)) return 460
        if (q.length >= 2) {
            if (isOrderedSubsequence(k.compactLower, q) ||
                k.initials.any { isOrderedSubsequence(it, q) } ||
                k.fullPinyins.any { isOrderedSubsequence(it, q) }
            ) {
                return 340
            }
        }
        return 0
    }

    private fun scoreKeys(k: TextSearchKeys, q: String, t9Mode: Boolean): Int {
        if (k.compactLower == q || k.rawLower == q) return 1000
        if (k.rawLower.startsWith(q) || k.compactLower.startsWith(q)) return 960
        if (k.initials.any { it == q }) return 950
        if (k.fullPinyins.any { it == q }) return 920
        if (k.initials.any { it.startsWith(q) }) return 900
        if (k.digitsOnly.isNotEmpty() && k.digitsOnly == q) return 880
        if (t9Mode && q.all { it in '0'..'9' }) {
            if (k.t9Initials.any { it == q }) return 870
            if (k.t9Initials.any { it.startsWith(q) }) return 835
        }
        if (k.fullPinyins.any { it.startsWith(q) }) return 850
        if (k.digitsOnly.isNotEmpty() && k.digitsOnly.startsWith(q)) return 825
        if (matchesSyllablePrefix(k.syllableLists, q, t9 = false, requireFirstToken = true)) return 805
        if (t9Mode && q.all { it in '0'..'9' }) {
            if (k.t9Fulls.any { it.startsWith(q) }) return 785
            if (matchesSyllablePrefix(k.syllableLists, q, t9 = true, requireFirstToken = true)) return 760
            if (matchesSyllablePrefix(k.syllableLists, q, t9 = true, requireFirstToken = false)) return 700
            if (k.t9Initials.any { it.contains(q) } || k.t9Fulls.any { it.contains(q) }) return 615
        }
        if (k.rawLower.contains(q) || k.compactLower.contains(q)) return 720
        if (matchesSyllablePrefix(k.syllableLists, q, t9 = false, requireFirstToken = false)) return 680
        if (k.fullPinyins.any { it.contains(q) }) return 650
        if (k.initials.any { it.contains(q) }) return 620
        if (k.digitsOnly.isNotEmpty() && k.digitsOnly.contains(q)) return 600
        return 0
    }

    internal fun matchesSyllablePrefix(
        syllableLists: List<List<String>>,
        query: String,
        t9: Boolean = false,
        requireFirstToken: Boolean = true
    ): Boolean {
        if (query.isEmpty() || syllableLists.isEmpty()) return false
        val startRange = if (requireFirstToken) 0..0 else syllableLists.indices
        val memo = HashMap<Int, Boolean>()
        fun dfs(tokIdx: Int, qi: Int): Boolean {
            if (qi == query.length) return true
            if (tokIdx >= syllableLists.size) return false
            val key = (tokIdx shl 8) or qi
            memo[key]?.let { return it }
            val readings = syllableLists[tokIdx]
            var hit = false
            for (raw in readings) {
                val cand = if (t9) PinyinIndexProvider.toT9String(raw) else raw
                if (cand.isEmpty()) continue
                val remaining = query.length - qi
                // Option 1: initial char of token -> move to next token.
                if (cand[0] == query[qi] && dfs(tokIdx + 1, qi + 1)) {
                    hit = true
                    break
                }
                // Option 2: full syllable/word consumed -> move to next token.
                if (cand.length > 1 && remaining >= cand.length &&
                    query.regionMatches(qi, cand, 0, cand.length) &&
                    dfs(tokIdx + 1, qi + cand.length)
                ) {
                    hit = true
                    break
                }
                // Option 3: prefix at end of query -> match complete.
                if (remaining in 2..cand.length && query.regionMatches(qi, cand, 0, remaining)) {
                    hit = true
                    break
                }
            }
            memo[key] = hit
            return hit
        }
        for (start in startRange) {
            if (dfs(start, 0)) return true
        }
        return false
    }

    internal fun isOrderedSubsequence(haystack: String, needle: String): Boolean {
        if (needle.isEmpty()) return true
        if (haystack.length < needle.length) return false
        var ni = 0
        for (ch in haystack) {
            if (ch == needle[ni]) {
                ni++
                if (ni == needle.length) return true
            }
        }
        return false
    }
}

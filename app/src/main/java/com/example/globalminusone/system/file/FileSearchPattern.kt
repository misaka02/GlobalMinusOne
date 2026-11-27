package com.example.globalminusone.system.file

import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException

/** RE2/J is linear-time; user expressions never run on Java's backtracking engine. */
internal class FileSearchPattern(private val query: String, regex: Boolean) {
    private val compiled: Pattern? = if (!regex) null else {
        require(query.length <= 256) { "正则最多256字符" }
        var expansion = 1L
        Regex("\\{(\\d+)(?:,(\\d*))?\\}").findAll(query).forEach { repetition ->
            val max = repetition.groups[2]?.value?.takeIf { it.isNotEmpty() } ?: repetition.groupValues[1]
            val count = max.toLongOrNull() ?: error("正则重复次数过大")
            require(count <= 128) { "正则单次有限重复最多128次" }
            expansion *= count.coerceAtLeast(1)
            require(expansion <= 4096 && expansion * query.length.coerceAtLeast(1) <= 8192) { "正则编译规模过大，请简化有限重复" }
        }
        try { Pattern.compile(query, Pattern.CASE_INSENSITIVE).also { require(it.programSize() <= 4096) { "正则程序超过4096指令，请简化模式" } } }
        catch (e: PatternSyntaxException) { throw IllegalArgumentException("正则无效：${e.message}。RE2不支持回溯引用或前后环视", e) }
    }
    fun matches(name: String): Boolean {
        if (compiled != null) require(name.length <= 4096) { "文件名超过4096字符，本次正则查找未完成；可改用普通文字查找" }
        return compiled?.matcher(name)?.find() ?: name.contains(query, ignoreCase = true)
    }
}

package com.example.globalminusone.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Privilege mode used by a [ComponentType.SHELL_COMMAND] card. */
@Serializable
enum class ShellExecutionMode(val label: String, val badge: String) {
    NORMAL("普通 UID (sh)", "SH"),
    SHIZUKU("Shizuku (ADB)", "Shizuku"),
    ROOT("Root (su)", "ROOT")
}

/**
 * Structured configuration for a [ComponentType.SHELL_COMMAND] card.
 * Stored encoded inside [ComponentItem.intentUri] with prefix `"gmo-shell-v1:"`
 * so `state_json_v5` schema stays backward-compatible.
 */
@Serializable
data class ShellCommandSpec(
    val command: String = "",
    val mode: ShellExecutionMode = ShellExecutionMode.NORMAL,
    val requireConfirm: Boolean = false,
    val timeoutSec: Int = 10,
    val showOutputDialog: Boolean = true,
    val preferCompactTile: Boolean = false
) {
    fun normalized(): ShellCommandSpec = copy(
        command = command.trim(),
        timeoutSec = timeoutSec.coerceIn(2, 60)
    )

    fun encodeToIntentUri(): String {
        val clean = normalized()
        return INTENT_PREFIX + codec.encodeToString(clean)
    }

    companion object {
        const val INTENT_PREFIX = "gmo-shell-v1:"
        private val codec = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

        private val HIGH_RISK_PATTERNS = listOf(
            Regex("""\brm\s+-[^\n;|&]*r[^\n;|&]*f|\brm\s+-[^\n;|&]*f[^\n;|&]*r|--no-preserve-root""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:mkfs|fdisk|parted|wipefs)\b|\bdd\s+[^\n;|&]*of=/dev/""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:reboot|poweroff|shutdown|halt)\b|\bsvc\s+power\s+shutdown\b""", RegexOption.IGNORE_CASE),
            Regex("""\bpm\s+(?:uninstall|clear)\s+(?:com\.android|android\b)""", RegexOption.IGNORE_CASE),
            Regex("""\bchmod\s+777\s+/|\bchown\s+[^\n;|&]+\s+/(?:system|vendor|data/system)\b""", RegexOption.IGNORE_CASE),
            Regex(""":\(\)\s*\{\s*:\s*\|\s*:\s*&\s*\}\s*;\s*:""")
        )

        fun isHighRiskCommand(command: String): Boolean {
            val trimmed = command.trim()
            if (trimmed.isEmpty()) return false
            return HIGH_RISK_PATTERNS.any { it.containsMatchIn(trimmed) }
        }

        fun fromComponent(item: ComponentItem): ShellCommandSpec {
            val uri = item.intentUri.trim()
            val fallbackCmd = item.textContent
            if (uri.startsWith(INTENT_PREFIX)) {
                val payload = uri.removePrefix(INTENT_PREFIX)
                val parsed = runCatching { codec.decodeFromString<ShellCommandSpec>(payload) }.getOrNull()
                if (parsed != null) {
                    val effectiveCmd = parsed.command.ifBlank { fallbackCmd }
                    return parsed.copy(
                        command = effectiveCmd,
                        timeoutSec = parsed.timeoutSec.coerceIn(2, 60)
                    )
                }
            }
            return ShellCommandSpec(
                command = fallbackCmd,
                mode = ShellExecutionMode.NORMAL,
                requireConfirm = false,
                timeoutSec = 10,
                showOutputDialog = true,
                preferCompactTile = false
            )
        }
    }
}

/** Built-in safe Shell command templates offered when creating or editing a Shell card. */
data class ShellCommandTemplate(
    val title: String,
    val description: String,
    val command: String,
    val mode: ShellExecutionMode = ShellExecutionMode.NORMAL,
    val requireConfirm: Boolean = false,
    val preferCompactTile: Boolean = false
)

val SHELL_COMMAND_TEMPLATES: List<ShellCommandTemplate> = listOf(
    ShellCommandTemplate(
        title = "内存概览",
        description = "无需Root，查看总内存与可用余量",
        command = "cat /proc/meminfo | head -n 10",
        mode = ShellExecutionMode.NORMAL
    ),
    ShellCommandTemplate(
        title = "存储空间",
        description = "无需Root，查看 /data 与 /sdcard 占用",
        command = "df -h /data /sdcard 2>/dev/null || df -h",
        mode = ShellExecutionMode.NORMAL
    ),
    ShellCommandTemplate(
        title = "网络接口",
        description = "查看当前 Wi-Fi / 流量接口地址",
        command = "ip -brief addr 2>/dev/null || ifconfig | head -n 20",
        mode = ShellExecutionMode.NORMAL
    ),
    ShellCommandTemplate(
        title = "网络延迟测试",
        description = "Ping 公共 DNS 检测网络连通性",
        command = "ping -c 2 -W 2 223.5.5.5",
        mode = ShellExecutionMode.NORMAL
    ),
    ShellCommandTemplate(
        title = "当前前台窗口",
        description = "通过 Shizuku 查看当前焦点包名与 Activity",
        command = "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -n 4",
        mode = ShellExecutionMode.SHIZUKU
    ),
    ShellCommandTemplate(
        title = "电池详细状态",
        description = "通过 Shizuku 读取电量/温度/充放电电流",
        command = "dumpsys battery",
        mode = ShellExecutionMode.SHIZUKU
    ),
    ShellCommandTemplate(
        title = "息屏锁屏",
        description = "通过 Shizuku 模拟电源键锁屏",
        command = "input keyevent 26",
        mode = ShellExecutionMode.SHIZUKU,
        requireConfirm = true,
        preferCompactTile = true
    ),
    ShellCommandTemplate(
        title = "清理下载临时日志",
        description = "示例带确认操作（建议开启执行前确认）",
        command = "ls -lh /sdcard/Download | head -n 25",
        mode = ShellExecutionMode.NORMAL,
        requireConfirm = false
    )
)

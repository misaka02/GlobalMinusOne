package com.example.globalminusone.screenassistant

import com.example.globalminusone.data.ScreenOcrEngine

internal object ScreenAssistantOverlayUiPolicy {
    fun shouldShowSettingsHint(
        panelCollapsed: Boolean,
        ocrDone: Boolean,
        blockCount: Int,
        ocrNote: String,
        engineUsed: String,
        configuredEngine: ScreenOcrEngine,
        isSubImageMode: Boolean
    ): Boolean {
        if (panelCollapsed || isSubImageMode) return false
        if (!ocrDone) return true
        if (blockCount <= 0 || ocrNote.isNotBlank()) return true
        return configuredEngine != ScreenOcrEngine.ACCESSIBILITY_TREE &&
            (engineUsed.contains("无障碍节点树") || engineUsed.contains("无障碍界面树"))
    }

    fun settingsHintActionLabel(
        ocrNote: String,
        engineUsed: String,
        configuredEngine: ScreenOcrEngine
    ): String = when {
        ocrNote.contains("签名") -> "去卸载修复 >"
        ocrNote.contains("未下载") || ocrNote.contains("尚未下载") || ocrNote.contains("未准备") -> "去下载模型 >"
        ocrNote.contains("未安装") || ocrNote.contains("尚未安装") -> "安装 OCR >"
        ocrNote.contains("异常") || ocrNote.contains("超时") || ocrNote.contains("连接") ||
            ocrNote.contains("校验") || ocrNote.contains("损坏") -> "排查 OCR >"
        configuredEngine != ScreenOcrEngine.ACCESSIBILITY_TREE &&
            (engineUsed.contains("无障碍节点树") || engineUsed.contains("无障碍界面树")) -> "启用 OCR >"
        else -> "识别设置 >"
    }
}

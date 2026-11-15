package com.example.globalminusone.ui.panel

/** A direct settings entry owns a boundary, not the invisible parents of the property tree. */
internal fun previousPropertySection(section: String, entrySection: String): String? = when {
    section == entrySection || section.isEmpty() -> null
    section in setOf("内部排列", "内部尺寸与间距", "图标与文字留白", "文件夹窗口") -> "容器与行为"
    else -> ""
}
